<template>
  <el-container class="admin-layout">
    <el-aside class="admin-aside" width="220px">
      <div class="aside-logo">电商运营后台</div>
      <el-menu
        router
        :default-active="activeMenu"
        class="aside-menu"
        background-color="#0b1220"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header :class="['admin-header', { 'admin-header-monitor': isMonitorRoute }]">
        <div class="header-left">
          <h1>{{ pageTitle }}</h1>
        </div>
        <div class="header-right">
          <span class="admin-name">{{ displayName }}</span>
          <el-button type="primary" color="#1677ff" plain @click="goWebSite">前台首页</el-button>
          <el-button type="danger" plain @click="handleLogout">退出登录</el-button>
        </div>
      </el-header>

      <el-main :class="['admin-main', { 'admin-main-monitor': isMonitorRoute }]">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAdminPermission } from '@/hooks/use_admin_permission';
import { useAdminStore } from '@/store/modules/admin';

const route = useRoute();
const router = useRouter();
const adminStore = useAdminStore();
const { hasPermission } = useAdminPermission();

const rawMenuItems = [
  { label: '数据概览', path: '/admin/index', permission: 'admin:dashboard:view' },
  { label: '监控看板', path: '/admin/monitor', permission: 'admin:dashboard:view' },
  { label: '商品管理', path: '/admin/product', permission: 'admin:product:view' },
  { label: '秒杀管理', path: '/admin/seckill', permission: 'admin:seckill:view' },
  { label: '订单管理', path: '/admin/order', permission: 'admin:order:view' },
  { label: '用户管理', path: '/admin/user', permission: 'admin:user:view' }
];

const menuItems = computed(() => rawMenuItems.filter((item) => hasPermission(item.permission)));
const activeMenu = computed(() => route.path);
const displayName = computed(() => adminStore.adminInfo?.name || adminStore.adminInfo?.username || '管理员');
const pageTitle = computed(() => route.meta?.title || '运营后台');
const isMonitorRoute = computed(() => route.name === 'AdminMonitor');

function goWebSite() {
  router.push('/web/index');
}

function handleLogout() {
  adminStore.clearAdminLogin();
  ElMessage.success('已退出后台登录');
  router.push('/admin/login');
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-aside {
  overflow: hidden;
  background: #0b1220;
  border-right: 1px solid rgba(148, 163, 184, 0.14);
}

.aside-logo {
  display: flex;
  height: 62px;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.aside-menu {
  border-right: none;
}

.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 62px;
  padding: 0 18px;
  border-bottom: 1px solid #e2e8f0;
  background: #fff;
}

.admin-header-monitor {
  border-bottom-color: rgba(51, 65, 85, 0.56);
  background: linear-gradient(180deg, #0b1525, #0a1220);
}

.admin-header-monitor .header-left h1,
.admin-header-monitor .admin-name {
  color: #e2e8f0;
}

.header-left h1 {
  color: #0f172a;
  font-size: 20px;
  font-weight: 700;
}

.header-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.admin-name {
  color: #334155;
  font-size: 14px;
  font-weight: 600;
}

.admin-main {
  min-height: calc(100vh - 62px);
  padding: 16px;
  background:
    radial-gradient(circle at 0% 0%, rgba(22, 119, 255, 0.08), transparent 28%),
    radial-gradient(circle at 100% 100%, rgba(255, 122, 0, 0.08), transparent 28%),
    #f5f7fb;
}

.admin-main-monitor {
  padding: 0;
  background: #050d19;
}

@media (max-width: 900px) {
  .admin-main-monitor {
    padding: 0;
  }
}
</style>

