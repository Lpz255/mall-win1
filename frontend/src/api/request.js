import axios from 'axios';
import { ElLoading, ElMessage } from 'element-plus';
import { getActivePinia } from 'pinia';
import router from '@/router';
import { useAppStore } from '@/store/modules/app';
import { useAdminStore } from '@/store/modules/admin';
import { useUserStore } from '@/store/modules/user';
import { adaptRequestPayload, adaptResponsePayload, normalizeSearchParams } from '@/utils/api_adapter';
import { safeDisplayText } from '@/utils/text_codec';

const SUCCESS_CODES = [200, 0];
const BUSINESS_CODE_MESSAGE_MAP = {
  400: '请求参数错误，请检查后重试',
  401: '登录状态已失效，请重新登录',
  403: '暂无访问权限，请联系管理员',
  404: '请求资源不存在',
  409: '请求冲突，请勿重复提交',
  429: '请求过于频繁，请稍后重试',
  500: '服务器内部异常，请稍后重试'
};

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
});

let loadingCount = 0;
let loadingInstance = null;

function getStoreInstances() {
  const activePinia = getActivePinia();
  if (!activePinia) {
    return {
      appStore: null,
      userStore: null,
      adminStore: null
    };
  }
  return {
    appStore: useAppStore(activePinia),
    userStore: useUserStore(activePinia),
    adminStore: useAdminStore(activePinia)
  };
}

function beginLoading() {
  const { appStore } = getStoreInstances();
  appStore?.startLoading();
}

function finishLoading() {
  const { appStore } = getStoreInstances();
  appStore?.endLoading();
}

function openLoading(text = '正在加载，请稍候...') {
  if (loadingCount === 0) {
    loadingInstance = ElLoading.service({
      lock: true,
      text,
      background: 'rgba(15, 23, 42, 0.35)'
    });
  }
  loadingCount += 1;
}

function closeLoading() {
  loadingCount = Math.max(0, loadingCount - 1);
  if (loadingCount === 0 && loadingInstance) {
    loadingInstance.close();
    loadingInstance = null;
  }
}

function jumpToLogin(isAdminRequest = false) {
  const { userStore, adminStore } = getStoreInstances();
  if (isAdminRequest) {
    adminStore?.clearAdminLogin();
  } else {
    userStore?.clearLogin();
  }

  const targetPath = isAdminRequest ? '/admin/login' : '/web/login';
  if (router.currentRoute.value.path !== targetPath) {
    router.push({
      path: targetPath,
      query: {
        redirect: router.currentRoute.value.fullPath
      }
    });
  }
}

request.interceptors.request.use(
  (config) => {
    beginLoading();
    const isAdminRequest = config.url?.startsWith('/admin');
    config.__isAdminRequest = isAdminRequest;
    const showLoading = config.showLoading !== false;
    config.__showLoading = showLoading;
    if (showLoading) {
      openLoading(config.loadingText || '正在加载，请稍候...');
    }

    // 统一从 Pinia 读取 Token，避免和路由守卫分别维护本地存储状态
    const { userStore, adminStore } = getStoreInstances();
    const token = isAdminRequest ? adminStore?.authToken : userStore?.authToken;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    if (config.adaptRequest !== false) {
      if (config.params) {
        config.params = normalizeSearchParams(config.params);
      }

      if (canAdaptBody(config.data)) {
        config.data = adaptRequestPayload(config.data);
      }
    }

    return config;
  },
  (error) => {
    if (error.config?.__showLoading) {
      closeLoading();
    }
    finishLoading();
    ElMessage.error('请求发送失败，请检查网络连接');
    return Promise.reject(error);
  }
);

request.interceptors.response.use(
  (response) => {
    if (response.config?.__showLoading) {
      closeLoading();
    }
    finishLoading();

    const result = response.data;

    if (response.config?.responseType === 'blob') {
      return result;
    }

    // 适配后端统一 Result 返回格式：{ code, message, data }
    if (result && typeof result === 'object' && Object.prototype.hasOwnProperty.call(result, 'code')) {
      if (SUCCESS_CODES.includes(result.code)) {
        return adaptResponsePayload(result.data);
      }

      const message = getBusinessErrorMessage(result.code, result.message || result.msg);
      ElMessage.error(message);

      if (result.code === 401) {
        jumpToLogin(Boolean(response.config?.__isAdminRequest));
      }

      return Promise.reject(new Error(message));
    }

    return adaptResponsePayload(result);
  },
  (error) => {
    if (error.config?.__showLoading) {
      closeLoading();
    }
    finishLoading();

    const status = error.response?.status;
    const backendMessage = error.response?.data?.message || error.response?.data?.msg;
    const message = resolveHttpErrorMessage(status, backendMessage, error);
    ElMessage.error(message);

    if (status === 401) {
      jumpToLogin(Boolean(error.config?.__isAdminRequest));
    }

    return Promise.reject(error);
  }
);

export default request;

function getBusinessErrorMessage(code, backendMessage) {
  const fixedMessage = safeDisplayText(backendMessage, '');
  if (fixedMessage) {
    return fixedMessage;
  }
  return BUSINESS_CODE_MESSAGE_MAP[code] || '操作失败，请稍后重试';
}

function resolveHttpErrorMessage(status, backendMessage, error) {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return '网络连接已断开，请检查网络后重试';
  }

  if (error?.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试';
  }

  if (error?.message === 'Network Error') {
    return '网络异常，请稍后重试';
  }

  const safeBackendMessage = safeDisplayText(backendMessage, '');
  if (safeBackendMessage) {
    return safeBackendMessage;
  }

  return statusMessageMapFallback(status);
}

function statusMessageMapFallback(status) {
  const map = {
    400: '请求参数有误，请检查后重试',
    401: '登录状态已失效，请重新登录',
    403: '当前账号无权限访问该资源',
    404: '请求地址不存在，请联系管理员',
    408: '请求超时，请稍后重试',
    429: '请求过于频繁，请稍后重试',
    500: '服务器内部异常，请稍后重试',
    502: '网关异常，请稍后重试',
    503: '服务不可用，请稍后重试'
  };
  return map[status] || '网络异常，请稍后重试';
}

function canAdaptBody(data) {
  if (!data) {
    return false;
  }
  if (typeof FormData !== 'undefined' && data instanceof FormData) {
    return false;
  }
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    return false;
  }
  if (typeof ArrayBuffer !== 'undefined' && data instanceof ArrayBuffer) {
    return false;
  }
  return true;
}
