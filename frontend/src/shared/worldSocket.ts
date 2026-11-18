import type {State} from './protocol';

type WorldSocketMessage=State|{type:'chat-changed'};
const requiredLists=['players','resources','actions'];
const optionalLists=['emotes','battles','inventory','loot','sceneLoot','carriedCorpses','sceneDoors','sceneObjects','sceneResidents','sceneEffects','blockedEdges'];
const optionalObjects=['ferry','scenePosition','production'];
const finiteNumberFields=['tick','serverTime','battleRevision'];

function isObject(value:unknown):value is Record<string,unknown>{
  return value!==null&&typeof value==='object'&&!Array.isArray(value);
}

export function parseWorldSocketMessage(raw:unknown):WorldSocketMessage|null{
  if(typeof raw!=='string')return null;
  let message:unknown;
  try{message=JSON.parse(raw);}catch{return null;}
  if(!isObject(message))return null;
  if(message.type==='chat-changed')return {type:'chat-changed'};
  if(message.type!=='state'||typeof message.version!=='string'||!isObject(message.character)||typeof message.character.id!=='string')return null;
  if(!finiteNumberFields.every(key=>typeof message[key]==='number'&&Number.isFinite(message[key])))return null;
  if(!requiredLists.every(key=>Array.isArray(message[key])))return null;
  if(!optionalLists.every(key=>message[key]===undefined||Array.isArray(message[key])))return null;
  if(!optionalObjects.every(key=>message[key]==null||isObject(message[key])))return null;
  if(message.sceneMoving!==undefined&&typeof message.sceneMoving!=='boolean')return null;
  if(message.sceneService!=null&&typeof message.sceneService!=='string')return null;
  if(message.fireballPreparation!==undefined&&typeof message.fireballPreparation!=='string')return null;
  // The authenticated snapshot owns the shapes of list entries. Check the envelope before dispatch.
  return message as State;
}
