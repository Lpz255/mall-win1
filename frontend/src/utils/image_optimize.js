export function buildOptimizedImageUrl(rawUrl, options = {}) {
  const {
    width = 640,
    quality = 75,
    format = 'webp'
  } = options;

  if (typeof rawUrl !== 'string' || !rawUrl.trim()) {
    return '';
  }

  const url = rawUrl.trim();
  if (!/^https?:\/\//.test(url)) {
    return url;
  }

  const hasQuery = url.includes('?');
  const query = `w=${width}&q=${quality}&fmt=${format}`;
  return `${url}${hasQuery ? '&' : '?'}${query}`;
}

