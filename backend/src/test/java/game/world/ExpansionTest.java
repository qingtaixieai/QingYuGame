package game.world;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpansionTest {
    @Test void preservesMainlandAndCreatesReachableSeparatedIsland(){
        Set<WorldMap.Hex> landings=new HashSet<>();
        for(long seed=0;seed<100;seed++){
            var old=WorldMap.generate(seed);var expanded=WorldMap.expand(old);var index=expanded.index();var oldIndex=old.index();
            var port=expanded.place("port");var landing=expanded.place("landing");landings.add(landing.hex());
            for(var t:old.tiles()){
                var actual=index.get(t.hex());assertEquals(t.terrain(),actual.terrain());assertEquals(t.road(),actual.road());
                if(!t.hex().equals(port.hex()))assertEquals(t,actual);
            }
            assertEquals(old.spawn(),expanded.spawn());assertFalse(expanded.path(old.spawn(),port.hex()).isEmpty());
            assertTrue(expanded.path(old.spawn(),landing.hex()).isEmpty());
            var route=expanded.seaRoute();assertTrue(route.size()>2,"seed "+seed+" route "+route);assertTrue(port.hex().neighbors().contains(route.getFirst()));assertTrue(landing.hex().neighbors().contains(route.getLast()));
            for(int i=0;i<route.size();i++){assertEquals("ocean",index.get(route.get(i)).terrain());if(i>0)assertTrue(route.get(i-1).neighbors().contains(route.get(i)));}
            long newLand=expanded.tiles().stream().filter(t->!oldIndex.containsKey(t.hex())&&t.walkable()).count();assertEquals(61,newLand);
            for(var t:expanded.tiles())if(!oldIndex.containsKey(t.hex())&&t.walkable()&&!t.hex().equals(landing.hex()))assertFalse(expanded.path(landing.hex(),t.hex()).isEmpty());
            assertSame(expanded,WorldMap.expand(expanded));assertEquals(expanded.tiles(),WorldMap.expand(old).tiles());
        }
        assertTrue(landings.size()>5);
    }
    @Test void multiCellPathAvoidsActorsAndWorldBoundary(){
        var origin=new WorldMap.Hex(0,0);var goal=new WorldMap.Hex(3,0);var blocked=Set.of(new WorldMap.Hex(1,0),new WorldMap.Hex(2,0));
        var path=BattleMovement.path(6,origin,goal,blocked);assertEquals(4,path.size());var previous=origin;
        for(var h:path){assertTrue(previous.neighbors().contains(h));assertFalse(blocked.contains(h));previous=h;}
        assertEquals(goal,previous);assertTrue(BattleMovement.path(6,origin,new WorldMap.Hex(7,0),blocked).isEmpty());
        assertTrue(BattleMovement.path(6,origin,new WorldMap.Hex(1,0),blocked).isEmpty());
        assertTrue(BattleMovement.path(6,origin,goal,new HashSet<>(origin.neighbors())).isEmpty());
    }
}
