import {Graphics,type Container} from 'pixi.js';
import {scenePoint} from './SceneCanvas';
import {drawTerrainProp,pixel,tree} from './worldArt';
import {HEX_SCENE,paintGround} from './mapPaint';
import {sceneKey} from './sceneGeometry';
import {innPixels,townPixels} from './districtSprites';
import type {LocalCell} from './localSceneArt';
import type {BlockedEdge,Hex} from './types';

function paint(g:Graphics,rows:readonly string[],palette:Readonly<Record<string,string>>,x:number,y:number,scale:number){
  for(let r=0;r<rows.length;r++){
    const row=rows[r];
    for(let q=0;q<row.length;){
      const ink=row[q];let next=q+1;
      while(next<row.length&&row[next]===ink)next++;
      if(palette[ink])pixel(g,x+q*scale,y+r*scale,(next-q)*scale,scale,palette[ink]);
      q=next;
    }
  }
}

const buildings:Record<string,readonly string[]>={旅店:townPixels.inn,商店:townPixels.shop,工坊:townPixels.workshop,铁匠铺:townPixels.smith};

export function drawDistrictBuilding(layer:Container,door:Hex,name:string){
  const {x,y}=scenePoint(door),kind=name.split(' · ').at(-1)??name;
  const rows=buildings[kind]??townPixels.workshop;
  const g=new Graphics();
  pixel(g,x-50,y-22,100,15,0x9e9670);
  pixel(g,x-42,y-12,84,7,0xb1a789);
  paint(g,rows,townPixels.palette,x-48,y-15-rows.length*8,8);
  layer.addChild(g);
}

export function drawDistrictFixture(g:Graphics,kind:'counter'|'bed'|'shelf',cell:Hex){
  const {x,y}=scenePoint(cell);
  paint(g,innPixels[kind],innPixels.palette,x-24,y-16,4);
}

const offsets=[{q:1,r:0},{q:0,r:1},{q:-1,r:1},{q:-1,r:0},{q:0,r:-1},{q:1,r:-1}];
const shade=(g:Graphics,x:number,y:number,width:number,height:number,color:number,alpha:number)=>
  g.rect(Math.round(x),Math.round(y),width,height).fill({color,alpha});
const seed=(cell:Hex)=>(Math.imul(cell.q+83,1597334677)^Math.imul(cell.r+31,3812015801))>>>0;

export function drawInteriorBase(g:Graphics,cell:LocalCell){
  const {x,y}=scenePoint(cell);
  if(cell.terrain==='wall'||cell.terrain==='inn-counter'){
    paintGround(g,x,y,{radius:36,orientation:HEX_SCENE,padding:0,color:0x675449});
    return;
  }
  if(cell.terrain==='bed'||cell.terrain==='shelf'){
    paintGround(g,x,y,{radius:36,orientation:HEX_SCENE,padding:0,color:innPixels.floor[1]});
    return;
  }
  if(!cell.walkable||!['floor','lobby','lounge','reception'].includes(cell.terrain))return;
  const floor=innPixels.floor[cell.terrain==='lounge'?1:cell.terrain==='reception'?2:0];
  paintGround(g,x,y,{radius:36,orientation:HEX_SCENE,padding:0,color:floor});
  // The seams align across tile edges. Sparse end joints keep the wood grain quiet.
  const halfHeight=Math.sqrt(3)*18;
  const first=Math.ceil((y-halfHeight)/17)*17;
  for(let row=first;row<y+halfHeight-1;row+=17){
    const halfWidth=36-Math.abs(row-y)/Math.sqrt(3);
    shade(g,x-halfWidth,row,Math.floor(halfWidth*2),1,0x755439,.16);
    if(seed(cell)%4===0)shade(g,x+(seed(cell)%17)-8,row,1,17,0x755439,.16);
  }
}

function drawPlazaMark(g:Graphics,x:number,y:number){
  const rings=[
    {color:0x9c967a,w:23,h:15},{color:0xe1d7b5,w:21,h:13},
    {color:0xa99d7f,w:16,h:9},{color:0x68938b,w:12,h:6}
  ];
  for(const {color,w,h} of rings){
    pixel(g,x-w+5,y-h,2*w-10,2*h,color);
    pixel(g,x-w,y-h+4,2*w,2*h-8,color);
  }
  pixel(g,x-8,y-3,10,2,0xb1c3a6);
  pixel(g,x-4,y+2,9,2,0x80aaa0);
}

function drawTown(g:Graphics,cells:LocalCell[]){
  for(const cell of cells){
    const {x,y}=scenePoint(cell),n=seed(cell);
    if(cell.terrain==='street'||cell.terrain==='plaza'){
      const stone=cell.terrain==='plaza'?0xaaa186:0x9f977b;
      if(cell.terrain==='plaza'||n%3!==1){
        for(const [dx,dy,w] of [[-19,-10,16],[3,6,18],[-13,17,12]]){
          const shift=(n>>>5)%9;
          shade(g,x+dx+shift,y+dy,w,2,stone,.65);
          shade(g,x+dx+shift,y+dy-2,w,2,0xf0e4bd,.7);
        }
      }
      if(cell.art==='doorstep'){
        pixel(g,x-28,y-19,56,35,0xad9e79);
        pixel(g,x-24,y-15,48,27,0xd1c2a0);
        for(const dy of [-5,4])pixel(g,x-23,y+dy,46,2,0xb2a482);
        pixel(g,x-28,y+16,56,3,0xe0cfa6);
      }else if(cell.art==='plaza-mark')drawPlazaMark(g,x,y);
    }else if(cell.art==='town-plot'){
      paintGround(g,x,y,{radius:36,orientation:HEX_SCENE,padding:0,color:0x9c9879});
      if(n%3===0){
        shade(g,x-19,y-7,17,3,0xb5ac8e,.6);
        shade(g,x+3,y+11,14,2,0x7b795e,.6);
      }
    }else if(cell.art==='town-edge'){
      if(n%7===0){
        pixel(g,x-16,y+2,13,5,0x899869);
        pixel(g,x-13,y-1,9,3,0xa5aa80);
        pixel(g,x+3,y+10,7,3,0x899869);
      }else if(n%3===0){
        pixel(g,x-18,y+8,22,5,0x93ab6f);
        pixel(g,x-11,y+4,9,5,0xa6b879);
      }
    }else if(cell.terrain==='forest'&&n%7===0){
      tree(g,x,y,n%2);
    }else if(cell.terrain==='rock'&&!cell.walkable){
      drawTerrainProp(g,x,y,'mountain',n);
    }
  }
}

function drawWallRim(g:Graphics,cell:LocalCell,index:Map<string,LocalCell>){
  const {x,y}=scenePoint(cell);
  for(const [side,delta] of offsets.entries()){
    const other=index.get(sceneKey({q:cell.q+delta.q,r:cell.r+delta.r}));
    if(!other?.walkable)continue;
    const a=side*Math.PI/3,b=(side+1)*Math.PI/3;
    const front=scenePoint(other).y>y;
    const radius=front?30:32;
    const ax=x+radius*Math.cos(a),ay=y+radius*Math.sin(a);
    const bx=x+radius*Math.cos(b),by=y+radius*Math.sin(b);
    g.moveTo(ax,ay).lineTo(bx,by).stroke({color:front?0xa88969:0x8a6a50,width:front?12:7,cap:'square'});
    g.moveTo(ax,ay-3).lineTo(bx,by-3).stroke({color:front?0xe0c7a0:0xbb9d76,width:3,cap:'square'});
  }
}

function drawInn(g:Graphics,cells:LocalCell[]){
  const index=new Map(cells.map(cell=>[sceneKey(cell),cell]));
  const isInn=cells.some(cell=>cell.art==='runner');
  for(const cell of cells)drawInteriorBase(g,cell);
  if(isInn&&index.get('1,-2')?.walkable){
    // The reception's pool of lamplight crosses the plank seams and stays inside the hall.
    for(const [width,height,alpha] of [[96,119,.07],[77,94,.09],[51,65,.12]]){
      const left=35-width/2,top=-88-height/2;
      shade(g,left+12,top,width-24,height,0xffe4a1,alpha);
      shade(g,left,top+12,12,height-24,0xffe4a1,alpha);
      shade(g,left+width-12,top+12,12,height-24,0xffe4a1,alpha);
    }
  }
  for(const cell of cells){
    const {x,y}=scenePoint(cell);
    if(cell.terrain==='wall'||cell.terrain==='inn-counter')drawWallRim(g,cell,index);
    if(cell.art==='runner'){
      pixel(g,x-15,y-31,30,62,0x87614e);
      pixel(g,x-11,y-31,22,62,0x9a715c);
      pixel(g,x-10,y-31,2,62,0xb5906f);
      pixel(g,x+8,y-31,2,62,0xb5906f);
    }
  }
  const lounge=cells.find(cell=>cell.art==='lounge-rug'&&cell.q===-2&&cell.r===1);
  if(lounge){
    const {x,y}=scenePoint(lounge);
    pixel(g,x-39,y-39,78,78,0x736452);
    pixel(g,x-36,y-36,72,72,0xa79a72);
    pixel(g,x-32,y-32,64,64,0x8d866c);
    for(const dy of [-23,19])pixel(g,x-23,y+dy,46,4,0xb2a27b);
    pixel(g,x-5,y-5,10,10,0xb2a27b);
  }
  for(const cell of cells){
    const {x,y}=scenePoint(cell);
    if(cell.terrain==='inn-counter'&&!cell.walkable){
      paint(g,innPixels.counter,innPixels.palette,x-24,y-11,4);
      shade(g,x-12,y-17,21,11,0xffd581,.23);
      pixel(g,x-4,y-14,6,7,0xffd581);
    }else if(!cell.walkable&&(cell.terrain==='bed'||cell.terrain==='shelf')){
      drawDistrictFixture(g,cell.terrain,cell);
    }else if(isInn&&!cell.walkable&&cell.terrain==='wall'&&cell.q===-4&&cell.r===2){
      paint(g,innPixels.bench,innPixels.palette,x-24,y-13,4);
    }
  }
}

export function drawDistrictGround(layer:Container,cells:LocalCell[],level:number,_edges:readonly BlockedEdge[]){
  if(level!==2&&level!==3)return;
  const g=new Graphics();
  layer.addChild(g);
  if(level===2)drawTown(g,cells);
  else drawInn(g,cells);
}
