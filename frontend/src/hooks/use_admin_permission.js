import { computed } from 'vue';
import { useAdminStore } from '@/store/modules/admin';

export function useAdminPermission() {
  const adminStore = useAdminStore();

  const permissionList = computed(() => adminStore.permissions || []);

  function hasPermission(permissionCode) {
    if (!permissionCode) {
      return true;
    }
    return adminStore.hasPermission(permissionCode);
  }

  return {
    permissionList,
    hasPermission
  };
}

