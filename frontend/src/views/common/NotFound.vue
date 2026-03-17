<template>
  <div class="not-found card-panel">
    <h1 class="title-text">页面不存在</h1>
    <p class="desc-text">你访问的页面路径无效，请检查地址或返回首页。</p>
    <RouterLink to="/web/index" class="back-link">返回首页</RouterLink>
  </div>
</template>

<style scoped>
.not-found {
  width: min(720px, calc(100vw - 32px));
  margin: 80px auto;
  padding: 28px;
}

.back-link {
  display: inline-flex;
  margin-top: 18px;
  color: #1677ff;
  font-weight: 600;
}
</style>
