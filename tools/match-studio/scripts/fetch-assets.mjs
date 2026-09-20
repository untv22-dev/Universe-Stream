import fs from 'node:fs/promises';
const tree=JSON.parse(await fs.readFile('/workspace/scratch/7c182ae544fc/logo-tree.json','utf8'));
const paths=tree.tree.filter(x=>/^logos\/(England|Spain|France|Türkiye).*\.png$/.test(x.path));
await fs.mkdir('dist/assets/clubs',{recursive:true});
const records=[]; let cursor=0;
await Promise.all(Array.from({length:6},async()=>{while(cursor<paths.length){const item=paths[cursor++];const name=item.path.split('/').at(-1).replace('.png','');const file='assets/clubs/'+name.replace(/[^a-zA-Z0-9]/g,'_')+'.png';const url='https://raw.githubusercontent.com/luukhopman/football-logos/master/'+item.path.split('/').map(encodeURIComponent).join('/');try{const r=await fetch(url);if(!r.ok)throw Error(r.status);await fs.writeFile('dist/'+file,Buffer.from(await r.arrayBuffer()));records.push({name,file,source:url});}catch(e){console.error(name,String(e));}}}));
await fs.writeFile('dist/assets/clubs.json',JSON.stringify(records,null,2));console.log('Downloaded',records.length,'club crests');
