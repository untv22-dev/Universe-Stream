import test from 'node:test';import assert from 'node:assert/strict';import {parseDelimited,detectMapping,mapRows,validate,paginate,raggedRows,sample,formatTime} from '../dist/core.mjs';
test('Arabic sample imports 14 matches without altering names',()=>{const raw=parseDelimited(sample);const rows=mapRows(raw.slice(1),detectMapping(raw[0]));assert.equal(rows.length,14);assert.deepEqual(validate(rows),[]);assert.equal(rows[0].home,'مانشستر سيتي');assert.equal(rows[0].featured,true);assert.equal(paginate(rows).length,1);});
test('CSV quotes and Excel fraction',()=>{assert.deepEqual(parseDelimited('a,b\n"one,two","three"'),[['a','b'],['one,two','three']]);assert.equal(formatTime(0.75),'18:00');assert.equal(formatTime('٩:٣٠'),'09:30');assert.throws(()=>parseDelimited('"oops'));});
test('validation and max limits',()=>{assert.ok(validate([{home:'',away:'X',league:'L',time:'29:10'}]).length===2);assert.throws(()=>mapRows(Array(301).fill([]),[0,1,2,3,4,5,6]));});
// A time-formatted Excel cell arrives as a number. Only the fraction is the time of day, so a
// cell that also carries a date (45920.75) used to fall through as the raw serial and be rejected.
test('Excel serial times keep only the time of day',()=>{
  assert.equal(formatTime(0.75),'18:00','time-only fraction');
  assert.equal(formatTime(45920.75),'18:00','date+time serial');
  assert.equal(formatTime(0),'00:00');
  assert.equal(formatTime(45920),'','a date with no time of day must not invent midnight');
  assert.equal(formatTime(2130),'21:30','hand-typed HHMM');
  assert.equal(formatTime(2170),'','2170 is not a valid HHMM');
});

test('string times normalise separators, digits and seconds',()=>{
  assert.equal(formatTime('21:30'),'21:30');
  assert.equal(formatTime('9:30'),'09:30');
  assert.equal(formatTime('٩:٣٠'),'09:30','Arabic-Indic digits');
  assert.equal(formatTime('۲۱:۳۰'),'21:30','Persian digits');
  assert.equal(formatTime('21:30:00'),'21:30','CSV exports often carry seconds');
  assert.equal(formatTime('21.30'),'21:30');
  assert.equal(formatTime('9 PM'),'9 PM','unparseable input is preserved for validate() to report');
  assert.equal(formatTime(null),'');
});

test('ragged rows are reported by number, not silently accepted',()=>{
  assert.deepEqual(raggedRows([['a','b','c'],['1','2','3']]),[]);
  assert.deepEqual(raggedRows([['a','b','c'],['1','2','3','4'],['1','2','3'],['1','2']]),[2,4]);
  assert.deepEqual(raggedRows([['a','b']]),[],'a header alone has nothing to compare against');
});

test('pagination never loses rows and sections fit',()=>{const rows=Array.from({length:300},(_,i)=>({league:'L'+i%5,id:i}));const p=paginate(rows,()=>80);assert.equal(p.flatMap(s=>s.flatMap(x=>x.rows)).length,300);for(const page of p)assert.ok(page.reduce((a,s)=>a+s.height+20,0)<=940);});
