package org.villageastra.client;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.villageastra.server.SettlementData;
import org.villageastra.domain.Profession;
import com.mojang.logging.LogUtils;

/** Four finite descending steps, timber support, return trip and hard obstruction in an isolated fixture. */
final class DeepMineProbe {
    static final BlockPos ORIGIN=new BlockPos(0,-40,0);
    static boolean enabled(){return Boolean.getBoolean("villageastra.deepMineSmoke");}
    static void ground(ServerLevel level){
        for(var pos:BlockPos.betweenClosed(-32,-59,-32,70,-41,64))
            level.setBlock(pos,(pos.getY()==-41?Blocks.GRASS_BLOCK:Blocks.STONE).defaultBlockState(),2);
    }
    /** AD-074: the drive starts at the bottom of the built shaft, so the fixture lies that deep too — AD-122: one below its last tread. */
    static final int DEEP=org.villageastra.world.MineWork.DRIVE_DESCENT;
    /** AD-122: the built mouth floor (stone brick, z 7-8 of the lot) the drive digs through: its bricks reach the chest as stone bricks. */
    static final int MOUTH_BRICKS=DEEP>org.villageastra.world.BuildingBlueprints.SHAFT_DESCENT?6:0;
    static void seed(ServerLevel level){
        // AD-122: a drive opened now is MineWork.HEIGHT (5) high; the bedrock is the top cell of step 4.
        int height=org.villageastra.world.MineWork.HEIGHT;
        for(int step=0;step<4;step++)for(int cell=0;cell<height;cell++)for(int x=26;x<=28;x++){var at=ORIGIN.offset(x,height-1-step-cell-DEEP,19+step);
            if(!level.getBlockState(at).is(Blocks.STONE_BRICKS))level.setBlock(at,Blocks.STONE.defaultBlockState(),3);}
        // AD-122: two lanterns in the hall for the drive's light (step 2): the miner takes them with his pick.
        if(level.getBlockEntity(ORIGIN.offset(1,1,4)) instanceof Container hall)for(int slot=0;slot<hall.getContainerSize();slot++)if(hall.getItem(slot).isEmpty()){hall.setItem(slot,new net.minecraft.world.item.ItemStack(Items.LANTERN,2));break;}
        for(int x=26;x<=28;x++)level.setBlock(ORIGIN.offset(x,height-5-DEEP,23),Blocks.BEDROCK.defaultBlockState(),3);
        try {java.nio.file.Files.writeString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("astra-deep-width.txt"),"3");java.nio.file.Files.writeString(level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("astra-deep-height.txt"),Integer.toString(height));}catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        // The probe measures a shift of work, not a night: the sun stands still so the miner does not go home to bed halfway.
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false,level.getServer());level.setDayTime(1000);
        // AD-101: an NPC mayor builds (a home for the newborn, a level) and his builders pay for it with the village's cobblestone and logs —
        // the mine's chest and the hall's timber this probe counts. The player governs, and the next election (ElectionRoll.PERIOD, 20 min,
        // which a player without 640 reputation loses to an NPC) is held past the shift, so the count stays the miner's alone.
        var players=level.getServer().getPlayerList().getPlayers();var data=SettlementData.get(level.getServer());
        for(var e:data.entries())if(!players.isEmpty()){e.settlement().appointPlayerMayor(players.get(0).getUUID());e.settlement().governance().restoreElection(data.clock().ticks()+10*org.villageastra.domain.ElectionRoll.PERIOD,0,null,0,0);}
        data.setDirty();
        LogUtils.getLogger().info("ASTRA_DEEP_MINE fixture: player mayor={}, election held off",!players.isEmpty());
        LogUtils.getLogger().info("ASTRA_DEEP_MINE fixture: {} stone blocks, four three-wide and {}-high steps, 32 support logs, bedrock stop, day held",12*height,height);
    }
    static boolean verify(ServerLevel level,SettlementData.Entry entry,boolean report){
        var marker=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("astra-deep-width.txt");
        int width=java.nio.file.Files.exists(marker)?3:1,height=deepHeight(marker.resolveSibling("astra-deep-height.txt")); // Archived narrow-shaft fixture remains a valid reload test.
        var output=(Container)level.getBlockEntity(ORIGIN.offset(33,1,16));var stock=(Container)level.getBlockEntity(ORIGIN.offset(1,1,4));
        var resident=entry.settlement().residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();var worker=level.getEntity(resident.id());
        var beam=ORIGIN.offset(35,height-4-DEEP,22);
        if(worker==null)throw new IllegalStateException("Missing miner entity");
        // The forester's logs reach the hall with the porters (run 2 of 2026-09-21: 32 -> 52) and would change the timber count the support is checked against: it rests.
        for(var r:entry.settlement().residents())if(r.profession()==Profession.FORESTER&&level.getEntity(r.id()) instanceof net.minecraft.world.entity.Mob m&&!m.isNoAi())m.setNoAi(true);
        if(report){
            var mine=entry.settlement().buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();
            var drive=org.villageastra.world.MineWork.read(level,mine);
            var target=org.villageastra.world.MineWork.target(entry,mine,drive);
            var mineOrigin=org.villageastra.world.BuildingPlacement.origin(entry,mine);
            LogUtils.getLogger().info("ASTRA_DEEP_MINE progress width={} cobble={} logs={} beam={} worker={} status={} drive=[{} step={} cell={} descent={}] target={} holds={} standOn={} mine={}",
                width,output.countItem(Items.COBBLESTONE),stock.countItem(Items.OAK_LOG),level.getBlockState(beam),worker.position(),
                worker instanceof org.villageastra.world.ResidentEntity r?r.workStatus():"?",
                drive.getString("stage"),drive.getInt("step"),drive.getInt("cell"),drive.getInt("descent"),
                target.toShortString(),level.getBlockState(target).getBlock(),
                level.getBlockState(worker.blockPosition().below()).getBlock(),mineOrigin.toShortString());
            // The shaft as it really stands: the middle column of the trench, step by step.
            var slice=new StringBuilder();
            for(int z=0;z<=8;z++){slice.append('z').append(z).append(':');
                for(int y=0;y>=-7;y--){var at=mineOrigin.offset(3,y,z);var block=level.getBlockState(at);
                    slice.append(block.isAir()?'.':block.is(Blocks.STONE_BRICKS)?'#':block.is(Blocks.STONE)?'S':'?');}
                slice.append(' ');}
            LogUtils.getLogger().info("ASTRA_DEEP_MINE shaft x=3 y=0..-7 {}",slice);
            if(worker instanceof net.minecraft.world.entity.Mob mob){
                var chest=ORIGIN.offset(33,1,16);var path=mob.getNavigation().createPath(chest,0);
                var doorway=ORIGIN.offset(36,1,12);var out=mob.getNavigation().createPath(doorway,0);
                var goals=new StringBuilder();
                mob.goalSelector.getAvailableGoals().stream().filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                    .forEach(g->goals.append(g.getGoal().getClass().getSimpleName()).append(' '));
                LogUtils.getLogger().info("ASTRA_DEEP_MINE goals=[{}] navDone={} hasPath={}",goals,mob.getNavigation().isDone(),mob.getNavigation().getPath()!=null);
                LogUtils.getLogger().info("ASTRA_DEEP_MINE route toChest={} nodes={} toDoor={} nodes={} onGround={} below={}",
                    path!=null&&path.canReach(),path==null?0:path.getNodeCount(),out!=null&&out.canReach(),out==null?0:out.getNodeCount(),
                    mob.onGround(),level.getBlockState(net.minecraft.core.BlockPos.containing(mob.getX(),mob.getY()-0.2,mob.getZ())).getBlock());
            }
            LogUtils.getLogger().info("ASTRA_DEEP_MINE ticking={} playerAway={}",level.isPositionEntityTicking(worker.blockPosition()),
                level.getServer().getPlayerList().getPlayers().isEmpty()?-1:(int)Math.sqrt(level.getServer().getPlayerList().getPlayers().get(0).distanceToSqr(worker)));
        }
        // AD-122: a 5-high drive sets a stair in each bottom cell of its four steps, cut from one cobblestone each.
        int stairs=height>=5?4*width:0,expected=4*height*width-stairs-MOUTH_BRICKS;
        if(output.countItem(Items.COBBLESTONE)>expected)throw new IllegalStateException("Mine duplicated finite stone");
        if(output.countItem(Items.COBBLESTONE)!=expected)return false;
        for(int x=0;x<width;x++)if(!level.getBlockState(ORIGIN.offset(width==1?27:26+x,height-4-DEEP,22)).is(Blocks.OAK_LOG))return false;
        if(stock.countItem(Items.OAK_LOG)!=32-width)throw new IllegalStateException("Support timber count does not match beam width");
        for(int step=0;step<4;step++)for(int cell=0;cell<height;cell++)for(int x=0;x<width;x++){
            var target=ORIGIN.offset(width==1?27:26+x,height-1-step-cell-DEEP,19+step);
            if(stairs>0&&cell==height-1){if(!level.getBlockState(target).is(Blocks.COBBLESTONE_STAIRS))throw new IllegalStateException("Missing stair "+target+": "+level.getBlockState(target));continue;}
            // AD-122: the light of step 2 hangs in the east column's top cell.
            if(height>=5&&step==2&&cell==0&&x==width-1){if(!level.getBlockState(target).is(Blocks.LANTERN))throw new IllegalStateException("Missing light "+target+": "+level.getBlockState(target));continue;}
            if(!(step==3&&cell==0)&&!level.getBlockState(target).isAir())throw new IllegalStateException("Uncut staircase cell "+target);
        }
        if(output.countItem(Items.STONE_BRICKS)!=MOUTH_BRICKS)throw new IllegalStateException("Mouth floor bricks "+output.countItem(Items.STONE_BRICKS)+" not "+MOUTH_BRICKS);
        if(!level.getBlockState(ORIGIN.offset(width==1?27:26,height-5-DEEP,23)).is(Blocks.BEDROCK))throw new IllegalStateException("Mine removed the hard stop");
        if(worker.distanceToSqr(ORIGIN.getX()+26.5,ORIGIN.getY()+1.5,ORIGIN.getZ()+16.5)>6.25)return false;
        var building=entry.settlement().buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();
        var area=entry.settlement().mineAreas().get(building.id());
        if(area==null||area.lastStep()!=3||area.width()!=width||area.height()!=height)throw new IllegalStateException("Excavated claim does not match physical shaft");
        if(width==3){
            var base=ORIGIN.offset(building.x(),building.y(),building.z());
            if(!org.villageastra.server.OwnershipEvents.disallowedPlacement(level,base.offset(8,-3-DEEP,13))
                ||org.villageastra.server.OwnershipEvents.disallowedPlacement(level,base.offset(9,-3-DEEP,13)))throw new IllegalStateException("Live mine claim lost its exact construction buffer");
        }

        LogUtils.getLogger().info("ASTRA_DEEP_MINE VERIFIED four steps width={} height={}, exactly {} cobblestone delivered, {} stairs set, one lantern, "+MOUTH_BRICKS+" mouth bricks, {} paid support logs, bedrock preserved, worker returned; reload={}",width,height,expected,stairs,width,Boolean.getBoolean("villageastra.reloadSmoke"));return true;
    }
    /** AD-122: the camera stands on the built stair of the mine and looks down the dug flight, with night vision (the owner's view of the shaft). */
    static void frameStairs(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();var player=server.getPlayerList().getPlayers().get(0);
        var mine=entry.settlement().buildings().stream().filter(b->b.type().equals("mine")).findFirst().orElseThrow();
        var feet=org.villageastra.world.BuildingPlacement.at(entry,mine,3,-3,4);var look=org.villageastra.world.BuildingPlacement.at(entry,mine,3,-DEEP-3,10);
        double eyeX=feet.getX()+.5,eyeY=feet.getY()+player.getEyeHeight(),eyeZ=feet.getZ()+.5,dx=look.getX()+.5-eyeX,dy=look.getY()+.5-eyeY,dz=look.getZ()+.5-eyeZ;
        float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90,pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,1200,0,false,false));
        player.teleportTo(level,feet.getX()+.5,feet.getY(),feet.getZ()+.5,yaw,pitch);
        LogUtils.getLogger().info("ASTRA_DEEP_MINE stairs camera at {} looking at {}",feet.toShortString(),look.toShortString());
    }
    /** The drive height the fixture was laid for: the marker's number (3 for the archived narrow fixture without one). */
    private static int deepHeight(java.nio.file.Path marker){
        try{return java.nio.file.Files.exists(marker)?Integer.parseInt(java.nio.file.Files.readString(marker).trim()):3;}catch(java.io.IOException|NumberFormatException ex){throw new IllegalStateException(ex);}
    }
    /** The player watches the miner at work: without somebody near, its chunk stops ticking and the shift never ends. */
    static void follow(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();
        var miner=entry.settlement().residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElse(null);
        if(miner==null)return;var entity=level.getEntity(miner.id());var player=server.getPlayerList().getPlayers().get(0);
        if(entity==null||player.distanceToSqr(entity)<=64)return;
        try{frameWorker(server);}catch(RuntimeException ignored){
            player.teleportTo(level,entity.getX(),entity.getY()+3,entity.getZ(),player.getYRot(),player.getXRot());
        }
    }
    static void frameWorker(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();var entry=SettlementData.get(server).entries().iterator().next();
        var miner=entry.settlement().residents().stream().filter(r->r.profession()==Profession.MINER).findFirst().orElseThrow();
        var entity=level.getEntity(miner.id());var player=server.getPlayerList().getPlayers().get(0);
        var target=entity.position().add(0,1,0);
        // Four sides first (the old frame), then the diagonals and a closer ring: a miner who has just stepped into a corner is still framed.
        int[][] sides={{0,-1},{0,1},{1,0},{-1,0},{1,1},{1,-1},{-1,1},{-1,-1}};
        for(int pass=0;pass<2;pass++)for(int radius=pass==0?3:2;radius<=(pass==0?5:6);radius++)for(int up=0;up<=5;up++)for(int side=0;side<(pass==0?4:8);side++){
            var feet=entity.position().add(sides[side][0]*radius,up,sides[side][1]*radius);var eye=feet.add(0,player.getEyeHeight(),0);
            var box=player.getBoundingBox().move(feet.subtract(player.position()));
            if(!level.noCollision(player,box))continue;
            var hit=level.clip(new net.minecraft.world.level.ClipContext(eye,target,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
            if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            double dx=target.x-eye.x,dz=target.z-eye.z,dy=target.y-eye.y;
            float yaw=(float)Math.toDegrees(Math.atan2(dz,dx))-90,pitch=(float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz)));
            player.teleportTo(level,feet.x,feet.y,feet.z,yaw,pitch);return;
        }
        throw new IllegalStateException("No unobstructed camera position for miner at "+entity.blockPosition().toShortString());
    }
}
