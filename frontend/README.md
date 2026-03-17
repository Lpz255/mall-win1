# 电商前端工程（用户端 + 运营后台）

## 1. 启动步骤
```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

## 2. 路由说明
### 2.1 用户端（/web/*）
- `/web/index`：首页
- `/web/product/list`：商品列表
- `/web/product/detail/:id`：商品详情
- `/web/seckill`：秒杀专区
- `/web/cart`：购物车
- `/web/order`：订单中心
- `/web/login`：登录/注册

### 2.2 运营后台（/admin/*）
- `/admin/login`：管理员登录
- `/admin/index`：数据概览
- `/admin/product`：商品管理
- `/admin/seckill`：秒杀管理
- `/admin/order`：订单管理
- `/admin/user`：用户管理

## 3. 关键目录
```text
src
├─ api
│  ├─ request.js
│  ├─ web
│  └─ admin
├─ components
│  ├─ web
│  └─ admin
├─ directives
│  └─ permission.js
├─ hooks
│  └─ use_admin_permission.js
├─ router
│  ├─ index.js
│  └─ modules
│     ├─ web.js
│     └─ admin.js
├─ store
│  └─ modules
│     ├─ user.js
│     └─ admin.js
└─ views
   ├─ web
   ├─ admin
   └─ layouts
```

## 4. 接口与权限对接说明
### 4.1 代理
`vite.config.js` 已配置：
- `/api/** -> http://localhost:8080`

### 4.2 统一返回格式
Axios 默认按以下结构解析：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```
- 成功码：`200` 或 `0`
- 失败：自动中文提示
- `401`：自动跳登录页（用户端跳 `/web/login`，后台跳 `/admin/login`）

### 4.3 后台 RBAC
- 登录后请求：`/admin/rbac/permissions`
- 权限列表存储：`admin_permissions`
- 路由守卫：无权限会清空后台登录态并跳转 `/admin/login`
- 按钮级权限：`v-permission` 指令 + `AdminActionButtons` 内置权限过滤

## 5. 后台接口清单（src/api/admin）
- 认证：`/admin/login`、`/admin/profile`、`/admin/rbac/permissions`
- 统计：`/admin/stats/overview`
- 商品：`/admin/product/list|create|update|delete|status`
- 秒杀：`/admin/seckill/list|create|update|start|stop|delete`
- 订单：`/admin/order/list|detail|status|refund`
- 用户：`/admin/user/list|status`

## 6. 功能验证建议
1. 打开 `/admin/login`，使用管理员账号密码登录。  
2. 登录后访问 `/admin/index`，确认统计卡片加载。  
3. 在 `/admin/product` 测试新增/编辑/删除/上下架（含二次确认）。  
4. 在 `/admin/seckill` 测试活动新增/编辑/启动/停止。  
5. 在 `/admin/order` 测试状态修改与退款处理。  
6. 在 `/admin/user` 测试启用/禁用用户。  
7. 各管理页测试筛选、分页、Excel 导出。  

## 7. 编码与中文
- `.vue/.js/.ts/.json` 均为 UTF-8 编码；
- 页面文案、注释、提示信息均为中文；
- 核心操作（如商品上下架）已加二次确认弹窗。

## 8. 联调与优化补充
- 接口参数适配：请求层自动规范化 `ID` 类型与时间格式；
- 中文乱码兜底：后端消息若编码异常，前端自动尝试修复；
- 高频接口缓存：商品列表/分类/热门请求启用本地缓存；
- 高频操作优化：
  - 购物车数量修改使用防抖提交；
  - 秒杀按钮点击使用节流 + 提交中禁用；
- 倒计时优化：使用 `requestAnimationFrame` 驱动刷新。

## 9. 打包与部署
```bash
npm run build
```

产物目录：`dist/`

部署相关文件：
- Nginx 配置：[deploy/nginx.conf](E:/01XM/demo/20260314/frontend/deploy/nginx.conf)
- 联调手册：[docs/integration_manual.md](E:/01XM/demo/20260314/frontend/docs/integration_manual.md)
- 部署说明：[docs/deploy_guide.md](E:/01XM/demo/20260314/frontend/docs/deploy_guide.md)
