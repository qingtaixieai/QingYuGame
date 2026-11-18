package game.world;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BattleExitRingTest {
    private final WorldMap.Hex origin=new WorldMap.Hex(0,0);

    @Test void onlyADoorCreatesAnIndoorExitEvenIfOtherEdgesSupplyCoordinates(){
        var door=new WorldMap.Hex(0,1);
        var scene=new SceneMap(UUID.randomUUID(),"test",2,3,origin,"Room",2,List.of(
            new SceneMap.Cell(0,0,"floor",true),new SceneMap.Cell(0,1,"floor",true)
        ));
        var ring=BattleExitRing.cells(scene,List.of(edge(0,true,origin),edge(6,true,door)));
        assertEquals(1,ring.size());
        assertEquals(door,ring.getFirst().hex());
        assertEquals(door,ring.getFirst().anchor());
        assertEquals(List.of(6),ring.getFirst().directions());
        var closed=BattleExitRing.cells(scene,List.of(edge(6,false,door))).getFirst();
        assertFalse(closed.walkable());
    }

    @Test void outdoorCornerUsesOnlyItsOpenDirection(){
        var scene=new SceneMap(UUID.randomUUID(),"test",2,2,origin,"Field",1,
            origin.neighbors().stream().map(h->new SceneMap.Cell(h.q(),h.r(),"grass",true)).toList());
        var edges=new ArrayList<BattleService.Edge>();
        for(int direction=0;direction<6;direction++)edges.add(edge(direction,direction==0,null));
        var ring=BattleExitRing.cells(scene,edges);
        assertEquals(Set.copyOf(origin.neighbors()),Set.copyOf(ring.stream().map(BattleExitRing.Cell::hex).toList()));
        assertTrue(ring.stream().anyMatch(c->!c.walkable()));
        assertTrue(ring.stream().anyMatch(BattleExitRing.Cell::walkable));
        assertTrue(ring.stream().filter(BattleExitRing.Cell::walkable).allMatch(c->c.directions().equals(List.of(0))));
    }

    private BattleService.Edge edge(int direction,boolean open,WorldMap.Hex cell){
        return new BattleService.Edge(direction,"Exit",0,0,open,null,"Destination",cell);
    }
}
