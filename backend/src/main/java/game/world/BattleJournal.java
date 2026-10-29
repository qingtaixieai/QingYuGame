package game.world;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * 战斗事件的持久化、事件读取与战报隐私脱敏。所有调用都在 BattleService 的既有事务/监控内完成，
 * 这里不开启自己的事务。
 */
public final class BattleJournal {
    private static final tools.jackson.databind.json.JsonMapper json=tools.jackson.databind.json.JsonMapper.builder().build();
    private final JdbcTemplate db;
    private final CharacterService characters;

    BattleJournal(JdbcTemplate db,CharacterService characters){this.db=db;this.characters=characters;}

    void event(UUID battle,String kind,UUID actor,UUID target,Integer q,Integer r,long now){
        event(battle,kind,actor,target,q,r,now,null,null);
    }
    void event(UUID battle,String kind,UUID actor,UUID target,Integer q,Integer r,long now,Integer amount,Integer blocked){
        db.update("insert into battle_events(encounter_id,kind,actor_id,target_id,q,r,happened_at,actor_name,target_name,amount,blocked) values(?,?,?,?,?,?,?,?,?,?,?)",battle,kind,actor,target,q,r,now,actor==null?null:characters.get(actor).name(),target==null?null:characters.get(target).name(),amount,blocked);
    }
    void detailLast(UUID battle,Map<String,Object> details){
        db.update("update battle_events set details=? where id=(select max(id) from battle_events where encounter_id=?)",json.writeValueAsString(details),battle);
    }
    List<BattleService.Event> events(UUID battle,int limit){
        var result=db.query("select * from battle_events where encounter_id=? order by id desc limit ?",(rs,n)->new BattleService.Event(rs.getLong("id"),rs.getString("kind"),rs.getObject("actor_id",UUID.class),rs.getObject("target_id",UUID.class),(Integer)rs.getObject("q"),(Integer)rs.getObject("r"),rs.getLong("happened_at"),rs.getString("actor_name"),rs.getString("target_name"),(Integer)rs.getObject("amount"),(Integer)rs.getObject("blocked"),rs.getString("details")==null?Map.of():json.readValue(rs.getString("details"),Map.class)),battle,limit);
        Collections.reverse(result);return result;
    }
    /** 进行中的战斗返回实时事件，否则读取最近一次战报；都没有时返回空战报。 */
    Object report(UUID actor,UUID activeBattleId){
        if(activeBattleId!=null)return Map.of("battleId",activeBattleId,"active",true,"events",events(activeBattleId,1000));
        return db.query("select document from recent_battle_reports where character_id=?",(r,n)->json.readValue(r.getString(1),Object.class),actor).stream().findFirst().orElse(Map.of("active",false,"events",List.of()));
    }
    /** 战斗关闭时给所有参战者落一份战报（必须在 close 事件写入之后调用）。 */
    void saveReport(UUID battle,long now){
        String document=json.writeValueAsString(Map.of("battleId",battle,"active",false,"finishedAt",now,"events",events(battle,1000)));
        db.update("insert into recent_battle_reports(character_id,encounter_id,finished_at,document) select character_id,?,?,? from battle_members where encounter_id=? on conflict(character_id) do update set encounter_id=excluded.encounter_id,finished_at=excluded.finished_at,document=excluded.document",battle,now,document,battle);
    }
    /** 以真实 JsonMapper 复制成 Map，再按查看者遮蔽他人属性/伤害细节；源数据保持只读。 */
    @SuppressWarnings("unchecked")
    public static Object publicView(Object source,UUID viewer,boolean current){
        Map<String,Object> data=json.readValue(json.writeValueAsString(source),Map.class);
        if(current)redactActors(data,viewer);
        redactEvents(data,viewer);
        if(current)redactIntents(data,viewer);
        return data;
    }
    @SuppressWarnings("unchecked")
    private static void redactActors(Map<String,Object> data,UUID viewer){
        for(var raw:(List<Map<String,Object>>)data.getOrDefault("actors",List.of()))if(!viewer.toString().equals(raw.get("accountId"))){
            var profile=(Map<String,Object>)raw.get("character");
            for(String field:List.of("strength","agility","constitution","intellect","perception","willpower","weapon","offhand","body","bindQ","bindR","deathQ","deathR","timerKind","timerEnd","protectedUntil"))profile.remove(field);
            raw.remove("loadout");raw.remove("weapon");raw.remove("initiativeRoll");raw.remove("initiativeScore");
        }
    }
    @SuppressWarnings("unchecked")
    private static void redactEvents(Map<String,Object> data,UUID actor){
        for(var e:(List<Map<String,Object>>)data.getOrDefault("events",List.of())){
            var details=(Map<String,Object>)e.get("details");if(details==null)continue;
            if(!actor.toString().equals(e.get("targetId"))){details.remove("armorReduction");details.remove("shieldReduction");}
            if(!actor.toString().equals(e.get("actorId"))){details.remove("weapon");details.remove("item");details.remove("quantity");}
        }
    }
    @SuppressWarnings("unchecked")
    private static void redactIntents(Map<String,Object> data,UUID viewer){
        for(var raw:(List<Map<String,Object>>)data.getOrDefault("intents",List.of()))if(!viewer.toString().equals(raw.get("attackerId"))){raw.remove("damage");raw.remove("weapon");}
    }
}
