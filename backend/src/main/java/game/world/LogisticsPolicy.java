package game.world;
/** Small pure decisions; ownership, stock, movement and payment are validated by shared services. */
public final class LogisticsPolicy {
 private LogisticsPolicy(){}
 public static long waitElapsed(long previous,long last,long now,boolean waiting){return previous+(waiting?Math.max(0,now-last):0);}
 public static int allocate(long available,int remaining){return (int)Math.max(0,Math.min(available,remaining));}
 public static boolean canDispatch(boolean sourceAllows,boolean destinationAllows,int available,int minimum){return canDispatch(sourceAllows,destinationAllows,available,minimum,true);}
 public static boolean canDispatch(boolean sourceAllows,boolean destinationAllows,int available,int minimum,boolean external){return !external||sourceAllows&&destinationAllows&&available>minimum;}
}
