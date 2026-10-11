package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Persisted status durations count only the affected character's battle turn starts. */
@Service
public class StatusService {
    public record State(String code,String name,String description,int remaining,int damage,UUID sourceId) {}
    private final JdbcTemplate db;
    StatusService(JdbcTemplate db){this.db=db;}
    public List<State> list(UUID actor){return db.query("select d.*,s.remaining,s.source_id from character_statuses s join status_definitions d on d.code=s.status_code where s.character_id=? order by d.code",(r,n)->new State(r.getString("code"),r.getString("name"),r.getString("description"),r.getInt("remaining"),r.getInt("tick_damage"),r.getObject("source_id",UUID.class)),actor);}
    public boolean has(UUID actor,String code){return list(actor).stream().anyMatch(s->s.code().equals(code));}
    public boolean apply(UUID actor,String code,UUID source){
        boolean refresh=has(actor,code);
        int changed=db.update("insert into character_statuses(character_id,status_code,source_id,remaining) select ?,code,?,duration from status_definitions where code=? on conflict(character_id,status_code) do update set remaining=excluded.remaining,source_id=excluded.source_id",actor,source,code);
        if(changed==0)throw AccountService.bad("未知状态");return refresh;
    }
    public boolean remove(UUID actor,String code){return db.update("delete from character_statuses where character_id=? and status_code=?",actor,code)>0;}
    public void elapsed(UUID actor,String code){db.update("delete from character_statuses where character_id=? and status_code=? and remaining=1",actor,code);db.update("update character_statuses set remaining=remaining-1 where character_id=? and status_code=?",actor,code);}
}
