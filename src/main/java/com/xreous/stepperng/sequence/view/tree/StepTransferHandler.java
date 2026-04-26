package com.xreous.stepperng.sequence.view.tree;

import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.condition.StepCondition;
import com.xreous.stepperng.sequence.StepSequence;
import com.xreous.stepperng.step.Step;
import com.xreous.stepperng.util.VariableQualifier;
import com.xreous.stepperng.variable.StepVariable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tree drag-and-drop: intra-sequence reorder plus cross-sequence move with variable rebind. */
final class StepTransferHandler extends TransferHandler {

    private static final DataFlavor STEP_FLAVOR =
            new DataFlavor(StepMove.class, "application/x-stepper-step-move");

    private final SequenceTreePanel owner;

    StepTransferHandler(SequenceTreePanel owner) { this.owner = owner; }

    @Override public int getSourceActions(JComponent c) { return MOVE; }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        if (!(path.getLastPathComponent() instanceof DefaultMutableTreeNode node)) return null;
        if (!(node.getUserObject() instanceof SequenceTreePanel.StepRef ref)) return null;
        if (isPinned(ref.sequence(), ref.step())) return null;
        int idx = ref.sequence().getSteps().indexOf(ref.step());
        return new StepTransferable(new StepMove(ref.sequence(), ref.step(), idx));
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDataFlavorSupported(STEP_FLAVOR) || !support.isDrop()) return false;
        StepMove move = extractMove(support);
        if (move == null) return false;
        JTree.DropLocation loc = (JTree.DropLocation) support.getDropLocation();
        TreePath dest = loc.getPath();
        if (dest == null) return false;
        StepSequence destSeq = sequenceOf(dest);
        if (destSeq == null) return false;
        int insertAt = resolveInsertIndex(loc, dest, destSeq);
        return !wouldDisplacePinned(destSeq, move, insertAt);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        StepMove move = extractMove(support);
        if (move == null) return false;

        JTree.DropLocation loc = (JTree.DropLocation) support.getDropLocation();
        TreePath dest = loc.getPath();
        StepSequence destSeq = sequenceOf(dest);
        if (destSeq == null) return false;

        int insertAt = resolveInsertIndex(loc, dest, destSeq);
        if (destSeq == move.sequence) return reorderWithinSequence(move, insertAt);
        return moveAcrossSequences(move, destSeq, insertAt);
    }

    private boolean reorderWithinSequence(StepMove move, int insertAt) {
        StepSequence seq = move.sequence;
        int from = seq.getSteps().indexOf(move.step);
        if (from < 0) return false;
        int target = insertAt;
        // Adjust for removal-then-insert semantics on downward moves.
        if (from < target) target--;
        if (target == from) return false;
        seq.moveStep(from, target);
        final int finalTarget = target;
        SwingUtilities.invokeLater(() -> owner.getPostMoveSelector().accept(seq, finalTarget));
        return true;
    }

    private boolean moveAcrossSequences(StepMove move, StepSequence destSeq, int insertAt) {
        StepSequence srcSeq = move.sequence;
        Step step = move.step;

        // Pull the live (possibly unsaved) request bytes from the open editor before moving.
        byte[] liveBytes = liveRequestBytes(srcSeq, step);
        if (liveBytes != null && liveBytes.length > 0) step.setRequestBody(liveBytes);

        Set<String> destNames = collectVariableNames(destSeq);
        Set<String> srcNames = collectVariableNames(srcSeq);
        Set<String> bareNames = VariableQualifier.findBareVarNames(step.getRequest());

        // Names used bare that won't resolve in dest but did resolve in src.
        Set<String> toQualify = new HashSet<>();
        for (String n : bareNames) {
            if (!destNames.contains(n) && srcNames.contains(n)) toQualify.add(n);
        }

        boolean wasValidation = step.getStepId().equals(srcSeq.getValidationStepId());
        boolean wasPostValidation = step.getStepId().equals(srcSeq.getPostValidationStepId());

        // Detect published variables on the moved step — external consumers using
        // $VAR:OldSeq:name$ will silently break when the host sequence changes.
        List<String> publishedNames = new ArrayList<>();
        for (StepVariable v : step.getVariableManager().getVariables()) {
            if (v.isPublished()) publishedNames.add(v.getIdentifier());
        }

        List<Step> srcSiblingsWithDangling = new ArrayList<>();
        for (Step s : srcSeq.getSteps()) {
            if (s == step) continue;
            StepCondition c = s.getCondition();
            if (c == null) continue;
            if (matchesStep(c.getGotoTarget(), step) || matchesStep(c.getElseGotoTarget(), step)) {
                srcSiblingsWithDangling.add(s);
            }
        }

        boolean clearOwnGoto = false;
        boolean clearOwnElseGoto = false;
        StepCondition myCond = step.getCondition();
        if (myCond != null) {
            // If own goto points to a step that stays in source (so won't be in dest), clear it.
            if (myCond.getGotoTarget() != null && !myCond.getGotoTarget().isEmpty()
                    && !stepIdExistsIn(destSeq, myCond.getGotoTarget())) {
                clearOwnGoto = true;
            }
            if (myCond.getElseGotoTarget() != null && !myCond.getElseGotoTarget().isEmpty()
                    && !stepIdExistsIn(destSeq, myCond.getElseGotoTarget())) {
                clearOwnElseGoto = true;
            }
        }

        String summary = buildSummary(srcSeq, destSeq, toQualify, wasValidation, wasPostValidation,
                clearOwnGoto, clearOwnElseGoto, srcSiblingsWithDangling, publishedNames);

        int choice = JOptionPane.showConfirmDialog(Stepper.suiteFrame(), summary, "Move step across sequences",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return false;

        if (!toQualify.isEmpty()) {
            step.setRequestBody(VariableQualifier.qualify(step.getRequest(), srcSeq.getTitle(), toQualify));
        }
        if (wasValidation) srcSeq.setValidationStepId(null);
        if (wasPostValidation) srcSeq.setPostValidationStepId(null);
        if (clearOwnGoto) myCond.setGotoTarget(null);
        if (clearOwnElseGoto) myCond.setElseGotoTarget(null);
        for (Step s : srcSiblingsWithDangling) {
            StepCondition c = s.getCondition();
            if (c == null) continue;
            if (matchesStep(c.getGotoTarget(), step)) c.setGotoTarget(null);
            if (matchesStep(c.getElseGotoTarget(), step)) c.setElseGotoTarget(null);
            srcSeq.stepModified(s);
        }

        srcSeq.removeStep(step);
        int clamped = Math.min(insertAt, destSeq.getSteps().size());
        destSeq.insertStepAt(clamped, step);

        Stepper.getSequenceManager().sequenceModified(srcSeq);
        Stepper.getSequenceManager().sequenceModified(destSeq);

        SwingUtilities.invokeLater(() -> owner.getPostMoveSelector().accept(destSeq, clamped));
        return true;
    }

    private static boolean isPinned(StepSequence seq, Step step) {
        String id = step.getStepId();
        return id.equals(seq.getValidationStepId()) || id.equals(seq.getPostValidationStepId());
    }

    private static boolean wouldDisplacePinned(StepSequence destSeq, StepMove move, int insertAt) {
        boolean sameSeq = destSeq == move.sequence;
        boolean hasPre = destSeq.getValidationStepId() != null
                && stepIdExistsIn(destSeq, destSeq.getValidationStepId());
        boolean hasPost = destSeq.getPostValidationStepId() != null
                && stepIdExistsIn(destSeq, destSeq.getPostValidationStepId());
        int size = destSeq.getSteps().size();
        if (hasPre && insertAt == 0) return true;
        int postBoundary = sameSeq ? size : size + 1;
        if (hasPost && insertAt >= postBoundary) return true;
        return false;
    }

    private static int resolveInsertIndex(JTree.DropLocation loc, TreePath dest, StepSequence destSeq) {
        int idx = loc.getChildIndex();
        int size = destSeq.getSteps().size();
        if (idx < 0) {
            Object last = dest.getLastPathComponent();
            if (last instanceof DefaultMutableTreeNode n
                    && n.getUserObject() instanceof SequenceTreePanel.StepRef ref) {
                idx = destSeq.getSteps().indexOf(ref.step()) + 1;
            } else {
                idx = size;
            }
        }
        if (idx > size) idx = size;
        return idx;
    }

    private static boolean matchesStep(String targetIdOrTitle, Step step) {
        if (targetIdOrTitle == null || targetIdOrTitle.isEmpty()) return false;
        return targetIdOrTitle.equals(step.getStepId())
                || targetIdOrTitle.equalsIgnoreCase(step.getTitle());
    }

    private static boolean stepIdExistsIn(StepSequence seq, String idOrTitle) {
        for (Step s : seq.getSteps()) {
            if (idOrTitle.equals(s.getStepId()) || idOrTitle.equalsIgnoreCase(s.getTitle())) return true;
        }
        return false;
    }

    private static Set<String> collectVariableNames(StepSequence seq) {
        Set<String> names = new HashSet<>();
        for (Step s : seq.getSteps()) {
            for (StepVariable v : s.getVariableManager().getVariables()) {
                names.add(v.getIdentifier());
            }
        }
        return names;
    }

    private static String buildSummary(StepSequence srcSeq, StepSequence destSeq, Set<String> toQualify,
                                       boolean wasValidation, boolean wasPostValidation,
                                       boolean clearOwnGoto, boolean clearOwnElseGoto,
                                       List<Step> siblingsWithDangling, List<String> publishedNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Move step from '").append(srcSeq.getTitle())
                .append("' to '").append(destSeq.getTitle()).append("'?\n\n");
        if (toQualify.isEmpty() && !wasValidation && !wasPostValidation
                && !clearOwnGoto && !clearOwnElseGoto && siblingsWithDangling.isEmpty()
                && publishedNames.isEmpty()) {
            sb.append("No variable or condition rebinds required.");
            return sb.toString();
        }
        sb.append("The following rebinds will be applied:\n");
        if (!toQualify.isEmpty()) {
            sb.append("  • ").append(toQualify.size())
                    .append(" variable reference(s) will be qualified to $VAR:")
                    .append(srcSeq.getTitle()).append(":name$: ")
                    .append(String.join(", ", toQualify)).append("\n");
        }
        if (wasValidation) sb.append("  • Source validation slot will be cleared\n");
        if (wasPostValidation) sb.append("  • Source post-validation slot will be cleared\n");
        if (clearOwnGoto) sb.append("  • Moved step's condition goto will be reset\n");
        if (clearOwnElseGoto) sb.append("  • Moved step's condition else-goto will be reset\n");
        if (!siblingsWithDangling.isEmpty()) {
            sb.append("  • ").append(siblingsWithDangling.size())
                    .append(" goto pointer(s) in the source sequence will be cleared\n");
        }
        if (!publishedNames.isEmpty()) {
            sb.append("\nWarning: this step publishes ").append(publishedNames.size())
                    .append(" variable(s): ").append(String.join(", ", publishedNames))
                    .append(".\nExternal references using $VAR:").append(srcSeq.getTitle())
                    .append(":name$ must be updated to $VAR:").append(destSeq.getTitle())
                    .append(":name$.\n");
        }
        return sb.toString();
    }

    private static byte[] liveRequestBytes(StepSequence seq, Step step) {
        try {
            if (Stepper.getUI() == null) return null;
            var host = Stepper.getUI().getExecutionHost(seq);
            return host != null ? host.liveRequestBytes(step) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static StepSequence sequenceOf(TreePath path) {
        for (int i = path.getPathCount() - 1; i >= 0; i--) {
            Object n = path.getPathComponent(i);
            if (n instanceof DefaultMutableTreeNode dmt) {
                Object u = dmt.getUserObject();
                if (u instanceof SequenceTreePanel.SequenceRef r) return r.sequence();
                if (u instanceof SequenceTreePanel.StepRef r) return r.sequence();
            }
        }
        return null;
    }

    private static StepMove extractMove(TransferSupport support) {
        try { return (StepMove) support.getTransferable().getTransferData(STEP_FLAVOR); }
        catch (UnsupportedFlavorException | java.io.IOException e) { return null; }
    }

    private record StepMove(StepSequence sequence, Step step, int originalIndex) {}

    private static final class StepTransferable implements Transferable {
        private final StepMove move;
        StepTransferable(StepMove move) { this.move = move; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{ STEP_FLAVOR }; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return STEP_FLAVOR.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!STEP_FLAVOR.equals(f)) throw new UnsupportedFlavorException(f);
            return move;
        }
    }
}
