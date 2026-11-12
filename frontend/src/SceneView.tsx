import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {Graphics} from 'pixi.js';
import {api} from './api';
import {BattleView,type BattleViewProps,type SceneBattleControls,type SceneBattleMap} from './BattleView';
import {arrangeInteractions,hitInteraction,interactionOffsets,paintInteractionPins,type CellInteraction} from './cellInteractions';
import {PlaceActions,PlaceComponents} from './PlaceActions';
import {terrainModules} from './sceneModules';
import {MapChrome} from './MapChrome';
import {SceneCanvas,sceneActor,cellKey,hexShape,sceneLabel,scenePoint,type DrawScene} from './SceneCanvas';
import {drawWorldActor} from './worldArt';
import {drawEmoteBubble} from './emotes';
import {localMarker,localRoute,localTerrain,type LocalCell} from './localSceneArt';
import {drawDistrictBuilding,drawDistrictGround} from './districtArt';
import {sceneArtCells} from './sceneArtLayout';
import {scenePath,sceneSides} from './sceneGeometry';
import type {BlockedEdge,Emote,Hex,LootContainer,Player} from './types';
import type {ProductionState,SceneObject} from './ProductionPanel';
import './scene.css';

export type ScenePosition=Hex&{characterId:string;sceneId:string};
export type SceneResident=Hex&{id:string;username:string;life:string;hp:number;maxHp:number;kind:string};
export type SceneDoor={id:string;name:string;cell:Hex;destinationScene:string;destination:Hex;open:boolean};
type SceneMap={level:number;id:string;worldVersion:string;name:string;radius:number;owner:Hex;cells:LocalCell[];blockedEdges:BlockedEdge[]};
type Props={
  position:ScenePosition;doors:SceneDoor[];service:string|null;onShop:()=>void;onRest:()=>void;
  objects:SceneObject[];onFacility:(o:SceneObject)=>void;residents:SceneResident[];players:Player[];loot:LootContainer[];
  onLoot:(id:string)=>void;moving:boolean;connected:boolean;me:string;onMessage:(s:string)=>void;onBattle:(id:string)=>void;
  emotes:Emote[]; worldName:string; production:ProductionState|null; clock:number; lifeBusy:boolean; onCancelRest:()=>void;
  battleView?:BattleViewProps; hidden?:boolean;
};
const same=(a:Hex,b:Hex)=>a.q===b.q&&a.r===b.r;
const distance=(a:Hex,b:Hex)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
const shortName=(name:string)=>name.split(' · ').at(-1)??name;
function openingView(map:SceneMap,position:Hex,doors:SceneDoor[]){
  const origin={q:0,r:0};
  const nearDoor=doors.find(door=>distance(door.cell,position)<=1);
  const route=map.level===2?scenePath(map.cells,position,origin,new Set(),map.blockedEdges):[];
  if(map.level===3){
    const shortScreen=window.matchMedia('(max-height:600px) and (orientation:landscape)').matches;
    return {center:{x:0,y:shortScreen?32:0},zoom:shortScreen ? .68 : .84};
  }
  const focus=nearDoor?.cell??route[Math.min(4,route.length-1)]??origin;
  const a=scenePoint(position),b=scenePoint(focus);
  const center={x:(a.x+b.x)/2,y:(a.y+b.y)/2};
  return {center,zoom:2.1};
}
const names=['东','东南','西南','西','西北','东北'];
const empty:LocalCell[]=[];
export function SceneView(props:Props){
  const {position,doors,service,onShop,onRest,objects,onFacility,residents,players,loot,onLoot,moving,connected,me,onMessage,onBattle,
    worldName,production,clock,emotes,lifeBusy,onCancelRest,battleView,hidden}=props;
  const [map,setMap]=useState<SceneMap|null>(null),[error,setError]=useState('');
  const [componentId,setComponentId]=useState<string|null>(null);
  const [selected,setSelected]=useState<Hex|null>(null),[busy,setBusy]=useState(false);
  const [preview,setPreview]=useState<Hex[]>([]),[planning,setPlanning]=useState(false),[planError,setPlanError]=useState('');
  const [walking,setWalking]=useState<Hex[]>([]),[reload,setReload]=useState(0);
  const [walkingTarget,setWalkingTarget]=useState<Hex|null>(null);
  const [entry,setEntry]=useState<Hex|null>(null);
  const [combat,setCombat]=useState<SceneBattleControls|null>(null);
  const inBattle=!!battleView;
  const alive=residents.some(resident=>resident.id===me&&resident.life==='alive');
  const [center,setCenter]=useState(()=>scenePoint(position)),[zoom,setZoom]=useState(1);
  const host=useRef<HTMLDivElement>(null),pending=useRef(false);
  const latest=useRef({position,doors,selected,onMessage});latest.current={position,doors,selected,onMessage};
  useEffect(()=>{
    const sceneId=position.sceneId;let cancelled=false;
    setMap(null);setError('');setSelected(null);setComponentId(null);setWalking([]);setWalkingTarget(null);
    void api<{active:boolean;map?:SceneMap;position?:ScenePosition}>('/scene').then(result=>{
      if(cancelled)return;
      if(result.map?.id!==sceneId){setError('场景状态已变化，请重试。');return;}
      setMap(result.map);
      const current=latest.current;
      const landed=result.position?.sceneId===sceneId?result.position:current.position;
      setEntry(landed);
      const opening=openingView(result.map,landed,current.doors);
      setCenter(opening.center);
      setZoom(opening.zoom);
    }).catch(e=>{if(!cancelled)setError(e.message);});
    return ()=>{cancelled=true;};
  },[position.sceneId,reload]);
  const cells=map?.id===position.sceneId?map.cells:empty;
  const index=useMemo(()=>new Map(cells.map(c=>[cellKey(c),c])),[cells]);
  const interactions=useMemo(()=>{
    const items:CellInteraction[]=[
      ...doors.map(door=>({id:door.id,kind:'door' as const,cell:door.cell,label:map?.level===3?'返回街道':shortName(door.name),order:0})),
      ...objects.map(object=>({id:object.id,kind:object.kind,cell:object,label:object.name,order:1})),
      ...loot.flatMap(item=>item.battleQ==null||item.battleR==null?[]:[{id:item.id,kind:'loot' as const,cell:{q:item.battleQ,r:item.battleR},label:item.name,order:2}]),
    ];
    const keeper=residents.find(resident=>resident.kind==='service'&&resident.life==='alive');
    if(keeper&&service)items.push({id:`service:${keeper.id}`,kind:'service',cell:keeper,label:service==='inn'?'旅店休整':'商店交易',order:3});
    return arrangeInteractions(items);
  },[doors,objects,loot,residents,service,map?.level]);
  const interactionById=useMemo(()=>new Map(interactions.map(item=>[item.id,item])),[interactions]);
  const cellComponents=selected?interactions.filter(item=>same(item.cell,selected)):[];
  const chosenComponent=cellComponents.find(item=>item.id===componentId);
  const selectedDoor=doors.find(door=>selected&&same(door.cell,selected)&&(!chosenComponent||chosenComponent.id===door.id));
  const artCells=useMemo(()=>sceneArtCells(cells,map?.level,doors,map?.name,map?.blockedEdges,entry??undefined),[cells,map,doors,entry]);
  const shownTerrain=useMemo(()=>new Map(artCells.map(c=>[cellKey(c),c.terrain])),[artCells]);
  const selectedTerrain=selected?shownTerrain.get(cellKey(selected)):undefined;
  const ownDoor=doors.find(door=>same(door.cell,position));
  const counterSelected=service==='inn'&&(!chosenComponent||chosenComponent.kind==='service')&&['inn-counter','reception'].includes(selectedTerrain??'');
  const facilitySelected=objects.some(o=>o.kind==='facility'&&selected&&same(o,selected)&&(!chosenComponent||chosenComponent.id===o.id));
  const adjacentTarget=facilitySelected||counterSelected&&selectedTerrain==='inn-counter';
  const closedDoor=selectedDoor?.open===false;
  const movingToSelected=moving&&selected!=null&&walkingTarget!=null&&same(selected,walkingTarget);
  useEffect(()=>{
    setPreview([]);setPlanError('');setPlanning(false);
    if(!selected||!map||map.id!==position.sceneId||!connected||!alive||inBattle||movingToSelected||same(latest.current.position,selected))return;
    if(!index.get(cellKey(selected))?.walkable&&!adjacentTarget)return;
    if(closedDoor)return;
    let current=true;
    setPlanning(true);
    const params=new URLSearchParams({version:map.worldVersion,q:String(selected.q),r:String(selected.r),adjacent:String(adjacentTarget)});
    void api<{path:Hex[]}>('/scene/route?'+params).then(result=>{
      if(current)setPreview(result.path);
    }).catch(e=>{if(current)setPlanError(e.message);}).finally(()=>{if(current)setPlanning(false);});
    return ()=>{current=false;};
  },[selected,map,position.sceneId,position.q,position.r,connected,alive,moving,index,adjacentTarget,closedDoor,inBattle,movingToSelected,componentId]);
  const route=useMemo(()=>preview.length?preview:moving?walking.slice(Math.max(0,walking.findIndex(h=>same(h,position))+1)):preview,[moving,walking,position.q,position.r,preview]);
  const ground=useCallback<DrawScene>(layer=>{
    localTerrain(layer,artCells,undefined,map?.blockedEdges,{exploration:true});
    if(map)drawDistrictGround(layer,artCells,map.level,map.blockedEdges??[]);
    if(map?.level===2)for(const door of doors){
      if(index.has(cellKey(door.cell)))drawDistrictBuilding(layer,door.cell,door.name);
    }
    const g=new Graphics();layer.addChild(g);
    for(const door of doors){
      if(!index.has(cellKey(door.cell)))continue;
      localMarker(g,door.cell,'door');const {x,y}=scenePoint(door.cell);
      sceneLabel(layer,map?.level===3?'返回街道':shortName(door.name),x,y+(map?.level===2?-113:33));
    }
    for(const object of objects){
      if(index.has(cellKey(object))&&!object.depleted)localMarker(g,object,object.code,interactionOffsets[object.id]);
    }
  },[artCells,map?.level,map?.blockedEdges,doors,index,objects]);
  const profiles=useMemo(()=>new Map(players.map(player=>[player.id,player])),[players]);
  const overlay=useCallback<DrawScene>(layer=>{
    const g=new Graphics();layer.addChild(g);
    const marked=selected??ownDoor?.cell;
    if(marked&&index.has(cellKey(marked)))hexShape(g,marked,33).fill({color:0xf1dc94,alpha:.2}).stroke({color:0x4c6244,width:4}).stroke({color:0xffebad,width:2});
    localRoute(g,position,route);
    const markedDoor=selectedDoor??(!selected?ownDoor:undefined);
    if(map?.level===2&&markedDoor){
      const {x,y}=scenePoint(markedDoor.cell);
      const shortSide=Math.min(host.current?.clientWidth??800,host.current?.clientHeight??800);
      const span=(map.radius*130+80);
      const labelSize=Math.min(48,Math.max(18,11*span/Math.max(1,shortSide*zoom)));
      sceneLabel(layer,shortName(markedDoor.name),x,y+37,labelSize);
    }
    for(const item of loot)if(item.battleQ!=null&&item.battleR!=null){
      const pin=interactionById.get(item.id);
      localMarker(g,{q:item.battleQ,r:item.battleR},item.kind,pin?.visible?pin.offset:undefined);
    }
    const visible=residents.filter(p=>p.life!=='dead'&&index.has(cellKey(p))).sort((a,b)=>scenePoint(a).y-scenePoint(b).y);
    for(const p of visible){
      const actor=sceneActor(layer,p.id,p),sprite=new Graphics();actor.addChild(sprite);
      const profile=profiles.get(p.id);
      drawWorldActor(sprite,{color:profile?.color??'#628aa0',online:profile?.online??true,kind:p.kind,species:profile?.species??null},p.id===me);
      sprite.scale.set(1.25);
      if(p.life==='down')sprite.rotation=Math.PI/2;
      const emote=emotes.find(item=>item.accountId===p.id&&item.expiresAt>clock);
      if(emote){
        const bubble=actor.addChild(new Graphics());
        bubble.y=-68;
        drawEmoteBubble(bubble,emote.code);
      }
      sceneLabel(actor,p.id===me?`${p.username} · 你`:p.username,0,p.id===me&&route.length?20:-34);
    }
    paintInteractionPins(layer,interactions,scenePoint,componentId);
  },[interactions,interactionById,componentId,selected,selectedDoor,ownDoor,index,position.q,position.r,route,loot,residents,profiles,me,emotes,clock,map?.level,map?.radius,zoom]);
  async function action(path:string,body:object={}){
    if(!map||map.id!==position.sceneId||pending.current||!connected)return;
    const movementGoal=latest.current.selected;
    pending.current=true;setBusy(true);
    try{
      const result=await api<{path?:Hex[]}>(path,path==='/production/start'?body:{version:map.worldVersion,...body});
      if(latest.current.position.sceneId!==map.id)return;
      if(path==='/scene/move'){
        setWalking(result.path??[]);
        setWalkingTarget(movementGoal);
        setPreview([]);
      }else if(path==='/stop'){setWalking([]);setWalkingTarget(null);}
    }catch(e){onMessage((e as Error).message);}
    finally{pending.current=false;setBusy(false);}
  }
  const ready=connected&&alive&&!moving&&!busy&&!!cells.length;
  const ownLoot=loot.filter(item=>item.battleQ===position.q&&item.battleR===position.r);
  const ownObjects=objects.filter(o=>distance(position,o)===(o.kind==='facility'?1:0));
  const cell=selected&&index.get(cellKey(selected));
  const atCell=!!selected&&same(position,selected);
  const actionDoor=selected?selectedDoor:ownDoor;
  const atDoor=!!actionDoor&&same(actionDoor.cell,position);
  const task=production?.task;
  const resting=task?.kind==='rest';
  const keeper=residents.some(resident=>resident.kind==='service'&&resident.life==='alive');
  const canRest=ready&&keeper&&!task&&!lifeBusy;
  const roomName=map?.name??'正在载入场景';
  const targetName=chosenComponent?.label??(selectedDoor?(map?.level===3?'返回街道的门':shortName(selectedDoor.name))
    :counterSelected?'柜台区':cell?objects.find(o=>same(o,cell))?.name??terrainModules[selectedTerrain??cell.terrain]?.name??'地面'
    :actionDoor&&map?.level===2?shortName(actionDoor.name):roomName);
  const showRoomService=chosenComponent?chosenComponent.kind==='service':!selected||counterSelected||atCell&&!actionDoor;
  const enterLabel=map?.level===3?'返回街道':'进入'+(actionDoor?shortName(actionDoor.name):'房间');
  const restAction=<button className="primary" disabled={!canRest} onClick={onRest}>旅店休整</button>;
  const enterAction=actionDoor&&atDoor&&<button className="primary" disabled={!ready||!actionDoor.open}
    onClick={()=>void action('/scene/door',{doorId:actionDoor.id})}>{enterLabel}</button>;
  const stopAction=<button className="primary" disabled={busy||!connected} onClick={()=>void action('/stop')}>停止移动</button>;
  const travelAction=preview.length>0&&<button className="primary" disabled={!connected||!alive||busy||planning} onClick={()=>{
    if(selected)void action('/scene/move',{q:selected.q,r:selected.r,adjacent:adjacentTarget});
  }}>{selectedDoor?'走到门口':counterSelected?'走到柜台附近':'前往这里'}</button>;
  const serviceAction=showRoomService&&service==='inn'?restAction
    :showRoomService&&service==='shop'?<button className="primary" disabled={!ready||!keeper} onClick={onShop}>商店交易</button>:null;
  const focusedObject=ownObjects.find(object=>object.id===chosenComponent?.id);
  const focusedLoot=ownLoot.find(item=>item.id===chosenComponent?.id);
  const focusedAction=focusedObject
    ? <button className="primary" disabled={!ready||focusedObject.depleted||focusedObject.inUse} onClick={()=>{
        if(focusedObject.kind==='facility')onFacility(focusedObject);
        else void action('/production/start',{kind:'gather',objectId:focusedObject.id});
      }}>{focusedObject.kind==='facility'?'查看配方':'采集'}</button>
    : focusedLoot?<button className="primary" disabled={!ready} onClick={()=>onLoot(focusedLoot.id)}>查看遗留物</button>:null;
  const primary=resting?<button className="primary" disabled={lifeBusy} onClick={onCancelRest}>取消休整</button>
    :moving?stopAction
    :selectedDoor?enterAction||travelAction:focusedAction||serviceAction||enterAction||travelAction;
  const remaining=route.length;
  const status=resting?`休整中 · 剩 ${Math.max(0,Math.ceil((task.dueAt-clock)/1000))} 秒`
    :moving?`正在移动${remaining?` · 剩 ${remaining} 格`:''}`
    :closedDoor?'门已关闭'
    :atDoor?'已到门口'
    :planning?'正在查找路线…'
    :planError|| (preview.length?`距${selectedDoor?'门口':'目标'} ${preview.length} 格`:selected&&!atCell&&!cell?.walkable&&!adjacentTarget?'这格无法通行':selected&&atCell?'已到达':'');
  const span=Math.max(580,(map?.radius??6)*130+80);
  const zoomBy=useCallback((factor:number)=>setZoom(z=>Math.min(4,Math.max(.45,z*factor))),[]);
  const fit=useCallback(()=>{setCenter({x:0,y:0});setZoom(.9);},[]);
  const locate=useCallback((h:Hex)=>setCenter(scenePoint(h)),[]);
  const sharedMap=useMemo<SceneBattleMap>(()=>({setControls:setCombat,locate,zoom:zoomBy,fit}),[locate,zoomBy,fit]);
  const combatDraw=combat?.drawOverlay;
  const combatLoot=battleView?.loot,combatActors=battleView?.battle.actors;
  const combatOverlay=useCallback<DrawScene>(layer=>{
    if(!combatDraw||!combatLoot||!combatActors)return;
    const markers=new Graphics();layer.addChild(markers);
    for(const item of combatLoot)if(item.battleQ!=null&&item.battleR!=null)
      localMarker(markers,{q:item.battleQ,r:item.battleR},item.kind);
    const engaged=new Set(combatActors.map(actor=>actor.accountId));
    for(const resident of residents){
      if(engaged.has(resident.id)||resident.life==='dead'||!index.has(cellKey(resident)))continue;
      const actor=sceneActor(layer,resident.id,resident),sprite=actor.addChild(new Graphics());
      const profile=profiles.get(resident.id);
      drawWorldActor(sprite,{color:profile?.color??'#628aa0',online:profile?.online??true,kind:resident.kind,species:profile?.species??null},false);
      sprite.scale.set(1.25);
      if(resident.life==='down')sprite.rotation=Math.PI/2;
      sceneLabel(actor,resident.username,0,-34);
    }
    combatDraw(layer);
  },[combatDraw,combatLoot,combatActors,residents,profiles,index]);
  const extraActions=[
    ...(serviceAction&&atDoor&&actionDoor&&!selected?[<button key="exit" className="place-secondary" disabled={!ready||!actionDoor.open}
      onClick={()=>void action('/scene/door',{doorId:actionDoor.id})}>{enterLabel}</button>]:[]),
    ...(moving&&!movingToSelected&&preview.length?[<button key="redirect" className="place-secondary" disabled={busy||!connected||!alive||planning} onClick={()=>{
      if(selected)void action('/scene/move',{q:selected.q,r:selected.r,adjacent:adjacentTarget});
    }}>改往选中地点</button>]:[]),
    ...(counterSelected&&travelAction&&!moving?[<button key="counter" className="place-secondary" disabled={!connected||!alive||busy||planning} onClick={()=>{
      if(selected)void action('/scene/move',{q:selected.q,r:selected.r,adjacent:adjacentTarget});
    }}>走到柜台附近</button>]:[]),
    ...(map?.level===2?[<button key="leave" className="place-secondary" disabled={!ready} onClick={()=>void action('/scene/leave')}>返回青屿大陆</button>]:[]),
    ...(map?.level===2?sceneSides(position,map.radius).map(direction=><button className="place-secondary" key={direction} disabled={!ready}
      onClick={()=>void action('/scene/cross',{direction})}>向{names[direction]}跨越</button>):[]),
    ...ownObjects.filter(o=>o.id!==focusedObject?.id).map(o=>o.kind==='facility'
      ?<button className="place-secondary" key={o.id} disabled={!ready} onClick={()=>onFacility(o)}>查看{o.name}配方</button>
      :<button className="place-secondary" key={o.id} disabled={!ready||o.depleted||o.inUse} onClick={()=>void action('/production/start',{kind:'gather',objectId:o.id})}>采集{o.name}</button>),
    ...ownLoot.filter(o=>o.id!==focusedLoot?.id).map(o=><button className="place-secondary" key={o.id} disabled={!ready} onClick={()=>onLoot(o.id)}>查看{o.name}</button>),
    ...residents.filter(p=>selected&&same(p,selected)&&p.id!==me&&p.life!=='dead').map(p=><button className="place-secondary" key={p.id} disabled={!ready}
      onClick={()=>onBattle(p.id)}>与{p.username}战斗</button>),
  ];
  const secondary=extraActions.length===1?extraActions[0]:extraActions.length>1?<details><summary className="place-secondary">更多操作</summary><div>{extraActions}</div></details>:null;
  const battleSelf=battleView?.battle.actors.find(actor=>actor.accountId===me);
  const entryPulse=battleView&&battleSelf?{id:battleView.battle.id,cell:battleSelf}:undefined;
  return <section className="scene-view" hidden={hidden} data-level={map?.level} data-scene-id={position.sceneId}
    aria-label={map?.level===3?'室内地图':'城镇及野外地图'}>
    <SceneCanvas host={host} camera={{center,zoom,span}} onCamera={next=>{setCenter(next.center);setZoom(next.zoom);}}
      className="scene-board" entryPulse={entryPulse} label={inBattle?'六边形战场':'场景六边格'} drawGround={ground} drawOverlay={inBattle&&combat?combatOverlay:overlay}
      drawHighlights={inBattle?combat?.drawHighlights:undefined} drawEffects={inBattle?combat?.drawEffects:undefined}
      onHover={inBattle?combat?.onHover:undefined} onSelect={(h,point)=>{
        if(inBattle){combat?.onSelect(h);return;}
        const hit=point?hitInteraction(interactions,point,scenePoint):undefined;
        const target=hit?.cell??h;
        if(selected&&same(target,selected)&&componentId===(hit?.id??null))return;
        if(!selected||!same(target,selected)||componentId!==(hit?.id??null))setPreview([]);
        setComponentId(hit?.id??null);
        setSelected(index.has(cellKey(target))?target:null);
      }}/>
    {battleView&&<BattleView key={battleView.battle.id} {...battleView} sharedMap={sharedMap}/>}
    {!inBattle&&<MapChrome title={map?.name??'正在载入场景'} subtitle={map?.level===3?`${worldName} › ${map.name.split(' · ')[0]}`:worldName} zoom={Math.round(zoom*100)}
      onZoom={zoomBy} onFit={fit} onLocate={()=>locate(position)} onMessage={onMessage} toolsClass="scene-tools"/>}
    {error&&<div className="scene-error" role="alert">{error}<button onClick={()=>setReload(v=>v+1)}>重试</button></div>}
    {map&&!inBattle&&<PlaceActions title={targetName} subtitle={status} primary={primary} secondary={secondary}
      onClose={selected?()=>{setSelected(null);setComponentId(null);}:undefined}>
      <PlaceComponents items={cellComponents} selected={componentId} onSelect={id=>{if(id!==componentId){setPreview([]);setComponentId(id);}}}/>
      {service==='inn'&&showRoomService&&!resting&&<p>{keeper?'停下后可休整 · 5 铜币 · 10 秒':'掌柜暂时无法提供服务'}</p>}
      {selectedDoor&&map.level!==3&&!atDoor&&!closedDoor&&<p>到门口后可以进入。</p>}
    </PlaceActions>}
  </section>;
}
