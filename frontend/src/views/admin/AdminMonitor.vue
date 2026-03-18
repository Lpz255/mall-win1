<template>
  <div :class="['admin-monitor-page', { 'admin-monitor-fullscreen': isFullscreen }]">
    <section class="monitor-hero">
      <div class="hero-copy">
        <p class="hero-kicker">Operations Command</p>
        <h2>经营监控大屏</h2>
        <p class="hero-desc">集中查看订单、营收、供给与秒杀态势，适合运营巡检、会议投屏和后台值守。</p>
      </div>
      <div class="hero-meta">
        <div class="meta-time">
          <span class="meta-label">当前时间</span>
          <strong>{{ nowText }}</strong>
        </div>
        <div class="meta-actions">
          <el-tag effect="dark" color="#132238">{{ autoRefreshText }}</el-tag>
          <el-tag effect="dark" color="#1b2f4f">{{ screenModeText }}</el-tag>
          <el-button plain color="#8b5cf6" @click="toggleFullscreen">{{ fullscreenButtonText }}</el-button>
          <el-button type="primary" color="#1d4ed8" @click="loadDashboard()">立即刷新</el-button>
        </div>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="item in metricCards" :key="item.key" class="metric-card">
        <div class="metric-head">
          <span class="metric-label">{{ item.label }}</span>
          <span class="metric-badge">{{ item.badge }}</span>
        </div>
        <strong class="metric-value">{{ item.value }}</strong>
        <p class="metric-sub">{{ item.subText }}</p>
      </article>
    </section>

    <section class="command-strip">
      <article class="command-status" :class="`command-status-${commandStatus.tone}`">
        <span class="command-status-label">{{ commandStatus.label }}</span>
        <p>{{ commandStatus.detail }}</p>
      </article>
      <article v-for="item in commandSignals" :key="item.key" class="signal-card" :class="`signal-card-${item.tone}`">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="broadcast-bar">
      <div class="broadcast-label">实时播报</div>
      <div class="broadcast-track">
        <div class="broadcast-loop">
          <span v-for="(line, index) in broadcastLoopItems" :key="`${line}-${index}`">{{ line }}</span>
        </div>
      </div>
    </section>

    <section class="monitor-grid monitor-grid-top">
      <article class="monitor-card monitor-card-wide">
        <div class="card-head">
          <div>
            <h3>近 7 天订单与销售趋势</h3>
            <p>同时观察订单量变化和销售额走势，识别峰值与回落区间。</p>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-box chart-box-large"></div>
      </article>

      <article class="monitor-card">
        <div class="card-head">
          <div>
            <h3>订单状态分布</h3>
            <p>判断当前履约压力主要集中在哪个状态。</p>
          </div>
        </div>
        <div ref="statusChartRef" class="chart-box"></div>
      </article>
    </section>

    <section class="monitor-grid monitor-grid-bottom">
      <article class="monitor-card">
        <div class="card-head">
          <div>
            <h3>热销商品 Top 5</h3>
            <p>按有效订单量排名，辅助运营判断核心成交商品。</p>
          </div>
        </div>
        <div ref="productChartRef" class="chart-box"></div>
      </article>

      <article class="monitor-card monitor-card-side">
        <div class="card-head">
          <div>
            <h3>秒杀战情</h3>
            <p>关注秒杀订单、转化率、活动状态与低库存预警。</p>
          </div>
        </div>

        <div class="battle-grid">
          <article v-for="item in seckillStats" :key="item.key" class="battle-item">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </article>
        </div>

        <div class="stock-panel">
          <div class="stock-head">
            <h4>低库存预警</h4>
            <span>{{ dashboard.seckillBoard.lowStockCount || 0 }} 个活动</span>
          </div>
          <div v-if="dashboard.seckillBoard.lowStockProducts?.length" class="stock-list">
            <article v-for="item in dashboard.seckillBoard.lowStockProducts" :key="`${item.productId}-${item.name}`" class="stock-item">
              <div>
                <strong>{{ item.name || `商品${item.productId}` }}</strong>
                <p>{{ item.startTime || '--' }} - {{ item.endTime || '--' }}</p>
              </div>
              <el-tag type="danger">余量 {{ item.stock ?? 0 }}</el-tag>
            </article>
          </div>
          <el-empty v-else description="暂无低库存预警" :image-size="86" />
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, markRaw, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { getAdminMonitorApi } from '@/api';

const AUTO_REFRESH_MS = 60000;

const dashboard = ref(createDefaultDashboard());
const nowText = ref('');
const isFullscreen = ref(false);
const trendChartRef = ref(null);
const statusChartRef = ref(null);
const productChartRef = ref(null);

const chartRegistry = new Map();
let clockTimer = null;
let refreshTimer = null;
let resizeObserver = null;
let windowResizeHandler = null;
let fullscreenChangeHandler = null;

const metricCards = computed(() => {
  const overview = dashboard.value.overview;
  return [
    {
      key: 'orders',
      label: '订单规模',
      value: formatNumber(overview.orderCount),
      badge: `今日 +${formatNumber(overview.todayOrderCount)}`,
      subText: `待支付 ${formatNumber(overview.waitPayCount)} / 待发货 ${formatNumber(overview.waitShipCount)}`
    },
    {
      key: 'sales',
      label: '累计销售额',
      value: `￥${formatMoney(overview.salesAmount)}`,
      badge: `今日 ￥${formatMoney(overview.todaySalesAmount)}`,
      subText: `秒杀转化率 ${formatRate(overview.seckillRate)}`
    },
    {
      key: 'goods',
      label: '商品供给',
      value: formatNumber(overview.onSaleProductCount),
      badge: `活动 ${formatNumber(overview.activeSeckillCount)}`,
      subText: '在售商品与秒杀活动同步巡检'
    },
    {
      key: 'users',
      label: '用户体量',
      value: formatNumber(overview.userCount),
      badge: `退款待处理 ${formatNumber(overview.waitRefundCount)}`,
      subText: `整体订单 ${formatNumber(overview.orderCount)} 笔`
    }
  ];
});

const seckillStats = computed(() => {
  const board = dashboard.value.seckillBoard;
  return [
    { key: 'orderCount', label: '秒杀订单', value: formatNumber(board.orderCount) },
    { key: 'conversionRate', label: '转化率', value: formatRate(board.conversionRate) },
    { key: 'running', label: '进行中活动', value: formatNumber(board.runningActivityCount) },
    { key: 'ended', label: '已结束活动', value: formatNumber(board.endedActivityCount) }
  ];
});

const autoRefreshText = computed(() => `自动刷新 ${AUTO_REFRESH_MS / 1000}s`);
const screenModeText = computed(() => (isFullscreen.value ? '全屏巡检中' : '窗口模式'));
const fullscreenButtonText = computed(() => (isFullscreen.value ? '退出全屏' : '进入全屏'));

const commandSignals = computed(() => {
  const overview = dashboard.value.overview;
  return [
    {
      key: 'todaySales',
      label: '今日销售额',
      value: `￥${formatMoney(overview.todaySalesAmount)}`,
      tone: 'accent'
    },
    {
      key: 'refunds',
      label: '退款待处理',
      value: formatNumber(overview.waitRefundCount),
      tone: overview.waitRefundCount > 0 ? 'alert' : 'safe'
    },
    {
      key: 'activeSeckill',
      label: '进行中秒杀',
      value: formatNumber(overview.activeSeckillCount),
      tone: 'warm'
    }
  ];
});

const commandStatus = computed(() => {
  const overview = dashboard.value.overview;
  if (overview.waitRefundCount > 0) {
    return {
      label: '异常关注',
      detail: `当前有 ${formatNumber(overview.waitRefundCount)} 笔退款待处理，请优先跟进售后。`,
      tone: 'alert'
    };
  }
  if (dashboard.value.seckillBoard.lowStockCount > 0) {
    return {
      label: '库存预警',
      detail: `共有 ${formatNumber(dashboard.value.seckillBoard.lowStockCount)} 个秒杀活动需要补货关注。`,
      tone: 'warm'
    };
  }
  return {
    label: '运行平稳',
    detail: '订单履约与秒杀活动暂无明显风险，可继续观察趋势变化。',
    tone: 'safe'
  };
});

const broadcastLines = computed(() => {
  const overview = dashboard.value.overview;
  const board = dashboard.value.seckillBoard;
  return [
    `今日订单 ${formatNumber(overview.todayOrderCount)} 笔，销售额 ￥${formatMoney(overview.todaySalesAmount)}`,
    `待发货 ${formatNumber(overview.waitShipCount)} 笔，待支付 ${formatNumber(overview.waitPayCount)} 笔`,
    `秒杀转化率 ${formatRate(board.conversionRate)}，进行中活动 ${formatNumber(board.runningActivityCount)} 个`
  ];
});

const broadcastLoopItems = computed(() => [...broadcastLines.value, ...broadcastLines.value]);

onMounted(async () => {
  updateClock();
  syncFullscreenState();
  clockTimer = window.setInterval(updateClock, 1000);
  refreshTimer = window.setInterval(() => {
    loadDashboard(true);
  }, AUTO_REFRESH_MS);
  fullscreenChangeHandler = () => {
    syncFullscreenState();
    resizeCharts();
  };
  document.addEventListener('fullscreenchange', fullscreenChangeHandler);
  await loadDashboard(true);
});

onBeforeUnmount(() => {
  if (clockTimer) {
    window.clearInterval(clockTimer);
  }
  if (refreshTimer) {
    window.clearInterval(refreshTimer);
  }
  if (fullscreenChangeHandler) {
    document.removeEventListener('fullscreenchange', fullscreenChangeHandler);
    fullscreenChangeHandler = null;
  }
  teardownResizeObserver();
  disposeCharts();
});

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen();
      return;
    }
    await document.documentElement.requestFullscreen();
  } catch (_error) {
    ElMessage.warning('当前环境暂不支持全屏显示');
  }
}

function syncFullscreenState() {
  isFullscreen.value = Boolean(document.fullscreenElement);
}

async function loadDashboard(silent = false) {
  try {
    const data = await getAdminMonitorApi();
    dashboard.value = mergeDashboard(data);
    await nextTick();
    renderCharts();
  } catch (_error) {
    if (!silent) {
      ElMessage.error('监控数据加载失败，请稍后重试');
    }
  }
}

function mergeDashboard(data) {
  const defaults = createDefaultDashboard();
  return {
    overview: {
      ...defaults.overview,
      ...(data?.overview || {})
    },
    trend7d: {
      ...defaults.trend7d,
      ...(data?.trend7d || {})
    },
    orderStatusDistribution: {
      ...defaults.orderStatusDistribution,
      ...(data?.orderStatusDistribution || {}),
      items: Array.isArray(data?.orderStatusDistribution?.items) ? data.orderStatusDistribution.items : []
    },
    topProducts: Array.isArray(data?.topProducts) ? data.topProducts : [],
    seckillBoard: {
      ...defaults.seckillBoard,
      ...(data?.seckillBoard || {}),
      lowStockProducts: Array.isArray(data?.seckillBoard?.lowStockProducts) ? data.seckillBoard.lowStockProducts : []
    }
  };
}

function renderCharts() {
  setChartOption('trend', trendChartRef.value, buildTrendOption());
  setChartOption('status', statusChartRef.value, buildStatusOption());
  setChartOption('products', productChartRef.value, buildProductOption());
  setupResizeObserver();
}

function setChartOption(key, element, option) {
  if (!element) {
    return;
  }

  let chart = chartRegistry.get(key);
  if (!chart) {
    chart = markRaw(echarts.init(element));
    chartRegistry.set(key, chart);
  }

  chart.setOption(option, true);
  chart.resize();
}

function buildTrendOption() {
  const trend = dashboard.value.trend7d;
  const dates = Array.isArray(trend.dates) && trend.dates.length ? trend.dates : ['--'];
  const orderCounts = Array.isArray(trend.orderCounts) && trend.orderCounts.length ? trend.orderCounts : [0];
  const salesAmounts = Array.isArray(trend.salesAmounts) && trend.salesAmounts.length ? trend.salesAmounts : [0];

  return {
    color: ['#3b82f6', '#f97316'],
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      top: 0,
      right: 0,
      textStyle: {
        color: '#94a3b8'
      }
    },
    grid: {
      left: 24,
      right: 24,
      top: 56,
      bottom: 24,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.35)'
        }
      },
      axisLabel: {
        color: '#94a3b8'
      }
    },
    yAxis: [
      {
        type: 'value',
        name: '订单量',
        axisLabel: {
          color: '#94a3b8'
        },
        splitLine: {
          lineStyle: {
            color: 'rgba(148, 163, 184, 0.12)'
          }
        }
      },
      {
        type: 'value',
        name: '销售额',
        axisLabel: {
          color: '#94a3b8',
          formatter: (value) => value
        },
        splitLine: {
          show: false
        }
      }
    ],
    series: [
      {
        name: '订单量',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        areaStyle: {
          color: 'rgba(59, 130, 246, 0.18)'
        },
        lineStyle: {
          width: 3
        },
        data: orderCounts.map((item) => Number(item || 0))
      },
      {
        name: '销售额',
        type: 'line',
        smooth: true,
        yAxisIndex: 1,
        symbol: 'diamond',
        symbolSize: 8,
        lineStyle: {
          width: 3
        },
        data: salesAmounts.map((item) => Number(item || 0))
      }
    ]
  };
}

function buildStatusOption() {
  const items = dashboard.value.orderStatusDistribution.items;
  return {
    color: ['#3b82f6', '#10b981', '#f97316', '#ef4444'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      textStyle: {
        color: '#94a3b8'
      }
    },
    series: [
      {
        type: 'pie',
        radius: ['46%', '72%'],
        center: ['50%', '44%'],
        itemStyle: {
          borderColor: '#08111f',
          borderWidth: 4
        },
        label: {
          color: '#e2e8f0',
          formatter: '{b}\n{c}'
        },
        data: items.length ? items : [{ name: '暂无数据', value: 0 }]
      }
    ]
  };
}

function buildProductOption() {
  const products = dashboard.value.topProducts.length ? dashboard.value.topProducts : [{ name: '暂无数据', orderCount: 0 }];
  return {
    color: ['#f59e0b'],
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: 24,
      right: 24,
      top: 24,
      bottom: 24,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      splitLine: {
        lineStyle: {
          color: 'rgba(148, 163, 184, 0.12)'
        }
      },
      axisLabel: {
        color: '#94a3b8'
      }
    },
    yAxis: {
      type: 'category',
      axisTick: {
        show: false
      },
      axisLine: {
        show: false
      },
      axisLabel: {
        color: '#cbd5e1'
      },
      data: products.map((item) => item.name || `商品${item.productId}`)
    },
    series: [
      {
        type: 'bar',
        barWidth: 18,
        label: {
          show: true,
          position: 'right',
          color: '#f8fafc'
        },
        itemStyle: {
          borderRadius: [0, 10, 10, 0]
        },
        data: products.map((item) => Number(item.orderCount || 0))
      }
    ]
  };
}

function setupResizeObserver() {
  const elements = [trendChartRef.value, statusChartRef.value, productChartRef.value].filter(Boolean);
  if (!elements.length) {
    return;
  }

  if (typeof ResizeObserver !== 'undefined') {
    if (!resizeObserver) {
      resizeObserver = new ResizeObserver(() => {
        resizeCharts();
      });
    }
    resizeObserver.disconnect();
    elements.forEach((element) => resizeObserver.observe(element));
  }

  if (!windowResizeHandler) {
    windowResizeHandler = () => {
      resizeCharts();
    };
    window.addEventListener('resize', windowResizeHandler);
  }
}

function teardownResizeObserver() {
  if (resizeObserver) {
    resizeObserver.disconnect();
  }
  if (windowResizeHandler) {
    window.removeEventListener('resize', windowResizeHandler);
    windowResizeHandler = null;
  }
}

function resizeCharts() {
  chartRegistry.forEach((chart) => chart?.resize());
}

function disposeCharts() {
  chartRegistry.forEach((chart) => chart?.dispose());
  chartRegistry.clear();
}

function updateClock() {
  nowText.value = new Date().toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  });
}

function createDefaultDashboard() {
  return {
    overview: {
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
    },
    trend7d: {
      dates: [],
      orderCounts: [],
      salesAmounts: []
    },
    orderStatusDistribution: {
      items: [],
      total: 0
    },
    topProducts: [],
    seckillBoard: {
      orderCount: 0,
      conversionRate: 0,
      runningActivityCount: 0,
      endedActivityCount: 0,
      lowStockCount: 0,
      lowStockProducts: []
    }
  };
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
.admin-monitor-page {
  --monitor-surface: rgba(10, 22, 40, 0.92);
  --monitor-border: rgba(71, 85, 105, 0.4);
  --monitor-text: #e2e8f0;
  --monitor-muted: #94a3b8;
  display: grid;
  gap: 18px;
  min-height: calc(100vh - 62px);
  padding: 20px;
  background:
    radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.2), transparent 28%),
    radial-gradient(circle at 100% 0%, rgba(249, 115, 22, 0.18), transparent 24%),
    linear-gradient(180deg, #08111f 0%, #091525 48%, #06101c 100%);
}

.monitor-hero,
.monitor-card,
.metric-card,
.command-status,
.signal-card,
.broadcast-bar {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--monitor-border);
  background: var(--monitor-surface);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04), 0 18px 40px rgba(2, 6, 23, 0.28);
  backdrop-filter: blur(10px);
}

.monitor-hero::before,
.monitor-card::before,
.metric-card::before,
.command-status::before,
.signal-card::before,
.broadcast-bar::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent 42%);
}

.monitor-hero > *,
.monitor-card > *,
.metric-card > *,
.command-status > *,
.signal-card > *,
.broadcast-bar > * {
  position: relative;
  z-index: 1;
}

.monitor-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border-radius: 24px;
}

.hero-kicker {
  margin: 0 0 8px;
  color: rgba(148, 163, 184, 0.9);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.hero-copy h2 {
  margin: 0;
  color: #f8fafc;
  font-size: 34px;
  font-weight: 700;
}

.hero-desc {
  margin: 12px 0 0;
  max-width: 720px;
  color: var(--monitor-muted);
  font-size: 14px;
  line-height: 1.8;
}

.hero-meta {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-items: flex-end;
}

.meta-time {
  display: grid;
  gap: 6px;
  justify-items: end;
}

.meta-label {
  color: var(--monitor-muted);
  font-size: 12px;
}

.meta-time strong {
  color: #f8fafc;
  font-size: 20px;
  font-weight: 700;
}

.meta-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: flex-end;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.metric-card {
  padding: 18px;
  border-radius: 20px;
}

.metric-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.metric-label {
  color: var(--monitor-muted);
  font-size: 13px;
  font-weight: 600;
}

.metric-badge {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.14);
  color: #bfdbfe;
  font-size: 12px;
}

.metric-value {
  display: block;
  margin-top: 18px;
  color: #f8fafc;
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
}

.metric-sub {
  margin: 10px 0 0;
  color: var(--monitor-muted);
  font-size: 13px;
  line-height: 1.7;
}

.command-strip {
  display: grid;
  grid-template-columns: minmax(280px, 1.4fr) repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.command-status,
.signal-card {
  border-radius: 20px;
}

.command-status {
  padding: 18px 20px;
}

.command-status::after {
  content: '';
  position: absolute;
  right: -40px;
  bottom: -54px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(59, 130, 246, 0.28), transparent 68%);
}

.command-status-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.command-status p {
  margin: 16px 0 0;
  color: #e2e8f0;
  font-size: 15px;
  line-height: 1.75;
}

.command-status-safe .command-status-label {
  background: rgba(16, 185, 129, 0.14);
  color: #6ee7b7;
}

.command-status-warm .command-status-label {
  background: rgba(249, 115, 22, 0.14);
  color: #fdba74;
}

.command-status-alert .command-status-label {
  background: rgba(239, 68, 68, 0.14);
  color: #fca5a5;
}

.signal-card {
  display: grid;
  gap: 10px;
  padding: 18px;
}

.signal-card span {
  color: var(--monitor-muted);
  font-size: 12px;
}

.signal-card strong {
  color: #f8fafc;
  font-size: 28px;
  font-weight: 700;
}

.signal-card-accent {
  border-color: rgba(59, 130, 246, 0.36);
}

.signal-card-warm {
  border-color: rgba(249, 115, 22, 0.34);
}

.signal-card-safe {
  border-color: rgba(16, 185, 129, 0.32);
}

.signal-card-alert {
  border-color: rgba(239, 68, 68, 0.32);
}

.broadcast-bar {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  padding: 14px 18px;
  border-radius: 18px;
}

.broadcast-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 94px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgba(29, 78, 216, 0.16);
  color: #bfdbfe;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.broadcast-track {
  min-width: 0;
  overflow: hidden;
}

.broadcast-loop {
  display: inline-flex;
  align-items: center;
  gap: 36px;
  min-width: max-content;
  color: #dbeafe;
  animation: monitor-marquee 22s linear infinite;
}

.broadcast-loop span {
  position: relative;
  padding-left: 18px;
  font-size: 14px;
  white-space: nowrap;
}

.broadcast-loop span::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #38bdf8;
  transform: translateY(-50%);
  box-shadow: 0 0 10px rgba(56, 189, 248, 0.8);
}

.monitor-grid {
  display: grid;
  gap: 18px;
}

.monitor-grid-top {
  grid-template-columns: minmax(0, 1.7fr) minmax(340px, 1fr);
}

.monitor-grid-bottom {
  grid-template-columns: minmax(0, 1.2fr) minmax(360px, 0.8fr);
}

.monitor-card {
  padding: 18px;
  border-radius: 22px;
}

.monitor-card-wide {
  min-height: 430px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-head h3,
.stock-head h4 {
  margin: 0;
  color: #f8fafc;
  font-size: 19px;
  font-weight: 700;
}

.card-head p {
  margin: 8px 0 0;
  color: var(--monitor-muted);
  font-size: 13px;
  line-height: 1.7;
}

.chart-box {
  width: 100%;
  height: 330px;
}

.chart-box-large {
  height: 360px;
}

.battle-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 8px;
}

.battle-item {
  display: grid;
  gap: 8px;
  padding: 14px;
  border: 1px solid rgba(59, 130, 246, 0.18);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.72), rgba(15, 23, 42, 0.92));
}

.battle-item span,
.stock-head span {
  color: var(--monitor-muted);
  font-size: 12px;
}

.battle-item strong {
  color: #f8fafc;
  font-size: 24px;
}

.stock-panel {
  margin-top: 16px;
  padding: 16px;
  border-radius: 18px;
  background: rgba(8, 17, 31, 0.74);
  border: 1px solid rgba(71, 85, 105, 0.32);
}

.stock-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.stock-list {
  display: grid;
  gap: 10px;
}

.stock-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.94);
  border: 1px solid rgba(71, 85, 105, 0.24);
}

.stock-item strong {
  color: #f8fafc;
  font-size: 14px;
}

.stock-item p {
  margin: 6px 0 0;
  color: var(--monitor-muted);
  font-size: 12px;
}

:deep(.el-empty__description p) {
  color: var(--monitor-muted);
}

.admin-monitor-fullscreen {
  min-height: 100vh;
  padding: 24px;
}

.admin-monitor-fullscreen .monitor-hero,
.admin-monitor-fullscreen .monitor-card,
.admin-monitor-fullscreen .metric-card,
.admin-monitor-fullscreen .command-status,
.admin-monitor-fullscreen .signal-card,
.admin-monitor-fullscreen .broadcast-bar {
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.05), 0 24px 48px rgba(2, 6, 23, 0.34);
}

.admin-monitor-fullscreen .hero-copy h2 {
  font-size: 38px;
}

.admin-monitor-fullscreen .metric-value {
  font-size: 36px;
}

.admin-monitor-fullscreen .battle-item strong,
.admin-monitor-fullscreen .signal-card strong {
  font-size: 30px;
}

.admin-monitor-fullscreen .chart-box {
  height: 360px;
}

.admin-monitor-fullscreen .chart-box-large {
  height: 420px;
}

.admin-monitor-fullscreen .broadcast-loop {
  animation-duration: 18s;
}

@keyframes monitor-marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .broadcast-loop {
    animation: none;
  }
}

@media (max-width: 1600px) {
  .command-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .command-status {
    grid-column: 1 / -1;
  }
}

@media (max-width: 1360px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .command-strip,
  .monitor-grid-top,
  .monitor-grid-bottom {
    grid-template-columns: 1fr;
  }

  .broadcast-bar {
    grid-template-columns: 1fr;
    align-items: start;
  }
}

@media (max-width: 900px) {
  .admin-monitor-page,
  .admin-monitor-fullscreen {
    padding: 14px;
  }

  .monitor-hero {
    flex-direction: column;
    align-items: flex-start;
    border-radius: 20px;
  }

  .hero-copy,
  .hero-meta {
    width: 100%;
  }

  .hero-meta,
  .meta-actions {
    align-items: flex-start;
    justify-content: flex-start;
  }

  .meta-time {
    justify-items: start;
  }

  .metric-grid,
  .battle-grid {
    grid-template-columns: 1fr;
  }

  .metric-card,
  .monitor-card,
  .command-status,
  .signal-card,
  .broadcast-bar,
  .stock-panel {
    border-radius: 18px;
  }

  .stock-item {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .chart-box,
  .chart-box-large,
  .admin-monitor-fullscreen .chart-box,
  .admin-monitor-fullscreen .chart-box-large {
    height: 300px;
  }

  .hero-copy h2,
  .admin-monitor-fullscreen .hero-copy h2 {
    font-size: 32px;
  }

  .metric-value,
  .admin-monitor-fullscreen .metric-value {
    font-size: 30px;
  }

  .battle-item strong,
  .signal-card strong,
  .admin-monitor-fullscreen .battle-item strong,
  .admin-monitor-fullscreen .signal-card strong {
    font-size: 26px;
  }

  .broadcast-loop {
    gap: 28px;
    animation-duration: 20s;
  }

  .broadcast-loop span {
    font-size: 13px;
  }
}
</style>
