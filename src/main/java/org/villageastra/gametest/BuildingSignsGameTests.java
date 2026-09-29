package org.villageastra.gametest;
import java.util.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.Settlement;
import org.villageastra.server.*;
import org.villageastra.world.*;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class BuildingSignsGameTests {
 @GameTest(template="empty",batch="sign_farm_chain",timeoutTicks=300) public static void buildingSignsFarmUpgradesKeepOwnedLabel(GameTestHelper h){
  var t=FarmBarnGameTests.yard(h);
  try{for(int level=1;level<=3;level++){
   var b=FarmBarnGameTests.farm(t);var expected=BuildingSigns.position(t.e(),b);
   h.assertTrue(BuildingSigns.owned(t.l(),t.e(),b,expected),"Farm level "+level+" owns its expected label at "+expected);
   var survey=BuildingTiers.survey(t.l(),t.e(),b);
   h.assertTrue(survey.ok(),"Farm upgrade "+level+" conflicts="+survey.conflicts()+" expected="+expected+" reason="+survey.reason());
   FarmBarnGameTests.upgrade(h,t);
  }}finally{FarmBarnGameTests.done(t.l(),t.s());}h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void buildingSignsDoNotBlockUpgrade(GameTestHelper h){
  var l=h.getLevel();var data=SettlementData.get(l.getServer());var center=h.absolutePos(new BlockPos(1,4,1));var s=new Settlement(UUID.randomUUID());
  var b=new Settlement.Building(UUID.randomUUID(),"home",0,0,0);s.addBuilding(b);var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
  try{
   for(int x=-4;x<12;x++)for(int z=-4;z<12;z++)l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
   BuildingPlacement.layout(e,b,"home").forEach((p,state)->l.setBlock(p,state,2));BuildingSigns.refresh(l,e);var pos=BuildingSigns.position(e,b);
   var survey=BuildingTiers.survey(l,e,b);h.assertTrue(!survey.conflicts().contains(pos),"Own sign cannot block upgrade");
   var next=BuildingPlacement.layout(e,b,"home@2");var after=next.getOrDefault(pos,Blocks.AIR.defaultBlockState());
   if(!after.is(Blocks.OAK_WALL_SIGN))h.assertTrue(survey.state().getList("ops",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().anyMatch(raw->((net.minecraft.nbt.CompoundTag)raw).getLong("pos")==pos.asLong()),"Old label retired through paid project operations");
   var before=l.getBlockState(pos);var operation=UUID.randomUUID();h.assertTrue(org.villageastra.persistence.WorldJournal.place(l,operation,pos,before,Blocks.AIR.defaultBlockState()),"Builder can retire owned label through journal");
   h.assertTrue(org.villageastra.persistence.WorldJournal.place(l,operation,pos,before,Blocks.AIR.defaultBlockState()),"Retirement receipt replays safely");
   l.setBlock(pos,Blocks.CHEST.defaultBlockState(),2);var blocked=BuildingTiers.survey(l,e,b);h.assertTrue(blocked.conflicts().contains(pos)||blocked.state().getList("ops",net.minecraft.nbt.Tag.TAG_COMPOUND).stream().noneMatch(raw->((net.minecraft.nbt.CompoundTag)raw).getLong("pos")==pos.asLong()),"Other block entities remain untouched");
   h.assertTrue(!org.villageastra.persistence.WorldJournal.place(l,UUID.randomUUID(),pos,l.getBlockState(pos),Blocks.AIR.defaultBlockState()),"Journal still rejects foreign block entities");
  }finally{data.remove(s.id());}h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void buildingSignsEveryDesignHasPaidSupportedLabel(GameTestHelper h){
  for(var d:BuildingBlueprints.designs())for(int tier=1;tier<=6;tier++){
   String id=d.id()+"@"+tier;var cells=BuildingBlueprints.layout(id,BlockPos.ZERO);
   var signs=cells.entrySet().stream().filter(c->c.getValue().is(Blocks.OAK_WALL_SIGN)).toList();
   h.assertTrue(signs.size()==1,"Exactly one entrance label: "+id);
   var sign=signs.get(0);var support=sign.getKey().relative(sign.getValue().getValue(WallSignBlock.FACING).getOpposite());
   var lot=BuildingBlueprints.design(id);var p=sign.getKey();
   h.assertTrue(p.getX()>=0&&p.getX()<lot.width()&&p.getZ()>=0&&p.getZ()<lot.depth(),"Label stays inside the declared lot: "+id+" "+p);
   h.assertTrue(cells.containsKey(support)&&cells.get(support).isSolidRender(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,support),"Supported label: "+id);
   h.assertTrue(BlueprintMaterials.count(id).getOrDefault("minecraft:oak_sign",0L)==1,"Label is in construction materials: "+id);
  }h.succeed();
 }
 @GameTest(template="empty",timeoutTicks=200) public static void buildingSignsRotationsProtectionAndLegacyMigration(GameTestHelper h){
  var l=h.getLevel();var data=SettlementData.get(l.getServer());var center=h.absolutePos(new BlockPos(1,4,1));
  for(int turn=0;turn<4;turn++){
   var s=new Settlement(UUID.randomUUID());var b=new Settlement.Building(UUID.randomUUID(),"home",0,0,0,turn);s.addBuilding(b);
   var e=new SettlementData.Entry(s,l.dimension().location().toString(),center);data.add(e);
   try{
    var cells=BuildingPlacement.layout("home",center,turn);cells.forEach((p,state)->l.setBlock(p,state,2));
    var pos=cells.entrySet().stream().filter(c->c.getValue().is(Blocks.OAK_WALL_SIGN)).findFirst().orElseThrow().getKey();
    BuildingSigns.refresh(l,e);var target=BuildingSigns.target(l,pos);
    h.assertTrue(target!=null&&target.building().id().equals(b.id()),"Correct rotated building");
    var sign=(SignBlockEntity)l.getBlockEntity(pos);h.assertTrue(sign.isWaxed()&&sign.getFrontText().getMessage(0,false).equals(net.minecraft.network.chat.Component.translatable("building.villageastra.home")),"Localized immutable name");
    var player=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"SignTest"));player.setGameMode(GameType.CREATIVE);player.setPos(pos.getCenter());
    h.assertTrue(!player.gameMode.destroyBlock(pos),"Entrance sign protected in creative");
    l.setBlock(pos,Blocks.GOLD_BLOCK.defaultBlockState(),3);BuildingSigns.refresh(l,e);h.assertTrue(l.getBlockState(pos).is(Blocks.GOLD_BLOCK)&&BuildingSigns.target(l,pos)==null,"Never replace or bind another block");
    l.setBlock(pos,Blocks.AIR.defaultBlockState(),3);BuildingSigns.refresh(l,e);h.assertTrue(BuildingSigns.target(l,pos)!=null,"Legacy empty slot gets label");
    var outside=center.offset(20,2,0);l.setBlock(outside,Blocks.OAK_WALL_SIGN.defaultBlockState(),2);h.assertTrue(BuildingSigns.target(l,outside)==null,"Player sign is not a building menu");
   }finally{data.remove(s.id());}
  }h.succeed();
 }
}
