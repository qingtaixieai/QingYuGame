package game.world;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Authored outdoor layout for the White Stone sample in a newly generated scene. */
final class WhiteStoneLayout {
    static final WorldMap.Hex OWNER=new WorldMap.Hex(7,-5);
    private static final WorldMap.Hex PLAZA=new WorldMap.Hex(0,0);
    private static final int GENERATION=4;
    private static final Set<WorldMap.Hex> PLAZA_FIXTURES=Set.of(PLAZA,new WorldMap.Hex(-3,3));
    private static final Map<String,WorldMap.Hex> CENTRES=Map.of(
        "shop",new WorldMap.Hex(-5,4),"workshop",new WorldMap.Hex(0,-5),
        "smith",new WorldMap.Hex(-5,0),"inn",new WorldMap.Hex(5,-3)
    );

    private WhiteStoneLayout() {}

    static boolean matches(WorldMap.Tile tile){
        return OWNER.equals(tile.hex())&&tile.place()!=null&&"town".equals(tile.place().type())&&"白石城".equals(tile.place().name());
    }

    static boolean applies(SceneMap scene){
        return scene.level()==2&&scene.generationVersion()==GENERATION&&OWNER.equals(scene.owner());
    }

    static WorldMap.Hex centre(String kind){return CENTRES.get(kind);}

    static WorldMap.Hex door(String kind){
        var centre=centre(kind);
        return new WorldMap.Hex(centre.q(),centre.r()+1);
    }

    static WorldMap.Hex southEntry(SceneMap scene){return new WorldMap.Hex(0,scene.radius());}

    static WorldMap.Hex plazaAccess(){return new WorldMap.Hex(0,1);}

    static SceneMap generate(WorldMap world,int radius){
        var base=SceneMap.outdoor(world,OWNER,radius);
        var plots=new HashSet<WorldMap.Hex>();
        var doors=new HashSet<WorldMap.Hex>();
        for(var kind:CENTRES.keySet()){
            var centre=centre(kind);
            plots.add(centre);
            plots.addAll(centre.neighbors());
            doors.add(door(kind));
        }
        plots.removeAll(doors);
        var entrances=List.of(
            new SceneMap.Edge(new WorldMap.Hex(1,6),new WorldMap.Hex(2,6)),
            new SceneMap.Edge(new WorldMap.Hex(1,7),new WorldMap.Hex(2,7)),
            new SceneMap.Edge(new WorldMap.Hex(-2,7),new WorldMap.Hex(-1,7))
        );
        var cells=base.cells().stream().map(cell->{
            var h=cell.hex();
            if(plots.contains(h))return new SceneMap.Cell(h.q(),h.r(),"wall",false);
            if(PLAZA_FIXTURES.contains(h))return new SceneMap.Cell(h.q(),h.r(),"furniture",false);
            String terrain=doors.contains(h)?"street":terrain(h);
            return new SceneMap.Cell(h.q(),h.r(),terrain,true);
        }).toList();
        return new SceneMap(base.id(),base.worldVersion(),GENERATION,2,OWNER,base.name(),radius,cells,entrances);
    }

    private static String terrain(WorldMap.Hex h){
        int q=h.q(),r=h.r(),distance=WorldMap.distance(PLAZA,h);
        if(distance<=3)return "plaza";
        if(q==0||r==0||q+r==0)return "street";
        if((q==1||q==-1)&&r>0&&r<=10)return "street";
        if((q>=1&&q<=5&&r==-2)||(q>=-5&&q<0&&(r==1||q+r==0)))return "street";
        if(distance>=9&&Math.floorMod(q*19+r*13,7)<2)return "forest";
        return "grass";
    }
}
