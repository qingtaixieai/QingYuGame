import test from 'node:test';
import assert from 'node:assert/strict';
import {actionProgress,productionProgress,worldProgress} from '../frontend/src/shared/actionProgress.ts';

const task={actor:'me',kind:'craft',recipe:'arrow',objectId:'bench',corpseId:null,sceneId:'camp',version:'world',worldQ:2,worldR:3,q:1,r:1,startedAt:1000,dueAt:4000,total:5,completed:1};
const context={me:'me',version:'world',world:{q:2,r:3},sceneId:'camp',actor:{q:1,r:1},objects:[{id:'bench',q:2,r:1}],loot:[]};

test('production anchors to the facility and uses per-batch threshold',()=>{
  const progress=productionProgress(task,context);
  assert.equal(progress.targetId,'bench');
  assert.deepEqual(progress.cell,{q:2,r:1});
  assert.equal(progress.personal,false);
  assert.equal(actionProgress(progress,2500).short,true);
  assert.equal(actionProgress(progress,2500).fraction,.5);
  assert.equal(actionProgress(progress,9000).fraction,1);
  assert.equal(actionProgress(progress,0).fraction,0);
});

test('ten seconds is short and a long action stays long near completion',()=>{
  const progress=productionProgress({...task,dueAt:11000},context);
  assert.equal(actionProgress(progress,10999).short,true);
  assert.equal(actionProgress({...progress,dueAt:11001},11000).short,false);
  assert.equal(actionProgress({...progress,dueAt:1000},1000),null);
});

test('other players and stale scenes cannot produce a local progress bar',()=>{
  assert.equal(productionProgress({...task,actor:'other'},context),null);
  assert.equal(productionProgress({...task,sceneId:'old'},context),null);
  assert.equal(productionProgress({...task,version:'old'},context),null);
  assert.equal(productionProgress(task,{...context,objects:[]}),null);
});

test('actions without a map object attach to the player, carried corpse included',()=>{
  const personal=productionProgress({...task,objectId:null},context);
  assert.equal(personal.personal,true);
  assert.deepEqual(personal.cell,context.actor);
  const skin=productionProgress({...task,objectId:null,corpseId:'corpse'}, {...context,loot:[{id:'corpse',carrierId:'me',q:2,r:3,battleQ:0,battleR:0}]});
  assert.equal(skin.personal,true);
  const actorWithIdentity={...context.actor,characterId:'me',sceneId:'camp'};
  assert.deepEqual(productionProgress({...task,objectId:null},{...context,actor:actorWithIdentity}).cell,context.actor);
});

test('world gathering displays only own action at resource coordinates',()=>{
  const action={accountId:'me',kind:'wood',nodeId:'tree',q:9,r:9,startedAt:1000,endsAt:6000};
  const progress=worldProgress(action,'me',[{id:'tree',q:4,r:5}]);
  assert.deepEqual(progress.cell,{q:4,r:5});
  assert.equal(worldProgress(action,'other',[{id:'tree',q:4,r:5}]),null);
});
