package com.xreous.stepperng.about.view;

import com.xreous.stepperng.Stepper;
import com.xreous.stepperng.util.Utils;
import com.xreous.stepperng.util.view.NoTextSelectionCaret;
import com.xreous.stepperng.util.view.WrappedTextPane;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;

public class AboutPanel extends JPanel {

    public AboutPanel(){
        this.setLayout(new GridBagLayout());
        JPanel innerPanel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.weighty = 0;
        gbc.gridx = gbc.gridy = 1;
        JLabel headerLabel = new JLabel("Stepper-NG");
        Font font = this.getFont().deriveFont(32f).deriveFont(this.getFont().getStyle() | Font.BOLD);
        headerLabel.setFont(font);
        headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        innerPanel.add(headerLabel, gbc);

        gbc.gridy++;
        gbc.weighty = 0;
        JLabel subtitle = new JLabel("A multi-stage repeater with dynamic variable extraction");
        Font subtitleFont = subtitle.getFont().deriveFont(16f).deriveFont(subtitle.getFont().getStyle() | Font.ITALIC);
        subtitle.setFont(subtitleFont);
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        innerPanel.add(subtitle, gbc);

        gbc.gridy++;
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        separator.setBorder(BorderFactory.createEmptyBorder(7,0,7,0));
        innerPanel.add(separator, gbc);

        JPanel contactPanel = new JPanel(new GridLayout(2,2));

        String githubLogoFilename = "GitHubLogo" +
                (UIManager.getLookAndFeel().getName().equalsIgnoreCase("darcula") ? "White" : "Black")
                + ".png";
        ImageIcon githubImage = Utils.loadImage(githubLogoFilename, 30, 30);
        JButton viewOnGithubButton;
        if(githubImage != null){
            viewOnGithubButton = new JButton("View Project on GitHub", githubImage);
            viewOnGithubButton.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            viewOnGithubButton.setIconTextGap(7);
        }else{
            viewOnGithubButton = new JButton("View Project on GitHub");
        }
        viewOnGithubButton.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/Xre0uS/stepper-ng"));
            } catch (IOException | URISyntaxException e) {}
        });

        JButton originalButton = new JButton("Original Stepper by CoreyD97");
        originalButton.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://github.com/CoreyD97/Stepper"));
            } catch (IOException | URISyntaxException e) {}
        });

        contactPanel.add(new JLabel("Maintained by: Xre0uS"));
        contactPanel.add(viewOnGithubButton);
        contactPanel.add(new JLabel("Based on Stepper by CoreyD97"));
        contactPanel.add(originalButton);
        contactPanel.setBorder(BorderFactory.createEmptyBorder(0,10,15,0));

        gbc.gridy++;
        innerPanel.add(contactPanel, gbc);

        gbc.gridy++;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        WrappedTextPane aboutContent = new WrappedTextPane();
        aboutContent.setEditable(false);
        aboutContent.setOpaque(false);
        aboutContent.setCaret(new NoTextSelectionCaret(aboutContent));
        JScrollPane aboutScrollPane = new JScrollPane(aboutContent);
        aboutScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        aboutScrollPane.setBorder(null);
        innerPanel.add(aboutScrollPane, gbc);
        Style bold = aboutContent.getStyledDocument().addStyle("bold", null);
        StyleConstants.setBold(bold, true);
        Style italics = aboutContent.getStyledDocument().addStyle("italics", null);
        StyleConstants.setItalic(italics, true);

        try {
            String intro = "Stepper-NG is a natural evolution of Burp Suite's Repeater tool, " +
                    "providing the ability to create sequences of steps and define regular expressions to extract " +
                    "values from responses which can then be used in subsequent steps. " +
                    "Based on the original Stepper by CoreyD97, migrated to the Montoya API " +
                    "and extended with global variables, auto-regex generation, published variables " +
                    "for automatic session management, conditional step execution, and more.\n\n";

            String quickStartHeader = "Quick Start:\n";
            String quickStart = "1. Create a sequence with +, double-click the tab to rename it (e.g. login-seq).\n" +
                    "2. Add steps manually or via right-click \u2192 Add to Stepper sequence from any Burp tool.\n" +
                    "3. Define post-execution variables with regex to capture response values (or highlight text and click Auto Regex).\n" +
                    "4. Use $VAR:name$ in later steps to insert captured values.\n" +
                    "5. Click Execute to run all steps in order.\n\n" +
                    "For automatic session handling:\n" +
                    "1. Publish a variable (check the Published checkbox on a post-execution variable).\n" +
                    "2. Use $VAR:login-seq:jwt$ in Repeater/Intruder/Scanner \u2014 the sequence auto-executes when referenced.\n" +
                    "3. Set a Validation Step to skip the sequence when the session is still valid.\n" +
                    "4. Set Validate every N requests in Preferences to throttle during scans.\n\n";

            String instructionsHeader = "Detailed Instructions:\n";
            String instructions = "1. Create a new sequence. Double-click the title to set a suitable name.\n" +
                    "2. Add your steps to the sequence manually, or using the context menu entry.\n" +
                    "3. Optional: Define variables for steps.\n" +
                    "  \u2022 Pre-execution variables obtain their value before the step is run. Useful for one-time passcodes etc.\n" +
                    "  \u2022 Post-execution variables extract their value from the step's response using regular expressions.\n" +
                    "  Tip: You can execute a single step to test your regular expressions using the button in the top right.\n" +
                    "  Tip: Highlight text in a response and click Auto Regex to generate a pattern automatically.\n" +
                    "4. Execute the entire sequence using the button at the bottom of the panel.\n\n" +
                    "Steps can be rearranged by right-clicking their tab.\n\n";

            String globalVarsHeader = "Global Variables:\n";
            String globalVars = "The Global Variables tab provides two types of variables available across all Burp tools:\n\n" +
                    "Static Global Variables: Simple key-value pairs you set manually. " +
                    "Use $GVAR:name$ in any request to substitute the value.\n\n" +
                    "Dynamic Global Variables: Define a regex pattern and Stepper-NG will automatically extract " +
                    "matching values from all HTTP responses flowing through Burp. " +
                    "Optionally filter by host regex. Use $DVAR:name$ in any request to substitute the latest matched value.\n\n";

            String variableHelpHeader = "Step Variables:\n";
            String variableHelp = "Variables can be defined within each step of a sequence.\n" +
                    "Pre-execution Variables: Prompts the user for a value before the step runs.\n" +
                    "Post-execution Variables: Define a regex to extract data from a step's response.\n" +
                    "All variables may be updated in later steps after their definition.\n\n";

            String regularExpressionHeader = "Post-Execution (Regex) Variables:\n";
            String regularExpressionHelp = "Variables defined with a regular expression are updated each time " +
                    "the step in which they are defined is executed.\n" +
                    "The regular expression is executed on the response, with the first match being used as the new value.\n" +
                    "If the regex has no groups, the whole match will be used.\n" +
                    "If the regex defines capture groups, the first group will be used.\n" +
                    "For non-capturing groups, use: (?:REGEX)\n\n";

            String regularExpressionExampleHeader = "Example: \n";
            String regularExpressionExample = "Response: \"Hello People, Hello World!\"\n" +
                    "Expression: World|Earth, Result: World\n" +
                    "Expression: Hello (World|Earth)!, Result: World\n" +
                    "Expression: (?:Goodbye|Hello) (World)!, Result: World\n\n";

            String variableUsageHeader = "Variable Usage:\n";
            String variableInsertion = "To use a variable in a request, insert it using the syntax below:\n";
            String variableExampleSequenceTitle = "In a sequence:  ";
            String variableExampleSequenceUsage = "$VAR:VARIABLE_IDENTIFIER$\n";
            String variableExampleToolTitle = "In other tools:  ";
            String variableExampleToolUsage = "$VAR:SEQUENCE_NAME:VARIABLE_IDENTIFIER$\n";
            String variableExampleGvarTitle = "Static global:  ";
            String variableExampleGvarUsage = "$GVAR:VARIABLE_NAME$\n";
            String variableExampleDvarTitle = "Dynamic global:  ";
            String variableExampleDvarUsage = "$DVAR:VARIABLE_NAME$\n\n";

            String stepExecutionHeader = "Executing Sequences From Other Tools:\n";
            String stepExecutionUsageA = "To execute a sequence before or after a request in another tool, " +
                    "add the headers ";
            String stepExecutionUsageItalics = "\"X-Stepper-Execute-Before: SEQUENCENAME\" " +
                    "or \"X-Stepper-Execute-After: SEQUENCENAME\"";
            String stepExecutionUsageB = " to the request. This will cause the sequence to be executed and variables " +
                    "to be updated every time the request is sent.\n\n";

            String sequenceArgsHeader = "Passing Arguments to Sequences:\n";
            String sequenceArgsA = "You can pass arguments to sequences to override global variable values during execution. " +
                    "This is useful when a sequence needs dynamic values that match the current request.\n\n" +
                    "Option 1 — Inline with the execute header:\n";
            String sequenceArgsBItalics = "\"X-Stepper-Execute-Before: SEQNAME: var1=value1; var2=value2\"\n\n";
            String sequenceArgsC = "Option 2 — Dedicated argument header (one per header, supports any value):\n";
            String sequenceArgsDItalics = "\"X-Stepper-Argument: var=value\"\n\n";

            String disableStepsHeader = "Disabling Steps:\n";
            String disableStepsText = "Right-click a step tab to enable or disable it. " +
                    "Disabled steps are skipped during sequence execution. " +
                    "This is useful for testing whether specific steps can be bypassed.\n\n";

            String disableSeqHeader = "Disabling Sequences:\n";
            String disableSeqText = "Right-click a sequence tab to enable or disable it. " +
                    "When disabled, $VAR: references are left as literal text, the sequence won't auto-execute, " +
                    "and passthrough sync is skipped. The tab shows a \u2298 prefix.\n\n";

            String sharedVarsHeader = "Shared Variable Names:\n";
            String sharedVarsText = "When multiple steps extract the same logical value (e.g. a JWT) from different " +
                    "endpoints with different response formats, give them the same variable identifier. " +
                    "After either step executes, the captured value syncs to same-named variables in other steps. " +
                    "Only one needs to be published. This also works with passthrough sync.\n\n";

            String conditionsHeader = "Conditional Steps:\n";
            String conditionsText = "Each step can have a condition evaluated after execution. " +
                    "Condition types: Response body or Status line (with matches/does not match), or Always (fires unconditionally). " +
                    "Actions: Continue to next step, Skip remaining steps, or Go to step. " +
                    "For pattern-based conditions, configure retry N\u00d7 with a delay between attempts. " +
                    "An else action fires when the condition does not trigger (after all retries are exhausted), " +
                    "enabling if/else branching — e.g., retry refresh 2\u00d7, skip if 200, else continue to full login. " +
                    "When set to Always, retry and else are ignored since the action always fires.\n\n";

            String sessionHeader = "Session Validation:\n";
            String sessionText = "Set a Validation Step on a sequence to enable session validation mode. " +
                    "Configure the step's condition to describe what a valid session looks like, " +
                    "e.g. \"If status line matches 200\". " +
                    "When the condition triggers, the session is valid and the rest of the sequence is skipped. " +
                    "When it doesn't trigger, the session is invalid and the full sequence runs.\n\n";

            String autoSessionHeader = "Auto Session Handler:\n";
            String autoSessionText = "Published variables drive session management automatically. " +
                    "When an outgoing request contains $VAR:SequenceName:varName$ referencing a published variable, " +
                    "Stepper-NG auto-executes the owning sequence. " +
                    "Use Burp's session handling rules to inject $VAR:seq:name$ into headers/cookies. " +
                    "The validation step runs first — if the session is valid, the rest is skipped. " +
                    "Use 'Validate every N requests' in Preferences to throttle checks during scans.\n\n";

            String overviewHeader = "Sequence Overview:\n";
            String overviewText = "Each sequence has an Overview tab that displays a summary table of all steps " +
                    "with their status, target, conditions, variables, and actions at a glance.\n\n";

            String publishedVarsHeader = "Published Variables:\n";
            String publishedVarsText = "Mark any post-execution variable as Published using the checkbox in the variable table. " +
                    "When an outgoing request contains $VAR:SequenceName:varName$ referencing a published variable, " +
                    "the sequence automatically executes before the request — no X-Stepper-Execute-Before header needed. " +
                    "Combined with validation steps, this gives you fully automatic token management.\n\n";

            String passthroughHeader = "Passthrough Variable Sync:\n";
            String passthroughText = "Published regex variables automatically update when matching patterns are found " +
                    "in responses flowing through Burp (proxy, repeater, etc.). " +
                    "This means if tokens are refreshed through normal browsing, Stepper-NG captures the new values " +
                    "without needing to re-run the sequence.\n\n";

            String loopPreventionHeader = "Infinite Loop Prevention:\n";
            String loopPreventionText = "Stepper-NG tracks the chain of executing sequences on each thread. " +
                    "If a sequence is already on the execution stack (circular dependency), it is skipped. " +
                    "A maximum nesting depth of " + com.xreous.stepperng.Globals.MAX_SEQUENCE_DEPTH + " prevents runaway execution.\n";

            String[] sections = new String[]{
                    intro, quickStartHeader, quickStart,
                    instructionsHeader, instructions,
                    globalVarsHeader, globalVars,
                    variableHelpHeader, variableHelp,
                    regularExpressionHeader, regularExpressionHelp,
                    regularExpressionExampleHeader, regularExpressionExample,
                    variableUsageHeader, variableInsertion,
                    variableExampleSequenceTitle, variableExampleSequenceUsage,
                    variableExampleToolTitle, variableExampleToolUsage,
                    variableExampleGvarTitle, variableExampleGvarUsage,
                    variableExampleDvarTitle, variableExampleDvarUsage,
                    stepExecutionHeader, stepExecutionUsageA,
                    stepExecutionUsageItalics, stepExecutionUsageB,
                    sequenceArgsHeader, sequenceArgsA, sequenceArgsBItalics,
                    sequenceArgsC, sequenceArgsDItalics,
                    disableStepsHeader, disableStepsText,
                    disableSeqHeader, disableSeqText,
                    sharedVarsHeader, sharedVarsText,
                    conditionsHeader, conditionsText,
                    sessionHeader, sessionText,
                    autoSessionHeader, autoSessionText,
                    publishedVarsHeader, publishedVarsText,
                    passthroughHeader, passthroughText,
                    loopPreventionHeader, loopPreventionText,
                    overviewHeader, overviewText};
            Style[] styles = new Style[]{
                    italics, bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    null, italics,
                    null, italics,
                    null, italics,
                    null, italics,
                    bold, null,
                    italics, null,
                    bold, null, italics,
                    null, italics,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null,
                    bold, null};

            String content = String.join("", sections);
            aboutContent.setText(content);
            int offset = 0;
            for (int i = 0; i < sections.length; i++) {
                String section = sections[i];
                if(styles[i] != null)
                    aboutContent.getStyledDocument().setCharacterAttributes(offset, section.length(), styles[i], false);
                offset += section.length();
            }

        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            Stepper.montoya.logging().logToError(writer.toString());
        }

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 0;
        gbc.weighty = 0.9;

        innerPanel.setPreferredSize(new Dimension(900, 800));
        innerPanel.setMinimumSize(new Dimension(500, 300));
        this.add(innerPanel, gbc);

        gbc.weighty = 0.1;
        gbc.gridx = 1;
        gbc.gridy++;
        this.add(new JPanel(), gbc);
        gbc.gridx = 3;
        this.add(new JPanel(), gbc);
    }
}
