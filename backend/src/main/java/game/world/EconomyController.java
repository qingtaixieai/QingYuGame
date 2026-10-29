package game.world;

import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** HTTP adapter only. Domain actions remain serialized by WorldService. */
@RestController
@RequestMapping("/api")
public class EconomyController {
    private final AccountService accounts;
    private final WorldService world;
    EconomyController(AccountService accounts, WorldService world) {
        this.accounts=accounts;
        this.world=world;
    }

    record HandoverCancel(UUID offerId) {}

    @GetMapping("/handover") public Object handovers(HttpServletRequest req){return world.handovers(accounts.require(req,true,false).id());}

    @PostMapping("/handover/offer") public Object offer(@RequestBody HandoverService.Offer b,HttpServletRequest req){return world.offer(accounts.require(req,true,false).id(),b);}

    @PostMapping("/handover/accept") public Object accept(@RequestBody HandoverService.Accept b,HttpServletRequest req){return world.acceptHandover(accounts.require(req,true,false).id(),b);}

    @PostMapping("/handover/cancel") public Object cancel(@RequestBody HandoverCancel b,HttpServletRequest req){world.cancelHandover(accounts.require(req,true,false).id(),b.offerId());return Map.of("message","已取消交接");}

    @GetMapping("/law/stations") public Object stations(HttpServletRequest req){return world.lawStations(accounts.require(req,true,false).id());}

    @GetMapping("/logistics") public Object logistics(HttpServletRequest req){var actor=accounts.require(req,true,false);return world.logisticsView(actor.id(),actor.admin());}

    @PostMapping("/logistics/stop/{target}") public Object logisticsStop(@PathVariable UUID target,HttpServletRequest req){return world.requestNpcStop(accounts.require(req,true,false).id(),target);}

    @GetMapping("/law/{id}/board") public Object board(@PathVariable UUID id,HttpServletRequest req){return world.lawBoard(accounts.require(req,true,false).id(),id);}

    @GetMapping("/law/{id}/corpses") public Object corpses(@PathVariable UUID id,HttpServletRequest req){return world.lawEligible(accounts.require(req,true,false).id(),id);}

    @PostMapping("/law/claim") public Object claim(@RequestBody LawService.Claim b,HttpServletRequest req){return world.claimBounty(accounts.require(req,true,false).id(),b);}

    @GetMapping("/production") public Object production(HttpServletRequest req){return world.production(accounts.require(req,true,false).id());}

    @PostMapping("/production/start") public Object productionStart(@RequestBody ProductionService.Start body,HttpServletRequest req){world.startProduction(accounts.require(req,true,false).id(),body);return Map.of("message","任务已开始");}

    @PostMapping("/production/cancel") public Object productionCancel(HttpServletRequest req){world.cancelProduction(accounts.require(req,true,false).id());return Map.of("message","任务已取消，未用材料已释放");}

    @GetMapping("/shop") public Object shop(HttpServletRequest req){return world.shop(accounts.require(req,true,false).id());}

    @PostMapping("/shop/trade") public Object trade(@RequestBody ShopService.Order body,HttpServletRequest req){return world.trade(accounts.require(req,true,false).id(),body);}
}
