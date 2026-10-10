import type {BattleActor,Item} from './types';
export function itemSupported(item:Item){return ['bandage','potion','throw-stone'].includes(item.use_action??'')&&(item.target_mode==='character'||item.target_mode==='self');}
export function targetReason(item:Item,own:BattleActor,target:BattleActor|undefined){
 if(!itemSupported(item))return '该道具暂未开放使用';
 if(!target)return '请选择范围内的角色';
 const distance=Math.max(Math.abs(target.q-own.q),Math.abs(target.r-own.r),Math.abs(target.q+target.r-own.q-own.r));
 if(item.target_mode==='self'&&target.accountId!==own.accountId)return '只能对自己使用';
 if(distance>(item.use_range??0))return '目标超出范围';
 if(item.use_action==='throw-stone'){if(target.accountId===own.accountId)return '不能投掷自己';return ['alive','down'].includes(target.character.life)?'':'目标不能被攻击';}
 if(target.character.life!=='alive')return '目标需要处于站立状态';
 if((item.heal_amount??0)>0&&target.character.hp>=target.character.maxHp)return '目标已满血';
 return '';
}
export function equipmentStats(items:Item[],main:string,off:string,body=''){
 const a=items.find(i=>i.code===main),b=items.find(i=>i.code===off),attack=b?.hand_usage!=='off_two';
 // Empty-hand values mirror the existing unarmed rule, not a newly granted weapon.
 return {damage:attack?(a?.damage??2):0,armor:(a?.passive_armor??0)+(b?.passive_armor??0)+(items.find(i=>i.code===body)?.passive_armor??0),range:attack?(a?.max_range??1):0};
}
export function equipmentChanges(items:Item[],oldMain:string,oldOff:string,main:string,off:string,oldBody='',body=''){
 const before=equipmentStats(items,oldMain,oldOff,oldBody),after=equipmentStats(items,main,off,body);
 return (['damage','armor','range'] as const).map(key=>({key,label:{damage:'伤害',armor:'护甲',range:'最远射程'}[key],value:after[key]-before[key]})).filter(x=>x.value!==0);
}
