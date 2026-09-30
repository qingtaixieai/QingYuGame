package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Server-owned tactical encounters. WorldService serializes commands and commits them before broadcasting. */
@Service
public class BattleService {
    public static final int RADIUS=6;
    private static final int TURN_POINTS=6;
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
    private final CharacterService characters;
    private final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    private final Map<UUID,Long> aiNext=new HashMap<>();
    private final long turnMillis;

    public record Summary(UUID id,int q,int r,int participants) {}
    public record Actor(UUID accountId,String username,String color,int q,int r,int initiative,int entryRound,boolean online,Integer withdrawDirection,CharacterService.Character character,WeaponRules.Weapon weapon,int initiativeRoll,int initiativeScore) {}
    public record Intent(UUID attackerId,UUID targetId,int q,int r,String visibility,List<WorldMap.Hex> cells,String weapon,int damage,int minRange,int maxRange) {}
    public record Event(long id,String kind,UUID actorId,UUID targetId,Integer q,Integer r,long happenedAt) {}
    private record Encounter(UUID id,String version,int q,int r,int round,UUID turn,int points,boolean attackUsed,long deadline,int nextInitiative,boolean turnStartEdge) {}
    private record Position(UUID id,int q,int r,int initiative,int entryRound) {}

    BattleService(JdbcTemplate db,AccountService accounts,CharacterService characters,@Value("${game.battle.turn-ms:45000}") long turnMillis){
        this.db=db;this.accounts=accounts;this.characters=characters;this.turnMillis=Math.max(5000,turnMillis);
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
        List<Actor> actors=db.query("select a.account_id,c.username,c.color,a.q,a.r,a.initiative,a.entry_round,a.withdraw_direction,a.initiative_roll,a.initiative_score from battle_actors a left join accounts c on c.id=a.account_id where a.encounter_id=? order by a.initiative",
            (rs,n)->new Actor(rs.getObject(1,UUID.class),characters.get(rs.getObject(1,UUID.class)).name(),Objects.requireNonNullElse(rs.getString(3),"#9aa69b"),rs.getInt(4),rs.getInt(5),rs.getInt(6),rs.getInt(7),(online.contains(rs.getObject(1,UUID.class))||characters.get(rs.getObject(1,UUID.class)).npc()),(Integer)rs.getObject(8),characters.get(rs.getObject(1,UUID.class)),characters.weapon(rs.getObject(1,UUID.class)),rs.getInt(9),rs.getInt(10)),b.id());
        List<Intent> intents=intents(b.id()).stream().filter(i->i.visibility().equals("public")||i.attackerId().equals(viewer)).toList();
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
        var a=characters.get(actor);var t=characters.get(target);
        characters.requireAlive(actor);
        if((!t.alive()&&!t.life().equals("down"))||t.protectedUntil()>now)throw bad("目标当前不能参战");
        if(a.q()!=t.q()||a.r()!=t.r())throw bad("只能向同一世界格的角色发起战斗");
        for(var c:List.of(a,t))if(!c.npc()&&(!accounts.get(c.id()).approved()||db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,c.id())==0))throw bad("角色尚未进入世界");
        if(forActor(actor)!=null||forActor(target)!=null)throw bad("角色已经在战斗中");
        if(at(version,a.q(),a.r())!=null)throw bad("此格已有战斗，请选择加入");
        UUID id=UUID.randomUUID();
        db.update("insert into battle_encounters(id,world_version,world_q,world_r,turn_account_id,turn_deadline) values(?,?,?,?,?,?)",id,version,a.q(),a.r(),actor,now+turnMillis);
        Set<UUID> entered=new HashSet<>(db.query("select id from accounts where approved=true and entered=true",(rs,n)->rs.getObject(1,UUID.class)));
        List<UUID> participants=new ArrayList<>(characters.all().stream().filter(c->(c.npc()||entered.contains(c.id()))&&(c.alive()||c.life().equals("down"))&&c.q()==a.q()&&c.r()==a.r()&&(c.protectedUntil()<=now||c.id().equals(actor))).map(CharacterService.Character::id).toList());
        Map<UUID,Integer> rolls=new HashMap<>();participants.forEach(u->rolls.put(u,1+new java.security.SecureRandom().nextInt(20)));
        Collections.shuffle(participants);
        participants.sort(Comparator.<UUID>comparingInt(u->rolls.get(u)+Math.floorDiv(characters.get(u).agility()-10,2)).reversed().thenComparing(Comparator.<UUID>comparingInt(u->characters.get(u).agility()).reversed()));
        characters.hostile(actor);
        List<WorldMap.Hex> cells=new ArrayList<>();
        for(int q=-RADIUS+1;q<RADIUS;q++)for(int r=-RADIUS+1;r<RADIUS;r++)if(distance(0,0,q,r)<RADIUS)cells.add(new WorldMap.Hex(q,r));
        if(participants.size()>cells.size())throw bad("此格人数超过战场容量");
        Collections.shuffle(cells);
        for(int i=0;i<participants.size();i++){UUID user=participants.get(i);if(engaged(user))throw bad("同格角色已在其他战斗中");var cell=cells.get(i);
            db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round) values(?,?,?,?,?,1)",id,user,cell.q(),cell.r(),i);}
        for(UUID user:participants){characters.cancelTimer(user);db.update("update battle_actors set initiative_roll=?,initiative_score=? where encounter_id=? and account_id=?",rolls.get(user),rolls.get(user)+Math.floorDiv(characters.get(user).agility()-10,2),id,user);}
        db.update("update battle_encounters set next_initiative=?,turn_account_id=? where id=?",participants.size(),participants.getFirst(),id);
        event(id,"start",actor,target,null,null,now);
        return id;
    }

    public UUID join(UUID actor,UUID id,String version,WorldMap world,long now){
        if(!world.version().equals(version))throw bad("世界已更新，请刷新地图");
        Encounter b=byId(id);
        if(b==null)throw bad("战斗已结束");
        characters.requireAlive(actor);characters.hostile(actor);
        var a=accounts.get(actor);
        if(!a.approved()||distance(a.q(),a.r(),b.q(),b.r())!=1)throw bad("请先到达战斗格旁边，再选择参战");
        if(forActor(actor)!=null)throw bad("你已经在战斗中");
        if(db.queryForObject("select count(*) from accounts where id=? and entered=true",Integer.class,actor)==0)throw bad("请先进入世界");
        int direction=direction(a.q()-b.q(),a.r()-b.r());
        enter(actor,b,direction,now);
        db.update("update accounts set q=?,r=? where id=?",b.q(),b.r(),actor);
        return id;
    }
    public void rejoinAtCell(UUID actor,long now){
        var c=characters.get(actor);var b=at(world.version(),c.q(),c.r());if(b==null)return;
        for(var e:edges(b))if(e.walkable()&&entryCell(b,e.direction())!=null){enter(actor,b,e.direction(),now);return;}
        throw bad("此格战场入口已满，请稍后复活");
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
        int roll=1+new java.security.SecureRandom().nextInt(20),score=roll+Math.floorDiv(characters.get(actor).agility()-10,2);
        db.update("insert into battle_actors(encounter_id,account_id,q,r,initiative,entry_round,initiative_roll,initiative_score) values(?,?,?,?,?,?,?,?)",b.id(),actor,spawn.q(),spawn.r(),b.nextInitiative(),b.round()+1,roll,score);
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
        return db.query("select attacker_id,target_id,q,r,visibility,cells,weapon,damage,min_range,max_range from battle_intents where encounter_id=?",
            (rs,n)->{int q=rs.getInt(3),r=rs.getInt(4);String cells=rs.getString(6);return new Intent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),q,r,rs.getString(5),cells==null?List.of(new WorldMap.Hex(q,r)):List.of(json.readValue(cells,WorldMap.Hex[].class)),rs.getString(7),rs.getInt(8),rs.getInt(9),rs.getInt(10));},battle);
    }
    private void resolve(UUID battle,Intent intent,long now){
        // Remove first: damage can down/kill an actor and cancel its other pending actions.
        if(db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,intent.attackerId())==0)return;
        boolean hit=false;
        for(var p:new ArrayList<>(positions(battle)))if(!p.id().equals(intent.attackerId())&&intent.cells().contains(new WorldMap.Hex(p.q(),p.r()))){hit(battle,intent.attackerId(),p.id(),p.q(),p.r(),intent.damage(),now);hit=true;}
        if(!hit)event(battle,"miss",intent.attackerId(),null,intent.q(),intent.r(),now);
    }
    private void requireStanding(UUID actor){characters.requireAlive(actor);}
    public void step(UUID actor,int q,int r,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);Position from=position(b.id(),actor);
        Set<WorldMap.Hex> occupied=new HashSet<>();
        positions(b.id()).stream().filter(p->!p.id().equals(actor)).forEach(p->occupied.add(new WorldMap.Hex(p.q(),p.r())));
        var route=BattleMovement.path(RADIUS,new WorldMap.Hex(from.q(),from.r()),new WorldMap.Hex(q,r),occupied);
        if(route.isEmpty())throw bad("这里无法到达，请选择可移动范围内的空格");
        if(route.size()>b.points())throw bad("行动点不足，请选择白色可移动范围内的格子");
        int currentQ=from.q(),currentR=from.r(),spent=0;
        for(var next:route){
            // Entering a marked cell is harmless; leaving it resolves that stored attack once.
            for(Intent intent:intents(b.id())){
                boolean leavingMarkedCell=!intent.attackerId().equals(actor)&&intent.cells().contains(new WorldMap.Hex(currentQ,currentR));
                boolean attackerLeavesRange=intent.attackerId().equals(actor)&&intent.cells().stream().noneMatch(h->{int d=WeaponRules.distance(next,h);return d>=intent.minRange()&&d<=intent.maxRange();});
                if(leavingMarkedCell||attackerLeavesRange)resolve(b.id(),intent,now);
            }
            if(!characters.get(actor).alive())break;
            spent++;
            db.update("update battle_actors set q=?,r=? where encounter_id=? and account_id=?",next.q(),next.r(),b.id(),actor);
            event(b.id(),"move",actor,null,next.q(),next.r(),now);
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
        var weapon=characters.weapon(actor);
        var cells=WeaponRules.cells(weapon,new WorldMap.Hex(from.q(),from.r()),new WorldMap.Hex(q,r));
        if(cells.isEmpty()||cells.stream().anyMatch(h->!within(h.q(),h.r())))throw bad("请选择武器可攻击的完整范围");
        Intent previous=intents(b.id()).stream().filter(i->i.attackerId().equals(actor)).findFirst().orElse(null);
        if(previous!=null&&previous.q()==q&&previous.r()==r)return;
        if(b.points()<weapon.cost())throw bad("本回合战术点不足");
        characters.hostile(actor);
        if(previous!=null){
            db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),actor);
            event(b.id(),"cancel",actor,null,previous.q(),previous.r(),now);
        }
        db.update("insert into battle_intents(encounter_id,attacker_id,target_id,q,r,visibility,cells,weapon,damage,min_range,max_range) values(?,?,null,?,?,'public',?,?,?,?,?)",b.id(),actor,q,r,json.writeValueAsString(cells),weapon.code(),weapon.damage(),weapon.minRange(),weapon.maxRange());
        db.update("update battle_encounters set turn_points=turn_points-?,attack_used=true where id=?",weapon.cost(),b.id());
        event(b.id(),"mark",actor,null,q,r,now);
        if(b.points()==weapon.cost())advanceTurn(b,from.initiative(),online,now);
    }
    public void endTurn(UUID actor,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);
        advanceTurn(b,position(b.id(),actor).initiative(),online,now);
    }
    private void advanceTurn(Encounter b,int after,Set<UUID> online,long now){
        List<Position> all=positions(b.id());
        if(finishIfNeeded(b.id(),now))return;
        int round=b.round();
        final int currentRound=round;
        Position next=all.stream().filter(p->p.initiative()>after&&p.entryRound()<=currentRound).findFirst().orElse(null);
        if(next==null){round++;sortInitiative(b.id());all=positions(b.id());final int newRound=round;next=all.stream().filter(p->p.entryRound()<=newRound).findFirst().orElse(null);}
        if(next==null)throw new IllegalStateException("Battle has no eligible actor");
        db.update("update battle_encounters set round_number=?,turn_account_id=?,turn_points=?,attack_used=false,turn_deadline=?,turn_start_edge=? where id=?",
            round,next.id(),characters.get(next.id()).alive()?TURN_POINTS:0,now+turnMillis,exit(next.q(),next.r()),b.id());
        event(b.id(),"turn",next.id(),null,null,null,now);
        Integer withdrawal=db.queryForObject("select withdraw_direction from battle_actors where encounter_id=? and account_id=?",Integer.class,b.id(),next.id());
        if(withdrawal!=null){
            if(depart(next.id(),byId(b.id()),withdrawal,online,now))return;
            db.update("update battle_actors set withdraw_direction=null where encounter_id=? and account_id=?",b.id(),next.id());
            event(b.id(),"withdraw-blocked",next.id(),null,null,null,now);
        }

        for(Intent intent:intents(b.id()))if(intent.attackerId().equals(next.id())){
            if(online.contains(next.id())||characters.get(next.id()).npc())resolve(b.id(),intent,now);
            else{
                event(b.id(),"cancel",next.id(),null,intent.q(),intent.r(),now);
                db.update("delete from battle_intents where encounter_id=? and attacker_id=?",b.id(),next.id());
            }
        }
    }
    private void hit(UUID battle,UUID attacker,UUID target,int q,int r,int damage,long now){
        String result=characters.damage(target,damage,now);
        if(result.equals("protected"))return;
        if(result.equals("down")||result.equals("death")){db.update("delete from battle_intents where encounter_id=? and attacker_id=?",battle,target);event(battle,result,attacker,target,q,r,now);}
        if(result.equals("death"))db.update("delete from battle_actors where encounter_id=? and account_id=?",battle,target);
        event(battle,"attack",attacker,target,q,r,now);
        if(db.update("update battle_actors set withdraw_direction=null where encounter_id=? and account_id=? and withdraw_direction is not null",battle,target)>0)
            event(battle,"withdraw-interrupted",target,attacker,q,r,now);
    }
    public void withdraw(UUID actor,int direction,boolean force,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);Position p=position(b.id(),actor);
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
        characters.relocate(actor,edge.q(),edge.r());
        event(b.id(),"exit",actor,null,p.q(),p.r(),now);
        if(destination!=null)enter(actor,destination,incoming,now);
        if(!finishIfNeeded(b.id(),now))advanceTurn(b,p.initiative(),online,now);
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
            if(finishIfNeeded(battle.id(),now)){changed=true;continue;}
            Encounter current=byId(battle.id());if(current==null)continue;
            Position turn=position(current.id(),current.turn());
            if(turn==null){advanceTurn(current,-1,online,now);changed=true;continue;}
            var c=characters.get(current.turn());
            if(c.npc()&&c.alive()){
                if(aiNext.getOrDefault(c.id(),0L)<=now){aiNext.put(c.id(),now+700);actNpc(current,online,now);changed=true;}
            }else if(!c.alive()||!online.contains(c.id())||current.deadline()<=now){
                if(positions(current.id()).stream().anyMatch(p->online.contains(p.id())||characters.get(p.id()).npc())){advanceTurn(current,turn.initiative(),online,now);changed=true;}
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
        if(all.size()<=1||standing==0){close(id,now);return true;}return false;
    }
    public void rescue(UUID actor,UUID target,Set<UUID> online,long now){
        Encounter b=requireTurn(actor);requireStanding(actor);var a=position(b.id(),actor);var t=position(b.id(),target);
        if(t==null||characters.get(target).npc()||distance(a.q(),a.r(),t.q(),t.r())!=1||b.points()!=6)throw bad("救起需要相邻并保留完整6点行动点");
        characters.rescue(target);event(b.id(),"rescue",actor,target,t.q(),t.r(),now);advanceTurn(b,a.initiative(),online,now);
    }
    public void surrender(UUID actor,Set<UUID> online,long now){characters.die(actor,now);remove(actor,online,now);}
    private void actNpc(Encounter b,Set<UUID> online,long now){
        Position own=position(b.id(),b.turn());if(own==null)return;
        var from=new WorldMap.Hex(own.q(),own.r());
        List<Position> targets=positions(b.id()).stream().filter(p->!p.id().equals(own.id())&&!characters.get(p.id()).npc()).toList();
        if(targets.isEmpty()){close(b.id(),now);return;}
        var standing=targets.stream().filter(p->characters.get(p.id()).alive()).toList();if(!standing.isEmpty())targets=standing;
        var options=new ArrayList<MonsterBrain.Option>();
        Intent pending=intents(b.id()).stream().filter(i->i.attackerId().equals(own.id())).findFirst().orElse(null);
        for(var t:targets){var h=new WorldMap.Hex(t.q(),t.r());if(distance(own.q(),own.r(),t.q(),t.r())==1&&b.points()>=2&&(pending==null||!pending.cells().contains(h)))options.add(new MonsterBrain.Option("attack",h,8+(characters.get(t.id()).hp()<=3?4:0)));}
        if(b.points()>0)for(var next:from.neighbors())if(within(next.q(),next.r())&&positions(b.id()).stream().noneMatch(p->p.q()==next.q()&&p.r()==next.r())){
            double score=0;int nearest=targets.stream().mapToInt(p->distance(next.q(),next.r(),p.q(),p.r())).min().orElse(20),before=targets.stream().mapToInt(p->distance(from.q(),from.r(),p.q(),p.r())).min().orElse(20);
            score+=(before-nearest)*2-0.4;
            if(pending!=null&&pending.cells().stream().noneMatch(h->WeaponRules.distance(next,h)==1)&&targets.stream().anyMatch(t->pending.cells().contains(new WorldMap.Hex(t.q(),t.r()))))score+=10;
            for(var i:intents(b.id()))if(!i.attackerId().equals(own.id())&&i.cells().contains(from))score-=i.damage()*2;
            if(b.points()<=2&&pending==null&&nearest>1)score-=1;
            options.add(new MonsterBrain.Option("move",next,score));
        }
        var choice=MonsterBrain.choose(options);
        if(choice==null)endTurn(own.id(),online,now);
        else if(choice.action().equals("attack"))attackCell(own.id(),choice.cell().q(),choice.cell().r(),online,now);
        else step(own.id(),choice.cell().q(),choice.cell().r(),online,now);
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
        for(Encounter b:encounters(""))db.update("delete from battle_encounters where id=?",b.id());
    }
}
