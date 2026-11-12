package game.world;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static game.world.WorldMap.Hex;

class NpcCombatNavigationTest {
    @Test void meleeApproachesLegalStanceInsteadOfOccupiedTarget() {
        var target=new Hex(2,0);var wall=new Hex(1,0);
        var distances=NpcCombatNavigation.distances(4,List.of(target),Set.of(target,wall),Set.of(),WeaponRules.UNARMED,false);
        assertFalse(distances.containsKey(target));assertFalse(distances.containsKey(wall));
        assertEquals(0,distances.get(new Hex(2,-1)));
        assertEquals(1,distances.get(new Hex(0,1)));
    }
    @Test void spearDoesNotAcceptClippedOrFriendlyFireStances() {
        var spear=new WeaponRules.Weapon("spear","","line",1,2,2,3);
        var target=new Hex(2,0);var friend=new Hex(1,0);
        var distances=NpcCombatNavigation.distances(2,List.of(target),Set.of(target,friend),Set.of(friend),spear,false);
        assertNotEquals(0,distances.get(new Hex(0,0)));
        assertEquals(0,distances.get(new Hex(2,-2)));
        assertFalse(distances.containsKey(new Hex(3,0)));
    }
    @Test void rangedDistancesKeepTargetSeedAndAvoidBlockedCells() {
        var target=new Hex(2,0);var wall=new Hex(1,0);
        var distances=NpcCombatNavigation.distances(3,List.of(target),Set.of(target,wall),Set.of(),WeaponRules.UNARMED,true);
        assertEquals(0,distances.get(target));assertFalse(distances.containsKey(wall));
        assertEquals(3,distances.get(new Hex(0,0)));
    }
    @Test void spearRejectsPartialShapeAtBoundaryWithoutAnyFriends() {
        var spear=new WeaponRules.Weapon("spear","","line",1,2,2,3);
        var target=new Hex(2,0);
        var distances=NpcCombatNavigation.distances(2,List.of(target),Set.of(target),Set.of(),spear,false);
        assertNotEquals(0,distances.get(new Hex(1,0)));
        assertEquals(0,distances.get(new Hex(0,0)));
    }
    @Test void noTargetsProducesNoRoute() {
        assertTrue(NpcCombatNavigation.distances(3,List.of(),Set.of(),Set.of(),WeaponRules.UNARMED,false).isEmpty());
    }
}
