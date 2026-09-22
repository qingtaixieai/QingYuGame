import {useEffect,useRef,useState} from 'react';
import {emoteArt,emotePalette} from './emotes';
export function PixelBubble(){return <svg width="26" height="26" viewBox="0 0 26 26" fill="none" aria-hidden="true" shapeRendering="crispEdges"><path d="M5 3H21V5H23V17H21V19H11L6 24V19H3V5H5Z" fill="currentColor"/><path d="M5 5H21V17H9L8 19V17H5Z" fill="#fff9e5"/><path d="M7 10H9V12H7ZM12 10H14V12H12ZM17 10H19V12H17Z" fill="currentColor"/></svg>}
export function PixelEmote({code}:{code:string}){const art=emoteArt.find(a=>a.code===code)!;return <svg viewBox="0 0 12 12" width="28" height="28" aria-hidden="true" shapeRendering="crispEdges">{art.pixels.flatMap((row,y)=>[...row].map((c,x)=>c==='.'?null:<rect key={`${x},${y}`} x={x} y={y} width="1" height="1" fill={emotePalette[c]}/>))}</svg>}
export function EmotePicker({disabled,onSend}:{disabled:boolean;onSend:(code:string)=>Promise<void>}){
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false),root=useRef<HTMLDivElement>(null);
 useEffect(()=>{const outside=(e:PointerEvent)=>{if(!root.current?.contains(e.target as Node))setOpen(false);};document.addEventListener('pointerdown',outside);return()=>document.removeEventListener('pointerdown',outside);},[]);
 useEffect(()=>{if(disabled)setOpen(false);},[disabled]);
 async function send(code:string){setBusy(true);try{await onSend(code);setOpen(false);}finally{setBusy(false);}}
 return <div className="emote-control" ref={root}>{open&&<div className="emote-wheel" role="group" aria-label="选择表情"><div className="emote-wheel-center"><PixelBubble/><span>传个心情</span></div>{emoteArt.map((e,i)=>{const angle=(i*45-90)*Math.PI/180;return <button key={e.code} className="emote-choice" style={{left:`${50+35*Math.cos(angle)}%`,top:`${50+35*Math.sin(angle)}%`}} title={e.name} aria-label={`发送${e.name}`} disabled={busy||disabled} onClick={()=>void send(e.code)}><PixelEmote code={e.code}/><span>{e.name}</span></button>;})}</div>}<button className={'emote-trigger '+(open?'active':'')} aria-label={open?'收起表情':'打开表情'} aria-expanded={open} title="表情" disabled={disabled} onClick={()=>setOpen(v=>!v)}><PixelBubble/></button></div>;
}
