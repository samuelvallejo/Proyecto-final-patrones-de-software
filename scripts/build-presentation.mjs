// Authoring source. Requires the supplied @oai/artifact-tool runtime; the PPTX itself is editable in PowerPoint.
import localizedCopy from './localized-copy.cjs';
const copy=localizedCopy.load('presentation.es.json');
import fs from 'node:fs/promises';import path from 'node:path';import {fileURLToPath,pathToFileURL} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const modules=process.env.ARTIFACT_NODE_MODULES,skill=process.env.PRESENTATIONS_SKILL_DIR,python=process.env.ARTIFACT_PYTHON;
if(!modules||!skill||!python)throw new Error('Set ARTIFACT_NODE_MODULES, PRESENTATIONS_SKILL_DIR and ARTIFACT_PYTHON to the bundled presentation runtime.');
process.env.RUNTIME_NODE_MODULES=modules;
const {Presentation,PresentationFile}=await import(pathToFileURL(path.join(modules,'@oai/artifact-tool/dist/artifact_tool.mjs')).href);
const {resolvePresentationFont,finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const font=resolvePresentationFont({fontFamily:'Arial'});const build=path.join(root,'.local',`slides-${Date.now()}`);await fs.mkdir(build,{recursive:true});
const deck=Presentation.create({slideSize:{width:1280,height:720}});
const colors={bg:'#101415',text:'#EFF4EF',muted:'#A1B3A6',accent:'#B2F771'};
function text(slide,value,x,y,w,h,size=28,bold=false,color=colors.text){const box=slide.shapes.add({geometry:'textbox',position:{left:x,top:y,width:w,height:h},fill:'none',line:{fill:'none',width:0}});box.text=value;box.text.style={typeface:font,fontSize:size,bold,color,autoFit:'none'};return box;}
function slide(title,notes=''){const s=deck.slides.add();s.background.fill=colors.bg;text(s,title,72,58,1136,85,48,true);text(s,String(deck.slides.items.length),1170,674,65,35,16,false,colors.muted);s.speakerNotes.textFrame.setText(notes);return s;}
function paragraphs(s,items,x=72,y=180,w=1060,size=28,gap=115){items.forEach((item,i)=>text(s,item,x,y+i*gap,w,gap-20,size));}
async function image(s,name,x,y,w,h){s.images.add({blob:new Uint8Array(await fs.readFile(path.join(root,'artifacts',name))),contentType:'image/png',alt:`Actual StreamGuard screenshot: ${name}`,fit:'contain',position:{left:x,top:y,width:w,height:h}});}
function table(s,values,x,y,w,h,widths){const t=s.tables.add({rows:values.length,columns:values[0].length,left:x,top:y,width:w,height:h,values,columnWidths:widths});t.borders.assign({fill:'#34443A',width:1,style:'solid'});for(let r=0;r<values.length;r++)for(let c=0;c<values[r].length;c++){let cell=t.getCell(r,c);cell.fill=colors.bg;cell.text.style={typeface:font,fontSize:r===0?25:26,bold:r===0,color:r===0?colors.accent:colors.text};}return t;}
{
 const s=deck.slides.add();s.background.fill=colors.bg;text(s,'StreamGuard',72,205,1100,105,78,true,colors.accent);text(s,copy.text('presentationText01'),76,325,1080,70,34);text(s,copy.text('presentationText02'),76,480,1080,44,25,false,colors.muted);s.speakerNotes.textFrame.setText(copy.text('presentationText03'));
}
{
 const s=slide(copy.text('presentationText04'),copy.text('presentationText05'));paragraphs(s,[copy.text('presentationText06'),copy.text('presentationText07'),copy.text('presentationText08')],72,180,420,27,128);await image(s,'explore-desktop.png',525,165,685,460);
}
{
 const s=slide(copy.text('presentationText09'),copy.text('presentationText10'));
 table(s,[[copy.text("slideLabelText01"),copy.text('presentationText11'),copy.text("slideLabelText02")],['Frontend','TypeScript / Vite / PWA',copy.text("slideLabelText03")],['Backend','Java 21 / Spring Boot',copy.text('presentationText12')],[copy.text('presentationText13'),'PostgreSQL',copy.text("slideLabelText04")],[copy.text('presentationText14'),copy.text('presentationText15'),copy.text('presentationText16')]],72,178,1136,390,[240,350,546]);text(s,copy.text('presentationText17'),72,600,1100,45,25,false,colors.muted);
}
{
 const s=slide(copy.text('presentationText18'),copy.text('presentationText19'));text(s,'65',72,185,320,150,118,true,colors.accent);text(s,copy.text('presentationText20'),75,345,430,55,32);text(s,copy.text('presentationText21'),75,425,430,90,29,false,colors.muted);paragraphs(s,[copy.text('presentationText22'),copy.text('presentationText23'),copy.text('presentationText24'),copy.text('presentationText25')],600,185,590,28,102);
}
{
 const s=slide(copy.text('presentationText26'),copy.text('presentationText27'));paragraphs(s,[copy.text('presentationText28'),copy.text('presentationText29'),copy.text('presentationText30'),copy.text('presentationText31')],72,185,1110,30,104);text(s,copy.text('presentationText32'),72,614,1100,45,25,false,colors.accent);
}
{
 const s=slide(copy.text('presentationText33'),copy.text('presentationText34'));paragraphs(s,[copy.text('presentationText35'),copy.text('presentationText36'),copy.text('presentationText37')],72,188,520,28,122);text(s,'ModerationPolicy.Builder\n\nlevel("STRICT")\nautoHide(true)\nmuteSeconds(300)\nthresholds(0.4, 0.7)\nblockedWords(...)\nbuild()',680,180,510,420,29,false,colors.accent);
}
{
 const s=slide(copy.text('presentationText38'),copy.text('presentationText39'));text(s,copy.text('presentationText40'),72,190,550,50,31,true);text(s,'execute(context)\n\ncreateAction()\naction.apply(context)\nreturn action.status()',72,280,520,260,29,false,colors.accent);text(s,copy.text("slideLabelText05"),675,190,535,80,31,true);text(s,copy.text('presentationText41'),675,300,535,250,28);
}
{
 const s=slide(copy.text('presentationText42'),copy.text('presentationText43'));table(s,[[copy.text("slideLabelText06"),'ModerationAnalyzer','EditorialAssistant'],['OllamaToolkit',copy.text('presentationText44'),copy.text('presentationText45')],['LocalToolkit',copy.text('presentationText46'),copy.text("slideLabelText07")]],72,215,1136,290,[260,410,466]);text(s,copy.text('presentationText47'),72,560,1100,70,29,false,colors.muted);
}
{
 const s=slide(copy.text('presentationText48'),copy.text('presentationText49'));text(s,'AiGateway.generate(instruction, input, schema)',72,185,1136,70,31,true,colors.accent);paragraphs(s,[copy.text('presentationText50'),copy.text('presentationText51'),copy.text('presentationText52')],72,300,1060,29,105);
}
{
 const s=slide(copy.text("slideLabelText08"),copy.text('presentationText53'));text(s,copy.text('presentationText54'),72,193,515,60,32,true,colors.accent);text(s,'ModerationNotice\nClipNotice',72,290,515,160,34);text(s,copy.text('presentationText55'),680,193,525,75,32,true,colors.accent);text(s,'DatabaseDelivery\nRealtimeDelivery',680,305,525,160,34);text(s,copy.text('presentationText56'),72,565,1100,70,29,false,colors.muted);
}
{
 const s=slide(copy.text('presentationText57'),copy.text('presentationText58'));paragraphs(s,[copy.text('presentationText59'),copy.text('presentationText60'),copy.text('presentationText61')],72,195,400,27,125);await image(s,'clips-desktop.png',515,180,690,445);
}
{
 const s=slide(copy.text('presentationText62'),copy.text('presentationText63'));paragraphs(s,[copy.text('presentationText64'),copy.text('presentationText65'),copy.text('presentationText66'),copy.text('presentationText67')],72,180,740,28,105);await image(s,'explore-mobile.png',905,156,260,490);
}
{
 const s=slide(copy.text('presentationText68'),copy.text('presentationText69'));paragraphs(s,[copy.text('presentationText70'),copy.text("slideLabelText09"),copy.text('presentationText71'),copy.text('presentationText72')],72,180,1110,29,105);text(s,copy.text('presentationText73'),72,615,1136,45,25,false,colors.accent);
}
{
 const s=slide(copy.text('presentationText74'),copy.text('presentationText75'));text(s,copy.text("slideLabelText10"),72,190,530,60,32,true,colors.accent);text(s,copy.text('presentationText76'),72,288,565,290,28);text(s,copy.text('presentationText77'),695,190,510,60,32,true,colors.accent);text(s,copy.text('presentationText78'),695,288,510,290,28);
}
const candidate=path.join(build,'candidate.pptx');await(await PresentationFile.exportPptx(deck)).save(candidate);
const final=path.join(root,process.env.PRESENTATION_OUTPUT||'artifacts/StreamGuard-typescript-presentation.pptx');
await finalizePresentation({workspaceDir:root,explicitTotalSlideCount:14,requiredNativeTableOwnerSlides:[3,8],candidatePath:candidate,finalPath:final,pythonExecutable:python,integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-heading-fit','--require-native-table-slide','3','--require-native-table-slide','8'],fontPolicy:{basis:'design',families:[font]},verifyArtifactToolImport:true,receiptPath:path.join(build,'validation.json')});
for(let i=0;i<deck.slides.items.length;i++){const preview=await deck.export({slide:deck.slides.items[i],format:'png',scale:1});await fs.writeFile(path.join(build,`slide-${i+1}.png`),new Uint8Array(await preview.arrayBuffer()));}
console.log(`Validated presentation: ${final}`);
