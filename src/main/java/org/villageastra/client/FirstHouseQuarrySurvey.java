package org.villageastra.client;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;

/** Read-only diagnostic for the observed first-house expedition; never selects or changes a job. */
final class FirstHouseQuarrySurvey {
 private static boolean done;
 static void sample(ServerLevel l,SettlementData.Entry e){
  if(done)return;
  for(var r:e.settlement().residents())if(r.profession()==Profession.MINER&&l.getEntity(r.id()) instanceof ResidentEntity n&&n.workStatus().equals("mine_floor")&&SurfaceQuarry.mayStart(l,e,n)){
   done=true;int exposed=0,stands=0,reached=0,reversible=0;var examples=new ArrayList<String>();
   for(int x=-96;x<=96;x++)for(int z=-96;z<=96;z++){
    var column=e.center().offset(x,0,z);if(!l.hasChunkAt(column))continue;int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
    for(int y=top;y>=Math.max(l.getMinBuildHeight(),top-8);y--){var p=new BlockPos(column.getX(),y,column.getZ());
     if(!l.getBlockState(p).is(Blocks.ANDESITE)||!SurfaceQuarry.safe(l,p))continue;exposed++;
     for(var d:Direction.Plane.HORIZONTAL)for(int dy=0;dy<=2;dy++){
      var feet=p.relative(d).above(dy);if(!HarvestAccess.standing(l,feet,p))continue;stands++;
      var path=n.routeTo(feet,0,NaturalSupplyGoal.ROUTE_RANGE);if(path!=null&&path.canReach())reached++;
      if(HarvestAccess.reversible(path))reversible++;
      if(examples.size()<8){int drop=0;if(path!=null)for(int i=1;i<path.getNodeCount();i++)drop=Math.max(drop,Math.abs(path.getNode(i).y-path.getNode(i-1).y));examples.add(p+" stand="+feet+" reached="+(path!=null&&path.canReach())+" maxStep="+drop+" tool="+SurfaceQuarry.tool(l,e,l.getBlockState(p)));}
     }
    }
   }
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE quarrySurvey worker={} exposed={} stands={} reached={} reversible={} examples={}",n.position(),exposed,stands,reached,reversible,examples);return;
  }
 }
}
