import type {Graphics} from 'pixi.js';

// Shared Pixi primitives from the Claude Sonnet 5.5 draft.
// Pass the orientation of the caller's grid without changing its coordinates.
export const HEX_MAP=-Math.PI/6;
export const HEX_SCENE=0;
export const PLANK_PITCH=Math.sqrt(3)*36/4;

export function hexPath(g:Graphics,x:number,y:number,radius:number,orientation:number=HEX_MAP){
  return g.poly(Array.from({length:6},(_,i)=>{
    const a=orientation===HEX_MAP?Math.PI/180*(60*i-30):i*Math.PI/3+orientation;
    return [x+radius*Math.cos(a),y+radius*Math.sin(a)];
  }).flat());
}

export type GroundStyle={radius:number;orientation?:number;color:number|string;padding?:number;insetColor?:number;insetAlpha?:number;alpha?:number};

/** 外圈 radius+.6 主色 + 内圈 radius-1 薄色块，保留 worldArt 原有的尺寸叠层。 */
export function paintGround(g:Graphics,x:number,y:number,style:GroundStyle){
  const {radius,orientation=HEX_MAP,color,alpha=1}=style;
  hexPath(g,x,y,radius+(style.padding??.6),orientation).fill({color,alpha});
  if(style.insetColor!==undefined)hexPath(g,x,y,radius-1,orientation).fill({color:style.insetColor,alpha:style.insetAlpha??.06});
}

/** 相邻格心之间的一段路：8/5 双层，scale 供二三级放大。 */
export function paintRoad(g:Graphics,a:{x:number;y:number},b:{x:number;y:number},scale=1,rounded=false){
  for(const [width,color] of [[8,0xd1c690],[5,0xe6d6a2]] as const)
    g.moveTo(a.x,a.y).lineTo(b.x,b.y).stroke({width:width*scale,color,...(rounded?{cap:'round' as const}:{})});
}

/** 一块横板：y 吸附到全局板缝，同一行在室内外永远同缝。 */
export function paintPlank(g:Graphics,x:number,y:number,width:number,color:number|string){
  const row=Math.round(y/PLANK_PITCH)*PLANK_PITCH,left=Math.round(x-width/2),w=Math.round(width),h=Math.round(PLANK_PITCH)-2,top=Math.round(row-h/2);
  g.rect(left,top,w,h).fill(color);
  g.rect(left,top,w,1).fill({color:0xf0dcb4,alpha:.35});
  g.rect(left,top+h-1,w,1).fill({color:0x8f7350,alpha:.45});
}
