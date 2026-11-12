package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adapter only. Domain actions remain serialized by WorldService. */
@RestController
@RequestMapping("/api")
public class AdminController {
    private final AccountService accounts;
    private final WorldService world;
    AdminController(AccountService accounts, WorldService world) {
        this.accounts=accounts;
        this.world=world;
    }

    record Approval(boolean approved) {}

    record Password(String password) {}

    record Regenerate(String confirmation) {}

    record GrantRequest(String code,int quantity) {}

    record DeployCreature(String species,Integer q,Integer r) {}

    record SkillRequest(String code,boolean granted) {}

    record BehaviorRequest(String behavior) {}

    @GetMapping("/admin/characters/{id}/behavior") public Object behavior(@PathVariable UUID id,HttpServletRequest req){accounts.require(req,true,true);return world.behavior(id);}

    @PostMapping("/admin/characters/{id}/behavior") public Object behavior(@PathVariable UUID id,@RequestBody BehaviorRequest body,HttpServletRequest req){world.behavior(accounts.require(req,true,true).id(),id,body.behavior());return Map.of("message","倾向已更新");}

    @GetMapping("/admin/characters") public Object characters(HttpServletRequest req){accounts.require(req,true,true);return world.adminCharacters();}

    @GetMapping("/admin/characters/{id}/skills") public Object skills(@PathVariable UUID id,HttpServletRequest req){accounts.require(req,true,true);return world.learnedSkills(id);}

    @PostMapping("/admin/characters/{id}/skills") public Object grantSkill(@PathVariable UUID id,@RequestBody SkillRequest body,HttpServletRequest req){world.grantSkill(accounts.require(req,true,true).id(),id,body.code(),body.granted());return Map.of("message","技能已更新");}

    @PostMapping("/admin/accounts/{id}/items") public Object grant(@PathVariable UUID id,@RequestBody GrantRequest body,HttpServletRequest req){world.grant(accounts.require(req,true,true).id(),id,body.code(),body.quantity());return Map.of("message","物品已发放");}

    @PostMapping("/admin/creatures/deploy") public Map<String,String> deployCreature(@RequestBody DeployCreature body,HttpServletRequest req){if(body.q()==null||body.r()==null)throw AccountService.bad("请填写投放坐标");world.deploy(accounts.require(req,true,true).id(),body.species(),body.q(),body.r());return Map.of("message","已投放");}

    @GetMapping("/admin/accounts/{id}/inventory") public List<Map<String,Object>> inventoryOf(@PathVariable UUID id,HttpServletRequest req){accounts.require(req,true,true);return world.inventory(id);}

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
}
