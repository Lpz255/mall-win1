import request from '@/api/request';

// 秒杀活动列表
export function getAdminSeckillListApi(params) {
  return request.get('/admin/seckill/list', { params });
}

// 新增秒杀活动
export function createAdminSeckillApi(payload) {
  return request.post('/admin/seckill/create', payload);
}

// 编辑秒杀活动
export function updateAdminSeckillApi(payload) {
  return request.post('/admin/seckill/update', payload);
}

// 启动秒杀活动
export function startAdminSeckillApi(id) {
  return request.post('/admin/seckill/start', { id });
}

// 停止秒杀活动
export function stopAdminSeckillApi(id) {
  return request.post('/admin/seckill/stop', { id });
}

// 删除秒杀活动
export function deleteAdminSeckillApi(id) {
  return request.post('/admin/seckill/delete', { id });
}

