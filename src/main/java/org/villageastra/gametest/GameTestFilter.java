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
  String raw=System.getProperty("villageastra.gtOnly","");String skip=System.getProperty("villageastra.gtSkip","");
  String shard=System.getProperty("villageastra.gtShard","");
  if(raw.isBlank()&&skip.isBlank()&&shard.isBlank()||!(event.getServer() instanceof GameTestServer server))return;
  int part=0,parts=1;
  if(!shard.isBlank()){
   var pair=shard.split("/",-1);if(pair.length!=2)throw new IllegalArgumentException("gtShard must be part/count, for example 1/4");
   try{part=Integer.parseInt(pair[0])-1;parts=Integer.parseInt(pair[1]);}catch(NumberFormatException ex){throw new IllegalArgumentException("Invalid gtShard: "+shard,ex);}
   if(parts<1||part<0||part>=parts)throw new IllegalArgumentException("gtShard requires 1 <= part <= count: "+shard);
  }
  final int selectedPart=part,partCount=parts;
  var words=Arrays.stream(raw.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
  var excluded=Arrays.stream(skip.toLowerCase(Locale.ROOT).split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
  try{
   var field=GameTestServer.class.getDeclaredField("testBatches");field.setAccessible(true);
   @SuppressWarnings("unchecked") var batches=(List<GameTestBatch>)field.get(server);
   var before=GameTestBatch.class.getDeclaredField("beforeBatchFunction");var after=GameTestBatch.class.getDeclaredField("afterBatchFunction");before.setAccessible(true);after.setAccessible(true);
   var kept=new ArrayList<GameTestBatch>();int count=0,total=0;var omitted=new ArrayList<String>();var selectedNames=new ArrayList<String>();var allNames=new TreeSet<String>();
   for(var batch:batches){
    total+=batch.getTestFunctions().size();
    batch.getTestFunctions().forEach(t->allNames.add(t.getTestName()));
    var tests=batch.getTestFunctions().stream().filter(t->{boolean chosen=words.isEmpty()||words.stream().anyMatch(w->matches(t,w));boolean drop=excluded.stream().anyMatch(w->matches(t,w));if(chosen&&drop)omitted.add(t.getTestName());return chosen&&!drop&&Math.floorMod(t.getTestName().hashCode(),partCount)==selectedPart;}).toList();
    if(tests.isEmpty())continue;count+=tests.size();
    tests.forEach(t->selectedNames.add(t.getTestName()));
    @SuppressWarnings("unchecked") var b=(java.util.function.Consumer<net.minecraft.server.level.ServerLevel>)before.get(batch);
    @SuppressWarnings("unchecked") var a=(java.util.function.Consumer<net.minecraft.server.level.ServerLevel>)after.get(batch);
    kept.add(new GameTestBatch(batch.getName(),tests,b,a));}
   field.set(server,kept);
   com.mojang.logging.LogUtils.getLogger().info("ASTRA_GT_FILTER only={} skip={} -> {}/{} tests in {} batches; explicitly omitted={}",words,excluded,count,total,kept.size(),omitted);
   if(!shard.isBlank()){
    if(allNames.size()!=total)throw new IllegalStateException("Duplicate registered GameTest names prevent exact shard accounting");
    Collections.sort(selectedNames);
    com.mojang.logging.LogUtils.getLogger().info("ASTRA_GT_SHARD part={}/{} total={} names={}",selectedPart+1,partCount,total,new com.google.gson.Gson().toJson(selectedNames));
    com.mojang.logging.LogUtils.getLogger().info("ASTRA_GT_CATALOG names={}",new com.google.gson.Gson().toJson(allNames));
   }
  }catch(ReflectiveOperationException ex){throw new IllegalStateException("GameTest filter",ex);}
 }
 private static boolean matches(TestFunction t,String word){return t.getTestName().toLowerCase(Locale.ROOT).contains(word)||t.getBatchName().toLowerCase(Locale.ROOT).contains(word);}
}
