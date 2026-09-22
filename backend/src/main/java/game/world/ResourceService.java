package game.world;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

/** Called only inside the authoritative WorldService monitor and transaction. Timers survive restarts. */
@Service
public class ResourceService {
    public record Node(UUID id,String kind,int q,int r,long readyAt) {}
    public record Action(UUID accountId,String kind,UUID nodeId,int q,int r,long startedAt,long endsAt) {}
    private final JdbcTemplate db;
    private final long chopMs,treeMs,stoneMs;
    ResourceService(JdbcTemplate db,@Value("${game.chop-ms:3000}") long chopMs,
        @Value("${game.tree-ms:60000}") long treeMs,@Value("${game.stone-ms:30000}") long stoneMs){
        this.db=db;this.chopMs=Math.max(350,chopMs);this.treeMs=Math.max(350,treeMs);this.stoneMs=Math.max(350,stoneMs);
    }
    public List<Node> nodes(){return db.query("select * from resource_nodes order by q,r",(rs,n)->new Node(rs.getObject("id",UUID.class),rs.getString("kind"),rs.getInt("q"),rs.getInt("r"),rs.getLong("ready_at")));}
    public List<Action> actions(){return db.query("select * from world_actions",(rs,n)->new Action(rs.getObject("account_id",UUID.class),rs.getString("kind"),rs.getObject("node_id",UUID.class),rs.getInt("q"),rs.getInt("r"),rs.getLong("started_at"),rs.getLong("ends_at")));}
    public List<Map<String,Object>> inventory(UUID id){return db.queryForList("select d.code,d.name,coalesce(i.quantity,0) as quantity from item_definitions d left join inventories i on i.item_code=d.code and i.account_id=? order by d.code",id);}
    public void initialize(WorldMap world){
        if(!nodes().isEmpty())return;
        for(var t:world.tiles())if(t.place()==null&&t.terrain().equals("forest"))insert("wood",t);
        var plains=new ArrayList<>(world.tiles().stream().filter(t->t.terrain().equals("plain")&&!t.road()&&t.place()==null).toList());
        Collections.shuffle(plains);
        for(var t:plains.subList(0,Math.min(30,plains.size())))insert("stone",t);
    }
    private void insert(String kind,WorldMap.Tile t){db.update("insert into resource_nodes(id,kind,q,r) values(?,?,?,?)",UUID.randomUUID(),kind,t.q(),t.r());}
    public void reset(WorldMap world){db.update("delete from world_actions");db.update("delete from resource_nodes");initialize(world);}
    public void cancel(UUID id){db.update("delete from world_actions where account_id=?",id);}
    private void award(UUID id,String item){db.update("insert into inventories(account_id,item_code,quantity) values(?,?,1) on conflict(account_id,item_code) do update set quantity=inventories.quantity+1",id,item);}
    public String collect(UUID id,int q,int r,long now){
        Node node=nodes().stream().filter(n->n.q==q&&n.r==r).findFirst().orElseThrow(()->AccountService.bad("这里没有可采集的资源"));
        if(node.readyAt!=0)throw AccountService.bad("资源正在恢复，请稍后再来");
        if(actions().stream().anyMatch(a->a.accountId.equals(id)))throw AccountService.bad("你已有正在进行的行动");
        if(actions().stream().anyMatch(a->a.nodeId.equals(node.id)))throw AccountService.bad("有人正在砍伐这片森林");
        if(node.kind.equals("stone")){
            award(id,"stone");db.update("update resource_nodes set ready_at=? where id=?",now+stoneMs,node.id);return "石头 +1";
        }
        db.update("insert into world_actions values(?,?,?,?,?,?,?)",id,"chop",node.id,q,r,now,now+chopMs);
        return "开始砍伐";
    }
    public boolean advance(WorldMap world,long now){
        boolean changed=false;
        for(Action a:actions())if(a.endsAt<=now){
            // Reward and action deletion share a transaction: never duplicate a reward after a restart.
            award(a.accountId,"wood");db.update("update resource_nodes set ready_at=? where id=?",a.endsAt+treeMs,a.nodeId);
            cancel(a.accountId);changed=true;
        }
        var nodes=nodes();Set<WorldMap.Hex> occupied=new HashSet<>();nodes.forEach(n->occupied.add(new WorldMap.Hex(n.q,n.r)));
        for(Node n:nodes)if(n.readyAt>0&&n.readyAt<=now){
            if(n.kind.equals("wood"))db.update("update resource_nodes set ready_at=0 where id=?",n.id);
            else{
                var free=new ArrayList<>(world.tiles().stream().filter(t->t.terrain().equals("plain")&&!t.road()&&t.place()==null&&!occupied.contains(t.hex())).toList());
                if(free.isEmpty())continue;
                Collections.shuffle(free);var t=free.getFirst();
                db.update("update resource_nodes set q=?,r=?,ready_at=0 where id=?",t.q(),t.r(),n.id);
                occupied.remove(new WorldMap.Hex(n.q,n.r));occupied.add(t.hex());
            }
            changed=true;
        }
        return changed;
    }
}
