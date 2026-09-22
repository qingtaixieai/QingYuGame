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
    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}
    @PostMapping("/register") public Map<String,String> register(@RequestBody Credentials c,HttpServletRequest req){moderation.requireGame(null,ModerationService.ip(req.getRemoteAddr()));world.register(c.username,c.password);return Map.of("message","注册成功，请登录查看审批状态");}
    @PostMapping("/login") public AccountService.Account login(@RequestBody Credentials c,HttpServletRequest req,HttpServletResponse res){return world.login(c.username,c.password,req,res);}
    @GetMapping("/me") public AccountService.Account me(HttpServletRequest req){return accounts.require(req,false,false);}
    @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest req,HttpServletResponse res){
        var a=accounts.require(req,false,false);accounts.logout(req,res);world.closeUser(a.id());return Map.of("message","已退出");
    }
    @GetMapping("/world") public WorldMap map(HttpServletRequest req){accounts.require(req,true,false);return world.map();}
    @GetMapping("/players") public Map<String,Object> players(HttpServletRequest req){accounts.require(req,true,false);return world.snapshot();}
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
