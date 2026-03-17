<template>
  <div class="page-container order-page">
    <section class="create-order card-panel">
      <div class="section-head">
        <h2 class="title-text">创建订单</h2>
        <p class="desc-text">可直接提交订单，或在购物车页面选择商品后结算</p>
      </div>

      <el-form :model="createForm" label-position="top" class="create-form">
        <el-form-item label="收货地址">
          <el-input v-model="createForm.address" placeholder="请输入收货地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="请输入订单备注（可选）" />
        </el-form-item>
      </el-form>

      <div class="create-actions">
        <el-button type="primary" color="#ff7a00" @click="handleCreateOrder">提交订单</el-button>
      </div>
    </section>

    <section class="order-list card-panel">
      <div class="section-head">
        <h2 class="title-text">我的订单</h2>
        <el-button @click="loadOrderList">刷新</el-button>
      </div>

      <el-table :data="orderList" border stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column prop="totalAmount" label="订单金额" min-width="120">
          <template #default="{ row }">￥{{ formatPrice(row.totalAmount || row.amount || 0) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" min-width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" min-width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="handleViewDetail(row)">查看详情</el-button>
            <el-button
              text
              type="danger"
              :disabled="!canCancel(row.status)"
              @click="handleCancelOrder(row)"
            >
              取消订单
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <AppPagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        @change="loadOrderList"
      />
    </section>

    <el-dialog v-model="detailDialogVisible" title="订单详情" width="680px">
      <div class="detail-grid">
        <p>订单号：{{ detailData.orderNo || '--' }}</p>
        <p>订单状态：{{ getStatusText(detailData.status) }}</p>
        <p>下单时间：{{ detailData.createTime || '--' }}</p>
        <p>收货地址：{{ detailData.address || '--' }}</p>
        <p>订单金额：￥{{ formatPrice(detailData.totalAmount || detailData.amount || 0) }}</p>
        <p>备注：{{ detailData.remark || '无' }}</p>
      </div>

      <el-table :data="normalizeItems(detailData.items)" border stripe>
        <el-table-column prop="name" label="商品名称" min-width="200" />
        <el-table-column prop="price" label="单价" min-width="120">
          <template #default="{ row }">￥{{ formatPrice(row.price || 0) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" min-width="100" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import AppPagination from '@/components/web/AppPagination.vue';
import { cancelOrderApi, createOrderApi, getOrderDetailApi, getOrderListApi } from '@/api';

const route = useRoute();
const router = useRouter();

const createForm = reactive({
  address: '',
  remark: ''
});

const orderList = ref([]);
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});
const detailDialogVisible = ref(false);
const detailData = ref({});

onMounted(() => {
  if (!checkLogin()) {
    return;
  }
  loadOrderList();
});

async function handleCreateOrder() {
  if (!createForm.address) {
    ElMessage.warning('请填写收货地址');
    return;
  }

  await createOrderApi({
    source: 'manual',
    address: createForm.address,
    remark: createForm.remark
  });
  ElMessage.success('订单创建成功');
  createForm.remark = '';
  await loadOrderList();
}

async function loadOrderList() {
  try {
    const data = await getOrderListApi({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    });
    const list = normalizeArray(data);
    orderList.value = list;
    pagination.total = Number(data?.total || data?.count || list.length || 0);
  } catch (_error) {
    orderList.value = [];
    pagination.total = 0;
  }
}

async function handleViewDetail(row) {
  const orderId = row.id || row.orderId;
  const data = await getOrderDetailApi(orderId);
  detailData.value = data || {};
  detailDialogVisible.value = true;
}

async function handleCancelOrder(row) {
  await ElMessageBox.confirm('确认取消该订单吗？', '提示', {
    confirmButtonText: '确认取消',
    cancelButtonText: '暂不取消',
    type: 'warning'
  });
  await cancelOrderApi(row.id || row.orderId);
  ElMessage.success('订单已取消');
  await loadOrderList();
}

function canCancel(status) {
  return ['pending', 'created', 'unpaid', 'WAIT_PAY', 0, 1].includes(status);
}

function getStatusType(status) {
  const map = {
    pending: 'warning',
    created: 'warning',
    unpaid: 'warning',
    paid: 'success',
    shipped: 'success',
    finished: 'success',
    canceled: 'info',
    timeout: 'danger',
    WAIT_PAY: 'warning',
    PAYED: 'success',
    DONE: 'success',
    CANCELED: 'info'
  };
  return map[status] || 'info';
}

function getStatusText(status) {
  const map = {
    pending: '待支付',
    created: '待支付',
    unpaid: '待支付',
    paid: '已支付',
    shipped: '已发货',
    finished: '已完成',
    canceled: '已取消',
    timeout: '已超时',
    WAIT_PAY: '待支付',
    PAYED: '已支付',
    DONE: '已完成',
    CANCELED: '已取消',
    0: '待支付',
    1: '待支付',
    2: '已支付',
    3: '已完成',
    4: '已取消'
  };
  return map[status] || String(status || '未知状态');
}

function checkLogin() {
  if (localStorage.getItem('token')) {
    return true;
  }
  ElMessage.warning('请先登录后查看订单');
  router.push({
    path: '/web/login',
    query: {
      redirect: route.fullPath
    }
  });
  return false;
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

function normalizeItems(data) {
  if (Array.isArray(data)) {
    return data;
  }
  if (Array.isArray(data?.list)) {
    return data.list;
  }
  return [];
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.order-page {
  display: grid;
  gap: 16px;
}

.create-order,
.order-list {
  padding: 16px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.create-form {
  max-width: 860px;
}

.create-actions {
  margin-top: 6px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
  color: #334155;
  font-size: 14px;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>

