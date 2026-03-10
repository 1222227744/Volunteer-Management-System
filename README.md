# 志愿者服务管理系统（后端 MVP）

当前仓库已完成后端第一版骨架，技术栈：

- Java 17
- Spring Boot 3
- Spring Data JPA
- MySQL 8
- Vue 3 + Vite

## 1. 目录结构

```text
backend/
  src/main/java/com/volunteer/vms/
    auth/            登录注册、会话拦截
    user/            用户与角色、排行榜
    activity/        活动发布、报名、签到
    service/         服务记录与积分
    content/         内容发布与审核
    announcement/    公告
    notification/    通知
    donation/        捐赠
    feedback/        反馈
    dashboard/       统计看板
    audit/           操作审计日志
```

## 2. 启动前准备

1. 启动 MySQL，创建数据库（任选一种）：
1. 使用脚本：[backend/src/main/resources/sql/init.sql](/d:/Documents/WorkSpace/Projects/softwareEngineer/backend/src/main/resources/sql/init.sql)
1. 或先手工执行：

```sql
CREATE DATABASE volunteer_service DEFAULT CHARACTER SET utf8mb4;
```

Windows 的 `mysql` 命令行执行 `SOURCE` 时，路径请用正斜杠：

```sql
SOURCE D:/Documents/WorkSpace/Projects/softwareEngineer/backend/src/main/resources/sql/init.sql;
```

2. 修改配置文件中的数据库账号密码：

[backend/src/main/resources/application.yml](/d:/Documents/WorkSpace/Projects/softwareEngineer/backend/src/main/resources/application.yml)

默认值为：

```yaml
spring:
  datasource:
    username: root
    password: root
```

## 3. 启动后端

在项目根目录执行：

```powershell
cd backend
mvn clean spring-boot:run
```

启动后地址：

- API 基础地址：`http://localhost:8080/api`
- 已配置监听地址：`0.0.0.0`（局域网可访问）

## 4. 启动前端

在项目根目录执行：

```powershell
cd frontend
npm install
npm run dev
```

前端访问地址：

- `http://localhost:5173`
- 前端开发服务已绑定：`0.0.0.0`

## 5. 默认初始化账号

系统首次启动会自动创建：

- 管理员：`admin / admin123`
- 组织方：`organizer / organizer123`

普通志愿者可通过 `/api/auth/register` 注册。

## 6. 鉴权方式

1. 登录：`POST /api/auth/login`
2. 从响应里拿到 `token`
3. 请求头携带：

```text
Authorization: Bearer <token>
```

## 7. 已实现模块（对应需求文档）

1. 用户与权限管理：注册、登录、角色修改、积分排行
2. 志愿活动管理：活动发布、活动列表、报名、签到、状态变更
3. 志愿服务记录：登记服务时长、个人记录查询
4. 评价与激励：服务记录自动积分、排行榜
5. 内容发布与审核：投稿、待审核列表、审核通过/驳回
6. 公告与通知：公告发布、个人/系统通知、已读标记
7. 捐赠与支持：捐赠记录提交、管理端汇总
8. 统计分析：管理看板统计接口
9. 互动反馈：反馈提交、处理回复、反馈结果通知
10. 操作审计：记录角色变更、活动状态变更、签到、审核等关键动作

## 8. 快速接口测试

可直接用 IDEA/VS Code 的 HTTP Client 打开：

[backend/api-demo.http](/d:/Documents/WorkSpace/Projects/softwareEngineer/backend/api-demo.http)

## 9. 前端当前页面

1. 登录与注册
2. 活动列表、活动发布、活动报名
3. 活动执行管理（状态更新、报名名单、签到）
4. 我的报名
5. 服务记录（个人记录、管理端登记）
6. 内容发布与审核（投稿、待审核处理）
7. 捐赠管理（个人捐赠、管理端总览）
8. 反馈管理（提交反馈、管理端处理）
9. 公告发布与公告列表
10. 消息通知与已读
11. 志愿者积分排行榜
12. 管理看板统计
13. 管理员用户管理（用户列表、角色调整）
14. 审计日志（按动作/关键字/操作人/目标类型/时间范围筛选 + 分页）
15. 审计日志导出（CSV）

## 10. 下一步建议

1. 增加文件上传（活动图片、服务证明）。
2. 为审计日志增加多条件保存（常用筛选方案一键复用）。
3. 将会话从内存 token 升级为 JWT + Redis（可选）。

## 11. 局域网访问方式

1. 在部署机器上查看本机 IP：

```powershell
ipconfig
```

2. 假设本机 IP 是 `192.168.1.23`，局域网其他设备可访问：

- 前端：`http://192.168.1.23:5173`
- 后端 API：`http://192.168.1.23:8080/api`

3. Windows 防火墙需放行端口 `5173` 与 `8080`，否则局域网设备无法连通。
