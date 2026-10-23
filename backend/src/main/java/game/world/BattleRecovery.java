package game.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Startup-only reconnect grace; never restores AP or executes turn effects. */
final class BattleRecovery {
    private record Turn(UUID actor,int round,long deadline) {}
    private final Map<UUID,Turn> turns=new HashMap<>();
    void remember(UUID battle,UUID actor,int round,long deadline){turns.put(battle,new Turn(actor,round,deadline));}
    boolean waitForReconnect(UUID battle,UUID actor,int round,long now){
        Turn turn=turns.get(battle);
        return turn!=null&&turn.actor().equals(actor)&&turn.round()==round&&now<turn.deadline();
    }
    void forget(UUID battle){turns.remove(battle);}
}
