import request from '@/api/request';

// 创建订单
export function createOrderApi(payload) {
  return request.post('/order/create', payload);
}

// 订单列表
export function getOrderListApi(params = {}) {
  return request.get('/order/list', { params });
}

// 订单详情
export function getOrderDetailApi(orderId) {
  return request.get('/order/detail', {
    params: { orderId }
  });
}

// 取消订单
export function cancelOrderApi(orderId) {
  return request.post('/order/cancel', { orderId });
}

