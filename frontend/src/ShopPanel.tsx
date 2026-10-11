import {useEffect,useState} from 'react';
import {createPortal} from 'react-dom';
import {api} from './api';
import {ItemIcon} from './CharacterPanel';
import type {Character,Item} from './types';
import './shop.css';
type Product=Item&{buy_price:number|null;sell_price:number|null};
type Corpse={id:string;name:string;contents:number;price:number|null};
type Shop={items:Product[];fixed:string[];corpses:Corpse[];coins:Record<string,number>;unlimited:boolean};
type Entry={code?:string;corpseId?:string;name:string;quantity:number;price:number};
export function ShopPanel({character,onClose}:{character:Character;onClose:()=>void}){
 const [data,setData]=useState<Shop|null>(null),[mode,setMode]=useState<'buy'|'sell'>('buy'),[cart,setCart]=useState<Entry[]>([]),[busy,setBusy]=useState(false),[message,setMessage]=useState('');
 function refresh(){return api<Shop>('/shop').then(setData);}
 useEffect(()=>{refresh().catch(e=>setMessage(e.message));},[]);
 const total=cart.reduce((n,x)=>n+x.quantity*x.price,0);
 const balance=data?data.coins.copper_coin+100*data.coins.silver_coin+10000*data.coins.gold_coin:0;
 const insufficient=mode==='buy'&&total>balance;
 let remaining=total;const takeCopper=Math.min(data?.coins.copper_coin??0,remaining);remaining-=takeCopper;
 const takeSilver=Math.min(data?.coins.silver_coin??0,Math.ceil(remaining/100));remaining=Math.max(0,remaining-takeSilver*100);
 const takeGold=Math.min(data?.coins.gold_coin??0,Math.ceil(remaining/10000));
 const change=takeCopper+takeSilver*100+takeGold*10000-total;
 const paymentText=insufficient?'钱币不足':mode==='buy'?`扣除 铜${takeCopper}／银${takeSilver}／金${takeGold}${change>0?'；找回 '+change+' 铜':''}`:`收到 ${total} 铜币`;
 function add(item:Product){const price=mode==='buy'?item.buy_price:item.sell_price;if(price==null)return;setCart(old=>{const found=old.find(x=>x.code===item.code);if(found)return old.map(x=>x===found?{...x,quantity:Math.min(mode==='buy'?10000:item.quantity,x.quantity+1)}:x);return [...old,{code:item.code,name:item.name,quantity:1,price}];});}
 async function submit(){setBusy(true);try{await api('/shop/trade',{mode,lines:cart.map(({code,corpseId,quantity})=>({code,corpseId,quantity}))});setCart([]);await refresh();setMessage(mode==='buy'?'购买完成':'出售完成');}catch(e){setMessage((e as Error).message);await refresh().catch(()=>{});}finally{setBusy(false);}}
 return createPortal(<div className="shop-overlay"><section className="shop-dialog" role="dialog" aria-modal="true" aria-label="城镇商店"><header><h2>城镇商店</h2><button onClick={onClose}>关闭</button></header><nav>{(['buy','sell'] as const).map(m=><button key={m} aria-pressed={m===mode} onClick={()=>{setMode(m);setCart([]);setMessage('');}}>{m==='buy'?'购买':'出售'}</button>)}<span>铜 {data?.coins.copper_coin??0} · 银 {data?.coins.silver_coin??0} · 金 {data?.coins.gold_coin??0}</span></nav>
 <div className="shop-columns"><section><h3>{mode==='buy'?'商品 · 铜币标价':'背包 · 商店收购价'}</h3><div className="shop-products">{data?.items.filter(i=>mode==='buy'?i.buy_price!=null:i.quantity>0).map(i=>{const blocked=mode==='sell'&&(data.fixed.includes(i.code)||[character.weapon,character.offhand,character.body].includes(i.code));return <button key={i.code} disabled={busy||blocked} title={blocked?'请先卸下或取消固定':i.description} onClick={()=>add(i)}><ItemIcon code={i.code}/><strong>{i.name}</strong><span>{mode==='buy'?i.buy_price:i.sell_price} 铜 / 个</span>{mode==='sell'&&<small>持有 {i.quantity}{blocked?' · 已装备或固定':''}</small>}</button>;})}{mode==='sell'&&data?.corpses.map(c=><button key={c.id} disabled={busy||c.contents>0||c.price==null||cart.some(x=>x.corpseId===c.id)} onClick={()=>setCart(old=>[...old,{corpseId:c.id,name:c.name,quantity:1,price:c.price!}])}><strong>{c.name}</strong><span>{c.price==null?'暂未定价':`${c.price} 铜`}</span><small>{c.contents>0?'请先取空内部物品':'独立尸体'}</small></button>)}</div></section>
 <section className="shop-cart"><h3>{mode==='buy'?'待购买':'待出售'}</h3>{!cart.length&&<p>点击左侧物品加入列表</p>}{cart.map((x,index)=><div className="shop-line" key={x.code??x.corpseId}><strong>{x.name}</strong>{x.corpseId?<span>×1</span>:<input aria-label={`${x.name}数量`} type="number" min="1" max={mode==='buy'?10000:data?.items.find(i=>i.code===x.code)?.quantity} value={x.quantity} onChange={e=>{const n=Math.max(1,Math.min(mode==='buy'?10000:data?.items.find(i=>i.code===x.code)?.quantity??1,Number(e.target.value)||1));setCart(old=>old.map((v,k)=>k===index?{...v,quantity:n}:v));}}/>}<span>{x.price*x.quantity} 铜</span><button onClick={()=>setCart(old=>old.filter((_,k)=>k!==index))}>移除</button></div>)}</section></div>
 <footer><strong>{mode==='buy'?'应付':'实际收到'}：{total} 铜币</strong><small>{paymentText}</small><button disabled={busy||!cart.length||insufficient} onClick={()=>void submit()}>{busy?'交易中…':mode==='buy'?'购买':'出售'}</button><p role="status">{message}</p></footer></section></div>,document.body);
}
