export const fields=['league','time','home','away','commentator','channel','featured'];
export const labels=['الدوري','الوقت','الفريق الأول','الفريق الثاني','المعلق','القناة','مميز'];
export const normalize=s=>String(s??'').normalize('NFD').replace(/[\u0300-\u036f\u064B-\u065F\u0670ـ]/g,'').replace(/[أإآ]/g,'ا').replace(/ى/g,'ي').toLowerCase().replace(/[^\p{L}\p{N}]/gu,'');
const aliases=[['league','competition','الدوري','البطولة'],['time','kickoff','الوقت','التوقيت'],['home','team1','الفريقالأول','الفريق1','صاحبالأرض'],['away','team2','الفريقالثاني','الفريق2','الضيف'],['commentator','المعلق'],['channel','القناة'],['featured','مميز','مميزة']];
export function detectMapping(row){return fields.map((_,i)=>row.findIndex(c=>aliases[i].some(a=>normalize(a)===normalize(c))));}
export function parseDelimited(text){text=String(text).replace(/^\uFEFF/,'').replace(/\r\n?/g,'\n').trim();if(!text)return [];const first=text.split('\n')[0];const sep=['\t','|',',',';'].sort((a,b)=>first.split(b).length-first.split(a).length)[0];let rows=[],row=[],v='',quoted=false;for(let i=0;i<text.length;i++){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){v+='"';i++;}else quoted=!quoted;}else if(!quoted&&(c===sep||c==='\n')){row.push(v.trim());v='';if(c==='\n'){rows.push(row);row=[];}}else v+=c;}if(quoted)throw Error('علامة اقتباس غير مغلقة في الجدول');row.push(v.trim());rows.push(row);return rows.filter(r=>r.some(c=>c!==''));}
export function formatTime(v){if(typeof v==='number'&&v>=0&&v<1){const n=Math.round(v*1440)%1440;return `${String(Math.floor(n/60)).padStart(2,'0')}:${String(n%60).padStart(2,'0')}`;}const s=String(v??'').trim().replace(/[٠-٩]/g,c=>'٠١٢٣٤٥٦٧٨٩'.indexOf(c));return /^\d:\d{2}$/.test(s)?'0'+s:s;}
export function mapRows(rows,mapping){if(rows.length>300)throw Error('الحد الأقصى 300 مباراة');return rows.map((row,i)=>Object.fromEntries([['id',i+1],...fields.map((f,j)=>{const raw=mapping[j]<0?'':row[mapping[j]]??'';return [f,f==='featured'?['نعم','yes','true','1','مميز','★'].includes(String(raw).trim().toLowerCase()):f==='time'?formatTime(raw):String(raw).trim()];})]));}
export function validate(rows){const errors=[];if(!rows.length)errors.push('أضف مباراة واحدة على الأقل');if(rows.length>300)errors.push('الحد الأقصى 300 مباراة');rows.forEach((r,i)=>{for(const f of ['league','home','away'])if(!r[f]?.trim())errors.push(`المباراة ${i+1}: ${labels[fields.indexOf(f)]} مطلوب`);if(!/^(?:[01]\d|2[0-3]):[0-5]\d$/.test(r.time))errors.push(`المباراة ${i+1}: الوقت يجب أن يكون مثل 21:30`);for(const f of fields.filter(x=>x!=='featured'))if(String(r[f]).length>160)errors.push(`المباراة ${i+1}: النص أطول من 160 حرفًا`);});return errors;}
export function groupRows(rows){const groups=new Map();for(const r of rows){if(!groups.has(r.league))groups.set(r.league,[]);groups.get(r.league).push(r);}return [...groups].map(([league,matches])=>({league,matches}));}
export function paginate(rows,heightOf=()=>48){const pages=[];let sections=[],used=0;const limit=940;for(const group of groupRows(rows)){let section=null;for(const row of group.matches){const h=heightOf(row);if(h+60>limit)throw Error('النص لا يتسع داخل البوستر');if(!section||used+h>limit){if(used+h+(!section?60:0)>limit&&sections.length){pages.push(sections);sections=[];used=0;}section={league:group.league,rows:[],height:40};sections.push(section);used+=60;}section.rows.push({data:row,height:h});section.height+=h;used+=h;}section=null;}if(sections.length)pages.push(sections);return pages;}
export const sample=`الدوري | الوقت | الفريق الأول | الفريق الثاني | المعلق | القناة | مميز
الدوري الإنجليزي | 16:00 | مانشستر سيتي | سندرلاند | عصام الشوالي | 4 | نعم
الدوري الإنجليزي | 16:00 | بورنموث | ليفربول | حفيظ دراجي | 2 | لا
الدوري الإنجليزي | 16:00 | ليدز يونايتد | كريستال بالاس | نوفل باشي | 5 | لا
الدوري الإنجليزي | 18:30 | فولهام | مانشستر يونايتد | أحمد البلوشي | 2 4K | نعم
الدوري الإسباني | 15:00 | خيتافي | مالقا | منتصر الأزهري | 9 | لا
الدوري الإسباني | 17:15 | أتلتيكو مدريد | ريال مدريد | علي سعيد الكعبي | 1 | نعم
الدوري الإسباني | 19:30 | ديبورتيفو لاكورونيا | ريال بيتيس | عامر الخوذيري | 4 | لا
الدوري الإسباني | 19:30 | فياريال | ليفانتي | محمد بركات | 3 | لا
الدوري الإسباني | 22:00 | فالنسيا | ريال سوسيداد | علي محمد علي | 2 | لا
الدوري الفرنسي | 16:00 | أوكسير | بريست | أحمد فؤاد | 6 | لا
الدوري الفرنسي | 18:15 | نيس | ليل | جواد بده | 5 | لا
الدوري الفرنسي | 21:45 | مارسيليا | باريس سان جيرمان | حسن العيدروس | 1 | نعم
الدوري التركي | 17:00 | فنربخشة | أيوب سبور | سمير اليعقوبي | 3 | لا
الدوري التركي | 20:00 | أوميد سبور | بشكتاش | أحمد عبده | 6 | لا`;
