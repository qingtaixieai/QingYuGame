package game.world;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.junit.jupiter.api.Assertions.*;

/** Production serialization vs current client required top-level fields.
 * Does not validate enum values, nested payloads or reference non-nullability. */
class GameplaySerializationContractTest {
    @Test void requiredClientFieldsExistInProductionJson() throws Exception {
        String protocol = Files.readString(Path.of("../frontend/src/shared/protocol.ts"));
        var mapper = JsonMapper.builder().build();
        var records = Map.of("Character",CharacterService.Character.class,
            "Loadout",EquipmentService.Loadout.class,"BattleSummary",BattleService.Summary.class,
            "BattleActor",BattleService.Actor.class,"BattleIntent",BattleService.Intent.class,
            "BattleEvent",BattleService.Event.class,"EquipmentItem",EquipmentService.Item.class);
        for (var entry : records.entrySet()) {
            var components = entry.getValue().getRecordComponents();
            var types = new Class<?>[components.length];
            var values = new Object[components.length];
            for (int i=0;i<components.length;i++) {
                types[i] = components[i].getType();
                if(types[i] == int.class) values[i] = Integer.valueOf(7);
                else if(types[i] == long.class) values[i] = Long.valueOf(7);
                else if(types[i] == boolean.class) values[i] = Boolean.TRUE;
                else if(types[i].isPrimitive()) fail("Add sample for " + types[i]);
            }
            var record = entry.getValue().getDeclaredConstructor(types).newInstance(values);
            var json = mapper.readTree(mapper.writeValueAsString(record));
            var declaration = Pattern.compile("(?m)^export type " + entry.getKey() + "\\s*=([^\\r\\n]+)").matcher(protocol);
            assertTrue(declaration.find(), "Missing client type: " + entry.getKey());
            for (String field : requiredFields(declaration.group(1)))
                assertTrue(json.has(field), entry.getKey()+" missing client field "+field);
            for (var component : components) {
                if(component.getType() == boolean.class)
                    assertTrue(json.get(component.getName()).isBoolean(), component.getName());
                else if(component.getType().isPrimitive())
                    assertTrue(json.get(component.getName()).isNumber(), component.getName());
            }
        }
    }

    static Set<String> requiredFields(String declaration) {
        var fields = new LinkedHashSet<String>();
        int open = declaration.indexOf('{');
        assertTrue(open >= 0, "Expected object type");
        String prefix = declaration.substring(0, open).replaceAll("\\s", "");
        assertTrue(prefix.isEmpty() || prefix.equals("Hex&"), "Unsupported intersection: "+prefix);
        if(prefix.equals("Hex&")) fields.addAll(List.of("q", "r"));
        int depth = 0, properties = 0;
        boolean closed = false;
        var part = new StringBuilder();
        for(int i=open+1;i<declaration.length();i++) {
            char c = declaration.charAt(i);
            if((c==';' || c=='}') && depth==0) {
                String property = part.toString().trim();
                if(!property.isEmpty()) {
                    var match = Pattern.compile("^(\\w+)(\\??)\\s*:").matcher(property);
                    assertTrue(match.find(), "Unsupported property: "+property);
                    if(match.group(2).isEmpty()) fields.add(match.group(1));
                    properties++;
                }
                part.setLength(0);
                if(c=='}') {closed=true;break;}
            } else {
                if(c=='{' || c=='(' || c=='[' || c=='<') depth++;
                if(c=='}' || c==')' || c==']' || c=='>') depth--;
                part.append(c);
            }
        }
        assertTrue(closed && properties>0 && !fields.isEmpty(), "Invalid or empty declaration");
        return fields;
    }

    @Test void parserKeepsNestedFieldsOutAndIncludesHex() {
        assertEquals(Set.of("q","r","id","nested"),
            requiredFields("Hex&{id:string;nested:{child:number;other:string};optional?:string};"));
        assertThrows(AssertionError.class, () -> requiredFields("{};"));
        assertThrows(AssertionError.class, () -> requiredFields("Other&{id:string};"));
    }
}
