import type {Hex} from '../../types';

export const SIZE=36;
export const xy=(h:Hex)=>({x:1.5*SIZE*h.q,y:Math.sqrt(3)*SIZE*(h.r+h.q/2)});
export const distance=(a:Hex,b:Hex)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
export const sides=(h:Hex,r:number)=>[h.q,h.q+h.r,h.r,-h.q,-h.q-h.r,-h.r].flatMap((n,i)=>n===r?[i]:[]);
export function polygon(h:Hex,r=SIZE-1){const p=xy(h);return Array.from({length:6},(_,i)=>{const a=i*Math.PI/3;return `${p.x+r*Math.cos(a)},${p.y+r*Math.sin(a)}`;}).join(' ');}
export function tiles(radius:number){const result:Hex[]=[];for(let r=-radius;r<=radius;r++)for(let q=-radius;q<=radius;q++)if(distance({q:0,r:0},{q,r})<=radius)result.push({q,r});return result;}
