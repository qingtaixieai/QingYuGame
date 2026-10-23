package game.world;

import java.util.*;

/** Deterministic shortest paths shared by range validation and multi-cell moves. */
final class BattleMovement {
    static List<WorldMap.Hex> path(int radius,WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> occupied){
        if(from.equals(to)||distance(to)>radius||occupied.contains(to))return List.of();
        var cells=new ArrayList<WorldMap.Hex>();
        for(int r=-radius;r<=radius;r++)for(int q=-radius;q<=radius;q++){
            var cell=new WorldMap.Hex(q,r);if(distance(cell)<=radius)cells.add(cell);
        }
        var blockers=new HashSet<>(occupied);blockers.remove(from);
        return new SceneTopology(cells).path(from,to,blockers);
    }
    private static int distance(WorldMap.Hex h){return Math.max(Math.max(Math.abs(h.q()),Math.abs(h.r())),Math.abs(h.q()+h.r()));}
}
