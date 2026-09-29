package org.villageastra.server;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

/** Starter-house suitability. Unloaded geometry never counts as damage. */
public final class BuildingIntegrity {
    public enum Result { USABLE, DAMAGED, UNKNOWN }
    private record Rules(int minimum_solid_floor_cells,int minimum_solid_roof_cells) {}
    private static final Rules RULES=readRules();
    private static Rules readRules() {
        var stream=BuildingIntegrity.class.getResourceAsStream("/data/villageastra/balance/housing.json");
        if(stream==null)throw new IllegalStateException("Missing housing balance");
        try(var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)) {
            Rules rules=new com.google.gson.Gson().fromJson(reader,Rules.class);
            if(rules.minimum_solid_floor_cells()<1 || rules.minimum_solid_floor_cells()>49 || rules.minimum_solid_roof_cells()<1 || rules.minimum_solid_roof_cells()>49)throw new IllegalStateException("Invalid housing balance");
            return rules;
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
    }
    private BuildingIntegrity() {}
    public static Result home(ServerLevel level,BlockPos base) { return home(level,base,"home"); }
    /** Beds, doors and solid floor/ceiling taken from the design itself; thresholds scale with the footprint (7×7 = balance values). */
    public static Result home(ServerLevel level,BlockPos base,String design) { return home(level,base,design,0); }
    /** AD-068: the same check for a turned house. */
    public static Result home(ServerLevel level,BlockPos base,String design,int turns) { return home(level,base,design,turns,-1); }
    /** AD-123 (H3): with {@code beds} ≥ 0 a house is damaged for its beds only when fewer whole beds stand anywhere in its footprint than the
     *  people it holds, so a level design that gained beds never evicts a house built with fewer. Doors are still checked cell by cell. */
    public static Result home(ServerLevel level,BlockPos base,String design,int turns,int beds) {
        var size=org.villageastra.world.BuildingPlacement.size(design,turns);int width=size[0],depth=size[1];
        for(int x=0;x<width;x++)for(int z=0;z<depth;z++) if(!level.hasChunkAt(base.offset(x,0,z)))return Result.UNKNOWN;
        var layout=org.villageastra.world.BuildingPlacement.layout(design,base,turns);
        if(beds>=0) {
            int top=0;for(var p:layout.keySet())top=Math.max(top,p.getY()-base.getY());
            int feet=0;for(int x=0;x<width;x++)for(int z=0;z<depth;z++)for(int y=0;y<=top;y++){var s=level.getBlockState(base.offset(x,y,z));if(org.villageastra.world.HousingLadder.wholeFoot(level,base.offset(x,y,z),s))feet++;}
            if(feet<beds)return Result.DAMAGED;
        }
        for(var cell:layout.entrySet()) {
            var expected=cell.getValue();
            if(!(expected.getBlock() instanceof BedBlock) && !(expected.getBlock() instanceof DoorBlock))continue;
            if(beds>=0 && expected.getBlock() instanceof BedBlock)continue;
            var state=level.getBlockState(cell.getKey());
            if(expected.getBlock() instanceof BedBlock && (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART)!=expected.getValue(BedBlock.PART)
                    || state.getValue(BedBlock.FACING)!=expected.getValue(BedBlock.FACING)))return Result.DAMAGED;
            if(expected.getBlock() instanceof DoorBlock && (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF)!=expected.getValue(DoorBlock.HALF)))return Result.DAMAGED;
        }
        int floor=0,roof=0,cells=width*depth;
        for(int x=0;x<width;x++)for(int z=0;z<depth;z++) {
            if(level.getBlockState(base.offset(x,0,z)).isSolidRender(level,base.offset(x,0,z)))floor++;
            if(level.getBlockState(base.offset(x,4,z)).isSolidRender(level,base.offset(x,4,z)))roof++;
        }
        // A mostly destroyed shell cannot remain housing just because two beds survived.
        return floor*49>=RULES.minimum_solid_floor_cells()*cells && roof*49>=RULES.minimum_solid_roof_cells()*cells ? Result.USABLE : Result.DAMAGED;
    }
}
