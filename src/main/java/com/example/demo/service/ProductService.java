package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.config.ProductCacheProperties;
import com.example.demo.entity.Product;
import com.example.demo.mapper.ProductMapper;
import com.example.demo.utils.BusinessException;
import com.example.demo.utils.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商品服务。
 * <p>
 * 高并发优化策略：
 * 1. 布隆过滤器拦截不存在商品ID，防止缓存穿透。
 * 2. 热点商品使用 Redis 互斥锁，防止缓存击穿。
 * 3. 缓存过期时间叠加随机值，降低同一时刻大面积失效风险。
 * </p>
 */
@Service
public class ProductService {

    private static final int PRODUCT_STATUS_ON_SALE = 1;
    private static final String PRODUCT_DETAIL_CACHE_KEY = "product:detail:";
    private static final String PRODUCT_DETAIL_LOCK_KEY = "product:detail:lock:";
    private static final String EMPTY_CACHE_FLAG = "__PRODUCT_NOT_EXIST__";

    private static final DefaultRedisScript<Long> UNLOCK_LUA_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final ProductMapper productMapper;
    private final RedisUtils redisUtils;
    private final StringRedisTemplate stringRedisTemplate;
    private final ProductCacheProperties cacheProperties;
    private final ObjectMapper objectMapper;

    /**
     * 商品ID布隆过滤器。
     */
    private volatile BloomFilter<Long> productIdBloomFilter;

    public ProductService(ProductMapper productMapper,
                          RedisUtils redisUtils,
                          StringRedisTemplate stringRedisTemplate,
                          ProductCacheProperties cacheProperties,
                          ObjectMapper objectMapper) {
        this.productMapper = productMapper;
        this.redisUtils = redisUtils;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheProperties = cacheProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化布隆过滤器。
     */
    @PostConstruct
    public void initBloomFilter() {
        rebuildBloomFilter();
    }

    /**
     * 查询商品列表。
     *
     * @return 商品列表
     */
    public List<Product> listProducts() {
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getStatus, PRODUCT_STATUS_ON_SALE)
                .orderByDesc(Product::getCreateTime);
        return productMapper.selectList(queryWrapper);
    }

    /**
     * 查询商品详情。
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    public Product getProductDetail(Long productId) {
        validateProductId(productId);

        // 布隆过滤器先行拦截明显不存在的商品ID，避免无效访问落到缓存和数据库。
        if (!getBloomFilter().mightContain(productId)) {
            throw new BusinessException(404, "商品不存在");
        }

        CacheReadResult cacheReadResult = readCache(productId);
        if (cacheReadResult.hit()) {
            if (cacheReadResult.empty()) {
                throw new BusinessException(404, "商品不存在");
            }
            return cacheReadResult.product();
        }

        // 热点商品走互斥锁，防止同一时刻大量线程击穿到数据库。
        if (isHotProduct(productId)) {
            return queryDetailWithHotMutex(productId);
        }

        // 普通商品直接回源并写缓存。
        return loadFromDbAndCache(productId, false);
    }

    /**
     * 带互斥锁查询热点商品详情。
     *
     * @param productId 商品ID
     * @return 商品详情
     */
    private Product queryDetailWithHotMutex(Long productId) {
        String lockKey = PRODUCT_DETAIL_LOCK_KEY + productId;

        for (int retry = 0; retry < cacheProperties.getLockRetryTimes(); retry++) {
            String lockValue = UUID.randomUUID().toString();
            boolean locked = tryLock(lockKey, lockValue);
            if (locked) {
                try {
                    // 二次检查缓存，避免重复回源。
                    CacheReadResult secondRead = readCache(productId);
                    if (secondRead.hit()) {
                        if (secondRead.empty()) {
                            throw new BusinessException(404, "商品不存在");
                        }
                        return secondRead.product();
                    }
                    return loadFromDbAndCache(productId, true);
                } finally {
                    unlock(lockKey, lockValue);
                }
            }

            // 未拿到锁时短暂等待，让已持锁线程先完成回填，降低数据库并发压力。
            sleepQuietly(cacheProperties.getLockRetryIntervalMillis());
            CacheReadResult waitRead = readCache(productId);
            if (waitRead.hit()) {
                if (waitRead.empty()) {
                    throw new BusinessException(404, "商品不存在");
                }
                return waitRead.product();
            }
        }

        // 锁竞争激烈时兜底回源，确保请求可用性。
        return loadFromDbAndCache(productId, true);
    }

    /**
     * 回源数据库并写缓存。
     *
     * @param productId 商品ID
     * @param hotProduct 是否热点商品
     * @return 商品详情
     */
    private Product loadFromDbAndCache(Long productId, boolean hotProduct) {
        Product product = productMapper.selectById(productId);
        if (product == null || !Objects.equals(product.getStatus(), PRODUCT_STATUS_ON_SALE)) {
            cacheEmptyValue(productId);
            throw new BusinessException(404, "商品不存在");
        }

        // 动态补充布隆过滤器，降低新上架商品在过滤器未刷新前被误判概率。
        getBloomFilter().put(productId);

        cacheProductValue(product, hotProduct);
        return product;
    }

    /**
     * 读取缓存。
     *
     * @param productId 商品ID
     * @return 缓存读取结果
     */
    private CacheReadResult readCache(Long productId) {
        String cacheKey = PRODUCT_DETAIL_CACHE_KEY + productId;
        Object cacheData = redisUtils.get(cacheKey);
        if (cacheData == null) {
            return CacheReadResult.missResult();
        }

        if (cacheData instanceof String stringValue && EMPTY_CACHE_FLAG.equals(stringValue)) {
            return CacheReadResult.emptyResult();
        }

        if (cacheData instanceof Product product) {
            return CacheReadResult.hitResult(product);
        }

        Product converted = objectMapper.convertValue(cacheData, Product.class);
        return CacheReadResult.hitResult(converted);
    }

    /**
     * 缓存商品详情。
     *
     * @param product 商品对象
     * @param hotProduct 是否热点商品
     */
    private void cacheProductValue(Product product, boolean hotProduct) {
        String cacheKey = PRODUCT_DETAIL_CACHE_KEY + product.getId();
        long baseExpireSeconds = hotProduct
                ? cacheProperties.getHotExpireSeconds()
                : cacheProperties.getDetailExpireSeconds();

        // 在基础过期时间上叠加随机偏移，打散缓存集中失效时间点，防止雪崩。
        long expireSeconds = baseExpireSeconds + buildRandomTtlOffsetSeconds();
        redisUtils.set(cacheKey, product, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 缓存空对象标记。
     *
     * @param productId 商品ID
     */
    private void cacheEmptyValue(Long productId) {
        String cacheKey = PRODUCT_DETAIL_CACHE_KEY + productId;
        long expireSeconds = cacheProperties.getNullExpireSeconds() + buildRandomTtlOffsetSeconds();
        redisUtils.set(cacheKey, EMPTY_CACHE_FLAG, expireSeconds, TimeUnit.SECONDS);
    }

    /**
     * 重建商品布隆过滤器。
     */
    private void rebuildBloomFilter() {
        List<Long> productIds = productMapper.selectObjs(new LambdaQueryWrapper<Product>().select(Product::getId))
                .stream()
                .filter(Objects::nonNull)
                .map(item -> Long.valueOf(String.valueOf(item)))
                .collect(Collectors.toList());

        int expectedInsertions = Math.max(productIds.size(), 10_000);
        BloomFilter<Long> bloomFilter = BloomFilter.create(
                Funnels.longFunnel(),
                expectedInsertions,
                0.01
        );

        for (Long productId : productIds) {
            bloomFilter.put(productId);
        }
        this.productIdBloomFilter = bloomFilter;
    }

    /**
     * 判断是否热点商品。
     *
     * @param productId 商品ID
     * @return 是否热点
     */
    private boolean isHotProduct(Long productId) {
        Set<Long> hotProductIdSet = CollectionUtils.isEmpty(cacheProperties.getHotProductIds())
                ? Collections.emptySet()
                : cacheProperties.getHotProductIds().stream().collect(Collectors.toSet());
        return hotProductIdSet.contains(productId);
    }

    /**
     * 尝试获取分布式锁。
     *
     * @param lockKey 锁键
     * @param lockValue 锁值
     * @return 是否获取成功
     */
    private boolean tryLock(String lockKey, String lockValue) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockValue,
                Duration.ofSeconds(cacheProperties.getLockExpireSeconds())
        );
        return Boolean.TRUE.equals(result);
    }

    /**
     * 释放分布式锁。
     *
     * @param lockKey 锁键
     * @param lockValue 锁值
     */
    private void unlock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(UNLOCK_LUA_SCRIPT, Collections.singletonList(lockKey), lockValue);
    }

    /**
     * 构建随机TTL偏移秒数。
     *
     * @return 偏移秒数
     */
    private long buildRandomTtlOffsetSeconds() {
        long randomMaxSeconds = cacheProperties.getTtlRandomMaxSeconds();
        if (randomMaxSeconds <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(1, randomMaxSeconds + 1);
    }

    /**
     * 入参校验。
     *
     * @param productId 商品ID
     */
    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new BusinessException(4004, "商品ID不合法");
        }
    }

    /**
     * 静默休眠。
     *
     * @param millis 休眠毫秒
     */
    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取布隆过滤器，避免空指针。
     *
     * @return BloomFilter
     */
    private BloomFilter<Long> getBloomFilter() {
        if (productIdBloomFilter == null) {
            synchronized (this) {
                if (productIdBloomFilter == null) {
                    productIdBloomFilter = BloomFilter.create(
                            Funnels.longFunnel(),
                            10_000,
                            0.01
                    );
                }
            }
        }
        return productIdBloomFilter;
    }

    /**
     * 缓存读取结果。
     *
     * @param hit 是否命中缓存
     * @param empty 是否为空标记
     * @param product 商品对象
     */
    private record CacheReadResult(boolean hit, boolean empty, Product product) {

        private static CacheReadResult missResult() {
            return new CacheReadResult(false, false, null);
        }

        private static CacheReadResult emptyResult() {
            return new CacheReadResult(true, true, null);
        }

        private static CacheReadResult hitResult(Product product) {
            return new CacheReadResult(true, false, product);
        }
    }
}