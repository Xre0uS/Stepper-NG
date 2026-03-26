package com.xreous.stepperng.about.view;

import com.xreous.stepperng.Globals;
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
        JLabel headerLabel = new JLabel("Stepper-NG v" + Globals.VERSION);
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
            String intro = "A multi-stage repeater for Burp Suite. Build request sequences, extract values " +
                    "with regex, and chain them across steps. Replaces session handling macros with " +
                    "automatic token management via published variables.\n\n";

            String quickStartHeader = "Quick Start:\n";
            String quickStart = "1. Create a sequence with +, rename the tab.\n" +
                    "2. Add steps via the + tab or right-click \u2192 Add to Stepper sequence.\n" +
                    "3. Define regex variables to extract response values (or highlight text \u2192 Auto Regex).\n" +
                    "4. Use $VAR:name$ in later steps. Click Execute to run.\n\n" +
                    "For auto session handling: publish a variable, reference it as $VAR:seq:name$ in other tools, " +
                    "and the sequence auto-executes when needed.\n\n";

            String syntaxHeader = "Variable Syntax:\n";
            String syntax = "$VAR:name$  \u2192  within the same sequence\n" +
                    "$VAR:SeqName:name$  \u2192  cross-sequence / other tools\n" +
                    "$GVAR:name$  \u2192  static global (manual key-value)\n" +
                    "$DVAR:name$  \u2192  dynamic global (auto-extracted from responses)\n\n";

            String regexHeader = "Regex Variables:\n";
            String regex = "Post-execution variables use regex on the response. First capture group is used as the value. " +
                    "No groups = whole match. Non-capturing: (?:REGEX)\n" +
                    "Example: Response \"Hello World!\" with regex Hello (World|Earth)! \u2192 World\n\n";

            String featuresHeader = "Key Features:\n";
            String features =
                    "\u2022 Published Variables - Mark a variable as \"published\" to expose it outside the sequence. " +
                    "When any tool (Scanner, Intruder, Repeater) sends a request containing $VAR:seq:name$, " +
                    "the owning sequence auto-executes to provide a fresh value. Combined with Burp session " +
                    "handling rules this gives fully automatic token/session management.\n\n" +

                    "\u2022 Passthrough & Sync - Published variables also update passively: every response " +
                    "flowing through Burp is matched against their regex patterns. This keeps tokens current " +
                    "without re-running the sequence, so the next substitution uses the latest value.\n\n" +

                    "\u2022 Conditional Steps - Each step can have an if/else condition based on status code or " +
                    "response body regex. On match you can continue, skip, goto another step, or retry up to N " +
                    "times with a configurable delay. This allows branching logic such as retrying on rate-limit " +
                    "responses or skipping optional steps.\n\n" +

                    "\u2022 Validation Step (Pre-Validation) - Designate one step as the validation step. It runs " +
                    "before the rest of the sequence: if its condition passes (e.g. session cookie still valid), " +
                    "the remaining steps are skipped entirely. Set \"validate every N requests\" to reduce " +
                    "overhead during high-volume scanning.\n\n" +

                    "\u2022 Post-Validation Step - Runs after the sequence completes to verify recovery succeeded. " +
                    "If the post-validation condition fails repeatedly, Stepper pauses Burp's task execution " +
                    "engine to prevent wasting requests with bad credentials, and alerts you to intervene.\n\n" +

                    "\u2022 Shared variable names - same identifier across steps syncs values automatically.\n" +
                    "\u2022 Hold requests - block concurrent workers until the sequence finishes (Preferences).\n" +
                    "\u2022 Global variables - static ($GVAR:) and dynamic ($DVAR:) across all tools.\n" +
                    "\u2022 Auto-backup - periodic JSON backups, restorable via Import.\n" +
                    "\u2022 Sequence args - X-Stepper-Execute-Before/After headers with inline variables.\n" +
                    "\u2022 Disable steps/sequences - right-click to toggle.\n" +
                    "\u2022 Stepper Replacements tab - preview variable substitutions with highlighting.\n" +
                    "\u2022 Sequence Overview tab - summary of all steps, conditions, and variables.\n" +
                    "\u2022 Loop prevention - max depth " + Globals.MAX_SEQUENCE_DEPTH + ", circular dependency detection.\n\n";

            String sessionActionHeader = "Session Handling Action:\n";
            String sessionAction = "For Extensions scope, add \"Stepper-NG: Variable Replacement for Extensions\" as a session handling " +
                    "action after the rule that injects $VAR: references. This resolves them inside the session " +
                    "handling phase since the HTTP handler runs before session rules for extensions.\n";

            String[] sections = new String[]{
                    intro, quickStartHeader, quickStart,
                    syntaxHeader, syntax,
                    regexHeader, regex,
                    featuresHeader, features,
                    sessionActionHeader, sessionAction};
            Style[] styles = new Style[]{
                    italics, bold, null,
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
