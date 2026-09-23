import {useState} from 'react';
import {Flag,Footprints,Swords,Hourglass,SkipForward,Users} from 'lucide-react';
import {api} from './api';
import type {BattleState,Hex} from './types';

const SIZE=43;
const ROOT3=Math.sqrt(3);
const xy=(h:Hex)=>({x:ROOT3*SIZE*(h.q+h.r/2),y:1.5*SIZE*h.r});
const distance=(a:Hex,b:Hex)=>Math.max(Math.abs(a.q-b.q),Math.abs(a.r-b.r),Math.abs(a.q+a.r-b.q-b.r));
const shortName=(name:string)=>{const chars=Array.from(name);return chars.length>8?chars.slice(0,7).join('')+'…':name;};
function polygon(h:Hex,r=SIZE-2){const p=xy(h);return Array.from({length:6},(_,i)=>{const a=(60*i-30)*Math.PI/180;return `${p.x+r*Math.cos(a)},${p.y+r*Math.sin(a)}`;}).join(' ');}
function tiles(radius:number){const result:Hex[]=[];for(let r=-radius;r<=radius;r++)for(let q=-radius;q<=radius;q++)if(distance({q:0,r:0},{q,r})<=radius)result.push({q,r});return result;}

export function BattleView({battle,me,connected,clock,onChanged,onMessage}:{battle:Extract<BattleState,{active:true}>;me:string;connected:boolean;clock:number;onChanged:()=>Promise<void>;onMessage:(message:string)=>void}){
  const [selected,setSelected]=useState<Hex|null>(null),[busy,setBusy]=useState(false);
  const own=battle.actors.find(a=>a.accountId===me);
  const target=selected?battle.actors.find(a=>a.q===selected.q&&a.r===selected.r):undefined;
  const myTurn=battle.turnAccountId===me&&!!own&&own.entryRound<=battle.round;
  const adjacent=!!own&&!!selected&&distance(own,selected)===1;
  const exit=!!selected&&battle.exits.some(h=>h.q===selected.q&&h.r===selected.r);
  const canMove=connected&&myTurn&&!busy&&adjacent&&!target&&battle.turnPoints>=1;
  const canAttack=connected&&myTurn&&!busy&&adjacent&&!!target&&target.accountId!==me&&!battle.attackUsed&&battle.turnPoints>=2;
  const current=battle.actors.find(a=>a.accountId===battle.turnAccountId);
  const recentAttack=[...battle.events].reverse().find(e=>e.kind==='attack'&&clock-e.happenedAt<1250);
  const seconds=Math.max(0,Math.ceil((battle.turnDeadline-clock)/1000));
  async function command(path:string,body:unknown){
    if(busy)return;
    setBusy(true);
    try{await api(path,body);await onChanged();setSelected(null);}catch(e){onMessage((e as Error).message);}finally{setBusy(false);}
  }
  return <section className="battle-surface" aria-label="局部战场">
    <div className="battle-grain" aria-hidden="true"/>
    <svg className="battle-board" viewBox="-340 -350 680 700" role="img" aria-label="六角战场，点击相邻格移动或选择攻击目标">
      <defs><filter id="battle-glow"><feGaussianBlur stdDeviation="7"/></filter></defs>
      {tiles(battle.radius).map(h=>{const k=`${h.q},${h.r}`,isExit=battle.exits.some(e=>e.q===h.q&&e.r===h.r),isSelected=selected?.q===h.q&&selected.r===h.r,marked=battle.intents.some(i=>i.q===h.q&&i.r===h.r),near=!!own&&distance(own,h)===1;
        return <g key={k} className={'battle-cell '+(isExit?'exit ':'')+(near&&myTurn?'reachable ':'')+(isSelected?'selected ':'')+(marked?'marked':'')}>
          <polygon points={polygon(h)} onClick={()=>setSelected(h)} role="button" tabIndex={0} aria-label={`${isExit?'出口 ':''}战场格 ${h.q}, ${h.r}${marked?'，已标红':''}`} onKeyDown={e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();setSelected(h);}}}/>
          {isExit&&<g pointerEvents="none"><text x={xy(h).x} y={xy(h).y+4} className="battle-exit-label">出口</text></g>}
        </g>;
      })}
      {battle.intents.map(i=><polygon className="battle-intent-ring" key={`${i.attackerId}-${i.targetId}`} points={polygon(i,SIZE-5)} pointerEvents="none"/>)}
      {battle.actors.map(a=>{const p=xy(a),active=a.accountId===battle.turnAccountId;return <g key={a.accountId} className={'battle-actor '+(a.accountId===me?'own ':'')+(active?'taking-turn ':'')+(a.online?'':'offline')} transform={`translate(${p.x} ${p.y})`} pointerEvents="none">
        <ellipse cy="18" rx="17" ry="6" fill="#344d3a" opacity=".2"/>
        <rect x="-14" y="-25" width="28" height="30" rx="3" fill={a.color} stroke="#3e513e" strokeWidth="2"/>
        <rect x="-11" y="-37" width="22" height="16" rx="4" fill="#f1d3a3" stroke="#6d604a" strokeWidth="2"/>
        <rect x="-14" y="-40" width="28" height="7" rx="2" fill={a.color}/>
        <rect x="-9" y="5" width="7" height="11" fill="#565a4c"/><rect x="2" y="5" width="7" height="11" fill="#565a4c"/>
        <text y="-49" className="battle-actor-name"><title>{a.username}</title>{shortName(a.username)}{a.accountId===me?' · 你':''}</text>
        {!a.online&&<text y="30" className="battle-offline-label">离线</text>}
        {a.entryRound>battle.round&&<text y="31" className="battle-wait-label">下轮入场</text>}
      </g>;})}
      {recentAttack&&recentAttack.q!==null&&recentAttack.r!==null&&<g key={recentAttack.id} className="battle-attack-flash" transform={`translate(${xy({q:recentAttack.q,r:recentAttack.r}).x} ${xy({q:recentAttack.q,r:recentAttack.r}).y})`} pointerEvents="none"><circle r="23" fill="#f9bc77" opacity=".55" filter="url(#battle-glow)"/><path d="M-28-24L25 28M26-25L-25 27" stroke="#fff1c4" strokeWidth="7" strokeLinecap="round"/></g>}
    </svg>
    <div className="battle-status"><span className="tiny-label">TACTICAL ENCOUNTER</span><h2>同格战场 <Users size={18}/></h2><p>第 {battle.round} 轮 · {battle.actors.length} 位旅人</p><div className="battle-status-row"><span>{myTurn?'你的回合':`${current?.username||'旅人'}的回合`}</span><strong>{myTurn?`${battle.turnPoints} 点战术点`:<><Hourglass size={14}/>{seconds} 秒</>}</strong></div>{own&&own.entryRound>battle.round&&<p className="battle-wait-note">已增援，下一轮按先攻顺序行动。</p>}</div>
    <div className="battle-actions"><div className="battle-selection">{selected?target?`${target.username} · ${target.online?'在线':'离线'}`:exit?'战场出口 · 移入后撤离':`空地 ${selected.q}, ${selected.r}`:'选择相邻格移动，或选择旅人标记攻击'}</div><div className="battle-action-buttons">{canMove&&<button className="primary compact" disabled={busy} onClick={()=>void command('/battle/step',{battleId:battle.id,q:selected!.q,r:selected!.r})}><Footprints size={16}/>{exit?'撤离战场':'移动一步'}</button>}{canAttack&&<button className="primary compact battle-attack-button" disabled={busy} onClick={()=>void command('/battle/attack',{battleId:battle.id,targetId:target!.accountId})}><Swords size={16}/>标记攻击格</button>}{myTurn&&<button className="soft-button" disabled={busy||!connected} onClick={()=>void command('/battle/end-turn',{battleId:battle.id})}><SkipForward size={16}/>结束回合</button>}{!myTurn&&<span className="battle-quiet">{own?.entryRound&&own.entryRound>battle.round?'等待下一轮':'等待行动顺序'}</span>}</div><div className="battle-exit-hint"><Flag size={13}/>走到出口可撤离 · 切换视角不会退出战斗</div></div>
    {recentAttack&&<div className="battle-event-pop" role="status">预定攻击已执行</div>}
  </section>;
}
