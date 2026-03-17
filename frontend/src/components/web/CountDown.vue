<template>
  <span class="countdown-text">{{ formattedText }}</span>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';

const props = defineProps({
  endTime: {
    type: [String, Number, Date],
    default: ''
  }
});

const now = ref(Date.now());
let frameId = 0;
let timerId = 0;

const remainMs = computed(() => {
  const target = new Date(props.endTime).getTime();
  if (!target) {
    return 0;
  }
  return Math.max(0, target - now.value);
});

const formattedText = computed(() => {
  if (remainMs.value <= 0) {
    return '已结束';
  }

  const totalSeconds = Math.floor(remainMs.value / 1000);
  const day = Math.floor(totalSeconds / 86400);
  const hour = Math.floor((totalSeconds % 86400) / 3600);
  const minute = Math.floor((totalSeconds % 3600) / 60);
  const second = totalSeconds % 60;

  if (day > 0) {
    return `${day}天 ${pad(hour)}:${pad(minute)}:${pad(second)}`;
  }
  return `${pad(hour)}:${pad(minute)}:${pad(second)}`;
});

function pad(value) {
  return String(value).padStart(2, '0');
}

onMounted(() => {
  startLoop();
});

onBeforeUnmount(() => {
  stopLoop();
});

watch(
  () => props.endTime,
  () => {
    now.value = Date.now();
    stopLoop();
    startLoop();
  }
);

function startLoop() {
  if (remainMs.value <= 0) {
    return;
  }

  const schedule = () => {
    timerId = window.setTimeout(() => {
      if (typeof requestAnimationFrame === 'function') {
        frameId = requestAnimationFrame(() => {
          now.value = Date.now();
          if (remainMs.value > 0) {
            schedule();
          }
        });
        return;
      }

      now.value = Date.now();
      if (remainMs.value > 0) {
        schedule();
      }
    }, 1000);
  };

  if (typeof window === 'undefined') {
    return;
  }
  schedule();
}

function stopLoop() {
  if (frameId) {
    cancelAnimationFrame(frameId);
    frameId = 0;
  }
  if (timerId) {
    clearInterval(timerId);
    timerId = 0;
  }
}
</script>

<style scoped>
.countdown-text {
  color: #ef4444;
  font-size: 14px;
  font-weight: 700;
}
</style>
