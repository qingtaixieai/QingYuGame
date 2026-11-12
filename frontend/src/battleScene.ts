import {Container,Graphics} from 'pixi.js';
import {battleCaptionLabel,cellKey,hexShape,sceneActor,sceneLabel,scenePoint} from './SceneCanvas';
import {localActor,localMarker,localRoute,localTerrain,type LocalCell} from './localSceneArt';
import {drawWorldActor} from './worldArt';
import type {BattleState,Hex,LootContainer} from './types';

type Battle=Extract<BattleState,{active:true}>;
type Mark={cell:Hex;color:number;alpha:number;width?:number};
const arrows=['→','↘','↙','←','↖','↗'];
const size=(h:Hex)=>Math.max(Math.abs(h.q),Math.abs(h.r),Math.abs(h.q+h.r));
export const battleCells=(battle:Battle):LocalCell[]=>{
  if(battle.cells)return battle.cells;
  const cells:LocalCell[]=[];
  for(let q=-battle.radius;q<=battle.radius;q++)for(let r=-battle.radius;r<=battle.radius;r++)
    if(size({q,r})<=battle.radius)cells.push({q,r,terrain:'grass',walkable:true});
  return cells;
};
export function drawBattleGround(layer:Container,battle:Battle,cells:LocalCell[],exitSides:(h:Hex)=>number[]){
  localTerrain(layer,cells,h=>exitSides(h).length>0,battle.blockedEdges);
  const decor=new Graphics();layer.addChild(decor);
  for(const h of cells){
    const sides=exitSides(h),{x,y}=scenePoint(h);
    if(sides.length)sceneLabel(layer,sides.map(side=>battle.edges.find(e=>e.direction===side)?.walkable?(arrows[side]??'门'):'×').join(' '),x,y,14);
    else if(!battle.sceneId&&((Math.imul(h.q+713+battle.worldQ,374761393)^Math.imul(h.r+997+battle.worldR,668265263))>>>0)%9===0)localMarker(decor,h,'wood');
  }
  for(const edge of battle.edges){
    if(!edge.cell&&battle.sceneLevel===3)continue;
    const a=edge.direction*Math.PI/3,extent=battle.radius*54+25;
    const {x,y}=edge.cell?scenePoint(edge.cell):{x:Math.cos(a)*extent,y:Math.sin(a)*extent};
    if(edge.cell){localMarker(decor,edge.cell,'door');continue;}
    decor.roundRect(x-48,y-15,96,34,6).fill({color:edge.walkable?0xeff0d9:0xaeb8a7,alpha:.9});
    sceneLabel(layer,`${edge.name} · ${edge.walkable?'可撤离':'不可通行'}`,x,y-6,10);
    sceneLabel(layer,edge.destination,x,y+9,9);
  }
}
export function drawSharedWithdrawal(layer:Container,battle:Battle,cells:LocalCell[],exitSides:(h:Hex)=>number[]){
  const outlines=layer.addChild(new Graphics());
  for(const edge of battle.edges){
    const door=edge.cell;
    const exits=door?cells.filter(cell=>matches(door,cell))
      :cells.filter(cell=>cell.walkable&&exitSides(cell).includes(edge.direction));
    if(!exits.length)continue;
    const color=edge.walkable?0xf3d48d:0xb78c80;
    for(const cell of exits)hexShape(outlines,cell,31).stroke({color,width:door?3:2,alpha:edge.walkable?.92:.6});
    const midpoint=exits.reduce((closest,cell)=>distanceSquaredFromCenter(cell)<distanceSquaredFromCenter(closest)?cell:closest);
    const {x,y}=scenePoint(midpoint);
    const exitName=door?'门':`${arrows[edge.direction]??''} ${edge.name}`;
    const status=edge.walkable?'可撤离':'不可通行';
    sceneLabel(layer,`${exitName} · ${status}`,x,y+(door?21:0),14,edge.walkable?'#544420':'#78453c');
  }
}
function distanceSquaredFromCenter(cell:Hex){
  const {x,y}=scenePoint(cell);
  return x*x+y*y;
}
export function drawBattleHighlights(layer:Container,{intents,marks,route,own,selected}:{
  intents:Battle['intents'];marks:Mark[];route?:Hex[]|null;own?:Hex;selected:Hex|null;
}){
  const g=new Graphics();layer.addChild(g);
  for(const {cell,color,alpha,width=2} of marks)hexShape(g,cell,32).fill({color,alpha}).stroke({color,alpha:Math.min(1,alpha+.44),width});
  if(route&&own)localRoute(g,own,route);
  if(selected){
    const onIntent=intents.some(intent=>intent.cells.some(cell=>matches(cell,selected)));
    hexShape(g,selected,onIntent?26:33).stroke({color:0x465943,width:4}).stroke({color:0xffefb9,width:2});
  }
}
export function drawBattleOverlay(layer:Container,{actors,relations,visual,loot,me,moving,shared}:{
  actors:Battle['actors'];relations:Battle['relations'];visual:Record<string,Hex>;
  loot:LootContainer[];me:string;moving:boolean;shared:boolean;
}){
  const g=new Graphics();layer.addChild(g);
  for(const item of loot){
    if(item.battleQ==null||item.battleR==null)continue;
    const cell={q:item.battleQ,r:item.battleR},{x,y}=scenePoint(cell);
    if(!shared)localMarker(g,cell,item.kind);
    sceneLabel(layer,item.kind==='corpse'?'尸体':item.kind==='chest'?'宝箱':'遗留物',x,y+21,10);
  }
  const ordered=[...actors].sort((a,b)=>scenePoint(visual[a.accountId]??a).y-scenePoint(visual[b.accountId]??b).y);
  for(const actor of ordered){
    const h=visual[actor.accountId]??actor,c=actor.character,down=c.life==='down';
    const actorLayer=shared?sceneActor(layer,actor.accountId,h):layer;
    const art=shared?actorLayer.addChild(new Graphics()):g;
    const {x,y}=shared?{x:0,y:0}:scenePoint(h);
    if(moving&&actor.accountId===me)art.ellipse(x,y+13,21,9).stroke({color:0xfff1aa,width:3});
    if(shared){
      const sprite=actorLayer.addChild(new Graphics());
      drawWorldActor(sprite,{color:actor.color,online:actor.online,kind:c.kind,species:c.species},actor.accountId===me);
      sprite.scale.set(1.25);
      if(down)sprite.rotation=Math.PI/2;
    }else localActor(g,h,actor.color,c.kind==='monster',down);
    const hp=down?c.downHp:c.hp,max=down?c.maxDownHp:c.maxHp;
    art.roundRect(x-21,y-37,42,6,2).fill(0x4b5042);
    const relation=actor.accountId===me?'ally':relations?.[actor.accountId]??'neutral';
    const healthColor={hostile:0xe45b4e,neutral:0xe9c64a,ally:0x68ae64}[relation];
    if(max>0&&hp>0)art.roundRect(x-21,y-37,42*Math.min(1,hp/max),6,2).fill(healthColor);
    const caption=actorLayer.addChild(new Container({label:battleCaptionLabel}));
    caption.position.set(x,y-41);
    sceneLabel(caption,`${actor.username}${actor.accountId===me?' · 你':''}`,0,-16,12);
    sceneLabel(caption,`${down?'倒地 ':''}${hp}/${max}`,0,-4,10);
    if(actor.withdrawDirection!=null||!actor.online)sceneLabel(actorLayer,actor.withdrawDirection!=null?'正在撤离…':'离线',x,y+27,10, '#765442');
  }
}
export function drawBattleEffects(layer:Container,events:Battle['events'],clock:number){
  const g=new Graphics();layer.addChild(g);
  for(const e of events){
    const age=clock-e.happenedAt;
    if(e.amount==null||e.q==null||e.r==null||age<0||age>1700)continue;
    const {x,y}=scenePoint({q:e.q,r:e.r}),heal=e.kind==='potion'||e.kind==='bandage';
    sceneLabel(layer,e.amount===0?'格挡':`${heal?'+':'−'}${e.amount}`,x,y-74-age/90,15,heal?'#40724a':'#a54236');
    if(age<340&&!heal&&e.amount>0)g.moveTo(x-21,y+18).lineTo(x+22,y-23).moveTo(x-15,y-20).lineTo(x+18,y+18).stroke({color:0xffeabc,alpha:1-age/340,width:5});
  }
}
export const matches=(a:Hex|null,b:Hex)=>a!=null&&cellKey(a)===cellKey(b);
