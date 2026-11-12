import type {LootContainer,BlockedEdge} from './types';
import {bindMapGestures} from './mapGestures';
import { memo, useEffect, useRef } from 'react';
import { Application, Container, Graphics, Text } from 'pixi.js';
// Pixi's official non-eval polyfills keep WebGL rendering compatible with production CSP.
import 'pixi.js/unsafe-eval';
import type { Hex, Player, Tile, World, ResourceNode, WorldAction, Emote, BattleSummary, FerryState } from './types';
import {hitInteraction,paintInteractionPins,type ArrangedInteraction} from './cellInteractions';
import {drawEmoteBubble} from './emotes';
import { key } from './types';
import {drawHouse,drawRuin,drawTerrainProp,drawWorldActor,drawWorldGround,drawWorldFerry,hex,pixel,tree} from './worldArt';
import {paintRoad} from './mapPaint';
import {advanceRenderClock,ferryRouteProgress} from './ferryMotion';

const SIZE=25, SQRT3=Math.sqrt(3);
export const position=(h:Hex)=>({x:SQRT3*SIZE*(h.q+h.r/2),y:1.5*SIZE*h.r});
export type MapControls={zoom:(factor:number)=>void;fit:()=>void;locate:(h:Hex)=>void};
type Props={blockedEdges?:BlockedEdge[];loot:LootContainer[];deathMark:Hex|null;active:boolean;ferry:FerryState|null;world:World;battles:BattleSummary[];emotes:Emote[];resources:ResourceNode[];actions:WorldAction[];clockOffset:number;players:Player[];me:string;selected:Tile|null;route:Hex[];interactions:ArrangedInteraction[]; activeComponent:string|null; onSelect:(tile:Tile,componentId?:string)=>void;onZoom:(zoom:number)=>void;controls:React.RefObject<MapControls|null>};
export const MapView=memo(function MapView(props:Props){
  const host=useRef<HTMLDivElement>(null), latest=useRef(props), application=useRef<Application|null>(null);
  latest.current=props;
  useEffect(()=>{if(application.current){if(props.active)application.current.ticker.start();else application.current.ticker.stop();}},[props.active]);
  useEffect(()=>{
    let disposed=false,cleanup=()=>{};
    const app=new Application();
    async function init(){
      await app.init({width:host.current!.clientWidth,height:host.current!.clientHeight,background:0xa5cdd0,antialias:false,resolution:Math.min(devicePixelRatio,2),autoDensity:true,preference:'webgl'});
      if(disposed||!host.current){app.destroy(true,{children:true});return;}
      application.current=app;
      host.current!.appendChild(app.canvas);
      const scene=new Container(),ground=new Graphics(),roads=new Graphics(),decor=new Graphics(),markers=new Container(),selection=new Graphics(),routeLine=new Graphics(),actors=new Container();
      app.stage.addChild(scene);const resourceLayer=new Container(),effects=new Container(),battleMarks=new Container();
      scene.addChild(ground,roads,decor,resourceLayer,markers,battleMarks,routeLine,selection,actors,effects);
      const passageMarks=new Graphics();scene.addChild(passageMarks);let passageSignature='';
      const lootLayer=new Container();scene.addChild(lootLayer);let lootSignature='';
      const interactionLayer=new Container();scene.addChild(interactionLayer);
      let previousInteractions:ArrangedInteraction[]|null=null,previousComponent:string|null=null;
      let battleSignature="",deathSignature="";
      const deathMarker=new Container();scene.addChild(deathMarker);
      const map=props.world,index=new Map(map.tiles.map(t=>[key(t),t]));
      for(const t of map.tiles){
        const {x,y}=position(t),n=Math.abs(t.q*137+t.r*73)%7;
        drawWorldGround(ground,t.terrain,x,y,n);
        if(t.road) for(const d of [[1,0],[0,1],[1,-1]]){
          const adjacent=index.get(`${t.q+d[0]},${t.r+d[1]}`);if(!adjacent?.road)continue;
          const b=position(adjacent);paintRoad(roads,{x,y},b);
        }
        if(t.terrain==='river'){
          for(const d of [[1,0],[0,1],[1,-1]]){const other=index.get(`${t.q+d[0]},${t.r+d[1]}`);if(other&&['river','bridge'].includes(other.terrain)){const b=position(other);ground.moveTo(x,y).lineTo(b.x,b.y).stroke({width:21,color:0x86bfc7});}}
          pixel(decor,x-5,y-6,11,1,0xa4d4d4);pixel(decor,x+3,y+4,8,1,0xa4d4d4);
        }
      }
      for(const t of [...map.tiles].sort((a,b)=>a.r-b.r)){
        const {x,y}=position(t),n=Math.abs(t.q*137+t.r*73)%7;
        if(t.place){
          if(t.place.type==='port'||t.place.type==='landing'){pixel(decor,x-15,y-3,30,14,0x846b48);for(let i=0;i<6;i++)pixel(decor,x-14+i*5,y-2,4,12,0xc8a877);pixel(decor,x-17,y-6,4,21,0x796441);pixel(decor,x+13,y-6,4,21,0x796441);if(t.place.type==='port'){pixel(decor,x-9,y-18,18,15,0xe6d9a2);pixel(decor,x-13,y-21,26,6,0x537b77);}}else if(t.place.type==='ruin')drawRuin(decor,x,y);else drawHouse(decor,x,y,t.place.type==='town');
          const text=new Text({text:t.place.name,style:{fontFamily:'Microsoft YaHei, sans-serif',fontSize:11,fontWeight:'600',fill:0x384e40,stroke:{color:0xf2f0d8,width:3}}});
          text.anchor.set(.5,0);text.position.set(x,y+15);markers.addChild(text);continue;
        }

        drawTerrainProp(decor,x,y,t.terrain,n,t.road);
      }
      for(const [title,x,y] of [['西 静 海',-625,80],['东 风 海',590,-40]] as const){const label=new Text({text:title,style:{fontFamily:'serif',fontSize:20,fill:0x659b9f,letterSpacing:8}});label.anchor.set(.5);label.position.set(x,y);scene.addChild(label);}
      const seaLine=new Graphics(),boat=new Container(),hull=new Graphics(),boatLabel=new Text({text:'',style:{fontFamily:'Microsoft YaHei',fontSize:10,fontWeight:'bold',fill:0x34574e,stroke:{color:0xfff3d5,width:3}}});
      boat.visible=false;
      boatLabel.anchor.set(.5);boatLabel.y=-36;boat.addChild(hull,boatLabel);scene.addChild(seaLine,boat);
      drawWorldFerry(hull);
      let seaDrawn=false;
      const mapWidth=SQRT3*SIZE*map.radius*2+100,mapHeight=1.5*SIZE*map.radius*2+110;
      let zoom=1,fitScale=1;
      function report(){latest.current.onZoom(Math.round(zoom/fitScale*100));}
      function fit(){fitScale=Math.min(app.screen.width/mapWidth,app.screen.height/mapHeight);zoom=fitScale;scene.scale.set(zoom);scene.position.set(app.screen.width/2,app.screen.height/2+5);report();}
      function zoomAt(factor:number,x=app.screen.width/2,y=app.screen.height/2){const next=Math.max(fitScale*.65,Math.min(Math.max(1.6,fitScale*5),zoom*factor));const ratio=next/zoom;scene.x=x-(x-scene.x)*ratio;scene.y=y-(y-scene.y)*ratio;zoom=next;scene.scale.set(zoom);report();}
      props.controls.current={zoom:factor=>zoomAt(factor),fit,locate:h=>{const p=position(h);if(zoom<.9){zoom=.9;scene.scale.set(zoom);}scene.position.set(app.screen.width/2-p.x*zoom,app.screen.height/2-p.y*zoom);report();}};
      fit();
      if(window.matchMedia('(max-height:600px) and (orientation:landscape)').matches){const h=props.players.find(p=>p.id===props.me)||map.spawn;props.controls.current?.locate(h);}
      const canvas=app.canvas;
      const unbind=bindMapGestures(canvas,{pan:(dx,dy)=>{scene.x+=dx;scene.y+=dy;},zoom:zoomAt,tap:(x,y)=>{
        const local=scene.toLocal({x,y});const rf=local.y/(1.5*SIZE),qf=local.x/(SQRT3*SIZE)-rf/2;
        let q=Math.round(qf),r=Math.round(rf),s=Math.round(-qf-rf);const dq=Math.abs(q-qf),dr=Math.abs(r-rf),ds=Math.abs(s+qf+rf);
        if(dq>dr&&dq>ds)q=-r-s;else if(dr>ds)r=-q-s;
        const hit=hitInteraction(latest.current.interactions,local,position);
        const tile=index.get(hit?key(hit.cell):`${q},${r}`);
        if(tile)latest.current.onSelect(tile,hit?.id);
      }});
      function wheel(e:WheelEvent){e.preventDefault();const rect=canvas.getBoundingClientRect();zoomAt(Math.exp(-e.deltaY*.0012),e.clientX-rect.left,e.clientY-rect.top);}
      canvas.style.cursor='grab';canvas.style.touchAction='none';canvas.addEventListener('wheel',wheel,{passive:false});
      const entities=new Map<string,{container:Container;body:Graphics;bar:Graphics;barPixels:number;bubble:Graphics;emoteCode:string;label:Text;battleTag:Text;target:{x:number;y:number};color:string;online:boolean;inBattle:boolean}>();
      let previousPlayers:Player[]|null=null,previousSelected:Tile|null|undefined,previousRoute:Hex[]|null=null;
      const resourceSprites=new Map<string,{g:Graphics;readyAt:number;q:number;r:number;x:number;y:number}>();
      let previousResources:ResourceNode[]|null=null,previousActions:WorldAction[]|null=null,workingNodes=new Set<string>();
      let previousFerry:FerryState|null=null,boatRoute:{x:number;y:number}[]=[],boatRouteIndex=0,boatLabelText='';
      const animations:{container:Container;start:number;x:number;y:number;stone?:Graphics}[]=[];
      function reward(n:ResourceNode){const p=position(n),c=new Container();c.position.set(p.x,p.y);effects.addChild(c);
        const label=new Text({text:n.kind==='stone'?'石头 +1':'木头 +1',style:{fontFamily:'Microsoft YaHei,sans-serif',fontSize:12,fontWeight:'bold',fill:0x345d43,stroke:{color:0xfff8da,width:3}}});label.anchor.set(.5);label.y=-24;c.addChild(label);
        let stone:Graphics|undefined;if(n.kind==='stone'){stone=new Graphics();pixel(stone,-6,-3,12,8,0x91a090);pixel(stone,-4,-5,8,5,0xd3d8bc);c.addChild(stone);}
        animations.push({container:c,start:performance.now(),x:p.x,y:p.y,stone});
      }
      let frameAt=performance.now(),renderTime=Date.now()+latest.current.clockOffset;
      app.ticker.add(ticker=>{
        const current=latest.current,frameNow=performance.now();
        if(previousInteractions!==current.interactions||previousComponent!==current.activeComponent){
          interactionLayer.removeChildren().forEach(child=>child.destroy({children:true}));
          paintInteractionPins(interactionLayer,current.interactions,position,current.activeComponent);
          previousInteractions=current.interactions;previousComponent=current.activeComponent;
        }
        const closedKey=JSON.stringify(current.blockedEdges||[]);
        if(closedKey!==passageSignature){
          passageSignature=closedKey;passageMarks.clear();
          for(const edge of current.blockedEdges||[]){
            const a=position(edge.from),b=position(edge.to),dx=b.x-a.x,dy=b.y-a.y,len=Math.hypot(dx,dy);
            if(!len)continue;
            const x=(a.x+b.x)/2,y=(a.y+b.y)/2,px=-dy/len*SIZE*.46,py=dx/len*SIZE*.46;
            passageMarks.moveTo(x-px,y-py).lineTo(x+px,y+py).stroke({color:0xffedce,width:5});
            passageMarks.moveTo(x-px,y-py).lineTo(x+px,y+py).stroke({color:0xa84d3c,width:2.5});
          }
        }
        renderTime=advanceRenderClock(renderTime,frameNow-frameAt,Date.now()+current.clockOffset);
        frameAt=frameNow;
        const now=renderTime;
        const markSignature=current.deathMark?key(current.deathMark):'';
        if(markSignature!==deathSignature){deathSignature=markSignature;deathMarker.removeChildren().forEach(c=>c.destroy());
          if(current.deathMark){const p=position(current.deathMark),g=new Graphics();hex(g,p.x,p.y,SIZE-2).fill({color:0xe8ddff,alpha:.25}).stroke({color:0xf5e5ff,width:2});g.moveTo(p.x,p.y-14).lineTo(p.x+7,p.y).lineTo(p.x,p.y+9).lineTo(p.x-7,p.y).closePath().fill(0xeaddff);deathMarker.addChild(g);
            const text=new Text({text:'死亡标记',style:{fontFamily:'Microsoft YaHei',fontSize:10,fill:0xffffff,stroke:{color:0x69587e,width:3}}});text.anchor.set(.5);text.position.set(p.x,p.y+21);deathMarker.addChild(text);}
        }
        const ferry=current.ferry;
        if(ferry!==previousFerry){
          previousFerry=ferry;boat.visible=!!ferry?.built;
          if(ferry){
            if(!seaDrawn){seaDrawn=true;for(const h of ferry.route){const p=position(h);seaLine.circle(p.x,p.y,2).fill({color:0xf4ead0,alpha:.65});}}
            boatRoute=ferry.route.map(position);boatRouteIndex=Math.max(0,ferry.route.findIndex(h=>key(h)===key(ferry)));
            const label=(ferry.passengerIds.includes(current.me)?'你在船上 · ':'望潮渡船 · ')+ferry.passengerIds.length+' 人';
            if(label!==boatLabelText){boatLabelText=label;boatLabel.text=label;}
          }
        }
        if(ferry&&boatRoute.length){
          const progress=ferryRouteProgress(ferry,boatRouteIndex,boatRoute.length,now),i=Math.floor(progress),f=progress-i,a=boatRoute[i],b=boatRoute[Math.min(i+1,boatRoute.length-1)];
          boat.position.set(a.x+(b.x-a.x)*f,a.y+(b.y-a.y)*f+Math.sin(now/450)*.8);
        }
        const lootKey=current.loot.map(x=>x.id+':'+x.q+','+x.r+':'+x.battleId).join('|');
        if(lootKey!==lootSignature){lootSignature=lootKey;lootLayer.removeChildren().forEach(x=>x.destroy());const groups=new Map<string,{q:number;r:number;kind:string;count:number}>();for(const x of current.loot){const k=key(x),old=groups.get(k);if(old){old.count++;}else groups.set(k,{q:x.q,r:x.r,kind:x.kind,count:1});}for(const x of groups.values()){const p=position(x),g=new Graphics();pixel(g,p.x+5,p.y+3,10,8,x.kind==='corpse'?0x855842:x.kind==='chest'?0x9e835d:0xb19a69);pixel(g,p.x+7,p.y,6,4,0xd6be86);lootLayer.addChild(g);const t=new Text({text:(x.kind==='corpse'?'尸体':x.kind==='chest'?'宝箱':'掉落堆')+(x.count>1?' ×'+x.count:''),style:{fontFamily:'Microsoft YaHei',fontSize:8,fill:0x4a392b,stroke:{color:0xf2e7c7,width:2}}});t.position.set(p.x+1,p.y+12);lootLayer.addChild(t);}}
        const nextBattleSignature=current.battles.map(b=>b.id+":"+b.participants).join("|");
        if(nextBattleSignature!==battleSignature){battleSignature=nextBattleSignature;battleMarks.removeChildren().forEach(c=>c.destroy());
          for(const battle of current.battles){const p=position(battle),mark=new Graphics();hex(mark,p.x,p.y,SIZE-1).fill({color:0xa95042,alpha:.22}).stroke({color:0xb65546,width:2});battleMarks.addChild(mark);
            const label=new Text({text:'⚔ 战斗封锁',style:{fontFamily:'Microsoft YaHei',fontSize:9,fill:0x8b3d32,fontWeight:'bold',stroke:{color:0xfff0d1,width:2}}});label.anchor.set(.5);label.position.set(p.x,p.y+20);battleMarks.addChild(label);}
        }
        if(current.resources!==previousResources){
          previousResources=current.resources;
          const nodeIds=new Set(current.resources.map(n=>n.id));
          for(const [id,entry]of resourceSprites)if(!nodeIds.has(id)){entry.g.destroy();resourceSprites.delete(id);}
          for(const n of current.resources){
            let entry=resourceSprites.get(n.id);
            if(!entry){const g=new Graphics();resourceLayer.addChild(g);entry={g,readyAt:-1,q:n.q,r:n.r,x:0,y:0};resourceSprites.set(n.id,entry);}
            if(entry.readyAt!==n.readyAt||entry.q!==n.q||entry.r!==n.r){
              if(entry.readyAt===0&&n.readyAt>0)reward(n);
              entry.readyAt=n.readyAt;entry.q=n.q;entry.r=n.r;const g=entry.g;g.clear();
              if(n.kind==='wood'){if(n.readyAt>0){pixel(g,-7,0,14,8,0x896447);pixel(g,-8,-2,16,4,0xd6b480);pixel(g,-4,-1,8,2,0x9c7b51);}else{tree(g,-8,3,0);tree(g,8,-4,1);}}
              else if(n.readyAt===0){pixel(g,-7,0,14,6,0x8b9c8b);pixel(g,-5,-4,10,6,0xc8d0b5);pixel(g,-3,-4,5,2,0xe6e7cf);}
              const p=position(n);entry.x=p.x;entry.y=p.y;g.position.set(p.x,p.y);
            }
          }
        }
        if(current.actions!==previousActions){
          previousActions=current.actions;
          const nextWorking=new Set(current.actions.map(a=>a.nodeId));
          for(const id of workingNodes)if(!nextWorking.has(id)){const entry=resourceSprites.get(id);if(entry)entry.g.x=entry.x;}
          workingNodes=nextWorking;
        }
        for(const id of workingNodes){const entry=resourceSprites.get(id);if(entry)entry.g.x=entry.x+Math.sin(now/65)*1.6;}
        for(let i=animations.length-1;i>=0;i--){const a=animations[i],elapsed=(performance.now()-a.start)/1100;if(elapsed>=1){a.container.destroy({children:true});animations.splice(i,1);continue;}a.container.y=a.y-elapsed*22;a.container.alpha=1-elapsed;if(a.stone)a.stone.scale.set(Math.max(0,1-elapsed*3));}

        if(current.players!==previousPlayers){
          previousPlayers=current.players;
          const counts=new Map<string,number>();
          const active=new Set(current.players.map(p=>p.id));
          for(const [id,e]of entities)if(!active.has(id)){e.container.destroy({children:true});entities.delete(id);}
          for(const p of current.players){
            const count=counts.get(key(p))||0;counts.set(key(p),count+1);const pos=position(p);pos.x+=(count%3-1)*9;pos.y+=Math.floor(count/3)*9;
            let entity=entities.get(p.id);
            if(!entity){const container=new Container(),body=new Graphics(),bar=new Graphics(),bubble=new Graphics(),label=new Text({text:p.username,style:{fontFamily:'Microsoft YaHei,sans-serif',fontSize:10,fill:0x254c41,stroke:{color:0xfff9e6,width:3}}}),battleTag=new Text({text:'战斗中',style:{fontFamily:'Microsoft YaHei,sans-serif',fontSize:12,fontWeight:'bold',fill:0xa44536,stroke:{color:0xfff6dc,width:4}}});label.anchor.set(.5);label.y=-28;battleTag.anchor.set(.5);battleTag.y=-43;battleTag.visible=p.inBattle;bubble.y=-68;container.addChild(body,label,battleTag,bar,bubble);actors.addChild(container);container.position.set(pos.x,pos.y);entity={container,body,bar,barPixels:-1,bubble,emoteCode:'',label,battleTag,target:pos,color:'',online:!p.online,inBattle:!p.inBattle};entities.set(p.id,entity);}
            entity.body.alpha=p.life==='soul'?.4:1;entity.body.rotation=p.life==='down'?Math.PI/2:0;entity.label.text=p.username+(p.life==='soul'?' · 灵魂':p.life==='down'?' · 倒地':p.kind==='monster'?' · 敌对':p.online?'':' · 离线');
            entity.target=pos;entity.container.visible=!current.ferry?.passengerIds.includes(p.id);
            if(entity.color!==p.color||entity.online!==p.online||entity.inBattle!==p.inBattle){entity.color=p.color;entity.online=p.online;entity.inBattle=p.inBattle;drawWorldActor(entity.body,p,p.id===current.me);
              entity.battleTag.visible=p.inBattle;entity.container.alpha=p.online?1:.65;
            }
          }
        }
        for(const [id,entity] of entities){
          const emote=current.emotes.find(e=>e.accountId===id&&e.expiresAt>now);entity.bubble.visible=!!emote;
          if(emote){
            if(entity.emoteCode!==emote.code){entity.emoteCode=emote.code;drawEmoteBubble(entity.bubble,emote.code);}
            entity.bubble.alpha=Math.min(1,(emote.expiresAt-now)/250);
          }
          const action=current.actions.find(a=>a.accountId===id),barPixels=action?Math.round(36*Math.max(0,Math.min(1,(now-action.startedAt)/(action.endsAt-action.startedAt)))):-1;
          if(barPixels!==entity.barPixels){entity.barPixels=barPixels;entity.bar.clear();if(action){entity.bar.roundRect(-19,-43,38,6,2).fill(0x354d40);entity.bar.roundRect(-18,-42,barPixels,4,1).fill(0xe8c875);}}
          const f=Math.min(1,ticker.deltaMS/100);entity.container.x+=(entity.target.x-entity.container.x)*f;entity.container.y+=(entity.target.y-entity.container.y)*f;}
        if(previousSelected!==current.selected){selection.clear();previousSelected=current.selected;if(current.selected){const p=position(current.selected);hex(selection,p.x,p.y,SIZE-1).fill({color:0xfff3be,alpha:.22}).stroke({color:0xfff5cd,width:2.2});}}
        if(previousRoute!==current.route||previousPlayers!==current.players){previousRoute=current.route;routeLine.clear();for(const h of current.route){const p=position(h);routeLine.circle(p.x,p.y,2.5).fill(0xfdf8df);}}
      });
      const resize=new ResizeObserver(()=>{if(disposed||!host.current)return;const w=host.current.clientWidth,h=host.current!.clientHeight,dx=w-app.screen.width,dy=h-app.screen.height,wasFit=Math.abs(zoom-fitScale)<.001;app.renderer.resize(w,h);if(wasFit)fit();else{fitScale=Math.min(w/mapWidth,h/mapHeight);scene.x+=dx/2;scene.y+=dy/2;report();}});resize.observe(host.current!);
      if(!latest.current.active)app.ticker.stop();
      cleanup=()=>{resize.disconnect();canvas.removeEventListener('wheel',wheel);unbind();props.controls.current=null;application.current=null;app.destroy(true,{children:true});};
    }
    init().catch(e=>{console.error(e);if(host.current)host.current.innerHTML='<div class="map-error">画面初始化失败，请开启浏览器硬件加速后刷新。</div>';});
    return()=>{disposed=true;cleanup();};
  },[props.world.version]);
  return <div className="map-canvas" style={{visibility:props.active?undefined:'hidden'}} aria-hidden={!props.active} ref={host} aria-label="大陆地图，拖动平移，滚轮缩放，点击选择地块"/>;
});
