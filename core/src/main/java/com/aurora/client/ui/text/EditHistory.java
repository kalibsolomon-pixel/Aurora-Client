package com.aurora.client.ui.text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/** Bounded, widget-independent text history. All positions are UTF-16 offsets. */
public final class EditHistory {
    public static final int DEFAULT_LIMIT = 100;
    public static final long TYPING_PAUSE_MILLIS = 1_000;

    public record State(String text, int cursor, int anchor) {
        public State {
            Objects.requireNonNull(text);
            if (cursor < 0 || cursor > text.length() || anchor < 0 || anchor > text.length()) {
                throw new IllegalArgumentException("Selection outside text");
            }
        }
    }

    /** Only individual typed word characters may join a typing run. */
    public enum Kind { TYPING, ATOMIC }

    private record Change(State before, State after) {}

    private final int limit;
    private final Deque<Change> undo = new ArrayDeque<>();
    private final Deque<Change> redo = new ArrayDeque<>();
    private boolean typing;
    private long lastTypedMillis;

    public EditHistory() { this(DEFAULT_LIMIT); }

    public EditHistory(int limit) {
        if (limit < 1) throw new IllegalArgumentException("History limit must be positive");
        this.limit = limit;
    }

    /** Record the accepted result, after validation/normalization and responder callbacks. */
    public void record(State before, State after, Kind kind, long nowMillis) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        Objects.requireNonNull(kind);
        if (before.text().equals(after.text())) {
            if (!before.equals(after) || kind == Kind.ATOMIC) breakRun();
            return;
        }
        Change previous = undo.peekLast();
        // A caller that replaced the document without clear() must never restore stale text.
        if (previous != null && !previous.after().text().equals(before.text())) {
            clear();
            previous = null;
        }
        boolean word = kind == Kind.TYPING && isTypedWordCharacter(before, after);
        boolean merge = word && typing && previous != null && previous.after().equals(before)
                && nowMillis >= lastTypedMillis && nowMillis - lastTypedMillis < TYPING_PAUSE_MILLIS;
        if (merge) {
            undo.removeLast();
            undo.addLast(new Change(previous.before(), after));
        } else {
            if (undo.size() == limit) undo.removeFirst();
            undo.addLast(new Change(before, after));
        }
        redo.clear();
        typing = word;
        lastTypedMillis = nowMillis;
    }

    private static boolean isTypedWordCharacter(State before, State after) {
        if (before.cursor() != before.anchor() || after.cursor() != after.anchor()) return false;
        int start = before.cursor(), end = after.cursor();
        if (end <= start || end > after.text().length()) return false;
        int cp = after.text().codePointAt(start);
        if (end - start != Character.charCount(cp)) return false;
        int type = Character.getType(cp);
        boolean word = Character.isLetterOrDigit(cp) || cp == '_'
                || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
        return word && after.text().length() == before.text().length() + end - start
                && before.text().regionMatches(0, after.text(), 0, start)
                && before.text().regionMatches(start, after.text(), end, before.text().length() - start);
    }

    /** Caret/selection/focus changes, clipboard actions and other editing boundaries. */
    public void breakRun() { typing = false; }

    /** Programmatic replacement starts a fresh document, including when its text is unchanged. */
    public void clear() {
        undo.clear();
        redo.clear();
        breakRun();
    }

    public State undo() {
        breakRun();
        Change change = undo.pollLast();
        if (change == null) return null;
        redo.addLast(change);
        return change.before();
    }

    public State redo() {
        breakRun();
        Change change = redo.pollLast();
        if (change == null) return null;
        undo.addLast(change);
        return change.after();
    }
}
