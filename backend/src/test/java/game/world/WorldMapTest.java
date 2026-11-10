package game.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class WorldMapTest {
    @Test void closedEdgeUsesAlternativeRouteAndBlocksBothDirectionsWithoutOne(){
        var a=new WorldMap.Hex(0,0);var b=new WorldMap.Hex(1,0);var c=new WorldMap.Hex(0,1);
        var tiles=new HashMap<WorldMap.Hex,WorldMap.Tile>();
        for(var h:List.of(a,b,c))tiles.put(h,new WorldMap.Tile(h.q(),h.r(),"plains",false,null));
        java.util.function.BiPredicate<WorldMap.Hex,WorldMap.Hex> open=(from,to)->
            !(from.equals(a)&&to.equals(b)||from.equals(b)&&to.equals(a));
        assertEquals(List.of(c,b),WorldMap.path(tiles,a,b,false,open));
        assertEquals(List.of(c,a),WorldMap.path(tiles,b,a,false,open));
        tiles.remove(c);
        assertTrue(WorldMap.path(tiles,a,b,false,open).isEmpty());
        assertTrue(WorldMap.path(tiles,b,a,false,open).isEmpty());
    }
    @Test void generatedWorldsHaveConnectedSettlementsAndLegalRoutes(){
        for(long seed=0;seed<100;seed++){
            WorldMap world=WorldMap.generate(seed);var tiles=world.index();
            for(var tile:world.tiles())if(tile.place()!=null&&!tile.hex().equals(world.spawn())){
                var path=world.path(world.spawn(),tile.hex());assertFalse(path.isEmpty(),"seed "+seed+" "+tile.place().name());
                var previous=world.spawn();
                for(var step:path){assertTrue(previous.neighbors().contains(step));assertTrue(tiles.get(step).walkable());previous=step;}
                assertEquals(tile.hex(),previous);
            }
            assertTrue(world.tiles().stream().anyMatch(t->t.terrain().equals("bridge")));
            for(var tile:world.tiles())if(!tile.walkable())assertTrue(world.path(world.spawn(),tile.hex()).isEmpty());
        }
    }
    @Test void generationIsReproducible(){assertEquals(WorldMap.generate(42).tiles(),WorldMap.generate(42).tiles());assertNotEquals(WorldMap.generate(42).tiles(),WorldMap.generate(43).tiles());}
    @Test void invalidDestinationIsRejected(){assertTrue(WorldMap.generate(1).path(new WorldMap.Hex(-3,3),new WorldMap.Hex(999,999)).isEmpty());}
}
