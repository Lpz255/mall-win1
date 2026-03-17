<template>
  <div class="page-container cart-page">
    <section class="cart-card card-panel">
      <div class="card-head">
        <h2 class="title-text">我的购物车</h2>
        <el-button text type="danger" @click="handleClearCart">清空购物车</el-button>
      </div>

      <div v-if="cartList.length" class="cart-list">
        <article v-for="item in cartList" :key="item.id || item.productId" class="cart-item">
          <el-checkbox :model-value="isSelected(item)" @change="(value) => handleToggleChecked(item, value)" />

          <RouterLink :to="`/web/product/detail/${item.productId || item.id}`" class="thumb-link">
            <img :src="item.image || item.cover || defaultImage" :alt="item.name || '购物车商品'" loading="lazy" />
          </RouterLink>

          <div class="item-info">
            <h3>{{ item.name || item.productName || '未命名商品' }}</h3>
            <p class="desc">{{ item.subtitle || item.description || '暂无描述' }}</p>
          </div>

          <div class="item-price">￥{{ formatPrice(item.price || item.salePrice || 0) }}</div>

          <el-input-number
            :model-value="Number(item.quantity || 1)"
            :min="1"
            :max="999"
            @change="(value) => handleUpdateQuantity(item, value)"
          />

          <div class="item-subtotal">
            ￥{{ formatPrice(Number(item.price || 0) * Number(item.quantity || 1)) }}
          </div>

          <el-button text type="danger" @click="handleRemove(item)">删除</el-button>
        </article>
      </div>

      <el-empty v-else description="购物车还是空的，快去选购吧" />
    </section>

    <section class="settle-bar card-panel">
      <div class="summary">
        <p>已选商品：<strong>{{ selectedCount }}</strong> 件</p>
        <p>
          合计金额：
          <strong class="total-price">￥{{ formatPrice(selectedTotalPrice) }}</strong>
        </p>
      </div>
      <div class="actions">
        <el-button @click="loadCartList">刷新购物车</el-button>
        <el-button type="primary" color="#ff7a00" :disabled="!selectedCount" @click="handleCheckout">
          去结算
        </el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  clearCartApi,
  createOrderApi,
  getCartListApi,
  removeCartItemApi,
  toggleCartItemCheckedApi,
  updateCartItemApi
} from '@/api';
import { useCartStore } from '@/store/modules/cart';
import { debounce } from '@/utils/rate_limit';

const route = useRoute();
const router = useRouter();
const cartStore = useCartStore();

const cartList = ref([]);
const selectedIds = ref([]);
const defaultImage = 'https://via.placeholder.com/120x120?text=Cart';

const selectedItems = computed(() => cartList.value.filter((item) => selectedIds.value.includes(item.id || item.productId)));
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0));
const selectedTotalPrice = computed(() =>
  selectedItems.value.reduce((sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0), 0)
);
const quantityUpdateDebounceMap = new Map();

onMounted(() => {
  if (!checkLogin()) {
    return;
  }
  loadCartList();
});

onBeforeUnmount(() => {
  quantityUpdateDebounceMap.forEach((handler) => handler.cancel?.());
  quantityUpdateDebounceMap.clear();
});

async function loadCartList() {
  try {
    const data = await getCartListApi();
    cartList.value = normalizeArray(data);
  } catch (_error) {
    // 后端未接入时，回退到本地缓存购物车，方便联调阶段演示
    cartList.value = cartStore.cartItems.map((item) => ({
      id: item.id,
      productId: item.id,
      ...item
    }));
  }

  syncCartStore();

  selectedIds.value = cartList.value
    .filter((item) => item.checked === true || item.selected === true || item.isChecked === true)
    .map((item) => item.id || item.productId);
}

async function handleToggleChecked(item, checked) {
  const itemId = item.id || item.productId;
  await toggleCartItemCheckedApi({
    id: itemId,
    checked
  });

  if (checked) {
    if (!selectedIds.value.includes(itemId)) {
      selectedIds.value.push(itemId);
    }
  } else {
    selectedIds.value = selectedIds.value.filter((id) => id !== itemId);
  }
}

async function handleUpdateQuantity(item, quantity) {
  const targetQuantity = Number(quantity || 1);
  const itemId = item.id || item.productId;

  // 高频操作优化：先更新本地视图，再以防抖方式提交接口，减少无效请求
  item.quantity = targetQuantity;
  syncCartStore();

  const updateHandler = getDebouncedQuantityUpdater(itemId);
  updateHandler({
    id: itemId,
    quantity: targetQuantity
  });
}

async function handleRemove(item) {
  await removeCartItemApi(item.id || item.productId);
  ElMessage.success('商品已移除');
  await loadCartList();
}

async function handleClearCart() {
  if (!cartList.value.length) {
    return;
  }

  try {
    await ElMessageBox.confirm('确认清空购物车吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    await clearCartApi();
    cartList.value = [];
    selectedIds.value = [];
    cartStore.clearCart();
    ElMessage.success('购物车已清空');
  } catch (_error) {
    // 用户取消时不提示错误
  }
}

async function handleCheckout() {
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择要结算的商品');
    return;
  }

  await createOrderApi({
    source: 'cart',
    cartItemIds: selectedIds.value
  });
  ElMessage.success('下单成功');
  router.push('/web/order');
}

function isSelected(item) {
  return selectedIds.value.includes(item.id || item.productId);
}

function checkLogin() {
  if (localStorage.getItem('token')) {
    return true;
  }
  ElMessage.warning('请先登录后查看购物车');
  router.push({
    path: '/web/login',
    query: {
      redirect: route.fullPath
    }
  });
  return false;
}

function normalizeArray(data) {
  if (Array.isArray(data)) {
    return data;
  }
  if (Array.isArray(data?.list)) {
    return data.list;
  }
  if (Array.isArray(data?.records)) {
    return data.records;
  }
  if (Array.isArray(data?.items)) {
    return data.items;
  }
  return [];
}

function formatPrice(value) {
  return Number(value || 0).toFixed(2);
}

function syncCartStore() {
  cartStore.setCartItems(
    cartList.value.map((item) => ({
      id: item.id || item.productId,
      name: item.name || item.productName,
      price: item.price || item.salePrice || 0,
      quantity: Number(item.quantity || 1)
    }))
  );
}

function getDebouncedQuantityUpdater(itemId) {
  if (quantityUpdateDebounceMap.has(itemId)) {
    return quantityUpdateDebounceMap.get(itemId);
  }

  const handler = debounce(async (payload) => {
    try {
      await updateCartItemApi(payload);
      ElMessage.success('数量已更新');
    } catch (_error) {
      // 保持本地数量展示，避免频繁回滚影响体验，用户可手动刷新购物车同步后端状态
    }
  }, 400);

  quantityUpdateDebounceMap.set(itemId, handler);
  return handler;
}
</script>

<style scoped>
.cart-page {
  display: grid;
  gap: 14px;
}

.cart-card {
  padding: 16px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.cart-list {
  display: grid;
  gap: 10px;
}

.cart-item {
  display: grid;
  grid-template-columns: auto 110px 1.2fr 150px 150px 150px auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
}

.thumb-link {
  display: block;
  width: 100px;
  height: 100px;
  overflow: hidden;
  border-radius: 8px;
  background: #f8fafc;
}

.thumb-link img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  display: grid;
  gap: 6px;
}

.item-info h3 {
  color: #0f172a;
  font-size: 15px;
}

.desc {
  color: #64748b;
  font-size: 13px;
}

.item-price,
.item-subtotal {
  color: #ef4444;
  font-size: 18px;
  font-weight: 700;
}

.settle-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
}

.summary {
  display: flex;
  gap: 24px;
  align-items: center;
}

.summary p {
  color: #334155;
  font-size: 14px;
}

.total-price {
  color: #ff5f00;
  font-size: 24px;
}

.actions {
  display: flex;
  gap: 10px;
}

@media (max-width: 1400px) {
  .cart-item {
    grid-template-columns: auto 100px 1fr;
  }

  .item-price,
  .item-subtotal {
    font-size: 16px;
  }
}
</style>
