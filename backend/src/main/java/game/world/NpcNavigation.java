package game.world;
import java.util.*;
import org.springframework.stereotype.Service;

/** Plans through actual doors and walkable cells. Does not move an actor. */
@Service
public class NpcNavigation {
 public record Leg(String action,UUID door,Integer direction,WorldMap.Hex cell,int cost) {}
 private record Node(UUID scene,WorldMap.Hex cell,int cost,Leg first) {}
 private final SceneRuntime runtime;private final TownService towns;private final SceneService scenes;
 NpcNavigation(SceneRuntime runtime,TownService towns,SceneService scenes){this.runtime=runtime;this.towns=towns;this.scenes=scenes;}
 public Leg route(UUID actor,UUID destination,WorldMap.Hex goal,int range,WorldMap world,boolean ignorePeople){
  var start=runtime.position(actor);if(start==null)return null;
  var queue=new PriorityQueue<Node>(Comparator.comparingInt(Node::cost));queue.add(new Node(start.sceneId(),start.hex(),0,null));
  var best=new HashMap<String,Integer>();int visited=0;Leg result=null;int total=Integer.MAX_VALUE;
  while(!queue.isEmpty()&&visited++<128){
   var n=queue.remove();if(n.cost()>=total)break;String key=n.scene()+":"+n.cell();if(best.getOrDefault(key,Integer.MAX_VALUE)<=n.cost())continue;best.put(key,n.cost());
   var map=runtime.map(n.scene());var blocked=ignorePeople?Set.<WorldMap.Hex>of():runtime.occupied(n.scene(),actor);
   if(n.scene().equals(destination)){
    var choices=map.cells().stream().filter(SceneMap.Cell::walkable).map(SceneMap.Cell::hex).filter(h->WorldMap.distance(h,goal)<=range&&!blocked.contains(h)).toList();
    List<WorldMap.Hex> shortest=null;WorldMap.Hex end=null;
    for(var h:choices){var p=map.topology().path(n.cell(),h,blocked);if(!n.cell().equals(h)&&p.isEmpty())continue;if(shortest==null||p.size()<shortest.size()){shortest=p;end=h;}}
    if(shortest!=null&&n.cost()+shortest.size()<total){var first=n.first();if(first==null)first=new Leg(shortest.isEmpty()?"arrived":"move",null,null,end,0);total=n.cost()+shortest.size();result=new Leg(first.action(),first.door(),first.direction(),first.cell(),total);}
   }
   for(var door:towns.doors(n.scene()))if(door.open()){
    var path=map.topology().path(n.cell(),door.cell(),blocked);if(!n.cell().equals(door.cell())&&path.isEmpty())continue;
    var dest=runtime.map(door.destinationScene());var landing=TownService.landing(dest,door.destination(),ignorePeople?Set.of():runtime.occupied(dest.id(),actor));if(landing==null)continue;
    var first=n.first()!=null?n.first():path.isEmpty()?new Leg("door",door.id(),null,null,0):new Leg("move",null,null,door.cell(),0);
    queue.add(new Node(dest.id(),landing,n.cost()+path.size()+1,first));
   }
   // Outdoor world hops are planned only toward the requested known destination.
   var target=runtime.map(destination);
   if(map.level()==2&&!map.owner().equals(target.owner())){
    var path=WorldMap.path(world.index(),map.owner(),target.owner(),false,scenes::open);if(path.isEmpty())continue;var next=path.getFirst();
    int direction=BattleService.DIRECTIONS.indexOf(new WorldMap.Hex(next.q()-map.owner().q(),next.r()-map.owner().r()));if(direction<0)continue;
    var dest=runtime.outdoor(world,next);WorldMap.Hex exit=null;List<WorldMap.Hex> exitPath=null;
    for(var c:map.cells())if(c.walkable()&&SceneRuntime.sides(c.hex(),map.radius()).contains(direction)&&!blocked.contains(c.hex())){var p=map.topology().path(n.cell(),c.hex(),blocked);if(!n.cell().equals(c.hex())&&p.isEmpty())continue;if(exitPath==null||p.size()<exitPath.size()){exit=c.hex();exitPath=p;}}
    if(exit==null)continue;var landing=SceneRuntime.edgeCell(dest,(direction+3)%6,ignorePeople?Set.of():runtime.occupied(dest.id(),actor));if(landing==null)continue;
    var first=n.first()!=null?n.first():exitPath.isEmpty()?new Leg("cross",null,direction,null,0):new Leg("move",null,null,exit,0);
    queue.add(new Node(dest.id(),landing,n.cost()+exitPath.size()+1,first));
   }
  }return result;
 }
}
