package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** One shared player/NPC action processor. Every call runs under the world monitor and transaction. */
@Service
public class ProductionService {
 public record Start(String kind,String recipe,Integer batches,UUID objectId,UUID corpseId) {}
 public record Task(UUID actor,String kind,String recipe,UUID objectId,UUID corpseId,UUID sceneId,String version,int worldQ,int worldR,Integer q,Integer r,long startedAt,long dueAt,int total,int completed) {}
 private final JdbcTemplate db;private final SceneRuntime runtime;private final EquipmentService equipment;private final CharacterService characters;private final LootService loot;
 @org.springframework.beans.factory.annotation.Autowired private SceneService scenes;
 @org.springframework.beans.factory.annotation.Autowired private TownService towns;
 ProductionService(JdbcTemplate db,SceneRuntime runtime,EquipmentService equipment,CharacterService characters,LootService loot){this.db=db;this.runtime=runtime;this.equipment=equipment;this.characters=characters;this.loot=loot;}
 private List<Task> tasks(String suffix,Object...args){return db.query("select * from production_tasks "+suffix,(r,n)->new Task(r.getObject("actor_id",UUID.class),r.getString("kind"),r.getString("recipe"),r.getObject("object_id",UUID.class),r.getObject("corpse_id",UUID.class),r.getObject("scene_id",UUID.class),r.getString("world_version"),r.getInt("world_q"),r.getInt("world_r"),(Integer)r.getObject("q"),(Integer)r.getObject("r"),r.getLong("started_at"),r.getLong("due_at"),r.getInt("total"),r.getInt("completed")),args);}
 public Task current(UUID actor){return tasks("where actor_id=?",actor).stream().findFirst().orElse(null);}
 public Object view(UUID actor){var out=new LinkedHashMap<String,Object>();out.put("recipes",ProductionRecipes.ALL);out.put("task",current(actor));out.put("reserved",db.queryForList("select item_code,quantity from inventory_reservations where actor_id=?",actor));out.put("notice",db.queryForList("select message,created_at from production_notices where actor_id=?",actor).stream().findFirst().orElse(null));out.put("skinTargets",skinTargets(actor));return out;}
 private List<Map<String,Object>> skinTargets(UUID actor){var c=characters.get(actor);var p=runtime.position(actor);return db.queryForList("select id,name from loot_containers where skinnable and (carrier_id=? or (carrier_id is null and battle_id is null and expires_at>? and world_version=? and q=? and r=? and scene_id is not distinct from ? and (? or (battle_q=? and battle_r=?)))) order by id",actor,System.currentTimeMillis(),scenes.version(),c.q(),c.r(),p==null?null:p.sceneId(),p==null,p==null?null:p.q(),p==null?null:p.r());}
 private boolean engaged(UUID actor){return db.queryForObject("select exists(select 1 from battle_actors where account_id=?)",Boolean.class,actor);}
 private boolean tool(UUID actor,String subtype){return subtype==null||db.queryForObject("select exists(select 1 from characters c join item_definitions d on d.code=c.weapon where c.id=? and d.subtype=?)",Boolean.class,actor,subtype);}
 private Map<String,Object> object(UUID id){if(id==null)throw AccountService.bad("请选择资源或设施");return db.queryForList("select * from scene_objects where id=? for update",id).stream().findFirst().orElseThrow(()->AccountService.bad("对象已不存在"));}
 private long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 private void access(UUID actor,Map<String,Object> obj,boolean adjacent){var p=runtime.position(actor);if(p==null||!p.sceneId().equals(obj.get("scene_id")))throw AccountService.bad("请进入对象所在二级地图");var h=new WorldMap.Hex((int)number(obj,"q"),(int)number(obj,"r"));if(WorldMap.distance(p.hex(),h)!=(adjacent?1:0))throw AccountService.bad(adjacent?"请走到设施相邻格":"请走到资源所在格");}
 public void start(UUID actor,WorldMap world,Start request,long now){
  if(request==null||request.kind()==null)throw AccountService.bad("请选择任务");characters.requireAlive(actor);
  if(engaged(actor))throw AccountService.bad("战斗中不能开始采集或制作");
  if(current(actor)!=null||characters.get(actor).timerKind()!=null||db.queryForObject("select exists(select 1 from world_actions where account_id=?)",Boolean.class,actor))throw AccountService.bad("已有持续任务，请先取消");
  var p=runtime.position(actor);long duration;String recipe=null;UUID objectId=null,corpseId=null;int count=1;
  switch(request.kind()){
   case "rest" -> {towns.requireService(actor,"inn");CoinPayment.pay(equipment.available(actor,"copper_coin"),equipment.available(actor,"silver_coin"),equipment.available(actor,"gold_coin"),5);duration=10000;}
   case "craft" -> {
    var r=ProductionRecipes.get(request.recipe());recipe=r.code();if(request.batches()==null)throw AccountService.bad("请选择制作批数");count=request.batches();if(!(r.batch()?Set.of(1,5,10).contains(count):count==1))throw AccountService.bad("请选择允许的制作批数");
    if(!r.facility().equals("portable")){var o=object(request.objectId());access(actor,o,true);if(!o.get("kind").equals("facility")||!o.get("code").equals(r.facility()))throw AccountService.bad("制作设施不匹配");if(db.queryForObject("select count(*) from production_tasks where object_id=?",Integer.class,request.objectId())>=number(o,"capacity"))throw AccountService.bad("设施使用中，请稍后再来");objectId=request.objectId();}
    for(var e:r.materials().entrySet())if(equipment.available(actor,e.getKey())<(long)e.getValue()*count)throw AccountService.bad("可用材料不足，部分材料可能已预留");duration=r.duration();
   }
   case "gather" -> {var o=object(request.objectId());access(actor,o,false);if(!o.get("kind").equals("resource"))throw AccountService.bad("不是采集资源");if(number(o,"ready_at")>now)throw AccountService.bad("资源尚未恢复");if(!tool(actor,(String)o.get("tool")))throw AccountService.bad("请先手持对应工具");if(db.queryForObject("select exists(select 1 from production_tasks where object_id=?)",Boolean.class,request.objectId()))throw AccountService.bad("资源正在被采集");duration=number(o,"duration_ms");objectId=request.objectId();if(duration==0){equipment.give(actor,(String)o.get("code"),1);db.update("update scene_objects set ready_at=? where id=?",now+number(o,"respawn_ms"),objectId);notice(actor,"石头 +1",now);return;}}
   case "skin" -> {if(!tool(actor,"dagger"))throw AccountService.bad("剥皮需要手持匕首");var c=characters.get(actor);loot.requireSkinnable(request.corpseId(),new LootService.Context(actor,world.version(),c.q(),c.r(),null,null,null),now);corpseId=request.corpseId();duration=8000;}
   default -> throw AccountService.bad("不支持的持续任务");
  }
  var c=characters.get(actor);db.update("insert into production_tasks(actor_id,kind,recipe,object_id,corpse_id,scene_id,world_version,world_q,world_r,q,r,started_at,due_at,total) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",actor,request.kind(),recipe,objectId,corpseId,p==null?null:p.sceneId(),world.version(),c.q(),c.r(),p==null?null:p.q(),p==null?null:p.r(),now,now+duration,count);
  if(recipe!=null)for(var e:ProductionRecipes.get(recipe).materials().entrySet())db.update("insert into inventory_reservations values(?,?,?)",actor,e.getKey(),(long)e.getValue()*count);
  if(request.kind().equals("rest")){
   long copper=equipment.available(actor,"copper_coin"),silver=equipment.available(actor,"silver_coin"),gold=equipment.available(actor,"gold_coin");var result=CoinPayment.pay(copper,silver,gold,5);long usedCopper=Math.min(copper,5),usedSilver=silver-result.silver(),usedGold=gold-result.gold(),change=result.copper()-(copper-usedCopper);
   db.update("insert into rest_payments values(?,?,?,?,?)",actor,usedCopper,usedSilver,usedGold,change);
   for(var e:Map.of("copper_coin",usedCopper,"silver_coin",usedSilver,"gold_coin",usedGold).entrySet())if(e.getValue()>0)db.update("insert into inventory_reservations values(?,?,?)",actor,e.getKey(),e.getValue());
  }
  notice(actor,"任务已开始",now);
 }
 private void notice(UUID actor,String message,long now){db.update("insert into production_notices values(?,?,?) on conflict(actor_id) do update set message=excluded.message,created_at=excluded.created_at",actor,message,now);}
 public void cancel(UUID actor){if(db.update("delete from production_tasks where actor_id=?",actor)>0){notice(actor,"任务已中断，未用材料已释放",System.currentTimeMillis());loot.expire(System.currentTimeMillis());}}
 public void moving(UUID actor){var task=current(actor);if(task!=null&&(task.sceneId()==null||!portable(task)))cancel(actor);}
 private boolean portable(Task t){return t.kind().equals("craft")&&ProductionRecipes.get(t.recipe()).facility().equals("portable");}
 public void restart(){db.update("insert into production_notices select actor_id,'服务重启，任务已中断，未用材料已释放',? from production_tasks on conflict(actor_id) do update set message=excluded.message,created_at=excluded.created_at",System.currentTimeMillis());db.update("delete from production_tasks");}
 private boolean valid(Task t){
  var c=characters.get(t.actor());if(!t.version().equals(scenes.version())||!c.alive()||engaged(t.actor())||c.q()!=t.worldQ()||c.r()!=t.worldR())return false;var p=runtime.position(t.actor());
  if(!Objects.equals(t.sceneId(),p==null?null:p.sceneId()))return false;if(!portable(t)&&p!=null&&(!Objects.equals(t.q(),p.q())||!Objects.equals(t.r(),p.r())))return false;
  if(t.kind().equals("gather")){var o=object(t.objectId());return tool(t.actor(),(String)o.get("tool"));}
  if(t.kind().equals("rest")){try{towns.requireService(t.actor(),"inn");}catch(org.springframework.web.server.ResponseStatusException e){return false;}}
  return !t.kind().equals("skin")||tool(t.actor(),"dagger");
 }
 public boolean advance(long now){
  boolean changed=false;
  for(var t:tasks("order by due_at,actor_id")){
   if(!valid(t)){cancel(t.actor());changed=true;continue;}if(t.dueAt()>now)continue;
   if(t.kind().equals("craft")){
    var recipe=ProductionRecipes.get(t.recipe());for(var e:recipe.materials().entrySet()){db.update("delete from inventory_reservations where actor_id=? and item_code=? and quantity=?",t.actor(),e.getKey(),e.getValue());db.update("update inventory_reservations set quantity=quantity-? where actor_id=? and item_code=?",e.getValue(),t.actor(),e.getKey());equipment.consume(t.actor(),e.getKey(),e.getValue());}equipment.give(t.actor(),recipe.code(),recipe.output());
    if(t.completed()+1<t.total()){db.update("update production_tasks set completed=completed+1,started_at=?,due_at=? where actor_id=?",now,now+recipe.duration(),t.actor());notice(t.actor(),recipe.name()+" +"+recipe.output(),now);}else{db.update("delete from production_tasks where actor_id=?",t.actor());notice(t.actor(),"制作完成："+recipe.name()+" ×"+(recipe.output()*t.total()),now);}
   }else if(t.kind().equals("gather")){var o=object(t.objectId());equipment.give(t.actor(),(String)o.get("code"),1);db.update("update scene_objects set ready_at=? where id=?",now+number(o,"respawn_ms"),t.objectId());db.update("delete from production_tasks where actor_id=?",t.actor());notice(t.actor(),o.get("name")+"采集完成 +1",now);
   }else if(t.kind().equals("rest")){
    var fee=db.queryForMap("select * from rest_payments where actor_id=?",t.actor());db.update("delete from inventory_reservations where actor_id=?",t.actor());
    for(String coin:List.of("copper","silver","gold")){long amount=number(fee,coin);if(amount>0)equipment.consume(t.actor(),coin+"_coin",(int)amount);}
    if(number(fee,"change_copper")>0)db.update("insert into inventories values(?,'copper_coin',?) on conflict(character_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",t.actor(),number(fee,"change_copper"));
    db.update("update characters set hp=max_hp where id=?",t.actor());db.update("delete from production_tasks where actor_id=?",t.actor());notice(t.actor(),"休整完成，生命已恢复，支付5铜",now);
   }else{boolean success=java.util.concurrent.ThreadLocalRandom.current().nextInt(100)<80;loot.finishSkin(t.corpseId(),t.actor(),success,now);notice(t.actor(),success?"剥皮成功，兽皮 +1":"剥皮失败，尸体已消耗",now);}
   changed=true;
  }return changed;
 }
}
