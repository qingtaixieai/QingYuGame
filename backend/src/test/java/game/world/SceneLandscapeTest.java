package game.world;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneLandscapeTest {
    @Test void generatedTerrainIsStableAndAllBoundaryDirectionsRemainReachable() {
        var world=WorldMap.generate(240910);
        var origin=new WorldMap.Hex(0,0);
        for(var terrain:List.of("plain","forest","beach","hill")){
            var tile=world.tiles().stream().filter(t->t.walkable()&&t.terrain().equals(terrain)).findFirst().orElse(null);
            if(tile==null)continue;
            var map=SceneLandscape.generate(world,tile.hex(),15);
            assertEquals(map,SceneLandscape.generate(world,tile.hex(),15));
            assertEquals(721,map.cells().size());
            assertTrue(map.cells().stream().map(SceneMap.Cell::terrain).distinct().count()>1);
            var topology=map.topology();
            for(var d:origin.neighbors())
                assertTrue(topology.connected(origin,new WorldMap.Hex(d.q()*15,d.r()*15),Set.of()));
        }
    }

    @Test void aClosedEdgeRejectsDirectStepsInBothDirectionsButAllowsDetours() {
        var a=new WorldMap.Hex(0,0);
        var b=new WorldMap.Hex(1,0);
        var c=new WorldMap.Hex(0,1);
        var edge=new SceneMap.Edge(a,b);
        var topology=new SceneTopology(List.of(a,b,c),List.of(edge));
        assertFalse(topology.canStep(a,b,Set.of()));
        assertFalse(topology.canStep(b,a,Set.of()));
        assertEquals(List.of(c,b),topology.path(a,b,Set.of()));
        assertEquals(List.of(c,b),BattleMovement.path(2,a,b,Set.of(new WorldMap.Hex(1,-1)),List.of(edge)));
        assertTrue(topology.path(a,b,Set.of(c)).isEmpty());
    }
}
