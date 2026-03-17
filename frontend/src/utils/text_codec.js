export function safeDisplayText(input, fallback = '') {
  if (typeof input !== 'string') {
    return fallback;
  }
  const value = input.trim();
  if (!value) {
    return fallback;
  }

  // 后端编码异常时，中文可能显示为“æµè¯”这类乱码，尝试修复
  const decoded = tryDecodeUtf8Mojibake(value);
  return decoded || fallback;
}

function tryDecodeUtf8Mojibake(text) {
  if (!looksLikeMojibake(text)) {
    return text;
  }

  try {
    const decoded = decodeURIComponent(escape(text));
    return decoded || text;
  } catch (_error) {
    return text;
  }
}

function looksLikeMojibake(text) {
  return /[ÃÂÄÅÆÐÑÒÓÔÕÖØÙÚÛÜÝÞãäåæçèéêëìíîïðñòóôõöøùúûüýþ]/.test(text);
}

