import {useEffect,useState} from 'react';
import {api} from './api';
import type {Character} from './types';
export function AdminSkills(){
 const [characters,setCharacters]=useState<Character[]>([]),[selected,setSelected]=useState(''),[learned,setLearned]=useState(false),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
 function refresh(){api<Character[]>('/admin/characters').then(setCharacters).catch(e=>setMessage(e.message));}
 useEffect(refresh,[]);
 useEffect(()=>{if(!selected)return;let active=true;setBusy(true);api<{code:string}[]>(`/admin/characters/${selected}/skills`).then(s=>{if(active)setLearned(s.some(x=>x.code==='interrupt'));}).catch(e=>{if(active)setMessage(e.message);}).finally(()=>{if(active)setBusy(false);});return()=>{active=false;};},[selected]);
 async function set(granted:boolean){setBusy(true);try{await api(`/admin/characters/${selected}/skills`,{code:'interrupt',granted});setLearned(granted);setMessage(granted?'此角色已学会打断':'已移除此角色的打断技能');}catch(e){setMessage((e as Error).message);}finally{setBusy(false);}}
 return <section className="admin-bag"><h3>角色技能</h3><p>仅修改选中的个体，玩家与 NPC 使用相同技能规则。</p><select aria-label="选择授予技能的角色" value={selected} onChange={e=>setSelected(e.target.value)}><option value="">请选择角色</option>{characters.filter(c=>c.life!=='dead').map(c=><option key={c.id} value={c.id}>{c.name} · {c.kind==='player'?'玩家':c.species} · {c.id.slice(0,8)}</option>)}</select><button onClick={refresh}>刷新角色</button>{selected&&<p>打断 · {learned?'已学会':'未学会'} <button disabled={busy} onClick={()=>void set(!learned)}>{learned?'移除技能':'授予技能'}</button></p>}<p role="status">{message}</p></section>;
}
