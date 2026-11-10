package game.world;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneRebuildTest {
    @Test void aSealedEdgeIsSymmetricAndStillAllowsADetour() {
        var start = new WorldMap.Hex(0,0);
        var end = new WorldMap.Hex(1,0);
        var detour = new WorldMap.Hex(0,1);
        var edge = new SceneMap.Edge(start,end);
        var topology = new SceneTopology(Set.of(start,end,detour),List.of(edge));
        assertFalse(topology.canStep(start,end,Set.of()));
        assertFalse(topology.canStep(end,start,Set.of()));
        assertEquals(List.of(detour,end),topology.path(start,end,Set.of()));
        assertTrue(topology.path(start,end,Set.of(detour)).isEmpty());
        var battleRoute = BattleMovement.path(2,start,end,Set.of(),List.of(edge));
        assertEquals(2,battleRoute.size());
        assertEquals(end,battleRoute.getLast());
    }

    @Test void generatedTownIsReproducibleAndConnectsToItsSixSides() {
        var world = WorldMap.generate(512L);
        var owner = world.spawn();
        var map = SceneLandscape.generate(world,owner,15);
        assertEquals(map,SceneLandscape.generate(world,owner,15));
        assertEquals(721,map.cells().size());
        var origin = new WorldMap.Hex(0,0);
        for (var direction : origin.neighbors()) {
            var border = new WorldMap.Hex(direction.q()*15,direction.r()*15);
            assertTrue(map.topology().connected(origin,border,Set.of()));
        }
    }
}
