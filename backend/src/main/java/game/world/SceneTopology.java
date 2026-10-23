package game.world;

import java.util.*;

/** Immutable traversable terrain. Callers supply temporary blockers separately. */
final class SceneTopology {
    private final Set<WorldMap.Hex> cells;
    SceneTopology(Collection<WorldMap.Hex> cells){this.cells=Set.copyOf(cells);}

    boolean connected(WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> blocked){
        return valid(from,blocked)&&valid(to,blocked)
            &&(from.equals(to)||!path(from,to,blocked).isEmpty());
    }
    private boolean valid(WorldMap.Hex h,Set<WorldMap.Hex> blocked){return cells.contains(h)&&!blocked.contains(h);}

    List<WorldMap.Hex> path(WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> blocked){
        if(!valid(from,blocked)||!valid(to,blocked)||from.equals(to))return List.of();
        var previous=new HashMap<WorldMap.Hex,WorldMap.Hex>();
        var queue=new ArrayDeque<WorldMap.Hex>();
        previous.put(from,from);queue.add(from);
        while(!queue.isEmpty()){
            var cell=queue.remove();
            for(var next:cell.neighbors()){
                if(!valid(next,blocked)||previous.containsKey(next))continue;
                previous.put(next,cell);
                if(next.equals(to)){
                    var result=new LinkedList<WorldMap.Hex>();
                    for(var step=to;!step.equals(from);step=previous.get(step))result.addFirst(step);
                    return List.copyOf(result);
                }
                queue.add(next);
            }
        }
        return List.of();
    }
}
