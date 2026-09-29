package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Owner 2026-09-24: every village its own name, never twice in a world; the same id, the same name; Latin letters for English. */
class VillageNamesTest {
 @Test void fiveHundredVillagesNeverShareAName(){
  var taken=new HashSet<String>();var r=new Random(3);
  for(int i=0;i<500;i++){var n=VillageNames.unique(new UUID(r.nextLong(),r.nextLong()),taken);assertFalse(n.isBlank());assertTrue(taken.add(n),"Twice: "+n);}
  assertTrue(taken.contains("Малая "+VillageNames.plain().get(0))||taken.stream().anyMatch(n->n.startsWith("Мал")||n.startsWith("Боль")),"Past the plain names come Малая/Большая");
 }
 @Test void theSameVillageGetsTheSameName(){
  var id=UUID.fromString("0f3c2a1e-1111-4222-8333-444455556666");var taken=Set.of("Дубовка");
  assertEquals(VillageNames.unique(id,taken),VillageNames.unique(id,taken));
  assertNotEquals("Дубовка",VillageNames.unique(id,Set.copyOf(VillageNames.plain().subList(1,VillageNames.plain().size()))),"A taken name is never given");
 }
 @Test void namesAreRussianAndSpellInLatin(){
  var plain=VillageNames.plain();assertEquals(plain.size(),new HashSet<>(plain).size(),"No plain name twice");
  assertTrue(plain.contains("Берёзовка")&&plain.contains("Каменец")&&plain.contains("Медвежий Лог"));
  assertEquals("Beryozovka",VillageNames.en("Берёзовка"));assertEquals("Kamenets",VillageNames.en("Каменец"));assertEquals("Malaya Dubovka",VillageNames.en("Малая Дубовка"));
  assertArrayEquals(new String[]{"Малая","Большая"},VillageNames.size("Дубовка"));assertArrayEquals(new String[]{"Малое","Большое"},VillageNames.size("Сосново"));
  assertArrayEquals(new String[]{"Малые","Большие"},VillageNames.size("Липки"));assertArrayEquals(new String[]{"Малый","Большой"},VillageNames.size("Каменец"));
 }
}
