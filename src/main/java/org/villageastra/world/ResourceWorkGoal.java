package org.villageastra.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import org.villageastra.domain.*;
import org.villageastra.server.SettlementData;
import org.villageastra.persistence.*;
import org.villageastra.server.ForestPlantings;
import java.util.*;
import java.nio.file.*;
import java.io.*;

/** One durable work record per workplace, shared by every eligible worker. Never creates resources on a timer. */
public final class ResourceWorkGoal extends Goal {
    private final ResidentEntity worker;
    private CompoundTag state;
    private Path file;
    private BlockPos base,stock,output;
    private boolean miner,farmer;
    private UUID buildingId;
    private final boolean withoutPlayers;
    private final java.util.function.LongSupplier dayTime;
    /** AD-104 P2: the farmer's calls with a batch in hand and nothing to do; kept in memory only, a restart waits the minute again. */
    private int idleCalls,oreCheck=-100;
    /** AD-104 P2 (balance/farmer.json): the items the farmer carries before he goes home; how far from the last plot he worked he still sows
     *  and reaps (a 9x9 module is 11.3 blocks corner to corner, and he stands up to 2.5 from a plot); the one-second calls he waits with a
     *  batch and nothing to do; the ticks before dusk he takes it home; the spare seeds the farm chest keeps — the rest stays in the soil. */
    public static final int BATCH,REACH,WAIT_CALLS,EVENING,SEED_KEEP;
    static{
        com.google.gson.JsonObject o;
        try(var s=ResourceWorkGoal.class.getResourceAsStream("/data/villageastra/balance/farmer.json")){if(s==null)throw new IllegalStateException("Missing farmer balance");o=com.google.gson.JsonParser.parseReader(new InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
        BATCH=o.get("batch_items").getAsInt();REACH=o.get("reach").getAsInt();WAIT_CALLS=o.get("wait_calls").getAsInt();EVENING=o.get("evening_ticks").getAsInt();SEED_KEEP=o.get("seed_keep").getAsInt();
        // A batch of 58 plus the largest loot of one plot (6: potatoes) still fits one stack of 64.
        if(o.get("schema").getAsInt()!=1||BATCH<1||BATCH>58||REACH<2||REACH>32||WAIT_CALLS<1||EVENING<0||EVENING>SleepGoal.DUSK||SEED_KEEP<0)throw new IllegalStateException("Invalid farmer balance");
    }
    /** AD-122 (balance/miner.json): the items the miner carries before he takes them to the mine chest, and the ticks before dusk he takes them. */
    public static final int MINER_BATCH,MINER_EVENING,LIGHT_STEPS,LIGHT_COLUMNS,LIGHTS_CARRIED;
    static{
        com.google.gson.JsonObject o;
        try(var s=ResourceWorkGoal.class.getResourceAsStream("/data/villageastra/balance/miner.json")){if(s==null)throw new IllegalStateException("Missing miner balance");o=com.google.gson.JsonParser.parseReader(new InputStreamReader(s,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}catch(IOException e){throw new IllegalStateException(e);}
        MINER_BATCH=o.get("batch_items").getAsInt();MINER_EVENING=o.get("evening_ticks").getAsInt();
        LIGHT_STEPS=o.get("light_every_steps").getAsInt();LIGHT_COLUMNS=o.get("light_every_columns").getAsInt();LIGHTS_CARRIED=o.get("lights_carried").getAsInt();
        if(o.get("schema").getAsInt()!=1||MINER_BATCH<1||MINER_BATCH>256||MINER_EVENING<0||MINER_EVENING>SleepGoal.DUSK||LIGHT_STEPS<2||LIGHT_COLUMNS<2||LIGHTS_CARRIED<1||LIGHTS_CARRIED>16)throw new IllegalStateException("Invalid miner balance");
    }
    /** AD-122: the miner's work was interrupted (another goal took over): the batch he carries goes to the chest before he digs on. In memory only. */
    private boolean interrupted;
    /** AD-122: the block this worker shows cracking (vanilla's destroy overlay), cleared when it breaks or he leaves it. In memory only. */
    private BlockPos cracking;
    public ResourceWorkGoal(ResidentEntity worker){this(worker,false);}
    /** A goal the tests drive call by call: it needs no player online and keeps no one-second cadence (the GuardGoal and RoadWorkGoal pattern). */
    public ResourceWorkGoal(ResidentEntity worker,boolean withoutPlayers){this(worker,withoutPlayers,()->worker.level().getDayTime());}
    /** The same with its own clock of the day, so a test decides whether the farmer's evening has come without touching the shared world time. */
    public ResourceWorkGoal(ResidentEntity worker,boolean withoutPlayers,java.util.function.LongSupplier dayTime){this.worker=worker;this.withoutPlayers=withoutPlayers;this.dayTime=dayTime;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public boolean canUse(){
        if(!(worker.level() instanceof ServerLevel level)||level.getServer().getPlayerCount()==0&&!withoutPlayers||worker.escortPlayer()!=null||worker.settlementId()==null)return false;
        if(!CargoCustody.mayStartWork(worker))return false;
        var entry=SettlementData.get(level.getServer()).entry(worker.settlementId());if(entry==null||!entry.dimension().equals(level.dimension().location().toString()))return false;
        var r=entry.settlement().resident(worker.getUUID());var building=entry.settlement().workplace(worker.getUUID());
        if(r==null||!r.alive()||building==null||(r.profession()!=Profession.MINER&&r.profession()!=Profession.FORESTER&&r.profession()!=Profession.FARMER))return false;
        // AD-053: a claimed quarry is the miner's assignment; the stair mine waits until that chunk is worked out.
        if(r.profession()==Profession.MINER&&(Excavation.pending(level,entry.settlement().id())||(Quarry.record(level,entry.settlement().id())!=null||Quarry.building(entry)!=null)&&!Quarry.worked(level,entry.settlement().id())))return false;
        buildingId=building.id();miner=r.profession()==Profession.MINER;farmer=r.profession()==Profession.FARMER;base=entry.center().offset(building.x(),building.y(),building.z());
        // AD-104 P2: the farm chest is the cell the farm's turn puts it in, the one porters fetch from (LogisticsRoutes.position); AD-131: the
        // forester's hut chest too (the lodge used to fill its unturned cell).
        stock=HallSite.stock(entry);output=BuildingPlacement.at(entry,building,1,1,4);
        file=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+building.id()+".bin");
        state=read();worker.workStatus(state.getString("status"));idleCalls=0;
        if(miner&&state.contains("extentStep"))noteArea(state.getInt("extentStep"));
        // A different worker must wait for the owner's physical return or death-cargo transaction.
        if(state.hasUUID("worker")&&!state.getUUID("worker").equals(worker.getUUID()))return false;
        state.putUUID("worker",worker.getUUID());save();return true;
    }
    @Override public boolean canContinueToUse(){
        if(worker.escortPlayer()!=null||!worker.isAlive()||worker.getServer().getPlayerCount()==0&&!withoutPlayers||CargoCustody.pending(worker.getServer(),worker.getUUID()))return false;
        var entry=SettlementData.get(worker.getServer()).entry(worker.settlementId());if(entry==null)return false;
        // AD-053: once a quarry is claimed the miner leaves the stair mine for it.
        if(miner&&worker.level() instanceof ServerLevel level&&(Excavation.pending(level,entry.settlement().id())||(Quarry.record(level,entry.settlement().id())!=null||Quarry.building(entry)!=null)&&!Quarry.worked(level,entry.settlement().id())))return false;
        var r=entry.settlement().resident(worker.getUUID());var workplace=entry.settlement().workplace(worker.getUUID());
        return entry.dimension().equals(worker.level().dimension().location().toString())&&r!=null&&workplace!=null&&r.profession()==(miner?Profession.MINER:farmer?Profession.FARMER:Profession.FORESTER)&&file.getFileName().toString().equals(workplace.id()+".bin");
    }
    /** AD-074: how far below the lot this drive runs. A record started before the shaft existed keeps its old geometry. */
    private int descent(){return state.getInt("descent");}
    private void noteArea(int step){
        var data=SettlementData.get(worker.getServer());var entry=data.entry(worker.settlementId());
        if(entry.settlement().noteMine(buildingId,step,state.getInt("width"),state.getInt("height"),descent()))data.setDirty();
        state.putInt("extentStep",Math.max(step,state.contains("extentStep")?state.getInt("extentStep"):0));
    }
    public static boolean recoverAreas(net.minecraft.server.MinecraftServer server,SettlementData.Entry entry){
        boolean changed=false;
        for(var building:entry.settlement().buildings())if(building.type().equals("mine")){
            var file=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/astra-work/"+building.id()+".bin");if(!Files.exists(file))continue;
            try {
                var t=NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(file))));
                if(t.getInt("schema")!=1)throw new IOException("Unsupported mine work record");
                int step=t.contains("extentStep")?t.getInt("extentStep"):t.getInt("step")-(Set.of("dig","deliver").contains(t.getString("stage"))?0:1);
                if(step>=0)changed|=entry.settlement().noteMine(building.id(),step,t.contains("width")?t.getInt("width"):1,t.contains("height")?t.getInt("height"):3,t.getInt("descent"));
            }catch(IOException ex){throw new IllegalStateException(ex);}
        }return changed;
    }
    @Override public void stop(){worker.displayWorkItem(ItemStack.EMPTY);worker.longStride(false);if(miner&&state!=null&&carrying())interrupted=true;uncrack();}
    private CompoundTag read(){
        try {if(Files.exists(file)){
            var t=NbtIo.read(new DataInputStream(new ByteArrayInputStream(AtomicRecord.read(file))));
            // AD-131: a forester's record is schema 2; one of schema 1 (the nursery's) starts again at its axe, the logs it held are the journal's.
            if(!miner&&!farmer&&t.getInt("schema")==1&&t.hasUUID("operation")){var fresh=new CompoundTag();fresh.putInt("schema",2);fresh.putInt("width",1);fresh.putInt("height",3);fresh.putInt("descent",0);
                if(t.contains("tool"))fresh.put("tool",t.getCompound("tool"));if(t.hasUUID("worker"))fresh.putUUID("worker",t.getUUID("worker"));
                fresh.putString("stage",ItemStack.of(fresh.getCompound("tool")).isEmpty()?"tool":"choose");fresh.putUUID("operation",UUID.randomUUID());return fresh;}
            if(t.getInt("schema")!=(!miner&&!farmer?2:1)||!t.hasUUID("operation")||!Set.of("tool","upgrade_tool","choose","dig","deliver","sapling","plant","replant","support_fetch","support_place","seal_fetch","seal_place","till","stair","light","nursery_soil").contains(t.getString("stage")))throw new IOException("Invalid resource work state");
            if(MineSealing.active(t)&&(!miner||!t.contains("sealFace",Tag.TAG_LONG)||!t.contains("sealAt",Tag.TAG_LONG)))throw new IOException("Invalid mine seal state");
            if(!t.contains("width"))t.putInt("width",1);
            if(!t.contains("height"))t.putInt("height",3);
            if(t.getInt("height")<3||t.getInt("height")>5)throw new IOException("Invalid shaft height");
            if(t.getInt("width")!=1&&t.getInt("width")!=3)throw new IOException("Invalid shaft width");
            return t;
        }}
        catch(IOException e){throw new IllegalStateException(e);}
        CompoundTag t=new CompoundTag();t.putInt("schema",!miner&&!farmer?2:1);t.putInt("width",miner?3:1);t.putInt("height",miner?MineWork.HEIGHT:3);
        // AD-074: a drive opened now starts at the bottom of the built shaft; one already begun keeps its old geometry.
        t.putInt("descent",miner?MineWork.DRIVE_DESCENT:0);
        t.putString("stage","tool");t.putUUID("operation",UUID.randomUUID());return t;
    }
    private void save(){try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();NbtIo.write(state,new DataOutputStream(bytes));AtomicRecord.write(file,bytes.toByteArray());}catch(IOException e){throw new IllegalStateException(e);}}
    private void status(String value){worker.workStatus(value);if(!state.getString("status").equals(value)){state.putString("status",value);save();if(Boolean.getBoolean("villageastra.resourceSmoke")||Boolean.getBoolean("villageastra.deepMineSmoke")||Boolean.getBoolean("villageastra.farmSmoke")||Boolean.getBoolean("villageastra.foresterProbe"))com.mojang.logging.LogUtils.getLogger().info("ASTRA_WORK {} {} at {}",miner?"miner":farmer?"farmer":"forester",value,worker.blockPosition());}}
    /** AD-079: the miner's own shaft is a stair of steps in a walled trench, and no single route out of it satisfies the navigator —
     *  it hands back a path that is finished where the miner already stands. Inside its own shaft the miner therefore walks tread by
     *  tread by itself, the way the builder climbs its ladder, and outside it the ordinary route takes over again. */
    private BlockPos workPos(int x,int y,int z){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());return BuildingPlacement.at(e,e.settlement().workplace(worker.getUUID()),x,y,z);}
    private boolean shaftWalk(BlockPos target){
        worker.longStride(false);
        if(!miner||state.getInt("descent")<=0)return false;
        var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());var local=BuildingPlacement.local(e,e.settlement().workplace(worker.getUUID()),worker.blockPosition());
        int lz=local.getZ();double ly=worker.getY()-base.getY();int lx=local.getX();
        // The lot stands a block above the first tread, so the miner is out of the shaft only when it is up on the lot AND on its floor,
        // not hanging over the open trench; at the bottom tread the drive is one stride away and the ordinary route works again.
        boolean overTrench=lx>=3&&lx<=4&&lz>=1&&lz<=6;
        var area=e.settlement().mineAreas().get(buildingId);
        boolean landing=lx>=2&&lx<=4&&lz>=7&&lz<=8&&ly>=-6&&ly<0;
        if(!overTrench&&!landing&&(area==null||!area.contains(lx,local.getY(),lz,0)))return false;
        // Up at the lot level with ground under its feet the miner is out, even standing on the very edge above the first tread;
        // only a miner hanging over the open trench (rising out of it) is still walked by hand.
        if(ly>=0.5&&(!overTrench||worker.onGround())||lz<1)return false;
        boolean out=target.getY()-base.getY()>=0;
        if(!out&&lz>=6&&!overTrench)return false;
        if(!out&&lz>=6&&ly<-0.5)return false;
        // AD-112: a miner in a gallery of the floor walks along it to the stair's middle column first; the stair's line would lead into the rock.
        if(out&&lz>6&&(lx<2||lx>4)){var along=workPos(lx>4?lx-1:lx+1,(int)Math.floor(ly+.01),lz);
            double ax=along.getX()+.5-worker.getX(),az=along.getZ()+.5-worker.getZ(),al=Math.max(.001,Math.sqrt(ax*ax+az*az));worker.getNavigation().stop();worker.longStride(true);
            worker.setDeltaMovement(ax/al*.12,worker.getDeltaMovement().y,az/al*.12);worker.getLookControl().setLookAt(along.getX()+.5,along.getY()+1,along.getZ()+.5);status("climbing_out_of_the_shaft");return true;}
        int next=out?(lz>6?6:lz-1):Math.min(6,lz+1);
        if(next<0)return false;
        // A tread carries the miner one block higher than the one below it; from the first tread it steps west onto the aisle of the lot,
        // whose own level is read from the world rather than assumed.
        var waypoint=next==0?stand(workPos(2,1,1)):workPos(3,-next+1,next);
        double dx=waypoint.getX()+.5-worker.getX(),dz=waypoint.getZ()+.5-worker.getZ(),dy=waypoint.getY()-worker.getY();
        double length=Math.max(.001,Math.sqrt(dx*dx+dz*dz));
        worker.getNavigation().stop();
        // A step up is taken with the stride itself (AD-079: a worker of the village steps a whole block), never with a hop that
        // would take the worker off the ground and stop the step from happening at all. Moving the miner by hand instead of by its
        // own legs was tried and dropped: it walked the worker into the rock.
        worker.longStride(true);
        worker.setDeltaMovement(dx/length*.12,worker.getDeltaMovement().y,dz/length*.12);
        worker.getLookControl().setLookAt(waypoint.getX()+.5,waypoint.getY()+1,waypoint.getZ()+.5);
        status(out?"climbing_out_of_the_shaft":"walking_down_the_shaft");
        return true;
    }
    /** The cell of that column a worker can really stand in: the nearest one that is free with something sturdy under it. */
    private BlockPos stand(BlockPos about){
        var level=(ServerLevel)worker.level();
        for(int dy=1;dy>=-2;dy--){var at=about.offset(0,dy,0);
            if(level.getBlockState(at).isAir()&&level.getBlockState(at.above()).isAir()
                &&level.getBlockState(at.below()).isFaceSturdy(level,at.below(),net.minecraft.core.Direction.UP))return at;}
        return about;
    }
    /** Where a forester stands to fell a tree: a free cell beside its foot (any cell of a 2x2 foot), the one nearest him — heading for the
     *  trunk itself sends the path to the closest a path can come to a solid log. */
    private BlockPos trunkSide(BlockPos foot){
        var level=(ServerLevel)worker.level();BlockPos best=null;double score=Double.MAX_VALUE;
        var feet=state.contains("base")?Arrays.stream(state.getLongArray("base")).mapToObj(BlockPos::of).toList():List.of(foot);
        var accessible=TreeAccess.find(worker,foot,feet);if(accessible!=null)return accessible;
        for(var f:feet)for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL){var at=f.relative(d);if(feet.contains(at))continue;
            var ground=level.getBlockState(at);
            if(!(ground.isAir()||ground.canBeReplaced())||!level.getBlockState(at.above()).isAir()||!level.getBlockState(at.below()).isFaceSturdy(level,at.below(),net.minecraft.core.Direction.UP))continue;
            double s=at.distSqr(worker.blockPosition());if(s<score){score=s;best=at;}}
        return best==null?foot:best;
    }
    /** The cell beside a chest a worker can really stand in. Buildings change shape — AD-074 put the mine's shaft where the miner
     *  used to stand — so the side is read from the world instead of always being the eastern one. */
    private BlockPos beside(BlockPos chest){
        var level=(ServerLevel)worker.level();
        for(var side:List.of(chest.east(),chest.north(),chest.south(),chest.west())){
            if(!level.getBlockState(side).isAir()||!level.getBlockState(side.above()).isAir())continue;
            var floor=side.below();
            if(level.getBlockState(floor).isFaceSturdy(level,floor,net.minecraft.core.Direction.UP))return side;
        }
        return chest.east();
    }
    /** AD-131: the kind of sapling the forester's planting under way sets (the oak when none is named). */
    private Item planted(){var kind=item(state.getString("species"));return kind==Items.AIR?Items.OAK_SAPLING:kind;}
    private Block saplingBlock(){return planted() instanceof BlockItem item?item.getBlock():Blocks.OAK_SAPLING;}
    private boolean near(BlockPos pos){
        if(miner){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());var mine=e.settlement().workplace(worker.getUUID());
            var waypoint=MineApproach.waypoint(worker,e,mine,state,pos);
            if(!waypoint.equals(pos)){
                if(!shaftWalk(waypoint)){if(withoutPlayers||worker.tickCount%20==0)worker.getNavigation().moveTo(waypoint.getX()+.5,waypoint.getY(),waypoint.getZ()+.5,.8);status("walking");}
                return false;
            }
        }
        if(shaftWalk(pos))return false;
        boolean precise=miner&&(state.getString("stage").equals("seal_place")||MineOreWork.active(state));
        double feetY=pos.getY();
        if(precise){var shape=worker.level().getBlockState(pos).getCollisionShape(worker.level(),pos);if(!shape.isEmpty())feetY+=shape.max(net.minecraft.core.Direction.Axis.Y);}
        double distance=worker.distanceToSqr(pos.getX()+.5,feetY+(precise?0:.5),pos.getZ()+.5);
        // Near the planned tread is not necessarily within reach of the lower face.
        // Keep the current path alive between the ordinary twenty-tick navigation updates.
        boolean faceReach=farmer||!state.getString("stage").equals("dig")||worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(BlockPos.of(state.getLong("target"))))<=16;
        if(distance<=(precise?.16:6.25)&&faceReach){worker.getNavigation().stop();return true;}
        // AD-131: a forester walks at his hut's pace (0.8, 1.0 from level V).
        var route=worker.getNavigation().getPath();
        boolean finalTreeStride=!miner&&!farmer&&!faceReach&&distance<=6.25&&route!=null&&route.canReach()&&worker.getNavigation().isDone();
        if(precise&&distance<2.25||finalTreeStride){if(!finalTreeStride)worker.getNavigation().stop();worker.getMoveControl().setWantedPosition(pos.getX()+.5,feetY,pos.getZ()+.5,.8);}
        else if(worker.tickCount%20==0)worker.getNavigation().moveTo(pos.getX()+.5,feetY,pos.getZ()+.5,!miner&&!farmer?ForestBalance.walkSpeed(hutLevel()):.8);
        status(!miner&&!farmer&&state.getString("stage").equals("dig")?"walking_to_tree":"walking");return false;
    }
    @Override public void tick(){
        // AD-122: a miner who has begun a block works it every tick, like a player holding the mouse button; walking and everything else keep the one-second call.
        // With a batch in hand he turns to the next cell of his drive at once, and starts a block the tick he stands at it.
        boolean face=miner&&state!=null&&(state.getString("stage").equals("dig")&&(state.getInt("labor")>0||atFace())||state.getString("stage").equals("choose")&&!state.getList("cargo",Tag.TAG_COMPOUND).isEmpty());
        // AD-122 (owner): below the lot the miner walks every tick, as he digs — a push once a second left him crawling up the stair.
        face|=miner&&base!=null&&worker.getY()-base.getY()<-.5;
        if(!withoutPlayers&&worker.tickCount%20!=0&&!face)return;
        ServerLevel level=(ServerLevel)worker.level();String stage=state.getString("stage");UUID id=state.getUUID("operation");
        if(miner){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());var mine=e.settlement().workplace(worker.getUUID());
            if(!MineOreWork.active(state)&&stage.equals("choose")&&worker.tickCount-oreCheck>=100){oreCheck=worker.tickCount;MineOreWork.begin(worker,e,mine,state);}
            if(MineOreWork.active(state)){
                if(WorldJournal.exists(level,state.getCompound("mineOre").getUUID("id"))){MineOreWork.tick(worker,state);save();return;}
                if(MineOreWork.needsStone(level,state)){if(MineOreWork.approaching(worker,state)&&near(beside(MineOreWork.materialSource(state))))MineOreWork.fetchStone(worker,e,mine,state);save();return;}
                if(!MineOreWork.approaching(worker,state)){save();return;}
                if(near(MineOreWork.stand(state))&&worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(MineOreWork.target(state)))<=16)MineOreWork.tick(worker,state);
                save();return;
            }
        }
        if(miner){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());if(MineClearance.tick(worker,e,e.settlement().workplace(worker.getUUID()),state)){save();return;}}
        if(miner&&!state.contains("stairStep")&&(stage.equals("choose")||MineSealing.active(state))){
            var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());int row=MineStairWork.repair(level,e,e.settlement().workplace(worker.getUUID()),state);
            if(row>=0){if(MineSealing.active(state)){MineSealing.reconcile(level,state);MineSealing.clear(state);}state.putInt("stairStep",row);beginStairs(level);return;}
        }
        ItemStack display=ItemStack.of(state.getCompound("tool"));
        if(stage.equals("plant")||stage.equals("replant"))display=new ItemStack(farmer?FarmCrops.pending(state).seed:planted());
        else if(stage.equals("support_place"))display=new ItemStack(Items.OAK_LOG);
        else if(stage.equals("deliver")){var cargo=state.getList("cargo",Tag.TAG_COMPOUND);int delivered=state.getInt("delivered");if(delivered<cargo.size())display=ItemStack.of(cargo.getCompound(delivered));}
        worker.displayWorkItem(display);
        if(stage.equals("upgrade_tool")){
            if(!near(beside(stock)))return;var old=ItemStack.of(state.getCompound("tool"));
            if(!old.isEmpty()&&!WorldJournal.deposit(level,Settlement.childId(id,"return_unfit"),stock,old)){status("output_full");return;}
            state.remove("tool");state.putString("stage","tool");state.putUUID("operation",UUID.randomUUID());save();return;
        }
        if(stage.equals("tool")){
            // AD-155 III: a tool the smithy's courier or wolf brought lies in the workplace's own chest - taken there, not at the hall
            // (a miner still due lights goes to the hall, where the lights are).
            boolean lightsDue=miner&&MineWork.shape(state).height()>=5&&state.getInt("lightsHeld")<LIGHTS_CARRIED;
            BlockPos from=state.contains("toolSource")?BlockPos.of(state.getLong("toolSource")):!lightsDue&&ownTool(level)?output:stock;
            if(from.equals(output)&&!state.contains("toolSource")){state.putLong("toolSource",output.asLong());save();}
            if(!near(beside(from)))return;
            if(miner&&from.equals(stock)&&restockLights(level,id))return;
            ItemStack tool=WorldJournal.recoverTake(level,id);
            if(tool.isEmpty() && !WorldJournal.exists(level,id) && level.getBlockEntity(from) instanceof Container c){
                for(int slot=0;slot<c.getContainerSize();slot++){
                    ItemStack item=c.getItem(slot);
                    if(fitsTool(item)&&(!from.equals(stock)||HallReserve.free(level,stock,item)>0)){tool=WorldJournal.take(level,id,from,slot,item.copy());break;}
                }
            }
            if(tool.isEmpty()&&!miner&&!farmer&&handFellingNeeded(level)){
                state.putBoolean("handFelling",true);state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();return;
            }
            if(tool.isEmpty()){if(state.contains("toolSource")){state.remove("toolSource");save();return;}status("missing_tool");return;}
            state.remove("toolSource");state.remove("handFelling");state.remove("requiredToolState");state.put("tool",tool.save(new CompoundTag()));state.putString("stage",state.getBoolean("resumeSupport")?"support_fetch":"choose");state.remove("resumeSupport");state.putUUID("operation",UUID.randomUUID());save();return;
        }
        // AD-131: the forester's own round — wild trees round his hut, replanting, trips (ForestWork).
        if(!miner&&!farmer){forest(level,stage,id);return;}
        if(miner&&MineSealing.active(state)){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());MineSealing.tick(level,e.settlement().workplace(worker.getUUID()),state,worker,stock,beside(stock),minerAccess(),this::near,this::save,this::status,level.getGameTime());return;}
        if(stage.equals("support_fetch")){
            if(!near(beside(stock)))return;
            if(restockLights(level,id))return;
            // AD-112: as many logs as the beam MineDrive gave — the stair's width, or one for a gallery.
            int width=MineWork.beam(state).count(),fetched=state.getInt("support_fetched");
            if(fetched>=width-state.getInt("support_placed")){state.putString("stage","support_place");save();return;}
            UUID take=MineWork.timberId(state,id,fetched);
            ItemStack timber=WorldJournal.recoverAmount(level,take);
            if(timber.isEmpty()&&!WorldJournal.exists(level,take)&&level.getBlockEntity(stock) instanceof Container c)
                for(int slot=0;slot<c.getContainerSize();slot++)if(MineTimber.material(c.getItem(slot))){
                    var stack=c.getItem(slot);int free=HallReserve.free(level,stock,stack);if(free<=0)continue;timber=WorldJournal.takeAmount(level,take,stock,slot,stack.copy(),Math.min(width-state.getInt("support_placed")-fetched,Math.min(stack.getCount(),free)));break;
                }
            if(timber.isEmpty()){status("missing_support_timber");return;}
            MineTimber.add(state,timber);state.putInt("support_fetched",fetched+timber.getCount());save();return;
        }
        if(stage.equals("support_place")){
            var planned=MineWork.beam(state);int width=planned.count(),placed=state.getInt("support_placed"),step=state.getInt("step");
            if(placed>=width){state.putString("stage",ItemStack.of(state.getCompound("tool")).isEmpty()?"tool":"choose");state.putUUID("operation",UUID.randomUUID());state.remove("support_fetched");state.remove("support_placed");state.remove("supportTimber");state.remove("beam");save();return;}
            var cell=planned.cells().get(placed);var stand=MineWork.beamStand(state);BlockPos beam=workPos(cell.x(),cell.y(),cell.z());BlockPos feet=workPos(stand.x(),stand.y(),stand.z());
            if(MineWork.gallery(state)&&!MineWork.supported(level,feet)){MineTimber.cancel(level,state);save();return;}
            if(!near(feet))return;
            var timber=MineTimber.first(state);if(!MineTimber.material(timber)){status("missing_support_timber");return;}
            var log=((BlockItem)timber.getItem()).getBlock().defaultBlockState().setValue(RotatedPillarBlock.AXIS,net.minecraft.core.Direction.Axis.X);
            if(log.getCollisionShape(level,beam).bounds().move(beam).intersects(worker.getBoundingBox())){
                var path=worker.getNavigation().createPath(feet,0);if(path!=null)worker.getNavigation().moveTo(path,.8);status("needs_access");return;
            }
            UUID place=MineWork.beamId(state,id,placed);
            if(!WorldJournal.place(level,place,beam,Blocks.AIR.defaultBlockState(),log)){status("support_conflict");return;}
            MineTimber.spend(state);if(!MineWork.gallery(state))noteArea(step-1);state.putInt("support_placed",placed+1);
            if(placed+1>=width){
                state.putString("stage",ItemStack.of(state.getCompound("tool")).isEmpty()?"tool":"choose");state.putUUID("operation",UUID.randomUUID());
                state.remove("support_fetched");state.remove("support_placed");state.remove("supportTimber");state.remove("beam");
            }save();return;
        }
        if(stage.equals("light")){
            // AD-122 (owner): the light due first, hung in the top cell above the walking headroom — a lantern from the roof, or a torch on the
            // wall — under its own journal id and paid from the lights he carries in the same record write; a cell it cannot take is passed by.
            var due=state.getIntArray("lightsDue");int held=state.getInt("lightsHeld");
            if(due.length<8||held<1){state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();return;}
            BlockPos at=workPos(due[0],due[1],due[2]);if(!near(workPos(due[3],due[4],due[5])))return;
            var block=lightBlock(state.getString("lightItem"),due[6],due[7]);UUID place=Settlement.childId(id,"light");
            boolean set=WorldJournal.recoverExisting(level,place)!=null||level.getBlockState(at).isAir()&&block.canSurvive(level,at)&&WorldJournal.place(level,place,at,Blocks.AIR.defaultBlockState(),block);
            if(set){held--;state.putInt("lightsHeld",held);if(held==0)state.remove("lightItem");worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                var sound=block.getSoundType();level.playSound(null,at,sound.getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,(sound.getVolume()+1)/2f,sound.getPitch()*.8f);}
            else status("light_blocked");
            state.putIntArray("lightsDue",Arrays.copyOfRange(due,8,due.length));
            state.putString("stage",due.length>=16&&held>0?"light":"choose");state.putUUID("operation",UUID.randomUUID());save();return;
        }
        if(stage.equals("stair")){
            // AD-122 (owner): the stairs of the step just finished, cut from the stone he carries (one block a stair), each set under its own
            // journal id and paid from the batch in the same record write; a cell not open any more is passed by without paying.
            var beforeRecovery=state.copy();MineStairWork.reconcile(level,state);MineStairWork.selectUnpaid(level,state,level.getBlockEntity(output) instanceof Container chest?chest:null);if(!state.equals(beforeRecovery))save();
            int step=state.getInt("stairStep"),placed=state.getInt("stairPlaced");var cells=MineDrive.stairs(step,MineWork.shape(state));
            Item stone=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new net.minecraft.resources.ResourceLocation(state.getString("stairItem")));
            if(placed>=cells.size()){endStairs(level);return;}
            var cell=cells.get(placed);BlockPos at=workPos(cell.x(),cell.y(),cell.z());
            if(!level.hasChunkAt(at)){status("unloaded");return;}
            if(!level.getBlockState(at).isAir()){state.putInt("stairPlaced",placed+1);save();return;}
            if(count(state.getList("cargo",Tag.TAG_COMPOUND),stone)<1){
                // Nothing left to cut them from (the stone went home before the step ended): the mine chest gives it, as the hall gives timber.
                if(!near(beside(output)))return;
                if(state.getBoolean("stairTaken")){state.putInt("stairTakeRound",state.getInt("stairTakeRound")+1);state.remove("stairTaken");save();}
                UUID take=MineStairWork.takeId(state);ItemStack got=WorldJournal.recoverAmount(level,take);
                if(got.isEmpty()&&!WorldJournal.exists(level,take)&&level.getBlockEntity(output) instanceof Container c)
                    for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(stone)){var stack=c.getItem(slot);got=WorldJournal.takeAmount(level,take,output,slot,stack.copy(),Math.min(cells.size()-placed,stack.getCount()));break;}
                if(got.isEmpty()){status("missing_stair_stone");save();return;}
                state.put("cargo",carry(state.getList("cargo",Tag.TAG_COMPOUND),List.of(got),Blocks.AIR.defaultBlockState(),-1));state.putBoolean("stairTaken",true);save();return;
            }
            var stand=MineDrive.next(new MineDrive.Drive(step,0,MineDrive.EAST,0),Integer.MAX_VALUE/2,MineWork.shape(state)).stand();
            if(!near(workPos(stand.x(),stand.y(),stand.z())))return;
            var mine=SettlementData.get(worker.getServer()).entry(worker.settlementId()).settlement().workplace(worker.getUUID());
            var block=BuildingPlacement.state(MineStairWork.stairs(stone).defaultBlockState().setValue(StairBlock.FACING,net.minecraft.core.Direction.NORTH),mine.rotation());
            if(block.getCollisionShape(level,at).bounds().move(at).intersects(worker.getBoundingBox())){status("needs_access");return;}
            UUID place=Settlement.childId(id,"stair/"+placed);
            boolean set=WorldJournal.recoverExisting(level,place)!=null||level.getBlockState(at).isAir()&&WorldJournal.place(level,place,at,Blocks.AIR.defaultBlockState(),block);
            if(set){state.put("cargo",without(state.getList("cargo",Tag.TAG_COMPOUND),stone,1));worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                var sound=block.getSoundType();level.playSound(null,at,sound.getPlaceSound(),net.minecraft.sounds.SoundSource.BLOCKS,(sound.getVolume()+1)/2f,sound.getPitch()*.8f);}
            state.putInt("stairPlaced",placed+1);status("setting_stairs");save();return;
        }
        if(stage.equals("sapling")){
            Item seed=farmer?FarmCrops.pending(state).seed:planted();
            BlockPos source=state.contains("plantSource")?BlockPos.of(state.getLong("plantSource")):stock;
            if(!state.contains("plantSource")){
                if(farmer&&level.getBlockEntity(output) instanceof Container c&&c.countItem(seed)>0)source=output;
                state.putLong("plantSource",source.asLong());save();
            }
            if(!near(source.east()))return;
            ItemStack sapling=WorldJournal.recoverTake(level,id);
            if(sapling.isEmpty()&&!WorldJournal.exists(level,id)&&level.getBlockEntity(source) instanceof Container c)
                for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(seed)){sapling=WorldJournal.take(level,id,source,slot,c.getItem(slot).copy());break;}
            if(sapling.isEmpty()){
                if(farmer&&!WorldJournal.exists(level,id)&&source.equals(output)){state.putLong("plantSource",stock.asLong());save();}
                if(farmer&&source.equals(stock)&&!WorldJournal.exists(level,id)){state.putBoolean("seekSeedHarvest",true);state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();}
                status(farmer?"missing_seeds":"missing_sapling");return;
            }
            state.putString("stage","plant");save();return;
        }
        if(stage.equals("plant")){
            UUID placement=state.hasUUID("plantPlacement")?state.getUUID("plantPlacement"):Settlement.childId(id,"plant");
            if(WorldJournal.recoverExisting(level,placement)!=null){finishPlant();return;}
            BlockPos target=BlockPos.of(state.getLong("target"));
            var planting=(farmer?FarmCrops.pending(state).block:saplingBlock()).defaultBlockState();
            if(farmer&&(!level.getBlockState(target).isAir()||!planting.canSurvive(level,target))){
                var entry=SettlementData.get(worker.getServer()).entry(worker.settlementId());
                var alternate=FarmWorkArea.cells(level,entry,worker.getUUID()).stream().filter(level::hasChunkAt).filter(p->level.getBlockState(p).isAir()&&planting.canSurvive(level,p)).min(Comparator.comparingDouble(p->p.distSqr(worker.blockPosition())));
                if(alternate.isEmpty()){status("occupied_planting_site");return;}
                state.putLong("target",alternate.get().asLong());state.putUUID("plantPlacement",UUID.randomUUID());save();return;
            }
            if(!near(target))return;
            if(!planting.canSurvive(level,target)){status("invalid_planting_ground");return;}
            if(!WorldJournal.place(level,placement,target,Blocks.AIR.defaultBlockState(),planting)){status("occupied_planting_site");return;}
            if(farmer)ResearchKnobs.feedCrop(level,SettlementData.get(worker.getServer()).entry(worker.settlementId()),output,target,placement);
            finishPlant();return;
        }
        if(stage.equals("till")){
            BlockPos target=BlockPos.of(state.getLong("target"));
            // A completed stroke is paid once even if farmland moisture or the crop changed before the checkpoint.
            if(WorldJournal.recoverExisting(level,id)==null){
                if(!near(target.above()))return;
                var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));
                if(!level.getBlockState(target.above()).isAir()||!FarmCrops.pending(state).canPrepare(level,target.above())||!level.getBlockState(target).equals(before)){retryTilling();return;}
                if(worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>16){status("needs_access");return;}
                int labor=state.getInt("labor")+20;state.putInt("labor",labor);worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if(labor<60){status("tilling");save();return;}
                if(!WorldJournal.place(level,id,target,before,FarmCrops.pending(state).soil())){retryTilling();return;}
            }
            ItemStack tool=ItemStack.of(state.getCompound("tool"));tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;
            state.put("tool",tool.save(new CompoundTag()));state.putString("stage",tool.isEmpty()?"tool":"choose");state.putUUID("operation",UUID.randomUUID());save();return;
        }
        if(stage.equals("replant")){
            // AD-104 P2: a plot is sown from a seed of the batch — the one just reaped at once, where the farmer stands. The seed is spent in the
            // same record write that leaves this stage, so a replay after a crash finds the sowing by its id and spends the seed once.
            UUID placement=Settlement.childId(id,"replant");BlockPos target=BlockPos.of(state.getLong("target"));var crop=FarmCrops.pending(state);
            boolean sown=WorldJournal.recoverExisting(level,placement)!=null;
            if(!sown&&count(state.getList("cargo",Tag.TAG_COMPOUND),crop.seed)>0){
                if(!near(target))return;
                var planting=crop.block.defaultBlockState();
                if(level.getBlockState(target).is(Blocks.AIR)&&planting.canSurvive(level,target))sown=WorldJournal.place(level,placement,target,Blocks.AIR.defaultBlockState(),planting);
            }
            // A sowing the world refused is a call with nothing done: were the refusal to last, the batch still goes home.
            if(sown){state.put("cargo",without(state.getList("cargo",Tag.TAG_COMPOUND),crop.seed,1));idleCalls=0;}
            else if(++idleCalls>=WAIT_CALLS){startDelivery();return;}
            state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();return;
        }
        if(stage.equals("choose")){
            BlockPos target=null;
            if(miner){
                // AD-112: MineDrive picks the cell — the stair down to the floor of the mine's working level, then its galleries.
                // AD-122: a full batch, a worn-out pick, the evening or an interruption send what he carries to the chest before the next cell.
                if(state.contains("stairStep")){beginStairs(level);return;}
                if(lightReady()){state.putString("stage","light");state.putUUID("operation",UUID.randomUUID());save();return;}
                if(carrying()&&batchDue()){startDelivery();return;}
                var mineEntry=SettlementData.get(worker.getServer()).entry(worker.settlementId());var mine=mineEntry.settlement().workplace(worker.getUUID());
                var next=MineWork.next(level,mineEntry,mine,state);MineWork.chose(state,next);
                // A cell he cannot dig now ends the batch too: it goes to the chest first, the cell is chosen again after (its beam with it).
                if(next.floor()){if(carrying()){deliverUndug();return;}if((withoutPlayers||worker.tickCount%100==0)&&MineProspecting.begin(level,mineEntry,mine,state)){save();return;}if(near(beside(output)))status("mine_floor");return;}
                target=MineWork.at(mineEntry,mine,next.cell());
                if(target.getY()<=level.getMinBuildHeight()+2){if(carrying()){deliverUndug();return;}status("bottom_of_world");return;}
                if(!level.hasChunkAt(target)){if(carrying()){deliverUndug();return;}status("unloaded");return;}
                var block=level.getBlockState(target);
                // An unsafe gallery cell (not ground, a block entity, a fluid in it or beside it) ends that gallery: the drive turns to the other side.
                if(MineWork.gallery(state)&&(!MineWork.galleryFloor(level,mineEntry,mine,state,next.cell())||MineWork.unsafeGallery(level,target))){MineWork.blocked(state);save();return;}
                // An open cell the drive passes; one that asks for a beam sends the batch to the chest first — the timber is fetched from the hall after.
                if(block.isAir()||MineWork.builtLining(level,mineEntry,mine,target,state)){if(MineWork.gallery(state))MineWork.claim(level,mineEntry,mine,state);advanceMine();afterCell(level);return;}
                // Only recognized soil, stone and ore; reject containers, structural wood and fluids.
                if(!MineWork.diggable(level,mineEntry,mine,target,state)){
                    if(carrying()){deliverUndug();return;}
                    // Walking out first: the status is the stop only once he stands at the chest (a status flip a call wrote the record twice).
                    if(near(beside(output)))status("unsafe_ground");return;
                }
            }else if(farmer){
                var entry=SettlementData.get(worker.getServer()).entry(worker.settlementId());
                var plots=FarmWorkArea.cells(level,entry,worker.getUUID()).stream().filter(level::hasChunkAt).sorted(Comparator.comparingDouble(p->worker.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5))).toList();
                boolean missingSeed=false;
                var policies=org.villageastra.server.FarmPolicies.get(worker.getServer());
                // AD-104 P2: with a batch in hand he only sows from it and reaps near the last plot he worked, until it goes home (batch()).
                if(!state.getList("cargo",Tag.TAG_COMPOUND).isEmpty()){target=batch(level,entry,plots,policies);if(target==null)return;}
                // A meal shortage comes before extending the field. The harvested
                // plot still follows the usual immediate, paid replant stage.
                else if(HandBread.open(level,entry))for(var pos:plots){var mature=FarmCrops.harvest(level,pos);if(mature!=null){target=mature;break;}}
                if(target==null&&state.getList("cargo",Tag.TAG_COMPOUND).isEmpty())for(var pos:plots)if(level.getBlockState(pos).isAir()){
                    var crop=policies.at(entry,pos);var planting=crop.block.defaultBlockState();
                    boolean seedAvailable=(level.getBlockEntity(output) instanceof Container c&&c.countItem(crop.seed)>0)||(level.getBlockEntity(stock) instanceof Container seedStock&&seedStock.countItem(crop.seed)>0);
                    if(!seedAvailable){missingSeed=true;continue;}
                    if(planting.canSurvive(level,pos)){
                        state.putString("crop",crop.id());state.putLong("target",pos.asLong());state.remove("plantSource");state.putString("stage","sapling");state.putUUID("operation",UUID.randomUUID());save();return;
                    }
                    if(crop.canPrepare(level,pos)&&!level.getBlockState(pos.below()).is(crop.soil().getBlock())){
                        state.putString("crop",crop.id());state.putLong("target",pos.below().asLong());state.put("before",NbtUtils.writeBlockState(level.getBlockState(pos.below())));state.putString("stage","till");state.putInt("labor",0);state.putUUID("operation",UUID.randomUUID());save();return;
                    }
                }
                if(target==null)for(var pos:plots){var mature=FarmCrops.harvest(level,pos);if(mature!=null){target=mature;break;}}
                if(target==null){status(missingSeed?"missing_seeds":"waiting_for_crops");return;}

                state.remove("seekSeedHarvest");
            }
            var before=level.getBlockState(target);ItemStack tool=ItemStack.of(state.getCompound("tool"));
            if(before.requiresCorrectToolForDrops()&&!tool.isCorrectToolForDrops(before)){if(miner&&carrying()){deliverUndug();return;}state.put("requiredToolState",NbtUtils.writeBlockState(before));state.putString("stage","upgrade_tool");status("tool_tier");save();return;}
            if(level.getBlockEntity(target)!=null||!before.getFluidState().isEmpty()){unfit("unsafe_ground");return;}
            if(miner){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());if(MineSealing.begin(level,e.settlement().workplace(worker.getUUID()),state,target)){save();return;}}
            for(var direction:net.minecraft.core.Direction.values())if(!level.getFluidState(target.relative(direction)).isEmpty()){unfit("fluid_boundary");return;}
            if(farmer){
                // AD-104 P2: of wheat's or beetroot's spare seed he carries home only what keeps SEED_KEEP in the farm chest, and the one he sows
                // again; the rest stays in the soil. Fixed here, before the reaping, so a replay of the harvest carries exactly the same.
                Item spare=spareSeed(before);int allowance=-1;
                if(spare!=null){var crop=policy(target);allowance=Math.max(0,SEED_KEEP-(level.getBlockEntity(output) instanceof Container c?c.countItem(spare):0)-count(state.getList("cargo",Tag.TAG_COMPOUND),spare))+(crop!=null&&crop.seed==spare?1:0);}
                state.putInt("seedAllowance",allowance);
            }
            state.putLong("target",target.asLong());state.put("before",NbtUtils.writeBlockState(before));state.putString("stage","dig");state.putInt("labor",0);save();return;
        }
        if(stage.equals("dig")){
            BlockPos target=BlockPos.of(state.getLong("target"));
            // Maintenance can wear out the pick while a deeper face is already queued.
            // Recover a committed harvest first; otherwise never destroy ore with an absent or unsuitable tool.
            if(miner&&!WorldJournal.exists(level,id)){
                var queued=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));
                if(queued.requiresCorrectToolForDrops()&&!ItemStack.of(state.getCompound("tool")).isCorrectToolForDrops(queued)){
                    if(carrying()){deliverUndug();return;}
                    state.put("requiredToolState",NbtUtils.writeBlockState(queued));state.putString("stage","upgrade_tool");status("tool_tier");save();return;
                }
            }
            // Water may move while the miner walks to the face. Re-plan before breaking,
            // while a harvest already committed to the journal must still be recovered.
            if(miner&&!WorldJournal.exists(level,id)&&(!level.getFluidState(target).isEmpty()||Arrays.stream(net.minecraft.core.Direction.values()).anyMatch(d->!level.getFluidState(target.relative(d)).isEmpty()))){
                uncrack();state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());state.putInt("labor",0);save();status("fluid_boundary");return;
            }
            BlockPos access=miner?minerAccess():target;
            if(farmer&&level.getBlockState(target).is(Blocks.SUGAR_CANE)){access=target;for(int n=0;n<3&&level.getBlockState(access.below()).is(Blocks.SUGAR_CANE);n++)access=access.below();}
            if(!near(access))return;
            if(worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>16){
                if(withoutPlayers||worker.tickCount%20==0){var path=worker.getNavigation().createPath(access,0);
                if(path!=null)worker.getNavigation().moveTo(path,.8);}
                status("needs_access");return;
            }
            ItemStack tool=ItemStack.of(state.getCompound("tool"));
            if(miner){
                // AD-122: the miner's labour is ticks at the face, and the block breaks after a player's time with his own tool. The count lives
                // in memory between the one-second saves: a restart only starts this block's swing again, the harvest stays journalled once.
                var rock=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));
                int labor=state.getInt("labor")+1,ticks=MinerSpeed.breakTicks(rock,level,target,tool);state.putInt("labor",labor);
                // As a player's: the arm swings, the crack grows over the break time and the block's hit sound plays.
                if(labor<ticks){status("working");if(withoutPlayers||worker.tickCount%20==0)save();crack(level,target,rock,labor,ticks);return;}
            }else{
            // AD-112: the forester's cut is his lodge's felling labour (its core's level).
            int labor=state.getInt("labor")+20;state.putInt("labor",labor);
            if(labor<80){status("working");save();worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);return;}
            }
            var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("before"));
            var loot=WorldJournal.harvest(level,id,target,before,tool);
            if(loot==null){uncrack();if(farmer){state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();}status("changed_target");return;}
            if(miner){cracking=null;MinerSpeed.broken(level,worker,target,before);}
            if(miner){if(MineWork.gallery(state)){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());MineWork.claim(level,e,e.settlement().workplace(worker.getUUID()),state);}else noteArea(state.getInt("step"));}
            state.putUUID("lastHarvest",id);
            if(farmer){
                // AD-104 P2: reaping a crop wears no hoe — vanilla wears a tool only on a block that takes time to break; tilling still does. The
                // loot joins the batch, and a crop's plot is sown again at once from its own seed, under this operation (stage replant).
                ListTag carried=carry(state.getList("cargo",Tag.TAG_COMPOUND),loot,before,state.contains("seedAllowance")?state.getInt("seedAllowance"):-1);
                state.put("cargo",carried);state.remove("seedAllowance");state.putInt("reaped",state.getInt("reaped")+1);idleCalls=0;
                var crop=before.getBlock() instanceof CropBlock?policy(target):null;
                if(crop!=null&&crop.block instanceof CropBlock&&count(carried,crop.seed)>0){state.putString("crop",crop.id());state.putString("stage","replant");}
                else{state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());}
                save();return;
            }
            tool.setDamageValue(tool.getDamageValue()+1);if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;
            if(miner){
                // AD-122: the block joins the batch he carries and the drive moves on in the same record write; he digs the next cell of
                // the drive, and the batch goes to the chest when it is full, when this cell asks for a beam or when the pick is worn out.
                state.put("cargo",carry(state.getList("cargo",Tag.TAG_COMPOUND),loot,before,-1));state.put("tool",tool.save(new CompoundTag()));
                advanceMine();afterCell(level);return;
            }
            ListTag cargo=new ListTag();for(ItemStack stack:loot)cargo.add(stack.save(new CompoundTag()));state.put("cargo",cargo);
            state.put("tool",tool.save(new CompoundTag()));state.putString("stage","deliver");state.putInt("delivered",0);save();return;
        }
        if(stage.equals("deliver")){
            if(!near(beside(output)))return;
            ListTag cargo=state.getList("cargo",Tag.TAG_COMPOUND);
            // AD-104 P2: the farmer puts his whole batch into the farm chest in one visit; the miner and the forester bring one stack a call.
            for(int index=state.getInt("delivered");index<cargo.size();index++){
                ItemStack item=ItemStack.of(cargo.getCompound(index));
                if(!WorldJournal.deposit(level,Settlement.childId(id,"delivery/"+index),output,item)){status("output_full");return;}
                state.putInt("delivered",index+1);save();if(!farmer&&!miner)return;
            }
            // AD-122: a batch's cells moved the drive on as they were dug ("advanced"); a record from before carries one cell not stepped yet.
            if(miner&&!state.getBoolean("advanced"))advanceMine();
            state.remove("advanced");
            boolean needsBeam=miner&&MineWork.needsBeam(state);
            state.putString("stage",needsBeam?"support_fetch":ItemStack.of(state.getCompound("tool")).isEmpty()?"tool":"choose");state.putUUID("operation",UUID.randomUUID());state.remove("cargo");save();
        }
    }
    /** Labour ticks to fell a tree of this many logs from its foot: three seconds a log, never less than a single block took. */
    public static int treeLabor(int logs){return Math.max(80,60*logs);}
    // ---------- AD-131: the forester of a hut I..VI ----------
    /** The working level of the forester's hut. */
    private int hutLevel(){var e=SettlementData.get(worker.getServer()).entry(worker.settlementId());var hut=e==null?null:e.settlement().workplace(worker.getUUID());return hut==null?1:BuildingLevels.level((ServerLevel)worker.level(),e,hut);}
    /** Game ticks the search waits after a whole round found no tree (in memory). */
    private long forestWait;

    private static List<BlockPos> longs(long[] a){var out=new ArrayList<BlockPos>();for(long v:a)out.add(BlockPos.of(v));return out;}
    private static Item item(String id){var key=net.minecraft.resources.ResourceLocation.tryParse(id);return key!=null&&net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)?net.minecraft.core.registries.BuiltInRegistries.ITEM.get(key):Items.AIR;}
    private static String id(Item item){return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();}
    /** The day's counts of the hut's card: trees felled and saplings set today. */
    private void today(ServerLevel level){long day=level.getDayTime()/24000L;if(state.getLong("felledDay")!=day){state.putLong("felledDay",day);state.putInt("felledToday",0);state.putInt("plantedToday",0);}}
    /** The trip goes home: the trees it holds, three stacks' worth of items, the evening or the night, or a worn-out axe. */
    private boolean tripDue(int level){long day=Math.floorMod(dayTime.getAsLong(),24000L);var cargo=state.getList("cargo",Tag.TAG_COMPOUND);
        return state.getInt("trees")>=ForestBalance.treesPerTrip(level)||count(cargo,null)>=3*64||ItemStack.of(state.getCompound("tool")).isEmpty()||day>=SleepGoal.DUSK-EVENING&&day<SleepGoal.DAWN;}
    private void forestDelivery(){state.remove("forestDeliveryAt");state.putString("stage","deliver");state.putInt("delivered",0);state.putUUID("operation",UUID.randomUUID());status("carrying_trees");save();}
    /** What the village has of an item for the forester: in his load, the hut chest, the hall stock. */
    private int have(ServerLevel level,Item item,boolean load){int n=load?count(state.getList("cargo",Tag.TAG_COMPOUND),item):0;
        if(level.getBlockEntity(output) instanceof Container c)n+=c.countItem(item);if(level.getBlockEntity(stock) instanceof Container c)n+=c.countItem(item);return n;}
    private Set<Item> atHand(ServerLevel level){var out=new HashSet<Item>();for(var id:ForestWork.PLANTED){var it=item(id);if(have(level,it,true)>0)out.add(it);}return out;}
    private Set<Item> carried(){var out=new HashSet<Item>();for(var s:stacks(state.getList("cargo",Tag.TAG_COMPOUND)))if(ForestWork.PLANTED.contains(id(s.getItem())))out.add(s.getItem());return out;}
    /** The kind for a foot: II the kind felled there (from his own crown, opened or not); III+ the kind least planted round the hut. */
    private Item kindFor(ServerLevel level,SettlementData.Entry e,org.villageastra.domain.Settlement.Building hut,int lv,Item felled){
        if(lv<3)return felled;var kind=ForestWork.nextSpecies(level,e,hut,lv,atHand(level),carried());return kind==null?felled:kind;}
    private void forest(ServerLevel level,String stage,UUID id){
        var e=SettlementData.get(level.getServer()).entry(worker.settlementId());var hut=e.settlement().workplace(worker.getUUID());int lv=BuildingLevels.level(level,e,hut);today(level);
        switch(stage){
            case "nursery_soil"->forestSoil(level,e);
            case "choose"->forestChoose(level,e,hut,lv);
            case "dig"->forestDig(level,e,hut,lv,id);
            case "replant"->forestReplant(level,e,hut,lv,id);
            case "sapling"->forestSapling(level,id);
            case "deliver"->forestDeliver(level,lv,id);
            default->{state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();}
        }
    }
    private void forestSoil(ServerLevel level,SettlementData.Entry e){
        if(NurserySoil.committed(level,state)){NurserySoil.prepare(level,state);save();return;}
        if(!state.getBoolean("soilHeld")){
            if(!near(beside(stock)))return;
            if(!NurserySoil.fetch(level,e,state,stock)){status("missing_building_materials");return;}save();
        }
        var target=BlockPos.of(state.getLong("target"));
        if(!ForestRenewal.sandy(level,target)){state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();return;}
        if(!near(target))return;status("replanting");state.putInt("soilLabor",state.getInt("soilLabor")+1);
        if(state.getInt("soilLabor")<20){save();return;}
        if(NurserySoil.prepare(level,state))worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);save();
    }
    /** A lost last axe must not make the wood needed to craft its replacement unobtainable. */
    private boolean handFellingNeeded(ServerLevel l){
        var e=SettlementData.get(l.getServer()).entry(worker.settlementId());int wood=0,sticks=0;
        for(var b:e.settlement().buildings()){
            var c=LogisticsRoutes.chest(l,e,b);if(c!=null)for(int i=0;i<c.getContainerSize();i++){
                var item=c.getItem(i);if(item.is(net.minecraft.tags.ItemTags.AXES)&&item.getDamageValue()<item.getMaxDamage())return false;
                if(!b.type().equals("town_hall")&&!b.type().equals("warehouse")&&!b.type().equals("forester"))continue;
                int free=Math.max(0,item.getCount()-LogisticsRoutes.reserve(b,item));
                if(item.is(net.minecraft.tags.ItemTags.LOGS))wood+=4*free;else if(item.is(net.minecraft.tags.ItemTags.PLANKS))wood+=free;else if(item.is(Items.STICK))sticks+=free;
            }
            if(b.type().equals("forester")){var f=MineWork.path(l,b.id());if(Files.exists(f)){var axe=ItemStack.of(org.villageastra.persistence.NbtRecord.read(f).getCompound("tool"));if(axe.is(net.minecraft.tags.ItemTags.AXES)&&axe.getDamageValue()<axe.getMaxDamage())return false;}}
        }
        return wood<3+(sticks>=2?0:2);
    }
    private void forestChoose(ServerLevel level,SettlementData.Entry e,org.villageastra.domain.Settlement.Building hut,int lv){
        boolean carrying=!state.getList("cargo",Tag.TAG_COMPOUND).isEmpty();
        if(ItemStack.of(state.getCompound("tool")).isEmpty()&&!state.getBoolean("handFelling")){if(carrying){forestDelivery();return;}state.putString("stage","tool");state.putUUID("operation",UUID.randomUUID());save();return;}
        if(carrying&&tripDue(lv)){forestDelivery();return;}
        // II+: a bare foot is planted first, as soon as a sapling of its kind is at hand (his load, the hut chest, the hall).
        if(lv>=2&&!state.getList("bare",Tag.TAG_COMPOUND).isEmpty()){
            var bare=state.getList("bare",Tag.TAG_COMPOUND);ListTag kept=new ListTag();CompoundTag pick=null;Item kind=null;double best=Double.MAX_VALUE;
            for(int i=0;i<bare.size();i++){var b=bare.getCompound(i);var foot=BlockPos.of(b.getLong("pos"));
                if(level.hasChunkAt(foot)&&(!level.getBlockState(foot).isAir()||!level.getBlockState(foot.below()).is(BlockTags.DIRT)))continue;kept.add(b);
                var k=kindFor(level,e,hut,lv,item(b.getString("kind")));if(k==Items.AIR||have(level,k,true)==0)continue;
                double d=foot.distSqr(worker.blockPosition());if(d<best){best=d;pick=b;kind=k;}}
            if(kept.size()!=bare.size())state.put("bare",kept);
            if(pick!=null){var foot=BlockPos.of(pick.getLong("pos"));state.putLong("target",foot.asLong());state.putLongArray("plantCells",new long[]{foot.asLong()});state.putString("species",id(kind));state.putBoolean("fromBare",true);
                state.putString("stage",count(state.getList("cargo",Tag.TAG_COMPOUND),kind)>0?"replant":"sapling");state.putUUID("operation",UUID.randomUUID());save();return;}
        }
        long day=Math.floorMod(dayTime.getAsLong(),24000L);if(day>=SleepGoal.DUSK-EVENING&&day<SleepGoal.DAWN){status("evening");return;}
        long now=level.getGameTime();if(now<forestWait&&!withoutPlayers){status("no_trees_in_reach");return;}
        boolean onTrip=state.getInt("trees")>0&&state.contains("lastFoot");
        // The nearest wild tree to his door (the spiral's order); on a trip of V+ the next within NEXT_TREE_REACH of the last foot.
        var tree=ForestWork.next(level,e,hut,lv,onTrip?BlockPos.of(state.getLong("lastFoot")):ForesterHut.door(e,hut),onTrip?ForestBalance.NEXT_TREE_REACH:0,now);
        if(tree==null){
            if(carrying){forestDelivery();return;}
            if(lv==1&&ForestWork.searched(hut.id())){boolean renewal=ForestRenewal.plan(level,e,hut,worker,state,atHand(level));save();if(renewal){status("replanting");return;}if(state.getBoolean("renewalPending")){status("seeking_trees");return;}}
            if(ForestWork.searched(hut.id())){forestWait=now+600;status("no_trees_in_reach");}else status("seeking_trees");return;}
        if(!carrying&&tree.sapling()!=null&&!forestRoom(level,output,new ItemStack(tree.sapling()))){status("output_full");return;}
        state.putLongArray("base",tree.base().stream().mapToLong(BlockPos::asLong).toArray());
        // A route that is planned and ends short of the trunk refuses that foot for a day. No route at all (the navigator plans none in the air
        // or over ground it has not loaded) proves nothing: he sets out, and the walk itself shows.
        var side=trunkSide(tree.foot());var path=worker.onGround()?worker.getNavigation().createPath(side,1):null;
        if(path!=null&&!path.canReach()){ForestWork.refuse(hut.id(),tree.foot(),now);state.remove("base");status("seeking_trees");return;}
        var befores=new ListTag();for(var log:tree.logs())befores.add(NbtUtils.writeBlockState(level.getBlockState(log)));
        state.putLong("target",tree.foot().asLong());state.putLongArray("tree",tree.logs().stream().mapToLong(BlockPos::asLong).toArray());state.put("treeBefore",befores);
        state.putLongArray("treeLeaves",tree.leaves().stream().mapToLong(BlockPos::asLong).toArray());
        state.putString("felledKind",tree.sapling()==null?"":id(tree.sapling()));
        state.putString("stage","dig");state.putInt("labor",0);status("walking_to_tree");save();
    }
    private void forestDig(ServerLevel level,SettlementData.Entry e,org.villageastra.domain.Settlement.Building hut,int lv,UUID id){
        BlockPos target=BlockPos.of(state.getLong("target"));BlockPos access=trunkSide(target);
        if(!near(access))return;
        if(worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))>16){
            if(withoutPlayers||worker.tickCount%20==0){var path=worker.getNavigation().createPath(access,0);if(path!=null)worker.getNavigation().moveTo(path,ForestBalance.walkSpeed(lv));}
            status("needs_access");return;}
        ItemStack tool=ItemStack.of(state.getCompound("tool"));
        int labor=state.getInt("labor")+fellingLabor(lv);state.putInt("labor",labor);int total=treeLabor(state.getLongArray("tree").length)*(state.getBoolean("handFelling")?6:1);
        if(labor<total){status("felling");save();worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);var foot=level.getBlockState(target);cracking=target;MinerSpeed.progress(level,worker,target,foot,labor,total);return;}
        fellTree(level,e,hut,lv,id,tool);
    }
    /** The long chop is done: every log and the crown's leaves come down in one journal batch (a replay takes none twice), the axe wears a
     *  point a log as far as it lasts; the loot (logs, saplings, sticks, apples) joins his load. */
    private void fellTree(ServerLevel level,SettlementData.Entry e,org.villageastra.domain.Settlement.Building hut,int lv,UUID id,ItemStack axe){
        ItemStack tool=axe;final ItemStack cutter=axe.copy();
        var logs=state.getLongArray("tree");var befores=state.getList("treeBefore",Tag.TAG_COMPOUND);var leaves=state.getLongArray("treeLeaves");
        // AD-131: the tree he began comes down whole, and the axe breaks on the last log it can take (as a player's does) — a trunk left
        // half standing has no foot on soil and no crown left, so no forester would ever find it again.
        int usable=tool.isEmpty()?(state.getBoolean("handFelling")?Integer.MAX_VALUE:0):tool.getMaxDamage()-tool.getDamageValue();var loot=new ArrayList<ItemStack>();int[] felled={0};
        if(usable<=0){for(var k:List.of("tree","treeBefore","treeLeaves","labor","base"))state.remove(k);uncrack();
            if(!state.getList("cargo",Tag.TAG_COMPOUND).isEmpty()){forestDelivery();return;}
            state.putString("stage","tool");state.putUUID("operation",UUID.randomUUID());save();status("missing_tool");return;}
        WorldJournal.batch(level,()->{
            for(int i=0;i<logs.length;i++){
                var before=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),befores.getCompound(i));
                var got=WorldJournal.harvest(level,Settlement.childId(id,"log/"+i),BlockPos.of(logs[i]),before,cutter);if(got==null)continue;MinerSpeed.broken(level,worker,BlockPos.of(logs[i]),before);
                loot.addAll(got);felled[0]++;}
            if(felled[0]>0)for(int j=0;j<leaves.length;j++){var at=BlockPos.of(leaves[j]);var leafId=Settlement.childId(id,"leaf/"+j);var now=level.getBlockState(at);
                if(!WorldJournal.exists(level,leafId)&&!(now.getBlock() instanceof LeavesBlock&&!now.getValue(LeavesBlock.PERSISTENT)))continue;
                var got=WorldJournal.harvest(level,leafId,at,now,cutter);if(got!=null)loot.addAll(got);}
            return null;});
        uncrack();
        var foot=BlockPos.of(state.getLong("target"));var base=state.contains("base")?longs(state.getLongArray("base")):List.of(foot);
        for(var k:List.of("tree","treeBefore","treeLeaves","labor"))state.remove(k);
        if(felled[0]==0){state.remove("base");state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();status("changed_target");return;}
        state.putUUID("lastHarvest",id);state.put("cargo",carry(state.getList("cargo",Tag.TAG_COMPOUND),loot,Blocks.AIR.defaultBlockState(),-1));
        tool.setDamageValue(Math.min(tool.getMaxDamage(),tool.getDamageValue()+felled[0]));if(tool.getDamageValue()>=tool.getMaxDamage())tool=ItemStack.EMPTY;state.put("tool",tool.save(new CompoundTag()));
        state.putInt("trees",state.getInt("trees")+1);state.putInt("felledToday",state.getInt("felledToday")+1);state.putInt("logsFelled",state.getInt("logsFelled")+felled[0]);state.putLong("lastFoot",foot.asLong());
        // II+: the foot is planted at once from his load; with none there it is remembered bare and the felling goes on.
        if(lv>=2&&level.getBlockState(foot).isAir()&&level.getBlockState(foot.below()).is(BlockTags.DIRT)){
            var felledKind=item(state.getString("felledKind"));var kind=kindFor(level,e,hut,lv,felledKind);var cells=List.of(foot);
            // A 2x2 trunk (dark oak) takes four saplings of its kind; with fewer the foot gets one of another kind.
            if(base.size()>=4&&kind==felledKind){if(count(state.getList("cargo",Tag.TAG_COMPOUND),kind)>=4)cells=base.subList(0,4);
                else{var other=atHand(level);other.remove(felledKind);var alt=lv>=3?ForestWork.nextSpecies(level,e,hut,lv,other,carried()):other.stream().findFirst().orElse(null);if(alt!=null)kind=alt;}}
            if(kind!=Items.AIR&&count(state.getList("cargo",Tag.TAG_COMPOUND),kind)>=cells.size()){
                state.putLongArray("plantCells",cells.stream().mapToLong(BlockPos::asLong).toArray());state.putString("species",id(kind));state.remove("fromBare");state.putString("stage","replant");save();return;}
            var bare=state.getList("bare",Tag.TAG_COMPOUND);if(bare.size()<ForestBalance.BARE_FEET_MAX){var b=new CompoundTag();b.putLong("pos",foot.asLong());b.putString("kind",id(felledKind==Items.AIR?Items.OAK_SAPLING:felledKind));bare.add(b);state.put("bare",bare);}
            status("felled_without_sapling");
        }
        afterTree(lv);
    }
    private void afterTree(int lv){state.remove("handFelling");for(var k:List.of("base","plantCells","species","fromBare","felledKind"))state.remove(k);
        if(tripDue(lv)){forestDelivery();return;}state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();}
    /** Sets the saplings on the foot from his load under the tree's own ids (replant, replant/k); each set is paid from the load in the same
     *  record write, so a replay finds it by its id and pays once. */
    private void forestReplant(ServerLevel level,SettlementData.Entry e,org.villageastra.domain.Settlement.Building hut,int lv,UUID id){
        var cells=longs(state.getLongArray("plantCells"));var kind=item(state.getString("species"));
        if(cells.isEmpty()||!(kind instanceof BlockItem block)){afterTree(lv);return;}
        if(!near(cells.get(0)))return;
        int planted=0;var sapling=block.getBlock().defaultBlockState();
        for(int k=0;k<cells.size();k++){var at=cells.get(k);var place=Settlement.childId(id,k==0?"replant":"replant/"+k);
            boolean set=WorldJournal.recoverExisting(level,place)!=null;
            if(!set&&count(state.getList("cargo",Tag.TAG_COMPOUND),kind)-planted>0&&level.getBlockState(at).isAir()&&sapling.canSurvive(level,at))set=WorldJournal.place(level,place,at,Blocks.AIR.defaultBlockState(),sapling);
            if(set){planted++;ForestPlantings.get(level.getServer()).recordForester(level,at,e.settlement().id(),hut.id(),id(kind),level.getGameTime());}}
        if(planted>0){state.put("cargo",without(state.getList("cargo",Tag.TAG_COMPOUND),kind,planted));state.putInt("plantedToday",state.getInt("plantedToday")+planted);worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);status("replanting");}
        if(state.getBoolean("fromBare")){var bare=state.getList("bare",Tag.TAG_COMPOUND);var kept=new ListTag();for(int i=0;i<bare.size();i++)if(bare.getCompound(i).getLong("pos")!=cells.get(0).asLong())kept.add(bare.getCompound(i));state.put("bare",kept);
            for(var k:List.of("plantCells","species","fromBare"))state.remove(k);state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();return;}
        afterTree(lv);
    }
    /** A sapling for a bare foot from the hut chest, else the hall stock, into his load under this operation's take. */
    private void forestSapling(ServerLevel level,UUID id){
        var kind=item(state.getString("species"));UUID take=Settlement.childId(id,"sapling");
        BlockPos source=state.contains("plantSource")?BlockPos.of(state.getLong("plantSource")):(level.getBlockEntity(output) instanceof Container c&&c.countItem(kind)>0?output:stock);
        if(!state.contains("plantSource")){state.putLong("plantSource",source.asLong());save();}
        if(!near(beside(source)))return;
        ItemStack got=WorldJournal.recoverTake(level,take);
        if(got.isEmpty()&&!WorldJournal.exists(level,take)&&level.getBlockEntity(source) instanceof Container c)
            for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(kind)){got=WorldJournal.take(level,take,source,slot,c.getItem(slot).copy());break;}
        state.remove("plantSource");
        if(got.isEmpty()){status("missing_sapling");for(var k:List.of("plantCells","species","fromBare"))state.remove(k);state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();return;}
        state.put("cargo",carry(state.getList("cargo",Tag.TAG_COMPOUND),List.of(got),Blocks.AIR.defaultBlockState(),-1));state.putString("stage","replant");save();
    }
    /** The load goes into the hut chest: a stack a call to IV, all of it in one visit from V. */
    private void forestDeliver(ServerLevel level,int lv,UUID id){
        boolean overflow=state.contains("forestDeliveryAt");
        var destination=overflow?BlockPos.of(state.getLong("forestDeliveryAt")):output;
        if(!near(beside(destination)))return;
        ListTag cargo=state.getList("cargo",Tag.TAG_COMPOUND);
        for(int index=state.getInt("delivered");index<cargo.size();index++){
            ItemStack item=ItemStack.of(cargo.getCompound(index));
            var delivery=Settlement.childId(id,"delivery/"+index);
            if(overflow&&!WorldJournal.exists(level,delivery)&&!forestOverflowRoom(level,destination,item)){status("output_full");return;}
            if(!WorldJournal.deposit(level,delivery,destination,item)){
                if(!overflow&&!WorldJournal.exists(level,delivery)&&!stock.equals(output)&&forestOverflowRoom(level,stock,item)){
                    state.putLong("forestDeliveryAt",stock.asLong());save();
                }
                status("output_full");return;
            }
            state.putInt("delivered",index+1);save();if(lv<5&&index+1<cargo.size())return;
        }
        for(var k:List.of("cargo","trees","lastFoot","delivered","forestDeliveryAt"))state.remove(k);
        if(overflow)status("output_full");
        state.putString("stage",ItemStack.of(state.getCompound("tool")).isEmpty()?"tool":"choose");state.putUUID("operation",UUID.randomUUID());save();
    }
    /** Emergency forestry cargo shares the pantry headroom used by ordinary bulk couriers. */
    private static boolean forestOverflowRoom(ServerLevel level,BlockPos pos,ItemStack item){
        return level.hasChunkAt(pos)&&level.getBlockEntity(pos) instanceof Container c&&LogisticsRoutes.surplusFits(c,item)&&forestRoom(level,pos,item);
    }
    /** A whole journalled stack must fit before selecting a different physical destination. */
    private static boolean forestRoom(ServerLevel level,BlockPos pos,ItemStack stack){
        if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof Container c))return false;
        int max=Math.min(c.getMaxStackSize(),stack.getMaxStackSize());
        for(int i=0;i<c.getContainerSize();i++){
            var before=c.getItem(i);
            if((before.isEmpty()||ItemStack.isSameItemSameTags(before,stack))&&before.getCount()+stack.getCount()<=max)return true;
        }
        return false;
    }
    /** Items as stacks of at most 64, one kind after another in the order they came. */
    static ListTag merged(List<ItemStack> items){var out=new ArrayList<ItemStack>();
        for(var item:items){var left=item.copy();for(var s:out)if(!left.isEmpty()&&ItemStack.isSameItemSameTags(s,left)&&s.getCount()<s.getMaxStackSize()){int move=Math.min(left.getCount(),s.getMaxStackSize()-s.getCount());s.grow(move);left.shrink(move);}
            while(!left.isEmpty()){var part=left.split(left.getMaxStackSize());out.add(part);}}
        var tag=new ListTag();for(var s:out)tag.add(s.save(new CompoundTag()));return tag;}
    private void finishPlant(){state.remove("plantSource");state.remove("plantPlacement");state.remove("sapling");state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();}
    /** AD-112: one cell of the drive done — MineDrive moves it on under the floor its target was chosen with. */
    private void advanceMine(){MineWork.step(state);}
    /** AD-122: after a cell of the drive — the stairs of a finished step first, then its beam (with the batch taken home first, as the
     *  timber is fetched from the hall), a batch that is due, or the next cell. */
    private void afterCell(ServerLevel level){
        if(state.contains("stairStep")&&carryingStone()){beginStairs(level);return;}
        if(lightReady()){state.putString("stage","light");state.putUUID("operation",UUID.randomUUID());save();return;}
        if(MineWork.needsBeam(state)){if(carrying()){startDelivery();return;}state.putString("stage","support_fetch");state.putUUID("operation",UUID.randomUUID());save();return;}
        if(carrying()&&batchDue()){startDelivery();return;}
        state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());save();
    }
    /** AD-122: a light is due and he carries one. */
    private boolean lightReady(){return state.getIntArray("lightsDue").length>=8&&state.getInt("lightsHeld")>0;}
    /** The light block: a lantern hanging from the roof, or a torch on the wall at (wallX, wallZ) of its cell, turned with the mine. */
    private BlockState lightBlock(String item,int wallX,int wallZ){
        var mine=SettlementData.get(worker.getServer()).entry(worker.settlementId()).settlement().workplace(worker.getUUID());
        var local=item.equals("minecraft:torch")?Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING,net.minecraft.core.Direction.fromDelta(-wallX,0,-wallZ)):Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true);
        return BuildingPlacement.state(local,mine.rotation());
    }
    /** AD-122: at the hall stock the miner takes lights up to LIGHTS_CARRIED — lanterns first, then torches, one kind at a time — once an
     *  operation, under its own journal id, booked in the same record write. True: this call went on it. */
    /** A tool this worker works with (and cuts the block it waits for). */
    private boolean fitsTool(ItemStack item){
        return (miner?item.getItem() instanceof PickaxeItem:farmer?item.getItem() instanceof HoeItem:item.getItem() instanceof AxeItem)&&(!state.contains("requiredToolState")||item.isCorrectToolForDrops(NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),state.getCompound("requiredToolState"))));
    }
    /** AD-155: whether the workplace's own chest holds a tool for this worker. */
    private boolean ownTool(ServerLevel level){
        if(output==null||!(level.getBlockEntity(output) instanceof Container c))return false;
        for(int slot=0;slot<c.getContainerSize();slot++)if(fitsTool(c.getItem(slot)))return true;return false;
    }
    private boolean restockLights(ServerLevel level,UUID id){
        int held=state.getInt("lightsHeld");if(!miner||MineWork.shape(state).height()<5||held>=LIGHTS_CARRIED||id.toString().equals(state.getString("lightsOp")))return false;
        UUID take=Settlement.childId(id,"lights");ItemStack got=WorldJournal.recoverAmount(level,take);
        if(got.isEmpty()&&!WorldJournal.exists(level,take)&&level.getBlockEntity(stock) instanceof Container c)
            search:for(Item kind:List.of(Items.LANTERN,Items.TORCH)){
                if(held>0&&!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(kind).toString().equals(state.getString("lightItem")))continue;
                // AD-137: lights the hall keeps for the active project are not the miner's; torches serve then.
                for(int slot=0;slot<c.getContainerSize();slot++)if(c.getItem(slot).is(kind)&&HallReserve.free(level,stock,c.getItem(slot))>0){var stack=c.getItem(slot);got=WorldJournal.takeAmount(level,take,stock,slot,stack.copy(),Math.min(LIGHTS_CARRIED-held,Math.min(stack.getCount(),HallReserve.free(level,stock,stack))));break search;}
            }
        state.putString("lightsOp",id.toString());
        if(!got.isEmpty()){state.putInt("lightsHeld",held+got.getCount());state.putString("lightItem",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(got.getItem()).toString());}
        save();return true;
    }
    private boolean carryingStone(){var cargo=state.getList("cargo",Tag.TAG_COMPOUND);return count(cargo,MineStairWork.carriedStone(cargo))>0;}
    private void retryTilling(){state.putString("stage","choose");state.putUUID("operation",UUID.randomUUID());state.putInt("labor",0);state.remove("before");state.remove("target");status("changed_target");save();}
    /** Choose available local stone for a new row before any payment; paid rows keep their material. */
    private void beginStairs(ServerLevel level){
        var cargo=state.getList("cargo",Tag.TAG_COMPOUND);var entry=SettlementData.get(worker.getServer()).entry(worker.settlementId());
        var mine=entry.settlement().workplace(worker.getUUID());
        Item stone=MineStairWork.newOrderStone(cargo,LogisticsRoutes.chest(level,entry,mine));
        state.putString("stairItem",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stone).toString());state.putInt("stairPlaced",0);state.remove("stairTaken");state.remove("stairTakeRound");
        state.putString("stage","stair");state.putUUID("operation",UUID.randomUUID());save();
    }
    private void endStairs(ServerLevel level){for(var key:List.of("stairStep","stairPlaced","stairItem","stairTaken","stairTakeRound"))state.remove(key);afterCell(level);}
    /** AD-122: a strike at the block — the swing, the crack of this share of the break time, the hit sound. */
    private void crack(ServerLevel level,BlockPos target,BlockState rock,int labor,int ticks){
        if(cracking!=null&&!cracking.equals(target))uncrack();cracking=target;
        worker.swing(net.minecraft.world.InteractionHand.MAIN_HAND);MinerSpeed.progress(level,worker,target,rock,labor,ticks);
    }
    private void uncrack(){if(cracking!=null&&worker.level() instanceof ServerLevel level)MinerSpeed.clear(level,worker,cracking);cracking=null;}
    /** Where the miner stands to dig the chosen cell: MineDrive's stand, or the tread of its step for a record from before AD-112. */
    private BlockPos minerAccess(){var a=MineWork.access(state);return a!=null?workPos(a.x(),a.y(),a.z()):workPos(3,1-state.getInt("step")-descent(),6+state.getInt("step"));}
    /** AD-112, AD-131: felling labour a call at a working level (core_levels.json forester/felling): 20/30/40/50/100/120, so a log of 60
     *  takes three calls at I and two at IV, and V doubles IV. */
    public static int fellingLabor(int level){return org.villageastra.domain.CoreEffects.value("forester","felling",level);}
    /** AD-104 P2: a call of the farmer with a batch in hand. The batch goes home first when it is full, when the hoe is gone, or in the evening
     *  (so no batch sleeps in the record). Then he sows an empty plot near him from the batch, else reaps the nearest ripe plot near him —
     *  that plot is returned for the usual checks. A ripe plot only beyond reach, or a minute with nothing to do, sends the batch home too.
     *  "Near" is REACH from the last plot he worked, so a whole module is one round. Null: this call is done. */
    private BlockPos batch(ServerLevel level,SettlementData.Entry entry,List<BlockPos> plots,org.villageastra.server.FarmPolicies policies){
        ListTag cargo=state.getList("cargo",Tag.TAG_COMPOUND);long day=Math.floorMod(dayTime.getAsLong(),24000L);
        if(count(cargo,null)>=BATCH||ItemStack.of(state.getCompound("tool")).isEmpty()||day>=SleepGoal.DUSK-EVENING&&day<SleepGoal.DUSK){startDelivery();return null;}
        BlockPos from=state.contains("target")?BlockPos.of(state.getLong("target")):worker.blockPosition();double reach=(double)REACH*REACH;
        // Deliver a real meal batch before optional sowing consumes the next round.
        if(hungryHome(level,entry,cargo)){startDelivery();return null;}
        for(var pos:plots)if(pos.distSqr(from)<=reach&&level.getBlockState(pos).is(Blocks.AIR)){
            var crop=policies.at(entry,pos);
            if(crop.block instanceof CropBlock&&count(cargo,crop.seed)>0&&crop.block.defaultBlockState().canSurvive(level,pos)){
                state.putString("crop",crop.id());state.putLong("target",pos.asLong());state.putString("stage","replant");state.putUUID("operation",UUID.randomUUID());save();return null;
            }
        }
        boolean ripe=false;
        for(var pos:plots){var mature=FarmCrops.harvest(level,pos);if(mature==null)continue;if(mature.distSqr(from)<=reach)return mature;ripe=true;}
        if(ripe||++idleCalls>=WAIT_CALLS)startDelivery();else status("waiting_with_cargo");
        return null;
    }
    /** AD-104 P2, bread margin (0.9.1): the village bakes by hand and its stores hold less than a job of wheat, so a batch with a whole unit of
     *  wheat goes home now instead of filling up. The starter village's morning harvest sat in the farmer's hands until noon while the hall had
     *  no wheat to bake the noon meal; a village fed by its mill and restaurant, or with wheat enough in store, keeps the full batch. */
    private boolean hungryHome(ServerLevel level,SettlementData.Entry entry,ListTag cargo){
        return farmer&&count(cargo,Items.WHEAT)>=HandBread.WHEAT_PER_UNIT&&HandBread.open(level,entry)
            &&HandBread.wheatAvailable(level,entry)<HandBread.WHEAT_PER_UNIT*HandBread.UNITS_PER_JOB;
    }
    /** AD-104 P2: the batch goes home — every stack into the farm chest in one visit, under a fresh operation. */
    private void startDelivery(){if(miner){state.putBoolean("advanced",true);interrupted=false;}state.putString("stage","deliver");state.putInt("delivered",0);state.putUUID("operation",UUID.randomUUID());idleCalls=0;save();}
    /** A ripe plot that cannot be worked shows why; for a farmer with a batch in hand it is also a call with nothing to do (AD-104 P2), so the batch still goes home. */
    private void unfit(String reason){if(miner&&carrying()){deliverUndug();return;}if(farmer&&!state.getList("cargo",Tag.TAG_COMPOUND).isEmpty()&&++idleCalls>=WAIT_CALLS)startDelivery();else status(reason);}
    /** AD-122: whether the miner carries dug blocks. */
    private boolean carrying(){return !state.getList("cargo",Tag.TAG_COMPOUND).isEmpty();}
    /** AD-122: the miner's batch goes to the chest now — full, the pick worn out, the evening or the night, or after an interruption. */
    private boolean batchDue(){long day=Math.floorMod(dayTime.getAsLong(),24000L);
        return count(state.getList("cargo",Tag.TAG_COMPOUND),null)>=MINER_BATCH||ItemStack.of(state.getCompound("tool")).isEmpty()||day>=SleepGoal.DUSK-MINER_EVENING&&day<SleepGoal.DAWN||interrupted;}
    /** AD-122: the batch goes to the chest before a cell chosen but not dug; its beam is noted again when the cell is chosen after the delivery. */
    private void deliverUndug(){state.remove("beam");startDelivery();}
    /** AD-122: the miner stands at the face of the block he has chosen, so the tick he may start on it need not wait for the next second. */
    private boolean atFace(){if(!state.contains("target"))return false;var access=minerAccess();var target=BlockPos.of(state.getLong("target"));
        return worker.distanceToSqr(access.getX()+.5,access.getY()+.5,access.getZ()+.5)<=6.25&&worker.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target))<=16;}
    /** The crop the farm asks of this plot, or null when the cell is no plot of any farm any more (its farm taken down meanwhile). */
    private FarmCrops policy(BlockPos pos){
        var entry=SettlementData.get(worker.getServer()).entry(worker.settlementId());
        try{return entry==null?null:org.villageastra.server.FarmPolicies.get(worker.getServer()).at(entry,pos);}catch(IllegalArgumentException notAPlot){return null;}
    }
    /** AD-104 P2: the seed a reaping gives besides its food — wheat's and beetroot's. A carrot or a potato is its own seed and food, so none is left behind. */
    static Item spareSeed(BlockState before){return before.is(Blocks.WHEAT)?Items.WHEAT_SEEDS:before.is(Blocks.BEETROOTS)?Items.BEETROOT_SEEDS:null;}
    /** AD-104 P2: one reaping's loot joins the batch, merged by item into stacks of one slot at most. Of the crop's spare seed only allowance
     *  are carried (-1: all of it); the rest stays in the soil — the harvest receipt keeps the whole vanilla loot. */
    static ListTag carry(ListTag cargo,List<ItemStack> loot,BlockState before,int allowance){
        var held=stacks(cargo);Item spare=spareSeed(before);int left=allowance;
        for(ItemStack drop:loot){
            int n=drop.getCount();
            if(allowance>=0&&spare!=null&&drop.is(spare)){n=Math.min(n,left);left-=n;}
            for(var stack:held)if(n>0&&ItemStack.isSameItemSameTags(stack,drop)&&stack.getCount()<stack.getMaxStackSize()){int k=Math.min(n,stack.getMaxStackSize()-stack.getCount());stack.grow(k);n-=k;}
            while(n>0){int k=Math.min(n,drop.getMaxStackSize());held.add(drop.copyWithCount(k));n-=k;}
        }
        return tags(held);
    }
    /** The batch less n of an item, taken from its last stacks first. */
    static ListTag without(ListTag cargo,Item item,int n){
        var held=stacks(cargo);
        for(int i=held.size()-1;i>=0&&n>0;i--)if(held.get(i).is(item)){int k=Math.min(n,held.get(i).getCount());held.get(i).shrink(k);n-=k;}
        return tags(held);
    }
    /** How many of an item the batch holds; null counts every item. */
    static int count(ListTag cargo,Item item){int n=0;for(var stack:stacks(cargo))if(item==null||stack.is(item))n+=stack.getCount();return n;}
    private static List<ItemStack> stacks(ListTag cargo){var out=new ArrayList<ItemStack>();for(Tag raw:cargo)out.add(ItemStack.of((CompoundTag)raw));return out;}
    private static ListTag tags(List<ItemStack> stacks){var out=new ListTag();for(var stack:stacks)if(!stack.isEmpty())out.add(stack.save(new CompoundTag()));return out;}
}
