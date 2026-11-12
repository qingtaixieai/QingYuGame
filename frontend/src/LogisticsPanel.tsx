import {useEffect,useState} from 'react';
import {api} from './api';
type Need={id:number;kind:string;state:string;remaining:number;delivered:number;message:string;recipient:string;item:string};
type Entry={id:number;kind:string;code:string|null;quantity:number;copper:number;happened_at:number;actor:string};
type Data={admin:boolean;needs:Need[];events:Entry[]};
const states:Record<string,string>={open:'等待认领',claimed:'执行中',pickup:'等待提货',done:'已完成',cancelled:'已取消'};
export function LogisticsPanel(){
 const [data,setData]=useState<Data|null>(null),[error,setError]=useState('');
 useEffect(()=>{let active=true;const refresh=async()=>{try{const result=await api<Data>('/logistics');if(active){setData(result);setError('');}}catch(e){if(active)setError((e as Error).message);}};void refresh();const timer=setInterval(refresh,4000);return()=>{active=false;clearInterval(timer);};},[]);
 return <details><summary>官方后勤状态</summary><p>仅供查看，暂不接受玩家配送订单。</p>{error&&<p role="alert">{error}</p>}{!data&&!error&&<p>正在读取…</p>}{data?.needs.length===0&&<p>暂无可查看的相关任务。</p>}{data?.needs.map(n=><article key={n.id}><strong>{n.recipient} · {n.item}</strong><p>{states[n.state]||n.state} · 待交 {n.remaining} · 已交 {n.delivered}</p>{n.message&&<p>{n.message}</p>}</article>)}{data?.admin&&<details><summary>管理员账目记录</summary>{(data.events||[]).length===0&&<p>暂无结算记录</p>}{(data.events||[]).map(e=><article key={e.id}><strong>{e.actor} · {({purchase:'采购',sale_recovery:'回款',delivered:'交付'} as Record<string,string>)[e.kind]||e.kind}</strong><p>{e.code||'尸体'} ×{e.quantity} · {e.copper} 铜币</p><small>{new Date(e.happened_at).toLocaleString()}</small></article>)}</details>}</details>;
}
