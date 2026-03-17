<template>
  <div class="page-container seckill-page">
    <section class="banner card-panel">
      <h1>秒杀专区</h1>
      <p>限时抢购，库存有限，手慢无</p>
    </section>

    <section class="seckill-grid">
      <article v-for="item in seckillList" :key="item.id || item.productId" class="seckill-card card-panel">
        <RouterLink :to="`/web/product/detail/${item.productId || item.id}`" class="image-link">
          <LazyImage :src="item.image || item.cover" :alt="item.name || item.productName" fallback-text="暂无秒杀图" />
        </RouterLink>

        <div class="info">
          <h3>{{ item.name || item.productName || '秒杀商品' }}</h3>
          <p class="desc">{{ item.subtitle || item.description || '精选秒杀商品，数量有限' }}</p>

          <div class="price-line">
            <span class="seckill-price">￥{{ formatPrice(item.seckillPrice || item.price || 0) }}</span>
            <span v-if="item.originalPrice" class="origin-price">￥{{ formatPrice(item.originalPrice) }}</span>
          </div>

          <div class="meta-line">
            <span>库存：{{ item.stock ?? '--' }}</span>
            <span>已抢：{{ item.sales || 0 }}</span>
          </div>

          <div class="time-line">
            <template v-if="getStatus(item) === 'upcoming'">
              距开始：
              <CountDown :end-time="item.startTime" />
            </template>
            <template v-else-if="getStatus(item) === 'running'">
              距结束：
              <CountDown :end-time="item.endTime" />
            </template>
            <template v-else>
              活动已结束
            </template>
          </div>

          <el-button
            type="primary"
            color="#ff7a00"
            :disabled="getStatus(item) !== 'running' || Number(item.stock || 0) <= 0 || isSubmitting(item)"
            @click="handleSeckill(item)"
          >
            {{ getButtonText(item) }}
          </el-button>
        </div>
      </article>
    </section>

    <el-empty v-if="!seckillList.length" description="暂无秒杀活动" />
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import CountDown from '@/components/web/CountDown.vue';
import LazyImage from '@/components/web/LazyImage.vue';
import { doSeckillApi, getSeckillListApi } from '@/api';
import { throttle } from '@/utils/rate_limit';

const router = useRouter();
const route = useRoute();
const seckillList = ref([]);
const pendingIds = ref([]);
const throttledSubmit = throttle(performSeckill, 1000);

onMounted(() => {
  loadSeckillList();
});

onBeforeUnmount(() => {
  throttledSubmit.cancel?.();
});

async function loadSeckillList() {
  try {
    const data = await getSeckillListApi();
    seckillList.value = normalizeArray(data);
  } catch (_error) {
    seckillList.value = [];
  }
}

function getStatus(item) {
  const now = Date.now();
  const start = new Date(item.startTime || now).getTime();
  const end = new Date(item.endTime || now).getTime();

  if (now < start) {
    return 'upcoming';
  }
  if (now > end) {
    return 'ended';
  }
  return 'running';
}

function getButtonText(item) {
  if (Number(item.stock || 0) <= 0) {
    return '库存不足';
  }
  const status = getStatus(item);
  if (status === 'upcoming') {
    return '即将开始';
  }
  if (status === 'ended') {
    return '活动结束';
  }
  return '立即秒杀';
}

async function handleSeckill(item) {
  throttledSubmit(item);
}

async function performSeckill(item) {
  if (!localStorage.getItem('token')) {
    ElMessage.warning('请先登录后再参与秒杀');
    router.push({
      path: '/web/login',
      query: {
        redirect: route.fullPath
      }
    });
    return;
  }

  const targetId = item.productId || item.id;
  if (pendingIds.value.includes(targetId)) {
    return;
  }

  pendingIds.value.push(targetId);
  try {
    // 核心交互逻辑：秒杀按钮加节流与提交态，避免重复点击导致重复请求
    await doSeckillApi(targetId);
    ElMessage.success('秒杀成功！');
    await loadSeckillList();
  } finally {
    pendingIds.value = pendingIds.value.filter((id) => id !== targetId);
  }
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

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}

function isSubmitting(item) {
  const targetId = item.productId || item.id;
  return pendingIds.value.includes(targetId);
}
</script>

<style scoped>
.seckill-page {
  display: grid;
  gap: 16px;
}

.banner {
  padding: 20px;
  background: linear-gradient(135deg, rgba(255, 122, 0, 0.12), rgba(239, 68, 68, 0.08));
}

.banner h1 {
  color: #9a3412;
  font-size: 28px;
}

.banner p {
  margin-top: 8px;
  color: #7c2d12;
}

.seckill-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.seckill-card {
  display: grid;
  overflow: hidden;
  grid-template-rows: auto 1fr;
}

.image-link {
  display: block;
  aspect-ratio: 16 / 10;
}

.info {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
}

.info h3 {
  color: #0f172a;
  font-size: 18px;
}

.desc {
  display: -webkit-box;
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  text-overflow: ellipsis;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.price-line {
  display: flex;
  gap: 8px;
  align-items: baseline;
}

.seckill-price {
  color: #ef4444;
  font-size: 26px;
  font-weight: 700;
}

.origin-price {
  color: #94a3b8;
  font-size: 13px;
  text-decoration: line-through;
}

.meta-line {
  display: flex;
  justify-content: space-between;
  color: #475569;
  font-size: 13px;
}

.time-line {
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 1280px) {
  .seckill-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .seckill-grid {
    grid-template-columns: 1fr;
  }
}
</style>
