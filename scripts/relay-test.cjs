/* Real browser playback with synthetic devices and deliberately disabled WebRTC. */
const {chromium} = require('playwright');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const ui = JSON.parse(fs.readFileSync(path.join(__dirname, '../frontend/public/locales/es.json'), 'utf8'));
const serverCopy = JSON.parse(fs.readFileSync(path.join(__dirname, '../backend/src/main/resources/locales/es.json'), 'utf8'));
const screenCapture = process.env.TEST_CAPTURE === 'screen';
const web = process.env.TEST_WEB_URL || 'http://localhost:5173';
const api = process.env.TEST_API_URL || 'http://localhost:8080';
const output = path.join(__dirname, '../artifacts/relay-test-results.json');

(async () => {
  const browser = await chromium.launch({headless: true, channel: process.env.TEST_BROWSER_CHANNEL || 'chrome', args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream']});
  const errors = [];
  let streamId = '', token = '';
  async function call(route, body) {
    const response = await fetch(`${api}/api${route}`, {method: 'POST', headers: {'Content-Type': 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {})}, body: JSON.stringify(body)});
    const value = await response.json(); if (!response.ok) throw new Error(`Test API ${route}: ${response.status}`); return value;
  }
  try {
    const suffix = Date.now().toString(36), username = `relay_${suffix}`;
    token = (await call('/auth/register', {username, email: `${username}@example.com`, password: 'RelayTest123!', aiConsent: true})).token;
    await call('/channels', {name: `Relay QA ${suffix}`, slug: username.replaceAll('_', '-'), description: 'Synthetic relay playback test'});
    const hostContext = await browser.newContext({permissions: ['camera', 'microphone'], viewport: {width: 1400, height: 950}});
    await hostContext.addInitScript(token => {
      sessionStorage.setItem('streamguard.token', token);
      const Socket = window.WebSocket; window.testSockets = [];
      window.WebSocket = class extends Socket {constructor(...args) {super(...args); window.testSockets.push(this);}};
    }, token);
    const host = await hostContext.newPage(); host.on('pageerror', error => errors.push(error.message));
    if (screenCapture) await host.addInitScript(() => {
      const nativeCapture = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
      navigator.mediaDevices.getDisplayMedia = async () => {
        const canvas = document.createElement('canvas'); canvas.width = 640; canvas.height = 360;
        const painter = canvas.getContext('2d'); let tick = 0;
        window.setInterval(() => {
          painter.fillStyle = `hsl(${tick++ % 360} 60% 35%)`; painter.fillRect(0, 0, 640, 360);
          painter.fillStyle = '#ffffff'; painter.font = '40px sans-serif'; painter.fillText(String(tick), 50, 100);
        }, 40);
        const display = canvas.captureStream(24);
        const systemAudio = await nativeCapture({audio: true});
        display.addTrack(systemAudio.getAudioTracks()[0]);
        return display;
      };
    });
    await host.goto(web); await host.getByRole('button', {name: ui.myStudio, exact: true}).click();
    const title = `Relay QA ${suffix}`;
    await host.getByLabel(ui.uiStudioText93, {exact: true}).fill(title);
    if (screenCapture) await host.getByRole('checkbox', {name: ui.uiStudioText92, exact: true}).check();
    const started = host.waitForResponse(response => response.request().method() === 'POST' && response.url().endsWith('/api/streams'));
    await host.getByRole('button', {name: ui.uiStudioText96, exact: true}).click();
    streamId = (await (await started).json()).id;
    await host.getByRole('button', {name: ui.uiStudioText87, exact: true}).waitFor();
    assert.equal(await host.locator('#live-video').evaluate(video => video.srcObject.getAudioTracks().length), 1);
    await host.getByRole('button', {name: ui.uiStudioText104, exact: true}).click();
    assert.equal(await host.evaluate(() => window.StreamMedia.microphoneEnabled()), false);
    await host.getByRole('button', {name: ui.uiStudioText105, exact: true}).click();
    const guestContext = await browser.newContext({viewport: {width: 1250, height: 850}});
    await guestContext.addInitScript(() => {
      const Peer = window.RTCPeerConnection; window.testPeers = [];
      window.RTCPeerConnection = class extends Peer {constructor(config) {super({...config, iceTransportPolicy: 'relay', iceServers: []}); window.testPeers.push(this);}};
    });
    const guest = await guestContext.newPage(); guest.on('pageerror', error => errors.push(error.message));
    await guest.goto(web);
    await guest.locator('.stream-card').filter({hasText: title}).getByRole('button', {name: ui.uiExploreText32, exact: true}).click();
    await guest.waitForFunction(() => {
      const video = document.querySelector('#live-video');
      return video?.dataset.transport === 'relay' && video.readyState >= 2 && video.videoWidth > 0 && video.getVideoPlaybackQuality().totalVideoFrames > 10;
    }, null, {timeout: 35000});
    const initial = await guest.locator('#live-video').evaluate(video => ({time: video.currentTime, frames: video.getVideoPlaybackQuality().totalVideoFrames, width: video.videoWidth, muted: video.muted}));
    await guest.waitForFunction(time => document.querySelector('#live-video')?.currentTime > time + 7, initial.time, {timeout: 20000});
    assert.equal(initial.muted, true);
    await guest.getByRole('button', {name: ui.mediaEnableSound, exact: true}).click();
    assert.equal(await guest.locator('#live-video').evaluate(video => video.muted), false);
    await guest.getByRole('button', {name: ui.mediaDisableSound, exact: true}).click();
    await guest.screenshot({path: path.join(__dirname, '../artifacts/relay-playback.png'), fullPage: true});
    const beforeReconnect = await guest.locator('#live-video').evaluate(video => video.currentTime);
    await host.evaluate(() => window.testSockets.find(socket => socket.url.endsWith('/ws/media') && socket.readyState === WebSocket.OPEN)?.close());
    await guest.waitForFunction(time => document.querySelector('#live-video')?.currentTime > time + 5, beforeReconnect, {timeout: 25000});
    assert(await host.evaluate(() => document.querySelector('#live-video').srcObject.getVideoTracks()[0].readyState === 'live'));
    assert.equal(await guest.evaluate(() => window.testPeers.some(peer => peer.connectionState === 'connected')), false);
    const final = await guest.locator('#live-video').evaluate(video => ({time: video.currentTime, frames: video.getVideoPlaybackQuality().totalVideoFrames}));
    const late = await guestContext.newPage(); late.on('pageerror', error => errors.push(error.message));
    await late.goto(web);
    await late.locator('.stream-card').filter({hasText: title}).getByRole('button', {name: ui.uiExploreText32, exact: true}).click();
    await late.waitForFunction(() => {
      const video = document.querySelector('#live-video');
      return video?.dataset.transport === 'relay' && video.videoWidth > 0 && video.getVideoPlaybackQuality().totalVideoFrames > 10;
    }, null, {timeout: 35000});
    await host.getByRole('button', {name: ui.uiStudioText87, exact: true}).click(); streamId = '';
    await guest.getByRole('status').filter({hasText: serverCopy.clipServiceHighlightText01}).waitFor();
    assert.deepEqual(errors, []);
    const result = {passed: true, capture: screenCapture ? 'synthetic-screen' : 'synthetic-camera', webUrl: web, apiUrl: api, initial, final, checks: ['Guest receives moving video with WebRTC blocked', 'Sequential media fragments continue beyond seven seconds', 'Autoplay starts muted and sound can be enabled', 'Publisher relay reconnects while capture remains live', 'Late guest receives playable video', 'Stream termination reaches guest'], errors};
    fs.writeFileSync(output, JSON.stringify(result, null, 2)); console.log(JSON.stringify(result, null, 2));
  } finally {
    if (streamId) await call(`/streams/${streamId}/end`, {}).catch(() => {});
    if (token) await call('/auth/logout', {}).catch(() => {});
    await browser.close();
  }
})().catch(error => {console.error(error); process.exitCode = 1;});
