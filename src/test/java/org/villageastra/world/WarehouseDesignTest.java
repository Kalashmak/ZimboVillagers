package org.villageastra.world;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** AD-147 §3 (CF-A): the warehouse's six plans on one 23x17 lot. The door (6,1,1), the stock chest (1,1,4) with its pair (1,1,3) and the
 *  core cell stand on every level; the cells of the store's other page chests are kept free (WarehouseStorage sets them down); the building grows with every level; VI is in the hall's castle
 *  masonry; and the owner's door rule (2026-09-23): no fence, gate, pane, bars, wall or trapdoor beside either half of a door. */
class WarehouseDesignTest {
    static final List<String> CONNECTING=List.of("fence","pane","iron_bars","_wall[","trapdoor");
    static Map<VillageStyle.Cell,String> plan(int level){return VillageStyle.plan("warehouse@"+level);}
    static String at(Map<VillageStyle.Cell,String> p,int x,int y,int z){return p.getOrDefault(new VillageStyle.Cell(x,y,z),"air");}
    static long volume(Map<VillageStyle.Cell,String> p){return p.entrySet().stream().filter(e->e.getKey().y()>=1&&!e.getValue().equals("air")).count();}

    @Test void everyLevelStandsOnTheLotWithItsFixedCells(){
        for(int level=1;level<=6;level++){var p=plan(level);
            for(var c:p.keySet())assertTrue(c.x()>=0&&c.x()<VillageStyle.WAREHOUSE_W&&c.z()>=0&&c.z()<VillageStyle.WAREHOUSE_D,"warehouse@"+level+" leaves its lot at "+c);
            assertTrue(at(p,6,1,1).startsWith("oak_door")&&at(p,6,1,1).contains("half=lower"),"warehouse@"+level+": the door at (6,1,1)");
            assertTrue(at(p,6,2,1).startsWith("oak_door")&&at(p,6,2,1).contains("half=upper"),"warehouse@"+level+": the door's upper half");
            assertTrue(at(p,1,1,4).startsWith(VillageStyle.CHEST),"warehouse@"+level+": the stock chest (1,1,4)");
            assertEquals("air",at(p,1,1,3),"warehouse@"+level+": its pair's cell (1,1,3) is kept for WarehouseStorage");
            var core=VillageStyle.WAREHOUSE_CORE;assertEquals("air",at(p,core[0],core[1],core[2]),"warehouse@"+level+": the core cell is kept free for the core");
            assertEquals("air",at(p,6,1,2),"warehouse@"+level+": the step inside the door is free");assertEquals("air",at(p,6,2,2),"warehouse@"+level+": headroom inside the door");
            long chests=p.values().stream().filter(v->v.startsWith(VillageStyle.CHEST)).count();
            assertEquals(1,chests,"warehouse@"+level+": the plan builds one chest, the master (one physical chest a design)");
            for(int i=1;i<VillageStyle.WAREHOUSE_PAGES.length;i++){var c=VillageStyle.WAREHOUSE_PAGES[i];
                if(c[0]<=level)assertEquals("air",at(p,c[1],c[2],c[3]),"warehouse@"+level+": page cell "+i+" is kept free for its chest");}}
    }
    @Test void noConnectingBlockBesideADoor(){
        for(int level=1;level<=6;level++){var p=plan(level);int doors=0;
            for(var e:p.entrySet()){if(!e.getValue().startsWith("oak_door"))continue;doors++;var c=e.getKey();
                for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){String side=at(p,c.x()+d[0],c.y(),c.z()+d[1]);
                    for(var bad:CONNECTING)assertFalse(side.contains(bad),"warehouse@"+level+": "+side+" beside the door at "+c);}}
            assertTrue(doors>=2,"warehouse@"+level+" has its door");}
    }
    @Test void theBuildingGrowsWithEveryLevel(){
        long before=0;int pages=0;
        for(int level=1;level<=6;level++){long v=volume(plan(level));assertTrue(v>before,"warehouse@"+level+" is bigger than the level before: "+v+" <= "+before);before=v;
            int n=0;for(var c:VillageStyle.WAREHOUSE_PAGES)if(c[0]<=level)n++;assertTrue(n/2>pages,"warehouse@"+level+" adds pages");pages=n/2;}
        assertEquals(12,pages,"VI has twelve pages (648 slots)");
    }
    @Test void theLastLevelIsInTheCastleMasonry(){
        var six=new HashSet<>(plan(6).values());
        for(var block:List.of("stone_bricks","polished_andesite","chiseled_stone_bricks"))assertTrue(six.stream().anyMatch(v->v.equals(block)),"VI uses "+block);
        assertTrue(six.stream().anyMatch(v->v.startsWith("deepslate_tile_stairs")),"VI's roofs are deepslate tile");
        assertFalse(plan(1).values().stream().anyMatch(v->v.startsWith("deepslate")),"I has no deepslate (no level-IV mine yet)");
    }
}
