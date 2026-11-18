import {Graphics,type Container} from 'pixi.js';
import {moduleSeams} from './sceneModules';
import {cellKey,hexShape,scenePoint} from './SceneCanvas';
import {drawWorldGround} from './worldArt';
import {HEX_SCENE,paintGround} from './mapPaint';
import type {Hex} from './types';

export type LocalCell=Hex & {
  terrain:string;
  walkable:boolean;
  art?:'plaza-mark'|'doorstep'|'town-edge'|'town-plot'|'runner'|'lounge-rug';
};
const sceneryTerrains=new Set(['grass','plain','forest','dirt','rock','sand','floor','lobby','lounge']);

export function drawSceneScenery(layer:Container,cells:LocalCell[],sceneId:string,radius:number,reserved:ReadonlySet<string>,town:boolean,indoor:boolean){
  const seed=Array.from(sceneId).reduce((value,char)=>Math.imul(value^char.charCodeAt(0),16777619),2166136261);
  const decor=layer.addChild(new Graphics());
  for(const cell of cells){
    const distance=Math.max(Math.abs(cell.q),Math.abs(cell.r),Math.abs(cell.q+cell.r));
    if(distance>=radius-1||!cell.walkable||cell.art||reserved.has(cellKey(cell))||!sceneryTerrains.has(cell.terrain))continue;
    const patch=hash({q:Math.floor(cell.q/3),r:Math.floor(cell.r/3)})^seed;
    const variant=(hash(cell)^seed)>>>0;
    if((patch>>>0)%4!==0||variant%3!==0)continue;
    const p=scenePoint(cell),x=p.x+(variant%15)-7,y=p.y+(variant%11)-5;
    if(indoor){
      block(decor,x-10,y-2,20,5,0xbc9877);
      block(decor,x-7,y-2,3,5,0xd6b895);block(decor,x+4,y-2,3,5,0xd6b895);
    }else if(town&&variant%7===0){
      block(decor,x-1,y-13,3,17,0x776147);
      block(decor,x-8,y-14,16,7,0x59756b);block(decor,x-4,y-12,8,2,0xe2ce9e);
    }else if(town||cell.terrain==='grass'||cell.terrain==='plain'){
      if(town){block(decor,x-9,y+1,18,6,0x8b7650);block(decor,x-8,y-1,16,4,0x697f49);}
      for(const [dx,dy] of [[-5,0],[0,-3],[6,1]]){
        block(decor,x+dx,y+dy,2,5,0x769553);block(decor,x+dx-1,y+dy-2,4,3,variant%2?0xe6bdb0:0xf1d688);
      }
    }else if(cell.terrain==='forest'){
      block(decor,x-10,y+3,20,4,0x806648);block(decor,x-8,y+1,15,3,0xa88561);
      block(decor,x+5,y-6,2,7,0x567c53);block(decor,x+2,y-5,8,2,0x759b63);
    }else if(cell.terrain==='sand'){
      for(const dx of [-5,0,5]){
        block(decor,x+dx,y-6,2,10,0x82934e);block(decor,x+dx-1,y-8,3,3,0xa28551);
      }
    }else{
      block(decor,x-8,y+1,15,5,0x858d76);block(decor,x-6,y-2,10,4,0xa9ad94);
      block(decor,x+8,y+5,4,3,0x9eaa86);
    }
  }
}
const palette:Record<string,number[]>={"camp-fence":[0x8f9d65],"camp-road":[0xc8ad7c,0xcdb382],"camp-trail":[0xb4af77,0xb9ad77],street:[0xc6b58b,0xc8b78e],plaza:[0xd2c39e,0xd5c5a1],lobby:[0xc5aa80],lounge:[0xb39470],reception:[0xd0b58d],deep:[0x558e9e,0x639cac],shallow:[0x88b8b4,0x9bc6bb],building:[0xa4b586],bed:[0xcbb08a],shelf:[0xcbb08a],rug:[0xcab18b],grass:[0xb6c982,0xb2c680,0xbdcd89],plain:[0xb6c982,0xb2c680,0xbdcd89],forest:[0x9bb777,0x97b478,0xa2bb7b],dirt:[0xcbb383,0xd2b988],paving:[0xc5bf9b,0xd3caab],floor:[0xcbb08a,0xc4a47c],wall:[0x8f745f],furniture:[0x997954],water:[0x78afba,0x80b5bd],sand:[0xd8c597,0xdfcda1],rock:[0x9fa695,0xa9ad98],bridge:[0xc9ad80]};
const hash=({q,r}:Hex)=>((Math.imul(q+713,374761393)^Math.imul(r+997,668265263))>>>0);
const block=(g:Graphics,x:number,y:number,w:number,h:number,color:number|string)=>g.rect(Math.round(x),Math.round(y),w,h).fill(color);
const outdoorTerrain:Record<string,string>={grass:'plain',plain:'plain',forest:'forest',sand:'beach',rock:'mountain',deep:'ocean',shallow:'river',water:'river',bridge:'bridge'};
export function drawLocalBase(g:Graphics,cell:LocalCell,exploration=false,outside=false){
  const shared=exploration?outdoorTerrain[cell.terrain]:undefined;
  const {x,y}=scenePoint(cell),alpha=outside?.43:1;
  if(shared){drawWorldGround(g,shared,x,y,Math.abs(cell.q*137+cell.r*73)%7,36,HEX_SCENE,alpha,0);return;}
  const colors=palette[cell.terrain]??palette.grass;
  const tone=exploration?Math.floor((Math.sin(cell.q*.57+cell.r*.23)+Math.cos(cell.r*.47)+2)*1.2):hash(cell);
  paintGround(g,x,y,{radius:36,orientation:HEX_SCENE,padding:0,color:colors[tone%colors.length],alpha});
}
export function localTerrain(layer:Container,cells:LocalCell[],outer:(h:Hex)=>boolean=()=>false,edges:readonly {from:Hex;to:Hex}[]=[],{exploration=false}={}){
  const g=new Graphics(),decor=new Graphics();
  layer.addChild(g,decor);
  for(const cell of cells){
    const {x,y}=scenePoint(cell),n=hash(cell);
    const outside=outer(cell);
    drawLocalBase(g,cell,exploration,outside);
    if(!exploration)g.stroke({color:0x607456,width:1,alpha:.25});
    // The shared district layer paints these finishes.
    if(exploration&&(cell.art==='town-plot'||['wall','inn-counter','bed','shelf','floor','lobby','lounge','reception','street','plaza'].includes(cell.terrain)))continue;
    if(cell.terrain==='camp-fence'){
      block(decor,x-25,y+7,50,7,0x77845b);
      block(decor,x-25,y-11,49,6,0xa17a4f);
      block(decor,x-22,y+1,48,5,0x795940);
      for(const post of [-20,0,20]){
        block(decor,x+post-3,y-18,7,29,0x634937);
        block(decor,x+post-1,y-18,3,24,0xba9261);
      }
    }else if(cell.terrain==='camp-road'||cell.terrain==='camp-trail'){
      const shade=cell.terrain==='camp-road'?0xa88d67:0x909161;
      block(decor,x-14,y+8,7,2,shade);
      block(decor,x+9,y-9,9,2,shade);
    }else if(cell.terrain==='wall'||cell.terrain==='inn-counter'){
      hexShape(decor,cell,34).fill(0xa89074).stroke({color:0x634f44,width:3});
      block(decor,x-23,y-14,46,5,0xd7c0a0);block(decor,x-27,y+1,54,3,0x756151);block(decor,x-21,y+16,42,4,0x70574a);
      block(decor,x-9,y-10,3,12,0x756151);block(decor,x+9,y+4,3,12,0x756151);
      if(cell.terrain==='inn-counter'){
        block(decor,x-19,y-13,38,23,0x6f4934);block(decor,x-18,y-14,36,5,0xc99b68);
        block(decor,x-14,y-6,10,3,0xe6c287);block(decor,x+6,y-7,8,5,0x94a38d);
      }
    }else if(cell.terrain==='building'){
      hexShape(decor,cell,34).fill(0x83986d).stroke({color:0x71865d,width:1});
    }else if(cell.terrain==='bed'){
      block(decor,x-11,y-22,23,45,0x745846);block(decor,x-8,y-18,17,35,0x7f9a91);
      block(decor,x-8,y-17,17,10,0xeee0bc);block(decor,x-8,y+9,17,3,0xb6bd99);
    }else if(cell.terrain==='shelf'){
      block(decor,x-23,y-17,46,32,0x78563f);block(decor,x-20,y-14,40,24,0x9e7751);
      for(let i=-17;i<=11;i+=7)block(decor,x+i,y-10,5,10,i%2?0xcba964:0x80978c);
      block(decor,x-21,y+2,42,4,0x5d4637);
    }else if(cell.terrain==='rug'){
      block(decor,x-21,y-20,42,40,0x9e6257);block(decor,x-17,y-17,34,34,0xbe8270);
      block(decor,x-8,y-7,16,14,0xd6ad79);
    }else if(cell.terrain==='furniture'){
      block(decor,x-21,y-12,42,24,0x655142);block(decor,x-21,y-16,42,17,0xbc9865);block(decor,x-17,y-13,34,3,0xd4b37e);
    }else if(cell.terrain==='plaza'||cell.terrain==='street'){
      if(n%3===0){block(decor,x-16,y+4,12,3,0xb2a078);block(decor,x+6,y-7,10,2,0xe0cfa8);}
    }else if(exploration&&cell.terrain==='forest'&&n%5<=1){
      block(decor,x-5,y+3,12,6,0x78935e);
      block(decor,x-8,y-1,13,6,0x8ca970);
      block(decor,x-4,y-4,8,4,0x9ab779);
    }else if(!outside&&['forest','grass','plain','rock','dirt'].includes(cell.terrain)&&(!exploration||n%5===0)){
      block(decor,x-19,y+12,7,2,0x7f9e65);block(decor,x-17,y+7,2,6,0x7f9e65);
      block(decor,x+11,y-13,5,2,0xd5d59c);
    }else if(['floor','lobby','lounge','reception'].includes(cell.terrain)){
      block(decor,x-24,y+8,47,1,0xb99a72);block(decor,x-17,y-8,34,1,0xb99a72);
    }
  }
  if(exploration){
    const joints=new Graphics();
    layer.addChildAt(joints,1);
    for(const {from,to,surface} of moduleSeams(cells,edges)){
      const a=scenePoint(from),b=scenePoint(to);
      if(surface==='indoor'&&from.terrain===to.terrain){
        const x=(a.x+b.x)/2,y=(a.y+b.y)/2;
        joints.moveTo(x-9,y).lineTo(x+9,y).stroke({color:0xd8bd92,width:2,alpha:.45});
      }
    }
  }
  for(const edge of edges){
    const a=scenePoint(edge.from),b=scenePoint(edge.to),x=(a.x+b.x)/2,y=(a.y+b.y)/2;
    const d=Math.hypot(b.x-a.x,b.y-a.y),dx=(b.y-a.y)*18/d,dy=(a.x-b.x)*18/d;
    decor.moveTo(x-dx,y-dy).lineTo(x+dx,y+dy).stroke({color:0x594538,width:7});
    decor.moveTo(x-dx,y-dy-2).lineTo(x+dx,y+dy-2).stroke({color:0xceae83,width:3});
  }
}
export function localMarker(g:Graphics,h:Hex,kind:string,offset?:{x:number;y:number}){
  const center=scenePoint(h);
  const x=center.x+(offset?.x??0),y=center.y+(offset?.y??0);
  g.ellipse(x,y+12,16,5).fill({color:0x415940,alpha:.25});
  if(kind==='door'){block(g,x-11,y-14,22,29,0x654c3d);block(g,x-8,y-12,16,25,0xb78b58);block(g,x+3,y+1,3,3,0xf4d282);}
  else if(kind==='chest'){
    block(g,x-15,y-11,30,22,0x48382d);
    block(g,x-13,y-7,26,16,0xad7848);
    block(g,x-13,y-10,26,6,0xc79b63);
    block(g,x-13,y+1,26,3,0x76543a);
    block(g,x-8,y-10,3,19,0x635b4b);
    block(g,x+6,y-10,3,19,0x635b4b);
    block(g,x-3,y-3,7,7,0xe8c36e);
    block(g,x,y,2,3,0x67513d);
  }
  else if(kind==='wood'){block(g,x-3,y-3,6,18,0x7a5b43);block(g,x-13,y-15,26,17,0x547452);block(g,x-8,y-23,17,17,0x78955e);block(g,x-6,y-19,9,5,0xa4b677);}
  else if(kind==='stone'||kind==='iron_ore'){block(g,x-13,y-3,27,14,0x788478);block(g,x-8,y-10,17,12,0xa9b1a0);block(g,x-7,y-8,9,3,0xd9dcc2);}
  else {block(g,x-8,y-3,17,13,kind==='corpse'?0x80543e:0x956c43);block(g,x-4,y-7,9,5,0xd9c89d);block(g,x-8,y+4,17,2,0x5b483c);}
}
export function localActor(g:Graphics,h:Hex,color:string,monster=false,down=false){
  const {x,y}=scenePoint(h);
  g.ellipse(x,y+13,17,5).fill({color:0x364636,alpha:.27});
  if(down){block(g,x-16,y+2,26,9,color);block(g,x+7,y-1,11,12,0xeac69b);return;}
  block(g,x-10,y+7,8,10,0x3e403a);block(g,x+2,y+7,8,10,0x3e403a);
  block(g,x-12,y-10,24,19,color);block(g,x-11,y+3,22,4,0x654a3e);
  block(g,x-9,y-23,18,16,monster?0xc4bd82:0xeac69b);block(g,x-10,y-26,20,6,monster?0x665843:0x563f34);
  block(g,x-4,y-18,2,3,0x403c34);block(g,x+4,y-18,2,3,0x403c34);
  block(g,x+12,y-8,3,15,0xe4e2cd);block(g,x+10,y+4,8,3,0x9e8256);
}
export function localRoute(g:Graphics,from:Hex,path:Hex[]){
  if(!path.length)return;
  for(const [width,color] of [[8,0x66572c],[4,0xf0cd65]]){
    const start=scenePoint(from);g.moveTo(start.x,start.y);
    for(const h of path){const {x,y}=scenePoint(h);g.lineTo(x,y);}
    g.stroke({color,width,cap:'round',join:'round'});
  }
  const {x,y}=scenePoint(path[path.length-1]);g.circle(x,y,5).fill(0xffe28b);
}
export {cellKey};
