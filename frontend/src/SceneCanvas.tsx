import {useEffect,useRef,useState,type RefObject} from 'react';
import {Application,Container,Graphics,Text} from 'pixi.js';
import 'pixi.js/unsafe-eval';
import {hexPath,HEX_SCENE} from './mapPaint';
import {bindMapGestures} from './mapGestures';
import type {Hex} from './types';
import {battleVfx,hitFlashTint,HIT_FLASH_MS} from './features/battle/vfx/registry';
import {METEOR_FALL_MS} from './features/battle/vfx/meteor';
import type {CombatNumber} from './features/battle/vfx/combatNumbers';
import {exitRingOffset} from './features/battle/vfx/exitRing';
import {cellKey,selectSceneCell,type CellSelection} from './sceneSelection';

export const HEX_SIZE=36;
export const scenePoint=({q,r}:Hex)=>({x:1.5*HEX_SIZE*q,y:Math.sqrt(3)*HEX_SIZE*(r+q/2)});
export {cellKey};
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
  return label;
}
export type Camera={center:{x:number;y:number};zoom:number;span:number};
const actorPrefix='actor:';
export const battleCaptionLabel='battle-actor-caption';
function sizeBattleCaptions(layer:Container,scale:number){
  const compact=window.matchMedia('(max-height:600px) and (orientation:landscape)').matches;
  const factor=compact?Math.max(1,Math.min(2.5,10/Math.max(1,12*scale))):1;
  const captions:{node:Container;left:number;right:number;top:number;bottom:number}[]=[];
  function add(node:Container,x:number,y:number){
    node.scale.set(factor);node.pivot.y=0;
    if(!compact)return;
    const bounds=node.getLocalBounds();
    captions.push({node,left:x+bounds.minX*factor,right:x+bounds.maxX*factor,
      top:y+bounds.minY*factor,bottom:y+bounds.maxY*factor});
  }
  for(const child of layer.children){
    if(child.label===battleCaptionLabel)add(child,child.x,child.y);
    if(!child.label?.startsWith(actorPrefix))continue;
    for(const caption of child.children)if(caption.label===battleCaptionLabel)add(caption,child.x+caption.x,child.y+caption.y);
  }
  const gap=2/Math.max(scale,.01);
  const placed:typeof captions=[];
  for(const caption of captions.sort((a,b)=>b.bottom-a.bottom)){
    let shift=0;
    for(const lower of placed){
      if(caption.left>=lower.right||caption.right<=lower.left||caption.top-shift>=lower.bottom+gap)continue;
      shift=Math.max(shift,caption.bottom-lower.top+gap);
    }
    caption.node.pivot.y=shift/factor;
    placed.push({...caption,top:caption.top-shift,bottom:caption.bottom-shift});
    placed.sort((a,b)=>b.bottom-a.bottom);
  }
}
export const actorSpriteLabel="actor-sprite";
export function sceneActor(layer:Container,id:string,h:Hex){
  const actor=new Container({label:actorPrefix+id}),p=scenePoint(h);
  actor.position.set(p.x,p.y);layer.addChild(actor);
  return actor;
}
export type DrawScene=(layer:Container)=>void;
export type DrawSceneActivity=(layer:Container)=>((now:number)=>boolean)|undefined;
export function SceneCanvas({host,camera,onCamera,onSelect,onHover,onCancelSelection,drawGround,drawActivity,drawHighlights,drawOverlay,className,label,entryPulse,meteors,exitRing,canSelect,selectionMode,selection,numbers,ringClosing}:{
  host:RefObject<HTMLDivElement|null>;camera:Camera;onCamera:(camera:Camera)=>void;onSelect:(h:Hex,point?:{x:number;y:number})=>void;
  onHover?:(h:Hex|null)=>void;drawGround:DrawScene;drawActivity?:DrawSceneActivity;drawHighlights?:DrawScene;drawOverlay:DrawScene;
  className:string;label:string;entryPulse?:{id:string;cell:Hex};
  selectionMode?:string;
  onCancelSelection?:()=>void;
  selection?:CellSelection;
  canSelect?:(cell:Hex)=>boolean;
  numbers?:readonly CombatNumber[];
  ringClosing?:boolean;
  meteors?:readonly {id:string;cell:Hex}[];
  exitRing?:readonly {hex:Hex;directions:readonly number[]}[];
}){
  const latest=useRef({camera,onCamera,onSelect,onHover,selection});
  latest.current={camera,onCamera,onSelect,onHover,selection};
  const selectionCells=selection?.cells,selectionStyle=selection?.style;
  function commitSelection(cell:Hex,point?:{x:number;y:number}){
    const current=latest.current;
    if(!selectSceneCell(current.selection,cell))current.onSelect(cell,point);
  }
  const [surface,setSurface]=useState<{app:Application;stage:Container;ground:Container;activity:Container;highlights:Container;overlay:Container}|null>(null);
  const [failed,setFailed]=useState(false);
  const focus=useRef<Hex|null>(null);
  const [hover,setHover]=useState<Hex|null>(null);
  function publishHover(cell:Hex|null){
    setHover(old=>old?.q===cell?.q&&old?.r===cell?.r?old:cell);
    latest.current.onHover?.(cell);
  }
  const previousMode=useRef(selectionMode);
  useEffect(()=>{
    const wasSelecting=previousMode.current!=null&&previousMode.current!=='inspect';
    if(wasSelecting&&selectionMode==='inspect')publishHover(null);
    previousMode.current=selectionMode;
  },[selectionMode]);
  const hoverOutline=useRef<Graphics|null>(null);
  useEffect(()=>{
    if(!surface)return;
    const outline=new Graphics();
    surface.stage.addChildAt(outline,surface.stage.getChildIndex(surface.overlay));
    hoverOutline.current=outline;
    return ()=>{hoverOutline.current=null;if(!outline.destroyed)outline.destroy();};
  },[surface]);
  const hoverIsValid=hover!=null&&!!(selection?.contains??canSelect)?.(hover);
  useEffect(()=>{
    const outline=hoverOutline.current;
    if(!outline||!surface)return;
    outline.clear();
    if(hover&&hoverIsValid){
      hexShape(outline,hover,31).fill({color:0xfff5c7,alpha:.24}).stroke({color:0x4b4934,width:4});
      hexShape(outline,hover,31).stroke({color:0xfff8d6,width:2});
    }
    surface.app.render();
  },[surface,hover,hoverIsValid]);
  const pulse=useRef(entryPulse);pulse.current=entryPulse;
  const effectsFeed=useRef({meteors,numbers});
  effectsFeed.current={meteors,numbers};
  const wakeEffects=useRef<()=>void>(()=>{});
  useEffect(()=>{
    if(!surface)return;
    const {app,stage,overlay}=surface;
    const sparks=stage.addChild(new Graphics());
    const numberLayer=stage.addChild(new Container());
    const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const seen=new Set<string>();
    const falling=new Map<string,{cell:Hex;start:number}>();
    const landed=new Set<string>();
    const flashes=new Map<string,number>();
    const pending=new Map<string,{event:CombatNumber;queued:number}>();
    const labels=new Map<string,{label:Text;start:number;y:number}>();
    let frame=0;
    function schedule(){if(!frame)frame=requestAnimationFrame(tick);}
    function tick(time:number){
      frame=0;
      const feed=effectsFeed.current;
      for(const event of feed.meteors??[])if(!seen.has(event.id)){
        seen.add(event.id);falling.set(event.id,{cell:event.cell,start:time});
      }
      for(const event of feed.numbers??[])if(!seen.has(event.id)){
        seen.add(event.id);pending.set(event.id,{event,queued:time});
      }
      sparks.clear();
      for(const [id,event] of falling){
        const point=scenePoint(event.cell),age=time-event.start;
        if(reduced||age>=METEOR_FALL_MS)landed.add(id);
        if(!battleVfx.meteor(sparks,point.x,point.y,-stage.y/stage.scale.y,age,reduced))falling.delete(id);
      }
      for(const [id,{event,queued}] of pending){
        if(event.meteorId&&!landed.has(event.meteorId)){
          if(time-queued>=1600)pending.delete(id);
          continue;
        }
        const point=scenePoint(event.cell);
        if(event.targetId)flashes.set(event.targetId,time);
        const y=point.y-58;
        const value=event.amount===0?'格挡':`${event.heal?'+':'−'}${event.amount}`;
        labels.set(id,{label:sceneLabel(numberLayer,value,point.x,y,15,event.heal?'#40724a':'#a54236'),start:time,y});
        pending.delete(id);
      }
      for(const [id,entry] of labels){
        const age=time-entry.start;
        if(age>=1400){entry.label.destroy();labels.delete(id);continue;}
        entry.label.y=entry.y-(reduced?0:age/38);
        entry.label.alpha=Math.min(1,(1400-age)/350);
      }
      for(const [id,start] of flashes){
        const actor=overlay.children.find(node=>node.label===actorPrefix+id);
        const sprite=actor?.children.find(node=>node.label===actorSpriteLabel);
        if(sprite)sprite.tint=hitFlashTint(time-start);
        if(time-start>=HIT_FLASH_MS)flashes.delete(id);
      }
      app.render();
      if(falling.size||flashes.size||labels.size||pending.size)schedule();
    }
    wakeEffects.current=schedule;
    return ()=>{
      wakeEffects.current=()=>{};cancelAnimationFrame(frame);
      if(!sparks.destroyed)sparks.destroy();
      if(!numberLayer.destroyed)numberLayer.destroy({children:true});
    };
  },[surface]);
  useEffect(()=>{if(meteors?.length||numbers?.length)wakeEffects.current();},[surface,meteors,numbers]);
  const ringKey=exitRing?.map(cell=>cellKey(cell.hex)+':'+cell.directions.join(',')).join(';');
  const ringStarted=useRef<{id:string;closing:boolean;at:number}|null>(null);
  useEffect(()=>{
    if(!surface||!exitRing?.length||!entryPulse)return;
    const {app,stage,activity}=surface;
    const layer=new Container();
    stage.addChildAt(layer,stage.getChildIndex(activity));
    const reduced=window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const closing=!!ringClosing;
    if(ringStarted.current?.id!==entryPulse.id||ringStarted.current.closing!==closing)
      ringStarted.current={id:entryPulse.id,closing,at:performance.now()};
    const start=ringStarted.current.at;
    const tiles=exitRing.map(cell=>{
      const node=layer.addChild(new Container());
      const shape=node.addChild(new Graphics()),open=cell.directions.length>0;
      hexShape(shape,cell.hex,34).fill({color:open?0xfffdf1:0x666d68,alpha:.25}).stroke({color:open?0xe9deaf:0x545e57,alpha:.55,width:2});

      return node;
    });
    let frame=0;
    const tick=(time:number)=>{
      tiles.forEach((tile,i)=>{const state=exitRingOffset(time-start,i,reduced);tile.y=closing?24-state.lift:state.lift;tile.alpha=closing?state.lift/24:state.alpha;});
      app.render();
      if(!reduced&&time-start<330)frame=requestAnimationFrame(tick);
    };
    tick(performance.now());
    return ()=>{cancelAnimationFrame(frame);if(!layer.destroyed)layer.destroy({children:true});};
  },[surface,ringKey,entryPulse?.id,ringClosing]);
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
      if(disposed){app.destroy({removeView:true,releaseGlobalResources:false},{children:true});return;}
      const stage=new Container(),ground=new Container(),activity=new Container(),highlights=new Container(),overlay=new Container();
      stage.addChild(ground,activity,highlights,overlay);app.stage.addChild(stage);
      const canvas=app.canvas;canvas.style.display='block';canvas.style.pointerEvents='none';node.prepend(canvas);
      const render=()=>{
        const c=latest.current.camera,s=scale();
        stage.scale.set(s);stage.position.set(app.screen.width/2-c.center.x*s,app.screen.height/2-c.center.y*s);
        sizeBattleCaptions(overlay,s);app.render();
      };
      const observer=new ResizeObserver(()=>{
        if(!node.clientWidth||!node.clientHeight)return;
        app.renderer.resize(node.clientWidth,node.clientHeight);render();
      });
      observer.observe(node);
      const release=bindMapGestures(node,{pan:(dx,dy)=>{publishHover(null);const c=latest.current.camera,s=scale();change({...c,center:{x:c.center.x-dx/s,y:c.center.y-dy/s}});},zoom,tap:(x,y)=>{focus.current=hexAt(x,y);commitSelection(focus.current,pointAt(x,y));}});
      const wheel=(e:WheelEvent)=>{e.preventDefault();publishHover(null);const box=node.getBoundingClientRect();zoom(Math.exp(-e.deltaY*.0012),e.clientX-box.left,e.clientY-box.top);};
      const move=(e:PointerEvent)=>{if(e.pointerType==='touch'||e.buttons){publishHover(null);return;}const box=node.getBoundingClientRect();publishHover(hexAt(e.clientX-box.left,e.clientY-box.top));};
      const leave=()=>publishHover(null);
      node.addEventListener('wheel',wheel,{passive:false});node.addEventListener('pointermove',move);node.addEventListener('pointerleave',leave);
      cleanup=()=>{release();observer.disconnect();node.removeEventListener('wheel',wheel);node.removeEventListener('pointermove',move);node.removeEventListener('pointerleave',leave);};
      setSurface({app,stage,ground,activity,highlights,overlay});
    }).catch(()=>{if(!disposed)setFailed(true);});
    return ()=>{disposed=true;cleanup();if(ready)app.destroy({removeView:true,releaseGlobalResources:false},{children:true});};
  },[host]);
  useEffect(()=>{if(surface){surface.ground.removeChildren().forEach(node=>node.destroy({children:true}));drawGround(surface.ground);}},[surface,drawGround]);
  useEffect(()=>{
    if(!surface)return;
    const {activity:layer,app}=surface;
    layer.removeChildren().forEach(node=>node.destroy({children:true}));
    const animate=drawActivity?.(layer);
    if(!animate)return;
    let frame=0;
    const tick=(now:number)=>{
      const active=animate(now);
      app.render();
      if(active)frame=requestAnimationFrame(tick);
    };
    frame=requestAnimationFrame(tick);
    return ()=>cancelAnimationFrame(frame);
  },[surface,drawActivity]);
  useEffect(()=>{
    if(!surface)return;
    const layer=surface.highlights;
    layer.removeChildren().forEach(node=>node.destroy({children:true}));drawHighlights?.(layer);
    if(selectionCells&&selectionStyle){
      const marks=layer.addChild(new Graphics({label:'selection-candidates'}));
      for(const cell of selectionCells)
        hexShape(marks,cell,32).fill({color:selectionStyle.fill,alpha:.22}).stroke({color:selectionStyle.stroke,alpha:.76,width:2});
    }
  },[surface,drawHighlights,selectionCells,selectionStyle]);
  useEffect(()=>{
    if(!surface)return;
    const {overlay,app,stage}=surface;
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
      sizeBattleCaptions(overlay,stage.scale.x);
      app.render();
      if(t<1)frame=requestAnimationFrame(animate);
    };
    frame=requestAnimationFrame(animate);
    return ()=>cancelAnimationFrame(frame);
  },[surface,drawOverlay]);
  useEffect(()=>{
    if(!surface||!host.current)return;
    const {app,stage,overlay}=surface,s=Math.min(host.current.clientWidth,host.current.clientHeight)*camera.zoom/camera.span;
    stage.scale.set(s);stage.position.set(app.screen.width/2-camera.center.x*s,app.screen.height/2-camera.center.y*s);
    sizeBattleCaptions(overlay,s);app.render();
  },[surface,host,camera.center,camera.zoom,camera.span,drawGround,drawActivity,drawHighlights,drawOverlay,selectionCells,selectionStyle]);
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
  return <div ref={host} data-retreat-ring={exitRing?.length?(ringClosing?"sinking":"open"):undefined} className={className} role="group" aria-label={label} tabIndex={0} onKeyDown={event=>{
    const directions:Record<string,Hex>={ArrowLeft:{q:-1,r:0},ArrowRight:{q:1,r:0},ArrowUp:{q:0,r:-1},ArrowDown:{q:0,r:1}};
    const h=focus.current??sceneHex(camera.center.x,camera.center.y),delta=directions[event.key];
    if(delta){event.preventDefault();focus.current={q:h.q+delta.q,r:h.r+delta.r};publishHover(focus.current);}
    else if(event.key==='Escape'&&(selection||onCancelSelection)){event.preventDefault();(selection?.onCancel??onCancelSelection)?.();}
    else if(event.key==='Enter'||event.key===' '){event.preventDefault();commitSelection(h);}
  }}>{failed&&<p role="alert">地图渲染初始化失败，请检查浏览器的图形加速。</p>}</div>;
}
