package game.world;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class LootRulesTest {
    UUID actor=UUID.randomUUID(),battle=UUID.randomUUID();
    LootService.Container container(UUID owner,UUID encounter,Integer q,Integer r){return new LootService.Container(UUID.randomUUID(),"corpse","尸体","monster",UUID.randomUUID(),"v",2,3,encounter,q,r,owner,0,owner==null?1800000L:null,0,0,0,0,0,0,0);}
    @Test void tacticalCoordinatesMustMatchNotJustWorldCell(){var c=new LootService.Context(actor,"v",2,3,battle,0,0);assertTrue(LootService.sameCell(container(null,battle,0,0),c));assertFalse(LootService.sameCell(container(null,battle,1,0),c));assertFalse(LootService.sameCell(container(null,null,null,null),c));}
    @Test void carriedCorpseOnlyAccessibleToCarrierAnywhere(){var x=container(actor,null,null,null);assertTrue(LootService.sameCell(x,new LootService.Context(actor,"other",8,8,battle,1,2)));assertFalse(LootService.sameCell(x,new LootService.Context(UUID.randomUUID(),"v",2,3,null,null,null)));}
    @Test void worldsAndEncountersAreIsolated(){var c=new LootService.Context(actor,"v2",2,3,null,null,null);assertFalse(LootService.sameCell(container(null,null,null,null),c));assertFalse(LootService.sameCell(container(null,battle,0,0),new LootService.Context(actor,"v",2,3,null,null,null)));assertEquals(1800000,LootService.GROUND_LIFETIME);}
}
