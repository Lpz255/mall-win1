import request from '@/api/request';
import { buildCacheKey, clearApiCacheByPrefix, withApiCache } from '@/utils/api_cache';
import { normalizeIdValue, normalizeSearchParams } from '@/utils/api_adapter';

// 首页轮播图
export function getHomeBannerApi() {
  const cacheKey = buildCacheKey('product:banner');
  return withApiCache(cacheKey, () => request.get('/product/banner'), {
    ttl: 5 * 60 * 1000,
    storage: 'session'
  });
}

// 商品分类导航
export function getProductCategoryApi() {
  const cacheKey = buildCacheKey('product:category');
  return withApiCache(cacheKey, () => request.get('/product/category'), {
    ttl: 10 * 60 * 1000,
    storage: 'session'
  });
}

// 首页热门商品
export function getHotProductApi(params = {}) {
  const normalizedParams = normalizeSearchParams(params);
  const cacheKey = buildCacheKey('product:hot', normalizedParams);
  return withApiCache(cacheKey, () => request.get('/product/hot', { params: normalizedParams }), {
    ttl: 60 * 1000,
    storage: 'session'
  });
}

// 商品列表（筛选 + 分页 + 排序）
export function getProductListApi(params = {}, options = {}) {
  const normalizedParams = normalizeSearchParams(params);
  const {
    useCache = true,
    ttl = 30 * 1000
  } = options;

  const fetcher = () => request.get('/product/list', { params: normalizedParams });
  if (!useCache) {
    return fetcher();
  }

  const cacheKey = buildCacheKey('product:list', normalizedParams);
  return withApiCache(cacheKey, fetcher, {
    ttl,
    storage: 'session'
  });
}

// 商品详情
export function getProductDetailApi(id) {
  const productId = normalizeIdValue(id);
  return request.get('/product/detail', {
    params: { id: productId }
  });
}

export function clearProductCache() {
  clearApiCacheByPrefix('product:', { storage: 'session' });
}
