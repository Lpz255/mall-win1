<template>
  <el-form :model="model" inline class="admin-search-form" @submit.prevent>
    <slot />
    <el-form-item class="action-item">
      <el-button type="primary" color="#1677ff" @click="$emit('search')">查询</el-button>
      <el-button @click="$emit('reset')">重置</el-button>
      <el-button v-if="canShowExport" type="success" plain @click="$emit('export')">导出Excel</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup>
import { computed } from 'vue';
import { useAdminPermission } from '@/hooks/use_admin_permission';

const props = defineProps({
  model: {
    type: Object,
    default: () => ({})
  },
  showExport: {
    type: Boolean,
    default: true
  },
  exportPermission: {
    type: String,
    default: ''
  }
});

defineEmits(['search', 'reset', 'export']);

const { hasPermission } = useAdminPermission();
const canShowExport = computed(() => props.showExport && hasPermission(props.exportPermission));
</script>

<style scoped>
.admin-search-form {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: center;
}

.action-item {
  margin-left: auto;
}
</style>
