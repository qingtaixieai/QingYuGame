package game.world;

import java.util.*;
import static game.world.WorldMap.Hex;

/** Pure attack geometry shared by players and NPCs. One declaration is one attack. */
public final class WeaponRules {
    public record Weapon(String code,String name,String shape,int minRange,int maxRange,int cost,int damage,String moveRule) {
        public Weapon(String code,String name,String shape,int minRange,int maxRange,int cost,int damage){this(code,name,shape,minRange,maxRange,cost,damage,"range");}
    }
    public static boolean retainsAttack(String policy,int min,int max,List<Hex> marked,Hex origin,Hex next){
        if(policy.equals("translated_shape")&&origin!=null){
            int dq=next.q()-origin.q(),dr=next.r()-origin.r();
            return marked.stream().anyMatch(h->marked.contains(new Hex(h.q()+dq,h.r()+dr)));
        }
        return marked.stream().anyMatch(h->{int d=distance(next,h);return d>=min&&d<=max;});
    }
    public static final Weapon UNARMED=new Weapon("unarmed","空手","single",1,1,2,2);
    public static final Weapon CLAWS=new Weapon("claws","利爪","single",1,1,2,3);
    public static final List<Hex> DIRECTIONS=List.of(new Hex(1,0),new Hex(0,1),new Hex(-1,1),new Hex(-1,0),new Hex(0,-1),new Hex(1,-1));
    public static int distance(Hex a,Hex b){return Math.max(Math.max(Math.abs(a.q()-b.q()),Math.abs(a.r()-b.r())),Math.abs(a.q()+a.r()-b.q()-b.r()));}
    public static boolean inRange(Weapon w,Hex from,Hex to){int d=distance(from,to);return d>=w.minRange&&d<=w.maxRange;}
    /** Only the aim centre must be in weapon range; peripheral cells are clipped. */
    public static List<Hex> aimCells(Weapon w,Hex from,Hex center,int radius){
        if(!inRange(w,from,center)||distance(new Hex(0,0),center)>radius)return List.of();
        var cells=new ArrayList<Hex>();cells.add(center);
        for(var d:DIRECTIONS){var h=new Hex(center.q()+d.q(),center.r()+d.r());if(distance(new Hex(0,0),h)<=radius)cells.add(h);}
        return List.copyOf(cells);
    }
    /** A multi-cell attack is legal only when its entire shape fits the battlefield. */
    public static List<Hex> battlefieldCells(Weapon w,Hex from,Hex target,int radius){
        var result=cells(w,from,target);
        return result.stream().anyMatch(h->distance(new Hex(0,0),h)>radius)?List.of():result;
    }
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
