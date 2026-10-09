import {useEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {Skull,Package,X} from 'lucide-react';
import {api} from './api';
import {ItemIcon} from './CharacterPanel';
import type {BattleState,Hex,Item,LootContainer} from './types';
import './pixel.css';
import './container.css';

const attributes=[['strength','力量'],['agility','敏捷'],['constitution','体魄'],['intellect','智识'],['perception','感知'],['willpower','意志']] as const;
const kindName=(k:string)=>k==='corpse'?'尸体':k==='chest'?'宝箱':'掉落堆';
function remaining(expires:number|null,clock:number){if(expires===null)return '携带中 · 计时暂停';const s=Math.max(0,Math.ceil((expires-clock)/1000));return `${Math.floor(s/60)}:${String(s%60).padStart(2,'0')} 后消失`;}

/** 单个容器的独立大页面：点尸体/宝箱/掉落堆 → 复用人物页模板展示。 */
export function ContainerPage({version,battle,me,clock,connected,position,containerId,onClose,onChanged}:{version:string;battle:BattleState;me:string;clock:number;connected:boolean;position:Hex;containerId:string;onClose:()=>void;onChanged:()=>Promise<void>}){
 const [container,setContainer]=useState<LootContainer|null>(null);
 const [contents,setContents]=useState<Item[]>([]);
 const [itemCode,setItemCode]=useState('');
 const [quantity,setQuantity]=useState(1);
 const [busy,setBusy]=useState(false),[loading,setLoading]=useState(true),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const pending=useRef(false),contentReq=useRef(0),panel=useRef<HTMLElement>(null);
 const battleId=battle.active?battle.id:null,own=battle.active?battle.actors.find(a=>a.accountId===me):null;
 const query=`?version=${encodeURIComponent(version)}${battleId?'&battleId='+battleId:''}`;
 const selectedItem=contents.find(i=>i.code===itemCode)||contents[0];
 const sameCell=!!container&&(container.carrierId===me||(!battle.active?container.q===position.q&&container.r===position.r:container.battleQ===own?.q&&container.battleR===own?.r));
 const available=connected&&(!battle.active||battle.turnAccountId===me&&own?.character.life==='alive'&&battle.turnPoints>=2);
 const expired=container?.expiresAt!=null&&container.expiresAt<=clock;
 const canTake=available&&sameCell&&!expired&&!busy&&!loading;
 const cost=battle.active?' · 2点':'';
 useEffect(()=>{panel.current?.focus();const key=(e:KeyboardEvent)=>{if(e.key==='Escape'&&!pending.current){e.stopPropagation();onClose();}};window.addEventListener('keydown',key);return()=>window.removeEventListener('keydown',key);},[]);
 useEffect(()=>{let alive=true;api<{ground:LootContainer[];carried:LootContainer[]}>('/loot'+query).then(d=>{if(!alive)return;const c=[...d.ground,...d.carried].find(x=>x.id===containerId)||null;setContainer(c);if(!c)setError('此处遗留物已被拿走或消失');}).catch(e=>{if(alive)setError(e.message);});return()=>{alive=false;};},[containerId,query]);
 useEffect(()=>{const req=++contentReq.current;setContents([]);setLoading(true);setQuantity(1);if(!container||!sameCell||expired){setLoading(false);return;}api<Item[]>('/loot/'+container.id+query).then(d=>{if(req===contentReq.current){setContents(d);setItemCode(old=>d.some(x=>x.code===old)?old:d[0]?.code||'');}}).catch(e=>{if(req===contentReq.current)setError(e.message);}).finally(()=>{if(req===contentReq.current)setLoading(false);});return()=>{contentReq.current++;};},[container,sameCell,expired,query]);
 async function act(action:string,code?:string,amount?:number){if(pending.current||!container)return;pending.current=true;setBusy(true);setError('');setNotice('');try{await api('/loot/action',{action,containerId:container.id,code,quantity:amount,version,battleId});setNotice(action==='carry'?'尸体与内部物品已装入背包':action==='dropCorpse'?'尸体已放回地上，重新计时30分钟':'物品已放入背包');await onChanged();const d=await api<{ground:LootContainer[];carried:LootContainer[]}>('/loot'+query);const updated=[...d.ground,...d.carried].find(x=>x.id===container.id);if(!updated){onClose();return;}setContainer(updated);setQuantity(1);}catch(e){setError((e as Error).message);}finally{pending.current=false;setBusy(false);}}
 return createPortal(<div className="container-overlay" onClick={e=>e.stopPropagation()}><section ref={panel} tabIndex={-1} className="container-page" role="dialog" aria-modal="true" aria-label="遗留物">
  <header className="container-head">
    <span className={'container-glyph kind-'+(container?.kind||'corpse')}>{container?.kind==='corpse'?<Skull size={30}/>:<Package size={30}/>}</span>
    <div className="container-title">
      <h2>{container?container.name:'遗留物'}</h2>
      {container&&<small>{kindName(container.kind)}{container.carrierId?' · 背包中':` · 地上 ${container.q},${container.r}`} · {remaining(container.expiresAt,clock)}</small>}
    </div>
    {container?.kind==='corpse'&&<span className={'container-badge '+(container.itemCount>0?'fresh':'done')}>{container.itemCount>0?'未搜刮':'已搜刮'}</span>}
    <button className="icon-button" aria-label="关闭" disabled={busy} onClick={onClose}><X size={18}/></button>
  </header>
  <div className="container-body">
    <aside className={'container-portrait kind-'+(container?.kind||'corpse')}>{container?.kind==='corpse'?<Skull size={72}/>:<Package size={72}/>}<span>{container?kindName(container.kind):''}</span></aside>
    <div className="container-detail">
      {!container?<p className="container-note">{error||'正在读取…'}</p>
       :!sameCell?<p className="container-note warn">必须站在此来源所在格，才能打开和拿取。</p>
       :expired?<p className="container-note warn">已到期消失。</p>
       :loading?<p className="container-note">正在查看物品…</p>
       :<>
        {container.kind==='corpse'&&<div className="container-attrs">{attributes.map(([key,name])=><div key={key}><span>{name}</span><strong>{container[key]}</strong></div>)}</div>}
        <h3 className="container-subtitle">物品 {contents.length>0&&<em>{contents.length} 种</em>}</h3>
        <div className="container-grid">{contents.map(i=><button key={i.code} className={'container-slot '+(selectedItem?.code===i.code?'selected':'')} title={i.description} aria-label={`查看${i.name}`} onClick={()=>{setItemCode(i.code);setQuantity(1);}}><ItemIcon code={i.code}/><span>{i.name}</span><b>{i.quantity}</b></button>)}{!contents.length&&<p className="container-empty">已搜空{container.kind==='corpse'&&!container.carrierId?'，仍可拿走整具尸体。':''}</p>}</div>
        {selectedItem&&<div className="container-item-detail"><strong>{selectedItem.name}</strong><p>{selectedItem.description}</p><label>拿取数量 <input aria-label="拿取数量" type="number" min="1" max={selectedItem.quantity} value={quantity} onChange={e=>setQuantity(Number(e.target.value))}/></label></div>}
       </>}
    </div>
  </div>
  <footer className="container-foot">
    <span className="container-feedback" aria-live="polite">{error||notice||(!available&&battle.active?'等待自己的回合并保留至少2行动点':'')}</span>
    {container&&<div className="container-actions">
      <button className="pixel-btn" disabled={!canTake||!selectedItem||!Number.isInteger(quantity)||quantity<1||quantity>(selectedItem?.quantity||0)} onClick={()=>void act('take',selectedItem?.code,quantity)}>拿取所选{cost}</button>
      <button className="pixel-btn" disabled={!canTake||!contents.length} onClick={()=>void act('takeAll')}>全部拿取{cost}</button>
      {container.kind==='corpse'&&(container.carrierId?<button className="pixel-btn pixel-btn--danger" disabled={!connected||busy||battle.active} onClick={()=>void act('dropCorpse')}>丢下尸体</button>:<button className="pixel-btn pixel-btn--primary" disabled={!canTake} onClick={()=>void act('carry')}>拿走尸体（含物品）{cost}</button>)}
    </div>}
  </footer>
 </section></div>,document.body);
}
