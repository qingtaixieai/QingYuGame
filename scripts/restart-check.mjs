import pg from 'pg';
import {createHash} from 'node:crypto';
import {readFileSync,writeFileSync} from 'node:fs';
import assert from 'node:assert/strict';
const client=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'worldgame'});
await client.connect();
const world=(await client.query('select document from worlds where id=1')).rows[0].document;
const positions=(await client.query('select id,q,r,approved from accounts order by id')).rows;
const state={worldHash:createHash('sha256').update(world).digest('hex'),positions};
if(process.argv[2]==='before'){writeFileSync('../.local/restart-before.json',JSON.stringify(state));console.log('Saved world and position fingerprints before restart.');}
else {assert.deepEqual(state,JSON.parse(readFileSync('../.local/restart-before.json','utf8')));console.log('PASS world and character positions unchanged across restart.');}
await client.end();
