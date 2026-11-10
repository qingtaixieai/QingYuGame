package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/** Accessed through WorldService's monitor. Saved terrain and portals determine public boundaries. */
@Service
public class SceneService {
    private final JdbcTemplate db;
    private final JsonMapper json=JsonMapper.builder().build();
    private final WorldPassages passages=new WorldPassages();
    private String version;
    private long revision;
    public synchronized long revision(){return revision;}
    public synchronized String version(){return version;}
    public SceneService(JdbcTemplate db){this.db=db;}
    public synchronized void initialize(String version){this.version=version;refresh();}
    public synchronized boolean open(WorldMap.Hex from,WorldMap.Hex to){return passages.open(from,to);}
    public synchronized List<WorldPassages.Edge> blocked(){return passages.blocked();}
    public synchronized void refresh(){
        var scenes=new HashMap<UUID,SceneMap>();
        for(String doc:db.queryForList("select document from scene_documents where world_version=?",String.class,version)){
            var scene=json.readValue(doc,SceneMap.class);scenes.put(scene.id(),scene);
        }
        var portals=db.queryForList("select p.document from scene_portals p join scene_documents a on a.id=p.first_scene join scene_documents b on b.id=p.second_scene where a.world_version=? and b.world_version=?",String.class,version,version)
            .stream().map(doc->json.readValue(doc,ScenePortal.class)).toList();
        var connections=new ArrayList<WorldPassages.Connection>();
        for(var portal:portals){
            SceneMap a=scenes.get(portal.first().sceneId()),b=scenes.get(portal.second().sceneId());
            // Interior doors never block unrelated outdoor traversal.
            if(a.level()!=2||b.level()!=2)continue;
            connections.add(new WorldPassages.Connection(a.owner(),b.owner(),portal.open()
                &&usable(a,portal.first().cell(),portals,scenes)&&usable(b,portal.second().cell(),portals,scenes)));
        }
        var before=passages.blocked();passages.replace(connections);
        if(!before.equals(passages.blocked()))revision++;
    }
    private boolean usable(SceneMap scene,WorldMap.Hex endpoint,List<ScenePortal> portals,Map<UUID,SceneMap> scenes){
        var topology=scene.topology();
        if(!topology.connected(endpoint,endpoint,Set.of()))return false;
        // For each distinct outdoor exit, at least one endpoint must be reachable.
        var exits=new HashMap<UUID,List<WorldMap.Hex>>();
        for(var p:portals){
            ScenePortal.Endpoint local=null,remote=null;
            if(p.first().sceneId().equals(scene.id())){local=p.first();remote=p.second();}
            else if(p.second().sceneId().equals(scene.id())){local=p.second();remote=p.first();}
            if(local!=null&&scenes.get(remote.sceneId()).level()==2)exits.computeIfAbsent(remote.sceneId(),k->new ArrayList<>()).add(local.cell());
        }
        return exits.values().stream().allMatch(options->options.stream().anyMatch(h->topology.connected(endpoint,h,Set.of())));
    }
    public synchronized void save(SceneMap scene){
        if(!scene.worldVersion().equals(version))throw new IllegalArgumentException("Wrong world version");
        var existing=db.queryForList("select document from scene_documents where id=?",String.class,scene.id());
        if(!existing.isEmpty()){
            var old=json.readValue(existing.getFirst(),SceneMap.class);
            if(!old.worldVersion().equals(version)||old.level()!=scene.level()||!old.owner().equals(scene.owner()))throw new IllegalArgumentException("Saved scene identity cannot change");
        }
        for(String document:db.queryForList("select document from scene_portals where first_scene=? or second_scene=?",String.class,scene.id(),scene.id())){
            var portal=json.readValue(document,ScenePortal.class);
            var endpoint=portal.first().sceneId().equals(scene.id())?portal.first():portal.second();
            if(scene.cells().stream().noneMatch(c->c.hex().equals(endpoint.cell())))throw new IllegalArgumentException("Cannot remove a referenced portal endpoint");
        }
        int written=db.update("insert into scene_documents(id,world_version,document) values(?,?,?) on conflict(id) do update set document=excluded.document where scene_documents.world_version=excluded.world_version",scene.id(),version,json.writeValueAsString(scene));
        if(written!=1)throw new IllegalArgumentException("Scene belongs to another world");
        refreshAfterCommit();
    }
    public synchronized void save(ScenePortal portal){
        var owners=db.queryForList("select document from scene_documents where id in (?,?) and world_version=?",String.class,portal.first().sceneId(),portal.second().sceneId(),version).stream().map(d->json.readValue(d,SceneMap.class)).toList();
        if(owners.size()!=2)throw new IllegalArgumentException("Portal scenes must exist in this world");
        var existing=db.queryForList("select document from scene_portals where id=?",String.class,portal.id());
        if(!existing.isEmpty()){
            var old=json.readValue(existing.getFirst(),ScenePortal.class);
            if(!old.first().sceneId().equals(portal.first().sceneId())||!old.second().sceneId().equals(portal.second().sceneId()))throw new IllegalArgumentException("Saved portal scene identity cannot change");
        }
        for(var scene:owners){var endpoint=scene.id().equals(portal.first().sceneId())?portal.first():portal.second();if(scene.cells().stream().noneMatch(c->c.hex().equals(endpoint.cell())))throw new IllegalArgumentException("Portal endpoint outside scene");}
        if(owners.get(0).level()==2&&owners.get(1).level()==2&&WorldMap.distance(owners.get(0).owner(),owners.get(1).owner())!=1)throw new IllegalArgumentException("Outdoor scenes must be adjacent");
        db.update("insert into scene_portals(id,first_scene,second_scene,document) values(?,?,?,?) on conflict(id) do update set first_scene=excluded.first_scene,second_scene=excluded.second_scene,document=excluded.document",portal.id(),portal.first().sceneId(),portal.second().sceneId(),json.writeValueAsString(portal));
        refreshAfterCommit();
    }
    private void refreshAfterCommit(){
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){refresh();}});
        else refresh();
    }
}
