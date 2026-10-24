package game.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SceneRuntimeTest {
    @Test void matchingEdgesAndBlockedCellsAreRespected(){
        var map=SceneMap.room(UUID.randomUUID(),"test",new WorldMap.Hex(0,0),"test",3);
        var occupied=new HashSet<WorldMap.Hex>();
        for(var cell:map.cells())if(SceneRuntime.sides(cell.hex(),3).contains(0))occupied.add(cell.hex());
        assertNull(SceneRuntime.edgeCell(map,0,occupied));
        var only=new WorldMap.Hex(3,-1);occupied.remove(only);
        assertEquals(only,SceneRuntime.edgeCell(map,0,occupied));
        assertEquals(List.of(0),SceneRuntime.sides(only,3));
    }
    @Test void rememberedPositionFallsBackNearbyWithoutCrossingAnObstacle(){
        var a=new WorldMap.Hex(0,0);var b=new WorldMap.Hex(1,0);
        var map=new SceneMap(UUID.randomUUID(),"test",1,2,a,"test",3,List.of(
            new SceneMap.Cell(0,0,"grass",true),new SceneMap.Cell(1,0,"grass",true),new SceneMap.Cell(3,0,"grass",true)));
        assertEquals(b,SceneRuntime.nearest(map,a,Set.of(a)));
        assertNull(SceneRuntime.nearest(map,a,Set.of(a,b)));
    }
}
