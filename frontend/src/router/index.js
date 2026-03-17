import { createRouter, createWebHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getActivePinia } from 'pinia';
import { useAdminStore } from '@/store/modules/admin';
import { useUserStore } from '@/store/modules/user';
import { webRoutes } from './modules/web';
import { adminLoginRoute, adminRoutes } from './modules/admin';

function getAuthStores() {
  const activePinia = getActivePinia();
  if (!activePinia) {
    return {
      userStore: null,
      adminStore: null
    };
  }

  return {
    userStore: useUserStore(activePinia),
    adminStore: useAdminStore(activePinia)
  };
}

function getRouteRedirect(to, fallbackPath) {
  return typeof to.query.redirect === 'string' ? to.query.redirect : fallbackPath;
}

function redirectToLogin(next, to, isAdminRoute) {
  next({
    path: isAdminRoute ? '/admin/login' : '/web/login',
    query: {
      redirect: to.fullPath
    }
  });
}

function applyDocumentTitle(to) {
  if (to.meta?.title) {
    document.title = `${to.meta.title} - 电商前端基础工程`;
    return;
  }

  document.title = '电商前端基础工程';
}

function clearAdminSession(adminStore) {
  adminStore?.clearAdminLogin();
}

function hasAdminRoutePermission(adminStore, permissionCode) {
  return adminStore?.hasPermission(permissionCode) ?? false;
}

function isAuthenticated(store, tokenKey) {
  if (!store) {
    return false;
  }

  return Boolean(store[tokenKey]);
}

function isAdminAuthenticated(adminStore) {
  return isAuthenticated(adminStore, 'authToken');
}

function isUserAuthenticated(userStore) {
  return isAuthenticated(userStore, 'authToken');
}

function redirectAuthenticatedUser(next, to, fallbackPath) {
  next(getRouteRedirect(to, fallbackPath));
}

function handleAdminPermissionDenied(next, to, adminStore) {
  ElMessage.error('当前账号无后台权限，请重新登录');
  clearAdminSession(adminStore);
  redirectToLogin(next, to, true);
}

function handleMissingAuth(next, to, isAdminRoute) {
  ElMessage.warning('请先登录后再访问该页面');
  redirectToLogin(next, to, isAdminRoute);
}

function isAdminRoutePath(path) {
  return path.startsWith('/admin');
}

function isAdminLoginPath(path) {
  return path === '/admin/login';
}

function isWebLoginPath(path) {
  return path === '/web/login';
}

function isRouteLoginSatisfied(isAdminRoute, userStore, adminStore) {
  return isAdminRoute ? isAdminAuthenticated(adminStore) : isUserAuthenticated(userStore);
}

function redirectIfAlreadyLoggedIn(to, next, userStore, adminStore) {
  if (isWebLoginPath(to.path) && isUserAuthenticated(userStore)) {
    redirectAuthenticatedUser(next, to, '/web/index');
    return true;
  }

  if (isAdminLoginPath(to.path) && isAdminAuthenticated(adminStore)) {
    redirectAuthenticatedUser(next, to, '/admin/index');
    return true;
  }

  return false;
}

function ensureAdminPermission(to, next, adminStore) {
  const requiredPermission = to.meta?.permission;
  if (!requiredPermission) {
    return true;
  }

  if (hasAdminRoutePermission(adminStore, requiredPermission)) {
    return true;
  }

  handleAdminPermissionDenied(next, to, adminStore);
  return false;
}

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
  const { userStore, adminStore } = getAuthStores();
  const needAuth = to.matched.some((record) => record.meta.requiresAuth);
  const isAdminRoute = isAdminRoutePath(to.path);

  applyDocumentTitle(to);

  if (needAuth && !isRouteLoginSatisfied(isAdminRoute, userStore, adminStore)) {
    handleMissingAuth(next, to, isAdminRoute);
    return;
  }

  if (redirectIfAlreadyLoggedIn(to, next, userStore, adminStore)) {
    return;
  }

  if (isAdminRoute && !ensureAdminPermission(to, next, adminStore)) {
    return;
  }

  next();
});

export default router;
