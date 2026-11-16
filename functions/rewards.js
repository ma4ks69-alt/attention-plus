const key=[1,2,3,0,1,2,3,0,1,2];
function validateCompletion(data,now){
 if(!['study','focus'].includes(data.kind)||!['Math','Literature','English','Biology','Science','ForeignLanguage','History','Focus'].includes(data.subject))throw Error('Invalid session kind or subject');
 if(!Number.isSafeInteger(data.startedAt)||!Number.isSafeInteger(data.finishedAt)||data.startedAt<0||data.finishedAt<data.startedAt||data.finishedAt>now+300000)throw Error('Invalid timestamps');
 if(!Number.isSafeInteger(data.durationSeconds)||data.durationSeconds<0||data.durationSeconds>86400||data.durationSeconds>Math.ceil((data.finishedAt-data.startedAt)/1000)+1)throw Error('Invalid study duration');
 if(!Array.isArray(data.answers)||data.answers.some(x=>!Number.isInteger(x)||x<0||x>3))throw Error('Invalid answers');
 if(data.kind==='study'&&(data.quizVersion!=='history-v1'||data.answers.length!==10))throw Error('Invalid quiz');
 if(data.kind==='focus'&&data.answers.length!==0)throw Error('Invalid focus answers');
 return {score:data.kind==='study'?key.reduce((n,a,i)=>n+(a===data.answers[i]?100:0),0):0,durationSeconds:data.durationSeconds,date:new Date(data.finishedAt).toISOString().slice(0,10)};
}
function streak(dates){const sorted=[...dates].sort();if(!sorted.length)return 0;let count=0,time=Date.parse(sorted.at(-1));while(dates.has(new Date(time).toISOString().slice(0,10))){count++;time-=86400000;}return count;}
module.exports={validateCompletion,streak};
