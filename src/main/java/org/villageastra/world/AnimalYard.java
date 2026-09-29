package org.villageastra.world;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.AABB;
import org.villageastra.domain.Settlement;
import org.villageastra.server.SettlementData;
/** AD-140: the one place the animal quests touch the livestock yard (AD-035/AD-138 belong to the livestock side). Which kinds the yard keeps,
 *  whether an animal stands in a working pen of its kind (LivestockPens, the kind each pen keeps by LivestockPolicies), and taking an animal
 *  into that pen's herd. */
public final class AnimalYard {
 private AnimalYard(){}
 public static final String TYPE="livestock";
 /** Test seam: the working level a GameTest's yard is taken to have (a real level above I needs its whole equipment built). */
 public static final Map<UUID,Integer> TEST_LEVEL=new java.util.concurrent.ConcurrentHashMap<>();
 /** The village's livestock yard, or null. */
 public static Settlement.Building yard(SettlementData.Entry e){for(var b:e.settlement().buildings())if(b.type().equals(TYPE))return b;return null;}
 /** The kind an animal is, as the yard names it ("" for anything else). */
 public static String kind(net.minecraft.world.entity.Entity a){
  return a instanceof MushroomCow?"":a instanceof Sheep?"sheep":a instanceof Cow?"cow":a instanceof Chicken?"chicken":a instanceof Pig?"pig":a instanceof Wolf?"wolf":"";
 }
 /** The pens the yard works now (a GameTest's yard may be taken to stand at a level whose equipment it does not have). */
 static List<LivestockPens.Pen> pens(ServerLevel l,SettlementData.Entry e,Settlement.Building yard){
  Integer test=TEST_LEVEL.get(e.settlement().id());return test!=null?LivestockPens.built(test):LivestockGoal.pens(l,e,yard);
 }
 /** The kinds the yard keeps now, in the order of its pens: what the board may ask for. A kind the mayor gives a pen is asked for too. */
 public static List<String> kinds(ServerLevel l,SettlementData.Entry e){
  var yard=yard(e);if(yard==null)return List.of();var out=new ArrayList<String>();
  for(var p:pens(l,e,yard)){var k=LivestockGoal.species(l,e,yard,p);if(!out.contains(k))out.add(k);}
  return out;
 }
 /** The working pen of this kind at a spot of the world, or null. */
 static LivestockPens.Pen penAt(ServerLevel l,SettlementData.Entry e,BlockPos at,String kind){
  var yard=yard(e);if(yard==null)return null;var p=LivestockPens.penAt(e,yard,at);
  if(p==null||!pens(l,e,yard).contains(p)||!LivestockGoal.species(l,e,yard,p).equals(kind))return null;return p;
 }
 /** Whether the animal stands in a working pen of its own kind of this village's yard. */
 public static boolean inPen(ServerLevel l,SettlementData.Entry e,net.minecraft.world.entity.Entity a,String kind){return kind.equals(kind(a))&&penAt(l,e,a.blockPosition(),kind)!=null;}
 /** Whether the spot lies in a working pen of this kind (the quest's hay nest). */
 public static boolean inPen(ServerLevel l,SettlementData.Entry e,BlockPos pos,String kind){return penAt(l,e,pos,kind)!=null;}
 /** Takes the animal into the village's herd, into the pen it stands in: the keeper's own from now on. */
 public static void admit(ServerLevel l,SettlementData.Entry e,Animal a,String kind){
  var yard=yard(e);var p=yard==null?null:penAt(l,e,a.blockPosition(),kind);
  if(p!=null)LivestockPens.tag(a,e.settlement().id(),yard,p);else{a.getPersistentData().putUUID(LivestockGoal.OWNER,e.settlement().id());a.setPersistenceRequired();}
 }
 /** Test and probe seam: a spot in the middle of the working pen of this kind, or null. */
 public static BlockPos middle(ServerLevel l,SettlementData.Entry e,String kind){
  var yard=yard(e);if(yard==null)return null;
  for(var p:pens(l,e,yard))if(LivestockGoal.species(l,e,yard,p).equals(kind))return LivestockPens.at(e,yard,new BlockPos(p.x()+3,1,p.z()+3));
  return null;
 }
 /** The kinds the village's own animals in the yard are of (the yard's ground and its search reach). Null while that ground is not in the world. */
 public static Set<String> present(ServerLevel l,SettlementData.Entry e){
  var yard=yard(e);if(yard==null)return Set.of();var box=LivestockPens.yardBox(e,yard).inflate(2);
  // Only once all of the yard's ground and its animals are in the world: a half-loaded yard would lock a kind it keeps.
  for(int x=(int)Math.floor(box.minX)>>4;x<=(int)Math.floor(box.maxX)>>4;x++)for(int z=(int)Math.floor(box.minZ)>>4;z<=(int)Math.floor(box.maxZ)>>4;z++)
   if(!l.hasChunk(x,z)||!l.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(x,z)))return null;
  var out=new TreeSet<String>();
  for(var a:l.getEntitiesOfClass(Animal.class,box,x->x.isAlive()&&LivestockGoal.owned(x,e.settlement().id()))){var k=kind(a);if(!k.isEmpty())out.add(k);}
  return out;
 }
 /** Where the yard stands, for the chart's home and the distance checks. */
 public static BlockPos at(SettlementData.Entry e){var yard=yard(e);return yard==null?e.center():LogisticsRoutes.position(e,yard);}
}
