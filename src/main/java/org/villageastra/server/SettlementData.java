package org.villageastra.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.villageastra.domain.*;
import java.util.*;

/** Overworld-owned registry, including villages in other dimensions. Schema 6 adds office and saved project orders; older schemas migrate deterministically. */
public final class SettlementData extends SavedData {
    public record Entry(Settlement settlement, String dimension, BlockPos center) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private SimulationClock clock = new SimulationClock(0);
    public static SettlementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SettlementData::load, () -> {
            // DimensionDataStorage catches decoder errors and would otherwise invoke a fresh-data supplier.
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/villageastra.dat");
            if (java.nio.file.Files.exists(file) || java.nio.file.Files.exists(file.resolveSibling("villageastra.dat_old")))
                throw new IllegalStateException("Existing ZimboVillagers data failed to load; refusing to overwrite " + file);
            return new SettlementData();
        }, "villageastra");
    }
    public SimulationClock clock() { return clock; }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public Entry entry(UUID id) { return entries.get(id); }
    public boolean add(Entry entry) {
        if (entries.putIfAbsent(entry.settlement().id(), entry) != null) return false;
        // Owner 2026-09-24: a village has its own name from the moment it appears (a save being read names its villages after the last one).
        if (!loading) name(entry.settlement());
        setDirty();
        return true;
    }
    private boolean loading;
    /** Every village's name in this world. */
    public java.util.Set<String> names(){var out=new java.util.HashSet<String>();for(var e:entries.values())if(!e.settlement().name().isEmpty())out.add(e.settlement().name());return out;}
    /** Gives a village without a name, or with one another village already has, a name of its own. True when it changed. */
    private boolean name(org.villageastra.domain.Settlement s){
        var others=new java.util.HashSet<String>();for(var e:entries.values())if(e.settlement()!=s&&!e.settlement().name().isEmpty())others.add(e.settlement().name());
        if(!s.name().isEmpty()&&!others.contains(s.name()))return false;
        s.name(org.villageastra.domain.VillageNames.unique(s.id(),others));return true;
    }
    /** Removes a registry entry (ruins/annexation and isolated tests); never touches blocks. */
    public boolean remove(UUID id) { boolean removed = entries.remove(id) != null; if (removed) setDirty(); return removed; }
    public void activeTick(boolean hasPlayers) { if (clock.advance(hasPlayers, false)) setDirty(); }
    public static SettlementData load(CompoundTag tag) {
        if (!tag.contains("schema", Tag.TAG_INT) || (tag.getInt("schema") < 1 || tag.getInt("schema") > 7))
            throw new IllegalArgumentException("Unsupported ZimboVillagers save schema; refusing to reset it");
        if (!tag.contains("activeTicks", Tag.TAG_LONG) || !tag.contains("settlements", Tag.TAG_LIST))
            throw new IllegalArgumentException("Incomplete ZimboVillagers save");
        requireCompoundList(tag,"settlements");
        int schema = tag.getInt("schema");
        SettlementData data = new SettlementData();data.loading=true;
        data.clock = new SimulationClock(tag.getLong("activeTicks"));
        for (Tag raw : tag.getList("settlements", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id") || !t.contains("center", Tag.TAG_LONG) ||
                    t.getString("dimension").isBlank() || net.minecraft.resources.ResourceLocation.tryParse(t.getString("dimension")) == null ||
                    !t.contains("homes", Tag.TAG_LIST) || !t.contains("residents", Tag.TAG_LIST))
                throw new IllegalArgumentException("Incomplete settlement record");
            requireCompoundList(t,"homes"); requireCompoundList(t,"residents");
            Settlement s = new Settlement(t.getUUID("id"));
            if(schema>=4){
                if(!t.contains("civilization",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing civilization");
                var c=t.getCompound("civilization");var done=new java.util.ArrayList<String>();
                for(Tag research:c.getList("completed",Tag.TAG_STRING))done.add(research.getAsString());
                s.restoreCivilization(Civilization.restore(c.getInt("level"),done,c.getString("active"),c.getLong("progress")));
            }
            for (Tag house : t.getList("homes", Tag.TAG_COMPOUND)) {
                CompoundTag h = (CompoundTag) house;
                s.addHome(new Settlement.Home(h.getUUID("id"), h.getInt("level"), h.getInt("capacity"), h.getBoolean("usable")));
            }
            for (Tag person : t.getList("residents", Tag.TAG_COMPOUND)) {
                CompoundTag r = (CompoundTag) person;
                var restored = new Resident(r.getUUID("id"), Resident.Life.valueOf(r.getString("life")),
                        r.getBoolean("educated"), r.contains("profession") ? Profession.fromId(r.getString("profession")) : null,
                        r.hasUUID("home") ? r.getUUID("home") : null, r.getLong("homelessSince"),schema>=3 ? new ResidentProfile(r.getString("name"),r.getInt("skin")) : ResidentProfile.generate(r.getUUID("id")));
                if (r.contains("lastMeal", Tag.TAG_LONG)) restored.restoreNeeds(r.getLong("born"), r.getLong("lastMeal"), r.getInt("missedMeals"), r.getLong("schoolTicks"));
                if (r.getBoolean("cadet")) restored.restoreCadet(true);
                if (r.getBoolean("sick")) restored.restoreSick(true);
                restored.restoreRecoveryRest(r.getLong("recoveryRest"));
                if (r.contains("drillTicks", Tag.TAG_LONG)) restored.restoreTraining(r.getBoolean("military"), r.getBoolean("recruit"), r.getLong("drillTicks"));
                s.restoreResident(restored);
            }
            if (t.contains("lastBirth", Tag.TAG_LONG)) s.recordBirth(t.getLong("lastBirth"));
            if (schema == 1) s.migrateStarterWorkplaces();
            else {
                if (!t.contains("buildings",Tag.TAG_LIST)) throw new IllegalArgumentException("Missing buildings");
                requireCompoundList(t,"buildings");
                for (Tag rawBuilding : t.getList("buildings",Tag.TAG_COMPOUND)) {
                    CompoundTag b = (CompoundTag) rawBuilding;
                    if (!b.hasUUID("id") || !b.contains("offset",Tag.TAG_LONG)) throw new IllegalArgumentException("Incomplete building");
                    BlockPos offset = BlockPos.of(b.getLong("offset"));
                    s.addBuilding(new Settlement.Building(b.getUUID("id"),b.getString("type"),offset.getX(),offset.getY(),offset.getZ(),b.getInt("rotation"),Math.max(1,b.getInt("level")),b.getString("wood")));
                    if(b.getInt("architectureRevision")<119)s.markLegacyArchitecture(b.getUUID("id"));
                }
                for (Tag rawResident : t.getList("residents",Tag.TAG_COMPOUND)) {
                    CompoundTag r = (CompoundTag) rawResident;
                    if (r.hasUUID("workplace")) {
                        Resident person = s.resident(r.getUUID("id"));
                        if (!person.alive() || person.profession() == null) throw new IllegalArgumentException("Inactive workplace owner");
                        s.assign(person.id(),person.profession(),r.getUUID("workplace"));
                    }
                }
            }
            if(schema>=5){
                requireCompoundList(t,"mines");
                for(Tag rawMine:t.getList("mines",Tag.TAG_COMPOUND)){
                    var mine=(CompoundTag)rawMine;
                    if(!mine.hasUUID("building")||!mine.contains("lastStep",Tag.TAG_INT)||!mine.contains("width",Tag.TAG_INT)||!mine.contains("height",Tag.TAG_INT)||s.mineAreas().containsKey(mine.getUUID("building")))throw new IllegalArgumentException("Invalid saved mine area");
                    s.noteMine(mine.getUUID("building"),mine.getInt("lastStep"),mine.getInt("width"),mine.getInt("height"),mine.getInt("descent"));
                    // AD-112: the galleries of its floors (absent before AD-112; no migration).
                    if(mine.contains("galleries")){requireCompoundList(mine,"galleries");for(Tag rawGallery:mine.getList("galleries",Tag.TAG_COMPOUND)){var g=(CompoundTag)rawGallery;
                        if(!g.contains("step",Tag.TAG_INT)||!g.contains("side",Tag.TAG_INT)||!g.contains("length",Tag.TAG_INT))throw new IllegalArgumentException("Invalid saved mine gallery");
                        s.noteMine(mine.getUUID("building"),new org.villageastra.domain.MineArea.Gallery(g.getInt("step"),g.getInt("side"),g.getInt("length")));}}
                }
            }
            // AD-130/AD-131: the lot layout version; absent means a settlement saved with the old lots.
            if(t.contains("lotLayout",Tag.TAG_INT))s.lotLayout(t.getInt("lotLayout"));
            if(t.contains("name",Tag.TAG_STRING))s.name(t.getString("name"));
            // AD-112 (AD-104 P4): the field its builders laid and its side; absent means level I, east.
            if(t.contains("westFields")){requireCompoundList(t,"westFields");for(Tag rawWest:t.getList("westFields",Tag.TAG_COMPOUND)){var f=(CompoundTag)rawWest;if(!f.hasUUID("building"))throw new IllegalArgumentException("Invalid saved west field");s.turnFieldWest(f.getUUID("building"));}}
            if(t.contains("fieldLevels")){requireCompoundList(t,"fieldLevels");for(Tag rawField:t.getList("fieldLevels",Tag.TAG_COMPOUND)){var f=(CompoundTag)rawField;if(!f.hasUUID("building")||!f.contains("level",Tag.TAG_INT))throw new IllegalArgumentException("Invalid saved field level");s.raiseFieldLevel(f.getUUID("building"),f.getInt("level"));}}
            // AD-135: which building each annex belongs to; absent means none (read after the buildings, which it names).
            if(t.contains("annexes")){requireCompoundList(t,"annexes");for(Tag rawAnnex:t.getList("annexes",Tag.TAG_COMPOUND)){var f=(CompoundTag)rawAnnex;if(!f.hasUUID("annex")||!f.hasUUID("parent"))throw new IllegalArgumentException("Invalid saved annex");s.linkAnnex(f.getUUID("annex"),f.getUUID("parent"));}}
            if(schema>=6){
                if(!t.contains("governance",Tag.TAG_COMPOUND))throw new IllegalArgumentException("Missing governance");
                var g=t.getCompound("governance");requireCompoundList(g,"paused");
                if(!g.contains("epoch",Tag.TAG_LONG)||!g.contains("revision",Tag.TAG_LONG)||(g.contains("player")&&!g.hasUUID("player")))throw new IllegalArgumentException("Invalid office");
                var paused=new ArrayList<UUID>();for(Tag rawPaused:g.getList("paused",Tag.TAG_COMPOUND)){var item=(CompoundTag)rawPaused;if(!item.hasUUID("project"))throw new IllegalArgumentException("Invalid paused project");paused.add(item.getUUID("project"));}
                s.restoreGovernance(Governance.restore(g.hasUUID("player")?g.getUUID("player"):null,g.getLong("epoch"),g.getLong("revision"),paused));
                if(schema>=7){if(g.contains("lastWinner")&&!g.hasUUID("lastWinner"))throw new IllegalArgumentException("Invalid election winner");s.governance().restoreElection(ElectionNbt.number(g,"nextElection"),ElectionNbt.number(g,"lastElection"),g.hasUUID("lastWinner")?g.getUUID("lastWinner"):null,ElectionNbt.number(g,"lastScore"),ElectionNbt.number(g,"lastReached"));}
            }
            if (!data.add(new Entry(s, t.getString("dimension"), BlockPos.of(t.getLong("center")))))
                throw new IllegalArgumentException("Duplicate settlement in save");
        }
        // Owner 2026-09-24: villages of a save made before names — or two with one name — get names of their own, in the save's order.
        data.loading=false;boolean named=false;for(var e:data.entries.values())named|=data.name(e.settlement());
        data.setDirty(schema < 7 || named);
        return data;
    }
    private static void requireCompoundList(CompoundTag tag,String key) {
        if (!(tag.get(key) instanceof ListTag list) || (!list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid record list: "+key);
    }
    @Override public CompoundTag save(CompoundTag root) {
        root.putInt("schema", 7);
        root.putLong("activeTicks", clock.ticks());
        ListTag settlements = new ListTag();
        for (Entry entry : entries.values()) {
            Settlement s = entry.settlement();
            CompoundTag t = new CompoundTag();
            t.putUUID("id", s.id()); t.putString("dimension", entry.dimension()); t.putLong("center", entry.center().asLong());
            var office=s.governance();var g=new CompoundTag();if(office.playerMayor()!=null)g.putUUID("player",office.playerMayor());g.putLong("epoch",office.epoch());g.putLong("revision",office.revision());
            g.putLong("nextElection",office.nextElection());g.putLong("lastElection",office.lastElection());g.putLong("lastScore",office.lastScore());g.putLong("lastReached",office.lastReached());if(office.lastWinner()!=null)g.putUUID("lastWinner",office.lastWinner());
            var paused=new ListTag();for(var id:office.pausedProjects()){var item=new CompoundTag();item.putUUID("project",id);paused.add(item);}g.put("paused",paused);t.put("governance",g);
            CompoundTag civ=new CompoundTag();civ.putInt("level",s.civilization().level());civ.putString("active",s.civilization().active());civ.putLong("progress",s.civilization().progress());
            ListTag researched=new ListTag();s.civilization().completed().forEach(id->researched.add(net.minecraft.nbt.StringTag.valueOf(id)));civ.put("completed",researched);t.put("civilization",civ);
            ListTag buildings = new ListTag();
            for (Settlement.Building building : s.buildings()) {
                CompoundTag b = new CompoundTag();
                b.putUUID("id",building.id()); b.putString("type",building.type()); b.putInt("architectureRevision",s.legacyArchitecture().contains(building.id())?117:119);
                if(!building.wood().isEmpty())b.putString("wood",building.wood());
                b.putLong("offset",new BlockPos(building.x(),building.y(),building.z()).asLong()); if(building.rotation()!=0)b.putInt("rotation",building.rotation()); if(building.level()>1)b.putInt("level",building.level()); buildings.add(b);
            }
            t.put("buildings",buildings);
            ListTag mines=new ListTag();s.mineAreas().forEach((id,area)->{var m=new CompoundTag();m.putUUID("building",id);m.putInt("lastStep",area.lastStep());m.putInt("width",area.width());m.putInt("height",area.height());if(area.descent()>0)m.putInt("descent",area.descent());
                if(!area.galleries().isEmpty()){var galleries=new ListTag();for(var gallery:area.galleries()){var gt=new CompoundTag();gt.putInt("step",gallery.step());gt.putInt("side",gallery.side());gt.putInt("length",gallery.length());galleries.add(gt);}m.put("galleries",galleries);}mines.add(m);});t.put("mines",mines);
            if(s.lotLayout()>0)t.putInt("lotLayout",s.lotLayout());
            if(!s.name().isEmpty())t.putString("name",s.name());
            ListTag west=new ListTag();for(var id:s.westFields()){var f=new CompoundTag();f.putUUID("building",id);west.add(f);}t.put("westFields",west);
            ListTag fields=new ListTag();s.fieldLevels().forEach((id,level)->{var f=new CompoundTag();f.putUUID("building",id);f.putInt("level",level);fields.add(f);});t.put("fieldLevels",fields);
            if(!s.annexParents().isEmpty()){ListTag annexes=new ListTag();s.annexParents().forEach((annex,parent)->{var f=new CompoundTag();f.putUUID("annex",annex);f.putUUID("parent",parent);annexes.add(f);});t.put("annexes",annexes);}
            ListTag houses = new ListTag();
            for (Settlement.Home home : s.homes()) {
                CompoundTag h = new CompoundTag();
                h.putUUID("id", home.id()); h.putInt("level", home.level());
                h.putInt("capacity", home.capacity()); h.putBoolean("usable", home.usable()); houses.add(h);
            }
            t.put("homes", houses);
            ListTag residents = new ListTag();
            for (Resident resident : s.residents()) {
                CompoundTag r = new CompoundTag();
                r.putString("name",resident.profile().name());r.putInt("skin",resident.profile().skin());
                r.putUUID("id", resident.id()); r.putString("life", resident.life().name());
                r.putBoolean("educated", resident.educated());
                if (resident.profession() != null) r.putString("profession", resident.profession().id());
                if (s.workplace(resident.id()) != null) r.putUUID("workplace",s.workplace(resident.id()).id());
                if (resident.home() != null) r.putUUID("home", resident.home());
                r.putLong("homelessSince", resident.homelessSince());
                r.putLong("born", resident.born()); r.putLong("lastMeal", resident.lastMeal()); r.putInt("missedMeals", resident.missedMeals()); r.putLong("schoolTicks", resident.schoolTicks());if(resident.cadet())r.putBoolean("cadet",true);if(resident.sick()){r.putBoolean("sick",true);r.putLong("recoveryRest",resident.recoveryRest());} r.putBoolean("military", resident.military()); r.putBoolean("recruit", resident.recruit()); r.putLong("drillTicks", resident.drillTicks());
                residents.add(r);
            }
            t.put("residents", residents); t.putLong("lastBirth", s.lastBirth()); settlements.add(t);
        }
        root.put("settlements", settlements);
        return root;
    }
}
