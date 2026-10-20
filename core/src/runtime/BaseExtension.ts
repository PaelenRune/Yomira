/**
 * Yomira Extension Runtime - Base Extension & HTTP Client
 *
 * Community extensions (comic/manga site scrapers) are plain objects, but the
 * first-party extensions shipped with Yomira (and anything written in
 * TypeScript) can extend `BaseExtension`. It provides:
 *   - A hardened HTTP client (browser-like headers, cookie jar, retries,
 *     Cloudflare / anti-bot detection, SSRF guards, response size limits).
 *   - Pure HTML/URL parsing helpers with no external dependencies.
 *   - Convenience builders for `Series`, `Chapter` and `Page` results.
 */

import {
  Chapter,
  ContentRating,
  ExtensionManifest,
  ExtensionSource,
  Page,
  Series,
  SeriesStatus,
  SeriesType,
} from '../types/index.js';

/* -------------------------------------------------------------------------- */
/*                                  Errors                                    */
/* -------------------------------------------------------------------------- */

export type ExtensionHttpErrorCode =
  | 'INVALID_URL'
  | 'BLOCKED_HOST'
  | 'NETWORK'
  | 'TIMEOUT'
  | 'HTTP_STATUS'
  | 'ANTI_BOT'
  | 'RESPONSE_TOO_LARGE'
  | 'ABORTED';

export interface ExtensionHttpErrorDetails {
  status?: number;
  url?: string;
  cause?: unknown;
}

export class ExtensionHttpError extends Error {
  readonly code: ExtensionHttpErrorCode;
  readonly status?: number;
  readonly url?: string;

  constructor(code: ExtensionHttpErrorCode, message: string, details: ExtensionHttpErrorDetails = {}) {
    super(message);
    this.name = 'ExtensionHttpError';
    this.code = code;
    this.status = details.status;
    this.url = details.url;
    if (details.cause !== undefined) {
      (this as { cause?: unknown }).cause = details.cause;
    }
  }
}

/* -------------------------------------------------------------------------- */
/*                                HTTP Client                                 */
/* -------------------------------------------------------------------------- */

export interface HttpClientOptions {
  /** Site root; relative requests resolve against it. */
  baseUrl?: string;
  userAgent?: string;
  /** Default `Referer` sent with every request (important for hotlink protection). */
  referer?: string;
  acceptLanguage?: string;
  timeoutMs?: number;
  maxRetries?: number;
  retryDelayMs?: number;
  maxResponseBytes?: number;
  /** Additional hosts the extension may talk to (CDNs, image proxies, ...). */
  allowedHosts?: string[];
  /** Allow localhost / RFC1918 hosts. Never enable in production. */
  allowPrivateHosts?: boolean;
  maxRedirects?: number;
  /** Persist cookies between requests (Cloudflare clearance, session cookies). */
  cookies?: boolean;
}

export interface ExtensionRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD';
  headers?: Record<string, string>;
  body?: string | Uint8Array;
  timeoutMs?: number;
  retries?: number;
  referer?: string;
}

export const DEFAULT_USER_AGENT =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 YomiraReader/1.0';

const DEFAULT_ACCEPT =
  'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8';

const RETRYABLE_STATUS = new Set([408, 425, 429, 500, 502, 503, 504]);

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export class ExtensionHttpResponse {
  constructor(
    readonly status: number,
    readonly url: string,
    readonly headers: Record<string, string>,
    readonly text: string,
    readonly bytes: Uint8Array
  ) {}

  get ok(): boolean {
    return this.status >= 200 && this.status < 300;
  }

  get statusText(): string {
    return this.ok ? 'OK' : `HTTP ${this.status}`;
  }

  json<T = unknown>(): T {
    return JSON.parse(this.text) as T;
  }

  /** Decoded image bytes (useful for data-URI pages or hash checks). */
  get imageBytes(): Uint8Array {
    return this.bytes;
  }
}

/**
 * Hardened HTTP client used by extensions.
 *
 * Security notes:
 *  - only http(s) is allowed;
 *  - loopback / private networks are rejected unless explicitly enabled;
 *  - hosts can be allow-listed (manifest host is always allowed);
 *  - redirects are followed manually and re-validated on every hop;
 *  - responses are capped in size;
 *  - Cloudflare challenge pages surface as `ANTI_BOT` instead of empty data.
 */
export class ExtensionHttpClient {
  private readonly baseUrl?: string;
  private readonly userAgent: string;
  private readonly acceptLanguage: string;
  private readonly timeoutMs: number;
  private readonly maxRetries: number;
  private readonly retryDelayMs: number;
  private readonly maxResponseBytes: number;
  private readonly allowPrivateHosts: boolean;
  private readonly maxRedirects: number;
  private readonly useCookies: boolean;
  private readonly allowedHosts: Set<string>;
  private defaultReferer?: string;
  private readonly cookieJar = new Map<string, Map<string, string>>();
  private readonly hostNextSlot = new Map<string, number>();

  constructor(options: HttpClientOptions = {}) {
    this.baseUrl = options.baseUrl;
    this.userAgent = options.userAgent ?? DEFAULT_USER_AGENT;
    this.defaultReferer = options.referer ?? options.baseUrl;
    this.acceptLanguage = options.acceptLanguage ?? 'en-US,en;q=0.9,tr;q=0.8';
    this.timeoutMs = options.timeoutMs ?? 20_000;
    this.maxRetries = options.maxRetries ?? 2;
    this.retryDelayMs = options.retryDelayMs ?? 600;
    this.maxResponseBytes = options.maxResponseBytes ?? 8 * 1024 * 1024;
    this.allowPrivateHosts = options.allowPrivateHosts ?? false;
    this.maxRedirects = options.maxRedirects ?? 5;
    this.useCookies = options.cookies ?? true;

    this.allowedHosts = new Set();
    for (const host of options.allowedHosts ?? []) {
      this.allowedHosts.add(host.toLowerCase());
    }
    if (this.baseUrl) {
      const parsed = safeUrl(this.baseUrl);
      if (parsed?.hostname) {
        this.allowedHosts.add(parsed.hostname.toLowerCase());
        if (parsed.hostname.startsWith('www.')) {
          this.allowedHosts.add(parsed.hostname.slice(4));
        } else {
          this.allowedHosts.add(`www.${parsed.hostname}`);
        }
      }
    }
  }

  /** Override the default `Referer` (e.g. before fetching chapter images). */
  setReferer(referer: string | undefined): void {
    this.defaultReferer = referer;
  }

  getReferer(): string | undefined {
    return this.defaultReferer;
  }

  /** Resolve a path/relative URL against the base URL. */
  resolve(pathOrUrl: string): URL {
    const base = this.baseUrl;
    try {
      return base ? new URL(pathOrUrl, base) : new URL(pathOrUrl);
    } catch {
      throw new ExtensionHttpError(
        'INVALID_URL',
        `Invalid URL: '${pathOrUrl}'${base ? ` (base: ${base})` : ''}`
      );
    }
  }

  async request(pathOrUrl: string, options: ExtensionRequestOptions = {}): Promise<ExtensionHttpResponse> {
    const firstUrl = this.resolve(pathOrUrl);
    this.assertAllowed(firstUrl);

    const retries = Math.max(0, options.retries ?? this.maxRetries);
    let attempt = 0;
    let lastError: unknown;

    while (attempt <= retries) {
      try {
        return await this.executeWithRedirects(firstUrl, options);
      } catch (error) {
        lastError = error;
        if (!this.isRetryable(error) || attempt === retries) {
          throw error;
        }
        const backoff = this.retryDelayMs * Math.pow(2, attempt);
        await delay(backoff);
        attempt += 1;
      }
    }

    throw lastError instanceof Error
      ? lastError
      : new ExtensionHttpError('NETWORK', 'Request failed', { cause: lastError });
  }

  async getText(pathOrUrl: string, options: ExtensionRequestOptions = {}): Promise<string> {
    return (await this.request(pathOrUrl, options)).text;
  }

  async getJson<T = unknown>(pathOrUrl: string, options: ExtensionRequestOptions = {}): Promise<T> {
    const response = await this.request(pathOrUrl, {
      ...options,
      headers: { Accept: 'application/json, text/plain, */*', ...options.headers },
    });
    try {
      return response.json<T>();
    } catch (error) {
      throw new ExtensionHttpError('HTTP_STATUS', `Invalid JSON response from ${response.url}`, {
        status: response.status,
        url: response.url,
        cause: error,
      });
    }
  }

  /** Copy of the current cookie header for a host (diagnostics / page headers). */
  getCookieHeader(hostname: string): string | undefined {
    const jar = this.cookieJar.get(hostname.toLowerCase());
    if (!jar || jar.size === 0) return undefined;
    return Array.from(jar.entries())
      .map(([name, value]) => `${name}=${value}`)
      .join('; ');
  }

  clearCookies(): void {
    this.cookieJar.clear();
  }

  /* ------------------------------ internals ------------------------------ */

  private async executeWithRedirects(
    startUrl: URL,
    options: ExtensionRequestOptions
  ): Promise<ExtensionHttpResponse> {
    let url = startUrl;
    let redirects = 0;

    for (;;) {
      const response = await this.executeSingle(url, options);
      if (![301, 302, 303, 307, 308].includes(response.status)) {
        return response;
      }

      const location = response.headers['location'];
      if (!location) return response;
      if (redirects >= this.maxRedirects) {
        throw new ExtensionHttpError('HTTP_STATUS', `Too many redirects (${this.maxRedirects})`, {
          status: response.status,
          url: url.toString(),
        });
      }

      let next: URL;
      try {
        next = new URL(location, url);
      } catch {
        throw new ExtensionHttpError('INVALID_URL', `Invalid redirect target: '${location}'`, {
          url: url.toString(),
        });
      }
      this.assertAllowed(next);
      redirects += 1;
      url = next;

      // 303 (and commonly 301/302 for POST) downgrade to GET.
      if (response.status === 303 || ((response.status === 301 || response.status === 302) && options.method === 'POST')) {
        options = { ...options, method: 'GET', body: undefined };
      }
    }
  }

  private async executeSingle(url: URL, options: ExtensionRequestOptions): Promise<ExtensionHttpResponse> {
    const host = url.hostname.toLowerCase();
    await this.enforceHostRateLimit(host);

    const headers = this.buildHeaders(url, options);
    const timeoutMs = options.timeoutMs ?? this.timeoutMs;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);

    let response: Response;
    try {
      response = await fetch(url.toString(), {
        method: options.method ?? 'GET',
        headers,
        body: options.body as BodyInit | undefined,
        redirect: 'manual',
        signal: controller.signal,
      });
    } catch (error) {
      if (controller.signal.aborted) {
        throw new ExtensionHttpError('TIMEOUT', `Request timed out after ${timeoutMs}ms: ${url.toString()}`, {
          url: url.toString(),
          cause: error,
        });
      }
      throw new ExtensionHttpError('NETWORK', `Network request failed: ${url.toString()}`, {
        url: url.toString(),
        cause: error,
      });
    } finally {
      clearTimeout(timer);
    }

    const responseHeaders: Record<string, string> = {};
    response.headers.forEach((value, key) => {
      responseHeaders[key.toLowerCase()] = value;
    });

    this.storeCookies(url, response);

    if ([301, 302, 303, 307, 308].includes(response.status)) {
      // Body is not needed and may be large; drain a small amount only.
      return new ExtensionHttpResponse(response.status, url.toString(), responseHeaders, '', new Uint8Array());
    }

    const bytes = await this.readBodyWithLimit(response, url, controller);
    const text = decodeBytes(bytes, responseHeaders['content-type']);

    this.detectAntiBot(response.status, responseHeaders, text, url);

    if (!response.ok) {
      throw new ExtensionHttpError('HTTP_STATUS', `HTTP ${response.status} from ${url.toString()}`, {
        status: response.status,
        url: url.toString(),
      });
    }

    return new ExtensionHttpResponse(response.status, url.toString(), responseHeaders, text, bytes);
  }

  private buildHeaders(
    url: URL,
    options: ExtensionRequestOptions,
    referer?: string
  ): Record<string, string> {
    const headers: Record<string, string> = {
      'User-Agent': this.userAgent,
      Accept: DEFAULT_ACCEPT,
      'Accept-Language': this.acceptLanguage,
      'Accept-Encoding': 'gzip, deflate, br',
      'Upgrade-Insecure-Requests': '1',
      'Sec-Fetch-Dest': 'document',
      'Sec-Fetch-Mode': 'navigate',
      'Sec-Fetch-Site': 'same-origin',
    };

    const effectiveReferer = options.referer ?? referer ?? this.defaultReferer;
    if (effectiveReferer) headers['Referer'] = effectiveReferer;

    const cookie = this.useCookies ? this.getCookieHeader(url.hostname) : undefined;
    if (cookie) headers['Cookie'] = cookie;

    for (const [key, value] of Object.entries(options.headers ?? {})) {
      if (typeof value !== 'string') continue;
      // Strip CR/LF to prevent header injection from extension code.
      headers[key] = value.replace(/[\r\n]+/g, ' ').slice(0, 2048);
    }

    return headers;
  }

  private storeCookies(url: URL, response: Response): void {
    if (!this.useCookies) return;
    const raw = getSetCookieHeaders(response);
    if (raw.length === 0) return;

    const host = url.hostname.toLowerCase();
    let jar = this.cookieJar.get(host);
    if (!jar) {
      jar = new Map();
      this.cookieJar.set(host, jar);
    }

    for (const cookie of raw) {
      const pair = cookie.split(';', 1)[0] ?? '';
      const separator = pair.indexOf('=');
      if (separator <= 0) continue;
      const name = pair.slice(0, separator).trim();
      const value = pair.slice(separator + 1).trim();
      if (!name) continue;
      if (/max-age=0|deleted/i.test(cookie)) {
        jar.delete(name);
      } else {
        jar.set(name, value);
      }
    }
  }

  private async readBodyWithLimit(
    response: Response,
    url: URL,
    controller: AbortController
  ): Promise<Uint8Array> {
    const declaredLength = Number(response.headers.get('content-length') ?? '0');
    if (declaredLength > this.maxResponseBytes) {
      controller.abort();
      throw new ExtensionHttpError(
        'RESPONSE_TOO_LARGE',
        `Response exceeds ${this.maxResponseBytes} bytes: ${url.toString()}`,
        { url: url.toString(), status: response.status }
      );
    }

    if (!response.body) {
      return new Uint8Array(await response.arrayBuffer());
    }

    const reader = response.body.getReader();
    const chunks: Uint8Array[] = [];
    let total = 0;

    try {
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        if (!value) continue;
        total += value.byteLength;
        if (total > this.maxResponseBytes) {
          await reader.cancel();
          controller.abort();
          throw new ExtensionHttpError(
            'RESPONSE_TOO_LARGE',
            `Response exceeds ${this.maxResponseBytes} bytes: ${url.toString()}`,
            { url: url.toString(), status: response.status }
          );
        }
        chunks.push(value);
      }
    } finally {
      reader.releaseLock();
    }

    const merged = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
      merged.set(chunk, offset);
      offset += chunk.byteLength;
    }
    return merged;
  }

  private detectAntiBot(status: number, headers: Record<string, string>, text: string, url: URL): void {
    const mitigated = headers['cf-mitigated'];
    const server = (headers['server'] ?? '').toLowerCase();
    const sample = text.slice(0, 6000);
    const challenge =
      /just a moment|cf-chl|challenge-platform|cf-browser-verification|attention required|enable javascript and cookies/i.test(
        sample
      );
    const cloudflare = mitigated !== undefined || server.includes('cloudflare') || /cloudflare/i.test(sample);

    if ((status === 403 || status === 503) && challenge && cloudflare) {
      throw new ExtensionHttpError(
        'ANTI_BOT',
        `Anti-bot challenge (Cloudflare) blocked ${url.toString()}. Supply a valid User-Agent/Referer or browser cookies.`,
        { status, url: url.toString() }
      );
    }

    if (status === 429) {
      throw new ExtensionHttpError('HTTP_STATUS', `Rate limited (HTTP 429) by ${url.hostname}`, {
        status,
        url: url.toString(),
      });
    }
  }

  private isRetryable(error: unknown): boolean {
    if (!(error instanceof ExtensionHttpError)) return false;
    if (error.code === 'NETWORK' || error.code === 'TIMEOUT') return true;
    return error.code === 'HTTP_STATUS' && error.status !== undefined && RETRYABLE_STATUS.has(error.status);
  }

  private async enforceHostRateLimit(host: string): Promise<void> {
    const now = Date.now();
    const nextSlot = this.hostNextSlot.get(host) ?? 0;
    if (now < nextSlot) {
      await delay(nextSlot - now);
    }
    this.hostNextSlot.set(host, Math.max(now, nextSlot) + 200);
  }

  private assertAllowed(url: URL): void {
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
      throw new ExtensionHttpError('BLOCKED_HOST', `Only http(s) URLs are allowed (got '${url.protocol}')`, {
        url: url.toString(),
      });
    }

    if (url.username || url.password) {
      throw new ExtensionHttpError('BLOCKED_HOST', 'URLs with embedded credentials are not allowed', {
        url: url.toString(),
      });
    }

    const hostname = url.hostname.toLowerCase();
    if (!hostname) {
      throw new ExtensionHttpError('INVALID_URL', 'URL has no hostname', { url: url.toString() });
    }

    if (!this.allowPrivateHosts && isPrivateHost(hostname)) {
      throw new ExtensionHttpError(
        'BLOCKED_HOST',
        `Requests to private/loopback host '${hostname}' are blocked`,
        { url: url.toString() }
      );
    }

    if (this.allowedHosts.size > 0 && !this.isHostAllowed(hostname)) {
      throw new ExtensionHttpError(
        'BLOCKED_HOST',
        `Host '${hostname}' is not allow-listed for this extension`,
        { url: url.toString() }
      );
    }
  }

  private isHostAllowed(hostname: string): boolean {
    const host = hostname.toLowerCase();
    for (const allowed of this.allowedHosts) {
      if (host === allowed || host.endsWith(`.${allowed}`)) return true;
    }
    return false;
  }
}

function safeUrl(value: string): URL | null {
  try {
    return new URL(value);
  } catch {
    return null;
  }
}

function getSetCookieHeaders(response: Response): string[] {
  const headers = response.headers as Headers & { getSetCookie?: () => string[] };
  if (typeof headers.getSetCookie === 'function') {
    return headers.getSetCookie();
  }
  const single = response.headers.get('set-cookie');
  return single ? single.split(/,(?=\s*[^;=,\s]+=)/) : [];
}

function decodeBytes(bytes: Uint8Array, contentType?: string): string {
  let charset = 'utf-8';
  const match = /charset\s*=\s*"?([^;"\s]+)"?/i.exec(contentType ?? '');
  if (match?.[1]) charset = match[1].toLowerCase();

  try {
    return new TextDecoder(charset).decode(bytes);
  } catch {
    return new TextDecoder('utf-8').decode(bytes);
  }
}

function isPrivateHost(hostname: string): boolean {
  const host = hostname.toLowerCase().replace(/^\[|\]$/g, '');

  if (host === 'localhost' || host.endsWith('.localhost') || host.endsWith('.local')) return true;
  if (host === '::1' || host === '::' || host === '0.0.0.0') return true;
  if (host.startsWith('fc') || host.startsWith('fd') || host.startsWith('fe80')) return true;

  const ipv4 = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(host);
  if (!ipv4) return false;

  const [a, b] = [Number(ipv4[1]), Number(ipv4[2])];
  if (a === 0 || a === 10 || a === 127) return true;
  if (a === 169 && b === 254) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  if (a === 100 && b >= 64 && b <= 127) return true;
  return false;
}

/* -------------------------------------------------------------------------- */
/*                            Parsing / HTML utils                            */
/* -------------------------------------------------------------------------- */

const NAMED_ENTITIES: Record<string, string> = {
  amp: '&',
  lt: '<',
  gt: '>',
  quot: '"',
  apos: "'",
  nbsp: ' ',
  ndash: '-',
  mdash: '-',
  hellip: '...',
  rsquo: "'",
  lsquo: "'",
  rdquo: '"',
  ldquo: '"',
};

/** Dependency-free helpers shared by host extensions and sandboxed scripts. */
export const HtmlUtils = {
  decodeEntities(input: string): string {
    return String(input).replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, (full, entity: string) => {
      if (entity.startsWith('#x') || entity.startsWith('#X')) {
        const code = parseInt(entity.slice(2), 16);
        return Number.isFinite(code) ? String.fromCodePoint(code) : full;
      }
      if (entity.startsWith('#')) {
        const code = parseInt(entity.slice(1), 10);
        return Number.isFinite(code) ? String.fromCodePoint(code) : full;
      }
      return NAMED_ENTITIES[entity.toLowerCase()] ?? full;
    });
  },

  stripHtml(input: string): string {
    return HtmlUtils.decodeEntities(
      String(input)
        .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, ' ')
        .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, ' ')
        .replace(/<br\s*\/?>/gi, '\n')
        .replace(/<\/(p|div|li|h[1-6])>/gi, '\n')
        .replace(/<[^>]+>/g, ' ')
    )
      .replace(/[ \t\f\v]+/g, ' ')
      .replace(/\s*\n\s*/g, '\n')
      .trim();
  },

  getAttribute(tagHtml: string, name: string): string | undefined {
    const escaped = name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const match = new RegExp(
      `\\b${escaped}\\s*=\\s*(?:"([^"]*)"|'([^']*)'|([^\\s>]+))`,
      'i'
    ).exec(tagHtml);
    if (!match) return undefined;
    return HtmlUtils.decodeEntities(match[1] ?? match[2] ?? match[3] ?? '');
  },

  extractAll(html: string, pattern: string | RegExp): string[] {
    const source = typeof pattern === 'string' ? escapeRegExp(pattern) : pattern.source;
    const flags = typeof pattern === 'string' ? 'g' : pattern.flags.includes('g') ? pattern.flags : `${pattern.flags}g`;
    const regex = new RegExp(source, flags.replace('y', ''));
    const results: string[] = [];
    let match: RegExpExecArray | null;
    let guard = 0;
    while ((match = regex.exec(String(html))) !== null && guard < 10_000) {
      results.push(match[1] ?? match[0]);
      if (match[0] === '') regex.lastIndex += 1;
      guard += 1;
    }
    return results;
  },

  matchFirst(html: string, pattern: string | RegExp): string | undefined {
    const source = typeof pattern === 'string' ? escapeRegExp(pattern) : pattern.source;
    const flags = typeof pattern === 'string' ? '' : pattern.flags.replace(/[gy]/g, '');
    const match = new RegExp(source, flags).exec(String(html));
    return match ? match[1] ?? match[0] : undefined;
  },

  absolutize(url: string, base: string): string {
    try {
      return new URL(url, base).toString();
    } catch {
      return url;
    }
  },
};

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/* -------------------------------------------------------------------------- */
/*                              Base Extension                                */
/* -------------------------------------------------------------------------- */

export abstract class BaseExtension implements ExtensionSource {
  abstract readonly manifest: ExtensionManifest;

  /** Subclasses may widen the HTTP configuration (CDN hosts, cookies, ...). */
  protected readonly httpOptions: Partial<HttpClientOptions> = {};

  private httpClientInstance?: ExtensionHttpClient;

  /** Lazily created so subclasses can initialize `manifest` first. */
  protected get http(): ExtensionHttpClient {
    if (!this.httpClientInstance) {
      this.httpClientInstance = new ExtensionHttpClient({
        baseUrl: this.manifest.baseUrl,
        referer: this.manifest.baseUrl,
        ...this.httpOptions,
      });
    }
    return this.httpClientInstance;
  }

  abstract search(query: string, page?: number): Promise<Series[]>;
  abstract getSeriesDetails(externalId: string): Promise<Partial<Series>>;
  abstract getChapters(externalId: string): Promise<Chapter[]>;
  abstract getChapterPages(chapterId: string): Promise<Page[]>;

  /** Manifest sanity check; the runtime rejects extensions with errors. */
  validateManifest(): string[] {
    const errors: string[] = [];
    const m = this.manifest;
    if (!m) return ['manifest is missing'];
    if (!m.id || !/^[a-zA-Z0-9][a-zA-Z0-9._:-]{1,127}$/.test(m.id)) errors.push('manifest.id is invalid');
    if (!m.name) errors.push('manifest.name is required');
    if (!m.version || !/^\d+\.\d+\.\d+/.test(m.version)) errors.push('manifest.version must be semver-like');
    if (!m.baseUrl) {
      errors.push('manifest.baseUrl is required');
    } else {
      try {
        const url = new URL(m.baseUrl);
        if (url.protocol !== 'http:' && url.protocol !== 'https:') {
          errors.push('manifest.baseUrl must use http(s)');
        }
      } catch {
        errors.push('manifest.baseUrl is not a valid URL');
      }
    }
    if (!Array.isArray(m.supportedLanguages) || m.supportedLanguages.length === 0) {
      errors.push('manifest.supportedLanguages must contain at least one language');
    }
    return errors;
  }

  /* --------------------------- request helpers --------------------------- */

  protected absoluteUrl(pathOrUrl: string, base: string = this.manifest.baseUrl): string {
    return HtmlUtils.absolutize(pathOrUrl, base);
  }

  protected buildUrl(pathOrUrl: string, params?: Record<string, string | number | boolean | undefined>): string {
    const url = new URL(this.absoluteUrl(pathOrUrl));
    for (const [key, value] of Object.entries(params ?? {})) {
      if (value === undefined) continue;
      url.searchParams.set(key, String(value));
    }
    return url.toString();
  }

  protected getText(
    pathOrUrl: string,
    options: ExtensionRequestOptions = {}
  ): Promise<string> {
    return this.http.getText(pathOrUrl, {
      referer: this.manifest.baseUrl,
      ...options,
      headers: { ...this.browserHeaders(), ...options.headers },
    });
  }

  protected getJson<T = unknown>(
    pathOrUrl: string,
    options: ExtensionRequestOptions = {}
  ): Promise<T> {
    return this.http.getJson<T>(pathOrUrl, {
      referer: this.manifest.baseUrl,
      ...options,
      headers: { ...this.browserHeaders(), ...options.headers },
    });
  }

  protected async getJsonApi<T = unknown>(
    pathOrUrl: string,
    options: ExtensionRequestOptions = {}
  ): Promise<T> {
    return this.getJson<T>(pathOrUrl, {
      ...options,
      headers: { Accept: 'application/json, text/plain, */*', ...options.headers },
    });
  }

  /** Browser-like default headers for HTML requests. */
  protected browserHeaders(): Record<string, string> {
    return {
      'User-Agent': DEFAULT_USER_AGENT,
      Referer: this.manifest.baseUrl,
    };
  }

  /**
   * Headers to attach to `<img>`/reader requests. Hotlink-protected CDNs
   * require the same Referer and User-Agent used to fetch the chapter page.
   */
  protected imageHeaders(extra?: Record<string, string>): Record<string, string> {
    return {
      'User-Agent': DEFAULT_USER_AGENT,
      Referer: this.manifest.baseUrl,
      ...extra,
    };
  }

  protected createPage(index: number, imageUrl: string, extraHeaders?: Record<string, string>): Page {
    return {
      index,
      imageUrl: this.absoluteUrl(imageUrl),
      headers: this.imageHeaders(extraHeaders),
    };
  }

  protected createChapter(partial: Partial<Chapter> & { id: string; chapterNumber: number }): Chapter {
    return {
      id: partial.id,
      seriesId: partial.seriesId ?? '',
      chapterNumber: partial.chapterNumber,
      volumeNumber: partial.volumeNumber,
      title: partial.title,
      releaseDate: partial.releaseDate,
      scanlationGroup: partial.scanlationGroup,
      language: partial.language ?? this.manifest.supportedLanguages[0] ?? 'en',
      pageCount: partial.pageCount,
      sourceUrl: partial.sourceUrl,
      extensionId: partial.extensionId ?? this.manifest.id,
    };
  }

  /* --------------------------- pure utilities --------------------------- */

  protected decodeEntities(value: string): string {
    return HtmlUtils.decodeEntities(value);
  }

  protected stripHtml(value: string): string {
    return HtmlUtils.stripHtml(value);
  }

  protected getAttribute(tagHtml: string, name: string): string | undefined {
    return HtmlUtils.getAttribute(tagHtml, name);
  }

  protected extractAll(html: string, pattern: string | RegExp): string[] {
    return HtmlUtils.extractAll(html, pattern);
  }

  protected matchFirst(html: string, pattern: string | RegExp): string | undefined {
    return HtmlUtils.matchFirst(html, pattern);
  }

  /** Coerce arbitrary site labels into the canonical enums. */
  protected static normalizeType(value: unknown, fallback: SeriesType = 'COMIC'): SeriesType {
    const raw = String(value ?? '').toUpperCase();
    if (['MANGA', 'MANHWA', 'MANHUA', 'COMIC', 'WEBTOON'].includes(raw)) return raw as SeriesType;
    if (raw.includes('MANHWA')) return 'MANHWA';
    if (raw.includes('MANHUA')) return 'MANHUA';
    if (raw.includes('WEBTOON')) return 'WEBTOON';
    if (raw.includes('MANGA')) return 'MANGA';
    return fallback;
  }

  protected static normalizeStatus(value: unknown): SeriesStatus {
    const raw = String(value ?? '').toUpperCase();
    if (raw.includes('COMPLETE')) return 'COMPLETED';
    if (raw.includes('HIATUS') || raw.includes('PAUSE')) return 'HIATUS';
    if (raw.includes('CANCEL') || raw.includes('DROP')) return 'CANCELLED';
    return 'ONGOING';
  }

  protected static normalizeRating(value: unknown, isNsfw = false): ContentRating {
    const raw = String(value ?? '').toUpperCase();
    if (raw.includes('PORN') || raw.includes('HENTAI') || raw.includes('18')) return 'PORNOGRAPHIC_18';
    if (raw.includes('ECCHI') || raw.includes('MATURE') || raw.includes('SMUT')) return 'MATURE_ECCHI';
    return isNsfw ? 'MATURE_ECCHI' : 'SAFE';
  }
}
