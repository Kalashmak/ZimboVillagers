package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
class NaturalLayoutTest {
 @Test void localTerrainHeightsPreserveIdentitiesAndWorkplaces(){
  UUID id=new UUID(51,97);int[] ys={0,-3,1,-1,2,-4,0};var flat=Settlement.natural(id);var sloped=Settlement.natural(id,ys);
  var a=List.copyOf(flat.buildings());var b=List.copyOf(sloped.buildings());
  for(int i=0;i<7;i++){assertEquals(a.get(i).id(),b.get(i).id());assertEquals(ys[i],b.get(i).y());}
  for(var resident:flat.residents())assertEquals(flat.workplace(resident.id()).id(),sloped.workplace(resident.id()).id());
  assertThrows(IllegalArgumentException.class,()->Settlement.natural(id,new int[]{0}));
 }

 @Test void irregularLotsAreStableAndKeepThreeBlockSpacing(){
  for(int seed=0;seed<100;seed++){
   UUID id=new UUID(seed,seed*17L);var a=Settlement.natural(id);var b=Settlement.natural(id);
   assertEquals(List.copyOf(a.buildings()),List.copyOf(b.buildings()));assertEquals(7,a.buildings().size());assertEquals(6,a.residents().size());
   assertTrue(a.buildings().stream().map(Settlement.Building::z).distinct().count()>=4);
   var lots=List.copyOf(a.buildings());
   for(int i=0;i<lots.size();i++)for(int j=i+1;j<lots.size();j++){
    var x=lots.get(i);var y=lots.get(j);int wx=x.type().equals("forester")?9:7,wy=y.type().equals("forester")?9:7;
    int dx=x.type().equals("forester")?17:x.type().equals("farm")?16:7,dy=y.type().equals("forester")?17:y.type().equals("farm")?16:7;
    int gapX=Math.max(y.x()-(x.x()+wx),x.x()-(y.x()+wy));int gapZ=Math.max(y.z()-(x.z()+dx),x.z()-(y.z()+dy));
    assertTrue(Math.max(gapX,gapZ)>=3,"Lots keep at least three free blocks");
   }
  }
 }
}
