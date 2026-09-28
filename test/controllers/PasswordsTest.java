package controllers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PasswordsTest {
    @Test
    public void verifiesOnlyTheOriginalPassword() {
        String hash = Passwords.hash("correct horse battery");

        assertTrue(Passwords.verify("correct horse battery", hash));
        assertFalse(Passwords.verify("correct horse batterY", hash));
        assertFalse(Passwords.verify(" correct horse battery", hash));
    }

    @Test
    public void rejectsMissingOrMalformedHashes() {
        assertFalse(Passwords.verify("anything", null));
        assertFalse(Passwords.verify("anything", ""));
        assertFalse(Passwords.verify("anything", "not-a-bcrypt-hash"));
        assertFalse(Passwords.verify(null, Passwords.hash("something long")));
    }

    @Test
    public void dummyCheckRunsWithoutFailing() {
        Passwords.verifyDummy("whatever");
        Passwords.verifyDummy(null);
    }

    @Test
    public void enforcesThePasswordPolicy() {
        assertNotNull(Passwords.validate("short", "short", "nick"));
        assertNotNull(Passwords.validate("PlayerOne1", "PlayerOne1", "playerone1"));
        assertNotNull(Passwords.validate("long enough", "long enougH", "nick"));
        assertNull(Passwords.validate("long enough", "long enough", "nick"));
    }

    @Test
    public void rejectsPasswordsBeyondTheBcryptLimit() {
        String ascii72 = "a".repeat(72);
        assertNull(Passwords.validate(ascii72, ascii72, "nick"));
        String ascii73 = "a".repeat(73);
        assertNotNull(Passwords.validate(ascii73, ascii73, "nick"));
        // 25 umlauts are 50 bytes, 37 are 74: the limit is in bytes, not characters.
        String umlauts = "ä".repeat(37);
        assertNotNull(Passwords.validate(umlauts, umlauts, "nick"));
    }

    @Test
    public void tokensAreRandomAndHashedDeterministically() {
        String first = Passwords.newToken();
        String second = Passwords.newToken();

        assertNotEquals(first, second);
        assertEquals(43, first.length());
        assertTrue(first.matches("[A-Za-z0-9_-]+"));
        assertEquals(Passwords.tokenHash(first), Passwords.tokenHash(first));
        assertEquals(64, Passwords.tokenHash(first).length());
        assertNotEquals(Passwords.tokenHash(first), Passwords.tokenHash(second));
    }
}
