package game.world;

import java.util.*;

/** Domain rules deliberately have no dependency on Spring or rendering. Axial hex coordinates. */
public record WorldMap(String version, long seed, String name, int radius, Hex spawn, List<Tile> tiles) {
    public record Hex(int q, int r) {
        public List<Hex> neighbors() {
            return List.of(new Hex(q+1,r),new Hex(q-1,r),new Hex(q,r+1),new Hex(q,r-1),new Hex(q+1,r-1),new Hex(q-1,r+1));
        }
    }
    public record Place(String name, String type, String description) {}
    public record Tile(int q, int r, String terrain, boolean road, Place place) {
        public Hex hex() { return new Hex(q,r); }
        public boolean walkable() { return !Set.of("ocean","mountain","river").contains(terrain); }
    }
    public Map<Hex, Tile> index() {
        Map<Hex,Tile> out = new LinkedHashMap<>();
        tiles.forEach(t -> out.put(t.hex(),t));
        return out;
    }
    public List<Hex> path(Hex from, Hex to) { return path(index(),from,to,false); }
    static List<Hex> path(Map<Hex,Tile> tiles, Hex from, Hex to, boolean buildRoad) {
        if (!tiles.containsKey(to) || (!buildRoad && !tiles.get(to).walkable())) return List.of();
        Map<Hex,Hex> previous = new HashMap<>();
        Map<Hex,Integer> cost = new HashMap<>();
        record Step(Hex h, int cost) {}
        PriorityQueue<Step> todo = new PriorityQueue<>(Comparator.comparingInt(Step::cost)
            .thenComparingInt(s -> s.h.q).thenComparingInt(s -> s.h.r));
        todo.add(new Step(from,0)); cost.put(from,0);
        while (!todo.isEmpty()) {
            Step s = todo.poll();
            if (s.cost != cost.get(s.h)) continue;
            if (s.h.equals(to)) break;
            for (Hex n : s.h.neighbors()) {
                Tile t=tiles.get(n);
                if (t==null || t.terrain.equals("ocean") || (!buildRoad && !t.walkable())) continue;
                int next=s.cost+(buildRoad ? (t.terrain.equals("mountain") ? 15 : t.terrain.equals("river") ? 5 : 1) : 1);
                if (next < cost.getOrDefault(n,Integer.MAX_VALUE)) {
                    cost.put(n,next); previous.put(n,s.h); todo.add(new Step(n,next));
                }
            }
        }
        if (!cost.containsKey(to)) return List.of();
        LinkedList<Hex> result=new LinkedList<>();
        for (Hex h=to; !h.equals(from); h=previous.get(h)) result.addFirst(h);
        return result;
    }
    public static WorldMap generate(long seed) {
        int radius=19;
        SplittableRandom random=new SplittableRandom(seed);
        double phase=random.nextDouble()*6.28;
        Map<Hex,Tile> tiles=new LinkedHashMap<>();
        for(int r=-radius;r<=radius;r++) for(int q=-radius;q<=radius;q++) {
            if (Math.abs(q+r)>radius) continue;
            double x=q+r*.5, y=r*.866;
            double angle=Math.atan2(y,x);
            double edge=14.4+1.0*Math.sin(angle*3+phase)+.7*Math.cos(angle*5-phase);
            double dist=Math.sqrt(x*x+y*y*1.18);
            String type="plain";
            if(dist>edge) type="ocean";
            else if(dist>edge-1) type="beach";
            else {
                double ridge=Math.abs(q+4+Math.sin(r*.35+phase)*2);
                if (ridge<1.4 && r < 1 && r > -11) type="mountain";
                else if(ridge<3 && r<2) type="hill";
                else if(Math.sin(q*.38+phase)+Math.cos(r*.43-phase)>.55) type="forest";
                int riverQ=(int)Math.round(Math.sin(r*.27)*2);
                if(q==riverQ || q==riverQ+1) type="river";
            }
            tiles.put(new Hex(q,r),new Tile(q,r,type,false,null));
        }
        // Remove disconnected coastal islands so this remains a single landmass.
        Hex spawn=new Hex(-3,3);
        Set<Hex> connected=new HashSet<>(); ArrayDeque<Hex> queue=new ArrayDeque<>();
        connected.add(spawn);queue.add(spawn);
        while(!queue.isEmpty()) for(Hex n:queue.remove().neighbors()) {
            Tile t=tiles.get(n);
            if(t!=null && !t.terrain.equals("ocean") && connected.add(n)) queue.add(n);
        }
        tiles.replaceAll((h,t)->connected.contains(h)?t:new Tile(h.q,h.r,"ocean",false,null));
        record Landmark(Hex h,Place p) {}
        List<Landmark> places=List.of(
            new Landmark(spawn,new Place("青禾镇","town","大陆腹地的旅人聚落。暖色屋顶围着小广场，远方的道路从这里展开。这里是所有旅人的起点。")),
            new Landmark(new Hex(7,-5),new Place("白石城","town","建在丘陵边缘的石城，北方的山风带来松木与雨水的气息。")),
            new Landmark(new Hex(-9,7),new Place("临湾镇","town","面向西南海湾的小镇。沿着道路，可以走回大陆中央的平原。")),
            new Landmark(new Hex(5,6),new Place("麦穗村","village","金色田垄环绕着村落，窄窄的土路从屋舍之间穿过。")),
            new Landmark(new Hex(-7,-3),new Place("松溪村","village","藏在山林边的小村庄。木屋与松树一起，守着通往山口的路。")),
            new Landmark(new Hex(3,-10),new Place("北风村","village","大陆北方的一处落脚点，石墙替旅人挡住山间的冷风。")),
            new Landmark(new Hex(-8,1),new Place("旧日石环","ruin","残缺石柱围成的古老圆环。苔藓覆盖了曾经刻在这里的文字。")),
            new Landmark(new Hex(9,0),new Place("林间遗迹","ruin","树影中的断墙仍勾勒着旧日庭院。这里暂时只有风声。")),
            new Landmark(new Hex(0,11),new Place("南境残塔","ruin","临近海岸的残塔，为穿越南方荒野的旅人留下醒目的路标。")));
        for(Landmark l:places) {
            Hex h=l.h; tiles.put(h,new Tile(h.q,h.r,"plain",true,l.p));
        }
        for(Landmark l:places) for(Hex h:path(tiles,spawn,l.h,true)) {
            Tile t=tiles.get(h);
            String terrain=t.terrain.equals("river")?"bridge":t.terrain.equals("mountain")?"hill":t.terrain;
            tiles.put(h,new Tile(h.q,h.r,terrain,true,t.place));
        }
        return new WorldMap(UUID.randomUUID().toString(),seed,"青屿大陆",radius,spawn,List.copyOf(tiles.values()));
    }
}
