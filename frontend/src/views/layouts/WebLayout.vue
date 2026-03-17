<template>
  <div class="web-layout">
    <header v-if="!isLoginPage" class="layout-header">
      <div class="header-inner page-container">
        <RouterLink to="/web/index" class="logo-link">电商前台</RouterLink>

        <nav class="main-nav">
          <RouterLink v-for="item in navItems" :key="item.path" :to="item.path" class="nav-link">
            {{ item.label }}
          </RouterLink>
        </nav>

        <div class="header-right">
          <el-badge :value="cartStore.cartCount" :max="99" class="cart-badge">
            <RouterLink to="/web/cart" class="cart-link">购物车</RouterLink>
          </el-badge>

          <template v-if="userStore.isLogin">
            <span class="user-name">{{ userName }}</span>
            <el-button type="primary" color="#1677ff" plain @click="handleLogout">退出</el-button>
          </template>
          <template v-else>
            <RouterLink to="/web/login" class="login-link">登录/注册</RouterLink>
          </template>
        </div>
      </div>
    </header>

    <main :class="['layout-main', { 'is-login-page': isLoginPage }]">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useCartStore } from '@/store/modules/cart';
import { useUserStore } from '@/store/modules/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const cartStore = useCartStore();

const navItems = [
  { label: '首页', path: '/web/index' },
  { label: '商品列表', path: '/web/product/list' },
  { label: '秒杀专区', path: '/web/seckill' },
  { label: '我的订单', path: '/web/order' }
];

const isLoginPage = computed(() => route.path === '/web/login');
const userName = computed(() => userStore.userInfo?.name || userStore.userInfo?.phone || '用户');

function handleLogout() {
  userStore.clearLogin();
  cartStore.clearCart();
  ElMessage.success('已退出登录');
  router.push('/web/login');
}
</script>

<style scoped>
.web-layout {
  min-height: 100vh;
}

.layout-header {
  position: sticky;
  top: 0;
  z-index: 40;
  border-bottom: 1px solid #e2e8f0;
  backdrop-filter: blur(10px);
  background: rgba(255, 255, 255, 0.9);
}

.header-inner {
  display: flex;
  gap: 20px;
  align-items: center;
  justify-content: space-between;
  height: 72px;
}

.logo-link {
  flex-shrink: 0;
  color: #ff7a00;
  font-size: 24px;
  font-weight: 700;
}

.main-nav {
  display: flex;
  gap: 6px;
  align-items: center;
}

.nav-link {
  padding: 8px 14px;
  color: #334155;
  font-size: 14px;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.nav-link:hover,
.nav-link.router-link-active {
  color: #fff;
  background: linear-gradient(135deg, #ff7a00, #1677ff);
}

.header-right {
  display: flex;
  gap: 14px;
  align-items: center;
}

.cart-link,
.login-link {
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
}

.cart-link:hover,
.login-link:hover {
  color: #1677ff;
}

.cart-badge :deep(.el-badge__content.is-fixed) {
  top: 8px;
}

.user-name {
  color: #1e293b;
  font-size: 14px;
  font-weight: 600;
}

.layout-main {
  min-height: calc(100vh - 72px);
  padding: 24px 0 32px;
}

.layout-main.is-login-page {
  min-height: 100vh;
  padding: 0;
}

@media (max-width: 1200px) {
  .header-inner {
    height: auto;
    padding: 12px 0;
    flex-wrap: wrap;
  }

  .main-nav {
    order: 3;
    width: 100%;
    overflow-x: auto;
  }
}
</style>
