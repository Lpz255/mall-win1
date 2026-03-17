<template>
  <article class="product-card card-panel">
    <RouterLink :to="`/web/product/detail/${safeId}`" class="image-link">
      <LazyImage :src="coverImage" :alt="safeName" fallback-text="暂无商品图" />
    </RouterLink>

    <div class="card-body">
      <RouterLink :to="`/web/product/detail/${safeId}`" class="product-name">
        {{ safeName }}
      </RouterLink>

      <p class="product-desc">{{ safeDesc }}</p>

      <div class="price-row">
        <div class="current-price">￥{{ formatPrice(displayPrice) }}</div>
        <div v-if="originPrice > displayPrice" class="origin-price">￥{{ formatPrice(originPrice) }}</div>
      </div>

      <div class="meta-row">
        <span>销量：{{ product.sales || 0 }}</span>
        <span>库存：{{ product.stock ?? '--' }}</span>
      </div>

      <div v-if="showActions" class="action-row">
        <el-button type="primary" color="#ff7a00" plain @click="$emit('add-cart', product)">加入购物车</el-button>
        <el-button type="primary" color="#1677ff" @click="$emit('buy-now', product)">立即购买</el-button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue';
import LazyImage from '@/components/web/LazyImage.vue';

const props = defineProps({
  product: {
    type: Object,
    default: () => ({})
  },
  showActions: {
    type: Boolean,
    default: true
  }
});

defineEmits(['add-cart', 'buy-now']);

const safeId = computed(() => props.product.id ?? props.product.productId ?? '');
const safeName = computed(() => props.product.name || props.product.productName || '未命名商品');
const safeDesc = computed(() => props.product.subtitle || props.product.description || '暂无商品描述');
const coverImage = computed(
  () => props.product.image || props.product.cover || props.product.mainImage || props.product.thumbnail || ''
);
const displayPrice = computed(() =>
  Number(props.product.seckillPrice || props.product.price || props.product.salePrice || 0)
);
const originPrice = computed(() => Number(props.product.originalPrice || props.product.marketPrice || displayPrice.value));

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.product-card {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 12px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.product-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}

.image-link {
  display: block;
  aspect-ratio: 1 / 1;
  overflow: hidden;
  background: #f8fafc;
}

.card-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
}

.product-name {
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.product-name:hover {
  color: #1677ff;
}

.product-desc {
  display: -webkit-box;
  min-height: 38px;
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
  text-overflow: ellipsis;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.price-row {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.current-price {
  color: #ff5f00;
  font-size: 20px;
  font-weight: 700;
}

.origin-price {
  color: #94a3b8;
  font-size: 12px;
  text-decoration: line-through;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  color: #64748b;
  font-size: 12px;
}

.action-row {
  display: flex;
  gap: 8px;
  margin-top: auto;
}

.action-row .el-button {
  flex: 1;
}
</style>

