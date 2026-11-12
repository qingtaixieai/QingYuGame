package game.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LogisticsPolicyTest {
 @Test void pickupClockCountsOnlyActualUnpausedWaiting(){assertEquals(1500,LogisticsPolicy.waitElapsed(500,1000,2000,true));assertEquals(500,LogisticsPolicy.waitElapsed(500,1000,2000,false));assertEquals(500,LogisticsPolicy.waitElapsed(500,2000,1000,true));}
 @Test void allocationCannotPromiseMoreThanStockOrRemainingNeed(){assertEquals(2,LogisticsPolicy.allocate(3,2));assertEquals(0,LogisticsPolicy.allocate(-1,3));assertEquals(0,LogisticsPolicy.allocate(3,0));}
 @Test void externalDispatchPreservesHomeMinimum(){assertFalse(LogisticsPolicy.canDispatch(false,false,3,1));assertFalse(LogisticsPolicy.canDispatch(true,true,1,1));assertTrue(LogisticsPolicy.canDispatch(true,true,2,1));assertTrue(LogisticsPolicy.canDispatch(false,false,1,1,false));}
}
