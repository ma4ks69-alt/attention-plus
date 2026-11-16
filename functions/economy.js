const catalog=require('./catalog/items.json');
function protectDays(dates,protectedDays,count,today,checkedThrough){
 const actual=new Set(dates),protectedSet=new Set(protectedDays||[]);let remaining=count,consumed=[];
 const last=[...actual,...protectedSet].sort().at(-1);if(!last)return {protectedDays:[...protectedSet],remaining,consumed,streak:0};
 let day=Date.parse(last)+86400000,end=Date.parse(today);
 while(day<end){const text=new Date(day).toISOString().slice(0,10);if(!actual.has(text)&&!protectedSet.has(text)){if(remaining<1 || (checkedThrough && text<=checkedThrough))break;remaining--;protectedSet.add(text);consumed.push(text);}day+=86400000;}
 let n=0,cursor=end;if(!actual.has(today)&&!protectedSet.has(today))cursor-=86400000;
 while(true){const d=new Date(cursor).toISOString().slice(0,10);if(actual.has(d))n++;else if(!protectedSet.has(d))break;cursor-=86400000;}
 return {protectedDays:[...protectedSet].sort(),remaining,consumed,streak:n};
}
function price(id){if(!Object.hasOwn(catalog,id))throw Error('Ismeretlen tárgy.');return catalog[id].price;}
module.exports={catalog,protectDays,price};
