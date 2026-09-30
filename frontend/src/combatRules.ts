import type {Hex,Weapon} from './types';
export const directions:Hex[]=[{q:1,r:0},{q:0,r:1},{q:-1,r:1},{q:-1,r:0},{q:0,r:-1},{q:1,r:-1}];
export const hexDistance=(a:Hex,b:Hex)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
export function attackCells(w:Weapon,from:Hex,target:Hex):Hex[]{
 const d=hexDistance(from,target);if(d<w.minRange||d>w.maxRange)return [];
 if(w.shape==='single')return [target];
 const index=directions.findIndex(v=>Array.from({length:w.maxRange},(_,i)=>i+1).some(n=>from.q+v.q*n===target.q&&from.r+v.r*n===target.r));
 if(index<0)return [];const v=directions[index];
 if(w.shape==='line')return Array.from({length:w.maxRange-w.minRange+1},(_,i)=>({q:from.q+v.q*(i+w.minRange),r:from.r+v.r*(i+w.minRange)}));
 const next=directions[(index+1)%6];return [{q:from.q+v.q,r:from.r+v.r},{q:from.q+next.q,r:from.r+next.r}];
}
