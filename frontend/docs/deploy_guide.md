# 生产部署说明（Nginx）

## 1. 打包
```bash
cd frontend
npm install
npm run build
```

打包结果目录：`frontend/dist`

## 2. 部署文件
1. 前端静态文件：`dist/*`
2. Nginx 配置：`deploy/nginx.conf`

## 3. 部署步骤
1. 上传 `dist` 到服务器目录，例如 `/data/www/ecommerce/dist`。  
2. 上传 `deploy/nginx.conf` 并替换到 Nginx 主配置。  
3. 根据真实后端地址调整配置中的 `upstream ecommerce_backend`。  
4. 根据后端接口是否保留 `/api` 前缀调整 `location /api/` 段。  
5. 检查配置并重载：
```bash
nginx -t
nginx -s reload
```

## 4. 联调建议
1. 先访问首页确认静态资源 200。  
2. 再访问 `/api` 接口确认代理通畅。  
3. 检查登录、加购、下单、后台管理等关键链路。  
4. 若中文异常，确认：
   - 后端返回 `charset=UTF-8`
   - Nginx `charset utf-8;`

