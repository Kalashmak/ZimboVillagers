package org.villageastra.world;

import org.junit.jupiter.api.Test;
import org.villageastra.domain.CoreEffects;
import static org.junit.jupiter.api.Assertions.*;

/** AD-147 §6.1 (owner defaults Q3, Q6): the warehouse's numbers - couriers 1/1/1/2/2/2 from its staff row (the core's "couriers" reads the
 *  same row), a store of 54 slots a page of its plan (108/162/324/432/486/648), five stacks in a cart from II. */
class WarehouseBalanceTest {
    static int pages(int level){int n=0;for(var c:VillageStyle.WAREHOUSE_PAGES)if(c[0]<=level)n++;return n/2;}
    @Test void theWarehouseNumbersFollowTheLadder(){
        int[] couriers={1,1,1,2,2,2},slots={108,162,324,432,486,648},stacks={0,5,5,5,5,5};
        for(int level=1;level<=6;level++){
            assertEquals(couriers[level-1],Staff.slots("warehouse",level),"couriers at "+level);
            assertEquals(couriers[level-1],CoreEffects.value("warehouse","couriers",level),"the core's couriers read the staff row at "+level);
            assertEquals(slots[level-1],pages(level)*54,"the plan's pages at "+level);
            assertEquals(slots[level-1],CoreEffects.value("warehouse","storage",level),"the core's storage at "+level);
            assertEquals(stacks[level-1],CoreEffects.value("warehouse","cart_stacks",level),"the cart's stacks at "+level);}
        assertEquals(2*CoreEffects.value("warehouse","storage",2),CoreEffects.value("warehouse","storage",3),"III doubles the store");
    }
}
