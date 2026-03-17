import { createRouter, createWebHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import { webRoutes } from './modules/web';
import { adminLoginRoute, adminRoutes } from './modules/admin';

const routes = [
  {
    path: '/',
    redirect: '/web/index'
  },
  {
    path: '/login',
    redirect: '/web/login'
  },
  adminLoginRoute,
  webRoutes,
  adminRoutes,
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/common/NotFound.vue'),
    meta: {
      title: '页面不存在'
    }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
});

router.beforeEach((to, _from, next) => {
  const userToken = localStorage.getItem('token');
  const adminToken = localStorage.getItem('admin_token');
  const needAuth = to.matched.some((record) => record.meta.requiresAuth);
  const isAdminRoute = to.path.startsWith('/admin');
  const routeToken = isAdminRoute ? adminToken : userToken;
  const requiredPermission = to.meta?.permission;
  const permissionList = parsePermissionList();

  if (to.meta?.title) {
    document.title = `${to.meta.title} - 电商前端基础工程`;
  } else {
    document.title = '电商前端基础工程';
  }

  if (needAuth && !routeToken) {
    ElMessage.warning('请先登录后再访问该页面');
    next({
      path: isAdminRoute ? '/admin/login' : '/web/login',
      query: {
        redirect: to.fullPath
      }
    });
    return;
  }

  if (to.path === '/web/login' && userToken) {
    const redirectPath = typeof to.query.redirect === 'string' ? to.query.redirect : '/web/index';
    next(redirectPath);
    return;
  }

  if (to.path === '/admin/login' && adminToken) {
    const redirectPath = typeof to.query.redirect === 'string' ? to.query.redirect : '/admin/index';
    next(redirectPath);
    return;
  }

  // 后台路由鉴权：若路由声明了 permission，则校验是否拥有按钮/页面级权限
  if (isAdminRoute && requiredPermission && !hasPermission(requiredPermission, permissionList)) {
    ElMessage.error('当前账号无后台权限，请重新登录');
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_info');
    localStorage.removeItem('admin_permissions');
    next({
      path: '/admin/login',
      query: {
        redirect: to.fullPath
      }
    });
    return;
  }

  next();
});

function parsePermissionList() {
  const text = localStorage.getItem('admin_permissions');
  if (!text) {
    return [];
  }

  try {
    const value = JSON.parse(text);
    return Array.isArray(value) ? value : [];
  } catch (_error) {
    localStorage.removeItem('admin_permissions');
    return [];
  }
}

function hasPermission(permissionCode, permissionList) {
  if (!permissionCode) {
    return true;
  }
  return permissionList.includes('*') || permissionList.includes(permissionCode);
}

export default router;
