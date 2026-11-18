package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import static game.world.WorldMap.distance;

@Service
final class SceneLoggingCamp {
    static final String NAME="废弃伐木营";
    static final String MATERIAL="camp_venom";
    static final long RESTOCK_MS=15*60_000;
    static final WorldMap.Hex CHEST=new WorldMap.Hex(3,0);
    static final List<WorldMap.Hex> PATROL=List.of(new WorldMap.Hex(-1,0),
        new WorldMap.Hex(0,0),new WorldMap.Hex(1,0),new WorldMap.Hex(0,1),new WorldMap.Hex(0,0));
    private static final Set<WorldMap.Hex> FENCE=Set.of(new WorldMap.Hex(0,2),
        new WorldMap.Hex(1,1),new WorldMap.Hex(2,0));
    private static final Set<WorldMap.Hex> PATH=Set.of(new WorldMap.Hex(-2,0),
        new WorldMap.Hex(-1,0),new WorldMap.Hex(0,0),new WorldMap.Hex(1,0),
        new WorldMap.Hex(0,1),new WorldMap.Hex(1,-1),new WorldMap.Hex(2,-1),
        new WorldMap.Hex(3,-1),CHEST,new WorldMap.Hex(-1,2),
        new WorldMap.Hex(-1,3),new WorldMap.Hex(0,3),new WorldMap.Hex(1,2),
        new WorldMap.Hex(2,1),new WorldMap.Hex(3,1));
    private record Camp(UUID scene,UUID monster,int patrol,Long restock){}
    private final JdbcTemplate db;
    private final SceneService scenes;
    private final CharacterService characters;
    private final TransactionTemplate transactions;
    private final JsonMapper json;

    SceneLoggingCamp(JdbcTemplate db,SceneService scenes,CharacterService characters,PlatformTransactionManager manager,JsonMapper json){
        this.db=db;this.scenes=scenes;this.characters=characters;
        this.transactions=new TransactionTemplate(manager);this.json=json;
    }
    static Optional<WorldMap.Hex> site(WorldMap world){
        return world.tiles().stream().filter(t->t.terrain().equals("forest")&&t.place()==null)
            .sorted(Comparator.comparingInt((WorldMap.Tile t)->distance(world.spawn(),t.hex()))
                .thenComparingInt(WorldMap.Tile::q).thenComparingInt(WorldMap.Tile::r))
            .map(WorldMap.Tile::hex).filter(hex->!world.path(world.spawn(),hex).isEmpty()).findFirst();
    }
    SceneMap ensure(SceneMap map,WorldMap.Hex site){
        if(!map.owner().equals(site)||map.level()!=2)return map;
        if(find(map.id())!=null)return map;
        return transactions.execute(status->{
            String saved=db.queryForObject("select document from scene_documents where id=? for update",String.class,map.id());
            if(find(map.id())!=null)return json.readValue(saved,SceneMap.class);
            return create(json.readValue(saved,SceneMap.class),System.currentTimeMillis());
        });
    }
    private SceneMap create(SceneMap map,long now){
        var occupied=new HashSet<>(db.query("select q,r from scene_positions where scene_id=? and active union select q,r from scene_objects where scene_id=?",
            (r,n)->new WorldMap.Hex(r.getInt(1),r.getInt(2)),map.id(),map.id()));
        var next=new SceneMap(map.id(),map.worldVersion(),map.generationVersion(),2,map.owner(),NAME,map.radius(),
            map.cells().stream().map(cell->{
                var hex=cell.hex();
                if(FENCE.contains(hex)&&!occupied.contains(hex))return new SceneMap.Cell(cell.q(),cell.r(),"camp-fence",false);
                if(PATH.contains(hex)){
                    boolean side=hex.r()>=2||hex.r()==1&&hex.q()>=2;
                    return new SceneMap.Cell(cell.q(),cell.r(),side?"camp-trail":"camp-road",true);
                }
                return cell;
            }).toList(), map.blockedEdges().stream().filter(edge->!PATH.contains(edge.from())||!PATH.contains(edge.to())).toList());
        scenes.save(next);
        UUID chest=UUID.randomUUID();
        db.update("""
            insert into loot_containers(id,kind,name,world_version,q,r,battle_q,battle_r,scene_id,created_at,expires_at)
            values(?,'chest','营地货箱',?,?,?,?,?,?,?,?)
            """,chest,map.worldVersion(),map.owner().q(),map.owner().r(),CHEST.q(),CHEST.r(),map.id(),now,Long.MAX_VALUE);
        var monster=characters.deploy("claw",map.owner().q(),map.owner().r(),now);
        db.update("update characters set deployed=false,name='营地爪兽' where id=?",monster.id());
        db.update("insert into scene_logging_camps(scene_id,chest_id,monster_id) values(?,?,?)",map.id(),chest,monster.id());
        for(var home:PATROL)if(!occupied.contains(home)){
            db.update("insert into scene_positions(character_id,scene_id,q,r,active) values(?,?,?,?,true)",monster.id(),map.id(),home.q(),home.r());
            break;
        }
        fill(chest);
        return next;
    }

    Set<UUID> residents(){
        return new HashSet<>(db.queryForList("select monster_id from scene_logging_camps",UUID.class));
    }
    boolean advance(SceneRuntime runtime,Set<UUID> online,Set<UUID> engaged,long now){
        boolean changed=false;
        for(var camp:read("")){
            var players=runtime.residents(camp.scene()).stream()
                .anyMatch(p->online.contains(p.characterId())&&!characters.get(p.characterId()).life().equals("soul"));
            if(!players)changed=restock(camp,now)||changed;
            var actor=camp.monster();
            if(engaged.contains(actor))continue;
            var monster=characters.get(actor);
            var p=runtime.position(actor);
            var home=PATROL.getFirst();
            if(!monster.alive()){
                if(monster.life().equals("dead")&&monster.timerEnd()<=now&&!runtime.occupied(camp.scene(),actor).contains(home)){
                    var owner=runtime.map(camp.scene()).owner();
                    characters.relocate(actor,owner.q(),owner.r());
                    characters.respawnMonster(actor,now);
                    runtime.put(actor,camp.scene(),home);
                    changed=true;
                }
                continue;
            }
            if(!players)continue;
            if(p==null||!p.sceneId().equals(camp.scene())){
                if(!monster.hex().equals(runtime.map(camp.scene()).owner())||runtime.occupied(camp.scene(),actor).contains(home))continue;
                runtime.put(actor,camp.scene(),home);
                p=runtime.position(actor);
                changed=true;
            }
            if(db.update("update characters set next_move=? where id=? and next_move<=?",now+3500,actor,now)!=1)continue;
            int index=camp.patrol()%PATROL.size();
            var target=PATROL.get(index);
            if(!p.hex().equals(target)&&distance(p.hex(),target)!=1){
                int current=PATROL.indexOf(p.hex());
                if(current<0){
                    var path=runtime.path(actor,home);
                    if(!path.isEmpty()&&runtime.step(actor,camp.scene(),path.getFirst()))changed=true;
                    continue;
                }
                index=current;
                target=PATROL.get(index);
            }
            if(p.hex().equals(target)){
                index=(index+1)%PATROL.size();
                target=PATROL.get(index);
            }
            if(runtime.step(actor,camp.scene(),target)){
                db.update("update scene_logging_camps set patrol_index=? where scene_id=?",index,camp.scene());
                changed=true;
            }
        }
        return changed;
    }
    void markTaken(UUID chest,long now){
        db.update("""
            update scene_logging_camps set restock_at=?
            where chest_id=? and restock_at is null and not exists(select 1 from loot_items where container_id=?)
            """,now+RESTOCK_MS,chest,chest);
    }
    private void fill(UUID chest){
        db.update("insert into loot_items(container_id,item_code,quantity) values(?,?,2) on conflict do nothing",chest,MATERIAL);
    }
    private boolean restock(Camp camp,long now){
        if(camp.restock()==null||camp.restock()>now)return false;
        return db.update("""
            with due as (
                update scene_logging_camps set restock_at=null where scene_id=? and restock_at<=?
                  and not exists(select 1 from battle_encounters where scene_id=? and active)
                returning chest_id
            )
            insert into loot_items(container_id,item_code,quantity)
            select chest_id,?,2 from due on conflict do nothing
            """,camp.scene(),now,camp.scene(),MATERIAL)>0;
    }
    private Camp find(UUID scene){return read("where scene_id=?",scene).stream().findFirst().orElse(null);}
    private List<Camp> read(String clause,Object... args){
        return db.query("select scene_id,monster_id,patrol_index,restock_at from scene_logging_camps "+clause,
            (r,n)->new Camp(r.getObject(1,UUID.class),r.getObject(2,UUID.class),
                r.getInt(3),r.getObject(4,Long.class)),args);
    }
}
