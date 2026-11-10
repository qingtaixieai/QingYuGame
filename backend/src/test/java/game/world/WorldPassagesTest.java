package game.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldPassagesTest {
    private final WorldMap.Hex a=new WorldMap.Hex(0,0),b=new WorldMap.Hex(1,0);
    @Test void oneClosedAlternativeDoesNotCloseTheSharedEdge(){
        var p=new WorldPassages();
        p.replace(List.of(new WorldPassages.Connection(a,b,false),new WorldPassages.Connection(b,a,true)));
        assertTrue(p.open(a,b));assertTrue(p.open(b,a));assertTrue(p.blocked().isEmpty());
        p.replace(List.of(new WorldPassages.Connection(a,b,false)));
        assertFalse(p.open(a,b));assertFalse(p.open(b,a));assertEquals(1,p.blocked().size());
        p.replace(List.of());assertTrue(p.open(a,b));
    }
    @Test void rejectsNonAdjacentAndDegenerateConnections(){
        assertThrows(IllegalArgumentException.class,()->new WorldPassages.Connection(a,a,true));
        assertThrows(IllegalArgumentException.class,()->new WorldPassages.Connection(a,new WorldMap.Hex(4,0),true));
    }
    @Test void blockedEndpointCannotCountAsAnAlternative(){
        var id=UUID.randomUUID();
        var map=new SceneMap(id,"v",1,2,a,"test",2,List.of(
            new SceneMap.Cell(0,0,"grass",true),new SceneMap.Cell(1,0,"grass",false)));
        assertFalse(WorldPassages.endpointConnected(map,b,List.of(a)));
        assertTrue(WorldPassages.endpointConnected(map,a,List.of(a)));
    }
    @Test void disconnectedRequiredExitClosesRouteButDetourKeepsItOpen(){
        var cells=new ArrayList<>(List.of(new SceneMap.Cell(0,0,"grass",true),new SceneMap.Cell(1,0,"rock",false),new SceneMap.Cell(2,0,"grass",true)));
        var id=UUID.randomUUID();var target=new WorldMap.Hex(2,0);
        assertFalse(WorldPassages.endpointConnected(new SceneMap(id,"v",1,2,a,"test",3,cells),a,List.of(target)));
        cells.addAll(List.of(new SceneMap.Cell(0,1,"grass",true),new SceneMap.Cell(1,1,"grass",true)));
        assertTrue(WorldPassages.endpointConnected(new SceneMap(id,"v",1,2,a,"test",3,cells),a,List.of(target)));
    }
}
