const {test,before,after}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs');const {randomUUID}=require('node:crypto');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,deleteDoc,collection,getDocs,disableNetwork,enableNetwork,serverTimestamp}=require('firebase/firestore');
const {ref,uploadBytes,getBytes}=require('firebase/storage');
const {initializeApp,deleteApp}=require('firebase/app');
const {getAuth,connectAuthEmulator,createUserWithEmailAndPassword,signInWithEmailAndPassword,signOut}=require('firebase/auth');
const {getFirestore,connectFirestoreEmulator}=require('firebase/firestore');
const {getFunctions,connectFunctionsEmulator,httpsCallable}=require('firebase/functions');
let env;const apps=[];const projectId='attentionplus-adef4';
before(async()=>{env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8080,rules:fs.readFileSync('firestore.rules','utf8')},storage:{host:'127.0.0.1',port:9199,rules:fs.readFileSync('storage.rules','utf8')}});});
after(async()=>{await Promise.all(apps.map(deleteApp));await env.cleanup();});
function client(name){const app=initializeApp({projectId,apiKey:'emulator',appId:'emulator'},name);apps.push(app);const auth=getAuth(app);connectAuthEmulator(auth,'http://127.0.0.1:9099',{disableWarnings:true});const db=getFirestore(app);connectFirestoreEmulator(db,'127.0.0.1',8080);const functions=getFunctions(app,'europe-west1');connectFunctionsEmulator(functions,'127.0.0.1',5001);return {app,auth,db,functions};}
function change(bucket='preferences',entity='app',patch={focusSeconds:60}){const id=randomUUID();return {id,bucket,entity,clock:1,patch};}
async function until(fn,timeout=20000){const end=Date.now()+timeout;while(Date.now()<end){const value=await fn();if(value)return value;await new Promise(r=>setTimeout(r,150));}throw Error('Timed out waiting for backend');}
test('private events are isolated and immutable; rewards cannot be forged',async()=>{
 const a=env.authenticatedContext('alice',{email:'alice@test.invalid'}).firestore(),b=env.authenticatedContext('bob').firestore(),anon=env.unauthenticatedContext().firestore();
 const event=change(),path=`users/alice/changes/${event.id}`;
 await assertSucceeds(setDoc(doc(a,path),event));await assertSucceeds(setDoc(doc(a,path),event));
 await assertFails(getDoc(doc(b,path)));await assertFails(getDoc(doc(anon,path)));await assertFails(setDoc(doc(b,path),event));
 await assertFails(setDoc(doc(a,path),{...event,patch:{focusSeconds:999}}));
 await assertFails(setDoc(doc(a,'users/alice/stats/summary'),{xp:999999}));
 await assertFails(setDoc(doc(a,'users/alice/inventory/free-premium'),{unlocked:true}));
 await assertFails(getDoc(doc(a,'usernames/anotheruser')));
 await assertFails(setDoc(doc(a,`users/alice/changes/${randomUUID()}`),{...change(),bucket:'rewards'}));
});
test('profile validation forbids email/username takeover and cross-account edits',async()=>{
 const uid=`profile-${randomUUID()}`,a=env.authenticatedContext(uid,{email:'a@test.invalid'}).firestore();const p=doc(a,`users/${uid}/profile/main`);
 const profile={email:'a@test.invalid',displayName:'a@test.invalid',photo:'',birthday:'2000-01-01',username:'',createdAt:serverTimestamp()};
 await assertSucceeds(setDoc(p,profile));await assertSucceeds(setDoc(p,{displayName:'Changed'},{merge:true}));
 await assertFails(setDoc(p,{username:'admin'},{merge:true}));await assertFails(setDoc(p,{email:'victim@test.invalid'},{merge:true}));
 await assertFails(setDoc(p,{password:'never-store-this'},{merge:true}));
});
test('Storage attachments are owner-only and reject non-images',async()=>{
 const a=env.authenticatedContext('media-a').storage(),b=env.authenticatedContext('media-b').storage();const path=`users/media-a/media/${randomUUID()}`;
 await assertSucceeds(uploadBytes(ref(a,path),new Uint8Array([137,80,78,71]),{contentType:'image/png'}));
 await assertSucceeds(getBytes(ref(a,path)));await assertFails(getBytes(ref(b,path)));
 await assertFails(uploadBytes(ref(a,`users/media-a/media/${randomUUID()}`),new Uint8Array([1]),{contentType:'text/plain'}));
});
test('Auth registration, password login, independent clients, offline replay, username login and exactly-once rewards',async()=>{
 const a=client(`device-a-${randomUUID()}`),b=client(`device-b-${randomUUID()}`);const email=`test-${randomUUID()}@example.test`,password='StrongPassword42!';
 const user=(await createUserWithEmailAndPassword(a.auth,email,password)).user;
 await assert.rejects(signInWithEmailAndPassword(b.auth,email,'incorrect'));
 await signInWithEmailAndPassword(b.auth,email,password);assert.equal(b.auth.currentUser.uid,user.uid);
 await setDoc(doc(a.db,`users/${user.uid}/profile/main`),{email,displayName:email,photo:'',birthday:'2000-01-01',username:'',createdAt:serverTimestamp()});
 const alias='test_'+randomUUID().slice(0,8);
 await httpsCallable(a.functions,'changeUsername')({username:alias});await signOut(b.auth);
 const token=(await httpsCallable(b.functions,'loginWithUsername')({username:alias,password})).data.token;
 const {signInWithCustomToken}=require('firebase/auth');await signInWithCustomToken(b.auth,token);assert.equal(b.auth.currentUser.uid,user.uid);
 const event=change('drafts','learning',{subject:'Math',text:'Offline note'}),path=`users/${user.uid}/changes/${event.id}`;
 await disableNetwork(a.db);const queued=setDoc(doc(a.db,path),event);await new Promise(r=>setTimeout(r,100));
 assert.equal((await getDoc(doc(b.db,path))).exists(),false);await enableNetwork(a.db);await queued;
 assert.equal((await getDoc(doc(b.db,path))).get('patch.text'),'Offline note');
 const second=change('preferences','app',{profileSubject:'Math'});await setDoc(doc(b.db,`users/${user.uid}/changes/${second.id}`),second);
 assert.equal((await getDocs(collection(a.db,`users/${user.uid}/changes`))).size,2);
 const id=randomUUID(),now=Date.now(),completion={kind:'study',subject:'Math',startedAt:now-10000,finishedAt:now,durationSeconds:5,answers:[1,2,3,0,1,2,3,0,1,2],quizVersion:'history-v1'};
 const target=doc(a.db,`users/${user.uid}/completions/${id}`);await setDoc(target,completion);await setDoc(target,completion);
 const receipt=await until(async()=>{const d=await getDoc(doc(b.db,`users/${user.uid}/receipts/${id}`));return d.exists()?d:null;});assert.equal(receipt.get('score'),1000);
 assert.equal((await getDoc(doc(a.db,`users/${user.uid}/stats/summary`))).get('xp'),1000);
 await signOut(b.auth);await createUserWithEmailAndPassword(b.auth,`other-${randomUUID()}@example.test`,password);
 await assert.rejects(getDoc(doc(b.db,path)));
});

test('shop is atomic and idempotent; XP boost is server timed and never multiplies currency',async()=>{
 const a=client(`shop-${randomUUID()}`),user=(await createUserWithEmailAndPassword(a.auth,`shop-${randomUUID()}@example.test`,'StrongPassword42!')).user,uid=user.uid;
 await env.withSecurityRulesDisabled(async ctx=>{await setDoc(doc(ctx.firestore(),`users/${uid}/stats/summary`),{points:1000,xp:1000});});
 const shop=httpsCallable(a.functions,'shopAction');const op={operationId:randomUUID(),action:'buy',item:'xp_potion'};
 await Promise.all([shop(op),shop(op),shop(op)]);
 assert.equal((await getDoc(doc(a.db,`users/${uid}/wallet/main`))).get('spent'),300);
 assert.equal((await getDoc(doc(a.db,`users/${uid}/inventory/xp_potion`))).get('count'),1);
 const activation={operationId:randomUUID(),action:'activate',item:'xp_potion'};const result=(await shop(activation)).data;await shop(activation);
 const boost=await getDoc(doc(a.db,`users/${uid}/boosts/active`));assert.equal(boost.get('endsAt')-boost.get('startsAt'),1800000);assert.equal(boost.get('endsAt'),result.endsAt);
 assert.equal((await getDoc(doc(a.db,`users/${uid}/inventory/xp_potion`))).get('count'),0);
 await assert.rejects(shop({...activation,operationId:randomUUID()}));
 const id=randomUUID(),now=Date.now();await setDoc(doc(a.db,`users/${uid}/completions/${id}`),{kind:'study',subject:'Math',startedAt:now-10000,finishedAt:now,durationSeconds:5,answers:[1,2,3,0,1,2,3,0,1,2],quizVersion:'history-v1'});
 const receipt=await until(async()=>{const d=await getDoc(doc(a.db,`users/${uid}/receipts/${id}`));return d.exists()?d:null});assert.equal(receipt.get('score'),1000);assert.equal(receipt.get('xp'),2000);
 assert.equal((await getDoc(doc(a.db,`users/${uid}/stats/summary`))).get('points'),2000);
 const poor=client(`poor-${randomUUID()}`);await createUserWithEmailAndPassword(poor.auth,`poor-${randomUUID()}@example.test`,'StrongPassword42!');await assert.rejects(httpsCallable(poor.functions,'shopAction')({operationId:randomUUID(),action:'buy',item:'streak_freeze'}));
 for(const bucket of ['wallet','operations','boosts','boostHistory','consumptions'])await assertFails(setDoc(doc(a.db,`users/${uid}/${bucket}/forged`),{count:999,points:999999}));
});

test('automatic freeze consumes one item once and never counts the protected day as studied',async()=>{
 const a=client(`freeze-${randomUUID()}`),uid=(await createUserWithEmailAndPassword(a.auth,`freeze-${randomUUID()}@example.test`,'StrongPassword42!')).user.uid;
 const day=n=>new Date(Date.now()-n*86400000).toISOString().slice(0,10);
 await env.withSecurityRulesDisabled(async ctx=>{const db=ctx.firestore();await setDoc(doc(db,`users/${uid}/stats/summary`),{points:1000,xp:1000});await setDoc(doc(db,`users/${uid}/receipts/prior`),{kind:'study',date:day(2),score:1000});await setDoc(doc(db,`users/${uid}/inventory/streak_freeze`),{id:'streak_freeze',count:2});});
 await Promise.all([httpsCallable(a.functions,'refreshStreak')(),httpsCallable(a.functions,'refreshStreak')()]);
 assert.equal((await getDoc(doc(a.db,`users/${uid}/inventory/streak_freeze`))).get('count'),1);
 const stats=await getDoc(doc(a.db,`users/${uid}/stats/summary`));assert.equal(stats.get('streak'),1);assert.deepEqual(stats.get('protectedDays'),[day(1)]);
 assert.equal((await getDocs(collection(a.db,`users/${uid}/consumptions`))).size,1);
});

test('account deletion removes private data and blocks still-valid tokens from recreating it',async()=>{
 const a=client(`delete-${randomUUID()}`),uid=(await createUserWithEmailAndPassword(a.auth,`delete-${randomUUID()}@example.test`,'StrongPassword42!')).user.uid;
 const event=change();await setDoc(doc(a.db,`users/${uid}/changes/${event.id}`),event);
 const token=await a.auth.currentUser.getIdToken();
 await httpsCallable(a.functions,'deleteAccount')();
 await env.withSecurityRulesDisabled(async ctx=>{assert.equal((await getDocs(collection(ctx.firestore(),`users/${uid}/changes`))).size,0);assert.equal((await getDoc(doc(ctx.firestore(),`users/${uid}`))).get('deleting'),true);});
 await assertFails(setDoc(doc(a.db,`users/${uid}/changes/${randomUUID()}`),change()));
 const stale=env.authenticatedContext(uid).storage();await assertFails(uploadBytes(ref(stale,`users/${uid}/media/${randomUUID()}`),new Uint8Array([137,80,78,71]),{contentType:'image/png'}));
 const res=await fetch('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:lookup?key=emulator',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({idToken:token})});assert.notEqual(res.status,200);
});
