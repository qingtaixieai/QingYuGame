package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adapter only. Domain actions remain serialized by WorldService. */
@RestController
@RequestMapping("/api")
public class BattleController {
    private final AccountService accounts;
    private final WorldService world;
    BattleController(AccountService accounts, WorldService world) {
        this.accounts=accounts;
        this.world=world;
    }

    record BattleStart(UUID targetId,String version) {}

    record BattleJoin(UUID battleId,String version) {}

    record BattleStep(UUID battleId,Integer q,Integer r) {}

    record BattleAttack(UUID battleId,UUID targetId,Integer q,Integer r) {}

    record BattleTurn(UUID battleId) {}

    record BattleWithdraw(UUID battleId,Integer direction,boolean force) {}

    record EquipmentRequest(String code,String mainHand,String offHand,String body,Boolean updateBody) {}

    record LifeRequest(String action,UUID targetId) {}

    record ActionRequest(UUID battleId,String actionId,UUID targetId,Integer q,Integer r) {}

    @PostMapping("/battle/action") public Object action(@RequestBody ActionRequest body,HttpServletRequest req){world.battleAction(accounts.require(req,true,false).id(),body.battleId(),body.actionId(),body.targetId(),body.q(),body.r());return Map.of("message","行动完成");}

    @GetMapping("/battle/report") public Object report(HttpServletRequest req){return world.battleReport(accounts.require(req,true,false).id());}

    @GetMapping("/battle/current") public Object currentBattle(HttpServletRequest req){return world.currentBattle(accounts.require(req,true,false).id());}

    @GetMapping("/hotbar") public Object hotbar(HttpServletRequest req){return world.hotbar(accounts.require(req,true,false).id());}

    @PostMapping("/hotbar/layout") public Object editHotbar(@RequestBody HotbarService.Edit body,HttpServletRequest req){return world.editHotbar(accounts.require(req,true,false).id(),body);}

    @PostMapping("/battle/start") public Map<String,UUID> startBattle(@RequestBody BattleStart body,HttpServletRequest req){
        if(body.targetId()==null||body.version()==null)throw AccountService.bad("请选择同格旅人");
        return Map.of("id",world.startBattle(accounts.require(req,true,false).id(),body.targetId(),body.version()));
    }

    @PostMapping("/battle/join") public Map<String,UUID> joinBattle(@RequestBody BattleJoin body,HttpServletRequest req){
        if(body.battleId()==null||body.version()==null)throw AccountService.bad("请选择此格战斗");
        return Map.of("id",world.joinBattle(accounts.require(req,true,false).id(),body.battleId(),body.version()));
    }

    @PostMapping("/battle/step") public Map<String,String> battleStep(@RequestBody BattleStep body,HttpServletRequest req){
        if(body.battleId()==null||body.q()==null||body.r()==null)throw AccountService.bad("战场位置无效");
        world.battleStep(accounts.require(req,true,false).id(),body.battleId(),body.q(),body.r());return Map.of("message","已移动");
    }

    @PostMapping("/battle/attack") public Map<String,String> battleAttack(@RequestBody BattleAttack body,HttpServletRequest req){
        if(body.battleId()==null||body.targetId()==null&&(body.q()==null||body.r()==null))throw AccountService.bad("请选择攻击格子");
        world.battleAttack(accounts.require(req,true,false).id(),body.battleId(),body.targetId(),body.q(),body.r());return Map.of("message","已标记攻击格");
    }

    @PostMapping("/battle/withdraw") public Map<String,String> battleWithdraw(@RequestBody BattleWithdraw body,HttpServletRequest req){
        if(body.battleId()==null||body.direction()==null)throw AccountService.bad("请选择撤离方向");
        world.battleWithdraw(accounts.require(req,true,false).id(),body.battleId(),body.direction(),body.force());return Map.of("message",body.force()?"已强制撤离":"已准备撤离");
    }

    @PostMapping("/battle/end-turn") public Map<String,String> battleEndTurn(@RequestBody BattleTurn body,HttpServletRequest req){
        if(body.battleId()==null)throw AccountService.bad("战斗已变化，请刷新战场");
        world.battleEndTurn(accounts.require(req,true,false).id(),body.battleId());return Map.of("message","回合结束");
    }

    @PostMapping("/battle/guard") public Map<String,String> battleGuard(@RequestBody BattleTurn body,HttpServletRequest req){
        if(body.battleId()==null)throw AccountService.bad("战斗已变化，请刷新战场");
        world.battleGuard(accounts.require(req,true,false).id(),body.battleId());return Map.of("message","已举盾");
    }

    @PostMapping("/battle/cancel-guard") public Map<String,String> battleCancelGuard(@RequestBody BattleTurn body,HttpServletRequest req){
        if(body.battleId()==null)throw AccountService.bad("战斗已变化，请刷新战场");
        world.battleCancelGuard(accounts.require(req,true,false).id(),body.battleId());return Map.of("message","已取消举盾");
    }

    @PostMapping("/battle/bandage") public Map<String,String> battleBandage(@RequestBody LifeRequest body,HttpServletRequest req){
        world.battleBandage(accounts.require(req,true,false).id(),body.targetId());return Map.of("message","已使用绷带");
    }

    @PostMapping("/battle/equipment") public Map<String,String> battleEquip(@RequestBody EquipmentRequest body,HttpServletRequest req){
        var id=accounts.require(req,true,false).id();if(Boolean.TRUE.equals(body.updateBody()))world.battleEquip(id,body.mainHand(),body.offHand(),body.body());else world.battleEquip(id,body.mainHand(),body.offHand());return Map.of("message","装备已更新");
    }
}
