import {useEffect,useState} from 'react';
import {api} from './api';
import type {Character} from './types';
export function AdminSkills(){
 const skillNames={interrupt:'打断',fireball:'火球术'};
 const [behavior,setBehavior]=useState('balanced');
 const [characters,setCharacters]=useState<Character[]>([]),[selected,setSelected]=useState(''),[learned,setLearned]=useState<string[]>([]),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
 useEffect(()=>{if(selected&&characters.find(c=>c.id===selected)?.kind==='monster')api<{behavior:string}>(`/admin/characters/${selected}/behavior`).then(x=>setBehavior(x.behavior)).catch(e=>setMessage(e.message));},[selected,characters]);
 function refresh(){api<Character[]>('/admin/characters').then(setCharacters).catch(e=>setMessage(e.message));}
 useEffect(refresh,[]);
 useEffect(()=>{if(!selected)return;let active=true;setBusy(true);api<{code:string}[]>(`/admin/characters/${selected}/skills`).then(s=>{if(active)setLearned(s.map(x=>x.code));}).catch(e=>{if(active)setMessage(e.message);}).finally(()=>{if(active)setBusy(false);});return()=>{active=false;};},[selected]);
 async function set(code:keyof typeof skillNames,granted:boolean){setBusy(true);try{await api(`/admin/characters/${selected}/skills`,{code,granted});setLearned(previous=>granted?[...previous,code]:previous.filter(skill=>skill!==code));setMessage(granted?'已授予'+skillNames[code]:'已移除'+skillNames[code]);}catch(e){setMessage((e as Error).message);}finally{setBusy(false);}}
 return <section className="admin-bag"><h3>角色技能</h3><p>仅修改选中的个体，玩家与 NPC 使用相同技能规则。</p><select aria-label="选择授予技能的角色" value={selected} onChange={e=>setSelected(e.target.value)}><option value="">请选择角色</option>{characters.filter(c=>c.life!=='dead').map(c=><option key={c.id} value={c.id}>{c.name} · {c.kind==='player'?'玩家':c.species} · {c.id.slice(0,8)}</option>)}</select><button onClick={refresh}>刷新角色</button>{selected&&(Object.keys(skillNames) as (keyof typeof skillNames)[]).map(code=>{const granted=learned.includes(code);return <p key={code}>{skillNames[code]} · {granted?'已学会':'未学会'} <button disabled={busy} onClick={()=>void set(code,!granted)}>{granted?'移除技能':'授予技能'}</button></p>;})}{selected&&characters.find(c=>c.id===selected)?.kind==='monster'&&<label>行为倾向 <select value={behavior} onChange={e=>{const value=e.target.value;api(`/admin/characters/${selected}/behavior`,{behavior:value}).then(()=>{setBehavior(value);setMessage('倾向已更新');}).catch(e=>setMessage(e.message));}}><option value="balanced">均衡</option><option value="aggressive">激进</option><option value="cautious">谨慎</option></select></label>}<p role="status">{message}</p></section>;
}
