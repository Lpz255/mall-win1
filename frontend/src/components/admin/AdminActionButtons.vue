<template>
  <div class="action-buttons">
    <template v-for="item in visibleActions" :key="item.key">
      <el-button
        :type="item.type || 'primary'"
        :plain="item.plain !== false"
        text
        :disabled="isActionDisabled(item)"
        @click="handleActionClick(item)"
      >
        {{ item.label }}
      </el-button>
    </template>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { ElMessageBox } from 'element-plus';
import { useAdminPermission } from '@/hooks/use_admin_permission';

const props = defineProps({
  row: {
    type: Object,
    default: () => ({})
  },
  actions: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['action']);
const { hasPermission } = useAdminPermission();

const visibleActions = computed(() => props.actions.filter((item) => hasPermission(item.permission)));

function isActionDisabled(actionItem) {
  if (typeof actionItem.disabled === 'function') {
    return actionItem.disabled(props.row);
  }
  return Boolean(actionItem.disabled);
}

async function handleActionClick(actionItem) {
  try {
    if (actionItem.confirmText) {
      await ElMessageBox.confirm(actionItem.confirmText, '二次确认', {
        type: 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消'
      });
    }
    emit('action', actionItem.key, props.row);
  } catch (_error) {
    // 用户取消操作时不做处理
  }
}
</script>

<style scoped>
.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
</style>
