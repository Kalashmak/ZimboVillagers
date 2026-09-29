package org.villageastra.world;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
/** AD-121: the hall's stock cell - where it always was for the 7x7 hall, on the castle kit's first chest for a castle village. */
class HallSiteTest {
 @Test void theStockIsWhereTheDesignsKeepIt(){
  assertEquals(new BlockPos(1,1,4),HallSite.stock(BlockPos.ZERO),"The 7x7 hall's chest stays at (1,1,4)");
  var s=HallSite.castleStock(BlockPos.ZERO);var cell=new CastlePlan.Cell(s.getX()-HallSite.CASTLE_X,s.getY(),s.getZ()-HallSite.CASTLE_Z);
  for(int level=1;level<=6;level++){var spec=CastlePlan.plan(level).get(cell);assertNotNull(spec,"level "+level);assertTrue(spec.startsWith("chest"),"The castle stock is its kit's chest at level "+level+": "+spec);}
  assertEquals(CastlePlan.SEAL,CastlePlan.plan(2).get(new CastlePlan.Cell(-HallSite.CASTLE_X,1,-HallSite.CASTLE_Z)),"The village centre stands on the seal");
 }

 @Test void theCastleSheltersAreOpenFloorInsideTheHall(){
  for(var at:new int[][]{{-1,1,-2},{1,1,-3}}){int x=at[0]-HallSite.CASTLE_X,y=at[1],z=at[2]-HallSite.CASTLE_Z;
   assertTrue(x>9&&x<19&&z>18&&z<28,"Inside the hall walls: "+x+","+z);
   for(int level=1;level<=6;level++){var plan=CastlePlan.plan(level);
    for(int k=0;k<2;k++){var s=plan.get(new CastlePlan.Cell(x,y+k,z));assertTrue(s==null||s.equals("air"),"Open at level "+level+": "+x+","+(y+k)+","+z+" is "+s);}
    assertNotNull(plan.get(new CastlePlan.Cell(x,0,z)),"A floor under it at level "+level);}}
 }
}
