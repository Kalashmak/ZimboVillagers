package org.villageastra.client;
import com.mojang.logging.LogUtils;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.*;
import org.villageastra.server.*;
import org.villageastra.world.*;
/** AD-088..AD-091: the new quest sites stand in real generated ground, the fall of a working is dug out with a pickaxe, the tower's brazier
 *  is lit with a real flint and steel, a crop quest opens carrots for the village, a real structure of the world is explored in person and
 *  far land walked through becomes a lead. */
final class WildProbe {
 private static final String[] KINDS={QuestSites.HIDEOUT,QuestSites.COLLAPSE,QuestSites.DEN,QuestSites.TOWER};
 private static int phase,ticks,built,sector,settle,walked;
 private static volatile String failure,progress="",fit="";
 private static volatile UUID collapse,tower,ruin,survey;
 private static volatile boolean ready,dug,lit,opened,explored,surveyed,shot;
 private static final Map<String,UUID> SITES=new HashMap<>();
 static boolean enabled(){return Boolean.getBoolean("villageastra.wildSmoke");}
 private static void capture(Minecraft mc,String suffix)throws Exception{
  var path=java.nio.file.Path.of("../docs/runs/"+mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName()+"-wild-"+suffix+".png");
  try(var img=Screenshot.takeScreenshot(mc.getMainRenderTarget())){img.writeToFile(path);}
  LogUtils.getLogger().info("ASTRA_WILD screenshot {}",path);
 }
 private static SettlementData.Entry entry(net.minecraft.server.MinecraftServer s){return SettlementData.get(s).entries().iterator().next();}
 private static net.minecraft.server.level.ServerPlayer player(net.minecraft.server.MinecraftServer s){return s.getPlayerList().getPlayers().get(0);}
 private static int count(net.minecraft.server.level.ServerLevel l,BlockPos o,int half,int from,int to,net.minecraft.world.level.block.Block block){
  int found=0;for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++)for(int dy=from;dy<=to;dy++)if(l.getBlockState(o.offset(dx,dy,dz)).is(block))found++;return found;
 }
 private static boolean at(net.minecraft.server.level.ServerLevel l,SettlementData.Entry e,net.minecraft.server.level.ServerPlayer p,int s){
  var target=Expeditions.target(l,e,s%Expeditions.SECTORS);
  if(p.distanceToSqr(target.getX(),target.getY(),target.getZ())>256){
   p.teleportTo(l,target.getX()+.5,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,target.getX(),target.getZ()),target.getZ()+.5,0,0);settle=0;return false;}
  return ++settle>=3;
 }
 private static QuestSites.Order order(String kind){
  return switch(kind){
   case QuestSites.HIDEOUT->new QuestSites.Order(kind,0,5,0,Blocks.AIR,new ItemStack(Items.IRON_INGOT,4),ItemStack.EMPTY);
   case QuestSites.COLLAPSE->new QuestSites.Order(kind,2,0,0,Blocks.AIR,ItemStack.EMPTY,ItemStack.EMPTY);
   case QuestSites.DEN->new QuestSites.Order(kind,0,1,0,Blocks.AIR,ItemStack.EMPTY,ItemStack.EMPTY);
   default->new QuestSites.Order(kind,0,0,0,Blocks.AIR,ItemStack.EMPTY,ItemStack.EMPTY);
  };
 }
 static void tick(Minecraft mc){try{
  if(failure!=null)throw new IllegalStateException(failure);
  if(++ticks>20000)throw new IllegalStateException("Wild timeout phase="+phase+" "+progress);
  var server=mc.getSingleplayerServer();
  if(phase==0&&ticks>60){phase=1;ticks=0;server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);
    server.setDifficulty(net.minecraft.world.Difficulty.EASY,true);
    l.setDayTime(1000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,server);
    for(var x:e.settlement().residents())if(l.getEntity(x.id()) instanceof ResidentEntity other)other.setNoAi(true);
    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.getInventory().clearContent();
    p.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));
    p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.setItemSlot(EquipmentSlot.FEET,new ItemStack(Items.DIAMOND_BOOTS));
    LogUtils.getLogger().info("ASTRA_WILD fixture: easy difficulty held in daylight, armour");ready=true;}catch(Exception ex){failure=ex.toString();}});}
  // The four new designs, each at a sector of real ground; the site of the fall and of the tower are kept for their deeds.
  else if(phase==1&&ready&&ticks%20==0){final String kind=KINDS[Math.min(built,KINDS.length-1)];final int s=sector;
   server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);if(!at(l,e,p,s))return;
    var id=UUID.randomUUID();var site=QuestSites.build(l,e,id,order(kind),p.blockPosition(),SettlementData.get(server).clock().ticks());
    if(site==null){fit+=kind+"@"+s+"=no-room ";sector=s+1;return;}
    var o=QuestSites.origin(site);int half=QuestSites.half(kind);
    String checked=switch(kind){
     case QuestSites.HIDEOUT->"logs="+count(l,o,half+1,0,0,Blocks.SPRUCE_LOG)+" roof="+count(l,o,half+1,1,1,Blocks.SPRUCE_PLANKS)+" band="+site.getList("mobs",11).size();
     case QuestSites.COLLAPSE->"gravel="+count(l,o,half+1,0,1,Blocks.GRAVEL)+" rails="+count(l,o,half+1,0,0,Blocks.RAIL)+" trapped="+QuestSites.people(site).size();
     case QuestSites.DEN->"bones="+count(l,o,half+1,0,0,Blocks.BONE_BLOCK)+" beast="+site.hasUUID("chief");
     default->"bricks="+count(l,o,half+1,0,4,Blocks.STONE_BRICKS)+" ladder="+count(l,o,half+1,0,4,Blocks.LADDER);
    };
    boolean whole=switch(kind){
     case QuestSites.HIDEOUT->site.getList("mobs",11).size()>=3&&count(l,o,half+1,1,1,Blocks.SPRUCE_PLANKS)>=30;
     case QuestSites.COLLAPSE->count(l,o,half+1,0,1,Blocks.GRAVEL)==4&&QuestSites.people(site).size()==2;
     case QuestSites.DEN->site.hasUUID("chief")&&count(l,o,half+1,0,0,Blocks.BONE_BLOCK)>=3;
     default->count(l,o,half+1,0,4,Blocks.STONE_BRICKS)>=70&&count(l,o,half+1,0,4,Blocks.LADDER)==5;
    };
    if(!whole){failure="The "+kind+" did not come out whole: "+checked;return;}
    fit+=kind+"@"+s+"=ok ";SITES.put(kind,id);built++;sector=s+1;
    LogUtils.getLogger().info("ASTRA_WILD built the {} in real ground at {}: {}",kind,o.toShortString(),checked);
   }catch(Exception ex){failure=ex.toString();}});
   if(sector>24)throw new IllegalStateException("No room for the wild sites: "+fit);
   if(built>=KINDS.length){collapse=SITES.get(QuestSites.COLLAPSE);tower=SITES.get(QuestSites.TOWER);phase=2;ticks=0;}}
  // The fall is dug out with a real pickaxe, top cells first, until the prospectors are free to walk.
  else if(phase==2&&ticks%10==0){server.execute(()->{try{
    var l=server.overworld();var p=player(server);var site=QuestSites.site(l,collapse);
    var plug=new ArrayList<BlockPos>();for(var raw:site.getList("seal",4))plug.add(BlockPos.of(((net.minecraft.nbt.LongTag)raw).getAsLong()));
    plug.sort(Comparator.comparingInt((BlockPos c)->c.getY()).reversed());
    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SHOVEL));
    for(var cell:plug)if(!l.getBlockState(cell).isAir()){p.teleportTo(l,cell.getX()+.5,cell.getY()-(cell.getY()>plug.get(plug.size()-1).getY()?1:0),cell.getZ()+.5,0,0);p.gameMode.destroyBlock(cell);return;}
    if(WildSites.dugOut(l,site)&&QuestSites.people(site).stream().allMatch(id->l.getEntity(id) instanceof ResidentEntity npc&&Adventures.hold(npc).isEmpty()))dug=true;
   }catch(Exception ex){failure=ex.toString();}});
   if(dug){LogUtils.getLogger().info("ASTRA_WILD the fall was dug out with a real shovel and the prospectors are free to walk");phase=3;ticks=0;}
   else if(ticks>1200)throw new IllegalStateException("The fall was not dug out");}
  // The tower: its chest filled, the parapet up, and the brazier lit by a real click with flint and steel.
  else if(phase==3&&ticks==20){server.execute(()->{try{
    var l=server.overworld();var p=player(server);var site=QuestSites.site(l,tower);
    var chest=(Container)l.getBlockEntity(BlockPos.of(site.getLong("chest")));chest.setItem(0,new ItemStack(Items.STONE_BRICKS,24));chest.setItem(1,new ItemStack(Items.COAL,8));
    if(!WildSites.finishTower(l,site,24,8)){failure="The supplied tower did not rise";return;}QuestSites.save(l,tower,site);
    var brazier=BlockPos.of(site.getLong("brazier"));
    p.teleportTo(l,brazier.getX()+1.5,brazier.getY()-1+1,brazier.getZ()+.5,90,45);
    p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(brazier));
    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.FLINT_AND_STEEL));
    LogUtils.getLogger().info("ASTRA_WILD the supplied tower raised its parapet: walls={}",count(l,QuestSites.origin(site),4,5,5,Blocks.STONE_BRICK_WALL));}catch(Exception ex){failure=ex.toString();}});}
  else if(phase==3&&ticks>60&&ticks%20==0){
   var site=QuestSites.site(server.overworld(),tower);var brazier=BlockPos.of(site.getLong("brazier"));
   if(!lit)mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(brazier).add(0,.4,0),Direction.UP,brazier,false));
   server.execute(()->{if(WildSites.lit(server.overworld(),QuestSites.site(server.overworld(),tower)))lit=true;});
   if(lit&&!shot){capture(mc,"tower");shot=true;LogUtils.getLogger().info("ASTRA_WILD a real flint and steel lit the brazier of the tower");phase=4;ticks=0;}
   else if(ticks>600)throw new IllegalStateException("The brazier did not light");}
  // A crop quest from the board: sixteen real carrots handed over at the stock open carrots for the village.
  else if(phase==4&&ticks==20){server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);var now=SettlementData.get(server).clock().ticks();
    var q=CropQuests.post(l,e,now);if(q==null){failure="No crop quest was posted";return;}
    if(!Quests.take(p,e.settlement().id(),q.getUUID("id")).equals("ok")){failure="The crop quest could not be taken";return;}
    var kind=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(q.getString("item")));
    p.getInventory().add(new ItemStack(kind,q.getInt("target")));
    var chest=LogisticsRoutes.position(e,Workshops.hall(e));p.teleportTo(l,chest.getX()+.5,chest.getY(),chest.getZ()+1.5,0,0);
    var result=Quests.handOver(p,e.settlement().id(),q.getUUID("id"));
    if(result.equals("ok")&&CropUnlocks.unlocked(l,e,q.getString("item"))){opened=true;
     LogUtils.getLogger().info("ASTRA_WILD a crop quest opened {} for the village: {} handed over at the stock",q.getString("item"),q.getInt("target"));}
    else failure="The crop quest did not open its kind: "+result;
   }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==4&&opened){phase=5;ticks=0;}
  // A real structure of the world: the player stands in one of its pieces and the village's own clock sees it.
  else if(phase==5&&ticks==20){server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);var now=SettlementData.get(server).clock().ticks();
    var q=Wilds.post(l,e,Wilds.RUIN,now);if(q==null){failure="No structure of the world within reach";return;}
    ruin=q.getUUID("id");PropertyLedger.get(server).gift(e.settlement().id(),p.getUUID(),Quests.trust(Wilds.RUIN));Quests.take(p,e.settlement().id(),ruin);
    var structure=l.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(q.getString("structure")));
    var site=BlockPos.of(q.getLong("site"));var start=l.getChunk(site.getX()>>4,site.getZ()>>4,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
    var inside=start.getPieces().get(0).getBoundingBox().getCenter();
    l.getChunk(inside.getX()>>4,inside.getZ()>>4);p.teleportTo(l,inside.getX()+.5,inside.getY(),inside.getZ()+.5,0,0);
    LogUtils.getLogger().info("ASTRA_WILD the board sent the player to {} at {}",q.getString("kind"),site.toShortString());
   }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==5&&ticks>40&&ticks%20==0){server.execute(()->{var q=Quests.quest(server.overworld(),entry(server).settlement().id(),ruin);if(q!=null&&q.getString("state").equals(Quests.DONE))explored=true;});
   if(explored){capture(mc,"ruin");LogUtils.getLogger().info("ASTRA_WILD standing in the structure explored it and the village paid");phase=6;ticks=0;}
   else if(ticks>1200)throw new IllegalStateException("The ruin was not counted as explored");}
  // Far land: the player walks through six of its nine chunks, and the village writes down a new lead there.
  else if(phase==6&&ticks==20){server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);var now=SettlementData.get(server).clock().ticks();
    var q=Wilds.post(l,e,Wilds.SURVEY,now);if(q==null){failure="No far land to survey";return;}
    survey=q.getUUID("id");Quests.take(p,e.settlement().id(),survey);
   }catch(Exception ex){failure=ex.toString();}});}
  else if(phase==6&&ticks>40&&ticks%120==0){server.execute(()->{try{
    var l=server.overworld();var e=entry(server);var p=player(server);var q=Quests.quest(l,e.settlement().id(),survey);
    if(q.getString("state").equals(Quests.DONE)){surveyed=true;return;}
    var region=q.getList("region",4);if(walked>=region.size())return;
    var chunk=new net.minecraft.world.level.ChunkPos(((net.minecraft.nbt.LongTag)region.get(walked)).getAsLong());l.getChunk(chunk.x,chunk.z);
    int x=chunk.getMiddleBlockX(),z=chunk.getMiddleBlockZ();p.teleportTo(l,x+.5,l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z),z+.5,0,0);walked++;
    progress="walked="+walked+" progress="+q.getInt("progress")+"/"+q.getInt("target");
   }catch(Exception ex){failure=ex.toString();}});
   if(surveyed){var lead=Expeditions.leads(server.overworld(),entry(server).settlement().id()).stream().filter(x->x.getInt("sector")>=8).findFirst().orElse(null);
    if(lead==null)throw new IllegalStateException("The survey wrote down no lead");
    LogUtils.getLogger().info("ASTRA_WILD VERIFIED new sites in real ground: {}; the fall dug out, the tower lit, a crop opened, a real structure explored, far land walked into lead sector {} ({}); reload=false",fit,lead.getInt("sector"),lead.getString("kind"));
    mc.stop();phase=7;}
   else if(ticks>4000)throw new IllegalStateException("The survey did not finish: "+progress);}
 }catch(Exception ex){LogUtils.getLogger().error("ASTRA_WILD FAILED",ex);Minecraft.getInstance().stop();}}
}
