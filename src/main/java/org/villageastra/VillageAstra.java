package org.villageastra;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.villageastra.world.ResidentEntity;
import org.villageastra.world.SettlementStructure;
import org.villageastra.world.SettlementPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

@Mod(VillageAstra.ID)
public final class VillageAstra {
    public static final String ID = "villageastra";
    public static final DeferredRegister<net.minecraft.world.level.block.Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES,ID);
    public static final RegistryObject<org.villageastra.world.OwnedChestBlock> OWNED_CHEST = BLOCKS.register("owned_chest",org.villageastra.world.OwnedChestBlock::new);
    public static final RegistryObject<org.villageastra.world.TimberScaffoldBlock> TIMBER_SCAFFOLD = BLOCKS.register("timber_scaffold",org.villageastra.world.TimberScaffoldBlock::new);
    // AD-138: the trough of a livestock pen.
    public static final RegistryObject<org.villageastra.world.FeederBlock> FEEDER = BLOCKS.register("feeder",org.villageastra.world.FeederBlock::new);
    public static final RegistryObject<org.villageastra.world.MobTrapBlock> MOB_TRAP = BLOCKS.register("mob_trap",org.villageastra.world.MobTrapBlock::new);
    public static final RegistryObject<org.villageastra.world.OwnedChestBlock> CARGO_CHEST = BLOCKS.register("cargo_chest",()->new org.villageastra.world.OwnedChestBlock(true));
    public static final RegistryObject<net.minecraft.world.level.block.entity.BlockEntityType<org.villageastra.world.OwnedChestEntity>> OWNED_CHEST_ENTITY = BLOCK_ENTITIES.register("owned_chest",()->net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(org.villageastra.world.OwnedChestEntity::new,OWNED_CHEST.get(),CARGO_CHEST.get()).build(null));
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    // AD-112: one core block and item per core type (CoreCatalog.TYPES) and the four universal rings III..VI.
    public static final java.util.Map<String,RegistryObject<org.villageastra.world.BuildingCoreBlock>> CORES = java.util.Collections.unmodifiableMap(org.villageastra.domain.CoreCatalog.TYPES.stream().collect(java.util.stream.Collectors.toMap(t->t,t->BLOCKS.register(org.villageastra.domain.CoreCatalog.path(t),()->new org.villageastra.world.BuildingCoreBlock(t)),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final java.util.Map<String,RegistryObject<Item>> CORE_ITEMS = java.util.Collections.unmodifiableMap(org.villageastra.domain.CoreCatalog.TYPES.stream().collect(java.util.stream.Collectors.toMap(t->t,t->ITEMS.register(org.villageastra.domain.CoreCatalog.path(t),()->new org.villageastra.world.CoreItem(CORES.get(t).get(),new Item.Properties().stacksTo(16))),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final java.util.Map<Integer,RegistryObject<Item>> CORE_RINGS = java.util.Collections.unmodifiableMap(java.util.stream.IntStream.rangeClosed(org.villageastra.domain.CoreCatalog.FIRST_RING,org.villageastra.domain.CoreCatalog.LAST_RING).boxed().collect(java.util.stream.Collectors.toMap(g->g,g->ITEMS.register("core_ring_"+g,()->new org.villageastra.world.CoreItem.CoreRingItem(g,new Item.Properties().stacksTo(16))),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final DeferredRegister<StructureType<?>> STRUCTURES = DeferredRegister.create(Registries.STRUCTURE_TYPE, ID);
    public static final DeferredRegister<StructurePieceType> PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, ID);
    public static final DeferredRegister<net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<?>> PLACEMENTS=DeferredRegister.create(Registries.STRUCTURE_PLACEMENT,ID);
    public static final RegistryObject<net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType<org.villageastra.world.SectorPlacement>> SECTOR_PLACEMENT=PLACEMENTS.register("sectors",()->()->org.villageastra.world.SectorPlacement.CODEC);
    public static final RegistryObject<StructureType<SettlementStructure>> SETTLEMENT_STRUCTURE =
            STRUCTURES.register("settlement", () -> () -> SettlementStructure.CODEC);
    public static final RegistryObject<StructurePieceType> SETTLEMENT_PIECE = PIECES.register("settlement", () -> SettlementPiece::new);
    public static final RegistryObject<Item> MAYOR_SHOVEL = ITEMS.register("mayor_shovel", () -> new net.minecraft.world.item.ShovelItem(net.minecraft.world.item.Tiers.IRON,1.5F,-3F,new Item.Properties()));
    public static final RegistryObject<Item> ZINDBO = ITEMS.register("zindbo", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> RESEARCH_BINDING = ITEMS.register("research_binding",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> WRITING_TOOL = ITEMS.register("writing_tool",()->new Item(new Item.Properties().durability(org.villageastra.domain.ProductionBalance.bookUses())));
    public static final RegistryObject<Item> TIMBER_SCAFFOLD_ITEM = ITEMS.register("timber_scaffold",()->new net.minecraft.world.item.BlockItem(TIMBER_SCAFFOLD.get(),new Item.Properties()));
    public static final RegistryObject<Item> FEEDER_ITEM = ITEMS.register("feeder",()->new net.minecraft.world.item.BlockItem(FEEDER.get(),new Item.Properties()));
    public static final RegistryObject<Item> MOB_TRAP_ITEM = ITEMS.register("mob_trap",()->new net.minecraft.world.item.BlockItem(MOB_TRAP.get(),new Item.Properties()));
    // AD-142: the framed window, one block and item per vanilla wood (FramedWindows.WOODS).
    public static final java.util.Map<String,RegistryObject<org.villageastra.world.FramedWindowBlock>> FRAMED_WINDOWS = java.util.Collections.unmodifiableMap(org.villageastra.domain.FramedWindows.WOODS.stream().collect(java.util.stream.Collectors.toMap(w->w,w->BLOCKS.register(org.villageastra.domain.FramedWindows.path(w),()->new org.villageastra.world.FramedWindowBlock(w)),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final java.util.Map<String,RegistryObject<Item>> FRAMED_WINDOW_ITEMS = java.util.Collections.unmodifiableMap(org.villageastra.domain.FramedWindows.WOODS.stream().collect(java.util.stream.Collectors.toMap(w->w,w->ITEMS.register(org.villageastra.domain.FramedWindows.path(w),()->new net.minecraft.world.item.BlockItem(FRAMED_WINDOWS.get(w).get(),new Item.Properties())),(a,b)->a,java.util.LinkedHashMap::new)));
    // AD-143: furniture, a chair and a table per vanilla wood (Furniture.WOODS), and the invisible seat a sitter rides on a chair.
    public static final java.util.Map<String,RegistryObject<org.villageastra.world.ChairBlock>> CHAIRS = java.util.Collections.unmodifiableMap(org.villageastra.domain.Furniture.WOODS.stream().collect(java.util.stream.Collectors.toMap(w->w,w->BLOCKS.register(org.villageastra.domain.Furniture.chairPath(w),()->new org.villageastra.world.ChairBlock(w)),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final java.util.Map<String,RegistryObject<Item>> CHAIR_ITEMS = java.util.Collections.unmodifiableMap(org.villageastra.domain.Furniture.WOODS.stream().collect(java.util.stream.Collectors.toMap(w->w,w->ITEMS.register(org.villageastra.domain.Furniture.chairPath(w),()->new net.minecraft.world.item.BlockItem(CHAIRS.get(w).get(),new Item.Properties())),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final java.util.Map<String,RegistryObject<org.villageastra.world.TableBlock>> TABLES = java.util.Collections.unmodifiableMap(org.villageastra.domain.Furniture.WOODS.stream().collect(java.util.stream.Collectors.toMap(w->w,w->BLOCKS.register(org.villageastra.domain.Furniture.tablePath(w),()->new org.villageastra.world.TableBlock(w)),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final java.util.Map<String,RegistryObject<Item>> TABLE_ITEMS = java.util.Collections.unmodifiableMap(org.villageastra.domain.Furniture.WOODS.stream().collect(java.util.stream.Collectors.toMap(w->w,w->ITEMS.register(org.villageastra.domain.Furniture.tablePath(w),()->new net.minecraft.world.item.BlockItem(TABLES.get(w).get(),new Item.Properties())),(a,b)->a,java.util.LinkedHashMap::new)));
    public static final RegistryObject<Item> BANDAGE =ITEMS.register("bandage",()->new Item(new Item.Properties()));
    /** AD-155 (Metallurgy IV): armour a smith of level IV makes for the village's wolves (VillageWolves.armour). */
    public static final RegistryObject<Item> WOLF_ARMOR =ITEMS.register("wolf_armor",()->new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FLOUR = ITEMS.register("flour",()->new Item(new Item.Properties()));
    public static final RegistryObject<Item> RESEARCH_VOLUME = ITEMS.register("research_volume",()->new Item(new Item.Properties()));
    public static final RegistryObject<EntityType<ResidentEntity>> RESIDENT = ENTITIES.register("resident",
            () -> EntityType.Builder.of(ResidentEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F).clientTrackingRange(10).build(ID + ":resident"));
    // ROAD-008: one simple cart with a real size and a real hold, pulled by a caravaneer on foot.
    public static final RegistryObject<EntityType<org.villageastra.world.CartEntity>> CART = ENTITIES.register("cart",
            () -> EntityType.Builder.<org.villageastra.world.CartEntity>of(org.villageastra.world.CartEntity::new, MobCategory.MISC)
                    .sized(1.1F, 0.9F).clientTrackingRange(10).build(ID + ":cart"));
    public static final RegistryObject<EntityType<org.villageastra.world.SeatEntity>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<org.villageastra.world.SeatEntity>of(org.villageastra.world.SeatEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).noSummon().fireImmune().clientTrackingRange(10).build(ID + ":seat"));
    public static final RegistryObject<Item> CART_ITEM = ITEMS.register("cart",()->new org.villageastra.world.CartItem(new Item.Properties().stacksTo(1)));

    public VillageAstra() {
        // AD-138 IV / AD-139: the restaurant's dogs are the kennel wolves of the livestock yard.
        org.villageastra.world.VillageDogs.provide(org.villageastra.world.VillageWolves.DOGS);
        org.villageastra.server.ConstructionNetwork.init();
        // AD-146: the conversation window with residents.
        org.villageastra.dialog.DialogNetwork.init();org.villageastra.dialog.ResidentTalk.init();org.villageastra.dialog.QuestTalk.init();
        org.villageastra.server.ResidentMarkerNetwork.init();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        STRUCTURES.register(bus);
        PIECES.register(bus);
        PLACEMENTS.register(bus);
        bus.addListener(this::attributes);
        bus.addListener((net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent event)->event.register(org.villageastra.persistence.ChunkReceipts.class));
    }
    private void attributes(EntityAttributeCreationEvent event) {
        event.put(RESIDENT.get(), ResidentEntity.attributes().build());
    }
}
