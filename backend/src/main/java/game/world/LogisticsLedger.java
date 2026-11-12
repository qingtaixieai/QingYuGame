package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** Persistent ownership and accounting. Call under the world lock and transaction. Never moves goods. */
@Service
public class LogisticsLedger {
 public record Need(long id,String key,UUID post,String kind,UUID recipient,String code,UUID corpse,int remaining,int delivered,int urgency,long created,int version,String state,UUID carrier,int reserved,long blocked,long retry,long waited,long clock,boolean pickupActive,String message){}
 private final JdbcTemplate db;private final EquipmentService equipment;
 LogisticsLedger(JdbcTemplate db,EquipmentService equipment){this.db=db;this.equipment=equipment;}
 public List<Need> query(String where,Object...args){return db.query("select * from logistics_needs "+where,(r,n)->new Need(r.getLong("id"),r.getString("need_key"),r.getObject("post_id",UUID.class),r.getString("kind"),r.getObject("recipient",UUID.class),r.getString("code"),r.getObject("corpse_id",UUID.class),r.getInt("remaining"),r.getInt("delivered"),r.getInt("urgency"),r.getLong("created_at"),r.getInt("version"),r.getString("state"),r.getObject("carrier",UUID.class),r.getInt("reserved"),r.getLong("blocked_since"),r.getLong("retry_after"),r.getLong("wait_elapsed"),r.getLong("last_clock"),r.getBoolean("pickup_active"),r.getString("message")),args);}
 public Need get(long id){return query("where id=?",id).stream().findFirst().orElseThrow(()->AccountService.bad("后勤需求不存在"));}
 @Transactional(propagation=Propagation.MANDATORY)
 public long upsert(String key,UUID post,String kind,UUID recipient,String code,UUID corpse,int shortage,int urgency,long now){
  int quantity=Math.max(0,Math.min(10000,shortage));
  var active=query("where need_key=? and state in ('open','claimed','pickup') for update",key);
  if(!active.isEmpty()){
   var n=active.getFirst();if(quantity==0){release(n.id(),"done",now,"实际缺口已满足");return n.id();}
   if(quantity!=n.remaining()){
    db.update("update handovers set state='cancelled' where logistics_need=? and state='pending'",n.id());
    db.update("update logistics_needs set remaining=?,reserved=least(reserved,?),urgency=?,version=version+1 where id=?",quantity,quantity,urgency,n.id());
   }else db.update("update logistics_needs set urgency=? where id=?",urgency,n.id());
   return n.id();
  }
  if(quantity==0)return 0;
  return db.queryForObject("insert into logistics_needs(need_key,post_id,kind,recipient,code,corpse_id,remaining,urgency,created_at) values(?,?,?,?,?,?,?,?,?) returning id",Long.class,key,post,kind,recipient,code,corpse,quantity,urgency,now);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public Need claim(long id,int version,UUID actor,long now){
  if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_agents a join characters c on c.id=a.actor_id where a.actor_id=? and c.life='alive')",Boolean.class,actor)))throw AccountService.bad("运输者不可认领");
  if(db.update("update logistics_needs set carrier=?,state='claimed',version=version+1,blocked_since=0,last_clock=?,message='' where id=? and version=? and state='open' and retry_after<=?",actor,now,id,version,now)!=1)throw AccountService.bad("需求已被认领或版本失效");return get(id);
 }
 public Need require(long id,int version,UUID actor){var n=query("where id=? for update",id).stream().findFirst().orElseThrow(()->AccountService.bad("任务不存在"));if(n.version()!=version||!Objects.equals(n.carrier(),actor)||!Set.of("claimed","pickup").contains(n.state()))throw AccountService.bad("后勤认领已失效");return n;}
 @Transactional(propagation=Propagation.MANDATORY)
 public int reserve(long id,int version,UUID actor){var n=require(id,version,actor);if(n.code()==null)return 0;int add=LogisticsPolicy.allocate(equipment.available(actor,n.code()),n.remaining()-n.reserved());if(add>0)db.update("update logistics_needs set reserved=reserved+? where id=?",add,id);return n.reserved()+add;}
 /** Release only this task's reservation immediately before the common handover, in the same transaction. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void releaseForHandover(long id,int version,UUID actor,int quantity){var n=require(id,version,actor);if(quantity<1||quantity>n.reserved())throw AccountService.bad("任务预留数量不足");db.update("update logistics_needs set reserved=reserved-? where id=?",quantity,id);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void release(long id,String state,long now,String message){
  if(!Set.of("open","done","cancelled").contains(state))throw new IllegalArgumentException("Invalid release state");
  db.update("update handovers set state='cancelled' where logistics_need=? and state='pending'",id);
  db.update("update logistics_needs set carrier=null,reserved=0,state=?,version=version+1,blocked_since=0,wait_elapsed=0,last_clock=?,pickup_active=false,retry_after=?,message=? where id=? and state in ('open','claimed','pickup')",state,now,state.equals("open")?now+10000:0,message,id);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void actorDied(UUID actor,long now){
  for(var n:query("where (carrier=? or recipient=?) and state in ('open','claimed','pickup')",actor,actor)){
   // Once custody changed, the original guard is no longer needed for delivery.
   boolean alreadyCollected=n.kind().equals("corpse")&&!Objects.equals(n.carrier(),actor)&&Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from loot_containers b join logistics_agents a on a.actor_id=b.carrier_id join characters c on c.id=a.actor_id where b.id=? and b.carrier_id<>? and c.life='alive')",Boolean.class,n.corpse(),actor));
   if(!alreadyCollected)release(n.id(),Objects.equals(n.recipient(),actor)?"cancelled":"open",now,"角色死亡，未完成部分已解除");
  }
  db.update("delete from logistics_stops where requester=? or target=?",actor,actor);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void delivered(long id,int version,UUID actor,int quantity,UUID receipt,long now){
  if(recorded(receipt))return;var n=require(id,version,actor);if(quantity<1||quantity>n.remaining())throw AccountService.bad("交付数量无效");
  db.update("update logistics_needs set remaining=remaining-?,delivered=delivered+?,reserved=least(reserved,remaining-?),blocked_since=0,wait_elapsed=0,last_clock=? where id=?",quantity,quantity,quantity,now,id);
  event(receipt,id,actor,"delivered",n.code(),quantity,0,now,"");if(n.remaining()==quantity)release(id,"done",now,"已完成");
 }
 public boolean recorded(UUID receipt){return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_events where request_id=?)",Boolean.class,receipt));}
 @Transactional(propagation=Propagation.MANDATORY)
 public void event(UUID receipt,Long need,UUID actor,String kind,String code,int quantity,long copper,long now,String details){db.update("insert into logistics_events(request_id,need_id,actor,kind,code,quantity,copper,happened_at,details) values(?,?,?,?,?,?,?,?,?)",receipt,need,actor,kind,code,quantity,copper,now,details);}
 @Transactional(propagation=Propagation.MANDATORY)
 public void purchase(UUID actor,UUID post,String code,int quantity,long unitCost,UUID receipt,long now){
  if(recorded(receipt))return;String funding=db.queryForObject("select funding from logistics_posts where id=?",String.class,post);
  db.update("insert into logistics_lots(owner,code,quantity,unit_cost,funding,fund_post,fund_actor,created_at) values(?,?,?,?,?,?,?,?)",actor,code,quantity,unitCost,funding,funding.equals("official")?post:null,funding.equals("agent")?actor:null,now);
  event(receipt,null,actor,"purchase",code,quantity,Math.multiplyExact(unitCost,quantity),now,funding);
 }
 /** Record previously untracked owned goods at zero procurement cost; no goods or coins are created. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void reconcileLots(UUID actor,long now){
  for(var row:db.queryForList("select code,sum(quantity) as quantity from logistics_lots where owner=? group by code",actor)){
   String code=(String)row.get("code");long physical=db.queryForObject("select coalesce((select quantity from inventories where character_id=? and item_code=?),0)",Long.class,actor,code),extra=((Number)row.get("quantity")).longValue()-physical;
   if(extra>0)for(var lot:lots(actor,code)){int take=(int)Math.min(extra,number(lot,"quantity"));db.update("update logistics_lots set quantity=quantity-? where id=?",take,lot.get("id"));extra-=take;if(extra==0)break;}
  }
  for(var row:db.queryForList("select i.item_code,i.quantity-coalesce((select sum(l.quantity) from logistics_lots l where l.owner=i.character_id and l.code=i.item_code),0) as untracked from inventories i join item_definitions d on d.code=i.item_code where i.character_id=? and d.kind<>'currency'",actor)){
   long missing=number(row,"untracked");if(missing>0)db.update("insert into logistics_lots(owner,code,quantity,unit_cost,funding,fund_actor,created_at) values(?,?,?,0,'agent',?,?)",actor,row.get("item_code"),Math.toIntExact(missing),actor,now);
  }
 }
 public List<Map<String,Object>> lots(UUID actor,String code){return db.queryForList("select * from logistics_lots where owner=? and code=? and quantity>0 order by id",actor,code);}
 public static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
 /** The common handover already paid the seller. Route those actual proceeds to each lot's funding source. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void accountSale(UUID actor,String code,int quantity,long unitPrice,UUID receipt,long now){
  if(recorded(receipt))return;int left=quantity;
  for(var lot:lots(actor,code)){int take=Math.min(left,(int)number(lot,"quantity"));if(take==0)continue;long paid=Math.multiplyExact(take,unitPrice);
   if(lot.get("funding").equals("official")){consumeCoins(actor,paid);db.update("update logistics_posts set treasury_copper=treasury_copper+? where id=?",paid,lot.get("fund_post"));}
   else if(!Objects.equals(lot.get("fund_actor"),actor)){consumeCoins(actor,paid);addCoins((UUID)lot.get("fund_actor"),paid);}
   db.update("update logistics_lots set quantity=quantity-? where id=?",take,lot.get("id"));left-=take;if(left==0)break;
  }
  if(left!=0)throw AccountService.bad("采购批次不足，不能重复结算");event(receipt,null,actor,"sale_recovery",code,quantity,Math.multiplyExact(quantity,unitPrice),now,"");
 }
 private void consumeCoins(UUID actor,long n){if(n>0&&db.update("update inventories set quantity=quantity-? where character_id=? and item_code='copper_coin' and quantity-reserved_quantity(character_id,item_code)>=?",n,actor,n)!=1)throw AccountService.bad("实际回款不足");}
 /** Runs only after the common handover moved goods and paid the seller, in its transaction. */
 @Transactional(propagation=Propagation.MANDATORY)
 public void settleHandover(UUID offer,long need,int version,UUID sender,UUID recipient,String code,int quantity,long price,boolean transfer,long now){
  var n=get(need);require(need,version,n.carrier());
  if(!Objects.equals(code,n.code())||quantity<1||quantity>n.remaining())throw AccountService.bad("交接与当前需求不符");
  UUID sale=UUID.nameUUIDFromBytes((offer+":sale").getBytes(java.nio.charset.StandardCharsets.UTF_8));
  if(transfer){
   if(!recipient.equals(n.carrier()))throw AccountService.bad("调货接收者不符");
   UUID post=db.queryForObject("select post_id from logistics_agents where actor_id=?",UUID.class,recipient);
   accountSale(sender,code,quantity,price,sale,now);
   purchase(recipient,post,code,quantity,price,UUID.nameUUIDFromBytes((offer+":purchase").getBytes(java.nio.charset.StandardCharsets.UTF_8)),now);
   reserve(need,version,recipient);
  }else{
   if(!sender.equals(n.carrier())||!recipient.equals(n.recipient()))throw AccountService.bad("配送接收者不符");
   accountSale(sender,code,quantity,price,sale,now);
   delivered(need,version,sender,quantity,offer,now);
  }
 }
 private void addCoins(UUID actor,long n){if(n>0)db.update("insert into inventories values(?,'copper_coin',?) on conflict(character_id,item_code) do update set quantity=inventories.quantity+excluded.quantity",actor,n);}
 public int incomingWanted(UUID actor,String code){long pending=db.queryForObject("select coalesce(sum(remaining-reserved),0) from logistics_needs where carrier=? and code=? and state in ('claimed','pickup')",Long.class,actor,code);return (int)Math.min(10000,Math.max(0,pending-equipment.available(actor,code)));}
 public boolean acceptsCorpse(UUID recipient,UUID sender,UUID corpse){return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from logistics_needs where carrier=? and recipient=? and corpse_id=? and kind='corpse' and state in ('claimed','pickup'))",Boolean.class,recipient,sender,corpse));}
 @Transactional(propagation=Propagation.MANDATORY)
 public void pickupClock(Need n,boolean atPointAndUnpaused,long now,long maximum){
  long elapsed=LogisticsPolicy.waitElapsed(n.waited(),n.clock(),now,n.pickupActive()&&atPointAndUnpaused);
  db.update("update logistics_needs set wait_elapsed=?,last_clock=?,pickup_active=?,message=? where id=? and version=?",elapsed,now,atPointAndUnpaused,atPointAndUnpaused?"请到指定提货点领取":"暂无法提货，等待计时暂停",n.id(),n.version());
  if(elapsed>=maximum)release(n.id(),"open",now,"提货等待超时，未完成部分释放");
 }
}
