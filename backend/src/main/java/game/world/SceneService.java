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
    public long revision(){return revision;}
    public SceneService(JdbcTemplate db){this.db=db;}
    public void initialize(String version){this.version=version;refresh();}
    public boolean open(WorldMap.Hex from,WorldMap.Hex to){return passages.open(from,to);}
    public List<WorldPassages.Edge> blocked(){return passages.blocked();}
    public void refresh(){
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
    public void save(SceneMap scene){
        if(!scene.worldVersion().equals(version))throw new IllegalArgumentException("Wrong world version");
        db.update("insert into scene_documents(id,world_version,document) values(?,?,?) on conflict(id) do update set document=excluded.document",scene.id(),version,json.writeValueAsString(scene));
        refreshAfterCommit();
    }
    public void save(ScenePortal portal){
        var owners=db.queryForList("select document from scene_documents where id in (?,?) and world_version=?",String.class,portal.first().sceneId(),portal.second().sceneId(),version).stream().map(d->json.readValue(d,SceneMap.class)).toList();
        if(owners.size()!=2)throw new IllegalArgumentException("Portal scenes must exist in this world");
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
