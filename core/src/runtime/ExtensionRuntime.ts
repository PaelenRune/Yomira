/**
 * Yomira Extension Runtime - Sandboxed Execution Engine
 *
 * Downloads (or receives) community scraper JavaScript and executes it in an
 * isolated context. The sandbox exposes only:
 *   - `fetch` / `http`  -> allow-listed, size-limited, cookie-aware HTTP bridge
 *   - `helpers`         -> pure regex/HTML/URL helpers
 *   - `sleep`           -> bounded async delay
 *   - `console`         -> captured log ring buffer
 *   - `URL`, `URLSearchParams` -> sandbox-native minimal implementations
 *
 * Explicitly unavailable: `process`, `require`, `fs`, `child_process`, module
 * loaders, `Buffer`, `Deno`, `Bun`, and code generation (`eval`/`Function`).
 *
 * Two engines are supported:
 *   - `vm`       (Node.js `node:vm`): real realm isolation. Preferred.
 *   - `function` (any runtime): shadowed-globals fallback, DEGRADED isolation.
 *
 * The `vm` engine uses `codeGeneration: { strings: false, wasm: false }` and a
 * bootstrap that closes over the host bridge, so extension code never receives
 * a host-realm function/object it could use to climb back to `process`.
 */

import {
  Chapter,
  ContentRating,
  ExtensionManifest,
  ExtensionSource,
  Page,
  Series,
  SeriesMetadataMapping,
  SeriesStatus,
  SeriesType,
} from '../types/index.js';
import {
  ExtensionHttpClient,
  ExtensionHttpError,
  HtmlUtils,
  type ExtensionRequestOptions,
  type HttpClientOptions,
} from './BaseExtension.js';

/* -------------------------------------------------------------------------- */
/*                                   Types                                    */
/* -------------------------------------------------------------------------- */

export type RuntimeIsolation = 'vm' | 'function';

export type ExtensionRuntimeErrorCode =
  | 'DISPOSED'
  | 'SOURCE_INVALID'
  | 'SOURCE_TOO_LARGE'
  | 'SOURCE_FETCH_FAILED'
  | 'ISOLATION_UNAVAILABLE'
  | 'MANIFEST_INVALID'
  | 'LOAD_FAILED'
  | 'NO_EXPORT'
  | 'METHOD_MISSING'
  | 'EXECUTION_FAILED'
  | 'EXECUTION_TIMEOUT'
  | 'RESULT_INVALID'
  | 'RESULT_TOO_LARGE'
  | 'NETWORK_DISABLED'
  | 'REQUEST_BUDGET_EXCEEDED';

export class ExtensionRuntimeError extends Error {
  readonly code: ExtensionRuntimeErrorCode;

  constructor(code: ExtensionRuntimeErrorCode, message: string, cause?: unknown) {
    super(message);
    this.name = 'ExtensionRuntimeError';
    this.code = code;
    if (cause !== undefined) {
      (this as { cause?: unknown }).cause = cause;
    }
  }
}

export interface ExtensionLogEntry {
  level: 'log' | 'info' | 'warn' | 'error' | 'debug' | 'trace';
  message: string;
  at: number;
}

export interface ExtensionRuntimeOptions {
  userAgent?: string;
  /** HTTP request timeout in milliseconds. */
  timeoutMs?: number;
  /** Per-method execution timeout in milliseconds (async work only). */
  methodTimeoutMs?: number;
  /** Maximum accepted scraper source size. */
  maxSourceBytes?: number;
  /** Maximum HTTP response size an extension may read. */
  maxResponseBytes?: number;
  /** Maximum serialized result size returned by one method call. */
  maxResultBytes?: number;
  /** Total HTTP requests an extension may perform in this runtime session. */
  maxRequestsPerExtension?: number;
  /** Extra hosts the extension is allowed to call (CDNs, image proxies). */
  allowedHosts?: string[];
  /** Never enable for untrusted extensions; intended for local development. */
  allowPrivateHosts?: boolean;
  allowNetwork?: boolean;
  cookies?: boolean;
  isolation?: 'auto' | 'vm' | 'function';
  /** Refuse to load extensions unless real `vm` isolation is available. */
  requireIsolation?: boolean;
  logger?: (entry: ExtensionLogEntry, extensionId: string) => void;
}

export interface LoadedExtensionInfo {
  id: string;
  name: string;
  version: string;
  baseUrl: string;
  isolation: RuntimeIsolation;
  loadedAt: number;
  sourceBytes: number;
  sourceHash: string;
  allowedHosts: string[];
  requestCount: number;
  methodCalls: number;
  logs: ExtensionLogEntry[];
}

type ExtensionMethod = 'search' | 'getSeriesDetails' | 'getChapters' | 'getChapterPages';

const REQUIRED_METHODS: ExtensionMethod[] = [
  'search',
  'getSeriesDetails',
  'getChapters',
  'getChapterPages',
];

const MAX_RESULT_ITEMS = 500;

interface SandboxDescriptor {
  manifest?: unknown;
  methods?: unknown;
}

interface SandboxInstance {
  isolation: RuntimeIsolation;
  descriptor: SandboxDescriptor;
  call(method: ExtensionMethod, args: unknown[]): Promise<unknown>;
  drainLogs(): ExtensionLogEntry[];
  dispose(): void;
}

interface LoadedEntry {
  info: LoadedExtensionInfo;
  token: { disposed: boolean };
  sandbox: SandboxInstance;
}

/* -------------------------------------------------------------------------- */
/*                          Bootstrap (sandbox realm)                         */
/* -------------------------------------------------------------------------- */

/**
 * Executed INSIDE the sandbox before the extension. Everything it defines is a
 * sandbox-realm object. The host bridge is captured in a closure and the global
 * reference is deleted, so extension code cannot reach host functions.
 */
const SANDBOX_BOOTSTRAP = String.raw`(function (hostRequest) {
  'use strict';

  var logs = [];

  function argToString(value) {
    if (typeof value === 'string') return value;
    try { return JSON.stringify(value); } catch (error) { return String(value); }
  }

  function record(level, args) {
    if (logs.length >= 200) return;
    var parts = [];
    for (var i = 0; i < args.length; i++) parts.push(argToString(args[i]));
    logs.push({ level: level, message: parts.join(' ').slice(0, 4000), at: Date.now() });
  }

  var sandboxConsole = {
    log: function () { record('log', arguments); },
    info: function () { record('info', arguments); },
    warn: function () { record('warn', arguments); },
    error: function () { record('error', arguments); },
    debug: function () { record('debug', arguments); },
    trace: function () { record('trace', arguments); }
  };

  globalThis.console = sandboxConsole;
  globalThis.__yomiraDrainLogs = function () {
    var copy = logs.splice(0, logs.length);
    return JSON.stringify(copy);
  };

  function makeError(payload) {
    var message = payload && payload.message ? String(payload.message) : 'Extension request failed';
    var error = new Error(message);
    if (payload) {
      if (payload.code) error.code = payload.code;
      if (payload.status !== undefined) error.status = payload.status;
    }
    return error;
  }

  function normalizeHeaders(headers) {
    var out = {};
    if (!headers || typeof headers !== 'object') return out;
    var keys = Object.keys(headers);
    for (var i = 0; i < keys.length && i < 40; i++) {
      var value = headers[keys[i]];
      if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
        out[keys[i]] = String(value);
      }
    }
    return out;
  }

  async function sandboxFetch(input, init) {
    init = init || {};
    var url = typeof input === 'string' ? input : (input && input.href) ? input.href : String(input);
    var requestPayload = {
      op: 'fetch',
      url: url,
      method: init.method || 'GET',
      headers: normalizeHeaders(init.headers),
      body: typeof init.body === 'string' ? init.body : undefined,
      timeoutMs: typeof init.timeoutMs === 'number' ? init.timeoutMs : undefined
    };
    var raw = await hostRequest(JSON.stringify(requestPayload));
    var data = JSON.parse(raw);
    if (!data.ok) throw makeError(data.error);
    var text = typeof data.text === 'string' ? data.text : '';
    var responseHeaders = data.headers && typeof data.headers === 'object' ? data.headers : {};
    return {
      ok: data.status >= 200 && data.status < 300,
      status: data.status,
      url: data.url,
      headers: responseHeaders,
      text: async function () { return text; },
      json: async function () { return JSON.parse(text); }
    };
  }

  var sandboxHttp = {
    request: sandboxFetch,
    getText: async function (url, init) { var response = await sandboxFetch(url, init); return response.text(); },
    getJson: async function (url, init) { var response = await sandboxFetch(url, init); return response.json(); }
  };

  function sleep(ms) {
    return hostRequest(JSON.stringify({ op: 'sleep', ms: ms })).then(function () { return undefined; });
  }

  /* ----------------------- minimal URL implementation ---------------------- */

  function decodePart(value) {
    try { return decodeURIComponent(String(value).replace(/\+/g, ' ')); } catch (error) { return String(value); }
  }

  function encodePart(value) {
    return encodeURIComponent(String(value));
  }

  function SandboxURLSearchParams(init) {
    this._list = [];
    if (init === undefined || init === null) return;
    if (typeof init === 'object') {
      var source = init;
      var keys = Object.keys(source);
      for (var k = 0; k < keys.length; k++) this.append(keys[k], source[keys[k]]);
      return;
    }
    var raw = String(init);
    if (raw.charAt(0) === '?') raw = raw.slice(1);
    var pairs = raw.split('&');
    for (var i = 0; i < pairs.length; i++) {
      if (!pairs[i]) continue;
      var index = pairs[i].indexOf('=');
      var key = index === -1 ? pairs[i] : pairs[i].slice(0, index);
      var value = index === -1 ? '' : pairs[i].slice(index + 1);
      this._list.push([decodePart(key), decodePart(value)]);
    }
  }

  SandboxURLSearchParams.prototype.append = function (key, value) {
    this._list.push([String(key), String(value)]);
  };
  SandboxURLSearchParams.prototype.set = function (key, value) {
    var name = String(key);
    var found = false;
    var next = [];
    for (var i = 0; i < this._list.length; i++) {
      if (this._list[i][0] !== name) { next.push(this._list[i]); continue; }
      if (!found) { next.push([name, String(value)]); found = true; }
    }
    if (!found) next.push([name, String(value)]);
    this._list = next;
  };
  SandboxURLSearchParams.prototype.get = function (key) {
    var name = String(key);
    for (var i = 0; i < this._list.length; i++) if (this._list[i][0] === name) return this._list[i][1];
    return null;
  };
  SandboxURLSearchParams.prototype.getAll = function (key) {
    var name = String(key);
    var out = [];
    for (var i = 0; i < this._list.length; i++) if (this._list[i][0] === name) out.push(this._list[i][1]);
    return out;
  };
  SandboxURLSearchParams.prototype.has = function (key) { return this.get(key) !== null; };
  SandboxURLSearchParams.prototype.delete = function (key) {
    var name = String(key);
    this._list = this._list.filter(function (pair) { return pair[0] !== name; });
  };
  SandboxURLSearchParams.prototype.toString = function () {
    var parts = [];
    for (var i = 0; i < this._list.length; i++) {
      parts.push(encodePart(this._list[i][0]) + '=' + encodePart(this._list[i][1]));
    }
    return parts.join('&');
  };
  SandboxURLSearchParams.prototype.forEach = function (callback, thisArg) {
    for (var i = 0; i < this._list.length; i++) {
      callback.call(thisArg, this._list[i][1], this._list[i][0], this);
    }
  };

  function normalizePath(path) {
    var suffix = '';
    var hashIndex = path.indexOf('#');
    if (hashIndex !== -1) { suffix = path.slice(hashIndex); path = path.slice(0, hashIndex); }
    var query = '';
    var queryIndex = path.indexOf('?');
    if (queryIndex !== -1) { query = path.slice(queryIndex); path = path.slice(0, queryIndex); }
    var absolute = path.charAt(0) === '/';
    var parts = path.split('/');
    var stack = [];
    for (var i = 0; i < parts.length; i++) {
      var part = parts[i];
      if (part === '' || part === '.') continue;
      if (part === '..') { stack.pop(); continue; }
      stack.push(part);
    }
    var result = (absolute ? '/' : '') + stack.join('/');
    if (result === '') result = '/';
    return result + query + suffix;
  }

  function absolutizeUrl(input, base) {
    var raw = String(input === undefined || input === null ? '' : input).trim();
    if (/^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(raw)) return raw;
    if (!base) throw new TypeError('Invalid URL: relative path without a base');
    var match = /^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^/?#]*)([^?#]*)/.exec(String(base));
    if (!match) throw new TypeError('Invalid base URL: ' + base);
    var origin = match[1] + '//' + match[2];
    var basePath = match[3] || '/';
    if (raw.slice(0, 2) === '//') return match[1] + raw;
    if (raw.charAt(0) === '/') return origin + normalizePath(raw);
    if (raw.charAt(0) === '?') return origin + basePath + raw;
    if (raw.charAt(0) === '#') return origin + basePath + raw;
    var directory = basePath.slice(0, basePath.lastIndexOf('/') + 1) || '/';
    return origin + normalizePath(directory + raw);
  }

  function SandboxURL(input, base) {
    var raw = absolutizeUrl(input, base);
    var match = /^([a-zA-Z][a-zA-Z0-9+.-]*:)\/\/([^/?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/.exec(raw);
    if (!match) throw new TypeError('Invalid URL: ' + raw);
    this.protocol = match[1].toLowerCase();
    this.host = match[2];
    var hostname = this.host;
    var atIndex = hostname.lastIndexOf('@');
    if (atIndex !== -1) hostname = hostname.slice(atIndex + 1);
    if (hostname.charAt(0) === '[') {
      var close = hostname.indexOf(']');
      this.hostname = close === -1 ? hostname : hostname.slice(0, close + 1);
    } else {
      this.hostname = hostname.split(':')[0];
    }
    var port = hostname.slice(this.hostname.length);
    this.port = port.charAt(0) === ':' ? port.slice(1) : '';
    this.origin = this.protocol + '//' + this.host;
    this.pathname = match[3] || '/';
    this.search = match[4] || '';
    this.hash = match[5] || '';
    this.searchParams = new SandboxURLSearchParams(this.search);
  }

  Object.defineProperty(SandboxURL.prototype, 'href', {
    get: function () {
      var query = this.searchParams.toString();
      return this.origin + this.pathname + (query ? '?' + query : '') + this.hash;
    }
  });
  SandboxURL.prototype.toString = function () { return this.href; };
  SandboxURL.prototype.toJSON = function () { return this.href; };

  /* ------------------------------- helpers --------------------------------- */

  var namedEntities = {
    amp: '&', lt: '<', gt: '>', quot: '"', apos: "'", nbsp: ' ',
    ndash: '-', mdash: '-', hellip: '...', rsquo: "'", lsquo: "'", rdquo: '"', ldquo: '"'
  };

  function decodeEntities(input) {
    return String(input).replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, function (full, entity) {
      if (entity.slice(0, 2).toLowerCase() === '#x') {
        var hex = parseInt(entity.slice(2), 16);
        return Number.isFinite(hex) ? String.fromCodePoint(hex) : full;
      }
      if (entity.charAt(0) === '#') {
        var dec = parseInt(entity.slice(1), 10);
        return Number.isFinite(dec) ? String.fromCodePoint(dec) : full;
      }
      var named = namedEntities[entity.toLowerCase()];
      return named === undefined ? full : named;
    });
  }

  function stripHtml(input) {
    var text = String(input)
      .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
      .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
      .replace(/<[^>]+>/g, ' ');
    return decodeEntities(text).replace(/[ \t\f\v]+/g, ' ').replace(/\s*\n\s*/g, '\n').trim();
  }

  function escapeRegExp(value) {
    return String(value).replace(/[.*+?^$\{}()|[\]\\]/g, '\\$&');
  }

  function getAttribute(tagHtml, name) {
    var escaped = escapeRegExp(name);
    var match = new RegExp('\\b' + escaped + '\\s*=\\s*(?:"([^"]*)"|\'([^\']*)\'|([^\\s>]+))', 'i').exec(String(tagHtml));
    if (!match) return undefined;
    return decodeEntities(match[1] !== undefined ? match[1] : match[2] !== undefined ? match[2] : match[3] || '');
  }

  function extractAll(html, pattern) {
    var source = typeof pattern === 'string' ? escapeRegExp(pattern) : pattern.source;
    var flags = typeof pattern === 'string' ? 'g' : (
      pattern.flags.indexOf('g') === -1 ? pattern.flags + 'g' : pattern.flags
    );
    var regex = new RegExp(source, flags.replace('y', ''));
    var results = [];
    var match;
    var guard = 0;
    while ((match = regex.exec(String(html))) !== null && guard < 10000) {
      results.push(match[1] !== undefined ? match[1] : match[0]);
      if (match[0] === '') regex.lastIndex += 1;
      guard += 1;
    }
    return results;
  }

  function matchFirst(html, pattern) {
    var source = typeof pattern === 'string' ? escapeRegExp(pattern) : pattern.source;
    var flags = typeof pattern === 'string' ? '' : pattern.flags.replace(/[gy]/g, '');
    var match = new RegExp(source, flags).exec(String(html));
    if (!match) return undefined;
    return match[1] !== undefined ? match[1] : match[0];
  }

  var helpers = {
    decodeEntities: decodeEntities,
    stripHtml: stripHtml,
    getAttribute: getAttribute,
    extractAll: extractAll,
    matchFirst: matchFirst,
    absolutize: absolutizeUrl
  };

  globalThis.fetch = sandboxFetch;
  globalThis.http = sandboxHttp;
  globalThis.helpers = helpers;
  globalThis.sleep = sleep;

  /* ------------------------------- install --------------------------------- */

  var extensionTarget = null;
  var extensionManifest = globalThis.__yomiraManifest
    ? JSON.parse(JSON.stringify(globalThis.__yomiraManifest))
    : null;
  delete globalThis.__yomiraManifest;

  function finishInstall(target, manifest) {
    if (!target || typeof target !== 'object') {
      throw new TypeError('Extension factory must return an object');
    }
    var present = [];
    for (var i = 0; i < 4; i++) {
      var name = ['search', 'getSeriesDetails', 'getChapters', 'getChapterPages'][i];
      if (typeof target[name] === 'function') present.push(name);
    }
    if (present.length !== 4) {
      var missing = ['search', 'getSeriesDetails', 'getChapters', 'getChapterPages'].filter(function (name) {
        return present.indexOf(name) === -1;
      });
      throw new TypeError('Extension is missing required methods: ' + missing.join(', '));
    }
    var exportedManifest = null;
    try { exportedManifest = target.manifest || null; } catch (error) { exportedManifest = null; }
    extensionTarget = target;
    return { manifest: exportedManifest || manifest || null, methods: present };
  }

  function install(value, manifest) {
    if (!value) {
      throw new TypeError('Extension did not export anything. Assign module.exports or call defineExtension().');
    }
    var target = value;
    if (typeof target === 'function') {
      var proto = target.prototype || {};
      var looksLikeClass = typeof proto.search === 'function' ||
        typeof proto.getChapters === 'function' ||
        typeof proto.getChapterPages === 'function' ||
        typeof proto.getSeriesDetails === 'function';
      target = looksLikeClass ? new target({
        manifest: manifest,
        fetch: sandboxFetch,
        http: sandboxHttp,
        helpers: helpers,
        sleep: sleep,
        console: sandboxConsole,
        URL: SandboxURL,
        URLSearchParams: SandboxURLSearchParams
      }) : target({
        manifest: manifest,
        fetch: sandboxFetch,
        http: sandboxHttp,
        helpers: helpers,
        sleep: sleep,
        console: sandboxConsole,
        URL: SandboxURL,
        URLSearchParams: SandboxURLSearchParams
      });
    }
    if (target && typeof target.then === 'function') {
      return target.then(function (resolved) { return finishInstall(resolved, manifest); });
    }
    return finishInstall(target, manifest);
  }

  globalThis.__yomiraInstall = install;
  globalThis.__yomiraCall = function (name, argsJson) {
    if (!extensionTarget) throw new Error('Extension is not initialized');
    var method = extensionTarget[name];
    if (typeof method !== 'function') throw new Error('Unknown extension method: ' + name);
    return method.apply(extensionTarget, JSON.parse(argsJson));
  };
})(globalThis.__yomiraHostRequest);
delete globalThis.__yomiraHostRequest;`;

/* -------------------------------------------------------------------------- */
/*                              Runtime Engine                                */
/* -------------------------------------------------------------------------- */

export class ExtensionRuntime {
  private readonly options: Required<
    Pick<
      ExtensionRuntimeOptions,
      | 'userAgent'
      | 'timeoutMs'
      | 'methodTimeoutMs'
      | 'maxSourceBytes'
      | 'maxResponseBytes'
      | 'maxResultBytes'
      | 'maxRequestsPerExtension'
      | 'allowPrivateHosts'
      | 'allowNetwork'
      | 'cookies'
      | 'isolation'
      | 'requireIsolation'
    >
  > &
    ExtensionRuntimeOptions;

  private readonly entries = new Map<string, LoadedEntry>();
  private disposed = false;

  constructor(options: ExtensionRuntimeOptions = {}) {
    this.options = {
      userAgent:
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
        '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 YomiraReader/1.0',
      timeoutMs: 20_000,
      methodTimeoutMs: 45_000,
      maxSourceBytes: 2 * 1024 * 1024,
      maxResponseBytes: 8 * 1024 * 1024,
      maxResultBytes: 4 * 1024 * 1024,
      maxRequestsPerExtension: 500,
      allowPrivateHosts: false,
      allowNetwork: true,
      cookies: true,
      isolation: 'auto',
      requireIsolation: false,
      ...options,
    };
  }

  /**
   * Load an extension from JavaScript source (CommonJS `module.exports`,
   * `exports.search = ...`, `defineExtension(obj)`, a class, or a factory).
   */
  async loadExtension(sourceCode: string, manifest?: ExtensionManifest): Promise<ExtensionSource> {
    this.assertNotDisposed();

    if (typeof sourceCode !== 'string' || sourceCode.trim().length === 0) {
      throw new ExtensionRuntimeError('SOURCE_INVALID', 'Extension source must be a non-empty string');
    }

    const sourceBytes = utf8ByteLength(sourceCode);
    if (sourceBytes > this.options.maxSourceBytes) {
      throw new ExtensionRuntimeError(
        'SOURCE_TOO_LARGE',
        `Extension source is ${sourceBytes} bytes; limit is ${this.options.maxSourceBytes}`
      );
    }

    if (manifest) {
      const errors = validateManifest(manifest);
      if (errors.length > 0) {
        throw new ExtensionRuntimeError(
          'MANIFEST_INVALID',
          `Invalid extension manifest: ${errors.join('; ')}`
        );
      }
    }

    const suppliedManifest = manifest ? deepFreeze(normalizeManifest(manifest)) : undefined;
    const idHint = suppliedManifest?.id ?? 'pending';
    const info: LoadedExtensionInfo = {
      id: idHint,
      name: suppliedManifest?.name ?? idHint,
      version: suppliedManifest?.version ?? '0.0.0',
      baseUrl: suppliedManifest?.baseUrl ?? '',
      isolation: 'function',
      loadedAt: Date.now(),
      sourceBytes,
      sourceHash: fnv1a(sourceCode),
      allowedHosts: [],
      requestCount: 0,
      methodCalls: 0,
      logs: [],
    };

    const sandbox = await this.createSandbox(sourceCode, suppliedManifest, info);
    info.isolation = sandbox.isolation;

    let resolvedManifest: ExtensionManifest;
    try {
      resolvedManifest = this.resolveManifest(sandbox.descriptor.manifest, suppliedManifest);
    } catch (error) {
      sandbox.dispose();
      throw error;
    }

    const methods = Array.isArray(sandbox.descriptor.methods)
      ? sandbox.descriptor.methods.filter((m): m is string => typeof m === 'string')
      : [];
    const missing = REQUIRED_METHODS.filter((method) => !methods.includes(method));
    if (missing.length > 0) {
      sandbox.dispose();
      throw new ExtensionRuntimeError(
        'METHOD_MISSING',
        `Extension '${resolvedManifest.id}' is missing required methods: ${missing.join(', ')}`
      );
    }

    info.id = resolvedManifest.id;
    info.name = resolvedManifest.name;
    info.version = resolvedManifest.version;
    info.baseUrl = resolvedManifest.baseUrl;
    info.allowedHosts = allowedHostsFor(resolvedManifest, this.options.allowedHosts ?? []);

    if (this.entries.has(resolvedManifest.id)) {
      this.unloadExtension(resolvedManifest.id);
    }

    const token = { disposed: false };
    const entry: LoadedEntry = { info, token, sandbox };
    this.entries.set(resolvedManifest.id, entry);

    (sandbox as SandboxInstance & { setManifest?: (manifest: ExtensionManifest) => void }).setManifest?.(
      resolvedManifest
    );

    return this.createSource(entry, resolvedManifest);
  }

  /**
   * Download an extension from a repository URL and load it.
   * `https` is required unless private hosts are explicitly enabled (dev).
   */
  async loadExtensionFromUrl(url: string, manifest?: ExtensionManifest): Promise<ExtensionSource> {
    this.assertNotDisposed();

    let parsed: URL;
    try {
      parsed = new URL(url);
    } catch {
      throw new ExtensionRuntimeError('SOURCE_FETCH_FAILED', `Invalid extension URL: '${url}'`);
    }

    if (parsed.protocol !== 'https:' && !(parsed.protocol === 'http:' && this.options.allowPrivateHosts)) {
      throw new ExtensionRuntimeError(
        'SOURCE_FETCH_FAILED',
        'Extension sources must be served over HTTPS'
      );
    }

    const client = new ExtensionHttpClient({
      userAgent: this.options.userAgent,
      timeoutMs: this.options.timeoutMs,
      maxResponseBytes: this.options.maxSourceBytes,
      allowPrivateHosts: this.options.allowPrivateHosts,
      maxRetries: 2,
      cookies: false,
    });

    let sourceCode: string;
    try {
      const response = await client.request(parsed.toString(), {
        headers: { Accept: 'application/javascript, text/javascript, text/plain, */*' },
      });
      sourceCode = response.text;
    } catch (error) {
      throw new ExtensionRuntimeError(
        'SOURCE_FETCH_FAILED',
        `Failed to download extension from ${url}: ${errorMessage(error)}`,
        error
      );
    }

    return this.loadExtension(sourceCode, manifest);
  }

  getLoadedExtensions(): LoadedExtensionInfo[] {
    return Array.from(this.entries.values()).map((entry) => ({
      ...entry.info,
      allowedHosts: [...entry.info.allowedHosts],
      logs: [...entry.info.logs],
    }));
  }

  getExtensionInfo(id: string): LoadedExtensionInfo | undefined {
    const entry = this.entries.get(id);
    if (!entry) return undefined;
    return {
      ...entry.info,
      allowedHosts: [...entry.info.allowedHosts],
      logs: [...entry.info.logs],
    };
  }

  getLogs(id: string): ExtensionLogEntry[] {
    return [...(this.entries.get(id)?.info.logs ?? [])];
  }

  unloadExtension(id: string): boolean {
    const entry = this.entries.get(id);
    if (!entry) return false;
    entry.token.disposed = true;
    entry.sandbox.dispose();
    this.entries.delete(id);
    return true;
  }

  dispose(): void {
    for (const id of Array.from(this.entries.keys())) {
      this.unloadExtension(id);
    }
    this.disposed = true;
  }

  /* ------------------------------ source API ------------------------------ */

  private createSource(entry: LoadedEntry, manifest: ExtensionManifest): ExtensionSource {
    const runtime = this;
    const frozenManifest = deepFreeze(normalizeManifest(manifest));

    const invoke = <T>(
      method: ExtensionMethod,
      args: unknown[],
      normalize: (raw: unknown) => T
    ): Promise<T> => runtime.invoke(entry, method, args, normalize);

    const source: ExtensionSource = {
      manifest: frozenManifest,
      search: (query, page = 1) => invoke('search', [String(query), page], (raw) => {
        const series: Series[] = [];
        const items = assertArray(raw, 'search');
        for (let index = 0; index < items.length; index += 1) {
          try {
            series.push(sanitizeSeries(items[index], frozenManifest, index));
          } catch (error) {
            // One malformed card must not break the whole result page.
            if (!(error instanceof ExtensionRuntimeError) || error.code !== 'RESULT_INVALID') {
              throw error;
            }
          }
        }
        return series;
      }),
      getSeriesDetails: (externalId) =>
        invoke('getSeriesDetails', [String(externalId)], (raw) =>
          sanitizePartialSeries(raw, frozenManifest)
        ),
      getChapters: (externalId) =>
        invoke('getChapters', [String(externalId)], (raw) => {
          return assertArray(raw, 'getChapters')
            .map((item, index) => sanitizeChapter(item, frozenManifest, String(externalId), index))
            .sort((a, b) => a.chapterNumber - b.chapterNumber);
        }),
      getChapterPages: (chapterId) =>
        invoke('getChapterPages', [String(chapterId)], (raw) => {
          return assertArray(raw, 'getChapterPages')
            .map((item, index) => sanitizePage(item, index, frozenManifest))
            .filter((page): page is Page => page !== null)
            .sort((a, b) => a.index - b.index);
        }),
    };

    return Object.freeze(source);
  }

  private async invoke<T>(
    entry: LoadedEntry,
    method: ExtensionMethod,
    args: unknown[],
    normalize: (raw: unknown) => T
  ): Promise<T> {
    this.assertNotDisposed();
    if (entry.token.disposed) {
      throw new ExtensionRuntimeError('DISPOSED', `Extension '${entry.info.id}' has been unloaded`);
    }

    entry.info.methodCalls += 1;
    const started = Date.now();

    try {
      const rawPromise = entry.sandbox.call(method, args);
      const raw = await withTimeout(
        rawPromise,
        this.options.methodTimeoutMs,
        `Extension '${entry.info.id}'.${method} timed out after ${this.options.methodTimeoutMs}ms`,
        'EXECUTION_TIMEOUT'
      );
      const safeRaw = cloneResult(raw, this.options.maxResultBytes, entry.info.id, method);
      return normalize(safeRaw);
    } catch (error) {
      throw this.wrapExecutionError(entry, method, error, started);
    } finally {
      this.flushLogs(entry);
    }
  }

  private wrapExecutionError(
    entry: LoadedEntry,
    method: ExtensionMethod,
    error: unknown,
    started: number
  ): Error {
    if (error instanceof ExtensionRuntimeError) return error;
    if (isAntiBotError(error)) return error as Error;

    const message = errorMessage(error);
    const elapsed = Date.now() - started;
    const hint = /timed out/i.test(message)
      ? ' (request or method timeout)'
      : /cloudflare|just a moment/i.test(message)
        ? ' (anti-bot challenge)'
        : '';
    return new ExtensionRuntimeError(
      'EXECUTION_FAILED',
      `Extension '${entry.info.id}'.${method} failed after ${elapsed}ms: ${message}${hint}`,
      error
    );
  }

  private flushLogs(entry: LoadedEntry): void {
    let logs: ExtensionLogEntry[];
    try {
      logs = entry.sandbox.drainLogs();
    } catch {
      return;
    }
    if (logs.length === 0) return;

    for (const log of logs) {
      if (!log || typeof log.message !== 'string') continue;
      entry.info.logs.push({
        level: (['log', 'info', 'warn', 'error', 'debug', 'trace'] as const).includes(
          log.level as ExtensionLogEntry['level']
        )
          ? (log.level as ExtensionLogEntry['level'])
          : 'log',
        message: log.message.slice(0, 4000),
        at: typeof log.at === 'number' ? log.at : Date.now(),
      });
      if (entry.info.logs.length > 200) entry.info.logs.shift();
      this.options.logger?.(log, entry.info.id);
    }
  }

  private resolveManifest(raw: unknown, supplied?: ExtensionManifest): ExtensionManifest {
    const exported = asRecord(raw);
    const base = supplied ?? normalizeManifest(exported);
    const merged = normalizeManifest({ ...exported, ...base });

    const errors = validateManifest(merged);
    if (errors.length > 0) {
      throw new ExtensionRuntimeError(
        'MANIFEST_INVALID',
        `Extension manifest is invalid: ${errors.join('; ')}`
      );
    }
    return deepFreeze(merged);
  }

  /* ----------------------------- sandbox setup ---------------------------- */

  private async createSandbox(
    sourceCode: string,
    suppliedManifest: ExtensionManifest | undefined,
    info: LoadedExtensionInfo
  ): Promise<SandboxInstance> {
    const preference = this.options.isolation;
    const vm = preference === 'function' ? null : loadNodeVm();

    if (preference === 'vm' && !vm) {
      throw new ExtensionRuntimeError(
        'ISOLATION_UNAVAILABLE',
        'The vm isolation engine was requested but node:vm is unavailable in this environment'
      );
    }
    if (!vm && this.options.requireIsolation) {
      throw new ExtensionRuntimeError(
        'ISOLATION_UNAVAILABLE',
        'Real sandbox isolation is required, but node:vm is unavailable in this environment'
      );
    }

    if (vm) {
      return this.createVmSandbox(vm, sourceCode, suppliedManifest, info);
    }

    this.options.logger?.(
      {
        level: 'warn',
        message:
          'node:vm is unavailable; using degraded function-scope isolation. Do not run untrusted extensions in this environment.',
        at: Date.now(),
      },
      info.id
    );
    return this.createFunctionSandbox(sourceCode, suppliedManifest, info);
  }

  private async createVmSandbox(
    vm: NodeVmModule,
    sourceCode: string,
    suppliedManifest: ExtensionManifest | undefined,
    info: LoadedExtensionInfo
  ): Promise<SandboxInstance> {
    let client: ExtensionHttpClient | undefined;
    let manifest = suppliedManifest;

    const getClient = (): ExtensionHttpClient => {
      if (!manifest) {
        throw new ExtensionRuntimeError(
          'MANIFEST_INVALID',
          'Network access is unavailable until the extension manifest is resolved'
        );
      }
      if (!client) {
        client = new ExtensionHttpClient(this.httpOptionsFor(manifest));
      }
      return client;
    };

    const hostRequest = (raw: unknown): Promise<string> =>
      this.dispatchHostRequest(info, getClient, raw);

    const sandboxGlobals: Record<string, unknown> = {
      __yomiraHostRequest: hostRequest,
      __yomiraManifest: suppliedManifest ? JSON.parse(JSON.stringify(suppliedManifest)) : null,
    };

    const context = vm.createContext(sandboxGlobals, {
      name: `yomira:${info.id}`,
      codeGeneration: { strings: false, wasm: false },
    });

    try {
      vm.runInContext(SANDBOX_BOOTSTRAP, context, {
        timeout: 5_000,
        filename: `${info.id}/bootstrap.js`,
      });
    } catch (error) {
      throw new ExtensionRuntimeError(
        'LOAD_FAILED',
        `Failed to initialize sandbox for '${info.id}': ${errorMessage(error)}`,
        error
      );
    }

    let disposed = false;
    let descriptor: SandboxDescriptor;

    try {
      const wrapped = buildExtensionWrapper(sourceCode);
      const result = vm.runInContext(wrapped, context, {
        timeout: this.options.methodTimeoutMs,
        filename: `${info.id}/index.js`,
      });

      // Async factory exports return a promise; resolve it with a bounded wait.
      const descriptorJson = isThenable(result)
        ? await withTimeout(
            Promise.resolve(result),
            this.options.methodTimeoutMs,
            `Extension '${info.id}' initialization timed out`,
            'EXECUTION_TIMEOUT'
          )
        : result;

      if (typeof descriptorJson !== 'string') {
        throw new ExtensionRuntimeError(
          'NO_EXPORT',
          `Extension '${info.id}' did not return a valid export descriptor`
        );
      }
      descriptor = JSON.parse(descriptorJson) as SandboxDescriptor;
    } catch (error) {
      if (error instanceof ExtensionRuntimeError) throw error;
      throw new ExtensionRuntimeError(
        'LOAD_FAILED',
        `Failed to load extension '${info.id}': ${errorMessage(error)}`,
        error
      );
    }

    const syncTimeout = Math.min(this.options.methodTimeoutMs, 30_000);

    const instance: SandboxInstance = {
      isolation: 'vm',
      descriptor,
      call(method, args) {
        if (disposed) {
          return Promise.reject(
            new ExtensionRuntimeError('DISPOSED', `Extension '${info.id}' sandbox is disposed`)
          );
        }
        // Args are primitives; embed them as JSON so no host object crosses realms.
        const argsLiteral = JSON.stringify(JSON.stringify(args));
        const code = `globalThis.__yomiraCall(${JSON.stringify(method)}, ${argsLiteral})`;
        try {
          const value = vm.runInContext(code, context, {
            timeout: syncTimeout,
            filename: `${info.id}/${method}.js`,
          });
          return Promise.resolve(value);
        } catch (error) {
          return Promise.reject(error);
        }
      },
      drainLogs() {
        if (disposed) return [];
        try {
          const raw = vm.runInContext('globalThis.__yomiraDrainLogs()', context, {
            timeout: 1_000,
            filename: `${info.id}/logs.js`,
          });
          return typeof raw === 'string' ? (JSON.parse(raw) as ExtensionLogEntry[]) : [];
        } catch {
          return [];
        }
      },
      dispose() {
        disposed = true;
        client = undefined;
      },
      setManifest(next: ExtensionManifest) {
        manifest = next;
      },
    } as SandboxInstance & { setManifest: (next: ExtensionManifest) => void };

    return instance;
  }

  private createFunctionSandbox(
    sourceCode: string,
    suppliedManifest: ExtensionManifest | undefined,
    info: LoadedExtensionInfo
  ): SandboxInstance {
    let client: ExtensionHttpClient | undefined;
    let manifest = suppliedManifest;
    let disposed = false;
    const logs: ExtensionLogEntry[] = [];

    const getClient = (): ExtensionHttpClient => {
      if (!manifest) {
        throw new ExtensionRuntimeError(
          'MANIFEST_INVALID',
          'Network access is unavailable until the extension manifest is resolved'
        );
      }
      if (!client) client = new ExtensionHttpClient(this.httpOptionsFor(manifest));
      return client;
    };

    const pushLog = (level: ExtensionLogEntry['level'], args: unknown[]): void => {
      if (logs.length >= 200) return;
      const message = args
        .map((value) => (typeof value === 'string' ? value : safeStringify(value)))
        .join(' ')
        .slice(0, 4000);
      logs.push({ level, message, at: Date.now() });
    };

    const consoleStub = {
      log: (...args: unknown[]) => pushLog('log', args),
      info: (...args: unknown[]) => pushLog('info', args),
      warn: (...args: unknown[]) => pushLog('warn', args),
      error: (...args: unknown[]) => pushLog('error', args),
      debug: (...args: unknown[]) => pushLog('debug', args),
      trace: (...args: unknown[]) => pushLog('trace', args),
    };

    const hostRequest = (raw: unknown): Promise<string> =>
      this.dispatchHostRequest(info, getClient, raw);

    const safeFetch = async (
      input: unknown,
      init?: Record<string, unknown>
    ): Promise<unknown> => {
      const options = init ?? {};
      const url =
        typeof input === 'string'
          ? input
          : input && typeof input === 'object' && 'href' in input
            ? String((input as { href: unknown }).href)
            : String(input);
      const payload = await hostRequest(
        JSON.stringify({
          op: 'fetch',
          url,
          method: typeof options.method === 'string' ? options.method : 'GET',
          headers: options.headers ?? {},
          body: typeof options.body === 'string' ? options.body : undefined,
        })
      );
      const data = JSON.parse(payload) as {
        ok: boolean;
        status: number;
        url: string;
        headers: Record<string, string>;
        text: string;
        error?: { message: string; code?: string; status?: number };
      };
      if (!data.ok) {
        const error = new Error(data.error?.message ?? 'Extension request failed');
        (error as Error & { code?: string; status?: number }).code = data.error?.code;
        (error as Error & { code?: string; status?: number }).status = data.error?.status;
        throw error;
      }
      const text = data.text ?? '';
      return {
        ok: data.status >= 200 && data.status < 300,
        status: data.status,
        url: data.url,
        headers: data.headers ?? {},
        text: async () => text,
        json: async () => JSON.parse(text),
      };
    };

    const safeHttp = {
      request: safeFetch,
      getText: async (url: string, init?: Record<string, unknown>) => {
        const response = (await safeFetch(url, init)) as { text: () => Promise<string> };
        return response.text();
      },
      getJson: async (url: string, init?: Record<string, unknown>) => {
        const response = (await safeFetch(url, init)) as { json: () => Promise<unknown> };
        return response.json();
      },
    };

    const sleep = async (ms: unknown): Promise<void> => {
      await hostRequest(JSON.stringify({ op: 'sleep', ms }));
    };

    const factory = new Function(
      '__safeFetch',
      '__safeHttp',
      '__helpers',
      '__sleep',
      '__console',
      '__SandboxURL',
      '__SandboxURLSearchParams',
      `// Degraded fallback isolation: no vm module in this runtime.
       var module = { exports: {} };
       var exports = module.exports;
       var __exported = null;
       var defineExtension = function (value) { __exported = value; return value; };
       (function () {
         "use strict";
         ${sourceCode}
       }).call(undefined);
       return __exported || module.exports;`
    );

    let exported: unknown;
    try {
      exported = factory(
        safeFetch,
        safeHttp,
        HtmlUtils,
        sleep,
        consoleStub,
        URL,
        URLSearchParams
      );
    } catch (error) {
      throw new ExtensionRuntimeError(
        'LOAD_FAILED',
        `Failed to load extension '${info.id}': ${errorMessage(error)}`,
        error
      );
    }

    if (!exported) {
      throw new ExtensionRuntimeError(
        'NO_EXPORT',
        `Extension '${info.id}' did not export anything. Assign module.exports or call defineExtension().`
      );
    }

    let target: unknown = exported;
    if (typeof target === 'function') {
      const context = {
        manifest,
        fetch: safeFetch,
        http: safeHttp,
        helpers: HtmlUtils,
        sleep,
        console: consoleStub,
        URL,
        URLSearchParams,
      };
      const proto = (target as { prototype?: Record<string, unknown> }).prototype ?? {};
      const looksLikeClass = REQUIRED_METHODS.some((method) => typeof proto[method] === 'function');
      const factoryTarget = target as unknown as (ctx: unknown) => unknown;
      target = looksLikeClass
        ? new (factoryTarget as unknown as new (ctx: unknown) => unknown)(context)
        : factoryTarget(context);
    }

    const targetRecord = asRecord(target);
    const methods = REQUIRED_METHODS.filter((method) => typeof targetRecord[method] === 'function');
    const missing = REQUIRED_METHODS.filter((method) => !methods.includes(method));
    if (missing.length > 0) {
      throw new ExtensionRuntimeError(
        'METHOD_MISSING',
        `Extension '${info.id}' is missing required methods: ${missing.join(', ')}`
      );
    }

    const descriptor: SandboxDescriptor = {
      manifest: targetRecord.manifest ?? manifest ?? null,
      methods,
    };

    const instance: SandboxInstance = {
      isolation: 'function',
      descriptor,
      call(method, args) {
        if (disposed) {
          return Promise.reject(
            new ExtensionRuntimeError('DISPOSED', `Extension '${info.id}' sandbox is disposed`)
          );
        }
        try {
          const fn = targetRecord[method] as (...callArgs: unknown[]) => unknown;
          return Promise.resolve(fn.apply(target, args));
        } catch (error) {
          return Promise.reject(error);
        }
      },
      drainLogs() {
        return logs.splice(0, logs.length);
      },
      dispose() {
        disposed = true;
        client = undefined;
      },
      setManifest(next: ExtensionManifest) {
        manifest = next;
      },
    } as SandboxInstance & { setManifest: (next: ExtensionManifest) => void };

    return instance;
  }

  /* ------------------------------ host bridge ----------------------------- */

  private async dispatchHostRequest(
    info: LoadedExtensionInfo,
    getClient: () => ExtensionHttpClient,
    raw: unknown
  ): Promise<string> {
    try {
      if (typeof raw !== 'string' || raw.length > 512 * 1024) {
        return errorPayload('Request payload is invalid or too large', 'REQUEST_INVALID');
      }

      let payload: Record<string, unknown>;
      try {
        payload = asRecord(JSON.parse(raw));
      } catch {
        return errorPayload('Malformed request payload', 'REQUEST_INVALID');
      }

      const op = asString(payload.op) ?? 'fetch';

      if (op === 'sleep') {
        const ms = clampNumber(asNumber(payload.ms) ?? 0, 0, 10_000);
        await delay(ms);
        return JSON.stringify({ ok: true });
      }

      if (op !== 'fetch') {
        return errorPayload(`Unsupported sandbox operation '${op}'`, 'REQUEST_INVALID');
      }

      if (!this.options.allowNetwork) {
        return errorPayload('Network access is disabled for extensions', 'NETWORK_DISABLED');
      }

      if (info.requestCount >= this.options.maxRequestsPerExtension) {
        return errorPayload(
          `Extension '${info.id}' exceeded its request budget of ${this.options.maxRequestsPerExtension}`,
          'REQUEST_BUDGET_EXCEEDED'
        );
      }

      const url = asString(payload.url);
      if (!url) {
        return errorPayload('Request URL is required', 'REQUEST_INVALID');
      }

      const method = (asString(payload.method) ?? 'GET').toUpperCase();
      if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD'].includes(method)) {
        return errorPayload(`Unsupported HTTP method '${method}'`, 'REQUEST_INVALID');
      }

      const headers = sanitizeHeaders(asRecord(payload.headers));
      const body = typeof payload.body === 'string' ? payload.body : undefined;
      const timeoutMs = clampNumber(
        asNumber(payload.timeoutMs) ?? this.options.timeoutMs,
        1_000,
        this.options.timeoutMs
      );

      info.requestCount += 1;

      const client = getClient();
      const requestOptions: ExtensionRequestOptions = {
        method: method as ExtensionRequestOptions['method'],
        headers,
        body,
        timeoutMs,
      };
      const response = await client.request(url, requestOptions);

      const responseHeaders: Record<string, string> = {};
      for (const [key, value] of Object.entries(response.headers)) {
        if (key === 'set-cookie') continue;
        responseHeaders[key] = value;
      }

      return JSON.stringify({
        ok: true,
        status: response.status,
        url: response.url,
        headers: responseHeaders,
        text: response.text.slice(0, this.options.maxResponseBytes),
      });
    } catch (error) {
      return errorPayload(errorMessage(error), errorCode(error), errorStatus(error));
    }
  }

  private httpOptionsFor(manifest: ExtensionManifest): HttpClientOptions {
    return {
      baseUrl: manifest.baseUrl,
      referer: manifest.baseUrl,
      userAgent: this.options.userAgent,
      timeoutMs: this.options.timeoutMs,
      maxResponseBytes: this.options.maxResponseBytes,
      allowPrivateHosts: this.options.allowPrivateHosts,
      allowedHosts: this.options.allowedHosts ?? [],
      cookies: this.options.cookies,
      maxRetries: 2,
    };
  }

  private assertNotDisposed(): void {
    if (this.disposed) {
      throw new ExtensionRuntimeError('DISPOSED', 'The extension runtime has been disposed');
    }
  }
}

/* -------------------------------------------------------------------------- */
/*                              vm module access                              */
/* -------------------------------------------------------------------------- */

interface NodeVmModule {
  createContext(sandbox: Record<string, unknown>, options?: Record<string, unknown>): unknown;
  runInContext(code: string, context: unknown, options?: Record<string, unknown>): unknown;
}

function loadNodeVm(): NodeVmModule | null {
  const globalRecord = globalThis as unknown as {
    process?: { getBuiltinModule?: (id: string) => unknown };
    require?: (id: string) => unknown;
  };

  try {
    if (typeof globalRecord.process?.getBuiltinModule === 'function') {
      const mod = globalRecord.process.getBuiltinModule('node:vm');
      if (mod) return mod as NodeVmModule;
    }
  } catch {
    // fall through
  }

  try {
    if (typeof globalRecord.require === 'function') {
      return globalRecord.require('node:vm') as NodeVmModule;
    }
  } catch {
    // fall through
  }

  return null;
}

function buildExtensionWrapper(sourceCode: string): string {
  return `(function () {
"use strict";
var module = { exports: {} };
var exports = module.exports;
var __yomiraExport = null;
var defineExtension = function (extension) { __yomiraExport = extension; return extension; };
${sourceCode}
var __value = __yomiraExport;
if (!__value && module.exports && (typeof module.exports === 'object' || typeof module.exports === 'function')) {
  __value = module.exports;
}
var __installed = globalThis.__yomiraInstall(__value, null);
if (__installed && typeof __installed.then === 'function') {
  return __installed.then(function (result) { return JSON.stringify(result); });
}
return JSON.stringify(__installed);
})()`;
}

/* -------------------------------------------------------------------------- */
/*                            Normalization helpers                           */
/* -------------------------------------------------------------------------- */

const SERIES_TYPES: SeriesType[] = ['MANGA', 'MANHWA', 'MANHUA', 'COMIC', 'WEBTOON'];
const SERIES_STATUSES: SeriesStatus[] = ['ONGOING', 'COMPLETED', 'HIATUS', 'CANCELLED'];
const CONTENT_RATINGS: ContentRating[] = ['SAFE', 'MATURE_ECCHI', 'PORNOGRAPHIC_18'];

function normalizeManifest(raw: unknown): ExtensionManifest {
  const record = asRecord(raw);
  const baseUrl = asString(record.baseUrl) ?? '';
  const isNsfw =
    typeof record.isNsfw === 'boolean'
      ? record.isNsfw
      : typeof record.isAdult === 'boolean'
        ? record.isAdult
        : false;

  try {
    new URL(baseUrl);
  } catch {
    // validation reports the error
  }

  return {
    id: asString(record.id) ?? '',
    name: asString(record.name) ?? '',
    version: asString(record.version) ?? '',
    iconUrl: asString(record.iconUrl) ?? '',
    author: asString(record.author) ?? 'Unknown',
    description: typeof record.description === 'string' ? record.description : '',
    baseUrl,
    supportedLanguages:
      Array.isArray(record.supportedLanguages) && record.supportedLanguages.length > 0
        ? record.supportedLanguages.filter((lang): lang is string => typeof lang === 'string')
        : ['en'],
    isNsfw,
    repoUrl: asString(record.repoUrl) ?? '',
  };
}

function validateManifest(manifest: ExtensionManifest): string[] {
  const errors: string[] = [];

  if (!manifest.id || !/^[a-zA-Z0-9][a-zA-Z0-9._:-]{1,127}$/.test(manifest.id)) {
    errors.push('id must match /^[a-zA-Z0-9][a-zA-Z0-9._:-]{1,127}$/');
  }
  if (!manifest.name) errors.push('name is required');
  if (!manifest.version || !/^\d+\.\d+\.\d+(?:[-+][0-9a-zA-Z.-]+)?$/.test(manifest.version)) {
    errors.push('version must be semver-like (e.g. 1.0.0)');
  }
  if (!manifest.baseUrl) {
    errors.push('baseUrl is required');
  } else {
    try {
      const url = new URL(manifest.baseUrl);
      if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        errors.push('baseUrl must use http(s)');
      }
    } catch {
      errors.push('baseUrl is not a valid URL');
    }
  }
  if (!Array.isArray(manifest.supportedLanguages) || manifest.supportedLanguages.length === 0) {
    errors.push('supportedLanguages must contain at least one language');
  }
  if (manifest.iconUrl) {
    try {
      new URL(manifest.iconUrl);
    } catch {
      errors.push('iconUrl is not a valid URL');
    }
  }
  return errors;
}

function allowedHostsFor(manifest: ExtensionManifest, extra: string[]): string[] {
  const hosts = new Set<string>(extra.map((host) => host.toLowerCase()));
  try {
    const url = new URL(manifest.baseUrl);
    hosts.add(url.hostname.toLowerCase());
    if (url.hostname.startsWith('www.')) hosts.add(url.hostname.slice(4));
    else hosts.add(`www.${url.hostname}`);
  } catch {
    // validation already reported it
  }
  return Array.from(hosts);
}

function assertArray(value: unknown, method: string): unknown[] {
  if (!Array.isArray(value)) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Extension method '${method}' must return an array (got ${typeof value})`
    );
  }
  if (value.length > MAX_RESULT_ITEMS) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Extension method '${method}' returned ${value.length} items; limit is ${MAX_RESULT_ITEMS}`
    );
  }
  return value;
}

function sanitizePartialSeries(raw: unknown, manifest: ExtensionManifest): Partial<Series> {
  const record = asRecord(raw);
  const out: Partial<Series> = {};

  const title = asString(record.canonicalTitle) ?? asString(record.title);
  if (title) out.canonicalTitle = title;

  const id = asString(record.id) ?? asString(record.externalId) ?? asString(record.slug);
  if (id) out.id = id;

  const originalTitle = asString(record.originalTitle);
  if (originalTitle) out.originalTitle = originalTitle;

  const aliases = asStringArray(record.aliases);
  if (aliases.length > 0) out.aliases = aliases;

  if (typeof record.description === 'string') out.description = record.description;

  const coverUrl = asHttpUrl(record.coverUrl);
  if (coverUrl) out.coverUrl = coverUrl;

  const bannerUrl = asHttpUrl(record.bannerUrl);
  if (bannerUrl) out.bannerUrl = bannerUrl;

  const type = asEnum(record.type, SERIES_TYPES);
  if (type) out.type = type;

  const status = asEnum(record.status, SERIES_STATUSES);
  if (status) out.status = status;

  const contentRating = asEnum(record.contentRating, CONTENT_RATINGS);
  if (contentRating) out.contentRating = contentRating;

  if (typeof record.isAdult === 'boolean') {
    out.isAdult = record.isAdult;
  } else if (contentRating === 'PORNOGRAPHIC_18') {
    out.isAdult = true;
  }

  const genres = asStringArray(record.genres);
  if (genres.length > 0) out.genres = genres;

  const tags = asStringArray(record.tags);
  if (tags.length > 0) out.tags = tags;

  const author = asString(record.author);
  if (author) out.author = author;

  const artist = asString(record.artist);
  if (artist) out.artist = artist;

  const year = asNumber(record.year);
  if (year !== undefined && year >= 1900 && year <= 2200) out.year = Math.trunc(year);

  const rating = asNumber(record.rating);
  if (rating !== undefined) out.rating = clampNumber(rating, 0, 5);

  const totalChapters = asNumber(record.totalChapters);
  if (totalChapters !== undefined && totalChapters >= 0) out.totalChapters = totalChapters;

  const sources = sanitizeMetadataSources(record.metadataSources, manifest);
  if (sources.length > 0) out.metadataSources = sources;

  const createdAt = asNumber(record.createdAt);
  if (createdAt !== undefined) out.createdAt = createdAt;

  const updatedAt = asNumber(record.updatedAt);
  if (updatedAt !== undefined) out.updatedAt = updatedAt;

  return out;
}

function sanitizeSeries(raw: unknown, manifest: ExtensionManifest, index: number): Series {
  const partial = sanitizePartialSeries(raw, manifest);
  const title = partial.canonicalTitle;
  if (!title) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Series at index ${index} has no canonicalTitle`
    );
  }

  const id = partial.id ?? `${manifest.id}:${slugify(title)}:${index}`;
  const contentRating: ContentRating = partial.contentRating ?? 'SAFE';
  const aliases = (partial.aliases ?? []).filter((alias) => alias !== title);
  const metadataSources: SeriesMetadataMapping[] =
    partial.metadataSources && partial.metadataSources.length > 0
      ? partial.metadataSources
      : [
          {
            providerId: manifest.id,
            externalId: id,
            sourceUrl: manifest.baseUrl,
            lastSyncedAt: Date.now(),
          },
        ];

  return {
    id,
    canonicalTitle: title,
    originalTitle: partial.originalTitle,
    aliases: Array.from(new Set(aliases)),
    description: partial.description ?? '',
    coverUrl: partial.coverUrl ?? '',
    bannerUrl: partial.bannerUrl,
    type: partial.type ?? 'COMIC',
    status: partial.status ?? 'ONGOING',
    contentRating,
    isAdult: contentRating === 'PORNOGRAPHIC_18',
    genres: partial.genres ?? [],
    tags: partial.tags ?? [],
    author: partial.author,
    artist: partial.artist,
    year: partial.year,
    rating: partial.rating,
    totalChapters: partial.totalChapters,
    metadataSources,
    createdAt: partial.createdAt ?? Date.now(),
    updatedAt: partial.updatedAt ?? Date.now(),
  };
}

function sanitizeChapter(
  raw: unknown,
  manifest: ExtensionManifest,
  externalId: string,
  index: number
): Chapter {
  const record = asRecord(raw);
  const chapterNumber =
    asNumber(record.chapterNumber) ?? asNumber(record.number) ?? asNumber(record.chapter) ?? index + 1;
  if (!Number.isFinite(chapterNumber)) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Chapter at index ${index} has an invalid chapterNumber`
    );
  }

  const id =
    asString(record.id) ?? asString(record.chapterId) ?? `${externalId}:chapter:${chapterNumber}`;

  const chapter: Chapter = {
    id,
    seriesId: asString(record.seriesId) ?? externalId,
    chapterNumber,
    language: asString(record.language) ?? manifest.supportedLanguages[0] ?? 'en',
    extensionId: asString(record.extensionId) ?? manifest.id,
  };

  const volumeNumber = asNumber(record.volumeNumber);
  if (volumeNumber !== undefined) chapter.volumeNumber = volumeNumber;

  const title = asString(record.title);
  if (title) chapter.title = title;

  const releaseDate = asString(record.releaseDate);
  if (releaseDate) chapter.releaseDate = releaseDate;

  const group = asString(record.scanlationGroup);
  if (group) chapter.scanlationGroup = group;

  const pageCount = asNumber(record.pageCount);
  if (pageCount !== undefined && pageCount >= 0) chapter.pageCount = Math.trunc(pageCount);

  const sourceUrl = asHttpUrl(record.sourceUrl);
  if (sourceUrl) chapter.sourceUrl = sourceUrl;

  return chapter;
}

function sanitizePage(raw: unknown, index: number, manifest: ExtensionManifest): Page | null {
  const record = asRecord(raw);
  const imageUrl = asHttpUrl(record.imageUrl) ?? asHttpUrl(record.url) ?? asHttpUrl(record.src);
  if (!imageUrl) return null;

  const headers: Record<string, string> = {
    Referer: manifest.baseUrl,
    'User-Agent': DEFAULT_PAGE_USER_AGENT,
  };

  const rawHeaders = asRecord(record.headers);
  let count = 0;
  for (const [key, value] of Object.entries(rawHeaders)) {
    if (count >= 20) break;
    if (!/^[a-zA-Z0-9-]{1,64}$/.test(key)) continue;
    if (typeof value !== 'string') continue;
    headers[key] = value.replace(/[\r\n]+/g, ' ').slice(0, 2048);
    count += 1;
  }

  const fallbackUrls = asStringArray(record.fallbackUrls)
    .map((url) => asHttpUrl(url))
    .filter((url): url is string => Boolean(url));

  const pageIndex = asNumber(record.index);
  const page: Page = {
    index: pageIndex !== undefined && pageIndex >= 0 ? Math.trunc(pageIndex) : index,
    imageUrl,
    headers,
  };
  if (fallbackUrls.length > 0) page.fallbackUrls = fallbackUrls;

  return page;
}

const DEFAULT_PAGE_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 YomiraReader/1.0';

function sanitizeMetadataSources(raw: unknown, manifest: ExtensionManifest): SeriesMetadataMapping[] {
  if (!Array.isArray(raw)) return [];
  const out: SeriesMetadataMapping[] = [];
  for (const item of raw.slice(0, 10)) {
    const record = asRecord(item);
    const providerId = asString(record.providerId) ?? manifest.id;
    const externalId = asString(record.externalId);
    if (!externalId) continue;
    out.push({
      providerId,
      externalId,
      sourceUrl: asHttpUrl(record.sourceUrl),
      lastSyncedAt: asNumber(record.lastSyncedAt) ?? Date.now(),
    });
  }
  return out;
}

function cloneResult(
  value: unknown,
  maxBytes: number,
  extensionId: string,
  method: string
): unknown {
  if (value === undefined || value === null) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Extension '${extensionId}'.${method} returned no data`
    );
  }

  let serialized: string | undefined;
  try {
    serialized = JSON.stringify(value, (_key, item: unknown) => {
      if (typeof item === 'function' || typeof item === 'symbol' || typeof item === 'bigint') {
        return undefined;
      }
      return item;
    });
  } catch (error) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Extension '${extensionId}'.${method} returned a non-serializable result: ${errorMessage(error)}`,
      error
    );
  }

  if (serialized === undefined) {
    throw new ExtensionRuntimeError(
      'RESULT_INVALID',
      `Extension '${extensionId}'.${method} returned an unsupported value`
    );
  }
  if (serialized.length > maxBytes) {
    throw new ExtensionRuntimeError(
      'RESULT_TOO_LARGE',
      `Extension '${extensionId}'.${method} returned ${serialized.length} bytes; limit is ${maxBytes}`
    );
  }

  return JSON.parse(serialized) as unknown;
}

/* -------------------------------------------------------------------------- */
/*                                  Utilities                                 */
/* -------------------------------------------------------------------------- */

function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs: number,
  message: string,
  code: ExtensionRuntimeErrorCode = 'EXECUTION_TIMEOUT'
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const timer = setTimeout(() => {
      reject(new ExtensionRuntimeError(code, message));
    }, timeoutMs);
    promise.then(
      (value) => {
        clearTimeout(timer);
        resolve(value);
      },
      (error) => {
        clearTimeout(timer);
        reject(error);
      }
    );
  });
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function deepFreeze<T>(value: T): T {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.freeze(value);
    for (const key of Object.keys(value as Record<string, unknown>)) {
      deepFreeze((value as Record<string, unknown>)[key]);
    }
  }
  return value;
}

function asRecord(value: unknown): Record<string, unknown> {
  if (value === null || typeof value !== 'object' || Array.isArray(value)) return {};
  return value as Record<string, unknown>;
}

function asString(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined;
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : undefined;
}

function asNumber(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) return value;
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value.replace(',', '.'));
    return Number.isFinite(parsed) ? parsed : undefined;
  }
  return undefined;
}

function asStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is string => typeof item === 'string' && item.trim().length > 0)
    .map((item) => item.trim())
    .slice(0, 50);
}

function asEnum<T extends string>(value: unknown, allowed: readonly T[]): T | undefined {
  if (typeof value !== 'string') return undefined;
  const normalized = value.trim().toUpperCase();
  return allowed.find((candidate) => candidate === normalized);
}

function asHttpUrl(value: unknown): string | undefined {
  const raw = asString(value);
  if (!raw) return undefined;
  try {
    const url = new URL(raw);
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return undefined;
    return url.toString();
  } catch {
    return undefined;
  }
}

function clampNumber(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
}

function sanitizeHeaders(raw: Record<string, unknown>): Record<string, string> {
  const headers: Record<string, string> = {};
  let count = 0;
  for (const [key, value] of Object.entries(raw)) {
    if (count >= 30) break;
    if (!/^[a-zA-Z0-9-]{1,64}$/.test(key)) continue;
    if (typeof value !== 'string' && typeof value !== 'number' && typeof value !== 'boolean') continue;
    headers[key] = String(value).replace(/[\r\n]+/g, ' ').slice(0, 2048);
    count += 1;
  }
  return headers;
}

function utf8ByteLength(value: string): number {
  if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(value).length;
  return value.length * 2;
}

function fnv1a(value: string): string {
  let hash = 0x811c9dc5;
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }
  return (hash >>> 0).toString(16).padStart(8, '0');
}

function errorMessage(error: unknown): string {
  if (error instanceof Error && typeof error.message === 'string') return error.message;
  if (typeof error === 'string') return error;
  try {
    return JSON.stringify(error);
  } catch {
    return String(error);
  }
}

function errorCode(error: unknown): string {
  if (error instanceof ExtensionRuntimeError) return error.code;
  if (error instanceof ExtensionHttpError) return error.code;
  const record = asRecord(error);
  return typeof record.code === 'string' ? record.code : 'REQUEST_FAILED';
}

function errorStatus(error: unknown): number | undefined {
  if (error instanceof ExtensionHttpError) return error.status;
  const record = asRecord(error);
  return typeof record.status === 'number' ? record.status : undefined;
}

function isAntiBotError(error: unknown): boolean {
  if (error instanceof ExtensionHttpError) return error.code === 'ANTI_BOT';
  const record = asRecord(error);
  return record.code === 'ANTI_BOT';
}

function isThenable(value: unknown): value is PromiseLike<unknown> {
  return (
    value !== null &&
    (typeof value === 'object' || typeof value === 'function') &&
    typeof (value as { then?: unknown }).then === 'function'
  );
}

function safeStringify(value: unknown): string {
  try {
    return JSON.stringify(value) ?? String(value);
  } catch {
    return String(value);
  }
}

function errorPayload(message: string, code: string, status?: number): string {
  return JSON.stringify({ ok: false, error: { message, code, status } });
}
