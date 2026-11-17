package com.aurora.client.config.profile;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ProfileNameValidator}: the one profile-name rule set, shared by
 * ProfileManager's CRUD gates and the screens' under-field refusals.
 */
class ProfileNameValidatorTest {

    @Test
    void acceptsANewDistinctName() {
        assertNull(ProfileNameValidator.problem("PvP", List.of("default", "Builder"), null));
        assertNull(ProfileNameValidator.problem("  Trailing  ", List.of("default"), null));
    }

    @Test
    void refusesEmptyBlankAndNull() {
        assertEquals("Name can't be empty", ProfileNameValidator.problem(null, List.of(), null));
        assertEquals("Name can't be empty", ProfileNameValidator.problem("", List.of(), null));
        assertEquals("Name can't be empty", ProfileNameValidator.problem("   ", List.of(), null));
    }

    @Test
    void refusesOverlengthNames() {
        assertEquals("Name is too long",
                ProfileNameValidator.problem("x".repeat(ProfileNameValidator.MAX_NAME_LENGTH + 1), List.of(), null));
        assertNull(ProfileNameValidator.problem("x".repeat(ProfileNameValidator.MAX_NAME_LENGTH), List.of(), null));
    }

    @Test
    void refusesAnExactlyTakenName() {
        assertEquals("Name already used",
                ProfileNameValidator.problem("PvP", List.of("default", "PvP"), null));
    }

    @Test
    void theCurrentProfileNeverCollidesWithItself() {
        assertNull(ProfileNameValidator.problem("PvP", List.of("default", "PvP"), "PvP"));
    }

    @Test
    void refusesANameThatSanitizesOntoAnotherProfileFile() {
        // "A*B" is stored as "A_B.json" — the very file an existing "A_B"
        // profile already owns; the create would overwrite it.
        assertEquals("Name already used",
                ProfileNameValidator.problem("A*B", List.of("A_B"), null));
    }

    @Test
    void refusesARenameOntoItsOwnFileName() {
        // Renaming "A_B" to "A*B" writes "A_B.json" then deletes "A_B.json";
        // the validator stops the rename before the loss.
        assertEquals("Name already used",
                ProfileNameValidator.problem("A*B", List.of("A_B"), "A_B"));
    }

    @Test
    void sanitizeReplacesIllegalCharactersAndTrims() {
        assertEquals("A_B", ProfileNameValidator.sanitize(" A*B "));
        assertEquals("a_b_c", ProfileNameValidator.sanitize("a<b>c"));
        assertEquals("normal", ProfileNameValidator.sanitize("normal"));
    }
}
