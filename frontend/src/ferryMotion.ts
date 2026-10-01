import type {FerryState} from './types';

// Continue across known route segments while a snapshot is in flight, but never
// predict departure from a dock or change the server-owned passenger position.
export function ferryRouteProgress(ferry:Pick<FerryState,'built'|'phase'|'nextAt'|'travelMs'>,index:number,length:number,now:number):number{
  const last=Math.max(0,length-1),start=Math.max(0,Math.min(last,index));
  if(!ferry.built||last===0||ferry.travelMs<=0||(ferry.phase!=='outbound'&&ferry.phase!=='inbound'))return start;
  const step=ferry.travelMs/last,traveled=Math.max(0,1+(now-ferry.nextAt)/step);
  return Math.max(0,Math.min(last,start+(ferry.phase==='outbound'?1:-1)*traveled));
}

// A network delay must not rewind the boat. Follow the server clock gradually
// during ordinary frames; after background suspension resume at server time.
export function advanceRenderClock(previous:number,elapsed:number,target:number):number{
  if(elapsed>1000)return target;
  const step=Math.max(0,elapsed),predicted=previous+step;
  return predicted+Math.max(-step*.1,Math.min(step*.1,target-predicted));
}
