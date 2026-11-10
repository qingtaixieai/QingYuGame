package game.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class SceneTopologyTest {
    private static WorldMap.Hex h(int q,int r){return new WorldMap.Hex(q,r);}
    @Test void alternativeRouteKeepsPortalsConnected(){
        var cells=Set.of(h(0,0),h(1,0),h(2,0),h(0,1),h(1,1));
        var graph=new SceneTopology(cells);
        assertTrue(graph.connected(h(0,0),h(2,0),Set.of(h(1,0))));
        assertFalse(graph.connected(h(0,0),h(2,0),Set.of(h(1,0),h(1,1))));
    }
    @Test void temporaryOccupantsAffectPathButNotTerrainConnectivity(){
        var graph=new SceneTopology(Set.of(h(0,0),h(1,0),h(2,0)));
        assertEquals(List.of(h(1,0),h(2,0)),graph.path(h(0,0),h(2,0),Set.of()));
        assertTrue(graph.path(h(0,0),h(2,0),Set.of(h(1,0))).isEmpty());
        assertTrue(graph.connected(h(0,0),h(2,0),Set.of()));
    }
    @Test void invalidAndBlockedEndpointsAreNotConnected(){
        var graph=new SceneTopology(Set.of(h(0,0)));
        assertFalse(graph.connected(h(9,9),h(9,9),Set.of()));
        assertFalse(graph.connected(h(0,0),h(0,0),Set.of(h(0,0))));
        assertTrue(graph.connected(h(0,0),h(0,0),Set.of()));
    }
}
