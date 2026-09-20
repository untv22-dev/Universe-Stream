import {normalize} from './core.mjs';
const arabic={
'AFC Bournemouth':'بورنموث', 'Arsenal FC':'أرسنال|ارسنال', 'Aston Villa':'أستون فيلا', 'Brentford FC':'برينتفورد', 'Brighton & Hove Albion':'برايتون', 'Chelsea FC':'تشيلسي', 'Coventry City':'كوفنتري سيتي', 'Crystal Palace':'كريستال بالاس', 'Everton FC':'إيفرتون', 'Fulham FC':'فولهام', 'Hull City':'هال سيتي', 'Ipswich Town':'إيبسويتش تاون', 'Leeds United':'ليدز يونايتد|ليدز', 'Liverpool FC':'ليفربول', 'Manchester City':'مانشستر سيتي|مان سيتي', 'Manchester United':'مانشستر يونايتد|مان يونايتد', 'Newcastle United':'نيوكاسل|نيوكاسل يونايتد', 'Nottingham Forest':'نوتنغهام فورست|نوتنجهام فورست', 'Sunderland AFC':'سندرلاند', 'Tottenham Hotspur':'توتنهام|توتنهام هوتسبير',
'AJ Auxerre':'أوكسير', 'AS Monaco':'موناكو', 'Angers SCO':'أنجيه', 'ESTAC Troyes':'تروا', 'FC Lorient':'لوريان', 'FC Toulouse':'تولوز', 'LOSC Lille':'ليل', 'Le Havre AC':'لوهافر|لو هافر', 'Le Mans FC':'لومان', 'OGC Nice':'نيس', 'Olympique Lyon':'ليون|أولمبيك ليون', 'Olympique Marseille':'مارسيليا|أولمبيك مارسيليا', 'Paris FC':'باريس إف سي', 'Paris Saint-Germain':'باريس سان جيرمان|باريس سان جرمان|PSG', 'RC Lens':'لانس', 'RC Strasbourg Alsace':'ستراسبورغ|ستراسبورج', 'Stade Brestois 29':'بريست', 'Stade Rennais FC':'رين',
'Athletic Bilbao':'أتلتيك بلباو|أتلتيك بيلباو', 'Atlético de Madrid':'أتلتيكو مدريد|اتليتكو مدريد|Atletico Madrid', 'CA Osasuna':'أوساسونا', 'Celta de Vigo':'سيلتا فيغو|سيلتا فيجو', 'Deportivo A Coruña':'ديبورتيفو لاكورونيا', 'Deportivo Alavés':'ألافيس', 'Elche CF':'إلتشي', 'FC Barcelona':'برشلونة|برشلونه', 'Getafe CF':'خيتافي', 'Levante UD':'ليفانتي', 'Málaga CF':'مالقا|مالاجا', 'RCD Espanyol Barcelona':'إسبانيول', 'Racing Santander':'راسينغ سانتاندير', 'Rayo Vallecano':'رايو فاليكانو', 'Real Betis Balompié':'ريال بيتيس|Real Betis', 'Real Madrid':'ريال مدريد', 'Real Sociedad':'ريال سوسيداد', 'Sevilla FC':'إشبيلية', 'Valencia CF':'فالنسيا', 'Villarreal CF':'فياريال',
'Alanyaspor':'ألانيا سبور', 'Amed SK':'أوميد سبور|آمد سبور|أميد', 'Basaksehir FK':'باشاك شهير', 'Besiktas JK':'بشكتاش|بيشكتاش', 'Caykur Rizespor':'ريزه سبور', 'Corum FK':'تشوروم', 'Erzurumspor FK':'أرضروم سبور', 'Eyüpspor':'أيوب سبور', 'Fenerbahce':'فنربخشة|فنربخشه|فنرباتشي', 'Galatasaray':'غلطة سراي|جالطة سراي', 'Gaziantep FK':'غازي عنتاب', 'Genclerbirligi Ankara':'غنتشلربيرليغي', 'Göztepe':'غوزتيبي', 'Kasimpasa':'قاسم باشا', 'Kocaelispor':'كوجالي سبور', 'Konyaspor':'قونيا سبور', 'Samsunspor':'سامسون سبور', 'Trabzonspor':'طرابزون سبور'};
// Levenshtein, capped: anything past `max` edits is already too far to accept, so stop counting.
function distance(a,b,max){if(Math.abs(a.length-b.length)>max)return max+1;let prev=Array.from({length:b.length+1},(_,i)=>i);for(let i=1;i<=a.length;i++){const row=[i];let best=i;for(let j=1;j<=b.length;j++){const v=a[i-1]===b[j-1]?prev[j-1]:1+Math.min(prev[j-1],prev[j],row[j-1]);row.push(v);if(v<best)best=v;}if(best>max)return max+1;prev=row;}return prev[b.length];}

// A name read off a screenshot can be a character or two out. An exact lookup rejects those, which
// would show a neutral mark for a club the catalogue does have.
//
// docs/design.md forbids ambiguous approximate matching, and this keeps to that: it accepts only
// one candidate within a tight budget (~12% of the name's length, at most 2 edits), requires the
// runner-up to be strictly worse, and reports what it corrected so the caller can show it. It is
// never silent, and it never guesses between two plausible clubs.
function nearest(map,key){const max=Math.min(2,Math.floor(key.length*0.12));if(max<1||key.length<5)return null;
let best=null,bestD=max+1,second=max+1;
for(const [candidate,item] of map){const d=distance(key,candidate,max);if(d<bestD){second=bestD;bestD=d;best=item;}else if(d<second)second=d;}
return bestD<=max&&bestD<second?{item:best,edits:bestD}:null;}

export async function loadCatalog(){const r=await fetch('./assets/clubs.json');if(!r.ok)throw Error('تعذر تحميل مكتبة الشعارات');const list=await r.json();const map=new Map();for(const item of list){const names=[item.name,item.name.replace(/\b(FC|AFC|CF|JK|SK|AJ|AS|RC|LOSC|OGC)\b/g,'').trim(),...(arabic[item.name]||'').split('|')];for(const n of names.filter(Boolean))map.set(normalize(n),item);}
const cache=new Map();
// Exact first, always. Approximate matching only runs when exact lookup found nothing.
const resolve=name=>{const key=normalize(name);if(!key)return null;if(cache.has(key))return cache.get(key);
const exact=map.get(key);const result=exact?{item:exact,edits:0}:nearest(map,key);cache.set(key,result);return result;};
return {find:name=>resolve(name)?.item,resolve,list};}
const leagueDefs=[{names:['الدوري الإنجليزي','الدوري الإنجليزي الممتاز','Premier League','EPL'],title:'Premier\nLeague',ar:'الدوري الإنجليزي\nالممتاز',color:'#321166',slogan:'More Than\na Game',logo:'pl'},{names:['الدوري الإسباني','LaLiga','La Liga'],title:'LALIGA',ar:'الدوري الإسباني',color:'#df2943',slogan:'Pasión por\nel Fútbol',logo:'pd'},{names:['الدوري الفرنسي','Ligue 1'],title:'LIGUE 1',ar:'الدوري الفرنسي',color:'#16419b',slogan:'Le Football\nToujours Plus Loin',logo:'fl1'},{names:['الدوري التركي','الدوري التركي الممتاز','Super Lig','Süper Lig'],title:'SüperLig',ar:'الدوري التركي\nالممتاز',color:'#bd2738',slogan:'Futbol Aşkına',logo:null}];
export function leagueFor(n){return leagueDefs.find(l=>l.names.some(a=>normalize(a)===normalize(n)))||{title:'',ar:n,color:'#49247b',slogan:'',logo:null};}
// Single source of truth for which league crests exist, so adding one to leagueDefs is enough
// to get it preloaded — poster.mjs no longer carries its own hardcoded copy of this list.
export const leagueLogoFiles=()=>leagueDefs.filter(l=>l.logo).map(l=>`assets/${l.logo}.png`);
