package game.world;
import java.util.*;
/** Decision ranking is independent from execution: NPCs must use normal battle commands. */
public final class MonsterBrain {
    public record Option(String action,WorldMap.Hex cell,double score){}
    public static Option choose(List<Option> options){return options.stream().filter(o->o.score()>0).max(Comparator.comparingDouble(Option::score)).orElse(null);}
    private MonsterBrain(){}
}
