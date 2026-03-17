const CACHE_PREFIX = 'api_cache:';

export function buildCacheKey(namespace, params = {}) {
  return `${namespace}:${stableStringify(params)}`;
}

export async function withApiCache(cacheKey, fetcher, options = {}) {
  const {
    ttl = 30 * 1000,
    storage = 'session'
  } = options;

  const fromCache = getApiCache(cacheKey, { storage });
  if (fromCache !== null) {
    return fromCache;
  }

  const freshData = await fetcher();
  setApiCache(cacheKey, freshData, { ttl, storage });
  return freshData;
}

export function getApiCache(cacheKey, options = {}) {
  const { storage = 'session' } = options;
  const fullKey = `${CACHE_PREFIX}${cacheKey}`;
  const store = getStorage(storage);
  if (!store) {
    return null;
  }

  const raw = store.getItem(fullKey);
  if (!raw) {
    return null;
  }

  try {
    const payload = JSON.parse(raw);
    if (!payload || payload.expireAt < Date.now()) {
      store.removeItem(fullKey);
      return null;
    }
    return payload.data ?? null;
  } catch (_error) {
    store.removeItem(fullKey);
    return null;
  }
}

export function setApiCache(cacheKey, data, options = {}) {
  const {
    ttl = 30 * 1000,
    storage = 'session'
  } = options;
  const store = getStorage(storage);
  if (!store) {
    return;
  }

  const fullKey = `${CACHE_PREFIX}${cacheKey}`;
  const payload = {
    expireAt: Date.now() + Math.max(0, ttl),
    data
  };
  store.setItem(fullKey, JSON.stringify(payload));
}

export function clearApiCacheByPrefix(prefix = '', options = {}) {
  const { storage = 'session' } = options;
  const store = getStorage(storage);
  if (!store) {
    return;
  }

  const targetPrefix = `${CACHE_PREFIX}${prefix}`;
  const keysToRemove = [];
  for (let index = 0; index < store.length; index += 1) {
    const key = store.key(index);
    if (key?.startsWith(targetPrefix)) {
      keysToRemove.push(key);
    }
  }

  keysToRemove.forEach((key) => store.removeItem(key));
}

function getStorage(storage) {
  if (typeof window === 'undefined') {
    return null;
  }
  if (storage === 'local') {
    return window.localStorage;
  }
  return window.sessionStorage;
}

function stableStringify(input) {
  if (input === null || input === undefined) {
    return '';
  }
  if (typeof input !== 'object') {
    return String(input);
  }
  if (Array.isArray(input)) {
    return `[${input.map((item) => stableStringify(item)).join(',')}]`;
  }

  const keys = Object.keys(input).sort();
  return `{${keys.map((key) => `${key}:${stableStringify(input[key])}`).join(',')}}`;
}

