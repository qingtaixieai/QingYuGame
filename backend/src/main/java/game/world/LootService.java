package game.world;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Instance containers. Called under WorldService's monitor/transaction; row locks also serialize claims. */
@Service
public class LootService {
    public static final long GROUND_LIFETIME=30*60*1000L;
    public record Container(UUID id,String kind,String name,String sourceKind,UUID sourceId,String version,int q,int r,
        UUID battleId,Integer battleQ,Integer battleR,UUID carrierId,long createdAt,Long expiresAt,long itemCount,
        int strength,int agility,int constitution,int intellect,int perception,int willpower) {}
    public record Context(UUID actor,String version,int q,int r,UUID battleId,Integer battleQ,Integer battleR) {}
    public record ItemDrop(String code,Integer quantity) {}
    public record Command(String action,UUID containerId,String code,Integer quantity,String version,UUID battleId,List<ItemDrop> items) {}
    private final JdbcTemplate db;
    @org.springframework.beans.factory.annotation.Autowired private LawService law;
    LootService(JdbcTemplate db){this.db=db;}
    private List<Container> query(String where,Object...args){
        return db.query("select c.*,(select coalesce(sum(quantity),0) from loot_items where container_id=c.id) as item_count from loot_containers c "+where,
            (r,n)->new Container(r.getObject("id",UUID.class),r.getString("kind"),r.getString("name"),r.getString("source_kind"),r.getObject("source_id",UUID.class),r.getString("world_version"),r.getInt("q"),r.getInt("r"),r.getObject("battle_id",UUID.class),(Integer)r.getObject("battle_q"),(Integer)r.getObject("battle_r"),r.getObject("carrier_id",UUID.class),r.getLong("created_at"),(Long)r.getObject("expires_at"),r.getLong("item_count"),r.getInt("strength"),r.getInt("agility"),r.getInt("constitution"),r.getInt("intellect"),r.getInt("perception"),r.getInt("willpower")),args);
    }
    public List<Container> ground(String version,long now){return query("where carrier_id is null and (scene_id is null or battle_id is not null) and world_version=? and expires_at>? order by created_at,id",version,now);}
    public List<Container> carried(UUID actor){return query("where carrier_id=? order by created_at,id",actor);}
    private UUID localScene(UUID actor){return db.queryForList("select scene_id from scene_positions where character_id=? and active",UUID.class,actor).stream().findFirst().orElse(null);}
    public List<Container> nearby(Context c,long now){return c.battleId()==null?query("where carrier_id is null and battle_id is null and world_version=? and q=? and r=? and scene_id is not distinct from ? and expires_at>? order by created_at,id",c.version(),c.q(),c.r(),localScene(c.actor()),now):query("where carrier_id is null and battle_id=? and expires_at>? order by created_at,id",c.battleId(),now);}
    public static boolean sameCell(Container x,Context c){return Objects.equals(x.carrierId(),c.actor())||x.carrierId()==null&&x.version().equals(c.version())&&x.q()==c.q()&&x.r()==c.r()&&Objects.equals(x.battleId(),c.battleId())&&(c.battleId()==null||Objects.equals(x.battleQ(),c.battleQ())&&Objects.equals(x.battleR(),c.battleR()));}
    private Container require(UUID id,Context c,long now){
        if(id==null)throw AccountService.bad("请选择尸体或小袋子");
        if(db.queryForObject("select exists(select 1 from handover_corpses where corpse_id=?)",Boolean.class,id))throw AccountService.bad("尸体已预留给当面交接，请先取消交接");
        var x=query("where id=? for update",id).stream().findFirst().orElseThrow(()->AccountService.bad("此处遗留物已被拿走或消失"));
        if(x.expiresAt()!=null&&x.expiresAt()<=now)throw AccountService.bad("此处遗留物已到期消失");
        if(!sameCell(x,c))throw AccountService.bad("必须站在同一格才能查看和拿取");
        if(x.carrierId()==null&&c.battleId()==null){var scene=db.queryForObject("select scene_id from loot_containers where id=?",UUID.class,id);if(!Objects.equals(scene,localScene(c.actor())))throw AccountService.bad("遗留物不在当前场景");if(scene!=null&&db.queryForObject("select count(*) from scene_positions where character_id=? and active and q=? and r=?",Integer.class,c.actor(),x.battleQ(),x.battleR())!=1)throw AccountService.bad("请走到遗留物所在格");}
        return x;
    }
    public List<Map<String,Object>> contents(UUID id,Context c,long now){require(id,c,now);return db.queryForList("select d.*,i.quantity from loot_items i join item_definitions d on d.code=i.item_code where i.container_id=? order by d.code",id);}
    public void createCorpse(CharacterService.Character c,String version,long now){
        db.update("delete from production_tasks where actor_id=?",c.id());
        db.update("update handovers set state='cancelled' where state='pending' and (sender=? or recipient=?)",c.id(),c.id());
        UUID id=UUID.randomUUID();
        db.update("insert into loot_containers(id,kind,name,source_kind,source_id,world_version,q,r,created_at,expires_at,strength,agility,constitution,intellect,perception,willpower) values(?,'corpse',?,'monster',?,?,?,?,?,?,?,?,?,?,?,?)",id,c.name()+"的尸体",c.id(),version,c.q(),c.r(),now,now+GROUND_LIFETIME,c.strength(),c.agility(),c.constitution(),c.intellect(),c.perception(),c.willpower());
        db.update("update loot_containers set source_species=? where id=?",c.species(),id);
        db.update("update loot_containers set skinnable=exists(select 1 from creature_harvest where species=?) where id=?",c.species(),id);
        db.update("update loot_containers c set battle_id=a.encounter_id,battle_q=a.q,battle_r=a.r from battle_actors a join battle_encounters b on b.id=a.encounter_id where c.id=? and a.account_id=? and b.active=true",id,c.id());
        markScene(id,c.id());
        if(!c.npc())db.update("update loot_containers set source_kind='player' where id=?",id);
        law.snapshot(id,c.id(),now);
        // Players keep their inventory/equipment; their unique corpse carries legal entitlement only.
        if(!c.npc())return;
        // Carried corpses remain independent instances when an NPC carrier dies.
        // Drop them at the actual death scene with their preserved remaining lifetime.
        db.update("update loot_containers carried set carrier_id=null,world_version=body.world_version,q=body.q,r=body.r,scene_id=body.scene_id,battle_id=body.battle_id,battle_q=body.battle_q,battle_r=body.battle_r,expires_at=?+carried.remaining_ms from loot_containers body where body.id=? and carried.carrier_id=?",now,id,c.id());
        // 生物背包物品随尸体掉落；装备的武器也一并进入尸体供玩家拾取。
        for(var row:db.queryForList("select item_code,quantity from inventories where character_id=? and quantity>0",c.id()))
            db.update("insert into loot_items(container_id,item_code,quantity) values(?,?,?) on conflict(container_id,item_code) do update set quantity=loot_items.quantity+excluded.quantity",id,row.get("item_code"),row.get("quantity"));
        for(String code:new String[]{c.weapon(),c.offhand(),c.body()})if(code!=null&&!Set.of("unarmed","claws").contains(code))
            db.update("insert into loot_items(container_id,item_code,quantity) values(?,?,1) on conflict(container_id,item_code) do update set quantity=loot_items.quantity+1",id,code);
        db.update("delete from inventories where character_id=?",c.id());
        db.update("update characters set weapon=null,offhand=null,body=null where id=?",c.id());
    }
    public void enterBattle(UUID battle,String version,int q,int r){
        var scene=db.queryForObject("select scene_id from battle_encounters where id=?",UUID.class,battle);
        if(scene!=null){db.update("update loot_containers set battle_id=? where scene_id=? and carrier_id is null and battle_id is null",battle,scene);return;}
        var old=query("where carrier_id is null and battle_id is null and world_version=? and q=? and r=?",version,q,r);
        for(var c:old){int cell=Math.floorMod(c.id().hashCode(),7);WorldMap.Hex h=cell==6?new WorldMap.Hex(0,0):BattleService.DIRECTIONS.get(cell);
            db.update("update loot_containers set battle_id=?,battle_q=?,battle_r=? where id=?",battle,h.q(),h.r(),c.id());}
    }
    public void closeBattle(UUID id){db.update("update loot_containers set battle_id=null,battle_q=case when scene_id is null then null else battle_q end,battle_r=case when scene_id is null then null else battle_r end where battle_id=?",id);}
    private void markScene(UUID container,UUID actor){db.update("update loot_containers set scene_id=null,battle_q=null,battle_r=null where id=? and battle_id is null",container);db.update("update loot_containers c set scene_id=p.scene_id,battle_q=p.q,battle_r=p.r from scene_positions p where c.id=? and p.character_id=? and p.active",container,actor);}
    public boolean expire(long now){return db.update("delete from loot_containers c where carrier_id is null and expires_at<=? and not exists(select 1 from production_tasks t where t.corpse_id=c.id)",now)>0;}
    public void resetGround(){db.update("delete from loot_containers where carrier_id is null");}
    public void execute(Context c,Command cmd,long now){
        if(cmd.action()==null)throw AccountService.bad("请选择操作");
        if(cmd.action().equals("dropItems")){
            if(c.battleId()!=null)throw AccountService.bad("当前仅开放战斗外丢弃");
            var drops=cmd.items()==null?List.<ItemDrop>of():cmd.items();
            if(drops.isEmpty())throw AccountService.bad("请选择要丢弃的物品");
            for(var d:drops){int amount=d.quantity()==null?0:d.quantity();if(amount<1)throw AccountService.bad("请输入有效数量");
                if(db.update("update inventories set quantity=quantity-? where character_id=? and item_code=? and quantity-reserved_quantity(character_id,item_code)>=?",amount,c.actor(),d.code(),amount)!=1)throw AccountService.bad("背包可用物品数量不足");}
            UUID id=UUID.randomUUID();db.update("insert into loot_containers(id,kind,name,world_version,q,r,created_at,expires_at) values(?,'pile','掉落堆',?,?,?,?,?)",id,c.version(),c.q(),c.r(),now,now+GROUND_LIFETIME);
            markScene(id,c.actor());
            for(var d:drops)db.update("insert into loot_items values(?,?,?)",id,d.code(),d.quantity());
            return;
        }
        var x=require(cmd.containerId(),c,now);
        if(db.queryForObject("select exists(select 1 from production_tasks where corpse_id=?)",Boolean.class,x.id()))throw AccountService.bad("尸体正在剥皮，请先取消任务");
        switch(cmd.action()){
            case "carry" -> {
                if(!x.kind().equals("corpse")||x.carrierId()!=null)throw AccountService.bad("只能搬走地上的尸体");
                if(x.itemCount()>0)throw AccountService.bad("请先取空尸体中的物品再收纳");
                db.update("update loot_containers set carrier_id=?,remaining_ms=greatest(0,expires_at-?),expires_at=null,battle_id=null,battle_q=null,battle_r=null,scene_id=null where id=?",c.actor(),now,x.id());
            }
            case "dropCorpse" -> {
                if(c.battleId()!=null)throw AccountService.bad("当前仅开放战斗外丢弃");
                if(!Objects.equals(x.carrierId(),c.actor()))throw AccountService.bad("尸体不在你的背包中");
                db.update("update loot_containers set carrier_id=null,world_version=?,q=?,r=?,expires_at=?+remaining_ms where id=?",c.version(),c.q(),c.r(),now,x.id());
                markScene(x.id(),c.actor());
            }
            case "take","takeAll" -> {
                var items=db.queryForList("select item_code,quantity from loot_items where container_id=?",x.id());
                if(items.isEmpty())throw AccountService.bad("已经没有可拿取的物品");
                if(cmd.action().equals("take")){
                    int amount=cmd.quantity()==null?0:cmd.quantity();if(amount<1)throw AccountService.bad("请输入有效数量");
                    var row=items.stream().filter(i->Objects.equals(i.get("item_code"),cmd.code())).findFirst().orElseThrow(()->AccountService.bad("该物品已被拿走"));
                    long remaining=((Number)row.get("quantity")).longValue();if(amount>remaining)throw AccountService.bad("剩余物品不足，请重新选择");
                    transfer(c.actor(),x.id(),cmd.code(),amount,remaining);
                }else for(var row:items){long amount=((Number)row.get("quantity")).longValue();transfer(c.actor(),x.id(),(String)row.get("item_code"),amount,amount);}
                if(x.kind().equals("pile"))db.update("delete from loot_containers where id=? and not exists(select 1 from loot_items where container_id=?)",x.id(),x.id());
            }
            default -> throw AccountService.bad("不支持的遗留物操作");
        }
    }
    public void requireSkinnable(UUID id,Context context,long now){
        var c=require(id,context,now);
        if(!c.kind().equals("corpse")||!Boolean.TRUE.equals(db.queryForObject("select skinnable from loot_containers where id=?",Boolean.class,id)))throw AccountService.bad("只有指定毛皮兽的尸体可以剥皮");
        if(db.queryForObject("select exists(select 1 from production_tasks where corpse_id=?)",Boolean.class,id))throw AccountService.bad("尸体正在被剥皮");
    }
    public void finishSkin(UUID id,UUID actor,boolean success,long now){
        if(!db.queryForObject("select exists(select 1 from production_tasks where actor_id=? and corpse_id=? and kind='skin')",Boolean.class,actor,id))throw AccountService.bad("剥皮任务已失效");
        var x=query("where id=? for update",id).getFirst();
        if(x.itemCount()>0){
            if(x.carrierId()!=null)throw AccountService.bad("携带尸体不能含有内部物品");
            UUID bag=UUID.randomUUID();db.update("insert into loot_containers(id,kind,name,world_version,q,r,battle_id,battle_q,battle_r,scene_id,created_at,expires_at) select ?,'pile','剥皮遗留物',world_version,q,r,battle_id,battle_q,battle_r,scene_id,?,? from loot_containers where id=?",bag,now,now+GROUND_LIFETIME,id);
            db.update("update loot_items set container_id=? where container_id=?",bag,id);
        }
        if(success)db.update("insert into inventories values(?,'hide',1) on conflict(character_id,item_code) do update set quantity=inventories.quantity+1",actor);
        db.update("delete from loot_containers where id=?",id);
    }
    private void transfer(UUID actor,UUID container,String code,long amount,long remaining){
        db.update("insert into inventories values(?,?,?) on conflict(character_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",actor,code,amount);
        if(amount==remaining)db.update("delete from loot_items where container_id=? and item_code=?",container,code);
        else db.update("update loot_items set quantity=quantity-? where container_id=? and item_code=?",amount,container,code);
    }
}
