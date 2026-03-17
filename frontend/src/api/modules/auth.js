import request from '@/api/request';

// 示例接口：按你的后端实际路径进行调整
export function loginApi(payload) {
  return request.post('/user/login', payload);
}

export function getUserInfoApi() {
  return request.get('/user/info');
}

