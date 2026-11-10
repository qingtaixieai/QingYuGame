package game.world;

import java.util.*;

/** Traversable terrain and shared boundaries; callers supply temporary occupancy. */
final class SceneTopology {
    private final Set<WorldMap.Hex> cells;
    private final Set<SceneMap.Edge> edges;
    SceneTopology(Collection<WorldMap.Hex> cells){this(cells,List.of());}
    SceneTopology(Collection<WorldMap.Hex> cells,Collection<SceneMap.Edge> edges){
        this.cells=Set.copyOf(cells);
        this.edges=Set.copyOf(edges);
    }
    private boolean valid(WorldMap.Hex h,Set<WorldMap.Hex> blocked){
        return cells.contains(h)&&!blocked.contains(h);
    }
    boolean canStep(WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> blocked){
        return WorldMap.distance(from,to)==1&&valid(from,blocked)&&valid(to,blocked)&&!edges.contains(new SceneMap.Edge(from,to));
    }
    boolean connected(WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> blocked){
        return valid(from,blocked)&&valid(to,blocked)&&(from.equals(to)||!path(from,to,blocked).isEmpty());
    }
    List<WorldMap.Hex> path(WorldMap.Hex from,WorldMap.Hex to,Set<WorldMap.Hex> blocked){
        if(!valid(from,blocked)||!valid(to,blocked)||from.equals(to))return List.of();
        return WorldMap.shortestPath(from,to,(a,b)->canStep(a,b,blocked)?1:0);
    }
}
