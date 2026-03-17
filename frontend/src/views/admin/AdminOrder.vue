<template>
  <div class="admin-order-page card-panel">
    <div class="panel-head">
      <h2>订单管理</h2>
    </div>

    <AdminSearchForm
      :model="searchForm"
      export-permission="admin:order:export"
      @search="handleSearch"
      @reset="handleReset"
      @export="handleExportExcel"
    >
      <el-form-item label="订单号">
        <el-input v-model="searchForm.orderNo" placeholder="请输入订单号" clearable />
      </el-form-item>
      <el-form-item label="用户信息">
        <el-input v-model="searchForm.userKeyword" placeholder="用户名/手机号" clearable />
      </el-form-item>
      <el-form-item label="订单状态">
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </AdminSearchForm>

    <el-table :data="tableData" border stripe class="data-table">
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column prop="userName" label="用户" min-width="120" />
      <el-table-column prop="phone" label="手机号" min-width="130" />
      <el-table-column prop="totalAmount" label="订单金额" min-width="120">
        <template #default="{ row }">￥{{ formatPrice(row.totalAmount || row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="订单状态" min-width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="refundStatus" label="退款状态" min-width="120">
        <template #default="{ row }">{{ refundText(row.refundStatus) }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" min-width="260" fixed="right">
        <template #default="{ row }">
          <AdminActionButtons :row="row" :actions="buildRowActions(row)" @action="handleRowAction" />
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        background
        layout="total, prev, pager, next, sizes, jumper"
        :total="pagination.total"
        :current-page="pagination.pageNum"
        :page-size="pagination.pageSize"
        :page-sizes="[10, 20, 30, 50]"
        @update:current-page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </div>

    <el-dialog v-model="statusDialogVisible" title="修改订单状态" width="480px">
      <el-form :model="statusForm" label-width="90px">
        <el-form-item label="订单号">
          <el-input v-model="statusForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="新状态">
          <el-select v-model="statusForm.status" style="width: 100%">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" color="#1677ff" @click="submitStatusUpdate">确认修改</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialogVisible" title="订单详情" width="720px">
      <div class="detail-grid">
        <p>订单号：{{ detailData.orderNo || '--' }}</p>
        <p>订单状态：{{ statusText(detailData.status) }}</p>
        <p>创建时间：{{ detailData.createTime || '--' }}</p>
        <p>用户：{{ detailData.userName || '--' }}</p>
        <p>手机号：{{ detailData.phone || '--' }}</p>
        <p>收货地址：{{ detailData.address || '--' }}</p>
      </div>

      <el-table :data="normalizeItems(detailData.items)" border stripe>
        <el-table-column prop="name" label="商品名称" min-width="200" />
        <el-table-column prop="price" label="单价" min-width="100">
          <template #default="{ row }">￥{{ formatPrice(row.price) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" min-width="80" />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  getAdminOrderDetailApi,
  getAdminOrderListApi,
  refundAdminOrderApi,
  updateAdminOrderStatusApi
} from '@/api';
import AdminActionButtons from '@/components/admin/AdminActionButtons.vue';
import AdminSearchForm from '@/components/admin/AdminSearchForm.vue';

const statusOptions = [
  { label: '待支付', value: 'WAIT_PAY' },
  { label: '已支付', value: 'PAYED' },
  { label: '已发货', value: 'SHIPPED' },
  { label: '已完成', value: 'DONE' },
  { label: '已取消', value: 'CANCELED' }
];

const searchForm = reactive({
  orderNo: '',
  userKeyword: '',
  status: ''
});

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});

const tableData = ref([]);
const statusDialogVisible = ref(false);
const detailDialogVisible = ref(false);
const detailData = ref({});

const statusForm = reactive({
  orderId: '',
  orderNo: '',
  status: 'WAIT_PAY'
});

onMounted(() => {
  loadOrderList();
});

async function loadOrderList() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    orderNo: searchForm.orderNo || undefined,
    userKeyword: searchForm.userKeyword || undefined,
    status: searchForm.status || undefined
  };

  try {
    const data = await getAdminOrderListApi(params);
    const list = normalizeArray(data);
    tableData.value = list;
    pagination.total = Number(data?.total || data?.count || list.length || 0);
  } catch (_error) {
    tableData.value = [];
    pagination.total = 0;
  }
}

function handleSearch() {
  pagination.pageNum = 1;
  loadOrderList();
}

function handleReset() {
  searchForm.orderNo = '';
  searchForm.userKeyword = '';
  searchForm.status = '';
  pagination.pageNum = 1;
  loadOrderList();
}

function onPageChange(value) {
  pagination.pageNum = value;
  loadOrderList();
}

function onPageSizeChange(value) {
  pagination.pageSize = value;
  pagination.pageNum = 1;
  loadOrderList();
}

function buildRowActions(row) {
  return [
    {
      key: 'detail',
      label: '详情',
      permission: 'admin:order:detail'
    },
    {
      key: 'status',
      label: '改状态',
      permission: 'admin:order:status'
    },
    {
      key: 'refund',
      label: '退款',
      type: 'danger',
      permission: 'admin:order:refund',
      confirmText: '确认发起退款处理吗？'
    }
  ];
}

async function handleRowAction(actionKey, row) {
  if (actionKey === 'detail') {
    const detail = await getAdminOrderDetailApi(row.id || row.orderId);
    detailData.value = detail || {};
    detailDialogVisible.value = true;
    return;
  }

  if (actionKey === 'status') {
    statusForm.orderId = row.id || row.orderId;
    statusForm.orderNo = row.orderNo || '';
    statusForm.status = row.status || 'WAIT_PAY';
    statusDialogVisible.value = true;
    return;
  }

  if (actionKey === 'refund') {
    await refundAdminOrderApi({
      orderId: row.id || row.orderId,
      refundAmount: row.totalAmount || row.amount || 0,
      reason: '运营后台手动退款'
    });
    ElMessage.success('退款处理成功');
    loadOrderList();
  }
}

async function submitStatusUpdate() {
  await updateAdminOrderStatusApi({
    orderId: statusForm.orderId,
    status: statusForm.status
  });
  statusDialogVisible.value = false;
  ElMessage.success('订单状态修改成功');
  loadOrderList();
}

async function handleExportExcel() {
  if (!tableData.value.length) {
    ElMessage.warning('暂无可导出的订单数据');
    return;
  }

  const XLSX = await import('xlsx');
  const exportRows = tableData.value.map((item) => ({
    订单号: item.orderNo || '',
    用户: item.userName || '',
    手机号: item.phone || '',
    订单金额: Number(item.totalAmount || item.amount || 0).toFixed(2),
    订单状态: statusText(item.status),
    退款状态: refundText(item.refundStatus),
    创建时间: item.createTime || ''
  }));
  const worksheet = XLSX.utils.json_to_sheet(exportRows);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, '订单管理');
  XLSX.writeFile(workbook, `订单管理_${Date.now()}.xlsx`);
  ElMessage.success('Excel导出成功');
}

function statusText(status) {
  const map = {
    WAIT_PAY: '待支付',
    PAYED: '已支付',
    SHIPPED: '已发货',
    DONE: '已完成',
    CANCELED: '已取消',
    wait_pay: '待支付',
    payed: '已支付',
    shipped: '已发货',
    done: '已完成',
    canceled: '已取消'
  };
  return map[status] || String(status || '未知状态');
}

function statusType(status) {
  const map = {
    WAIT_PAY: 'warning',
    PAYED: 'success',
    SHIPPED: 'primary',
    DONE: 'success',
    CANCELED: 'info',
    wait_pay: 'warning',
    payed: 'success',
    shipped: 'primary',
    done: 'success',
    canceled: 'info'
  };
  return map[status] || 'info';
}

function refundText(status) {
  const map = {
    none: '未退款',
    pending: '退款中',
    success: '已退款',
    fail: '退款失败',
    NONE: '未退款',
    PENDING: '退款中',
    SUCCESS: '已退款',
    FAIL: '退款失败'
  };
  return map[status] || String(status || '未退款');
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

function normalizeItems(items) {
  if (Array.isArray(items)) {
    return items;
  }
  if (Array.isArray(items?.list)) {
    return items.list;
  }
  return [];
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.admin-order-page {
  padding: 16px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.panel-head h2 {
  color: #0f172a;
  font-size: 20px;
}

.data-table {
  margin-top: 10px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
  color: #334155;
  font-size: 14px;
}
</style>
