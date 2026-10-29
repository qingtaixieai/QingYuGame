package game.world;

import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Creates configured couriers and respawns dead couriers; never relocates a living courier. */
@Service
public class LogisticsRoster {
 private final JdbcTemplate db;private final SceneRuntime runtime;private final BattleService battles;
 LogisticsRoster(JdbcTemplate db,SceneRuntime runtime,BattleService battles){this.db=db;this.runtime=runtime;this.battles=battles;}
 private static int number(Map<String,Object> row,String key){return ((Number)row.get(key)).intValue();}
 @Transactional(propagation=Propagation.MANDATORY)
 public void ensure(long now){
  for(var station:db.queryForList("select s.id,s.scene_id,s.faction_id,p.q,p.r from law_stations s join scene_positions p on p.character_id=s.officer_id and p.active and p.scene_id=s.scene_id")){
   UUID id=UUID.nameUUIDFromBytes((station.get("id")+":logistics").getBytes(StandardCharsets.UTF_8));
   db.update("insert into logistics_posts(id,station_id,scene_id,faction_id,q,r) values(?,?,?,?,?,?) on conflict(station_id) do nothing",id,station.get("id"),station.get("scene_id"),station.get("faction_id"),station.get("q"),station.get("r"));
   db.update("insert into logistics_stock_targets(post_id,code,quantity) values(?,'bandage',4) on conflict do nothing",id);
   db.update("insert into logistics_clients(actor_id,post_id) select t.actor_id,? from npc_tasks t join characters c on c.id=t.actor_id where t.post_id=? and c.kind<>'player' on conflict(actor_id) do nothing",id,station.get("id"));
  }
  for(var post:db.queryForList("select * from logistics_posts order by id")){
   UUID postId=(UUID)post.get("id"),scene=(UUID)post.get("scene_id");var map=runtime.map(scene);
   for(int i=0;i<number(post,"capacity");i++){
    UUID actor=UUID.nameUUIDFromBytes((postId+":courier:"+i).getBytes(StandardCharsets.UTF_8));
    var existing=db.queryForList("select life,timer_end from characters where id=?",actor);
    if(!existing.isEmpty()&&(!existing.getFirst().get("life").equals("dead")||((Number)existing.getFirst().get("timer_end")).longValue()>now))continue;
    Set<WorldMap.Hex> blocked=new HashSet<>(runtime.occupied(scene,actor));
    for(var service:db.queryForList("select p.q,p.r from scene_positions p join characters c on c.id=p.character_id where p.scene_id=? and p.active and c.kind='service' and c.species not in ('guard','courier')",scene)){
     var h=new WorldMap.Hex(number(service,"q"),number(service,"r"));blocked.add(h);blocked.addAll(h.neighbors());
    }
    blocked.addAll(db.query("select board_q,board_r from law_stations where scene_id=?",(r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),scene));
    var home=new WorldMap.Hex(number(post,"q"),number(post,"r"));
    var cell=map.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->!blocked.contains(h)&&SceneRuntime.sides(h,map.radius()).isEmpty()).min(Comparator.comparingInt(h->WorldMap.distance(h,home))).orElse(null);
    if(cell==null)break;
    db.update("insert into characters(id,kind,species,name,q,r,bind_q,bind_r,next_move) values(?,'service','courier','后勤员',?,?,?,?,?) on conflict(id) do update set life='alive',hp=characters.max_hp,down_hp=characters.max_down_hp,timer_kind=null,timer_end=0",actor,map.owner().q(),map.owner().r(),map.owner().q(),map.owner().r(),Long.MAX_VALUE);
    runtime.put(actor,scene,cell);
    db.update("insert into logistics_agents(actor_id,post_id,home_q,home_r) values(?,?,?,?) on conflict(actor_id) do update set next_think=0,failures=0,task='idle'",actor,postId,cell.q(),cell.r());
    battles.joinResident(actor,now);
   }
  }
 }
}
