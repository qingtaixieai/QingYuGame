import type {Graphics} from 'pixi.js';
import {hexPath,paintGround} from './mapPaint';

export const worldTerrainColors:Record<string,number>={ocean:0xa5cdd0,plain:0xb8cf8b,forest:0x9fbe7c,hill:0xb6c18b,mountain:0xa8b7a0,beach:0xded7a1,river:0x86bfc7,bridge:0x86bfc7};
export const hex=hexPath;
export function pixel(g:Graphics,x:number,y:number,w:number,h:number,c:number|string){g.rect(Math.round(x),Math.round(y),w,h).fill(c);}

export function drawWorldGround(g:Graphics,terrain:string,x:number,y:number,n:number,size=25,orientation?:number,alpha=1,padding=.6){
  paintGround(g,x,y,{radius:size,orientation,padding,alpha,color:worldTerrainColors[terrain]??worldTerrainColors.plain,
    insetColor:['plain','forest','hill'].includes(terrain)?n<3?0xe1e8a7:0x688d62:undefined,insetAlpha:.06*alpha});
  if(terrain==='ocean'&&n===1){pixel(g,x-7,y,10,1,0xb8d9d7);pixel(g,x+1,y+3,6,1,0xb8d9d7);}
}
export function drawWorldFerry(g:Graphics){
  pixel(g,-19,4,38,5,0x6e5945);pixel(g,-15,9,30,5,0x84684c);pixel(g,-10,14,20,3,0x94734d);pixel(g,-13,2,26,5,0xd3b17a);pixel(g,-1,-28,3,34,0x6f6551);pixel(g,3,-25,15,20,0xfff0cf);pixel(g,3,-25,10,4,0xdca55b);pixel(g,-12,-16,11,15,0xf0ddb0);
}
export function drawWorldMonster(g:Graphics,species:string|null){
  g.clear();g.ellipse(0,9,13,5).fill({color:0x365443,alpha:.25});if(species==='archer'){pixel(g,-9,-8,18,19,0x3e6670);
  pixel(g,-7,-23,14,14,0xdfc69b);
  pixel(g,-10,-28,20,7,0x53624a);
  pixel(g,-8,10,6,7,0x344438);
  pixel(g,3,10,6,7,0x344438);
  pixel(g,14,-18,3,28,0xb78c51);
  pixel(g,17,-12,3,17,0xb78c51);
  pixel(g,-5,-1,28,2,0xdfe4cc);}else if(species==='claw'){pixel(g,-13,-8,26,15,0x5c6b3d);
  pixel(g,-8,-4,16,10,0x77854e);
  pixel(g,-16,-9,6,10,0x4c5934);
  pixel(g,10,-9,6,10,0x4c5934);
  pixel(g,-15,-8,4,2,0x8fa35c);
  pixel(g,11,-6,3,2,0x8fa35c);
  pixel(g,-18,-8,5,16,0x4c5934);
  pixel(g,13,-8,5,16,0x4c5934);
  pixel(g,-11,6,8,9,0x3f4a30);
  pixel(g,3,6,8,9,0x3f4a30);
  pixel(g,-13,13,11,3,0x2f3826);
  pixel(g,2,13,11,3,0x2f3826);
  pixel(g,-18,7,2,5,0xede1bc);
  pixel(g,-16,7,2,5,0xede1bc);
  pixel(g,-14,7,2,5,0xede1bc);
  pixel(g,12,7,2,5,0xede1bc);
  pixel(g,14,7,2,5,0xede1bc);
  pixel(g,16,7,2,5,0xede1bc);
  pixel(g,-7,-19,14,10,0x6c7a48);
  pixel(g,-9,-23,4,5,0x4c5934);
  pixel(g,5,-23,4,5,0x4c5934);
  pixel(g,-5,-16,3,3,0xffce69);
  pixel(g,2,-16,3,3,0xffce69);
  pixel(g,-5,-11,10,2,0x3a2a22);
  pixel(g,-4,-10,2,3,0xede1bc);
  pixel(g,2,-10,2,3,0xede1bc);}else{pixel(g,-10,-8,20,17,0xa83c34);
  pixel(g,-7,-19,14,14,0xc96a56);
  pixel(g,-10,-23,5,9,0xede0bb);
  pixel(g,5,-23,5,9,0xede0bb);
  pixel(g,-5,-14,3,3,0xffd071);
  pixel(g,3,-14,3,3,0xffd071);
  pixel(g,-14,-5,5,15,0x8a322c);
  pixel(g,9,-5,5,15,0x8a322c);
  pixel(g,-7,9,5,5,0x493c34);
  pixel(g,3,9,5,5,0x493c34);}
}

export function tree(g:Graphics,x:number,y:number,variant:number){
  g.ellipse(x,y+9,10,4).fill({color:0x53694f,alpha:.17});pixel(g,x-2,y,4,10,0x7c7853);
  const c=variant?0x739568:0x5a8866;
  pixel(g,x-7,y-12,14,17,0x52795c);pixel(g,x-10,y-7,20,9,c);pixel(g,x-6,y-16,12,18,c);
  pixel(g,x-3,y-18,7,5,variant?0x9bb478:0x82a275);pixel(g,x-7,y-8,5,7,variant?0x9bb478:0x82a275);
}

export function drawMountain(g:Graphics,x:number,y:number){
  const steps=[[-17,7,34,7],[-14,0,28,8],[-10,-8,20,9],[-7,-15,14,8],[-3,-22,6,8]];
  for(const [dx,dy,w,h] of steps)pixel(g,x+dx,y+dy,w,h,0x7e8c83);
  for(const [dx,dy,w,h] of steps.slice(1))pixel(g,x+dx,y+dy,Math.ceil(w/2),h,0xa1aaa0);
  pixel(g,x-3,y-22,6,8,0xe9ead6);pixel(g,x-7,y-15,14,4,0xe9ead6);pixel(g,x-7,y-11,5,3,0xe9ead6);
}
export function drawHouse(g:Graphics,x:number,y:number,big:boolean,roof:readonly (string|number)[]=[0x795342,0xb87550,0xcc9160,0xddad76]){
  g.ellipse(x,y+10,big?18:13,5).fill({color:0x59694f,alpha:.2});
  pixel(g,x-11,y-4,22,15,0x8e7355);pixel(g,x-9,y-5,18,14,0xf4e8bc);
  pixel(g,x-4,y+2,6,9,0x685d47);pixel(g,x+5,y-1,3,4,0x769396);pixel(g,x-8,y-1,3,4,0x769396);
  pixel(g,x-15,y-8,30,4,roof[0]);pixel(g,x-12,y-12,24,5,roof[1]);pixel(g,x-9,y-16,18,5,roof[2]);pixel(g,x-5,y-19,10,4,roof[3]);
  pixel(g,x+6,y-22,4,9,0x8e7355);
  if(big){pixel(g,x-20,y-6,7,19,0x9c9e88);pixel(g,x+14,y-6,7,19,0x9c9e88);pixel(g,x-21,y-9,9,4,0xd5d5b3);pixel(g,x+13,y-9,9,4,0xd5d5b3);}
}
export function drawRuin(g:Graphics,x:number,y:number){
  g.ellipse(x,y+8,17,6).fill({color:0x637151,alpha:.2});
  for(const [dx,dy,h] of [[-11,-10,19],[7,-15,24],[-2,2,7]]){pixel(g,x+dx,y+dy,6,h,0x8f9b8b);pixel(g,x+dx,y+dy,3,h,0xc6c9ac);pixel(g,x+dx-1,y+dy-2,8,3,0xd8d7b5);}
  pixel(g,x-12,y+8,10,3,0x7d9b61);pixel(g,x+3,y+5,6,4,0x7d9b61);
}

export function drawTerrainProp(g:Graphics,x:number,y:number,terrain:string,n:number,road=false){
        if(terrain==='mountain')drawMountain(g,x,y);
        if(terrain==='hill'){pixel(g,x-13,y+2,26,5,0x9eac7b);pixel(g,x-9,y-3,18,6,0xcbd0a0);pixel(g,x-4,y-6,8,4,0xd8d9ae);}
        if(terrain==='plain'&&!road){if(n===0)tree(g,x,y,1);else if(n===2){pixel(g,x-6,y-1,2,3,0xe8e5b5);pixel(g,x+5,y+3,2,3,0xe8e5b5);pixel(g,x-5,y+3,2,2,0x94b376);}}
        if(terrain==='bridge'){pixel(g,x-17,y-8,34,16,0x997751);for(let i=0;i<8;i++)pixel(g,x-15+i*4,y-7,3,14,0xd6b982);pixel(g,x-19,y-11,38,3,0x84664a);pixel(g,x-19,y+8,38,3,0x84664a);}
}

export function drawWorldActor(g:Graphics,{color,online,kind,species}:{color:string;online:boolean;kind:string;species:string|null},own=false){
  if(kind==='monster'){drawWorldMonster(g,species);return;}
  g.clear();g.ellipse(0,8,9,4).fill({color:0x365443,alpha:.25});
              if(own)g.circle(0,2,14).stroke({color:0xfdf7d1,width:2});
              const c=online?Number.parseInt(color.slice(1),16):0x929d97;
              pixel(g,-6,-5,12,12,c);pixel(g,-5,-14,10,10,0xf1d3a3);pixel(g,-7,-17,14,6,c);pixel(g,-4,7,3,5,0x54594b);pixel(g,2,7,3,5,0x54594b);pixel(g,-2,-10,1,2,0x5a5748);pixel(g,3,-10,1,2,0x5a5748);
}
