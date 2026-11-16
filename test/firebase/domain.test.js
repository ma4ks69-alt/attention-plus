const test=require('node:test');const assert=require('node:assert/strict');
const {validateCompletion,streak}=require('../../functions/rewards');
test('reward derives from answers, never from supplied XP',()=>{const now=Date.now();const r=validateCompletion({kind:'study',subject:'Math',startedAt:now-120000,finishedAt:now,durationSeconds:60,answers:[1,2,3,0,1,2,3,0,1,0],quizVersion:'history-v1',xp:999999},now);assert.equal(r.score,900);});
test('invalid duration, answers and future completions are rejected',()=>{const now=Date.now();const good={kind:'study',subject:'Math',startedAt:now-10000,finishedAt:now,durationSeconds:8,answers:[1,2,3,0,1,2,3,0,1,2],quizVersion:'history-v1'};assert.throws(()=>validateCompletion({...good,durationSeconds:999999},now));assert.throws(()=>validateCompletion({...good,answers:[9]},now));assert.throws(()=>validateCompletion({...good,finishedAt:now+86400000},now));});
test('late offline days produce the correct consecutive streak without same-day inflation',()=>{assert.equal(streak(new Set(['2026-11-15','2026-11-13','2026-11-14','2026-11-15'])),3);assert.equal(streak(new Set(['2026-11-10','2026-11-15'])),1);});
const {protectDays}=require('../../functions/economy');
test('freeze cannot retroactively restore an already broken streak',()=>{
 const r=protectDays(['2026-11-10'],[],5,'2026-11-16','2026-11-14');assert.equal(r.streak,0);assert.equal(r.consumed.length,0);
 const protectedResult=protectDays(['2026-11-13','2026-11-14'],[],1,'2026-11-16');assert.equal(protectedResult.streak,2);assert.equal(protectedResult.remaining,0);assert.deepEqual(protectedResult.protectedDays,['2026-11-15']);
});
