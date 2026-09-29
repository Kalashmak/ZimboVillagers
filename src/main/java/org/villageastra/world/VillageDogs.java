package org.villageastra.world;
import java.util.*;
import net.minecraft.server.level.ServerLevel;
import org.villageastra.server.SettlementData;
/** AD-139: the dogs of a village as the restaurant's couriers see them — the one seam between the restaurant and the livestock work (AD-138).
 *  Owner rule: a village's wolves and dogs appear only after their quest, their building (the kennel) and their research level. Until the
 *  livestock work installs its kennel registry with {@link #provide}, no village has a dog: {@link #available} is empty, the level-V courier
 *  carries by hand and the restaurant's card marks the dog as planned. Nothing here spawns or tames an animal. */
public final class VillageDogs {
 private VillageDogs(){}
 /** What the livestock work answers: the free, fed dogs of a village (their entity ids), and a dog sent along with a courier and back.
  *  Contract (AD-147 §5.1): a dog given to {@link #follow} or {@link #harness} is not listed by {@link #free} again until {@link #release};
  *  the logistics keep no list of their own busy dogs. The warehouse's wolves (AD-147, V-VI) use the three methods below; a source that
  *  does not answer them (their defaults) harnesses no wolf, so the warehouse's couriers carry on alone. */
 public interface Source{
  List<UUID> free(ServerLevel l,SettlementData.Entry e);
  default void follow(ServerLevel l,SettlementData.Entry e,UUID dog,ResidentEntity courier){}
  default void release(ServerLevel l,SettlementData.Entry e,UUID dog){}
  /** AD-147: the dog takes the hitch of this cart (the cart follows it); false when it cannot now. */
  default boolean harness(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){return false;}
  /** AD-147 (VI): the dog goes on its own to a place, pulling its cart; false when it cannot now. */
  default boolean send(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos to){return false;}
  /** AD-147: whether the dog stands within {@code r} of a place (false for a dog not loaded or gone). */
  default boolean near(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos at,double r){return false;}
  /** AD-147: whether this source answers harness/send/near (its dogs pull carts). The kennel registry (VillageWolves.DOGS) does not yet:
   *  until it does, the warehouse's wolves stay planned even with kennel wolves (their couriers are never relieved for wolves that cannot pull). */
  default boolean pulls(){return false;}
 }
 private static volatile Source source=null;
 /** Installed by the livestock work (its kennel registry); a test may install a stand-in and must clear it after. */
 public static void provide(Source s){source=s;}
 /** Whether any source of dogs exists yet (false until the livestock work is merged). */
 public static boolean provided(){return source!=null;}
 /** Whether the source's dogs pull carts (AD-147): the warehouse's wolves need it; the restaurant's dog (follow) does not. */
 public static boolean pulls(){var s=source;return s!=null&&s.pulls();}
 /** The village's free, fed dogs that may go with a courier now; empty without a source. */
 public static List<UUID> available(ServerLevel l,SettlementData.Entry e){var s=source;return s==null?List.of():List.copyOf(s.free(l,e));}
 public static void follow(ServerLevel l,SettlementData.Entry e,UUID dog,ResidentEntity courier){var s=source;if(s!=null&&dog!=null)s.follow(l,e,dog,courier);}
 /** Frees a dog: a cart it pulls drops the hitch first (the livestock side calls this too), then the source takes the dog back. */
 public static void release(ServerLevel l,SettlementData.Entry e,UUID dog){if(dog==null)return;CartEntity.unhitch(l,dog);var s=source;if(s!=null)s.release(l,e,dog);}
 public static boolean harness(ServerLevel l,SettlementData.Entry e,UUID dog,CartEntity cart){var s=source;return s!=null&&dog!=null&&cart!=null&&s.harness(l,e,dog,cart);}
 public static boolean send(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos to){var s=source;return s!=null&&dog!=null&&s.send(l,e,dog,to);}
 public static boolean near(ServerLevel l,SettlementData.Entry e,UUID dog,net.minecraft.core.BlockPos at,double r){var s=source;return s!=null&&dog!=null&&s.near(l,e,dog,at,r);}
}
