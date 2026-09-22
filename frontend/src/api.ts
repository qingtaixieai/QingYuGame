export class ApiError extends Error{constructor(public status:number,message:string){super(message);}}
export async function api<T>(path:string,body?:unknown):Promise<T>{
  const response=await fetch('/api'+path,{credentials:'same-origin',headers:body===undefined?{}:{'Content-Type':'application/json','X-World-Request':'1'},method:body===undefined?'GET':'POST',body:body===undefined?undefined:JSON.stringify(body)});
  const data=await response.json().catch(()=>({}));
  if(!response.ok)throw new ApiError(response.status,data.message||(response.status===429?'请求过于频繁，请一分钟后重试':'服务暂不可用，请稍后重试'));
  return data;
}
