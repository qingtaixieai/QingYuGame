import {Graphics,Text,type Container} from 'pixi.js';
import type {Hex} from './types';

type Offset={x:number;y:number};
export type CellInteraction={
  id:string;
  kind:'place'|'ferry'|'door'|'resource'|'facility'|'loot'|'service';
  label:string;
  cell:Hex;
  offset?:Offset;
  order?:number;
};

const cellKey=(h:Hex)=>`${h.q},${h.r}`;
const pinRadius=7;
// Optional per-instance adjustments in map pixel units, keyed by the registered component ID.
export const interactionOffsets:Readonly<Record<string,Offset>>={};
const colors={place:0x63836b,ferry:0x568493,door:0x9c7552,resource:0x668453,facility:0x9b7952,loot:0x8d6591,service:0xa18444};

export function arrangeInteractions(items:readonly CellInteraction[]){
  const groups=new Map<string,CellInteraction[]>();
  for(const item of items){
    const key=cellKey(item.cell);
    const group=groups.get(key);
    if(group)group.push(item);
    else groups.set(key,[item]);
  }
  return [...groups.values()].flatMap(group=>{
    const sorted=group.sort((a,b)=>(a.order??0)-(b.order??0)||a.id.localeCompare(b.id));
    return sorted.map((item,index)=>{
      const manual=item.offset??interactionOffsets[item.id];
      const pair=sorted.length===2;
      const offset=manual??{x:(index%2===0?-8:8),y:pair?5:index<2?-5:11};
      // Larger stacks use the complete list in the place panel to avoid covering adjacent tiles.
      return {...item,number:index+1,visible:!!manual||sorted.length>1&&index<4,offset};
    });
  });
}

export type ArrangedInteraction=ReturnType<typeof arrangeInteractions>[number];
type Project=(cell:Hex)=>Offset;

export function paintInteractionPins(layer:Container,items:readonly ArrangedInteraction[],project:Project,selected?:string|null){
  const graphics=layer.addChild(new Graphics());
  for(const item of items){
    if(!item.visible)continue;
    const center=project(item.cell);
    const x=center.x+item.offset.x,y=center.y+item.offset.y;
    graphics.circle(x,y,pinRadius).fill(colors[item.kind])
      .stroke({color:item.id===selected?0xffda72:0xfff3d2,width:item.id===selected?2.6:1.4});
    const label=layer.addChild(new Text({text:String(item.number),style:{fontSize:9,fill:0xfff9e7,fontWeight:'bold'}}));
    label.anchor.set(.5);label.position.set(x,y);
  }
}

export function hitInteraction(items:readonly ArrangedInteraction[],point:Offset,project:Project){
  for(let i=items.length-1;i>=0;i--){
    const item=items[i];
    if(!item.visible)continue;
    const center=project(item.cell);
    if(Math.hypot(point.x-center.x-item.offset.x,point.y-center.y-item.offset.y)<=pinRadius+1)return item;
  }
}
