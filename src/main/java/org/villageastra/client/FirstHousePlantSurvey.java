package org.villageastra.client;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.villageastra.domain.Profession;
import org.villageastra.server.SettlementData;
import org.villageastra.world.*;
/** Read-only: routeTo uses its own navigation and never changes the worker's actual route. */
final class FirstHousePlantSurvey {
 private static boolean done;
 static void sample(ServerLevel l,SettlementData.Entry e){
  if(done)return;
  for(var r:e.settlement().residents())if(r.profession()==Profession.MINER&&l.getEntity(r.id()) instanceof ResidentEntity n&&n.onGround()&&!n.isSleeping()&&n.workStatus().equals("mine_floor")){
   done=true;var plants=new ArrayList<BlockPos>();
   for(int x=-192;x<=192;x++)for(int z=-192;z<=192;z++){var column=e.center().offset(x,0,z);if(!l.hasChunkAt(column))continue;int top=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
    for(int y=top;y<=top+3;y++){var p=new BlockPos(column.getX(),y,column.getZ());if(l.getBlockState(p).is(Blocks.SUGAR_CANE)&&!l.getBlockState(p.above()).is(Blocks.SUGAR_CANE))plants.add(p);}
   }
   plants.sort(Comparator.comparingDouble(p->p.distSqr(e.center())));boolean wanted=NaturalSupplyGoal.demand(l,e).contains(net.minecraft.world.item.Items.SUGAR_CANE);
   for(var p:plants.stream().limit(4).toList()){var examples=new ArrayList<String>();
    for(var d:Direction.Plane.HORIZONTAL)for(int dy=-2;dy<=2;dy++){var feet=p.relative(d).above(dy);if(!HarvestAccess.standing(l,feet,p))continue;var path=n.routeTo(feet,0,NaturalSupplyGoal.ROUTE_RANGE,4F);int step=0;
     if(path!=null)for(int i=1;i<path.getNodeCount();i++)step=Math.max(step,Math.abs(path.getNode(i).y-path.getNode(i-1).y));
     var wide=n.routeTo(feet,0,NaturalSupplyGoal.ROUTE_RANGE);
     examples.add(feet+" expandedReached="+(wide!=null&&wide.canReach())+" expandedReversible="+HarvestAccess.reversible(wide)+" expandedNodes="+(wide==null?0:wide.getNodeCount())+" reached="+(path!=null&&path.canReach())+" maxStep="+step+" nodes="+(path==null?0:path.getNodeCount())+" end="+(path==null?null:path.getEndNode()));
    }
    com.mojang.logging.LogUtils.getLogger().info("ASTRA_FIRST_HOUSE plantSurvey from={} target={} wanted={} safe={} maxUpStep={} approaches={}",n.blockPosition(),p,wanted,NaturalSupplyGoal.safe(l,p),n.maxUpStep(),examples);
   }return;
  }
 }
}
