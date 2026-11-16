const {onCall,HttpsError}=require('firebase-functions/v2/https');
const {onSchedule}=require('firebase-functions/v2/scheduler');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {getAuth}=require('firebase-admin/auth');
const {getStorage}=require('firebase-admin/storage');
const {catalog,protectDays}=require('./economy');
const options={region:'europe-west1',enforceAppCheck:process.env.FUNCTIONS_EMULATOR!=='true',maxInstances:10};
const db=getFirestore();
async function refresh(uid){
 const root=db.doc(`users/${uid}`);
 await db.runTransaction(async tx=>{
  const stats=root.collection('stats').doc('summary'),ice=root.collection('inventory').doc('streak_freeze');
  const [s,i,r,account]=await Promise.all([tx.get(stats),tx.get(ice),tx.get(root.collection('receipts')),tx.get(root)]);if(account.get('deleting'))return;
  const state=s.data()||{},today=new Date().toISOString().slice(0,10);
  const next=protectDays(r.docs.filter(d=>d.get('kind')==='study').map(d=>d.get('date')),state.protectedDays||[],i.get('count')||0,today,state.freezeCheckedThrough);
  if(next.consumed.length){tx.set(ice,{id:'streak_freeze',name:catalog.streak_freeze.name,count:next.remaining},{merge:true});for(const day of next.consumed)tx.create(root.collection('consumptions').doc(`freeze-${day}`),{item:'streak_freeze',day,createdAt:FieldValue.serverTimestamp()});}
  if(s.exists)tx.set(stats,{streak:next.streak,protectedDays:next.protectedDays,freezeCheckedThrough:new Date(Date.parse(today)-86400000).toISOString().slice(0,10)},{merge:true});
 });
}
exports.refreshStreak=onCall(options,async req=>{if(!req.auth)throw new HttpsError('unauthenticated','Jelentkezz be.');await refresh(req.auth.uid);return {ok:true};});
exports.protectDailyStreaks=onSchedule({schedule:'10 0 * * *',timeZone:'UTC',region:'europe-west1'},async()=>{
 const users=await db.collection('users').listDocuments();for(const root of users)await refresh(root.id);
});
exports.shopAction=onCall(options,async req=>{
 if(!req.auth)throw new HttpsError('unauthenticated','Jelentkezz be.');
 const {operationId,item,action}=req.data||{};
 if(!/^[a-f0-9-]{36}$/.test(operationId||'')||!Object.hasOwn(catalog,item)||!['buy','activate'].includes(action))throw new HttpsError('invalid-argument','Érvénytelen vásárlás.');
 const uid=req.auth.uid,root=db.doc(`users/${uid}`);await refresh(uid);
 return db.runTransaction(async tx=>{
  const op=root.collection('operations').doc(operationId),wallet=root.collection('wallet').doc('main'),inventory=root.collection('inventory').doc(item),boost=root.collection('boosts').doc('active');
  const [old,w,i,s,b,account]=await Promise.all([tx.get(op),tx.get(wallet),tx.get(inventory),tx.get(root.collection('stats').doc('summary')),tx.get(boost),tx.get(root)]);
  if(account.get('deleting'))throw new HttpsError('failed-precondition','A fiók törlése folyamatban van.');
  if(old.exists){if(old.get('item')!==item||old.get('action')!==action)throw new HttpsError('already-exists','Azonosítóütközés.');return old.data();}
  const spent=w.get('spent')||0,earned=s.get('points')||0,count=i.get('count')||0,now=Date.now();
  let result={item,action,operationId,createdAt:now};
  if(action==='buy'){
   const price=catalog[item].price;if(earned-spent<price)throw new HttpsError('failed-precondition','Nincs elég pontod ehhez a tárgyhoz.');
   tx.set(wallet,{spent:spent+price});tx.set(inventory,{id:item,name:catalog[item].name,count:count+1});result={...result,price,balance:earned-spent-price};
  }else{
   if(item!=='xp_potion')throw new HttpsError('failed-precondition','A széria befagyasztó automatikusan működik.');
   if(count<1)throw new HttpsError('failed-precondition','Nincs ilyen tárgy az eszköztáradban.');
   if((b.get('endsAt')||0)>now)throw new HttpsError('failed-precondition','Már aktív a dupla XP.');
   const endsAt=now+catalog.xp_potion.durationSeconds*1000;
   tx.set(inventory,{id:item,name:catalog[item].name,count:count-1});tx.set(boost,{startsAt:now,endsAt,multiplier:2});
   tx.create(root.collection('boostHistory').doc(operationId),{startsAt:now,endsAt,multiplier:2});result={...result,endsAt};
  }
  tx.create(op,result);return result;
 });
});
exports.deleteAccount=onCall({...options,timeoutSeconds:300},async req=>{
 if(!req.auth)throw new HttpsError('unauthenticated','Jelentkezz be újra.');
 if(Date.now()/1000-req.auth.token.auth_time>300)throw new HttpsError('failed-precondition','A törléshez jelentkezz be újra.');
 const uid=req.auth.uid,root=db.doc(`users/${uid}`),profile=await root.collection('profile').doc('main').get(),alias=profile.get('username');
 await root.set({deleting:true});
 // Keep a minimal revocation tombstone so existing one-hour ID tokens cannot recreate private data.
 const bucket=getStorage().bucket();try{await bucket.deleteFiles({prefix:`users/${uid}/`});}catch(e){if(e.code!==404)throw e;}
 for(const collection of await root.listCollections())await db.recursiveDelete(collection);
 if(alias){const ref=db.doc(`usernames/${alias}`);await db.runTransaction(async tx=>{if((await tx.get(ref)).get('uid')===uid)tx.delete(ref);});}
 try{await getAuth().deleteUser(uid);}catch(e){if(e.code!=="auth/user-not-found")throw e;}await root.set({deleting:true,deletedAt:FieldValue.serverTimestamp()});return {ok:true};
});

exports.settleStreak=refresh;
