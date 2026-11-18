import {Graphics} from 'pixi.js';

// Original procedural pixel art. The origin is the impact point.
export const METEOR_FALL_MS=760;
const FALL=METEOR_FALL_MS,END=1440;
export function meteor(g:Graphics,x:number,y:number,top:number,age:number,reduced:boolean){
  if(age<0||age>=END)return false;
  const rect=(dx:number,dy:number,w:number,h:number,color:number,alpha=1)=>
    g.rect(Math.round(dx),Math.round(dy),w,h).fill({color,alpha});
  const travel=reduced?1:Math.min(1,age/FALL);
  if(travel<1){
    const d=1-travel*travel,cx=x-140*d,cy=y+(top-y-40)*d;
    for(let i=5;i>=1;i--)rect(cx-i*6,cy-i*10,10+i,5,0xff823c,1-i/7);
    rect(cx-10,cy-7,20,15,0x73372c);rect(cx-7,cy-10,14,18,0xaa5134);
    rect(cx-6,cy-5,12,10,0xffac4c);rect(cx-2,cy-3,7,5,0xffe6a1);
  }else{
    const t=Math.min(1,(age-(reduced?0:FALL))/(END-FALL));
    const alpha=1-t;
    if(alpha>0){
      g.circle(x,y,7+26*t).stroke({color:0xffce7c,width:3,alpha});
      rect(x-6,y-5,12,10,0xffe3a0,alpha);
      for(let i=0;i<7;i++){
        const a=i*Math.PI*2/7,d=6+25*t;
        rect(x+Math.cos(a)*d,y+Math.sin(a)*d,4,3,i%2?0xf78548:0xffc16a,alpha);
      }
    }
  }
  return true;
}
