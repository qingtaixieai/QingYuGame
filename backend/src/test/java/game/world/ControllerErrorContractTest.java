package game.world;

import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ControllerErrorContractTest {
    @Test void splitWorldControllerKeepsOriginalMissingAccountMessage() throws Exception {
        var accounts=mock(AccountService.class);var world=mock(WorldService.class);
        when(world.map()).thenThrow(new EmptyResultDataAccessException(1));
        MockMvcBuilders.standaloneSetup(new WorldController(accounts,world))
            .setControllerAdvice(new GameApiErrors(),new ApiErrors()).build()
            .perform(get("/api/world")).andExpect(status().isNotFound())
            .andExpect(content().json("{\"message\":\"账号不存在\"}"));
    }
    @Test void chatControllerKeepsItsExistingMissingRecordMessage() throws Exception {
        var accounts=mock(AccountService.class);var moderation=mock(ModerationService.class);
        when(moderation.accounts()).thenThrow(new EmptyResultDataAccessException(1));
        MockMvcBuilders.standaloneSetup(new ChatController(accounts,mock(ChatService.class),mock(WorldService.class),moderation))
            .setControllerAdvice(new GameApiErrors(),new ApiErrors()).build()
            .perform(get("/api/admin/moderation/accounts")).andExpect(status().isNotFound())
            .andExpect(content().json("{\"message\":\"记录不存在\"}"));
    }
}
