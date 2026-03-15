# 志愿者服务管理系统（可移植/可复现版）

本项目是一个前后端分离系统，支持用户权限、活动管理、服务记录、内容审核、通知公告、捐赠、反馈、统计和审计日志。

## 1. 技术架构

- 后端：Java 17 + Spring Boot 3.2 + Spring Data JPA + MySQL 8
- 前端：Vue 3 + Vite
- 部署形态：单机部署（Windows PC 可直接作为服务端）

## 2. 目录说明

```text
softwareEngineer/
  backend/                  后端工程（Maven）
    src/main/java/...       业务代码
    src/main/resources/...  配置与SQL脚本
    pom.xml
  frontend/                 前端工程（Node + Vite）
    src/...                 页面与路由
    package.json
    package-lock.json
  README.md
```

说明：

- 根目录的 [package.json](/d:/Documents/WorkSpace/Projects/softwareEngineer/package.json) 与 [package-lock.json](/d:/Documents/WorkSpace/Projects/softwareEngineer/package-lock.json) 不参与前端构建，前端实际依赖以 `frontend/` 目录为准。

## 3. 环境需求（推荐版本）

1. 操作系统：Windows 10/11（Linux/macOS 也可，用等价命令）
2. JDK：17（必须）
3. Maven：3.9.x
4. Node.js：20 LTS（推荐 20.x）
5. npm：随 Node.js 安装（推荐 10.x）
6. MySQL：8.0+（建议 8.4.x）
7. 可选工具：Git、IDEA、VS Code、Postman/Apifox

端口约定：

1. 后端：`8080`
2. 前端开发服务：`5173`
3. 前端预览服务：`4173`
4. MySQL：`3306`

## 4. 第一次构建（从零）

### 4.1 配置数据库

在 MySQL 中执行：

```sql
CREATE DATABASE IF NOT EXISTS volunteer_service DEFAULT CHARACTER SET utf8mb4;
```

或执行项目脚本：

```sql
SOURCE D:/Documents/WorkSpace/Projects/softwareEngineer/backend/src/main/resources/sql/init.sql;
```

### 4.2 修改后端配置

编辑 [application.yml](/d:/Documents/WorkSpace/Projects/softwareEngineer/backend/src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    username: root
    password: 你的密码
```

### 4.3 启动后端

```powershell
cd D:\Documents\WorkSpace\Projects\softwareEngineer\backend
mvn clean spring-boot:run
```

后端地址：

- `http://localhost:8080/api`

### 4.4 启动前端

```powershell
cd D:\Documents\WorkSpace\Projects\softwareEngineer\frontend
npm install
npm run dev
```

前端地址：

- `http://localhost:5173`

## 5. 默认账号

后端首次启动会自动创建：

1. 管理员：`admin / admin123`
2. 组织方：`organizer / organizer123`

普通志愿者可在前端注册。

## 6. 构建产物（用于发布/验收）

### 6.1 后端打包

```powershell
cd D:\Documents\WorkSpace\Projects\softwareEngineer\backend
mvn clean package -DskipTests
```

产物：

- `backend\target\volunteer-service-backend-0.0.1-SNAPSHOT.jar`

启动 jar：

```powershell
java -jar target\volunteer-service-backend-0.0.1-SNAPSHOT.jar
```

### 6.2 前端打包

```powershell
cd D:\Documents\WorkSpace\Projects\softwareEngineer\frontend
npm run build
```

产物：

- `frontend\dist\`

本地预览：

```powershell
npm run preview
```

## 7. 如何复现（给别人测试）

目标：在另一台电脑上拿到压缩包后，按文档可直接跑通。

### 7.1 建议打包内容（轻量）

打包这些目录/文件：

1. `backend/src`
2. `backend/pom.xml`
3. `frontend/src`
4. `frontend/package.json`
5. `frontend/package-lock.json`
6. `frontend/index.html`
7. `frontend/vite.config.js`
8. `README.md`

建议不要打包（可减少体积）：

1. `.git/`
2. `backend/target/`
3. `frontend/node_modules/`
4. `frontend/dist/`

### 7.2 目标机器复现步骤

1. 安装第 3 节的软件环境
2. 解压项目到任意目录（例如 `D:\Test\softwareEngineer`）
3. 配置并启动 MySQL，创建数据库 `volunteer_service`
4. 修改 `backend/src/main/resources/application.yml` 的数据库密码
5. 启动后端：`cd backend && mvn spring-boot:run`
6. 安装前端依赖并启动：`cd frontend && npm install && npm run dev`
7. 浏览器访问：`http://localhost:5173`

### 7.3 离线复现（无外网）

如果目标机器无法联网：

1. 需要在打包时额外包含 `frontend/node_modules/`
2. 后端 Maven 依赖建议提前在目标机本地仓库准备，或一并迁移 `%USERPROFILE%\.m2\repository`（体积较大）

## 8. 如何移植到局域网访问

本项目已配置监听 `0.0.0.0`，支持局域网访问。

### 8.1 查询本机 IP

```powershell
ipconfig
```

假设本机 IP 为 `192.168.1.23`，其他设备可访问：

1. 前端：`http://192.168.1.23:5173`
2. 后端：`http://192.168.1.23:8080/api`

### 8.2 防火墙端口

放行：

1. `5173`（前端）
2. `8080`（后端）
3. `3306`（如果需要远程连数据库）

## 9. 启停服务顺序（适合个人 PC）

### 启动顺序

1. MySQL
2. 后端
3. 前端

### 停止顺序

1. 前端（终端 `Ctrl + C`）
2. 后端（终端 `Ctrl + C`）
3. MySQL（可选）

MySQL 服务控制（管理员 PowerShell，服务名按你机器实际为准）：

```powershell
Start-Service -Name MySQL80
Stop-Service -Name MySQL80
```

## 10. 一致性验证命令（复现实验推荐）

后端：

```powershell
cd backend
mvn -q test
```

前端：

```powershell
cd frontend
npm run build
```

若两条命令都成功，说明当前代码在该机器可正常构建。

## 11. 已实现模块清单

1. 用户与权限管理
2. 志愿活动管理
3. 服务记录与积分
4. 内容发布与审核
5. 公告与通知
6. 捐赠管理
7. 反馈处理
8. 统计看板
9. 审计日志（多条件筛选、分页、CSV 导出）

## 12. 常见问题

1. `Unknown database 'volunteer_service'`：
先建库，或检查 `application.yml` 账号密码是否正确。
2. MySQL `SOURCE` 报 `Unknown command '\D'`：
Windows 下路径改用 `/`，不要用 `\`。
3. 局域网访问不到：
优先检查防火墙和端口占用。
4. 前端依赖安装慢：
可配置 npm 镜像或使用你已迁移的 Node 配置。
