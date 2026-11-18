export function exitRingOffset(age:number,index:number,reduced:boolean){
  const t=reduced?1:Math.min(1,Math.max(0,(age-(index%9)*12)/230));
  return {lift:(1-t)*(1-t)*24,alpha:.35+.65*t};
}
