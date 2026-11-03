package com.economofar.api.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Tag("unit")
class PasswordPolicyTest {

    private static final String EMAIL = "jean.dupont@mail.com";

    @Test
    @DisplayName("a password with letters and digits is accepted")
    void goodPassword() {
        assertNull(PasswordPolicy.weakness("Tr0ubadour-92", EMAIL));
        assertNull(PasswordPolicy.weakness("abcdefg1", EMAIL));
        assertNull(PasswordPolicy.weakness("1abcdefg", EMAIL));
    }

    @Test
    @DisplayName("a password without any digit is refused")
    void noDigit() {
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("abcdefghij", EMAIL));
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("Correct-Horse-Battery", EMAIL));
    }

    @Test
    @DisplayName("a password without any letter is refused")
    void noLetter() {
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("1234567890", EMAIL));
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("12-34-56-78", EMAIL));
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("        ", EMAIL));
    }

    @Test
    @DisplayName("an empty or missing password is refused")
    void emptyPassword() {
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("", EMAIL));
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness(null, EMAIL));
    }

    @Test
    @DisplayName("accented letters count as letters")
    void accentedLetters() {
        assertNull(PasswordPolicy.weakness("\u00e9t\u00e9\u00e9t\u00e91", EMAIL));
        assertNull(PasswordPolicy.weakness("\u00c9COLE2024", EMAIL));
    }

    @Test
    @DisplayName("the most common passwords are refused, whatever the case")
    void commonPasswords() {
        assertEquals(PasswordPolicy.TOO_COMMON, PasswordPolicy.weakness("password1", EMAIL));
        assertEquals(PasswordPolicy.TOO_COMMON, PasswordPolicy.weakness("Password123", EMAIL));
        assertEquals(PasswordPolicy.TOO_COMMON, PasswordPolicy.weakness("AZERTY123", EMAIL));
        assertEquals(PasswordPolicy.TOO_COMMON, PasswordPolicy.weakness("P@ssw0rd", EMAIL));
        assertEquals(PasswordPolicy.TOO_COMMON, PasswordPolicy.weakness("MotDePasse1", EMAIL));
    }

    @Test
    @DisplayName("a common password with something added is accepted")
    void commonPasswordWithMore() {
        assertNull(PasswordPolicy.weakness("password1-for-economofar", EMAIL));
    }

    @Test
    @DisplayName("a password that holds the part of the e-mail before the at sign is refused")
    void containsTheEmail() {
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("jean.dupont2024", EMAIL));
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("MyJean.Dupont9", EMAIL));
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("JEAN.DUPONT-7", EMAIL));
    }

    @Test
    @DisplayName("a short part before the at sign is not held against the password, but the whole address is")
    void shortLocalPart() {
        assertNull(PasswordPolicy.weakness("abcdefg1xyz", "abc@mail.com"));
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("ab1@mail.com", "ab1@mail.com"));
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("AB1@MAIL.COM", "ab1@mail.com"));
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("abcd1234xyz", "abcd@mail.com"));
    }

    @Test
    @DisplayName("a missing e-mail does not break the check")
    void missingEmail() {
        assertNull(PasswordPolicy.weakness("Tr0ubadour-92", null));
        assertNull(PasswordPolicy.weakness("Tr0ubadour-92", "   "));
        assertNull(PasswordPolicy.weakness("Tr0ubadour-92", "no-at-sign"));
    }

    @Test
    @DisplayName("the e-mail check ignores spaces around the address")
    void emailWithSpaces() {
        assertEquals(PasswordPolicy.CONTAINS_EMAIL, PasswordPolicy.weakness("jean.dupont2024", "  jean.dupont@mail.com  "));
    }

    @Test
    @DisplayName("the rules are checked in order: letter and digit, then common, then e-mail")
    void ruleOrder() {
        assertEquals(PasswordPolicy.NEEDS_LETTER_AND_DIGIT, PasswordPolicy.weakness("jean.dupont-mail", EMAIL));
        assertEquals(PasswordPolicy.TOO_COMMON, PasswordPolicy.weakness("password1", "password1@mail.com"));
    }

    @Test
    @DisplayName("holding only a piece of the part before the at sign is not enough to be refused")
    void pieceOfTheLocalPart() {
        assertNull(PasswordPolicy.weakness("ean.dupont2024", EMAIL));
    }

    @Test
    @DisplayName("an address without a part before the at sign holds nothing against the password")
    void noLocalPart() {
        assertNull(PasswordPolicy.weakness("zz@mail.com1", "@mail.com"));
    }

    @Test
    @DisplayName("BCrypt reads 72 bytes at most, so the length is counted in bytes")
    void lengthInBytes() {
        String accented = "\u00e9".repeat(35);

        assertNull(PasswordPolicy.weakness(accented + "1", EMAIL));
        assertNull(PasswordPolicy.weakness(accented + "12", EMAIL));
        assertEquals(PasswordPolicy.TOO_LONG, PasswordPolicy.weakness(accented + "123", EMAIL));
        assertEquals(PasswordPolicy.TOO_LONG, PasswordPolicy.weakness("\u00e9".repeat(40) + "1", EMAIL));
        assertNull(PasswordPolicy.weakness("a".repeat(71) + "1", EMAIL));
        assertEquals(PasswordPolicy.TOO_LONG, PasswordPolicy.weakness("a".repeat(72) + "1", EMAIL));
    }

    @Test
    @DisplayName("a password that is too long in bytes is reported before the other rules")
    void tooLongComesFirst() {
        assertEquals(PasswordPolicy.TOO_LONG, PasswordPolicy.weakness("\u00e9".repeat(40), EMAIL));
    }

    @Test
    @DisplayName("letters outside the basic plane count as letters")
    void rareLetters() {
        String rare = "\ud835\udc9c\ud835\udc9c\ud835\udc9c\ud835\udc9c1";

        assertNull(PasswordPolicy.weakness(rare, EMAIL));
    }
}
