# 志愿者服务管理系统

本项目是一个面向课程设计场景的前后端分离志愿者服务管理系统。当前仓库已经实现以下业务：

- 用户注册、登录、权限控制
- 志愿活动发布、报名、审核、签到签退
- 服务记录与排行
- 内容投稿与审核
- 公告、通知、反馈、捐赠
- 统计看板与审计日志

当前目标是让项目在完成环境配置后即可直接启动、演示和验收。

## 0. 文档基线与当前阶段

本仓库当前以以下源文档作为最新需求与设计基线：

- `软件需求规格说明书_2026-v1.1.docx/.pdf`
- `概要设计v1.docx/.pdf`
- `志愿者管理系统_可行性分析报告(V2.0).docx/.pdf`

仓库中的 `README.md`、`系统设计说明书.md`、`需求实现对应矩阵.md` 和后续新增的迭代计划文档，负责把这些源文档与当前代码实现同步起来。

当前代码库对应第二次迭代阶段的课程版系统：

- 已具备核心业务主链，可直接演示注册、活动、报名、签到签退、服务记录、内容审核、公告通知、反馈、捐赠、统计和审计。
- `v2` 已补齐活动状态联动、用户级通知已读、基础活动评价、组织方数据边界和真实审计分页导出。
- 当前剩余工作主要集中在文档回填、数据库迁移治理标准化和自动化测试覆盖。

## 1. 技术栈

### 前端

- `Vue 3`
- `Vite`
- `Vue Router`

说明：

- 当前前端代码使用 `JavaScript` 编写。
- 当前实现未引入 `TypeScript`、`Pinia`、`Element Plus`、`Axios` 和 `ECharts`。
- 前端通过 `/api` 调用后端接口。

### 后端

- `Java 17`
- `Spring Boot 3.2`
- `Spring Data JPA`
- `Spring Security Crypto`
- `MySQL 8`

### 当前部署形态

- 本地开发：前端开发服务器 + 后端 Spring Boot + MySQL
- 生产/演示部署：`Nginx + 前端静态文件 + Spring Boot Jar + MySQL`

## 2. 目录结构

```text
softwareEngineer/
  backend/                        后端工程
    src/main/java/...             Java 代码
    src/main/resources/...        配置与 SQL
    .env.example                  后端环境变量示例
    pom.xml
  frontend/                       前端工程
    src/...                       Vue 页面与接口
    .env.example                  前端环境变量示例
    package.json
    vite.config.js
  deploy/
    nginx.example.conf            Nginx 反向代理示例
  README.md
```

说明：

- 根目录的 `package.json` 不参与前端构建。
- 前端实际依赖和脚本以 `frontend/` 下内容为准。

## 3. 运行环境要求

推荐版本如下：

- `JDK 17`
- `Maven 3.9+`
- `Node.js 20 LTS`
- `npm 10+`
- `MySQL 8.0+`

默认端口如下：

- 后端：`8080`
- 前端开发：`5173`
- 前端预览：`4173`
- MySQL：`3306`

## 4. 第一次使用前必须做的事

### 4.1 配置后端环境变量

先复制后端示例文件：

```powershell
Copy-Item backend/.env.example backend/.env
```

然后编辑 `backend/.env`，至少改这几个值：

```env
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/volunteer_service?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=你的MySQL密码
```

如果你想修改默认启动账号，也可以改：

```env
VMS_BOOTSTRAP_ADMIN_USERNAME=admin
VMS_BOOTSTRAP_ADMIN_PASSWORD=admin123
VMS_BOOTSTRAP_ORGANIZER_USERNAME=organizer
VMS_BOOTSTRAP_ORGANIZER_PASSWORD=organizer123
```

### 4.2 配置前端环境变量

复制前端示例文件：

```powershell
Copy-Item frontend/.env.example frontend/.env
```

默认情况下不需要改。如果你的后端端口或地址变了，再改：

```env
VITE_BACKEND_ORIGIN=http://127.0.0.1:8080
VITE_DEV_PORT=5173
```

### 4.3 初始化数据库

有两种方式。

方式一：先建库，让应用自动建表

```sql
CREATE DATABASE IF NOT EXISTS volunteer_service
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
```

方式二：执行项目里的初始化 SQL

```sql
SOURCE D:/Documents/WorkSpace/10-Projects/Course/Big-Assignments/softwareEngineer/backend/src/main/resources/sql/init.sql;
```

说明：

- 当前 `application.yml` 仍配置 `ddl-auto=update`，用于开发环境兼容建表。
- `v2` 新增了 `backend/src/main/resources/sql/migrations/` 版本化迁移脚本，以及启动期 `schema_migrations` 历史登记。
- 如果你已经手动执行了 `init.sql`，后端会在现有结构基础上结合迁移兼容逻辑继续运行。

## 5. 启动步骤

### 5.1 启动后端

```powershell
cd backend
mvn clean spring-boot:run
```

启动成功后，后端接口根路径为：

- `http://127.0.0.1:8080/api`

### 5.2 启动前端

新开一个终端：

```powershell
cd frontend
npm install
npm run dev
```

启动成功后，前端访问地址为：

- `http://127.0.0.1:5173`

### 5.3 登录系统

如果你启用了默认账号初始化，系统首次启动会自动创建：

- 管理员：`admin / admin123`
- 组织方：`organizer / organizer123`

你也可以通过注册页自己创建一个普通志愿者账号。

## 6. 一次性快速启动流程

如果你已经安装好 Java、Node、MySQL，并且只想尽快跑起来，按下面顺序操作：

1. 复制 `backend/.env.example` 为 `backend/.env`
2. 修改 `backend/.env` 里的数据库用户名和密码
3. 复制 `frontend/.env.example` 为 `frontend/.env`
4. 在 MySQL 中创建数据库 `volunteer_service`
5. 启动后端：`cd backend && mvn clean spring-boot:run`
6. 启动前端：`cd frontend && npm install && npm run dev`
7. 打开 `http://127.0.0.1:5173`

## 7. 打包与部署

### 7.1 后端打包

```powershell
cd backend
mvn clean package -DskipTests
```

生成文件：

- `backend/target/volunteer-service-backend-0.0.1-SNAPSHOT.jar`

启动方式：

```powershell
java -jar target/volunteer-service-backend-0.0.1-SNAPSHOT.jar
```

如果要带自定义环境文件，可先确保 `backend/.env` 存在，或者直接通过系统环境变量传参。

### 7.2 前端打包

```powershell
cd frontend
npm install
npm run build
```

生成目录：

- `frontend/dist/`

预览：

```powershell
npm run preview
```

### 7.3 Nginx 部署示例

仓库中已经提供示例配置：

- [deploy/nginx.example.conf](d:/Documents/WorkSpace/10-Projects/Course/Big-Assignments/softwareEngineer/deploy/nginx.example.conf)

思路是：

- Nginx 提供前端静态资源
- `/api/` 反向代理到 Spring Boot

## 8. 构建验证

建议在交付或迁移到其他电脑前执行一次构建验证。

### 后端验证

```powershell
cd backend
mvn -q test
```

### 前端验证

```powershell
cd frontend
npm run build
```

如果这两条都成功，说明当前机器上的依赖和构建环境是通的。

说明：

- 后端和前端都应作为本轮交付前的基础验证项执行。
- 数据库结构治理说明见 [数据库迁移方案.md](d:/Documents/WorkSpace/10-Projects/Course/Big-Assignments/softwareEngineer/数据库迁移方案.md)。

## 9. 环境文件说明

### 后端环境文件

示例文件：

- [backend/.env.example](d:/Documents/WorkSpace/10-Projects/Course/Big-Assignments/softwareEngineer/backend/.env.example)

主要配置项：

- `SERVER_ADDRESS`：后端监听地址
- `SERVER_PORT`：后端端口
- `SPRING_DATASOURCE_URL`：数据库连接串
- `SPRING_DATASOURCE_USERNAME`：数据库用户名
- `SPRING_DATASOURCE_PASSWORD`：数据库密码
- `VMS_BOOTSTRAP_ENABLED`：是否自动初始化默认账号
- `VMS_BOOTSTRAP_ADMIN_*`：默认管理员信息
- `VMS_BOOTSTRAP_ORGANIZER_*`：默认组织方信息

### 前端环境文件

示例文件：

- [frontend/.env.example](d:/Documents/WorkSpace/10-Projects/Course/Big-Assignments/softwareEngineer/frontend/.env.example)

主要配置项：

- `VITE_BACKEND_ORIGIN`：开发环境下代理到的后端地址
- `VITE_DEV_HOST`：前端开发服务监听地址
- `VITE_DEV_PORT`：前端开发端口
- `VITE_PREVIEW_HOST`：前端预览监听地址
- `VITE_PREVIEW_PORT`：前端预览端口

## 10. 当前实现和环境落地说明

本次已经做了以下落地处理：

- 后端数据库配置改为可通过 `backend/.env` 覆盖
- 后端默认账号改为可通过环境变量配置
- 前端代理地址和端口改为可通过 `frontend/.env` 配置
- 提供了 `backend/.env.example` 和 `frontend/.env.example`
- 提供了 Nginx 反向代理示例
- 登录页取消了写死的默认账号密码预填
- `.gitignore` 已忽略真实 `.env` 文件，避免泄露本机密码
- 已补充需求实现对应矩阵、演示数据初始化、数据库兼容迁移、版本化迁移脚本和默认账号昵称纠偏

## 11. 常见问题

### 11.1 后端启动时报数据库连接失败

先检查：

- MySQL 是否启动
- `backend/.env` 里的用户名密码是否正确
- 数据库 `volunteer_service` 是否存在

### 11.2 前端打开后接口请求失败

先检查：

- 后端是否已启动
- `frontend/.env` 中的 `VITE_BACKEND_ORIGIN` 是否正确
- 浏览器开发者工具里 `/api` 请求是否被代理到正确地址

### 11.3 登录后提示登录态失效

当前项目的登录态是应用内维护的，后端重启后旧 token 会失效。  
这属于当前版本的设计结果，重新登录即可。

### 11.4 局域网访问不到

先检查：

- 后端监听地址是否为 `0.0.0.0`
- 前端开发服务监听地址是否为 `0.0.0.0`
- 本机防火墙是否放行端口
- 你访问的是不是本机真实局域网 IP

## 12. 建议的下一步

当前仓库已经进入第二次迭代收口阶段，建议优先按 [第二次迭代范围与目标.md](./第二次迭代范围与目标.md) 和 [数据库迁移方案.md](./数据库迁移方案.md) 继续推进。

本轮剩余优先事项建议如下：

1. 继续同步旧文档和最新概要设计，避免说明文档与代码口径再次漂移。
2. 补齐高价值自动化测试，覆盖通知、活动状态、评价、审计筛选等关键回归点。
3. 在轻量迁移治理基础上，继续评估是否切换到标准迁移框架。
4. 视课程展示需求，再决定是否扩展附件、异常更正和更细粒度治理能力。

以下内容保留为后续扩展，而不是第二次迭代强制范围：

- `Redis/JWT` 登录态升级
- 短信、邮件、真实支付接口
- 通用文件上传与对象存储
- 独立“支持者/捐赠者”账号体系
