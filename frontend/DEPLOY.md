# 云客猫智能客服平台 — 前端部署教程

本教程面向前端新手，从零开始教你如何在本地 Windows 开发运行，以及如何部署到 Linux 服务器。

---

## 目录
- [一、Windows 本地开发启动](#一windows-本地开发启动)
- [二、Linux 服务器部署（Nginx 静态托管）](#二linux-服务器部署nginx-静态托管)
- [三、常见问题排查](#三常见问题排查)

---

## 一、Windows 本地开发启动

### 1.1 安装 Node.js（必须）

Node.js 是前端项目的运行环境，相当于 Java 项目需要 JDK。

**步骤：**
1. 打开浏览器访问 https://nodejs.org
2. 下载 **LTS 版本**（长期支持版，更稳定），推荐 20.x 或以上
3. 双击 `.msi` 安装包，一路点击 "Next" 完成安装
4. 验证安装是否成功：

```powershell
# 打开 PowerShell 或 CMD，输入：
node -v
# 应该显示类似 v20.14.0

npm -v
# 应该显示类似 10.7.0
```

> **新手提示：** `node` 是运行环境，`npm` 是 Node 自带的包管理器，用来下载项目依赖。

---

### 1.2 安装依赖

打开终端（PowerShell 或 CMD），进入前端项目目录，安装项目所需的所有第三方库：

```powershell
cd D:\intelligent-cloud-cat\icc-frontend
npm install
```

执行后会在 `icc-frontend` 目录下生成 `node_modules` 文件夹（存放依赖包）。

> 这个文件夹很大，已经在 `.gitignore` 中忽略，不需要提交到 Git。

---

### 1.3 确保后端已启动

前端通过代理访问后端 API，默认代理到 `http://localhost:8080`。

**确认后端已启动：**
- 先启动 Java 后端（icc-bootstrap 模块），确保 `http://localhost:8080` 可以访问
- 如果后端端口不是 8080，修改 `.env.development` 文件中的配置（见 1.4）

---

### 1.4 启动开发服务器

```powershell
cd D:\intelligent-cloud-cat\icc-frontend
npm run dev
```

启动后终端会显示：
```
  VITE v5.x.x  ready in xxx ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

在浏览器打开 `http://localhost:5173`，就能看到前端页面了。

**开发模式特点：**
- 修改代码后浏览器**自动刷新**，无需手动重启
- 前端以独立端口运行，API 请求自动代理到后端

**如果需要修改后端地址：**

编辑 `icc-frontend/vite.config.js`，修改 proxy target：

```javascript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',  // 改成你的后端地址
      changeOrigin: true
    }
  }
}
```

修改后需要**重启** `npm run dev`。

---

### 1.5 常用命令

| 命令 | 说明 |
|------|------|
| `npm run dev` | 启动开发服务器（热更新） |
| `npm run build` | 打包生产版本（输出到 dist/） |
| `npm run preview` | 本地预览打包后的结果 |

---

## 二、Linux 服务器部署（Nginx 静态托管）

以下以 **CentOS 7/8** 或 **Ubuntu 20.04+** 为例。

### 2.1 在 Windows 上打包

```powershell
cd D:\intelligent-cloud-cat\icc-frontend
npm run build
```

成功后 `icc-frontend` 下会生成 `dist` 目录，里面就是所有需要部署的静态文件。

---

### 2.2 上传文件到 Linux 服务器

**方式一：使用 SCP 上传（推荐）**

在 Windows PowerShell 中执行：
```powershell
scp -r D:\intelligent-cloud-cat\icc-frontend\dist\* root@你的服务器IP:/usr/share/nginx/html/icc/
```

**方式二：使用 FTP 工具**

推荐使用 **WinSCP** 或 **FileZilla**，将 `dist` 文件夹里的所有内容拖拽到服务器的 `/usr/share/nginx/html/icc/` 目录。

---

### 2.3 安装和配置 Nginx

SSH 登录到你的 Linux 服务器：

```bash
ssh root@你的服务器IP
```

#### 2.3.1 安装 Nginx

**Ubuntu/Debian：**
```bash
sudo apt update
sudo apt install nginx -y
```

**CentOS/RHEL：**
```bash
sudo yum install epel-release -y
sudo yum install nginx -y
```

#### 2.3.2 创建存放目录

```bash
sudo mkdir -p /usr/share/nginx/html/icc
```

#### 2.3.3 上传 Nginx 配置文件

项目中已经准备好了 `nginx.conf`，按以下步骤配置：

**方式一：SCP 上传配置文件**
```powershell
# 在你本地 Windows 执行
scp D:\intelligent-cloud-cat\icc-frontend\nginx.conf root@你的服务器IP:/etc/nginx/conf.d/icc.conf
```

**方式二：在服务器上手动创建**

SSH 登录服务器后：
```bash
sudo vi /etc/nginx/conf.d/icc.conf
```

然后将下面内容粘贴进去：

```nginx
# Nginx 配置 - 云客猫智能客服平台前端部署
server {
    listen       80;
    server_name  localhost;

    # 前端静态文件根目录
    root   /usr/share/nginx/html/icc;
    index  index.html;

    # Gzip 压缩（提升加载速度）
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript image/svg+xml;
    gzip_min_length 1000;
    gzip_comp_level 6;

    # 前端页面路由（history 模式必须）
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 反向代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 长连接支持（聊天流式响应需要）
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
    }
}
```

> **关键说明：**
> - `try_files $uri $uri/ /index.html;` — 这条配置让所有前端路由都指向 `index.html`，Vue Router 的 history 模式依赖这个
> - `proxy_buffering off;` — 关闭缓冲，聊天流式响应（SSE）必须关闭，否则消息会卡住
> - `proxy_read_timeout 3600s;` — 超时设为 1 小时，防止长时间对话被断开

#### 2.3.4 启动 Nginx

```bash
# 测试配置是否正确
sudo nginx -t

# 如果显示 "syntax is ok" 和 "test is successful"，则启动
sudo systemctl start nginx
sudo systemctl enable nginx    # 开机自启
```

---

### 2.4 配置防火墙

```bash
# 开放 80 端口（HTTP）
sudo firewall-cmd --add-port=80/tcp --permanent
sudo firewall-cmd --reload

# 或者如果用的是 ufw（Ubuntu）
sudo ufw allow 80/tcp
```

> **如果后端不在同一台服务器：** 需要修改 `nginx.conf` 中的 `proxy_pass http://127.0.0.1:8080` 为后端服务器的实际地址。

---

### 2.5 验证部署

在浏览器访问 `http://你的服务器IP`，应该能看到登录页面。

---

### 2.6 配置域名（可选）

如果已购买域名，修改 `nginx.conf` 中的 `server_name`：

```nginx
server {
    listen       80;
    server_name  your-domain.com;   # 改成你的域名
    root         /usr/share/nginx/html/icc;
    # ... 其余不变
}
```

然后重启 Nginx：
```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

### 2.7 配置 HTTPS（强烈推荐）

使用 Let's Encrypt 免费证书：

```bash
# 安装 certbot（Ubuntu）
sudo apt install certbot python3-certbot-nginx -y

# 一键配置 HTTPS
sudo certbot --nginx -d your-domain.com

# 证书会自动续期，无需手动操作
```

---

## 三、常见问题排查

### Q1：`npm install` 很慢或失败

设置国内镜像源：
```powershell
npm config set registry https://registry.npmmirror.com
```
然后重新 `npm install`。

---

### Q2：页面能打开但登录失败 / 请求报 404

检查后端是否已启动。确认 `http://localhost:8080`（或你的后端地址）可以访问。

---

### Q3：Linux 部署后页面空白

按 F12 打开浏览器开发者工具 → Console 面板，查看是否有报错。常见原因是 API 地址配置不对。

---

### Q4：Nginx 报 403 Forbidden

通常是文件权限问题：
```bash
sudo chmod -R 755 /usr/share/nginx/html/icc
```

也可能是 SELinux 限制（CentOS）：
```bash
sudo setenforce 0   # 临时关闭测试
# 永久关闭需编辑 /etc/selinux/config
```

---

### Q5：聊天消息不流式输出，而是一次性返回

检查 Nginx 配置中是否设置了 `proxy_buffering off;`。这条配置对 SSE 流式响应是必须的。

---

### Q6：修改代码后如何更新线上？

```powershell
# 1. 本地重新打包
cd D:\intelligent-cloud-cat\icc-frontend
npm run build

# 2. 上传覆盖服务器上的文件
scp -r dist\* root@你的服务器IP:/usr/share/nginx/html/icc/
```

Nginx 托管的静态文件，更新后无需重启 Nginx，刷新浏览器即可。

---

## 项目技术栈总览

| 技术 | 用途 |
|------|------|
| Vue 3 | 前端框架（Composition API） |
| JavaScript | 业务逻辑（`.js` + `<script setup>`，禁止 TypeScript） |
| Vite | 开发服务器 & 打包工具 |
| Vue Router | 前端路由（页面跳转） |
| Pinia | 状态管理（用户登录状态） |
| Axios | HTTP 请求客户端 |
| Fetch API | SSE 流式对话连接 |

---

> 如有问题，检查 Node.js 版本 >= 18，Nginx 版本 >= 1.18。
