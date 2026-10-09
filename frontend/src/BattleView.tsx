import type {LootContainer} from './types';
import {bindMapGestures} from './mapGestures';
import {MapTools} from './MapTools';
import {useEffect,useMemo,useRef,useState} from 'react';
import {SkipForward,LogOut,LocateFixed,Plus,Minus,Maximize,X,ShieldOff} from 'lucide-react';
import {BattleInventory} from './BattleInventory';
import {Hotbar} from './Hotbar';
import {itemSupported,targetReason} from './battleInventoryRules';
import {attackCells,hexDistance,retainsAttack} from './combatRules';
import {api} from './api';
import type {BattleState,Hex,Item} from './types';
const SIZE=36;
const xy=(h:Hex)=>({x:1.5*SIZE*h.q,y:Math.sqrt(3)*SIZE*(h.r+h.q/2)});
const distance=(a:Hex,b:Hex)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
const sides=(h:Hex,r:number)=>[h.q,h.q+h.r,h.r,-h.q,-h.q-h.r,-h.r].flatMap((n,i)=>n===r?[i]:[]);
function polygon(h:Hex,r=SIZE-1){const p=xy(h);return Array.from({length:6},(_,i)=>{const a=i*Math.PI/3;return `${p.x+r*Math.cos(a)},${p.y+r*Math.sin(a)}`;}).join(' ');}
function tiles(radius:number){const result:Hex[]=[];for(let r=-radius;r<=radius;r++)for(let q=-radius;q<=radius;q++)if(distance({q:0,r:0},{q,r})<=radius)result.push({q,r});return result;}
function Sprite({color='#b87855',monster=false,species=null}:{color?:string;monster?:boolean;species?:string|null}){if(monster)return species==='claw'?<g shapeRendering="crispEdges"><ellipse cy="14" rx="16" ry="5" fill="#384636" opacity=".3"/><rect x="-13" y="-8" width="26" height="15" fill="#5c6b3d"/><rect x="-8" y="-4" width="16" height="10" fill="#77854e"/><rect x="-16" y="-9" width="6" height="10" fill="#4c5934"/><rect x="10" y="-9" width="6" height="10" fill="#4c5934"/><rect x="-15" y="-8" width="4" height="2" fill="#8fa35c"/><rect x="11" y="-6" width="3" height="2" fill="#8fa35c"/><rect x="-18" y="-8" width="5" height="16" fill="#4c5934"/><rect x="13" y="-8" width="5" height="16" fill="#4c5934"/><rect x="-11" y="6" width="8" height="9" fill="#3f4a30"/><rect x="3" y="6" width="8" height="9" fill="#3f4a30"/><rect x="-13" y="13" width="11" height="3" fill="#2f3826"/><rect x="2" y="13" width="11" height="3" fill="#2f3826"/><rect x="-18" y="7" width="2" height="5" fill="#ede1bc"/><rect x="-16" y="7" width="2" height="5" fill="#ede1bc"/><rect x="-14" y="7" width="2" height="5" fill="#ede1bc"/><rect x="12" y="7" width="2" height="5" fill="#ede1bc"/><rect x="14" y="7" width="2" height="5" fill="#ede1bc"/><rect x="16" y="7" width="2" height="5" fill="#ede1bc"/><rect x="-7" y="-19" width="14" height="10" fill="#6c7a48"/><rect x="-9" y="-23" width="4" height="5" fill="#4c5934"/><rect x="5" y="-23" width="4" height="5" fill="#4c5934"/><rect x="-5" y="-16" width="3" height="3" fill="#ffce69"/><rect x="2" y="-16" width="3" height="3" fill="#ffce69"/><rect x="-5" y="-11" width="10" height="2" fill="#3a2a22"/><rect x="-4" y="-10" width="2" height="3" fill="#ede1bc"/><rect x="2" y="-10" width="2" height="3" fill="#ede1bc"/></g>:<g shapeRendering="crispEdges"><ellipse cy="13" rx="18" ry="5" fill="#384636" opacity=".3"/><path d="M-13-10h26v23h-26M-18-8h7v18h-7M11-8h7v18h-7" fill="#a83c34"/><path d="M-10-26h20v18h-20" fill="#c96a56"/><path d="M-14-33h6v12h-6M8-33h6v12H8" fill="#ede1bc"/><path d="M-6-21h4v4h-4M3-21h4v4H3" fill="#ffce69"/><path d="M-11 10h8v7h-8M3 10h8v7H3" fill="#4a3a31"/></g>;return <g shapeRendering="crispEdges"><ellipse cy="13" rx="15" ry="5" fill="#253b38" opacity=".23"/><path d="M-10-9h20v22H-10z" fill="#34483f"/><path d="M-9-8h18v17H-9z" fill={color}/><path d="M-6-7h4V6h-4z" fill="#ffffff" opacity=".25"/><path d="M-9 8h7v8h-7zm12 0h7v8H3z" fill="#394039"/><path d="M-8-25H8v18H-8z" fill="#efd4a4"/><path d="M-9-27H8v5H-9zM-11-23h6v9h-6zM5-23h5v5H5z" fill="#67513d"/><path d="M-3-18h2v3h-2zm7 0h2v3H4z" fill="#3b4037"/><path d="M-13-6h5v11h-5z" fill="#efd4a4"/><path d="M12-14h3v21h-3z" fill="#e9e8cb"/><path d="M9 4h9v3H9z" fill="#a58a51"/><path d="M-8 3H8v3H-8z" fill="#795c3d"/></g>;}
function Tree(){return <g shapeRendering="crispEdges"><ellipse cy="15" rx="23" ry="7" fill="#486448" opacity=".22"/><path d="M-4-6H4v24H-4z" fill="#76634a"/><path d="M-2-3H2v20H-2z" fill="#ab8857"/><path d="M-20-31h40v25h-40zM-14-43h28v44h-28zM-25-23h50v13h-50z" fill="#486c53"/><path d="M-20-30h33v20h-33zM-12-42h25v28h-25zM-25-21h35v10h-35z" fill="#6d935e"/><path d="M-12-41H5v8h-17zM-20-28h12v10h-12zM6-30h9v6H6z" fill="#98b777"/><path d="M-7-23H5v9H-7zM10-14h7v5h-7z" fill="#7da464"/><path d="M-15-9h8v4h-8z" fill="#afbf7c"/></g>;}
function Rock(){return <g shapeRendering="crispEdges"><ellipse cy="10" rx="19" ry="5" fill="#516149" opacity=".2"/><path d="M-17-4h8v-9H7v5h9v19h-33z" fill="#788778"/><path d="M-15-3h8v-8H5v5h8v9h-28z" fill="#b0b6a0"/><path d="M-6-10H4v7H-6z" fill="#d0d0b6"/><path d="M-14 5H3v5h-17z" fill="#95a18a"/><path d="M8 5h10v8H8z" fill="#6e865d"/></g>;}
export function BattleView({loot,onLoot,battle,me,items,revision,connected,clock,onChanged,onMessage}:{loot:LootContainer[];onLoot:(id?:string)=>void;battle:Extract<BattleState,{active:true}>;me:string;items:Item[];revision:number;connected:boolean;clock:number;onChanged:()=>Promise<void>;onMessage:(message:string)=>void}){
 const [hover,setHover]=useState<Hex|null>(null);
 const [inventoryOpen,setInventoryOpen]=useState(false);
 const [inventoryTab,setInventoryTab]=useState<'equipment'|'items'>('equipment');
 const [targetItemCode,setTargetItemCode]=useState<string|null>(null),[lastItemCode,setLastItemCode]=useState<string|null>(null);
 const commandPending=useRef(false),itemTurn=useRef(''),returnToInventory=useRef(false);
 const [mode,setMode]=useState('inspect');
 const [selected,setSelected]=useState<Hex|null>(null),[movePreview,setMovePreview]=useState<Hex|null>(null),[busy,setBusy]=useState(false),[zoom,setZoom]=useState(()=>window.matchMedia('(max-height:600px) and (orientation:landscape)').matches?1.7:1),[center,setCenter]=useState({x:0,y:0});
 const board=useRef<SVGSVGElement|null>(null),camera=useRef({zoom,center});camera.current={zoom,center};
 const tap=useRef<(x:number,y:number)=>void>(()=>{});
 useEffect(()=>{const node=board.current;if(!node)return;return bindMapGestures(node,{
  pan:(dx,dy)=>{const c=camera.current,scale=890/c.zoom/Math.min(node.clientWidth,node.clientHeight);const next={x:c.center.x-dx*scale,y:c.center.y-dy*scale};camera.current={...c,center:next};setCenter(next);},
  zoom:(factor,x,y)=>{const c=camera.current,z=Math.max(.65,Math.min(3.5,c.zoom*factor)),size=Math.min(node.clientWidth,node.clientHeight),change=(890/c.zoom-890/z)/size;const next={x:c.center.x+(x-node.clientWidth/2)*change,y:c.center.y+(y-node.clientHeight/2)*change};camera.current={zoom:z,center:next};setZoom(z);setCenter(next);},
  tap:(x,y)=>tap.current(x,y)
 });},[]);
 const own=battle.actors.find(a=>a.accountId===me),current=battle.actors.find(a=>a.accountId===battle.turnAccountId);
 const target=selected?battle.actors.find(a=>a.q===selected.q&&a.r===selected.r):undefined;
 const myTurn=battle.turnAccountId===me&&!!own&&own.entryRound<=battle.round;
 const reachable=useMemo(()=>{
   const out=new Map<string,Hex[]>();if(!own||!myTurn)return out;
   const occupied=new Set(battle.actors.filter(a=>a.accountId!==me).map(a=>`${a.q},${a.r}`));
   const queue:Hex[]=[own];out.set(`${own.q},${own.r}`,[]);
   for(let i=0;i<queue.length;i++){const from=queue[i],path=out.get(`${from.q},${from.r}`)!;if(path.length>=battle.turnPoints)continue;
     for(const [dq,dr] of [[1,0],[-1,0],[0,1],[0,-1],[1,-1],[-1,1]]){const next={q:from.q+dq,r:from.r+dr},k=`${next.q},${next.r}`;
       if(distance({q:0,r:0},next)<=battle.radius&&!occupied.has(k)&&!out.has(k)){out.set(k,[...path,next]);queue.push(next);}}
   }out.delete(`${own.q},${own.r}`);return out;
 },[battle.actors,battle.turnPoints,battle.radius,myTurn,me]);
 useEffect(()=>{setMode('inspect');setSelected(null);setMovePreview(null);},[battle.turnAccountId,battle.round]);
 useEffect(()=>{if(movePreview&&!reachable.has(`${movePreview.q},${movePreview.r}`)){setMovePreview(null);setSelected(null);}},[movePreview,reachable]);
 const [visual,setVisual]=useState<Record<string,Hex>>(()=>Object.fromEntries(battle.actors.map(a=>[a.accountId,a])));
 const paths=useRef(new Map<string,Hex[]>()),seenEvent=useRef(Math.max(0,...battle.events.map(e=>e.id)));
 useEffect(()=>{
   const fresh=battle.events.filter(e=>e.id>seenEvent.current);seenEvent.current=Math.max(seenEvent.current,...battle.events.map(e=>e.id));
   for(const a of battle.actors){const steps=fresh.filter(e=>e.kind==='move'&&e.actorId===a.accountId).map(e=>({q:e.q!,r:e.r!}));if(steps.length)paths.current.set(a.accountId,[...(paths.current.get(a.accountId)||[]),...steps]);else if(!paths.current.has(a.accountId))setVisual(v=>({...v,[a.accountId]:a}));}
 },[battle.actors,battle.events]);
 useEffect(()=>{const timer=setInterval(()=>{for(const [id,path] of paths.current){const next=path.shift();if(next)setVisual(v=>({...v,[id]:next}));if(!path.length)paths.current.delete(id);}},110);return()=>clearInterval(timer);},[]);
 const enabled=connected&&myTurn&&!busy&&own?.character.life==='alive';
 const targetItem=items.find(i=>i.code===targetItemCode&&i.quantity>0);
 function cancelItemTarget(){if(commandPending.current)return;setTargetItemCode(null);setMode('inspect');setSelected(null);setInventoryTab('items');setInventoryOpen(returnToInventory.current);}
 useEffect(()=>{if(!targetItemCode||commandPending.current)return;const reason=!connected?'连接中断，已取消道具选取':!myTurn||itemTurn.current!==`${battle.round}:${battle.turnAccountId}`?'回合已结束，已取消道具选取':own?.character.life!=='alive'?'当前无法行动':!targetItem?'道具已耗尽':battle.turnPoints<targetItem.attack_cost?'行动点不足':'';if(reason){setTargetItemCode(null);setMode('inspect');setSelected(null);setInventoryTab('items');setInventoryOpen(returnToInventory.current);onMessage(reason);}},[targetItemCode,targetItem,connected,myTurn,own?.character.life,battle.turnPoints,battle.round,battle.turnAccountId,busy,onMessage]);
 useEffect(()=>{if(!targetItemCode)return;const cancel=(e:KeyboardEvent)=>{if(e.key==='Escape'){e.preventDefault();cancelItemTarget();}};window.addEventListener('keydown',cancel);return()=>window.removeEventListener('keydown',cancel);},[targetItemCode]);
 const weapon=own?.weapon;
 const canAttack=!!own?.actions.includes('attack');
 useEffect(()=>{setMode('inspect');setMovePreview(null);setSelected(null);},[own?.character.weapon,own?.character.offhand]);
 const shapeAt=(h:Hex)=>own&&weapon?attackCells(weapon,own,h).filter(c=>hexDistance({q:0,r:0},c)<=battle.radius):[];
 const selectable=(h:Hex)=>canAttack&&!!own&&!!weapon&&attackCells(weapon,own,h).length>0&&attackCells(weapon,own,h).every(c=>hexDistance({q:0,r:0},c)<=battle.radius);
 const pendingAttack=battle.intents.find(i=>i.attackerId===me);
 const previewPath=movePreview?reachable.get(`${movePreview.q},${movePreview.r}`):undefined;
 const ownEdges=own?sides(own,battle.radius):[];
 const recentAttack=[...battle.events].reverse().find(e=>e.kind==='attack'&&clock-e.happenedAt<1250);
 const notice=[...battle.events].reverse().find(e=>clock-e.happenedAt<5000&&['withdraw-interrupted','withdraw-blocked','attack','miss'].includes(e.kind));
 const noticeText=notice?(notice.kind==='withdraw-interrupted'?`${battle.actors.find(a=>a.accountId===notice.actorId)?.username||'旅人'} 的撤离被攻击打断`:notice.kind==='withdraw-blocked'?'目的地暂时无法进入，撤离已取消':notice.kind==='miss'?'预设攻击落空':'攻击已执行'):'';
 const seconds=Math.max(0,Math.ceil((battle.turnDeadline-clock)/1000));
 const span=890/zoom;
 async function command(path:string,body:object){if(commandPending.current)return;commandPending.current=true;setBusy(true);try{if(path==='rescue')await api('/character/action',{action:'rescue',...body});else await api('/battle/'+path,path==='equipment'||path==='bandage'?body:{battleId:battle.id,...body});await onChanged();return true;}catch(e){onMessage((e as Error).message);return false;}finally{commandPending.current=false;setBusy(false);}}
 function useItem(item:Item){if(!enabled||!own||!itemSupported(item)||battle.turnPoints<item.attack_cost)return;setLastItemCode(item.code);if(item.target_mode==='self'){const reason=targetReason(item,own,own);if(reason){onMessage(reason);return;}void command(item.use_action!,{targetId:own.accountId}).then(ok=>{if(ok)setInventoryOpen(false);});return;}setInventoryOpen(false);itemTurn.current=`${battle.round}:${battle.turnAccountId}`;setTargetItemCode(item.code);setMode('item');setSelected(null);setMovePreview(null);setCenter(xy(own));}
 function choose(h:Hex){
   if(mode==='item'&&targetItem&&own){if(!enabled||commandPending.current)return;const patient=battle.actors.find(a=>a.q===h.q&&a.r===h.r);const reason=targetReason(targetItem,own,patient);if(reason){onMessage(reason);return;}void command(targetItem.use_action!,{targetId:patient!.accountId}).then(ok=>{if(ok){setTargetItemCode(null);setMode('inspect');setSelected(null);}});return;}
   if(mode==='move'){
     if(movePreview){
       if(enabled&&movePreview.q===h.q&&movePreview.r===h.r&&reachable.has(`${h.q},${h.r}`)){
         setMovePreview(null);setSelected(h);void command('step',h);
       }else{setMovePreview(null);setSelected(null);}
       return;
     }
     setSelected(h);
     if(enabled&&reachable.has(`${h.q},${h.r}`))setMovePreview(h);
     return;
   }
   setSelected(h);
   if(!enabled)return;
   if(mode==='rescue'&&own){const patient=battle.actors.find(a=>a.q===h.q&&a.r===h.r);if(patient?.character.life==='down'&&patient.character.kind==='player'&&distance(own,patient)===1)void command('rescue',{targetId:patient.accountId}).then(ok=>{if(ok)setMode('inspect');});}
   if(mode==='attack'&&own&&selectable(h)&&battle.turnPoints>=own.weapon.cost){void command('attack',h).then(ok=>{if(ok)setMode('inspect');});}
 }
 tap.current=(x,y)=>{const node=board.current;if(!node)return;const c=camera.current,scale=890/c.zoom/Math.min(node.clientWidth,node.clientHeight),wx=c.center.x+(x-node.clientWidth/2)*scale,wy=c.center.y+(y-node.clientHeight/2)*scale,qf=wx/(1.5*SIZE),rf=wy/(Math.sqrt(3)*SIZE)-qf/2;
 let q=Math.round(qf),r=Math.round(rf),v=Math.round(-qf-rf);const dq=Math.abs(q-qf),dr=Math.abs(r-rf),dv=Math.abs(v+qf+rf);if(dq>dr&&dq>dv)q=-r-v;else if(dr>dv)r=-q-v;if(distance({q:0,r:0},{q,r})<=battle.radius)choose({q,r});};
 const moveTriggers=!!pendingAttack&&!!previewPath&&previewPath.some(h=>!retainsAttack(pendingAttack,h));
 function locate(h:Hex){setSelected(h);setCenter(xy(h));setZoom(1.5);}
 return <section className="battle-surface" aria-label="局部战场">
 <button className="loot-battle-open" disabled={!connected||busy||own?.character.life!=='alive'} onClick={()=>{setMode('inspect');setTargetItemCode(null);setInventoryOpen(false);onLoot();}}>尸体与遗留物 · {loot.length}</button>
 <div className="battle-grain" aria-hidden="true"/>
 {mode==='inspect'&&selected&&<div className="battle-cell-loot">{loot.filter(x=>x.battleQ===selected.q&&x.battleR===selected.r).map(x=><button key={x.id} disabled={!connected||busy} onClick={()=>onLoot(x.id)}>查看{x.name}{own?.q===x.battleQ&&own?.r===x.battleR?'':' · 需移动到此格'}</button>)}</div>}
 <div className="initiative-shell"><div className="initiative-caption"><span>第 {battle.round} 轮</span><b>{myTurn?'轮到你行动':`${current?.username||'旅人'} 的回合`}</b><span>{seconds} 秒 · {battle.turnPoints}/6 行动点</span></div>
 <div className="initiative-strip" aria-label="本轮行动顺序">{battle.actors.map((a,i)=>{const waiting=a.entryRound>battle.round,active=a.accountId===battle.turnAccountId,acted=!waiting&&a.initiative<(current?.initiative??0);return <button key={a.accountId} className={'initiative-portrait '+(active?'current ':'')+(acted?'acted ':'')+(!a.online?'offline ':'')+(waiting?'reinforcement':'')} onClick={()=>locate(a)} title={`定位 ${a.username} · 先攻 ${a.initiativeRoll}＋${a.initiativeScore-a.initiativeRoll}＝${a.initiativeScore}`} aria-label={`定位 ${a.username}${active?'，当前回合':''}`}><span className="portrait-order">{i+1} · ⚄{a.initiativeScore}</span><svg viewBox="-25 -33 50 54"><Sprite color={a.color} monster={a.character.kind==='monster'} species={a.character.species}/></svg><strong>{a.username}{a.accountId===me?' · 你':''}</strong><small>{a.character.life==='down'?'倒地':a.withdrawDirection!=null?'正在撤离':waiting?'下轮加入':!a.online?'离线':active?'行动中':acted?'已行动':'待行动'}</small></button>;})}</div></div>
 <svg ref={board} className="battle-board" viewBox={`${center.x-span/2} ${center.y-span/2} ${span} ${span}`} aria-label="六边形战场，外圈是撤离区域" onWheel={e=>setZoom(z=>Math.max(.65,Math.min(3.5,z*(e.deltaY>0?.9:1.1))))}>

 {tiles(battle.radius).map(h=>{const p=xy(h),edge=sides(h,battle.radius),outer=edge.length>0,blocked=outer&&edge.every(d=>!battle.edges[d].walkable),chosen=selected?.q===h.q&&selected.r===h.r,marked=battle.intents.some(i=>i.cells.some(c=>c.q===h.q&&c.r===h.r)),near=selectable(h),n=((Math.imul(h.q+713+battle.worldQ,374761393)^Math.imul(h.r+997+battle.worldR,668265263))>>>0),occupied=battle.actors.some(a=>a.q===h.q&&a.r===h.r);return <g key={`${h.q},${h.r}`} className={'battle-cell '+(outer?'outer ':'')+(blocked?'blocked ':'')+(mode==='move'&&reachable.has(`${h.q},${h.r}`)?'reachable ':'')+(mode==='attack'&&near&&enabled?'attackable ':'')+(chosen?'selected ':'')+(marked?'marked':'')}>
 <polygon className="cell-ground" points={polygon(h)} fill={['#b9cc8b','#b5c986','#c0d090','#bacb88'][n%4]} onMouseEnter={()=>setHover(h)} onMouseLeave={()=>setHover(null)} tabIndex={0} role="button" aria-label={`战场格 ${h.q}, ${h.r}${outer?'，撤离区':''}`} onKeyDown={e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();choose(h);}}}/>
 {mode==='move'&&reachable.has(`${h.q},${h.r}`)&&<polygon className="move-range" points={polygon(h,SIZE-3)} fill="white" fillOpacity={.05+.23*reachable.get(`${h.q},${h.r}`)!.length/Math.max(1,battle.turnPoints)} stroke="white" strokeOpacity={.2+.65*reachable.get(`${h.q},${h.r}`)!.length/Math.max(1,battle.turnPoints)} strokeWidth="2" pointerEvents="none"/>}
 {mode==='attack'&&near&&enabled&&<polygon className="attack-range" points={polygon(h,SIZE-4)} pointerEvents="none"/>}
 {mode==='item'&&targetItem&&own&&distance(own,h)<=(targetItem.use_range??0)&&<polygon className={targetReason(targetItem,own,battle.actors.find(a=>a.q===h.q&&a.r===h.r))?'item-target-range':'item-target-valid'} points={polygon(h,SIZE-4)} pointerEvents="none"/>}
 {chosen&&<polygon className="selected-ring" points={polygon(h,SIZE-2)} pointerEvents="none"/>}
 <g pointerEvents="none" opacity={outer?.42:1} transform={`translate(${p.x} ${p.y})`}><path d="M-19 12h3v-5m0 5h4v-3M12-18h3v-4m0 4h4v-2" stroke="#72965e" strokeWidth="2" fill="none"/><path d="M-10-15h5v2h-5M15 15h4v2h-4" fill="#e0dda0"/>{!outer&&n%9===0?<g opacity={occupied?.35:1} transform="translate(10 -2) scale(.9)"><Tree/></g>:!outer&&n%7===0?<g transform="translate(12 5) scale(.85)"><Rock/></g>:null}{outer&&<text className="edge-cell-label" y="5">{edge.map(d=>battle.edges[d].walkable?['→','↘','↙','←','↖','↗'][d]:'×').join(' ')}</text>}</g>
 {marked&&<polygon className="battle-intent-ring" points={polygon(h,SIZE-4)} pointerEvents="none"/>}</g>;})}
 {mode==='attack'&&hover&&selectable(hover)&&shapeAt(hover).map(h=><polygon key={h.q+','+h.r} className="attack-preview" points={polygon(h,SIZE-5)}/>)}
 {previewPath&&own&&<g className="move-preview-route" pointerEvents="none"><polyline className="move-preview-shadow" points={[own,...previewPath].map(h=>{const p=xy(h);return `${p.x},${p.y}`;}).join(' ')}/><polyline className="move-preview-line" points={[own,...previewPath].map(h=>{const p=xy(h);return `${p.x},${p.y}`;}).join(' ')}/>{previewPath.map((h,i)=><polygon key={`${h.q},${h.r}`} className={'move-preview-step '+(i===previewPath.length-1?'destination':'')} points={polygon(h,SIZE-7)}/>)}</g>}
 {battle.edges.map(e=>{const a=e.direction*Math.PI/3,p={x:Math.cos(a)*405,y:Math.sin(a)*405};return <g key={e.direction} transform={`translate(${p.x} ${p.y})`} className={'battle-side-label '+(!e.walkable?'blocked':'')} pointerEvents="none"><rect x="-49" y="-16" width="98" height="40" rx="7"/><text textAnchor="middle" y="0">{e.name} · {e.walkable?e.battleId?'另一战场':'可撤离':'不可通行'}</text><text textAnchor="middle" y="15" className="destination">{e.destination}</text></g>;})}
 {[...battle.actors].sort((a,b)=>xy(a).y-xy(b).y).map(a=>{const p=xy(visual[a.accountId]||a),active=a.accountId===battle.turnAccountId;return <g key={a.accountId} transform={`translate(${p.x} ${p.y})`} className={'battle-actor '+(!a.online?'offline':'')} pointerEvents="none" style={{transition:'transform 110ms linear'}}>{mode==='move'&&a.accountId===me&&<ellipse cy="12" rx="21" ry="9" fill="none" stroke={active?'#fff1aa':a.accountId===me?'#71c4d0':'#72836a'} strokeWidth={active?3:2}/>}<g opacity={a.character.life==='down'?.5:1} transform={a.character.life==='down'?'rotate(75)':undefined}><Sprite color={a.color} monster={a.character.kind==='monster'} species={a.character.species}/></g><rect className="health-track" x="-20" y="-40" width="40" height="6" rx="2"/><rect className="health-fill" x="-20" y="-40" width={40*(a.character.life==='down'?a.character.downHp/a.character.maxDownHp:a.character.hp/a.character.maxHp)} height="6" rx="2"/><text className="health-text" y="-46">{a.character.life==='down'?`倒地 ${a.character.downHp}/${a.character.maxDownHp}`:`${a.character.hp}/${a.character.maxHp}`}</text><text className="battle-actor-name" y="-62">{a.username}{a.accountId===me?' · 你':''}</text>{a.withdrawDirection!=null&&<text className="battle-wait-label" y="29">正在撤离…</text>}{!a.online&&<text className="battle-wait-label" y="40">离线</text>}</g>;})}
 {recentAttack&&<g key={recentAttack.id} transform={`translate(${xy({q:recentAttack.q!,r:recentAttack.r!}).x} ${xy({q:recentAttack.q!,r:recentAttack.r!}).y})`} pointerEvents="none"><path className="battle-attack-flash" d="M-22 20L22-25M-16-22L18 19" stroke="#fff1c0" strokeWidth="7"/></g>}
 {loot.map(x=>{const p=xy({q:x.battleQ!,r:x.battleR!});return <g key={x.id} className="loot-marker" transform={`translate(${p.x-18} ${p.y+12})`}><rect x="-6" y="-6" width="17" height="12" fill={x.kind==='corpse'?'#80543e':'#b09359'} stroke="#493c2c" strokeWidth="2"/><path d="M-2-8h9v4h-9" fill="#d9c89d"/><text y="19" fontSize="9" fill="#47362d">{x.kind==='corpse'?'尸体':x.kind==='chest'?'宝箱':'掉落堆'}</text></g>})}
 </svg>
 <MapTools className="battle-tools" onMessage={onMessage}><button title="放大战场" aria-label="放大战场" onClick={()=>setZoom(z=>Math.min(2.8,z*1.2))}><Plus size={18}/></button><button title="缩小战场" aria-label="缩小战场" onClick={()=>setZoom(z=>Math.max(.65,z/1.2))}><Minus size={18}/></button><button title="查看全场" aria-label="查看全场" onClick={()=>{setCenter({x:0,y:0});setZoom(.8);}}><Maximize size={18}/></button><button title="定位自己" aria-label="定位自己" onClick={()=>own&&locate(own)}><LocateFixed size={18}/></button></MapTools>
 <Hotbar signature={JSON.stringify([battle,items,revision])} connected={connected} busy={busy||mode==='item'} mode={mode} points={myTurn?battle.turnPoints:null} reaction={own?.reactionPoints??0} armor={own?.loadout.armor??0} onMessage={onMessage} onEdit={()=>{if(targetItemCode){cancelItemTarget();return;}setMode('inspect');setMovePreview(null);setSelected(null);}} onAction={action=>{
   setMovePreview(null);setSelected(null);
   const item=items.find(i=>i.code===action.id&&i.use_action===action.command&&i.quantity>0);
   if(action.source==='item'){if(!item||!itemSupported(item)){onMessage('道具已耗尽或暂未开放使用');return;}returnToInventory.current=false;useItem(item);}
   else if(action.command==='equipment'){setMode('inspect');setInventoryTab('equipment');setInventoryOpen(true);}
   else if(action.command==='guard'){setMode('inspect');void command('guard',{});}
   else setMode(m=>m===action.command?'inspect':action.command);
 }} commands={<><button className="end-turn" disabled={!enabled} onClick={()=>{setMovePreview(null);setMode('inspect');void command('end-turn',{});}}><SkipForward/><span>结束回合</span></button>{mode!=='inspect'&&<button aria-label={targetItemCode?(returnToInventory.current?'取消选目标，返回背包':'取消选目标'):'取消动作选择'} title={targetItemCode?(returnToInventory.current?'返回背包':'取消选目标'):'取消动作选择'} disabled={busy} onClick={()=>{if(targetItemCode){cancelItemTarget();return;}setMode('inspect');setMovePreview(null);setSelected(null);}}><X/></button>}{own?.shieldRaised&&<button aria-label="取消举盾" title="取消举盾，不返还行动点" disabled={!enabled} onClick={()=>void command('cancel-guard',{})}><ShieldOff/></button>}</>}/>
 <div className="battle-actions"><div className="battle-selection">{mode==='item'&&targetItem?`${targetItem.name} · 选择目标 · ${targetItem.attack_cost} 行动点`:own?.withdrawDirection!=null?'正在等待撤离':own&&own.entryRound>battle.round?'下一轮开始行动':mode==='move'?previewPath?`路线 ${previewPath.length} 格 · 消耗 ${previewPath.length} 点 · 余 ${battle.turnPoints-previewPath.length} 点${moveTriggers?' · 途中触发原红格攻击':''}；再点同一格移动，点其他格取消`:`选择白色范围内的空格预览路线 · 剩余 ${battle.turnPoints} 点`:mode==='attack'?`${weapon?.name} · 选择攻击方向/格子 · ${weapon?.damage} 伤害 · ${weapon?.cost} 点`:selected?`${target?.username||'草地'} · ${selected.q}, ${selected.r}`:'先选择下方的移动或攻击'}</div>
 {mode==='rescue'&&<p>选择相邻倒地旅人</p>}
 {mode.includes('withdraw')&&ownEdges.length>0&&<div className="withdraw-options">{ownEdges.map(d=>{const e=battle.edges[d],force=mode==='force-withdraw';return <div className="withdraw-row" key={d}><span><b>{e.name} → {e.destination}</b><small>{!e.walkable?'此方向不可通行':e.battleId?'注意：将进入另一场战斗':'返回相邻大世界格'}</small></span><button disabled={!enabled||!e.walkable||(force&&(!battle.turnStartEdge||battle.turnPoints!==6))} onClick={()=>void command('withdraw',{direction:d,force})}><LogOut size={13}/>{force?'强退 · 6 点':'准备撤离'}</button></div>;})}</div>}
 <div className="battle-feedback" role="status" aria-live="polite">{noticeText}</div><div className="battle-quiet">{ownEdges.length?'普通撤离会立即结束本回合；强制撤离需回合开始已在外圈。':'红格：进入不触发，离开或攻击到期时结算'}</div></div>
 {inventoryOpen&&own&&<BattleInventory initialTab={inventoryTab} initialItem={lastItemCode} own={own} actors={battle.actors} items={items} enabled={!!enabled} points={battle.turnPoints} busy={busy} onClose={()=>setInventoryOpen(false)} onCommand={command} onUse={item=>{returnToInventory.current=true;useItem(item);}}/>}
 </section>;
}
