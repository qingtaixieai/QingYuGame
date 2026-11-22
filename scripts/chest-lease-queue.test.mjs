import test from 'node:test';
import assert from 'node:assert/strict';
import {queueChestLease} from '../frontend/src/shared/chestLeaseQueue.ts';
const deferred=()=>{let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};};

test('late open closes in its operation before the next page opens',async()=>{
 const opened=deferred(),closed=deferred(),started=deferred(),events=[];
 let disposed=false;
 const old=queueChestLease('late',async()=>{
  events.push('old open');started.resolve();await opened.promise;
  if(disposed){events.push('old close');await closed.promise;}
 });
 await started.promise;
 disposed=true;
 const next=queueChestLease('late',async()=>{events.push('new open');});
 opened.resolve();await Promise.resolve();await Promise.resolve();
 assert.deepEqual(events,['old open','old close']);
 closed.resolve();await Promise.all([old,next]);
 assert.deepEqual(events,['old open','old close','new open']);
});

test('a failed operation is reported and the next operation still runs',async()=>{
 const events=[];
 const failed=queueChestLease('failure',async()=>{throw Error('network');});
 const next=queueChestLease('failure',async()=>{events.push('next');return 42;});
 await assert.rejects(failed,/network/);
 assert.equal(await next,42);assert.deepEqual(events,['next']);
});

test('different chests run independently',async()=>{
 const gate=deferred(),started=deferred();
 const a=queueChestLease('A',async()=>{started.resolve();await gate.promise;});
 await started.promise;
 assert.equal(await queueChestLease('B',async()=>17),17);
 gate.resolve();await a;
});

test('an effect disposed while queued can skip its request',async()=>{
 const gate=deferred();let disposed=false,opened=false;
 const previous=queueChestLease('skip',()=>gate.promise);
 const next=queueChestLease('skip',async()=>{if(disposed)return;opened=true;});
 disposed=true;gate.resolve();await Promise.all([previous,next]);
 assert.equal(opened,false);
});
