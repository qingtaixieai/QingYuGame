package game.world;

import java.util.*;

/** Deterministic shortest paths shared by range validation and multi-cell moves. */
final class BattleMovement {
    static List<WorldMap.Hex> path(int radius,WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> occupied){
        return path(radius,from,to,occupied,List.of());
    }

    static List<WorldMap.Hex> path(int radius,WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> occupied,Collection<SceneMap.Edge> blockedEdges){
        if(from.equals(to)||distance(to)>radius||occupied.contains(to))return List.of();
        var walls=Set.copyOf(blockedEdges);
        var previous=new HashMap<WorldMap.Hex,WorldMap.Hex>();
        var queue=new ArrayDeque<WorldMap.Hex>();
        previous.put(from,from);
        queue.add(from);
        while(!queue.isEmpty()){
            var cell=queue.remove();
            for(var next:cell.neighbors()){
                if(previous.containsKey(next)||!canStep(radius,cell,next,occupied,walls))continue;
                previous.put(next,cell);
                if(next.equals(to)){
                    var route=new LinkedList<WorldMap.Hex>();
                    for(var step=to;!step.equals(from);step=previous.get(step))route.addFirst(step);
                    return List.copyOf(route);
                }
                queue.add(next);
            }
        }
        return List.of();
    }
    static boolean canStep(int radius,WorldMap.Hex from,WorldMap.Hex next,Set<WorldMap.Hex> occupied,Set<SceneMap.Edge> walls){
        return distance(next)<=radius&&!occupied.contains(next)&&!walls.contains(new SceneMap.Edge(from,next));
    }
    private static int distance(WorldMap.Hex h){return WorldMap.distance(new WorldMap.Hex(0,0),h);}
}
