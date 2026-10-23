package game.world;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Persist the complete document; the seed is not a replacement for saved terrain. */
public record SceneMap(UUID id,String worldVersion,int generationVersion,int level,
                       WorldMap.Hex owner,String name,int radius,List<Cell> cells) {
    public record Cell(int q,int r,String terrain,boolean walkable){
        public WorldMap.Hex hex(){return new WorldMap.Hex(q,r);}
    }
    public SceneMap {
        Objects.requireNonNull(id);Objects.requireNonNull(worldVersion);Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        if(level!=2&&level!=3)throw new IllegalArgumentException("Invalid scene level");
        if(radius<1||radius>100)throw new IllegalArgumentException("Invalid scene radius");
        cells=List.copyOf(cells);
        var seen=new HashSet<WorldMap.Hex>();
        for(var cell:cells)if(!seen.add(cell.hex())||WorldMap.distance(new WorldMap.Hex(0,0),cell.hex())>radius)
            throw new IllegalArgumentException("Invalid scene cell");
    }
    public static SceneMap outdoor(WorldMap world,WorldMap.Hex owner,int radius){
        var tile=world.index().get(owner);
        if(tile==null)throw new IllegalArgumentException("Unknown world tile");
        var id=UUID.nameUUIDFromBytes((world.version()+":outdoor:"+owner.q()+":"+owner.r()).getBytes(StandardCharsets.UTF_8));
        return new SceneMap(id,world.version(),1,2,owner,tile.place()==null?"室外":tile.place().name(),radius,
            hexagon(radius,tile.terrain(),tile.walkable()));
    }
    public static SceneMap room(UUID id,String version,WorldMap.Hex owner,String name,int radius){
        return new SceneMap(id,version,1,3,owner,name,radius,hexagon(radius,"floor",true));
    }
    private static List<Cell> hexagon(int radius,String terrain,boolean walkable){
        if(radius<1||radius>100)throw new IllegalArgumentException("Invalid scene radius");
        var cells=new ArrayList<Cell>();
        for(int r=-radius;r<=radius;r++)for(int q=-radius;q<=radius;q++)
            if(Math.abs(q+r)<=radius)cells.add(new Cell(q,r,terrain,walkable));
        return List.copyOf(cells);
    }
    SceneTopology topology(){return new SceneTopology(cells.stream().filter(Cell::walkable).map(Cell::hex).toList());}
}
