import {execFileSync} from 'node:child_process';
import {existsSync,mkdirSync,readdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const api=process.env.PUBLIC_API_URL||'http://localhost:8080';
const parsed=new URL(api);
if(!['http:','https:'].includes(parsed.protocol)||parsed.username||parsed.password||parsed.search||parsed.hash||parsed.pathname!=='/')throw new Error('PUBLIC_API_URL debe ser el origen del backend, sin ruta /api ni credenciales.');
if(process.env.VERCEL && parsed.protocol!=='https:')throw new Error('El backend de producción debe usar HTTPS.');
const env={...process.env};
try{execFileSync('java',['-version'],{stdio:'pipe'});}catch{
 if(process.platform!=='linux')throw new Error('Instala JDK 21 y agrega java al PATH.');
 const tools=path.join(root,'.tools');mkdirSync(tools,{recursive:true});
 const javaDir=path.join(tools,'jdk');mkdirSync(javaDir,{recursive:true});
 if(!readdirSync(javaDir).length){
  const archive=path.join(tools,'jdk.tar.gz');
  execFileSync('curl',['-fL','--retry','2','https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse','-o',archive],{stdio:'inherit'});
  execFileSync('tar',['-xzf',archive,'-C',javaDir],{stdio:'inherit'});
 }
 env.JAVA_HOME=path.join(javaDir,readdirSync(javaDir).find(name=>name.startsWith('jdk')));env.PATH=path.join(env.JAVA_HOME,'bin')+path.delimiter+env.PATH;
}
if(process.platform==='win32')execFileSync('cmd.exe',['/d','/c','mvnw.cmd','-B','-ntp','-pl','frontend','package'],{cwd:root,env,stdio:'inherit'});
else execFileSync('sh',['mvnw','-B','-ntp','-pl','frontend','package'],{cwd:root,env,stdio:'inherit'});
writeFileSync(path.join(root,'frontend/dist/config.js'),`window.STREAMGUARD_API = ${JSON.stringify(parsed.origin)};\n`);
// Static generated build; GWT does not need a Java server on Vercel.
console.log(`Frontend Java compilado. API pública: ${parsed.origin}`);
