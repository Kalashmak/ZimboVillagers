package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CivilizationTest {
 @Test void levelsGateResearchAndRequireActualLabor(){
  var c=new Civilization();assertThrows(IllegalArgumentException.class,()->c.begin("medicine"));
  c.begin("ironworking");assertFalse(c.work(16000,false,true));assertFalse(c.work(16000,true,false));assertEquals(0,c.progress());
  assertTrue(c.work(16000,true,true));assertThrows(IllegalArgumentException.class,()->c.begin("medicine"));
  c.completedHallUpgrade(2);c.begin("medicine");c.work(80,true,true);
  var copy=Civilization.restore(c.level(),c.completed(),c.active(),c.progress());assertEquals(80,copy.progress());assertEquals("medicine",copy.active());
  assertThrows(IllegalArgumentException.class,()->copy.completedHallUpgrade(2));
 }
 @Test void rejectsCorruptResearch(){assertThrows(IllegalArgumentException.class,()->Civilization.restore(1,List.of("siege"),"",0));assertThrows(IllegalArgumentException.class,()->Civilization.restore(2,List.of("medicine"),"",0));}
}
