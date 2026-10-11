import type {Character,Item} from './types';
export function EquipmentComparison({item,character,items,battle=false}:{item:Item;character:Character;items:Item[];battle?:boolean}){
 if(!['weapon','shield','armor'].includes(item.kind))return null;
 const code=item.kind==='armor'?character.body:item.kind==='shield'?character.offhand:character.weapon;
 const old=items.find(i=>i.code===code);
 const delta=(a:number,b:number)=>`${a}（${a-b>=0?'+':''}${a-b}）`;
 const shape=(s:string)=>({line:'直线贯穿',fan:'相邻扇面',single:'单格'}[s]||s);
 return <section className="equipment-comparison"><strong>对比：{old?.name||'未装备'}</strong>{item.kind==='weapon'?<><p>伤害 {delta(item.damage,old?.damage??2)} · 消耗 {item.attack_cost} 行动点</p><p>射程 {old?`${old.min_range}–${old.max_range}`:'1'} → {item.min_range}–{item.max_range} 格</p><p>攻击形状 {shape(old?.shape??'single')} → {shape(item.shape)}</p></>:<p>护甲 {delta(item.passive_armor,old?.passive_armor??0)}{item.kind==='shield'?` · 举盾减伤 ${delta(item.guard_reduction,old?.guard_reduction??0)}`:''}</p>}<p>{item.kind==='armor'?'躯干护甲':item.hand_usage.endsWith('two')?'占用双手':'单手装备'}{battle?' · 确认换装消耗2行动点':''}</p></section>;
}
