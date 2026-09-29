package org.villageastra.domain;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authoritative identities and housing, no duplicate inventory counters. */
public final class Settlement {
    public record Home(UUID id, int level, int capacity, boolean usable) {
        public Home {
            Objects.requireNonNull(id);
            if (level < 1 || capacity < 1) throw new IllegalArgumentException("Invalid house");
        }
    }
    /** Coordinates relative to the settlement origin; each building has its own real chest. */
    /** AD-068: rotation is the number of clockwise quarter turns of the design inside its corner box (0 for every generated building).
     *  AD-073: level is the level the building was built up to (1…6) — the structure repairs and protection keep; what the level does is read from its equipment. */
    public record Building(UUID id, String type, int x, int y, int z, int rotation, int level, String wood) {
        public Building {
            Objects.requireNonNull(id);
            if (type == null || type.isBlank()) throw new IllegalArgumentException("Missing building type");
            if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("Invalid building rotation");
            if (level < 1 || level > 6) throw new IllegalArgumentException("Invalid building level");
            if (wood == null || !Set.of("", "oak", "birch", "spruce", "jungle", "acacia", "cherry", "dark_oak").contains(wood)) throw new IllegalArgumentException("Invalid building wood");
        }
        public Building(UUID id, String type, int x, int y, int z, int rotation, int level) { this(id, type, x, y, z, rotation, level, ""); }
        public Building(UUID id, String type, int x, int y, int z, int rotation) { this(id, type, x, y, z, rotation, 1); }
        public Building(UUID id, String type, int x, int y, int z) { this(id, type, x, y, z, 0, 1); }
        public Building withLevel(int value) { return new Building(id, type, x, y, z, rotation, value, wood); }
    }
    private final Set<UUID> legacyArchitecture=new LinkedHashSet<>();
    public Set<UUID> legacyArchitecture(){return Collections.unmodifiableSet(legacyArchitecture);}
    public void markLegacyArchitecture(UUID building){legacyArchitecture.add(building);}
    public void finishArchitectureMigration(UUID building){legacyArchitecture.remove(building);}
    private final Map<UUID,MineArea> mineAreas=new LinkedHashMap<>();
    public Map<UUID,MineArea> mineAreas(){return Collections.unmodifiableMap(mineAreas);}
    public boolean noteMine(UUID building,int step,int width){return noteMine(building,step,width,width==1?3:4,0);}
    public boolean noteMine(UUID building,int step,int width,int height){return noteMine(building,step,width,height,0);}
    public boolean noteMine(UUID building,int step,int width,int height,int descent){
        var b=buildings.get(building);if(b==null||!b.type().equals("mine"))throw new IllegalArgumentException("Unknown mine building");
        var old=mineAreas.get(building);var next=new MineArea(step,width,height,descent);
        if(old!=null){if(old.width()!=width||old.height()!=height)throw new IllegalArgumentException("Changed saved shaft width");if(old.lastStep()>=step)return false;next=new MineArea(step,width,height,descent,old.galleries());}
        mineAreas.put(building,next);return true;
    }
    /** AD-112: a gallery of the mine's floor, claimed as far as it is dug; a claim only grows. The stair must be claimed first. */
    public boolean noteMine(UUID building,MineArea.Gallery gallery){
        var old=mineAreas.get(building);if(old==null)throw new IllegalArgumentException("A gallery needs its mine's stair");
        var next=old.with(gallery);if(next.equals(old))return false;
        mineAreas.put(building,next);return true;
    }
    /** AD-112 (AD-104 P4): the field its builders have laid (level I until a farm project with field work completes) and its side. */
    private final Set<UUID> westFields=new LinkedHashSet<>();
    private final Map<UUID,Integer> fieldLevels=new LinkedHashMap<>();
    public boolean westField(UUID farm){return westFields.contains(farm);}
    public Set<UUID> westFields(){return Collections.unmodifiableSet(westFields);}
    public int fieldLevel(UUID farm){return fieldLevels.getOrDefault(farm,1);}
    public Map<UUID,Integer> fieldLevels(){return Collections.unmodifiableMap(fieldLevels);}
    public boolean turnFieldWest(UUID farm){farm(farm);return westFields.add(farm);}
    /** The field only grows. */
    public boolean raiseFieldLevel(UUID farm,int level){
        farm(farm);if(level<1||level>6)throw new IllegalArgumentException("Invalid field level");
        if(level<=fieldLevel(farm))return false;fieldLevels.put(farm,level);return true;
    }
    /** AD-135: an annex is a building of its own beside its parent; the parent each annex belongs to. A link is made once and never moves. */
    private final Map<UUID,UUID> annexParents=new LinkedHashMap<>();
    public Map<UUID,UUID> annexParents(){return Collections.unmodifiableMap(annexParents);}
    public UUID annexParent(UUID annex){return annexParents.get(annex);}
    public java.util.List<Building> annexes(UUID parent){var out=new java.util.ArrayList<Building>();for(var e:annexParents.entrySet())if(e.getValue().equals(parent)&&buildings.containsKey(e.getKey()))out.add(buildings.get(e.getKey()));return out;}
    public boolean linkAnnex(UUID annex,UUID parent){
        var a=buildings.get(annex);if(a==null||!buildings.containsKey(parent)||annex.equals(parent))throw new IllegalArgumentException("Unknown annex or parent");
        if(!AnnexTypes.annex(a.type()))throw new IllegalArgumentException("Not an annex: "+a.type());
        if(annexParents.containsKey(parent))throw new IllegalArgumentException("An annex has no annexes");
        var old=annexParents.putIfAbsent(annex,parent);if(old!=null&&!old.equals(parent))throw new IllegalArgumentException("Annex belongs to another building");
        return old==null;
    }
    /** AD-130/AD-131: the OrganicLots layout version its lots were made with (0: saved before the version was kept, read as the old lots). */
    private int lotLayout;
    public int lotLayout(){return lotLayout;}
    public void lotLayout(int version){if(version<0)throw new IllegalArgumentException("Invalid lot layout");lotLayout=version;}
    private void farm(UUID id){var b=buildings.get(id);if(b==null||!b.type().equals("farm"))throw new IllegalArgumentException("Unknown farm building");}
    private Governance governance=new Governance();
    public Governance governance(){return governance;}
    public void restoreGovernance(Governance value){
        if(value.playerMayor()!=null&&residents.values().stream().anyMatch(r->r.alive()&&r.profession()==Profession.MAYOR))throw new IllegalArgumentException("Two saved office holders");
        governance=Objects.requireNonNull(value);
    }
    /** Server election/result hook; clients cannot nominate themselves through a command packet. */
    public void appointPlayerMayor(UUID player){
        Objects.requireNonNull(player);
        residents.values().stream().filter(r->r.alive()&&r.profession()==Profession.MAYOR).forEach(r->{r.assign(null);employment.remove(r.id());});
        governance.appointPlayer(player);
    }
    /** Use an existing adult in persisted resident order, preferring an unemployed resident. */
    public boolean appointNpcMayor(){
        boolean changed=governance.playerMayor()!=null;governance.appointNpc();
        if(residents.values().stream().anyMatch(r->r.alive()&&r.profession()==Profession.MAYOR))return changed;
        var hall=buildings.values().stream().filter(b->b.type().equals("town_hall")).findFirst().orElse(null);
        var adults=residents.values().stream().filter(r->r.life()==Resident.Life.ADULT).toList();
        var successor=adults.stream().filter(r->r.profession()==null).findFirst().orElse(adults.isEmpty()?null:adults.get(0));
        if(hall==null||successor==null)return changed;
        assign(successor.id(),Profession.MAYOR,hall.id());return true;
    }
    private Civilization civilization=new Civilization();
    public Civilization civilization(){return civilization;}
    public void restoreCivilization(Civilization value){civilization=Objects.requireNonNull(value);}
    private final Map<UUID, Building> buildings = new LinkedHashMap<>();
    private final Map<UUID, UUID> employment = new LinkedHashMap<>();
    public Collection<Building> buildings() { return Collections.unmodifiableCollection(buildings.values()); }
    public void addBuilding(Building building) {
        if (buildings.putIfAbsent(building.id(), building) != null) throw new IllegalArgumentException("Duplicate building");
    }
    /** AD-073: a finished upgrade raises the level the building is kept at; levels only go up one at a time. */
    public boolean raiseBuildingLevel(UUID id, int level) {
        var b = buildings.get(id); if (b == null) throw new IllegalArgumentException("Unknown building");
        if (level <= b.level()) return false;
        if (level != b.level() + 1) throw new IllegalArgumentException("Building levels are raised one at a time");
        buildings.put(id, b.withLevel(level)); return true;
    }
    /** AD-125: a moved building keeps its id, type and level; only its place and turn change. False when it already stands there. */
    public boolean moveBuilding(UUID id, int x, int y, int z, int rotation) {
        var b = buildings.get(id); if (b == null) throw new IllegalArgumentException("Unknown building");
        if (b.x() == x && b.y() == y && b.z() == z && b.rotation() == rotation) return false;
        buildings.put(id, new Building(id, b.type(), x, y, z, rotation, b.level(), b.wood())); return true;
    }
    /** AD-130: the living residents posted at a building, in the order they were posted (a farmer's rank among the farm's farmers). */
    public java.util.List<UUID> employees(UUID building) {
        var out = new java.util.ArrayList<UUID>();
        employment.forEach((resident, at) -> { var r = residents.get(resident); if (at.equals(building) && r != null && r.alive() && r.profession() != null) out.add(resident); });
        return out;
    }
    /** AD-130: a worker leaves his post (a farm that needs fewer hands); the labour pass posts him again where he is wanted. */
    public boolean unassign(UUID resident) {
        var r = residents.get(resident); if (r == null || r.profession() == null) return false;
        r.assign(null); employment.remove(resident); return true;
    }
    public Building workplace(UUID resident) {
        Resident r = residents.get(resident);
        return r == null || !r.alive() || r.profession() == null ? null : buildings.get(employment.get(resident));
    }
    public void assign(UUID resident, Profession role, UUID building) {
        Building b = Objects.requireNonNull(buildings.get(building), "Unknown workplace");
        int assigned = (int) employment.entrySet().stream().filter(e -> e.getValue().equals(building)
                && !e.getKey().equals(resident) && residents.get(e.getKey()).alive()).count();
        assign(resident, role, b.type(), assigned);
        employment.put(resident, building);
    }
    /** AD-131: the starter layout; its mine stands at x 32, east of the forester's 15x21 lot (x 12..26). */
    public static java.util.List<Building> initialBuildings(UUID id) {return starterBuildings(id,32);}
    /** The starter layout before AD-131 put its mine at x 24: schema-1 saves were made with it. */
    private static java.util.List<Building> starterBuildings(UUID id,int mineX) {
        return java.util.List.of(
            new Building(childId(id,"building/town_hall"),"town_hall",0,0,0),
            new Building(childId(id,"house/0"),"home",10,0,0),
            new Building(childId(id,"house/1"),"home",20,0,0),
            new Building(childId(id,"house/2"),"home",30,0,0),
            new Building(childId(id,"building/farm"),"farm",0,0,12),
            new Building(childId(id,"building/forester"),"forester",12,0,12),
            new Building(childId(id,"building/mine"),"mine",mineX,0,12));
    }
    /** Saved natural layouts have irregular lots; identity and employment stay unchanged. */
    public static Settlement natural(UUID id){
        Settlement s=initial(id);int[][] lots={{0,0},{-14,-8},{12,-4},{-5,15},{12,16},{-22,12},{28,10}};
        Random random=new Random(id.getMostSignificantBits()^id.getLeastSignificantBits());int i=0;
        for(Building building:List.copyOf(s.buildings.values())){
            int x=lots[i][0],z=lots[i][1];if(i>0){x+=random.nextInt(5)-2;z+=random.nextInt(5)-2;}i++;
            s.buildings.put(building.id(),new Building(building.id(),building.type(),x,0,z));
        }
        s.westFields.clear();s.lotLayout=1;
        return s;
    }
    public static Settlement natural(UUID id,int[] elevations){
        return natural(id,elevations,1);
    }
    public static Settlement natural(UUID id,int[] elevations,int version){
        Settlement s=natural(id);
        if(version>=2)for(Building b:OrganicLots.buildings(id,version))s.buildings.put(b.id(),b);
        s.lotLayout=version;
        if(elevations.length!=7||elevations[0]!=0)throw new IllegalArgumentException("Invalid terrain layout");
        int index=0;for(Building b:List.copyOf(s.buildings.values())){
            int y=elevations[index++];if(Math.abs(y)>(version>=2?16:8))throw new IllegalArgumentException("Terrain exceeds supported range");
            s.buildings.put(b.id(),new Building(b.id(),b.type(),b.x(),y,b.z(),b.rotation(),b.level(),b.wood()));
        }return s;
    }
    /** Deterministic migration for schema 1, which only supported the seven starter buildings. */
    public void migrateStarterWorkplaces() {migrateStarterWorkplaces(starterBuildings(id,24));}
    private void migrateStarterWorkplaces(java.util.List<Building> starter) {
        starter.forEach(this::addBuilding);
        for (Resident r : residents.values()) if (r.alive() && r.profession() != null) {
            String type = r.profession() == Profession.PORTER ? "town_hall" : r.profession().workplace();
            buildings.values().stream().filter(b -> b.type().equals(type)).findFirst()
                    .ifPresent(b -> assign(r.id(),r.profession(),b.id()));
        }
    }
    private final UUID id;
    private final Map<UUID, Home> homes = new LinkedHashMap<>();
    private final Map<UUID, Resident> residents = new LinkedHashMap<>();
    public Settlement(UUID id) { this.id = Objects.requireNonNull(id); }
    /** Owner 2026-09-24: the village's own name, unique in its world (VillageNames); empty only until SettlementData gives it one. */
    private String name="";
    public String name(){return name;}
    public void name(String name){this.name=name==null?"":name.strip();}
    private long lastBirth = -1;
    public long lastBirth() { return lastBirth; }
    public void recordBirth(long activeTicks) { if (activeTicks < -1) throw new IllegalArgumentException("Invalid birth time"); lastBirth = activeTicks; }
    public UUID id() { return id; }
    public Collection<Home> homes() { return Collections.unmodifiableCollection(homes.values()); }
    public Collection<Resident> residents() { return Collections.unmodifiableCollection(residents.values()); }
    public Resident resident(UUID id) { return residents.get(id); }
    public void addHome(Home home) {
        if (homes.putIfAbsent(home.id(), home) != null) throw new IllegalArgumentException("Duplicate home");
    }
    public void admit(Resident resident, UUID house) {
        if (residents.containsKey(resident.id())) throw new IllegalArgumentException("Duplicate resident");
        requireSpace(house);
        requireMayorUnique(resident);
        resident.house(house);
        residents.put(resident.id(), resident);
    }
    /** Restore also accepts an unhoused resident; corruption fails instead of resetting progress. */
    public void restoreResident(Resident resident) {
        if (residents.containsKey(resident.id())) throw new IllegalArgumentException("Duplicate resident");
        if (resident.home() != null) requireSpace(resident.home());
        requireMayorUnique(resident);
        residents.put(resident.id(), resident);
    }
    private void requireMayorUnique(Resident resident) {
        if(resident.profession()==Profession.MAYOR&&governance.playerMayor()!=null)throw new IllegalStateException("Player holds office");
        if (resident.profession() == Profession.MAYOR && residents.values().stream()
                .anyMatch(r -> r.profession() == Profession.MAYOR)) throw new IllegalStateException("Two mayors");
    }
    private void requireSpace(UUID id) {
        Home home = homes.get(id);
        if (home == null || !home.usable() || occupancy(id) >= home.capacity())
            throw new IllegalStateException("No usable housing slot");
    }
    /** MULTI-003: removes a living resident for a transfer; the same object (identity, education, profile) is admitted by the destination. The job stays behind. */
    public Resident release(UUID resident) {
        Resident r = Objects.requireNonNull(residents.get(resident), "Unknown resident");
        if (!r.alive() || r.profession() == Profession.MAYOR) throw new IllegalStateException("Resident cannot leave");
        residents.remove(resident); employment.remove(resident);
        if (r.life() == Resident.Life.ADULT) r.assign(null);
        return r;
    }
    public long occupancy(UUID home) {
        return residents.values().stream().filter(r -> r.alive() && home.equals(r.home())).count();
    }
    public void rehouse(UUID resident, UUID home) {
        Resident r = Objects.requireNonNull(residents.get(resident));
        if (home.equals(r.home())) return;
        requireSpace(home);
        r.house(home);
    }
    /** AD-059: a damaged home put back by the builders takes people again. */
    public void restoreHome(UUID home) {
        Home old = Objects.requireNonNull(homes.get(home));
        homes.put(home, new Home(home, old.level(), old.capacity(), true));
    }
    /** AD-123 (H1): a finished project placed more beds; the home's capacity only ever rises, its design mark stays. */
    public void upgradeHome(UUID home, int capacity) {
        Home old = Objects.requireNonNull(homes.get(home));
        if (capacity <= old.capacity()) return;
        homes.put(home, new Home(home, old.level(), capacity, old.usable()));
    }
    public void damageHome(UUID home, long ticks) {
        Home old = Objects.requireNonNull(homes.get(home));
        homes.put(home, new Home(home, old.level(), old.capacity(), false));
        residents.values().stream().filter(r -> home.equals(r.home())).forEach(r -> r.loseHome(ticks));
    }
    public void assign(UUID resident, Profession role, String workplace, int assignments) {
        Resident r = Objects.requireNonNull(residents.get(resident));
        boolean temporaryPorter = role == Profession.PORTER && workplace.equals("town_hall");
        // AD-094: an archer serves at the archery or on a tower of the castle wall.
        boolean tower = role == Profession.ARCHER_GUARD && workplace.equals("wall_tower");
        // AD-139: a porter posted at the restaurant is its courier; an old world's bakery is the restaurant (CoreCatalog.canonical).
        // AD-155: and a porter posted at the smithy is its courier (III-IV).
        boolean courier = role == Profession.PORTER && (CoreCatalog.canonical(workplace).equals("restaurant") || CoreCatalog.canonical(workplace).equals("smithy"));
        if (!role.workplace().equals(CoreCatalog.canonical(AnnexTypes.workplace(workplace))) && !temporaryPorter && !tower && !courier) throw new IllegalStateException("Wrong workplace");
        if(role==Profession.MAYOR&&governance.playerMayor()!=null)throw new IllegalStateException("Player holds office");
        if (role == Profession.MAYOR && residents.values().stream().anyMatch(other -> other != r && other.profession() == role))
            throw new IllegalStateException("Two mayors");
        // AD-159: a barracks holds 8/12/16 by its level (staff.json, Population); the domain keeps only the most any level has.
        if (role == Profession.SOLDIER && assignments >= 16) throw new IllegalStateException("Barracks full");
        r.assign(role);
        employment.remove(resident);
    }
    public static UUID childId(UUID parent, String suffix) {
        return UUID.nameUUIDFromBytes((parent + "/" + suffix).getBytes(StandardCharsets.UTF_8));
    }
    public static Settlement initial(UUID id) {
        Settlement s = new Settlement(id);
        for (int i = 0; i < 3; i++) s.addHome(new Home(childId(id, "house/" + i), 1, 2, true));
        Profession[] roles = {Profession.MAYOR, Profession.FARMER, Profession.FORESTER,
                Profession.MINER, Profession.BUILDER, Profession.PORTER};
        for (int i = 0; i < roles.length; i++) {
            Resident r = new Resident(childId(id, "resident/" + i), Resident.Life.ADULT, false, roles[i], null, -1);
            s.admit(r, childId(id, "house/" + (i / 2)));
        }
        s.migrateStarterWorkplaces(initialBuildings(id));
        // AD-130: the starter farm's field grows west, clear of the forester's 15x21 lot east of it.
        s.lotLayout=OrganicLots.CURRENT;s.buildings.values().stream().filter(b->b.type().equals("farm")).forEach(b->s.westFields.add(b.id()));
        return s;
    }
}
