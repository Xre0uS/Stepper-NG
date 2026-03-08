package com.xreous.stepperng.util.variablereplacementstab;

import burp.api.montoya.ui.editor.extension.EditorCreationContext;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;
import burp.api.montoya.ui.editor.extension.HttpRequestEditorProvider;
import com.xreous.stepperng.sequencemanager.SequenceManager;

public class VariableReplacementsTabFactory implements HttpRequestEditorProvider {

    private final SequenceManager sequenceManager;

    public VariableReplacementsTabFactory(SequenceManager sequenceManager){
        this.sequenceManager = sequenceManager;
    }

    @Override
    public ExtensionProvidedHttpRequestEditor provideHttpRequestEditor(EditorCreationContext creationContext) {
        return new VariableReplacementsTab(sequenceManager);
    }
}
