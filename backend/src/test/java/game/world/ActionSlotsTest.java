package game.world;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActionSlotsTest {
    private Map<String,String> owned(){var m=new LinkedHashMap<String,String>();m.put("move","common");m.put("equipment","common");m.put("attack:axe","weapon");m.put("bandage","item");return m;}
    @Test void defaultsFillOnlyTheirSourceAndReconcileIsIdempotent(){
        var owned=owned();var l=ActionSlots.reconcile(ActionSlots.empty(),owned);
        assertEquals("move",l.slots().get(0).actionRef());assertEquals("attack:axe",l.slots().get(6).actionRef());assertEquals("bandage",l.slots().get(12).actionRef());assertEquals(l,ActionSlots.reconcile(l,owned));
    }
    @Test void manualClearingAndReorderingSurviveRefresh(){
        var owned=owned();var l=ActionSlots.reconcile(ActionSlots.empty(),owned);
        l=ActionSlots.edit(l,"swap",0,4,null,owned);l=ActionSlots.edit(l,"clear",1,null,null,owned);
        assertEquals(l,ActionSlots.reconcile(l,owned));assertEquals("move",l.slots().get(4).actionRef());assertNull(l.slots().get(1).actionRef());
        l=ActionSlots.edit(l,"assign",2,null,"equipment",owned);assertEquals("equipment",l.slots().get(2).actionRef());
    }
    @Test void locksSurviveOwnershipLossAndNewActionsSkipLockedSlots(){
        var owned=owned();var l=ActionSlots.reconcile(ActionSlots.empty(),owned);l=ActionSlots.edit(l,"lock",12,null,null,owned);
        owned.remove("bandage");l=ActionSlots.reconcile(l,owned);assertNull(l.slots().get(12).actionRef());assertTrue(l.slots().get(12).locked());
        owned.put("bandage","item");l=ActionSlots.reconcile(l,owned);assertEquals("bandage",l.slots().get(13).actionRef());assertNull(l.slots().get(12).actionRef());
    }
    @Test void cannotEditLockedOrInvalidSlotsOrDuplicateOrCrossSource(){
        var owned=owned();var l=ActionSlots.reconcile(ActionSlots.empty(),owned);var locked=ActionSlots.edit(l,"lock",0,null,null,owned);
        assertThrows(RuntimeException.class,()->ActionSlots.edit(locked,"clear",0,null,null,owned));
        assertThrows(RuntimeException.class,()->ActionSlots.edit(locked,"swap",1,0,null,owned));
        assertThrows(RuntimeException.class,()->ActionSlots.edit(l,"swap",0,6,null,owned));
        assertThrows(RuntimeException.class,()->ActionSlots.edit(l,"assign",3,null,"move",owned));
        assertThrows(RuntimeException.class,()->ActionSlots.edit(l,"assign",3,null,"fabricated",owned));
        assertThrows(RuntimeException.class,()->ActionSlots.edit(l,"clear",-1,null,null,owned));
    }
    @Test void fullyLockedPageExpandsWithoutOverwriting(){
        var l=ActionSlots.empty();for(int i=12;i<18;i++)l=ActionSlots.edit(l,"lock",i,null,null,Map.of());
        var next=ActionSlots.reconcile(l,Map.of("bandage","item"));assertEquals(ActionSlots.SOURCES.size()*6+1,next.slots().size());assertEquals("bandage",next.slots().getLast().actionRef());assertEquals(6,next.slots().getLast().index());
    }
}
