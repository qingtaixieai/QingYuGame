package game.world;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 战报/战场视图脱敏：只遮蔽他人属性与伤害细节，且绝不改动源数据。 */
class BattleJournalTest {
    private static final UUID VIEWER=UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID OTHER=UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final List<String> PRIVATE_CHARACTER_FIELDS=List.of(
        "strength","agility","constitution","intellect","perception","willpower","weapon","offhand","body",
        "bindQ","bindR","deathQ","deathR","timerKind","timerEnd","protectedUntil");

    private static Map<String,Object> actor(UUID id){
        Map<String,Object> character=new LinkedHashMap<>();
        character.put("id",id.toString());
        character.put("name","角色"+id);
        for(String field:PRIVATE_CHARACTER_FIELDS)character.put(field,11);
        Map<String,Object> raw=new LinkedHashMap<>();
        raw.put("accountId",id.toString());
        raw.put("character",character);
        raw.put("loadout",Map.of("weapon","sword"));
        raw.put("weapon",Map.of("code","sword"));
        raw.put("initiativeRoll",12);
        raw.put("initiativeScore",14);
        raw.put("q",1);
        return raw;
    }

    private static Map<String,Object> event(UUID actorId,UUID targetId){
        Map<String,Object> details=new LinkedHashMap<>();
        details.put("baseDamage",7);
        details.put("attributeBonus",0);
        details.put("armorReduction",2);
        details.put("shieldReduction",1);
        details.put("finalDamage",4);
        details.put("weapon","斧");
        details.put("item","bandage");
        details.put("quantity",1);
        Map<String,Object> raw=new LinkedHashMap<>();
        raw.put("id",1);
        raw.put("kind","attack");
        raw.put("actorId",actorId.toString());
        raw.put("targetId",targetId.toString());
        raw.put("details",details);
        return raw;
    }

    private static Map<String,Object> intent(UUID attackerId){
        Map<String,Object> raw=new LinkedHashMap<>();
        raw.put("attackerId",attackerId.toString());
        raw.put("q",2);
        raw.put("damage",9);
        raw.put("weapon","斧");
        return raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object value){return (Map<String,Object>)value;}

    @Test
    @SuppressWarnings("unchecked")
    void publicCurrentHidesOtherActorsPrivateFieldsAndDamageDetails(){
        Map<String,Object> source=new LinkedHashMap<>();
        source.put("active",true);
        source.put("actors",new ArrayList<>(List.of(actor(VIEWER),actor(OTHER))));
        source.put("events",new ArrayList<>(List.of(event(OTHER,OTHER),event(VIEWER,OTHER))));
        source.put("intents",new ArrayList<>(List.of(intent(OTHER),intent(VIEWER))));

        Map<String,Object> view=map(BattleJournal.publicView(source,VIEWER,true));

        List<Map<String,Object>> actors=(List<Map<String,Object>>)view.get("actors");
        Map<String,Object> own=actors.get(0),other=actors.get(1);
        Map<String,Object> ownCharacter=(Map<String,Object>)own.get("character");
        Map<String,Object> otherCharacter=(Map<String,Object>)other.get("character");
        for(String field:PRIVATE_CHARACTER_FIELDS){assertTrue(ownCharacter.containsKey(field),"本人保留 "+field);assertFalse(otherCharacter.containsKey(field),"他人隐藏 "+field);}
        assertEquals("角色"+VIEWER,ownCharacter.get("name"));
        assertEquals("角色"+OTHER,otherCharacter.get("name"));
        assertTrue(own.containsKey("loadout")&&own.containsKey("weapon")&&own.containsKey("initiativeRoll")&&own.containsKey("initiativeScore"));
        assertFalse(other.containsKey("loadout")||other.containsKey("weapon")||other.containsKey("initiativeRoll")||other.containsKey("initiativeScore"));

        List<Map<String,Object>> events=(List<Map<String,Object>>)view.get("events");
        Map<String,Object> ownAttack=(Map<String,Object>)events.get(1).get("details");
        assertEquals(7,ownAttack.get("baseDamage"));
        assertFalse(ownAttack.containsKey("armorReduction"));
        assertEquals("斧",ownAttack.get("weapon"));
        Map<String,Object> otherAttack=(Map<String,Object>)events.get(0).get("details");
        assertEquals(7,otherAttack.get("baseDamage"));
        assertEquals(4,otherAttack.get("finalDamage"));
        assertFalse(otherAttack.containsKey("armorReduction")||otherAttack.containsKey("shieldReduction"));
        assertFalse(otherAttack.containsKey("weapon")||otherAttack.containsKey("item")||otherAttack.containsKey("quantity"));

        List<Map<String,Object>> intents=(List<Map<String,Object>>)view.get("intents");
        assertFalse(intents.get(0).containsKey("damage")||intents.get(0).containsKey("weapon"));
        assertEquals(2,intents.get(0).get("q"));
        assertTrue(intents.get(1).containsKey("damage")&&intents.get(1).containsKey("weapon"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicViewNeverMutatesTheSource(){
        Map<String,Object> other=actor(OTHER);
        Map<String,Object> otherEvent=event(OTHER,OTHER);
        Map<String,Object> otherIntent=intent(OTHER);
        Map<String,Object> source=new LinkedHashMap<>();
        source.put("active",true);
        source.put("actors",new ArrayList<>(List.of(actor(VIEWER),other)));
        source.put("events",new ArrayList<>(List.of(otherEvent)));
        source.put("intents",new ArrayList<>(List.of(otherIntent)));

        BattleJournal.publicView(source,VIEWER,true);

        Map<String,Object> sourceCharacter=(Map<String,Object>)((Map<String,Object>)other.get("character"));
        for(String field:PRIVATE_CHARACTER_FIELDS)assertTrue(sourceCharacter.containsKey(field),"源角色字段未被删除 "+field);
        assertTrue(other.containsKey("loadout")&&other.containsKey("weapon")&&other.containsKey("initiativeRoll")&&other.containsKey("initiativeScore"));
        Map<String,Object> sourceDetails=(Map<String,Object>)otherEvent.get("details");
        for(String field:List.of("armorReduction","shieldReduction","weapon","item","quantity"))assertTrue(sourceDetails.containsKey(field),"源事件字段未被删除 "+field);
        assertTrue(otherIntent.containsKey("damage")&&otherIntent.containsKey("weapon"));
        assertSame(other,((List<Object>)source.get("actors")).get(1));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publicReportUsesOnlyEventRedaction(){
        Map<String,Object> source=new LinkedHashMap<>();
        source.put("battleId",UUID.randomUUID().toString());
        source.put("active",false);
        source.put("events",new ArrayList<>(List.of(event(OTHER,OTHER),event(VIEWER,OTHER))));

        Map<String,Object> view=map(BattleJournal.publicView(source,VIEWER,false));

        List<Map<String,Object>> events=(List<Map<String,Object>>)view.get("events");
        Map<String,Object> otherAttack=(Map<String,Object>)events.get(0).get("details");
        assertFalse(otherAttack.containsKey("weapon")||otherAttack.containsKey("item")||otherAttack.containsKey("quantity"));
        assertFalse(otherAttack.containsKey("armorReduction")||otherAttack.containsKey("shieldReduction"));
        Map<String,Object> ownAttack=(Map<String,Object>)events.get(1).get("details");
        assertTrue(ownAttack.containsKey("weapon")&&ownAttack.containsKey("item")&&ownAttack.containsKey("quantity"));
        assertFalse(ownAttack.containsKey("armorReduction")||ownAttack.containsKey("shieldReduction"));
        assertEquals(false,view.get("active"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void victimSeesOwnMitigationButNotAttackersEquipment(){
        var view=map(BattleJournal.publicView(Map.of("events",List.of(event(OTHER,VIEWER))),VIEWER,false));
        var events=(List<Map<String,Object>>)view.get("events");
        var details=(Map<String,Object>)events.getFirst().get("details");
        assertEquals(2,details.get("armorReduction"));
        assertEquals(1,details.get("shieldReduction"));
        assertFalse(details.containsKey("weapon"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyReportStaysEmpty(){
        Map<String,Object> empty=new LinkedHashMap<>();
        empty.put("active",false);
        empty.put("events",List.of());

        Map<String,Object> view=map(BattleJournal.publicView(empty,VIEWER,false));

        assertEquals(false,view.get("active"));
        assertTrue(((List<Object>)view.get("events")).isEmpty());
        assertEquals(empty,view);

        Map<String,Object> noEvents=map(BattleJournal.publicView(Map.of("active",true),VIEWER,true));
        assertEquals(true,noEvents.get("active"));
        assertFalse(noEvents.containsKey("events"));
    }
}
