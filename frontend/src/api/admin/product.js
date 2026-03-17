import request from '@/api/request';

// 商品列表
export function getAdminProductListApi(params) {
  return request.get('/admin/product/list', { params });
}

// 新增商品
export function createAdminProductApi(payload) {
  return request.post('/admin/product/create', payload);
}

// 编辑商品
export function updateAdminProductApi(payload) {
  return request.post('/admin/product/update', payload);
}

// 删除商品
export function deleteAdminProductApi(id) {
  return request.post('/admin/product/delete', { id });
}

// 上下架
export function toggleAdminProductStatusApi(id, status) {
  return request.post('/admin/product/status', { id, status });
}

// 上传商品图片（MinIO）
export function uploadAdminProductImageApi(formData) {
  return request.post('/admin/product/upload-image', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    adaptRequest: false
  });
}
