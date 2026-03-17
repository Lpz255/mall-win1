<template>
  <section class="login-wrap">
    <div class="login-card card-panel">
      <div class="title-area">
        <h1>用户中心</h1>
        <p>支持手机号 + 验证码登录，或账号密码登录</p>
      </div>
      <div class="login-entry-switch">
        <el-button type="primary" color="#ff7a00" plain disabled>用户登录</el-button>
        <el-button type="primary" color="#1677ff" @click="goAdminLogin">管理员登录</el-button>
      </div>

      <el-tabs v-model="activeTab" class="login-tabs">
        <el-tab-pane label="密码登录" name="password">
          <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-position="top">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="passwordForm.phone" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="passwordForm.password" type="password" placeholder="请输入密码" show-password />
            </el-form-item>
            <el-button type="primary" color="#ff7a00" class="full-btn" @click="submitPasswordLogin">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="验证码登录" name="code">
          <el-form ref="codeFormRef" :model="codeForm" :rules="codeRules" label-position="top">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="codeForm.phone" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="验证码" prop="code">
              <div class="code-row">
                <el-input v-model="codeForm.code" placeholder="请输入验证码" maxlength="6" />
                <el-button :disabled="countdown > 0" @click="sendCode">
                  {{ countdown > 0 ? `${countdown}s后重试` : '发送验证码' }}
                </el-button>
              </div>
            </el-form-item>
            <el-button type="primary" color="#ff7a00" class="full-btn" @click="submitCodeLogin">
              登录
            </el-button>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="注册" name="register">
          <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-position="top">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="registerForm.phone" placeholder="请输入手机号" maxlength="11" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="registerForm.password" type="password" placeholder="请设置登录密码" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请再次输入密码"
                show-password
              />
            </el-form-item>
            <el-button type="primary" color="#1677ff" class="full-btn" @click="submitRegister">注册并登录</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { loginByCodeApi, loginByPasswordApi, registerApi, sendLoginCodeApi } from '@/api';
import { useUserStore } from '@/store/modules/user';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const activeTab = ref('password');
const countdown = ref(0);
let countdownTimer = null;

const passwordFormRef = ref();
const codeFormRef = ref();
const registerFormRef = ref();

const passwordForm = reactive({
  phone: '',
  password: ''
});

const codeForm = reactive({
  phone: '',
  code: ''
});

const registerForm = reactive({
  phone: '',
  password: '',
  confirmPassword: ''
});

const validatePhone = (_rule, value, callback) => {
  const reg = /^1\d{10}$/;
  if (!reg.test(value || '')) {
    callback(new Error('请输入正确的11位手机号'));
    return;
  }
  callback();
};

const passwordRules = {
  phone: [{ required: true, validator: validatePhone, trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

const codeRules = {
  phone: [{ required: true, validator: validatePhone, trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
};

const registerRules = {
  phone: [{ required: true, validator: validatePhone, trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码长度不能少于6位', trigger: 'blur' }],
  confirmPassword: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (!value) {
          callback(new Error('请再次输入密码'));
          return;
        }
        if (value !== registerForm.password) {
          callback(new Error('两次输入密码不一致'));
          return;
        }
        callback();
      },
      trigger: 'blur'
    }
  ]
};

async function sendCode() {
  await codeFormRef.value?.validateField('phone');
  await sendLoginCodeApi(codeForm.phone);
  ElMessage.success('验证码已发送，请注意查收');
  startCountdown();
}

async function submitPasswordLogin() {
  await passwordFormRef.value?.validate();
  const result = await loginByPasswordApi(passwordForm);
  finishLogin(result, passwordForm.phone);
}

async function submitCodeLogin() {
  await codeFormRef.value?.validate();
  const result = await loginByCodeApi(codeForm);
  finishLogin(result, codeForm.phone);
}

async function submitRegister() {
  await registerFormRef.value?.validate();
  const result = await registerApi({
    phone: registerForm.phone,
    password: registerForm.password
  });
  finishLogin(result, registerForm.phone, '注册成功，已自动登录');
}

function startCountdown() {
  countdown.value = 60;
  clearCountdown();
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      clearCountdown();
    }
  }, 1000);
}

function clearCountdown() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function finishLogin(result, phone, successMessage = '登录成功') {
  const token = result?.token || result?.accessToken || result?.jwt || result?.data?.token || '';
  const userInfo = result?.userInfo || result?.user || result?.profile || {
    phone,
    name: `用户${phone?.slice(-4) || ''}`
  };

  if (!token) {
    ElMessage.warning('登录成功但未返回Token，请检查后端字段');
    return;
  }

  userStore.setToken(token);
  userStore.setUserInfo(userInfo);
  ElMessage.success(successMessage);

  const redirectPath = typeof route.query.redirect === 'string' ? route.query.redirect : '/web/index';
  router.push(redirectPath);
}

function goAdminLogin() {
  router.push('/admin/login');
}

onBeforeUnmount(() => {
  clearCountdown();
});
</script>

<style scoped>
.login-wrap {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.login-card {
  width: min(520px, 100%);
  padding: 30px 28px;
}

.title-area h1 {
  color: #0f172a;
  font-size: 30px;
}

.title-area p {
  margin-top: 8px;
  margin-bottom: 14px;
  color: #64748b;
  font-size: 14px;
}

.login-entry-switch {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.code-row {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
}

.full-btn {
  width: 100%;
}
</style>
