import { useEffect, useRef } from 'react';
import { Application, Container, Graphics, Text } from 'pixi.js';
// Pixi's official non-eval polyfills keep WebGL rendering compatible with production CSP.
import 'pixi.js/unsafe-eval';
import type { Hex, Player, Tile, World, ResourceNode, WorldAction, Emote } from './types';
import {emoteArt,emotePalette} from './emotes';
import { key } from './types';

const SIZE=25, SQRT3=Math.sqrt(3);
export const position=(h:Hex)=>({x:SQRT3*SIZE*(h.q+h.r/2),y:1.5*SIZE*h.r});
const colors:Record<string,number>={ocean:0xa5cdd0,plain:0xb8cf8b,forest:0x9fbe7c,hill:0xb6c18b,mountain:0xa8b7a0,beach:0xded7a1,river:0x86bfc7,bridge:0x86bfc7};
function hex(g:Graphics,x:number,y:number,r:number){return g.poly(Array.from({length:6},(_,i)=>{const a=Math.PI/180*(60*i-30);return [x+r*Math.cos(a),y+r*Math.sin(a)]}).flat());}
function pixel(g:Graphics,x:number,y:number,w:number,h:number,c:number){g.rect(Math.round(x),Math.round(y),w,h).fill(c);}
function tree(g:Graphics,x:number,y:number,variant:number){
  g.ellipse(x,y+9,10,4).fill({color:0x53694f,alpha:.17});pixel(g,x-2,y,4,10,0x7c7853);
  const c=variant?0x739568:0x5a8866;
  pixel(g,x-7,y-12,14,17,0x52795c);pixel(g,x-10,y-7,20,9,c);pixel(g,x-6,y-16,12,18,c);
  pixel(g,x-3,y-18,7,5,variant?0x9bb478:0x82a275);pixel(g,x-7,y-8,5,7,variant?0x9bb478:0x82a275);
}
function mountain(g:Graphics,x:number,y:number){
  const steps=[[-17,7,34,7],[-14,0,28,8],[-10,-8,20,9],[-7,-15,14,8],[-3,-22,6,8]];
  for(const [dx,dy,w,h] of steps)pixel(g,x+dx,y+dy,w,h,0x7e8c83);
  for(const [dx,dy,w,h] of steps.slice(1))pixel(g,x+dx,y+dy,Math.ceil(w/2),h,0xa1aaa0);
  pixel(g,x-3,y-22,6,8,0xe9ead6);pixel(g,x-7,y-15,14,4,0xe9ead6);pixel(g,x-7,y-11,5,3,0xe9ead6);
}
function house(g:Graphics,x:number,y:number,big:boolean){
  g.ellipse(x,y+10,big?18:13,5).fill({color:0x59694f,alpha:.2});
  pixel(g,x-11,y-4,22,15,0x8e7355);pixel(g,x-9,y-5,18,14,0xf4e8bc);
  pixel(g,x-4,y+2,6,9,0x685d47);pixel(g,x+5,y-1,3,4,0x769396);pixel(g,x-8,y-1,3,4,0x769396);
  pixel(g,x-15,y-8,30,4,0x795342);pixel(g,x-12,y-12,24,5,0xb87550);pixel(g,x-9,y-16,18,5,0xcc9160);pixel(g,x-5,y-19,10,4,0xddad76);
  pixel(g,x+6,y-22,4,9,0x8e7355);
  if(big){pixel(g,x-20,y-6,7,19,0x9c9e88);pixel(g,x+14,y-6,7,19,0x9c9e88);pixel(g,x-21,y-9,9,4,0xd5d5b3);pixel(g,x+13,y-9,9,4,0xd5d5b3);}
}
function ruin(g:Graphics,x:number,y:number){
  g.ellipse(x,y+8,17,6).fill({color:0x637151,alpha:.2});
  for(const [dx,dy,h] of [[-11,-10,19],[7,-15,24],[-2,2,7]]){pixel(g,x+dx,y+dy,6,h,0x8f9b8b);pixel(g,x+dx,y+dy,3,h,0xc6c9ac);pixel(g,x+dx-1,y+dy-2,8,3,0xd8d7b5);}
  pixel(g,x-12,y+8,10,3,0x7d9b61);pixel(g,x+3,y+5,6,4,0x7d9b61);
}
export type MapControls={zoom:(factor:number)=>void;fit:()=>void;locate:(h:Hex)=>void};
type Props={world:World;emotes:Emote[];resources:ResourceNode[];actions:WorldAction[];clockOffset:number;players:Player[];me:string;selected:Tile|null;route:Hex[];onSelect:(tile:Tile)=>void;onZoom:(zoom:number)=>void;controls:React.RefObject<MapControls|null>};
export function MapView(props:Props){
  const host=useRef<HTMLDivElement>(null), latest=useRef(props);
  latest.current=props;
  useEffect(()=>{
    let disposed=false,cleanup=()=>{};
    const app=new Application();
    async function init(){
      await app.init({resizeTo:host.current!,background:0xa5cdd0,antialias:false,resolution:Math.min(devicePixelRatio,2),autoDensity:true,preference:'webgl'});
      if(disposed){app.destroy(true,{children:true});return;}
      host.current!.appendChild(app.canvas);
      const scene=new Container(),ground=new Graphics(),roads=new Graphics(),decor=new Graphics(),markers=new Container(),selection=new Graphics(),routeLine=new Graphics(),actors=new Container();
      app.stage.addChild(scene);const resourceLayer=new Container(),effects=new Container();
      scene.addChild(ground,roads,decor,resourceLayer,markers,routeLine,selection,actors,effects);
      const map=props.world,index=new Map(map.tiles.map(t=>[key(t),t]));
      for(const t of map.tiles){
        const {x,y}=position(t),n=Math.abs(t.q*137+t.r*73)%7;
        hex(ground,x,y,SIZE+.6).fill(colors[t.terrain]);
        if(['plain','forest','hill'].includes(t.terrain))hex(ground,x,y,SIZE-1).fill({color:n<3?0xe1e8a7:0x688d62,alpha:.06});
        if(t.terrain==='ocean'&&n===1){pixel(ground,x-7,y,10,1,0xb8d9d7);pixel(ground,x+1,y+3,6,1,0xb8d9d7);}
        if(t.road) for(const d of [[1,0],[0,1],[1,-1]]){
          const adjacent=index.get(`${t.q+d[0]},${t.r+d[1]}`);if(!adjacent?.road)continue;
          const b=position(adjacent);roads.moveTo(x,y).lineTo(b.x,b.y).stroke({width:8,color:0xd1c690});roads.moveTo(x,y).lineTo(b.x,b.y).stroke({width:5,color:0xe6d6a2});
        }
        if(t.terrain==='river'){
          for(const d of [[1,0],[0,1],[1,-1]]){const other=index.get(`${t.q+d[0]},${t.r+d[1]}`);if(other&&['river','bridge'].includes(other.terrain)){const b=position(other);ground.moveTo(x,y).lineTo(b.x,b.y).stroke({width:21,color:0x86bfc7});}}
          pixel(decor,x-5,y-6,11,1,0xa4d4d4);pixel(decor,x+3,y+4,8,1,0xa4d4d4);
        }
      }
      for(const t of [...map.tiles].sort((a,b)=>a.r-b.r)){
        const {x,y}=position(t),n=Math.abs(t.q*137+t.r*73)%7;
        if(t.place){
          if(t.place.type==='ruin')ruin(decor,x,y);else house(decor,x,y,t.place.type==='town');
          const text=new Text({text:t.place.name,style:{fontFamily:'Microsoft YaHei, sans-serif',fontSize:11,fontWeight:'600',fill:0x384e40,stroke:{color:0xf2f0d8,width:3}}});
          text.anchor.set(.5,0);text.position.set(x,y+15);markers.addChild(text);continue;
        }

        if(t.terrain==='mountain')mountain(decor,x,y);
        if(t.terrain==='hill'){pixel(decor,x-13,y+2,26,5,0x9eac7b);pixel(decor,x-9,y-3,18,6,0xcbd0a0);pixel(decor,x-4,y-6,8,4,0xd8d9ae);}
        if(t.terrain==='plain'&&!t.road){if(n===0)tree(decor,x,y,1);else if(n===2){pixel(decor,x-6,y-1,2,3,0xe8e5b5);pixel(decor,x+5,y+3,2,3,0xe8e5b5);pixel(decor,x-5,y+3,2,2,0x94b376);}}
        if(t.terrain==='bridge'){pixel(decor,x-17,y-8,34,16,0x997751);for(let i=0;i<8;i++)pixel(decor,x-15+i*4,y-7,3,14,0xd6b982);pixel(decor,x-19,y-11,38,3,0x84664a);pixel(decor,x-19,y+8,38,3,0x84664a);}
      }
      for(const [title,x,y] of [['西 静 海',-625,80],['东 风 海',590,-40]] as const){const label=new Text({text:title,style:{fontFamily:'serif',fontSize:20,fill:0x659b9f,letterSpacing:8}});label.anchor.set(.5);label.position.set(x,y);scene.addChild(label);}
      let zoom=1,fitScale=1;
      function report(){latest.current.onZoom(Math.round(zoom/fitScale*100));}
      function fit(){fitScale=Math.min(app.screen.width/1530,app.screen.height/1300);zoom=fitScale;scene.scale.set(zoom);scene.position.set(app.screen.width/2,app.screen.height/2+5);report();}
      function zoomAt(factor:number,x=app.screen.width/2,y=app.screen.height/2){const next=Math.max(fitScale*.65,Math.min(fitScale*5,zoom*factor));const ratio=next/zoom;scene.x=x-(x-scene.x)*ratio;scene.y=y-(y-scene.y)*ratio;zoom=next;scene.scale.set(zoom);report();}
      props.controls.current={zoom:factor=>zoomAt(factor),fit,locate:h=>{const p=position(h);if(zoom<fitScale*1.7){zoom=fitScale*1.7;scene.scale.set(zoom);}scene.position.set(app.screen.width/2-p.x*zoom,app.screen.height/2-p.y*zoom);report();}};
      fit();
      let start:{x:number;y:number;sx:number;sy:number}|null=null;
      const canvas=app.canvas;
      function point(e:PointerEvent){const rect=canvas.getBoundingClientRect();return {x:e.clientX-rect.left,y:e.clientY-rect.top};}
      function down(e:PointerEvent){if(e.button!==0)return;const p=point(e);start={...p,sx:scene.x,sy:scene.y};canvas.setPointerCapture(e.pointerId);canvas.style.cursor='grabbing';}
      function move(e:PointerEvent){if(start){const p=point(e);scene.position.set(start.sx+p.x-start.x,start.sy+p.y-start.y);}}
      function up(e:PointerEvent){if(!start)return;const p=point(e),distance=Math.hypot(p.x-start.x,p.y-start.y);start=null;canvas.style.cursor='grab';if(distance>6)return;
        const local=scene.toLocal(p);const rf=local.y/(1.5*SIZE),qf=local.x/(SQRT3*SIZE)-rf/2;
        let q=Math.round(qf),r=Math.round(rf),s=Math.round(-qf-rf);const dq=Math.abs(q-qf),dr=Math.abs(r-rf),ds=Math.abs(s+qf+rf);
        if(dq>dr&&dq>ds)q=-r-s;else if(dr>ds)r=-q-s;
        const tile=index.get(`${q},${r}`);if(tile)latest.current.onSelect(tile);
      }
      function wheel(e:WheelEvent){e.preventDefault();const rect=canvas.getBoundingClientRect();zoomAt(Math.exp(-e.deltaY*.0012),e.clientX-rect.left,e.clientY-rect.top);}
      canvas.style.cursor='grab';canvas.addEventListener('pointerdown',down);canvas.addEventListener('pointermove',move);canvas.addEventListener('pointerup',up);canvas.addEventListener('pointercancel',()=>{start=null;});canvas.addEventListener('wheel',wheel,{passive:false});
      const entities=new Map<string,{container:Container;body:Graphics;bar:Graphics;bubble:Graphics;emoteCode:string;label:Text;target:{x:number;y:number};color:string;online:boolean}>();
      let previousPlayers:Player[]|null=null,previousSelected:Tile|null|undefined,previousRoute:Hex[]|null=null;
      const resourceSprites=new Map<string,{g:Graphics;readyAt:number;q:number;r:number}>();
      const animations:{container:Container;start:number;x:number;y:number;stone?:Graphics}[]=[];
      function reward(n:ResourceNode){const p=position(n),c=new Container();c.position.set(p.x,p.y);effects.addChild(c);
        const label=new Text({text:n.kind==='stone'?'石头 +1':'木头 +1',style:{fontFamily:'Microsoft YaHei,sans-serif',fontSize:12,fontWeight:'bold',fill:0x345d43,stroke:{color:0xfff8da,width:3}}});label.anchor.set(.5);label.y=-24;c.addChild(label);
        let stone:Graphics|undefined;if(n.kind==='stone'){stone=new Graphics();pixel(stone,-6,-3,12,8,0x91a090);pixel(stone,-4,-5,8,5,0xd3d8bc);c.addChild(stone);}
        animations.push({container:c,start:performance.now(),x:p.x,y:p.y,stone});
      }
      app.ticker.add(ticker=>{
        const current=latest.current,now=Date.now()+current.clockOffset;
        const nodeIds=new Set(current.resources.map(n=>n.id));
        for(const [id,entry]of resourceSprites)if(!nodeIds.has(id)){entry.g.destroy();resourceSprites.delete(id);}
        for(const n of current.resources){
          let entry=resourceSprites.get(n.id);const p=position(n);
          if(!entry){const g=new Graphics();resourceLayer.addChild(g);entry={g,readyAt:-1,q:n.q,r:n.r};resourceSprites.set(n.id,entry);}
          if(entry.readyAt!==n.readyAt||entry.q!==n.q||entry.r!==n.r){
            if(entry.readyAt===0&&n.readyAt>0)reward(n);
            entry.readyAt=n.readyAt;entry.q=n.q;entry.r=n.r;const g=entry.g;g.clear();
            if(n.kind==='wood'){if(n.readyAt>0){pixel(g,-7,0,14,8,0x896447);pixel(g,-8,-2,16,4,0xd6b480);pixel(g,-4,-1,8,2,0x9c7b51);}else{tree(g,-8,3,0);tree(g,8,-4,1);}}
            else if(n.readyAt===0){pixel(g,-7,0,14,6,0x8b9c8b);pixel(g,-5,-4,10,6,0xc8d0b5);pixel(g,-3,-4,5,2,0xe6e7cf);}
          }
          const working=current.actions.some(a=>a.nodeId===n.id);
          entry.g.position.set(p.x+(working?Math.sin(now/65)*1.6:0),p.y);
        }
        for(let i=animations.length-1;i>=0;i--){const a=animations[i],elapsed=(performance.now()-a.start)/1100;if(elapsed>=1){a.container.destroy({children:true});animations.splice(i,1);continue;}a.container.y=a.y-elapsed*22;a.container.alpha=1-elapsed;if(a.stone)a.stone.scale.set(Math.max(0,1-elapsed*3));}

        if(current.players!==previousPlayers){
          previousPlayers=current.players;
          const counts=new Map<string,number>();
          const active=new Set(current.players.map(p=>p.id));
          for(const [id,e]of entities)if(!active.has(id)){e.container.destroy({children:true});entities.delete(id);}
          for(const p of current.players){
            const count=counts.get(key(p))||0;counts.set(key(p),count+1);const pos=position(p);pos.x+=(count%3-1)*9;pos.y+=Math.floor(count/3)*9;
            let entity=entities.get(p.id);
            if(!entity){const container=new Container(),body=new Graphics(),bar=new Graphics(),bubble=new Graphics(),label=new Text({text:p.username,style:{fontFamily:'Microsoft YaHei,sans-serif',fontSize:10,fill:0x254c41,stroke:{color:0xfff9e6,width:3}}});label.anchor.set(.5);label.y=-28;bubble.y=-68;container.addChild(body,label,bar,bubble);actors.addChild(container);container.position.set(pos.x,pos.y);entity={container,body,bar,bubble,emoteCode:'',label,target:pos,color:'',online:!p.online};entities.set(p.id,entity);}
            entity.target=pos;
            if(entity.color!==p.color||entity.online!==p.online){entity.color=p.color;entity.online=p.online;const g=entity.body;g.clear();g.ellipse(0,8,9,4).fill({color:0x365443,alpha:.25});
              if(p.id===current.me)g.circle(0,2,14).stroke({color:0xfdf7d1,width:2});
              const c=p.online?Number.parseInt(p.color.slice(1),16):0x929d97;
              pixel(g,-6,-5,12,12,c);pixel(g,-5,-14,10,10,0xf1d3a3);pixel(g,-7,-17,14,6,c);pixel(g,-4,7,3,5,0x54594b);pixel(g,2,7,3,5,0x54594b);pixel(g,-2,-10,1,2,0x5a5748);pixel(g,3,-10,1,2,0x5a5748);
              entity.label.text=p.username+(p.online?'':' · 离线');entity.container.alpha=p.online?1:.65;
            }
          }
        }
        for(const [id,entity] of entities){
          const emote=current.emotes.find(e=>e.accountId===id&&e.expiresAt>now);entity.bubble.visible=!!emote;
          if(emote){if(entity.emoteCode!==emote.code){entity.emoteCode=emote.code;const g=entity.bubble;g.clear();g.poly([-19,-18,19,-18,19,-15,22,-15,22,12,19,12,19,15,5,15,0,20,-3,15,-19,15,-19,12,-22,12,-22,-15,-19,-15]).fill(0xfff9e5).stroke({color:0x607753,width:1.5});const art=emoteArt.find(a=>a.code===emote.code);art?.pixels.forEach((row,y)=>[...row].forEach((c,x)=>{if(c!=='.')pixel(g,x*2-12,y*2-13,2,2,Number.parseInt(emotePalette[c].slice(1),16));}));}entity.bubble.alpha=Math.min(1,(emote.expiresAt-now)/250);}
          const action=current.actions.find(a=>a.accountId===id);entity.bar.clear();if(action){const progress=Math.max(0,Math.min(1,(now-action.startedAt)/(action.endsAt-action.startedAt)));entity.bar.roundRect(-19,-43,38,6,2).fill(0x354d40);entity.bar.roundRect(-18,-42,36*progress,4,1).fill(0xe8c875);}const f=Math.min(1,ticker.deltaMS/100);entity.container.x+=(entity.target.x-entity.container.x)*f;entity.container.y+=(entity.target.y-entity.container.y)*f;}
        if(previousSelected!==current.selected){selection.clear();previousSelected=current.selected;if(current.selected){const p=position(current.selected);hex(selection,p.x,p.y,SIZE-1).fill({color:0xfff3be,alpha:.22}).stroke({color:0xfff5cd,width:2.2});}}
        if(previousRoute!==current.route||previousPlayers!==current.players){previousRoute=current.route;routeLine.clear();for(const h of current.route){const p=position(h);routeLine.circle(p.x,p.y,2.5).fill(0xfdf8df);}}
      });
      const resize=new ResizeObserver(()=>{if(disposed)return;const w=host.current!.clientWidth,h=host.current!.clientHeight,dx=w-app.screen.width,dy=h-app.screen.height,wasFit=Math.abs(zoom-fitScale)<.001;app.renderer.resize(w,h);if(wasFit)fit();else{fitScale=Math.min(w/1530,h/1300);scene.x+=dx/2;scene.y+=dy/2;report();}});resize.observe(host.current!);
      cleanup=()=>{resize.disconnect();canvas.removeEventListener('wheel',wheel);canvas.removeEventListener('pointerdown',down);canvas.removeEventListener('pointermove',move);canvas.removeEventListener('pointerup',up);props.controls.current=null;app.destroy(true,{children:true});};
    }
    init().catch(e=>{console.error(e);if(host.current)host.current.innerHTML='<div class="map-error">画面初始化失败，请开启浏览器硬件加速后刷新。</div>';});
    return()=>{disposed=true;cleanup();};
  },[props.world.version]);
  return <div className="map-canvas" ref={host} aria-label="大陆地图，拖动平移，滚轮缩放，点击选择地块"/>;
}
