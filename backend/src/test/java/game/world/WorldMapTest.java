package game.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class WorldMapTest {
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
