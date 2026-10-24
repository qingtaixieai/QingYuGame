import type {Hex} from './types';
export type SceneCell=Hex&{terrain:string;walkable:boolean};
export const sceneKey=(h:Hex)=>`${h.q},${h.r}`;
export const sceneXY=(h:Hex)=>({x:54*h.q,y:Math.sqrt(3)*36*(h.r+h.q/2)});
export const scenePolygon=(h:Hex)=>{const p=sceneXY(h);return Array.from({length:6},(_,i)=>`${p.x+35*Math.cos(i*Math.PI/3)},${p.y+35*Math.sin(i*Math.PI/3)}`).join(' ');};
export const sceneSides=(h:Hex,r:number)=>[h.q,h.q+h.r,h.r,-h.q,-h.q-h.r,-h.r].flatMap((v,i)=>v===r?[i]:[]);
export const groundColor=(terrain:string)=>({grass:'#b9cc8b',dirt:'#b5a27e',sand:'#dfce94',rock:'#a1aaa0',paving:'#bcb8a3',shallow:'#97c7c3',deep:'#659ba9',forest:'#b9cc8b',plain:'#b9cc8b'}[terrain]||'#b9cc8b');
export function scenePath(cells:SceneCell[],from:Hex,to:Hex,occupied:Set<string>):Hex[]{
 const legal=new Set(cells.filter(c=>c.walkable).map(sceneKey)),goal=sceneKey(to),start=sceneKey(from);if(!legal.has(goal)||occupied.has(goal)||goal===start)return [];
 const queue=[from],previous=new Map<string,Hex>();previous.set(start,from);
 for(let i=0;i<queue.length;i++){const h=queue[i];for(const [q,r] of [[1,0],[0,1],[-1,1],[-1,0],[0,-1],[1,-1]]){const n={q:h.q+q,r:h.r+r},key=sceneKey(n);if(!legal.has(key)||occupied.has(key)||previous.has(key))continue;previous.set(key,h);if(key===goal){const path:Hex[]=[];for(let p=to;sceneKey(p)!==start;p=previous.get(sceneKey(p))!)path.unshift(p);return path;}queue.push(n);}}return [];
}
