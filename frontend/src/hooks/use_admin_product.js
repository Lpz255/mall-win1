import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  clearProductCache,
  createAdminProductApi,
  deleteAdminProductApi,
  getAdminProductListApi,
  toggleAdminProductStatusApi,
  uploadAdminProductImageApi,
  updateAdminProductApi
} from '@/api';

const DEFAULT_SEARCH_FORM = {
  keyword: '',
  categoryId: '',
  status: ''
};

const DEFAULT_DIALOG_FORM = {
  id: null,
  name: '',
  categoryId: '',
  price: 99,
  stock: 1,
  status: 'on_sale',
  image: '',
  description: ''
};

const MAX_IMAGE_SIZE_MB = 5;

export function useAdminProduct() {
  const searchForm = reactive(createSearchForm());
  const pagination = reactive({
    pageNum: 1,
    pageSize: 10,
    total: 0
  });
  const tableData = ref([]);
  const dialogVisible = ref(false);
  const dialogMode = ref('create');
  const imageUploading = ref(false);
  const dialogForm = reactive(createDialogForm());

  function createSearchForm() {
    return {
      ...DEFAULT_SEARCH_FORM
    };
  }

  function createDialogForm() {
    return {
      ...DEFAULT_DIALOG_FORM
    };
  }

  function buildListParams() {
    return {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      keyword: searchForm.keyword || undefined,
      categoryId: searchForm.categoryId || undefined,
      status: searchForm.status || undefined
    };
  }

  function applyDialogForm(values) {
    Object.assign(dialogForm, {
      ...DEFAULT_DIALOG_FORM,
      ...values
    });
  }

  function fillDialogForm(row = {}) {
    applyDialogForm({
      id: row.id || row.productId || null,
      name: row.name || row.productName || '',
      categoryId: row.categoryId || '',
      price: Number(row.price || row.salePrice || 0),
      stock: Number(row.stock || 1),
      status: isOnSale(row.status) ? 'on_sale' : 'off_sale',
      image: row.image || row.cover || '',
      description: row.description || ''
    });
  }

  function resetDialogForm() {
    applyDialogForm(DEFAULT_DIALOG_FORM);
  }

  function resetSearchForm() {
    Object.assign(searchForm, DEFAULT_SEARCH_FORM);
  }

  async function loadProductList() {
    try {
      const data = await getAdminProductListApi(buildListParams());
      const list = normalizeArray(data);
      tableData.value = list;
      pagination.total = Number(data?.total || data?.count || list.length || 0);
    } catch (_error) {
      tableData.value = [];
      pagination.total = 0;
    }
  }

  function openCreateDialog() {
    dialogMode.value = 'create';
    resetDialogForm();
    dialogVisible.value = true;
  }

  function openEditDialog(row) {
    dialogMode.value = 'edit';
    fillDialogForm(row);
    dialogVisible.value = true;
  }

  function buildSubmitPayload() {
    return {
      id: dialogForm.id || undefined,
      name: dialogForm.name,
      categoryId: dialogForm.categoryId,
      price: Number(dialogForm.price),
      stock: Number(dialogForm.stock),
      status: dialogForm.status,
      image: dialogForm.image,
      description: dialogForm.description
    };
  }

  async function submitDialog() {
    const payload = buildSubmitPayload();

    if (dialogMode.value === 'create') {
      await createAdminProductApi(payload);
      ElMessage.success('商品新增成功');
    } else {
      await updateAdminProductApi(payload);
      ElMessage.success('商品编辑成功');
    }

    clearProductCache();
    dialogVisible.value = false;
    await loadProductList();
  }

  function validateImageFile(file) {
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
    const productId = row.id || row.productId;
    if (actionKey === 'edit') {
      openEditDialog(row);
      return;
    }

    if (actionKey === 'toggle') {
      const nextStatus = isOnSale(row.status) ? 'off_sale' : 'on_sale';
      await toggleAdminProductStatusApi(productId, nextStatus);
      ElMessage.success(nextStatus === 'on_sale' ? '商品上架成功' : '商品下架成功');
      clearProductCache();
      await loadProductList();
      return;
    }

    if (actionKey === 'delete') {
      await deleteAdminProductApi(productId);
      ElMessage.success('商品删除成功');
      clearProductCache();
      await loadProductList();
    }
  }

  return {
    searchForm,
    pagination,
    tableData,
    dialogVisible,
    dialogMode,
    imageUploading,
    dialogForm,
    resetSearchForm,
    resetDialogForm,
    loadProductList,
    openCreateDialog,
    openEditDialog,
    submitDialog,
    validateImageFile,
    handleImageUpload,
    buildRowActions,
    handleRowAction
  };
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
