package game.world;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Persisted buildings and fixed-endpoint doors. Mutations share WorldService's transaction/monitor. */
@Service
public class TownService {
 public record Door(UUID id,String name,WorldMap.Hex cell,UUID destinationScene,WorldMap.Hex destination,boolean open) {}
 private final JdbcTemplate db;private final SceneService scenes;
 private final JsonMapper json=JsonMapper.builder().build();
 TownService(JdbcTemplate db,SceneService scenes){this.db=db;this.scenes=scenes;}
 private UUID id(String text){return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));}
 private SceneMap map(UUID scene){return json.readValue(db.queryForObject("select document from scene_documents where id=?",String.class,scene),SceneMap.class);}
 public boolean serviceNpc(UUID actor){return db.queryForObject("select exists(select 1 from town_buildings where npc_id=?)",Boolean.class,actor);}
 public List<Door> doors(UUID scene){
  if(scene==null)return List.of();var result=new ArrayList<Door>();
  for(String document:db.queryForList("select p.document from scene_portals p join town_doors d on d.portal_id=p.id where p.first_scene=? or p.second_scene=? order by p.id",String.class,scene,scene)){
   var p=json.readValue(document,ScenePortal.class);var from=p.first().sceneId().equals(scene)?p.first():p.second();var to=p.first().sceneId().equals(scene)?p.second():p.first();
   result.add(new Door(p.id(),map(to.sceneId()).name(),from.cell(),to.sceneId(),to.cell(),p.open()));
  }return result;
 }
 public Door requireDoor(UUID scene,UUID door,WorldMap.Hex standing){var d=doors(scene).stream().filter(x->x.id().equals(door)).findFirst().orElseThrow(()->AccountService.bad("门不在当前场景"));if(!d.open()||!d.cell().equals(standing))throw AccountService.bad("请站到这扇门的入口");return d;}
 public static WorldMap.Hex landing(SceneMap map,WorldMap.Hex door,Set<WorldMap.Hex> occupied){
  var walkable=new HashSet<>(map.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).toList());if(!walkable.contains(door))return null;
  var queue=new ArrayDeque<WorldMap.Hex>();var seen=new HashSet<WorldMap.Hex>();queue.add(door);seen.add(door);
  while(!queue.isEmpty()){var h=queue.remove();if(!occupied.contains(h))return h;for(var n:h.neighbors())if(WorldMap.distance(door,n)<=2&&walkable.contains(n)&&seen.add(n))queue.add(n);}return null;
 }
 public String service(UUID actor){var rows=db.queryForList("select b.kind from town_buildings b join scene_positions p on p.scene_id=b.id where p.character_id=? and p.active and b.kind in ('shop','inn')",String.class,actor);return rows.isEmpty()?null:rows.getFirst();}
 public void requireService(UUID actor,String kind){
  var rows=db.queryForList("select b.npc_id,b.id,c.life from scene_positions p join town_buildings b on b.id=p.scene_id join characters c on c.id=b.npc_id where p.character_id=? and p.active and b.kind=?",actor,kind);
  if(rows.isEmpty())throw AccountService.bad(kind.equals("shop")?"请进入商店后交易":"请进入旅店后休整");var row=rows.getFirst();
  if(!"alive".equals(row.get("life"))||!db.queryForObject("select exists(select 1 from scene_positions where character_id=? and scene_id=? and active)",Boolean.class,row.get("npc_id"),row.get("id")))throw AccountService.bad("服务人员暂时无法提供服务");
  if(db.queryForObject("select exists(select 1 from battle_encounters where active and scene_id=?)",Boolean.class,row.get("id")))throw AccountService.bad("战斗中暂停服务");
 }
 public SceneMap ensure(SceneMap outdoor,WorldMap world){
  if(outdoor.level()!=2)return outdoor;var tile=world.index().get(outdoor.owner());if(tile==null||tile.place()==null||!Set.of("town","village").contains(tile.place().type()))return outdoor;
  if(db.queryForObject("select count(*) from town_buildings where outdoor_id=?",Integer.class,outdoor.id())==4)return outdoor;
  var reserved=new HashSet<WorldMap.Hex>(db.query("select q,r from scene_objects where scene_id=? union select q,r from scene_positions where scene_id=? and active",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),outdoor.id(),outdoor.id()));
  var cells=new ArrayList<>(outdoor.cells());
  for(String kind:List.of("shop","workshop","smith","inn")){
   if(db.queryForObject("select exists(select 1 from town_buildings where outdoor_id=? and kind=?)",Boolean.class,outdoor.id(),kind))continue;
   String name=switch(kind){case "shop"->"商店";case "workshop"->"工坊";case "smith"->"铁匠铺";default->"旅店";};
   WorldMap.Hex center=null,door=null;
   for(var candidate:outdoor.cells()){
    var h=candidate.hex();if(WorldMap.distance(h,new WorldMap.Hex(0,0))>outdoor.radius()-4)continue;
    var footprint=new HashSet<>(h.neighbors());footprint.add(h);
    if(footprint.stream().anyMatch(reserved::contains)||footprint.stream().anyMatch(f->cells.stream().noneMatch(c->c.hex().equals(f)&&c.walkable())))continue;
    var entry=new WorldMap.Hex(h.q(),h.r()+1);var remaining=cells.stream().filter(c->c.walkable()&&(!footprint.contains(c.hex())||c.hex().equals(entry))).map(SceneMap.Cell::hex).toList();
    if(!connected(remaining))continue;center=h;door=entry;reserved.addAll(footprint);break;
   }
   if(center==null)throw AccountService.bad("此城镇暂时没有合法建筑空间");
   var footprint=new HashSet<>(center.neighbors());footprint.add(center);var entry=door;
   cells.replaceAll(c->footprint.contains(c.hex())?new SceneMap.Cell(c.q(),c.r(),c.hex().equals(entry)?"paving":"wall",c.hex().equals(entry)):c);
   UUID roomId=id(outdoor.id()+":"+kind);int radius=Set.of("workshop","smith").contains(kind)?5:4;var insideDoor=new WorldMap.Hex(0,radius);
   var base=SceneMap.room(roomId,world.version(),outdoor.owner(),tile.place().name()+" · "+name,radius);
   var room=new SceneMap(roomId,world.version(),1,3,outdoor.owner(),base.name(),radius,base.cells().stream().map(c->WorldMap.distance(c.hex(),new WorldMap.Hex(0,0))==radius&&!c.hex().equals(insideDoor)?new SceneMap.Cell(c.q(),c.r(),"wall",false):c).toList());scenes.save(room);
   UUID npc=id(roomId+":keeper");db.update("insert into characters(id,kind,species,name,q,r,bind_q,bind_r,next_move) values(?,'service',?,?,?,?,?,?,?) on conflict(id) do nothing",npc,kind,name+"掌柜",outdoor.owner().q(),outdoor.owner().r(),outdoor.owner().q(),outdoor.owner().r(),Long.MAX_VALUE);
   db.update("insert into scene_positions(character_id,scene_id,q,r,active) values(?,?,0,0,true)",npc,roomId);
   db.update("insert into town_buildings values(?,?,?,?)",roomId,outdoor.id(),kind,npc);
   UUID portal=id(roomId+":door");scenes.save(new ScenePortal(portal,new ScenePortal.Endpoint(outdoor.id(),door),new ScenePortal.Endpoint(roomId,insideDoor),true));db.update("insert into town_doors values(?,?)",portal,roomId);
   if(kind.equals("workshop")||kind.equals("smith")){
    var codes=kind.equals("workshop")?List.of("workbench"):List.of("furnace","anvil");var blocked=new HashSet<WorldMap.Hex>();int q=-1;
    for(String code:codes){var spot=new WorldMap.Hex(q,1);q+=2;blocked.add(spot);var objects=db.queryForList("select id from scene_objects where scene_id=? and code=?",UUID.class,outdoor.id(),code);
     for(UUID object:objects){db.update("delete from production_tasks where object_id=?",object);var old=db.queryForMap("select q,r from scene_objects where id=?",object);int oq=((Number)old.get("q")).intValue(),or=((Number)old.get("r")).intValue();cells.replaceAll(c->c.q()==oq&&c.r()==or?new SceneMap.Cell(c.q(),c.r(),c.terrain(),true):c);db.update("update scene_objects set scene_id=?,q=?,r=? where id=?",roomId,spot.q(),spot.r(),object);}
    }
    scenes.save(new SceneMap(room.id(),room.worldVersion(),room.generationVersion(),3,room.owner(),room.name(),room.radius(),room.cells().stream().map(c->blocked.contains(c.hex())?new SceneMap.Cell(c.q(),c.r(),"furniture",false):c).toList()));
   }
  }
  var result=new SceneMap(outdoor.id(),outdoor.worldVersion(),outdoor.generationVersion(),2,outdoor.owner(),outdoor.name(),outdoor.radius(),cells);scenes.save(result);return result;
 }
 private boolean connected(List<WorldMap.Hex> cells){if(cells.isEmpty())return false;var allowed=new HashSet<>(cells);var seen=new HashSet<WorldMap.Hex>();var queue=new ArrayDeque<WorldMap.Hex>();queue.add(cells.getFirst());seen.add(cells.getFirst());while(!queue.isEmpty())for(var n:queue.remove().neighbors())if(allowed.contains(n)&&seen.add(n))queue.add(n);return seen.size()==allowed.size();}
 public List<UUID> respawn(long now){var revived=new ArrayList<UUID>();
  for(var row:db.queryForList("select b.id,b.npc_id,d.document from town_buildings b join characters c on c.id=b.npc_id join scene_documents d on d.id=b.id where c.life='dead' and c.timer_end<=?",now)){
   var room=json.readValue((String)row.get("document"),SceneMap.class);var npc=(UUID)row.get("npc_id");var occupied=new HashSet<>(db.query("select p.q,p.r from scene_positions p join characters c on c.id=p.character_id where p.scene_id=? and p.active and c.life in ('alive','down')",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),room.id()));
   var spot=landing(room,new WorldMap.Hex(0,0),occupied);if(spot==null)continue;
   db.update("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,timer_kind=null,timer_end=0,death_q=null,death_r=null where id=?",npc);db.update("update scene_positions set active=false where character_id=?",npc);db.update("insert into scene_positions(character_id,scene_id,q,r,active) values(?,?,?,?,true) on conflict(character_id,scene_id) do update set q=excluded.q,r=excluded.r,active=true",npc,room.id(),spot.q(),spot.r());revived.add(npc);
  }return revived;
 }
}
