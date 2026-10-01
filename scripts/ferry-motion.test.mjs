import test from 'node:test';
import assert from 'node:assert/strict';
import {advanceRenderClock,ferryRouteProgress} from '../frontend/src/ferryMotion.ts';
const sailing={built:true,phase:'outbound',nextAt:2500,travelMs:10000};
test('late snapshot does not pause at an outbound tile boundary',()=>{
  assert.equal(ferryRouteProgress(sailing,0,5,2500),1);
  assert.equal(ferryRouteProgress(sailing,0,5,2750),1.1);
  assert.equal(ferryRouteProgress({...sailing,nextAt:5000},1,5,2750),1.1);
});
test('inbound motion remains continuous across snapshot changes',()=>{
  const ferry={...sailing,phase:'inbound'};
  assert.equal(ferryRouteProgress(ferry,4,5,2750),2.9);
  assert.equal(ferryRouteProgress({...ferry,nextAt:5000},3,5,2750),2.9);
});
test('a stale snapshot stops at the dock instead of predicting another trip',()=>{
  assert.equal(ferryRouteProgress(sailing,0,5,100000),4);
  assert.equal(ferryRouteProgress({...sailing,phase:'inbound'},4,5,100000),0);
  assert.equal(ferryRouteProgress({...sailing,phase:'island'},4,5,100000),4);
  assert.equal(ferryRouteProgress({...sailing,phase:'mainland'},0,5,100000),0);
});
test('unbuilt and degenerate routes have a finite stationary position',()=>{
  assert.equal(ferryRouteProgress({...sailing,built:false},0,5,10000),0);
  assert.equal(ferryRouteProgress(sailing,0,1,10000),0);
});

test('jittery network clock never rewinds or freezes an outbound boat',()=>{
  let clock=1000,previous=ferryRouteProgress(sailing,0,5,clock);
  for(let frame=1;frame<=120;frame++){
    const elapsed=1000/60,target=1000+frame*elapsed+(frame%20<10?-250:120);
    const next=advanceRenderClock(clock,elapsed,target);
    assert.ok(next>clock);
    assert.ok(Math.abs(next-clock-elapsed)<=elapsed*.100001);
    const progress=ferryRouteProgress(sailing,0,5,next);
    assert.ok(progress>previous);previous=progress;clock=next;
  }
});
test('render clock resumes after suspension and corrects drift gradually',()=>{
  assert.equal(advanceRenderClock(1000,5000,6100),6100);
  assert.equal(advanceRenderClock(1000,0,900),1000);
  let clock=1000;
  for(let frame=1;frame<=100;frame++)clock=advanceRenderClock(clock,20,1000+20*frame+100);
  assert.equal(clock,3100);
});
