import type {BattleEvent,Hex} from '../../../types';

export type CombatNumber={id:string;cell:Hex;amount:number;heal:boolean;targetId?:string;meteorId?:string};

export function combatNumbers(events:BattleEvent[],clock:number,actors:readonly (Hex&{accountId:string})[]=[]):CombatNumber[]{
  const recent=events.filter(event=>clock-event.happenedAt<=1800);
  const meteors=new Map<string,string>();
  const key=(event:BattleEvent)=>[event.happenedAt,event.actorId,event.q,event.r].join('/');
  const result:CombatNumber[]=[];
  for(const event of recent){
    if(event.kind==='meteor'){meteors.set(key(event),'battle:'+event.id);continue;}
    const cell=event.q!=null&&event.r!=null?{q:event.q,r:event.r}:
      event.kind==='status-tick'?actors.find(actor=>actor.accountId===event.targetId):undefined;
    if(!cell||event.amount==null)continue;
    const heal=event.kind==='potion'||event.kind==='bandage';
    const damage=event.kind==='attack'||event.kind==='status-tick';
    if(!heal&&!damage)continue;
    result.push({id:'number:'+event.id,cell,amount:event.amount,heal,
      targetId:damage&&event.amount>0?event.targetId??undefined:undefined,meteorId:damage?meteors.get(key(event)):undefined});
  }
  return result;
}
