import {Graphics,type Container} from 'pixi.js';
import {cellKey,hexShape,sceneLabel,scenePoint} from './SceneCanvas';
import {localActor,localMarker,localRoute,localTerrain,type LocalCell} from './localSceneArt';
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
export function drawBattleOverlay(layer:Container,{battle,marks,route,own,selected,visual,loot,me,moving,clock}:{
  battle:Battle;marks:Mark[];route?:Hex[]|null;own?:Hex;selected:Hex|null;visual:Record<string,Hex>;
  loot:LootContainer[];me:string;moving:boolean;clock:number;
}){
  const g=new Graphics();layer.addChild(g);
  for(const {cell,color,alpha,width=2} of marks)hexShape(g,cell,32).fill({color,alpha}).stroke({color,alpha:Math.min(1,alpha+.44),width});
  if(route&&own)localRoute(g,own,route);
  if(selected)hexShape(g,selected,33).stroke({color:0x465943,width:4}).stroke({color:0xffefb9,width:2});
  for(const item of loot){
    if(item.battleQ==null||item.battleR==null)continue;
    const cell={q:item.battleQ,r:item.battleR},{x,y}=scenePoint(cell);
    localMarker(g,cell,item.kind);
    sceneLabel(layer,item.kind==='corpse'?'尸体':item.kind==='chest'?'宝箱':'遗留物',x,y+21,10);
  }
  const actors=[...battle.actors].sort((a,b)=>scenePoint(visual[a.accountId]??a).y-scenePoint(visual[b.accountId]??b).y);
  for(const actor of actors){
    const h=visual[actor.accountId]??actor,{x,y}=scenePoint(h),c=actor.character,down=c.life==='down';
    if(moving&&actor.accountId===me)g.ellipse(x,y+13,21,9).stroke({color:0xfff1aa,width:3});
    localActor(g,h,actor.color,c.kind==='monster',down);
    const hp=down?c.downHp:c.hp,max=down?c.maxDownHp:c.maxHp;
    g.roundRect(x-21,y-37,42,6,2).fill(0x4b5042);
    if(max>0&&hp>0)g.roundRect(x-21,y-37,42*Math.min(1,hp/max),6,2).fill(down?0xbb7755:0x75a85e);
    sceneLabel(layer,`${actor.username}${actor.accountId===me?' · 你':''}`,x,y-57,12);
    sceneLabel(layer,`${down?'倒地 ':''}${hp}/${max}`,x,y-45,10);
    if(actor.withdrawDirection!=null||!actor.online)sceneLabel(layer,actor.withdrawDirection!=null?'正在撤离…':'离线',x,y+27,10, '#765442');
  }
  for(const e of battle.events){
    const age=clock-e.happenedAt;
    if(e.amount==null||e.q==null||e.r==null||age<0||age>1700)continue;
    const {x,y}=scenePoint({q:e.q,r:e.r}),heal=e.kind==='potion'||e.kind==='bandage';
    sceneLabel(layer,e.amount===0?'格挡':`${heal?'+':'−'}${e.amount}`,x,y-74-age/90,15,heal?'#40724a':'#a54236');
    if(age<340&&!heal&&e.amount>0)g.moveTo(x-21,y+18).lineTo(x+22,y-23).moveTo(x-15,y-20).lineTo(x+18,y+18).stroke({color:0xffeabc,alpha:1-age/340,width:5});
  }
}
export const matches=(a:Hex|null,b:Hex)=>a!=null&&cellKey(a)===cellKey(b);
