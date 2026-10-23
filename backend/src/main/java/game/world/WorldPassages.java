package game.world;

import java.util.*;

/** Public, undirected boundaries. Any usable alternative keeps a boundary open. */
final class WorldPassages {
    record Connection(WorldMap.Hex from,WorldMap.Hex to,boolean open){
        Connection {if(WorldMap.distance(from,to)!=1)throw new IllegalArgumentException("Passage endpoints must be adjacent");}
    }
    record Edge(WorldMap.Hex from,WorldMap.Hex to) {
        Edge {if(from.q()>to.q()||from.q()==to.q()&&from.r()>to.r()){var swap=from;from=to;to=swap;}}
    }
    private Set<Edge> closed=Set.of();
    void replace(Collection<Connection> connections){
        var available=new HashMap<Edge,Boolean>();
        for(var c:connections)available.merge(new Edge(c.from(),c.to()),c.open(),Boolean::logicalOr);
        var next=new HashSet<Edge>();available.forEach((e,open)->{if(!open)next.add(e);});closed=Set.copyOf(next);
    }
    boolean open(WorldMap.Hex from,WorldMap.Hex to){return WorldMap.distance(from,to)==1&&!closed.contains(new Edge(from,to));}
    List<Edge> blocked(){return closed.stream().sorted(Comparator.comparingInt((Edge e)->e.from().q()).thenComparingInt(e->e.from().r()).thenComparingInt(e->e.to().q()).thenComparingInt(e->e.to().r())).toList();}
    static boolean endpointConnected(SceneMap scene,WorldMap.Hex endpoint,List<WorldMap.Hex> required){
        var topology=scene.topology();
        return topology.connected(endpoint,endpoint,Set.of())&&required.stream().allMatch(h->topology.connected(endpoint,h,Set.of()));
    }
}
