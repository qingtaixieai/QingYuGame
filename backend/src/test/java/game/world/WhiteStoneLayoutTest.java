package game.world;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhiteStoneLayoutTest {
    @Test void authoredTownHasStableDoorsAndConnectedApproaches() {
        var world=WorldMap.generate(240910);
        var sample=WhiteStoneLayout.generate(world,15);
        assertEquals(sample,WhiteStoneLayout.generate(world,15));
        assertEquals(SceneMap.outdoor(world,WhiteStoneLayout.OWNER,15).id(),sample.id());
        assertTrue(WhiteStoneLayout.applies(sample));
        var plaza=WhiteStoneLayout.plazaAccess();
        var topology=sample.topology();
        var origin=new WorldMap.Hex(0,0);
        for(var fixture:Set.of(origin,new WorldMap.Hex(-3,3))){
            var cell=sample.cells().stream().filter(c->c.hex().equals(fixture)).findFirst().orElseThrow();
            assertEquals("furniture",cell.terrain());
            assertFalse(cell.walkable());
            assertFalse(topology.connected(plaza,fixture,Set.of()));
        }
        for(var direction:origin.neighbors()){
            var border=new WorldMap.Hex(direction.q()*15,direction.r()*15);
            assertTrue(topology.connected(border,plaza,Set.of()));
        }
        for(var kind:Set.of("shop","workshop","smith","inn")){
            var centre=WhiteStoneLayout.centre(kind);
            var door=WhiteStoneLayout.door(kind);
            assertFalse(topology.canStep(door,centre,Set.of()));
            assertTrue(topology.connected(plaza,door,Set.of()));
        }
        var fence=sample.blockedEdges().getFirst();
        assertFalse(topology.canStep(fence.from(),fence.to(),Set.of()));
        assertFalse(topology.canStep(fence.to(),fence.from(),Set.of()));
        assertTrue(topology.connected(fence.from(),fence.to(),Set.of()));
    }

    @Test void regularGenerationKeepsExistingTownGenerationVersion() {
        var world=WorldMap.generate(240910);
        var regular=SceneLandscape.generate(world,WhiteStoneLayout.OWNER,15);
        assertFalse(WhiteStoneLayout.applies(regular));
    }
}
