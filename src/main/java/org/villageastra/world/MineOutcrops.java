package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;

/** An ore face visible from a claimed, open gallery is a reason to survey its adjacent row. */
public final class MineOutcrops {
 private MineOutcrops(){}
 public static boolean wanted(BlockState s,Set<Item> demand){
  if(demand.contains(s.getBlock().asItem()))return true;
  return s.is(BlockTags.IRON_ORES)&&demand.contains(Items.RAW_IRON)
   ||s.is(BlockTags.COPPER_ORES)&&demand.contains(Items.RAW_COPPER)
   ||s.is(BlockTags.GOLD_ORES)&&demand.contains(Items.RAW_GOLD)
   ||s.is(BlockTags.COAL_ORES)&&demand.contains(Items.COAL)
   ||s.is(BlockTags.REDSTONE_ORES)&&demand.contains(Items.REDSTONE)
   ||s.is(BlockTags.LAPIS_ORES)&&demand.contains(Items.LAPIS_LAZULI)
   ||s.is(BlockTags.DIAMOND_ORES)&&demand.contains(Items.DIAMOND);
 }
 public static int floor(ServerLevel l,SettlementData.Entry e,Settlement.Building mine,MineArea area,int limit,Set<Integer> visited,Set<Item> demand){
  int best=-1,height=MineDrive.Shape.galleryHeight(area.height());
  for(var g:area.galleries())for(int dz:new int[]{-1,1}){
   int row=g.step()+dz;if(row<0||row>limit||visited.contains(row)||row<=best)continue;
   int edge=g.side()==MineDrive.EAST?(area.width()==1?4:5):(area.width()==1?2:1),dx=g.side()==MineDrive.EAST?1:-1;
   for(int run=0;run<g.length();run++)for(int h=0;h<height;h++){
    int y=-g.step()-area.descent()+h;
    if(y<-row-area.descent()||y>=-row-area.descent()+height)continue;
    var open=BuildingPlacement.at(e,mine,edge+dx*run,y,7+g.step());var rock=BuildingPlacement.at(e,mine,edge+dx*run,y,7+row);
    if(!l.hasChunkAt(open)||!l.hasChunkAt(rock)||!l.getBlockState(open).isAir()||!l.getFluidState(open).isEmpty())continue;
    if(wanted(l.getBlockState(rock),demand)){best=row;break;}
   }
  }
  return best;
 }
}
