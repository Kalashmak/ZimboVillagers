package org.villageastra.domain;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
/** AD-121: a castle village (CASTLE_LOTS) lays its 37x37 castle with the centre on the seal and rings its six starter lots round it,
 *  every lot inside the wider reach (64), none overlapping; the older layouts keep their reach of 47. */
class CastleLotsTest {
 @Test void theCastleVillageFitsItsWiderPiece(){
  assertEquals(47,OrganicLots.reach(OrganicLots.BARN_LOTS));assertEquals(64,OrganicLots.reach(OrganicLots.CASTLE_LOTS));
  int redrawn=0;
  for(int i=0;i<200;i++){var id=new UUID(0x5eedL*i+17,0xca57L^(i*31L));int salt=OrganicLots.redraws(id,OrganicLots.CASTLE_LOTS);assertTrue(salt>=0,"village "+i+" allocates");if(salt>0)redrawn++;
   var lots=OrganicLots.buildings(id,OrganicLots.CASTLE_LOTS);var hall=lots.get(0);
   assertEquals("town_hall",hall.type());assertEquals(OrganicLots.CASTLE_X,hall.x());assertEquals(OrganicLots.CASTLE_Z,hall.z());
   for(var a:lots){assertTrue(OrganicLots.inside(a,OrganicLots.CASTLE_LOTS),a.type()+" inside 64 in village "+i);
    for(var b:lots)if(a!=b)assertFalse(OrganicLots.overlaps(a,b,OrganicLots.CASTLE_LOTS),a.type()+" overlaps "+b.type()+" in village "+i);}}
  assertTrue(redrawn<100,"Most castle villages fit at the first draw: "+redrawn+" of 200 redrawn");
 }
}
