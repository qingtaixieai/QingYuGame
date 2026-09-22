import pg from 'pg';
import {randomBytes} from 'node:crypto';
import {existsSync,writeFileSync} from 'node:fs';
const client=new pg.Client({host:'127.0.0.1',port:55432,user:'worldgame',database:'postgres'});
await client.connect();
if(!(await client.query("select 1 from pg_database where datname='worldgame'")).rowCount)await client.query('create database worldgame');
await client.end();
if(!existsSync('../.local/dev.json'))writeFileSync('../.local/dev.json',JSON.stringify({username:'admin',password:randomBytes(18).toString('base64url')}));
console.log('Local PostgreSQL database and development credentials ready.');
