import { useAdminStore } from '@/store/modules/admin';
import pinia from '@/store';

function checkPermission(permissionCode) {
  const adminStore = useAdminStore(pinia);
  return adminStore.hasPermission(permissionCode);
}

function removeElement(el) {
  if (el.parentNode) {
    el.parentNode.removeChild(el);
  }
}

export default {
  mounted(el, binding) {
    const permissionCode = binding.value;
    if (!permissionCode) {
      return;
    }

    if (!checkPermission(permissionCode)) {
      removeElement(el);
    }
  }
};
