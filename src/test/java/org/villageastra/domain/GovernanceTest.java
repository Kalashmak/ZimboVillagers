package org.villageastra.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GovernanceTest {
 @Test void authoritySurvivesRestoreButOldOfficeDoesNot(){var a=UUID.randomUUID();var b=UUID.randomUUID();var p=UUID.randomUUID();var g=new Governance();g.appointPlayer(a);long epoch=g.epoch();assertTrue(g.setPaused(a,epoch,0,p,true));g=Governance.restore(a,g.epoch(),g.revision(),g.pausedProjects());assertTrue(g.paused(p));assertTrue(g.canManage(a,epoch));g.appointPlayer(b);assertFalse(g.setPaused(a,epoch,1,p,false));g.appointPlayer(a);assertFalse(g.setPaused(a,epoch,1,p,false));assertTrue(g.paused(p));}
 @Test void visitorAndDuplicateOrderCannotChangeWork(){var g=new Governance();var a=UUID.randomUUID();var p=UUID.randomUUID();g.appointPlayer(a);assertFalse(g.setPaused(UUID.randomUUID(),g.epoch(),0,p,true));assertTrue(g.setPaused(a,g.epoch(),0,p,true));assertFalse(g.setPaused(a,g.epoch(),0,p,false));assertTrue(g.paused(p));assertEquals(1,g.revision());}
 @Test void playerAppointmentDoesNotLeaveTwoMayors(){var s=Settlement.initial(UUID.randomUUID());s.appointPlayerMayor(UUID.randomUUID());assertEquals(0,s.residents().stream().filter(r->r.profession()==Profession.MAYOR).count());assertEquals(6,s.residents().size());assertEquals(3,s.homes().size());}
}
