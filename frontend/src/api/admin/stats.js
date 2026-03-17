import request from '@/api/request';

// 后台首页数据概览
export function getAdminOverviewApi(params = {}) {
  return request.get('/admin/stats/overview', { params });
}

