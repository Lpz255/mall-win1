<template>
  <div class="admin-user-page card-panel">
    <div class="panel-head">
      <h2>用户管理</h2>
    </div>

    <AdminSearchForm
      :model="searchForm"
      export-permission="admin:user:export"
      @search="handleSearch"
      @reset="handleReset"
      @export="handleExportExcel"
    >
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="用户名/手机号" clearable />
      </el-form-item>
      <el-form-item label="用户状态">
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 160px">
          <el-option label="正常" value="enabled" />
          <el-option label="禁用" value="disabled" />
        </el-select>
      </el-form-item>
    </AdminSearchForm>

    <el-table :data="tableData" border stripe class="data-table">
      <el-table-column prop="id" label="用户ID" min-width="90" />
      <el-table-column prop="name" label="用户名" min-width="120" />
      <el-table-column prop="phone" label="手机号" min-width="130" />
      <el-table-column prop="level" label="等级" min-width="90" />
      <el-table-column prop="status" label="状态" min-width="110">
        <template #default="{ row }">
          <el-tag :type="isEnabled(row.status) ? 'success' : 'danger'">
            {{ isEnabled(row.status) ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="registerTime" label="注册时间" min-width="170" />
      <el-table-column prop="lastLoginTime" label="最近登录" min-width="170" />
      <el-table-column label="操作" min-width="140" fixed="right">
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getAdminUserListApi, toggleAdminUserStatusApi } from '@/api';
import AdminActionButtons from '@/components/admin/AdminActionButtons.vue';
import AdminSearchForm from '@/components/admin/AdminSearchForm.vue';

const searchForm = reactive({
  keyword: '',
  status: ''
});

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});

const tableData = ref([]);

onMounted(() => {
  loadUserList();
});

async function loadUserList() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    keyword: searchForm.keyword || undefined,
    status: searchForm.status || undefined
  };

  try {
    const data = await getAdminUserListApi(params);
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
  loadUserList();
}

function handleReset() {
  searchForm.keyword = '';
  searchForm.status = '';
  pagination.pageNum = 1;
  loadUserList();
}

function onPageChange(value) {
  pagination.pageNum = value;
  loadUserList();
}

function onPageSizeChange(value) {
  pagination.pageSize = value;
  pagination.pageNum = 1;
  loadUserList();
}

function buildRowActions(row) {
  const target = isEnabled(row.status) ? 'disabled' : 'enabled';
  return [
    {
      key: 'toggle',
      label: target === 'enabled' ? '启用' : '禁用',
      permission: 'admin:user:toggle',
      confirmText: `确认${target === 'enabled' ? '启用' : '禁用'}该用户吗？`
    }
  ];
}

async function handleRowAction(actionKey, row) {
  if (actionKey !== 'toggle') {
    return;
  }

  const nextStatus = isEnabled(row.status) ? 'disabled' : 'enabled';
  await toggleAdminUserStatusApi(row.id, nextStatus);
  ElMessage.success(nextStatus === 'enabled' ? '用户启用成功' : '用户禁用成功');
  loadUserList();
}

async function handleExportExcel() {
  if (!tableData.value.length) {
    ElMessage.warning('暂无可导出的用户数据');
    return;
  }

  const XLSX = await import('xlsx');
  const exportRows = tableData.value.map((item) => ({
    用户ID: item.id || '',
    用户名: item.name || '',
    手机号: item.phone || '',
    用户等级: item.level || '',
    状态: isEnabled(item.status) ? '正常' : '禁用',
    注册时间: item.registerTime || '',
    最近登录: item.lastLoginTime || ''
  }));
  const worksheet = XLSX.utils.json_to_sheet(exportRows);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, '用户管理');
  XLSX.writeFile(workbook, `用户管理_${Date.now()}.xlsx`);
  ElMessage.success('Excel导出成功');
}

function isEnabled(status) {
  return ['enabled', 'ENABLE', 'ENABLED', 1, '1', true].includes(status);
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
</script>

<style scoped>
.admin-user-page {
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
