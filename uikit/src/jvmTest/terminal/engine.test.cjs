/* Run with node --test --test-force-exit. PLAYWRIGHT_MODULE can point to an installed Playwright module. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');

const assets = path.resolve(__dirname, '../../commonMain/composeResources/files/terminal');
const read = name => fs.readFileSync(path.join(assets, name), 'utf8');

test('bundled xterm handles real terminal output, input, modes, resize and disposal', async () => {
  const browser = await chromium.launch({ headless: true, ...(process.env.TERMINAL_BROWSER_CHANNEL ? { channel: process.env.TERMINAL_BROWSER_CHANNEL } : {}) });
  try {
    const page = await browser.newPage({ viewport: { width: 900, height: 450 } });
    const requests = [];
    page.on('request', request => requests.push(request.url()));
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const font = fs.readFileSync(path.resolve(assets, '../../font/departure_mono.otf')).toString('base64');
    await page.setContent(`<!doctype html><html><head>
      <meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; font-src data:; connect-src 'none'">
      <style>@font-face{font-family:'Departure Mono';src:url(data:font/otf;base64,${font}) format('opentype')}${read('xterm.css')}html,body,#terminal{width:100%;height:100%;margin:0;overflow:hidden}.xterm{height:100%}</style>
      </head><body><div id="terminal"></div>
      <script type="application/json" id="terminal-options">{"fontFamily":"Departure Mono, monospace","fontSize":14,"scrollback":5000}</script>
      <script>${read('xterm.js')}</script><script>${read('addon-fit.js')}</script>
      <script>window.events=[];window.addEventListener('shadcn-terminal-event', e => window.events.push(e.detail));
      window.OriginalTerminal=window.Terminal;window.Terminal=class extends OriginalTerminal { constructor(o){super(o);window.engine=this;} };</script>
      <script>${read('terminal.js')}</script></body></html>`);
    await page.waitForFunction(() => window.events.some(e => e.type === 'ready'));
    assert.equal(await page.evaluate(async () => (await document.fonts.load("14px 'Departure Mono'")).length), 1, 'bundled OTF loads offline');
    let nextId = 0;
    async function write(data) {
      const id = nextId++;
      await page.evaluate(command => shadcnTerminal.dispatch(command), { type: 'write', data, id });
      await page.waitForFunction(id => events.some(e => e.type === 'ack' && e.id === id), id);
    }
    const firstLine = () => page.evaluate(() => engine.buffer.active.getLine(0).translateToString(true));

    await write('hello\r\x1b[2K\x1b[31mworld\x1b[0m');
    assert.equal(await firstLine(), 'world', 'cursor movement, erase-line and ANSI color parse');
    assert.ok(await page.evaluate(() => engine.buffer.active.getLine(0).getCell(0).isFgPalette()), 'ANSI foreground is applied');
    await write('\x1b[?1049hfull screen app\x1b[?1049l');
    assert.equal(await firstLine(), 'world', 'alternate-screen programs restore the original screen');
    await page.evaluate(() => {
      shadcnTerminal.dispatch({type:'theme',theme:{red:'#ab1234',brightWhite:'#fafafa'}});
      shadcnTerminal.dispatch({type:'cursorBlink',data:'false'});
    });
    assert.equal(await page.evaluate(() => engine.options.theme.red), '#ab1234', 'custom ANSI palette applies');
    assert.equal(await page.evaluate(() => engine.options.cursorBlink), false, 'reduced motion stops blinking');
    assert.equal(await firstLine(), 'world', 'theme/motion changes preserve terminal state');
    await write('\r\nUnicode: λ 漢字 😀');
    assert.ok(await page.evaluate(() => engine.buffer.active.getLine(1).translateToString(true).includes('λ 漢字 😀')));

    await page.locator('.xterm-helper-textarea').focus();
    await page.keyboard.press('Control+c');
    await page.waitForFunction(() => events.some(e => e.type === 'input' && e.data === '\x03'));
    await write('\x1b[?1h');
    await page.evaluate(() => shadcnTerminal.dispatch({type:'key',data:'Up'}));
    await page.waitForFunction(() => events.some(e => e.type === 'input' && e.data === '\x1bOA'));
    await write('\x1b[?1l\x1b[?2004h');
    await page.evaluate(() => shadcnTerminal.dispatch({type:'paste',data:'echo hello\n'}));
    await page.waitForFunction(() => events.some(e => e.type === 'input' && e.data.startsWith('\x1b[200~') && e.data.endsWith('\x1b[201~')));

    await page.evaluate(() => shadcnTerminal.dispatch({type:'resize',columns:88,rows:33}));
    await page.waitForFunction(() => events.some(e => e.type === 'resize' && e.columns === 88 && e.rows === 33));
    assert.deepEqual(await page.evaluate(() => [engine.cols, engine.rows]), [88,33]);
    await write('\x1b]2;remote shell\x07\x07');
    assert.ok(await page.evaluate(() => events.some(e => e.type === 'title' && e.data === 'remote shell')));
    assert.ok(await page.evaluate(() => events.some(e => e.type === 'bell')));
    await write("</script><script>window.attacked=true</script>");
    assert.equal(await page.evaluate(() => window.attacked), undefined, 'terminal output never executes HTML');
    assert.equal(requests.length, 0, 'the engine makes no network requests');
    assert.deepEqual(errors, []);
    if (process.env.TERMINAL_SCREENSHOT_PATH) await page.screenshot({path: process.env.TERMINAL_SCREENSHOT_PATH});
    await page.evaluate(() => shadcnTerminal.dispose());
    assert.equal(await page.locator('.xterm').count(), 0, 'dispose releases terminal DOM');
  } catch (error) {
    console.error(error);
    throw error;
  } finally {
    // Windows can leave Playwright waiting on an already-exited browser process handle.
    // The terminal's own disposal is asserted above; bound only browser-runner teardown.
    await Promise.race([browser.close(), new Promise(resolve => setTimeout(resolve, 5000).unref())]);
  }
});
