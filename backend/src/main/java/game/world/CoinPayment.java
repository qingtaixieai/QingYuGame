package game.world;

/** Small denominations are spent first. No inventory consolidation or automatic upgrades. */
public final class CoinPayment {
    public record Change(long copper,long silver,long gold) {}
    public static Change pay(long copper,long silver,long gold,long cost){
        if(copper<0||silver<0||gold<0||cost<0)throw AccountService.bad("钱币数量无效");
        long need=cost,c=Math.min(copper,need);need-=c;
        long s=Math.min(silver,(need+99)/100);need=Math.max(0,need-s*100);
        long g=Math.min(gold,(need+9999)/10000);need=Math.max(0,need-g*10000);
        if(need>0)throw AccountService.bad("钱币不足");
        long change=c+s*100+g*10000-cost;
        return new Change(copper-c+change,silver-s,gold-g);
    }
    private CoinPayment(){}
}
