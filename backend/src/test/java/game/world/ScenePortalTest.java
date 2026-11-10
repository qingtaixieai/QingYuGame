package game.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class ScenePortalTest {
    @Test void bothDirectionsReferToTheSameConnection(){
        var a=new ScenePortal.Endpoint(UUID.randomUUID(),new WorldMap.Hex(1,0));
        var b=new ScenePortal.Endpoint(UUID.randomUUID(),new WorldMap.Hex(-1,0));
        var portal=new ScenePortal(UUID.randomUUID(),a,b,true);
        assertEquals(b,portal.destination(a));assertEquals(a,portal.destination(b));
        assertThrows(IllegalArgumentException.class,()->portal.destination(new ScenePortal.Endpoint(a.sceneId(),new WorldMap.Hex(0,0))));
        assertThrows(IllegalStateException.class,()->new ScenePortal(portal.id(),a,b,false).destination(a));
    }
}
