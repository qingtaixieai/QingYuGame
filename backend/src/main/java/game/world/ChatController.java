package game.world;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ChatController {
    private final AccountService accounts;private final ChatService chat;private final WorldService world;private final ModerationService moderation;
    ChatController(AccountService accounts,ChatService chat,WorldService world,ModerationService moderation){this.accounts=accounts;this.chat=chat;this.world=world;this.moderation=moderation;}
    record Send(UUID peer,String kind,String body) {} record Read(UUID peer,long upTo) {} record Review(boolean approve) {}
    record Ban(String targetType,String target,String scope,Long minutes,String reason) {}
    @GetMapping("/chat/status") public Object status(HttpServletRequest req){return chat.status(accounts.require(req,true,false).id(),ModerationService.ip(req.getRemoteAddr()));}
    @GetMapping("/chat/messages") public Object history(@RequestParam(required=false) UUID peer,@RequestParam(required=false) Long before,HttpServletRequest req){return chat.history(accounts.require(req,true,false).id(),peer,before);}
    @PostMapping("/chat/messages") public Object send(@RequestBody Send body,HttpServletRequest req){var a=accounts.require(req,true,false);var m=chat.send(a.id(),ModerationService.ip(req.getRemoteAddr()),body.peer,body.kind,body.body);world.notifyChat(body.peer==null?null:Set.of(a.id(),body.peer));return m;}
    @PostMapping("/chat/read") public Object read(@RequestBody Read body,HttpServletRequest req){var a=accounts.require(req,true,false);if(chat.read(a.id(),body.peer,body.upTo))world.notifyChat(Set.of(a.id()));return Map.of("ok",true);}
    @PostMapping(value="/chat/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public Object upload(@RequestPart("file") MultipartFile file,HttpServletRequest req){var a=accounts.require(req,true,false);var result=chat.upload(a.id(),ModerationService.ip(req.getRemoteAddr()),file);world.notifyChat(null);return result;}
    @GetMapping("/chat/images/{id}") public ResponseEntity<byte[]> image(@PathVariable UUID id,HttpServletRequest req){var data=chat.image(accounts.require(req,true,false),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(data.mime())).cacheControl(CacheControl.noStore()).header("Content-Disposition","inline; filename=\"chat-image\"").header("X-Content-Type-Options","nosniff").body(data.bytes());}
    @GetMapping("/admin/chat/images") public Object pending(HttpServletRequest req){accounts.require(req,true,true);return chat.pending();}
    @PostMapping("/admin/chat/images/{id}") public Object review(@PathVariable UUID id,@RequestBody Review body,HttpServletRequest req){chat.review(accounts.require(req,true,true).id(),id,body.approve);world.notifyChat(null);return Map.of("message",body.approve?"已批准换图，旧图片已删除":"已拒绝申请，当前图片不变");}
    @GetMapping("/admin/moderation/accounts") public Object moderationAccounts(HttpServletRequest req){accounts.require(req,true,true);return moderation.accounts();}
    @GetMapping("/admin/bans") public Object bans(HttpServletRequest req){accounts.require(req,true,true);return moderation.list();}
    @PostMapping("/admin/bans") public Object ban(@RequestBody Ban body,HttpServletRequest req){var a=accounts.require(req,true,true);return Map.of("id",world.ban(a.id(),ModerationService.ip(req.getRemoteAddr()),body.targetType,body.target,body.scope,body.minutes,body.reason));}
    @PostMapping("/admin/bans/{id}/revoke") public Object revoke(@PathVariable UUID id,HttpServletRequest req){world.unban(accounts.require(req,true,true).id(),id);return Map.of("message","已解除封禁");}
}
