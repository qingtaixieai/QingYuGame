import test from 'node:test';
import assert from 'node:assert/strict';
import {ActionResultFeed} from '../frontend/src/shared/actionResults.ts';

const result=(id,happenedAt,actor='me')=>({id,actor,happenedAt,outcome:'success',items:[],message:'木头 +1'});
test('initial and reconnected history never replays effects',()=>{
  const feed=new ActionResultFeed();
  assert.deepEqual(feed.receive([result('old',1000)],'me',1200),[]);
  assert.deepEqual(feed.receive([result('old',1000)],'me',1500),[]);
  assert.deepEqual(new ActionResultFeed().receive([result('old',1000)],'me',1500),[]);
});
test('new authoritative results animate once and only for their owner',()=>{
  const feed=new ActionResultFeed();feed.receive([],'me',1000);
  assert.deepEqual(feed.receive([result('new',1100),result('other',1100,'other')],'me',1200).map(r=>r.id),['new']);
  assert.deepEqual(feed.receive([result('new',1100)],'me',1400),[]);
});
test('late or implausibly future results remain history without an animation',()=>{
  const feed=new ActionResultFeed();feed.receive([],'me',10000);
  assert.deepEqual(feed.receive([result('late',1000),result('future',20000)],'me',10000),[]);
});
test('duplicate IDs in the same snapshot produce only one animation',()=>{
  const feed=new ActionResultFeed();feed.receive([],'me',1000);
  assert.deepEqual(feed.receive([result('same',1100),result('same',1100)],'me',1200).map(r=>r.id),['same']);
});
