package game.world;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Server-owned preparation. A ready charge belongs to a character, not an encounter. */
@Service
final class SkillPreparation {
    static final String FIREBALL="fireball";
    static final int DAMAGE=5;
    enum State { NONE, CHANTING, READY }
    private final JdbcTemplate db;

    SkillPreparation(JdbcTemplate db){this.db=db;}

    State state(UUID actor,long now){
        return db.query("select ready,ready_at from skill_preparations where character_id=? and skill_code=?",
            (rs,n)->{Long due=rs.getObject(2,Long.class);return rs.getBoolean(1)||due!=null&&due<=now?State.READY:State.CHANTING;},
            actor,FIREBALL).stream().findFirst().orElse(State.NONE);
    }

    void begin(UUID actor,UUID encounter,Long dueAt){
        if(db.update("insert into skill_preparations(character_id,skill_code,encounter_id,ready_at) values(?,?,?,?) on conflict do nothing",
            actor,FIREBALL,encounter,dueAt)!=1)throw AccountService.bad("火球术已经在准备中");
    }

    boolean completeTurn(UUID actor,UUID encounter){
        return db.update("update skill_preparations set ready=true,encounter_id=null where character_id=? and skill_code=? and encounter_id=?",
            actor,FIREBALL,encounter)>0;
    }

    boolean interrupt(UUID actor,long now){
        return db.update("delete from skill_preparations where character_id=? and skill_code=? and not ready and (ready_at is null or ready_at>?)",
            actor,FIREBALL,now)>0;
    }

    void spend(UUID actor,long now){
        if(db.update("delete from skill_preparations where character_id=? and skill_code=? and (ready or ready_at<=?)",
            actor,FIREBALL,now)!=1)throw AccountService.bad("火球术尚未就绪");
    }

    void clear(UUID actor){db.update("delete from skill_preparations where character_id=?",actor);}
    void cancelEncounter(UUID encounter){db.update("delete from skill_preparations where encounter_id=?",encounter);}
}
