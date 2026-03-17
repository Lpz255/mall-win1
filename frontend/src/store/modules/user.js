import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

const TOKEN_KEY = 'token';
const USER_INFO_KEY = 'user_info';

function parseUserInfoFromLocal() {
  const text = localStorage.getItem(USER_INFO_KEY);
  if (!text) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch (_error) {
    localStorage.removeItem(USER_INFO_KEY);
    return null;
  }
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '');
  const userInfo = ref(parseUserInfoFromLocal());
  const isLogin = computed(() => Boolean(token.value));

  function setToken(value) {
    token.value = value || '';
    if (token.value) {
      localStorage.setItem(TOKEN_KEY, token.value);
    } else {
      localStorage.removeItem(TOKEN_KEY);
    }
  }

  function setUserInfo(value) {
    userInfo.value = value || null;
    if (userInfo.value) {
      localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo.value));
    } else {
      localStorage.removeItem(USER_INFO_KEY);
    }
  }

  function clearLogin() {
    setToken('');
    setUserInfo(null);
  }

  return {
    token,
    userInfo,
    isLogin,
    setToken,
    setUserInfo,
    clearLogin
  };
});

