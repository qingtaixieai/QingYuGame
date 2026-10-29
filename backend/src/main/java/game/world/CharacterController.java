package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adapter only. Domain actions remain serialized by WorldService. */
@RestController
@RequestMapping("/api")
public class CharacterController {
    private final AccountService accounts;
    private final WorldService world;
    CharacterController(AccountService accounts, WorldService world) {
        this.accounts=accounts;
        this.world=world;
    }

    record Move(Integer q,Integer r,String version) {}

    record EquipmentRequest(String code,String mainHand,String offHand,String body,Boolean updateBody) {}

    record LifeRequest(String action,UUID targetId) {}

    record AmmoRequest(String code) {}

    @GetMapping("/character/status") public Object status(HttpServletRequest req){return world.status(accounts.require(req,true,false).id());}

    @PostMapping("/character/ammo") public Object ammo(@RequestBody AmmoRequest body,HttpServletRequest req){world.selectAmmo(accounts.require(req,true,false).id(),body.code());return Map.of("message","弹药已选择");}

    @GetMapping("/loot") public Object loot(HttpServletRequest req,@RequestParam String version,@RequestParam(required=false) UUID battleId){return world.lootList(accounts.require(req,true,false).id(),version,battleId);}

    @GetMapping("/loot/{id}") public Object lootContents(HttpServletRequest req,@PathVariable UUID id,@RequestParam String version,@RequestParam(required=false) UUID battleId){return world.lootContents(accounts.require(req,true,false).id(),id,version,battleId);}

    @PostMapping("/loot/action") public Object lootAction(HttpServletRequest req,@RequestBody LootService.Command body){world.lootCommand(accounts.require(req,true,false).id(),body);return Map.of("message","操作成功");}

    @GetMapping("/character") public Object character(HttpServletRequest req){return world.character(accounts.require(req,true,false).id());}

    @GetMapping("/items") public Object items(HttpServletRequest req){accounts.require(req,true,false);return world.catalog();}

    @PostMapping("/equipment") public Object equip(@RequestBody EquipmentRequest body,HttpServletRequest req){var id=accounts.require(req,true,false).id();var main=body.mainHand()!=null||body.offHand()!=null?body.mainHand():body.code();if(Boolean.TRUE.equals(body.updateBody()))world.equip(id,main,body.offHand(),body.body());else world.equip(id,main,body.offHand());return Map.of("message","装备已更新");}

    @PostMapping("/character/action") public Object life(@RequestBody LifeRequest body,HttpServletRequest req){world.lifeAction(accounts.require(req,true,false).id(),body.action(),body.targetId());return Map.of("message","行动已处理");}

    @GetMapping("/inventory") public List<Map<String,Object>> inventory(HttpServletRequest req){return world.inventory(accounts.require(req,true,false).id());}

    @PostMapping("/collect") public Map<String,String> collect(@RequestBody Move m,HttpServletRequest req){
        var a=accounts.require(req,true,false);
        if(m.q==null||m.r==null)throw AccountService.bad("坐标无效");
        return world.collect(a.id(),m.q,m.r,m.version);
    }
}
