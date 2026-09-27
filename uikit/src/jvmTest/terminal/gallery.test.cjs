/* Integration test of the assembled Wasm demo, including the real iframe host. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');

const repo = path.resolve(__dirname, '../../../..');
const dist = path.resolve(process.env.WASM_DIST || path.join(repo, 'demoApp/webApp/build/dist/wasmJs/developmentExecutable'));
const screenshots = path.resolve(process.env.WASM_SCREENSHOT_DIR || path.join(repo, 'build/reports/wasm-gallery'));
const mime = { '.html': 'text/html', '.js': 'text/javascript', '.wasm': 'application/wasm', '.css': 'text/css', '.json': 'application/json', '.otf': 'font/otf', '.png': 'image/png', '.svg': 'image/svg+xml' };

test('assembled Wasm galleries and Compose-to-xterm bridge work at desktop and phone widths', { timeout: 180000 }, async () => {
  assert.ok(fs.existsSync(path.join(dist, 'index.html')), `Build the Wasm distribution first: ${dist}`);
  fs.mkdirSync(screenshots, { recursive: true });
  const server = http.createServer((request, response) => {
    const name = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const file = path.resolve(dist, '.' + (name === '/' ? '/index.html' : name));
    if (!file.startsWith(dist + path.sep)) { response.writeHead(403).end(); return; }
    fs.readFile(file, (error, data) => {
      if (error) { response.writeHead(404).end(); return; }
      response.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' }).end(data);
    });
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  // Full Chromium uses the normal compositor. The separate headless-shell
  // overpaints Canvas/iframe interop even when both elements have correct bounds.
  const browser = await chromium.launch({ headless: true, channel: 'chromium' });
  let page;
  try {
    page = await browser.newPage({ viewport: { width: 1280, height: 1000 }, reducedMotion: 'reduce' });
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
    page.on('response', response => { if (response.status() >= 400) errors.push(`${response.status()} ${response.url()}`); });
    await page.addInitScript(() => {
      // Restore a public demo route, without requiring an offscreen sidebar item.
      if (window === window.top) localStorage.setItem('.demoapp_session.json', JSON.stringify({ isDark: false, lastScreen: JSON.stringify({ type: 'com.github.jershell.shadcn.ui.navigation.Component', id: 'Metal button' }) }));
      // Observe the real bridge without replacing its implementation.
      window.terminalCommands = [];
      window.addEventListener('message', event => {
        if (typeof event.data === 'string') {
          try { window.terminalCommands.push(JSON.parse(event.data)); } catch (_) {}
        }
      });
    });
    await page.goto(`http://127.0.0.1:${server.address().port}/`, { waitUntil: 'networkidle', timeout: 90000 });
    await page.keyboard.press('Tab'); // Activate Compose's accessibility surface.
    const button = name => page.getByRole('button', { name, exact: true });
    // Compose's semantic nodes sit behind its Canvas. Activate their real
    // accessibility click handlers; terminal typing uses native keyboard input.
    const activate = name => button(name).dispatchEvent('click');
    async function navigate(name) {
      await activate(name);
      await page.waitForTimeout(400);
    }
    await button('Upgrade to Pro').waitFor();
    console.log('Metal gallery loaded');
    await button('Upgrade to Pro').click({ force: true });
    await page.getByText('Activated 1 times', { exact: true }).waitFor();
    await activate('Gold');

    async function capture(name) {
      console.log(`Capture ${name}`);
      for (const width of [1280, 390]) {
        await page.setViewportSize({ width, height: 1000 });
        await page.waitForTimeout(350);
        if (width === 390) {
          await button('Open navigation').waitFor();
          assert.equal(await button('Overview').count(), 0, 'phone navigation does not squeeze the gallery');
          if (name === 'metal') {
            await activate('Open navigation');
            await button('Overview').waitFor();
            await page.keyboard.press('Escape');
            await button('Overview').waitFor({ state: 'hidden' });
            await activate('Open navigation');
            await button('Overview').waitFor();
            await activate('Metal button');
            await button('Overview').waitFor({ state: 'hidden' });
          }
        }
        await page.screenshot({ path: path.join(screenshots, `${name}-${width}.png`) });
      }
      await page.setViewportSize({ width: 1280, height: 1000 });
      await page.waitForTimeout(400);
    }
    await capture('metal');

    await navigate('AI rendering');
    await button('Increment').waitFor();
    await activate('Increment');
    await page.getByText(/Count: 1/).waitFor();
    assert.equal(await page.getByRole('checkbox').count(), 2, 'GFM tasks have one checkbox per item');
    await capture('rendering');

    await navigate('Agent status');
    await page.getByText(/Reasoning/).first().waitFor();
    await capture('status');

    await navigate('Agent chat');
    await button('Chatbot').waitFor();
    await capture('chat');

    await navigate('Editor themes');
    await page.getByText(/Departure Mono/).first().waitFor();
    await capture('themes');

    await navigate('Terminal');
    await activate('Open terminal');
    const frame = page.frameLocator('iframe[title="Terminal"]');
    await frame.locator('.xterm-helper-textarea').waitFor();
    const terminalFrame = page.frames().find(value => value !== page.mainFrame());
    await page.waitForFunction(() => terminalCommands.some(event => event.type === 'ready'));
    await terminalFrame.waitForFunction(() => terminalCommands.some(command => command.type === 'write' && command.data.includes('Compose terminal')));
    await frame.locator('.xterm-helper-textarea').focus();
    await page.keyboard.type('echo-test');
    await terminalFrame.waitForFunction(() => terminalCommands.filter(command => command.type === 'write').map(command => command.data).join('').includes('echo-test'));
    await activate('ANSI sample');
    await page.waitForFunction(() => terminalCommands.some(event => event.type === 'bell'));
    const controlPixels = (await button('Clear').screenshot()).toString('base64');
    const visibleInk = await page.evaluate(async base64 => {
      const image = new Image();
      image.src = `data:image/png;base64,${base64}`;
      await image.decode();
      const canvas = document.createElement('canvas');
      canvas.width = image.width; canvas.height = image.height;
      const context = canvas.getContext('2d');
      context.drawImage(image, 0, 0);
      const pixels = context.getImageData(0, 0, image.width, image.height).data;
      let count = 0;
      for (let i = 0; i < pixels.length; i += 4) {
        if (pixels[i] < 170 && pixels[i + 1] < 170 && pixels[i + 2] < 170 && pixels[i + 3] > 0) count++;
      }
      return count;
    }, controlPixels);
    assert.ok(visibleInk > 10, 'the HTML terminal does not visually cover the Compose controls below it');
    await page.setViewportSize({ width: 390, height: 1000 });
    await page.waitForTimeout(350);
    await activate('Open navigation');
    await button('Overview').waitFor();
    assert.equal(await page.locator('iframe[title="Terminal"]').isVisible(), false, 'modal content covers the native HTML surface');
    assert.equal(await page.locator('iframe[title="Terminal"]').getAttribute('inert'), '');
    assert.equal(await button('Clear').count(), 0, 'background controls are absent from the modal accessibility tree');
    await terminalFrame.evaluate(() => shadcnTerminal.dispatch({ type: 'input', data: 'background-bridge' }));
    await terminalFrame.waitForFunction(() => terminalCommands.some(command => command.type === 'write' && command.data.includes('background-bridge')));
    await page.keyboard.press('Escape');
    await button('Open navigation').waitFor();
    assert.equal(await page.locator('iframe[title="Terminal"]').isVisible(), true);
    assert.equal(await page.locator('iframe[title="Terminal"]').getAttribute('inert'), null);
    assert.ok(page.frames().includes(terminalFrame), 'modal dismissal preserves the existing terminal frame');
    await capture('terminal');
    const dimensions = await page.evaluate(() => terminalCommands.filter(event => event.type === 'resize').map(event => event.columns));
    assert.ok(new Set(dimensions).size > 1, 'Compose viewport resize reaches the iframe engine');
    await activate('Clear');
    await terminalFrame.waitForFunction(() => terminalCommands.some(command => command.type === 'clear'));
    await navigate('Metal button');
    await button('Upgrade to Pro').waitFor();
    assert.equal(await page.locator('iframe[title="Terminal"]').count(), 0, 'navigation disposes the native HTML host');

    await navigate('Dialog');
    await activate('Open dialog');
    await button('Open nested dialog').waitFor();
    assert.equal(await button('Metal button').count(), 0, 'dialog isolates background semantics');
    await activate('Open nested dialog');
    await button('Close nested dialog').waitFor();
    assert.equal(await button('Open nested dialog').count(), 0, 'nested dialog isolates its parent');
    await page.keyboard.press('Escape');
    await button('Open nested dialog').waitFor();
    await page.keyboard.press('Escape');
    await button('Open dialog').waitFor();
    await button('Metal button').waitFor();
    await navigate('Drawer');
    await activate('Open drawer');
    await button('Cancel').waitFor();
    assert.equal(await button('Metal button').count(), 0, 'bottom sheet isolates background semantics');
    await activate('Cancel');
    await button('Open drawer').waitFor();
    await button('Metal button').waitFor();
    assert.deepEqual(errors, [], 'no runtime exceptions or failed resources');
  } catch (error) {
    if (page) {
      await page.screenshot({ path: path.join(screenshots, 'failure.png') });
      console.error(await page.locator('body').ariaSnapshot());
    }
    throw error;
  } finally {
    await Promise.race([browser.close(), new Promise(resolve => setTimeout(resolve, 5000).unref())]);
    server.close();
  }
});
