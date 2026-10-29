import type {SceneObject,Recipe,Task,ProductionState} from './shared/production';
export type {SceneObject,Recipe,Task,ProductionState} from './shared/production';
import {useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {api} from './api';
import type {Item} from './types';
import './production.css';
const facilities:Record<string,string>={portable:'随身',workbench:'工作台',furnace:'熔炉',anvil:'铁砧'};
export function ProductionPanel({state,items,facility,onFacility,disabled,battle,clock,onMessage}:{state:ProductionState|null;items:Item[];facility:SceneObject|null;onFacility:(v:SceneObject|null)=>void;disabled:boolean;battle:boolean;clock:number;onMessage:(s:string)=>void}){
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),[selected,setSelected]=useState('bandage'),[batches,setBatches]=useState(1),[message,setMessage]=useState('');
 const lastNotice=useRef<number|undefined>(undefined);
 useEffect(()=>{if(!state)return;const timestamp=state.notice?.created_at??0;if(lastNotice.current!==undefined&&timestamp>lastNotice.current&&state.notice)onMessage(state.notice.message);lastNotice.current=timestamp;},[state?.notice?.created_at]);
 useEffect(()=>{if(facility){setOpen(true);const r=state?.recipes.find(x=>x.facility===facility.code);if(r){setSelected(r.code);setBatches(1);}}},[facility?.id]);
 useEffect(()=>{if(battle){setOpen(false);onFacility(null);}},[battle]);
 const task=state?.task,recipe=state?.recipes.find(r=>r.code===selected),name=(code:string)=>items.find(i=>i.code===code)?.name||code;
 async function command(path:string,body:object={}){if(busy)return;setBusy(true);setMessage('');try{await api('/production/'+path,body);}catch(e){setMessage((e as Error).message);onMessage((e as Error).message);}finally{setBusy(false);}}
 const enough=!!recipe&&Object.entries(recipe.materials).every(([code,n])=>(items.find(i=>i.code===code)?.available??items.find(i=>i.code===code)?.quantity??0)>=n*batches);
 const hasFacility=recipe?.facility==='portable'||recipe?.facility===facility?.code;
 const progress=task?Math.max(0,Math.min(1,(clock-task.startedAt)/(task.dueAt-task.startedAt))):0;
 return <>
 <button className="production-launch" onClick={()=>setOpen(true)}>配方与制作</button>
 {task&&<aside className="production-progress"><strong>{task.kind==='craft'?name(task.recipe||'')+'制作':task.kind==='skin'?'剥皮':task.kind==='rest'?'旅店休整':'采集'} · {task.completed+1}/{task.total}</strong><progress max="1" value={progress}/><button onClick={()=>setOpen(true)}>查看</button><button disabled={busy||disabled} onClick={()=>void command('cancel')}>取消</button><small>取消丢失当前进度，已完成部分保留</small></aside>}
 {open&&createPortal(<div className="production-overlay"><section role="dialog" aria-modal="true" aria-label="采集与制作" className="production-dialog"><header><h2>采集与制作</h2><button onClick={()=>{setOpen(false);onFacility(null);}}>关闭</button></header><p>{facility?`${facility.name} · ${facility.inUse?'有人使用':'空闲'}`:'基础配方随时可看；设施配方需走到设施旁，再点击设施。'}</p><div className="production-columns"><nav>{state?.recipes.map(r=><button aria-pressed={r.code===selected} key={r.code} onClick={()=>{setSelected(r.code);setBatches(1);}}>{r.name}<small>{facilities[r.facility]} · {r.duration/1000}秒</small></button>)}</nav><section>{recipe&&<><h3>{recipe.name} · 每批{recipe.output}件</h3><ul>{Object.entries(recipe.materials).map(([code,n])=>{const i=items.find(i=>i.code===code);return <li key={code}>{name(code)} ×{n*batches}<small>总量 {i?.quantity||0} / 可用 {i?.available??i?.quantity??0} / 预留 {i?.reserved||0}</small></li>;})}</ul><div className="production-batches">{(recipe.batch?[1,5,10]:[1]).map(n=><button key={n} aria-pressed={n===batches} onClick={()=>setBatches(n)}>{n}批</button>)}</div><button disabled={busy||disabled||battle||!!task||!enough||!hasFacility} onClick={()=>void command('start',{kind:'craft',recipe:recipe.code,batches,objectId:recipe.facility==='portable'?null:facility?.id})}>开始制作</button>{!hasFacility&&<p>需要在{facilities[recipe.facility]}旁操作</p>}{!enough&&<p>可用材料不足</p>}</>}{task&&<section><h3>正在进行 · 已完成{task.completed}/{task.total}</h3>{state?.reserved.map(r=><p key={r.item_code}>{name(r.item_code)}：预留 {r.quantity}</p>)}<button disabled={busy||disabled} onClick={()=>void command('cancel')}>取消当前任务</button><small>未完成这批的进度丢失；未用材料释放。</small></section>}{!!state?.skinTargets?.length&&<section><h3>可剥皮尸体</h3>{state.skinTargets.map(c=><button key={c.id} disabled={busy||disabled||battle||!!task} onClick={()=>void command('start',{kind:'skin',corpseId:c.id})}>{c.name} · 剥皮8秒</button>)}<p>需手持匕首；成功获得1兽皮，成功或失败都会消耗尸体。</p></section>}</section></div><p role="status">{message||state?.notice?.message}</p></section></div>,document.body)}
 </>;
}
