package game.world;

import java.util.*;
import static game.world.WorldMap.Hex;

/** Pure attack geometry shared by players and NPCs. One declaration is one attack. */
public final class WeaponRules {
    public record Weapon(String code,String name,String shape,int minRange,int maxRange,int cost,int damage) {}
    public static final Weapon UNARMED=new Weapon("unarmed","空手","single",1,1,2,2);
    public static final Weapon CLAWS=new Weapon("claws","利爪","single",1,1,2,3);
    public static final List<Hex> DIRECTIONS=List.of(new Hex(1,0),new Hex(0,1),new Hex(-1,1),new Hex(-1,0),new Hex(0,-1),new Hex(1,-1));
    public static int distance(Hex a,Hex b){return Math.max(Math.max(Math.abs(a.q()-b.q()),Math.abs(a.r()-b.r())),Math.abs(a.q()+a.r()-b.q()-b.r()));}
    public static boolean inRange(Weapon w,Hex from,Hex to){int d=distance(from,to);return d>=w.minRange&&d<=w.maxRange;}
    public static List<Hex> cells(Weapon w,Hex from,Hex target){
        if(!inRange(w,from,target))return List.of();
        if(w.shape.equals("single"))return List.of(target);
        int direction=-1;
        for(int i=0;i<6;i++){Hex d=DIRECTIONS.get(i);for(int n=1;n<=w.maxRange;n++)if(target.equals(new Hex(from.q()+d.q()*n,from.r()+d.r()*n)))direction=i;}
        if(direction<0)return List.of();
        Hex d=DIRECTIONS.get(direction);
        if(w.shape.equals("line")){List<Hex> out=new ArrayList<>();for(int n=w.minRange;n<=w.maxRange;n++)out.add(new Hex(from.q()+d.q()*n,from.r()+d.r()*n));return out;}
        if(w.shape.equals("fan")){Hex next=DIRECTIONS.get((direction+1)%6);return List.of(new Hex(from.q()+d.q(),from.r()+d.r()),new Hex(from.q()+next.q(),from.r()+next.r()));}
        return List.of();
    }
    private WeaponRules(){}
}
