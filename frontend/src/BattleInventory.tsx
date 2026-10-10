import {useEffect,useRef,useState} from 'react';
import {ArrowRight,ArrowDown,ArrowUp,BriefcaseMedical,Check,ChevronDown,Shield,Swords,TriangleAlert,X} from 'lucide-react';
import type {BattleActor,Item} from './types';
import {equipmentChanges,itemSupported,targetReason} from './battleInventoryRules';
import './battleInventory.css';

export function BattleInventory({own,actors,items,enabled,points,busy,onClose,onCommand,onUse,initialTab='equipment',initialItem}:{own:BattleActor;actors:BattleActor[];items:Item[];enabled:boolean;points:number;busy:boolean;onClose:()=>void;onCommand:(path:string,body:object)=>Promise<boolean|undefined>;onUse:(item:Item)=>void;initialTab?:'equipment'|'items';initialItem?:string|null}){
 const dialog=useRef<HTMLDialogElement>(null),mainButton=useRef<HTMLButtonElement>(null),offButton=useRef<HTMLButtonElement>(null);
 const [tab,setTab]=useState<'equipment'|'items'>(initialTab),[picker,setPicker]=useState<'main'|'off'|'body'|null>(null);
 const [body,setBody]=useState(own.character.body??'');
 const [main,setMain]=useState(own.character.weapon??''),[off,setOff]=useState(own.character.offhand??'');
 useEffect(()=>{dialog.current?.showModal();if(initialTab==='items'&&initialItem)Array.from(dialog.current?.querySelectorAll<HTMLButtonElement>('[data-item]')??[]).find(b=>b.dataset.item===initialItem)?.focus();},[]);
 useEffect(()=>{setMain(own.character.weapon??'');setOff(own.character.offhand??'');setBody(own.character.body??'');},[own.character.weapon,own.character.offhand,own.character.body]);
 useEffect(()=>{if(picker)dialog.current?.querySelector<HTMLButtonElement>('.equipment-options [aria-checked="true"]')?.focus();},[picker]);
 const available=items.filter(i=>i.quantity>0||i.code===own.character.weapon||i.code===own.character.offhand||i.code===own.character.body);
 const weapons=available.filter(i=>i.kind==='weapon'),shields=available.filter(i=>i.kind==='shield');
 const consumables=items.filter(i=>i.use_action&&i.quantity>0);
 const changed=body!==(own.character.body??'')||main!==(own.character.weapon??'')||off!==(own.character.offhand??'');
 const unavailable=busy?'正在处理':!enabled?'当前无法行动':points<2?'行动点不足':'';
 const name=(code:string)=>items.find(i=>i.code===code)?.name||code||'空';
 const mainItem=items.find(i=>i.code===main),offItem=items.find(i=>i.code===off);
 const changes=equipmentChanges(items,own.character.weapon??'',own.character.offhand??'',main,off,own.character.body??'',body);
 function chooseMain(code:string){setMain(code);if(available.find(i=>i.code===code)?.hand_usage==='main_two'||(code&&available.find(i=>i.code===off)?.hand_usage==='off_two'))setOff('');}
 function chooseOff(code:string){setOff(code);if(available.find(i=>i.code===code)?.hand_usage==='off_two'||(code&&available.find(i=>i.code===main)?.hand_usage==='main_two'))setMain('');}
 function closePicker(){const hand=picker;setPicker(null);(hand==='main'?mainButton:offButton).current?.focus();}
 function details(item:Item){return item.kind==='armor'?`躯干护甲 +${item.passive_armor} · 不影响移动`:item.kind==='weapon'?`伤害 ${item.damage} · 射程 ${item.min_range}–${item.max_range} · ${item.hand_usage==='main_two'?'双手':'单手'}`:`护甲 +${item.passive_armor} · 举盾减伤 ${item.guard_reduction} · ${item.hand_usage==='off_two'?'双手':'单手'}`;}
 return <dialog ref={dialog} className="battle-inventory" aria-labelledby="battle-inventory-title" onCancel={e=>{e.preventDefault();if(picker)closePicker();else if(!busy)onClose();}}>
  <header><h2 id="battle-inventory-title">战斗背包</h2><button aria-label="关闭战斗背包" title="关闭" disabled={busy} onClick={onClose}><X size={18}/></button></header>
  <div className="battle-inventory-tabs" role="tablist" aria-label="物品分类">{(['equipment','items'] as const).map(id=><button key={id} role="tab" aria-selected={tab===id} aria-controls={'battle-'+id} id={'battle-'+id+'-tab'} onClick={()=>{setTab(id);setPicker(null);}}>{id==='equipment'?'装备':'道具'}</button>)}</div>
  {tab==='equipment'?<section id="battle-equipment" role="tabpanel" aria-labelledby="battle-equipment-tab">
   <div className="battle-equipment-slots">
    <button ref={mainButton} className={'equipment-card '+(main!==(own.character.weapon??'')?'changed':'')} aria-label="选择主手装备" aria-expanded={picker==='main'} aria-controls="equipment-options" disabled={busy} onClick={()=>setPicker(picker==='main'?null:'main')}><span><Swords size={16}/>主手<ChevronDown size={14}/></span><strong>{!main&&offItem?.hand_usage==='off_two'?'已收起':mainItem?.name??'空手'}</strong><small>{!main&&offItem?.hand_usage==='off_two'?'被双手盾占用':mainItem?details(mainItem):'普通攻击'}</small></button>
    <button ref={offButton} className={'equipment-card '+(off!==(own.character.offhand??'')?'changed':'')} aria-label="选择副手装备" aria-expanded={picker==='off'} aria-controls="equipment-options" disabled={busy} onClick={()=>setPicker(picker==='off'?null:'off')}><span><Shield size={16}/>副手<ChevronDown size={14}/></span><strong>{!off&&mainItem?.hand_usage==='main_two'?'已收起':offItem?.name??'空'}</strong><small>{!off&&mainItem?.hand_usage==='main_two'?'被双手武器占用':offItem?details(offItem):'未装备'}</small></button>
   <button className="equipment-card" aria-label="选择躯干护甲" disabled={busy} onClick={()=>setPicker(picker==='body'?null:'body')}><span>躯干护甲</span><strong>{name(body)}</strong><small>护甲 +{items.find(i=>i.code===body)?.passive_armor??0}</small></button>
   </div>
   {picker&&<div id="equipment-options" className="equipment-options" role="radiogroup" aria-label={picker==='main'?'可选主手装备':picker==='body'?'可选躯干护甲':'可选副手装备'} onKeyDown={e=>{if(['ArrowDown','ArrowUp','ArrowLeft','ArrowRight','Home','End'].includes(e.key)){e.preventDefault();const buttons=Array.from(e.currentTarget.querySelectorAll<HTMLButtonElement>('[role=radio]'));const current=buttons.indexOf(document.activeElement as HTMLButtonElement);const next=e.key==='Home'?0:e.key==='End'?buttons.length-1:(current+(['ArrowDown','ArrowRight'].includes(e.key)?1:-1)+buttons.length)%buttons.length;buttons[next]?.focus();}}}>
    {[null,...(picker==='main'?weapons:picker==='body'?available.filter(i=>i.kind==='armor'):shields)].map(i=><button key={i?.code??'empty'} role="radio" aria-checked={(picker==='main'?main:picker==='body'?body:off)===(i?.code??'')} disabled={busy} onClick={()=>{if(picker==='main')chooseMain(i?.code??'');else if(picker==='body')setBody(i?.code??'');else chooseOff(i?.code??'');closePicker();}}><span><strong>{i?.name??(picker==='main'?'空手':'空')}</strong><small>{i?details(i):'不装备物品'}</small></span>{(picker==='main'?main:picker==='body'?body:off)===(i?.code??'')&&<Check size={16}/>}</button>)}
   </div>}
   <div className="battle-equipment-preview"><div><span>当前</span><p>{name(own.character.weapon??'')} / {name(own.character.offhand??'')} / {name(own.character.body??'')}</p></div><div><span>换装后</span><ArrowRight size={16}/><p>{name(main)} / {name(off)} / {name(body)}</p></div>{changed&&<div className="equipment-deltas">{changes.map(d=><span key={d.key} className={d.value>0?'positive':'negative'}>{d.label}{d.value>0?<ArrowUp size={12}/>:<ArrowDown size={12}/>} {Math.abs(d.value)}</span>)}</div>}<p className="equipment-warning"><TriangleAlert size={14}/>确认换装将取消：攻击预设 / 瞄准 · 举盾</p></div>
   <footer><div><span className="equipment-cost">{changed?'−2':'0'} 行动点</span><span className="equipment-pips" aria-label={`剩余 ${Math.max(0,points-(changed?2:0))} 行动点`}>{Array.from({length:6},(_,i)=><i key={i} className={i<Math.max(0,points-(changed?2:0))?'available':i<points?'spent':''}/>)}</span><small role="status">{unavailable||(!changed?'装备未改变':'')}</small></div><button className="confirm-equipment" disabled={!!unavailable||!changed} onClick={async()=>{if(await onCommand('equipment',{mainHand:main||null,offHand:off||null,body:body||null,updateBody:true}))onClose();}}><Check size={16}/>确认换装</button></footer>
  </section>:<section id="battle-items" role="tabpanel" aria-labelledby="battle-items-tab">
   {consumables.length?consumables.map(item=>{const reason=busy?'正在处理':!enabled?'当前无法行动':!itemSupported(item)?'该道具暂未开放使用':points<item.attack_cost?'行动点不足':item.target_mode==='self'?targetReason(item,own,own):!actors.some(a=>!targetReason(item,own,a))?'范围内没有可用目标':'';return <div className="battle-consumable" key={item.code}><BriefcaseMedical size={24}/><div><strong>{item.name} <small>×{item.quantity}</small></strong><p>{item.heal_amount!=null?`恢复 ${item.heal_amount} 生命 · ${item.attack_cost} 行动点`:item.description}</p>{reason&&<small className="item-unavailable">{reason}</small>}</div><button data-item={item.code} aria-label={`使用${item.name}`} disabled={!!reason} title={reason||`使用${item.name}`} onClick={()=>onUse(item)}>使用</button></div>;}):<p className="battle-items-empty">没有可用道具</p>}
  </section>}
 </dialog>;
}
