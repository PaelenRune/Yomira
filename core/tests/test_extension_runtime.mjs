/**
 * Extension Runtime integration test.
 * Run after `npm run build`: `node core/tests/test_extension_runtime.mjs`
 */
import http from 'node:http';
import {
  BaseExtension,
  ExtensionRuntime,
  ExtensionHttpClient,
  ExtensionHttpError,
} from '../dist/index.js';

const manifest = {
  id: 'com.yomira.test-scraper',
  name: 'Test Scraper',
  version: '1.0.0',
  iconUrl: 'https://example.com/icon.png',
  author: 'Yomira',
  description: 'Runtime integration test extension',
  baseUrl: 'https://reader.example.com',
  supportedLanguages: ['en'],
  isNsfw: false,
  repoUrl: 'https://example.com/repo',
};

let passed = 0;
let failed = 0;

function check(name, condition, detail = '') {
  if (condition) {
    passed += 1;
    console.log(`   [PASS] ${name}${detail ? ` -> ${detail}` : ''}`);
  } else {
    failed += 1;
    console.log(`   [FAIL] ${name}${detail ? ` -> ${detail}` : ''}`);
  }
}

const source = `
const extensionManifest = ${JSON.stringify(manifest)};

function probe() {
  const result = {
    process: typeof process,
    require: typeof require,
    fs: typeof fs,
    childProcess: typeof child_process,
    buffer: typeof Buffer,
    moduleLoader: typeof module,
  };
  try {
    result.functionEscape = Function('return typeof process')();
  } catch (error) {
    result.functionEscape = 'blocked:' + error.name;
  }
  try {
    result.constructorEscape = ({}).constructor.constructor('return typeof process')();
  } catch (error) {
    result.constructorEscape = 'blocked:' + error.name;
  }
  try {
    result.fetchEscape = fetch.constructor.constructor('return typeof process')();
  } catch (error) {
    result.fetchEscape = 'blocked:' + error.name;
  }
  try {
    result.evalEscape = eval('1 + 1');
  } catch (error) {
    result.evalEscape = 'blocked:' + error.name;
  }
  return result;
}

const extension = {
  manifest: extensionManifest,
  async search(query, page) {
    const sec = probe();
    const description = Object.keys(sec).map(k => k + '=' + sec[k]).join('|');
    return [
      {
        id: 'series-1',
        canonicalTitle: 'Found: ' + query + ' p' + page,
        description,
        coverUrl: 'https://cdn.example.com/cover.jpg',
        type: 'MANGA',
        status: 'ONGOING',
        contentRating: 'SAFE',
        aliases: ['Found: ' + query + ' p' + page, 'Alt'],
        genres: ['Action'],
        tags: ['Test'],
      },
      { id: 'bad', description: 'no title' },
    ];
  },
  async getSeriesDetails(externalId) {
    if (externalId === 'blocked-net') {
      try {
        await fetch('https://not-allow-listed.example.com/steal');
        return { canonicalTitle: 'unexpected-network-success' };
      } catch (error) {
        return { canonicalTitle: 'blocked-net', description: String(error.code || error.message) };
      }
    }
    if (externalId === 'sandbox-net') {
      const response = await fetch('/api?x=1');
      const json = await response.json();
      return {
        canonicalTitle: 'sandbox-net',
        description: JSON.stringify(json),
      };
    }
    return {
      id: externalId,
      canonicalTitle: 'Details',
      description: '<b>Hello&nbsp;World</b>',
      contentRating: 'PORNOGRAPHIC_18',
      rating: 4.4,
      year: 2020,
      genres: ['Drama'],
    };
  },
  async getChapters(externalId) {
    return [
      { id: 'ch-2', chapterNumber: 2, title: 'Second' },
      { id: 'ch-1', chapterNumber: 1, title: 'First' },
      { chapterNumber: '3.5', title: 'Half' },
    ];
  },
  async getChapterPages(chapterId) {
    return [
      { imageUrl: 'https://cdn.example.com/2.jpg', index: 1 },
      { imageUrl: 'https://cdn.example.com/1.jpg', index: 0, headers: { Referer: 'https://reader.example.com/ch/1', 'X-Bad': 'a\\r\\nInjected: yes' } },
      { imageUrl: 'javascript:alert(1)' },
      { imageUrl: 'file:///etc/passwd' },
    ];
  },
};

module.exports = extension;
`;

function startServer() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, 'http://127.0.0.1');
      if (url.pathname === '/api') {
        res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(
          JSON.stringify({
            ok: true,
            path: url.pathname + url.search,
            userAgent: req.headers['user-agent'] ?? null,
            referer: req.headers['referer'] ?? null,
            cookie: req.headers['cookie'] ?? null,
          })
        );
        return;
      }
      if (url.pathname === '/cookie') {
        res.writeHead(200, { 'Set-Cookie': 'session=abc123; Path=/', 'Content-Type': 'text/plain' });
        res.end('cookie set');
        return;
      }
      if (url.pathname === '/echo') {
        res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end(
          JSON.stringify({
            userAgent: req.headers['user-agent'] ?? null,
            referer: req.headers['referer'] ?? null,
            cookie: req.headers['cookie'] ?? null,
          })
        );
        return;
      }
      if (url.pathname === '/extension.js') {
        const origin = `http://${req.headers.host}`;
        res.writeHead(200, { 'Content-Type': 'application/javascript; charset=utf-8' });
        res.end(`
          module.exports = {
            manifest: ${JSON.stringify({ ...manifest, id: 'com.yomira.downloaded', baseUrl: origin })},
            async search(query) {
              return [{ id: 'downloaded-1', canonicalTitle: 'Downloaded ' + query }];
            },
            async getSeriesDetails() { return {}; },
            async getChapters() { return []; },
            async getChapterPages() { return []; }
          };
        `);
        return;
      }
      if (url.pathname === '/cf') {
        res.writeHead(403, {
          'Content-Type': 'text/html',
          'cf-mitigated': 'challenge',
          Server: 'cloudflare',
        });
        res.end('<!doctype html><title>Just a moment...</title><div id="cf-chl"></div>');
        return;
      }
      res.writeHead(404);
      res.end('not found');
    });

    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      resolve({ server, port });
    });
  });
}

async function main() {
  console.log('=== YOMIRA EXTENSION RUNTIME TEST ===\n');

  console.log('-> 1. Loading extension into vm sandbox');
  const runtime = new ExtensionRuntime({ maxRequestsPerExtension: 50 });
  const extension = await runtime.loadExtension(source, manifest);
  const info = runtime.getExtensionInfo(manifest.id);
  check('manifest exposed and frozen', extension.manifest.id === manifest.id && Object.isFrozen(extension.manifest));
  check('vm isolation active', info?.isolation === 'vm', `isolation=${info?.isolation}`);
  check('source hash computed', typeof info?.sourceHash === 'string' && info.sourceHash.length === 8);

  console.log('\n-> 2. search() + sandbox escape probes');
  const results = await extension.search('solo leveling', 2);
  check('returns valid Series[]', results.length === 1 && results[0].canonicalTitle.includes('solo leveling'));
  check('invalid entry rejected', results[0].id === 'series-1');
  const description = results[0].description;
  check('no process in sandbox', description.includes('process=undefined'), description.slice(0, 160));
  check('no require in sandbox', description.includes('require=undefined'));
  check('no fs in sandbox', description.includes('fs=undefined'));
  check('no child_process in sandbox', description.includes('childProcess=undefined'));
  check('no Buffer in sandbox', description.includes('buffer=undefined'));
  check('Function() blocked', /functionEscape=blocked:/.test(description), description.match(/functionEscape=[^|]+/)?.[0]);
  check('({}).constructor.constructor blocked', /constructorEscape=blocked:/.test(description));
  check('fetch.constructor escape blocked', /fetchEscape=blocked:/.test(description));
  check('eval() blocked', /evalEscape=blocked:/.test(description));
  check('aliases deduplicated', results[0].aliases.length === 1 && results[0].aliases[0] === 'Alt');
  check('metadataSources backfilled', results[0].metadataSources[0]?.providerId === manifest.id);

  console.log('\n-> 3. details / chapters / pages normalization');
  const details = await extension.getSeriesDetails('abc');
  check('contentRating preserved', details.contentRating === 'PORNOGRAPHIC_18');
  check('isAdult derived', details.isAdult === true);
  check('rating clamped', details.rating === 4.4);
  const chapters = await extension.getChapters('series-1');
  check('chapters sorted ascending', chapters.length === 3 && chapters[0].chapterNumber === 1 && chapters[1].chapterNumber === 2);
  check('string chapter number parsed', chapters[2].chapterNumber === 3.5);
  check('chapter extensionId filled', chapters[0].extensionId === manifest.id);
  const pages = await extension.getChapterPages('ch-1');
  check('invalid image schemes dropped', pages.length === 2);
  check('pages sorted by index', pages[0].index === 0 && pages[1].index === 1);
  check('default Referer header applied', pages[1].headers?.Referer === manifest.baseUrl);
  check('header injection stripped', !String(pages[0].headers?.['X-Bad'] ?? '').includes('\n'));
  check('custom Referer preserved', pages[0].headers?.Referer === 'https://reader.example.com/ch/1');

  console.log('\n-> 4. Host allow-list enforcement');
  const blocked = await extension.getSeriesDetails('blocked-net');
  check('non-allow-listed host blocked', blocked.description === 'BLOCKED_HOST', blocked.description);
  check('request counter incremented', (runtime.getExtensionInfo(manifest.id)?.requestCount ?? 0) === 1);

  console.log('\n-> 5. BaseExtension HTTP client + anti-bot + cookies');
  const { server, port } = await startServer();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    class SiteExtension extends BaseExtension {
      manifest = { ...JSON.parse(JSON.stringify(manifest)), id: 'com.yomira.site-extension', baseUrl };
      httpOptions = { allowPrivateHosts: true, baseUrl };

      async search(query) {
        const first = await this.getText('/cookie');
        const echo = JSON.parse(await this.getText('/echo'));
        return [{
          id: 'site-1',
          canonicalTitle: `Site ${query}`,
          description: `${first}|${echo.userAgent}|${echo.referer}|${echo.cookie}`,
          aliases: [],
          coverUrl: '',
          type: 'COMIC',
          status: 'ONGOING',
          contentRating: 'SAFE',
          isAdult: false,
          genres: [],
          tags: [],
          metadataSources: [{ providerId: this.manifest.id, externalId: 'site-1', lastSyncedAt: Date.now() }],
          createdAt: Date.now(),
          updatedAt: Date.now(),
        }];
      }
      async getSeriesDetails() { return {}; }
      async getChapters() { return []; }
      async getChapterPages() { return []; }
    }

    const site = new SiteExtension();
    check('BaseExtension manifest validated', site.validateManifest().length === 0, site.validateManifest().join('; '));
    const siteResults = await site.search('test');
    const parts = siteResults[0].description.split('|');
    check('cookie persisted across requests', parts[0] === 'cookie set' && parts[3].includes('session=abc123'), parts[3]);
    check('browser User-Agent sent', parts[1].includes('Mozilla/5.0'));
    check('Referer sent', parts[2] === baseUrl);

    const client = new ExtensionHttpClient({ baseUrl, allowPrivateHosts: true, maxRetries: 0 });
    let antiBotCode = 'none';
    try {
      await client.request('/cf');
    } catch (error) {
      antiBotCode = error instanceof ExtensionHttpError ? error.code : 'wrong-error';
    }
    check('Cloudflare challenge detected', antiBotCode === 'ANTI_BOT', antiBotCode);

    let privateBlocked = 'none';
    try {
      await new ExtensionHttpClient({ baseUrl: 'http://127.0.0.1:1/' }).request('/x');
    } catch (error) {
      privateBlocked = error instanceof ExtensionHttpError ? error.code : 'wrong-error';
    }
    check('private host blocked by default', privateBlocked === 'BLOCKED_HOST', privateBlocked);

    console.log('\n-> 6. Sandboxed relative fetch (allow-listed local host)');
    const localManifest = { ...manifest, id: 'com.yomira.local-scraper', baseUrl };
    const localSource = `
      module.exports = {
        manifest: ${JSON.stringify(localManifest)},
        async search(query) {
          const response = await fetch('/api?from=sandbox');
          const json = await response.json();
          return [{
            id: 'local-1',
            canonicalTitle: 'Local ' + query,
            description: JSON.stringify(json)
          }];
        },
        async getSeriesDetails() { return {}; },
        async getChapters() { return []; },
        async getChapterPages() { return []; }
      };
    `;
    const localRuntime = new ExtensionRuntime({ allowPrivateHosts: true });
    const localExtension = await localRuntime.loadExtension(localSource, localManifest);
    const localResults = await localExtension.search('fetch');
    const localPayload = JSON.parse(localResults[0].description);
    check('sandbox fetch reached allowed host', localPayload.ok === true && localPayload.path === '/api?from=sandbox');
    check('sandbox fetch sent Referer', localPayload.referer === baseUrl, String(localPayload.referer));
    check('sandbox fetch sent User-Agent', String(localPayload.userAgent).includes('Mozilla/5.0'));

    console.log('\n-> 6b. Download extension source from URL');
    const downloaded = await localRuntime.loadExtensionFromUrl(
      `${baseUrl}/extension.js`,
      { ...manifest, id: 'com.yomira.downloaded', baseUrl }
    );
    const downloadedResults = await downloaded.search('source');
    check('downloaded source loaded', downloadedResults[0]?.canonicalTitle === 'Downloaded source');
    check('downloaded manifest respected', downloaded.manifest.id === 'com.yomira.downloaded');
  } finally {
    server.close();
  }

  console.log('\n-> 7. Duplicate load + unload');
  const runtime2 = new ExtensionRuntime();
  const loadedA = await runtime2.loadExtension(source, manifest);
  const loadedB = await runtime2.loadExtension(source, manifest);
  check('reload replaces previous instance', loadedA !== loadedB && runtime2.getLoadedExtensions().length === 1);
  check('unload works', runtime2.unloadExtension(manifest.id) === true && runtime2.getLoadedExtensions().length === 0);
  let disposedError = '';
  try {
    await loadedB.search('after-unload');
  } catch (error) {
    disposedError = error.code ?? '';
  }
  check('unloaded source cannot be used', disposedError === 'DISPOSED', disposedError);

  console.log(`\n=== RESULT: ${passed} passed, ${failed} failed ===`);
  if (failed > 0) process.exit(1);
}

main().catch((error) => {
  console.error('\nTest crashed:', error);
  process.exit(1);
});
