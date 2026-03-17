import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

const CART_ITEMS_KEY = 'cart_items';

function parseCartFromLocal() {
  const text = localStorage.getItem(CART_ITEMS_KEY);
  if (!text) {
    return [];
  }

  try {
    return JSON.parse(text);
  } catch (_error) {
    localStorage.removeItem(CART_ITEMS_KEY);
    return [];
  }
}

export const useCartStore = defineStore('cart', () => {
  const cartItems = ref(parseCartFromLocal());

  const cartCount = computed(() =>
    cartItems.value.reduce((sum, item) => sum + Number(item.quantity || 0), 0)
  );

  const cartTotalPrice = computed(() =>
    cartItems.value.reduce((sum, item) => sum + Number(item.price || 0) * Number(item.quantity || 0), 0)
  );

  function persistCart() {
    localStorage.setItem(CART_ITEMS_KEY, JSON.stringify(cartItems.value));
  }

  function setCartItems(items = []) {
    cartItems.value = Array.isArray(items) ? items : [];
    persistCart();
  }

  function addToCart(product) {
    if (!product?.id) {
      return;
    }

    const target = cartItems.value.find((item) => item.id === product.id);
    if (target) {
      target.quantity += product.quantity || 1;
    } else {
      cartItems.value.push({
        id: product.id,
        name: product.name || '未命名商品',
        price: Number(product.price || 0),
        quantity: Number(product.quantity || 1)
      });
    }

    persistCart();
  }

  function removeFromCart(productId) {
    cartItems.value = cartItems.value.filter((item) => item.id !== productId);
    persistCart();
  }

  function clearCart() {
    cartItems.value = [];
    persistCart();
  }

  return {
    cartItems,
    cartCount,
    cartTotalPrice,
    setCartItems,
    addToCart,
    removeFromCart,
    clearCart
  };
});
