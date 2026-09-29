package org.villageastra.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** probe-fix-01: over no door of a village-style design hangs a lantern in the lintel cell — a lantern there left a pocket
 *  beside the door where the starter home's builder got wedged with his head under it. */
class VillageStyleDoorTest {
    @Test void noLanternHangsInADoorLintel() {
        int doors=0;
        for(var id:VillageStyle.ids()){
            var plan=VillageStyle.plan(id);
            for(var cell:plan.entrySet()){
                if(!cell.getValue().startsWith("oak_door")||!cell.getValue().contains("half=upper"))continue;doors++;
                var c=cell.getKey();var over=plan.get(new VillageStyle.Cell(c.x(),c.y()+1,c.z()));
                assertNotNull(over,id+": nothing over the door at "+c);
                assertFalse(over.startsWith("lantern"),id+": the lintel over the door at "+c+" is "+over);
            }
        }
        // AD-159: the dog academy is an open yard behind a fence gate, one more design without a door.
        assertTrue(doors>=VillageStyle.ids().size()-4,"Most designs have a door: "+doors);
    }
}
