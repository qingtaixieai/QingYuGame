package game.world;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
/** Durable personal outcomes, committed atomically with the inventory mutation. */
@Service
public final class ProductionResults {
 public record Item(String code,long quantity) {}
 public record Result(UUID id,UUID actor,String kind,String outcome,UUID objectId,UUID corpseId,UUID sceneId,String version,int worldQ,int worldR,Integer q,Integer r,long happenedAt,String message,List<Item> items,boolean personal) {}
 public record Target(UUID sceneId,Integer q,Integer r,boolean personal) {}
 private final JdbcTemplate db; private final JsonMapper json;
 ProductionResults(JdbcTemplate db,JsonMapper json){this.db=db;this.json=json;}
 public Target target(ProductionService.Task t){
  if(t.sceneId()!=null&&t.objectId()!=null){var objects=db.queryForList("select scene_id,q,r from scene_objects where id=?",t.objectId());if(!objects.isEmpty()){var o=objects.getFirst();return new Target((UUID)o.get("scene_id"),((Number)o.get("q")).intValue(),((Number)o.get("r")).intValue(),false);}}
  if(t.corpseId()!=null){var corpses=db.queryForList("select carrier_id,scene_id,battle_q,battle_r from loot_containers where id=?",t.corpseId());if(!corpses.isEmpty()){var c=corpses.getFirst();if(c.get("carrier_id")!=null)return new Target(t.sceneId(),t.q(),t.r(),true);return new Target((UUID)c.get("scene_id"),(Integer)c.get("battle_q"),(Integer)c.get("battle_r"),false);}}
  return new Target(t.sceneId(),t.q(),t.r(),t.objectId()==null&&t.corpseId()==null);
 }
 public void record(ProductionService.Task t,String outcome,String message,List<Item> items,long now){record(t,target(t),outcome,message,items,now);}
 public void record(ProductionService.Task t,Target target,String outcome,String message,List<Item> items,long now){
  db.update("insert into production_results(id,actor_id,kind,outcome,object_id,corpse_id,scene_id,world_version,world_q,world_r,q,r,happened_at,message,items,personal) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),t.actor(),t.kind(),outcome,t.objectId(),t.corpseId(),target.sceneId(),t.version(),t.worldQ(),t.worldR(),target.q(),target.r(),now,message,json.writeValueAsString(items),target.personal());
 }
 public List<Result> recent(UUID actor){return db.query("select * from production_results where actor_id=? order by happened_at desc,id desc limit 100",(r,n)->new Result(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("kind"),r.getString("outcome"),r.getObject("object_id",UUID.class),r.getObject("corpse_id",UUID.class),r.getObject("scene_id",UUID.class),r.getString("world_version"),r.getInt("world_q"),r.getInt("world_r"),(Integer)r.getObject("q"),(Integer)r.getObject("r"),r.getLong("happened_at"),r.getString("message"),Arrays.asList(json.readValue(r.getString("items"),Item[].class)),r.getBoolean("personal")),actor);}
}
