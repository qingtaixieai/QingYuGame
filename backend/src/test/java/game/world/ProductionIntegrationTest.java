package game.world;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="PHASE1_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/qingyu_phase1_[a-z0-9_]+")
class ProductionIntegrationTest {
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",()->System.getenv("PHASE1_DATABASE_URL"));r.add("spring.datasource.username",()->"worldgame");r.add("spring.datasource.password",()->"local-only");r.add("app.admin-password",()->"phase-one-local-test-only");}
 @Autowired WorldService world;@Autowired ProductionService work;@Autowired EquipmentService equipment;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager manager;
 @Autowired SceneRuntime runtime;@Autowired CharacterService characters;@Autowired LootService loot;@Autowired ShopService shop;@Autowired BattleService battles;@Autowired SceneService scenes;
 @BeforeEach void reset(){synchronized(world){db.update("delete from production_tasks");battles.reset();db.update("delete from scene_portals");db.update("delete from scene_documents");scenes.refresh();db.update("update accounts set entered=false");db.update("update characters set next_move=? where kind<>'player'",Long.MAX_VALUE);}}
 @Autowired SkillService skills;
 @Autowired SkillPreparation preparations;
 @Test void explorationAttackOnlyStartsOnRecipientAndReplaysOnce(){synchronized(world){
  UUID caster=actor(),target=actor(),bystander=actor();
  local(caster);
  var local=runtime.position(caster);
  var version=world.map().version();
  characters.relocate(target,characters.get(caster).q(),characters.get(caster).r());
  characters.relocate(bystander,characters.get(caster).q(),characters.get(caster).r());
  var origin=new WorldMap.Hex(0,0);
  var hit=new WorldMap.Hex(1,0);
  runtime.put(caster,local.sceneId(),origin);
  runtime.put(target,local.sceneId(),hit);
  runtime.put(bystander,local.sceneId(),new WorldMap.Hex(3,0));
  int before=characters.get(target).hp();
  var miss=UUID.randomUUID();
  world.exploreAction(caster,version,"attack",0,1,miss);
  assertFalse(battles.engaged(caster));
  assertEquals(before,characters.get(target).hp());
  db.update("update characters set protected_until=? where id=?",System.currentTimeMillis()+60000,target);
  world.exploreAction(caster,version,"attack",hit.q(),hit.r(),UUID.randomUUID());
  assertEquals(before,characters.get(target).hp());
  assertFalse(battles.engaged(caster));
  db.update("update characters set protected_until=0 where id=?",target);
  var contact=UUID.randomUUID();
  world.exploreAction(caster,version,"attack",hit.q(),hit.r(),contact);
  var damage=characters.get(target).hp();
  assertTrue(damage<before);
  assertTrue(battles.engaged(caster));
  assertTrue(battles.engaged(target));
  assertFalse(battles.engaged(bystander));
  assertEquals(6,battles.current(caster,Set.of(caster,target)).get("turnPoints"));
  world.exploreAction(caster,version,"attack",hit.q(),hit.r(),contact);
  assertEquals(damage,characters.get(target).hp());
  var conflict=assertThrows(org.springframework.web.server.ResponseStatusException.class,
      ()->world.exploreAction(caster,version,"attack",0,1,contact));
  assertEquals("动作编号已用于不同请求",conflict.getReason());
 }}
 @Test void failedExplorationRequestCanRetryAndOldReceiptsStillReplay(){synchronized(world){
  UUID caster=actor();local(caster);
  var p=runtime.position(caster);runtime.put(caster,p.sceneId(),new WorldMap.Hex(0,0));
  var version=world.map().version();
  var request=UUID.randomUUID();
  var before=world.snapshot(caster).get("battleRevision");
  assertThrows(RuntimeException.class,()->world.exploreAction(caster,version,"attack",25,0,request));
  assertEquals(0,db.queryForObject("select count(*) from scene_action_receipts where actor_id=? and request_id=?",Integer.class,caster,request));
  assertEquals(before,world.snapshot(caster).get("battleRevision"));
  assertNotNull(world.exploreAction(caster,version,"attack",1,0,request));
  var old=UUID.randomUUID();
  db.update("insert into scene_action_receipts(actor_id,request_id,created_at) values(?,?,?)",caster,old,System.currentTimeMillis());
  var revision=world.snapshot(caster).get("battleRevision");
  assertEquals(Map.of("preparation","NONE","battle",false),world.exploreAction(caster,version,"attack",25,0,old));
  assertEquals(revision,world.snapshot(caster).get("battleRevision"));
 }}
 @Autowired BattleExploration exploration;
 @Test void explorationFireballIsReadyAfterThreeSeconds(){synchronized(world){
  var caster=actor();
  local(caster);
  var position=runtime.position(caster);
  long now=System.currentTimeMillis();
  tx().executeWithoutResult(status->{
   skills.set(caster,"fireball",true);
   exploration.act(caster,position,"skill:fireball",null,null,now);
   assertEquals(SkillPreparation.State.CHANTING,preparations.state(caster,now+2999));
   assertEquals(SkillPreparation.State.READY,preparations.state(caster,now+3000));
   assertFalse(preparations.interrupt(caster,now+3000));
  });
 }}
 @Autowired SceneLoggingCamp camps;
 @Test void loggingCampUsesOneSharedChestAndRestocksAfterViewerCloses(){synchronized(world){
  UUID first=actor(),second=actor();
  var map=world.map();
  var site=SceneLoggingCamp.site(map).orElseThrow();
  assertEquals(SceneLoggingCamp.NAME,world.clientMap().index().get(site).place().name());
  for(UUID player:List.of(first,second)){
   characters.relocate(player,site.q(),site.r());
   world.enterScene(player,map.version());
  }
  var location=runtime.position(first);
  assertEquals(location.sceneId(),runtime.position(second).sceneId());
  var campsite=runtime.map(location.sceneId());
  var chest=db.queryForObject("select chest_id from scene_logging_camps where scene_id=?",UUID.class,campsite.id());
  var monster=db.queryForObject("select monster_id from scene_logging_camps where scene_id=?",UUID.class,campsite.id());
  assertTrue(campsite.topology().connected(location.hex(),SceneLoggingCamp.CHEST,Set.copyOf(SceneLoggingCamp.PATROL)));
  assertTrue(campsite.topology().connected(SceneLoggingCamp.PATROL.getFirst(),SceneLoggingCamp.CHEST,Set.of()));
  var take=new LootService.Command("takeAll",chest,null,null,map.version(),null,null);
  assertThrows(RuntimeException.class,()->world.lootCommand(first,take));
  runtime.put(first,campsite.id(),SceneLoggingCamp.CHEST);
  var firstView=world.openChest(first,chest,map.version(),null);
  world.lootCommand(first,take);
  assertEquals(2,equipment.available(first,SceneLoggingCamp.MATERIAL));
  world.closeChest(first,chest,firstView.token());
  runtime.leave(first);
  runtime.put(second,campsite.id(),SceneLoggingCamp.CHEST);
  var secondView=world.openChest(second,chest,map.version(),null);
  assertThrows(RuntimeException.class,()->world.lootCommand(second,take));
  long restock=db.queryForObject("select restock_at from scene_logging_camps where scene_id=?",Long.class,campsite.id());
  tx().executeWithoutResult(status->camps.advance(runtime,Set.of(second),Set.of(),restock+1));
  assertEquals(0,equipment.available(second,SceneLoggingCamp.MATERIAL));
  assertEquals(0L,db.queryForObject("select count(*) from loot_items where container_id=?",Long.class,chest));
  world.closeChest(second,chest,secondView.token());
  runtime.leave(second);
  tx().executeWithoutResult(status->{
   characters.die(monster,restock-1);
   db.update("update characters set timer_end=? where id=?",restock,monster);
   camps.advance(runtime,Set.of(),Set.of(),restock+1);
  });
  assertTrue(characters.get(monster).alive());
  world.enterScene(second,map.version());
  runtime.put(second,campsite.id(),SceneLoggingCamp.CHEST);
  world.openChest(second,chest,map.version(),null);
  world.lootCommand(second,take);
  assertEquals(2,equipment.available(second,SceneLoggingCamp.MATERIAL));
  assertEquals(monster,db.queryForObject("select monster_id from scene_logging_camps where scene_id=?",UUID.class,campsite.id()));
  var town=map.tiles().stream().filter(tile->tile.place()!=null&&tile.place().type().equals("town")).findFirst().orElseThrow().hex();
  runtime.leave(second);
  characters.relocate(second,town.q(),town.r());
  world.enterScene(second,map.version());
  UUID workshop=db.queryForObject("select id from town_buildings where outdoor_id=? and kind='workshop'",UUID.class,runtime.position(second).sceneId());
  runtime.put(second,workshop,new WorldMap.Hex(0,2));
  var bench=object(second,"workbench");
  var benchCell=new WorldMap.Hex(((Number)bench.get("q")).intValue(),((Number)bench.get("r")).intValue());
  var standing=benchCell.neighbors().stream().filter(h->runtime.map(workshop).cells().stream().anyMatch(c->c.hex().equals(h)&&c.walkable())).findFirst().orElseThrow();
  runtime.put(second,workshop,standing);
  give(second,"arrow",5);
  long craftAt=System.currentTimeMillis();
  tx().executeWithoutResult(status->{
   work.start(second,map,new ProductionService.Start("craft","poison_arrow",1,(UUID)bench.get("id"),null),craftAt);
   work.advance(craftAt+5000);
  });
  assertEquals(5,equipment.available(second,"poison_arrow"));
  assertEquals(1,equipment.available(second,SceneLoggingCamp.MATERIAL));
  assertEquals(0,equipment.available(second,"arrow"));
 }}
 @Test void fireballChantsThroughEndTurnAndSpendsReadyChargeOnEmptyCell(){synchronized(world){
  UUID caster=actor(),other=actor();
  local(caster);
  var from=characters.get(caster);
  characters.relocate(other,from.q(),from.r());
  world.enterScene(other,world.map().version());
  UUID battle=world.startBattle(caster,other,world.map().version());
  var scene=runtime.map(runtime.position(caster).sceneId());
  var home=runtime.position(caster).hex();
  var occupied=runtime.position(other).hex();
  var empty=scene.cells().stream().map(SceneMap.Cell::hex)
      .filter(h->!h.equals(home)&&!h.equals(occupied)&&WorldMap.distance(home,h)<=6).findFirst().orElseThrow();
  long now=System.currentTimeMillis();
  var online=Set.of(caster,other);
  tx().executeWithoutResult(s->{
   skills.set(caster,"fireball",true);
   db.update("update battle_encounters set turn_account_id=?,turn_points=6 where id=?",caster,battle);
   battles.action(caster,"skill:fireball",null,null,null,online,now);
   assertEquals(SkillPreparation.State.CHANTING,preparations.state(caster,now));
   assertEquals(4,db.queryForObject("select turn_points from battle_encounters where id=?",Integer.class,battle));
   battles.endTurn(caster,online,now);
   battles.endTurn(other,online,now+1);
   assertEquals(SkillPreparation.State.READY,preparations.state(caster,now+1));
   battles.action(caster,"skill:fireball",null,empty.q(),empty.r(),online,now+2);
   assertEquals(SkillPreparation.State.NONE,preparations.state(caster,now+2));
   assertEquals(6,db.queryForObject("select turn_points from battle_encounters where id=?",Integer.class,battle));
   assertEquals(1,db.queryForObject("select count(*) from battle_events where encounter_id=? and kind='meteor'",Integer.class,battle));
  });
 }}
 @Test void townResourcesAreSpreadAndAvoidTheCentralStreets(){synchronized(world){
  var map=world.map();
  var town=map.tiles().stream().filter(t->t.place()!=null&&t.place().type().equals("town")).findFirst().orElseThrow();
  var scene=tx().execute(s->runtime.outdoor(map,town.hex()));
  var nodes=db.query("select q,r from scene_objects where scene_id=? and kind='resource'",(rs,n)->new WorldMap.Hex(rs.getInt(1),rs.getInt(2)),scene.id());
  assertFalse(nodes.isEmpty());
  assertTrue(nodes.stream().map(WorldMap.Hex::q).distinct().count()>1,nodes.toString());
  for(var node:nodes){
   var tile=scene.cells().stream().filter(c->c.hex().equals(node)).findFirst().orElseThrow();
   assertTrue(tile.walkable()&&!Set.of("paving","street","plaza").contains(tile.terrain()));
   assertTrue(WorldMap.distance(node,new WorldMap.Hex(0,0))>=5);
  }
  var before=db.queryForList("select * from scene_objects where scene_id=? order by id",scene.id());
  db.update("update production_scene_content set version=1 where scene_id=?",scene.id());
  tx().execute(s->runtime.outdoor(map,town.hex()));
  assertEquals(before,db.queryForList("select * from scene_objects where scene_id=? order by id",scene.id()));
  assertEquals(2,db.queryForObject("select version from production_scene_content where scene_id=?",Integer.class,scene.id()));
 }}
 @Test void pickaxeAttackIgnoresOneArmorThroughSharedBattleAction(){synchronized(world){
  var a=actor();local(a);var b=actor();var c=characters.get(a);characters.relocate(b,c.q(),c.r());world.enterScene(b,world.map().version());equip(a,"pickaxe");give(b,"light_armor",1);tx().executeWithoutResult(s->equipment.equip(b,null,null,"light_armor"));
  var map=runtime.map(runtime.position(a).sceneId());tx().executeWithoutResult(s->{scenes.save(new SceneMap(map.id(),map.worldVersion(),map.generationVersion(),2,map.owner(),map.name(),map.radius(),map.cells().stream().map(h->Math.abs(h.q())<=1&&h.r()==0?new SceneMap.Cell(h.q(),h.r(),h.terrain(),true):h).toList()));runtime.put(a,map.id(),new WorldMap.Hex(0,0));runtime.put(b,map.id(),new WorldMap.Hex(1,0));});
  var battle=world.startBattle(a,b,world.map().version());db.update("update battle_encounters set turn_account_id=?,turn_points=6 where id=?",a,battle);int before=characters.get(b).hp();int armor=db.queryForObject("select passive_armor from item_definitions where code='light_armor'",Integer.class);
  world.battleAttack(a,battle,null,1,0);world.battleStep(a,battle,-1,0);
  assertEquals(Math.max(1,3-Math.max(0,armor-1)),before-characters.get(b).hp());
 }}
 void give(UUID actor,String code,int amount){tx().executeWithoutResult(s->equipment.give(actor,code,amount));}
 void equip(UUID actor,String code){give(actor,code,1);tx().executeWithoutResult(s->equipment.equip(actor,code));}
 Map<String,Object> object(UUID actor,String code){return db.queryForMap("select o.* from scene_objects o join scene_positions p on p.scene_id=o.scene_id where p.character_id=? and p.active and o.code=? limit 1",actor,code);}
 void local(UUID a){var h=world.map().tiles().stream().filter(t->t.walkable()&&t.terrain().equals("forest")&&t.place()==null).findFirst().orElseThrow().hex();characters.relocate(a,h.q(),h.r());world.enterScene(a,world.map().version());}
 void atObject(UUID a,Map<String,Object> o){runtime.put(a,(UUID)o.get("scene_id"),new WorldMap.Hex(((Number)o.get("q")).intValue(),((Number)o.get("r")).intValue()));}
 LootService.Context context(UUID a){var c=characters.get(a);return new LootService.Context(a,world.map().version(),c.q(),c.r(),null,null,null);}
 TransactionTemplate tx(){return new TransactionTemplate(manager);}
 UUID actor(){var a=world.register("work_"+UUID.randomUUID().toString().substring(0,8),"local-test-password");db.update("update accounts set approved=true,entered=true where id=?",a.id());return a.id();}
 @Test void reservesWholeBatchAndCancellationKeepsCompletedOutput(){synchronized(world){var a=actor();equipment.give(a,"fiber",15);long now=System.currentTimeMillis();tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("craft","bandage",5,null,null),now));assertEquals(0,equipment.available(a,"fiber"));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->equipment.consume(a,"fiber",1)));tx().executeWithoutResult(s->work.advance(now+5000));assertEquals(1,db.queryForObject("select quantity from inventories where character_id=? and item_code='bandage'",Integer.class,a));tx().executeWithoutResult(s->work.cancel(a));assertEquals(12,equipment.available(a,"fiber"));assertNull(work.current(a));}}
 @Test void recipeArrowUsesTenPerBatch(){assertEquals(10,ProductionRecipes.get("arrow").output());assertEquals(3000,ProductionRecipes.get("arrow").duration());}
 @Test void facilityCapacityAndArrowBatchesUseSharedReservations(){synchronized(world){var town=world.map().tiles().stream().filter(t->t.place()!=null&&t.place().type().equals("town")).findFirst().orElseThrow().hex();List<UUID> actors=new ArrayList<>();Map<String,Object> bench=null;for(int i=0;i<3;i++){var a=actor();actors.add(a);characters.relocate(a,town.q(),town.r());world.enterScene(a,world.map().version());var room=db.queryForObject("select id from town_buildings where outdoor_id=? and kind='workshop'",UUID.class,runtime.position(a).sceneId());runtime.put(a,room,new WorldMap.Hex(0,2));var o=object(a,"workbench");bench=o;var center=new WorldMap.Hex(((Number)o.get("q")).intValue(),((Number)o.get("r")).intValue());var cell=center.neighbors().get(i);runtime.put(a,(UUID)o.get("scene_id"),cell);give(a,"wood",5);give(a,"iron_ingot",5);}long now=System.currentTimeMillis();var request=new ProductionService.Start("craft","arrow",5,(UUID)bench.get("id"),null);for(var a:actors.subList(0,2))tx().executeWithoutResult(s->work.start(a,world.map(),request,now));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->work.start(actors.get(2),world.map(),request,now)));assertThrows(RuntimeException.class,()->tx().execute(s->shop.transact(actors.getFirst(),new ShopService.Order("sell",List.of(new ShopService.Line("wood",1,null))))));tx().executeWithoutResult(s->{work.advance(now+3000);work.cancel(actors.getFirst());});assertEquals(10,equipment.available(actors.getFirst(),"arrow"));assertEquals(4,equipment.available(actors.getFirst(),"wood"));tx().executeWithoutResult(s->work.start(actors.get(2),world.map(),request,now+4000));}}
 @Test void enteringBattleReleasesProductionBeforeAnyCombatAction(){synchronized(world){var a=actor();var b=actor();give(a,"fiber",3);long now=System.currentTimeMillis();tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("craft","bandage",1,null,null),now));world.startBattle(a,b,world.map().version());assertNull(work.current(a));assertEquals(3,equipment.available(a,"fiber"));}}
 @Test void gatheringRequiresHeldToolAndSameCellAndInterruptsWhenUnequipped(){synchronized(world){var a=actor();local(a);var o=object(a,"wood");var request=new ProductionService.Start("gather",null,1,(UUID)o.get("id"),null);long now=System.currentTimeMillis();assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->work.start(a,world.map(),request,now)));tx().executeWithoutResult(s->atObject(a,o));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->work.start(a,world.map(),request,now)));equip(a,"axe");tx().executeWithoutResult(s->work.start(a,world.map(),request,now));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->work.start(a,world.map(),request,now)));tx().executeWithoutResult(s->{equipment.equip(a,null);work.advance(now+5000);});assertNull(work.current(a));assertEquals(0,equipment.available(a,"wood"));}}
 @Test void gatheringCompletesOnceAndTimestampControlsRegrowth(){synchronized(world){var a=actor();local(a);var o=object(a,"wood");equip(a,"axe");long now=System.currentTimeMillis();tx().executeWithoutResult(s->{atObject(a,o);work.start(a,world.map(),new ProductionService.Start("gather",null,1,(UUID)o.get("id"),null),now);work.advance(now+5000);work.advance(now+5001);});assertEquals(1,equipment.available(a,"wood"));assertEquals(now+125000,db.queryForObject("select ready_at from scene_objects where id=?",Long.class,o.get("id")));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("gather",null,1,(UUID)o.get("id"),null),now+6000)));}}
 @Test void reservationBlocksDropSellAndDuplicateTaskAndRestartReleases(){synchronized(world){var a=actor();give(a,"fiber",3);long now=System.currentTimeMillis();tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("craft","bandage",1,null,null),now));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->loot.execute(context(a),new LootService.Command("dropItems",null,null,null,world.map().version(),null,List.of(new LootService.ItemDrop("fiber",1))),now)));assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("craft","bandage",1,null,null),now)));world.load();assertNull(work.current(a));assertEquals(3,equipment.available(a,"fiber"));assertEquals(0,equipment.available(a,"bandage"));}}
 @Test void shopReplaySurvivesReloadAndPayloadCannotChange(){synchronized(world){var a=actor();var id=UUID.randomUUID();var order=new ShopService.Order("buy",List.of(new ShopService.Line("bread",2,null)),id);var first=tx().execute(s->shop.transact(a,order));world.load();var again=tx().execute(s->shop.transact(a,order));assertNotNull(first);assertNotNull(again);assertEquals(2,equipment.available(a,"bread"));assertEquals(24,equipment.available(a,"copper_coin"));assertThrows(RuntimeException.class,()->tx().execute(s->shop.transact(a,new ShopService.Order("buy",List.of(new ShopService.Line("bread",3,null)),id))));}}
 @Test void failedShopOrderRollsBackAndCanReuseRequestId(){synchronized(world){var a=actor();var id=UUID.randomUUID();var order=new ShopService.Order("buy",List.of(new ShopService.Line("axe",1,null)),id);assertThrows(RuntimeException.class,()->tx().execute(s->shop.transact(a,order)));assertEquals(0,equipment.available(a,"axe"));assertEquals(0,db.queryForObject("select count(*) from transaction_receipts where actor_id=?",Integer.class,a));give(a,"copper_coin",100);tx().execute(s->shop.transact(a,order));assertEquals(1,equipment.available(a,"axe"));}}
 @Test void portableCraftCanMoveWithinSceneButCrossingCancels(){synchronized(world){var a=actor();local(a);give(a,"fiber",6);long now=System.currentTimeMillis();tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("craft","bandage",1,null,null),now));var p=runtime.position(a);var next=runtime.map(p.sceneId()).cells().stream().filter(c->c.walkable()&&!c.hex().equals(p.hex())).findFirst().orElseThrow();tx().executeWithoutResult(s->{work.moving(a);runtime.put(a,p.sceneId(),next.hex());work.advance(now+5000);});assertEquals(1,equipment.available(a,"bandage"));tx().executeWithoutResult(s->work.start(a,world.map(),new ProductionService.Start("craft","bandage",1,null,null),now+6000));world.leaveScene(a,world.map().version());assertNull(work.current(a));assertEquals(3,equipment.available(a,"fiber"));}}
 UUID corpse(UUID a,long expires,boolean contents){var id=UUID.randomUUID();var p=runtime.position(a);var c=characters.get(a);db.update("insert into loot_containers(id,kind,name,source_kind,source_species,skinnable,world_version,q,r,scene_id,battle_q,battle_r,created_at,expires_at) values(?,'corpse','测试毛皮兽','monster','claw',true,?,?,?,?,?,?,?,?)",id,world.map().version(),c.q(),c.r(),p.sceneId(),p.q(),p.r(),System.currentTimeMillis(),expires);if(contents)db.update("insert into loot_items values(?,'wood',2)",id);return id;}
 @Test void skinClaimProtectsExpiredCorpseAndFailureLeavesContentsInBag(){synchronized(world){var a=actor();local(a);equip(a,"skinning_knife");long now=System.currentTimeMillis();var id=corpse(a,now+10000,true);tx().executeWithoutResult(s->{work.start(a,world.map(),new ProductionService.Start("skin",null,1,null,id),now);loot.expire(now+11000);});assertEquals(1,db.queryForObject("select count(*) from loot_containers where id=?",Integer.class,id));tx().executeWithoutResult(s->loot.finishSkin(id,a,false,now+12000));assertEquals(0,db.queryForObject("select count(*) from loot_containers where id=?",Integer.class,id));assertEquals(0,equipment.available(a,"hide"));assertNull(work.current(a));assertEquals(2,db.queryForObject("select sum(i.quantity) from loot_items i join loot_containers l on l.id=i.container_id where l.name='剥皮遗留物' and l.scene_id=?",Integer.class,runtime.position(a).sceneId()));}}
 @Test void corpseMustBeEmptyToCarryAndDroppingResumesRemainingTime(){synchronized(world){var a=actor();local(a);long now=System.currentTimeMillis();var id=corpse(a,now+100000,true);var carry=new LootService.Command("carry",id,null,null,world.map().version(),null,null);assertThrows(RuntimeException.class,()->tx().executeWithoutResult(s->loot.execute(context(a),carry,now)));tx().executeWithoutResult(s->{loot.execute(context(a),new LootService.Command("takeAll",id,null,null,world.map().version(),null,null),now);loot.execute(context(a),carry,now+1000);});assertNull(db.queryForObject("select expires_at from loot_containers where id=?",Long.class,id));tx().executeWithoutResult(s->loot.execute(context(a),new LootService.Command("dropCorpse",id,null,null,world.map().version(),null,null),now+50000));assertEquals(now+149000,db.queryForObject("select expires_at from loot_containers where id=?",Long.class,id));}}
 @Autowired ProductionResults actionResults;
 UUID chest(UUID actor){var c=characters.get(actor);var p=runtime.position(actor);UUID id=UUID.randomUUID();db.update("insert into loot_containers(id,kind,name,world_version,q,r,scene_id,battle_q,battle_r,created_at,expires_at) values(?,'chest','测试货箱',?,?,?,?,?,?,?,?)",id,world.map().version(),c.q(),c.r(),p.sceneId(),p.q(),p.r(),System.currentTimeMillis(),Long.MAX_VALUE);db.update("insert into loot_items values(?,'wood',2)",id);return id;}
 @Test void chestReadCannotClaimAndStaleCloseCannotReleaseNewViewer(){synchronized(world){
  var a=actor();var b=actor();local(a);var p=runtime.position(a);characters.relocate(b,characters.get(a).q(),characters.get(a).r());runtime.put(b,p.sceneId(),p.hex());var id=chest(a);var version=world.map().version();
  assertThrows(RuntimeException.class,()->world.lootContents(a,id,version,null));
  var firstView=world.openChest(a,id,version,null);var view=world.openChest(a,id,version,null);assertNotEquals(firstView.token(),view.token());world.closeChest(a,id,firstView.token());
  assertThrows(RuntimeException.class,()->world.openChest(b,id,version,null));
  assertThrows(RuntimeException.class,()->world.lootContents(b,id,version,null));
  var take=new LootService.Command("takeAll",id,null,null,version,null,null);
  assertThrows(RuntimeException.class,()->world.lootCommand(b,take));
  world.closeChest(a,id,view.token());var newer=world.openChest(a,id,version,null);assertNotEquals(view.token(),newer.token());world.closeChest(a,id,view.token());
  assertThrows(RuntimeException.class,()->world.openChest(b,id,version,null));world.lootCommand(a,take);assertEquals(2,equipment.available(a,"wood"));
  assertThrows(RuntimeException.class,()->world.lootCommand(a,take));assertEquals(2,equipment.available(a,"wood"));
  world.closeChest(a,id,newer.token());world.openChest(b,id,version,null);
 }}
 @Test void invalidMoveDoesNotReleaseChestButValidMoveAndDisconnectDo(){synchronized(world){
  var a=actor();local(a);var p=runtime.position(a);var id=chest(a);String version=world.map().version();world.openChest(a,id,version,null);
  assertThrows(RuntimeException.class,()->world.moveScene(a,999,999,version));assertTrue(loot.views().containsKey(id));
  var dest=runtime.map(p.sceneId()).cells().stream().filter(c->!c.hex().equals(p.hex())&&!runtime.path(a,c.hex()).isEmpty()).findFirst().orElseThrow().hex();world.moveScene(a,dest.q(),dest.r(),version);assertFalse(loot.views().containsKey(id));world.stop(a);
  world.openChest(a,id,version,null);world.closeUser(a);assertFalse(loot.views().containsKey(id));
 }}
 @Test void durableResultsCommitOnceAndStopCancelsOnlyUnfinishedBatches(){synchronized(world){
  var a=actor();var b=actor();give(a,"fiber",15);long now=System.currentTimeMillis();tx().executeWithoutResult(st->work.start(a,world.map(),new ProductionService.Start("craft","bandage",5,null,null),now));
  tx().executeWithoutResult(st->work.advance(now+5000));var completed=actionResults.recent(a);assertEquals(1,completed.size());assertEquals("success",completed.getFirst().outcome());assertEquals("bandage",completed.getFirst().items().getFirst().code());
  world.stop(a);assertNull(work.current(a));assertEquals(12,equipment.available(a,"fiber"));var records=actionResults.recent(a);assertEquals(2,records.size());assertTrue(records.stream().anyMatch(r->r.outcome().equals("cancelled")));assertTrue(actionResults.recent(b).isEmpty());
  tx().executeWithoutResult(st->work.advance(now+10000));assertEquals(2,actionResults.recent(a).size());assertEquals(1,equipment.available(a,"bandage"));
  assertEquals(actionResults.recent(a),world.actionResults(a));
 }}
 @Test void resultInsertionRollsBackWithAward(){synchronized(world){var a=actor();give(a,"fiber",3);long now=System.currentTimeMillis();tx().executeWithoutResult(st->work.start(a,world.map(),new ProductionService.Start("craft","bandage",1,null,null),now));tx().executeWithoutResult(st->{work.advance(now+5000);st.setRollbackOnly();});assertTrue(actionResults.recent(a).isEmpty());assertEquals(0,equipment.available(a,"bandage"));assertNotNull(work.current(a));tx().executeWithoutResult(st->work.advance(now+5000));assertEquals(1,actionResults.recent(a).size());assertEquals(1,equipment.available(a,"bandage"));}}

 @Test void resultUsesFacilityAnchorAndPreservesConsumedCorpseAnchor(){synchronized(world){
  var a=actor();local(a);var p=runtime.position(a);var obj=object(a,"wood");var target=new WorldMap.Hex(((Number)obj.get("q")).intValue(),((Number)obj.get("r")).intValue());
  var task=new ProductionService.Task(a,"craft","arrow",(UUID)obj.get("id"),null,p.sceneId(),world.map().version(),characters.get(a).q(),characters.get(a).r(),target.q()+1,target.r(),0,3000,1,0);
  tx().executeWithoutResult(st->actionResults.record(task,"success","测试定位",List.of(),System.currentTimeMillis()));var result=actionResults.recent(a).getFirst();assertEquals(target.q(),result.q());assertEquals(target.r(),result.r());assertFalse(result.personal());
  equip(a,"skinning_knife");var id=corpse(a,System.currentTimeMillis()+60000,false);long now=System.currentTimeMillis();tx().executeWithoutResult(st->work.start(a,world.map(),new ProductionService.Start("skin",null,null,null,id),now));tx().executeWithoutResult(st->work.advance(now+9000));var skin=actionResults.recent(a).stream().filter(r->id.equals(r.corpseId())).findFirst().orElseThrow();assertEquals(p.q(),skin.q());assertEquals(p.r(),skin.r());assertFalse(skin.personal());assertEquals(0,db.queryForObject("select count(*) from loot_containers where id=?",Integer.class,id));
  var carried=corpse(a,now+60000,false);tx().executeWithoutResult(st->loot.execute(context(a),new LootService.Command("carry",carried,null,null,world.map().version(),null,null),now));tx().executeWithoutResult(st->work.start(a,world.map(),new ProductionService.Start("skin",null,null,null,carried),now));tx().executeWithoutResult(st->work.advance(now+9000));assertTrue(actionResults.recent(a).stream().filter(r->carried.equals(r.corpseId())).findFirst().orElseThrow().personal());
 }}
 @Autowired AccountService accounts;
 @Test void chestSurvivesFirstSocketDisconnectAndReleasesOnLastOne() throws Exception {synchronized(world){
  var a=actor();local(a);var id=chest(a);var req=new org.springframework.mock.web.MockHttpServletRequest();req.setRemoteAddr("127.0.0.1");var res=new org.springframework.mock.web.MockHttpServletResponse();accounts.login(accounts.get(a).username(),"local-test-password",req,res);String token=res.getHeader("Set-Cookie").split(";",2)[0].substring("WORLD_SESSION=".length());
  var one=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);var two=org.mockito.Mockito.mock(org.springframework.web.socket.WebSocketSession.class);org.mockito.Mockito.when(one.getId()).thenReturn("feedback-one");org.mockito.Mockito.when(two.getId()).thenReturn("feedback-two");org.mockito.Mockito.when(one.isOpen()).thenReturn(true);org.mockito.Mockito.when(two.isOpen()).thenReturn(true);
  world.connect(a,token,"127.0.0.1",one);world.connect(a,token,"127.0.0.1",two);world.openChest(a,id,world.map().version(),null);world.disconnect("feedback-one");assertTrue(loot.views().containsKey(id));world.disconnect("feedback-two");assertFalse(loot.views().containsKey(id));
 }}
 @Test void npcChestAccessAndBattleTransitionUseSharedRules(){synchronized(world){
  var a=actor();local(a);var p=runtime.position(a);var id=chest(a);long now=System.currentTimeMillis();var npc=characters.deploy("claw",characters.get(a).q(),characters.get(a).r(),now);runtime.put(npc.id(),p.sceneId(),p.hex());var take=new LootService.Command("takeAll",id,null,null,world.map().version(),null,null);
  assertThrows(RuntimeException.class,()->world.lootCommand(npc.id(),take));world.openChest(npc.id(),id,world.map().version(),null);assertThrows(RuntimeException.class,()->world.openChest(a,id,world.map().version(),null));long before=equipment.available(npc.id(),"wood");world.lootCommand(npc.id(),take);assertEquals(before+2,equipment.available(npc.id(),"wood"));
  var next=p.hex().neighbors().stream().filter(h->runtime.map(p.sceneId()).cells().stream().anyMatch(c->c.hex().equals(h)&&c.walkable())).findFirst().orElseThrow();runtime.put(a,p.sceneId(),next);world.startBattle(a,npc.id(),world.map().version());assertFalse(loot.views().containsKey(id));
 }}
 @Test void noIdleTimeoutAndSceneDepartureReleaseChest(){synchronized(world){var a=actor();local(a);var id=chest(a);world.openChest(a,id,world.map().version(),null);tx().executeWithoutResult(st->loot.pruneViews(System.currentTimeMillis()+24*60*60_000L));assertTrue(loot.views().containsKey(id));world.leaveScene(a,world.map().version());assertFalse(loot.views().containsKey(id));}}

}
