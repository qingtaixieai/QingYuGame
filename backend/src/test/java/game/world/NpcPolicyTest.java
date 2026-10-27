package game.world;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NpcPolicyTest {
 @Test void hardRulesBeatScores(){assertEquals("wait",NpcPolicy.choose(List.of(new NpcPolicy.Candidate("illegal",1000,false),new NpcPolicy.Candidate("wait",1,true)),null,0).key());}
 @Test void keepsCurrentUntilClearlyBetter(){var options=List.of(new NpcPolicy.Candidate("home",10,true),new NpcPolicy.Candidate("shop",11,true));assertEquals("home",NpcPolicy.choose(options,"home",3).key());assertEquals("shop",NpcPolicy.choose(options,"gone",3).key());}
 @Test void invalidCurrentIsReevaluated(){assertNull(NpcPolicy.choose(List.of(new NpcPolicy.Candidate("old",100,false)),"old",0));}
 @Test void failureBackoffIsBounded(){assertEquals(500,NpcPolicy.retryDelay(0));assertTrue(NpcPolicy.retryDelay(4)>NpcPolicy.retryDelay(1));assertEquals(10000,NpcPolicy.retryDelay(50));}
 @Test void dispatchNeverConsumesMinimumReserve(){assertEquals(0,NpcPolicy.dispatchCount(1,1,2));assertEquals(1,NpcPolicy.dispatchCount(3,1,1));assertEquals(2,NpcPolicy.dispatchCount(3,1,4));}
}
