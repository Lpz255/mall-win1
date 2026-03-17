import request from '@/api/request';

// 购物车列表
export function getCartListApi() {
  return request.get('/cart/list');
}

// 加入购物车
export function addCartItemApi(payload) {
  return request.post('/cart/add', payload);
}

// 修改数量
export function updateCartItemApi(payload) {
  return request.post('/cart/update', payload);
}

// 选中/取消
export function toggleCartItemCheckedApi(payload) {
  return request.post('/cart/checked', payload);
}

// 删除商品
export function removeCartItemApi(id) {
  return request.post('/cart/remove', { id });
}

// 清空购物车
export function clearCartApi() {
  return request.post('/cart/clear');
}

