package game.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static game.world.WorldMap.Hex;

class AimRulesTest {
    private final WeaponRules.Weapon bow=new WeaponRules.Weapon("short_bow","短弓","single",2,4,2,3);
    @Test void centreDeterminesRangeAndPeripheralCanReachFirstAndFifthRing(){
        var origin=new Hex(0,0);
        assertTrue(WeaponRules.aimCells(bow,origin,new Hex(1,0),6).isEmpty());
        assertTrue(WeaponRules.aimCells(bow,origin,new Hex(5,0),6).isEmpty());
        assertTrue(WeaponRules.aimCells(bow,origin,new Hex(2,0),6).contains(new Hex(1,0)));
        assertTrue(WeaponRules.aimCells(bow,origin,new Hex(4,0),6).contains(new Hex(5,0)));
    }
    @Test void boundaryClipsPeripheryInsteadOfRejectingLegalCentre(){
        var cells=WeaponRules.aimCells(bow,new Hex(2,0),new Hex(6,0),6);
        assertFalse(cells.isEmpty());assertTrue(cells.size()<7);
        assertTrue(cells.stream().allMatch(h->WeaponRules.distance(h,new Hex(0,0))<=6));
        assertTrue(WeaponRules.aimCells(bow,new Hex(4,0),new Hex(7,0),6).isEmpty());
    }
    @Test void upgradingLayoutPreservesLockedSlotsAndAddsSkillPage(){
        var old=new ActionSlots.Layout(java.util.List.of(new ActionSlots.Slot("common",0,"move",true)),java.util.List.of("move"));
        var next=ActionSlots.reconcile(old,java.util.Map.of("move","common","skill:interrupt","skill"));
        assertEquals(old.slots().getFirst(),next.slots().getFirst());
        assertEquals(6,next.slots().stream().filter(s->s.source().equals("skill")).count());
        assertEquals(1,next.slots().stream().filter(s->"skill:interrupt".equals(s.actionRef())).count());
        assertEquals(next,ActionSlots.reconcile(next,java.util.Map.of("move","common","skill:interrupt","skill")));
    }
}
