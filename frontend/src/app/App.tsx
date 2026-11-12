import React,{useEffect,useState} from "react";
import {ArrowRight,LoaderCircle,RefreshCw,Leaf,LockKeyhole} from "lucide-react";
import {api,ApiError} from "../api";
import type {Account} from "../types";
import {Brand} from '../components/Brand';
import {IslandArt} from '../components/IslandArt';
import {Game} from '../features/game/Game';

export function App(){
  const [me,setMe]=useState<Account|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState('');
  const [register,setRegister]=useState(false),[busy,setBusy]=useState(false),[notice,setNotice]=useState('');
  useEffect(()=>{api<Account>('/me').then(setMe).catch(e=>{if(!(e instanceof ApiError&&e.status===401))setError(e.message);}).finally(()=>setLoading(false));},[]);
  const logout=async()=>{try{await api('/logout',{});setMe(null);}catch(e){setError((e as Error).message);}};
  if(loading)return <div className="loading-screen"><Brand/><LoaderCircle className="spin"/><p>正在连接青屿大陆…</p></div>;
  if(me?.approved)return <Game me={me} onLogout={logout} onAccessLost={(reason)=>{if(reason)setError(reason);api<Account>('/me').then(setMe).catch(()=>setMe(null));}}/>;
  if(me)return <div className="gate"><Brand/><div className="pending-card"><span className="big-icon"><Leaf size={38}/></span><p className="eyebrow">旅途即将开始</p><h1>你好，{me.username}</h1><p>你的账号正在等待管理员批准。<br/>获准后，就能和其他旅人一起探索大陆。</p><button className="primary" disabled={busy} onClick={async()=>{setBusy(true);try{const a=await api<Account>('/me');setMe(a);setNotice(a.approved?'':'暂未获准，请稍后再来');}catch{setMe(null);}finally{setBusy(false);}}}><RefreshCw size={16}/>查看审批状态</button>{notice&&<p role="status" className="form-note">{notice}</p>}<button className="text-button" onClick={logout}>退出账号</button></div><span className="gate-footer">青屿 · 大世界探索试玩</span></div>;
  async function submit(e:React.SubmitEvent<HTMLFormElement>){
    e.preventDefault();setError('');setNotice('');setBusy(true);const f=new FormData(e.currentTarget),credentials={username:String(f.get('username')).trim(),password:String(f.get('password'))};
    try{if(register){await api('/register',credentials);setNotice('申请已提交。请登录查看审批状态。');setRegister(false);}else setMe(await api<Account>('/login',credentials));}catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  return <div className="auth-layout"><section className="auth-art"><Brand/><div className="art-copy"><p className="eyebrow">一张地图，一场共同的冒险</p><h1>远方有路，<br/>此刻出发。</h1><p>穿过林间，沿着河岸，遇见同路的旅人。</p></div><IslandArt/><div className="art-foot"><span>六角天地 · 自由探索</span><span>青屿大陆 / 01</span></div></section><section className="auth-form-wrap"><div className="auth-form"><span className="tiny-label">WELCOME TO QINGYU</span><h2>{register?'成为一名旅人':'欢迎回到青屿'}</h2><p className="subtle">{register?'注册账号，申请进入这片共同的世界。':'登录账号，接着走完未完的旅途。'}</p><div className="auth-tabs"><button className={!register?'active':''} onClick={()=>{setRegister(false);setError('');}}>账号登录</button><button className={register?'active':''} onClick={()=>{setRegister(true);setError('');}}>申请加入</button></div><form onSubmit={submit}><label>用户名<input name="username" required minLength={2} maxLength={24} autoComplete="username" placeholder="你的旅人名字"/></label><label>密码<input name="password" type="password" required minLength={register?10:1} maxLength={72} autoComplete={register?'new-password':'current-password'} placeholder={register?'至少 10 位字符':'输入你的密码'}/></label>{error&&<div className="form-error" role="alert">{error}</div>}{notice&&<div className="form-note" role="status">{notice}</div>}<button className="primary full" disabled={busy}>{busy?<LoaderCircle size={18} className="spin"/>:register?'提交加入申请':'进入青屿大陆'}{!busy&&<ArrowRight size={18}/>}</button></form><p className="auth-hint"><LockKeyhole size={14}/>{register?'账号经管理员批准后，即可进入游戏。':'忘记密码？请联系管理员重置。'}</p></div><span className="auth-footer">慢一点，世界值得探索。</span></section></div>;
}
