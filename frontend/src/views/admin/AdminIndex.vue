<template>
  <div class="admin-index-page">
    <section class="card-grid">
      <article class="stat-card card-panel">
        <p class="label">订单总数</p>
        <h3>{{ formatNumber(overview.orderCount) }}</h3>
        <span class="sub-text">今日新增 {{ formatNumber(overview.todayOrderCount) }}</span>
      </article>

      <article class="stat-card card-panel">
        <p class="label">销售额（元）</p>
        <h3>{{ formatMoney(overview.salesAmount) }}</h3>
        <span class="sub-text">今日销售 {{ formatMoney(overview.todaySalesAmount) }}</span>
      </article>

      <article class="stat-card card-panel">
        <p class="label">秒杀转化率</p>
        <h3>{{ formatRate(overview.seckillRate) }}</h3>
        <span class="sub-text">较昨日 {{ formatRate(overview.seckillRateDiff) }}</span>
      </article>
    </section>

    <section class="detail-section card-panel">
      <div class="head-row">
        <h2>关键指标明细</h2>
        <el-button @click="loadOverview">刷新</el-button>
      </div>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="待支付订单">{{ formatNumber(overview.waitPayCount) }}</el-descriptions-item>
        <el-descriptions-item label="待发货订单">{{ formatNumber(overview.waitShipCount) }}</el-descriptions-item>
        <el-descriptions-item label="待退款订单">{{ formatNumber(overview.waitRefundCount) }}</el-descriptions-item>
        <el-descriptions-item label="在售商品数">{{ formatNumber(overview.onSaleProductCount) }}</el-descriptions-item>
        <el-descriptions-item label="秒杀活动数">{{ formatNumber(overview.activeSeckillCount) }}</el-descriptions-item>
        <el-descriptions-item label="注册用户数">{{ formatNumber(overview.userCount) }}</el-descriptions-item>
      </el-descriptions>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { getAdminOverviewApi } from '@/api';

const overview = ref({
  orderCount: 0,
  todayOrderCount: 0,
  salesAmount: 0,
  todaySalesAmount: 0,
  seckillRate: 0,
  seckillRateDiff: 0,
  waitPayCount: 0,
  waitShipCount: 0,
  waitRefundCount: 0,
  onSaleProductCount: 0,
  activeSeckillCount: 0,
  userCount: 0
});

onMounted(() => {
  loadOverview();
});

async function loadOverview() {
  try {
    const data = await getAdminOverviewApi();
    overview.value = {
      ...overview.value,
      ...(data || {})
    };
  } catch (_error) {
    // 保留默认值，避免页面空白
  }
}

function formatMoney(value) {
  return Number(value || 0).toFixed(2);
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN');
}

function formatRate(value) {
  return `${Number(value || 0).toFixed(2)}%`;
}
</script>

<style scoped>
.admin-index-page {
  display: grid;
  gap: 16px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 18px;
}

.label {
  color: #64748b;
  font-size: 14px;
}

.stat-card h3 {
  margin: 8px 0;
  color: #0f172a;
  font-size: 32px;
  font-weight: 700;
}

.sub-text {
  color: #475569;
  font-size: 13px;
}

.detail-section {
  padding: 18px;
}

.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.head-row h2 {
  color: #0f172a;
  font-size: 18px;
}

@media (max-width: 1200px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>

