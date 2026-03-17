export const webRoutes = {
  path: '/web',
  component: () => import('@/views/layouts/WebLayout.vue'),
  redirect: '/web/index',
  children: [
    {
      path: 'index',
      name: 'WebIndex',
      component: () => import('@/views/web/WebIndex.vue'),
      meta: {
        title: '商城首页'
      }
    },
    {
      path: 'product/list',
      name: 'ProductList',
      component: () => import('@/views/web/ProductList.vue'),
      meta: {
        title: '商品列表'
      }
    },
    {
      path: 'product/detail/:id',
      name: 'ProductDetail',
      component: () => import('@/views/web/ProductDetail.vue'),
      meta: {
        title: '商品详情'
      }
    },
    {
      path: 'seckill',
      name: 'SeckillPage',
      component: () => import('@/views/web/SeckillPage.vue'),
      meta: {
        title: '秒杀专区'
      }
    },
    {
      path: 'cart',
      name: 'CartPage',
      component: () => import('@/views/web/CartPage.vue'),
      meta: {
        title: '购物车',
        requiresAuth: true
      }
    },
    {
      path: 'order',
      name: 'OrderPage',
      component: () => import('@/views/web/OrderPage.vue'),
      meta: {
        title: '我的订单',
        requiresAuth: true
      }
    },
    {
      path: 'login',
      name: 'WebLogin',
      component: () => import('@/views/web/WebLogin.vue'),
      meta: {
        title: '用户登录'
      }
    }
  ]
};
