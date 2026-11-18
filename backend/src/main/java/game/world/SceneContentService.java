package game.world;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Persistent content placement. Existing cells are never regenerated. */
@Service
public class SceneContentService {
 private static final int CONTENT_VERSION=2;
 private static final WorldMap.Hex CENTER=new WorldMap.Hex(0,0);
 private static final Set<String> CLEAR_TERRAIN=Set.of("paving","street","plaza","bridge","deep","shallow","water");
 public record ObjectView(UUID id,String kind,String code,String name,int q,int r,boolean depleted,boolean inUse) {}
 private final JdbcTemplate db;private final SceneService scenes;
 SceneContentService(JdbcTemplate db,SceneService scenes){this.db=db;this.scenes=scenes;}
 public SceneMap ensure(SceneMap map,WorldMap world){
  if(map.level()!=2)return map;
  int version=db.queryForList("select version from production_scene_content where scene_id=?",Integer.class,map.id()).stream().findFirst().orElse(0);
  if(version>=CONTENT_VERSION)return map;
  var tile=world.index().get(map.owner());if(tile==null)return map;
  boolean town=tile.place()!=null&&Set.of("town","village").contains(tile.place().type());
  UUID sceneId=map.id();
  var existing=db.query("select q,r from scene_objects where scene_id=? and kind='resource'",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),sceneId);
  var taken=new HashSet<WorldMap.Hex>(db.query("select q,r from scene_positions where scene_id=? and active union select q,r from scene_objects where scene_id=?",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),sceneId,sceneId));
  int radius=map.radius();
  var topology=map.topology();
  boolean authored=WhiteStoneLayout.applies(map);
  var access=authored?WhiteStoneLayout.plazaAccess():CENTER;
  var blocked=new HashSet<WorldMap.Hex>();
  if(town&&version==0){
   var facilities=new HashSet<>(db.queryForList("select code from scene_objects where scene_id=? and kind='facility'",String.class,sceneId));
   var sites=new ArrayList<>(map.cells().stream().filter(c->c.walkable()&&!taken.contains(c.hex())&&WorldMap.distance(c.hex(),CENTER)<radius-1)
       .filter(c->!authored||!CLEAR_TERRAIN.contains(c.terrain()))
       .sorted(Comparator.comparingInt(c->Objects.hash(sceneId,c.q(),c.r()))).toList());
   // TownService moves these existing facility seeds into the appropriate rooms.
   for(String code:List.of("workbench","furnace","anvil")){
    if(facilities.contains(code))continue;
    var cell=take(sites);if(cell==null)break;
    insert(map,cell,"facility",code,switch(code){case "workbench"->"工坊工作台";case "furnace"->"铁匠铺熔炉";default->"铁匠铺铁砧";},code.equals("anvil")?1:2,0,0,null);
    blocked.add(cell.hex());
   }
  }
  var available=new ArrayList<>(map.cells().stream()
      .filter(c->c.walkable()&&!CLEAR_TERRAIN.contains(c.terrain()))
      .filter(c->!taken.contains(c.hex())&&!blocked.contains(c.hex())&&WorldMap.distance(c.hex(),CENTER)<radius-1)
      .filter(c->!town||WorldMap.distance(c.hex(),CENTER)>=5)
      .sorted(Comparator.comparingInt(SceneMap.Cell::q).thenComparingInt(SceneMap.Cell::r)).toList());
  Collections.shuffle(available,new Random(world.seed()^sceneId.getMostSignificantBits()^sceneId.getLeastSignificantBits()^version));
  List<String> kinds=version==0?switch(tile.terrain()){
   case "forest"->List.of("wood","wood","wood","fiber");
   case "mountain","hill"->List.of("iron_ore","iron_ore","fiber");
   case "plain"->List.of("fiber","fiber","stone","stone","wood");
   default->List.of("fiber","fiber","wood");
  }:town?List.of():switch(tile.terrain()){
   case "forest"->List.of("wood","fiber");
   case "mountain","hill"->List.of("iron_ore");
   case "plain"->List.of("fiber","stone");
   default->List.of("fiber");
  };
  var placed=new ArrayList<>(existing);
  for(String code:kinds){
   var cell=available.stream().filter(c->placed.stream().allMatch(p->WorldMap.distance(p,c.hex())>=3))
       .sorted(Comparator.comparingInt(c->terrainFit(code,c.terrain())))
       .filter(c->topology.connected(access,c.hex(),blocked)).findFirst().orElse(null);
   if(cell==null)continue;
   available.remove(cell);placed.add(cell.hex());
   insert(map,cell,"resource",code,switch(code){case "wood"->"树木";case "iron_ore"->"铁矿脉";case "fiber"->"纤维植物";default->"小石头";},1,switch(code){case "wood"->5000;case "iron_ore"->8000;case "fiber"->3000;default->0;},switch(code){case "wood"->120000;case "iron_ore"->180000;case "fiber"->60000;default->30000;},switch(code){case "wood"->"axe";case "iron_ore"->"pickaxe";default->null;});
  }
  if(!blocked.isEmpty()){map=map.withCells(map.cells().stream().map(c->blocked.contains(c.hex())?new SceneMap.Cell(c.q(),c.r(),c.terrain(),false):c).toList());scenes.save(map);}
  db.update("insert into production_scene_content values(?,?) on conflict(scene_id) do update set version=excluded.version",sceneId,CONTENT_VERSION);return map;
 }
 private int terrainFit(String code,String terrain){
  return switch(code){
   case "wood"->terrain.equals("forest")?0:1;
   case "iron_ore"->terrain.equals("rock")?0:1;
   case "stone"->Set.of("grass","dirt").contains(terrain)?0:1;
   default->Set.of("grass","forest","sand").contains(terrain)?0:1;
  };
 }
 private SceneMap.Cell take(List<SceneMap.Cell> cells){return cells.isEmpty()?null:cells.removeFirst();}
 private void insert(SceneMap map,SceneMap.Cell h,String kind,String code,String name,int capacity,int duration,int respawn,String tool){UUID id=UUID.nameUUIDFromBytes((map.id()+":"+code+":"+h.q()+":"+h.r()).getBytes(StandardCharsets.UTF_8));db.update("insert into scene_objects(id,scene_id,kind,code,name,q,r,capacity,duration_ms,respawn_ms,tool) values(?,?,?,?,?,?,?,?,?,?,?)",id,map.id(),kind,code,name,h.q(),h.r(),capacity,duration,respawn,tool);}
 public List<ObjectView> objects(UUID scene,long now){return db.query("select o.*,exists(select 1 from production_tasks t where t.object_id=o.id) as in_use from scene_objects o where scene_id=? order by q,r,id",(r,n)->new ObjectView(r.getObject("id",UUID.class),r.getString("kind"),r.getString("code"),r.getString("name"),r.getInt("q"),r.getInt("r"),r.getLong("ready_at")>now,r.getBoolean("in_use")),scene);}
 public boolean refreshStones(long now){
  boolean changed=false;var json=tools.jackson.databind.json.JsonMapper.builder().build();
  for(var row:db.queryForList("select o.id,o.scene_id,d.document from scene_objects o join scene_documents d on d.id=o.scene_id where o.code='stone' and o.ready_at>0 and o.ready_at<=?",now)){
   var map=json.readValue((String)row.get("document"),SceneMap.class);var occupied=new HashSet<>(db.query("select q,r from scene_objects where scene_id=? and id<>?",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),row.get("scene_id"),row.get("id")));
   var cells=new ArrayList<>(map.cells().stream().filter(c->c.walkable()&&Set.of("grass","dirt").contains(c.terrain())&&!occupied.contains(c.hex())).toList());
   if(!cells.isEmpty()){Collections.shuffle(cells);var h=cells.getFirst();db.update("update scene_objects set q=?,r=?,ready_at=0 where id=?",h.q(),h.r(),row.get("id"));changed=true;}
  }return changed;
 }
}
