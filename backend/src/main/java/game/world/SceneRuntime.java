package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** All mutations run under the WorldService monitor and its database transaction. */
@Service
public class SceneRuntime {
    public record Position(UUID characterId,UUID sceneId,int q,int r){public WorldMap.Hex hex(){return new WorldMap.Hex(q,r);}}
    private final JdbcTemplate db;private final SceneService scenes;private final CharacterService characters;
    private final JsonMapper json=JsonMapper.builder().build();
    @org.springframework.beans.factory.annotation.Autowired private SceneContentService content;
    SceneRuntime(JdbcTemplate db,SceneService scenes,CharacterService characters){this.db=db;this.scenes=scenes;this.characters=characters;}
    public SceneMap map(UUID id){return db.queryForList("select document from scene_documents where id=?",String.class,id).stream().map(d->json.readValue(d,SceneMap.class)).findFirst().orElseThrow(()->AccountService.bad("场景已不存在"));}
    public Position position(UUID actor){return db.query("select character_id,scene_id,q,r from scene_positions where character_id=? and active",(r,n)->new Position(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getInt(3),r.getInt(4)),actor).stream().findFirst().orElse(null);}
    public List<Position> residents(UUID scene){return db.query("select character_id,scene_id,q,r from scene_positions where scene_id=? and active order by character_id",(r,n)->new Position(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getInt(3),r.getInt(4)),scene);}
    public Set<UUID> activeActors(){return new HashSet<>(db.queryForList("select character_id from scene_positions where active",UUID.class));}
    public Set<WorldMap.Hex> occupied(UUID scene,UUID except){var out=new HashSet<WorldMap.Hex>();for(var p:residents(scene))if(!p.characterId().equals(except)){var c=characters.get(p.characterId());if(c.alive()||c.life().equals("down"))out.add(p.hex());}return out;}
    public SceneMap outdoor(WorldMap world,WorldMap.Hex owner){
        var id=UUID.nameUUIDFromBytes((world.version()+":outdoor:"+owner.q()+":"+owner.r()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var saved=db.queryForList("select document from scene_documents where id=? and world_version=?",String.class,id,world.version());
        if(!saved.isEmpty())return content.ensure(json.readValue(saved.getFirst(),SceneMap.class),world);
        var tile=world.index().get(owner);if(tile==null||!tile.walkable())throw AccountService.bad("此地不能进入");
        var base=SceneMap.outdoor(world,owner,15);var cells=new ArrayList<SceneMap.Cell>();
        for(var cell:base.cells()){
            int variation=Math.floorMod(Objects.hash(world.seed(),owner,cell.hex()),13);
            String terrain=switch(tile.terrain()){case "beach"->"sand";case "mountain","hill"->"rock";case "bridge"->"paving";default->variation<2?"dirt":"grass";};
            cells.add(new SceneMap.Cell(cell.q(),cell.r(),terrain,true));
        }
        var result=new SceneMap(id,world.version(),2,2,owner,base.name(),15,cells);scenes.save(result);return content.ensure(result,world);
    }
    public void arrived(UUID actor,String version,WorldMap.Hex from,WorldMap.Hex to){
        if(WorldMap.distance(from,to)!=1){db.update("delete from world_arrivals where character_id=?",actor);return;}
        db.update("insert into world_arrivals values(?,?,?,?,?,?) on conflict(character_id) do update set world_version=excluded.world_version,from_q=excluded.from_q,from_r=excluded.from_r,to_q=excluded.to_q,to_r=excluded.to_r",actor,version,from.q(),from.r(),to.q(),to.r());
    }
    public Integer incoming(UUID actor,SceneMap scene){
        var rows=db.queryForList("select from_q,from_r from world_arrivals where character_id=? and world_version=? and to_q=? and to_r=?",actor,scene.worldVersion(),scene.owner().q(),scene.owner().r());
        if(rows.isEmpty())return null;var row=rows.getFirst();int d=BattleService.DIRECTIONS.indexOf(new WorldMap.Hex(((Number)row.get("from_q")).intValue()-scene.owner().q(),((Number)row.get("from_r")).intValue()-scene.owner().r()));return d<0?null:d;
    }
    public Position enter(UUID actor,SceneMap scene,boolean edgeOnly,Integer direction){
        if(!scene.worldVersion().equals(scenes.version())||!scene.owner().equals(characters.get(actor).hex()))throw AccountService.bad("场景不属于当前世界位置");
        var old=position(actor);if(old!=null){if(old.sceneId().equals(scene.id()))return old;throw AccountService.bad("请先离开当前场景");}
        var occupied=occupied(scene.id(),actor);WorldMap.Hex cell=null;
        if(!edgeOnly){var remembered=db.query("select q,r from scene_positions where character_id=? and scene_id=?",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),actor,scene.id());if(!remembered.isEmpty()){cell=nearest(scene,remembered.getFirst(),occupied);if(cell==null)throw AccountService.bad("原位置附近没有合法空位");}}
        if(cell==null)cell=edgeCell(scene,direction,occupied);
        if(cell==null)throw AccountService.bad("对应边缘没有合法空位，请稍后再试");
        put(actor,scene.id(),cell);return position(actor);
    }
    public void put(UUID actor,UUID scene,WorldMap.Hex cell){
        db.update("update scene_positions set active=false where character_id=? and active",actor);
        db.update("insert into scene_positions(character_id,scene_id,q,r,active) values(?,?,?,?,true) on conflict(character_id,scene_id) do update set q=excluded.q,r=excluded.r,active=true",actor,scene,cell.q(),cell.r());
    }
    public void leave(UUID actor){db.update("update scene_positions set active=false where character_id=? and active",actor);}
    public void requireWorld(UUID actor){if(position(actor)!=null)throw AccountService.bad("请先返回一级地图再操作");}
    public boolean samePlace(UUID a,UUID b){var x=position(a);var y=position(b);return x==null?y==null:y!=null&&x.sceneId().equals(y.sceneId())&&x.hex().equals(y.hex());}
    public List<WorldMap.Hex> path(UUID actor,WorldMap.Hex target){var p=position(actor);if(p==null)throw AccountService.bad("你不在二级地图中");return map(p.sceneId()).topology().path(p.hex(),target,occupied(p.sceneId(),actor));}
    public boolean step(UUID actor,UUID expectedScene,WorldMap.Hex next){
        var p=position(actor);if(p==null||!p.sceneId().equals(expectedScene)||WorldMap.distance(p.hex(),next)!=1)return false;
        var map=map(p.sceneId());if(!map.topology().connected(p.hex(),next,occupied(p.sceneId(),actor)))return false;
        if(map.cells().stream().noneMatch(c->c.hex().equals(next)&&c.walkable()))return false;
        put(actor,p.sceneId(),next);return true;
    }
    public void reconcile(WorldMap world){
        for(UUID actor:activeActors()){var c=characters.get(actor);var p=position(actor);var map=map(p.sceneId());
            if(!map.worldVersion().equals(world.version())||!map.owner().equals(c.hex())||(!c.alive()&&!c.life().equals("down")))leave(actor);
        }
    }
    public static List<Integer> sides(WorldMap.Hex h,int radius){
        if(WorldMap.distance(new WorldMap.Hex(0,0),h)>radius)return List.of();
        int[] v={h.q(),h.q()+h.r(),h.r(),-h.q(),-h.q()-h.r(),-h.r()};var out=new ArrayList<Integer>();for(int i=0;i<6;i++)if(v[i]==radius)out.add(i);return out;
    }
    public static WorldMap.Hex edgeCell(SceneMap map,Integer direction,Set<WorldMap.Hex> occupied){
        var cells=new ArrayList<>(map.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->!occupied.contains(h)&&!sides(h,map.radius()).isEmpty()&&(direction==null||sides(h,map.radius()).contains(direction))).toList());
        Collections.shuffle(cells);return cells.isEmpty()?null:cells.getFirst();
    }
    public static WorldMap.Hex nearest(SceneMap map,WorldMap.Hex from,Set<WorldMap.Hex> occupied){
        var walkable=new HashSet<>(map.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).toList());
        var queue=new ArrayDeque<WorldMap.Hex>();var seen=new HashSet<WorldMap.Hex>();queue.add(from);seen.add(from);
        while(!queue.isEmpty()){var h=queue.remove();if(walkable.contains(h)&&!occupied.contains(h))return h;
            for(var n:h.neighbors())if(walkable.contains(n)&&seen.add(n))queue.add(n);
        }return null;
    }
}
