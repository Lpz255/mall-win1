import request from '@/api/request';

// 管理员账号密码登录
export function adminLoginApi(payload) {
  return request.post('/admin/login', payload);
}

// 获取当前管理员信息
export function getAdminProfileApi() {
  return request.get('/admin/profile');
}

// 获取 RBAC 权限列表
export function getAdminPermissionApi() {
  return request.get('/admin/rbac/permissions');
}

