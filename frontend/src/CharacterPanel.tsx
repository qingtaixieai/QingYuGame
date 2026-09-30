import {useState} from 'react';
import {Axe,TreePine,Mountain,Swords,UserRound,X} from 'lucide-react';
import type {Character,Item} from './types';
import './character.css';
export function ItemIcon({code}:{code:string}){return code==='wood'?<TreePine/>:code==='stone'?<Mountain/>:code==='axe'?<Axe/>:code==='spear'?<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M4 21L17 7M14 7L21 2 20 10Z"/></svg>:<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 22l7-9m0 0l3-3m0 0l2-2M15 3l1 2m5-1l-1 2m3 5l-3-1"/><circle cx="17" cy="8" r="4"/></svg>;}
const attributes=[['strength','力量'],['agility','敏捷'],['constitution','体魄'],['intellect','智识'],['perception','感知'],['willpower','意志']] as const;
export function CharacterPanel({character,items,locked,onEquip}:{character:Character|null;items:Item[];locked:boolean;onEquip:(code:string|null)=>Promise<void>}){
 const [selected,setSelected]=useState<string|null>(null),[hovered,setHovered]=useState<string|null>(null),[busy,setBusy]=useState(false);
 const detail=items.find(i=>i.code===(hovered??selected)),equipped=items.find(i=>i.code===character?.weapon);
 async function equip(code:string|null){setBusy(true);try{await onEquip(code);}finally{setBusy(false);}}
 if(!character)return <p>正在读取人物…</p>;
 return <div className="character-panel"><section className="character-vitals"><UserRound size={32}/><div><strong>{character.name}</strong><span>{character.life==='soul'?'灵魂':character.life==='down'?'倒地':'冒险者'}</span></div><div className="vitals-line">生命 {character.hp}/{character.maxHp}<meter min={0} max={character.maxHp} value={character.hp}/></div><small>倒地生命 {character.downHp}/{character.maxDownHp}</small></section>
 <div className="attribute-grid">{attributes.map(([key,name])=><div key={key} title={`${name}基础值。${key==='agility'?'影响开战先攻判定。':'后续开放成长与派生效果。'}`}><span>{name}</span><strong>{character[key]}</strong></div>)}</div>
 <h3>人物装备</h3><div className="equipment-row"><button className="item-slot equipped" aria-label="武器槽" onClick={()=>setSelected(character.weapon)} onMouseEnter={()=>setHovered(character.weapon)} onMouseLeave={()=>setHovered(null)}>{equipped?<ItemIcon code={equipped.code}/>:<Swords/>}<span>{equipped?.name||'空手'}</span></button><div><strong>{equipped?.name||'空手 · 相邻单格'}</strong><p>{equipped?`${equipped.damage} 伤害 · ${equipped.attack_cost} 行动点`:'2 伤害 · 2 行动点'}</p>{equipped&&<button disabled={locked||busy} onClick={()=>void equip(null)}>卸下武器</button>}</div></div>
 <h3>背包</h3><div className="inventory-grid">{items.filter(i=>i.quantity>0).map(i=><button key={i.code} className={'item-slot '+(selected===i.code?'selected':'')} aria-label={`${i.name}，数量${i.quantity}`} onClick={()=>setSelected(i.code)} onMouseEnter={()=>setHovered(i.code)} onMouseLeave={()=>setHovered(null)} onFocus={()=>setHovered(i.code)} onBlur={()=>setHovered(null)}><ItemIcon code={i.code}/><span>{i.name}</span><b>{i.quantity}</b></button>)}</div>
 {!items.some(i=>i.quantity>0)&&<p className="empty">背包还是空的，去收集些材料吧。</p>}
 {detail&&<section className="item-detail" aria-live="polite"><button aria-label="关闭物品详情" className="icon-button" onClick={()=>{setSelected(null);setHovered(null);}}><X size={14}/></button><strong>{detail.name}</strong><p>{detail.description}</p>{detail.kind==='weapon'&&<><p>伤害 {detail.damage} · 消耗 {detail.attack_cost} 点</p><button disabled={locked||busy||detail.quantity<1||character.weapon===detail.code} onClick={()=>void equip(detail.code)}>{character.weapon===detail.code?'已装备':locked?'战斗或特殊状态中不能换装':'装备'}</button></>}</section>}
 </div>;
}
export function LifePanel({character,clock,town,busy,onAction,onLocate}:{character:Character;clock:number;town:boolean;busy:boolean;onAction:(action:string)=>void;onLocate:()=>void}){
 const [confirm,setConfirm]=useState(false);
 return <section className="life-panel"><strong>{character.life==='soul'?'灵魂回归':character.life==='down'?'你已倒地':'生命与休整'}</strong><span>生命 {character.hp}/{character.maxHp} · 倒地 {character.downHp}/{character.maxDownHp}</span>
 {character.timerKind&&<p>{character.timerKind==='recall'?'回城':'休整'} · {Math.max(0,Math.ceil((character.timerEnd-clock)/1000))} 秒 <button disabled={busy} onClick={()=>onAction('cancel')}>取消</button></p>}
 {character.life==='soul'?<><p>从绑定城市跑回死亡标记复活，或原地等待回城。灵魂不能跨海。</p><button onClick={onLocate}>定位死亡标记</button><button disabled={busy||!!character.timerKind} onClick={()=>onAction('recall')}>回城复活 · 5秒</button><button disabled={busy||character.q!==character.deathQ||character.r!==character.deathR} onClick={()=>onAction('revive')}>死亡标记复活</button></>:<>{town&&character.life==='alive'&&<><button disabled={busy} onClick={()=>onAction('bind')}>设为回归城市</button><button disabled={busy||!!character.timerKind} onClick={()=>onAction('rest')}>休整 · 10秒</button></>}{character.life==='down'&&<p>等待其他旅人救起，或选择死亡后以灵魂回归。</p>}<button onClick={()=>setConfirm(!confirm)}>结束角色生命</button>{confirm&&<div><p>将进入灵魂状态，确定吗？</p><button disabled={busy} onClick={()=>{setConfirm(false);onAction('surrender');}}>确认死亡</button><button onClick={()=>setConfirm(false)}>取消</button></div>}</>}
 </section>;
}
