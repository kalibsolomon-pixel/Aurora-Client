package com.aurora.client.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A ranked, memoized search over a fixed list of entries, each with weighted
 * text fields (2026-11-12). The feature search indexes a feature's name
 * (weight 3), the names of the settings inside it (2) and its description
 * (1); a hit reports which field matched best, so the result can say "found
 * through the Hitbox Color setting".
 *
 * <p>Results are memoized on the normalized query and tolerance: a screen
 * asking every frame pays for one search per change. Build a new index when
 * the entries change. No Minecraft imports (ADR 0003).
 */
public final class SearchIndex<T> {

    /** One searchable field: its prepared text, weight, and the label a hit through it reports (null for the name). */
    public record Field(SearchMatcher.Target target, int weight, String via) {}

    /** One entry: the value returned and its fields. */
    public record Entry<T>(T value, List<Field> fields) {}

    /** A match: the value, its score, and the label of the field that matched (null when the name did). */
    public record Hit<T>(T value, int score, String via) {}

    private final List<Entry<T>> entries;
    private String lastQuery;
    private int lastTolerance = -1;
    private List<Hit<T>> lastHits = List.of();

    public SearchIndex(List<Entry<T>> entries) {
        this.entries = List.copyOf(entries);
    }

    public static <T> Builder<T> builder() { return new Builder<>(); }

    /** Every entry, ranked best first; an empty query returns every entry in order. */
    public synchronized List<Hit<T>> search(String raw, int tolerance) {
        SearchMatcher.Query query = SearchMatcher.query(raw, tolerance);
        if (query.text().equals(lastQuery) && query.tolerance() == lastTolerance) return lastHits;
        List<Hit<T>> hits = new ArrayList<>();
        for (Entry<T> entry : entries) {
            int best = -1;
            String via = null;
            for (Field field : entry.fields()) {
                int s = SearchMatcher.score(query, field.target());
                if (s < 0) continue;
                s *= field.weight();
                if (s > best) {
                    best = s;
                    via = field.via();
                }
            }
            if (best >= 0) hits.add(new Hit<>(entry.value(), best, via));
        }
        if (!query.isEmpty()) hits.sort((a, b) -> Integer.compare(b.score(), a.score())); // stable: ties keep order
        lastQuery = query.text();
        lastTolerance = query.tolerance();
        lastHits = Collections.unmodifiableList(hits);
        return lastHits;
    }

    public int size() { return entries.size(); }

    /** Collects entries; {@link Entries#field} adds fields to the entry last begun. */
    public static final class Builder<T> {
        private final List<Entry<T>> entries = new ArrayList<>();

        public Entries<T> entry(T value) {
            List<Field> fields = new ArrayList<>();
            entries.add(new Entry<>(value, fields));
            return new Entries<>(this, fields);
        }

        public SearchIndex<T> build() { return new SearchIndex<>(entries); }
    }

    /** Fluent field adder for one entry. */
    public static final class Entries<T> {
        private final Builder<T> builder;
        private final List<Field> fields;

        Entries(Builder<T> builder, List<Field> fields) {
            this.builder = builder;
            this.fields = fields;
        }

        /** Adds a field; blank text is skipped. */
        public Entries<T> field(String text, int weight, String via) {
            if (text != null && !text.isBlank()) fields.add(new Field(SearchMatcher.target(text), weight, via));
            return this;
        }

        public Entries<T> entry(T value) { return builder.entry(value); }

        public SearchIndex<T> build() { return builder.build(); }
    }
}
