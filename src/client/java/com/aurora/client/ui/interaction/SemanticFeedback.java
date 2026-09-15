package com.aurora.client.ui.interaction;

/** Dispatches feedback selected by a semantic action. */
@FunctionalInterface
public interface SemanticFeedback {
    SemanticFeedback NONE = sound -> {};

    void play(SemanticSound sound);
}
