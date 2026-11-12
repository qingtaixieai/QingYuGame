package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adapter only. Domain actions remain serialized by WorldService. */
@RestController
@RequestMapping("/api")
public class WorldController {
    private final AccountService accounts;
    private final WorldService world;
    WorldController(AccountService accounts, WorldService world) {
        this.accounts=accounts;
        this.world=world;
    }

    record EmoteRequest(String code,String version) {}

    public record DoorRequest(UUID doorId,String version) {}

    record Move(Integer q,Integer r,String version,WorldMap.Hex origin) {}

    record SceneCommand(String version,Integer q,Integer r,Integer direction,Boolean adjacent) {}

    record FerryRequest(String version,String item,Integer quantity) {}

    @PostMapping("/scene/door") public Object door(@RequestBody DoorRequest body,HttpServletRequest req){world.useDoor(accounts.require(req,true,false).id(),body.doorId(),body.version());return Map.of("message","已通过门");}

    @GetMapping("/scene") public Object scene(HttpServletRequest req){return world.scene(accounts.require(req,true,false).id());}

    @PostMapping("/scene/enter") public Object enterScene(@RequestBody SceneCommand b,HttpServletRequest req){world.enterScene(accounts.require(req,true,false).id(),b.version());return Map.of("message","已进入二级地图");}

    @PostMapping("/scene/leave") public Object leaveScene(@RequestBody SceneCommand b,HttpServletRequest req){world.leaveScene(accounts.require(req,true,false).id(),b.version());return Map.of("message","已返回一级地图");}

    @PostMapping("/scene/cross") public Object crossScene(@RequestBody SceneCommand b,HttpServletRequest req){if(b.direction()==null)throw AccountService.bad("请选择出口");world.crossScene(accounts.require(req,true,false).id(),b.direction(),b.version());return Map.of("message","已跨越边界");}

    @GetMapping("/scene/route") public Object sceneRoute(@RequestParam(required=false) Integer q,@RequestParam(required=false) Integer r,@RequestParam(required=false) String version,@RequestParam(defaultValue="false") boolean adjacent,HttpServletRequest req){
        var actor=accounts.require(req,true,false).id();
        if(q==null||r==null||version==null)throw AccountService.bad("请选择格子");
        return Map.of("path",world.previewScene(actor,q,r,version,adjacent));
    }

    @PostMapping("/scene/move") public Object moveScene(@RequestBody SceneCommand b,HttpServletRequest req){if(b.q()==null||b.r()==null)throw AccountService.bad("请选择格子");return Map.of("path",world.moveScene(accounts.require(req,true,false).id(),b.q(),b.r(),b.version(),Boolean.TRUE.equals(b.adjacent())));}

    @GetMapping("/world") public WorldMap map(HttpServletRequest req){accounts.require(req,true,false);return world.map();}

    @GetMapping("/players") public Map<String,Object> players(HttpServletRequest req){return world.snapshot(accounts.require(req,true,false).id());}

    @GetMapping("/world/route") public WorldService.RoutePreview route(
            @RequestParam(required=false) Integer q,@RequestParam(required=false) Integer r,
            @RequestParam(required=false) String version,HttpServletRequest req,HttpServletResponse response){
        var actor=accounts.require(req,true,false).id();
        requireWorldCoordinates(q,r);
        response.setHeader("Cache-Control","no-store");
        return world.previewRoute(actor,q,r,version);
    }

    @PostMapping("/move") public Map<String,Object> move(@RequestBody Move m,HttpServletRequest req){
        var a=accounts.require(req,true,false);
        requireWorldCoordinates(m.q,m.r);
        return Map.of("path",world.move(a.id(),m.q,m.r,m.version,m.origin));
    }

    private void requireWorldCoordinates(Integer q,Integer r){
        if(q==null||r==null||Math.abs((long)q)>1000||Math.abs((long)r)>1000)throw AccountService.bad("坐标无效");
    }

    @PostMapping("/emote") public WorldService.Emote emote(@RequestBody EmoteRequest body,HttpServletRequest req){return world.emote(accounts.require(req,true,false).id(),body.code,body.version);}

    @PostMapping("/stop") public Map<String,String> stop(HttpServletRequest req){world.stop(accounts.require(req,true,false).id());return Map.of("message","已停下");}

    @PostMapping("/ferry/contribute") public Map<String,String> contribute(@RequestBody FerryRequest body,HttpServletRequest req){
        if(body.quantity()==null)throw AccountService.bad("请输入投入数量");
        return world.contribute(accounts.require(req,true,false).id(),body.version(),body.item(),body.quantity());
    }

    @PostMapping("/ferry/board") public Map<String,String> board(@RequestBody FerryRequest body,HttpServletRequest req){world.ferryBoard(accounts.require(req,true,false).id(),body.version());return Map.of("message","已登船，靠岸后可手动下船");}

    @PostMapping("/ferry/disembark") public Map<String,String> disembark(@RequestBody FerryRequest body,HttpServletRequest req){world.ferryDisembark(accounts.require(req,true,false).id(),body.version());return Map.of("message","已下船");}
}
