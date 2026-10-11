import {useEffect,useState} from 'react';
import {api} from './api';
import './statuses.css';
export type Status={code:string;name:string;description:string;remaining:number;damage:number;sourceId:string|null};
type Data={statuses:Status[];paused:boolean;selectedAmmo:string};
export function StatusPanel({revision,compact=false,ammoOnly=false}:{revision:string|number;compact?:boolean;ammoOnly?:boolean}){
 const [data,setData]=useState<Data|null>(null),[expanded,setExpanded]=useState(false),[error,setError]=useState('');
 useEffect(()=>{let alive=true;api<Data>('/character/status').then(d=>{if(alive)setData(d);}).catch(()=>{});return()=>{alive=false;};},[revision]);
 async function ammo(code:string){try{await api('/character/ammo',{code});setData(d=>d?{...d,selectedAmmo:code}:d);setError('');}catch(e){setError((e as Error).message);}}
 if(compact&&!data?.statuses.length)return null;
 return <section className={compact?'status-strip':'status-panel'} aria-label="角色状态">{!compact&&<><h3>状态与弹药</h3><label>使用弹药 <select value={data?.selectedAmmo??'arrow'} onChange={e=>void ammo(e.target.value)}><option value="arrow">普通箭</option><option value="poison_arrow">毒箭</option></select></label><small>耗尽后不会自动替换</small></>}{!ammoOnly&&data?.statuses.map(s=><button key={s.code} onClick={()=>setExpanded(v=>!v)} title={s.description}>☠ {s.name} · {s.remaining} 次{data.paused?' · 暂停结算':''}</button>)}{!ammoOnly&&(!compact||expanded)&&data?.statuses.map(s=><p key={s.code}>{s.description} 剩余 {s.remaining} 次自身行动开始。</p>)}{!ammoOnly&&!compact&&!data?.statuses.length&&<p>当前没有状态效果</p>}{error&&<p role="status">{error}</p>}</section>;
}
