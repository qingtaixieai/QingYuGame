package game.world;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class BattleRecoveryTest {
    @Test void graceBelongsOnlyToRecoveredTurnAndExpiresAtDeadline() {
        var recovery=new BattleRecovery();
        UUID battle=UUID.randomUUID(), actor=UUID.randomUUID();
        recovery.remember(battle,actor,3,1000);
        assertTrue(recovery.waitForReconnect(battle,actor,3,999));
        assertFalse(recovery.waitForReconnect(battle,actor,3,1000));
        assertFalse(recovery.waitForReconnect(battle,UUID.randomUUID(),3,999));
        assertFalse(recovery.waitForReconnect(battle,actor,4,999));
        recovery.forget(battle);
        assertFalse(recovery.waitForReconnect(battle,actor,3,999));
    }
}
