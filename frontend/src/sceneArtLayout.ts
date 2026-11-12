import {edgeKey,sceneKey,scenePath,sceneXY} from './sceneGeometry';
import type {LocalCell} from './localSceneArt';
import type {BlockedEdge,Hex} from './types';

const distance=(a:Hex,b:Hex)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
const origin={q:0,r:0};
const north={q:0,r:-1};
const offset=(a:Hex,b:Hex)=>({q:a.q+b.q,r:a.r+b.r});
const empty=new Set<string>();
const nearest=(cells:LocalCell[],target:Hex)=>cells.reduce<LocalCell|undefined>(
  (best,cell)=>!best||distance(cell,target)<distance(best,target)?cell:best,undefined
);

function innLayout(cells:LocalCell[],door:Hex,edges:BlockedEdge[]):LocalCell[]{
  const walkable=cells.filter(cell=>cell.walkable);
  const keeper=nearest(walkable,origin);
  if(!keeper)return cells;
  const path=new Set([door,...scenePath(cells,door,keeper,empty,edges)].map(sceneKey));
  const doorY=sceneXY(door).y;
  const counter=cells.filter(cell=>cell.terrain==='wall'&&!cell.walkable&&cell.r===-4&&cell.q>=0&&cell.q<=1);
  const counterKeys=new Set(counter.map(sceneKey));
  return cells.map(cell=>{
    const key=sceneKey(cell);
    if(counterKeys.has(key))return {...cell,terrain:'inn-counter'};
    if(!cell.walkable||!['floor','rug'].includes(cell.terrain))return cell;
    const {x,y}=sceneXY(cell);
    if(path.has(key))return {...cell,terrain:y>doorY-62?'lobby':'reception',art:'runner'};
    if(y>=doorY-76)return {...cell,terrain:'lobby'};
    if(x<-48&&y>=-91&&y<=58)return {...cell,terrain:'lounge',art:'lounge-rug'};
    return {...cell,terrain:y<26&&x>-30&&x<110?'reception':'floor'};
  });
}

function townLayout(cells:LocalCell[],doors:{cell:Hex}[],edges:BlockedEdge[],entry?:Hex):LocalCell[]{
  const walkable=cells.filter(cell=>cell.walkable);
  const open=new Set(walkable.map(sceneKey));
  const sealed=new Set(edges.map(edge=>edgeKey(edge.from,edge.to)));
  const ordered=doors.map(door=>door.cell).filter(door=>open.has(sceneKey(door))).sort((a,b)=>a.q-b.q||a.r-b.r);
  if(!ordered.length)return cells;
  const terrain=new Map<string,string>();
  const art=new Map<string,LocalCell['art']>();
  const mark=(cell:Hex,surface:string)=>{terrain.set(sceneKey(cell),surface);};
  const connect=(from:Hex,to:Hex,surface:string)=>{
    const path=scenePath(cells,from,to,empty,edges);
    if(!path.length&&sceneKey(from)!==sceneKey(to))return;
    for(const cell of [from,...path])mark(cell,surface);
  };
  const fronts=ordered.map(door=>{
    const south=[{q:0,r:1},{q:-1,r:1},{q:1,r:0}].map(delta=>offset(door,delta))
      .filter(cell=>open.has(sceneKey(cell))&&!sealed.has(edgeKey(door,cell)));
    for(const cell of [door,...south])mark(cell,'street');
    art.set(sceneKey(door),'doorstep');
    return south[0]??door;
  });
  for(let i=1;i<fronts.length;i++)connect(fronts[i-1],fronts[i],'street');

  const middle=fronts[Math.floor((fronts.length-1)/2)];
  const plaza=nearest(walkable.filter(cell=>!terrain.has(sceneKey(cell))),offset(middle,{q:0,r:3}))??middle;
  const radius=cells.reduce((max,cell)=>Math.max(max,distance(cell,origin)),0);
  const landing=entry&&distance(entry,origin)===radius&&open.has(sceneKey(entry))
    ?entry:nearest(walkable.filter(cell=>distance(cell,origin)===radius),{q:0,r:radius});
  if(landing){
    const junction=nearest(walkable,offset(plaza,{q:0,r:3}));
    if(junction){
      connect(landing,junction,'street');
      connect(junction,plaza,'street');
    }
  }
  connect(plaza,middle,'street');

  const square=walkable.filter(cell=>distance(cell,plaza)<=2&&sceneXY(cell).y>sceneXY(middle).y+29);
  for(const cell of square)mark(cell,'plaza');
  mark(plaza,'plaza');
  art.set(sceneKey(plaza),'plaza-mark');
  const painted=new Set(terrain.keys());
  return cells.map(cell=>{
    if(!cell.walkable)return cell.terrain==='wall'?{...cell,terrain:'building',art:'town-plot'}:cell;
    const key=sceneKey(cell);
    const surface=terrain.get(key)??(cell.terrain==='paving'?'grass':cell.terrain);
    const naturalEdge=distance(cell,origin)>=radius-2&&['grass','plain','dirt','forest'].includes(surface);
    const verge=[north,{q:0,r:1},{q:1,r:0},{q:1,r:-1},{q:-1,r:0},{q:-1,r:1}].some(delta=>painted.has(sceneKey(offset(cell,delta))));
    const feature=art.get(key)??(naturalEdge&&!verge?'town-edge':undefined);
    return surface===cell.terrain&&!feature?cell:{...cell,terrain:surface,art:feature};
  });
}

/** Presentation only. Persisted walkability, blocked edges and door coordinates stay authoritative. */
export function sceneArtCells(cells:LocalCell[],level:number|undefined,doors:{cell:Hex}[],name:string|undefined,edges:BlockedEdge[]=[],entry?:Hex):LocalCell[]{
  if(level===3&&name?.endsWith('旅店')&&doors[0])return innLayout(cells,doors[0].cell,edges);
  if(level===2&&doors.length&&cells.length)return townLayout(cells,doors,edges,entry);
  return cells;
}
