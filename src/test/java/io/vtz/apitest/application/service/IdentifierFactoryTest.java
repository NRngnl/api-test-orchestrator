package io.vtz.apitest.application.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentifierFactoryTest {
    @Test
    void generatesUuidBackedHexStrings() {
        IdentifierFactory factory = new IdentifierFactory();

        String value = factory.randomString(16);

        assertEquals(16, value.length());
        assertTrue(value.matches("[0-9a-f]{16}"));
    }

    @Test
    void rejectsLengthsLongerThanUuidWithoutDashes() {
        IdentifierFactory factory = new IdentifierFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.randomString(33));
    }
}
