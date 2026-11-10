package game.world;

import java.util.*;

/** Generates new outdoor documents. Saved maps remain authoritative. */
final class SceneLandscape {
    private SceneLandscape() {}

    static SceneMap generate(WorldMap world, WorldMap.Hex owner, int radius) {
        var base = SceneMap.outdoor(world, owner, radius);
        var tile = world.index().get(owner);
        var origin = new WorldMap.Hex(0, 0);
        var random = new SplittableRandom(world.seed() ^ (owner.q()*73856093L) ^ (owner.r()*19349663L));
        boolean town = tile.place()!=null && Set.of("town","village").contains(tile.place().type());
        boolean wet = Set.of("beach","bridge").contains(tile.terrain());
        double phase = random.nextDouble(2*Math.PI);
        Set<WorldMap.Hex> roads = new HashSet<>();
        if(town || tile.road() || tile.terrain().equals("bridge")){
            var index = world.index();
            for(var direction:origin.neighbors()){
                var neighbor = index.get(new WorldMap.Hex(owner.q()+direction.q(),owner.r()+direction.r()));
                if(neighbor==null || !neighbor.walkable() || !town && !neighbor.road())continue;
                for(int distance=0;distance<=radius;distance++)
                    roads.add(new WorldMap.Hex(direction.q()*distance,direction.r()*distance));
            }
        }
        var cells = base.cells().stream().map(cell -> {
            var h=cell.hex();
            double x=h.q()+h.r()*.5, y=h.r()*.866;
            double relief=Math.sin(x*.24+phase)+.7*Math.cos(y*.31-phase)+.45*Math.sin((x+y)*.38);
            String terrain;
            if(roads.contains(h))terrain="paving";
            else if(wet)terrain=relief>1.14?"shallow":"sand";
            else if(tile.terrain().equals("forest"))terrain=relief>-.15?"forest":"grass";
            else if(Set.of("hill","mountain").contains(tile.terrain()))terrain=relief>.15?"rock":"grass";
            else terrain=relief>.85?"dirt":relief<-.9&&!town?"forest":"grass";
            boolean barrier=!town&&!roads.contains(h)&&WorldMap.distance(origin,h)<radius-1
                &&(terrain.equals("rock")&&relief>1.48||terrain.equals("shallow")&&relief>1.68);
            return new SceneMap.Cell(h.q(),h.r(),barrier&&wet?"deep":terrain,!barrier);
        }).toList();

        var edges = new ArrayList<SceneMap.Edge>();
        // A short fence beside the village approach, with room to go around both ends.
        if(town && radius>=6){
            for(int r=3;r<=5;r++){
                var a=new WorldMap.Hex(2,r);var b=new WorldMap.Hex(3,r);
                if(WorldMap.distance(origin,b)<radius&&!roads.contains(a)&&!roads.contains(b))
                    edges.add(new SceneMap.Edge(a,b));
            }
        }
        var candidate=new SceneMap(base.id(),world.version(),3,2,owner,base.name(),radius,cells,edges);
        var topology=candidate.topology();
        for(var direction:origin.neighbors()){
            var exit=new WorldMap.Hex(direction.q()*radius,direction.r()*radius);
            if(!topology.connected(origin,exit,Set.of()))
                return new SceneMap(base.id(),world.version(),3,2,owner,base.name(),radius,
                    cells.stream().map(c->new SceneMap.Cell(c.q(),c.r(),c.terrain().equals("deep")?"shallow":c.terrain(),true)).toList());
        }
        return candidate;
    }
}
