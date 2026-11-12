package game.world;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Shared allegiance query. No inventory or hidden-object inspection. */
@Service
public class NpcRelations {
 private final JdbcTemplate db;private final CharacterService characters;private final LawService law;
 NpcRelations(JdbcTemplate db,CharacterService characters,LawService law){this.db=db;this.characters=characters;this.law=law;}
 public boolean malicious(UUID actor){return Boolean.TRUE.equals(db.queryForObject("select malicious from characters where id=?",Boolean.class,actor));}
 public String faction(UUID actor){return db.queryForList("select p.faction_id from npc_tasks t join guard_posts p on p.id=t.post_id where actor_id=? union all select p.faction_id from logistics_agents a join logistics_posts p on p.id=a.post_id where a.actor_id=?",String.class,actor,actor).stream().findFirst().orElse(null);}
 public boolean enemy(UUID actor,UUID target){
  if(actor.equals(target))return false;var a=characters.get(actor);var t=characters.get(target);
  if(!t.alive()&&!t.life().equals("down"))return false;
  String faction=faction(actor);
  if(faction!=null)return malicious(target)||law.wanted(target,faction)||attacked(target,actor);
  if(!a.npc())return !t.npc()||enemy(target,actor)||db.queryForObject("select exists(select 1 from battle_events e join battle_encounters b on b.id=e.encounter_id where b.active and e.kind='start' and e.actor_id=? and e.target_id=?)",Boolean.class,actor,target);
  if(malicious(actor))return !malicious(target);
  return attacked(target,actor);
 }
 private boolean attacked(UUID actor,UUID target){return db.queryForObject("select exists(select 1 from battle_events e join battle_encounters b on b.id=e.encounter_id where b.active and e.actor_id=? and e.target_id=? and e.kind in ('attack','interrupt'))",Boolean.class,actor,target);}
}
