package game.world;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CoinPaymentTest {
 @Test void copperFirst(){assertEquals(new CoinPayment.Change(7,2,1),CoinPayment.pay(10,2,1,3));}
 @Test void silverBreakReturnsActualCopper(){assertEquals(new CoinPayment.Change(97,0,0),CoinPayment.pay(0,1,0,3));}
 @Test void goldUsedOnlyAfterSmallerCoins(){assertEquals(new CoinPayment.Change(9912,0,0),CoinPayment.pay(12,1,1,200));}
 @Test void insufficientIsRejected(){assertThrows(RuntimeException.class,()->CoinPayment.pay(3,0,0,4));}
 @Test void neverCreatesValue(){for(int cost=0;cost<1100;cost++){var c=CoinPayment.pay(17,12,2,cost);assertEquals(21217-cost,c.copper()+c.silver()*100+c.gold()*10000);}}
}
