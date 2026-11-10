package game.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

class SceneMapTest {
    @Test void outdoorLayoutSurvivesSerialization(){
        var world=WorldMap.generate(42);
        var scene=SceneMap.outdoor(world,world.spawn(),15);
        assertEquals(721,scene.cells().size());
        assertEquals(2,scene.level());
        var json=JsonMapper.builder().build();
        assertEquals(scene,json.readValue(json.writeValueAsString(scene),SceneMap.class));
        assertEquals(scene,SceneMap.outdoor(world,world.spawn(),15));
    }
    @Test void independentRoomsShareWorldOwnershipButNotIdentity(){
        var owner=new WorldMap.Hex(0,0);
        var a=SceneMap.room(UUID.randomUUID(),"v",owner,"房间甲",3);
        var b=SceneMap.room(UUID.randomUUID(),"v",owner,"房间乙",3);
        assertNotEquals(a.id(),b.id());
        assertEquals(a.owner(),b.owner());
        assertEquals(3,a.level());
        assertThrows(IllegalArgumentException.class,()->SceneMap.room(UUID.randomUUID(),"v",owner,"坏房间",-1));
    }
}
