import {useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {X,Trash2} from 'lucide-react';
import {api} from './api';
import {ItemIcon} from './CharacterPanel';
import type {Item} from './types';
import './loot.css';

export function DropPanel({items,version,connected,onClose,onChanged}:{items:Item[];version:string;connected:boolean;onClose:()=>void;onChanged:()=>Promise<void>}){
 const [counts,setCounts]=useState<Record<string,number>>({}),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const panel=useRef<HTMLElement>(null);
 const droppable=items.filter(i=>i.quantity>0);
 const chosen=droppable.filter(i=>(counts[i.code]||0)>0);
 const total=chosen.reduce((s,i)=>s+(counts[i.code]||0),0);
 useEffect(()=>{panel.current?.focus();const key=(e:KeyboardEvent)=>{if(e.key==='Escape'&&!busy){e.stopPropagation();onClose();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[busy]);
 function bump(code:string,d:number){setCounts(o=>{const n=Math.max(0,Math.min((o[code]||0)+d,droppable.find(i=>i.code===code)?.quantity||0));return {...o,[code]:n};});}
 async function confirm(){if(busy||!chosen.length)return;setBusy(true);setError('');try{await api('/loot/action',{action:'dropItems',items:chosen.map(i=>({code:i.code,quantity:counts[i.code]})),version});await onChanged();onClose();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return createPortal(<div className="loot-overlay" onClick={e=>e.stopPropagation()}><section ref={panel} tabIndex={-1} className="loot-panel drop-panel" role="dialog" aria-modal="true" aria-label="丢弃物品"><header><div><h2>丢弃物品</h2><small>点选要丢弃的物品，确认后一起丢到当前格子的掉落堆</small></div><button aria-label="关闭丢弃" disabled={busy} onClick={onClose}><X/></button></header>
 <div className="drop-list">{droppable.map(i=><div key={i.code} className={'drop-row '+(counts[i.code]>0?'chosen':'')}><ItemIcon code={i.code}/><span className="drop-info"><strong>{i.name}</strong><small>× {i.quantity}</small></span><span className="drop-count">{counts[i.code]||0}</span><button className="drop-bump" aria-label={`减少${i.name}`} disabled={busy||!(counts[i.code]>0)} onClick={()=>bump(i.code,-1)}>−</button><button className="drop-bump" aria-label={`增加${i.name}`} disabled={busy||(counts[i.code]||0)>=i.quantity} onClick={()=>bump(i.code,1)}>＋</button></div>)}{!droppable.length&&<p className="loot-empty">背包还是空的。</p>}</div>
 <div className="loot-feedback" aria-live="polite">{error}</div><div className="loot-controls"><span className="drop-total">{total>0?`将丢弃 ${chosen.length} 种 · 共 ${total} 件`:''}</span><button className="loot-primary" disabled={busy||!connected||!chosen.length} onClick={confirm}><Trash2 size={15}/>确认丢弃</button></div></section></div>,document.body);
}
