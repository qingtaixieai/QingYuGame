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
    private final CharacterService characters;private final EquipmentService equipment;
    private final BattleService battles;private final FerryService ferry;private final HotbarService hotbar;
    private final LootService loot;
    WorldService(JdbcTemplate db,AccountService accounts,PlatformTransactionManager manager,ResourceService resources,ModerationService moderation,BattleService battles,FerryService ferry,CharacterService characters,EquipmentService equipment,HotbarService hotbar,LootService loot){this.loot=loot;this.hotbar=hotbar;this.characters=characters;this.equipment=equipment;this.ferry=ferry;this.moderation=moderation;this.resources=resources;this.db=db;this.accounts=accounts;this.battles=battles;tx=new TransactionTemplate(manager);}
    @PostConstruct void load(){
        var rows=db.queryForList("select document from worlds where id=1",String.class);
        if(rows.isEmpty()) {world=WorldMap.generate(new SecureRandom().nextLong());db.update("insert into worlds values(1,?)",json.writeValueAsString(world));}
        else world=json.readValue(rows.getFirst(),WorldMap.class);
        WorldMap previous=world;world=WorldMap.expand(world);
        battles.world(world);
        tx.executeWithoutResult(s->{
            if(!previous.version().equals(world.version())){
                db.update("update worlds set document=? where id=1",json.writeValueAsString(world));
                db.update("update battle_encounters set world_version=? where world_version=?",world.version(),previous.version());
                db.update("update loot_containers set world_version=? where world_version=?",world.version(),previous.version());
            }
            boolean empty=resources.nodes().isEmpty();resources.initialize(world);
            if(!empty&&!previous.version().equals(world.version()))resources.extend(previous,world);
            ferry.initialize(world,System.currentTimeMillis());characters.initialize(world,System.currentTimeMillis());
        });
    }
    public synchronized WorldMap map(){return world;}
    public synchronized Map<String,Object> snapshot(){return snapshot(null);}
    public synchronized Map<String,Object> snapshot(UUID viewer){
        Set<UUID> online=new HashSet<>();connections.values().forEach(c->online.add(c.user));
        Set<UUID> battling=battles.actorIds();
        var profiles=characters.all();var viewing=viewer==null?null:characters.get(viewer);
        var players=profiles.stream().filter(c->!c.life().equals("dead")&&(viewing!=null&&viewing.life().equals("soul")?c.id().equals(viewer):!c.life().equals("soul"))).map(c->{
            Map<String,Object> p=new LinkedHashMap<>();p.put("id",c.id());p.put("username",c.name());p.put("q",c.q());p.put("r",c.r());p.put("color",c.npc()?"#a56850":accounts.get(c.id()).color());p.put("online",c.npc()||online.contains(c.id()));p.put("moving",routes.containsKey(c.id()));p.put("inBattle",battling.contains(c.id()));p.put("kind",c.kind());p.put("life",c.life());p.put("hp",c.hp());p.put("maxHp",c.maxHp());return p;
        }).toList();
        // Pending accounts have never entered the world and must not appear on the map.
        Set<UUID> entered=new HashSet<>(db.query("select id from accounts where entered=true",
            (rs,n)->rs.getObject(1,UUID.class)));
        var out=new LinkedHashMap<String,Object>(Map.of("type","state","version",world.version(),"players",players.stream().filter(p->(entered.contains(p.get("id"))||p.get("kind").equals("monster"))).toList(),"tick",tick,"resources",resources.nodes(),"actions",resources.actions(),"serverTime",System.currentTimeMillis(),"emotes",emotes.values().stream().filter(e->e.expiresAt()>System.currentTimeMillis()).toList(),"battles",battles.summaries(world.version()),"battleRevision",battleRevision));boolean soul=viewing!=null&&viewing.life().equals("soul");
        out.put("ferry",soul?null:ferry.state());if(soul){out.put("resources",List.of());out.put("actions",List.of());out.put("emotes",List.of());out.put("battles",List.of());}
        out.put("loot",soul?List.of():loot.ground(world.version(),System.currentTimeMillis()));
        if(viewing!=null){out.put("character",viewing);out.put("inventory",resources.inventory(viewer));out.put("carriedCorpses",loot.carried(viewer));}return out;
    }
    public synchronized AccountService.Account register(String name,String pass){var a=accounts.register(name,pass,world.spawn());characters.ensurePlayers();return a;}
    public synchronized List<WorldMap.Hex> move(UUID id,int q,int r,String version){
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新地图后重试");
        AccountService.Account a=accounts.get(id);
        if(!a.approved())throw AccountService.bad("游戏权限已被撤销");
        ferry.requireAshore(id);
        if(characters.get(id).life().equals("down"))throw AccountService.bad("倒地后无法移动");
        if(battles.engaged(id))throw AccountService.bad("角色正在战斗中，大世界视角只能查看");
        WorldMap.Hex target=new WorldMap.Hex(q,r),from=new WorldMap.Hex(a.q(),a.r());
        if(from.equals(target)){routes.remove(id);return List.of();}
        boolean soul=characters.get(id).life().equals("soul");
        if(!soul&&battles.blocked(q,r))throw AccountService.bad("此格正在战斗，已封锁通行；请从相邻格选择参战");
        var available=new HashMap<>(world.index());
        if(!soul)battles.summaries(world.version()).forEach(b->available.remove(new WorldMap.Hex(b.q(),b.r())));
        List<WorldMap.Hex> path=WorldMap.path(available,from,target,false);
        if(path.isEmpty())throw AccountService.bad("无法到达这里，请选择陆地或经桥梁绕行");
        tx.executeWithoutResult(s->{resources.cancel(id);characters.cancelTimer(id);});
        routes.put(id,new ArrayDeque<>(path));broadcast();return path;
    }
    public synchronized void stop(UUID id){
        ferry.requireAshore(id);
        if(characters.get(id).life().equals("down"))throw AccountService.bad("倒地后无法移动");
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
            routes.remove(c.user);characters.cancelTimer(c.user);
            if(battles.engaged(c.user))battleRevision++;
        }
        broadcast();
    }
    public synchronized void closeUser(UUID id){
        routes.remove(id);emotes.remove(id);characters.cancelTimer(id);
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
        WorldMap next=WorldMap.expand(WorldMap.generate(new SecureRandom().nextLong()));
        tx.executeWithoutResult(s->{db.update("update worlds set document=? where id=1",json.writeValueAsString(next));
            db.update("update accounts set q=?,r=?",next.spawn().q(),next.spawn().r());resources.reset(next);loot.resetGround();battles.reset();ferry.reset(next,System.currentTimeMillis());characters.reset(next,System.currentTimeMillis());audit(actor,"regenerate",next.version());});
        world=next;battles.world(world);routes.clear();emotes.clear();battleRevision++;broadcast();return world;
    }
    private LootService.Context lootContext(UUID id,String version,UUID battleId){
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新");
        if(!Objects.equals(battles.currentId(id),battleId))throw AccountService.bad("战斗状态已变化，请重新打开列表");
        characters.requireAlive(id);ferry.requireAshore(id);
        if(routes.containsKey(id))throw AccountService.bad("请停下后操作");
        if(battleId!=null)return battles.lootContext(id,version);
        var c=characters.get(id);if(battles.blocked(c.q(),c.r()))throw AccountService.bad("此格已被战斗封锁");
        return new LootService.Context(id,version,c.q(),c.r(),null,null,null);
    }
    public synchronized Object lootList(UUID id,String version,UUID battleId){var c=lootContext(id,version,battleId);return Map.of("ground",loot.nearby(c,System.currentTimeMillis()),"carried",loot.carried(id));}
    public synchronized Object lootContents(UUID id,UUID container,String version,UUID battleId){var c=lootContext(id,version,battleId);return tx.execute(s->loot.contents(container,c,System.currentTimeMillis()));}
    public synchronized void lootCommand(UUID id,LootService.Command cmd){
        var c=lootContext(id,cmd.version(),cmd.battleId());long now=System.currentTimeMillis();
        tx.executeWithoutResult(s->{
            if(c.battleId()!=null)battles.requireLootAction(id);
            loot.execute(c,cmd,now);
            if(c.battleId()!=null)battles.payLootAction(id,onlineUsers(),now);
            characters.hostile(id);resources.cancel(id);hotbar.sync(id);
        });battleRevision++;broadcast();
    }
    public synchronized List<Map<String,Object>> inventory(UUID id){accounts.get(id);return resources.inventory(id);}
    public synchronized Map<String,String> collect(UUID id,int q,int r,String version){
        characters.requireAlive(id);
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新地图后重试");
        var a=accounts.get(id);
        if(!a.approved())throw AccountService.bad("游戏权限已被撤销");
        ferry.requireAshore(id);
        if(battles.engaged(id))throw AccountService.bad("角色正在战斗中，不能在大世界采集");
        if(a.q()!=q||a.r()!=r||routes.containsKey(id))throw AccountService.bad("请先到达资源所在格子并停下");
        String message=tx.execute(s->{characters.hostile(id);return resources.collect(id,q,r,System.currentTimeMillis());});
        broadcast();return Map.of("message",message);
    }
    private AccountService.Account requireFerryAction(UUID id,String version){
        characters.requireAlive(id);
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新地图");
        var a=accounts.get(id);
        if(!a.approved())throw AccountService.bad("游戏权限已被撤销");
        if(battles.engaged(id))throw AccountService.bad("请先离开战斗");
        if(routes.containsKey(id))throw AccountService.bad("请先到达岸边并停下");
        return a;
    }
    public synchronized Map<String,String> contribute(UUID id,String version,String item,int quantity){
        var a=requireFerryAction(id,version);ferry.requireAshore(id);var port=world.place("port");
        if(a.q()!=port.q()||a.r()!=port.r())throw AccountService.bad("请先到达望潮港");
        String message=tx.execute(s->ferry.contribute(id,item,quantity,System.currentTimeMillis()));broadcast();return Map.of("message",message);
    }
    public synchronized void ferryBoard(UUID id,String version){
        var a=requireFerryAction(id,version);
        tx.executeWithoutResult(s->{ferry.advance(System.currentTimeMillis());ferry.board(id,a.q(),a.r());resources.cancel(id);});
        emotes.remove(id);broadcast();
    }
    public synchronized void ferryDisembark(UUID id,String version){
        requireFerryAction(id,version);
        tx.executeWithoutResult(s->{ferry.advance(System.currentTimeMillis());var dock=ferry.dock();
            if(dock!=null&&battles.blocked(dock.q(),dock.r()))throw AccountService.bad("停靠点正在战斗，暂时无法下船");
            ferry.disembark(id);
        });broadcast();
    }
    private Set<UUID> onlineUsers(){Set<UUID> online=new HashSet<>();connections.values().forEach(c->online.add(c.user));return online;}
    private void requireBattle(UUID actor,UUID battleId){
        if(battleId==null||!battleId.equals(battles.currentId(actor)))throw AccountService.bad("战斗已变化，请刷新战场");
    }
    public synchronized Object currentBattle(UUID actor){return battles.current(actor,onlineUsers());}
    public synchronized HotbarService.State hotbar(UUID actor){return tx.execute(s->hotbar.state(actor,battles.current(actor,onlineUsers())));}
    public synchronized HotbarService.State editHotbar(UUID actor,HotbarService.Edit edit){
        var result=tx.execute(s->{hotbar.edit(actor,edit);return hotbar.state(actor,battles.current(actor,onlineUsers()));});
        battleRevision++;broadcast();return result;
    }
    public synchronized UUID startBattle(UUID actor,UUID target,String version){
        ferry.requireAshore(actor);ferry.requireAshore(target);
        if(routes.containsKey(actor)||routes.containsKey(target))throw AccountService.bad("双方需先在同一世界格停下");
        UUID id=tx.execute(s->{UUID next=battles.start(actor,target,version,world,System.currentTimeMillis());battles.actorIds().forEach(resources::cancel);return next;});
        battles.actorIds().forEach(idInBattle->{routes.remove(idInBattle);emotes.remove(idInBattle);});battleRevision++;broadcast();return id;
    }
    public synchronized UUID joinBattle(UUID actor,UUID battleId,String version){
        ferry.requireAshore(actor);
        if(routes.containsKey(actor))throw AccountService.bad("请先停下再加入战斗");
        UUID id=tx.execute(s->{UUID next=battles.join(actor,battleId,version,world,System.currentTimeMillis());resources.cancel(actor);return next;});
        routes.remove(actor);battleRevision++;broadcast();return id;
    }
    public synchronized void battleStep(UUID actor,UUID battleId,int q,int r){
        requireBattle(actor,battleId);
        tx.executeWithoutResult(s->battles.step(actor,q,r,onlineUsers(),System.currentTimeMillis()));
        battleRevision++;broadcast();
    }
    public synchronized void battleAttack(UUID actor,UUID battleId,UUID target,Integer q,Integer r){
        requireBattle(actor,battleId);
        tx.executeWithoutResult(s->{if(q!=null&&r!=null)battles.attackCell(actor,q,r,onlineUsers(),System.currentTimeMillis());else battles.attack(actor,target,onlineUsers(),System.currentTimeMillis());});
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
        characters.requireAlive(id);
        if(!world.version().equals(version))throw AccountService.bad("世界已更新，请刷新后再发送表情");
        if(!accounts.get(id).approved())throw AccountService.bad("游戏权限已被撤销");
        ferry.requireAshore(id);
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
        var soulsBefore=characters.all().stream().filter(c->c.life().equals("soul")).map(CharacterService.Character::id).toList();
        boolean lifeChanged=Boolean.TRUE.equals(tx.execute(s->{boolean changed=characters.advance(now,onlineUsers());for(UUID id:soulsBefore)if(characters.get(id).alive())battles.rejoinAtCell(id,now);return changed;}));
        if(lifeChanged)battleRevision++;
        boolean monstersChanged=Boolean.TRUE.equals(tx.execute(s->characters.wander(now,battles.actorIds())));
        boolean encounterChanged=Boolean.TRUE.equals(tx.execute(s->monsterEncounters(now)));
        if(encounterChanged)battleRevision++;
        boolean ferryChanged=Boolean.TRUE.equals(tx.execute(s->ferry.advance(now)));
        boolean lootChanged=Boolean.TRUE.equals(tx.execute(s->loot.expire(now)));
        if(lootChanged)battleRevision++;
        boolean resourcesChanged=Boolean.TRUE.equals(tx.execute(s->resources.advance(world,now)))||lootChanged;
        boolean battlesChanged=Boolean.TRUE.equals(tx.execute(s->battles.advance(onlineUsers(),now)));
        if(battlesChanged){battleRevision++;for(var c:characters.all())if(!c.alive()){routes.remove(c.id());resources.cancel(c.id());}}
        if(tick%60==0){
            closeInvalidSessions();
        }
        if(routes.isEmpty()){if(lifeChanged||monstersChanged||encounterChanged||resourcesChanged||emotesChanged||battlesChanged||ferryChanged||tick%30==0)broadcast();return;}
        routes.entrySet().removeIf(e->battles.engaged(e.getKey())||characters.get(e.getKey()).life().equals("down")||!characters.get(e.getKey()).life().equals("soul")&&!e.getValue().isEmpty()&&battles.blocked(e.getValue().peek().q(),e.getValue().peek().r()));
        Map<UUID,WorldMap.Hex> steps=new HashMap<>();
        routes.forEach((id,path)->{if(!path.isEmpty())steps.put(id,path.peek());});
        // Commit positions before announcing them; a restart cannot roll back an acknowledged step.
        tx.executeWithoutResult(s->steps.forEach((id,h)->db.update("update accounts set q=?,r=? where id=?",h.q(),h.r(),id)));
        steps.keySet().forEach(id->routes.get(id).remove());
        tx.executeWithoutResult(s->{for(var e:routes.entrySet())if(characters.get(e.getKey()).life().equals("soul")&&!e.getValue().isEmpty()){var h=e.getValue().remove();characters.relocate(e.getKey(),h.q(),h.r());}});
        routes.entrySet().removeIf(e->e.getValue().isEmpty());
        if(Boolean.TRUE.equals(tx.execute(s->monsterEncounters(now))))battleRevision++;
        broadcast();
    }
    private boolean monsterEncounters(long now){
        boolean changed=false;
        Set<UUID> entered=new HashSet<>(db.query("select id from accounts where entered=true and approved=true",(r,n)->r.getObject(1,UUID.class)));
        for(var monster:characters.all())if(monster.npc()&&monster.alive()&&!battles.engaged(monster.id())&&!battles.blocked(monster.q(),monster.r())){
            var target=characters.all().stream().filter(c->!c.npc()&&entered.contains(c.id())&&(c.alive()||c.life().equals("down"))&&c.protectedUntil()<=now&&c.hex().equals(monster.hex())&&!battles.engaged(c.id())&&!ferry.aboard(c.id())).findFirst();
            if(target.isPresent()){battles.start(monster.id(),target.get().id(),world.version(),world,now);changed=true;}
        }
        if(changed)battles.actorIds().forEach(id->{routes.remove(id);resources.cancel(id);characters.cancelTimer(id);});return changed;
    }
    public synchronized CharacterService.Character character(UUID id){return characters.get(id);}
    public synchronized List<Map<String,Object>> catalog(){return equipment.catalog();}
    public synchronized void equip(UUID id,String code){equip(id,code,null);}
    public synchronized void equip(UUID id,String main,String off){characters.requireAlive(id);ferry.requireAshore(id);if(battles.engaged(id))throw AccountService.bad("战斗中请使用战斗栏换装");tx.executeWithoutResult(s->{equipment.equip(id,main,off);hotbar.sync(id);});broadcast();}
    public synchronized void grant(UUID admin,UUID target,String code,int quantity){accounts.get(target);tx.executeWithoutResult(s->{equipment.give(target,code,quantity);hotbar.sync(target);audit(admin,"grant-item",target+":"+code+":"+quantity);});battleRevision++;broadcast();}
    public synchronized void lifeAction(UUID id,String action,UUID target){
        if(action==null)throw AccountService.bad("请选择行动");
        long now=System.currentTimeMillis();
        tx.executeWithoutResult(s->{
            if(action.equals("surrender")){battles.surrender(id,onlineUsers(),now);routes.remove(id);}
            else if(action.equals("rescue")&&battles.engaged(id))battles.rescue(id,target,onlineUsers(),now);
            else {
                if(battles.engaged(id))throw AccountService.bad("请先离开战斗");
                ferry.requireAshore(id);
                if(routes.containsKey(id))throw AccountService.bad("请先停下");
                switch(action){
                    case "bind" -> characters.bind(id);
                    case "rest","recall" -> {resources.cancel(id);characters.begin(id,action,now);}
                    case "revive" -> {characters.returnToMark(id,now);battles.rejoinAtCell(id,now);}
                    case "cancel" -> characters.cancelTimer(id);
                    case "rescue" -> {characters.requireAlive(id);var a=characters.get(id);var t=characters.get(target);if(t.npc()||!a.hex().equals(t.hex())||battles.engaged(target))throw AccountService.bad("请与倒地旅人处于同一世界格");characters.rescue(target);}
                    case "bandage" -> {characters.requireAlive(id);var a=characters.get(id);UUID healTarget=target==null?id:target;var t=characters.get(healTarget);if(!a.hex().equals(t.hex()))throw AccountService.bad("战斗外绷带只能治疗同格角色");if(!t.alive())throw AccountService.bad("只能治疗站立存活角色");if(t.hp()>=t.maxHp())throw AccountService.bad("目标已满血");equipment.consume(id,"bandage",1);characters.heal(healTarget,4);}
                    default -> throw AccountService.bad("行动不存在");
                }
            }
            hotbar.sync(id);
        });battleRevision++;broadcast();
    }
    public synchronized void battleGuard(UUID actor,UUID battleId){requireBattle(actor,battleId);tx.executeWithoutResult(s->battles.guard(actor,System.currentTimeMillis()));battleRevision++;broadcast();}
    public synchronized void battleCancelGuard(UUID actor,UUID battleId){requireBattle(actor,battleId);tx.executeWithoutResult(s->battles.cancelGuard(actor,System.currentTimeMillis()));battleRevision++;broadcast();}
    public synchronized void battleBandage(UUID actor,UUID target){UUID battleId=battles.currentId(actor);requireBattle(actor,battleId);tx.executeWithoutResult(s->{battles.bandage(actor,target,onlineUsers(),System.currentTimeMillis());hotbar.sync(actor);});battleRevision++;broadcast();}
    public synchronized void battleEquip(UUID actor,String main,String off){UUID battleId=battles.currentId(actor);requireBattle(actor,battleId);tx.executeWithoutResult(s->{battles.equip(actor,main,off,onlineUsers(),System.currentTimeMillis());hotbar.sync(actor);});battleRevision++;broadcast();}
    private void broadcast(){
        if(connections.isEmpty())return;

        List<String> dead=new ArrayList<>();
        connections.forEach((key,c)->{try{if(c.socket.isOpen())c.socket.sendMessage(new TextMessage(json.writeValueAsString(snapshot(c.user))));else dead.add(key);}catch(Exception e){dead.add(key);}});
        for(String id:dead){Connection c=connections.remove(id);if(c!=null){try{c.socket.close();}catch(Exception ignored){}
            if(connections.values().stream().noneMatch(x->x.user.equals(c.user))){routes.remove(c.user);if(battles.engaged(c.user))battleRevision++;}}}
    }
}
