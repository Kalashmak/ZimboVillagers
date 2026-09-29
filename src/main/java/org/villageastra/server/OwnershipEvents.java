package org.villageastra.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
import org.villageastra.world.StarterVillage;
import java.util.Set;
import java.util.stream.Collectors;

/** Building protection only; blast damage is deliberately not cancelled. */
@Mod.EventBusSubscriber(modid = VillageAstra.ID)
public final class OwnershipEvents {
    private static final class Layout {
    private static final Set<BlockPos> STRUCTURAL = java.util.stream.Stream.concat(
            StarterVillage.layout(BlockPos.ZERO).entrySet().stream(),StarterVillage.approaches(BlockPos.ZERO).entrySet().stream())
            .filter(e -> !e.getValue().isAir() && !e.getValue().is(Blocks.WHEAT) && !e.getValue().is(Blocks.OAK_SAPLING))
            .map(java.util.Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }
    private OwnershipEvents() {}
    private static final java.util.Map<String,Set<BlockPos>> BUILDING_BLOCKS=new java.util.HashMap<>();
    private static Set<BlockPos> structural(String type){return BUILDING_BLOCKS.computeIfAbsent(type,key->org.villageastra.world.BuildingBlueprints.layout(key,BlockPos.ZERO).entrySet().stream().filter(e->!e.getValue().isAir()&&!e.getValue().is(Blocks.OAK_SAPLING)&&!e.getValue().is(Blocks.WHEAT)).map(java.util.Map.Entry::getKey).collect(Collectors.toUnmodifiableSet()));}
    private static final java.util.function.Predicate<org.villageastra.domain.Settlement.Building> EVERY=building->true;
    public static boolean protectedBlock(ServerLevel level, BlockPos pos) {return protectedBlock(level,pos,EVERY);}
    /** AD-125: protection by the buildings {@code consider} accepts only — a moved building's own cells are not protected from its move. */
    public static boolean protectedBlock(ServerLevel level, BlockPos pos, java.util.function.Predicate<org.villageastra.domain.Settlement.Building> consider) {
        // AD-094: the castle wall is the village's as much as its buildings are.
        if (org.villageastra.world.Walls.wallBlock(level, pos)) return true;
        var data = SettlementData.get(level.getServer());
        for (var entry : data.entries()) {
            if(!entry.dimension().equals(level.dimension().location().toString()))continue;
            boolean legacy=entry.settlement().buildings().stream().anyMatch(b->b.id().equals(org.villageastra.domain.Settlement.childId(entry.settlement().id(),"house/0"))&&b.x()==10&&b.z()==0);
            // AD-125: the starter layout as a whole guards every starter cell; a move asks only about the other buildings' own bounds.
            if(legacy&&consider==EVERY&&Layout.STRUCTURAL.contains(pos.subtract(entry.center())))return true;
            for(var building:entry.settlement().buildings()){
                if(!consider.test(building))continue;
                String type=boundsKey(entry,building);
                if(bounds(type).contains(org.villageastra.world.BuildingPlacement.local(entry,building,pos)))return true;
                var area=entry.settlement().mineAreas().get(building.id());var local=org.villageastra.world.BuildingPlacement.local(entry,building,pos);
                if(area!=null&&area.contains(local.getX(),local.getY(),local.getZ(),0))return true;
                // AD-104: the field's ground (farmland and water of its modules) is the settlement's.
                if(building.type().equals("farm")&&org.villageastra.world.FarmField.ground(entry,building,pos))return true;
                // AD-130: every cell of its barn (walls, decks, lanterns, tower, roof, machinery) too.
                if(building.type().equals("farm")&&org.villageastra.world.FarmBarn.contains(entry,building,pos))return true;
                // AD-131: the forester's hut keeps its whole 15x21 lot at every level (its plans all fill the lot's ground), the courtyard grove with it.
            }
        }
        return false;
    }
    private record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        boolean contains(BlockPos p){return p.getX()>=minX&&p.getX()<=maxX&&p.getY()>=minY&&p.getY()<=maxY&&p.getZ()>=minZ&&p.getZ()<=maxZ;}
        boolean buffered(BlockPos p){return p.getX()>=minX-3&&p.getX()<=maxX+3&&p.getY()>=minY-3&&p.getY()<=maxY+3&&p.getZ()>=minZ-3&&p.getZ()<=maxZ+3;}
    }
    private static final java.util.Map<String,Bounds> BOUNDS=new java.util.HashMap<>();
    /** AD-112: the key of a building's bounds — its design at the kept level; a farm's also names the field its builders laid ("#f" level,
     *  "#w" on the west side), so its bounds grow with the land and not with the kept level alone. */
    private static String boundsKey(SettlementData.Entry entry,org.villageastra.domain.Settlement.Building building){
        String type=org.villageastra.world.BuildingTiers.layoutId(entry.settlement(),building.type(),org.villageastra.world.BuildingTiers.built(entry,building));
        if(!building.type().equals("farm"))return type;var s=entry.settlement();
        // AD-130: "#b" the barn laid (IV..VI), "#l" a village that keeps the AD-104 field table.
        return type+"#f"+s.fieldLevel(building.id())+"#b"+org.villageastra.world.FarmBarn.laid(s,building)+(org.villageastra.world.FarmField.legacy(s)?"#l":"")+(s.westField(building.id())?"#w":"");
    }
    private static Bounds bounds(String type){return BOUNDS.computeIfAbsent(type,full->{
        int mark=full.indexOf('#');String key=mark<0?full:full.substring(0,mark);
        var positions=new java.util.ArrayList<>(org.villageastra.world.BuildingBlueprints.layout(key,BlockPos.ZERO).entrySet().stream().filter(e->!e.getValue().isAir()).map(java.util.Map.Entry::getKey).toList());
        // AD-104: a farm kept at any level ("farm@N") keeps its whole field, and the three-block buffer around it; AD-112: the field its builders laid.
        if(key.equals("farm")||key.startsWith("farm@")){int field=1;boolean west=full.endsWith("#w");int f=full.indexOf("#f");
            if(f>=0){int end=full.indexOf('#',f+2);field=Integer.parseInt(full.substring(f+2,end<0?full.length():end));}
            int barn=0;int bi=full.indexOf("#b");if(bi>=0){int end=full.indexOf('#',bi+2);barn=Integer.parseInt(full.substring(bi+2,end<0?full.length():end));}
            var mods=org.villageastra.world.FarmField.modules(field,west,full.contains("#l"));
            var box=org.villageastra.world.FarmField.box(mods);positions.add(new BlockPos(box[0],0,box[1]));positions.add(new BlockPos(box[2],1+org.villageastra.world.FarmField.FLOOR_PITCH*org.villageastra.world.FarmField.floors(mods).get(org.villageastra.world.FarmField.floors(mods).size()-1),box[3]));
            // AD-130: and its barn to the top of the roof and the tower.
            var barnBox=org.villageastra.world.FarmBarn.box(barn,west);if(barnBox.length==6){positions.add(new BlockPos(barnBox[0],barnBox[1],barnBox[2]));positions.add(new BlockPos(barnBox[3],barnBox[4],barnBox[5]));}}
        return new Bounds(positions.stream().mapToInt(BlockPos::getX).min().orElseThrow(),positions.stream().mapToInt(BlockPos::getY).min().orElseThrow(),positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow(),positions.stream().mapToInt(BlockPos::getX).max().orElseThrow(),positions.stream().mapToInt(BlockPos::getY).max().orElseThrow(),positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
    });}
    /** AD-094: a laid block of a castle wall is the village's like its buildings — nobody breaks it, hits it off or pushes it away. */
    static boolean laidWall(ServerLevel level,BlockPos pos){var s=level.getBlockState(pos);return org.villageastra.world.Walls.palette(s)&&org.villageastra.world.Walls.wallBlock(level,pos);}
    public static boolean disallowedPlacement(ServerLevel level,BlockPos pos){return disallowedPlacement(level,pos,null);}
    /** AD-135: an annex stands right beside its parent — inside the parent's three-block buffer — so its survey leaves the parent (and only it) out. */
    public static boolean disallowedPlacement(ServerLevel level,BlockPos pos,java.util.UUID beside){
        // AD-125: the new lot of a building being moved is reserved until the move is done.
        if(org.villageastra.world.Relocations.reserved(level,pos))return true;
        for(var entry:SettlementData.get(level.getServer()).entries())if(entry.dimension().equals(level.dimension().location().toString()))
            for(var building:entry.settlement().buildings()){
                if(building.id().equals(beside))continue;
                String type=boundsKey(entry,building);
                if(bounds(type).buffered(org.villageastra.world.BuildingPlacement.local(entry,building,pos)))return true;
                var area=entry.settlement().mineAreas().get(building.id());var local=org.villageastra.world.BuildingPlacement.local(entry,building,pos);
                if(area!=null&&area.contains(local.getX(),local.getY(),local.getZ(),3))return true;
            }
        return false;
    }
    /** AD-059: dynamite is the one block a player may put in and beside buildings — and take back — so a building can really be blown up. */
    public static boolean explosive(net.minecraft.world.level.block.state.BlockState state){return state.is(Blocks.TNT);}
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent event){
        if(!(event.getEntity() instanceof net.minecraft.world.entity.player.Player)||!(event.getLevel() instanceof ServerLevel level))return;
        if(!(event instanceof BlockEvent.EntityMultiPlaceEvent)&&explosive(event.getPlacedBlock()))return;
        boolean blocked=event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ?multi.getReplacedBlockSnapshots().stream().anyMatch(s->disallowedPlacement(level,s.getPos())):disallowedPlacement(level,event.getPos());
        if(blocked){event.setCanceled(true);((net.minecraft.world.entity.player.Player)event.getEntity()).displayClientMessage(net.minecraft.network.chat.Component.translatable("message.villageastra.protected_building"),true);}
    }
    @SubscribeEvent public static void piston(net.minecraftforge.event.level.PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var resolver=event.getStructureHelper();
        if (resolver==null || !resolver.resolve()) return;
        if (resolver.getToPush().stream().anyMatch(pos->disallowedPlacement(level,pos)||laidWall(level,pos)||disallowedPlacement(level,pos.relative(resolver.getPushDirection())))
                || resolver.getToDestroy().stream().anyMatch(pos->disallowedPlacement(level,pos)||laidWall(level,pos)||disallowedPlacement(level,pos.relative(resolver.getPushDirection())))) event.setCanceled(true);
    }
    @SubscribeEvent public static void chunk(net.minecraftforge.event.level.ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
        level.getServer().execute(()-> {
            for (var entity:java.util.List.copyOf(chunk.getBlockEntities().values()))
                if(entity instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest
                        && !(chest instanceof org.villageastra.world.OwnedChestEntity)
                        && chest.getPersistentData().hasUUID("AstraSettlement")) upgradeLegacyChest(level,chest.getBlockPos());
        });
    }
    /** One server-thread chunk mutation; remove old BE first so onRemove cannot spill duplicate items. */
    public static void upgradeLegacyChest(ServerLevel level,BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity old)
                || old instanceof org.villageastra.world.OwnedChestEntity || !old.getPersistentData().hasUUID("AstraSettlement")) return;
        var state=old.getBlockState();
        if (!state.is(Blocks.CHEST) || state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE)
            throw new IllegalStateException("Unsupported legacy owned double chest at "+pos);
        var saved=old.saveWithoutMetadata();
        level.removeBlockEntity(pos);
        level.setBlock(pos,VillageAstra.OWNED_CHEST.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.ChestBlock.FACING,state.getValue(net.minecraft.world.level.block.ChestBlock.FACING))
                .setValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED,state.getValue(net.minecraft.world.level.block.ChestBlock.WATERLOGGED)),3);
        var replacement=level.getBlockEntity(pos);
        if (!(replacement instanceof org.villageastra.world.OwnedChestEntity)) throw new IllegalStateException("Chest migration failed");
        replacement.load(saved); replacement.setChanged();
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getState().is(VillageAstra.CARGO_CHEST.get())||explosive(event.getState()))return;
        if (event.getLevel() instanceof ServerLevel level && (disallowedPlacement(level, event.getPos())||laidWall(level,event.getPos()))) event.setCanceled(true);
    }
    @SubscribeEvent public static void hit(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event){
        if(!(event.getLevel() instanceof ServerLevel level)||event.getLevel().getBlockState(event.getPos()).is(VillageAstra.CARGO_CHEST.get())||explosive(event.getLevel().getBlockState(event.getPos()))||!(disallowedPlacement(level,event.getPos())||laidWall(level,event.getPos())))return;
        event.setCanceled(true);
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player){
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(level,event.getPos()));
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.villageastra.protected_building"),true);
        }
    }
}
