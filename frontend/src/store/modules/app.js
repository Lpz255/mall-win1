import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

export const useAppStore = defineStore('app', () => {
  const pendingRequests = ref(0);

  const globalLoading = computed(() => pendingRequests.value > 0);

  function startLoading() {
    pendingRequests.value += 1;
  }

  function endLoading() {
    pendingRequests.value = Math.max(0, pendingRequests.value - 1);
  }

  function setLoading(value) {
    pendingRequests.value = value ? 1 : 0;
  }

  return {
    pendingRequests,
    globalLoading,
    startLoading,
    endLoading,
    setLoading
  };
});

