import {Assets,Graphics,Rectangle,Sprite,Texture,type Container} from 'pixi.js';
import {scenePoint} from './SceneCanvas';
import {drawTerrainProp,pixel,tree} from './worldArt';
import {HEX_SCENE,paintGround} from './mapPaint';
import {sceneKey} from './sceneGeometry';
import {innPixels} from './districtSprites';
import type {LocalCell} from './localSceneArt';
import type {BlockedEdge,Hex} from './types';
import whiteStoneAtlasUrl from './assets/white-stone-atlas.png';

const whiteStoneFrames={
  inn:[20,66,375,323], shop:[403,113,305,270], workshop:[726,100,337,282], smith:[1084,64,330,326],
  tree:[58,416,275,290], pine:[403,401,288,305], well:[744,429,296,283], stall:[1112,405,277,303],
  fence:[54,817,265,160], flowers:[419,790,256,196], lantern:[807,753,168,257], shrub:[1100,800,277,185],
} as const;
type WhiteStoneSprite=keyof typeof whiteStoneFrames;
type WhiteStoneAtlas=Record<WhiteStoneSprite,Texture>;
let whiteStoneAtlas:Promise<WhiteStoneAtlas>|undefined;

export function loadWhiteStoneAtlas(){
  whiteStoneAtlas??=Assets.load<Texture>(whiteStoneAtlasUrl).then(atlas=>{
    atlas.source.scaleMode='nearest';
    return Object.fromEntries(Object.entries(whiteStoneFrames).map(([key,[x,y,width,height]])=>[
      key,new Texture({source:atlas.source,frame:new Rectangle(x,y,width,height)}),
    ])) as WhiteStoneAtlas;
  }).catch(error=>{whiteStoneAtlas=undefined;throw error;});
  return whiteStoneAtlas;
}

function whiteStoneSprite(layer:Container,atlas:WhiteStoneAtlas,kind:WhiteStoneSprite,cell:Hex,width:number,dx=0,dy=0){
  const texture=atlas[kind],sprite=new Sprite(texture),point=scenePoint(cell);
  sprite.anchor.set(.5,1);
  sprite.position.set(Math.round(point.x+dx),Math.round(point.y+dy));
  sprite.width=width;
  sprite.height=width*texture.height/texture.width;
  layer.addChild(sprite);
  return sprite;
}

export function drawWhiteStoneTown(layer:Container,atlas:WhiteStoneAtlas,cells:LocalCell[],doors:readonly {cell:Hex;name:string}[],edges:readonly BlockedEdge[]){
  const doorKinds:Record<string,WhiteStoneSprite>={旅店:'inn',商店:'shop',工坊:'workshop',铁匠铺:'smith'};
  const index=new Map(cells.map(cell=>[sceneKey(cell),cell]));
  for(const door of doors){
    const name=door.name.split(' · ').at(-1)??door.name;
    whiteStoneSprite(layer,atlas,doorKinds[name]??'workshop',door.cell,name==='旅店'?158:134,0,-15);
    whiteStoneSprite(layer,atlas,'flowers',door.cell,26,29,-7);
  }
  whiteStoneSprite(layer,atlas,'well',{q:0,r:0},58,0,22);
  for(const [q,r] of [[-3,5],[3,2],[-3,-1],[4,-5],[-6,7],[4,4],[6,-5],[-3,-7],[7,2],[-4,9],[3,8],[-3,12],[4,10],[-4,14]]){
    const cell=index.get(sceneKey({q,r}));
    if(!cell?.walkable||!['grass','forest'].includes(cell.terrain))continue;
    whiteStoneSprite(layer,atlas,q<0?'tree':'pine',cell,58,0,8);
  }
  whiteStoneSprite(layer,atlas,'stall',{q:-3,r:3},52,0,18);
  for(const {from,to} of edges){
    const a=scenePoint(from),b=scenePoint(to);
    const fence=whiteStoneSprite(layer,atlas,'fence',from,36,(b.x-a.x)/2,(b.y-a.y)/2);
    fence.anchor.set(.5);
    fence.rotation=Math.atan2(b.y-a.y,b.x-a.x)+Math.PI/2;
  }
  for(const [q,r] of [[-1,6],[1,7],[-3,-1],[3,-2]]){
    const cell=index.get(sceneKey({q,r}));
    if(cell?.walkable)whiteStoneSprite(layer,atlas,'lantern',cell,14,25,12);
  }
  for(const [q,r] of [[-2,8],[2,7],[-5,6],[6,-2]]){
    const cell=index.get(sceneKey({q,r}));
    if(cell?.walkable&&cell.terrain==='grass')whiteStoneSprite(layer,atlas,'shrub',cell,32,0,11);
  }
}

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

const roofs:Record<string,{width:number;deep:number;shadow:number;tile:number;light:number}>={
  旅店:{width:120,deep:108,shadow:0x875245,tile:0xb46954,light:0xce8368},
  商店:{width:100,deep:83,shadow:0x52626a,tile:0x6d8187,light:0x8d9ca0},
  工坊:{width:96,deep:80,shadow:0x85634f,tile:0xad835e,light:0xcaa079},
  铁匠铺:{width:106,deep:86,shadow:0x49575e,tile:0x65747a,light:0x839299},
};

export function drawDistrictBuilding(layer:Container,door:Hex,name:string){
  const {x,y}=scenePoint(door),kind=name.split(' · ').at(-1)??name;
  const roof=roofs[kind]??roofs.工坊;
  const g=new Graphics();
  const left=Math.round(x-roof.width/2),right=left+roof.width,bottom=Math.round(y-24),top=bottom-roof.deep;
  const ridge=Math.floor((top+bottom)/2),hip=24;
  pixel(g,left+5,top+7,roof.width+7,roof.deep+5,0x9a8e66);
  pixel(g,left-3,top-3,roof.width+6,roof.deep+6,0x574e47);
  pixel(g,left,top,roof.width,roof.deep,roof.shadow);
  for(let row=top;row<bottom;row+=6){
    const slope=row<ridge?roof.tile:roof.light;
    const inset=Math.floor(hip*(1-Math.min(1,Math.abs(row-ridge)/(roof.deep/2))));
    const start=left+inset,end=right-inset;
    pixel(g,start,row,end-start,Math.min(5,bottom-row),slope);
    for(let joint=start+(Math.floor((row-top)/6)%2===0?9:4);joint<end;joint+=12){
      pixel(g,joint,row,1,Math.min(4,bottom-row),roof.shadow);
    }
    pixel(g,start,row,2,Math.min(5,bottom-row),roof.light);
    pixel(g,end-2,row,2,Math.min(5,bottom-row),roof.light);
  }
  pixel(g,left+hip,ridge-2,roof.width-hip*2,4,roof.shadow);
  pixel(g,left+hip,ridge-2,roof.width-hip*2,1,roof.light);
  pixel(g,right-26,top+15,14,15,0xb6a887);
  pixel(g,right-25,top+15,12,11,0xe9d8ad);
  pixel(g,right-22,top+17,6,5,0x695c4e);
  pixel(g,x-12,bottom+4,24,5,0xb0a080);
  pixel(g,x-10,bottom+9,20,3,0xdbcca6);
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

function drawStonePaving(g:Graphics,x:number,y:number,seed:number){
  for(let row=-24;row<=24;row+=12){
    const extent=Math.floor(32-(Math.abs(row)+6)/Math.sqrt(3));
    for(let left=-extent+((row+24)/12%2)*7;left<extent-5;left+=15){
      const wide=Math.min(13,extent-left),tone=(seed+left+row)&3;
      shade(g,x+left,y+row,wide,9,tone===0?0xaaa186:0xece0bd,.38);
      shade(g,x+left+1,y+row+8,wide-1,1,0x9f967d,.41);
    }
  }
}

function drawTownCanopy(g:Graphics,x:number,y:number,seed:number){
  const palette=seed%2===0?[0x64844d,0x87a05c,0xa6b973]:[0x537957,0x739664,0x99b575];
  shade(g,x-19,y-10,40,25,0x516346,.26);
  for(const [dx,dy,w,h] of [[-20,-12,38,26],[-13,-19,25,37],[-23,-5,43,14]])pixel(g,x+dx,y+dy,w,h,palette[0]);
  for(const [dx,dy,w,h] of [[-18,-12,24,20],[-12,-17,21,26],[-6,-9,24,19]])pixel(g,x+dx,y+dy,w,h,palette[1]);
  pixel(g,x-10,y-13,13,8,palette[2]);
  pixel(g,x-17,y-7,8,7,palette[2]);
  pixel(g,x+5,y-6,9,6,palette[2]);
}

function drawTown(g:Graphics,cells:LocalCell[]){
  for(const cell of cells){
    const {x,y}=scenePoint(cell),n=seed(cell);
    if(cell.terrain==='street'||cell.terrain==='plaza'){
      const stone=cell.terrain==='plaza'?0xaaa186:0x9f977b;
      if(cell.terrain==='plaza')drawStonePaving(g,x,y,n);
      else if(n%3!==1){
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
      if(n%10===1){
        drawTownCanopy(g,x,y,n);
      }else if(n%7===0){
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
