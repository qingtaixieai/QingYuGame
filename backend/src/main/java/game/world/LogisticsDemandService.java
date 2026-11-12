package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Derives official needs from real holdings; it never buys, moves, or creates goods. */
@Service
public class LogisticsDemandService {
 private final JdbcTemplate db;
 private final LogisticsLedger ledger;
 LogisticsDemandService(JdbcTemplate db,LogisticsLedger ledger){this.db=db;this.ledger=ledger;}

 @Transactional(propagation=Propagation.MANDATORY)
 public void synchronize(long now){
  db.update("update logistics_needs n set pickup_active=false,last_clock=? where n.state='pickup' and exists(select 1 from battle_actors a join battle_encounters b on b.id=a.encounter_id where a.account_id=n.carrier and b.active)",now);
  for(UUID dead:db.queryForList("select distinct c.id from characters c join logistics_needs n on n.carrier=c.id or n.recipient=c.id where c.life='dead' and n.state in ('open','claimed','pickup')",UUID.class))ledger.actorDied(dead,now);
  for(var n:ledger.query("where state in ('open','claimed','pickup')")){
   if(n.kind().equals("corpse")&&n.state().equals("open")&&n.retry()<=now){
    var owners=db.queryForList("select b.carrier_id from loot_containers b join logistics_agents a on a.actor_id=b.carrier_id join characters c on c.id=a.actor_id where b.id=? and c.life='alive'",UUID.class,n.corpse());
    if(!owners.isEmpty()){ledger.claim(n.id(),n.version(),owners.getFirst(),now);continue;}
   }
   if(n.recipient()!=null&&!configured(n.recipient(),n.post())&&!collected(n))ledger.release(n.id(),"cancelled",now,"已不在官方服务名单");
   else if(n.kind().equals("supply")&&!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from npc_supply_targets where actor=? and code=?)",Boolean.class,n.recipient(),n.code())))ledger.release(n.id(),"cancelled",now,"该物品已不在补给配置中");
   else if(n.kind().equals("corpse")&&!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from loot_containers where id=?)",Boolean.class,n.corpse())))ledger.release(n.id(),"cancelled",now,"尸体已不在世界中");
   else if(n.kind().equals("corpse")&&Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from loot_containers b where b.id=? and (b.carrier_id is null or (b.carrier_id<>? and not exists(select 1 from logistics_agents a join characters c on c.id=a.actor_id where a.actor_id=b.carrier_id and c.life='alive'))))",Boolean.class,n.corpse(),n.recipient())))ledger.release(n.id(),"cancelled",now,"尸体已掉落或易主，原交接失效；实物保留");
  }
  for(var row:db.queryForList("select l.actor_id,l.post_id,t.code,t.quantity-coalesce(i.quantity,0) as shortage,c.hp,c.max_hp from logistics_clients l join characters c on c.id=l.actor_id join npc_supply_targets t on t.actor=c.id left join inventories i on i.character_id=c.id and i.item_code=t.code where l.enabled and c.kind<>'player' and c.life='alive'")){
   UUID actor=(UUID)row.get("actor_id");String code=(String)row.get("code");
   ledger.upsert("supply:"+actor+":"+code,(UUID)row.get("post_id"),"supply",actor,code,null,(int)LogisticsLedger.number(row,"shortage"),LogisticsLedger.number(row,"hp")*2<LogisticsLedger.number(row,"max_hp")?100:20,now);
  }
  for(var row:db.queryForList("select distinct l.actor_id,l.post_id,b.corpse_id from logistics_clients l join characters c on c.id=l.actor_id join logistics_posts p on p.id=l.post_id join loot_containers body on body.carrier_id=c.id join corpse_bounties b on b.corpse_id=body.id and b.faction_id=p.faction_id where l.enabled and c.kind<>'player' and c.life='alive' and not exists(select 1 from loot_items i where i.container_id=body.id and i.quantity>0)")){
   UUID corpse=(UUID)row.get("corpse_id");ledger.upsert("corpse:"+corpse,(UUID)row.get("post_id"),"corpse",(UUID)row.get("actor_id"),null,corpse,1,30,now);
  }
  // Station stock is shared across agents. Assigned client deliveries are not free stock.
  for(var row:db.queryForList("select t.post_id,t.code,t.quantity,coalesce((select sum(greatest(0,i.quantity-reserved_quantity(a.actor_id,t.code))) from logistics_agents a join characters c on c.id=a.actor_id join inventories i on i.character_id=a.actor_id and i.item_code=t.code where a.post_id=t.post_id and c.life='alive'),0)+coalesce((select sum(n.reserved) from logistics_needs n where n.post_id=t.post_id and n.code=t.code and n.kind='stock' and n.state in ('claimed','pickup')),0) as available from logistics_stock_targets t")){
   UUID post=(UUID)row.get("post_id");String code=(String)row.get("code");long shortage=Math.max(0,LogisticsLedger.number(row,"quantity")-LogisticsLedger.number(row,"available"));
   ledger.upsert("stock:"+post+":"+code,post,"stock",null,code,null,(int)Math.min(10000,shortage),0,now);
  }
 }
 private boolean configured(UUID actor,UUID post){return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_clients l join characters c on c.id=l.actor_id where l.actor_id=? and l.post_id=? and l.enabled and c.kind<>'player')",Boolean.class,actor,post));}
 private boolean collected(LogisticsLedger.Need n){return n.kind().equals("corpse")&&Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from loot_containers b join logistics_agents a on a.actor_id=b.carrier_id join characters c on c.id=a.actor_id where b.id=? and c.life='alive')",Boolean.class,n.corpse()));}
}
