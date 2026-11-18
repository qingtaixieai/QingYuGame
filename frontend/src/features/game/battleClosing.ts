export function createBattleClosingTimer(
  schedule:(callback:()=>void,delay:number)=>number,
  unschedule:(handle:number)=>void
){
  let current:{id:string;handle:number;expire:()=>void}|undefined;
  function cancel(){
    if(!current)return;
    unschedule(current.handle);
    current=undefined;
  }
  function start(id:string,delay:number,expire:()=>void){
    cancel();
    const handle=schedule(()=>{
      if(current?.handle!==handle||current?.id!==id)return;
      current=undefined;
      expire();
    },delay);
    current={id,handle,expire};
  }
  return {
    start,cancel,isCurrent:(id:string)=>current?.id===id,
    extend(id:string,delay:number){
      if(current?.id!==id)return false;
      start(id,delay,current.expire);
      return true;
    },
  };
}
