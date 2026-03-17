<template>
  <div class="page-container detail-page">
    <section class="detail-card card-panel">
      <el-skeleton :loading="detailLoading" animated :rows="8">
        <template #default>
          <div class="detail-grid">
            <div class="gallery-block">
              <div class="main-image">
                <img :src="currentImage" :alt="product.name || '商品图片'" loading="lazy" />
              </div>
              <div class="thumb-list">
                <button
                  v-for="(item, index) in imageList"
                  :key="index"
                  :class="['thumb-btn', { active: currentImage === item }]"
                  @click="currentImage = item"
                >
                  <img :src="item" :alt="`商品图${index + 1}`" loading="lazy" />
                </button>
              </div>
            </div>

            <div class="info-block">
              <h1 class="product-title">{{ product.name || product.productName || '商品名称' }}</h1>
              <p class="product-subtitle">{{ product.subtitle || '品质好货，售后无忧' }}</p>

              <div class="price-box">
                <span class="price-label">促销价</span>
                <span class="price-value">￥{{ formatPrice(product.price || product.salePrice || 0) }}</span>
                <span v-if="product.originalPrice" class="origin-price">￥{{ formatPrice(product.originalPrice) }}</span>
              </div>

              <div class="meta-list">
                <p>库存：{{ product.stock ?? '--' }}</p>
                <p>销量：{{ product.sales || 0 }}</p>
                <p>商品编号：{{ product.id || route.params.id }}</p>
              </div>

              <div class="action-row">
                <span class="quantity-label">购买数量</span>
                <el-input-number v-model="buyQuantity" :min="1" :max="maxBuyCount" />
              </div>

              <div class="button-row">
                <el-button type="primary" color="#ff7a00" :disabled="isOutOfStock" @click="handleAddCart">
                  加入购物车
                </el-button>
                <el-button type="primary" color="#1677ff" :disabled="isOutOfStock" @click="handleBuyNow">
                  立即购买
                </el-button>
              </div>

              <p v-if="isOutOfStock" class="stock-tip">当前商品库存不足，暂不可下单</p>
            </div>
          </div>

          <section class="detail-desc-block">
            <h3>商品详情</h3>
            <p>{{ product.detailContent || product.description || '暂无商品详情' }}</p>
          </section>
        </template>
      </el-skeleton>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { addCartItemApi, createOrderApi, getProductDetailApi } from '@/api';
import { useCartStore } from '@/store/modules/cart';

const route = useRoute();
const router = useRouter();
const cartStore = useCartStore();

const detailLoading = ref(false);
const product = ref({});
const currentImage = ref('');
const buyQuantity = ref(1);

const imageList = computed(() => {
  const data = product.value;
  if (Array.isArray(data.images) && data.images.length) {
    return data.images;
  }
  if (Array.isArray(data.imageList) && data.imageList.length) {
    return data.imageList;
  }
  if (data.image || data.cover || data.mainImage) {
    return [data.image || data.cover || data.mainImage];
  }
  return ['https://via.placeholder.com/640x640?text=No+Image'];
});

const maxBuyCount = computed(() => {
  const stock = Number(product.value.stock || 1);
  return Math.max(1, stock);
});

const isOutOfStock = computed(() => Number(product.value.stock || 0) <= 0);

onMounted(() => {
  loadProductDetail();
});

watch(
  () => route.params.id,
  () => {
    loadProductDetail();
  }
);

async function loadProductDetail() {
  detailLoading.value = true;
  try {
    const data = await getProductDetailApi(route.params.id);
    product.value = data || {};
    currentImage.value = imageList.value[0];
    buyQuantity.value = 1;
  } finally {
    detailLoading.value = false;
  }
}

async function handleAddCart() {
  if (!checkLogin()) {
    return;
  }

  await addCartItemApi({
    productId: product.value.id || route.params.id,
    quantity: buyQuantity.value
  });
  cartStore.addToCart({
    id: product.value.id || route.params.id,
    name: product.value.name || product.value.productName,
    price: product.value.price || product.value.salePrice || 0,
    quantity: buyQuantity.value
  });
  ElMessage.success('加入购物车成功');
}

async function handleBuyNow() {
  if (!checkLogin()) {
    return;
  }

  if (isOutOfStock.value) {
    ElMessage.warning('库存不足，暂无法购买');
    return;
  }

  await createOrderApi({
    source: 'buy_now',
    items: [
      {
        productId: product.value.id || route.params.id,
        quantity: buyQuantity.value
      }
    ]
  });
  ElMessage.success('下单成功');
  router.push('/web/order');
}

function checkLogin() {
  if (localStorage.getItem('token')) {
    return true;
  }

  ElMessage.warning('请先登录后再操作');
  router.push({
    path: '/web/login',
    query: {
      redirect: route.fullPath
    }
  });
  return false;
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.detail-page {
  display: grid;
}

.detail-card {
  padding: 20px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1.1fr;
  gap: 26px;
}

.gallery-block {
  display: grid;
  gap: 10px;
}

.main-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: 12px;
  background: #f8fafc;
}

.main-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.thumb-btn {
  width: 100%;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  cursor: pointer;
  background: #fff;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
}

.thumb-btn.active {
  border-color: #1677ff;
}

.thumb-btn img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info-block {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.product-title {
  color: #0f172a;
  font-size: 28px;
}

.product-subtitle {
  color: #64748b;
  font-size: 14px;
}

.price-box {
  display: flex;
  gap: 10px;
  align-items: baseline;
  padding: 16px;
  border-radius: 12px;
  background: #fff7ed;
}

.price-label {
  color: #9a3412;
  font-size: 14px;
  font-weight: 600;
}

.price-value {
  color: #ff5f00;
  font-size: 34px;
  font-weight: 700;
}

.origin-price {
  color: #94a3b8;
  font-size: 14px;
  text-decoration: line-through;
}

.meta-list {
  display: grid;
  gap: 8px;
  color: #475569;
  font-size: 14px;
}

.action-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.quantity-label {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.button-row {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}

.stock-tip {
  color: #ef4444;
  font-size: 13px;
}

.detail-desc-block {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid #e2e8f0;
}

.detail-desc-block h3 {
  margin-bottom: 10px;
  color: #0f172a;
  font-size: 18px;
}

.detail-desc-block p {
  color: #334155;
  line-height: 1.8;
  white-space: pre-wrap;
}

@media (max-width: 1200px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
