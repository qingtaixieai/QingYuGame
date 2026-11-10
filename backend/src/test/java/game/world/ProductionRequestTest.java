package game.world;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
class ProductionRequestTest {
 @Test void gatherDoesNotRequireCraftBatchCount(){var request=JsonMapper.builder().build().readValue("{\"kind\":\"gather\",\"objectId\":\"00000000-0000-0000-0000-000000000001\"}",ProductionService.Start.class);assertEquals("gather",request.kind());}
 @Test void skinDoesNotRequireCraftBatchCount(){var request=JsonMapper.builder().build().readValue("{\"kind\":\"skin\",\"corpseId\":\"00000000-0000-0000-0000-000000000001\"}",ProductionService.Start.class);assertEquals("skin",request.kind());}
}
