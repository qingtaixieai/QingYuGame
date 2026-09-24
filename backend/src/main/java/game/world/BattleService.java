package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Server-owned tactical encounters. WorldService serializes commands and commits them before broadcasting. */
@Service
public class BattleService {
    public static final int RADIUS=6;
    private static final int TURN_POINTS=6,ATTACK_COST=2;
    public static final List<WorldMap.Hex> DIRECTIONS=List.of(new WorldMap.Hex(1,0),new WorldMap.Hex(0,1),new WorldMap.Hex(-1,1),new WorldMap.Hex(-1,0),new WorldMap.Hex(0,-1),new WorldMap.Hex(1,-1));
    private static final String[] NAMES={"东","东南","西南","西","西北","东北"};
    private WorldMap world;
    public void world(WorldMap value){world=value;}
    public static List<Integer> sides(int q,int r){
        List<Integer> out=new ArrayList<>();
        int[] values={q,q+r,r,-q,-q-r,-r};
        if(distance(0,0,q,r)>RADIUS)return out;
        for(int i=0;i<6;i++)if(values[i]==RADIUS)out.add(i);
        return out;
    }
    public record Edge(int direction,String name,int q,int r,boolean walkable,UUID battleId,String destination) {}
    private List<Edge> edges(Encounter b){
        var index=world.index();List<Edge> result=new ArrayList<>();
        for(int i=0;i<6;i++){var d=DIRECTIONS.get(i);int q=b.q()+d.q(),r=b.r()+d.r();var tile=index.get(new WorldMap.Hex(q,r));var other=at(b.version(),q,r);
            result.add(new Edge(i,NAMES[i],q,r,tile!=null&&tile.walkable(),other==null?null:other.id(),tile==null?"世界边界":tile.place()!=null?tile.place().name():switch(tile.terrain()){case "forest"->"森林";case "mountain"->"高山";case "river"->"河流";case "ocean"->"海洋";default->"原野";}));}
        return result;
    }
    public boolean blocked(int q,int r){return at(world.version(),q,r)!=null;}
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final long turnMillis;

    public record Summary(UUID id,int q,int r,int participants) {}
    public record Actor(UUID accountId,String username,String color,int q,int r,int initiative,int entryRound,boolean online,Integer withdrawDirection) {}
    public record Intent(UUID attackerId,UUID targetId,int q,int r,String visibility) {}
    public record Event(long id,String kind,UUID actorId,UUID targetId,Integer q,Integer r,long happenedAt) {}
    private record Encounter(UUID id,String version,int q,int r,int round,UUID turn,int points,boolean attackUsed,long deadline,int nextInitiative,boolean turnStartEdge) {}
    private record Position(UUID id,int q,int r,int initiative,int entryRound) {}

    BattleService(JdbcTemplate db,AccountService accounts,@Value("${game.battle.turn-ms:45000}") long turnMillis){
        this.db=db;this.accounts=accounts;this.turnMillis=Math.max(5000,turnMillis);
    }

    private List<Encounter> encounters(String where,Object... args){
        return db.query("select id,world_version,world_q,world_r,round_number,turn_account_id,turn_points,attack_used,turn_deadline,next_initiative,turn_start_edge from battle_encounters where active=true "+where,
            (rs,n)->new Encounter(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getObject(6,UUID.class),rs.getInt(7),rs.getBoolean(8),rs.getLong(9),rs.getInt(10),rs.getBoolean(11)),args);
    }
    private Encounter at(String version,int q,int r){return encounters("and world_version=? and world_q=? and world_r=?",version,q,r).stream().findFirst().orElse(null);}
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
        db.update("insert into battle_events(encounter_id,kind,actor_id,target_id,q,r,happened_at) values(?,?,?,?,?,?,?)",battle,kind,actor,target,q,r,now);
    }

    public boolean engaged(UUID user){return forActor(user)!=null;}
    public UUID currentId(UUID user){Encounter b=forActor(user);return b==null?null:b.id();}
    public Set<UUID> actorIds(){return new HashSet<>(db.query("select a.account_id from battle_actors a join battle_encounters b on b.id=a.encounter_id where b.active=true",
        (rs,n)->rs.getObject(1,UUID.class)));}
    public List<Summary> summaries(String version){
        return db.query("select b.id,b.world_q,b.world_r,count(a.account_id) from battle_encounters b join battle_actors a on a.encounter_id=b.id where b.active=true and b.world_version=? group by b.id,b.world_q,b.world_r",
            (rs,n)->new Summary(rs.getObject(1,UUID.class),rs.getInt(2),rs.getInt(3),rs.getInt(4)),version);
    }
    public Object current(UUID viewer,Set<UUID> online){
        Encounter b=forActor(viewer);
        if(b==null)return Map.of("active",false);
        List<Actor> actors=db.query("select a.account_id,c.username,c.color,a.q,a.r,a.initiative,a.entry_round,a.withdraw_direction from battle_actors a left join accounts c on c.id=a.account_id where a.encounter_id=? order by a.initiative",
            (rs,n)->new Actor(rs.getObject(1,UUID.class),Objects.requireNonNullElse(rs.getString(2),"旅人"),Objects.requireNonNullElse(rs.getString(3),"#9aa69b"),rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),online.contains(rs.getObject(1,UUID.class)),(Integer)rs.getObject(8)),b.id());
        List<Intent> intents=db.query("select attacker_id,target_id,q,r,visibility from battle_intents where encounter_id=?",
            (rs,n)->new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getInt(4),rs.getString(5)),b.id())
            .stream().filter(i->i.visibility().equals("public")||i.attackerId().equals(viewer)).toList();
        List<Event> events=db.query("select id,kind,actor_id,target_id,q,r,happened_at from battle_events where encounter_id=? order by id desc limit 16",
            (rs,n)->new Event(rs.getLong(1),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),(Integer)rs.getObject(5),(Integer)rs.getObject(6),rs.getLong(7)),b.id());
        Collections.reverse(events);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("active",true);out.put("id",b.id());out.put("worldQ",b.q());out.put("worldR",b.r());out.put("radius",RADIUS);
        out.put("round",b.round());out.put("turnAccountId",b.turn());out.put("turnPoints",b.points());out.put("turnDeadline",b.deadline());
        out.put("attackUsed",b.attackUsed());out.put("actors",actors);out.put("intents",intents);out.put("events",events);out.put("edges",edges(b));out.put("turnStartEdge",b.turnStartEdge());
        return out;
    }

    public UUID start(UUID actor,UUID target,String version,WorldMap world,long now){
        if(!world.version().equals(version))throw bad("世界已更新，请刷新地图");
        if(actor.equals(target))throw bad("不能向自己发起战斗");
        var a=accounts.get(actor);var t=accounts.get(target);
        if(!a.approved()||!t.approved())throw bad("目标未获准进入世界");
        if(a.q()!=t.q()||a.r()!=t.r())throw bad("只能向同一世界格的旅人发起战斗");
        if(db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,actor)==0)throw bad("请先进入世界");
        if(db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,target)==0)throw bad("目标尚未进入世界");
        if(forActor(actor)!=null||forActor(target)!=null)throw bad("角色已经在战斗中");
        if(at(version,a.q(),a.r())!=null)throw bad("此格已有战斗，请选择加入");
        UUID id=UUID.randomUUID();
        db.update("insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline) values(?,?,?,?,?,?)",
            id,version,a.q(),a.r(),actor,now+turnMillis);
        List<UUID> participants=new ArrayList<>(db.query("select id from accounts where approved=true and entered=true and q=? and r=? order by id",(rs,n)->rs.getObject(1,UUID.class),a.q(),a.r()));
        participants.remove(actor);participants.addFirst(actor);
        List<WorldMap.Hex> cells=new ArrayList<>();
        for(int q=-RADIUS+1;q<RADIUS;q++)for(int r=-RADIUS+1;r<RADIUS;r++)if(distance(0,0,q,r)<RADIUS)cells.add(new WorldMap.Hex(q,r));
        if(participants.size()>cells.size())throw bad("此格人数超过战场容量");
        Collections.shuffle(cells);
        for(int i=0;i<participants.size();i++){UUID user=participants.get(i);if(engaged(user))throw bad("同格角色已在其他战斗中");var cell=cells.get(i);
            db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?,?,?,1)",id,user,cell.q(),cell.r(),i);}
        db.update("update battle_encounters set next_initiative=? where id=?",participants.size(),id);
        event(id,"start",actor,target,null,null,now);
        return id;
    }

    public UUID join(UUID actor,UUID id,String version,WorldMap world,long now){
        if(!world.version().equals(version))throw bad("世界已更新，请刷新地图");
        Encounter b=byId(id);
        if(b==null)throw bad("战斗已结束");
        var a=accounts.get(actor);
        if(!a.approved()||distance(a.q(),a.r(),b.q(),b.r())!=1)throw bad("请先到达战斗格旁边，再选择参战");
        if(forActor(actor)!=null)throw bad("你已经在战斗中");
        if(db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,actor)==0)throw bad("请先进入世界");
        int direction=direction(a.q()-b.q(),a.r()-b.r());
        enter(actor,b,direction,now);
        db.update("update accounts set q=?,r=? where id=?",b.q(),b.r(),actor);
        return id;
    }
    private static int direction(int q,int r){return DIRECTIONS.indexOf(new WorldMap.Hex(q,r));}
    private WorldMap.Hex entryCell(Encounter b,int direction){
        Set<String> occupied=new HashSet<>();positions(b.id()).forEach(p->occupied.add(p.q()+","+p.r()));
        List<WorldMap.Hex> cells=new ArrayList<>();
        for(int q=-RADIUS;q<=RADIUS;q++)for(int r=-RADIUS;r<=RADIUS;r++)if(sides(q,r).contains(direction)&&!occupied.contains(q+","+r))cells.add(new WorldMap.Hex(q,r));
        Collections.shuffle(cells);return cells.isEmpty()?null:cells.getFirst();
    }
    private void enter(UUID actor,Encounter b,int direction,long now){
        var spawn=entryCell(b,direction);if(spawn==null)throw bad("该方向的战场入口已满，请稍后再试");
        db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?,?,?,?)",b.id(),actor,spawn.q(),spawn.r(),b.nextInitiative(),b.round()+1);
        db.update("update battle_encounters set next_initiative=next_initiative+1 where id=?",b.id());
        event(b.id(),"join",actor,null,spawn.q(),spawn.r(),now);
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
        return db.query("select attacker_id,target_id,q,r,visibility from battle_intents where encounter_id=?",
            (rs,n)->new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getInt(4),rs.getString(5)),battle);
    }
    private void resolve(UUID battle,Intent intent,long now){
        Position occupant=positions(battle).stream().filter(p->p.q()==intent.q()&&p.r()==intent.r()&&!p.id().equals(intent.attackerId())).findFirst().orElse(null);
        if(occupant!=null)hit(battle,intent.attackerId(),occupant.id(),intent.q(),intent.r(),now);
        else event(battle,"miss",intent.attackerId(),null,intent.q(),intent.r(),now);
        db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,intent.attackerId());
    }
    public void step(UUID actor,int q,int r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position from=position(b.id(),actor);
        Set<WorldMap.Hex> occupied=new HashSet<>();
        positions(b.id()).stream().filter(p->!p.id().equals(actor)).forEach(p->occupied.add(new WorldMap.Hex(p.q(),p.r())));
        var route=BattleMovement.path(RADIUS,new WorldMap.Hex(from.q(),from.r()),new WorldMap.Hex(q,r),occupied);
        if(route.isEmpty())throw bad("这里无法到达，请选择可移动范围内的空格");
        if(route.size()>b.points())throw bad("行动点不足，请选择白色可移动范围内的格子");
        int currentQ=from.q(),currentR=from.r();
        for(var next:route){
            // Entering a marked cell is harmless; leaving it resolves that stored attack once.
            for(Intent intent:intents(b.id())){
                boolean leavingMarkedCell=!intent.attackerId().equals(actor)&&intent.q()==currentQ&&intent.r()==currentR;
                boolean attackerLeavesRange=intent.attackerId().equals(actor)&&distance(next.q(),next.r(),intent.q(),intent.r())>1;
                if(leavingMarkedCell||attackerLeavesRange)resolve(b.id(),intent,now);
            }
            db.update("update battle_actors set q=?,r=? where encounter_id=? and account_id=?",next.q(),next.r(),b.id(),actor);
            event(b.id(),"move",actor,null,next.q(),next.r(),now);
            currentQ=next.q();currentR=next.r();
        }
        db.update("update battle_encounters set turn_points=turn_points-? where id=?",route.size(),b.id());
        if(b.points()==route.size())advanceTurn(b,from.initiative(),online,now);
    }
    public void attack(UUID actor,UUID target,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position to=position(b.id(),target);
        if(to==null)throw bad("请选择相邻战场格子");
        attackCell(actor,to.q(),to.r(),online,now);
    }
    public void attackCell(UUID actor,int q,int r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position from=position(b.id(),actor);
        if(!within(q,r)||distance(from.q(),from.r(),q,r)!=1)throw bad("普通攻击只能预设相邻格");
        if(b.attackUsed())throw bad("本回合已经选择过普通攻击");
        if(b.points()<ATTACK_COST)throw bad("本回合战术点不足");
        db.update("insert into battle_intents(encounter_id,attacker_id,target_id,q,r,visibility) values(?,?,null,?,?,'public')",b.id(),actor,q,r);
        db.update("update battle_encounters set turn_points=turn_points-?,attack_used=true where id=?",ATTACK_COST,b.id());
        event(b.id(),"mark",actor,null,q,r,now);
        if(b.points()==ATTACK_COST)advanceTurn(b,from.initiative(),online,now);
    }
    public void endTurn(UUID actor,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);
        advanceTurn(b,position(b.id(),actor).initiative(),online,now);
    }
    private void advanceTurn(Encounter b,int after,Set<UUID> online,long now){
        List<Position> all=positions(b.id());
        if(all.size()<=1){close(b.id(),now);return;}
        int round=b.round();
        final int currentRound=round;
        Position next=all.stream().filter(p->p.initiative()>after&&p.entryRound()<=currentRound).findFirst().orElse(null);
        if(next==null){round++;final int newRound=round;next=all.stream().filter(p->p.entryRound()<=newRound).findFirst().orElse(null);}
        if(next==null)throw new IllegalStateException("Battle has no eligible actor");
        db.update("update battle_encounters set round_number=?,turn_account_id=?,turn_points=?,attack_used=false,turn_deadline=?,turn_start_edge=? where id=?",
            round,next.id(),TURN_POINTS,now+turnMillis,exit(next.q(),next.r()),b.id());
        event(b.id(),"turn",next.id(),null,null,null,now);
        Integer withdrawal=db.queryForObject("select withdraw_direction from battle_actors where encounter_id=? and account_id=?",Integer.class,b.id(),next.id());
        if(withdrawal!=null){
            if(depart(next.id(),byId(b.id()),withdrawal,online,now))return;
            db.update("update battle_actors set withdraw_direction=null where encounter_id=? and account_id=?",b.id(),next.id());
            event(b.id(),"withdraw-blocked",next.id(),null,null,null,now);
        }

        for(Intent intent:intents(b.id()))if(intent.attackerId().equals(next.id())){
            if(online.contains(next.id()))resolve(b.id(),intent,now);
            else{
                event(b.id(),"cancel",next.id(),null,intent.q(),intent.r(),now);
                db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),next.id());
            }
        }
    }
    private void hit(UUID battle,UUID attacker,UUID target,int q,int r,long now){
        event(battle,"attack",attacker,target,q,r,now);
        if(db.update("update battle_actors set withdraw_direction=null where encounter_id=? and account_id=? and withdraw_direction is not null",battle,target)>0)
            event(battle,"withdraw-interrupted",target,attacker,q,r,now);
    }
    public void withdraw(UUID actor,int direction,boolean force,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position p=position(b.id(),actor);
        if(direction<0||direction>5||!sides(p.q(),p.r()).contains(direction))throw bad("请站在对应方向的外圈格子");
        if(!edges(b).get(direction).walkable())throw bad("该方向的大世界地形不可通行");
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
        Edge edge=edges(b).get(direction);if(!edge.walkable())return false;
        Encounter destination=edge.battleId()==null?null:byId(edge.battleId());
        int incoming=(direction+3)%6;
        if(destination!=null&&entryCell(destination,incoming)==null)return false;
        Position p=position(b.id(),actor);
        // Leaving the encounter cancels telegraphs; it is not a tactical step out of a marked cell.
        db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
        db.update("delete from battle_actors where encounter_id=? and account_id=?",b.id(),actor);
        db.update("update accounts set q=?,r=? where id=?",edge.q(),edge.r(),actor);
        event(b.id(),"exit",actor,null,p.q(),p.r(),now);
        if(destination!=null)enter(actor,destination,incoming,now);
        if(positions(b.id()).size()<=1)close(b.id(),now);else advanceTurn(b,p.initiative(),online,now);
        return true;
    }
    private void close(UUID id,long now){
        db.update("update battle_encounters set active=false where id=?",id);
        db.update("delete from battle_intents where encounter_id=?",id);
        db.update("delete from battle_actors where encounter_id=?",id);
        event(id,"close",null,null,null,null,now);
    }
    public boolean advance(Set<UUID> online,long now){
        boolean changed=false;
        for(Encounter battle:encounters("")){
            List<Position> all=positions(battle.id());
            if(all.size()<=1){close(battle.id(),now);changed=true;continue;}
            if(all.stream().noneMatch(p->online.contains(p.id())))continue;
            // Offline and timed-out actors cannot hold a battle indefinitely.
            for(int n=0;n<=all.size();n++){
                Encounter current=byId(battle.id());
                if(current==null||online.contains(current.turn())&&current.deadline()>now)break;
                Position turn=position(current.id(),current.turn());
                advanceTurn(current,turn==null?-1:turn.initiative(),online,now);
                changed=true;
            }
        }
        return changed;
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
        if(positions(b.id()).size()<=1)close(b.id(),now);
        else if(b.turn().equals(actor))advanceTurn(b,p.initiative(),online,now);
        return true;
    }
    public void reset(){
        for(Encounter b:encounters(""))db.update("delete from battle_encounters where id=?",b.id());
    }
}
