package models;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoleTest {
    @Test
    public void adminImpliesEveryRole() {
        for (Role role : Role.values()) assertTrue(Role.ADMIN.grants(role));
    }

    @Test
    public void otherRolesGrantOnlyThemselves() {
        assertTrue(Role.NEWS.grants(Role.NEWS));
        assertFalse(Role.NEWS.grants(Role.CLANWARS));
        assertFalse(Role.LINEUP.grants(Role.ADMIN));
    }

    @Test
    public void memberHasRolesOfTheirEntriesOnly() {
        User member = new User();
        assertFalse(member.hasRole(Role.NEWS));

        member.setRoles(List.of(entry(Role.NEWS), entry(Role.QUAKE3)));
        assertTrue(member.hasRole(Role.NEWS));
        assertTrue(member.hasRole(Role.QUAKE3));
        assertFalse(member.hasRole(Role.MEDIA));
        assertFalse(member.hasRole(Role.ADMIN));
    }

    @Test
    public void onlyActiveMembersWithPasswordCanLogin() {
        User member = new User();
        member.setActive(true);
        assertFalse(member.canLogin());
        member.setPasswordHash("$2a$12$hash");
        assertTrue(member.canLogin());
        member.setActive(false);
        assertFalse(member.canLogin());
    }

    private static UserRole entry(Role role) {
        UserRole entry = new UserRole();
        entry.setRole(role);
        return entry;
    }
}
