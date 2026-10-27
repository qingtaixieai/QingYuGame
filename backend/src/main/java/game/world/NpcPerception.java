package game.world;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Location knowledge shared by human viewers and NPC decision callers. No inventory data. */
@Service
public class NpcPerception {
 public record Seen(UUID actor,UUID sceneId,int q,int r,boolean precise,UUID entrance){public WorldMap.Hex hex(){return new WorldMap.Hex(q,r);}}
 private final SceneRuntime runtime;private final TownService towns;private final CharacterService characters;private final JdbcTemplate db;
 NpcPerception(SceneRuntime runtime,TownService towns,CharacterService characters,JdbcTemplate db){this.runtime=runtime;this.towns=towns;this.characters=characters;this.db=db;}
 public boolean samePlace(WorldMap world,WorldMap.Hex a,WorldMap.Hex b){
  if(a.equals(b))return true;var ta=world.index().get(a);var tb=world.index().get(b);if(ta==null||tb==null||ta.place()==null||tb.place()==null||!ta.place().name().equals(tb.place().name()))return false;
  var seen=new HashSet<WorldMap.Hex>();var queue=new ArrayDeque<WorldMap.Hex>();seen.add(a);queue.add(a);
  while(!queue.isEmpty())for(var n:queue.remove().neighbors()){var tile=world.index().get(n);if(tile==null||tile.place()==null||!tile.place().name().equals(ta.place().name())||!seen.add(n))continue;if(n.equals(b))return true;queue.add(n);}return false;
 }
 public Seen observe(UUID viewer,UUID target,WorldMap world,boolean alarm){
  var a=runtime.position(viewer);var b=runtime.position(target);if(a==null||b==null)return null;
  if(!alarm&&!samePlace(world,characters.get(viewer).hex(),characters.get(target).hex()))return null;
  if(alarm||a.sceneId().equals(b.sceneId())||runtime.map(b.sceneId()).level()==2)return new Seen(target,b.sceneId(),b.q(),b.r(),true,null);
  // Outside an interior, only its public entrance is known, never the resident's exact tile.
  for(UUID outdoor:db.queryForList("select outdoor_id from town_buildings where id=?",UUID.class,b.sceneId()))for(var door:towns.doors(outdoor))if(door.destinationScene().equals(b.sceneId()))return new Seen(target,outdoor,door.cell().q(),door.cell().r(),false,door.id());
  return null;
 }
 public List<UUID> localActors(UUID viewer,WorldMap world){var origin=characters.get(viewer).hex();var result=new ArrayList<UUID>();for(var row:db.queryForList("select p.character_id,d.document from scene_positions p join scene_documents d on d.id=p.scene_id where p.active")){UUID id=(UUID)row.get("character_id");if(!id.equals(viewer)&&samePlace(world,origin,characters.get(id).hex()))result.add(id);}return result;}
}
