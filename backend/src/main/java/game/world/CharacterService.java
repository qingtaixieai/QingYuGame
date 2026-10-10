package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Shared player/creature lifecycle. All writes run in the world's transaction/monitor. */
@Service
public class CharacterService {
    public record Character(UUID id,String kind,String species,String name,int q,int r,int hp,int maxHp,int downHp,int maxDownHp,String life,
        int strength,int agility,int constitution,int intellect,int perception,int willpower,String weapon,String offhand,String body,
        int bindQ,int bindR,Integer deathQ,Integer deathR,String timerKind,long timerEnd,long protectedUntil,
        boolean deployed) {
        public boolean alive(){return life.equals("alive");}
        public boolean npc(){return !kind.equals("player");}
        /** 恶霸：红身、使斧、以老家为圆心游荡。其余生物（利爪怪）走爪子、按登录点那片游荡。 */
        public boolean bully(){return "bully".equals(species);}
        public WorldMap.Hex hex(){return new WorldMap.Hex(q,r);}
    }
    private final JdbcTemplate db;private final EquipmentService equipment;private final LootService loot;
    private final long recallMs,restMs,respawnMs,protectionMs;
    private WorldMap world;
    private final Map<String,List<WorldMap.Hex>> roamCache=new HashMap<>();
    public static final UUID ISLAND_BEAST=UUID.fromString("a3347a88-6099-42bf-9d66-000000000001");
    CharacterService(JdbcTemplate db,EquipmentService equipment,LootService loot,@Value("${game.recall-ms:5000}") long recall,
        @Value("${game.rest-ms:10000}") long rest,@Value("${game.monster.respawn-ms:60000}") long respawn,
        @Value("${game.revive-protection-ms:5000}") long protection){this.db=db;this.equipment=equipment;this.loot=loot;recallMs=recall;restMs=rest;respawnMs=respawn;protectionMs=protection;}
    public void initialize(WorldMap world,long now){
        this.world=world;roamCache.clear();ensurePlayers();
        // Timers freeze during downtime; a reconnect never auto-revives a soul.
        db.update("update characters set timer_end=?+greatest(0,timer_end-saved_at) where timer_end>0 and saved_at>0",now);
        db.update("update characters set protected_until=?+greatest(0,protected_until-saved_at) where protected_until>saved_at and saved_at>0",now);
        db.update("update characters set saved_at=?",now);
        var area=roamingArea();if(!area.isEmpty()){var h=area.getFirst();db.update("insert into characters(id,kind,species,name,q,r,hp,max_hp,down_hp,max_down_hp,bind_q,bind_r,saved_at) values(?,'monster','bully','恶霸',?,?,12,12,6,6,?,?,?) on conflict(id) do nothing",ISLAND_BEAST,h.q(),h.r(),h.q(),h.r(),now);
            if(db.queryForObject("select weapon is null from characters where id=?",Boolean.class,ISLAND_BEAST))kit(ISLAND_BEAST);}
    }
    public void ensurePlayers(){db.update("insert into characters(id,account_id,bind_q,bind_r) select id,id,?,? from accounts on conflict(id) do nothing",world.spawn().q(),world.spawn().r());}
    private static final String PROFILE_SQL="select c.*,coalesce(a.username,c.name) as display_name,coalesce(a.q,c.q) as world_q,coalesce(a.r,c.r) as world_r from characters c left join accounts a on a.id=c.account_id";
    private static final org.springframework.jdbc.core.RowMapper<Character> PROFILE=(r,n)->new Character(r.getObject("id",UUID.class),r.getString("kind"),r.getString("species"),r.getString("display_name"),r.getInt("world_q"),r.getInt("world_r"),r.getInt("hp"),r.getInt("max_hp"),r.getInt("down_hp"),r.getInt("max_down_hp"),r.getString("life"),r.getInt("strength"),r.getInt("agility"),r.getInt("constitution"),r.getInt("intellect"),r.getInt("perception"),r.getInt("willpower"),r.getString("weapon"),r.getString("offhand"),r.getString("body"),r.getInt("bind_q"),r.getInt("bind_r"),(Integer)r.getObject("death_q"),(Integer)r.getObject("death_r"),r.getString("timer_kind"),r.getLong("timer_end"),r.getLong("protected_until"),r.getBoolean("deployed"));
    public List<Character> all(){return db.query(PROFILE_SQL,PROFILE);}
    public Character get(UUID id){return db.query(PROFILE_SQL+" where c.id=?",PROFILE,id).stream().findFirst().orElseThrow(()->AccountService.bad("角色不存在"));}
    public WeaponRules.Weapon weapon(UUID id){var c=get(id);var w=equipment.loadout(id).weapon();return c.npc()&&w.code().equals("unarmed")?WeaponRules.CLAWS:w;}
    /** 生物（NPC）装备与背包：出生/重生时刷新。恶霸使斧、背包各 1-4 石木；利爪怪用爪（weapon 置空走 fallback）、各 0-3 石木；绷带均 0-2。与玩家共用同一套背包。 */
    public void kit(UUID id){
        var c=get(id);
        db.update("delete from inventories where character_id=?",id);
        if("archer".equals(c.species())){
            db.update("update characters set weapon='short_bow',offhand=null,body='light_armor' where id=?",id);
            give(id,"arrow",12);give(id,"healing_potion",1);return;
        }
        var rnd=new java.security.SecureRandom();
        if(c.bully()){
            db.update("update characters set weapon='axe',offhand=null where id=?",id);
            give(id,"stone",1+rnd.nextInt(4));give(id,"wood",1+rnd.nextInt(4));
        }else{
            db.update("update characters set weapon=null,offhand=null where id=?",id);
            give(id,"stone",rnd.nextInt(4));give(id,"wood",rnd.nextInt(4));
        }
        give(id,"bandage",rnd.nextInt(3));
    }
    /** 管理员在固定地点投放一只生物。species：'bully' 恶霸 / 'claw' 利爪怪。投放的怪记为 deployed，死后不重生。 */
    public Character deploy(String species,int q,int r,long now){
        UUID id=UUID.randomUUID();String name=switch(species){case "bully"->"恶霸";case "archer"->"弓箭手";default->"利爪怪";};
        db.update("insert into characters(id,kind,species,deployed,name,q,r,hp,max_hp,down_hp,max_down_hp,bind_q,bind_r,saved_at) values(?,'monster',?,true,?,?,?,12,12,6,6,?,?,?)",id,species,name,q,r,q,r,now);
        kit(id);return get(id);
    }
    private void give(UUID id,String code,int quantity){if(quantity<=0)return;equipment.give(id,code,quantity);}
    public EquipmentService.Loadout loadout(UUID id){return equipment.loadout(id);}
    public void requireAlive(UUID id){if(!get(id).alive())throw AccountService.bad("倒地或灵魂状态不能执行此行动");}
    public void relocate(UUID id,int q,int r){var c=get(id);if(c.npc())db.update("update characters set q=?,r=? where id=?",q,r,id);else db.update("update accounts set q=?,r=? where id=?",q,r,id);}
    public void cancelTimer(UUID id){db.update("update characters set timer_kind=null,timer_end=0 where id=? and life<>'dead'",id);}
    public void hostile(UUID id){db.update("update characters set protected_until=0 where id=?",id);cancelTimer(id);}
    public String damage(UUID id,int amount,long now){
        var c=get(id);if(c.life.equals("soul")||c.life.equals("dead")||c.protectedUntil>now)return "protected";
        cancelTimer(id);
        if(c.alive()){
            int hp=Math.max(0,c.hp-amount);db.update("update characters set hp=?,life=? where id=?",hp,hp==0?"down":"alive",id);
            return hp==0?"down":"hit";
        }
        int hp=Math.max(0,c.downHp-amount);db.update("update characters set down_hp=? where id=?",hp,id);
        if(hp==0){die(id,now);return "death";}return "hit";
    }
    public String heal(UUID id,int amount){
        var c=get(id);
        if(!c.life.equals("alive"))throw AccountService.bad("只能治疗站立存活角色");
        if(c.hp>=c.maxHp)return "full";
        db.update("update characters set hp=least(max_hp,hp+?) where id=?",amount,id);
        return "healed";
    }
    public void die(UUID id,long now){
        var c=get(id);if(c.life.equals("soul")||c.life.equals("dead"))return;
        loot.createCorpse(c,world.version(),now);
        db.update("update characters set life=?,hp=0,down_hp=0,death_q=?,death_r=?,timer_kind=?,timer_end=?,protected_until=0,saved_at=? where id=?",c.npc()?"dead":"soul",c.q,c.r,c.npc()?"respawn":null,c.npc()?now+respawnMs:0,now,id);
        db.update("delete from world_actions where account_id=?",id);db.update("delete from ferry_passengers where account_id=?",id);
        if(!c.npc())relocate(id,c.bindQ,c.bindR);
    }
    public void rescue(UUID id){var c=get(id);if(!c.life.equals("down"))throw AccountService.bad("目标没有倒地");db.update("update characters set life='alive',hp=least(max_hp,5) where id=?",id);}
    public void bind(UUID id){requireAlive(id);var c=get(id);var tile=world.index().get(c.hex());if(tile==null||tile.place()==null||!tile.place().type().equals("town"))throw AccountService.bad("请先到达城镇");db.update("update characters set bind_q=?,bind_r=? where id=?",c.q,c.r,id);}
    public void begin(UUID id,String kind,long now){
        var c=get(id);
        if(kind.equals("recall")){if(!c.life.equals("soul"))throw AccountService.bad("只有灵魂可以回城");}
        else if(kind.equals("rest")){requireAlive(id);var t=world.index().get(c.hex());if(t==null||t.place()==null||!t.place().type().equals("town"))throw AccountService.bad("请在城镇休整");}
        else throw AccountService.bad("行动不存在");
        db.update("update characters set timer_kind=?,timer_end=?,saved_at=? where id=?",kind,now+(kind.equals("recall")?recallMs:restMs),now,id);
    }
    public void returnToMark(UUID id,long now){var c=get(id);if(!c.life.equals("soul")||c.deathQ==null||c.q!=c.deathQ||c.r!=c.deathR)throw AccountService.bad("请先到达死亡标记");revive(id,now);}
    private void revive(UUID id,long now){db.update("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,death_q=null,death_r=null,timer_kind=null,timer_end=0,protected_until=? where id=?",now+protectionMs,id);}
    public boolean advance(long now,Set<UUID> online){
        boolean changed=false;
        for(var c:all())if(c.timerEnd>0&&c.timerEnd<=now){
            if("recall".equals(c.timerKind)){if(!online.contains(c.id)){cancelTimer(c.id);changed=true;continue;}relocate(c.id,c.bindQ,c.bindR);revive(c.id,now);}
            else if("rest".equals(c.timerKind))db.update("update characters set hp=max_hp,down_hp=max_down_hp,timer_kind=null,timer_end=0 where id=?",c.id);
            else continue;
            changed=true;
        }
        db.update("update characters set saved_at=? where timer_end>0 or protected_until>?",now,now);return changed;
    }
    /** 以某点为圆心、minDist~6 格内、可通行、无地标的格子。世界生成后固定，缓存复用。 */
    private List<WorldMap.Hex> areaAround(WorldMap.Hex start,int minDist){
        return roamCache.computeIfAbsent(start.q()+","+start.r()+":"+minDist,key->{
            var index=world.index();
            if(!index.containsKey(start)||!index.get(start).walkable())return List.of();
            Set<WorldMap.Hex> seen=new HashSet<>();ArrayDeque<WorldMap.Hex> queue=new ArrayDeque<>();queue.add(start);seen.add(start);
            while(!queue.isEmpty()){var h=queue.remove();for(var n:h.neighbors())if(!seen.contains(n)&&index.containsKey(n)&&index.get(n).walkable()){seen.add(n);queue.add(n);}}
            return seen.stream().filter(h->WeaponRules.distance(h,start)>=minDist&&WeaponRules.distance(h,start)<=6&&index.get(h).place()==null).sorted(Comparator.comparingInt(WorldMap.Hex::q).thenComparingInt(WorldMap.Hex::r)).toList();
        });
    }
    /** 自动那只的出生点：登录点（landing）周边 2~6 格那片（避开紧贴码头的格子）。 */
    public List<WorldMap.Hex> roamingArea(){var dock=world.place("landing");return dock==null?List.of():areaAround(dock.hex(),2);}
    /** 所有生物的游荡区：以老家（投放/出生点，即 bind 点）为圆心、1~6 格内（含相邻那圈，否则站在老家会卡死）。 */
    private List<WorldMap.Hex> homeArea(Character c){return areaAround(new WorldMap.Hex(c.bindQ,c.bindR),1);}
    public boolean wander(long now,Set<UUID> engaged){
        boolean changed=false;
        for(var c:all())if(c.npc()&&!engaged.contains(c.id)){var area=homeArea(c);if(area.isEmpty())continue;
            if(c.life.equals("dead")&&c.timerEnd<=now){
                if(c.deployed)continue; // 管理员投放的怪不重生，只留尸体
                var occupied=new HashSet<>(all().stream().filter(x->!x.id.equals(c.id)&&!x.life.equals("soul")&&!x.life.equals("dead")).map(Character::hex).toList());var free=new ArrayList<>(area.stream().filter(h->!occupied.contains(h)).toList());if(free.isEmpty())continue;Collections.shuffle(free);var h=free.getFirst();relocate(c.id,h.q(),h.r());revive(c.id,now);kit(c.id);db.update("update characters set protected_until=0 where id=?",c.id);changed=true;}
            else if(c.alive()&&db.queryForObject("select next_move from characters where id=?",Long.class,c.id)<=now){var options=new ArrayList<>(c.hex().neighbors().stream().filter(area::contains).toList());if(!options.isEmpty()){Collections.shuffle(options);var h=options.getFirst();relocate(c.id,h.q(),h.r());changed=true;}db.update("update characters set next_move=? where id=?",now+4000,c.id);}
        }return changed;
    }
    public void reset(WorldMap next,long now){world=next;db.update("delete from characters where kind<>'player'");db.update("update characters set life='alive',hp=max_hp,down_hp=max_down_hp,bind_q=?,bind_r=?,death_q=null,death_r=null,timer_kind=null,timer_end=0,protected_until=0",next.spawn().q(),next.spawn().r());initialize(next,now);}
}
