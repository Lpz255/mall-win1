import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

const ADMIN_TOKEN_KEY = 'admin_token';
const ADMIN_INFO_KEY = 'admin_info';
const ADMIN_PERMISSIONS_KEY = 'admin_permissions';

function parseJson(key, fallbackValue) {
  const text = localStorage.getItem(key);
  if (!text) {
    return fallbackValue;
  }

  try {
    return JSON.parse(text);
  } catch (_error) {
    localStorage.removeItem(key);
    return fallbackValue;
  }
}

export const useAdminStore = defineStore('admin', () => {
  const adminToken = ref(localStorage.getItem(ADMIN_TOKEN_KEY) || '');
  const adminInfo = ref(parseJson(ADMIN_INFO_KEY, null));
  const permissions = ref(parseJson(ADMIN_PERMISSIONS_KEY, []));

  const isAdminLogin = computed(() => Boolean(adminToken.value));
  const authToken = computed(() => adminToken.value);
  const permissionList = computed(() => permissions.value);

  function setAdminToken(value) {
    adminToken.value = value || '';
    if (adminToken.value) {
      localStorage.setItem(ADMIN_TOKEN_KEY, adminToken.value);
    } else {
      localStorage.removeItem(ADMIN_TOKEN_KEY);
    }
  }

  function setAdminInfo(value) {
    adminInfo.value = value || null;
    if (adminInfo.value) {
      localStorage.setItem(ADMIN_INFO_KEY, JSON.stringify(adminInfo.value));
    } else {
      localStorage.removeItem(ADMIN_INFO_KEY);
    }
  }

  function setPermissions(value) {
    const list = Array.isArray(value) ? value : [];
    permissions.value = list;
    localStorage.setItem(ADMIN_PERMISSIONS_KEY, JSON.stringify(list));
  }

  function hasPermission(permissionCode) {
    if (!permissionCode) {
      return true;
    }
    return permissionList.value.includes('*') || permissionList.value.includes(permissionCode);
  }

  function clearAdminLogin() {
    setAdminToken('');
    setAdminInfo(null);
    setPermissions([]);
  }

  return {
    adminToken,
    adminInfo,
    permissions,
    isAdminLogin,
    authToken,
    permissionList,
    setAdminToken,
    setAdminInfo,
    setPermissions,
    hasPermission,
    clearAdminLogin
  };
});

