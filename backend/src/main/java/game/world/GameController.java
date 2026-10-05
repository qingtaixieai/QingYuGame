package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api")
public class GameController {
    private final AccountService accounts;private final WorldService world;private final ModerationService moderation;
    GameController(AccountService accounts,WorldService world,ModerationService moderation){this.moderation=moderation;this.accounts=accounts;this.world=world;}
    record EmoteRequest(String code,String version) {}
    record Credentials(String username,String password) {}
    record Move(Integer q,Integer r,String version) {}
    record Approval(boolean approved) {}
    record Password(String password) {}
    record Regenerate(String confirmation) {}
    record BattleStart(UUID targetId,String version) {}
    record BattleJoin(UUID battleId,String version) {}
    record BattleStep(UUID battleId,Integer q,Integer r) {}
    record BattleAttack(UUID battleId,UUID targetId,Integer q,Integer r) {}
    record BattleTurn(UUID battleId) {}
    record BattleWithdraw(UUID battleId,Integer direction,boolean force) {}
    record EquipmentRequest(String code,String mainHand,String offHand) {}
    record GrantRequest(String code,int quantity) {}
    record DeployCreature(String species,Integer q,Integer r) {}
    record LifeRequest(String action,UUID targetId) {}
    @GetMapping("/loot") public Object loot(HttpServletRequest req,@RequestParam String version,@RequestParam(required=false) UUID battleId){return world.lootList(accounts.require(req,true,false).id(),version,battleId);}
    @GetMapping("/loot/{id}") public Object lootContents(HttpServletRequest req,@PathVariable UUID id,@RequestParam String version,@RequestParam(required=false) UUID battleId){return world.lootContents(accounts.require(req,true,false).id(),id,version,battleId);}
    @PostMapping("/loot/action") public Object lootAction(HttpServletRequest req,@RequestBody LootService.Command body){world.lootCommand(accounts.require(req,true,false).id(),body);return Map.of("message","操作成功");}
    @GetMapping("/character") public Object character(HttpServletRequest req){return world.character(accounts.require(req,true,false).id());}
    @GetMapping("/items") public Object items(HttpServletRequest req){accounts.require(req,true,false);return world.catalog();}
    @PostMapping("/equipment") public Object equip(@RequestBody EquipmentRequest body,HttpServletRequest req){world.equip(accounts.require(req,true,false).id(),body.mainHand()!=null||body.offHand()!=null?body.mainHand():body.code(),body.offHand());return Map.of("message","装备已更新");}
    @PostMapping("/character/action") public Object life(@RequestBody LifeRequest body,HttpServletRequest req){world.lifeAction(accounts.require(req,true,false).id(),body.action(),body.targetId());return Map.of("message","行动已处理");}
    @PostMapping("/admin/accounts/{id}/items") public Object grant(@PathVariable UUID id,@RequestBody GrantRequest body,HttpServletRequest req){world.grant(accounts.require(req,true,true).id(),id,body.code(),body.quantity());return Map.of("message","物品已发放");}
    @PostMapping("/admin/creatures/deploy") public Map<String,String> deployCreature(@RequestBody DeployCreature body,HttpServletRequest req){if(body.q()==null||body.r()==null)throw AccountService.bad("请填写投放坐标");world.deploy(accounts.require(req,true,true).id(),body.species(),body.q(),body.r());return Map.of("message","已投放");}
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
    @PostMapping("/register") public Map<String,String> register(@RequestBody Credentials c,HttpServletRequest req){moderation.requireGame(null,ModerationService.ip(req.getRemoteAddr()));world.register(c.username,c.password);return Map.of("message","注册成功，请登录查看审批状态");}
    @PostMapping("/login") public AccountService.Account login(@RequestBody Credentials c,HttpServletRequest req,HttpServletResponse res){return world.login(c.username,c.password,req,res);}
    @GetMapping("/me") public AccountService.Account me(HttpServletRequest req){return accounts.require(req,false,false);}
    @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest req,HttpServletResponse res){
        var a=accounts.require(req,false,false);accounts.logout(req,res);world.closeUser(a.id());return Map.of("message","已退出");
    }
    @GetMapping("/world") public WorldMap map(HttpServletRequest req){accounts.require(req,true,false);return world.map();}
    @GetMapping("/players") public Map<String,Object> players(HttpServletRequest req){return world.snapshot(accounts.require(req,true,false).id());}
    @PostMapping("/move") public Map<String,Object> move(@RequestBody Move m,HttpServletRequest req){
        var a=accounts.require(req,true,false);
        if(m.q==null||m.r==null||Math.abs((long)m.q)>1000||Math.abs((long)m.r)>1000)throw AccountService.bad("坐标无效");
        return Map.of("path",world.move(a.id(),m.q,m.r,m.version));
    }
    @PostMapping("/emote") public WorldService.Emote emote(@RequestBody EmoteRequest body,HttpServletRequest req){return world.emote(accounts.require(req,true,false).id(),body.code,body.version);}
    @GetMapping("/inventory") public List<Map<String,Object>> inventory(HttpServletRequest req){return world.inventory(accounts.require(req,true,false).id());}
    @GetMapping("/admin/accounts/{id}/inventory") public List<Map<String,Object>> inventoryOf(@PathVariable UUID id,HttpServletRequest req){accounts.require(req,true,true);return world.inventory(id);}
    @PostMapping("/collect") public Map<String,String> collect(@RequestBody Move m,HttpServletRequest req){
        var a=accounts.require(req,true,false);
        if(m.q==null||m.r==null)throw AccountService.bad("坐标无效");
        return world.collect(a.id(),m.q,m.r,m.version);
    }
    @PostMapping("/stop") public Map<String,String> stop(HttpServletRequest req){world.stop(accounts.require(req,true,false).id());return Map.of("message","已停下");}
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
        world.battleEquip(accounts.require(req,true,false).id(),body.mainHand(),body.offHand());return Map.of("message","装备已更新");
    }
    record FerryRequest(String version,String item,Integer quantity) {}
    @PostMapping("/ferry/contribute") public Map<String,String> contribute(@RequestBody FerryRequest body,HttpServletRequest req){
        if(body.quantity()==null)throw AccountService.bad("请输入投入数量");
        return world.contribute(accounts.require(req,true,false).id(),body.version(),body.item(),body.quantity());
    }
    @PostMapping("/ferry/board") public Map<String,String> board(@RequestBody FerryRequest body,HttpServletRequest req){world.ferryBoard(accounts.require(req,true,false).id(),body.version());return Map.of("message","已登船，靠岸后可手动下船");}
    @PostMapping("/ferry/disembark") public Map<String,String> disembark(@RequestBody FerryRequest body,HttpServletRequest req){world.ferryDisembark(accounts.require(req,true,false).id(),body.version());return Map.of("message","已下船");}
    @GetMapping("/admin/accounts") public List<AccountService.Account> list(HttpServletRequest req){accounts.require(req,true,true);return accounts.list();}
    @PostMapping("/admin/accounts/{id}/approval") public Map<String,String> approval(@PathVariable UUID id,@RequestBody Approval body,HttpServletRequest req){
        world.approve(accounts.require(req,true,true).id(),id,body.approved);return Map.of("message",body.approved?"已批准进入":"已撤销权限");
    }
    @PostMapping("/admin/accounts/{id}/password") public Map<String,String> password(@PathVariable UUID id,@RequestBody Password body,HttpServletRequest req){
        world.resetPassword(accounts.require(req,true,true).id(),id,body.password);return Map.of("message","密码已重置，原登录已失效");
    }
    @PostMapping("/admin/regenerate") public WorldMap regenerate(@RequestBody Regenerate body,HttpServletRequest req){
        var a=accounts.require(req,true,true);
        if(!"重新生成世界".equals(body.confirmation))throw AccountService.bad("请输入：重新生成世界");
        return world.regenerate(a.id());
    }
    @ExceptionHandler(ResponseStatusException.class) public ResponseEntity<?> expected(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.requireNonNullElse(e.getReason(),"请求未完成")));}
    @ExceptionHandler(org.springframework.dao.EmptyResultDataAccessException.class) public ResponseEntity<?> missing(){return ResponseEntity.status(404).body(Map.of("message","账号不存在"));}
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    public ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","请求格式无效"));}
}
