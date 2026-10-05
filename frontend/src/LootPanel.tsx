import {useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {Package,Skull,X} from 'lucide-react';
import {api} from './api';
import type {BattleState,LootContainer} from './types';
import './pixel.css';
import './loot.css';

const kindName=(k:string)=>k==='corpse'?'尸体':k==='chest'?'宝箱':'掉落堆';
function Glyph({kind,size=24}:{kind:string;size?:number}){return kind==='corpse'?<Skull size={size}/>:<Package size={size}/>;}
function remaining(expires:number|null,clock:number){if(expires===null)return '携带中 · 计时暂停';const s=Math.max(0,Math.ceil((expires-clock)/1000));return `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')} 后消失`;}

/** 地面遗留物列表：只列出当前格有哪些容器，点开进各自的独立页。 */
export function LootPanel({version,battle,me,clock,revision,onOpenContainer,onClose}:{version:string;battle:BattleState;me:string;clock:number;revision:number;onOpenContainer:(id:string)=>void;onClose:()=>void}){
 const [ground,setGround]=useState<LootContainer[]>([]);
 const [loading,setLoading]=useState(true),[error,setError]=useState('');
 const panel=useRef<HTMLElement>(null);
 const battleId=battle.active?battle.id:null;
 const query=`?version=${encodeURIComponent(version)}${battleId?'&battleId='+battleId:''}`;
 useEffect(()=>{panel.current?.focus();const key=(e:KeyboardEvent)=>{if(e.key==='Escape'){e.stopPropagation();onClose();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[]);
 useEffect(()=>{let alive=true;setLoading(true);api<{ground:LootContainer[]}>('/loot'+query).then(d=>{if(alive){setGround(d.ground);setError('');}}).catch(e=>{if(alive)setError(e.message);}).finally(()=>{if(alive)setLoading(false);});return()=>{alive=false;};},[query,revision]);
 return createPortal(<div className="container-overlay" onClick={e=>e.stopPropagation()}><section ref={panel} tabIndex={-1} className="loot-list" role="dialog" aria-modal="true" aria-label="此处遗留物">
  <header className="loot-list-head"><div><h2>此处遗留</h2><small>{battle.active?`战斗中 · ${battle.turnPoints} 行动点 · 同格查看免费`:'点开查看各自的内容'}</small></div><button className="icon-button" aria-label="关闭" onClick={onClose}><X size={18}/></button></header>
  <div className="loot-list-body">
   {loading&&<p className="container-note">正在读取…</p>}
   {!loading&&!ground.length&&!error&&<p className="container-note">这里没有遗留物。</p>}
   {ground.map(x=><button key={x.id} className="loot-card" onClick={()=>onOpenContainer(x.id)}><Glyph kind={x.kind}/><span><strong>{x.name}</strong><small>{kindName(x.kind)} · {x.itemCount} 件内容</small><small>{remaining(x.expiresAt,clock)}{x.battleId?` · 格 ${x.battleQ},${x.battleR}`:''}</small></span></button>)}
   {error&&<p className="container-note warn">{error}</p>}
  </div>
 </section></div>,document.body);
}
