<template>
  <div class="page-container index-page">
    <section class="hero-section">
      <div class="carousel-box card-panel">
        <el-carousel :interval="4000" height="340px" indicator-position="outside">
          <el-carousel-item v-for="item in banners" :key="item.id">
            <a :href="item.link || 'javascript:void(0)'" class="banner-link">
              <img :src="item.image" :alt="item.title" loading="lazy" class="banner-image" />
            </a>
          </el-carousel-item>
        </el-carousel>
      </div>

      <div class="seckill-entry card-panel">
        <h3>限时秒杀</h3>
        <p>{{ seckillEntry.name || '今日秒杀已开启，先到先得' }}</p>
        <div class="price-row">
          <span class="sale-price">￥{{ formatPrice(seckillEntry.seckillPrice || seckillEntry.price || 0) }}</span>
          <span v-if="seckillEntry.originalPrice" class="origin-price">
            ￥{{ formatPrice(seckillEntry.originalPrice) }}
          </span>
        </div>
        <p class="countdown-row">
          倒计时：
          <CountDown :end-time="seckillEntry.endTime || defaultEndTime" />
        </p>
        <div class="entry-btns">
          <el-button type="primary" color="#ff7a00" @click="goSeckill">立即抢购</el-button>
          <el-button type="primary" color="#1677ff" plain @click="goProductList">查看更多商品</el-button>
        </div>
      </div>
    </section>

    <section class="category-section card-panel">
      <div class="section-title">商品分类导航</div>
      <div class="category-list">
        <el-button
          v-for="item in categories"
          :key="item.id"
          type="primary"
          plain
          color="#1677ff"
          @click="jumpCategory(item)"
        >
          {{ item.name }}
        </el-button>
      </div>
    </section>

    <section class="hot-section">
      <div class="section-head">
        <h2 class="title-text">热门商品</h2>
        <RouterLink to="/web/product/list" class="more-link">查看更多</RouterLink>
      </div>
      <div class="product-grid">
        <ProductCard
          v-for="item in hotProducts"
          :key="item.id || item.productId"
          :product="item"
          @add-cart="handleAddCart"
          @buy-now="handleBuyNow"
        />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import ProductCard from '@/components/web/ProductCard.vue';
import CountDown from '@/components/web/CountDown.vue';
import { addCartItemApi, getHomeBannerApi, getHotProductApi, getProductCategoryApi, getSeckillListApi } from '@/api';
import { useCartStore } from '@/store/modules/cart';

const router = useRouter();
const cartStore = useCartStore();

const banners = ref([]);
const categories = ref([]);
const hotProducts = ref([]);
const seckillList = ref([]);

const defaultEndTime = computed(() => Date.now() + 3600 * 1000);
const seckillEntry = computed(() => seckillList.value[0] || {});

onMounted(() => {
  loadHomeData();
});

async function loadHomeData() {
  const [bannerRes, categoryRes, hotRes, seckillRes] = await Promise.allSettled([
    getHomeBannerApi(),
    getProductCategoryApi(),
    getHotProductApi({ size: 8 }),
    getSeckillListApi({ size: 6 })
  ]);

  banners.value = normalizeBannerList(getSafeData(bannerRes));
  categories.value = normalizeArray(getSafeData(categoryRes));
  hotProducts.value = normalizeArray(getSafeData(hotRes));
  seckillList.value = normalizeArray(getSafeData(seckillRes));

  if (!categories.value.length) {
    categories.value = [
      { id: 1, name: '手机数码' },
      { id: 2, name: '家用电器' },
      { id: 3, name: '服饰箱包' },
      { id: 4, name: '食品生鲜' }
    ];
  }

  if (!hotProducts.value.length) {
    hotProducts.value = mockHotProducts();
  }

  if (!banners.value.length) {
    banners.value = hotProducts.value.slice(0, 3).map((item, index) => ({
      id: item.id || index,
      title: item.name || '轮播图',
      image: item.image || 'https://via.placeholder.com/1200x340?text=Banner'
    }));
  }
}

function getSafeData(result) {
  if (result.status === 'fulfilled') {
    return result.value;
  }
  return [];
}

function normalizeArray(data) {
  if (Array.isArray(data)) {
    return data;
  }
  if (Array.isArray(data?.list)) {
    return data.list;
  }
  if (Array.isArray(data?.records)) {
    return data.records;
  }
  if (Array.isArray(data?.items)) {
    return data.items;
  }
  return [];
}

function normalizeBannerList(data) {
  return normalizeArray(data).map((item, index) => ({
    id: item.id || index,
    title: item.title || item.name || '轮播图',
    image: item.image || item.imgUrl || item.cover || '',
    link: item.link || item.url || ''
  }));
}

function mockHotProducts() {
  return [
    {
      id: 101,
      name: '高性能游戏手机',
      price: 2999,
      stock: 88,
      sales: 1200,
      image: 'https://via.placeholder.com/400x400?text=Hot+1'
    },
    {
      id: 102,
      name: '智能降噪蓝牙耳机',
      price: 399,
      stock: 260,
      sales: 950,
      image: 'https://via.placeholder.com/400x400?text=Hot+2'
    },
    {
      id: 103,
      name: '4K 轻薄笔记本电脑',
      price: 4999,
      stock: 36,
      sales: 670,
      image: 'https://via.placeholder.com/400x400?text=Hot+3'
    },
    {
      id: 104,
      name: '家用空气炸锅',
      price: 269,
      stock: 128,
      sales: 1800,
      image: 'https://via.placeholder.com/400x400?text=Hot+4'
    }
  ];
}

async function handleAddCart(product) {
  const token = localStorage.getItem('token');
  if (!token) {
    ElMessage.warning('请先登录后再加入购物车');
    router.push('/web/login?redirect=/web/index');
    return;
  }

  await addCartItemApi({
    productId: product.id || product.productId,
    quantity: 1
  });
  cartStore.addToCart({
    id: product.id || product.productId,
    name: product.name || product.productName,
    price: product.price || product.salePrice || 0,
    quantity: 1
  });
  ElMessage.success('加入购物车成功');
}

function handleBuyNow(product) {
  router.push(`/web/product/detail/${product.id || product.productId}`);
}

function jumpCategory(item) {
  router.push({
    path: '/web/product/list',
    query: {
      categoryId: item.id,
      categoryName: item.name
    }
  });
}

function goSeckill() {
  router.push('/web/seckill');
}

function goProductList() {
  router.push('/web/product/list');
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.index-page {
  display: grid;
  gap: 20px;
}

.hero-section {
  display: grid;
  grid-template-columns: 2.3fr 1fr;
  gap: 16px;
}

.carousel-box {
  overflow: hidden;
  border-radius: 14px;
}

.banner-link {
  display: block;
  width: 100%;
  height: 100%;
}

.banner-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.seckill-entry {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding: 22px;
}

.seckill-entry h3 {
  color: #ff7a00;
  font-size: 26px;
}

.seckill-entry p {
  color: #475569;
  font-size: 14px;
}

.price-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.sale-price {
  color: #ef4444;
  font-size: 30px;
  font-weight: 700;
}

.origin-price {
  color: #94a3b8;
  font-size: 14px;
  text-decoration: line-through;
}

.countdown-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.entry-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 8px;
}

.category-section {
  padding: 18px;
}

.section-title {
  margin-bottom: 12px;
  color: #1e293b;
  font-size: 18px;
  font-weight: 700;
}

.category-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hot-section {
  display: grid;
  gap: 14px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.more-link {
  color: #1677ff;
  font-size: 14px;
  font-weight: 600;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

@media (max-width: 1280px) {
  .hero-section {
    grid-template-columns: 1fr;
  }

  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>

