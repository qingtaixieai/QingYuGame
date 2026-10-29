package game.world;

import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="PHASE1_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/qingyu_phase1_[a-z0-9_]+")
class LogisticsIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv("PHASE1_DATABASE_URL"));r.add("spring.datasource.username",()->"worldgame");r.add("spring.datasource.password",()->"local-only");r.add("app.admin-password",()->"phase-one-local-test-only");}
 @Autowired WorldService world;@Autowired AccountService accounts;@Autowired CharacterService characters;@Autowired SceneRuntime runtime;@Autowired SceneService scenes;@Autowired BattleService battles;@Autowired LawService law;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager manager;@Autowired LogisticsLedger ledger;@Autowired EquipmentService equipment;
 TransactionTemplate tx(){return new TransactionTemplate(manager);}
 UUID courier,client,post;
 @Autowired HandoverService handovers;
 @Autowired LogisticsDemandService demands;
 @Autowired LogisticsRoster roster;
 @Autowired LogisticsDirector director;
 void fixtures(){
  db.update("delete from handovers");db.update("delete from logistics_posts");db.update("delete from loot_containers");db.update("delete from crimes");battles.reset();db.update("delete from scene_portals");db.update("delete from scene_documents");scenes.refresh();db.update("update characters set life='dead',timer_end=? where kind='monster'",Long.MAX_VALUE);
  var a=accounts.register("cargo"+UUID.randomUUID().toString().substring(0,9),"local-test-password",world.map().spawn());client=a.id();db.update("update accounts set approved=true,entered=true where id=?",client);characters.ensurePlayers();var town=world.map().tiles().stream().filter(t->t.place()!=null&&t.place().type().equals("town")).findFirst().orElseThrow().hex();characters.relocate(client,town.q(),town.r());world.enterScene(client,world.map().version());
  var p=runtime.position(client);post=UUID.randomUUID();db.update("insert into logistics_posts(id,scene_id,faction_id,q,r) values(?,?,'qingyu',?,?)",post,p.sceneId(),p.q(),p.r());courier=UUID.randomUUID();db.update("insert into characters(id,kind,species,name,q,r,bind_q,bind_r) values(?,'service','courier','后勤验收员',?,?,?,?)",courier,town.q(),town.r(),town.q(),town.r());runtime.put(courier,p.sceneId(),p.hex());db.update("insert into logistics_agents(actor_id,post_id,home_q,home_r) values(?,?,?,?)",courier,post,p.q(),p.r());db.update("insert into npc_supply_targets values(?,'bandage',4) on conflict(actor,code) do update set quantity=4",client);
 }
 long need(int qty){return ledger.upsert("test:"+client,post,"supply",client,"bandage",null,qty,10,System.currentTimeMillis());}
 @Test void firstFailedActionKeepsClaimForBlockedTimeout(){synchronized(world){fixtures();long now=System.currentTimeMillis();
  long id=tx().execute(s->{equipment.give(courier,"bandage",2);return need(2);});
  var actions=new LogisticsDirector.Actions(){public boolean moving(UUID a){return false;}public void navigate(UUID a,NpcNavigation.Leg l){throw AccountService.bad("模拟受阻");}public void buy(UUID a,String c,int q,UUID p,long t){throw AccountService.bad("模拟受阻");}public void deliver(LogisticsLedger.Need n,long t){throw AccountService.bad("模拟受阻");}public void collect(LogisticsLedger.Need n,long t){throw AccountService.bad("模拟受阻");}public void claim(UUID a,UUID station,UUID body,long t){throw AccountService.bad("模拟受阻");}public void transfer(LogisticsLedger.Need n,UUID a,int q,long cost,long t){throw AccountService.bad("模拟受阻");}};
  tx().executeWithoutResult(s->director.act(courier,world.map(),now,actions));assertEquals(courier,ledger.get(id).carrier());
  assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->director.act(courier,world.map(),now+1000,actions)));
  tx().executeWithoutResult(s->director.rejected(courier,now+1000));assertEquals(now+1000,ledger.get(id).blocked());
  tx().executeWithoutResult(s->director.rejected(courier,now+121001));assertEquals("open",ledger.get(id).state());assertNull(ledger.get(id).carrier());assertEquals(2,equipment.available(courier,"bandage"));
 }}
 @Test void costTransferUsesActualHandoverAndRetainsAcquisitionCost(){synchronized(world){fixtures();tx().executeWithoutResult(s->{
  var p=runtime.position(client);var adjacent=runtime.map(p.sceneId()).cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->WorldMap.distance(p.hex(),h)==1).findFirst().orElseThrow();runtime.put(courier,p.sceneId(),adjacent);
  db.update("update characters set kind='service' where id=?",client);db.update("insert into logistics_clients(actor_id,post_id) values(?,?)",client,post);db.update("insert into logistics_agents(actor_id,post_id,home_q,home_r) values(?,?,?,?)",client,post,p.q(),p.r());
  long now=System.currentTimeMillis();equipment.give(client,"bandage",2);ledger.purchase(client,post,"bandage",2,3,UUID.randomUUID(),now);var n=ledger.claim(need(2),1,courier,now);
  UUID offer=handovers.proposeLogistics(client,new HandoverService.Offer(courier,"sell",List.of(new HandoverService.Line("bandage",2)),UUID.randomUUID()),n.id(),n.version(),3L,now);var accept=handovers.npcAcceptance(offer);assertNotNull(accept);handovers.accept(courier,accept,now);
  assertEquals(0,equipment.available(client,"bandage"));assertEquals(2,ledger.get(n.id()).reserved());assertEquals(3,LogisticsLedger.number(ledger.lots(courier,"bandage").getFirst(),"unit_cost"));assertEquals(6,db.queryForObject("select treasury_copper from logistics_posts where id=?",Long.class,post));
 });}}
 @Test void nearbyStopIsTemporaryAndCannotBeRepeatedImmediately(){synchronized(world){fixtures();
  world.requestNpcStop(client,courier);assertEquals(true,db.queryForObject("select accepted from logistics_stops where requester=? and target=?",Boolean.class,client,courier));
  assertThrows(RuntimeException.class,()->world.requestNpcStop(client,courier));assertEquals(5000,db.queryForObject("select expires_at-requested_at from logistics_stops where requester=? and target=?",Long.class,client,courier));
 }}
 @Test void worldDispatchActuallyWalksAndDeliversOwnedStock(){runDelivery(true);}
 @Test void worldDispatchBuysAtActualShopBeforeDelivery(){runDelivery(false);}
 void runDelivery(boolean stock){synchronized(world){fixtures();
  tx().executeWithoutResult(s->{db.update("update characters set kind='service' where id=?",client);db.update("insert into logistics_clients(actor_id,post_id) values(?,?)",client,post);if(stock)equipment.give(courier,"bandage",4);var p=runtime.position(client);var h=runtime.map(p.sceneId()).cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(x->WorldMap.distance(x,p.hex())>=2&&!runtime.occupied(p.sceneId(),courier).contains(x)).findFirst().orElseThrow();runtime.put(courier,p.sceneId(),h);});
  long now=System.currentTimeMillis();for(int i=0;i<120&&equipment.available(client,"bandage")<4;i++){world.advanceNpcs(now+i*4000);for(int j=0;j<4;j++)world.stepNpcRoutes();}
  assertEquals(4,equipment.available(client,"bandage"));assertTrue(ledger.query("where recipient=? and state='done'",client).stream().anyMatch(n->n.delivered()>0));
  if(!stock)assertTrue(db.queryForObject("select exists(select 1 from logistics_events where kind='purchase')",Boolean.class));
 }}
 @Test void rosterDoesNotDuplicateOrTeleportLivingCouriers(){synchronized(world){fixtures();tx().executeWithoutResult(s->{
  long now=System.currentTimeMillis();roster.ensure(now);
  var actors=db.queryForList("select actor_id from logistics_agents where post_id=? and actor_id<>? order by actor_id",UUID.class,post,courier);assertEquals(2,actors.size());
  UUID actor=actors.getFirst();var p=runtime.position(actor);var dest=runtime.map(p.sceneId()).cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->!h.equals(p.hex())&&!runtime.occupied(p.sceneId(),actor).contains(h)).findFirst().orElseThrow();runtime.put(actor,p.sceneId(),dest);
  roster.ensure(now+1);assertEquals(dest,runtime.position(actor).hex());assertEquals(3,db.queryForObject("select count(*) from logistics_agents where post_id=?",Integer.class,post));
 });}}
 @Test void demandsExcludePlayersAndAggregateStationStock(){synchronized(world){fixtures();tx().executeWithoutResult(s->{
  db.update("insert into logistics_clients(actor_id,post_id) values(?,?)",client,post);
  db.update("insert into logistics_stock_targets values(?,'bandage',4)",post);equipment.give(courier,"bandage",2);
  UUID second=UUID.randomUUID();db.update("insert into characters(id,kind,species,name,bind_q,bind_r) values(?,'service','courier','第二运输员',0,0)",second);db.update("insert into logistics_agents(actor_id,post_id,home_q,home_r) values(?,?,0,0)",second,post);equipment.give(second,"bandage",1);
  long now=System.currentTimeMillis();demands.synchronize(now);
  assertTrue(ledger.query("where kind='supply' and recipient=?",client).isEmpty());
  var stock=ledger.query("where kind='stock' and post_id=? and state='open'",post).getFirst();assertEquals(1,stock.remaining());
  demands.synchronize(now+1);assertEquals(1,ledger.query("where kind='stock' and post_id=? and state='open'",post).size());
  db.update("update characters set kind='service' where id=?",client);demands.synchronize(now+2);
  var supply=ledger.query("where kind='supply' and recipient=? and state='open'",client).getFirst();assertEquals(4,supply.remaining());
  equipment.give(client,"bandage",4);demands.synchronize(now+3);assertEquals("done",ledger.get(supply.id()).state());
  equipment.consume(client,"bandage",1);demands.synchronize(now+4);var renewed=ledger.query("where kind='supply' and recipient=? and state='open'",client).getFirst();
  db.update("delete from npc_supply_targets where actor=? and code='bandage'",client);demands.synchronize(now+5);assertEquals("cancelled",ledger.get(renewed.id()).state());
 });}}
 @Test void sharedHandoverMovesRealGoodsAndSettlesExactlyOnce(){synchronized(world){fixtures();tx().executeWithoutResult(s->{
  var p=runtime.position(client);var neighbor=runtime.map(p.sceneId()).cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->WorldMap.distance(p.hex(),h)==1).findFirst().orElseThrow();runtime.put(courier,p.sceneId(),neighbor);
  db.update("update characters set kind='service' where id=?",client);db.update("insert into logistics_clients(actor_id,post_id) values(?,?)",client,post);
  equipment.give(courier,"bandage",2);long now=System.currentTimeMillis();ledger.purchase(courier,post,"bandage",2,3,UUID.randomUUID(),now);
  var n=ledger.claim(need(2),1,courier,now);ledger.reserve(n.id(),n.version(),courier);ledger.releaseForHandover(n.id(),n.version(),courier,2);
  UUID offer=handovers.proposeLogistics(courier,new HandoverService.Offer(client,"sell",List.of(new HandoverService.Line("bandage",2)),UUID.randomUUID()),n.id(),n.version(),null,now);
  var accept=handovers.npcAcceptance(offer);assertNotNull(accept);handovers.accept(client,accept,now);handovers.accept(client,accept,now);
  assertEquals(2,equipment.available(client,"bandage"));assertEquals(0,equipment.available(courier,"bandage"));assertEquals("done",ledger.get(n.id()).state());assertEquals(2,ledger.get(n.id()).delivered());
  long price=db.queryForObject("select buy_price from item_definitions where code='bandage'",Long.class);assertEquals(price*2,db.queryForObject("select treasury_copper from logistics_posts where id=?",Long.class,post));
  assertEquals(0,LogisticsLedger.number(db.queryForMap("select coalesce(sum(quantity),0) as quantity from logistics_lots where owner=?",courier),"quantity"));
 });}}
 @Test void originalGuardDeathDoesNotCancelAnAlreadyCollectedCorpse(){synchronized(world){fixtures();tx().executeWithoutResult(s->{
  UUID corpse=UUID.randomUUID();long now=System.currentTimeMillis();
  db.update("insert into loot_containers(id,kind,name,source_kind,source_id,world_version,q,r,created_at,carrier_id) values(?,'corpse','验收尸体','monster',?,?,0,0,?,?)",corpse,UUID.randomUUID(),world.map().version(),now,courier);
  long id=ledger.upsert("corpse:"+corpse,post,"corpse",client,null,corpse,1,10,now);
  var n=ledger.claim(id,1,courier,now);
  ledger.actorDied(client,now+1);
  assertEquals(n.version(),ledger.get(id).version());assertEquals(courier,ledger.get(id).carrier());
  ledger.release(id,"open",now+2,"模拟长期受阻");ledger.actorDied(client,now+3);assertEquals("open",ledger.get(id).state());
  demands.synchronize(now+10003);assertEquals(courier,ledger.get(id).carrier());
  ledger.actorDied(courier,now+10004);
  assertNull(ledger.get(id).carrier());assertEquals("open",ledger.get(id).state());
 });}}
 @Test void restartPreservesTimerRemainderWithoutOverflow(){synchronized(world){fixtures();tx().executeWithoutResult(s->{
  long now=System.currentTimeMillis();
  db.update("update characters set timer_end=?,protected_until=?,saved_at=? where id=?",Long.MAX_VALUE,Long.MAX_VALUE,now-1000,courier);
  db.update("update characters set timer_end=?,saved_at=? where id=?",now+4000,now-1000,client);
  characters.initialize(world.map(),now);
  assertEquals(Long.MAX_VALUE,characters.get(courier).timerEnd());
  assertEquals(Long.MAX_VALUE,characters.get(courier).protectedUntil());
  assertEquals(now+5000,characters.get(client).timerEnd());
 });}}
 @Test void claimAndReservationAreExclusiveAndCancellationKeepsPhysicalStock(){synchronized(world){fixtures();
  var n=tx().execute(s->{equipment.give(courier,"bandage",3);return ledger.claim(need(4),1,courier,System.currentTimeMillis());});
  assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->ledger.claim(n.id(),1,courier,System.currentTimeMillis())));
  tx().executeWithoutResult(s->{assertEquals(3,ledger.reserve(n.id(),n.version(),courier));assertEquals(0,equipment.available(courier,"bandage"));});
  assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->equipment.consume(courier,"bandage",1)));
  tx().executeWithoutResult(s->ledger.release(n.id(),"open",System.currentTimeMillis(),"test"));
  assertEquals(3,equipment.available(courier,"bandage"));assertThrows(RuntimeException.class,()->tx().execute(s->ledger.require(n.id(),n.version(),courier)));
 }}
 @Test void changedShortageReusesNeedAndPreservesCompletedProgress(){synchronized(world){fixtures();tx().executeWithoutResult(s->{equipment.give(courier,"bandage",4);long id=need(4);var n=ledger.claim(id,1,courier,System.currentTimeMillis());ledger.reserve(id,n.version(),courier);assertEquals(id,need(2));var updated=ledger.get(id);assertEquals(n.version()+1,updated.version());assertEquals(2,updated.reserved());assertThrows(RuntimeException.class,()->ledger.require(id,n.version(),courier));ledger.releaseForHandover(id,updated.version(),courier,1);var receipt=UUID.randomUUID();ledger.delivered(id,updated.version(),courier,1,receipt,System.currentTimeMillis());ledger.delivered(id,updated.version(),courier,1,receipt,System.currentTimeMillis());assertEquals(1,ledger.get(id).delivered());assertEquals(1,ledger.get(id).remaining());});}}
 @Test void writesRequireTransactionAndFailedRecoveryRollsBack(){synchronized(world){fixtures();
  assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->need(1));
  tx().executeWithoutResult(s->{equipment.give(courier,"bandage",1);equipment.give(courier,"copper_coin",10);ledger.purchase(courier,post,"bandage",1,3,UUID.randomUUID(),1);});
  UUID receipt=UUID.randomUUID();assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->ledger.accountSale(courier,"bandage",2,5,receipt,2)));
  assertEquals(10,equipment.available(courier,"copper_coin"));assertEquals(0,db.queryForObject("select treasury_copper from logistics_posts where id=?",Long.class,post));
  assertEquals(1,LogisticsLedger.number(ledger.lots(courier,"bandage").getFirst(),"quantity"));assertFalse(ledger.recorded(receipt));
 }}
 @Test void deathReleasesReservationsBeforeDroppingRealGoods(){synchronized(world){fixtures();tx().executeWithoutResult(s->{equipment.give(courier,"bandage",3);long id=need(3);var n=ledger.claim(id,1,courier,System.currentTimeMillis());ledger.reserve(id,n.version(),courier);characters.die(courier,System.currentTimeMillis());assertNull(ledger.get(id).carrier());assertEquals("open",ledger.get(id).state());assertEquals(3,db.queryForObject("select sum(i.quantity) from loot_items i join loot_containers l on l.id=i.container_id where l.source_id=? and i.item_code='bandage'",Long.class,courier));});}}
 @Test void procurementAndRevenueAreIdempotentAndUseOriginalFunding(){synchronized(world){fixtures();tx().executeWithoutResult(s->{equipment.give(courier,"bandage",4);UUID buy=UUID.randomUUID();ledger.purchase(courier,post,"bandage",4,3,buy,1);ledger.purchase(courier,post,"bandage",4,3,buy,1);assertEquals(4,ledger.lots(courier,"bandage").stream().mapToLong(l->LogisticsLedger.number(l,"quantity")).sum());equipment.give(courier,"copper_coin",10);UUID sale=UUID.randomUUID();ledger.accountSale(courier,"bandage",2,5,sale,2);ledger.accountSale(courier,"bandage",2,5,sale,2);assertEquals(10,db.queryForObject("select treasury_copper from logistics_posts where id=?",Long.class,post));assertEquals(0,equipment.available(courier,"copper_coin"));assertEquals(2,ledger.lots(courier,"bandage").stream().mapToLong(l->LogisticsLedger.number(l,"quantity")).sum());});}}
}
