import assert from 'node:assert/strict';
import {bindMapGestures} from '../frontend/src/mapGestures.ts';
class Target {handlers=new Map();capture=new Set();addEventListener(k,f){this.handlers.set(k,f)}removeEventListener(k){this.handlers.delete(k)}getBoundingClientRect(){return {left:10,top:20}}setPointerCapture(id){this.capture.add(id)}hasPointerCapture(id){return this.capture.has(id)}releasePointerCapture(id){this.capture.delete(id)}emit(type,id,x,y){this.handlers.get(type)?.({type,pointerId:id,clientX:x+10,clientY:y+20,button:0})}}
globalThis.window=new Target();const node=new Target(),taps=[],pans=[],zooms=[];const cleanup=bindMapGestures(node,{tap:(...p)=>taps.push(p),pan:(...p)=>pans.push(p),zoom:(...p)=>zooms.push(p)});
node.emit('pointerdown',1,50,50);node.emit('pointerup',1,50,50);assert.deepEqual(taps,[[50,50]]);
node.emit('pointerdown',1,50,50);node.emit('pointermove',1,70,60);node.emit('pointerup',1,70,60);assert.equal(taps.length,1);assert.deepEqual(pans.at(-1),[20,10]);
node.emit('pointerdown',1,100,100);node.emit('pointerdown',2,200,100);node.emit('pointermove',2,300,100);assert.equal(zooms.at(-1)[0],2);assert.deepEqual(zooms.at(-1).slice(1),[150,100]);node.emit('pointerup',2,300,100);node.emit('pointermove',1,110,100);node.emit('pointerup',1,110,100);assert.equal(taps.length,1);
node.emit('pointerdown',1,10,10);node.emit('pointercancel',1,10,10);assert.equal(taps.length,1);cleanup();assert.equal(node.handlers.size,0);assert.equal(window.handlers.size,0);console.log('PASS gestures: tap, pan, pinch anchor, no tap after pinch/cancel, cleanup');
