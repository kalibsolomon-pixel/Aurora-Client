package com.aurora.client.search;

import com.aurora.client.SourceTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The shared matcher behind every search field, and the feature search built on it (2026-11-12). */
class SearchMatcherTest {

    private static int score(String query, int tolerance, String target) {
        return SearchMatcher.score(SearchMatcher.query(query, tolerance), SearchMatcher.target(target));
    }

    @Test void normalizesBothSides() {
        assertEquals("sports esports", SearchMatcher.normalize("  Sports_Esports "));
        assertEquals("cafe creme", SearchMatcher.normalize("Café-Crème"));
        assertEquals("minecraft:flame", SearchMatcher.normalize("minecraft:flame"));
        assertTrue(SearchMatcher.query("   ", 2).isEmpty());
        assertEquals(0, score("", 2, "anything"), "an empty query matches everything");
    }

    @Test void exactBeatsPrefixBeatsSubstringBeatsTypo() {
        int exact = score("zoom", 2, "Zoom");
        int prefix = score("zoo", 2, "Zoom");
        int substring = score("oom", 2, "Zoom");
        int typo = score("zoon", 2, "Zoom");
        assertTrue(exact > prefix && prefix > substring && substring > typo && typo > 0,
                exact + " > " + prefix + " > " + substring + " > " + typo);
    }

    @Test void everyWordMustMatch() {
        assertTrue(score("hit color", 2, "Hitbox Color") > 0);
        assertEquals(-1, score("hit sound", 0, "Hitbox Color"));
    }

    @Test void typosFollowTheSettingAndTheWordLength() {
        assertEquals(-1, score("frelook", 0, "FreeLook"), "0 finds exact matches only");
        assertTrue(score("frelook", 1, "FreeLook") > 0, "a missing letter is one typo");
        assertTrue(score("satruation", 1, "Saturation Bar") > 0, "a swapped pair is one typo");
        assertEquals(-1, score("satrutaoin", 1, "Saturation Bar"), "three edits are more than one");
        assertTrue(score("satrutaoin", 3, "Saturation Bar") < 0, "words of 7+ letters allow at most 2 typos");
        assertEquals(-1, score("fov", 3, "Fog"), "words of 3 letters or fewer never fuzzy-match");
        assertTrue(score("pign", 1, "Ping") > 0, "4-letter words allow one typo");
        assertEquals(-1, score("pgni", 3, "Ping"), "and only one, whatever the setting");
        assertTrue(score("armr", 1, "Armor Status") > 0, "a typo while still typing a longer word");
    }

    @Test void distanceIsBoundedOptimalStringAlignment() {
        assertEquals(0, SearchMatcher.distance("abc", "abc", 2));
        assertEquals(1, SearchMatcher.distance("abc", "acb", 2));
        assertEquals(1, SearchMatcher.distance("abc", "abcd", 2));
        assertEquals(3, SearchMatcher.distance("abc", "xyzw", 2), "past the bound reports bound + 1");
    }

    @Test void indexRanksNamesFirstAndReportsTheMatchingSetting() {
        SearchIndex<String> index = SearchIndex.<String>builder()
                .entry("hitbox").field("Hitbox", 3, null).field("Shows entity hitboxes", 1, null)
                    .field("Hitbox Color", 2, "Hitbox Color")
                .entry("zoom").field("Zoom", 3, null).field("Zoom in with a key", 1, null)
                    .field("Scroll to Zoom", 2, "Scroll to Zoom")
                .entry("theme").field("Theme", 3, null).field("Accent Color", 2, "Accent Color")
                .build();
        var color = index.search("color", 2);
        assertEquals(List.of("hitbox", "theme"), color.stream().map(SearchIndex.Hit::value).toList());
        assertEquals("Hitbox Color", color.get(0).via(), "the hit names the setting it came through");
        var zoom = index.search("zoom", 2);
        assertEquals("zoom", zoom.get(0).value());
        assertNull(zoom.get(0).via(), "a name match reports no setting");
        assertSame(zoom, index.search(" ZOOM ", 2), "a repeated query is answered from the memo");
        assertEquals(3, index.search("", 2).size());
    }

    @Test void everySearchFieldUsesTheSharedMatcher() {
        for (String file : List.of("screen/AuroraScreen.java", "screen/ProfileManagerScreen.java",
                "screen/WaypointManagerScreen.java", "screen/setting/SearchListSetting.java",
                "screen/setting/ItemScaleSetting.java", "screen/setting/EffectExpiryListSetting.java")) {
            assertTrue(SourceTree.read(file).contains("com.aurora.client.search.Search"), file);
        }
        assertTrue(SourceTree.read("screen/IconCatalog.java").contains("com.aurora.client.search.SearchIndex"));
        String aurora = SourceTree.read("screen/AuroraScreen.java");
        assertFalse(aurora.contains("m.name.toLowerCase().contains(q)"), "the old per-frame substring filter is gone");
        assertTrue(aurora.contains("if (hits != lastHits)"), "result tiles rebuild only when the hits change");
        assertTrue(aurora.contains("searchField.setValue(searchQuery);"), "a re-init keeps the query visible");
    }
}
