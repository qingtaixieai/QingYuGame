import {useState} from 'react';
import {Ship,Anchor,TreePine,Mountain} from 'lucide-react';
import type {FerryState,Item,Player,Hex} from './types';
import {key} from './types';
export function FerryPanel({ferry,me,own,items,clock,enabled,onCommand,onLocate}:{ferry:FerryState;me:string;own?:Player;items:Item[];clock:number;enabled:boolean;onCommand:(action:string,body?:object)=>Promise<void>;onLocate:(h:Hex)=>void}){
 const [busy,setBusy]=useState(false),aboard=ferry.passengerIds.includes(me);
 const dock=ferry.phase==='mainland'?ferry.mainlandPort:ferry.phase==='island'?ferry.islandLanding:null;
 const here=!!own&&!!dock&&key(own)===key(dock),atPort=!!own&&key(own)===key(ferry.mainlandPort);
 const index=ferry.route.findIndex(h=>key(h)===key(ferry)),step=ferry.travelMs/Math.max(1,ferry.route.length-1);
 const seconds=Math.max(0,Math.ceil((ferry.nextAt-clock+(dock?0:ferry.phase==='outbound'?Math.max(0,ferry.route.length-2-index)*step:Math.max(0,index-1)*step))/1000));
 const where=ferry.phase==='mainland'?'望潮港':ferry.phase==='island'?'远屿浅滩':ferry.phase==='outbound'?'驶向远屿浅滩':'驶向望潮港';
 async function command(action:string,body?:object){setBusy(true);try{await onCommand(action,body);}finally{setBusy(false);}}
 return <section className={'ferry-panel '+(aboard?'aboard':'')} aria-label="公共渡船"><header><Ship size={22}/><div><strong>{ferry.built?'望潮渡船':'一起造一艘船'}</strong><small>{aboard?'你正在船上':ferry.built?'大陆与远屿之间的公共航线':'在望潮港投入木头与石头'}</small></div><button className="icon-button" title="定位小船" aria-label="定位小船" onClick={()=>onLocate(ferry.built?ferry:ferry.mainlandPort)}><Anchor size={17}/></button></header>
 {!ferry.built?<><p>大家共同完成，建成后自动往返。投入的材料将用于造船。</p>{(['wood','stone'] as const).map(item=>{const needed=item==='wood'?ferry.woodNeeded:ferry.stoneNeeded,amount=ferry[item],owned=items.find(i=>i.code===item)?.quantity||0;return <div className="ferry-material" key={item}><div>{item==='wood'?<TreePine size={15}/>:<Mountain size={15}/>}<b>{item==='wood'?'木头':'石头'}</b><span>{amount} / {needed}</span></div><progress value={amount} max={needed}/><div><small>背包 {owned}</small><button disabled={!enabled||busy||!atPort||aboard||owned<1||amount>=needed} onClick={()=>void command('contribute',{item,quantity:1})}>投入 1 个</button><button disabled={!enabled||busy||!atPort||aboard||owned<1||amount>=needed} onClick={()=>void command('contribute',{item,quantity:needed-amount})}>补齐所需</button></div></div>})}{!atPort&&<p>到达港口后可以投入材料。</p>}</>:<><div className="ferry-status"><strong>{where}</strong><span>{dock?`${seconds} 秒后出发`:`约 ${seconds} 秒后靠岸`}</span></div><p>船上 {ferry.passengerIds.length} 人 · 两岸停靠 {Math.round(ferry.dwellMs/1000)} 秒<br/>单程约 {Math.round(ferry.travelMs/1000)} 秒 · 不限人数</p>{aboard?<button className="primary full" disabled={!enabled||busy||!dock} onClick={()=>void command('disembark')}>{dock?'在这里下船':'航行中 · 等待靠岸'}</button>:<button className="primary full" disabled={!enabled||busy||!here} onClick={()=>void command('board')}>{here?'登船':dock?'请到小船停靠的岸边':'等待小船靠岸'}</button>}<small className="ferry-hint">靠岸后需手动下船；留在船上会继续往返。</small></>}
 </section>;
}
