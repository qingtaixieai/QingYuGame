package game.world;

import java.util.*;
import static game.world.WorldMap.Hex;

/** Pure AI navigation. It suggests distances; only shared battle actions may execute movement. */
public final class NpcCombatNavigation {
    private NpcCombatNavigation() {}

    public static Map<Hex,Integer> distances(int radius,List<Hex> targets,Set<Hex> blocked,
                                             Set<Hex> friends,WeaponRules.Weapon weapon,boolean ranged) {
        var result=new HashMap<Hex,Integer>();
        var queue=new ArrayDeque<Hex>();
        if(ranged) {
            for(var target:targets) {result.put(target,0);queue.add(target);}
        } else {
            // Approach a stance from which the full attack shape is legal, not an occupied target.
            var candidates=new HashSet<Hex>();
            for(var target:targets)
                for(int dq=-weapon.maxRange();dq<=weapon.maxRange();dq++)
                    for(int dr=-weapon.maxRange();dr<=weapon.maxRange();dr++) {
                        var cell=new Hex(target.q()+dq,target.r()+dr);
                        if(within(cell,radius)&&!blocked.contains(cell))candidates.add(cell);
                    }
            for(var cell:candidates) {
                boolean legal=false;
                for(int dq=-weapon.maxRange();dq<=weapon.maxRange()&&!legal;dq++)
                    for(int dr=-weapon.maxRange();dr<=weapon.maxRange()&&!legal;dr++) {
                        var cells=WeaponRules.battlefieldCells(weapon,cell,new Hex(cell.q()+dq,cell.r()+dr),radius);
                        legal=cells.stream().noneMatch(friends::contains)&&targets.stream().anyMatch(cells::contains);
                    }
                if(legal) {result.put(cell,0);queue.add(cell);}
            }
        }
        while(!queue.isEmpty()) {
            var cell=queue.remove();
            for(var next:cell.neighbors())if(within(next,radius)&&!blocked.contains(next)&&!result.containsKey(next)) {
                result.put(next,result.get(cell)+1);queue.add(next);
            }
        }
        return result;
    }
    private static boolean within(Hex cell,int radius) {return WeaponRules.distance(new Hex(0,0),cell)<=radius;}
}
