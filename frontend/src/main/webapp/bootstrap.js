/* Load Spanish resources before either Java UI or browser media adapters start. */
(async () => {
  'use strict';
  async function loadScript(source) {
    await new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = source;
      script.onload = resolve;
      script.onerror = () => reject(new Error(`Could not load application script: ${source}`));
      document.head.append(script);
    });
  }
  try {
    const response = await fetch('locales/es.json');
    if (!response.ok) throw new Error(`Locale request failed: ${response.status}`);
    const copy = await response.json();
    window.StreamGuardLocale = Object.freeze(copy);
    document.title = copy.pageTitle;
    document.querySelector('meta[name="description"]').content = copy.pageDescription;
    document.getElementById('boot-status').textContent = copy.bootPreparing;
    await loadScript('media.js');
    await loadScript('streamguard/streamguard.nocache.js');
  } catch (error) {
    console.error('Application startup failed', error);
    const status = document.getElementById('boot-status');
    if (status) status.textContent = window.StreamGuardLocale?.bootFailed || '⚠';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.textContent = '↻';
    retry.setAttribute('aria-label', window.StreamGuardLocale?.bootRetry || '↻');
    retry.addEventListener('click', () => window.location.reload());
    document.querySelector('.boot-screen')?.append(retry);
  }
})();
