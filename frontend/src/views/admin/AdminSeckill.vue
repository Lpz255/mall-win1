<template>
  <div class="admin-seckill-page card-panel">
    <div class="panel-head">
      <h2>秒杀管理</h2>
      <el-button v-permission="'admin:seckill:create'" type="primary" color="#1677ff" @click="openCreateDialog">
        新增秒杀活动
      </el-button>
    </div>

    <AdminSearchForm
      :model="searchForm"
      export-permission="admin:seckill:export"
      @search="handleSearch"
      @reset="handleReset"
      @export="handleExportExcel"
    >
      <el-form-item label="商品ID">
        <el-input v-model="searchForm.productId" placeholder="请输入商品ID" clearable />
      </el-form-item>
      <el-form-item label="活动状态">
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </AdminSearchForm>

    <el-table :data="tableData" border stripe class="data-table">
      <el-table-column prop="id" label="活动ID" min-width="90" />
      <el-table-column prop="productId" label="商品ID" min-width="90" />
      <el-table-column prop="productName" label="商品名称" min-width="200" show-overflow-tooltip />
      <el-table-column prop="seckillPrice" label="秒杀价" min-width="100">
        <template #default="{ row }">￥{{ formatPrice(row.seckillPrice) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" min-width="90" />
      <el-table-column prop="status" label="状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" min-width="170" />
      <el-table-column prop="endTime" label="结束时间" min-width="170" />
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增秒杀活动' : '编辑秒杀活动'" width="680px">
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="100px">
        <el-form-item label="商品ID" prop="productId">
          <el-input v-model="dialogForm.productId" placeholder="请输入商品ID" />
        </el-form-item>
        <el-form-item label="商品名称" prop="productName">
          <el-input v-model="dialogForm.productName" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="秒杀价格" prop="seckillPrice">
          <el-input-number
            v-model="dialogForm.seckillPrice"
            :min="0.01"
            :precision="2"
            :step="1"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="秒杀库存" prop="stock">
          <el-input-number v-model="dialogForm.stock" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-date-picker
            v-model="dialogForm.startTime"
            type="datetime"
            placeholder="请选择开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-date-picker
            v-model="dialogForm.endTime"
            type="datetime"
            placeholder="请选择结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="活动状态" prop="status">
          <el-radio-group v-model="dialogForm.status">
            <el-radio-button label="draft">草稿</el-radio-button>
            <el-radio-button label="running">进行中</el-radio-button>
            <el-radio-button label="stopped">已停止</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" color="#1677ff" @click="submitDialog">
          {{ dialogMode === 'create' ? '确认新增' : '确认保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  createAdminSeckillApi,
  deleteAdminSeckillApi,
  getAdminSeckillListApi,
  startAdminSeckillApi,
  stopAdminSeckillApi,
  updateAdminSeckillApi
} from '@/api';
import AdminActionButtons from '@/components/admin/AdminActionButtons.vue';
import AdminSearchForm from '@/components/admin/AdminSearchForm.vue';

const statusOptions = [
  { label: '草稿', value: 'draft' },
  { label: '进行中', value: 'running' },
  { label: '已停止', value: 'stopped' },
  { label: '已结束', value: 'ended' }
];

const searchForm = reactive({
  productId: '',
  status: ''
});

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});

const tableData = ref([]);
const dialogVisible = ref(false);
const dialogMode = ref('create');
const dialogFormRef = ref();
const dialogForm = reactive({
  id: null,
  productId: '',
  productName: '',
  seckillPrice: 9.9,
  stock: 10,
  startTime: '',
  endTime: '',
  status: 'draft'
});

const dialogRules = {
  productId: [{ required: true, message: '请输入商品ID', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  seckillPrice: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (Number(value) <= 0) {
          callback(new Error('秒杀价必须大于0'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ],
  stock: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (!Number.isInteger(Number(value)) || Number(value) <= 0) {
          callback(new Error('库存必须为正整数'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (!value) {
          callback(new Error('请选择结束时间'));
          return;
        }
        if (dialogForm.startTime && new Date(value).getTime() <= new Date(dialogForm.startTime).getTime()) {
          callback(new Error('结束时间必须晚于开始时间'));
          return;
        }
        callback();
      },
      trigger: 'change'
    }
  ]
};

onMounted(() => {
  loadSeckillList();
});

async function loadSeckillList() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    productId: searchForm.productId || undefined,
    status: searchForm.status || undefined
  };
  try {
    const data = await getAdminSeckillListApi(params);
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
  loadSeckillList();
}

function handleReset() {
  searchForm.productId = '';
  searchForm.status = '';
  pagination.pageNum = 1;
  loadSeckillList();
}

function onPageChange(value) {
  pagination.pageNum = value;
  loadSeckillList();
}

function onPageSizeChange(value) {
  pagination.pageSize = value;
  pagination.pageNum = 1;
  loadSeckillList();
}

function openCreateDialog() {
  dialogMode.value = 'create';
  resetDialogForm();
  dialogVisible.value = true;
}

function openEditDialog(row) {
  dialogMode.value = 'edit';
  dialogForm.id = row.id || null;
  dialogForm.productId = row.productId || '';
  dialogForm.productName = row.productName || row.name || '';
  dialogForm.seckillPrice = Number(row.seckillPrice || 0);
  dialogForm.stock = Number(row.stock || 1);
  dialogForm.startTime = row.startTime || '';
  dialogForm.endTime = row.endTime || '';
  dialogForm.status = row.status || 'draft';
  dialogVisible.value = true;
}

async function submitDialog() {
  await dialogFormRef.value?.validate();

  const payload = {
    id: dialogForm.id || undefined,
    productId: dialogForm.productId,
    productName: dialogForm.productName,
    seckillPrice: Number(dialogForm.seckillPrice),
    stock: Number(dialogForm.stock),
    startTime: dialogForm.startTime,
    endTime: dialogForm.endTime,
    status: dialogForm.status
  };

  if (dialogMode.value === 'create') {
    await createAdminSeckillApi(payload);
    ElMessage.success('秒杀活动新增成功');
  } else {
    await updateAdminSeckillApi(payload);
    ElMessage.success('秒杀活动编辑成功');
  }

  dialogVisible.value = false;
  loadSeckillList();
}

function buildRowActions(row) {
  return [
    {
      key: 'edit',
      label: '编辑',
      permission: 'admin:seckill:edit'
    },
    {
      key: 'start',
      label: '启动',
      permission: 'admin:seckill:start',
      confirmText: '确认启动该秒杀活动吗？'
    },
    {
      key: 'stop',
      label: '停止',
      permission: 'admin:seckill:stop',
      confirmText: '确认停止该秒杀活动吗？'
    },
    {
      key: 'delete',
      label: '删除',
      type: 'danger',
      permission: 'admin:seckill:delete',
      confirmText: '确认删除该秒杀活动吗？删除后不可恢复。'
    }
  ];
}

async function handleRowAction(actionKey, row) {
  const id = row.id;
  if (actionKey === 'edit') {
    openEditDialog(row);
    return;
  }

  if (actionKey === 'start') {
    await startAdminSeckillApi(id);
    ElMessage.success('秒杀活动已启动');
    loadSeckillList();
    return;
  }

  if (actionKey === 'stop') {
    await stopAdminSeckillApi(id);
    ElMessage.success('秒杀活动已停止');
    loadSeckillList();
    return;
  }

  if (actionKey === 'delete') {
    await deleteAdminSeckillApi(id);
    ElMessage.success('秒杀活动删除成功');
    loadSeckillList();
  }
}

async function handleExportExcel() {
  if (!tableData.value.length) {
    ElMessage.warning('暂无可导出的秒杀数据');
    return;
  }

  const XLSX = await import('xlsx');
  const exportRows = tableData.value.map((item) => ({
    活动ID: item.id || '',
    商品ID: item.productId || '',
    商品名称: item.productName || '',
    秒杀价: Number(item.seckillPrice || 0).toFixed(2),
    库存: item.stock ?? '',
    状态: statusText(item.status),
    开始时间: item.startTime || '',
    结束时间: item.endTime || ''
  }));
  const worksheet = XLSX.utils.json_to_sheet(exportRows);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, '秒杀管理');
  XLSX.writeFile(workbook, `秒杀管理_${Date.now()}.xlsx`);
  ElMessage.success('Excel导出成功');
}

function statusText(status) {
  const map = {
    draft: '草稿',
    running: '进行中',
    stopped: '已停止',
    ended: '已结束',
    DRAFT: '草稿',
    RUNNING: '进行中',
    STOPPED: '已停止',
    ENDED: '已结束'
  };
  return map[status] || String(status || '未知状态');
}

function statusTagType(status) {
  const map = {
    draft: 'info',
    running: 'success',
    stopped: 'warning',
    ended: 'danger',
    DRAFT: 'info',
    RUNNING: 'success',
    STOPPED: 'warning',
    ENDED: 'danger'
  };
  return map[status] || 'info';
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

function resetDialogForm() {
  dialogForm.id = null;
  dialogForm.productId = '';
  dialogForm.productName = '';
  dialogForm.seckillPrice = 9.9;
  dialogForm.stock = 10;
  dialogForm.startTime = '';
  dialogForm.endTime = '';
  dialogForm.status = 'draft';
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.admin-seckill-page {
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
</style>
