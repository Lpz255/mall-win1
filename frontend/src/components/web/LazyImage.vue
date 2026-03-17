<template>
  <div class="lazy-image-wrap">
    <img
      v-if="displaySrc"
      :src="displaySrc"
      :alt="alt"
      :loading="loading"
      decoding="async"
      :fetchpriority="props.fetchPriority"
      class="lazy-image"
      @error="handleError"
    />
    <div v-else class="placeholder">{{ props.fallbackText }}</div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { buildOptimizedImageUrl } from '@/utils/image_optimize';

const props = defineProps({
  src: {
    type: String,
    default: ''
  },
  alt: {
    type: String,
    default: '商品图片'
  },
  loading: {
    type: String,
    default: 'lazy'
  },
  optimize: {
    type: Boolean,
    default: true
  },
  optimizeWidth: {
    type: Number,
    default: 640
  },
  optimizeQuality: {
    type: Number,
    default: 75
  },
  fetchPriority: {
    type: String,
    default: 'auto'
  },
  fallbackText: {
    type: String,
    default: '图片加载失败'
  }
});

const localSrc = ref(props.src);

watch(
  () => props.src,
  (value) => {
    localSrc.value = value;
  }
);

function handleError() {
  localSrc.value = '';
}

const displaySrc = computed(() => {
  if (!localSrc.value) {
    return '';
  }
  if (!props.optimize) {
    return localSrc.value;
  }
  return buildOptimizedImageUrl(localSrc.value, {
    width: props.optimizeWidth,
    quality: props.optimizeQuality
  });
});
</script>

<style scoped>
.lazy-image-wrap {
  width: 100%;
  height: 100%;
}

.lazy-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #64748b;
  font-size: 14px;
  background: #f8fafc;
}
</style>
