import test from 'node:test';
import assert from 'node:assert/strict';
import {ferryRouteProgress} from '../frontend/src/ferryMotion.ts';
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
