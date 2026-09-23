package game.world;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.security.SecureRandom;

/** One authoritative world owner in v1. All mutations enter this monitor; no network handler owns game state. */
@Service
public class WorldService {
    private final JdbcTemplate db;private final AccountService accounts;private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    private WorldMap world;
    private final Map<UUID,ArrayDeque<WorldMap.Hex>> routes=new HashMap<>();
    private record Connection(UUID user,String token,String ip,WebSocketSession socket) {}
    private final Map<String,Connection> connections=new HashMap<>();
    public record Emote(UUID accountId,String code,long startedAt,long expiresAt) {}
    private static final Set<String> EMOTE_CODES=Set.of("happy","sad","angry","question","thumb","heart","help","ok");
    private final Map<UUID,Emote> emotes=new HashMap<>();
    private long tick=0,battleRevision=0;
    private final ResourceService resources;private final ModerationService moderation;
    private final BattleService battles;
    WorldService(JdbcTemplate db,AccountService accounts,PlatformTransactionManager manager,ResourceService resources,ModerationService moderation,BattleService battles){this.moderation=moderation;this.resources=resources;this.db=db;this.accounts=accounts;this.battles=battles;tx=new TransactionTemplate(manager);}
    @PostConstruct void load(){
        var rows=db.queryForList("select document from worlds where id=1",String.class);
        if(rows.isEmpty()) {world=WorldMap.generate(new SecureRandom().nextLong());db.update("insert into worlds values(1,?)",json.writeValueAsString(world));}
        else world=json.readValue(rows.getFirst(),WorldMap.class);
        battles.world(world);
        tx.executeWithoutResult(s->resources.initialize(world));
    }
    public synchronized WorldMap map(){return world;}
    public synchronized Map<String,Object> snapshot(){
        Set<UUID> online=new HashSet<>();connections.values().forEach(c->online.add(c.user));
        Set<UUID> battling=battles.actorIds();
        var players=accounts.list().stream().map(a->Map.of("id",a.id(),"username",a.username(),"q",a.q(),"r",a.r(),
            "color",a.color(),"online",online.contains(a.id()),"moving",routes.containsKey(a.id()),"inBattle",battling.contains(a.id())))
            .toList();
        // Pending accounts have never entered the world and must not appear on the map.
        Set<UUID> entered=new HashSet<>(db.query("select id from accounts where entered=true",
            (rs,n)->rs.getObject(1,UUID.class)));
        return Map.of("type","state","version",world.version(),"players",players.stream().filter(p->entered.contains(p.get("id"))).toList(),"tick",tick,"resources",resources.nodes(),"actions",resources.actions(),"serverTime",System.currentTimeMillis(),"emotes",emotes.values().stream().filter(e->e.expiresAt()>System.currentTimeMillis()).toList(),"battles",battles.summaries(world.version()),"battleRevision",battleRevision);
    }
    public synchronized AccountService.Account register(String name,String pass){return accounts.register(name,pass,world.spawn());}
    public synchronized List<WorldMap.Hex> move(UUID id,int q,int r,String version){
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新地图后重试");
        AccountService.Account a=accounts.get(id);
        if(!a.approved())throw AccountService.bad("游戏权限已被撤销");
        if(battles.engaged(id))throw AccountService.bad("角色正在战斗中，大世界视角只能查看");
        WorldMap.Hex target=new WorldMap.Hex(q,r),from=new WorldMap.Hex(a.q(),a.r());
        if(from.equals(target)){routes.remove(id);return List.of();}
        if(battles.blocked(q,r))throw AccountService.bad("此格正在战斗，已封锁通行；请从相邻格选择参战");
        var available=new HashMap<>(world.index());
        battles.summaries(world.version()).forEach(b->available.remove(new WorldMap.Hex(b.q(),b.r())));
        List<WorldMap.Hex> path=WorldMap.path(available,from,target,false);
        if(path.isEmpty())throw AccountService.bad("无法到达这里，请选择陆地或经桥梁绕行");
        tx.executeWithoutResult(s->resources.cancel(id));
        routes.put(id,new ArrayDeque<>(path));broadcast();return path;
    }
    public synchronized void stop(UUID id){
        if(battles.engaged(id))throw AccountService.bad("角色正在战斗中，大世界视角只能查看");
        routes.remove(id);broadcast();
    }
    public synchronized void connect(UUID id,String token,String ip,WebSocketSession raw){
        // Approval is checked again under the world lock to serialize against revocation.
        try{accounts.authenticate(token);moderation.requireGame(id,ip);}catch(Exception e){try{raw.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}return;}
        if(!accounts.get(id).approved()){try{raw.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}return;}
        if(connections.size()>=40){try{raw.close(CloseStatus.SERVICE_OVERLOAD);}catch(Exception ignored){}return;}
        boolean wasOnline=onlineUsers().contains(id);
        db.update("update accounts set entered=true where id=?",id);
        connections.put(raw.getId(),new Connection(id,token,ip,new ConcurrentWebSocketSessionDecorator(raw,2000,131072)));
        boolean resumed=battles.resume(id,System.currentTimeMillis());
        if(resumed||!wasOnline&&battles.engaged(id))battleRevision++;
        broadcast();
    }
    public synchronized void disconnect(String socketId){
        Connection c=connections.remove(socketId);
        if(c!=null && connections.values().stream().noneMatch(x->x.user.equals(c.user))){
            routes.remove(c.user);
            if(battles.engaged(c.user))battleRevision++;
        }
        broadcast();
    }
    public synchronized void closeUser(UUID id){
        routes.remove(id);emotes.remove(id);
        var matches=connections.entrySet().stream().filter(e->e.getValue().user.equals(id)).toList();
        for(var e:matches){connections.remove(e.getKey());try{e.getValue().socket.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}}
        if(!matches.isEmpty()&&battles.engaged(id))battleRevision++;
        broadcast();
    }
    public synchronized void approve(UUID actor,UUID target,boolean allowed){
        var a=accounts.get(target);
        if(a.admin())throw AccountService.bad("不能撤销管理员的游戏权限");
        tx.executeWithoutResult(s->{db.update("update accounts set approved=? where id=?",allowed,target);audit(actor,allowed?"approve":"revoke",target.toString());});
        if(!allowed){boolean removed=Boolean.TRUE.equals(tx.execute(s->{resources.cancel(target);return battles.remove(target,onlineUsers(),System.currentTimeMillis());}));if(removed)battleRevision++;closeUser(target);}else broadcast();
    }
    public synchronized void resetPassword(UUID actor,UUID target,String password){
        if(accounts.get(target).admin())throw AccountService.bad("管理员密码请通过服务器维护流程修改");
        tx.executeWithoutResult(s->{accounts.resetPassword(target,password);audit(actor,"reset-password",target.toString());});closeUser(target);
    }
    public synchronized WorldMap regenerate(UUID actor){
        WorldMap next=WorldMap.generate(new SecureRandom().nextLong());
        tx.executeWithoutResult(s->{db.update("update worlds set document=? where id=1",json.writeValueAsString(next));
            db.update("update accounts set q=?,r=?",next.spawn().q(),next.spawn().r());resources.reset(next);battles.reset();audit(actor,"regenerate",next.version());});
        world=next;battles.world(world);routes.clear();emotes.clear();battleRevision++;broadcast();return world;
    }
    public synchronized List<Map<String,Object>> inventory(UUID id){accounts.get(id);return resources.inventory(id);}
    public synchronized Map<String,String> collect(UUID id,int q,int r,String version){
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新地图后重试");
        var a=accounts.get(id);
        if(!a.approved())throw AccountService.bad("游戏权限已被撤销");
        if(battles.engaged(id))throw AccountService.bad("角色正在战斗中，不能在大世界采集");
        if(a.q()!=q||a.r()!=r||routes.containsKey(id))throw AccountService.bad("请先到达资源所在格子并停下");
        String message=tx.execute(s->resources.collect(id,q,r,System.currentTimeMillis()));
        broadcast();return Map.of("message",message);
    }
    private Set<UUID> onlineUsers(){Set<UUID> online=new HashSet<>();connections.values().forEach(c->online.add(c.user));return online;}
    private void requireBattle(UUID actor,UUID battleId){
        if(battleId==null||!battleId.equals(battles.currentId(actor)))throw AccountService.bad("战斗已变化，请刷新战场");
    }
    public synchronized Object currentBattle(UUID actor){return battles.current(actor,onlineUsers());}
    public synchronized UUID startBattle(UUID actor,UUID target,String version){
        if(routes.containsKey(actor)||routes.containsKey(target))throw AccountService.bad("双方需先在同一世界格停下");
        UUID id=tx.execute(s->{UUID next=battles.start(actor,target,version,world,System.currentTimeMillis());battles.actorIds().forEach(resources::cancel);return next;});
        battles.actorIds().forEach(idInBattle->{routes.remove(idInBattle);emotes.remove(idInBattle);});battleRevision++;broadcast();return id;
    }
    public synchronized UUID joinBattle(UUID actor,UUID battleId,String version){
        if(routes.containsKey(actor))throw AccountService.bad("请先停下再加入战斗");
        UUID id=tx.execute(s->{UUID next=battles.join(actor,battleId,version,world,System.currentTimeMillis());resources.cancel(actor);return next;});
        routes.remove(actor);battleRevision++;broadcast();return id;
    }
    public synchronized void battleStep(UUID actor,UUID battleId,int q,int r){
        requireBattle(actor,battleId);
        tx.executeWithoutResult(s->battles.step(actor,q,r,onlineUsers(),System.currentTimeMillis()));
        battleRevision++;broadcast();
    }
    public synchronized void battleAttack(UUID actor,UUID battleId,UUID target){
        requireBattle(actor,battleId);
        tx.executeWithoutResult(s->battles.attack(actor,target,onlineUsers(),System.currentTimeMillis()));
        battleRevision++;broadcast();
    }
    public synchronized void battleWithdraw(UUID actor,UUID battleId,int direction,boolean force){
        requireBattle(actor,battleId);
        tx.executeWithoutResult(s->battles.withdraw(actor,direction,force,onlineUsers(),System.currentTimeMillis()));
        battleRevision++;broadcast();
    }
    public synchronized void battleEndTurn(UUID actor,UUID battleId){
        requireBattle(actor,battleId);
        tx.executeWithoutResult(s->battles.endTurn(actor,onlineUsers(),System.currentTimeMillis()));
        battleRevision++;broadcast();
    }
    public synchronized Emote emote(UUID id,String code,String version){
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新后再发送表情");
        if(!accounts.get(id).approved())throw AccountService.bad("游戏权限已被撤销");
        if(battles.engaged(id))throw AccountService.bad("角色正在战斗中，不能发送大世界表情");
        if(code==null||!EMOTE_CODES.contains(code))throw AccountService.bad("不支持的表情");
        long now=System.currentTimeMillis();var previous=emotes.get(id);
        if(previous!=null&&now-previous.startedAt()<250)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,"表情发送太快，请稍后再试");
        var next=new Emote(id,code,now,now+3000);emotes.put(id,next);broadcast();return next;
    }
    public synchronized AccountService.Account login(String name,String password,jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res){
        var a=accounts.login(name,password,req,res);closeInvalidSessions();broadcast();return a;
    }
    private void closeInvalidSessions(){
        for(var entry:List.copyOf(connections.entrySet())){var c=entry.getValue();try{accounts.authenticate(c.token);moderation.requireGame(c.user,c.ip);}catch(Exception e){connections.remove(entry.getKey());try{c.socket.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}if(connections.values().stream().noneMatch(x->x.user.equals(c.user))){routes.remove(c.user);if(battles.engaged(c.user))battleRevision++;}}}
    }
    public synchronized void notifyChat(Set<UUID> users){
        var payload=new TextMessage("{\"type\":\"chat-changed\"}");
        for(var c:List.copyOf(connections.values()))if(users==null||users.contains(c.user)){try{c.socket.sendMessage(payload);}catch(Exception ignored){}}
    }
    public synchronized UUID ban(UUID actor,String actorIp,String type,String target,String scope,Long minutes,String reason){
        UUID id=tx.execute(s->moderation.create(actor,actorIp,type,target,scope,minutes,reason));
        if("game".equals(scope)){
            Set<UUID> affected=new HashSet<>();
            if("account".equals(type))affected.add(UUID.fromString(target));
            else for(var c:connections.values())if(c.ip.equals(ModerationService.ip(target)))affected.add(c.user);
            boolean removed=Boolean.TRUE.equals(tx.execute(s->{affected.forEach(resources::cancel);boolean changed=false;for(UUID user:affected)changed|=battles.remove(user,onlineUsers(),System.currentTimeMillis());return changed;}));
            if(removed)battleRevision++;closeInvalidSessions();broadcast();
        }
        notifyChat(null);return id;
    }
    public synchronized void unban(UUID actor,UUID id){tx.executeWithoutResult(s->moderation.revoke(actor,id));notifyChat(null);}
    private void audit(UUID actor,String action,String target){db.update("insert into admin_audit(actor,action,target) values(?,?,?)",actor,action,target);}
    @Scheduled(fixedDelay=350) public synchronized void advance(){
        tick++;
        boolean emotesChanged=emotes.values().removeIf(e->e.expiresAt()<=System.currentTimeMillis());
        long now=System.currentTimeMillis();
        boolean resourcesChanged=Boolean.TRUE.equals(tx.execute(s->resources.advance(world,now)));
        boolean battlesChanged=Boolean.TRUE.equals(tx.execute(s->battles.advance(onlineUsers(),now)));
        if(battlesChanged)battleRevision++;
        if(tick%60==0){
            closeInvalidSessions();
        }
        if(routes.isEmpty()){if(resourcesChanged||emotesChanged||battlesChanged||tick%30==0)broadcast();return;}
        routes.entrySet().removeIf(e->battles.engaged(e.getKey())||!e.getValue().isEmpty()&&battles.blocked(e.getValue().peek().q(),e.getValue().peek().r()));
        Map<UUID,WorldMap.Hex> steps=new HashMap<>();
        routes.forEach((id,path)->{if(!path.isEmpty())steps.put(id,path.peek());});
        // Commit positions before announcing them; a restart cannot roll back an acknowledged step.
        tx.executeWithoutResult(s->steps.forEach((id,h)->db.update("update accounts set q=?,r=? where id=?",h.q(),h.r(),id)));
        steps.keySet().forEach(id->routes.get(id).remove());routes.entrySet().removeIf(e->e.getValue().isEmpty());broadcast();
    }
    private void broadcast(){
        if(connections.isEmpty())return;
        TextMessage payload=new TextMessage(json.writeValueAsString(snapshot()));
        List<String> dead=new ArrayList<>();
        connections.forEach((key,c)->{try{if(c.socket.isOpen())c.socket.sendMessage(payload);else dead.add(key);}catch(Exception e){dead.add(key);}});
        for(String id:dead){Connection c=connections.remove(id);if(c!=null){try{c.socket.close();}catch(Exception ignored){}
            if(connections.values().stream().noneMatch(x->x.user.equals(c.user))){routes.remove(c.user);if(battles.engaged(c.user))battleRevision++;}}}
    }
}
