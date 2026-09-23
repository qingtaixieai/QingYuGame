package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Server-owned tactical encounters. WorldService serializes commands and commits them before broadcasting. */
@Service
public class BattleService {
    public static final int RADIUS=4;
    private static final int TURN_POINTS=6,ATTACK_COST=2;
    private static final List<WorldMap.Hex> EXITS=List.of(
        new WorldMap.Hex(4,0),new WorldMap.Hex(0,4),new WorldMap.Hex(-4,4),
        new WorldMap.Hex(-4,0),new WorldMap.Hex(0,-4),new WorldMap.Hex(4,-4));
    private final JdbcTemplate db;
    private final AccountService accounts;
    private final long turnMillis;

    public record Summary(UUID id,int q,int r,int participants) {}
    public record Actor(UUID accountId,String username,String color,int q,int r,int initiative,int entryRound,boolean online) {}
    public record Intent(UUID attackerId,UUID targetId,int q,int r,String visibility) {}
    public record Event(long id,String kind,UUID actorId,UUID targetId,Integer q,Integer r,long happenedAt) {}
    private record Encounter(UUID id,String version,int q,int r,int round,UUID turn,int points,boolean attackUsed,long deadline,int nextInitiative) {}
    private record Position(UUID id,int q,int r,int initiative,int entryRound) {}

    BattleService(JdbcTemplate db,AccountService accounts,@Value("${game.battle.turn-ms:45000}") long turnMillis){
        this.db=db;this.accounts=accounts;this.turnMillis=Math.max(5000,turnMillis);
    }

    private List<Encounter> encounters(String where,Object... args){
        return db.query("select id,world_version,world_q,world_r,round_number,turn_account_id,turn_points,attack_used,turn_deadline,next_initiative from battle_encounters where active=true "+where,
            (rs,n)->new Encounter(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getInt(5),rs.getObject(6,UUID.class),rs.getInt(7),rs.getBoolean(8),rs.getLong(9),rs.getInt(10)),args);
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
    private static boolean exit(int q,int r){return EXITS.stream().anyMatch(h->h.q()==q&&h.r()==r);}
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
        List<Actor> actors=db.query("select a.account_id,c.username,c.color,a.q,a.r,a.initiative,a.entry_round from battle_actors a left join accounts c on c.id=a.account_id where a.encounter_id=? order by a.initiative",
            (rs,n)->new Actor(rs.getObject(1,UUID.class),Objects.requireNonNullElse(rs.getString(2),"旅人"),Objects.requireNonNullElse(rs.getString(3),"#9aa69b"),rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),online.contains(rs.getObject(1,UUID.class))),b.id());
        List<Intent> intents=db.query("select attacker_id,target_id,q,r,visibility from battle_intents where encounter_id=?",
            (rs,n)->new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getInt(4),rs.getString(5)),b.id())
            .stream().filter(i->i.visibility().equals("public")||i.attackerId().equals(viewer)).toList();
        List<Event> events=db.query("select id,kind,actor_id,target_id,q,r,happened_at from battle_events where encounter_id=? order by id desc limit 16",
            (rs,n)->new Event(rs.getLong(1),rs.getString(2),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),(Integer)rs.getObject(5),(Integer)rs.getObject(6),rs.getLong(7)),b.id());
        Collections.reverse(events);
        Map<String,Object> out=new LinkedHashMap<>();
        out.put("active",true);out.put("id",b.id());out.put("worldQ",b.q());out.put("worldR",b.r());out.put("radius",RADIUS);
        out.put("round",b.round());out.put("turnAccountId",b.turn());out.put("turnPoints",b.points());out.put("turnDeadline",b.deadline());
        out.put("attackUsed",b.attackUsed());out.put("actors",actors);out.put("intents",intents);out.put("events",events);out.put("exits",EXITS);
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
        db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?, ?,0,1)",id,actor,-1,0);
        db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?, ?,1,1)",id,target,1,0);
        event(id,"start",actor,target,null,null,now);
        return id;
    }

    public UUID join(UUID actor,UUID id,String version,WorldMap world,long now){
        if(!world.version().equals(version))throw bad("世界已更新，请刷新地图");
        Encounter b=byId(id);
        if(b==null)throw bad("战斗已结束");
        var a=accounts.get(actor);
        if(!a.approved()||a.q()!=b.q()||a.r()!=b.r()||db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,actor)==0)throw bad("只有已进入同一世界格的旅人可以加入");
        if(forActor(actor)!=null)throw bad("你已经在战斗中");
        Set<String> occupied=new HashSet<>();positions(id).forEach(p->occupied.add(p.q()+","+p.r()));
        WorldMap.Hex spawn=edgePositions().stream().filter(h->!occupied.contains(h.q()+","+h.r())).findFirst().orElse(null);
        if(spawn==null)throw bad("战场入口暂时没有空位");
        db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?,?,?,?)",
            id,actor,spawn.q(),spawn.r(),b.nextInitiative(),b.round()+1);
        db.update("update battle_encounters set next_initiative=next_initiative+1 where id=?",id);
        event(id,"join",actor,null,spawn.q(),spawn.r(),now);
        return id;
    }
    private static List<WorldMap.Hex> edgePositions(){
        List<WorldMap.Hex> out=new ArrayList<>();
        for(int q=-RADIUS;q<=RADIUS;q++)for(int r=-RADIUS;r<=RADIUS;r++)
            if(within(q,r)&&distance(0,0,q,r)==RADIUS&&!exit(q,r))out.add(new WorldMap.Hex(q,r));
        out.sort(Comparator.comparingDouble(h->Math.atan2(h.r()*1.5,h.q()+h.r()*.5)));
        return out;
    }

    private Encounter requireTurn(UUID actor){
        Encounter b=forActor(actor);
        if(b==null)throw bad("你当前不在战斗中");
        if(!b.turn().equals(actor))throw bad("现在不是你的回合");
        Position p=position(b.id(),actor);
        if(p==null||p.entryRound()>b.round())throw bad("请等待下一轮加入先攻");
        return b;
    }
    public void step(UUID actor,int q,int r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position from=position(b.id(),actor);
        if(b.points()<1)throw bad("本回合战术点不足");
        if(!within(q,r)||distance(from.q(),from.r(),q,r)!=1)throw bad("请选择相邻的战场格子");
        if(positions(b.id()).stream().anyMatch(p->p.q()==q&&p.r()==r))throw bad("该格已有角色");
        // A marked target leaving its red cell takes the already declared attack now.
        List<Intent> triggered=db.query("select attacker_id,target_id,q,r,visibility from battle_intents where encounter_id=? and target_id=? and q=? and r=?",
            (rs,n)->new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getInt(4),rs.getString(5)),b.id(),actor,from.q(),from.r());
        for(Intent i:triggered){event(b.id(),"attack",i.attackerId(),actor,from.q(),from.r(),now);
            db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),i.attackerId());}
        if(exit(q,r)){
            event(b.id(),"exit",actor,null,q,r,now);
            db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
            db.update("delete from battle_actors where encounter_id=? and account_id=?",b.id(),actor);
            if(positions(b.id()).size()<=1)close(b.id(),now);
            else advanceTurn(b,from.initiative(),online,now);
            return;
        }
        db.update("update battle_actors set q=?,r=? where encounter_id=? and account_id=?",q,r,b.id(),actor);
        db.update("update battle_encounters set turn_points=turn_points-1 where id=?",b.id());
        event(b.id(),"move",actor,null,q,r,now);
        if(b.points()==1)advanceTurn(b,from.initiative(),online,now);
    }
    public void attack(UUID actor,UUID target,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);Position from=position(b.id(),actor),to=position(b.id(),target);
        if(to==null||actor.equals(target))throw bad("请选择另一名参战角色");
        if(distance(from.q(),from.r(),to.q(),to.r())!=1)throw bad("普通攻击只能选择相邻格的角色");
        if(b.attackUsed())throw bad("本回合已经选择过普通攻击");
        if(b.points()<ATTACK_COST)throw bad("本回合战术点不足");
        db.update("insert into battle_intents(encounter_id,attacker_id,target_id,q,r,visibility) values(?,?,?,?,?,'public')",
            b.id(),actor,target,to.q(),to.r());
        db.update("update battle_encounters set turn_points=turn_points-?,attack_used=true where id=?",ATTACK_COST,b.id());
        event(b.id(),"mark",actor,target,to.q(),to.r(),now);
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
        db.update("update battle_encounters set round_number=?,turn_account_id=?,turn_points=?,attack_used=false,turn_deadline=? where id=?",
            round,next.id(),TURN_POINTS,now+turnMillis,b.id());
        event(b.id(),"turn",next.id(),null,null,null,now);
        List<Intent> pending=db.query("select attacker_id,target_id,q,r,visibility from battle_intents where encounter_id=? and attacker_id=?",
            (rs,n)->new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getInt(3),rs.getInt(4),rs.getString(5)),b.id(),next.id());
        for(Intent intent:pending){
            Position target=position(b.id(),intent.targetId());
            if(online.contains(next.id())&&target!=null&&target.q()==intent.q()&&target.r()==intent.r())
                event(b.id(),"attack",next.id(),intent.targetId(),intent.q(),intent.r(),now);
            else if(!online.contains(next.id()))event(b.id(),"cancel",next.id(),intent.targetId(),intent.q(),intent.r(),now);
            db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),next.id());
        }
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
        db.update("delete from battle_intents where encounter_id=? and (attacker_id=? or target_id=?)",b.id(),actor,actor);
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
