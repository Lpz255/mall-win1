<template>
  <div class="admin-product-page card-panel">
    <div class="panel-head">
      <h2>商品管理</h2>
      <el-button v-permission="'admin:product:create'" type="primary" color="#1677ff" @click="openCreateDialog">
        新增商品
      </el-button>
    </div>

    <AdminSearchForm
      :model="searchForm"
      export-permission="admin:product:export"
      @search="handleSearch"
      @reset="handleReset"
      @export="handleExportExcel"
    >
      <el-form-item label="关键词">
        <el-input v-model="searchForm.keyword" placeholder="商品名称/ID" clearable />
      </el-form-item>
      <el-form-item label="分类">
        <el-select v-model="searchForm.categoryId" placeholder="全部分类" clearable style="width: 160px">
          <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
    </AdminSearchForm>

    <el-table :data="tableData" border stripe class="data-table">
      <el-table-column prop="id" label="商品ID" min-width="90" />
      <el-table-column prop="name" label="商品名称" min-width="220" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" min-width="120" />
      <el-table-column prop="price" label="价格" min-width="100">
        <template #default="{ row }">￥{{ formatPrice(row.price) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" min-width="90" />
      <el-table-column prop="sales" label="销量" min-width="90" />
      <el-table-column prop="status" label="状态" min-width="100">
        <template #default="{ row }">
          <el-tag :type="isOnSale(row.status) ? 'success' : 'info'">
            {{ isOnSale(row.status) ? '上架中' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column label="操作" min-width="220" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增商品' : '编辑商品'" width="680px">
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="90px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="dialogForm.name" placeholder="请输入商品名称" />
        </el-form-item>
        <el-form-item label="商品分类" prop="categoryId">
          <el-select v-model="dialogForm.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="售价" prop="price">
          <el-input-number v-model="dialogForm.price" :min="0.01" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="dialogForm.stock" :min="1" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="dialogForm.status">
            <el-radio-button label="on_sale">上架</el-radio-button>
            <el-radio-button label="off_sale">下架</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="商品图片" prop="image">
          <div class="image-upload-wrap">
            <div class="image-upload-toolbar">
              <el-upload
                :show-file-list="false"
                accept="image/*"
                :http-request="handleImageUpload"
                :before-upload="beforeImageUpload"
                :disabled="imageUploading"
              >
                <el-button :loading="imageUploading" type="primary" plain>上传到 MinIO</el-button>
              </el-upload>
              <el-button v-if="dialogForm.image" link type="danger" @click="dialogForm.image = ''">清空图片</el-button>
            </div>
            <el-input v-model="dialogForm.image" placeholder="上传后自动回填图片URL" readonly />
            <el-image
              v-if="dialogForm.image"
              :src="dialogForm.image"
              fit="cover"
              class="image-preview"
              :preview-src-list="[dialogForm.image]"
              preview-teleported
            />
          </div>
        </el-form-item>
        <el-form-item label="商品描述" prop="description">
          <el-input v-model="dialogForm.description" type="textarea" :rows="3" placeholder="请输入商品描述（可选）" />
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
  clearProductCache,
  createAdminProductApi,
  deleteAdminProductApi,
  getAdminProductListApi,
  getProductCategoryApi,
  toggleAdminProductStatusApi,
  uploadAdminProductImageApi,
  updateAdminProductApi
} from '@/api';
import AdminActionButtons from '@/components/admin/AdminActionButtons.vue';
import AdminSearchForm from '@/components/admin/AdminSearchForm.vue';

const statusOptions = [
  { label: '上架中', value: 'on_sale' },
  { label: '已下架', value: 'off_sale' }
];
const MAX_IMAGE_SIZE_MB = 5;

const searchForm = reactive({
  keyword: '',
  categoryId: '',
  status: ''
});

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});

const categoryOptions = ref([]);
const tableData = ref([]);

const dialogVisible = ref(false);
const dialogMode = ref('create');
const dialogFormRef = ref();
const imageUploading = ref(false);
const dialogForm = reactive({
  id: null,
  name: '',
  categoryId: '',
  price: 99,
  stock: 1,
  status: 'on_sale',
  image: '',
  description: ''
});

const dialogRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择商品分类', trigger: 'change' }],
  price: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (Number(value) <= 0) {
          callback(new Error('售价必须大于0'));
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
  ]
};

onMounted(() => {
  loadCategories();
  loadProductList();
});

async function loadCategories() {
  try {
    const data = await getProductCategoryApi();
    categoryOptions.value = normalizeArray(data);
  } catch (_error) {
    categoryOptions.value = [];
  }
}

async function loadProductList() {
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    keyword: searchForm.keyword || undefined,
    categoryId: searchForm.categoryId || undefined,
    status: searchForm.status || undefined
  };
  try {
    const data = await getAdminProductListApi(params);
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
  loadProductList();
}

function handleReset() {
  searchForm.keyword = '';
  searchForm.categoryId = '';
  searchForm.status = '';
  pagination.pageNum = 1;
  loadProductList();
}

function onPageChange(value) {
  pagination.pageNum = value;
  loadProductList();
}

function onPageSizeChange(value) {
  pagination.pageSize = value;
  pagination.pageNum = 1;
  loadProductList();
}

function openCreateDialog() {
  dialogMode.value = 'create';
  resetDialogForm();
  dialogVisible.value = true;
}

function openEditDialog(row) {
  dialogMode.value = 'edit';
  dialogForm.id = row.id || row.productId || null;
  dialogForm.name = row.name || row.productName || '';
  dialogForm.categoryId = row.categoryId || '';
  dialogForm.price = Number(row.price || row.salePrice || 0);
  dialogForm.stock = Number(row.stock || 1);
  dialogForm.status = isOnSale(row.status) ? 'on_sale' : 'off_sale';
  dialogForm.image = row.image || row.cover || '';
  dialogForm.description = row.description || '';
  dialogVisible.value = true;
}

function beforeImageUpload(file) {
  const isImage = String(file?.type || '').startsWith('image/');
  if (!isImage) {
    ElMessage.warning('只能上传图片文件');
    return false;
  }

  const sizeInMb = Number(file?.size || 0) / 1024 / 1024;
  if (sizeInMb > MAX_IMAGE_SIZE_MB) {
    ElMessage.warning(`图片大小不能超过${MAX_IMAGE_SIZE_MB}MB`);
    return false;
  }
  return true;
}

async function handleImageUpload(option) {
  const formData = new FormData();
  formData.append('file', option.file);
  imageUploading.value = true;
  try {
    const data = await uploadAdminProductImageApi(formData);
    const imageUrl = data?.url || '';
    if (!imageUrl) {
      throw new Error('上传成功但未返回图片URL');
    }
    dialogForm.image = imageUrl;
    ElMessage.success('图片上传成功');
    option.onSuccess?.(data);
  } catch (error) {
    option.onError?.(error);
  } finally {
    imageUploading.value = false;
  }
}

async function submitDialog() {
  await dialogFormRef.value?.validate();

  const payload = {
    id: dialogForm.id || undefined,
    name: dialogForm.name,
    categoryId: dialogForm.categoryId,
    price: Number(dialogForm.price),
    stock: Number(dialogForm.stock),
    status: dialogForm.status,
    image: dialogForm.image,
    description: dialogForm.description
  };

  if (dialogMode.value === 'create') {
    await createAdminProductApi(payload);
    ElMessage.success('商品新增成功');
  } else {
    await updateAdminProductApi(payload);
    ElMessage.success('商品编辑成功');
  }
  clearProductCache();

  dialogVisible.value = false;
  loadProductList();
}

function buildRowActions(row) {
  const nextStatus = isOnSale(row.status) ? 'off_sale' : 'on_sale';
  return [
    {
      key: 'edit',
      label: '编辑',
      permission: 'admin:product:edit'
    },
    {
      key: 'toggle',
      label: isOnSale(row.status) ? '下架' : '上架',
      permission: 'admin:product:toggle',
      confirmText: `确认${isOnSale(row.status) ? '下架' : '上架'}该商品吗？`,
      meta: {
        nextStatus
      }
    },
    {
      key: 'delete',
      label: '删除',
      type: 'danger',
      permission: 'admin:product:delete',
      confirmText: '确认删除该商品吗？删除后不可恢复。'
    }
  ];
}

async function handleRowAction(actionKey, row) {
  if (actionKey === 'edit') {
    openEditDialog(row);
    return;
  }

  if (actionKey === 'toggle') {
    // 核心操作逻辑：商品上下架前增加二次确认弹窗，避免误操作
    const nextStatus = isOnSale(row.status) ? 'off_sale' : 'on_sale';
    await toggleAdminProductStatusApi(row.id || row.productId, nextStatus);
    ElMessage.success(nextStatus === 'on_sale' ? '商品上架成功' : '商品下架成功');
    clearProductCache();
    loadProductList();
    return;
  }

  if (actionKey === 'delete') {
    await deleteAdminProductApi(row.id || row.productId);
    ElMessage.success('商品删除成功');
    clearProductCache();
    loadProductList();
  }
}

async function handleExportExcel() {
  if (!tableData.value.length) {
    ElMessage.warning('暂无可导出的商品数据');
    return;
  }

  const XLSX = await import('xlsx');
  const exportRows = tableData.value.map((item) => ({
    商品ID: item.id || item.productId || '',
    商品名称: item.name || item.productName || '',
    分类: item.categoryName || '',
    价格: Number(item.price || item.salePrice || 0).toFixed(2),
    库存: item.stock ?? '',
    销量: item.sales ?? 0,
    状态: isOnSale(item.status) ? '上架中' : '已下架',
    创建时间: item.createTime || ''
  }));

  const worksheet = XLSX.utils.json_to_sheet(exportRows);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, '商品管理');
  XLSX.writeFile(workbook, `商品管理_${Date.now()}.xlsx`);
  ElMessage.success('Excel导出成功');
}

function isOnSale(status) {
  return ['on_sale', 'ON_SALE', 1, '1', true].includes(status);
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
  dialogForm.name = '';
  dialogForm.categoryId = '';
  dialogForm.price = 99;
  dialogForm.stock = 1;
  dialogForm.status = 'on_sale';
  dialogForm.image = '';
  dialogForm.description = '';
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}
</script>

<style scoped>
.admin-product-page {
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

.image-upload-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}

.image-upload-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.image-preview {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px solid #d9e2ef;
}
</style>
