import type {FerryState} from './types';

// Continue across known route segments while a snapshot is in flight, but never
// predict departure from a dock or change the server-owned passenger position.
export function ferryRouteProgress(ferry:Pick<FerryState,'built'|'phase'|'nextAt'|'travelMs'>,index:number,length:number,now:number):number{
  const last=Math.max(0,length-1),start=Math.max(0,Math.min(last,index));
  if(!ferry.built||last===0||ferry.travelMs<=0||(ferry.phase!=='outbound'&&ferry.phase!=='inbound'))return start;
  const step=ferry.travelMs/last,traveled=Math.max(0,1+(now-ferry.nextAt)/step);
  return Math.max(0,Math.min(last,start+(ferry.phase==='outbound'?1:-1)*traveled));
}
