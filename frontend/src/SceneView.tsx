import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {Graphics} from 'pixi.js';
import {Footprints,LocateFixed,LogOut,Maximize,Minus,Plus,Square,X} from 'lucide-react';
import {api} from './api';
import {MapTools} from './MapTools';
import {SceneCanvas,sceneActor,cellKey,hexShape,sceneLabel,scenePoint,type DrawScene} from './SceneCanvas';
import {localActor,localBuilding,localMarker,localRoute,localTerrain,type LocalCell} from './localSceneArt';
import {scenePath,sceneSides} from './sceneGeometry';
import type {BlockedEdge,Hex,LootContainer} from './types';
import type {SceneObject} from './ProductionPanel';
import './scene.css';

export type ScenePosition=Hex&{characterId:string;sceneId:string};
export type SceneResident=Hex&{id:string;username:string;life:string;hp:number;maxHp:number;kind:string};
export type SceneDoor={id:string;name:string;cell:Hex;destinationScene:string;destination:Hex;open:boolean};
type SceneMap={level:number;id:string;worldVersion:string;name:string;radius:number;owner:Hex;cells:LocalCell[];blockedEdges:BlockedEdge[]};
type Props={
  position:ScenePosition;doors:SceneDoor[];service:string|null;onShop:()=>void;onRest:()=>void;
  objects:SceneObject[];onFacility:(o:SceneObject)=>void;residents:SceneResident[];loot:LootContainer[];
  onLoot:(id:string)=>void;moving:boolean;connected:boolean;me:string;onMessage:(s:string)=>void;onBattle:(id:string)=>void;
};
const same=(a:Hex,b:Hex)=>a.q===b.q&&a.r===b.r;
const names=['东','东南','西南','西','西北','东北'];
const terrainNames:Record<string,string>={wall:'墙体',building:'建筑',bed:'床铺',shelf:'货架',rug:'地毯',furniture:'家具',floor:'木地板',grass:'草地',forest:'林地',dirt:'土路',paving:'石板路',sand:'沙地',rock:'岩地',deep:'深水',shallow:'浅水'};
const empty:LocalCell[]=[];
export function SceneView(props:Props){
  const {position,doors,service,onShop,onRest,objects,onFacility,residents,loot,onLoot,moving,connected,me,onMessage,onBattle}=props;
  const [map,setMap]=useState<SceneMap|null>(null),[error,setError]=useState('');
  const [selected,setSelected]=useState<Hex|null>(null),[busy,setBusy]=useState(false);
  const [walking,setWalking]=useState<Hex[]>([]),[reload,setReload]=useState(0);
  const [center,setCenter]=useState(()=>scenePoint(position)),[zoom,setZoom]=useState(1);
  const host=useRef<HTMLDivElement>(null),pending=useRef(false);
  const latest=useRef({position,onMessage});latest.current={position,onMessage};
  useEffect(()=>{
    const sceneId=position.sceneId;let cancelled=false;
    setMap(null);setError('');setSelected(null);setWalking([]);
    void api<{active:boolean;map?:SceneMap}>('/scene').then(result=>{
      if(cancelled)return;
      if(result.map?.id!==sceneId){setError('场景状态已变化，请重试。');return;}
      setMap(result.map);setCenter(result.map.level===2?scenePoint(latest.current.position):{x:0,y:0});setZoom(result.map.level===2?1.65:.9);
    }).catch(e=>{if(!cancelled)setError(e.message);});
    return ()=>{cancelled=true;};
  },[position.sceneId,reload]);
  const cells=map?.id===position.sceneId?map.cells:empty;
  const index=useMemo(()=>new Map(cells.map(c=>[cellKey(c),c])),[cells]);
  const occupied=useMemo(()=>new Set(residents.filter(p=>p.id!==me&&['alive','down'].includes(p.life)).map(cellKey)),[residents,me]);
  const preview=useMemo(()=>selected&&map&&connected&&!moving?scenePath(cells,position,selected,occupied,map.blockedEdges):[],
    [selected,map,connected,moving,cells,position.q,position.r,occupied]);
  const route=useMemo(()=>moving?walking.slice(Math.max(0,walking.findIndex(h=>same(h,position))+1)):preview,[moving,walking,position.q,position.r,preview]);
  const ground=useCallback<DrawScene>(layer=>{
    localTerrain(layer,cells,undefined,map?.blockedEdges);
  },[cells,map]);
  const overlay=useCallback<DrawScene>(layer=>{
    const g=new Graphics();layer.addChild(g);
    if(selected&&index.has(cellKey(selected)))hexShape(g,selected,33).fill({color:0xf1dc94,alpha:.2}).stroke({color:0x4c6244,width:4}).stroke({color:0xffebad,width:2});
    localRoute(g,position,route);
    for(const door of doors){
      if(!index.has(cellKey(door.cell)))continue;
      if(map?.level===2)localBuilding(g,door.cell,door.name);
      localMarker(g,door.cell,'door');const {x,y}=scenePoint(door.cell);
      sceneLabel(layer,door.name.split(' · ').at(-1)??door.name,x,y+(map?.level===2?-113:-24));
    }
    for(const object of objects)if(index.has(cellKey(object))&&!object.depleted)localMarker(g,object,object.code);
    for(const item of loot)if(item.battleQ!=null&&item.battleR!=null)localMarker(g,{q:item.battleQ,r:item.battleR},item.kind);
    const visible=residents.filter(p=>p.life!=='dead'&&index.has(cellKey(p))).sort((a,b)=>scenePoint(a).y-scenePoint(b).y);
    for(const p of visible){
      const actor=sceneActor(layer,p.id,p),sprite=new Graphics();actor.addChild(sprite);
      localActor(sprite,{q:0,r:0},p.id===me?'#bd8153':p.kind==='monster'?'#946748':'#628aa0',p.kind==='monster',p.life==='down');
      sceneLabel(actor,p.id===me?`${p.username} · 你`:p.username,0,-34);
    }
  },[selected,index,position.q,position.r,route,doors,map?.level,objects,loot,residents,me]);
  async function action(path:string,body:object={}){
    if(!map||map.id!==position.sceneId||pending.current||!connected)return;
    pending.current=true;setBusy(true);
    try{
      const result=await api<{path?:Hex[]}>(path,{version:map.worldVersion,...body});
      if(latest.current.position.sceneId!==map.id)return;
      if(path==='/scene/move')setWalking(result.path??[]);
      else if(path==='/stop')setWalking([]);
    }catch(e){onMessage((e as Error).message);}
    finally{pending.current=false;setBusy(false);}
  }
  const ready=connected&&!moving&&!busy&&!!cells.length;
  const ownDoor=doors.filter(door=>same(door.cell,position));
  const cell=selected&&index.get(cellKey(selected));
  const atCell=!!selected&&same(position,selected);
  const span=Math.max(580,(map?.radius??6)*130+80);
  return <section className="scene-view" data-level={map?.level} aria-label={map?.level===3?'三级室内地图':'二级探索地图'}>
    <SceneCanvas host={host} camera={{center,zoom,span}} onCamera={next=>{setCenter(next.center);setZoom(next.zoom);}}
      className="scene-board" label="场景六边格" drawGround={ground} drawOverlay={overlay}
      onSelect={h=>{if(index.has(cellKey(h)))setSelected(h);else setSelected(null);}}/>
    <div className="scene-caption"><strong>{map?.name??'正在载入场景'}</strong><span>{map?.level===3?'三级 · 室内':'二级 · 地块'} {map&&` · ${map.owner.q}, ${map.owner.r}`}</span></div>
    {error&&<div className="scene-error" role="alert">{error}<button onClick={()=>setReload(v=>v+1)}>重试</button></div>}
    <MapTools className="map-tools scene-tools" onMessage={onMessage}>
      <button aria-label="放大场景" onClick={()=>setZoom(z=>Math.min(4,z*1.2))}><Plus size={18}/></button>
      <button aria-label="缩小场景" onClick={()=>setZoom(z=>Math.max(.45,z/1.2))}><Minus size={18}/></button>
      <button aria-label="查看全图" onClick={()=>{setCenter({x:0,y:0});setZoom(1);}}><Maximize size={18}/></button>
      <button aria-label="定位自己" onClick={()=>{setCenter(scenePoint(position));setZoom(map?.level===2?1.65:1);}}><LocateFixed size={18}/></button>
    </MapTools>
    <div className="scene-actions">
      {moving&&<button disabled={busy||!connected} onClick={()=>void action('/stop')}><Square size={14}/>停止移动</button>}
      {ownDoor.map(door=><button key={door.id} disabled={!ready||!door.open} onClick={()=>void action('/scene/door',{doorId:door.id})}><LogOut size={15}/>{map?.level===3?'返回室外':door.name}</button>)}
      {map?.level===2&&sceneSides(position,map.radius).map(direction=><button key={direction} disabled={!ready} onClick={()=>void action('/scene/cross',{direction})}>向{names[direction]}跨越</button>)}
      {map?.level===2&&<button disabled={!ready} onClick={()=>void action('/scene/leave')}>返回一级地图</button>}
      {service==='shop'&&<button disabled={!ready} onClick={onShop}>商店交易</button>}
      {service==='inn'&&<button disabled={!ready} onClick={onRest}>旅店休整</button>}
    </div>
    {cell&&<aside className="scene-detail">
      <header><div><small>{cell.q}, {cell.r} · {cell.walkable?'可通行':'不可通行'}</small><h3>{terrainNames[cell.terrain]??'地面'}</h3></div>
        <button className="icon-button" aria-label="关闭格子详情" onClick={()=>setSelected(null)}><X size={16}/></button></header>
      {doors.filter(d=>same(d.cell,cell)).map(d=><p key={d.id}>{d.name} · {d.open?(atCell?'可通过':'到达后可通过'):'门已关闭'}</p>)}
      {objects.filter(o=>same(o,cell)).map(o=><div className="scene-interaction" key={o.id}><span>{o.name}{o.depleted?' · 恢复中':o.inUse?' · 使用中':''}</span>
        {o.kind==='facility'?<button disabled={!ready||Math.max(Math.abs(position.q-o.q),Math.abs(position.r-o.r),Math.abs(position.q+position.r-o.q-o.r))!==1} onClick={()=>onFacility(o)}>查看配方</button>:
        <button disabled={!ready||!atCell||o.depleted||o.inUse} onClick={()=>void action('/production/start',{kind:'gather',objectId:o.id})}>采集</button>}</div>)}
      {loot.filter(o=>o.battleQ===cell.q&&o.battleR===cell.r).map(o=><div className="scene-interaction" key={o.id}><span>{o.name}</span><button disabled={!ready||!atCell} onClick={()=>onLoot(o.id)}>查看</button></div>)}
      {residents.filter(p=>same(p,cell)&&p.id!==me&&p.life!=='dead').map(p=><div className="scene-interaction" key={p.id}><span>{p.username}</span><button disabled={!ready} onClick={()=>onBattle(p.id)}>发起战斗</button></div>)}
      <button className="primary full" disabled={!ready||!preview.length} onClick={()=>void action('/scene/move',{q:cell.q,r:cell.r})}><Footprints size={16}/>{moving?'正在移动':atCell?'你已在这里':preview.length?`前往 · ${preview.length} 格`:'不可到达'}</button>
    </aside>}
  </section>;
}
