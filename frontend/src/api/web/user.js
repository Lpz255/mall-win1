import request from '@/api/request';

// 密码登录
export function loginByPasswordApi(payload) {
  return request.post('/user/login/password', payload);
}

// 验证码登录
export function loginByCodeApi(payload) {
  return request.post('/user/login/code', payload);
}

// 发送验证码
export function sendLoginCodeApi(phone) {
  return request.post('/user/send/code', { phone });
}

// 用户注册
export function registerApi(payload) {
  return request.post('/user/register', payload);
}

// 用户信息
export function getUserProfileApi() {
  return request.get('/user/profile');
}

