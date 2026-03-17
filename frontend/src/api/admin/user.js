import request from '@/api/request';

// 用户列表
export function getAdminUserListApi(params) {
  return request.get('/admin/user/list', { params });
}

// 用户启用/禁用
export function toggleAdminUserStatusApi(id, status) {
  return request.post('/admin/user/status', { id, status });
}

