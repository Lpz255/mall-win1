<template>
  <div class="pagination-wrap">
    <el-pagination
      background
      layout="total, prev, pager, next, sizes, jumper"
      :total="total"
      :current-page="currentPage"
      :page-size="pageSize"
      :page-sizes="[10, 20, 30, 50]"
      @update:current-page="onCurrentPageChange"
      @update:page-size="onPageSizeChange"
    />
  </div>
</template>

<script setup>
const props = defineProps({
  total: {
    type: Number,
    default: 0
  },
  currentPage: {
    type: Number,
    default: 1
  },
  pageSize: {
    type: Number,
    default: 10
  }
});

const emit = defineEmits(['update:currentPage', 'update:pageSize', 'change']);

function onCurrentPageChange(value) {
  emit('update:currentPage', value);
  emit('change');
}

function onPageSizeChange(value) {
  emit('update:pageSize', value);
  emit('update:currentPage', 1);
  emit('change');
}
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>

