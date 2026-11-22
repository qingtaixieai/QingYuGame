/** Serialize lease writes per chest across component lifetimes. Rejections stay visible
 * to callers, while the queue itself always recovers for the next operation. */
const tails=new Map<string,Promise<void>>();
export function queueChestLease<T>(chestId:string,operation:()=>Promise<T>):Promise<T>{
 const result=(tails.get(chestId)??Promise.resolve()).then(operation);
 const tail=result.then(()=>{},()=>{});
 tails.set(chestId,tail);
 void tail.then(()=>{if(tails.get(chestId)===tail)tails.delete(chestId);});
 return result;
}
