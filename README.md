# 志愿者服务管理系统

本项目是一个面向课程设计场景的前后端分离志愿者服务管理系统。当前仓库已经实现以下业务：

- 用户注册、登录、权限控制
- 志愿活动发布、报名、审核、签到签退
- 服务记录与排行
- 内容投稿与审核
- 公告、通知、反馈、捐赠
- 统计看板与审计日志

当前目标是让项目在完成环境配置后即可直接启动、验证和验收。

## 0. 文档基线与当前阶段

本仓库当前以以下源文档作为最新需求与设计基线：

- `软件需求规格说明书_2026-v1.3.docx`
- `概要设计v1.docx/.pdf`
- `志愿者管理系统_可行性分析报告(V2.0).docx/.pdf`

仓库中的 `README.md`、`docs/接口文档.md`、`docs/需求实现对应矩阵.md`、`docs/运维部署手册.md` 和迭代计划文档，负责把这些源文档与当前代码实现同步起来；已收工的需求规格、概要设计等 `docx/pdf` 文档和系统设计说明书保持归档状态，不再随每次代码调整反复改写。

当前代码库对应第四次迭代阶段的 v4 系统：

- 已具备核心业务主链，可直接验证注册、活动、报名审核、自助签到签退、服务记录、内容审核、公告通知、反馈、资源对接、捐赠、统计和审计。
- `v3` 已补齐 JWT 登录态、Axios 客户端、文件上传审计、支付结果确认、WebSocket 站内通知、外部通知任务、异常考勤更正、统计导出、系统配置和故障处理记录。
- `v4` 已完成生产化和质量增强范围：服务记录更正闭环、真实 SMTP 邮件发送、外部通知/支付/文件存储适配边界、SQL 样例数据、API 安全响应头、systemd 示例配置。
- 当前剩余工作主要集中在短信/支付/对象存储真实第三方账号联调、Flyway/Liquibase 标准迁移框架替换和更完整的自动化回归测试。

## 1. 技术栈

### 前端

- `Vue 3`
- `Vite`
- `Vue Router`
- `Axios`

说明：

- 当前前端代码使用 `JavaScript` 编写。
- 当前实现未引入 `TypeScript`、`Pinia`、`Element Plus` 和 `ECharts`。
- 前端通过 `/api` 调用后端接口。

### 后端

- `Java 17`
- `Spring Boot 3.2`
- `Spring Data JPA`
- `Spring Security Crypto`
- `MySQL 8`

### 当前部署形态

- 本地开发：前端开发服务器 + 后端 Spring Boot + MySQL
- 生产/验收部署：`Nginx + 前端静态文件 + Spring Boot Jar + MySQL`

## 2. 目录结构

```text
softwareEngineer/
  backend/                        后端工程
    src/main/java/...             Java 代码
    src/main/resources/...        配置与 SQL
      sql/init.sql                数据库初始化脚本
      sql/demo-data.sql           样例与测试数据脚本
    .env.example                  后端环境变量示例
    api-demo.http                 接口冒烟请求
    pom.xml
  frontend/                       前端工程
    src/...                       Vue 页面与接口
    .env.example                  前端环境变量示例
    package.json
    vite.config.js
  deploy/
    nginx.example.conf            Nginx 反向代理示例
    volunteer-service-backend.service.example
                                  systemd 服务示例
  scripts/
    setup-ubuntu.sh               Ubuntu 一键生成真实运行配置
    setup-windows.ps1             Windows 一键生成真实运行配置
  docs/
    接口文档.md                    当前代码对应的接口与外部服务边界说明
    运维部署手册.md                环境配置、部署发布和日常运维说明
    测试流程引导.md                提交前功能、接口和构建验证顺序
    视频演示流程.md                3 到 4 分钟演示流程
    需求实现对应矩阵.md            需求、设计分层与代码实现对应关系
    *迭代范围与目标.md             第二至第四次迭代范围和完成状态
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
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/volunteer_service?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8mb4
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

以下相对路径假设你从项目根目录启动 MySQL 客户端。

```sql
SOURCE backend/src/main/resources/sql/init.sql;
```

说明：

- 当前 `application.yml` 默认配置 `ddl-auto=none`，数据库结构由 `init.sql` 和 `sql/migrations/` 中的版本化脚本管理。
- 空库首次启动时，后端会自动执行 `backend/src/main/resources/sql/init.sql` 初始化基础表结构。
- 已有库启动时，后端会通过 `schema_migrations` 历史表登记迁移脚本，并按兼容判定补齐缺失字段、表和索引。

### 4.4 导入样例数据

默认情况下不会自动创建样例账号或导入样例数据。展示或接口测试前，可在 MySQL 客户端中执行：

以下相对路径同样假设你从项目根目录启动 MySQL 客户端。

```sql
SOURCE backend/src/main/resources/sql/demo-data.sql;
```

导入后可使用以下账号登录：

- 管理员：`admin@example.com / admin123`
- 组织方：`organizer@example.com / organizer123`
- 志愿者：`liuqi@example.com / volunteer123`

说明：

- `demo-data.sql` 会先清理它自己创建的样例账号、样例活动、样例资源和关联数据，再重新插入，便于重复导入。
- 样例账号使用邮箱格式用户名，可直接配合真实 SMTP 邮件发送能力。
- 真实环境不要导入样例数据；真实账号应通过注册或后台管理流程创建。

### 4.5 一键生成真实运行配置

仓库提供两个相对路径脚本，用于生成本机真实运行配置，不写入任何私密默认值：

Ubuntu：

```bash
bash scripts/setup-ubuntu.sh --db-user root --db-password '你的MySQL密码'
```

Windows PowerShell：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-windows.ps1 -DbUser root -DbPassword "你的MySQL密码"
```

如果希望同时初始化数据库并导入样例数据：

```bash
bash scripts/setup-ubuntu.sh --db-user root --db-password '你的MySQL密码' --init-db --with-demo
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-windows.ps1 -DbUser root -DbPassword "你的MySQL密码" -InitDb -WithDemo
```

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

默认情况下不会自动创建账号或导入样例数据。展示时推荐先执行 `demo-data.sql`，然后使用 `admin@example.com / admin123` 登录。

如果只需要初始化空系统管理员和组织方账号，也可以在 `backend/.env` 中启用 `VMS_BOOTSTRAP_ENABLED=true`。该开关只创建默认账号，不导入业务样例数据。

## 6. 一次性快速启动流程

如果你已经安装好 Java、Node、MySQL，并且只想尽快跑起来，按下面顺序操作：

1. 运行 `scripts/setup-windows.ps1` 或 `scripts/setup-ubuntu.sh` 生成 `.env`
2. 在 MySQL 中执行 `backend/src/main/resources/sql/init.sql`
3. 如需展示数据，再执行 `backend/src/main/resources/sql/demo-data.sql`
4. 启动后端：`cd backend && mvn clean spring-boot:run`
5. 启动前端：`cd frontend && npm install && npm run dev`
6. 打开 `http://127.0.0.1:5173`

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

- [deploy/nginx.example.conf](./deploy/nginx.example.conf)

思路是：

- Nginx 提供前端静态资源
- `/api/` 反向代理到 Spring Boot

### 7.4 systemd 服务示例

仓库中提供后端服务守护进程示例：

- [deploy/volunteer-service-backend.service.example](./deploy/volunteer-service-backend.service.example)

使用时需根据服务器实际路径、运行用户和 Jar 文件名调整 `WorkingDirectory`、`EnvironmentFile` 和 `ExecStart`。

## 8. 构建验证

建议在交付或迁移到其他电脑前执行一次构建验证。

### 后端验证

```powershell
cd backend
mvn -q -DskipTests package
```

### 前端验证

```powershell
cd frontend
npm run build
```

如果这两条都成功，说明当前机器上的依赖和构建环境是通的。

说明：

- 后端当前不保留 `src/test` 自动化测试目录，展示验证以 SQL 样例数据、`backend/api-demo.http` 和构建验证为主。
- 后端和前端都应作为本轮交付前的基础验证项执行。
- 完整人工验收流程按 [docs/测试流程引导.md](./docs/测试流程引导.md) 执行，覆盖志愿者、组织方、管理员三类角色和接口冒烟。
- 数据库结构治理以 `backend/src/main/resources/sql/init.sql` 和 `backend/src/main/resources/sql/migrations/` 中的实际脚本为准。

## 9. 环境文件说明

### 后端环境文件

示例文件：

- [backend/.env.example](./backend/.env.example)

主要配置项：

- `SERVER_ADDRESS`：后端监听地址
- `SERVER_PORT`：后端端口
- `SPRING_DATASOURCE_URL`：数据库连接串
- `SPRING_DATASOURCE_USERNAME`：数据库用户名
- `SPRING_DATASOURCE_PASSWORD`：数据库密码
- `SPRING_JPA_HIBERNATE_DDL_AUTO`：Hibernate DDL 策略，默认 `none`
- `VMS_BOOTSTRAP_ENABLED`：是否自动初始化默认账号，默认 `false`
- `VMS_BOOTSTRAP_ADMIN_*`：默认管理员信息
- `VMS_BOOTSTRAP_ORGANIZER_*`：默认组织方信息
- `VMS_EMAIL_ENABLED`：是否启用真实 SMTP 邮件发送，默认 `false`
- `VMS_EMAIL_HOST` / `VMS_EMAIL_PORT`：SMTP 服务器地址和端口
- `VMS_EMAIL_USERNAME` / `VMS_EMAIL_PASSWORD`：SMTP 登录账号和授权码或密码
- `VMS_EMAIL_FROM` / `VMS_EMAIL_FROM_NAME`：邮件发件地址和发件人名称

真实邮箱说明：

- 默认不启用真实邮件，外部通知仍使用本地发送实现。
- 启用 `VMS_EMAIL_ENABLED=true` 后，EMAIL 通道会通过 SMTP 真实发送。
- 当前系统复用用户 `username` 作为邮件收件地址；需要真实发送时，请让接收用户使用邮箱格式用户名注册。
- 常见 465 端口配置为 `VMS_EMAIL_SSL_ENABLED=true`、`VMS_EMAIL_STARTTLS_ENABLED=false`。
- 常见 587 端口配置为 `VMS_EMAIL_SSL_ENABLED=false`、`VMS_EMAIL_STARTTLS_ENABLED=true`。

### 前端环境文件

示例文件：

- [frontend/.env.example](./frontend/.env.example)

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
- 已补充需求实现对应矩阵、可 `SOURCE` 导入的样例数据 SQL、数据库兼容迁移、版本化迁移脚本、默认账号昵称纠偏、JWT 登录态和 Axios 客户端
- v4 已补齐服务记录更正闭环、真实 SMTP 邮件发送适配、外部通知发送适配端口、支付结果确认网关适配端口、本地文件对象存储适配端口、API 安全响应头和 systemd 示例配置
- 当前唯一维护的接口说明为 [docs/接口文档.md](./docs/接口文档.md)，根目录旧版接口设计文档已清理

### 10.1 样例数据与接口脚本

- 样例数据脚本：[backend/src/main/resources/sql/demo-data.sql](./backend/src/main/resources/sql/demo-data.sql)
- 接口冒烟脚本：[backend/api-demo.http](./backend/api-demo.http)
- Windows 配置脚本：[scripts/setup-windows.ps1](./scripts/setup-windows.ps1)
- Ubuntu 配置脚本：[scripts/setup-ubuntu.sh](./scripts/setup-ubuntu.sh)

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

当前项目使用 JWT 登录态。若后端修改了 `VMS_JWT_SECRET`、token 过期，或用户账号被停用/锁定，前端会清理本地登录状态，需要重新登录。

### 11.4 局域网访问不到

先检查：

- 后端监听地址是否为 `0.0.0.0`
- 前端开发服务监听地址是否为 `0.0.0.0`
- 本机防火墙是否放行端口
- 你访问的是不是本机真实局域网 IP

## 12. 建议的下一步

当前仓库已经完成第四次迭代范围，建议优先按 [第四次迭代范围与目标.md](./docs/第四次迭代范围与目标.md)、[需求实现对应矩阵.md](./docs/需求实现对应矩阵.md) 和 [测试流程引导.md](./docs/测试流程引导.md) 做验收走查。
部署和日常运维按 [运维部署手册.md](./docs/运维部署手册.md) 执行。

后续优先事项建议如下：

1. 若展示需要真实邮件验证，配置并联调 SMTP 账号；若需要进一步生产化，再接入真实短信服务、支付网关和对象存储实现类。
2. 若项目继续长期维护，再将当前轻量迁移器切换为 Flyway 或 Liquibase，并为已有库制定 baseline 方案。
3. 继续补齐前端交互回归测试和更完整的数据层集成测试。
4. 继续同步旧文档和最新概要设计，避免说明文档与代码口径再次漂移。

以下内容保留为后续扩展，而不是当前 v4 强制范围：

- `Redis` 或数据库持久会话
- 真实短信、真实支付接口
- 第三方对象存储和更细粒度文件访问策略
- 独立“支持者/捐赠者”账号体系
