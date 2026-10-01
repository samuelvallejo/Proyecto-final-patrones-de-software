/* Browser interoperability only. Navigation, forms, rendering and API workflows live in Java. */
(() => {
 'use strict';
 const state={api:'',token:'',config:{},local:null,remote:null,ws:null,peers:new Map(),pending:new Map(),host:false,stream:'',started:0,segments:[],recorder:null,interval:null,callback:()=>{},stopping:false,capture:null,audio:null,audioTimer:null,speech:null,urls:[],lastPeak:0};
 async function request(path,body){
  const options={method:'POST',headers:{Authorization:`Bearer ${state.token}`}};
  if(body instanceof FormData)options.body=body;else{options.headers['Content-Type']='application/json';options.body=JSON.stringify(body);}
  const response=await fetch(`${state.api}/api${path}`,options);
  if(!response.ok){let message='No se pudo completar la operación';try{message=(await response.json()).error||message;}catch{}throw new Error(message);}
  return response.json();
 }
 function emit(event){state.callback(event);}
 function attach(){const el=document.getElementById('live-video');const source=state.host?state.local:state.remote;if(el&&source){el.srcObject=source;el.muted=state.host;el.play().catch(()=>{});const placeholder=document.getElementById('video-placeholder');if(placeholder)placeholder.style.display='none';}}
 function send(data){if(state.ws?.readyState===WebSocket.OPEN)state.ws.send(JSON.stringify(data));}
 function closePeers(){for(const peer of state.peers.values())peer.close();state.peers.clear();state.pending.clear();}
 function recorderType(){return ['video/webm;codecs=vp8,opus','video/webm','video/mp4'].find(type=>window.MediaRecorder?.isTypeSupported(type));}
 async function prepare(screen){
  if(!navigator.mediaDevices)throw new Error('La cámara requiere HTTPS o localhost en un navegador compatible.');
  if(!recorderType())throw new Error('Este navegador no permite grabar clips. Prueba una versión reciente de Chrome, Edge o Safari.');
  stop();state.stopping=false;
  state.local=screen?await navigator.mediaDevices.getDisplayMedia({video:{frameRate:24},audio:true}):await navigator.mediaDevices.getUserMedia({video:{width:{ideal:1280},height:{ideal:720},frameRate:{ideal:24}},audio:true});
  state.local.getVideoTracks()[0].addEventListener('ended',()=>{if(state.host)stop();});state.host=true;attach();
 }
 function createPeer(id){
  const peer=new RTCPeerConnection({iceServers:state.config.iceServers||[{urls:'stun:stun.l.google.com:19302'}]});state.peers.set(id,peer);
  peer.onicecandidate=e=>{if(e.candidate)send({type:'signal',to:id,payload:{candidate:e.candidate}});};
  peer.ontrack=e=>{state.remote=e.streams[0]||new MediaStream([e.track]);attach();};
  peer.onconnectionstatechange=()=>{if(peer.connectionState==='failed')emit({type:'error',message:'No se pudo establecer el video. Esta red puede necesitar un servidor TURN configurado por el creador.'});};
  if(state.host&&state.local)for(const track of state.local.getTracks())peer.addTrack(track,state.local);
  return peer;
 }
 async function signal(event){
  const peer=state.peers.get(event.from)||createPeer(event.from);const payload=event.payload;
  if(payload.description){
   await peer.setRemoteDescription(payload.description);
   for(const candidate of state.pending.get(event.from)||[])await peer.addIceCandidate(candidate);state.pending.delete(event.from);
   if(payload.description.type==='offer'){const answer=await peer.createAnswer();await peer.setLocalDescription(answer);send({type:'signal',to:event.from,payload:{description:peer.localDescription}});}
  }else if(payload.candidate){
   if(peer.remoteDescription)await peer.addIceCandidate(payload.candidate);else{if(!state.pending.has(event.from))state.pending.set(event.from,[]);state.pending.get(event.from).push(payload.candidate);}
  }
 }
 function connect(stream,host,callback){
  if(state.ws){state.ws.onclose=null;state.ws.close();}closePeers();state.stream=stream;state.host=host;state.callback=callback;state.stopping=false;
  if(host&&!state.local){emit({type:'error',message:'Prepara primero la cámara.'});return;}
  let joined=false;const ws=new WebSocket(`${state.api.replace(/^http/,'ws')}/ws`);state.ws=ws;
  ws.onopen=()=>send({type:'join',streamId:stream,token:state.token,host});
  ws.onmessage=async raw=>{try{const event=JSON.parse(raw.data);
   if(event.type==='joined'){joined=true;if(host){state.started=Date.now();startRecording();startAudio();}}
   if(event.type==='error'&&!joined)ws.close();
   if(event.type==='viewer-joined'&&host){const peer=createPeer(event.peerId);const offer=await peer.createOffer();await peer.setLocalDescription(offer);send({type:'signal',to:event.peerId,payload:{description:peer.localDescription}});}
   if(event.type==='viewer-left'){state.peers.get(event.peerId)?.close();state.peers.delete(event.peerId);}
   if(event.type==='signal')await signal(event);
   if(event.type==='capture'&&host)capture(event.highlightId);
   if(event.type==='subtitle'){const el=document.getElementById('live-caption');if(el){el.textContent=event.text;setTimeout(()=>{if(el.textContent===event.text)el.textContent='';},7000);}}
   if(event.type==='ended')stop();emit(event);
  }catch(error){emit({type:'error',message:`No se pudo procesar el evento de video: ${error.message}`});}};
  ws.onerror=()=>emit({type:'error',message:'No se pudo conectar al directo. Revisa el servidor y la conexión.'});
  ws.onclose=()=>{if(state.stopping)return;const wasHost=state.host;stop();emit({type:'ended',message:wasHost?'La conexión se perdió y el directo terminó. Puedes comenzar uno nuevo desde el estudio.':'Se perdió la conexión al directo. Vuelve a entrar.'});};
 }
 function startRecording(){
  if(!state.local||state.stopping)return;let chunks=[];const start=Math.floor((Date.now()-state.started)/1000);const recorder=new MediaRecorder(state.local,{mimeType:recorderType(),videoBitsPerSecond:1200000,audioBitsPerSecond:64000});state.recorder=recorder;state.recorderStarted=Date.now();
  recorder.ondataavailable=e=>{if(e.data.size)chunks.push(e.data);};
  recorder.onstop=()=>{
   const end=Math.max(start+1,Math.ceil((Date.now()-state.started)/1000));const blob=new Blob(chunks,{type:recorder.mimeType});
   if(blob.size){state.segments.push({blob,start,end});if(state.segments.length>4)state.segments.shift();}
   if(state.capture){const highlight=state.capture;state.capture=null;upload(highlight,state.segments.at(-1));}
   if(!state.stopping&&state.host)startRecording();
  };
  recorder.start();clearTimeout(state.interval);state.interval=setTimeout(()=>{if(recorder.state==='recording')recorder.stop();},15000);
 }
 function capture(highlight){
  if(state.capture){emit({type:'capture-status',message:'Ya se está capturando otro momento.',error:true});return;}
  const currentSeconds=state.recorder?(Date.now()-state.recorderStarted)/1000:0;
  if(state.recorder?.state==='recording'&&currentSeconds>=2){state.capture=highlight;clearTimeout(state.interval);state.recorder.stop();}
  else if(state.segments.length)upload(highlight,state.segments.at(-1));
  else{state.capture=highlight;emit({type:'capture-status',message:'El primer segmento está grabándose. El clip aparecerá al completar hasta 15 segundos.',error:false});}
 }
 async function upload(highlight,segment){
  if(!segment)return;emit({type:'capture-status',message:'Guardando tu momento y preparando el clip…',error:false});
  const data=new FormData();data.append('highlightId',highlight);data.append('start',segment.start);data.append('end',segment.end);data.append('file',segment.blob,segment.blob.type.includes('mp4')?'clip.mp4':'clip.webm');
  try{await request(`/streams/${state.stream}/segments`,data);emit({type:'capture-status',message:'Clip guardado. Puedes revisarlo en tu biblioteca.',error:false});}catch(e){emit({type:'capture-status',message:e.message,error:true});}
 }
 function startAudio(){
  if(!state.local?.getAudioTracks().length)return;
  try{state.audio=new (window.AudioContext||window.webkitAudioContext)();const source=state.audio.createMediaStreamSource(state.local);const analyser=state.audio.createAnalyser();analyser.fftSize=1024;source.connect(analyser);const samples=new Float32Array(analyser.fftSize);let hits=0;
   state.audioTimer=setInterval(()=>{analyser.getFloatTimeDomainData(samples);const rms=Math.sqrt(samples.reduce((s,v)=>s+v*v,0)/samples.length);hits=rms>.28?hits+1:0;if(hits>=2&&Date.now()-state.lastPeak>90000){state.lastPeak=Date.now();request(`/streams/${state.stream}/highlights`,{source:'AUDIO_PEAK',reason:'Aumento del nivel de audio detectado en el navegador (RMS > 0.28)'}).catch(()=>{});}},500);
  }catch{/* Audio peak detection is optional; manual markers and chat remain available. */}
 }
 async function captions(){
  if(!state.host)throw new Error('Los subtítulos se activan desde el estudio del creador.');
  const Recognition=window.SpeechRecognition||window.webkitSpeechRecognition;if(!Recognition)throw new Error('Este navegador no ofrece transcripción automática. Prueba Chrome o Edge.');
  if(state.speech)return;
  const speech=new Recognition();state.speech=speech;speech.lang='es-CO';speech.continuous=true;speech.interimResults=false;
  speech.onresult=event=>{for(let i=event.resultIndex;i<event.results.length;i++)if(event.results[i].isFinal){const end=Math.max(1,(Date.now()-state.started)/1000),text=event.results[i][0].transcript;request(`/streams/${state.stream}/subtitles`,{start:Math.max(0,end-5),end,text}).catch(e=>emit({type:'error',message:e.message}));}};
  speech.onend=()=>{if(state.host&&!state.stopping&&state.speech===speech){try{speech.start();}catch{}}};
  speech.onerror=e=>{if(e.error==='not-allowed'||e.error==='service-not-allowed'){state.speech=null;emit({type:'error',message:'No se pudo activar la transcripción. Revisa el permiso del micrófono.'});}};speech.start();
 }
 async function playback(asset,id,download){
  const res=await fetch(`${state.api}/api/media/${asset}`,{headers:state.token?{Authorization:`Bearer ${state.token}`}:{}});if(!res.ok)throw new Error('No se pudo cargar el clip o todavía no es público.');const url=URL.createObjectURL(await res.blob());state.urls.push(url);
  if(download){const link=document.createElement('a');link.href=url;link.download=`streamguard-${asset}.webm`;document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);}else{const video=document.getElementById(id);if(video){video.src=url;video.play().catch(()=>{});}}
 }
 function stop(){
  state.stopping=true;state.capture=null;clearTimeout(state.interval);clearInterval(state.audioTimer);if(state.recorder?.state==='recording')state.recorder.stop();state.recorder=null;
  const ws=state.ws;state.ws=null;if(ws){ws.onclose=null;ws.close();}closePeers();
  if(state.speech){const s=state.speech;state.speech=null;s.onend=null;s.stop();}if(state.audio){state.audio.close().catch(()=>{});state.audio=null;}
  if(state.local){for(const track of state.local.getTracks())track.stop();state.local=null;}state.remote=null;state.host=false;state.segments=[];
  for(const url of state.urls)URL.revokeObjectURL(url);state.urls=[];
 }
 window.StreamMedia={configure:(api,token,config)=>{state.api=api.replace(/\/$/,'');state.token=token;state.config=config;},prepare,connect,attach,stop,captions,playback};
 window.addEventListener('beforeunload',stop);
})();
