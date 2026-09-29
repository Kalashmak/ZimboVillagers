package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ResidentProfileTest {
    @Test void namesAndSkinsAreStableAndVaried() {
        var names=new HashSet<String>();var skins=new HashSet<Integer>();
        for(int n=0;n<100;n++) {
            UUID id=UUID.nameUUIDFromBytes(("person/"+n).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var profile=ResidentProfile.generate(id);assertEquals(profile,ResidentProfile.generate(id));
            names.add(profile.name());skins.add(profile.skin());
        }
        assertTrue(names.size()>70);assertEquals(9,skins.size());
    }
    @Test void changingProfessionCannotRenamePerson() {
        var person=Settlement.initial(UUID.randomUUID()).residents().iterator().next();
        var before=person.profile();person.assign(Profession.BUILDER);assertEquals(before,person.profile());
    }
}
