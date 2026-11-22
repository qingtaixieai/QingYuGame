import {Container,Graphics,Text} from 'pixi.js';
import type {ActionResult,Hex,LootContainer} from '../shared/protocol';
import {actionProgress,type ActionProgress} from '../shared/actionProgress';

type Point={x:number;y:number};
type Target={id:string;cell:Hex;offset:Point;visible:boolean};
export type FeedbackFrame={clock:number;progress:ActionProgress|null;results:readonly ActionResult[];me:string;version:string;sceneId:string|null;world:Hex;actor:Hex;loot:readonly LootContainer[];targets:readonly Target[]};
// Both maps project into their existing world container, so pan and zoom stay native.
export function createActionFeedback(layer:Container,project:(cell:Hex)=>Point){
  const bar=layer.addChild(new Container()),shape=bar.addChild(new Graphics());
  const caption=bar.addChild(new Text({text:'',style:{fontFamily:'Microsoft YaHei',fontSize:10,fill:0xfff4cb,stroke:{color:0x293d35,width:3}}}));
  caption.anchor.set(.5,1);caption.y=-6;
  const entries=new Map<string,{node:Container;flash:Graphics;text:Text}>();
  function anchor(cell:Hex,id:string,personal:boolean,frame:FeedbackFrame){
    const p=project(cell),target=frame.targets.find(t=>t.id===id);
    const occupiedBySelf=cell.q===frame.actor.q&&cell.r===frame.actor.r;
    return {x:p.x+(!personal&&target?.visible?target.offset.x:0),y:p.y+(!personal&&target?.visible?target.offset.y:0)-(occupiedBySelf?76:personal?60:32)};
  }
  return (frame:FeedbackFrame)=>{
    const progress=actionProgress(frame.progress,frame.clock);
    bar.visible=!!progress?.short;
    if(progress?.short){
      const point=anchor(progress.cell,progress.targetId,progress.personal,frame);bar.position.set(point.x,point.y);
      bar.scale.set(Math.min(2.5,Math.max(1,1/Math.max(.1,Math.hypot(layer.worldTransform.a,layer.worldTransform.b)))));
      shape.clear().roundRect(-24,0,48,7,2).fill(0x354d40);
      if(progress.fraction>0)shape.roundRect(-23,1,46*progress.fraction,5,1).fill(0xe8c875);
      caption.text=`${progress.remaining}秒${progress.total>1?` · ${progress.completed+1}/${progress.total}`:''}`;
    }
    const active=new Set<string>();
    for(const result of frame.results){
      const age=frame.clock-result.happenedAt;
      if(age<0||age>2400||result.actor!==frame.me||result.version!==frame.version||result.sceneId!==frame.sceneId||
        result.worldQ!==frame.world.q||result.worldR!==frame.world.r)continue;
      const targetId=result.objectId??result.corpseId??frame.me;
      const target=frame.targets.find(t=>t.id===targetId),corpse=frame.loot.find(t=>t.id===result.corpseId);
      const declaredPersonal=result.personal;
      const personal=declaredPersonal??(!result.objectId&&!result.corpseId||corpse?.carrierId===frame.me);
      // A consumed corpse can be gone from state; the settled result retains its actual coordinates.
      const corpseCell=corpse&&!corpse.carrierId?(frame.sceneId?(corpse.battleQ!=null&&corpse.battleR!=null?{q:corpse.battleQ,r:corpse.battleR}:null):corpse):null;
      const cell=personal?frame.actor:target?.cell??corpseCell??(result.q!=null&&result.r!=null?{q:result.q,r:result.r}:null);
      if(!cell)continue;
      active.add(result.id);
      let entry=entries.get(result.id);
      if(!entry){
        const node=layer.addChild(new Container()),flash=node.addChild(new Graphics());
        const color=result.outcome==='success'?0xffe99a:result.outcome==='failure'?0xff796e:0xc2c5c2;
        const text=node.addChild(new Text({text:result.outcome==='cancelled'?'已停止':result.message,style:{fontFamily:'Microsoft YaHei',fontSize:12,fontWeight:'bold',fill:color,stroke:{color:0x293d35,width:3}}}));text.anchor.set(.5,1);
        flash.circle(0,22,17).fill({color,alpha:.5});
        entry={node,flash,text};entries.set(result.id,entry);
      }
      const point=anchor(cell,targetId,personal,frame);entry.node.position.set(point.x,point.y);
      // Keep settled output above the next batch's caption instead of covering it.
      entry.flash.alpha=Math.max(0,1-age/550);entry.text.y=-26-age/120;
      entry.text.alpha=Math.min(1,(2400-age)/500);
    }
    for(const [id,entry] of entries)if(!active.has(id)){entry.node.destroy({children:true});entries.delete(id);}
    return !!progress?.short||active.size>0;
  };
}
