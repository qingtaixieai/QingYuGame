import test from 'node:test';
import assert from 'node:assert/strict';
import {parseWorldSocketMessage} from '../frontend/src/shared/worldSocket.ts';
import {createBattleClosingTimer} from '../frontend/src/features/game/battleClosing.ts';

test('world socket ignores invalid messages and preserves optional scene fields',()=>{
  const state={type:'state',version:'v1',serverTime:100,tick:1,battleRevision:2,
    character:{id:'player',life:'alive'},players:[],resources:[],actions:[],emotes:[],battles:[],inventory:[],
    sceneLoot:[{id:'chest'}],carriedCorpses:[],production:null};
  assert.equal(parseWorldSocketMessage('{bad json'),null);
  assert.equal(parseWorldSocketMessage(JSON.stringify({...state,players:null})),null);
  assert.equal(parseWorldSocketMessage(JSON.stringify({...state,tick:'1'})),null);
  assert.equal(parseWorldSocketMessage(JSON.stringify({...state,sceneLoot:'invalid'})),null);
  assert.equal(parseWorldSocketMessage(JSON.stringify({...state,scenePosition:'not an object'})),null);
  assert.equal(parseWorldSocketMessage(JSON.stringify({...state,sceneMoving:1})),null);
  assert.deepEqual(parseWorldSocketMessage(JSON.stringify(state)),state);
  assert.deepEqual(parseWorldSocketMessage('{"type":"chat-changed"}'),{type:'chat-changed'});
});

test('a report for the previous battle cannot replace the current closing timer',()=>{
  const scheduled=new Map(),cleared=[];
  let handle=0;
  const timer=createBattleClosingTimer((callback,delay)=>{scheduled.set(++handle,{callback,delay});return handle;},id=>scheduled.delete(id));
  timer.start('old',1800,()=>cleared.push('old'));
  const oldCallback=scheduled.get(1).callback;
  timer.start('new',1800,()=>cleared.push('new'));
  assert.equal(timer.extend('old',420),false);
  oldCallback();
  assert.equal(timer.isCurrent('new'),true);
  const replacedCallback=scheduled.get(2).callback;
  assert.equal(timer.extend('new',420),true);
  replacedCallback();
  assert.equal(timer.isCurrent('new'),true);
  assert.equal(scheduled.get(3).delay,420);
  scheduled.get(3).callback();
  assert.equal(timer.isCurrent('new'),false);
  assert.deepEqual(cleared,['new']);
  timer.start('same',500,()=>cleared.push('stale'));
  const stale=scheduled.get(4).callback;
  timer.cancel();
  timer.start('same',500,()=>cleared.push('same'));
  stale();
  assert.equal(timer.isCurrent('same'),true);
  scheduled.get(5).callback();
  assert.deepEqual(cleared,['new','same']);
});
