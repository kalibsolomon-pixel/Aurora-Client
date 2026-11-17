package com.aurora.client.ui.text;

import org.junit.jupiter.api.Test;

import static com.aurora.client.ui.text.EditHistory.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class EditHistoryTest {
    private final EditHistory history = new EditHistory();

    private static EditHistory.State end(String text) {
        return new EditHistory.State(text, text.length(), text.length());
    }

    private void type(String before, String after, long time) {
        history.record(end(before), end(after), TYPING, time);
    }

    @Test void consecutiveTypingUndoesAndRedoesAsOneWord() {
        type("", "c", 0); type("c", "ca", 100); type("ca", "cat", 200);
        assertEquals(end(""), history.undo());
        assertNull(history.undo());
        assertEquals(end("cat"), history.redo());
        assertNull(history.redo());
    }

    @Test void whitespaceSeparatesBothNeighboringWords() {
        type("", "a", 0); type("a", "a ", 10); type("a ", "a b", 20); type("a b", "a bc", 30);
        assertEquals(end("a "), history.undo());
        assertEquals(end("a"), history.undo());
        assertEquals(end(""), history.undo());
    }

    @Test void punctuationIsAWordBoundary() {
        type("", "a", 0); type("a", "a-", 10); type("a-", "a-b", 20);
        assertEquals(end("a-"), history.undo());
        assertEquals(end("a"), history.undo());
    }

    @Test void pauseOfOneSecondStartsNewStep() {
        type("", "a", 0); type("a", "ab", 999); type("ab", "abc", 1999);
        assertEquals(end("ab"), history.undo());
        assertEquals(end(""), history.undo());
    }

    @Test void clockReversalDoesNotMerge() {
        type("", "a", 50); type("a", "ab", 49);
        assertEquals(end("a"), history.undo());
    }

    @Test void explicitCaretRoundTripBreaksRun() {
        type("", "a", 0); history.breakRun(); type("a", "ab", 1);
        assertEquals(end("a"), history.undo());
    }

    @Test void changedCaretBreaksRunWithoutExplicitNotification() {
        type("", "ab", 0);
        history.record(new EditHistory.State("ab", 0, 0), new EditHistory.State("cab", 1, 1), TYPING, 1);
        assertEquals(new EditHistory.State("ab", 0, 0), history.undo());
        assertEquals(end(""), history.undo());
    }

    @Test void selectionReplacementRestoresSelectionDirection() {
        var selected = new EditHistory.State("hello", 1, 4);
        var replaced = new EditHistory.State("hxo", 2, 2);
        history.record(selected, replaced, TYPING, 0);
        assertEquals(selected, history.undo());
        assertEquals(replaced, history.redo());
    }

    @Test void reverseSelectionIsRestored() {
        var selected = new EditHistory.State("hello", 4, 1);
        history.record(selected, new EditHistory.State("ho", 1, 1), ATOMIC, 0);
        assertEquals(selected, history.undo());
    }

    @Test void pasteIsSeparateEvenWhenItContainsOneLetter() {
        type("", "a", 0);
        history.record(end("a"), end("ab"), ATOMIC, 1);
        type("ab", "abc", 2);
        assertEquals(end("ab"), history.undo());
        assertEquals(end("a"), history.undo());
        assertEquals(end(""), history.undo());
    }

    @Test void deletionAndInsertionNeverCoalesce() {
        type("", "a", 0); type("a", "ab", 1);
        history.record(end("ab"), end("a"), ATOMIC, 2);
        type("a", "ac", 3);
        assertEquals(end("a"), history.undo());
        assertEquals(end("ab"), history.undo());
        assertEquals(end(""), history.undo());
    }

    @Test void forwardWordDeletionRestoresCaret() {
        var before = new EditHistory.State("one two", 0, 0);
        var after = new EditHistory.State("two", 0, 0);
        history.record(before, after, ATOMIC, 0);
        assertEquals(before, history.undo());
        assertEquals(after, history.redo());
    }

    @Test void undoThenEditDiscardsRedoBranch() {
        type("", "a", 0); history.breakRun(); type("a", "ab", 1);
        history.undo(); type("a", "ac", 2);
        assertNull(history.redo());
        assertEquals(end("a"), history.undo());
    }

    @Test void undoRedoBreakTypingRun() {
        type("", "a", 0); history.undo(); history.redo(); type("a", "ab", 1);
        assertEquals(end("a"), history.undo());
    }

    @Test void rejectedCharacterDoesNotMakeAnUndoStepOrDiscardRedo() {
        type("", "a", 0); history.undo();
        type("", "", 1);
        assertEquals(end("a"), history.redo());
        assertEquals(end(""), history.undo());
    }

    @Test void noOpClipboardActionStillBreaksRun() {
        type("", "a", 0);
        history.record(end("a"), end("a"), ATOMIC, 1);
        type("a", "ab", 2);
        assertEquals(end("a"), history.undo());
    }

    @Test void noOpSelectionChangeBreaksRun() {
        type("", "a", 0);
        history.record(end("a"), new EditHistory.State("a", 0, 1), TYPING, 1);
        type("a", "ab", 2);
        assertEquals(end("a"), history.undo());
    }

    @Test void programmaticResetClearsBothStacks() {
        type("", "a", 0); history.breakRun(); type("a", "ab", 1); history.undo();
        history.clear();
        assertNull(history.undo()); assertNull(history.redo());
        type("loaded", "loadedx", 2);
        assertEquals(end("loaded"), history.undo());
        assertNull(history.undo());
    }

    @Test void defaultHistoryDropsOldestBeyondOneHundredSteps() {
        for (int i = 0; i < 105; i++) {
            history.record(end(Integer.toString(i)), end(Integer.toString(i + 1)), ATOMIC, i);
        }
        for (int i = 104; i >= 5; i--) assertEquals(end(Integer.toString(i)), history.undo());
        assertNull(history.undo());
        for (int i = 6; i <= 105; i++) assertEquals(end(Integer.toString(i)), history.redo());
        assertNull(history.redo());
    }

    @Test void oneStepHistoryWorks() {
        var single = new EditHistory(1);
        single.record(end(""), end("a"), ATOMIC, 0);
        single.record(end("a"), end("ab"), ATOMIC, 1);
        assertEquals(end("a"), single.undo()); assertNull(single.undo());
        assertEquals(end("ab"), single.redo());
    }

    @Test void combiningMarksAndNonLatinLettersJoinTypingRun() {
        type("", "e", 0); type("e", "e\u0301", 1); type("e\u0301", "e\u0301\u4e2d", 2);
        assertEquals(end(""), history.undo());
    }

    @Test void supplementaryLetterUsesUtf16CaretOffsets() {
        String letter = new String(Character.toChars(0x10400));
        type("", letter, 0); type(letter, letter + "x", 1);
        assertEquals(end(""), history.undo());
        assertEquals(end(letter + "x"), history.redo());
    }

    @Test void emojiIsPreservedAsAnAtomicCharacter() {
        type("", "\ud83d\ude00", 0); type("\ud83d\ude00", "\ud83d\ude00x", 1);
        assertEquals(end("\ud83d\ude00"), history.undo());
        assertEquals(end(""), history.undo());
    }

    @Test void multiCharacterCommitIsOneSeparateStep() {
        type("", "a", 0); type("a", "abc", 1); type("abc", "abcd", 2);
        assertEquals(end("abc"), history.undo());
        assertEquals(end("a"), history.undo());
    }

    @Test void normalizedResponderResultIsRecordedRatherThanRawInput() {
        history.record(end("12"), end("123"), ATOMIC, 0);
        assertEquals(end("12"), history.undo());
        assertEquals(end("123"), history.redo());
    }

    @Test void staleDocumentCannotUndoIntoEarlierDocument() {
        type("", "a", 0); type("loaded", "loadedx", 1);
        assertEquals(end("loaded"), history.undo());
        assertNull(history.undo());
    }

    @Test void invalidLimitsAndSelectionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EditHistory(0));
        assertThrows(IllegalArgumentException.class, () -> new EditHistory.State("a", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EditHistory.State("a", 0, 2));
        assertThrows(NullPointerException.class, () -> new EditHistory.State(null, 0, 0));
    }
}
