import request from '@/api/request';

// 订单列表
export function getAdminOrderListApi(params) {
  return request.get('/admin/order/list', { params });
}

// 修改订单状态
export function updateAdminOrderStatusApi(payload) {
  return request.post('/admin/order/status', payload);
}

// 退款处理
export function refundAdminOrderApi(payload) {
  return request.post('/admin/order/refund', payload);
}

// 订单详情
export function getAdminOrderDetailApi(orderId) {
  return request.get('/admin/order/detail', {
    params: { orderId }
  });
}

