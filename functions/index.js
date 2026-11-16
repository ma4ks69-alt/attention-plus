const {onDocumentCreated}=require('firebase-functions/v2/firestore');
const {onCall,HttpsError}=require('firebase-functions/v2/https');
const {defineString}=require('firebase-functions/params');
const {initializeApp}=require('firebase-admin/app');
const {getFirestore,FieldValue}=require('firebase-admin/firestore');
const {getAuth}=require('firebase-admin/auth');
const {validateCompletion,streak}=require('./rewards');
const {protectDays}=require('./economy');
initializeApp();
const db=getFirestore();
const region='europe-west1';
const apiKey=defineString('AUTH_WEB_API_KEY');
const callable={region,enforceAppCheck:process.env.FUNCTIONS_EMULATOR!=='true'};

exports.awardCompletion=onDocumentCreated({document:'users/{uid}/completions/{id}',region,retry:true},async event=>{
 const {uid,id}=event.params, data=event.data.data(), root=db.doc(`users/${uid}`), receipt=root.collection('receipts').doc(id);
 if((await root.get()).get("deleting"))return;
 let reward;
 try {reward=validateCompletion(data,Date.now());}
 catch(error){await root.collection('rejectedCompletions').doc(id).set({reason:error.message,createdAt:FieldValue.serverTimestamp()});return;}
 await require('./shop').settleStreak(uid);
 await db.runTransaction(async tx=>{
  if((await tx.get(receipt)).exists)return;
  const statsRef=root.collection('stats').doc('summary');
  const old=(await tx.get(statsRef)).data()||{};
  const history=await tx.get(root.collection('receipts'));
  const dates=new Set(history.docs.filter(doc=>doc.get('kind')==='study').map(doc=>doc.get('date')));if(data.kind==='study')dates.add(reward.date);
  const boosts=await tx.get(root.collection('boostHistory'));
  const rootData=(await tx.get(root)).data()||{};if(rootData.deleting)return;
  const acceptedAt=event.data.createTime.toMillis();
  const xp=reward.score*(data.kind==='study' && boosts.docs.some(doc=>acceptedAt>=doc.get('startsAt') && acceptedAt<doc.get('endsAt')) ? 2 : 1);
  const protectedStreak=protectDays(dates,old.protectedDays||[],0,new Date().toISOString().slice(0,10));
  tx.create(receipt,{...reward,xp,kind:data.kind,subject:data.subject,id,answers:data.answers,confirmedAt:FieldValue.serverTimestamp()});
  tx.set(statsRef,{points:(old.points||0)+reward.score,xp:(old.xp||0)+xp,
    totalStudySeconds:(old.totalStudySeconds||0)+reward.durationSeconds,sessions:(old.sessions||0)+1,
    streak:protectedStreak.streak,protectedDays:old.protectedDays||[],freezeCheckedThrough:old.freezeCheckedThrough||'',lastDay:[...dates].sort().at(-1)||'',updatedAt:FieldValue.serverTimestamp()});
  const subjectRef=root.collection('subjectStats').doc(data.subject);
  tx.set(subjectRef,{points:FieldValue.increment(reward.score),seconds:FieldValue.increment(reward.durationSeconds),sessions:FieldValue.increment(1)}, {merge:true});
  if(data.kind==='study')tx.set(root.collection('challenges').doc(`daily-${reward.date}`),{completed:true,date:reward.date,sessionId:id});
 });
});

exports.changeUsername=onCall(callable,async request=>{
 if(!request.auth)throw new HttpsError('unauthenticated','Jelentkezz be.');
 const username=String(request.data.username||'').trim().toLowerCase();
 if(!/^[a-z0-9_]{3,24}$/.test(username))throw new HttpsError('invalid-argument','A név 3–24 kisbetű, szám vagy aláhúzás lehet.');
 const uid=request.auth.uid,profile=db.doc(`users/${uid}/profile/main`),alias=db.doc(`usernames/${username}`);
 await db.runTransaction(async tx=>{
  const old=await tx.get(profile),found=await tx.get(alias);
  if(found.exists&&found.get('uid')!==uid)throw new HttpsError('already-exists','Ez a felhasználónév foglalt.');
  if((await tx.get(db.doc(`users/${uid}`))).get("deleting"))throw new HttpsError("failed-precondition","A fiók törlése folyamatban van.");
  const previous=old.get('username');
  tx.set(alias,{uid});tx.set(profile,{username},{merge:true});
  if(previous&&previous!==username)tx.delete(db.doc(`usernames/${previous}`));
 });
 return {username};
});

// Do not expose a public username -> email lookup. Passwords exist only for this
// HTTPS request, are never logged/persisted, and are checked by Firebase Auth.
exports.loginWithUsername=onCall({...callable,maxInstances:10},async request=>{
 const username=String(request.data.username||'').trim().toLowerCase(),password=String(request.data.password||'');
 const invalid=()=>new HttpsError('unauthenticated','Hibás felhasználónév vagy jelszó.');
 if(!/^[a-z0-9_]{3,24}$/.test(username)||password.length<1||password.length>4096)throw invalid();
 // Per-name server rate limit; no usernames/emails are disclosed to the caller.
 const limit=db.doc(`loginLimits/${username}`);
 await db.runTransaction(async tx=>{
  const d=(await tx.get(limit)).data()||{},now=Date.now(),same=now-(d.start||0)<60000;
  if(same&&d.count>=8)throw new HttpsError('resource-exhausted','Túl sok próbálkozás. Próbáld újra egy perc múlva.');
  tx.set(limit,{start:same?d.start:now,count:same?d.count+1:1});
 });
 const match=await db.doc(`usernames/${username}`).get();if(!match.exists)throw invalid();
 const user=await getAuth().getUser(match.get('uid'));if(!user.email)throw invalid();
 const host=process.env.FIREBASE_AUTH_EMULATOR_HOST;
 const url=host?`http://${host}/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=emulator`:`https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey.value()}`;
 const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:user.email,password,returnSecureToken:true}),signal:AbortSignal.timeout(15000)});
 if(!response.ok)throw invalid();
 const result=await response.json();if(result.localId!==user.uid)throw invalid();
 return {token:await getAuth().createCustomToken(user.uid)};
});


const shop=require('./shop');for(const name of ['shopAction','refreshStreak','protectDailyStreaks','deleteAccount'])exports[name]=shop[name];
