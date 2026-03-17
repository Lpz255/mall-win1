<template>
  <div class="page-container product-list-page">
    <section class="filter-panel card-panel">
      <div class="filter-grid">
        <el-input v-model="filterForm.keyword" placeholder="请输入商品关键词" clearable />

        <el-select v-model="filterForm.categoryId" placeholder="请选择分类" clearable>
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>

        <div class="price-range">
          <el-input-number v-model="filterForm.minPrice" :min="0" :precision="2" placeholder="最低价" />
          <span>至</span>
          <el-input-number v-model="filterForm.maxPrice" :min="0" :precision="2" placeholder="最高价" />
        </div>

        <el-select v-model="filterForm.sortType" placeholder="请选择排序" clearable>
          <el-option label="综合排序" value="" />
          <el-option label="价格从低到高" value="price_asc" />
          <el-option label="价格从高到低" value="price_desc" />
          <el-option label="销量优先" value="sales_desc" />
        </el-select>
      </div>

      <div class="filter-actions">
        <el-button type="primary" color="#1677ff" @click="handleSearch">筛选</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </section>

    <section class="list-panel">
      <div class="list-head">
        <h2 class="title-text">商品列表</h2>
        <p class="desc-text">共找到 {{ pagination.total }} 件商品</p>
      </div>

      <div v-if="productList.length" class="product-grid">
        <ProductCard
          v-for="item in productList"
          :key="item.id || item.productId"
          :product="item"
          @add-cart="handleAddCart"
          @buy-now="handleBuyNow"
        />
      </div>
      <el-empty v-else description="暂无符合条件的商品" class="empty-state" />

      <AppPagination
        v-model:current-page="pagination.pageNum"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        @change="loadProductList"
      />
    </section>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppPagination from '@/components/web/AppPagination.vue';
import ProductCard from '@/components/web/ProductCard.vue';
import { addCartItemApi, getProductCategoryApi, getProductListApi } from '@/api';
import { useCartStore } from '@/store/modules/cart';

const route = useRoute();
const router = useRouter();
const cartStore = useCartStore();

const categories = ref([]);
const productList = ref([]);
const filterForm = reactive({
  keyword: '',
  categoryId: '',
  minPrice: null,
  maxPrice: null,
  sortType: ''
});
const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
});

onMounted(async () => {
  await loadCategories();
  applyRouteFilter();
  await loadProductList();
});

async function loadCategories() {
  try {
    const data = await getProductCategoryApi();
    categories.value = normalizeArray(data);
  } catch (_error) {
    categories.value = [];
  }
}

async function loadProductList() {
  const sortMap = {
    price_asc: { sortField: 'price', sortOrder: 'asc' },
    price_desc: { sortField: 'price', sortOrder: 'desc' },
    sales_desc: { sortField: 'sales', sortOrder: 'desc' }
  };

  const sort = sortMap[filterForm.sortType] || {};
  const params = {
    pageNum: pagination.pageNum,
    pageSize: pagination.pageSize,
    keyword: filterForm.keyword || undefined,
    categoryId: filterForm.categoryId || undefined,
    minPrice: filterForm.minPrice ?? undefined,
    maxPrice: filterForm.maxPrice ?? undefined,
    ...sort
  };

  try {
    const data = await getProductListApi(params);
    const list = normalizeArray(data);

    productList.value = list;
    pagination.total = Number(data?.total || data?.count || list.length || 0);

    // 后端若未返回总数，兼容前端分页展示
    if (!data?.total && !data?.count) {
      pagination.total = list.length;
    }
  } catch (_error) {
    productList.value = [];
    pagination.total = 0;
  }
}

function applyRouteFilter() {
  if (route.query.categoryId) {
    filterForm.categoryId = Number(route.query.categoryId) || route.query.categoryId;
  }
  if (route.query.keyword) {
    filterForm.keyword = String(route.query.keyword);
  }
}

function handleSearch() {
  pagination.pageNum = 1;
  loadProductList();
}

function handleReset() {
  filterForm.keyword = '';
  filterForm.categoryId = '';
  filterForm.minPrice = null;
  filterForm.maxPrice = null;
  filterForm.sortType = '';
  pagination.pageNum = 1;
  loadProductList();
}

async function handleAddCart(product) {
  const token = localStorage.getItem('token');
  if (!token) {
    ElMessage.warning('请先登录后再加入购物车');
    router.push({
      path: '/web/login',
      query: {
        redirect: route.fullPath
      }
    });
    return;
  }

  await addCartItemApi({
    productId: product.id || product.productId,
    quantity: 1
  });
  cartStore.addToCart({
    id: product.id || product.productId,
    name: product.name || product.productName,
    price: product.price || product.salePrice || 0,
    quantity: 1
  });
  ElMessage.success('加入购物车成功');
}

function handleBuyNow(product) {
  router.push(`/web/product/detail/${product.id || product.productId}`);
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
</script>

<style scoped>
.product-list-page {
  display: grid;
  gap: 16px;
}

.filter-panel {
  padding: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 1fr 0.8fr;
  gap: 10px;
}

.price-range {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 8px;
  align-items: center;
}

.filter-actions {
  display: flex;
  gap: 10px;
  margin-top: 12px;
}

.list-panel {
  padding: 4px 0;
}

.list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.empty-state {
  padding: 34px 0 20px;
}

@media (max-width: 1400px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 980px) {
  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
