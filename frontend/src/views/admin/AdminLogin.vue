<template>
  <section class="admin-login-page">
    <div class="login-card card-panel">
      <div class="title-block">
        <h1>运营后台登录</h1>
        <p>请输入管理员账号密码</p>
      </div>
      <div class="login-entry-switch">
        <el-button type="primary" color="#ff7a00" @click="goUserLogin">用户登录</el-button>
        <el-button type="primary" color="#1677ff" plain disabled>管理员登录</el-button>
      </div>

      <el-form ref="formRef" :model="formModel" :rules="rules" label-position="top">
        <el-form-item label="管理员账号" prop="username">
          <el-input v-model="formModel.username" placeholder="请输入管理员账号" clearable />
        </el-form-item>
        <el-form-item label="登录密码" prop="password">
          <el-input
            v-model="formModel.password"
            type="password"
            placeholder="请输入登录密码"
            show-password
            clearable
          />
        </el-form-item>

        <el-button type="primary" color="#1677ff" class="submit-btn" :loading="submitting" @click="submitLogin">
          登录后台
        </el-button>
      </el-form>
    </div>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { adminLoginApi, getAdminPermissionApi, getAdminProfileApi } from '@/api';
import { DEFAULT_ADMIN_PERMISSIONS } from '@/constants/admin_permissions';
import { useAdminStore } from '@/store/modules/admin';

const router = useRouter();
const route = useRoute();
const adminStore = useAdminStore();

const formRef = ref();
const submitting = ref(false);
const formModel = reactive({
  username: '',
  password: ''
});

const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
};

async function submitLogin() {
  await formRef.value?.validate();
  submitting.value = true;

  try {
    const loginResult = await adminLoginApi(formModel);
    const adminToken = parseAdminToken(loginResult);
    if (!adminToken) {
      ElMessage.error('登录失败：后端未返回有效Token');
      return;
    }

    adminStore.setAdminToken(adminToken);
    adminStore.setAdminInfo(parseAdminInfo(loginResult));

    const [permissionRes, profileRes] = await Promise.allSettled([getAdminPermissionApi(), getAdminProfileApi()]);
    const permissionList = parsePermissionList(loginResult, permissionRes);
    const profile = parseProfile(profileRes, loginResult);

    adminStore.setPermissions(permissionList);
    adminStore.setAdminInfo(profile);

    ElMessage.success('登录成功');

    const redirectPath = typeof route.query.redirect === 'string' ? route.query.redirect : '/admin/index';
    router.push(redirectPath);
  } finally {
    submitting.value = false;
  }
}

function parseAdminToken(data) {
  return data?.token || data?.accessToken || data?.jwt || data?.data?.token || '';
}

function parseAdminInfo(data) {
  return data?.adminInfo || data?.user || data?.profile || {
    username: formModel.username,
    name: formModel.username
  };
}

function parsePermissionList(loginResult, permissionRes) {
  if (permissionRes.status === 'fulfilled') {
    const permissionData = permissionRes.value;
    if (Array.isArray(permissionData)) {
      return permissionData;
    }
    if (Array.isArray(permissionData?.permissions)) {
      return permissionData.permissions;
    }
  }

  if (Array.isArray(loginResult?.permissions)) {
    return loginResult.permissions;
  }
  if (Array.isArray(loginResult?.adminInfo?.permissions)) {
    return loginResult.adminInfo.permissions;
  }

  // 联调阶段兜底：若 RBAC 接口暂未返回权限，给出本地默认权限，保证后台可继续开发
  ElMessage.warning('未获取到RBAC权限列表，已启用本地开发权限');
  return DEFAULT_ADMIN_PERMISSIONS;
}

function parseProfile(profileRes, loginResult) {
  if (profileRes.status === 'fulfilled') {
    return profileRes.value || parseAdminInfo(loginResult);
  }
  return parseAdminInfo(loginResult);
}

function goUserLogin() {
  router.push('/web/login');
}
</script>

<style scoped>
.admin-login-page {
  display: grid;
  min-height: 100vh;
  padding: 24px;
  place-items: center;
}

.login-card {
  width: min(460px, 100%);
  padding: 28px;
}

.title-block {
  margin-bottom: 16px;
}

.title-block h1 {
  color: #0f172a;
  font-size: 30px;
}

.title-block p {
  margin-top: 6px;
  margin-bottom: 14px;
  color: #64748b;
  font-size: 14px;
}

.login-entry-switch {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.submit-btn {
  width: 100%;
}
</style>
