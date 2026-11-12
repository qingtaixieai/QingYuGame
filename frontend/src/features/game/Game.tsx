import {LootPanel} from "../../LootPanel";
import {SocialPanel} from "../social/SocialPanel";
import {ContainerPage} from "../../ContainerPage";
import {DropPanel} from "../../DropPanel";
import type {LootContainer} from "../../types";
import {MapChrome} from "../../MapChrome";
import {arrangeInteractions,type CellInteraction} from "../../cellInteractions";
import {PlaceActions,PlaceComponents} from "../../PlaceActions";
import {GameCodex} from "../../GameCodex";
import {useCallback,useEffect,useMemo,useRef,useState} from "react";
import {Anchor,BookOpen,Compass,Map as MapIcon,Users,Shield,LogOut,TreePine,Home,Landmark,LocateFixed,X,ChevronRight,Footprints,LoaderCircle,Square,Leaf,Backpack,MessageCircle,PanelLeftClose,PanelLeftOpen,Swords} from "lucide-react";
import {api,ApiError} from "../../api";
import {ChatPanel,useChatStatus} from "../social/Chat";
import {EmotePicker} from "../social/EmotePicker";
import {MapView} from "../../MapView";
import {FerryPanel} from "../../FerryPanel";
import {ShopPanel} from "../../ShopPanel";
import {ProductionPanel,type ProductionState,type SceneObject} from "../../ProductionPanel";
import {StatusPanel} from "../../StatusPanel";
import {CombatLog} from "../../CombatLog";
import {CharacterPanel,LifePanel} from "../../CharacterPanel";
import {BattleView,type BattleViewProps} from "../../BattleView";
import {SceneView,type ScenePosition,type SceneResident} from "../../SceneView";
import type {MapControls} from "../../MapView";
import type {Character,Account,Hex,Player,State,Tile,World,Item,ResourceNode,WorldAction,Emote,BattleSummary,BattleState,FerryState} from "../../types";
import {key,terrainNames,placeNames,walkable} from "../../types";
import {Admin} from '../admin/Admin';
import {Brand} from '../../components/Brand';

export type GameProps={me:Account;onLogout:()=>void;onAccessLost:(reason?:string)=>void};
type WorldRoute={version:string;origin:Hex;path:Hex[]};
const emptyRoute:Hex[]=[];
export function Game({me,onLogout,onAccessLost}:GameProps){
  const [blockedEdges,setBlockedEdges]=useState<import('../../types').BlockedEdge[]>([]);
  const [scenePosition,setScenePosition]=useState<ScenePosition|null>(null),[sceneResidents,setSceneResidents]=useState<SceneResident[]>([]),[sceneMoving,setSceneMoving]=useState(false);
  const [production,setProduction]=useState<ProductionState|null>(null),[sceneObjects,setSceneObjects]=useState<SceneObject[]>([]),[facility,setFacility]=useState<SceneObject|null>(null);
  const [sceneDoors,setSceneDoors]=useState<import('../../SceneView').SceneDoor[]>([]),[sceneService,setSceneService]=useState<string|null>(null);
  const [sceneLoot,setSceneLoot]=useState<LootContainer[]>([]);
  useEffect(()=>{setSelected(null);setRoute([]);setLootOpen(false);setContainerOpen(null);setDropOpen(false);setShopOpen(false);setFacility(null);},[scenePosition?.sceneId]);
  const [battleEnded,setBattleEnded]=useState(false);
  const [shopOpen,setShopOpen]=useState(false);
  const [loot,setLoot]=useState<LootContainer[]>([]),[corpses,setCorpses]=useState<LootContainer[]>([]),[lootOpen,setLootOpen]=useState(false),[containerOpen,setContainerOpen]=useState<string|null>(null),[dropOpen,setDropOpen]=useState(false);
  const [character,setCharacter]=useState<Character|null>(null),[topHidden,setTopHidden]=useState(()=>window.matchMedia('(max-height:600px) and (orientation:landscape)').matches),[lifeBusy,setLifeBusy]=useState(false);
  const [world,setWorld]=useState<World|null>(null),[players,setPlayers]=useState<Player[]>([]),[selected,setSelected]=useState<Tile|null>(null),[route,setRoute]=useState<Hex[]>([]);
  const [routePreview,setRoutePreview]=useState<{id:string;route?:WorldRoute;error?:string}>();
  const [previewRetry,setPreviewRetry]=useState(0),[travelPending,setTravelPending]=useState(false);
  const [ferryDetails,setFerryDetails]=useState(false),[ferryBusy,setFerryBusy]=useState(false);
  const [worldDetailsExpanded,setWorldDetailsExpanded]=useState(true);
  const [tab,setTab]=useState<'places'|'players'|'inventory'|'chat'|'codex'>('places'),[sidebarOpen,setSidebarOpen]=useState(()=>!window.matchMedia('(max-height:600px) and (orientation:landscape)').matches),[filter,setFilter]=useState('all'),[zoom,setZoom]=useState(100),[connected,setConnected]=useState(false),[message,setMessage]=useState(''),[admin,setAdmin]=useState(false),[loading,setLoading]=useState(true);
  const [chatRevision,setChatRevision]=useState(0),[chatMode,setChatMode]=useState<'public'|'private'>('public'),[chatPeer,setChatPeer]=useState<string|null>(null);
  const {status:chatStatus,refresh:refreshChat}=useChatStatus(chatRevision,onAccessLost);
  useEffect(()=>{if(scenePosition)setSidebarOpen(false);},[scenePosition?.sceneId]);
  const showTab=(next:'places'|'players'|'inventory'|'chat'|'codex')=>{if(tab===next)setSidebarOpen(open=>!open);else{setTab(next);setSidebarOpen(true);}};
  const openPrivate=(id:string)=>{setChatPeer(id);setChatMode('private');setTab('chat');setSidebarOpen(true);};
  const [ferry,setFerry]=useState<FerryState|null>(null);
  const [emotes,setEmotes]=useState<Emote[]>([]);
  const [items,setItems]=useState<Item[]>([]),[resources,setResources]=useState<ResourceNode[]>([]),[actions,setActions]=useState<WorldAction[]>([]),[clock,setClock]=useState(Date.now());
  const [battle,setBattle]=useState<BattleState>({active:false}),[battles,setBattles]=useState<BattleSummary[]>([]),[view,setView]=useState<'world'|'battle'>('world');
  const battleRef=useRef<BattleState>({active:false}),battleRevision=useRef(-1),battleRequest=useRef(0);
  const [hotbarRevision,setHotbarRevision]=useState(-1);
  const refreshBattle=useCallback(async()=>{const request=++battleRequest.current;const next=await api<BattleState>('/battle/current');if(request!==battleRequest.current)return;const previous=battleRef.current;battleRef.current=next;setBattle(next);if(next.active&&(!previous.active||previous.id!==next.id)){setView('battle');setSidebarOpen(false);}if(!next.active&&previous.active){setView('world');setBattleEnded(!previous.sceneId);setMessage(previous.sceneId?'战斗已结束或已撤离，位置以当前场景为准':'你已离开战场，可查看本格战利品');}},[]);
  const offset=useRef(0),resourceSignature=useRef(''),previousItems=useRef<Item[]|null>(null);
  const applyInventory=(next:Item[])=>{if(previousItems.current){const gained=next.map(i=>({name:i.name,quantity:i.quantity-(previousItems.current!.find(old=>old.code===i.code)?.quantity||0)})).filter(i=>i.quantity>0);if(gained.length)setMessage(gained.map(i=>`${i.name} +${i.quantity}`).join(' · '));}previousItems.current=next;setItems(next);};
  useEffect(()=>{const timer=setInterval(()=>setClock(Date.now()+offset.current),1000);return()=>clearInterval(timer);},[]);
  const controls=useRef<MapControls|null>(null),worldRef=useRef<World|null>(null),lastVersion=useRef('');
  const reload=useCallback(async()=>{const data=await api<World>('/world');worldRef.current=data;lastVersion.current=data.version;setWorld(data);setRoute([]);setSelected(null);},[]);
  useEffect(()=>{let alive=true;reload().catch(e=>{if(alive)setMessage(e.message);}).finally(()=>{if(alive)setLoading(false);});return()=>{alive=false;};},[reload]);
  useEffect(()=>{void refreshBattle().catch(e=>setMessage(e.message));},[refreshBattle]);
  useEffect(()=>{
    if(!world)return;
    let alive=true,socket:WebSocket|null=null,timer:ReturnType<typeof setTimeout>|null=null,attempt=0;
    function connect(){
      socket=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`);
      socket.onopen=()=>{if(alive){setConnected(true);attempt=0;resourceSignature.current='';void refreshBattle().catch(e=>setMessage(e.message));}};
      socket.onmessage=e=>{if(!alive)return;const data=JSON.parse(e.data);if(data.type==='chat-changed'){setChatRevision(v=>v+1);return;}if(data.type!=='state')return;const state=data as State;
        if(data.version!==lastVersion.current){lastVersion.current=data.version;setMessage('世界地图已更新，正在载入');reload().catch(e=>setMessage(e.message));}
        offset.current=data.serverTime-Date.now();setClock(data.serverTime);setActions(data.actions);setEmotes(data.emotes||[]);setBattles(state.battles||[]);setFerry(state.ferry||null);
        setBlockedEdges(state.blockedEdges||[]);
        const nextDoors=state.sceneDoors??[],nextObjects=state.sceneObjects??[];
        setSceneDoors(old=>JSON.stringify(old)===JSON.stringify(nextDoors)?old:nextDoors);
        setSceneService(state.sceneService||null);setProduction(state.production||null);
        setSceneObjects(old=>JSON.stringify(old)===JSON.stringify(nextObjects)?old:nextObjects);setScenePosition(state.scenePosition||null);setSceneMoving(!!state.sceneMoving);
        const nextResidents=state.sceneResidents||[],nextLoot=(state as State&{loot?:LootContainer[]}).loot||[];
        setSceneResidents(old=>JSON.stringify(old)===JSON.stringify(nextResidents)?old:nextResidents);
        setLoot(old=>JSON.stringify(old)===JSON.stringify(nextLoot)?old:nextLoot);
        setSceneLoot((state as State&{sceneLoot?:LootContainer[]}).sceneLoot||[]);
        if(state.battleRevision!==battleRevision.current){battleRevision.current=state.battleRevision;setHotbarRevision(state.battleRevision);void refreshBattle().catch(e=>setMessage(e.message));}
        const signature=JSON.stringify(data.resources);if(signature!==resourceSignature.current){setResources(data.resources);resourceSignature.current=signature;api<Item[]>('/inventory').then(v=>{if(alive)applyInventory(v);}).catch(e=>{if(alive){if(resourceSignature.current===signature)resourceSignature.current='';setMessage(e.message);}});}
        setCorpses((state as State&{carriedCorpses:LootContainer[]}).carriedCorpses||[]);setCharacter(state.character);if(state.inventory){previousItems.current=state.inventory;setItems(state.inventory);}
        setPlayers(old=>JSON.stringify(old)===JSON.stringify(state.players)?old:state.players);
        const own=state.players.find(p=>p.id===me.id);if(own)setRoute(old=>{const i=old.findIndex(h=>h.q===own.q&&h.r===own.r);return own.moving?(i>=0?old.slice(i+1):old):(old.length?[]:old);});
      };
      socket.onclose=()=>{if(!alive)return;setConnected(false);api<Account>('/me').then(a=>{if(!a.approved){onAccessLost();return;}timer=setTimeout(connect,Math.min(10000,1000*2**attempt++));}).catch(e=>{if(e instanceof ApiError&&[401,403].includes(e.status))onAccessLost(e.message);else timer=setTimeout(connect,5000);});};
      socket.onerror=()=>socket?.close();
    }
    connect();return()=>{alive=false;if(timer)clearTimeout(timer);socket?.close();};
  },[!!world,me.id,refreshBattle]);
  const own=players.find(p=>p.id===me.id),online=players.filter(p=>p.online&&p.kind!=='monster');
  const aboard=!!ferry?.passengerIds.includes(me.id);
  const [activeComponent,setActiveComponent]=useState<string|null>(null);
  const choose=useCallback((tile:Tile,componentId?:string)=>{setActiveComponent(componentId??null);setSelected(tile);setFerryDetails(false);setWorldDetailsExpanded(true);if(window.matchMedia('(max-height:600px) and (orientation:landscape)').matches)setSidebarOpen(false);},[]);
  const localTile=own&&world?.tiles.find(t=>key(t)===key(own));
  const place=selected??localTile;
  const worldInteractions=useMemo(()=>{
    const items:CellInteraction[]=[];
    for(const tile of world?.tiles??[])if(tile.place)items.push({id:`place:${key(tile)}`,kind:'place',label:tile.place.name,cell:tile});
    if(ferry)for(const cell of [ferry.mainlandPort,ferry.islandLanding])items.push({id:`ferry:${key(cell)}`,kind:'ferry',label:'渡船',cell,order:1});
    for(const resource of resources)items.push({id:resource.id,kind:'resource',label:resource.kind==='wood'?'森林资源':'散落石头',cell:resource,order:2});
    for(const item of loot)if(!item.battleId)items.push({id:item.id,kind:'loot',label:item.name,cell:item,order:3});
    return arrangeInteractions(items);
  },[world,resources,loot,ferry?.mainlandPort.q,ferry?.mainlandPort.r,ferry?.islandLanding.q,ferry?.islandLanding.r]);
  const worldCellComponents=place?worldInteractions.filter(item=>key(item.cell)===key(place)):[];
  const worldComponent=worldCellComponents.find(item=>item.id===activeComponent);
  const atPlace=!!own&&!!place&&key(own)===key(place);
  const canUsePlace=atPlace&&connected&&!own?.moving&&!aboard&&!battle.active;
  const resource=place&&resources.find(n=>key(n)===key(place));
  const nearbyLoot=place?loot.filter(x=>key(x)===key(place)):[];
  const openBattle=place&&battles.find(b=>key(b)===key(place));
  const here=place?players.filter(p=>key(p)===key(place)):[];
  const isSoul=character?.life==='soul';
  const destinationQ=selected?.q,destinationR=selected?.r,originQ=own?.q,originR=own?.r;
  const routeVersion=world?.version;
  const routeEnd=route.at(-1);
  const followingRoute=!!selected&&!!routeEnd&&!!own?.moving&&key(selected)===key(routeEnd);
  const canPreview=connected&&!scenePosition&&!aboard&&!battle.active&&!atPlace&&!followingRoute&&
    !!selected&&walkable(selected)&&(isSoul||!openBattle)&&(character?.life==='alive'||isSoul);
  const passageSignature=blockedEdges.map(edge=>`${key(edge.from)}:${key(edge.to)}`).sort().join(';');
  const battleSignature=isSoul?'':battles.map(key).sort().join(';');
  const previewId=canPreview&&routeVersion&&originQ!=null&&originR!=null
    ? JSON.stringify([routeVersion,originQ,originR,destinationQ,destinationR,passageSignature,battleSignature,previewRetry]) : null;
  const activePreview=previewId&&routePreview?.id===previewId?routePreview:undefined;
  useEffect(()=>{
    setRoutePreview(undefined);
    if(!previewId||destinationQ==null||destinationR==null||!routeVersion)return;
    let cancelled=false;
    const query=new URLSearchParams({q:String(destinationQ),r:String(destinationR),version:routeVersion});
    void api<WorldRoute>(`/world/route?${query}`).then(next=>{
      if(cancelled||lastVersion.current!==routeVersion)return;
      if(next.version===routeVersion&&next.origin.q===originQ&&next.origin.r===originR){
        setRoutePreview({id:previewId,route:next});
      }else{
        setRoutePreview({id:previewId,error:'当前位置已变化，请重新预览路线'});
      }
    }).catch((e:Error)=>{
      if(!cancelled&&lastVersion.current===routeVersion)setRoutePreview({id:previewId,error:e.message});
    });
    return()=>{cancelled=true;};
  },[previewId,destinationQ,destinationR,routeVersion,originQ,originR]);
  async function travel(){
    const plan=activePreview?.route;
    if(!selected||!plan?.path.length||travelPending||lastVersion.current!==plan.version)return;
    setTravelPending(true);
    try{
      const data=await api<{path:Hex[]}>('/move',{q:selected.q,r:selected.r,version:plan.version,origin:plan.origin});
      if(lastVersion.current!==plan.version)return;
      setRoute(data.path);
      setMessage(`前往${selected.place?.name||terrainNames[selected.terrain]} · ${data.path.length} 格`);
    }catch(e){
      setMessage((e as Error).message);
      setPreviewRetry(i=>i+1);
    }finally{
      setTravelPending(false);
    }
  }
  async function startBattle(targetId:string){if(!world)return;try{await api('/battle/start',{targetId,version:world.version});await refreshBattle();setSidebarOpen(false);}catch(e){setMessage((e as Error).message);}}
  async function joinBattle(id:string){if(!world)return;try{await api('/battle/join',{battleId:id,version:world.version});await refreshBattle();setSidebarOpen(false);}catch(e){setMessage((e as Error).message);}}
  async function ferryCommand(action:string,body:object={}){
    if(!world||ferryBusy)return;
    setFerryBusy(true);
    try{
      const result=await api<{message:string}>('/ferry/'+action,{...body,version:world.version});
      setMessage(result.message);
      if(action==='board')setSelected(null);
      applyInventory(await api<Item[]>('/inventory'));
    }catch(e){setMessage((e as Error).message);}
    finally{setFerryBusy(false);}
  }
  async function lifeAction(action:string,targetId?:string){setLifeBusy(true);try{await api('/character/action',{action,targetId});await refreshBattle();}catch(e){setMessage((e as Error).message);}finally{setLifeBusy(false);}}
  async function equip(mainHand:string|null,offHand:string|null=null,body:string|null=character?.body??null){try{await api('/equipment',{mainHand,offHand,body,updateBody:true});}catch(e){setMessage((e as Error).message);}}
  const landmarks=world?.tiles.filter(t=>t.place&&(filter==='all'||t.place.type===filter))||[];
  const showWorldActions=!!world&&(!scenePosition||battle.active&&view==='world')&&view!=='battle';
  const alive=character?.life==='alive';
  const atPort=!!ferry&&!!place&&key(place)===key(ferry.mainlandPort);
  const atLanding=!!ferry&&!!place&&key(place)===key(ferry.islandLanding);
  const showFerry=!!ferry&&!battle.active&&(aboard||atPlace&&alive&&(atPort||atLanding));
  const ferryDock=ferry?.phase==='mainland'?ferry.mainlandPort:ferry?.phase==='island'?ferry.islandLanding:null;
  const canBoard=!!ferryDock&&!!own&&key(own)===key(ferryDock);
  const ferryEnabled=connected&&!battle.active&&!own?.moving&&!ferryBusy;
  const enterPlace=atPlace&&alive&&!aboard&&!battle.active
    ? <button className={showFerry?'place-secondary':'primary'} disabled={!canUsePlace} onClick={()=>void api('/scene/enter',{version:routeVersion}).catch(e=>setMessage(e.message))}>{place?.place?.type==='town'?'进入城镇':'进入当前地块'}</button>
    : undefined;
  const nearBattle=!!own&&!!place&&Math.max(Math.abs(own.q-place.q),Math.abs(own.r-place.r),Math.abs(own.q+own.r-place.q-place.r))===1;
  const primaryAction=showFerry&&ferry
    ? aboard
      ? <button className="primary" disabled={!ferryEnabled||!ferryDock} onClick={()=>void ferryCommand('disembark')}>{ferryDock?'在这里下船':'航行中 · 等待靠岸'}</button>
      : ferry.built
        ? <button className="primary" disabled={!ferryEnabled||!canBoard} onClick={()=>void ferryCommand('board')}>{canBoard?'登船':'等待渡船靠岸'}</button>
        : atPort
          ? <button className="primary" aria-expanded={ferryDetails&&worldDetailsExpanded} onClick={()=>{setFerryDetails(!ferryDetails||!worldDetailsExpanded);setWorldDetailsExpanded(true);}}>{ferryDetails&&worldDetailsExpanded?'收起造船详情':'查看造船详情'}</button>
          : enterPlace
    : openBattle&&!isSoul&&!battle.active&&!aboard
      ? <button className="primary" disabled={!connected||!nearBattle||!!own?.moving} onClick={()=>void joinBattle(openBattle.id)}><Swords size={16}/>加入战斗</button>
      : atPlace
        ? enterPlace
        : activePreview?.route?.path.length
          ? <button className="primary" disabled={travelPending} onClick={()=>void travel()}><Footprints size={16}/>{travelPending?'正在出发…':'前往这里'}</button>
          : undefined;
  const secondaryAction=own?.moving&&!battle.active
    ? <button className="place-secondary" onClick={()=>void api('/stop',{}).catch(e=>setMessage(e.message))}><Square size={12}/>停止移动</button>
    : showFerry&&ferry
      ? <>{!aboard&&enterPlace!==primaryAction&&enterPlace}<button className="place-secondary" title="定位小船" aria-label="定位小船" onClick={()=>controls.current?.locate(ferry.built?ferry:ferry.mainlandPort)}><Anchor size={16}/></button></>
      : undefined;
  const componentAction=atPlace&&worldComponent
    ?worldComponent.kind==='loot'?<button className="primary" disabled={!canUsePlace||!alive} onClick={()=>setContainerOpen(worldComponent.id)}>查看遗留物</button>
      :worldComponent.kind==='place'||worldComponent.kind==='resource'?enterPlace:primaryAction
    :primaryAction;
  const routeStatus=followingRoute?'正在前往此地'
    : activePreview?.route?`路线 · ${activePreview.route.path.length} 格`
    : activePreview?.error??(previewId?'正在规划路线…':null);
  const hasWorldDetails=activePreview?.error||selected?.place?.description||
    selected&&!atPlace&&!walkable(selected)||showFerry||openBattle||
    atPlace&&place?.place?.type==='town'&&alive||here.some(p=>p.id!==me.id)||
    alive&&(resource||nearbyLoot.length>0);
  const activeBattleId=battle.active?battle.id:null;
  const battleLoot=useMemo(()=>activeBattleId?loot.filter(item=>item.battleId===activeBattleId):[],[loot,activeBattleId]);
  const battleProps:BattleViewProps|undefined=battle.active?{
    battle,me:me.id,items,revision:hotbarRevision,connected,clock,onChanged:refreshBattle,onMessage:setMessage,
    loot:battleLoot,onLoot:id=>id?setContainerOpen(id):setLootOpen(true)
  }:undefined;
  const battleSceneId=battleProps?.battle.sceneId;
  const battleActor=battleProps?.battle.actors.find(actor=>actor.accountId===me.id);
  const sceneBattle=battleSceneId?battleProps:undefined;
  const renderedScenePosition=battleSceneId&&battleActor&&scenePosition?.sceneId!==battleSceneId
    ? {q:battleActor.q,r:battleActor.r,sceneId:battleSceneId,characterId:me.id}
    : scenePosition;
  return <div className={'game-shell '+(sidebarOpen?'sidebar-open':'sidebar-closed')+(topHidden?' top-hidden':'')}><div className="rotate-prompt"><Compass size={40}/><h2>请横屏游玩</h2><p>旋转手机，展开你的冒险地图</p></div><button className="top-toggle" aria-expanded={!topHidden} aria-label={topHidden?'展开顶部横栏':'收起顶部横栏'} onClick={()=>setTopHidden(v=>!v)}><TreePine size={22}/></button><header className="topbar"><Brand/><div className="world-heading"><span>{battle.active&&view==='battle'?'局部战斗':'世界探索'}</span><span className="divider"/><span>青屿大陆</span><span className="version-badge">探索试玩</span></div><div className="top-actions">{battle.active&&<button className="view-switch" aria-label={view==='world'?'切换到战斗地图':'切换到大世界地图'} onClick={()=>setView(current=>current==='world'?'battle':'world')}>{view==='world'?<Swords size={16}/>:<MapIcon size={16}/ >}{view==='world'?'查看战场':'查看大世界'}</button>}<span className={'connection '+(connected?'':'disconnected')}><i/>{connected?`${online.length} 位旅人在线`:'正在重新连接'}</span><span className="avatar" style={{background:me.color}}>{me.username.slice(0,1)}</span><span className="username">{me.username}</span><button className="icon-button" title="退出登录" aria-label="退出登录" onClick={onLogout}><LogOut size={17}/></button></div></header>
    <div className="workspace"><nav className="rail"><button aria-label={sidebarOpen?'收起侧栏':'展开侧栏'} aria-expanded={sidebarOpen} title={sidebarOpen?'收起侧栏':'展开侧栏'} onClick={()=>setSidebarOpen(open=>!open)}>{sidebarOpen?<PanelLeftClose size={21}/>:<PanelLeftOpen size={21}/>}</button><button className={sidebarOpen&&tab==='places'?'active':''} aria-label="地点目录" title="地点目录" onClick={()=>showTab('places')}><MapIcon size={22}/></button><button className={sidebarOpen&&tab==='players'?'active':''} aria-label="旅人列表" title="旅人列表" onClick={()=>showTab('players')}><Users size={21}/></button><button className={sidebarOpen&&tab==='inventory'?'active':''} aria-label="人物" title="人物" onClick={()=>showTab('inventory')}><Backpack size={21}/></button><button className={'chat-rail-button '+(sidebarOpen&&tab==='chat'?'active':'')} aria-label="聊天室" title="聊天室" onClick={()=>showTab('chat')}><MessageCircle size={21}/>{(chatStatus.publicUnread||chatStatus.contacts.some(c=>c.unread))&&<i className="unread-dot" aria-label="聊天有未读消息"/>}</button><button className={sidebarOpen&&tab==='codex'?'active':''} aria-label="图鉴" title="图鉴" onClick={()=>showTab('codex')}><BookOpen size={21}/></button><div className="rail-spacer"/>{me.admin&&<button title="管理后台" aria-label="管理后台" onClick={()=>setAdmin(true)}><Shield size={21}/></button>}<span className="rail-leaf"><Leaf size={18}/></span></nav>
    <aside className={'sidebar '+(tab==='chat'?'chat-sidebar':'')+(sidebarOpen?'':' collapsed')} aria-hidden={!sidebarOpen}>{tab==='codex'?<GameCodex world={world}/>:tab==='chat'?<ChatPanel me={me} status={chatStatus} players={players} revision={chatRevision} refresh={refreshChat} mode={chatMode} onMode={setChatMode} peer={chatPeer} onPeer={setChatPeer}/>:<><div className="sidebar-heading"><span className="tiny-label">{tab==='places'?'THE WORLD AT A GLANCE':tab==='inventory'?'YOUR ADVENTURER':'FELLOW TRAVELERS'}</span><h2>{tab==='places'?'大陆手记':tab==='inventory'?'人物':'同行旅人'}<span>{tab==='places'?world?.tiles.filter(t=>t.place).length||'—':tab==='inventory'?items.filter(i=>i.quantity>0).length:players.length}</span></h2><p>{tab==='places'?'选一处目的地，让好奇心带路。':tab==='inventory'?'准备行装，踏上下一段旅途。':'每个名字，都是另一段旅途。'}</p></div>
      {tab==='places'?<><div className="filters">{[['all','全部'],['town','城镇'],['village','村庄'],['ruin','遗迹'],['port','港口'],['landing','停靠点']].map(([id,label])=><button key={id} onClick={()=>setFilter(id)} className={filter===id?'active':''}>{label}</button>)}</div><div className="place-list">{landmarks.map(t=><button className={'place-row '+(selected&&key(selected)===key(t)?'selected':'')} key={key(t)} onClick={()=>{choose(t);controls.current?.locate(t);}}><span className={'place-icon '+t.place!.type}>{t.place!.type==='ruin'?<Landmark size={20}/>:<Home size={20}/>}</span><span><strong>{t.place!.name}{key(t)===key(world!.spawn)&&<em>起点</em>}</strong><small>{placeNames[t.place!.type]} <span>·</span> {t.q}, {t.r}</small></span><ChevronRight size={15}/></button>)}</div><div className="sidebar-note"><Compass size={23}/><div><strong>不必赶路</strong><p>这里没有行动点限制。<br/>沿途的风景，也值得停留。</p></div></div></>:tab==='inventory'?<CharacterPanel corpses={corpses} onLoot={id=>setContainerOpen(id)} onDrop={()=>setDropOpen(true)} character={character} items={items} locked={battle.active||character?.life!=='alive'} onEquip={equip}/>:<div className="place-list">{players.filter(p=>p.kind!=='monster').map(p=><div className="traveler-row" key={p.id}><button className="place-row" onClick={()=>{const t=world?.tiles.find(t=>key(t)===key(p));if(t)choose(t);controls.current?.locate(p);}}><span className="avatar" style={{background:p.online?p.color:'#a7ada8'}}>{p.username.slice(0,1)}</span><span><strong>{p.username}{p.id===me.id&&<em>你</em>}</strong><small>{p.online?'在线':'离线'} · {p.q}, {p.r}</small></span><i className={'status-dot '+(p.online?'online':'')}/></button>{p.id!==me.id&&<button className="traveler-chat icon-button" title={`私聊 ${p.username}`} aria-label={`私聊 ${p.username}`} onClick={()=>openPrivate(p.id)}><MessageCircle size={15}/></button>}</div>)}{!players.length&&<p className="empty">连接后将在这里显示旅人。</p>}</div>}
    </>}</aside>
    <main className={'map-surface '+(battle.active&&view==='battle'?'show-battle':'')}>{world&&<MapView blockedEdges={blockedEdges} loot={loot} deathMark={character?.life==='soul'&&character.deathQ!=null&&character.deathR!=null?{q:character.deathQ,r:character.deathR}:null} active={(!scenePosition||battle.active&&view==='world')&&!(battle.active&&view==='battle')} ferry={ferry} world={world} battles={battles} emotes={scenePosition?[]:emotes} resources={resources} actions={actions} clockOffset={offset.current} players={players} me={me.id} selected={selected} interactions={worldInteractions} activeComponent={worldComponent?.id??null} route={activePreview?.route?.path??(own?.moving?route:emptyRoute)} onSelect={choose} onZoom={setZoom} controls={controls}/>}
      {battleEnded&&!battle.active&&<div className="battle-result"><strong>已返回大世界</strong><button onClick={()=>{setLootOpen(true);setBattleEnded(false);}}>查看战利品</button><button onClick={()=>setBattleEnded(false)}>关闭</button></div>}
      {!battle.active&&<ProductionPanel state={production} items={items} facility={facility} onFacility={setFacility} disabled={!connected||!!own?.moving||sceneMoving||character?.life!=='alive'} battle={false} clock={clock} onMessage={setMessage}/>}
      <SocialPanel carried={corpses} me={me.id} position={scenePosition} residents={sceneResidents} items={items} battle={battle.active} connected={connected} onMessage={setMessage}/>
      {!battle.active&&<CombatLog revision={hotbarRevision}/>}
      {renderedScenePosition&&<SceneView key={renderedScenePosition.sceneId} position={renderedScenePosition}
        battleView={sceneBattle} hidden={battle.active&&(view==='world'||!sceneBattle)} worldName={world?.name??'青屿大陆'}
        production={production} clock={clock} emotes={emotes} lifeBusy={lifeBusy} onCancelRest={()=>void lifeAction('cancel')} doors={sceneDoors} service={sceneService} onShop={()=>setShopOpen(true)} onRest={()=>void lifeAction('rest')} objects={sceneObjects} onFacility={setFacility} residents={sceneResidents} players={players} loot={sceneLoot} onLoot={setContainerOpen} moving={sceneMoving} connected={connected} me={me.id} onMessage={setMessage} onBattle={id=>void startBattle(id)}/>}
      {battleProps&&view==='battle'&&!battleProps.battle.sceneId&&<BattleView key={battleProps.battle.id} {...battleProps}/>}
      {battle.active&&view==='world'&&<div className="battle-world-note" role="status">角色正在战斗中 · 大世界仅供查看</div>}
      {loading&&<div className="map-loading"><LoaderCircle className="spin"/>正在展开大陆地图…</div>}
      {!world&&!loading&&<div className="map-loading"><p>{message||'地图暂时无法载入'}</p><button className="primary" onClick={()=>{setLoading(true);reload().catch(e=>setMessage(e.message)).finally(()=>setLoading(false));}}>重新加载</button></div>}
      {(!scenePosition||battle.active&&view==='world')&&<MapChrome title={world?.name??'青屿大陆'} subtitle="全境地图 · 自由探索" zoom={zoom}
        onZoom={factor=>controls.current?.zoom(factor)} onFit={()=>controls.current?.fit()} onLocate={()=>controls.current?.locate(own||me)} onMessage={setMessage}/>}
      {character&&<LifePanel character={character} clock={clock} busy={lifeBusy} onAction={a=>void lifeAction(a)} onLocate={()=>{if(character.deathQ!=null&&character.deathR!=null){const t=world?.tiles.find(t=>t.q===character.deathQ&&t.r===character.deathR);if(t){choose(t);controls.current?.locate(t);}}}}/>}
      {shopOpen&&character&&!battle.active&&<ShopPanel character={character} onClose={()=>setShopOpen(false)}/>}
      <StatusPanel revision={hotbarRevision} compact/>
      {lootOpen&&world&&<LootPanel version={world.version} battle={battle} me={me.id} clock={clock} revision={hotbarRevision} onOpenContainer={id=>{setLootOpen(false);setContainerOpen(id);}} onClose={()=>setLootOpen(false)}/>}
      {containerOpen&&world&&character&&<ContainerPage key={containerOpen} version={world.version} battle={battle} me={me.id} clock={clock} connected={connected&&!aboard&&!own?.moving&&!sceneMoving&&character.life==='alive'} position={character} scenePosition={scenePosition} containerId={containerOpen} onClose={()=>setContainerOpen(null)} onChanged={async()=>{await refreshBattle();setItems(await api<Item[]>('/inventory'));}}/>}
      {dropOpen&&world&&character&&<DropPanel items={items} version={world.version} connected={connected&&!aboard&&!own?.moving&&character.life==='alive'} onClose={()=>setDropOpen(false)} onChanged={async()=>{await refreshBattle();setItems(await api<Item[]>('/inventory'));}}/>}
      {showWorldActions&&(aboard||place&&(selected||!battle.active&&(alive||own?.moving)))&&<PlaceActions
        className="place-actions-world"
        title={aboard?'望潮渡船':worldComponent?.label??place?.place?.name??(place?terrainNames[place.terrain]:'当前位置')}
        subtitle={aboard?'你正在船上':routeStatus??(place?`${place.place?placeNames[place.place.type]:terrainNames[place.terrain]} · ${place.q}, ${place.r}`:undefined)}
        detailsExpanded={worldDetailsExpanded}
        onToggleDetails={()=>setWorldDetailsExpanded(open=>!open)}
        onClose={selected&&!aboard?()=>{setSelected(null);setActiveComponent(null);setFerryDetails(false);}:undefined}
        primary={componentAction}
        secondary={componentAction===secondaryAction?undefined:secondaryAction}>
        {!aboard&&<PlaceComponents items={worldCellComponents} selected={worldComponent?.id} onSelect={id=>setActiveComponent(id)}/>}
        {hasWorldDetails&&<>
        {activePreview?.error&&<button onClick={()=>setPreviewRetry(i=>i+1)}>重新预览</button>}
        {!aboard&&selected?.place?.description&&<p>{selected.place.description}</p>}
        {!aboard&&selected&&!atPlace&&!walkable(selected)&&!openBattle&&<p>此格无法通行</p>}
        {showFerry&&ferry&&!ferry.built&&!ferryDetails&&<p>{atPort
          ? `造船进度 · 木头 ${ferry.wood}/${ferry.woodNeeded} · 石头 ${ferry.stone}/${ferry.stoneNeeded}`
          : '望潮港的渡船尚未建成'}</p>}
        {showFerry&&ferry&&(ferry.built||atPort&&ferryDetails)&&<FerryPanel ferry={ferry} items={items} clock={clock}
          enabled={canUsePlace&&ferryEnabled} onContribute={(item,quantity)=>void ferryCommand('contribute',{item,quantity})}/>}
        {!aboard&&place?.place?.type==='town'&&alive&&atPlace&&!battle.active&&<div className="town-actions">
          <button disabled={lifeBusy||!canUsePlace} onClick={()=>void lifeAction('bind')}>设为回归城市</button>
          {character.timerKind==='rest'&&<p>休整中 · {Math.max(0,Math.ceil((character.timerEnd-clock)/1000))} 秒 <button disabled={lifeBusy} onClick={()=>void lifeAction('cancel')}>取消</button></p>}
        </div>}
        {!aboard&&openBattle&&!isSoul&&<p>{openBattle.participants} 人在战场内 · 从相邻格加入战斗</p>}
        {!aboard&&!openBattle&&atPlace&&here.filter(p=>p.id!==me.id).map(p=><div className="world-place-occupant" key={p.id}>
          <span>{p.username}{p.online?'':'（离线）'}</span>
          {p.life==='down'&&p.kind!=='monster'&&<button disabled={lifeBusy||!canUsePlace} onClick={()=>void lifeAction('rescue',p.id)}>救起</button>}
          <button disabled={!canUsePlace} onClick={()=>void startBattle(p.id)}>发起战斗</button>
        </div>)}
        {!aboard&&!atPlace&&here.length>0&&<p>{here.map(p=>p.username+(p.online?'':'（离线）')).join('、')}</p>}
        {!aboard&&alive&&resource&&<p>{resource.kind==='stone'?'此地可能有散落石头':'此地有森林资源'} · 进入地块后可查看</p>}
        {!aboard&&alive&&nearbyLoot.length>0&&<div className="loot-world-entry">
          <span>此处遗留 · {nearbyLoot.length} 个来源</span>
          <button disabled={!canUsePlace} onClick={()=>setLootOpen(true)}>遗留物</button>
        </div>}
        </>}
      </PlaceActions>}
      {world&&<EmotePicker disabled={!connected||battle.active||aboard||character?.life!=='alive'} onSend={async code=>{try{await api('/emote',{code,version:world.version});}catch(e){setMessage((e as Error).message);}}}/>}
      {(!scenePosition||battle.active&&view==='world')&&<div className="map-bottom"><div className="legend"><span><i style={{background:'#abc781'}}/>平原</span><span><i style={{background:'#628b63'}}/>森林</span><span><i style={{background:'#909d8b'}}/>山地</span><span><i style={{background:'#80b7c0'}}/>河流</span><span><i style={{background:'#dbca91'}}/>道路</span></div><div className="position-pill"><LocateFixed size={14}/>{aboard?'乘船中':'当前位置'} <b>{own?.q??me.q}, {own?.r??me.r}</b></div></div>}
      {message&&world&&<div className="toast" role="status">{message}<button className="icon-button" aria-label="关闭提示" onClick={()=>setMessage('')}><X size={14}/></button></div>}
    </main></div><footer className="statusbar"><span><i className="status-dot online"/>同一片大陆，共同探索</span><span>拖动平移 <b>·</b> 滚轮缩放 <b>·</b> 点击地块查看与前往</span><span>青屿 / 探索版 0.1</span></footer>
    {admin&&<Admin onClose={()=>setAdmin(false)} onRegenerate={reload}/>}
  </div>;
}
