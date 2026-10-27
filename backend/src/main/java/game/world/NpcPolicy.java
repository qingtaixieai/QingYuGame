package game.world;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Pure selection: legality is never a score. Execution must revalidate. */
public final class NpcPolicy {
 public record Candidate(String key,double score,boolean legal) {}
 public static Candidate choose(List<Candidate> options,String current,double margin){
  var legal=options.stream().filter(Candidate::legal).filter(c->Double.isFinite(c.score())).toList();
  var best=legal.stream().max(Comparator.comparingDouble(Candidate::score)).orElse(null);if(best==null)return null;
  var keep=legal.stream().filter(c->c.key().equals(current)).findFirst().orElse(null);
  if(keep!=null&&best.score()<=keep.score()+margin)return keep;
  var near=legal.stream().filter(c->c.score()>=best.score()-Math.abs(best.score())*.05).toList();
  return near.get(ThreadLocalRandom.current().nextInt(near.size()));
 }
 public static long retryDelay(int failures){return Math.min(10000,500L<<Math.min(5,Math.max(0,failures)));}
 public static int dispatchCount(int available,int minimum,int batch){return Math.max(0,Math.min(batch,available-minimum));}
 private NpcPolicy(){}
}
