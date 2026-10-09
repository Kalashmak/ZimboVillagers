package org.villageastra.gametest;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraftforge.gametest.*;
import org.villageastra.VillageAstra;
import org.villageastra.domain.OrganicLots;
import org.villageastra.world.SettlementSectors;
@GameTestHolder(VillageAstra.ID) @PrefixGameTestTemplate(false)
public final class OrganicLotRecoveryGameTests {
 @GameTest(template="empty",batch="organic_lot_recovery",timeoutTicks=100)
 public static void sectorSurveySurvivesAnExhaustedLayoutCandidate(GameTestHelper h){
  long seed=1512229112050356085L;
  var exhausted=UUID.fromString("7df7eabf-14fb-3273-81e5-bf000396d621");
  h.assertTrue(OrganicLots.redraws(exhausted,OrganicLots.CURRENT)==-1,"The actual crash candidate still exhausts its unchanged layout draws");
  var l=h.getLevel();var generator=(NoiseBasedChunkGenerator)l.getChunkSource().getGenerator();
  var state=RandomState.create(generator.generatorSettings().value(),l.registryAccess().lookupOrThrow(Registries.NOISE),seed);
  var plan=SettlementSectors.plan(generator,state,seed,l,1574,0);
  h.assertTrue(plan==SettlementSectors.plan(generator,state,seed,l,1574,0),"Bounded survey result is cached, including an unsuitable sector");
  if(plan!=null)h.assertTrue(OrganicLots.buildings(plan.id()).size()==7,"Any selected village has its complete existing lot allocation");
  h.succeed();
 }
}
