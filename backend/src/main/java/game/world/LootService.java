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
    LootService(JdbcTemplate db){this.db=db;}
    private List<Container> query(String where,Object...args){
        return db.query("select c.*,(select coalesce(sum(quantity),0) from loot_items where container_id=c.id) as item_count from loot_containers c "+where,
            (r,n)->new Container(r.getObject("id",UUID.class),r.getString("kind"),r.getString("name"),r.getString("source_kind"),r.getObject("source_id",UUID.class),r.getString("world_version"),r.getInt("q"),r.getInt("r"),r.getObject("battle_id",UUID.class),(Integer)r.getObject("battle_q"),(Integer)r.getObject("battle_r"),r.getObject("carrier_id",UUID.class),r.getLong("created_at"),(Long)r.getObject("expires_at"),r.getLong("item_count"),r.getInt("strength"),r.getInt("agility"),r.getInt("constitution"),r.getInt("intellect"),r.getInt("perception"),r.getInt("willpower")),args);
    }
    public List<Container> ground(String version,long now){return query("where carrier_id is null and world_version=? and expires_at>? order by created_at,id",version,now);}
    public List<Container> carried(UUID actor){return query("where carrier_id=? order by created_at,id",actor);}
    public List<Container> nearby(Context c,long now){return c.battleId()==null?query("where carrier_id is null and battle_id is null and world_version=? and q=? and r=? and expires_at>? order by created_at,id",c.version(),c.q(),c.r(),now):query("where carrier_id is null and battle_id=? and expires_at>? order by created_at,id",c.battleId(),now);}
    public static boolean sameCell(Container x,Context c){return Objects.equals(x.carrierId(),c.actor())||x.carrierId()==null&&x.version().equals(c.version())&&x.q()==c.q()&&x.r()==c.r()&&Objects.equals(x.battleId(),c.battleId())&&(c.battleId()==null||Objects.equals(x.battleQ(),c.battleQ())&&Objects.equals(x.battleR(),c.battleR()));}
    private Container require(UUID id,Context c,long now){
        if(id==null)throw AccountService.bad("请选择尸体或小袋子");
        var x=query("where id=? for update",id).stream().findFirst().orElseThrow(()->AccountService.bad("此处遗留物已被拿走或消失"));
        if(x.expiresAt()!=null&&x.expiresAt()<=now)throw AccountService.bad("此处遗留物已到期消失");
        if(!sameCell(x,c))throw AccountService.bad("必须站在同一格才能查看和拿取");
        return x;
    }
    public List<Map<String,Object>> contents(UUID id,Context c,long now){require(id,c,now);return db.queryForList("select d.*,i.quantity from loot_items i join item_definitions d on d.code=i.item_code where i.container_id=? order by d.code",id);}
    public void createCorpse(CharacterService.Character c,String version,long now){
        // Player source schema is reserved, but player death deliberately produces no corpse or drops.
        if(!c.npc())return;
        UUID id=UUID.randomUUID();
        db.update("insert into loot_containers(id,kind,name,source_kind,source_id,world_version,q,r,created_at,expires_at,strength,agility,constitution,intellect,perception,willpower) values(?,'corpse',?,'monster',?,?,?,?,?,?,?,?,?,?,?,?)",id,c.name()+"的尸体",c.id(),version,c.q(),c.r(),now,now+GROUND_LIFETIME,c.strength(),c.agility(),c.constitution(),c.intellect(),c.perception(),c.willpower());
        db.update("update loot_containers c set battle_id=a.encounter_id,battle_q=a.q,battle_r=a.r from battle_actors a join battle_encounters b on b.id=a.encounter_id where c.id=? and a.account_id=? and b.active=true",id,c.id());
        // No invented loot table. Future creature inventories can populate loot_items in this transaction.
    }
    public void enterBattle(UUID battle,String version,int q,int r){
        var old=query("where carrier_id is null and battle_id is null and world_version=? and q=? and r=?",version,q,r);
        for(var c:old){int cell=Math.floorMod(c.id().hashCode(),7);WorldMap.Hex h=cell==6?new WorldMap.Hex(0,0):BattleService.DIRECTIONS.get(cell);
            db.update("update loot_containers set battle_id=?,battle_q=?,battle_r=? where id=?",battle,h.q(),h.r(),c.id());}
    }
    public void closeBattle(UUID id){db.update("update loot_containers set battle_id=null,battle_q=null,battle_r=null where battle_id=?",id);}
    public boolean expire(long now){return db.update("delete from loot_containers where carrier_id is null and expires_at<=?",now)>0;}
    public void resetGround(){db.update("delete from loot_containers where carrier_id is null");}
    public void execute(Context c,Command cmd,long now){
        if(cmd.action()==null)throw AccountService.bad("请选择操作");
        if(cmd.action().equals("dropItems")){
            if(c.battleId()!=null)throw AccountService.bad("当前仅开放战斗外丢弃");
            var drops=cmd.items()==null?List.<ItemDrop>of():cmd.items();
            if(drops.isEmpty())throw AccountService.bad("请选择要丢弃的物品");
            for(var d:drops){int amount=d.quantity()==null?0:d.quantity();if(amount<1)throw AccountService.bad("请输入有效数量");
                if(db.update("update inventories set quantity=quantity-? where account_id=? and item_code=? and quantity>=?",amount,c.actor(),d.code(),amount)!=1)throw AccountService.bad("背包物品数量不足");}
            UUID id=UUID.randomUUID();db.update("insert into loot_containers(id,kind,name,world_version,q,r,created_at,expires_at) values(?,'pile','掉落堆',?,?,?,?,?)",id,c.version(),c.q(),c.r(),now,now+GROUND_LIFETIME);
            for(var d:drops)db.update("insert into loot_items values(?,?,?)",id,d.code(),d.quantity());
            return;
        }
        var x=require(cmd.containerId(),c,now);
        switch(cmd.action()){
            case "carry" -> {
                if(!x.kind().equals("corpse")||x.carrierId()!=null)throw AccountService.bad("只能搬走地上的尸体");
                db.update("update loot_containers set carrier_id=?,expires_at=null,battle_id=null,battle_q=null,battle_r=null where id=?",c.actor(),x.id());
            }
            case "dropCorpse" -> {
                if(c.battleId()!=null)throw AccountService.bad("当前仅开放战斗外丢弃");
                if(!Objects.equals(x.carrierId(),c.actor()))throw AccountService.bad("尸体不在你的背包中");
                db.update("update loot_containers set carrier_id=null,world_version=?,q=?,r=?,expires_at=? where id=?",c.version(),c.q(),c.r(),now+GROUND_LIFETIME,x.id());
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
    private void transfer(UUID actor,UUID container,String code,long amount,long remaining){
        db.update("insert into inventories values(?,?,?) on conflict(account_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",actor,code,amount);
        if(amount==remaining)db.update("delete from loot_items where container_id=? and item_code=?",container,code);
        else db.update("update loot_items set quantity=quantity-? where container_id=? and item_code=?",amount,container,code);
    }
}
