import {useEffect,useRef,useState} from 'react';
import {Crosshair,Target as Bow,FlaskConical,Hand,Footprints,Swords,Shield,Package,HeartHandshake,LogOut,Bandage,LockKeyhole,Unlock,Settings2,Check,X,Plus,LoaderCircle,RefreshCw} from 'lucide-react';
import type {ReactNode} from 'react';
import {api} from './api';
import './hotbar.css';

type Slot={source:string;index:number;actionRef:string|null;locked:boolean};
export type Action={id:string;source:string;name:string;icon:string;command:string;description:string;cost:number;costKind:string;quantity:number|null;disabledReason:string};
type Layout={revision:number;slots:Slot[];actions:Action[]};
const pages=[['common','通用'],['weapon','武器'],['item','道具'],['skill','技能'],['spell','法术'],['class','职业']];
const icons={'quick-shot':Bow,aim:Crosshair,'aimed-shot':Bow,potion:FlaskConical,'throw-stone':Hand,'skill:interrupt':Hand,move:Footprints,attack:Swords,guard:Shield,equipment:Package,rescue:HeartHandshake,withdraw:LogOut,bandage:Bandage};
export function Hotbar({signature,connected,busy,mode,points,reaction,armor,commands,onAction,onMessage,onEdit}:{signature:string;connected:boolean;busy:boolean;mode:string;points:number|null;reaction:number;armor:number;commands:ReactNode;onAction:(action:Action)=>void;onMessage:(message:string)=>void;onEdit:()=>void}){
 const [data,setData]=useState<Layout|null>(null),[page,setPage]=useState('common'),[editing,setEditing]=useState(false),[selected,setSelected]=useState<number|null>(null),[saving,setSaving]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[retry,setRetry]=useState(0),[adding,setAdding]=useState(false);
 const request=useRef(0),dragged=useRef<number|null>(null),pending=useRef(false);
 useEffect(()=>{let active=true;const n=++request.current;setLoading(true);setError('');api<Layout>('/hotbar').then(next=>{if(active&&n===request.current)setData(old=>old&&old.revision>next.revision?old:next);}).catch(e=>{if(active&&n===request.current)setError(e.message);}).finally(()=>{if(active&&n===request.current)setLoading(false);});return()=>{active=false;};},[signature,retry]);
 useEffect(()=>{setSelected(null);setAdding(false);},[page,editing]);
 async function edit(operation:string,from:number,to?:number,actionRef?:string){
  if(!data||pending.current||!connected)return;
  pending.current=true;setSaving(true);setAdding(false);
  try{const next=await api<Layout>('/hotbar/layout',{revision:data.revision,operation,from,to,actionRef});setData(old=>old&&old.revision>next.revision?old:next);setSelected(operation==='swap'?to??null:from);}
  catch(e){onMessage((e as Error).message);setRetry(v=>v+1);}
  finally{pending.current=false;setSaving(false);}
 }
 const active=selected==null?null:data?.slots[selected];
 const restored=data?.actions.filter(a=>a.source===page&&!data.slots.some(s=>s.actionRef===a.id))??[];
 function select(index:number){if(selected!=null&&selected!==index&&!adding)void edit('swap',selected,index);else setSelected(selected===index?null:index);}
 return <div className="action-hotbar" aria-label="战斗操作栏">
  <div className="hotbar-resources"><strong>{points??'—'}</strong><span>行动点</span><div className="hotbar-pips">{Array.from({length:6},(_,i)=><i key={i} className={points!=null&&i<points?'available':''}/>)}</div><small>反应 {reaction}/2</small>{armor>0&&<small>护甲 +{armor}</small>}</div>
  <div className="action-hotbar-main">
   <div className="action-hotbar-heading"><div role="tablist" aria-label="动作来源">{pages.map(([id,name])=><button key={id} role="tab" aria-selected={page===id} aria-controls="action-slot-page" id={'action-tab-'+id} onClick={()=>setPage(id)}>{name}</button>)}</div><button className="hotbar-icon" aria-label={editing?'完成整理':'整理战斗栏'} title={editing?'完成整理':'整理战斗栏'} aria-pressed={editing} onClick={()=>{onEdit();setEditing(v=>!v);}}>{editing?<Check/>:<Settings2/>}</button></div>
   <div className="action-slot-row" role="tabpanel" aria-labelledby={'action-tab-'+page} id="action-slot-page" onPointerDown={e=>{const button=(e.target as Element).closest('button');if(!editing&&button?.getAttribute('aria-disabled')==='true'&&!button.classList.contains('empty'))onMessage(button.title);}} onKeyDown={e=>{if(!editing&&(e.key==='Enter'||e.key===' ')){const button=(e.target as Element).closest('button');if(button?.getAttribute('aria-disabled')==='true'&&!button.classList.contains('empty'))onMessage(button.title);}}}>
    {data?.slots.map((s,index)=>{
     if(s.source!==page)return null;const action=data.actions.find(a=>a.id===s.actionRef),Icon=action?icons[action.icon as keyof typeof icons]??Swords:Plus;
     const reason=!connected?'连接已断开':busy||saving?'正在处理':loading?'正在更新动作':error?'动作更新失败，请重试':action?.disabledReason??'';
     return <button key={s.source+s.index} className={'action-slot '+(action?'cost-'+action.costKind:'empty')+(selected===index&&editing?' slot-selected':'')+(!editing&&action?.command===mode?' chosen':'')} title={editing?`${s.index+1} · ${action?.name??'空槽'}${s.locked?' · 已锁定':''}`:action?`${action.name}：${action.description}${reason?' '+reason:''}`:'空槽'} aria-label={editing?`整理槽位 ${s.index+1} ${action?.name??'空槽'}`:action?.name??`空槽 ${s.index+1}`} aria-disabled={editing?saving||!connected:!action||!!reason} draggable={editing&&!s.locked&&!saving&&!!action} onDragStart={e=>{dragged.current=index;e.dataTransfer.effectAllowed='move';e.dataTransfer.setData('text/plain',String(index));}} onDragEnd={()=>{dragged.current=null;}} onDragOver={e=>{if(editing&&!s.locked&&dragged.current!=null)e.preventDefault();}} onDrop={e=>{e.preventDefault();if(dragged.current!=null)void edit('swap',dragged.current,index);dragged.current=null;}} onClick={()=>{if(editing){if(!saving&&connected)select(index);}else if(action&&!reason)onAction(action);}}>
      {action?<><Icon/><span>{action.name}</span><small className="slot-cost">{action.costKind==='turn'?'回合':`${action.cost}${action.costKind==='movement'?'/格':''}`}</small>{action.quantity!=null&&<b className="slot-quantity">{action.quantity}</b>}</>:editing?<Plus/>:<span className="empty-slot-mark"/>}{s.locked&&<LockKeyhole className="slot-lock"/>}
     </button>;
    })}
    {!data&&<span className="hotbar-load">{loading?<LoaderCircle className="spin"/>:error}</span>}
   </div>
   {error&&<button className="hotbar-retry" onClick={()=>setRetry(v=>v+1)}><RefreshCw size={14}/>{error} · 重试</button>}
   {editing&&<div className="slot-editor" aria-label="槽位整理"><span>{active?`槽位 ${active.index+1}`:'选择槽位'}</span><button className="hotbar-icon" aria-label={active?.locked?'解锁槽位':'锁定槽位'} title={active?.locked?'解锁槽位':'锁定槽位'} disabled={!active||saving||!connected} onClick={()=>void edit('lock',selected!)}>{active?.locked?<Unlock/>:<LockKeyhole/>}</button><button className="hotbar-icon" aria-label="清空槽位" title="清空槽位" disabled={!active?.actionRef||active.locked||saving||!connected} onClick={()=>void edit('clear',selected!)}><X/></button><button className="hotbar-icon" aria-label="放入动作" title="放入动作" disabled={!active||active.locked||!restored.length||saving||!connected} onClick={()=>setAdding(v=>!v)}><Plus/></button>{saving&&<LoaderCircle size={16} className="spin"/>}{adding&&<select aria-label="选择放入的动作" value="" onChange={e=>void edit('assign',selected!,undefined,e.target.value)}><option value="" disabled>选择动作</option>{restored.map(a=><option key={a.id} value={a.id}>{a.name}</option>)}</select>}</div>}
  </div>
  <div className="hotbar-commands">{commands}</div>
 </div>;
}
