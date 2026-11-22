import type {Hex,WorldAction} from './protocol';
import type {Task} from './production';

export type ActionProgress={
  id:string;kind:string;targetId:string;cell:Hex;personal:boolean;
  startedAt:number;dueAt:number;total:number;completed:number;
};
type Target=Hex&{id:string};
type LootTarget=Target&{carrierId:string|null;battleQ:number|null;battleR:number|null};
export type ProgressContext={
  me:string;version:string;world:Hex;sceneId:string|null;actor:Hex;
  objects:readonly Target[];loot:readonly LootTarget[];
};

export function actionProgress(action:ActionProgress|null|undefined,clock:number){
  if(!action)return null;
  const duration=action.dueAt-action.startedAt;
  if(!Number.isFinite(duration)||duration<=0)return null;
  return {
    ...action,short:duration<=10000,
    fraction:Math.max(0,Math.min(1,(clock-action.startedAt)/duration)),
    remaining:Math.max(0,Math.ceil((action.dueAt-clock)/1000)),
  };
}

export function productionProgress(task:Task|null|undefined,context:ProgressContext):ActionProgress|null{
  if(!task||(task.actor&&task.actor!==context.me)||
    (task.version&&task.version!==context.version)||
    (task.sceneId??null)!==context.sceneId||
    (task.worldQ!=null&&task.worldQ!==context.world.q)||
    (task.worldR!=null&&task.worldR!==context.world.r))return null;
  let targetId=context.me,cell={q:context.actor.q,r:context.actor.r},personal=true;
  if(task.objectId){
    const object=context.objects.find(candidate=>candidate.id===task.objectId);
    if(!object)return null;
    targetId=object.id;cell={q:object.q,r:object.r};personal=false;
  }else if(task.corpseId){
    const corpse=context.loot.find(candidate=>candidate.id===task.corpseId);
    if(!corpse)return null;
    if(!corpse.carrierId){
      if(context.sceneId&&(corpse.battleQ==null||corpse.battleR==null))return null;
      targetId=corpse.id;
      cell=context.sceneId?{q:corpse.battleQ!,r:corpse.battleR!}:{q:corpse.q,r:corpse.r};
      personal=false;
    }else if(corpse.carrierId!==context.me)return null;
  }
  return {
    id:`production:${context.me}:${task.startedAt}:${task.completed}`,
    kind:task.kind,targetId,cell,personal,
    startedAt:task.startedAt,dueAt:task.dueAt,total:task.total,completed:task.completed,
  };
}

export function worldProgress(action:WorldAction|undefined,me:string,resources:readonly Target[]):ActionProgress|null{
  if(!action||action.accountId!==me)return null;
  const node=resources.find(candidate=>candidate.id===action.nodeId);
  if(!node)return null;
  return {
    id:`world:${me}:${action.startedAt}`,kind:action.kind,targetId:node.id,
    cell:{q:node.q,r:node.r},personal:false,
    startedAt:action.startedAt,dueAt:action.endsAt,total:1,completed:0,
  };
}
