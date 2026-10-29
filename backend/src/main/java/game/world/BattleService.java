package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Server-owned tactical encounters. WorldService serializes commands and commits them before broadcasting. */
@Service
public class BattleService {
    @org.springframework.beans.factory.annotation.Autowired private NpcRelations npcRelations;
    @org.springframework.beans.factory.annotation.Autowired private LawService law;
    public void payHandover(UUID actor,Set<UUID> online,long now){var b=requireTurn(actor);requireStanding(actor);if(b.points()<1)throw bad("交接需要1行动点");if(Boolean.TRUE.equals(db.queryForObject("select sprinted from battle_actors where encounter_id=? and account_id=?",Boolean.class,b.id(),actor)))throw bad("快跑后不能交出物品");db.update("update battle_actors set handed_over=true where encounter_id=? and account_id=?",b.id(),actor);db.update("update battle_encounters set turn_points=turn_points-1 where id=?",b.id());event(b.id(),"handover",actor,null,null,null,now);if(b.points()==1)advanceTurn(b,position(b.id(),actor).initiative(),online,now);}
    @org.springframework.beans.factory.annotation.Autowired private ProductionService production;
    public static final int RADIUS=6;
    private static final int TURN_POINTS=6;
    public static final List<WorldMap.Hex> DIRECTIONS=List.of(new WorldMap.Hex(1,0),new WorldMap.Hex(0,1),new WorldMap.Hex(-1,1),new WorldMap.Hex(-1,0),new WorldMap.Hex(0,-1),new WorldMap.Hex(1,-1));
    private static final String[] NAMES={"东","东南","西南","西","西北","东北"};
    private WorldMap world;
    @org.springframework.beans.factory.annotation.Autowired private SceneRuntime sceneRuntime;
    @org.springframework.beans.factory.annotation.Autowired private TownService towns;
    private UUID sceneId(Encounter b){return b.scene()==null?null:b.scene().id();}
    private SceneMap scene(Encounter b){return b.scene();}
    private int radius(Encounter b){var scene=scene(b);return scene==null?RADIUS:scene.radius();}
    private List<Integer> sides(Encounter b,int q,int r){var h=new WorldMap.Hex(q,r);var result=new ArrayList<Integer>();if(b.scene()==null||b.scene().level()==2)result.addAll(SceneRuntime.sides(h,radius(b)));var doors=towns.doors(sceneId(b));for(int i=0;i<doors.size();i++)if(doors.get(i).open()&&doors.get(i).cell().equals(h))result.add(6+i);return result;}
    private boolean within(Encounter b,int q,int r){return distance(0,0,q,r)<=radius(b);}
    private boolean exit(Encounter b,int q,int r){return !sides(b,q,r).isEmpty();}
    private Set<WorldMap.Hex> terrainBlocked(Encounter b){var s=scene(b);if(s==null)return Set.of();var legal=new HashSet<>(s.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).toList());var out=new HashSet<WorldMap.Hex>();for(int q=-s.radius();q<=s.radius();q++)for(int r=-s.radius();r<=s.radius();r++){var h=new WorldMap.Hex(q,r);if(!legal.contains(h))out.add(h);}return out;}
    private void saveScenePositions(Encounter b){var id=sceneId(b);if(id!=null)for(var p:positions(b.id()))sceneRuntime.put(p.id(),id,new WorldMap.Hex(p.q(),p.r()));}
    private java.util.function.BiPredicate<WorldMap.Hex,WorldMap.Hex> passageOpen=(a,b)->true;
    public void passages(java.util.function.BiPredicate<WorldMap.Hex,WorldMap.Hex> value){passageOpen=value;}
    public void world(WorldMap value){world=value;}
    public static List<Integer> sides(int q,int r){
        List<Integer> out=new ArrayList<>();
        int[] values={q,q+r,r,-q,-q-r,-r};
        if(distance(0,0,q,r)>RADIUS)return out;
        for(int i=0;i<6;i++)if(values[i]==RADIUS)out.add(i);
        return out;
    }
    public record Edge(int direction,String name,int q,int r,boolean walkable,UUID battleId,String destination,WorldMap.Hex cell) {}
    private List<Edge> edges(Encounter b){
        var index=world.index();List<Edge> result=new ArrayList<>();
        for(int i=0;i<6;i++){var d=DIRECTIONS.get(i);int q=b.q()+d.q(),r=b.r()+d.r();var tile=index.get(new WorldMap.Hex(q,r));var other=at(b.version(),q,r);
            result.add(new Edge(i,NAMES[i],q,r,(b.scene()==null||b.scene().level()==2)&&tile!=null&&tile.walkable()&&passageOpen.test(new WorldMap.Hex(b.q(),b.r()),new WorldMap.Hex(q,r)),other==null?null:other.id(),tile==null?"世界边界":tile.place()!=null?tile.place().name():switch(tile.terrain()){case "forest"->"森林";case "mountain"->"高山";case "river"->"河流";case "ocean"->"海洋";default->"原野";},null));}
        var doors=towns.doors(sceneId(b));for(int i=0;i<doors.size();i++){var d=doors.get(i);var dest=sceneRuntime.map(d.destinationScene());var other=atScene(dest.id());result.add(new Edge(6+i,"门",dest.owner().q(),dest.owner().r(),d.open(),other==null?null:other.id(),d.name(),d.cell()));}
        return result;
    }
    private Encounter atScene(UUID id){return encounters("and scene_id=?",id).stream().findFirst().orElse(null);}
    public void joinResident(UUID actor,long now){var p=sceneRuntime.position(actor);if(p==null||engaged(actor))return;var b=atScene(p.sceneId());if(b!=null){characters.requireAlive(actor);if(sceneRuntime.occupied(p.sceneId(),actor).contains(p.hex())||b.scene().cells().stream().noneMatch(c->c.hex().equals(p.hex())&&c.walkable()))throw bad("战场入口已满或不可通行");enterAt(actor,b,p.hex(),now);}}
    public boolean blocked(int q,int r){return at(world.version(),q,r)!=null;}
    /** 与 (q,r) 相邻的进行中战斗 id（用于生物增援），没有则 null。 */
    public UUID adjacentEncounter(int q,int r){for(var d:DIRECTIONS){var target=new WorldMap.Hex(q+d.q(),r+d.r());if(!passageOpen.test(new WorldMap.Hex(q,r),target))continue;var e=at(world.version(),target.q(),target.r());if(e!=null)return e.id();}return null;}
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final CharacterService characters;
    private final BattleJournal journal;
    private final EquipmentService equipment;
    private final LootService loot;
    private final CombatActions combatActions;
    private final StatusService statuses;
    private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BattleService.class);
    private final Map<UUID,Long> aiNext=new HashMap<>();
    private final BattleRecovery recovery=new BattleRecovery();
    private final long turnMillis;

    public void recoverAfterRestart(long now){
        for(Encounter battle:encounters("")){
            db.update("update battle_encounters set turn_deadline=? where id=? and active=true",now+turnMillis,battle.id());
            recovery.remember(battle.id(),battle.turn(),battle.round(),now+turnMillis);
        }
        aiNext.clear();
    }

    public record Summary(UUID id,int q,int r,int participants) {}
    public record Actor(UUID accountId,String username,String color,int q,int r,int initiative,int entryRound,boolean online,Integer withdrawDirection,CharacterService.Character character,WeaponRules.Weapon weapon,EquipmentService.Loadout loadout,int initiativeRoll,int initiativeScore,int reactionPoints,boolean shieldRaised,List<String> actions) {}
    public record Intent(UUID attackerId,UUID targetId,int q,int r,String visibility,List<WorldMap.Hex> cells,String weapon,int damage,int minRange,int maxRange,String moveRule,Integer originQ,Integer originR,String kind,boolean ready) {}
    public record Event(long id,String kind,UUID actorId,UUID targetId,Integer q,Integer r,long happenedAt,String actorName,String targetName,Integer amount,Integer blocked,Map<String,Object> details) {}
    private record Encounter(UUID id,String version,int q,int r,int round,UUID turn,int points,boolean attackUsed,long deadline,int nextInitiative,boolean turnStartEdge,SceneMap scene) {}
    private record Position(UUID id,int q,int r,int initiative,int entryRound) {}

    BattleService(JdbcTemplate db,AccountService accounts,CharacterService characters,EquipmentService equipment,LootService loot,CombatActions combatActions,StatusService statuses,@Value("${game.battle.turn-ms:45000}") long turnMillis){
        this.statuses=statuses;
        this.db=db;this.accounts=accounts;this.characters=characters;this.journal=new BattleJournal(db,characters);this.equipment=equipment;this.loot=loot;this.combatActions=combatActions;this.turnMillis=Math.max(5000,turnMillis);
    }

    private List<Encounter> encounters(String where,Object... args){
        return db.query("select id,world_version,world_q,world_r,round_number,turn_account_id,turn_points,attack_used,turn_deadline,next_initiative,turn_start_edge,(select document from scene_documents where id=battle_encounters.scene_id) from battle_encounters where active=true "+where,
            (rs,n)->new Encounter(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getObject(6,UUID.class),rs.getInt(7),rs.getBoolean(8),rs.getLong(9),rs.getInt(10),rs.getBoolean(11),rs.getString(12)==null?null:json.readValue(rs.getString(12),SceneMap.class)),args);
    }
    private Encounter at(String version,int q,int r){return encounters("and world_version=? and world_q=? and world_r=? and (scene_id is null or scene_id in (select id from scene_documents where (document::jsonb->>'level')::int=2))",version,q,r).stream().findFirst().orElse(null);}
    private Encounter forActor(UUID id){return encounters("and id in (select encounter_id from battle_actors where account_id=?)",id).stream().findFirst().orElse(null);}
    private Encounter byId(UUID id){return encounters("and id=?",id).stream().findFirst().orElse(null);}
    private List<Position> positions(UUID id){
        return db.query("select account_id,q,r,initiative,entry_round from battle_actors where encounter_id=? order by initiative",
            (rs,n)->new Position(rs.getObject(1,UUID.class),rs.getInt(2),rs.getInt(3),rs.getInt(4),rs.getInt(5)),id);
    }
    private Position position(UUID battle,UUID user){return positions(battle).stream().filter(p->p.id().equals(user)).findFirst().orElse(null);}
    private static int distance(int aq,int ar,int bq,int br){return Math.max(Math.max(Math.abs(aq-bq),Math.abs(ar-br)),Math.abs(aq+ar-bq-br));}
    private static boolean within(int q,int r){return distance(0,0,q,r)<=RADIUS;}
    private static boolean exit(int q,int r){return !sides(q,r).isEmpty();}
    private static RuntimeException bad(String message){return AccountService.bad(message);}
    private void event(UUID battle,String kind,UUID actor,UUID target,Integer q,Integer r,long now){
        journal.event(battle,kind,actor,target,q,r,now);
    }
    private void detailLast(UUID battle,Map<String,Object> details){journal.detailLast(battle,details);}
    public Object publicCurrent(UUID viewer,Set<UUID> online){
        return BattleJournal.publicView(current(viewer,online),viewer,true);
    }
    private void event(UUID battle,String kind,UUID actor,UUID target,Integer q,Integer r,long now,Integer amount,Integer blocked){
        journal.event(battle,kind,actor,target,q,r,now,amount,blocked);
    }
    private List<Event> events(UUID battle,int limit){return journal.events(battle,limit);}
    public Object report(UUID actor){
        var b=forActor(actor);return journal.report(actor,b==null?null:b.id());
    }
    public Object publicReport(UUID actor){
        return BattleJournal.publicView(report(actor),actor,false);
    }

    public boolean engaged(UUID user){return forActor(user)!=null;}
    public UUID currentId(UUID user){Encounter b=forActor(user);return b==null?null:b.id();}
    public Set<UUID> actorIds(){return new HashSet<>(db.query("select a.account_id from battle_actors a join battle_encounters b on b.id=a.encounter_id where b.active=true",
        (rs,n)->rs.getObject(1,UUID.class)));}
    public List<Summary> summaries(String version){
        return db.query("select b.id,b.world_q,b.world_r,count(a.account_id) from battle_encounters b join battle_actors a on a.encounter_id=b.id where b.active=true and b.world_version=? and (b.scene_id is null or b.scene_id in (select id from scene_documents where (document::jsonb->>'level')::int=2)) group by b.id,b.world_q,b.world_r",
            (rs,n)->new Summary(rs.getObject(1,UUID.class),rs.getInt(2),rs.getInt(3),rs.getInt(4)),version);
    }
    public Object current(UUID viewer,Set<UUID> online){
        Encounter b=forActor(viewer);
        if(b==null)return Map.of("active",false);
        List<Actor> actors=db.query("select a.account_id,c.username,c.color,a.q,a.r,a.initiative,a.entry_round,a.withdraw_direction,a.initiative_roll,a.initiative_score,a.reaction_points,a.shield_raised from battle_actors a left join accounts c on c.id=a.account_id where a.encounter_id=? order by a.initiative",
            (rs,n)->{UUID id=rs.getObject(1,UUID.class);var c=characters.get(id);var l=characters.loadout(id);return new Actor(id,law.displayName(id,c.name()),Objects.requireNonNullElse(rs.getString(3),"#9aa69b"),rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),(online.contains(id)||c.npc()),(Integer)rs.getObject(8),c,l.weapon(),l,rs.getInt(9),rs.getInt(10),rs.getInt("reaction_points"),rs.getBoolean("shield_raised"),id.equals(viewer)?actions(c,l):List.of());},b.id());
        List<Intent> intents=intents(b.id()).stream().filter(i->i.visibility().equals("public")||i.attackerId().equals(viewer)).toList();
        List<Event> events=events(b.id(),32);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("active",true);out.put("id",b.id());out.put("worldQ",b.q());out.put("worldR",b.r());out.put("radius",radius(b));
        var scene=scene(b);if(scene!=null){out.put("sceneId",scene.id());out.put("sceneLevel",scene.level());out.put("cells",scene.cells());}
        out.put("round",b.round());out.put("turnAccountId",b.turn());out.put("turnPoints",b.points());out.put("turnDeadline",b.deadline());
        out.put("attackUsed",b.attackUsed());out.put("actors",actors);out.put("intents",intents);out.put("events",events);out.put("edges",edges(b));out.put("turnStartEdge",b.turnStartEdge());
        out.put("actionOptions",actionOptions(viewer));
        var stateMap=new LinkedHashMap<UUID,Object>();for(var p:positions(b.id()))stateMap.put(p.id(),statuses.list(p.id()));out.put("statuses",stateMap);
        return out;
    }
    private List<String> actions(CharacterService.Character c,EquipmentService.Loadout l){
        List<String> out=new ArrayList<>();
        if(c.alive()){out.add("move");if(l.canAttack()&&equipment.projectile(l.weapon().code())==null)out.add("attack");out.add("endTurn");}
        if(l.canGuard())out.add("guard");
        if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from inventories where character_id=? and item_code='bandage' and quantity>0)",Boolean.class,c.id())))out.add("bandage");
        if(c.kind().equals("player"))out.add("rescue");
        out.add("withdraw");return out;
    }

    public UUID start(UUID actor,UUID target,String version,WorldMap world,long now){
        if(!world.version().equals(version))throw bad("世界已更新，请刷新地图");
        if(actor.equals(target))throw bad("不能向自己发起战斗");
        var a=characters.get(actor);var t=characters.get(target);
        characters.requireAlive(actor);
        if((!t.alive()&&!t.life().equals("down"))||t.protectedUntil()>now)throw bad("目标当前不能参战");
        var ap=sceneRuntime.position(actor);var tp=sceneRuntime.position(target);
        if(ap==null&&(a.q()!=t.q()||a.r()!=t.r()))throw bad("只能向同一世界格的角色发起战斗");
        if((ap==null)!=(tp==null)||ap!=null&&!ap.sceneId().equals(tp.sceneId()))throw bad("目标不在同一实际场景");
        for(var c:List.of(a,t))if(!c.npc()&&(!accounts.get(c.id()).approved()||db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,c.id())==0))throw bad("角色尚未进入世界");
        if(forActor(actor)!=null||forActor(target)!=null)throw bad("角色已经在战斗中");
        UUID id=UUID.randomUUID();
        var actualScene=ap==null?sceneRuntime.outdoor(world,a.hex()):sceneRuntime.map(ap.sceneId());
        if(atScene(actualScene.id())!=null)throw bad("此场景已有战斗，请选择加入");
        if(ap==null){var occupied=sceneRuntime.occupied(actualScene.id(),actor);var starts=new ArrayList<>(actualScene.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->SceneRuntime.sides(h,actualScene.radius()).isEmpty()&&!occupied.contains(h)).toList());if(starts.size()<2)throw bad("场景内部没有足够合法空位");Collections.shuffle(starts);sceneRuntime.put(actor,actualScene.id(),starts.get(0));sceneRuntime.put(target,actualScene.id(),starts.get(1));}
        db.update("insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline,scene_id) values(?,?,?,?,?,?,?)",id,version,a.q(),a.r(),actor,now+turnMillis,actualScene.id());
        Set<UUID> entered=new HashSet<>(db.query("select id from accounts where approved=true and entered=true",(rs,n)->rs.getObject(1,UUID.class)));
        List<UUID> participants=new ArrayList<>(sceneRuntime.residents(actualScene.id()).stream().map(SceneRuntime.Position::characterId).filter(u->{var c=characters.get(u);return (c.npc()||entered.contains(u))&&(c.alive()||c.life().equals("down"));}).toList());
        Map<UUID,Integer> rolls=new HashMap<>();participants.forEach(u->rolls.put(u,1+new java.security.SecureRandom().nextInt(20)));
        Collections.shuffle(participants);
        participants.sort(Comparator.<UUID>comparingInt(u->rolls.get(u)+Math.floorDiv(characters.get(u).agility()-10,2)).reversed().thenComparing(Comparator.<UUID>comparingInt(u->characters.get(u).agility()).reversed()));
        characters.hostile(actor);
        for(int i=0;i<participants.size();i++){UUID user=participants.get(i);if(engaged(user))throw bad("同格角色已在其他战斗中");var cell=sceneRuntime.position(user).hex();
            db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?,?,?,1)",id,user,cell.q(),cell.r(),i);}
        for(UUID user:participants){production.cancel(user);characters.cancelTimer(user);db.update("update battle_actors set initiative_roll=?,initiative_score=? where encounter_id=? and account_id=?",rolls.get(user),rolls.get(user)+Math.floorDiv(characters.get(user).agility()-10,2),id,user);}
        var firstPosition=sceneRuntime.position(participants.getFirst());db.update("update battle_encounters set next_initiative=?,turn_account_id=?,turn_start_edge=? where id=?",participants.size(),participants.getFirst(),exit(byId(id),firstPosition.q(),firstPosition.r()),id);
        event(id,"start",actor,target,null,null,now);
        for(UUID participant:participants)db.update("insert into battle_members values(?,?) on conflict do nothing",id,participant);
        loot.enterBattle(id,version,a.q(),a.r());
        if(tickStatuses(id,participants.getFirst(),now))advanceTurn(byId(id),0,Set.of(),now);
        return id;
    }

    public UUID join(UUID actor,UUID id,String version,WorldMap world,long now){
        if(!world.version().equals(version))throw bad("世界已更新，请刷新地图");
        Encounter b=byId(id);
        if(b==null)throw bad("战斗已结束");
        if(b.scene()!=null&&b.scene().level()==3)throw bad("室内战斗必须通过对应门加入");
        characters.requireAlive(actor);characters.hostile(actor);
        if(forActor(actor)!=null)throw bad("你已经在战斗中");
        var c=characters.get(actor);
        int gap=distance(c.q(),c.r(),b.q(),b.r());
        if(gap>1)throw bad("请先到达战斗格或相邻格");
        if(gap==1&&!passageOpen.test(c.hex(),new WorldMap.Hex(b.q(),b.r())))throw bad("该方向通道已封闭");
        var local=sceneRuntime.position(actor);
        if(local!=null&&(gap!=1||!sidesForWorldCross(local,new WorldMap.Hex(b.q(),b.r()))))throw bad("请先走到对应边缘再参战");
        if(!c.npc()){
            var a=accounts.get(actor);
            if(!a.approved())throw bad("请先到达战斗格旁边，再选择参战");
            if(db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,actor)==0)throw bad("请先进入世界");
        }
        int direction=gap==0?-1:direction(c.q()-b.q(),c.r()-b.r());
        enter(actor,b,direction,now);
        characters.relocate(actor,b.q(),b.r());
        return id;
    }
    public void rejoinAtCell(UUID actor,long now){
        var c=characters.get(actor);var b=at(world.version(),c.q(),c.r());if(b==null)return;
        for(var e:edges(b))if(e.walkable()&&entryCell(b,e.direction())!=null){enter(actor,b,e.direction(),now);return;}
        throw bad("此格战场入口已满，请稍后复活");
    }
    private static int direction(int q,int r){return DIRECTIONS.indexOf(new WorldMap.Hex(q,r));}
    private boolean sidesForWorldCross(SceneRuntime.Position p,WorldMap.Hex destination){var s=sceneRuntime.map(p.sceneId());if(s.level()!=2)return false;int d=direction(destination.q()-s.owner().q(),destination.r()-s.owner().r());return SceneRuntime.sides(p.hex(),s.radius()).contains(d);}
    private WorldMap.Hex entryCell(Encounter b,int direction){
        Set<String> occupied=new HashSet<>();positions(b.id()).forEach(p->occupied.add(p.q()+","+p.r()));
        List<WorldMap.Hex> cells=new ArrayList<>();
        var blocked=terrainBlocked(b);int radius=radius(b);
        for(int q=-radius;q<=radius;q++)for(int r=-radius;r<=radius;r++)if((direction<0?!sides(b,q,r).isEmpty():sides(b,q,r).contains(direction))&&!occupied.contains(q+","+r)&&!blocked.contains(new WorldMap.Hex(q,r)))cells.add(new WorldMap.Hex(q,r));
        Collections.shuffle(cells);return cells.isEmpty()?null:cells.getFirst();
    }
    private void enter(UUID actor,Encounter b,int direction,long now){
        production.cancel(actor);
        var spawn=entryCell(b,direction);if(spawn==null)throw bad("该方向的战场入口已满，请稍后再试");
        enterAt(actor,b,spawn,now);
    }
    private void enterAt(UUID actor,Encounter b,WorldMap.Hex spawn,long now){
        production.cancel(actor);characters.cancelTimer(actor);
        var sceneId=sceneId(b);if(sceneId!=null)sceneRuntime.put(actor,sceneId,spawn);else sceneRuntime.leave(actor);
        int roll=1+new java.security.SecureRandom().nextInt(20),score=roll+Math.floorDiv(characters.get(actor).agility()-10,2);
        db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round,initiative_roll,initiative_score) values(?,?,?,?,?,?,?,?)",b.id(),actor,spawn.q(),spawn.r(),b.nextInitiative(),b.round()+1,roll,score);
        db.update("update battle_encounters set next_initiative=next_initiative+1 where id=?",b.id());
        event(b.id(),"join",actor,null,spawn.q(),spawn.r(),now);
        db.update("insert into battle_members values(?,?) on conflict do nothing",b.id(),actor);
    }

    private Encounter requireTurn(UUID actor){
        Encounter b=forActor(actor);
        if(b==null)throw bad("你当前不在战斗中");
        if(!b.turn().equals(actor))throw bad("现在不是你的回合");
        Position p=position(b.id(),actor);
        if(p==null||p.entryRound()>b.round())throw bad("请等待下一轮加入先攻");
        return b;
    }
    private List<Intent> intents(UUID battle){
        return db.query("select attacker_id,target_id,q,r,visibility,cells,weapon,damage,min_range,max_range,move_rule,origin_q,origin_r,intent_kind,ready from battle_intents where encounter_id=?",
            (rs,n)->{int q=rs.getInt(3),r=rs.getInt(4);String cells=rs.getString(6);return new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),q,r,rs.getString(5),cells==null?List.of(new WorldMap.Hex(q,r)):List.of(json.readValue(cells,WorldMap.Hex[].class)),rs.getString(7),rs.getInt(8),rs.getInt(9),rs.getInt(10),rs.getString(11),(Integer)rs.getObject(12),(Integer)rs.getObject(13),rs.getString(14),rs.getBoolean(15));},battle);
    }
    private boolean retains(Intent i,WorldMap.Hex next){return WeaponRules.retainsAttack(i.moveRule(),i.minRange(),i.maxRange(),i.cells(),i.originQ()==null?null:new WorldMap.Hex(i.originQ(),i.originR()),next);}
    private void resolve(UUID battle,Intent intent,long now,String trigger){
        event(battle,"attack-trigger",intent.attackerId(),null,intent.q(),intent.r(),now);detailLast(battle,Map.of("trigger",trigger));
        // Remove first: damage can down/kill an actor and cancel its other pending actions.
        if(db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,intent.attackerId())==0)return;
        boolean hit=false;
        for(var p:new ArrayList<>(positions(battle)))if(!p.id().equals(intent.attackerId())&&intent.cells().contains(new WorldMap.Hex(p.q(),p.r()))){hit(battle,intent.attackerId(),p.id(),p.q(),p.r(),intent.damage(),now,true);hit=true;}
        if(!hit)event(battle,"miss",intent.attackerId(),null,intent.q(),intent.r(),now);
    }
    private void requireStanding(UUID actor){characters.requireAlive(actor);}
    public record ActionOption(CombatActions.Definition definition,String disabledReason,List<UUID> targets,List<WorldMap.Hex> cells) {}
    /** This exact evaluation feeds the player's controls, the AI and the command validator. */
    public List<ActionOption> actionOptions(UUID actor){
        Encounter b=forActor(actor);var profile=characters.get(actor);
        Position from=b==null?null:position(b.id(),actor);
        List<Position> participants=b==null?List.of():positions(b.id());
        List<Intent> pending=b==null?List.of():intents(b.id());
        Intent own=pending.stream().filter(i->i.attackerId().equals(actor)).findFirst().orElse(null);
        var result=new ArrayList<ActionOption>();
        for(var d:combatActions.owned(actor)){
            if(d.effect().startsWith("legacy:"))continue;
            String reason=null;var targets=new ArrayList<UUID>();var cells=new ArrayList<WorldMap.Hex>();
            if(b==null||from==null)reason="请先进入战斗";
            else if(!profile.alive())reason="倒地或灵魂状态不能行动";
            else if(!actor.equals(b.turn())||from.entryRound()>b.round())reason="请等待自己的回合";
            else if(b.points()<d.cost())reason="行动点不足";
            else if(d.item()!=null&&!equipment.has(actor,d.item()))reason="没有可用弹药或道具";
            if(from!=null){
                var origin=new WorldMap.Hex(from.q(),from.r());
                if(d.effect().equals("aim")){
                    for(int q=-radius(b);q<=radius(b);q++)for(int r=-radius(b);r<=radius(b);r++){
                        int distance=distance(from.q(),from.r(),q,r);
                        if(within(b,q,r)&&distance>=d.minRange()&&distance<=d.maxRange())cells.add(new WorldMap.Hex(q,r));
                    }
                }else if(d.effect().equals("heal_self")){
                    if(profile.alive()&&profile.hp()<profile.maxHp())targets.add(actor);
                }else if(d.effect().equals("cure_self")){
                    if(statuses.has(actor,"poison"))targets.add(actor);
                }else if(d.effect().equals("aimed_projectile")&&(own==null||!own.kind().equals("aim")||!own.ready())){
                    if(reason==null)reason="需在瞄准后的下一次行动射击";
                }else for(var p:participants){
                    if(p.id().equals(actor))continue;
                    var target=characters.get(p.id());if(!target.alive()&&!target.life().equals("down"))continue;
                    var h=new WorldMap.Hex(p.q(),p.r());int distance=WeaponRules.distance(origin,h);
                    boolean valid=distance>=d.minRange()&&distance<=d.maxRange();
                    if(d.effect().equals("aimed_projectile"))valid=own!=null&&own.kind().equals("aim")&&own.ready()&&own.cells().contains(h);
                    if(d.effect().equals("interrupt_intent"))valid=valid&&pending.stream().anyMatch(i->i.attackerId().equals(p.id()));
                    if(valid)targets.add(p.id());
                }
            }
            if(reason==null&&targets.isEmpty()&&cells.isEmpty())reason=d.effect().equals("heal_self")?"生命已满":d.effect().equals("cure_self")?"当前没有中毒":"没有符合条件的目标";
            result.add(new ActionOption(d,reason,List.copyOf(targets),List.copyOf(cells)));
        }
        return List.copyOf(result);
    }
    public void action(UUID actor,String actionId,UUID target,Integer q,Integer r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);
        var option=actionOptions(actor).stream().filter(o->o.definition().id().equals(actionId)).findFirst().orElseThrow(()->bad("当前没有这个动作"));
        if(option.disabledReason()!=null)throw bad(option.disabledReason());
        var d=option.definition();var from=position(b.id(),actor);
        if(d.effect().equals("aim")){
            if(q==null||r==null||!option.cells().contains(new WorldMap.Hex(q,r)))throw bad("请选择射程内的瞄准中心");
            var w=characters.weapon(actor);var cells=WeaponRules.aimCells(w,new WorldMap.Hex(from.q(),from.r()),new WorldMap.Hex(q,r),radius(b));
            cancelIntent(b.id(),actor,"cancel",now);
            db.update("insert into battle_intents(encounter_id,attacker_id,q,r,visibility,cells,weapon,damage,min_range,max_range,move_rule,origin_q,origin_r,intent_kind,ready) values(?,?,?,?,'public',?,?,?,?,?,?,?,?,'aim',false)",b.id(),actor,q,r,json.writeValueAsString(cells),w.code(),d.damage(),w.minRange(),w.maxRange(),w.moveRule(),from.q(),from.r());
            characters.hostile(actor);event(b.id(),"aim",actor,null,q,r,now);
        }else{
            if(d.effect().equals("heal_self")||d.effect().equals("cure_self"))target=actor;
            if(target==null||!option.targets().contains(target))throw bad("目标已不满足这个动作的条件");
            var to=position(b.id(),target);
            switch(d.effect()){
                case "cure_self" -> {equipment.consume(actor,d.item(),1);statuses.remove(actor,"poison");event(b.id(),"status-cured",actor,actor,to.q(),to.r(),now);}
                case "heal_self" -> {var c=characters.get(actor);int healed=Math.min(d.damage(),c.maxHp()-c.hp());equipment.consume(actor,d.item(),1);characters.heal(actor,d.damage());event(b.id(),"potion",actor,actor,to.q(),to.r(),now,healed,null);}
                case "interrupt_intent" -> {cancelIntent(b.id(),target,"interrupted",now);law.hostile(actor,target,"非法敌对打断",now);characters.hostile(actor);event(b.id(),"interrupt",actor,target,to.q(),to.r(),now);}
                case "projectile","aimed_projectile" -> {
                    equipment.consume(actor,d.item(),1);
                    if(d.effect().equals("aimed_projectile"))db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
                    event(b.id(),"attack-trigger",actor,target,to.q(),to.r(),now);detailLast(b.id(),Map.of("trigger",d.name(),"item",d.item()));
                    characters.hostile(actor);int applied=hit(b.id(),actor,target,to.q(),to.r(),d.damage(),now);
                    String status=equipment.ammoStatus(d.item());
                    if(applied>0&&status!=null&&!Set.of("dead","soul").contains(characters.get(target).life())){
                        boolean refreshed=statuses.apply(target,status,actor);event(b.id(),refreshed?"status-refreshed":"status-added",actor,target,to.q(),to.r(),now);
                    }
                }
                default -> throw bad("尚未支持这个动作效果");
            }
        }
        event(b.id(),"action-cost",actor,null,null,null,now);
        detailLast(b.id(),Map.of("action",d.name(),"actionPoints",d.cost(),"item",Objects.requireNonNullElse(d.item(),""),"quantity",d.item()==null?0:1));
        db.update("update battle_encounters set turn_points=turn_points-? where id=?",d.cost(),b.id());
        if(!finishIfNeeded(b.id(),now)&&b.points()==d.cost())advanceTurn(b,from.initiative(),online,now);
    }
    private void cancelIntent(UUID battle,UUID actor,String reason,long now){
        if(db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,actor)>0)event(battle,reason,actor,null,null,null,now);
    }
    public void step(UUID actor,int q,int r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);Position from=position(b.id(),actor);
        Set<WorldMap.Hex> occupied=new HashSet<>();
        positions(b.id()).stream().filter(p->!p.id().equals(actor)).forEach(p->occupied.add(new WorldMap.Hex(p.q(),p.r())));
        occupied.addAll(terrainBlocked(b));
        var route=BattleMovement.path(radius(b),new WorldMap.Hex(from.q(),from.r()),new WorldMap.Hex(q,r),occupied);
        if(route.isEmpty())throw bad("这里无法到达，请选择可移动范围内的空格");
        if(route.size()>b.points())throw bad("行动点不足，请选择白色可移动范围内的格子");
        int currentQ=from.q(),currentR=from.r(),spent=0;
        for(var next:route){
            // Entering a marked cell is harmless; leaving it resolves that stored attack once.
            for(Intent intent:intents(b.id())){
                if(intent.kind().equals("aim"))continue;
                boolean leavingMarkedCell=!intent.attackerId().equals(actor)&&intent.cells().contains(new WorldMap.Hex(currentQ,currentR));
                boolean attackerLeavesRange=intent.attackerId().equals(actor)&&!retains(intent,next);
                if(leavingMarkedCell||attackerLeavesRange)resolve(b.id(),intent,now,leavingMarkedCell?"目标离开预设格":"攻击者移动离开维持范围");
            }
            if(!characters.get(actor).alive())break;
            spent++;
            if(db.update("delete from battle_intents where encounter_id=? and attacker_id=? and intent_kind='aim'",b.id(),actor)>0)event(b.id(),"aim-cancel",actor,null,null,null,now);
            db.update("update battle_actors set q=?,r=? where encounter_id=? and account_id=?",next.q(),next.r(),b.id(),actor);
            var sceneId=sceneId(b);if(sceneId!=null)sceneRuntime.put(actor,sceneId,next);
            event(b.id(),"move",actor,null,next.q(),next.r(),now);detailLast(b.id(),Map.of("actionPoints",1));
            currentQ=next.q();currentR=next.r();
        }
        db.update("update battle_encounters set turn_points=turn_points-? where id=?",spent,b.id());
        if(!finishIfNeeded(b.id(),now)&&(b.points()==spent||!characters.get(actor).alive()))advanceTurn(b,from.initiative(),online,now);
    }
    public void attack(UUID actor,UUID target,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position to=position(b.id(),target);
        if(to==null)throw bad("请选择相邻战场格子");
        attackCell(actor,to.q(),to.r(),online,now);
    }
    public void attackCell(UUID actor,int q,int r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);Position from=position(b.id(),actor);
        if(!characters.loadout(actor).canAttack())throw bad("当前装备无法普通攻击");
        var weapon=characters.weapon(actor);
        if(equipment.projectile(weapon.code())!=null)throw bad("远程武器请选择速射或瞄准");
        var cells=WeaponRules.battlefieldCells(weapon,new WorldMap.Hex(from.q(),from.r()),new WorldMap.Hex(q,r),radius(b));
        if(cells.isEmpty())throw bad("请选择武器可攻击的完整范围");
        Intent previous=intents(b.id()).stream().filter(i->i.attackerId().equals(actor)).findFirst().orElse(null);
        if(previous!=null&&previous.q()==q&&previous.r()==r)return;
        if(b.points()<weapon.cost())throw bad("本回合战术点不足");
        for(var p:positions(b.id()))if(!p.id().equals(actor)&&cells.contains(new WorldMap.Hex(p.q(),p.r())))law.hostile(actor,p.id(),"非法预设攻击",now);
        characters.hostile(actor);
        if(previous!=null){
            db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
            event(b.id(),"cancel",actor,null,previous.q(),previous.r(),now);
        }
        db.update("insert into battle_intents(encounter_id,attacker_id,target_id,q,r,visibility,cells,weapon,damage,min_range,max_range,move_rule,origin_q,origin_r) values(?,?,null,?,?,'public',?,?,?,?,?,?,?,?)",b.id(),actor,q,r,json.writeValueAsString(cells),weapon.code(),weapon.damage(),weapon.minRange(),weapon.maxRange(),weapon.moveRule(),from.q(),from.r());
        db.update("update battle_encounters set turn_points=turn_points-?,attack_used=true where id=?",weapon.cost(),b.id());
        event(b.id(),"mark",actor,null,q,r,now);detailLast(b.id(),Map.of("actionPoints",weapon.cost(),"weapon",weapon.name()));
        if(b.points()==weapon.cost())advanceTurn(b,from.initiative(),online,now);
    }
    public void endTurn(UUID actor,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);
        advanceTurn(b,position(b.id(),actor).initiative(),online,now);
    }
    public LootService.Context lootContext(UUID actor,String version){
        Encounter b=forActor(actor);if(b==null)throw bad("战斗已结束，请回到大世界");
        var p=position(b.id(),actor);return new LootService.Context(actor,version,b.q(),b.r(),b.id(),p.q(),p.r());
    }
    public void requireLootAction(UUID actor){requireStanding(actor);if(requireTurn(actor).points()<2)throw bad("需要2行动点");}
    public void payLootAction(UUID actor,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireLootAction(actor);
        db.update("update battle_encounters set turn_points=turn_points-2 where id=?",b.id());
        event(b.id(),"loot",actor,null,null,null,now);
        if(b.points()==2)advanceTurn(b,position(b.id(),actor).initiative(),online,now);
    }
    private void advanceTurn(Encounter b,int after,Set<UUID> online,long now){
        db.update("update handovers set state='cancelled' where state='pending' and battle_id=?",b.id());
        recovery.forget(b.id());
        if(db.update("delete from battle_intents where encounter_id=? and attacker_id=? and intent_kind='aim' and ready=true",b.id(),b.turn())>0)event(b.id(),"aim-expired",b.turn(),null,null,null,now);
        List<Position> all=positions(b.id());
        if(finishIfNeeded(b.id(),now))return;
        int round=b.round();
        final int currentRound=round;
        Position next=all.stream().filter(p->p.initiative()>after&&p.entryRound()<=currentRound).findFirst().orElse(null);
        if(next==null){round++;sortInitiative(b.id());all=positions(b.id());final int newRound=round;next=all.stream().filter(p->p.entryRound()<=newRound).findFirst().orElse(null);}
        if(next==null)throw new IllegalStateException("Battle has no eligible actor");
        db.update("update battle_encounters set round_number=?,turn_account_id=?,turn_points=?,attack_used=false,turn_deadline=?,turn_start_edge=? where id=?",
            round,next.id(),characters.get(next.id()).alive()?TURN_POINTS:0,now+turnMillis,exit(b,next.q(),next.r()),b.id());
        db.update("update battle_actors set reaction_points=0,shield_raised=false where encounter_id=? and account_id=?",b.id(),next.id());
        db.update("update battle_actors set handed_over=false,sprinted=false where encounter_id=? and account_id=?",b.id(),next.id());
        event(b.id(),"turn",next.id(),null,null,null,now);
        if(tickStatuses(b.id(),next.id(),now)){
            if(!finishIfNeeded(b.id(),now))advanceTurn(byId(b.id()),next.initiative(),online,now);
            return;
        }
        Integer withdrawal=db.queryForObject("select withdraw_direction from battle_actors where encounter_id=? and account_id=?",Integer.class,b.id(),next.id());
        if(withdrawal!=null){
            if(depart(next.id(),byId(b.id()),withdrawal,online,now))return;
            db.update("update battle_actors set withdraw_direction=null where encounter_id=? and account_id=?",b.id(),next.id());
            event(b.id(),"withdraw-blocked",next.id(),null,null,null,now);
        }

        for(Intent intent:intents(b.id()))if(intent.attackerId().equals(next.id())){
            if(intent.kind().equals("aim")){
                db.update("update battle_intents set ready=true where encounter_id=? and attacker_id=?",b.id(),next.id());
                event(b.id(),"aim-ready",next.id(),null,intent.q(),intent.r(),now);
            }else if(online.contains(next.id())||characters.get(next.id()).npc())resolve(b.id(),intent,now,"攻击者下一次行动开始");
            else{
                event(b.id(),"cancel",next.id(),null,intent.q(),intent.r(),now);
                db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),next.id());
            }
        }
    }
    private boolean tickStatuses(UUID battle,UUID actor,long now){
        for(var status:statuses.list(actor)){
            String result=characters.damage(actor,status.damage(),now);
            event(battle,"status-tick",status.sourceId(),actor,null,null,now,status.damage(),0);
            if(result.equals("death")){
                event(battle,"status-death",status.sourceId(),actor,null,null,now);
                db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,actor);
                db.update("delete from battle_actors where encounter_id=? and account_id=?",battle,actor);
                return true;
            }
            statuses.elapsed(actor,status.code());
            if(!statuses.has(actor,status.code()))event(battle,"status-expired",actor,actor,null,null,now);
            if(result.equals("down")){
                db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,actor);
                event(battle,"down",status.sourceId(),actor,null,null,now);
            }
        }
        if(!characters.get(actor).alive())db.update("update battle_encounters set turn_points=0 where id=?",battle);
        return false;
    }
    private int hit(UUID battle,UUID attacker,UUID target,int q,int r,int damage,long now){return hit(battle,attacker,target,q,r,damage,now,false);}
    private int hit(UUID battle,UUID attacker,UUID target,int q,int r,int damage,long now,boolean weaponAttack){
        law.hostile(attacker,target,"非法攻击",now);
        var defense=characters.loadout(target);
        int piercing=weaponAttack?db.queryForObject("select coalesce((select armor_piercing from item_definitions where code=?),0)",Integer.class,characters.weapon(attacker).code()):0;
        int afterArmor=Math.max(1,damage-Math.max(0,defense.armor()-piercing));
        int effective=effectiveDamage(battle,target,damage,piercing);
        var priorStatuses=statuses.list(target);
        String result=effective<=0?"hit":characters.damage(target,effective,now);
        if(result.equals("protected"))return 0;
        if(result.equals("down")||result.equals("death")){db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,target);event(battle,result,attacker,target,q,r,now);}
        if(result.equals("death")){db.update("delete from battle_actors where encounter_id=? and account_id=?",battle,target);for(var s:priorStatuses){event(battle,"status-removed",attacker,target,q,r,now);detailLast(battle,Map.of("status",s.name(),"reason","死亡"));}}
        event(battle,"attack",attacker,target,q,r,now,effective,Math.max(0,damage-effective));
        detailLast(battle,Map.of("baseDamage",damage,"attributeBonus",0,"armorReduction",Math.max(0,damage-afterArmor),"shieldReduction",Math.max(0,afterArmor-effective),"finalDamage",effective,"weapon",characters.weapon(attacker).name()));
        if(db.update("update battle_actors set withdraw_direction=null where encounter_id=? and account_id=? and withdraw_direction is not null",battle,target)>0)
            event(battle,"withdraw-interrupted",target,attacker,q,r,now);
        return effective;
    }
    private int effectiveDamage(UUID battle,UUID target,int damage,int piercing){
        if(damage<=0)return 0;
        var loadout=characters.loadout(target);
        int afterArmor=Math.max(0,damage-Math.max(0,loadout.armor()-piercing));
        if(damage>0&&afterArmor==0)afterArmor=1;
        Integer rp=db.queryForObject("select reaction_points from battle_actors where encounter_id=? and account_id=?",Integer.class,battle,target);
        Boolean raised=db.queryForObject("select shield_raised from battle_actors where encounter_id=? and account_id=?",Boolean.class,battle,target);
        if(Boolean.TRUE.equals(raised)&&rp!=null&&rp>0&&loadout.canGuard()){
            int guarded=Math.max(loadout.guardMinDamage(),afterArmor-loadout.guardReduction());
            if(guarded<afterArmor){
                db.update("update battle_actors set reaction_points=reaction_points-1 where encounter_id=? and account_id=?",battle,target);
                return guarded;
            }
        }
        return afterArmor;
    }
    public void guard(UUID actor,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);
        var loadout=characters.loadout(actor);if(!loadout.canGuard())throw bad("需要装备盾牌才能举盾");
        Integer rp=db.queryForObject("select reaction_points from battle_actors where encounter_id=? and account_id=?",Integer.class,b.id(),actor);
        int add=Math.min(2-(rp==null?0:rp),b.points());
        if(add<=0)throw bad("反应点已满或行动点不足");
        db.update("update battle_actors set reaction_points=reaction_points+?,shield_raised=true where encounter_id=? and account_id=?",add,b.id(),actor);
        db.update("update battle_encounters set turn_points=turn_points-? where id=?",add,b.id());
        event(b.id(),"guard",actor,null,null,null,now);
    }
    public void cancelGuard(UUID actor,long now){
        Encounter b=requireTurn(actor);
        db.update("update battle_actors set reaction_points=0,shield_raised=false where encounter_id=? and account_id=?",b.id(),actor);
        event(b.id(),"guard-cancel",actor,null,null,null,now);
    }
    public void bandage(UUID actor,UUID target,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);if(target==null)target=actor;
        Position a=position(b.id(),actor),t=position(b.id(),target);
        if(t==null||distance(a.q(),a.r(),t.q(),t.r())>1)throw bad("绷带只能治疗自己或相邻角色");
        if(b.points()<2)throw bad("行动点不足");
        var targetProfile=characters.get(target);if(!targetProfile.alive())throw bad("只能治疗站立存活角色");
        if(targetProfile.hp()>=targetProfile.maxHp())throw bad("目标已满血");
        equipment.consume(actor,"bandage",1);
        law.aid(actor,target,now);
        characters.heal(target,4);
        db.update("update battle_encounters set turn_points=turn_points-2 where id=?",b.id());
        event(b.id(),"bandage",actor,target,t.q(),t.r(),now,Math.min(4,targetProfile.maxHp()-targetProfile.hp()),null);detailLast(b.id(),Map.of("actionPoints",2,"item","bandage","quantity",1));
        if(b.points()==2)advanceTurn(b,a.initiative(),online,now);
    }
    public void equip(UUID actor,String main,String off,Set<UUID> online,long now){
        equip(actor,main,off,characters.loadout(actor).body(),online,now);
    }
    public void equip(UUID actor,String main,String off,String body,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);if(b.points()<2)throw bad("行动点不足");
        equipment.equip(actor,main,off,body);
        db.update("update battle_encounters set turn_points=turn_points-2 where id=?",b.id());
        db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
        db.update("update battle_actors set reaction_points=0,shield_raised=false where encounter_id=? and account_id=?",b.id(),actor);
        event(b.id(),"equip",actor,null,null,null,now);detailLast(b.id(),Map.of("actionPoints",2));
        if(b.points()==2)advanceTurn(b,position(b.id(),actor).initiative(),online,now);
    }
    public void withdraw(UUID actor,int direction,boolean force,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);Position p=position(b.id(),actor);
        if(direction<0||direction>=edges(b).size()||!sides(b,p.q(),p.r()).contains(direction))throw bad("请站在对应方向的外圈格子");
        if(!edges(b).get(direction).walkable())throw bad("该出口已关闭或不可通行");
        if(force){
            if(!b.turnStartEdge()||b.points()!=TURN_POINTS)throw bad("强制撤离需要回合开始已在外圈且保留完整6点");
            if(!depart(actor,b,direction,online,now))throw bad("目的地入口已满，请稍后再试");
        }else{
            db.update("update battle_actors set withdraw_direction=? where encounter_id=? and account_id=?",direction,b.id(),actor);
            event(b.id(),"withdraw",actor,null,p.q(),p.r(),now);
            advanceTurn(b,p.initiative(),online,now);
        }
    }
    private boolean depart(UUID actor,Encounter b,int direction,Set<UUID> online,long now){
        if(direction>=6)return departDoor(actor,b,direction,online,now);
        Edge edge=edges(b).get(direction);if(!edge.walkable())return false;
        Encounter destination=edge.battleId()==null?null:byId(edge.battleId());
        int incoming=(direction+3)%6;
        if(destination!=null&&entryCell(destination,incoming)==null)return false;
        SceneMap nextScene=null;WorldMap.Hex nextCell=null;
        if(sceneId(b)!=null&&destination==null){nextScene=sceneRuntime.outdoor(world,new WorldMap.Hex(edge.q(),edge.r()));nextCell=SceneRuntime.edgeCell(nextScene,incoming,sceneRuntime.occupied(nextScene.id(),actor));if(nextCell==null)return false;}
        Position p=position(b.id(),actor);
        // Leaving the encounter cancels telegraphs; it is not a tactical step out of a marked cell.
        db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
        db.update("delete from battle_actors where encounter_id=? and account_id=?",b.id(),actor);
        characters.relocate(actor,edge.q(),edge.r());
        if(nextScene!=null)sceneRuntime.put(actor,nextScene.id(),nextCell);else if(destination==null)sceneRuntime.leave(actor);
        event(b.id(),"exit",actor,null,p.q(),p.r(),now);
        if(destination!=null)enter(actor,destination,incoming,now);
        if(!finishIfNeeded(b.id(),now))advanceTurn(b,p.initiative(),online,now);
        return true;
    }
    private boolean departDoor(UUID actor,Encounter b,int direction,Set<UUID> online,long now){
        var doors=towns.doors(sceneId(b));int index=direction-6;if(index<0||index>=doors.size())return false;var door=doors.get(index);var pos=position(b.id(),actor);
        if(!door.open()||!door.cell().equals(new WorldMap.Hex(pos.q(),pos.r())))return false;
        var target=sceneRuntime.map(door.destinationScene());var spot=TownService.landing(target,door.destination(),sceneRuntime.occupied(target.id(),actor));if(spot==null)return false;
        db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);db.update("delete from battle_actors where encounter_id=? and account_id=?",b.id(),actor);
        sceneRuntime.put(actor,target.id(),spot);if(target.level()==2)characters.relocate(actor,target.owner().q(),target.owner().r());event(b.id(),"exit",actor,null,pos.q(),pos.r(),now);joinResident(actor,now);
        if(!finishIfNeeded(b.id(),now))advanceTurn(b,pos.initiative(),online,now);return true;
    }
    private void close(UUID id,long now){
        var b=byId(id);if(b!=null)saveScenePositions(b);
        recovery.forget(id);
        loot.closeBattle(id);
        db.update("update battle_encounters set active=false where id=?",id);
        db.update("delete from battle_intents where encounter_id=?",id);
        db.update("delete from battle_actors where encounter_id=?",id);
        event(id,"close",null,null,null,null,now);
        journal.saveReport(id,now);
    }
    public boolean advance(Set<UUID> online,long now){
        boolean changed=false;
        for(Encounter battle:encounters("")){
            if(finishIfNeeded(battle.id(),now)){changed=true;continue;}
            Encounter current=byId(battle.id());if(current==null)continue;
            Position turn=position(current.id(),current.turn());
            if(turn==null){advanceTurn(current,-1,online,now);changed=true;continue;}
            var c=characters.get(current.turn());
            // Timeout applies before the controller (human or AI) is allowed to act.
            if(!c.alive()||current.deadline()<=now||!c.npc()&&!online.contains(c.id())&&!recovery.waitForReconnect(current.id(),c.id(),current.round(),now)){
                if(positions(current.id()).stream().anyMatch(p->online.contains(p.id())||characters.get(p.id()).npc())){advanceTurn(current,turn.initiative(),online,now);changed=true;}
            }else if(c.npc()){
                if(aiNext.getOrDefault(c.id(),0L)<=now){
                    aiNext.put(c.id(),now+700);
                    try{actNpc(current,online,now);}
                    catch(org.springframework.web.server.ResponseStatusException rejected){
                        // A rejected AI command has no human controller to choose another action.
                        // Database/programming errors must still surface.
                        log.warn("NPC command rejected; ending turn. battle={} actor={} reason={}",current.id(),c.id(),rejected.getReason());
                        Encounter remaining=byId(current.id());
                        if(remaining!=null&&remaining.turn().equals(c.id()))endTurn(c.id(),online,now);
                    }
                    changed=true;
                }
            }
        }return changed;
    }
    private void sortInitiative(UUID battle){
        var order=db.query("select a.account_id from battle_actors a join characters c on c.id=a.account_id where encounter_id=? order by a.initiative_score desc,c.agility desc,a.initiative",(r,n)->r.getObject(1,UUID.class),battle);
        db.update("update battle_actors set initiative=initiative+10000 where encounter_id=?",battle);
        for(int i=0;i<order.size();i++)db.update("update battle_actors set initiative=? where encounter_id=? and account_id=?",i,battle,order.get(i));
    }
    private boolean finishIfNeeded(UUID id,long now){
        if(byId(id)==null)return true;
        var all=positions(id);long standing=all.stream().filter(p->characters.get(p.id()).alive()).count();
        boolean opponents=all.stream().anyMatch(a->characters.get(a.id()).alive()&&all.stream().anyMatch(t->npcRelations.enemy(a.id(),t.id())));
        if(all.size()<=1||standing==0||!opponents){close(id,now);return true;}return false;
    }
    public void rescue(UUID actor,UUID target,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);var a=position(b.id(),actor);var t=position(b.id(),target);
        if(t==null||characters.get(target).npc()||distance(a.q(),a.r(),t.q(),t.r())!=1||b.points()!=6)throw bad("救起需要相邻并保留完整6点行动点");
        characters.rescue(target);event(b.id(),"rescue",actor,target,t.q(),t.r(),now);advanceTurn(b,a.initiative(),online,now);
    }
    public void surrender(UUID actor,Set<UUID> online,long now){characters.die(actor,now);remove(actor,online,now);}
    private java.util.function.BiPredicate<UUID,Long> logisticsAction=(actor,now)->false;
    public void logisticsAction(java.util.function.BiPredicate<UUID,Long> action){logisticsAction=action;}
    private void actNpc(Encounter b,Set<UUID> online,long now){
        Position own=position(b.id(),b.turn());if(own==null)return;
        if(logisticsAction.test(own.id(),now))return;
        var from=new WorldMap.Hex(own.q(),own.r());
        List<Position> targets=positions(b.id()).stream().filter(p->npcRelations.enemy(own.id(),p.id())).toList();
        boolean holdsPost=Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from npc_tasks where actor_id=? and post_id is not null and task='idle')",Boolean.class,own.id()));
        if(targets.isEmpty()){if(collectForNpc(b,own,online,now))return;if(!holdsPost&&npcRelations.faction(own.id())!=null)retreatNpc(b,own,online,now);else endTurn(own.id(),online,now);return;}
        var standing=targets.stream().filter(p->characters.get(p.id()).alive()).toList();if(!standing.isEmpty())targets=standing;
        var options=new ArrayList<MonsterBrain.Option>();
        Intent pending=intents(b.id()).stream().filter(i->i.attackerId().equals(own.id())).findFirst().orElse(null);
        var weapon=characters.weapon(own.id());
        var projectile=equipment.projectile(weapon.code());
        boolean ranged=projectile!=null;
        String behavior=db.queryForObject("select behavior from characters where id=?",String.class,own.id());
        int healThreshold=behavior.equals("cautious")?9:behavior.equals("aggressive")?3:6;
        double dangerWeight=behavior.equals("cautious")?3:behavior.equals("aggressive")?0.5:2;
        var traits=db.queryForList("select caution,duty from npc_tasks where actor_id=?",own.id());
        double caution=traits.isEmpty()?1:((Number)traits.getFirst().get("caution")).doubleValue(),duty=traits.isEmpty()?1:((Number)traits.getFirst().get("duty")).doubleValue();
        dangerWeight*=caution;healThreshold=(int)Math.ceil(healThreshold*caution);
        boolean lowAndOut=characters.get(own.id()).hp()<=Math.max(1,(behavior.equals("cautious")?4:behavior.equals("aggressive")?1:2)*caution/duty)&&!equipment.has(own.id(),"healing_potion")&&!equipment.has(own.id(),"bandage");
        if(ranged&&!equipment.has(own.id(),equipment.selectedAmmo(own.id(),projectile.ammoCode()))||lowAndOut){
            retreatNpc(b,own,online,now);return;
        }
        for(var available:actionOptions(own.id()))if(available.disabledReason()==null){
            var definition=available.definition();
            for(UUID targetId:available.targets()){
                if(!targetId.equals(own.id())&&targets.stream().noneMatch(t->t.id().equals(targetId)))continue;
                var t=position(b.id(),targetId);double score=switch(definition.effect()){
                    case "heal_self"->characters.get(own.id()).hp()<=healThreshold?18:0;
                    case "cure_self"->behavior.equals("aggressive")?9:19;
                    case "aimed_projectile"->20;
                    case "interrupt_intent"->12;
                    default->ranged?10:5;
                };
                if(score>0)options.add(new MonsterBrain.Option("shared:"+definition.id(),new WorldMap.Hex(t.q(),t.r()),score));
            }
            if(definition.effect().equals("aim")&&pending==null&&targets.stream().allMatch(t->distance(own.q(),own.r(),t.q(),t.r())>=3)){
                for(var center:available.cells())if(targets.stream().anyMatch(t->distance(center.q(),center.r(),t.q(),t.r())<=1))options.add(new MonsterBrain.Option("shared:aim",center,b.points()<=2?11:3));
            }
        }
        if(!ranged&&characters.loadout(own.id()).canAttack()&&b.points()>=weapon.cost()){
            // Enumerate aim cells, not only targets: a fan can hit a target with its second cell.
            for(int q=-radius(b);q<=radius(b);q++)for(int r=-radius(b);r<=radius(b);r++){
                var aim=new WorldMap.Hex(q,r);
                var cells=WeaponRules.battlefieldCells(weapon,from,aim,radius(b));
                if(cells.isEmpty())continue;
                if(positions(b.id()).stream().anyMatch(p->!p.id().equals(own.id())&&!npcRelations.enemy(own.id(),p.id())&&cells.contains(new WorldMap.Hex(p.q(),p.r()))))continue;
                double score=0;
                for(var t:targets){var h=new WorldMap.Hex(t.q(),t.r());
                    if(cells.contains(h)&&(pending==null||!pending.cells().contains(h)))score+=8+(characters.get(t.id()).hp()<=3?4:0);
                }
                if(score>0)options.add(new MonsterBrain.Option("attack",aim,score));
            }
        }
        if(b.points()>=2){var self=characters.get(own.id());if(self.hp()<self.maxHp()&&equipment.has(own.id(),"bandage"))options.add(new MonsterBrain.Option("heal",from,self.hp()<=healThreshold?14:behavior.equals("aggressive")?0:6));}
        var distances=npcDistances(b,own,targets);
        if(!holdsPost&&b.points()>0)for(var next:from.neighbors())if(within(b,next.q(),next.r())&&!terrainBlocked(b).contains(next)&&positions(b.id()).stream().noneMatch(p->p.q()==next.q()&&p.r()==next.r())){
            if(!distances.containsKey(next))continue;
            double score=0;int nearest=distances.get(next),before=distances.getOrDefault(from,1000);
            score+=(ranged?(Math.abs(before-(behavior.equals("cautious")?4:3))-Math.abs(nearest-(behavior.equals("cautious")?4:3))):(before-nearest))*2-0.4;
            if(pending!=null&&pending.kind().equals("aim"))score-=12;
            if(pending!=null&&pending.kind().equals("melee")&&!retains(pending,next)&&targets.stream().anyMatch(t->pending.cells().contains(new WorldMap.Hex(t.q(),t.r()))))score+=10;
            // Already marked: staying also takes this pending hit. Penalize newly entered
            // threats, rather than making every escape/reposition score negative forever.
            for(var i:intents(b.id()))if(i.kind().equals("melee")&&!i.attackerId().equals(own.id())&&!i.cells().contains(from)&&i.cells().contains(next))score-=i.damage()*dangerWeight;
            if(b.points()<=2&&pending==null&&nearest>1)score-=1;
            options.add(new MonsterBrain.Option("move",next,score));
        }
        var choice=MonsterBrain.choose(options);
        if(choice==null)endTurn(own.id(),online,now);
        else if(choice.action().startsWith("shared:")){var target=positions(b.id()).stream().filter(p->p.q()==choice.cell().q()&&p.r()==choice.cell().r()).findFirst().orElse(null);action(own.id(),choice.action().substring(7),target==null?null:target.id(),choice.cell().q(),choice.cell().r(),online,now);}
        else if(choice.action().equals("attack"))attackCell(own.id(),choice.cell().q(),choice.cell().r(),online,now);
        else if(choice.action().equals("heal"))bandage(own.id(),own.id(),online,now);
        else step(own.id(),choice.cell().q(),choice.cell().r(),online,now);
    }
    private Map<WorldMap.Hex,Integer> npcDistances(Encounter b,Position own,List<Position> targets){
        var occupied=positions(b.id()).stream().filter(p->!p.id().equals(own.id())).toList();
        var blocked=new HashSet<>(terrainBlocked(b));
        occupied.forEach(p->blocked.add(new WorldMap.Hex(p.q(),p.r())));
        var friends=occupied.stream().filter(p->!npcRelations.enemy(own.id(),p.id()))
            .map(p->new WorldMap.Hex(p.q(),p.r())).collect(java.util.stream.Collectors.toSet());
        var weapon=characters.weapon(own.id());
        return NpcCombatNavigation.distances(radius(b),
            targets.stream().map(t->new WorldMap.Hex(t.q(),t.r())).toList(),blocked,friends,weapon,
            equipment.projectile(weapon.code())!=null);
    }
    private boolean collectForNpc(Encounter b,Position own,Set<UUID> online,long now){
        var rows=db.queryForList("select l.id,l.battle_q,l.battle_r from npc_tasks t join loot_containers l on l.id=t.corpse_id where t.actor_id=? and l.battle_id=? and l.carrier_id is null and l.expires_at>?",own.id(),b.id(),now);
        if(rows.isEmpty())return false;var row=rows.getFirst();var goal=new WorldMap.Hex(((Number)row.get("battle_q")).intValue(),((Number)row.get("battle_r")).intValue());var from=new WorldMap.Hex(own.q(),own.r());
        if(from.equals(goal)){
            if(b.points()<2){endTurn(own.id(),online,now);return true;}UUID corpse=(UUID)row.get("id");var context=lootContext(own.id(),b.version());requireLootAction(own.id());
            String action=loot.contents(corpse,context,now).isEmpty()?"carry":"takeAll";loot.execute(context,new LootService.Command(action,corpse,null,null,b.version(),b.id(),null),now);payLootAction(own.id(),online,now);return true;
        }
        var blocked=new HashSet<>(terrainBlocked(b));positions(b.id()).stream().filter(p->!p.id().equals(own.id())&&(characters.get(p.id()).alive()||characters.get(p.id()).life().equals("down"))).forEach(p->blocked.add(new WorldMap.Hex(p.q(),p.r())));
        var route=BattleMovement.path(radius(b),from,goal,blocked);if(route.isEmpty()||b.points()<1){endTurn(own.id(),online,now);return true;}var next=route.get(Math.min(route.size(),b.points())-1);step(own.id(),next.q(),next.r(),online,now);return true;
    }
    private void retreatNpc(Encounter b,Position own,Set<UUID> online,long now){
        db.update("update npc_tasks set task=case when corpse_id is null then 'return' else task end,target_id=null,alarm_id=null where actor_id=?",own.id());
        var from=new WorldMap.Hex(own.q(),own.r());var blocked=new HashSet<>(terrainBlocked(b));
        positions(b.id()).stream().filter(p->!p.id().equals(own.id())).forEach(p->blocked.add(new WorldMap.Hex(p.q(),p.r())));
        WorldMap.Hex destination=null;int best=Integer.MAX_VALUE;
        for(var edge:edges(b))if(edge.walkable()){
            if(edge.cell()!=null&&edge.cell().equals(from)||edge.direction()<6&&sides(b,own.q(),own.r()).contains(edge.direction())){withdraw(own.id(),edge.direction(),b.turnStartEdge()&&b.points()==6,online,now);return;}
            var candidates=scene(b).cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->edge.cell()!=null?h.equals(edge.cell()):sides(b,h.q(),h.r()).contains(edge.direction())).toList();
            for(var h:candidates){var route=BattleMovement.path(radius(b),from,h,blocked);if(!route.isEmpty()&&route.size()<best){best=route.size();destination=route.get(Math.min(route.size(),Math.max(1,b.points()))-1);}}
        }
        if(destination!=null&&b.points()>0)step(own.id(),destination.q(),destination.r(),online,now);else endTurn(own.id(),online,now);
    }
    public boolean resume(UUID actor,long now){
        Encounter b=forActor(actor);
        if(b==null||!b.turn().equals(actor)||b.deadline()>now)return false;
        db.update("update battle_encounters set turn_deadline=? where id=?",now+turnMillis,b.id());
        return true;
    }
    public boolean remove(UUID actor,Set<UUID> online,long now){
        Encounter b=forActor(actor);
        if(b==null)return false;
        Position p=position(b.id(),actor);
        db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
        db.update("delete from battle_actors where encounter_id=? and account_id=?",b.id(),actor);
        event(b.id(),"removed",actor,null,null,null,now);
        if(finishIfNeeded(b.id(),now))return true;
        if(b.turn().equals(actor))advanceTurn(b,p.initiative(),online,now);
        return true;
    }
    public void reset(){
        aiNext.clear();
        for(Encounter b:encounters("")){loot.closeBattle(b.id());db.update("delete from battle_encounters where id=?",b.id());}
    }
}
