package game.world;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Skills belong to individual characters, independent of their controller or species. */
@Service
public class SkillService {
    public record Skill(String code,String name,String description,String effect,int cost,int minRange,int maxRange) {}
    private final JdbcTemplate db;
    SkillService(JdbcTemplate db){this.db=db;}
    public List<Skill> learned(UUID id){return db.query("select d.* from skill_definitions d join character_skills s on s.skill_code=d.code where s.character_id=? order by d.code",(r,n)->new Skill(r.getString("code"),r.getString("name"),r.getString("description"),r.getString("effect"),r.getInt("action_cost"),r.getInt("min_range"),r.getInt("max_range")),id);}
    public Skill require(UUID id,String code){return learned(id).stream().filter(s->s.code().equals(code)).findFirst().orElseThrow(()->AccountService.bad("尚未学会这个技能"));}
    public void set(UUID id,String code,boolean granted){
        if(!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from skill_definitions where code=?)",Boolean.class,code)))throw AccountService.bad("技能不存在");
        if(granted)db.update("insert into character_skills(character_id,skill_code) values(?,?) on conflict do nothing",id,code);
        else db.update("delete from character_skills where character_id=? and skill_code=?",id,code);
    }
}
