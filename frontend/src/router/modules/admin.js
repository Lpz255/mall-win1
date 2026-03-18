export const adminLoginRoute = {
  path: '/admin/login',
  name: 'AdminLogin',
  component: () => import('@/views/admin/AdminLogin.vue'),
  meta: {
    title: '后台登录'
  }
};

export const adminRoutes = {
  path: '/admin',
  component: () => import('@/views/layouts/AdminLayout.vue'),
  redirect: '/admin/index',
  meta: {
    requiresAuth: true,
    requiresAdmin: true
  },
  children: [
    {
      path: 'index',
      name: 'AdminIndex',
      component: () => import('@/views/admin/AdminIndex.vue'),
      meta: {
        title: '数据概览',
        requiresAuth: true,
        requiresAdmin: true,
        permission: 'admin:dashboard:view'
      }
    },
    {
      path: 'monitor',
      name: 'AdminMonitor',
      component: () => import('@/views/admin/AdminMonitor.vue'),
      meta: {
        title: '监控看板',
        requiresAuth: true,
        requiresAdmin: true,
        permission: 'admin:dashboard:view'
      }
    },
    {
      path: 'product',
      name: 'AdminProduct',
      component: () => import('@/views/admin/AdminProduct.vue'),
      meta: {
        title: '商品管理',
        requiresAuth: true,
        requiresAdmin: true,
        permission: 'admin:product:view'
      }
    },
    {
      path: 'seckill',
      name: 'AdminSeckill',
      component: () => import('@/views/admin/AdminSeckill.vue'),
      meta: {
        title: '秒杀管理',
        requiresAuth: true,
        requiresAdmin: true,
        permission: 'admin:seckill:view'
      }
    },
    {
      path: 'order',
      name: 'AdminOrder',
      component: () => import('@/views/admin/AdminOrder.vue'),
      meta: {
        title: '订单管理',
        requiresAuth: true,
        requiresAdmin: true,
        permission: 'admin:order:view'
      }
    },
    {
      path: 'user',
      name: 'AdminUser',
      component: () => import('@/views/admin/AdminUser.vue'),
      meta: {
        title: '用户管理',
        requiresAuth: true,
        requiresAdmin: true,
        permission: 'admin:user:view'
      }
    }
  ]
};

