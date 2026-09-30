/** Pointer gestures shared by the Pixi world and SVG battle. Pinching never becomes a tap. */
export function bindMapGestures(node:HTMLElement|SVGSVGElement,callbacks:{pan:(dx:number,dy:number)=>void;zoom:(factor:number,x:number,y:number)=>void;tap:(x:number,y:number)=>void}){
 const pointers=new Map<number,{x:number;y:number}>();let moved=false,origin={x:0,y:0};
 const point=(e:PointerEvent)=>{const r=node.getBoundingClientRect();return {x:e.clientX-r.left,y:e.clientY-r.top};};
 const down=(e:PointerEvent)=>{if(e.button!==0)return;const p=point(e);if(!pointers.size){origin=p;moved=false;}else moved=true;pointers.set(e.pointerId,p);node.setPointerCapture(e.pointerId);};
 const move=(e:PointerEvent)=>{const old=pointers.get(e.pointerId);if(!old)return;const p=point(e);if(pointers.size===2){const other=[...pointers.entries()].find(([id])=>id!==e.pointerId)![1];const before=Math.hypot(old.x-other.x,old.y-other.y),after=Math.hypot(p.x-other.x,p.y-other.y);if(before>2&&after>2){callbacks.zoom(after/before,(old.x+other.x)/2,(old.y+other.y)/2);callbacks.pan((p.x-old.x)/2,(p.y-old.y)/2);}moved=true;}else if(pointers.size===1){if(Math.hypot(p.x-origin.x,p.y-origin.y)>6)moved=true;callbacks.pan(p.x-old.x,p.y-old.y);}pointers.set(e.pointerId,p);};
 const up=(e:PointerEvent)=>{if(!pointers.has(e.pointerId))return;const p=point(e);pointers.delete(e.pointerId);if(node.hasPointerCapture(e.pointerId))node.releasePointerCapture(e.pointerId);if(!pointers.size&&!moved&&e.type==='pointerup')callbacks.tap(p.x,p.y);else moved=true;};
 const cancel=()=>{pointers.clear();moved=true;};
 node.addEventListener('pointerdown',down as EventListener);node.addEventListener('pointermove',move as EventListener);node.addEventListener('pointerup',up as EventListener);node.addEventListener('pointercancel',up as EventListener);window.addEventListener('blur',cancel);
 return()=>{node.removeEventListener('pointerdown',down as EventListener);node.removeEventListener('pointermove',move as EventListener);node.removeEventListener('pointerup',up as EventListener);node.removeEventListener('pointercancel',up as EventListener);window.removeEventListener('blur',cancel);};
}
