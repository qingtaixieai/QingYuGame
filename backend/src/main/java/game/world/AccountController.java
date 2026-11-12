package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adapter only. Domain actions remain serialized by WorldService. */
@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;
    private final WorldService world;
    private final ModerationService moderation;
    AccountController(AccountService accounts, WorldService world, ModerationService moderation) {
        this.accounts=accounts;
        this.world=world;
        this.moderation=moderation;
    }

    record Credentials(String username,String password) {}

    @GetMapping("/health") public Map<String,String> health(){return Map.of("status","ok");}

    @PostMapping("/register") public Map<String,String> register(@RequestBody Credentials c,HttpServletRequest req){moderation.requireGame(null,ModerationService.ip(req.getRemoteAddr()));world.register(c.username,c.password);return Map.of("message","注册成功，请登录查看审批状态");}

    @PostMapping("/login") public AccountService.Account login(@RequestBody Credentials c,HttpServletRequest req,HttpServletResponse res){return world.login(c.username,c.password,req,res);}

    @GetMapping("/me") public AccountService.Account me(HttpServletRequest req){return accounts.require(req,false,false);}

    @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest req,HttpServletResponse res){
        var a=accounts.require(req,false,false);accounts.logout(req,res);world.closeUser(a.id());return Map.of("message","已退出");
    }
}
