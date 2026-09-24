package game.world;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** All calls run under WorldService's lock; mutations share its database transaction. */
@Service
public class FerryService {
    private final JdbcTemplate db;
    private final int woodNeeded,stoneNeeded;
    private final long dwellMs,stepMs;
    private WorldMap world;
    private List<WorldMap.Hex> route=List.of();
    private record Saved(int wood,int stone,boolean built,String phase,int index,long nextAt,long savedAt) {}
    public record State(int wood,int stone,int woodNeeded,int stoneNeeded,boolean built,String phase,
        int q,int r,long nextAt,long dwellMs,long travelMs,WorldMap.Hex mainlandPort,WorldMap.Hex islandLanding,
        List<WorldMap.Hex> route,List<UUID> passengerIds) {}
    FerryService(JdbcTemplate db,@Value("${game.ferry.wood:8}") int wood,@Value("${game.ferry.stone:4}") int stone,
        @Value("${game.ferry.dwell-ms:30000}") long dwell,@Value("${game.ferry.step-ms:2500}") long step){
        this.db=db;woodNeeded=Math.max(1,wood);stoneNeeded=Math.max(1,stone);dwellMs=Math.max(350,dwell);stepMs=Math.max(350,step);
    }
    public void initialize(WorldMap value,long now){
        world=value;route=world.seaRoute();
        db.update("insert into ferry_state(id,world_version,saved_at) values(1,?,?) on conflict(id) do nothing",world.version(),now);
        Saved s=saved();
        // Preserve the remaining stage duration while the game server is stopped.
        if(s.built)db.update("update ferry_state set next_at=?,saved_at=? where id=1",now+Math.max(0,s.nextAt-s.savedAt),now);
        db.update("update ferry_state set world_version=? where id=1",world.version());
        syncPassengers(saved());
    }
    public void reset(WorldMap value,long now){db.update("delete from ferry_passengers");db.update("delete from ferry_state");initialize(value,now);}
    private Saved saved(){return db.queryForObject("select wood,stone,built,phase,route_index,next_at,saved_at from ferry_state where id=1",
        (rs,n)->new Saved(rs.getInt(1),rs.getInt(2),rs.getBoolean(3),rs.getString(4),rs.getInt(5),rs.getLong(6),rs.getLong(7)));}
    public List<UUID> passengers(){return db.query("select account_id from ferry_passengers order by account_id",(rs,n)->rs.getObject(1,UUID.class));}
    public boolean aboard(UUID id){return db.queryForObject("select count(*) from ferry_passengers where account_id=?",Integer.class,id)>0;}
    public void requireAshore(UUID id){if(aboard(id))throw AccountService.bad("你正在船上，请靠岸下船后再行动");}
    public State state(){Saved s=saved();var h=route.get(s.index);return new State(s.wood,s.stone,woodNeeded,stoneNeeded,s.built,s.phase,h.q(),h.r(),s.nextAt,dwellMs,(route.size()-1)*stepMs,
        world.place("port").hex(),world.place("landing").hex(),route,passengers());}
    public String contribute(UUID id,String item,int quantity,long now){
        Saved s=saved();
        if(s.built)throw AccountService.bad("公共小船已经建成");
        if(!Set.of("wood","stone").contains(Objects.requireNonNullElse(item,""))||quantity<1||quantity>100000)throw AccountService.bad("请选择木头或石头，以及有效数量");
        int remaining=item.equals("wood")?woodNeeded-s.wood:stoneNeeded-s.stone;
        Long inventory=db.queryForObject("select coalesce((select quantity from inventories where account_id=? and item_code=?),0)",Long.class,id,item);
        int amount=(int)Math.min(Math.min(quantity,remaining),inventory);
        if(amount<=0)throw AccountService.bad(remaining<=0?"这类材料已经足够":"背包里没有这种材料");
        db.update("update inventories set quantity=quantity-? where account_id=? and item_code=?",amount,id,item);
        int wood=s.wood+(item.equals("wood")?amount:0),stone=s.stone+(item.equals("stone")?amount:0);
        boolean built=wood>=woodNeeded&&stone>=stoneNeeded;
        db.update("update ferry_state set wood=?,stone=?,built=?,next_at=?,saved_at=? where id=1",wood,stone,built,built?now+dwellMs:0,now);
        return built?"公共小船已建成，正在港口等候登船":"已投入"+(item.equals("wood")?"木头":"石头")+" × "+amount;
    }
    public WorldMap.Hex dock(){Saved s=saved();if(!s.built)return null;return switch(s.phase){case "mainland"->world.place("port").hex();case "island"->world.place("landing").hex();default->null;};}
    public void board(UUID id,int q,int r){
        requireAshore(id);var dock=dock();
        if(dock==null)throw AccountService.bad("小船尚未靠岸，请等待下一班");
        if(dock.q()!=q||dock.r()!=r)throw AccountService.bad("请先到达小船当前停靠的岸边");
        db.update("insert into ferry_passengers values(?)",id);syncPassengers(saved());
    }
    public void disembark(UUID id){
        if(!aboard(id))throw AccountService.bad("你当前不在船上");
        var dock=dock();if(dock==null)throw AccountService.bad("航行途中不能下船，请等待靠岸");
        db.update("delete from ferry_passengers where account_id=?",id);
        db.update("update accounts set q=?,r=? where id=?",dock.q(),dock.r(),id);
    }
    private void syncPassengers(Saved s){var h=route.get(s.index);db.update("update accounts set q=?,r=? where id in (select account_id from ferry_passengers)",h.q(),h.r());}
    public boolean advance(long now){
        Saved s=saved();if(!s.built)return false;
        String phase=s.phase;int index=s.index;long next=s.nextAt;boolean changed=false;
        while(next<=now){
            changed=true;
            switch(phase){
                case "mainland" -> {phase="outbound";next+=stepMs;}
                case "island" -> {phase="inbound";next+=stepMs;}
                case "outbound" -> {index++;if(index==route.size()-1){phase="island";next+=dwellMs;}else next+=stepMs;}
                case "inbound" -> {index--;if(index==0){phase="mainland";next+=dwellMs;}else next+=stepMs;}
                default -> throw new IllegalStateException("Unknown ferry phase");
            }
        }
        if(changed||now-s.savedAt>=1000)db.update("update ferry_state set phase=?,route_index=?,next_at=?,saved_at=? where id=1",phase,index,next,now);
        if(changed)syncPassengers(new Saved(s.wood,s.stone,true,phase,index,next,now));
        return changed;
    }
}
