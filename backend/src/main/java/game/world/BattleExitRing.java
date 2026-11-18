package game.world;

import java.util.*;

/** Existing map boundary cells tagged with their available escape directions. */
final class BattleExitRing {
    record Cell(WorldMap.Hex hex, List<Integer> directions, WorldMap.Hex anchor) {
        public boolean walkable(){return !directions.isEmpty();}
    }

    static List<Cell> cells(SceneMap scene,List<BattleService.Edge> edges){
        var walkable=new HashSet<>(scene.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).toList());
        if(scene.level()==2){
            var directions=new HashSet<>(edges.stream().filter(BattleService.Edge::walkable).map(BattleService.Edge::direction).toList());
            var result=new ArrayList<Cell>();
            for(var tile:scene.cells()){
                var hex=tile.hex();
                var sides=SceneRuntime.sides(hex,scene.radius());
                if(sides.isEmpty())continue;
                var available=tile.walkable()?sides.stream().filter(directions::contains).toList():List.<Integer>of();
                result.add(new Cell(hex,available,tile.walkable()?hex:null));
            }
            return List.copyOf(result);
        }
        Map<WorldMap.Hex,List<Integer>> doors=new LinkedHashMap<>();
        for(var edge:edges){
            if(edge.direction()<6||!walkable.contains(edge.cell()))continue;
            var directions=doors.computeIfAbsent(edge.cell(),key->new ArrayList<>());
            if(edge.walkable())directions.add(edge.direction());
        }
        return doors.entrySet().stream().map(e->new Cell(e.getKey(),List.copyOf(e.getValue()),e.getKey())).toList();
    }

    // Active encounters saved with actors or loot outside the map keep their old geometry until close.
    static List<Cell> legacyCells(SceneMap scene,List<BattleService.Edge> edges){
        int outer=scene.radius()+1;
        var result=new ArrayList<Cell>();
        var occupied=new HashSet<>(scene.cells().stream().map(SceneMap.Cell::hex).toList());
        var walkable=new HashSet<>(scene.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).toList());
        if(scene.level()==2){
            for(int q=-outer;q<=outer;q++)for(int r=-outer;r<=outer;r++){
                var hex=new WorldMap.Hex(q,r);
                var sides=SceneRuntime.sides(hex,outer);
                if(sides.isEmpty())continue;
                var directions=sides.stream().filter(d->edges.get(d).walkable()).toList();
                var anchor=hex.neighbors().stream().filter(walkable::contains).findFirst().orElse(null);
                result.add(new Cell(hex,anchor==null?List.of():directions,anchor));
            }
        }else{
            for(var door:edges){
                if(door.direction()<6||door.cell()==null||!walkable.contains(door.cell()))continue;
                var anchor=door.cell();
                var outside=anchor.neighbors().stream().filter(h->!occupied.contains(h))
                    .max(Comparator.comparingInt(h->WorldMap.distance(h,new WorldMap.Hex(0,0)))).orElse(null);
                if(outside!=null){
                    occupied.add(outside);
                    result.add(new Cell(outside,door.walkable()?List.of(door.direction()):List.of(),anchor));
                }
            }
        }
        return List.copyOf(result);
    }
    private BattleExitRing(){}
}
