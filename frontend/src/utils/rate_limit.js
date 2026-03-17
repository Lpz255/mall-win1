export function debounce(fn, wait = 300) {
  let timer = null;

  const debounced = (...args) => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }

    timer = setTimeout(() => {
      fn(...args);
      timer = null;
    }, wait);
  };

  debounced.cancel = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  return debounced;
}

export function throttle(fn, wait = 300) {
  let lastTime = 0;
  let timer = null;

  const throttled = (...args) => {
    const now = Date.now();
    const remain = wait - (now - lastTime);
    if (remain <= 0) {
      lastTime = now;
      fn(...args);
      return;
    }

    if (timer) {
      return;
    }

    timer = setTimeout(() => {
      lastTime = Date.now();
      fn(...args);
      timer = null;
    }, remain);
  };

  throttled.cancel = () => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };

  return throttled;
}

