package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Logistics decisions select common actions; all effects remain in the world gateway. */
@Service
public class LogisticsDirector {
 public interface Actions {
  boolean moving(UUID actor);
  void navigate(UUID actor,NpcNavigation.Leg leg);
  void buy(UUID actor,String code,int quantity,UUID post,long now);
  void deliver(LogisticsLedger.Need need,long now);
  void collect(LogisticsLedger.Need need,long now);
  void claim(UUID actor,UUID station,UUID corpse,long now);
  void transfer(LogisticsLedger.Need need,UUID sender,int quantity,long cost,long now);
 }
 private final JdbcTemplate db;private final LogisticsLedger ledger;private final EquipmentService equipment;
 private final SceneRuntime runtime;private final NpcNavigation navigation;private final NpcPerception perception;private final CharacterService characters;
 LogisticsDirector(JdbcTemplate db,LogisticsLedger ledger,EquipmentService equipment,SceneRuntime runtime,NpcNavigation navigation,NpcPerception perception,CharacterService characters){this.db=db;this.ledger=ledger;this.equipment=equipment;this.runtime=runtime;this.navigation=navigation;this.perception=perception;this.characters=characters;}
 private static int number(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}
 private static WorldMap.Hex cell(Map<String,Object> row){return new WorldMap.Hex(number(row,"q"),number(row,"r"));}
 public List<UUID> ready(long now){return db.queryForList("select a.actor_id from logistics_agents a join characters c on c.id=a.actor_id where c.life='alive' and a.next_think<=? and not exists(select 1 from battle_actors ba join battle_encounters b on b.id=ba.encounter_id where b.active and ba.account_id=a.actor_id) order by a.actor_id",UUID.class,now);}
 public void act(UUID actor,WorldMap world,long now,Actions actions){
  if(actions.moving(actor)||runtime.position(actor)==null){db.update("update logistics_needs set pickup_active=false,last_clock=? where carrier=? and state='pickup'",now,actor);return;}
  if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_stops where target=? and accepted and expires_at>?)",Boolean.class,actor,now)))return;
  var agent=db.queryForMap("select a.*,p.scene_id,p.q,p.r,p.blocked_ms,p.allow_external,p.minimum from logistics_agents a join logistics_posts p on p.id=a.post_id where a.actor_id=?",actor);
  ledger.reconcileLots(actor,now);
  var owned=ledger.query("where carrier=? and state in ('claimed','pickup') order by urgency desc,created_at,id",actor);
  if(owned.isEmpty()){
   var pending=ledger.query("where (post_id=? or (? and post_id in (select id from logistics_posts where allow_external))) and state='open' and retry_after<=? order by case when post_id=? then 0 else 1 end,urgency desc,created_at,id",agent.get("post_id"),agent.get("allow_external"),now,agent.get("post_id"));
   for(var candidate:pending){
    if(!candidate.post().equals(agent.get("post_id"))){int available=db.queryForObject("select count(*) from logistics_agents a join characters c on c.id=a.actor_id join scene_positions p on p.character_id=a.actor_id and p.active join logistics_posts home on home.id=a.post_id where a.post_id=? and p.scene_id=home.scene_id and c.life='alive' and not exists(select 1 from logistics_needs n where n.carrier=a.actor_id and n.state in ('claimed','pickup')) and not exists(select 1 from battle_actors ba join battle_encounters b on b.id=ba.encounter_id where ba.account_id=a.actor_id and b.active)",Integer.class,agent.get("post_id"));if(available<=number(agent,"minimum"))continue;}
    boolean stockElsewhere=candidate.code()!=null&&equipment.available(actor,candidate.code())==0&&Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_agents a join characters c on c.id=a.actor_id join inventories i on i.character_id=a.actor_id and i.item_code=? where a.post_id=? and a.actor_id<>? and c.life='alive' and i.quantity-reserved_quantity(a.actor_id,i.item_code)>0 and not exists(select 1 from logistics_needs n where n.carrier=a.actor_id and n.state in ('claimed','pickup')))",Boolean.class,candidate.code(),agent.get("post_id"),actor));
    if(stockElsewhere)continue;
    var claimed=new ArrayList<LogisticsLedger.Need>();claimed.add(ledger.claim(candidate.id(),candidate.version(),actor,now));
    if(candidate.code()!=null){int total=candidate.remaining();for(var extra:pending){if(extra.id()==candidate.id()||!extra.post().equals(candidate.post())||!Objects.equals(extra.code(),candidate.code())||total+extra.remaining()>10000)continue;claimed.add(ledger.claim(extra.id(),extra.version(),actor,now));total+=extra.remaining();}}
    // Persist the claim before attempting an action that may roll back.
    db.update("update logistics_agents set task='claimed',next_think=? where actor_id=?",now+200,actor);return;
   }
  }
  if(owned.isEmpty()){
   go(actor,(UUID)agent.get("scene_id"),new WorldMap.Hex(number(agent,"home_q"),number(agent,"home_r")),0,world,actions);
   db.update("update logistics_agents set task='return',next_think=? where actor_id=?",now+1000,actor);return;
  }
  var n=owned.getFirst();
  if(n.kind().equals("corpse")){
   UUID holder=db.queryForObject("select carrier_id from loot_containers where id=?",UUID.class,n.corpse());
   if(actor.equals(holder)){
    var stations=db.queryForList("select s.id,s.scene_id,p.q,p.r from law_stations s join logistics_posts l on l.station_id=s.id join scene_positions p on p.character_id=s.officer_id and p.scene_id=s.scene_id and p.active join characters c on c.id=s.officer_id where l.id=? and c.life='alive'",n.post());
    if(stations.isEmpty())throw AccountService.bad("执法设施暂不可用");var station=stations.getFirst();
    if(go(actor,(UUID)station.get("scene_id"),cell(station),1,world,actions)){actions.claim(actor,(UUID)station.get("id"),n.corpse(),now);ledger.delivered(n.id(),n.version(),actor,1,UUID.randomUUID(),now);}return;
   }
   if(!Objects.equals(holder,n.recipient()))throw AccountService.bad("尸体已离开原持有者");
   var source=perception.observe(actor,n.recipient(),world,false);if(source==null)throw AccountService.bad("暂时找不到交尸人员");
   if(go(actor,source.sceneId(),source.hex(),source.precise()?1:0,world,actions)){
    if(source.precise())actions.collect(n,now);else actions.navigate(actor,new NpcNavigation.Leg("door",source.entrance(),null,null,1));
   }return;
  }
  if(n.code()==null)return;
  int reserved=ledger.reserve(n.id(),n.version(),actor);n=ledger.get(n.id());
  if(reserved==0){
   for(UUID donor:db.queryForList("select a.actor_id from logistics_agents a join characters c on c.id=a.actor_id join inventories i on i.character_id=a.actor_id and i.item_code=? where a.post_id=? and a.actor_id<>? and c.life='alive' and i.quantity-reserved_quantity(a.actor_id,i.item_code)>0 and not exists(select 1 from battle_actors ba join battle_encounters b on b.id=ba.encounter_id where ba.account_id=a.actor_id and b.active) order by a.actor_id",UUID.class,n.code(),agent.get("post_id"),actor)){
    var source=perception.observe(actor,donor,world,false);if(source==null||!source.precise())continue;
    var route=navigation.route(actor,source.sceneId(),source.hex(),1,world,false);if(route==null)continue;
    if(go(actor,source.sceneId(),source.hex(),1,world,actions)){ledger.reconcileLots(donor,now);var lots=ledger.lots(donor,n.code());if(lots.isEmpty())continue;var lot=lots.getFirst();int quantity=(int)Math.min(n.remaining(),Math.min(equipment.available(donor,n.code()),LogisticsLedger.number(lot,"quantity")));actions.transfer(n,donor,quantity,LogisticsLedger.number(lot,"unit_cost"),now);}return;
   }
   Map<String,Object> shop=null;int distance=Integer.MAX_VALUE;
   for(var candidate:db.queryForList("select b.id,p.q,p.r from town_buildings b join characters c on c.id=b.npc_id join scene_positions p on p.character_id=c.id and p.scene_id=b.id and p.active where b.kind='shop' and c.life='alive' order by b.id")){
    UUID scene=(UUID)candidate.get("id");if(!perception.samePlace(world,characters.get(actor).hex(),runtime.map(scene).owner()))continue;
    var route=navigation.route(actor,scene,cell(candidate),1,world,true);if(route!=null&&route.cost()<distance){distance=route.cost();shop=candidate;}
   }
   if(shop==null)throw AccountService.bad("没有已知可达商店");
   if(go(actor,(UUID)shop.get("id"),cell(shop),1,world,actions)){
    var grouped=ledger.query("where carrier=? and code=? and state in ('claimed','pickup') order by urgency desc,created_at,id",actor,n.code());int quantity=grouped.stream().mapToInt(x->x.remaining()-x.reserved()).sum();quantity=Math.min(10000,Math.max(0,quantity-Math.toIntExact(equipment.available(actor,n.code()))));
    if(quantity>0)actions.buy(actor,n.code(),quantity,(UUID)agent.get("post_id"),now);for(var part:grouped)ledger.reserve(part.id(),part.version(),actor);
   }
   db.update("update logistics_agents set task='procure',next_think=? where actor_id=?",now+1000,actor);return;
  }
  if(n.kind().equals("stock")){ledger.release(n.id(),"done",now,"驻点实物备货已到位");return;}
  var seen=perception.observe(actor,n.recipient(),world,false);
  if(seen==null){
   var pickup=db.queryForMap("select scene_id,q,r from logistics_posts where id=?",n.post());
   boolean arrived=go(actor,(UUID)pickup.get("scene_id"),cell(pickup),1,world,actions);
   db.update("update logistics_needs set state='pickup' where id=?",n.id());long limit=db.queryForObject("select pickup_ms from logistics_posts where id=?",Long.class,n.post());ledger.pickupClock(n,arrived,now,limit);return;
  }
  if(go(actor,seen.sceneId(),seen.hex(),seen.precise()?1:0,world,actions)){
   if(seen.precise())actions.deliver(n,now);else actions.navigate(actor,new NpcNavigation.Leg("door",seen.entrance(),null,null,1));
  }
  db.update("update logistics_agents set task='deliver',next_think=?,failures=0 where actor_id=?",now+1000,actor);
 }
 private boolean go(UUID actor,UUID scene,WorldMap.Hex goal,int range,WorldMap world,Actions actions){var leg=navigation.route(actor,scene,goal,range,world,false);if(leg==null)throw AccountService.bad("后勤路线暂时受阻");if(leg.action().equals("arrived")){db.update("update logistics_needs set blocked_since=0 where carrier=? and state in ('claimed','pickup')",actor);return true;}actions.navigate(actor,leg);db.update("update logistics_needs set blocked_since=0 where carrier=? and state in ('claimed','pickup')",actor);return false;}
 public void rejected(UUID actor,long now){
  db.update("update logistics_agents set failures=failures+1,next_think=? where actor_id=?",now+3000,actor);
  for(var n:ledger.query("where carrier=? and state in ('claimed','pickup')",actor)){
   if(n.state().equals("pickup"))db.update("update logistics_needs set pickup_active=false,last_clock=? where id=?",now,n.id());
   long limit=db.queryForObject("select blocked_ms from logistics_posts where id=?",Long.class,n.post());
   if(n.blocked()==0)db.update("update logistics_needs set blocked_since=? where id=?",now,n.id());else if(now-n.blocked()>=limit)ledger.release(n.id(),"open",now,"长期受阻，重新分派");
  }
 }
}
