package org.villageastra.domain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FirstHouseBaselineTest {
 @Test void freshRequiresNoResearch(){var s=Settlement.natural(UUID.randomUUID());assertEquals(7,FirstHouseBaseline.buildings(s,false,false).size());assertThrows(IllegalStateException.class,()->FirstHouseBaseline.buildings(s,false,true));}
 @Test void resumedResearchDoesNotChangeBuildingBaseline(){var s=Settlement.natural(UUID.randomUUID());var fresh=FirstHouseBaseline.buildings(s,false,false);assertEquals(fresh,FirstHouseBaseline.buildings(s,true,true));}
 @Test void resumeCannotTreatAnAddedHouseAsAnInitialOne(){var s=Settlement.natural(UUID.randomUUID());s.addBuilding(new Settlement.Building(UUID.randomUUID(),"home",50,0,50));assertThrows(IllegalStateException.class,()->FirstHouseBaseline.buildings(s,true,true));}
}
