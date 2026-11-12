import {useEffect,useRef,useState,type RefObject} from 'react';
import {Application,Container,Graphics,Text} from 'pixi.js';
import 'pixi.js/unsafe-eval';
import {hexPath,HEX_SCENE} from './mapPaint';
import {bindMapGestures} from './mapGestures';
import type {Hex} from './types';

export const HEX_SIZE=36;
export const scenePoint=({q,r}:Hex)=>({x:1.5*HEX_SIZE*q,y:Math.sqrt(3)*HEX_SIZE*(r+q/2)});
export const cellKey=({q,r}:Hex)=>`${q},${r}`;
export function sceneHex(x:number,y:number):Hex{
  const qf=x/(1.5*HEX_SIZE),rf=y/(Math.sqrt(3)*HEX_SIZE)-qf/2,sf=-qf-rf;
  let q=Math.round(qf),r=Math.round(rf);
  const s=Math.round(sf),dq=Math.abs(q-qf),dr=Math.abs(r-rf),ds=Math.abs(s-sf);
  if(dq>dr&&dq>ds)q=-r-s;
  else if(dr>ds)r=-q-s;
  return {q,r};
}
export function hexShape(g:Graphics,h:Hex,radius=HEX_SIZE){
  const {x,y}=scenePoint(h);
  return hexPath(g,x,y,radius,HEX_SCENE);
}
export function sceneLabel(layer:Container,value:string,x:number,y:number,size=12,color='#334f41'){
  const label=new Text({text:value,style:{fontFamily:'Microsoft YaHei, sans-serif',fontSize:size,fontWeight:'bold',fill:color,stroke:{color:'#f7f1d5',width:2.5}},resolution:2});
  label.anchor.set(.5);label.position.set(x,y);layer.addChild(label);
}
export type Camera={center:{x:number;y:number};zoom:number;span:number};
const actorPrefix='actor:';
export function sceneActor(layer:Container,id:string,h:Hex){
  const actor=new Container({label:actorPrefix+id}),p=scenePoint(h);
  actor.position.set(p.x,p.y);layer.addChild(actor);
  return actor;
}
export type DrawScene=(layer:Container)=>void;
export function SceneCanvas({host,camera,onCamera,onSelect,onHover,drawGround,drawHighlights,drawOverlay,drawEffects,className,label,entryPulse}:{
  host:RefObject<HTMLDivElement|null>;camera:Camera;onCamera:(camera:Camera)=>void;onSelect:(h:Hex,point?:{x:number;y:number})=>void;
  onHover?:(h:Hex|null)=>void;drawGround:DrawScene;drawHighlights?:DrawScene;drawOverlay:DrawScene;drawEffects?:DrawScene;
  className:string;label:string;entryPulse?:{id:string;cell:Hex};
}){
  const latest=useRef({camera,onCamera,onSelect,onHover});
  latest.current={camera,onCamera,onSelect,onHover};
  const [surface,setSurface]=useState<{app:Application;stage:Container;ground:Container;highlights:Container;overlay:Container;effects:Container}|null>(null);
  const [failed,setFailed]=useState(false);
  const focus=useRef<Hex|null>(null);
  const pulse=useRef(entryPulse);pulse.current=entryPulse;
  useEffect(()=>{
    const node=host.current;if(!node)return;
    const app=new Application();
    let disposed=false,ready=false,cleanup=()=>{};
    const scale=()=>Math.min(node.clientWidth,node.clientHeight)*latest.current.camera.zoom/latest.current.camera.span;
    const pointAt=(x:number,y:number)=>{
      const s=scale(),c=latest.current.camera.center;
      return {x:c.x+(x-node.clientWidth/2)/s,y:c.y+(y-node.clientHeight/2)/s};
    };
    const hexAt=(x:number,y:number)=>{const p=pointAt(x,y);return sceneHex(p.x,p.y);};
    const change=(next:Camera)=>{latest.current.camera=next;latest.current.onCamera(next);};
    const zoom=(factor:number,x:number,y:number)=>{
      const c=latest.current.camera,z=Math.max(.45,Math.min(4,c.zoom*factor));
      if(z===c.zoom)return;
      const delta=(1-c.zoom/z)/scale();
      change({...c,zoom:z,center:{x:c.center.x+(x-node.clientWidth/2)*delta,y:c.center.y+(y-node.clientHeight/2)*delta}});
    };
    void app.init({width:Math.max(1,node.clientWidth),height:Math.max(1,node.clientHeight),backgroundAlpha:0,antialias:true,resolution:Math.min(devicePixelRatio,2),autoDensity:true,autoStart:false,preference:'webgl'}).then(()=>{
      ready=true;
      if(disposed){app.destroy(true,{children:true});return;}
      const stage=new Container(),ground=new Container(),highlights=new Container(),overlay=new Container(),effects=new Container();
      stage.addChild(ground,highlights,overlay,effects);app.stage.addChild(stage);
      const canvas=app.canvas;canvas.style.display='block';canvas.style.pointerEvents='none';node.prepend(canvas);
      const render=()=>{
        const c=latest.current.camera,s=scale();
        stage.scale.set(s);stage.position.set(app.screen.width/2-c.center.x*s,app.screen.height/2-c.center.y*s);app.render();
      };
      const observer=new ResizeObserver(()=>{
        if(!node.clientWidth||!node.clientHeight)return;
        app.renderer.resize(node.clientWidth,node.clientHeight);render();
      });
      observer.observe(node);
      const release=bindMapGestures(node,{pan:(dx,dy)=>{const c=latest.current.camera,s=scale();change({...c,center:{x:c.center.x-dx/s,y:c.center.y-dy/s}});},zoom,tap:(x,y)=>{focus.current=hexAt(x,y);latest.current.onSelect(focus.current,pointAt(x,y));}});
      const wheel=(e:WheelEvent)=>{e.preventDefault();const box=node.getBoundingClientRect();zoom(Math.exp(-e.deltaY*.0012),e.clientX-box.left,e.clientY-box.top);};
      const move=(e:PointerEvent)=>{if(e.pointerType==='touch'||e.buttons)return;const box=node.getBoundingClientRect();latest.current.onHover?.(hexAt(e.clientX-box.left,e.clientY-box.top));};
      const leave=()=>latest.current.onHover?.(null);
      node.addEventListener('wheel',wheel,{passive:false});node.addEventListener('pointermove',move);node.addEventListener('pointerleave',leave);
      cleanup=()=>{release();observer.disconnect();node.removeEventListener('wheel',wheel);node.removeEventListener('pointermove',move);node.removeEventListener('pointerleave',leave);};
      setSurface({app,stage,ground,highlights,overlay,effects});
    }).catch(()=>{if(!disposed)setFailed(true);});
    return ()=>{disposed=true;cleanup();if(ready)app.destroy(true,{children:true});};
  },[host]);
  useEffect(()=>{if(surface){surface.ground.removeChildren().forEach(node=>node.destroy({children:true}));drawGround(surface.ground);}},[surface,drawGround]);
  useEffect(()=>{
    if(!surface)return;
    const layer=surface.highlights;
    layer.removeChildren().forEach(node=>node.destroy({children:true}));drawHighlights?.(layer);
  },[surface,drawHighlights]);
  useEffect(()=>{
    if(!surface)return;
    const {overlay,app}=surface;
    const before=new Map(overlay.children.filter(node=>node.label?.startsWith(actorPrefix)).map(node=>[node.label,{x:node.x,y:node.y}]));
    overlay.removeChildren().forEach(node=>node.destroy({children:true}));drawOverlay(overlay);
    const moves=overlay.children.flatMap(node=>{
      const from=before.get(node.label);
      if(!from||Math.hypot(node.x-from.x,node.y-from.y)<.5)return [];
      const to={x:node.x,y:node.y};node.position.set(from.x,from.y);
      return [{node,from,to}];
    });
    if(!moves.length)return;
    const start=performance.now();
    let frame=0;
    const animate=(now:number)=>{
      const t=Math.min(1,(now-start)/155);
      for(const {node,from,to} of moves)node.position.set(from.x+(to.x-from.x)*t,from.y+(to.y-from.y)*t);
      app.render();
      if(t<1)frame=requestAnimationFrame(animate);
    };
    frame=requestAnimationFrame(animate);
    return ()=>cancelAnimationFrame(frame);
  },[surface,drawOverlay]);
  useEffect(()=>{
    if(!surface)return;
    const layer=surface.effects;
    layer.removeChildren().forEach(node=>node.destroy({children:true}));drawEffects?.(layer);
  },[surface,drawEffects]);
  useEffect(()=>{
    if(!surface||!host.current)return;
    const {app,stage}=surface,s=Math.min(host.current.clientWidth,host.current.clientHeight)*camera.zoom/camera.span;
    stage.scale.set(s);stage.position.set(app.screen.width/2-camera.center.x*s,app.screen.height/2-camera.center.y*s);app.render();
  },[surface,host,camera.center,camera.zoom,camera.span,drawGround,drawHighlights,drawOverlay,drawEffects]);
  useEffect(()=>{
    const current=pulse.current;
    if(!surface||!current||window.matchMedia('(prefers-reduced-motion: reduce)').matches)return;
    const point=scenePoint(current.cell);
    const ring=surface.stage.addChild(new Graphics().circle(0,0,22).stroke({color:0xffdf82,width:3}));
    ring.position.set(point.x,point.y+8);
    const start=performance.now();
    let frame=0;
    const animate=(now:number)=>{
      const progress=Math.min(1,(now-start)/260);
      ring.alpha=1-progress;ring.scale.set(.8+progress*.65);
      surface.app.render();
      if(progress<1)frame=requestAnimationFrame(animate);
    };
    frame=requestAnimationFrame(animate);
    return ()=>{cancelAnimationFrame(frame);if(!ring.destroyed)ring.destroy();};
  },[surface,entryPulse?.id]);
  return <div ref={host} className={className} role="group" aria-label={label} tabIndex={0} onKeyDown={event=>{
    const directions:Record<string,Hex>={ArrowLeft:{q:-1,r:0},ArrowRight:{q:1,r:0},ArrowUp:{q:0,r:-1},ArrowDown:{q:0,r:1}};
    const h=focus.current??sceneHex(camera.center.x,camera.center.y),delta=directions[event.key];
    if(delta){event.preventDefault();focus.current={q:h.q+delta.q,r:h.r+delta.r};onHover?.(focus.current);}
    else if(event.key==='Enter'||event.key===' '){event.preventDefault();onSelect(h);}
  }}>{failed&&<p role="alert">地图渲染初始化失败，请检查浏览器的图形加速。</p>}</div>;
}
