package game.world;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Persistent content placement. Existing cells are never regenerated. */
@Service
public class SceneContentService {
 public record ObjectView(UUID id,String kind,String code,String name,int q,int r,boolean depleted,boolean inUse) {}
 private final JdbcTemplate db;private final SceneService scenes;
 SceneContentService(JdbcTemplate db,SceneService scenes){this.db=db;this.scenes=scenes;}
 public SceneMap ensure(SceneMap map,WorldMap world){
  if(map.level()!=2||db.queryForObject("select exists(select 1 from production_scene_content where scene_id=?)",Boolean.class,map.id()))return map;
  var tile=world.index().get(map.owner());if(tile==null)return map;
  var taken=new HashSet<WorldMap.Hex>(db.query("select q,r from scene_positions where scene_id=? and active",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),map.id()));
  int radius=map.radius();var available=new ArrayList<>(map.cells().stream().filter(c->c.walkable()&&WorldMap.distance(c.hex(),new WorldMap.Hex(0,0))<radius-1&&!taken.contains(c.hex())).toList());
  UUID sceneId=map.id();available.sort(Comparator.comparingInt(c->Objects.hash(sceneId,c.q(),c.r())));
  var blocked=new HashSet<WorldMap.Hex>();
  if(tile.place()!=null&&Set.of("town","village").contains(tile.place().type())){
   for(String code:List.of("workbench","furnace","anvil")){var cell=take(available);if(cell==null)break;insert(map,cell,"facility",code,switch(code){case "workbench"->"工坊工作台";case "furnace"->"铁匠铺熔炉";default->"铁匠铺铁砧";},code.equals("anvil")?1:2,0,0,null);blocked.add(cell.hex());}
  }
  List<String> kinds=switch(tile.terrain()){case "forest"->List.of("wood","wood","wood","fiber","stone");case "mountain","hill"->List.of("iron_ore","iron_ore","stone","stone","fiber");default->List.of("fiber","fiber","stone","stone","wood");};
  for(String code:kinds){if(code.equals("stone")&&!tile.terrain().equals("plain"))continue;var cell=take(available);if(cell==null)break;insert(map,cell,"resource",code,switch(code){case "wood"->"树木";case "iron_ore"->"铁矿脉";case "fiber"->"纤维植物";default->"小石头";},1,switch(code){case "wood"->5000;case "iron_ore"->8000;case "fiber"->3000;default->0;},switch(code){case "wood"->120000;case "iron_ore"->180000;case "fiber"->60000;default->30000;},switch(code){case "wood"->"axe";case "iron_ore"->"pickaxe";default->null;});}
  if(!blocked.isEmpty()){map=new SceneMap(map.id(),map.worldVersion(),map.generationVersion(),map.level(),map.owner(),map.name(),map.radius(),map.cells().stream().map(c->blocked.contains(c.hex())?new SceneMap.Cell(c.q(),c.r(),c.terrain(),false):c).toList());scenes.save(map);}
  db.update("insert into production_scene_content values(?,1)",map.id());return map;
 }
 private SceneMap.Cell take(List<SceneMap.Cell> cells){return cells.isEmpty()?null:cells.removeFirst();}
 private void insert(SceneMap map,SceneMap.Cell h,String kind,String code,String name,int capacity,int duration,int respawn,String tool){UUID id=UUID.nameUUIDFromBytes((map.id()+":"+code+":"+h.q()+":"+h.r()).getBytes(StandardCharsets.UTF_8));db.update("insert into scene_objects(id,scene_id,kind,code,name,q,r,capacity,duration_ms,respawn_ms,tool) values(?,?,?,?,?,?,?,?,?,?,?)",id,map.id(),kind,code,name,h.q(),h.r(),capacity,duration,respawn,tool);}
 public List<ObjectView> objects(UUID scene,long now){return db.query("select o.*,exists(select 1 from production_tasks t where t.object_id=o.id) as in_use from scene_objects o where scene_id=? order by q,r",(r,n)->new ObjectView(r.getObject("id",UUID.class),r.getString("kind"),r.getString("code"),r.getString("name"),r.getInt("q"),r.getInt("r"),r.getLong("ready_at")>now,r.getBoolean("in_use")),scene);}
 public boolean refreshStones(long now){
  boolean changed=false;var json=tools.jackson.databind.json.JsonMapper.builder().build();
  for(var row:db.queryForList("select o.id,o.scene_id,d.document from scene_objects o join scene_documents d on d.id=o.scene_id where o.code='stone' and o.ready_at>0 and o.ready_at<=?",now)){
   var map=json.readValue((String)row.get("document"),SceneMap.class);var occupied=new HashSet<>(db.query("select q,r from scene_objects where scene_id=? and id<>?",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),row.get("scene_id"),row.get("id")));
   var cells=new ArrayList<>(map.cells().stream().filter(c->c.walkable()&&Set.of("grass","dirt").contains(c.terrain())&&!occupied.contains(c.hex())).toList());
   if(!cells.isEmpty()){Collections.shuffle(cells);var h=cells.getFirst();db.update("update scene_objects set q=?,r=?,ready_at=0 where id=?",h.q(),h.r(),row.get("id"));changed=true;}
  }return changed;
 }
}
