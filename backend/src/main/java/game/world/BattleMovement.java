package game.world;

import java.util.*;

/** Deterministic shortest paths shared by range validation and multi-cell moves. */
final class BattleMovement {
    static List<WorldMap.Hex> path(int radius,WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> occupied){
        if(from.equals(to)||distance(to)>radius||occupied.contains(to))return List.of();
        Map<WorldMap.Hex,WorldMap.Hex> previous=new HashMap<>();
        ArrayDeque<WorldMap.Hex> queue=new ArrayDeque<>();queue.add(from);previous.put(from,from);
        while(!queue.isEmpty()){
            var h=queue.remove();
            for(var n:h.neighbors())if(distance(n)<=radius&&!occupied.contains(n)&&!previous.containsKey(n)){
                previous.put(n,h);
                if(n.equals(to)){
                    LinkedList<WorldMap.Hex> result=new LinkedList<>();
                    for(var step=to;!step.equals(from);step=previous.get(step))result.addFirst(step);
                    return result;
                }
                queue.add(n);
            }
        }
        return List.of();
    }
    private static int distance(WorldMap.Hex h){return Math.max(Math.max(Math.abs(h.q()),Math.abs(h.r())),Math.abs(h.q()+h.r()));}
}
