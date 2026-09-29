package org.villageastra.domain;
import java.util.List;
/** AD-112: which building holds which core, and the ids of cores and rings. Pure: no registry, so JUnit and the client may use it.
 *  Every work building holds one core from level II; levels III-VI each add one universal ring (III copper, IV iron, V gold, VI netherite).
 *  The quarry shares the mine's core, the upper halls share the hall's, and houses hold none. */
public final class CoreCatalog {
 private CoreCatalog(){}
 public static final String NS="villageastra";
 /** The 21 core types, in the order of the catalogue. */
 public static final List<String> TYPES=List.of("farm","livestock","forester","mine","smithy","carpentry","masonry","mill","bakery","clinic","town_hall","guard_house","archery","barracks","school","laboratory","cartographer","expedition","warehouse","caravan","engineering");
 /** AD-139: the restaurant replaced the bakery. A world made before it keeps its "bakery" buildings in NBT; every system that matches a
  *  building to its workshop, profession, automation or dining reads the type through {@link #canonical}. The restaurant's core is the
  *  bakery's core type and block (core_bakery, "the hearth"): a new id would be a new registry entry and a world migration for nothing, and
  *  the frozen AD-117 architecture (Legacy117*) knows its old buildings by these core types. */
 public static final java.util.Map<String,String> ALIASES=java.util.Map.of("bakery","restaurant");
 private static final java.util.Map<String,String> CORE_OF=java.util.Map.of("restaurant","bakery");
 /** The current type an old building type stands for ("bakery" → "restaurant"), every other type itself. */
 public static String canonical(String type){return type==null?null:ALIASES.getOrDefault(type,type);}
 /** The lowest level that holds a core, and the level each ring stands for. */
 public static final int CORE_LEVEL=2,FIRST_RING=3,LAST_RING=6;
 /** The core type of a building type: quarry → mine, any hall → town_hall, houses and anything outside the catalogue → null. */
 public static String coreType(String buildingType){
  if(buildingType==null)return null;buildingType=CORE_OF.getOrDefault(buildingType,buildingType);if(buildingType.equals("quarry"))return "mine";if(buildingType.startsWith("town_hall"))return "town_hall";
  return TYPES.contains(buildingType)?buildingType:null;
 }
 /** Registry path of a core type's block and item, e.g. core_farm. */
 public static String path(String coreType){return "core_"+coreType;}
 /** Item id of the core a building type holds (villageastra:core_mine for the quarry), or null for a building without a core. */
 public static String coreId(String buildingType){var t=coreType(buildingType);return t==null?null:NS+":"+path(t);}
 /** Item id of the ring that raises a core to this grade (3..6). */
 public static String ringId(int grade){if(grade<FIRST_RING||grade>LAST_RING)throw new IllegalArgumentException("No core ring for grade "+grade);return NS+":core_ring_"+grade;}
 public static boolean isRing(String id){return id!=null&&ringGrade(id)>0;}
 public static boolean isCore(String id){return id!=null&&id.startsWith(NS+":core_")&&TYPES.contains(id.substring(NS.length()+6));}
 /** The grade a ring id stands for, or 0 if the id is not a ring. */
 public static int ringGrade(String id){
  if(id==null||!id.startsWith(NS+":core_ring_"))return 0;
  try{int g=Integer.parseInt(id.substring(NS.length()+11));return g>=FIRST_RING&&g<=LAST_RING?g:0;}catch(NumberFormatException e){return 0;}
 }
 /** The core type of a core item id, or null. */
 public static String typeOf(String coreId){return isCore(coreId)?coreId.substring(NS.length()+6):null;}
}
