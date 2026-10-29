package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Persistent decisions only. Every action is sent to the same WorldService gateway as human input. */
@Service
public class NpcDirector {
 public interface Actions {
  void stop(UUID actor);
  boolean moving(UUID actor);
  void navigate(UUID actor,NpcNavigation.Leg leg);
  void attack(UUID actor,UUID target);
  void loot(UUID actor,UUID corpse,String action);
  void claim(UUID actor,UUID station,UUID corpse);
  void buy(UUID actor,String code,int quantity);
  void life(UUID actor,String action);
 }
 private final JdbcTemplate db;private final SceneRuntime runtime;private final CharacterService characters;
 private final LawService law;private final NpcRelations relations;private final NpcNavigation navigation;
 private final BattleService battles;private final EquipmentService equipment;
 private WorldMap world;
 @org.springframework.beans.factory.annotation.Autowired private NpcPerception perception;
 NpcDirector(JdbcTemplate db,SceneRuntime runtime,CharacterService characters,LawService law,NpcRelations relations,NpcNavigation navigation,BattleService battles,EquipmentService equipment){this.db=db;this.runtime=runtime;this.characters=characters;this.law=law;this.relations=relations;this.navigation=navigation;this.battles=battles;this.equipment=equipment;}
 public void initialize(WorldMap map){world=map;db.update("update npc_tasks set next_think=0,failures=0");}
 private static int num(Map<String,Object> m,String k){return ((Number)m.get(k)).intValue();}
 private static long time(Map<String,Object> m,String k){return ((Number)m.get(k)).longValue();}
 private WorldMap.Hex cell(Map<String,Object> m,String q,String r){return new WorldMap.Hex(num(m,q),num(m,r));}
 private void task(UUID actor,String task,UUID target){db.update("update npc_tasks set task=?,target_id=?,next_think=0,failures=0 where actor_id=?",task,target,actor);}
 public void ensure(long now){
  for(var s:db.queryForList("select s.*,p.q,p.r from law_stations s join scene_positions p on p.character_id=s.officer_id and p.scene_id=s.scene_id and p.active")){
   UUID post=(UUID)s.get("id"),scene=(UUID)s.get("scene_id");
   db.update("insert into guard_posts(id,scene_id,faction_id,q,r) values(?,?,?,?,?) on conflict do nothing",post,scene,s.get("faction_id"),s.get("q"),s.get("r"));
   var config=db.queryForMap("select * from guard_posts where id=?",post);var map=runtime.map(scene);
   for(int i=0;i<num(config,"capacity");i++){
    UUID id=UUID.nameUUIDFromBytes((post+":guard:"+i).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    if(db.queryForObject("select exists(select 1 from npc_tasks where actor_id=?)",Boolean.class,id))continue;
    var blocked=runtime.occupied(scene,id);
    // A post must never occupy the board or the service's only interaction approach.
    for(var service:db.queryForList("select p.q,p.r from scene_positions p join characters c on c.id=p.character_id where p.scene_id=? and p.active and c.kind='service' and c.species<>'guard'",scene)){var h=cell(service,"q","r");blocked.add(h);blocked.addAll(h.neighbors());}
    blocked.addAll(db.query("select board_q,board_r from law_stations where scene_id=?",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),scene));
    var h=map.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(p->!blocked.contains(p)&&SceneRuntime.sides(p,map.radius()).isEmpty()).min(Comparator.comparingInt(p->WorldMap.distance(p,cell(config,"q","r")))).orElse(null);if(h==null)break;
    db.update("insert into characters(id,kind,species,name,q,r,bind_q,bind_r,hp,max_hp,weapon,body,next_move) values(?,'service','guard','守卫',?,?,?,?,18,18,'spear','light_armor',?) on conflict(id) do update set life='alive',hp=characters.max_hp,timer_kind=null,timer_end=0",id,map.owner().q(),map.owner().r(),map.owner().q(),map.owner().r(),Long.MAX_VALUE);
    runtime.put(id,scene,h);double delta=Math.floorMod(id.hashCode(),21)/100.0;
    db.update("insert into npc_tasks(actor_id,post_id,home_scene,home_q,home_r,caution,duty,damaged_hp) values(?,?,?,?,?,?,?,18)",id,post,scene,h.q(),h.r(),.9+delta,1.5+delta);
    equipment.give(id,"bandage",2);db.update("insert into npc_supply_targets values(?,'bandage',2) on conflict do nothing",id);
    battles.joinResident(id,now);
   }
  }
  for(UUID actor:runtime.activeActors()){var c=characters.get(actor);if(!c.npc())continue;var p=runtime.position(actor);double delta=Math.floorMod(actor.hashCode(),31)/100.0;
   db.update("insert into npc_tasks(actor_id,home_scene,home_q,home_r,caution,duty,damaged_hp) values(?,?,?,?,?,?,?) on conflict do nothing",actor,p.sceneId(),p.q(),p.r(),.85+delta,1.0,c.hp());
  }
 }
 public boolean inJurisdiction(UUID actor,String faction){var c=characters.get(actor);return db.queryForObject("select exists(select 1 from jurisdiction where world_version=? and q=? and r=? and faction_id=? and enforced)",Boolean.class,world.version(),c.q(),c.r(),faction);}
 private boolean actionable(UUID actor,String faction){var c=characters.get(actor);return (c.alive()||c.life().equals("down"))&&inJurisdiction(actor,faction)&&(relations.malicious(actor)||law.wanted(actor,faction));}
 private void alarms(long now,Actions actions){
  db.update("insert into guard_alarms(faction_id,target_id,created_at) select distinct j.faction_id,c.id,? from characters c left join accounts a on a.id=c.id join jurisdiction j on j.world_version=? and j.q=coalesce(a.q,c.q) and j.r=coalesce(a.r,c.r) and j.enforced where c.life in ('alive','down') and (c.kind<>'player' or a.entered) and exists(select 1 from guard_posts p where p.faction_id=j.faction_id) and (c.malicious or exists(select 1 from crimes cr where cr.actor=c.id and cr.faction_id=j.faction_id and cr.cleared_at is null)) on conflict(faction_id,target_id) where active do nothing",now,world.version());
  for(var a:db.queryForList("select * from guard_alarms where active order by created_at,id")){
   UUID target=(UUID)a.get("target_id");long id=time(a,"id");String faction=(String)a.get("faction_id");
   var assigned=db.queryForList("select * from npc_tasks where alarm_id=?",id);
   if(!actionable(target,faction)){
    db.update("update guard_alarms set active=false where id=?",id);
    // Release unavailable assignees before assigning the corpse to a survivor.
    for(var t:assigned){UUID actor=(UUID)t.get("actor_id");if(!characters.get(actor).alive())db.update("update npc_tasks set alarm_id=null,corpse_id=null,target_id=null,task='return' where actor_id=?",actor);}
    for(var t:assigned){UUID actor=(UUID)t.get("actor_id");actions.stop(actor);if(!characters.get(actor).alive())continue;var corpse=findCorpse(target,faction,actor,now);task(actor,corpse==null?"return":"collect",corpse==null?null:target);db.update("update npc_tasks set alarm_id=null,corpse_id=? where actor_id=?",corpse,actor);}continue;
   }
   boolean support=Boolean.TRUE.equals(a.get("needs_support")),pending=false;
   for(var t:assigned){var c=characters.get((UUID)t.get("actor_id"));if(c.hp()<num(t,"damaged_hp"))support=true;
    if(c.alive()&&!battles.engaged(c.id()))pending=true;db.update("update npc_tasks set damaged_hp=? where actor_id=?",c.hp(),c.id());
   }
   db.update("update guard_alarms set batch_pending=?,needs_support=? where id=?",pending,support,id);
   if(pending||!assigned.isEmpty()&&!support)continue;
   var targetPosition=runtime.position(target);if(targetPosition==null)continue;
   var candidates=new ArrayList<Map<String,Object>>();var routes=new HashMap<UUID,NpcNavigation.Leg>();
   for(var t:db.queryForList("select t.* from npc_tasks t join guard_posts p on p.id=t.post_id join characters c on c.id=t.actor_id where p.faction_id=? and t.task='idle' and c.life='alive' and c.hp>c.max_hp/3",faction)){
    UUID actor=(UUID)t.get("actor_id");if(battles.engaged(actor))continue;var post=db.queryForMap("select * from guard_posts where id=?",t.get("post_id"));
    int reserve=db.queryForObject("select count(*) from npc_tasks t join characters c on c.id=t.actor_id where t.post_id=? and t.task='idle' and c.life='alive' and not exists(select 1 from battle_actors a join battle_encounters b on b.id=a.encounter_id where b.active and a.account_id=t.actor_id)",Integer.class,t.get("post_id"));
    if(NpcPolicy.dispatchCount(reserve,num(post,"minimum"),num(post,"batch_size"))==0)continue;
    var leg=navigation.route(actor,targetPosition.sceneId(),targetPosition.hex(),1,world,true);if(leg!=null){candidates.add(t);routes.put(actor,leg);}
   }
   candidates.sort(Comparator.comparingInt(t->routes.get((UUID)t.get("actor_id")).cost()));
   if(!candidates.isEmpty()){
    var first=candidates.getFirst();UUID post=(UUID)first.get("post_id");var config=db.queryForMap("select * from guard_posts where id=?",post);int available=db.queryForObject("select count(*) from npc_tasks t join characters c on c.id=t.actor_id where t.post_id=? and task='idle' and c.life='alive' and not exists(select 1 from battle_actors a join battle_encounters b on b.id=a.encounter_id where b.active and a.account_id=t.actor_id)",Integer.class,post);
    int count=NpcPolicy.dispatchCount(available,num(config,"minimum"),num(config,"batch_size"));
    for(var t:candidates)if(count>0&&post.equals(t.get("post_id"))){UUID actor=(UUID)t.get("actor_id");task(actor,"pursue",target);db.update("update npc_tasks set alarm_id=? where actor_id=?",id,actor);count--;}
    db.update("update guard_alarms set batch_pending=true,needs_support=false where id=?",id);
   }
  }
 }
 private UUID findCorpse(UUID target,String faction,UUID actor,long now){return db.queryForList("select l.id from loot_containers l join corpse_bounties b on b.corpse_id=l.id where l.source_id=? and b.faction_id=? and l.carrier_id is null and l.expires_at>? and l.scene_id is not null and l.battle_q is not null and l.battle_r is not null and not exists(select 1 from npc_tasks t where t.corpse_id=l.id and t.actor_id<>?) order by l.created_at desc",UUID.class,target,faction,now,actor).stream().findFirst().orElse(null);}
 private boolean go(UUID actor,UUID scene,WorldMap.Hex target,int range,Actions actions){
  var leg=navigation.route(actor,scene,target,range,world,false);if(leg==null)throw AccountService.bad("路线暂不可用");
  if(leg.action().equals("arrived"))return true;actions.navigate(actor,leg);return false;
 }
 private void respawn(long now){
  for(var t:db.queryForList("select t.*,p.respawn_ms from npc_tasks t join guard_posts p on p.id=t.post_id join characters c on c.id=t.actor_id where c.life='dead' and c.timer_end<=?",now)){
   UUID actor=(UUID)t.get("actor_id"),scene=(UUID)t.get("home_scene");var home=cell(t,"home_q","home_r");var h=TownService.landing(runtime.map(scene),home,runtime.occupied(scene,actor));if(h==null)continue;
   var map=runtime.map(scene);db.update("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,timer_kind=null,timer_end=0,q=?,r=?,weapon='spear',body='light_armor' where id=?",map.owner().q(),map.owner().r(),actor);runtime.put(actor,scene,h);equipment.give(actor,"bandage",2);task(actor,"return",null);db.update("update npc_tasks set alarm_id=null,corpse_id=null,damaged_hp=18 where actor_id=?",actor);battles.joinResident(actor,now);
  }
 }
 public void prepare(long now,Actions actions){if(world!=null){ensure(now);respawn(now);alarms(now,actions);}}
 public boolean tick(long now,Actions actions){
  if(world==null)return false;boolean changed=false;
  for(var t:db.queryForList("select t.* from npc_tasks t join characters c on c.id=t.actor_id where c.life='alive' order by t.actor_id")){
   UUID actor=(UUID)t.get("actor_id");var p=runtime.position(actor);if(p==null||"courier".equals(characters.get(actor).species())||battles.engaged(actor)||actions.moving(actor)||db.queryForObject("select exists(select 1 from production_tasks where actor_id=?)",Boolean.class,actor))continue;
   if(!"pursue".equals(t.get("task"))&&Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_stops where target=? and accepted and expires_at>?)",Boolean.class,actor,now)))continue;
   UUID target=(UUID)t.get("target_id");var tp=target==null?null:runtime.position(target);String signature=p.sceneId()+":"+p.hex()+":"+tp+":"+characters.get(actor).hp();
   if(time(t,"next_think")>now&&signature.equals(t.get("signature")))continue;
   try{decide(t,now,actions);db.update("update npc_tasks set failures=0,next_think=?,signature=? where actor_id=?",now+1000,signature,actor);changed=true;}
   catch(ResponseStatusException rejected){int failures=num(t,"failures")+1;db.update("update npc_tasks set failures=?,next_think=?,signature=? where actor_id=?",failures,now+NpcPolicy.retryDelay(failures),signature,actor);}
  }return changed;
 }
 private void decide(Map<String,Object> t,long now,Actions actions){
  UUID actor=(UUID)t.get("actor_id");var self=characters.get(actor);var p=runtime.position(actor);String current=(String)t.get("task");boolean guard=t.get("post_id")!=null;
  if(self.hp()<self.maxHp()&&(equipment.has(actor,"healing_potion")||equipment.has(actor,"bandage"))){actions.life(actor,equipment.has(actor,"healing_potion")?"potion":"bandage");return;}
  if(Set.of("idle","return").contains(current)){
   var pickup=db.queryForList("select p.scene_id,p.q,p.r from logistics_needs n join logistics_posts p on p.id=n.post_id where n.recipient=? and n.state='pickup' and n.pickup_active order by n.urgency desc,n.created_at limit 1",actor);
   boolean mayCollect=!guard||db.queryForObject("select count(*) from npc_tasks t join characters c on c.id=t.actor_id where t.post_id=? and t.task='idle' and c.life='alive'",Integer.class,t.get("post_id"))>db.queryForObject("select minimum from guard_posts where id=?",Integer.class,t.get("post_id"));
   if(!pickup.isEmpty()&&mayCollect){var point=pickup.getFirst();go(actor,(UUID)point.get("scene_id"),cell(point,"q","r"),1,actions);return;}
  }
  // Carrying a bounty is a hard responsibility, not just another score.
  var carried=db.queryForList("select l.id,b.faction_id from loot_containers l join corpse_bounties b on b.corpse_id=l.id where l.carrier_id=? order by l.created_at",actor);
  if(!carried.isEmpty()){
   var corpse=carried.getFirst();var options=db.queryForList("select s.*,p.q,p.r from law_stations s join characters c on c.id=s.officer_id join scene_positions p on p.character_id=c.id and p.active and p.scene_id=s.scene_id where s.faction_id=? and c.life='alive' and not exists(select 1 from battle_encounters b where b.scene_id=s.scene_id and b.active)",corpse.get("faction_id"));
   Map<String,Object> best=null;int distance=Integer.MAX_VALUE;for(var s:options){var leg=navigation.route(actor,(UUID)s.get("scene_id"),cell(s,"q","r"),1,world,true);if(leg!=null&&leg.cost()<distance){best=s;distance=leg.cost();}}
   if(best==null)throw AccountService.bad("暂时没有可办理执法人员");task(actor,"deliver",null);
   if(go(actor,(UUID)best.get("scene_id"),cell(best,"q","r"),1,actions)){actions.claim(actor,(UUID)best.get("id"),(UUID)corpse.get("id"));task(actor,"return",null);}return;
  }
  if(current.equals("collect")){
   UUID corpse=(UUID)t.get("corpse_id");var rows=db.queryForList("select * from loot_containers where id=? and carrier_id is null and expires_at>?",corpse,now);
   if(rows.isEmpty()){task(actor,"return",null);return;}var c=rows.getFirst();UUID scene=(UUID)c.get("scene_id");if(scene==null){task(actor,"return",null);return;}
   if(go(actor,scene,cell(c,"battle_q","battle_r"),0,actions)){
    boolean contents=db.queryForObject("select exists(select 1 from loot_items where container_id=? and quantity>0)",Boolean.class,corpse);actions.loot(actor,corpse,contents?"takeAll":"carry");if(!contents)task(actor,"deliver",null);
   }return;
  }
  if(current.equals("pursue")){
   UUID target=(UUID)t.get("target_id");String f=guard?relations.faction(actor):null;var observed=target==null?null:perception.observe(actor,target,world,guard&&actionable(target,f));
   boolean visible=target!=null&&(guard?actionable(target,f):observed!=null);
   if(visible&&observed!=null){db.update("update npc_tasks set last_scene=?,last_q=?,last_r=?,last_seen=? where actor_id=?",observed.sceneId(),observed.q(),observed.r(),now,actor);
    if(observed.precise()&&p.sceneId().equals(observed.sceneId())){actions.attack(actor,target);return;}
    if(go(actor,observed.sceneId(),observed.hex(),observed.precise()?1:0,actions)&&!observed.precise())actions.navigate(actor,new NpcNavigation.Leg("door",observed.entrance(),null,null,1));return;
   }
   if(!guard&&t.get("last_scene")!=null){if(!go(actor,(UUID)t.get("last_scene"),cell(t,"last_q","last_r"),0,actions))return;}
   task(actor,"return",null);return;
  }
  if(current.equals("return")||current.equals("deliver")){
   UUID homeScene=(UUID)t.get("home_scene");var home=cell(t,"home_q","home_r");
   var route=navigation.route(actor,homeScene,home,0,world,false);
   if(route==null){go(actor,homeScene,home,2,actions);return;}
   if(go(actor,homeScene,home,0,actions)){task(actor,"idle",null);db.update("update npc_tasks set alarm_id=null,corpse_id=null where actor_id=?",actor);}return;
  }
  // Shared purchases use actual configured shortages. A guard must leave the minimum at post.
  boolean mayLeave=!guard||db.queryForObject("select count(*) from npc_tasks t join characters c on c.id=t.actor_id where t.post_id=? and t.task='idle' and c.life='alive' and not exists(select 1 from battle_actors a join battle_encounters b on b.id=a.encounter_id where b.active and a.account_id=t.actor_id)",Integer.class,t.get("post_id"))>db.queryForObject("select minimum from guard_posts where id=?",Integer.class,t.get("post_id"))||current.equals("supply");
  var shortages=db.queryForList("select t.code as item_code,t.quantity from npc_supply_targets t where t.actor=? and not exists(select 1 from logistics_needs n where n.recipient=t.actor and n.code=t.code and n.kind='supply' and n.state in ('claimed','pickup')) order by t.code",actor);
  if(mayLeave)for(var shortage:shortages){String code=(String)shortage.get("item_code");int amount=Math.toIntExact(Math.max(0,num(shortage,"quantity")-equipment.available(actor,code)));if(amount==0)continue;
   var shops=db.queryForList("select b.id,p.q,p.r from town_buildings b join scene_positions p on p.character_id=b.npc_id and p.scene_id=b.id and p.active join characters c on c.id=b.npc_id join scene_documents d on d.id=b.outdoor_id where b.kind='shop' and c.life='alive'");
   Map<String,Object> best=null;int cost=Integer.MAX_VALUE;for(var s:shops){var scene=runtime.map((UUID)s.get("id"));if(!samePlace(self.hex(),scene.owner()))continue;var leg=navigation.route(actor,scene.id(),cell(s,"q","r"),1,world,true);if(leg!=null&&leg.cost()<cost){cost=leg.cost();best=s;}}
   if(best==null)throw AccountService.bad("没有已知可达商店");task(actor,"supply",null);if(go(actor,(UUID)best.get("id"),cell(best,"q","r"),1,actions)){actions.buy(actor,code,amount);task(actor,"return",null);}return;
  }
  if(current.equals("supply")){task(actor,"return",null);return;}
  if(guard||self.kind().equals("service"))return;
  var choices=new ArrayList<NpcPolicy.Candidate>();var visible=new HashMap<String,UUID>();
  for(UUID other:perception.localActors(actor,world))if(relations.enemy(actor,other)){var op=perception.observe(actor,other,world,false);if(op==null)continue;var leg=navigation.route(actor,op.sceneId(),op.hex(),op.precise()?1:0,world,true);if(leg!=null){String key=other.toString();visible.put(key,other);choices.add(new NpcPolicy.Candidate(key,60-leg.cost(),true));}}
  var choice=NpcPolicy.choose(choices,t.get("target_id")==null?null:t.get("target_id").toString(),8);
  if(choice!=null){task(actor,"pursue",visible.get(choice.key()));return;}
  if(!p.sceneId().equals(t.get("home_scene"))){task(actor,"return",null);return;}
  if(db.queryForObject("select next_move from characters where id=?",Long.class,actor)>now)return;
  var neighbors=new ArrayList<>(p.hex().neighbors());Collections.shuffle(neighbors);
  for(var next:neighbors)if(WorldMap.distance(next,cell(t,"home_q","home_r"))<=5){var leg=navigation.route(actor,p.sceneId(),next,0,world,false);if(leg!=null){actions.navigate(actor,leg);break;}}
  db.update("update characters set next_move=? where id=?",now+4000,actor);
 }
 private boolean ordinaryVisible(UUID actor,UUID target){var a=runtime.position(actor);var b=runtime.position(target);if(a==null||b==null)return false;return a.sceneId().equals(b.sceneId())&&samePlace(characters.get(actor).hex(),characters.get(target).hex());}
 private boolean samePlace(WorldMap.Hex a,WorldMap.Hex b){if(a.equals(b))return true;var ta=world.index().get(a);var tb=world.index().get(b);if(ta==null||tb==null||ta.place()==null||tb.place()==null||!ta.place().name().equals(tb.place().name()))return false;var seen=new HashSet<WorldMap.Hex>();var queue=new ArrayDeque<WorldMap.Hex>();seen.add(a);queue.add(a);while(!queue.isEmpty())for(var n:queue.remove().neighbors()){var tile=world.index().get(n);if(tile==null||tile.place()==null||!tile.place().name().equals(ta.place().name())||!seen.add(n))continue;if(n.equals(b))return true;queue.add(n);}return false;}
}
