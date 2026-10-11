package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Transactions execute inside WorldService's world lock and database transaction. */
@Service
public class ShopService {
    public record Line(String code,int quantity,UUID corpseId) {}
    public record Order(String mode,List<Line> lines) {}
    private final JdbcTemplate db;private final EquipmentService equipment;private final LootService loot;
    private final JsonMapper json=JsonMapper.builder().build();
    ShopService(JdbcTemplate db,EquipmentService equipment,LootService loot){this.db=db;this.equipment=equipment;this.loot=loot;}
    private Set<String> fixed(UUID actor){
        var result=new HashSet<String>();
        for(String doc:db.queryForList("select document from action_layouts where account_id=?",String.class,actor)){
            for(var s:json.readValue(doc,ActionSlots.Layout.class).slots())if(s.locked()&&s.actionRef()!=null){
                String code=switch(s.actionRef()){case "potion"->"healing_potion";case "throw-stone"->"stone";default->s.actionRef().replaceFirst("^(attack:|guard:)","");};result.add(code);
            }
        }return result;
    }
    public Object view(UUID actor){
        return Map.of("items",db.queryForList("select d.*,coalesce(i.quantity,0) as quantity from item_definitions d left join inventories i on i.item_code=d.code and i.character_id=? where d.buy_price is not null or d.sell_price is not null order by d.code",actor),"fixed",fixed(actor),"corpses",corpses(actor),"coins",coins(actor),"unlimited",true);
    }
    private List<Map<String,Object>> corpses(UUID actor){return db.queryForList("select l.id,l.name,(select coalesce(sum(quantity),0) from loot_items where container_id=l.id) as contents,case l.source_species when 'bully' then 5 when 'archer' then 3 else null end as price from loot_containers l where l.carrier_id=? and l.kind='corpse' order by l.id",actor);}
    private Map<String,Long> coins(UUID actor){var result=new LinkedHashMap<String,Long>();for(String c:List.of("copper_coin","silver_coin","gold_coin"))result.put(c,db.queryForObject("select coalesce((select quantity from inventories where character_id=? and item_code=?),0)",Long.class,actor,c));return result;}
    private void setCoin(UUID actor,String code,long quantity){db.update("insert into inventories(character_id,item_code,quantity) values(?,?,?) on conflict(character_id,item_code) do update set quantity=excluded.quantity",actor,code,quantity);}
    public Object transact(UUID actor,Order order){
        if(order==null||order.lines()==null||order.lines().isEmpty()||order.lines().size()>100||!Set.of("buy","sell").contains(Objects.requireNonNullElse(order.mode(),"")))throw AccountService.bad("交易内容无效");
        boolean buy=order.mode().equals("buy");long total=0;Set<String> seen=new HashSet<>(),fixed=fixed(actor);
        var loadout=equipment.loadout(actor);var equipped=Arrays.asList(loadout.mainHand(),loadout.offHand(),loadout.body());
        for(var line:order.lines()){
            if(line==null||line.quantity()<1||line.quantity()>10000)throw AccountService.bad("数量须为1至10000");
            if(line.corpseId()!=null){
                if(buy||line.quantity()!=1||!seen.add("corpse:"+line.corpseId()))throw AccountService.bad("尸体必须独立出售");
                var c=corpses(actor).stream().filter(x->line.corpseId().equals(x.get("id"))).findFirst().orElseThrow(()->AccountService.bad("尸体不在背包中"));
                if(((Number)c.get("contents")).longValue()>0)throw AccountService.bad("请先取空尸体内部物品");
                if(c.get("price")==null)throw AccountService.bad("该种尸体尚未配置收购价格");
                total+=((Number)c.get("price")).longValue();db.update("delete from loot_containers where id=? and carrier_id=?",line.corpseId(),actor);
            }else{
                if(line.code()==null||!seen.add(line.code()))throw AccountService.bad("物品不能重复提交");
                var rows=db.queryForList("select buy_price,sell_price from item_definitions where code=?",line.code());
                if(rows.isEmpty())throw AccountService.bad("物品不存在");Object price=rows.getFirst().get(buy?"buy_price":"sell_price");
                if(price==null)throw AccountService.bad("商店不交易此物品");
                if(!buy&&(fixed.contains(line.code())||equipped.contains(line.code())))throw AccountService.bad("请先卸下或取消固定再出售");
                total+=((Number)price).longValue()*line.quantity();
                if(buy)db.update("insert into inventories(character_id,item_code,quantity) values(?,?,?) on conflict(character_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",actor,line.code(),line.quantity());
                else equipment.consume(actor,line.code(),line.quantity());
            }
        }
        var coins=coins(actor);
        if(buy){var change=CoinPayment.pay(coins.get("copper_coin"),coins.get("silver_coin"),coins.get("gold_coin"),total);setCoin(actor,"copper_coin",change.copper());setCoin(actor,"silver_coin",change.silver());setCoin(actor,"gold_coin",change.gold());}
        else setCoin(actor,"copper_coin",Math.addExact(coins.get("copper_coin"),total));
        return Map.of("total",total,"coins",coins(actor),"message",buy?"购买完成":"出售完成");
    }
}
