package game.world;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
final class SceneActionReceipts {
    private static final long LIFETIME_MS=86_400_000;
    private record Payload(String action,Integer q,Integer r) {}
    private final JdbcTemplate db;
    private final JsonMapper json;

    SceneActionReceipts(JdbcTemplate db,JsonMapper json){
        this.db=db;
        this.json=json;
    }

    // Called inside the same transaction that validates and executes the action.
    boolean reserve(UUID actor,UUID request,String action,Integer q,Integer r,long now){
        String payload=json.writeValueAsString(new Payload(action,q,r));
        if(db.update("""
            insert into scene_action_receipts(actor_id,request_id,created_at,payload)
            values(?,?,?,?) on conflict (actor_id,request_id) do nothing
            """,actor,request,now,payload)==1){
            db.update("delete from scene_action_receipts where actor_id=? and created_at<?",actor,now-LIFETIME_MS);
            return true;
        }
        String previous=db.queryForObject("select payload from scene_action_receipts where actor_id=? and request_id=?",String.class,actor,request);
        if(previous!=null&&!previous.equals(payload))throw AccountService.bad("动作编号已用于不同请求");
        // Receipts written before V36 have no stored original payload.
        return false;
    }
}
