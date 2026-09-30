package game.world;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static game.world.WorldMap.Hex;
class WeaponRulesTest {
 @Test void flailHasBlindFirstRing(){var w=new WeaponRules.Weapon("flail","","single",2,2,2,5);var origin=new Hex(0,0);assertTrue(WeaponRules.cells(w,origin,new Hex(1,0)).isEmpty());int count=0;for(int q=-2;q<=2;q++)for(int r=-2;r<=2;r++)if(!WeaponRules.cells(w,origin,new Hex(q,r)).isEmpty())count++;assertEquals(12,count);}
 @Test void axeCoversSixDistinctConnectedFans(){var w=new WeaponRules.Weapon("axe","","fan",1,1,2,3);Set<Set<Hex>> shapes=new HashSet<>();for(var d:WeaponRules.DIRECTIONS){var cells=WeaponRules.cells(w,new Hex(0,0),d);assertEquals(2,cells.size());assertEquals(1,WeaponRules.distance(cells.get(0),cells.get(1)));shapes.add(Set.copyOf(cells));}assertEquals(6,shapes.size());}
 @Test void spearAcceptsBothCellsButRejectsDiagonalSecondRing(){var w=new WeaponRules.Weapon("spear","","line",1,2,2,3);assertEquals(List.of(new Hex(1,0),new Hex(2,0)),WeaponRules.cells(w,new Hex(0,0),new Hex(2,0)));assertTrue(WeaponRules.cells(w,new Hex(0,0),new Hex(1,1)).isEmpty());}
 @Test void brainDoesNotTakeNegativeTrade(){assertNull(MonsterBrain.choose(List.of(new MonsterBrain.Option("move",new Hex(0,0),-1))));assertEquals("attack",MonsterBrain.choose(List.of(new MonsterBrain.Option("move",new Hex(0,0),1),new MonsterBrain.Option("attack",new Hex(1,0),8))).action());}
}
