const ID_KEY_REG = /(id|Id|ID)$/;
const DATE_KEY_REG = /(time|Time|date|Date)$/;

// 统一格式化日期为 yyyy-MM-dd HH:mm:ss，适配后端常见入参格式
export function formatDateTime(input) {
  const date = input instanceof Date ? input : new Date(input);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');

  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

export function normalizeIdValue(value) {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value !== 'string') {
    return value;
  }

  const text = value.trim();
  if (!text) {
    return value;
  }

  // 仅转换安全整数，避免长整型精度损失
  if (/^\d+$/.test(text)) {
    const numberValue = Number(text);
    if (Number.isSafeInteger(numberValue)) {
      return numberValue;
    }
  }
  return value;
}

export function adaptRequestPayload(input, keyName = '') {
  if (input === null || input === undefined) {
    return input;
  }

  if (input instanceof Date) {
    return formatDateTime(input);
  }

  if (Array.isArray(input)) {
    return input.map((item) => adaptRequestPayload(item, keyName));
  }

  if (isPlainObject(input)) {
    const result = {};
    Object.keys(input).forEach((key) => {
      result[key] = adaptRequestPayload(input[key], key);
    });
    return result;
  }

  if (typeof input === 'string') {
    if (ID_KEY_REG.test(keyName)) {
      return normalizeIdValue(input);
    }
    return input;
  }

  if (typeof input === 'number') {
    return input;
  }

  return input;
}

export function adaptResponsePayload(input, keyName = '') {
  if (input === null || input === undefined) {
    return input;
  }

  if (Array.isArray(input)) {
    return input.map((item) => adaptResponsePayload(item, keyName));
  }

  if (isPlainObject(input)) {
    const result = {};
    Object.keys(input).forEach((key) => {
      result[key] = adaptResponsePayload(input[key], key);
    });
    return result;
  }

  if (typeof input === 'string') {
    if (ID_KEY_REG.test(keyName)) {
      return normalizeIdValue(input);
    }
    return input;
  }

  return input;
}

export function normalizeSearchParams(params = {}) {
  const adapted = adaptRequestPayload(params);
  if (!isPlainObject(adapted)) {
    return params;
  }

  const normalized = {};
  Object.keys(adapted).forEach((key) => {
    const value = adapted[key];

    // 查询参数统一过滤空串，避免后端收到无效筛选项
    if (value === '' || value === null || value === undefined) {
      return;
    }

    if (DATE_KEY_REG.test(key) && value instanceof Date) {
      normalized[key] = formatDateTime(value);
      return;
    }

    normalized[key] = value;
  });

  return normalized;
}

function isPlainObject(value) {
  return Object.prototype.toString.call(value) === '[object Object]';
}

