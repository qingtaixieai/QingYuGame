import {useEffect,useState} from 'react';
import './combatLog.css';
import {api} from './api';
import type {BattleEvent} from './types';

export function eventText(e:BattleEvent){
 const a=e.actorName||'角色',t=e.targetName||'目标';
 if(e.kind==='attack')return `${a} → ${t}：${e.amount??'—'} 伤害${e.blocked?`，减免 ${e.blocked}`:''}`;
 if(e.kind==='bandage'||e.kind==='potion')return `${a} 治疗 ${t}：恢复 ${e.amount??'—'} 生命`;
 if(e.kind==='interrupt')return `${a} 打断了 ${t} 的预设`;
 if(e.kind==='down'||e.kind==='death')return `${a} ${e.kind==='down'?'使其倒地':'击败'}：${t}`;
 const labels:Record<string,string>={start:'发起战斗',join:'加入战斗',turn:'开始行动',move:'移动',mark:'预设攻击',aim:'准备瞄准','aim-ready':'瞄准就绪','aim-cancel':'移动取消瞄准','aim-expired':'瞄准到期',cancel:'取消预设',interrupted:'预设被打断',miss:'攻击落空',down:'使目标倒地',death:'击败目标',guard:'举盾','guard-cancel':'取消举盾',equip:'更换装备',loot:'拾取遗留物',withdraw:'准备撤离',exit:'离开战斗','withdraw-blocked':'撤离受阻','withdraw-interrupted':'撤离被打断',close:'战斗结束',removed:'离开战场'};
 return e.kind==='close'?'战斗结束':`${a} · ${labels[e.kind]||e.kind}${e.q!=null?` (${e.q}, ${e.r})`:''}`;
}
export function CombatLog({revision}:{revision:string|number}){
 const [open,setOpen]=useState(false),[report,setReport]=useState<{active:boolean;events:BattleEvent[]}|null>(null),[error,setError]=useState('');
 useEffect(()=>{if(!open)return;let active=true;api<{active:boolean;events:BattleEvent[]}>('/battle/report').then(r=>{if(active){setReport(r);setError('');}}).catch(e=>{if(active)setError(e.message);});return()=>{active=false;};},[open,revision]);
 return <aside className={'combat-log '+(open?'open':'')}><button aria-expanded={open} onClick={()=>setOpen(v=>!v)}>{open?'收起战报':'战斗记录'}</button>{open&&<section aria-label="战斗记录"><strong>{report?.active?'当前战斗':'最近完成的战斗'}</strong>{error?<p>{error}</p>:report?.events.length?<ol>{report.events.map(e=><li key={e.id}><time>{new Date(e.happenedAt).toLocaleTimeString('zh-CN',{hour12:false})}</time> {eventText(e)}</li>)}</ol>:<p>暂无战斗记录</p>}</section>}</aside>;
}
