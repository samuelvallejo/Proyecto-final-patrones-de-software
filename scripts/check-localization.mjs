/* Ensure every referenced translation exists before packaging Java or TypeScript assets. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
function read(relative){return fs.readFileSync(path.join(root,relative),'utf8');}
function catalog(relative){const values=JSON.parse(read(relative));for(const [key,value] of Object.entries(values)){if(!/^[a-z][A-Za-z0-9]*$/.test(key)||typeof value!=='string')throw new Error(`Invalid translation entry: ${relative}:${key}`);}return values;}
function files(relative){const directory=path.join(root,relative);return fs.readdirSync(directory,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?files(path.join(relative,entry.name)):[path.join(relative,entry.name)]);}
const ui=catalog('frontend/public/locales/es.json');
const server=catalog('backend/src/main/resources/locales/es.json');
const fixtures=catalog('backend/src/test/resources/fixtures/es.json');
let references=0;
function check(source,pattern,values,filename){for(const match of source.matchAll(pattern)){if(typeof values[match[1]]!=='string')throw new Error(`Missing translation ${match[1]} in ${filename}`);references++;}}
for(const filename of files('frontend/src').filter(name=>name.endsWith('.ts')))check(read(filename),/\bt\(['"]([A-Za-z0-9]+)['"]\)/g,ui,filename);
for(const filename of files('backend/src/main/java'))check(read(filename),/Messages\.text\("([A-Za-z0-9]+)"\)/g,server,filename);
for(const filename of files('backend/src/test/java'))check(read(filename),/TestFixtures\.text\("([A-Za-z0-9]+)"\)/g,fixtures,filename);
for(const [source,resource] of [['browser-test.cjs','browser.es.json'],['build-presentation.mjs','presentation.es.json']])check(read('scripts/'+source),/copy\.text\(['"]([A-Za-z0-9]+)['"]\)/g,catalog('scripts/locales/'+resource),source);
check(read('scripts/document-schema.py'),/copy\["([A-Za-z0-9]+)"\]/g,catalog('scripts/locales/schema.es.json'),'document-schema.py');
for(const filename of [...files('backend/src/main/java'),...files('backend/src/test/java'),...files('frontend/src').filter(name=>name.endsWith('.ts')),...files('scripts').filter(name=>/\.(mjs|cjs|py|ps1)$/.test(name))])if(/[\u00c1\u00c9\u00cd\u00d3\u00da\u00d1\u00e1\u00e9\u00ed\u00f3\u00fa\u00f1\u00bf\u00a1]/.test(read(filename)))throw new Error(`Localized text must be moved out of executable source: ${filename}`);
console.log(`Localization check passed: ${references} references, Spanish UI and English source.`);
