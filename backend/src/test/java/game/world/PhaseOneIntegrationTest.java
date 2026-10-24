package game.world;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly opt in with a disposable local database, never a development or production database. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named="PHASE1_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/qingyu_phase1_[a-z0-9_]+")
class PhaseOneIntegrationTest {
    @DynamicPropertySource static void config(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->System.getenv("PHASE1_DATABASE_URL"));
        r.add("spring.datasource.username",()->"worldgame");
        r.add("spring.datasource.password",()->Objects.requireNonNullElse(System.getenv("PHASE1_DATABASE_PASSWORD"),"local-only"));
        r.add("app.admin-password",()->"phase-one-local-test-only");
        r.add("app.secure-cookie",()->false);
    }
    @Autowired WorldService world;
    @Autowired SceneService scenes;
    @Autowired BattleService battles;
    @Autowired CharacterService characters;
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager manager;

    @BeforeEach void cleanIsolatedFixtures(){
        synchronized(world){
            battles.reset();
            db.update("delete from scene_portals");db.update("delete from scene_documents");scenes.refresh();
            db.update("delete from world_actions");
        }
    }

    @Test void persistedClosureStopsQueuedMovementNpcAndBattleEntryAndRestores(){
        synchronized(world){
            var map=world.map();var index=map.index();
            var a=map.tiles().stream().filter(t->t.walkable()&&t.hex().neighbors().stream().allMatch(h->index.containsKey(h)&&index.get(h).walkable())).findFirst().orElseThrow().hex();
            var b=a.neighbors().getFirst();
            var account=world.register("phase_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            UUID user=account.id();
            db.update("update accounts set approved=true,entered=true,q=?,r=? where id=?",a.q(),a.r(),user);
            characters.relocate(user,a.q(),a.r());
            var original=world.move(user,b.q(),b.r(),map.version());assertFalse(original.isEmpty());
            var center=SceneMap.outdoor(map,a,3);var portals=new ArrayList<ScenePortal>();
            var tx=new TransactionTemplate(manager);
            tx.executeWithoutResult(s->{
                scenes.save(center);
                for(var h:a.neighbors()){
                    var neighbor=SceneMap.outdoor(map,h,3);scenes.save(neighbor);
                    var portal=new ScenePortal(UUID.randomUUID(),new ScenePortal.Endpoint(center.id(),new WorldMap.Hex(0,0)),new ScenePortal.Endpoint(neighbor.id(),new WorldMap.Hex(0,0)),false);
                    portals.add(portal);scenes.save(portal);
                }
            });
            assertEquals(6,scenes.blocked().size());assertFalse(scenes.open(a,b));assertFalse(scenes.open(b,a));
            world.advance();assertEquals(a,characters.get(user).hex(),"Queued movement must stop before closed edge");
            assertThrows(RuntimeException.class,()->world.move(user,b.q(),b.r(),map.version()));
            // The same physical boundary applies to NPC wander and reinforcement.
            var monster=CharacterService.ISLAND_BEAST;characters.relocate(monster,a.q(),a.r());
            db.update("update characters set bind_q=?,bind_r=?,next_move=0,protected_until=0 where id=?",a.q(),a.r(),monster);
            characters.wander(System.currentTimeMillis(),Set.of());assertEquals(a,characters.get(monster).hex());
            var defender=world.register("defender_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            var attacker=world.register("attacker_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            for(UUID id:List.of(defender.id(),attacker.id())){db.update("update accounts set approved=true,entered=true where id=?",id);characters.relocate(id,b.q(),b.r());}
            long now=System.currentTimeMillis();
            UUID fight=tx.execute(s->battles.start(attacker.id(),defender.id(),map.version(),map,now));
            assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->battles.join(user,fight,map.version(),map,now)));
            assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->battles.join(monster,fight,map.version(),map,now)));
            UUID active=db.queryForObject("select turn_account_id from battle_encounters where id=?",UUID.class,fight);
            int direction=BattleService.DIRECTIONS.indexOf(new WorldMap.Hex(a.q()-b.q(),a.r()-b.r()));
            var d=BattleService.DIRECTIONS.get(direction);
            db.update("update battle_actors set q=?,r=? where encounter_id=? and account_id=?",d.q()*BattleService.RADIUS,d.r()*BattleService.RADIUS,fight,active);
            db.update("update battle_encounters set turn_start_edge=true,turn_points=6 where id=?",fight);
            assertThrows(RuntimeException.class,()->tx.executeWithoutResult(s->battles.withdraw(active,direction,true,Set.of(),now)));
            assertEquals(6,db.queryForObject("select turn_points from battle_encounters where id=?",Integer.class,fight));
            var reloaded=new SceneService(db);reloaded.initialize(map.version());assertEquals(scenes.blocked(),reloaded.blocked());
            // Reopening one of the persisted routes makes the shared edge usable again.
            var first=portals.getFirst();tx.executeWithoutResult(s->scenes.save(new ScenePortal(first.id(),first.first(),first.second(),true)));
            assertTrue(scenes.open(a,b));assertTrue(scenes.open(b,a));
            tx.executeWithoutResult(s->battles.join(user,fight,map.version(),map,now));assertTrue(battles.engaged(user));
            // A rollback cannot publish a passage state that was never committed.
            tx.executeWithoutResult(s->{scenes.save(first);s.setRollbackOnly();});assertTrue(scenes.open(a,b));
            db.update("delete from scene_portals");db.update("delete from scene_documents");scenes.refresh();
        }
    }

    @Test void startupRecoveryChangesOnlyDeadlineAndPreservesTurnData(){
        synchronized(world){
            var a=world.register("recover_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            var b=world.register("recover_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            for(UUID id:List.of(a.id(),b.id()))db.update("update accounts set approved=true,entered=true where id=?",id);
            long now=System.currentTimeMillis();
            var tx=new TransactionTemplate(manager);
            UUID battle=tx.execute(s->battles.start(a.id(),b.id(),world.map().version(),world.map(),now));
            db.update("update battle_encounters set turn_points=2,turn_deadline=1 where id=?",battle);
            var before=db.queryForMap("select * from battle_encounters where id=?",battle);
            var actors=db.queryForList("select * from battle_actors where encounter_id=? order by account_id",battle);
            var events=db.queryForList("select * from battle_events where encounter_id=? order by id",battle);
            world.load();
            var after=db.queryForMap("select * from battle_encounters where id=?",battle);
            assertTrue(((Number)after.remove("turn_deadline")).longValue()>now);before.remove("turn_deadline");assertEquals(before,after);
            assertEquals(actors,db.queryForList("select * from battle_actors where encounter_id=? order by account_id",battle));
            assertEquals(events,db.queryForList("select * from battle_events where encounter_id=? order by id",battle));
            tx.executeWithoutResult(s->battles.advance(Set.of(),now+1));
            assertEquals(before.get("turn_account_id"),db.queryForMap("select turn_account_id from battle_encounters where id=?",battle).get("turn_account_id"));
        }
    }

    @Test void restartCancelsUnfinishedGatheringAndRestWithoutAwardingItems(){
        synchronized(world){
            var a=world.register("gather_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            var node=db.queryForMap("select * from resource_nodes where kind='wood' limit 1");
            db.update("insert into world_actions values(?,?,?,?,?,?,?)",a.id(),"chop",node.get("id"),node.get("q"),node.get("r"),1,2);
            db.update("update characters set timer_kind='rest',timer_end=2,saved_at=1,hp=1 where id=?",a.id());
            var before=db.queryForList("select * from inventories where character_id=? order by item_code",a.id());
            world.load();
            assertEquals(0,db.queryForObject("select count(*) from world_actions where account_id=?",Integer.class,a.id()));
            assertEquals(0,characters.get(a.id()).timerEnd());assertEquals(1,characters.get(a.id()).hp());
            assertEquals(before,db.queryForList("select * from inventories where character_id=? order by item_code",a.id()));
        }
    }

    @Test void terrainDisconnectionAndAlternativePathChangePublicEdgesAfterCommit(){
        synchronized(world){
            var map=world.map();var owner=map.spawn();var zero=new WorldMap.Hex(0,0);
            var left=new WorldMap.Hex(-1,0);var right=new WorldMap.Hex(1,0);
            var cells=new ArrayList<>(List.of(new SceneMap.Cell(-1,0,"grass",true),new SceneMap.Cell(0,0,"rock",false),new SceneMap.Cell(1,0,"grass",true)));
            var middle=new SceneMap(UUID.randomUUID(),map.version(),1,2,owner,"middle",2,cells);
            var west=new SceneMap(UUID.randomUUID(),map.version(),1,2,new WorldMap.Hex(owner.q()-1,owner.r()),"west",2,List.of(new SceneMap.Cell(0,0,"grass",true)));
            var east=new SceneMap(UUID.randomUUID(),map.version(),1,2,new WorldMap.Hex(owner.q()+1,owner.r()),"east",2,List.of(new SceneMap.Cell(0,0,"grass",true)));
            var tx=new TransactionTemplate(manager);
            tx.executeWithoutResult(s->{for(var scene:List.of(middle,west,east))scenes.save(scene);
                scenes.save(new ScenePortal(UUID.randomUUID(),new ScenePortal.Endpoint(middle.id(),left),new ScenePortal.Endpoint(west.id(),zero),true));
                scenes.save(new ScenePortal(UUID.randomUUID(),new ScenePortal.Endpoint(middle.id(),right),new ScenePortal.Endpoint(east.id(),zero),true));});
            assertFalse(scenes.open(owner,west.owner()));assertFalse(scenes.open(owner,east.owner()));
            cells.add(new SceneMap.Cell(-1,1,"grass",true));cells.add(new SceneMap.Cell(0,1,"grass",true));
            tx.executeWithoutResult(s->scenes.save(new SceneMap(middle.id(),map.version(),1,2,owner,"middle",2,cells)));
            assertTrue(scenes.open(owner,west.owner()));assertTrue(scenes.open(owner,east.owner()));
            var afterRestart=new SceneService(db);afterRestart.initialize(map.version());assertTrue(afterRestart.open(owner,east.owner()));
        }
    }

    @Test void soulSecondStepRechecksTheSharedBoundary(){
        synchronized(world){
            var map=world.map();var index=map.index();
            var a=map.tiles().stream().filter(t->t.walkable()&&List.of(1,2).stream().allMatch(n->{var h=new WorldMap.Hex(t.q()+n,t.r());return index.containsKey(h)&&index.get(h).walkable();})).findFirst().orElseThrow().hex();
            var middle=new WorldMap.Hex(a.q()+1,a.r());var end=new WorldMap.Hex(a.q()+2,a.r());
            var actor=world.register("soul_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            db.update("update accounts set approved=true,entered=true where id=?",actor.id());characters.relocate(actor.id(),a.q(),a.r());
            db.update("update characters set life='soul',hp=0 where id=?",actor.id());
            assertEquals(List.of(middle,end),world.move(actor.id(),end.q(),end.r(),map.version()));
            var first=SceneMap.outdoor(map,middle,2);var second=SceneMap.outdoor(map,end,2);var zero=new WorldMap.Hex(0,0);
            new TransactionTemplate(manager).executeWithoutResult(s->{scenes.save(first);scenes.save(second);scenes.save(new ScenePortal(UUID.randomUUID(),new ScenePortal.Endpoint(first.id(),zero),new ScenePortal.Endpoint(second.id(),zero),false));});
            assertTrue(scenes.open(a,middle),"The first step must stay open: "+scenes.blocked());
            assertFalse(scenes.open(middle,end),"Only the second step is closed");
            world.advance();assertEquals(middle,characters.get(actor.id()).hex());
        }
    }

    @Test void expiredNpcTurnAdvancesBeforeItCanAct(){
        synchronized(world){
            var account=world.register("timeout_"+UUID.randomUUID().toString().substring(0,8),"phase-test-password");
            db.update("update accounts set approved=true,entered=true where id=?",account.id());
            UUID monster=CharacterService.ISLAND_BEAST;
            db.update("update characters set life='alive',hp=max_hp where id=?",monster);
            characters.relocate(monster,account.q(),account.r());
            long now=System.currentTimeMillis();var tx=new TransactionTemplate(manager);
            UUID fight=tx.execute(s->battles.start(monster,account.id(),world.map().version(),world.map(),now));
            db.update("update battle_encounters set turn_account_id=?,turn_deadline=1 where id=?",monster,fight);
            tx.executeWithoutResult(s->battles.advance(Set.of(account.id()),now));
            assertNotEquals(monster,db.queryForObject("select turn_account_id from battle_encounters where id=?",UUID.class,fight));
        }
    }

    @Test void savingSceneCannotOverwriteAnotherWorldOrDetachExistingPortal(){
        synchronized(world){
            var map=world.map();var id=UUID.randomUUID();
            var foreign=new SceneMap(id,"foreign-world",1,2,map.spawn(),"foreign",2,List.of(new SceneMap.Cell(0,0,"grass",true)));
            var json=tools.jackson.databind.json.JsonMapper.builder().build();
            db.update("insert into scene_documents values(?,?,?)",id,foreign.worldVersion(),json.writeValueAsString(foreign));
            var local=new SceneMap(id,map.version(),1,2,map.spawn(),"local",2,foreign.cells());
            var tx=new TransactionTemplate(manager);
            assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->scenes.save(local)));
            assertEquals(json.writeValueAsString(foreign),db.queryForObject("select document from scene_documents where id=?",String.class,id));
            var a=SceneMap.outdoor(map,map.spawn(),2);var b=SceneMap.outdoor(map,map.spawn().neighbors().stream().filter(h->map.index().containsKey(h)).findFirst().orElseThrow(),2);
            var zero=new WorldMap.Hex(0,0);
            tx.executeWithoutResult(s->{scenes.save(a);scenes.save(b);scenes.save(new ScenePortal(UUID.randomUUID(),new ScenePortal.Endpoint(a.id(),zero),new ScenePortal.Endpoint(b.id(),zero),true));});
            assertThrows(IllegalArgumentException.class,()->tx.executeWithoutResult(s->scenes.save(new SceneMap(a.id(),map.version(),1,2,a.owner(),"removed endpoint",2,List.of(new SceneMap.Cell(1,0,"grass",true))))));
        }
    }
}
