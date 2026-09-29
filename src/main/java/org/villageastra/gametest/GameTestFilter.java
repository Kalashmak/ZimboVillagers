package org.villageastra.gametest;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.villageastra.VillageAstra;
/** Dev-only: {@code runGameTestServer -PgtOnly=a,b} runs only the GameTests whose name or batch contains one of the given words (case
 *  ignored), so one long test can be iterated on without the whole 28-minute suite. Without the flag nothing changes. */
@Mod.EventBusSubscriber(modid=VillageAstra.ID)
public final class GameTestFilter {
 private GameTestFilter(){}
 @SubscribeEvent public static void started(ServerStartedEvent event){
  String raw=System.getProperty("villageastra.gtOnly");
  if(raw==null||raw.isBlank()||!(event.getServer() instanceof GameTestServer server))return;
  var words=Arrays.stream(raw.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
  try{
   var field=GameTestServer.class.getDeclaredField("testBatches");field.setAccessible(true);
   @SuppressWarnings("unchecked") var batches=(List<GameTestBatch>)field.get(server);
   var before=GameTestBatch.class.getDeclaredField("beforeBatchFunction");var after=GameTestBatch.class.getDeclaredField("afterBatchFunction");before.setAccessible(true);after.setAccessible(true);
   var kept=new ArrayList<GameTestBatch>();int count=0;
   for(var batch:batches){
    var tests=batch.getTestFunctions().stream().filter(t->words.stream().anyMatch(w->t.getTestName().toLowerCase(Locale.ROOT).contains(w)||t.getBatchName().toLowerCase(Locale.ROOT).contains(w))).toList();
    if(tests.isEmpty())continue;count+=tests.size();
    @SuppressWarnings("unchecked") var b=(java.util.function.Consumer<net.minecraft.server.level.ServerLevel>)before.get(batch);
    @SuppressWarnings("unchecked") var a=(java.util.function.Consumer<net.minecraft.server.level.ServerLevel>)after.get(batch);
    kept.add(new GameTestBatch(batch.getName(),tests,b,a));}
   field.set(server,kept);
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_GT_FILTER {} -> {} tests in {} batches",words,count,kept.size());
  }catch(ReflectiveOperationException ex){throw new IllegalStateException("GameTest filter",ex);}
 }
}
