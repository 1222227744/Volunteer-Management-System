-- Volunteer Service Management System demo/test data.
-- Usage after schema initialization:
--   mysql> SOURCE backend/src/main/resources/sql/demo-data.sql;
--
-- This script is intentionally idempotent for its own demo records. It first
-- removes data created by this file, then reinserts a coherent v4 demo dataset.

USE volunteer_service;
SET NAMES utf8mb4;

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS demo_user_ids;
DROP TEMPORARY TABLE IF EXISTS demo_activity_ids;
DROP TEMPORARY TABLE IF EXISTS demo_resource_ids;
DROP TEMPORARY TABLE IF EXISTS demo_need_ids;

CREATE TEMPORARY TABLE demo_user_ids (id BIGINT PRIMARY KEY);
CREATE TEMPORARY TABLE demo_activity_ids (id BIGINT PRIMARY KEY);
CREATE TEMPORARY TABLE demo_resource_ids (id BIGINT PRIMARY KEY);
CREATE TEMPORARY TABLE demo_need_ids (id BIGINT PRIMARY KEY);

INSERT INTO demo_user_ids
SELECT id FROM users
WHERE username IN (
  'admin@example.com',
  'organizer@example.com',
  'liuqi@example.com',
  'chenmo@example.com',
  'linan@example.com',
  'zhaowei@example.com',
  'sunhao@example.com'
);

INSERT INTO demo_activity_ids
SELECT id FROM activities
WHERE title IN (
  '城市公园环保清洁行动',
  '社区图书馆阅读陪伴',
  '校园迎新志愿服务',
  '敬老院陪伴慰问'
);

INSERT INTO demo_resource_ids
SELECT id FROM public_resources
WHERE name IN ('医用口罩', '儿童绘本');

INSERT INTO demo_need_ids
SELECT id FROM help_needs
WHERE title IN ('敬老院探访防护物资需求', '社区儿童阅读角补充图书');

DELETE FROM external_notification_tasks WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM notifications WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM audit_logs
WHERE operator_id IN (SELECT id FROM demo_user_ids)
   OR detail LIKE '%演示%'
   OR detail LIKE '%城市公园环保清洁行动%'
   OR detail LIKE '%校园迎新志愿服务%';
DELETE FROM service_record_corrections
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR activity_id IN (SELECT id FROM demo_activity_ids);
DELETE FROM activity_attendance_corrections
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR activity_id IN (SELECT id FROM demo_activity_ids);
DELETE FROM activity_feedbacks
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR activity_id IN (SELECT id FROM demo_activity_ids);
DELETE FROM service_records
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR activity_id IN (SELECT id FROM demo_activity_ids);
DELETE FROM activity_registrations
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR activity_id IN (SELECT id FROM demo_activity_ids);
DELETE FROM honor_records WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM content_posts WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM announcements
WHERE publisher_id IN (SELECT id FROM demo_user_ids)
   OR title IN ('五一期间活动安排说明', '服务记录公示规则更新');
DELETE FROM donations
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR donor_name IN ('刘琪', '陈墨', '组织方账号');
DELETE FROM donation_orders
WHERE user_id IN (SELECT id FROM demo_user_ids)
   OR callback_token LIKE 'demo-%';
DELETE FROM feedbacks WHERE user_id IN (SELECT id FROM demo_user_ids);
DELETE FROM resource_matches
WHERE resource_id IN (SELECT id FROM demo_resource_ids)
   OR need_id IN (SELECT id FROM demo_need_ids);
DELETE FROM public_resources WHERE id IN (SELECT id FROM demo_resource_ids);
DELETE FROM help_needs WHERE id IN (SELECT id FROM demo_need_ids);
DELETE FROM incident_records WHERE title IN ('演示环境 SMTP 联调', '数据库初始化复核');
DELETE FROM activities WHERE id IN (SELECT id FROM demo_activity_ids);
DELETE FROM users WHERE id IN (SELECT id FROM demo_user_ids);

INSERT INTO users (
  username, password, display_name, role, phone, service_intention,
  account_status, verification_status, verification_comment, points, created_at
) VALUES
  ('admin@example.com', '$2b$10$n.2lffSIUPTwBXAlRrdVPudsrmpqI.vvBBAHqdJbiy1dqnAlq42u.', '系统管理员', 'ADMIN', '13800000001', '平台运营与审计管理', 'ENABLED', 'VERIFIED', NULL, 320, DATE_SUB(NOW(), INTERVAL 45 DAY)),
  ('organizer@example.com', '$2b$10$2fpU63nInMxeCABipRorguMoLgSrOFysFvLuVub2YmNMRXZJjUj06', '组织方账号', 'ORGANIZER', '13800000002', '社区公益活动组织', 'ENABLED', 'VERIFIED', NULL, 180, DATE_SUB(NOW(), INTERVAL 44 DAY)),
  ('liuqi@example.com', '$2b$10$0goAJqfjbVvbvau99G66yODh46hcawi9Aryvm6p/DbSwfoyy/RA.m', '刘琪', 'VOLUNTEER', '13800000003', '校园迎新、秩序维护、活动宣传', 'ENABLED', 'PENDING', NULL, 96, DATE_SUB(NOW(), INTERVAL 30 DAY)),
  ('chenmo@example.com', '$2b$10$0goAJqfjbVvbvau99G66yODh46hcawi9Aryvm6p/DbSwfoyy/RA.m', '陈墨', 'VOLUNTEER', '13800000004', '环保清洁、社区陪伴', 'ENABLED', 'VERIFIED', NULL, 132, DATE_SUB(NOW(), INTERVAL 29 DAY)),
  ('linan@example.com', '$2b$10$0goAJqfjbVvbvau99G66yODh46hcawi9Aryvm6p/DbSwfoyy/RA.m', '林安', 'VOLUNTEER', '13800000005', '儿童阅读陪伴、图书整理', 'ENABLED', 'VERIFIED', NULL, 68, DATE_SUB(NOW(), INTERVAL 28 DAY)),
  ('zhaowei@example.com', '$2b$10$0goAJqfjbVvbvau99G66yODh46hcawi9Aryvm6p/DbSwfoyy/RA.m', '赵薇', 'VOLUNTEER', '13800000006', '环保宣传、现场引导', 'ENABLED', 'VERIFIED', NULL, 110, DATE_SUB(NOW(), INTERVAL 27 DAY)),
  ('sunhao@example.com', '$2b$10$0goAJqfjbVvbvau99G66yODh46hcawi9Aryvm6p/DbSwfoyy/RA.m', '孙昊', 'VOLUNTEER', '13800000007', '物资搬运、后勤支持', 'ENABLED', 'REJECTED', '演示数据：资料信息不完整。', 42, DATE_SUB(NOW(), INTERVAL 26 DAY));

SET @admin_id := (SELECT id FROM users WHERE username = 'admin@example.com');
SET @organizer_id := (SELECT id FROM users WHERE username = 'organizer@example.com');
SET @liu_id := (SELECT id FROM users WHERE username = 'liuqi@example.com');
SET @chen_id := (SELECT id FROM users WHERE username = 'chenmo@example.com');
SET @lin_id := (SELECT id FROM users WHERE username = 'linan@example.com');
SET @zhao_id := (SELECT id FROM users WHERE username = 'zhaowei@example.com');
SET @sun_id := (SELECT id FROM users WHERE username = 'sunhao@example.com');

INSERT INTO activities (
  title, description, location, start_time, end_time, registration_deadline,
  participation_requirement, attachment_file_id, max_participants, status,
  check_code, organizer_id, created_at
) VALUES
  ('城市公园环保清洁行动', '组织志愿者分组清理步道、草坪和水域周边垃圾，并向游客宣传垃圾分类知识。', '城南市民公园', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 12 DAY), '需自备防晒用品，现场统一发放手套和垃圾袋。', NULL, 20, 'FINISHED', 'PARK2026', @organizer_id, DATE_SUB(NOW(), INTERVAL 15 DAY)),
  ('社区图书馆阅读陪伴', '为社区儿童开展阅读陪伴、绘本整理和借阅秩序维护。', '青禾社区图书馆', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 3 DAY), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 4 DAY), '需有耐心，能够完成基础图书分类和儿童陪伴阅读。', NULL, 12, 'FINISHED', 'BOOK2026', @organizer_id, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  ('校园迎新志愿服务', '在报到点协助路线指引、物资发放和新生咨询接待。', '大学生活动中心', DATE_ADD(NOW(), INTERVAL 2 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 2 DAY), INTERVAL 6 HOUR), DATE_ADD(NOW(), INTERVAL 1 DAY), '需熟悉校园主要路线，能够连续服务不少于 3 小时。', NULL, 30, 'PUBLISHED', 'HELLO26', @organizer_id, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ('敬老院陪伴慰问', '陪伴老人聊天、协助整理房间并进行简单文娱活动组织。', '康乐敬老院', DATE_ADD(NOW(), INTERVAL 5 DAY), DATE_ADD(DATE_ADD(NOW(), INTERVAL 5 DAY), INTERVAL 4 HOUR), DATE_ADD(NOW(), INTERVAL 4 DAY), '需具备基本沟通能力，活动当天服从现场分组安排。', NULL, 15, 'PUBLISHED', 'ELDER26', @organizer_id, NOW());

SET @park_id := (SELECT id FROM activities WHERE title = '城市公园环保清洁行动');
SET @library_id := (SELECT id FROM activities WHERE title = '社区图书馆阅读陪伴');
SET @campus_id := (SELECT id FROM activities WHERE title = '校园迎新志愿服务');
SET @elder_id := (SELECT id FROM activities WHERE title = '敬老院陪伴慰问');

INSERT INTO activity_registrations (
  activity_id, user_id, status, registered_at, check_in_at, check_out_at,
  review_comment, reviewed_at
) VALUES
  (@park_id, @chen_id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 4 HOUR), '活动完成，服务记录已登记。', DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (@park_id, @zhao_id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 20 MINUTE), DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 230 MINUTE), '活动完成，服务记录已登记。', DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (@park_id, @liu_id, 'CANCELLED', DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL, '志愿者因时间冲突主动取消报名。', DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (@library_id, @lin_id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_ADD(DATE_SUB(NOW(), INTERVAL 3 DAY), INTERVAL 3 HOUR), '活动完成，服务记录已登记。', DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (@library_id, @sun_id, 'REJECTED', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, '本场阅读陪伴名额有限，优先安排已有阅读活动经验的志愿者。', DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (@campus_id, @liu_id, 'PENDING', DATE_SUB(NOW(), INTERVAL 20 HOUR), NULL, NULL, NULL, NULL),
  (@campus_id, @chen_id, 'APPROVED', DATE_SUB(NOW(), INTERVAL 18 HOUR), NULL, NULL, '符合活动参与要求。', DATE_SUB(NOW(), INTERVAL 12 HOUR)),
  (@campus_id, @zhao_id, 'APPROVED', DATE_SUB(NOW(), INTERVAL 10 HOUR), NULL, NULL, '符合活动参与要求。', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
  (@elder_id, @lin_id, 'PENDING', DATE_SUB(NOW(), INTERVAL 4 HOUR), NULL, NULL, NULL, NULL);

SET @chen_park_registration_id := (SELECT id FROM activity_registrations WHERE activity_id = @park_id AND user_id = @chen_id);

INSERT INTO activity_attendance_corrections (
  activity_id, registration_id, user_id, action, before_status, after_status,
  before_check_in_at, after_check_in_at, before_check_out_at, after_check_out_at,
  reason, corrected_by, corrected_by_name, corrected_at
) VALUES
  (@park_id, @chen_park_registration_id, @chen_id, 'SET_CHECKED_OUT', 'CHECKED_IN', 'CHECKED_OUT',
   DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY), NULL,
   DATE_ADD(DATE_SUB(NOW(), INTERVAL 10 DAY), INTERVAL 4 HOUR), '演示数据：志愿者签退时间由组织方补录。', @organizer_id, '组织方账号', DATE_SUB(NOW(), INTERVAL 9 DAY));

INSERT INTO service_records (
  user_id, activity_id, hours, achievement, evidence_url, evidence_file_id, created_at
) VALUES
  (@chen_id, @park_id, 4.00, '负责公园东侧步道清洁与垃圾分类宣传，共完成 8 袋可回收垃圾整理。', 'https://example.com/evidence/park-chen', NULL, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (@zhao_id, @park_id, 3.50, '完成水域沿线清洁和游客引导，协助现场秩序维护。', 'https://example.com/evidence/park-zhao', NULL, DATE_SUB(NOW(), INTERVAL 9 DAY)),
  (@lin_id, @library_id, 3.00, '组织儿童阅读陪伴活动并完成图书归类与借阅登记辅助。', 'https://example.com/evidence/library-lin', NULL, DATE_SUB(NOW(), INTERVAL 2 DAY));

SET @lin_library_record_id := (SELECT id FROM service_records WHERE activity_id = @library_id AND user_id = @lin_id);

INSERT INTO service_record_corrections (
  service_record_id, activity_id, user_id, requester_id, requester_name, status,
  old_hours, new_hours, old_achievement, new_achievement, old_evidence_url,
  new_evidence_url, old_evidence_file_id, new_evidence_file_id, reason,
  review_comment, reviewed_by, reviewed_by_name, requested_at, reviewed_at
) VALUES
  (@lin_library_record_id, @library_id, @lin_id, @lin_id, '林安', 'PENDING',
   3.00, 3.50, '组织儿童阅读陪伴活动并完成图书归类与借阅登记辅助。',
   '组织儿童阅读陪伴活动，补充完成读者登记台整理和活动后书架归位。',
   'https://example.com/evidence/library-lin', 'https://example.com/evidence/library-lin-correction',
   NULL, NULL, '实际服务结束后继续完成书架归位，申请补充 0.5 小时。', NULL, NULL, NULL,
   DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

INSERT INTO activity_feedbacks (activity_id, user_id, rating, comment, created_at) VALUES
  (@park_id, @chen_id, 5, '活动组织清晰，分工明确，现场物资和路线说明都很到位。', DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (@park_id, @zhao_id, 4, '环保宣传和清洁任务衔接顺畅，建议后续增加中途补水点。', DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (@library_id, @lin_id, 5, '活动节奏舒适，儿童阅读陪伴和书架整理都安排得很合理。', DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO content_posts (
  user_id, title, content, image_file_id, status, review_comment, created_at, reviewed_at
) VALUES
  (@chen_id, '公园清洁活动成果纪实', '我们分为四个小组完成了步道、草坪和湖边区域的清洁，还向游客发放了垃圾分类宣传卡片。', NULL, 'APPROVED', '内容详实，可用于成果展示。', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (@lin_id, '社区阅读陪伴心得', '本次活动中孩子们参与度很高，志愿者还补充整理了借阅区书架标签。', NULL, 'PENDING', NULL, DATE_SUB(NOW(), INTERVAL 20 HOUR), NULL),
  (@liu_id, '迎新志愿者招募倡议', '建议迎新活动增加行李搬运路线图和咨询台值班说明。', NULL, 'REJECTED', '请区分活动倡议与个人心得后重新投稿。', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 20 HOUR));

INSERT INTO announcements (title, content, publisher_id, created_at) VALUES
  ('五一期间活动安排说明', '节日期间平台仍开放报名，但线下服务活动将按照组织方实际通知执行。', @organizer_id, DATE_SUB(NOW(), INTERVAL 6 DAY)),
  ('服务记录公示规则更新', '服务记录登记需以签到签退留痕为前提，异常情况由管理员复核。', @admin_id, DATE_SUB(NOW(), INTERVAL 5 DAY));

INSERT INTO donation_orders (
  user_id, donor_name, amount, message, status, callback_token, payment_note, created_at, paid_at
) VALUES
  (@liu_id, '刘琪', 50.00, '支持迎新志愿者饮水补给', 'PAID', 'demo-liuqi-paid-token', '演示订单：支付成功', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (@chen_id, '陈墨', 88.00, '用于环保活动物资采购', 'PAID', 'demo-chenmo-paid-token', '演示订单：支付成功', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@lin_id, '林安', 30.00, '待演示模拟支付的订单', 'PENDING', 'demo-linan-pending-token', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
  (@sun_id, '孙昊', 20.00, '演示订单：支付失败不生成捐赠记录', 'FAILED', 'demo-sunhao-failed-token', '银行卡余额不足', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

SET @liu_paid_order_id := (SELECT id FROM donation_orders WHERE callback_token = 'demo-liuqi-paid-token');
SET @chen_paid_order_id := (SELECT id FROM donation_orders WHERE callback_token = 'demo-chenmo-paid-token');

INSERT INTO donations (donor_name, user_id, order_id, amount, message, created_at) VALUES
  ('刘琪', @liu_id, @liu_paid_order_id, 50.00, '支持迎新志愿者饮水补给', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('陈墨', @chen_id, @chen_paid_order_id, 88.00, '用于环保活动物资采购', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  ('组织方账号', @organizer_id, NULL, 200.00, '补充社区活动宣传展板费用', DATE_SUB(NOW(), INTERVAL 4 DAY));

INSERT INTO feedbacks (user_id, content, status, reply, created_at, resolved_at) VALUES
  (@liu_id, '希望活动报名页面增加报名状态说明，便于区分待审核和已通过。', 'RESOLVED', '已在活动执行页补充审核流程，后续会继续优化报名页说明。', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
  (@sun_id, '建议捐赠记录支持导出回执，方便后续汇总。', 'OPEN', NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

INSERT INTO public_resources (
  name, category, source, quantity, unit, available_scope, status, created_by, created_at
) VALUES
  ('医用口罩', '防护物资', '社区爱心企业', 300, '只', '社区老人探访、线下活动防护', 'RESERVED', @organizer_id, DATE_SUB(NOW(), INTERVAL 5 DAY)),
  ('儿童绘本', '图书物资', '青禾书店', 80, '册', '社区图书馆与儿童阅读活动', 'AVAILABLE', @organizer_id, DATE_SUB(NOW(), INTERVAL 4 DAY));

SET @mask_resource_id := (SELECT id FROM public_resources WHERE name = '医用口罩');
SET @book_resource_id := (SELECT id FROM public_resources WHERE name = '儿童绘本');

INSERT INTO help_needs (
  title, requester, content, quantity, unit, location, required_at, status, created_by, created_at
) VALUES
  ('敬老院探访防护物资需求', '康乐敬老院', '近期志愿者探访活动需要基础防护物资，用于现场发放和备用。', 120, '只', '康乐敬老院', DATE_ADD(NOW(), INTERVAL 4 DAY), 'MATCHED', @organizer_id, DATE_SUB(NOW(), INTERVAL 3 DAY)),
  ('社区儿童阅读角补充图书', '青禾社区', '社区阅读角希望补充适合小学低年级儿童阅读的绘本和科普读物。', 60, '册', '青禾社区图书馆', DATE_ADD(NOW(), INTERVAL 10 DAY), 'OPEN', @organizer_id, DATE_SUB(NOW(), INTERVAL 2 DAY));

SET @elder_need_id := (SELECT id FROM help_needs WHERE title = '敬老院探访防护物资需求');

INSERT INTO resource_matches (
  resource_id, need_id, allocated_quantity, progress_note, status, created_by, created_at, updated_at
) VALUES
  (@mask_resource_id, @elder_need_id, 120, '已完成资源锁定，等待活动前统一发放。', 'MATCHED', @organizer_id, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL);

INSERT INTO honor_records (
  user_id, honor_type, title, reason, showcase_text, related_activity_id,
  points_awarded, awarded_by, awarded_at, public_visible
) VALUES
  (@chen_id, 'EXCELLENT_VOLUNTEER', '优秀志愿者', '累计服务时长和活动评价表现突出，在公园环保清洁行动中承担重点区域清洁任务。', '陈墨在城市公园环保清洁行动中主动承担东侧步道清洁和垃圾分类宣传任务，现场沟通耐心，服务记录完整，获得活动评价 5 分。', @park_id, 30, @admin_id, DATE_SUB(NOW(), INTERVAL 1 DAY), b'1'),
  (@lin_id, 'SERVICE_STAR', '阅读陪伴服务之星', '在社区阅读陪伴活动中服务态度稳定，活动成果记录清晰。', '林安在社区图书馆阅读陪伴活动中协助儿童阅读和图书整理，能够持续关注儿童参与体验，是阅读陪伴场景中的稳定志愿力量。', @library_id, 20, @admin_id, DATE_SUB(NOW(), INTERVAL 1 DAY), b'1');

INSERT INTO notifications (user_id, title, content, read_flag, created_at) VALUES
  (@admin_id, '系统演示数据已加载', '当前环境已预置活动、报名、服务记录、反馈和审计日志数据，可直接用于课程展示。', b'0', NOW()),
  (@organizer_id, '系统演示数据已加载', '当前环境已预置活动、报名、服务记录、反馈和审计日志数据，可直接用于课程展示。', b'0', NOW()),
  (@liu_id, '反馈已处理', '你提交的页面优化建议已完成处理，请前往反馈页面查看回复。', b'0', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
  (@chen_id, '活动报名审核通过', '你报名的“校园迎新志愿服务”已审核通过，请按时参加。', b'1', DATE_SUB(NOW(), INTERVAL 12 HOUR)),
  (@lin_id, '服务记录更正申请已提交', '你在活动“社区图书馆阅读陪伴”中的服务记录更正申请已提交，等待组织方处理。', b'0', DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO external_notification_tasks (
  user_id, channel, title, content, recipient, status, retry_count, max_retries,
  last_error, created_at, last_tried_at, sent_at
) VALUES
  (@liu_id, 'EMAIL', '反馈已处理', '你提交的页面优化建议已完成处理，请前往反馈页面查看回复。', 'liuqi@example.com', 'SENT', 1, 3, NULL, DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
  (@sun_id, 'SMS', '资料审核未通过', '你的实名资料审核未通过，请补充资料后重新提交。', '13800000007', 'FAILED', 1, 3, '课程版模拟短信失败记录，用于演示重试。', DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR), NULL);

INSERT INTO incident_records (
  title, description, severity, status, handling_measure, result,
  created_by, created_by_name, created_at, resolved_at
) VALUES
  ('演示环境 SMTP 联调', '课程展示前需要确认 SMTP 账号、授权码和收件用户邮箱格式。', 'MEDIUM', 'OPEN', '检查 backend/.env 中 VMS_EMAIL_* 配置。', NULL, @admin_id, '系统管理员', DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
  ('数据库初始化复核', '演示数据库已通过 init.sql 建表，并通过 demo-data.sql 导入样例数据。', 'LOW', 'RESOLVED', '执行 SOURCE init.sql 与 SOURCE demo-data.sql。', '演示库结构与数据已复核。', @admin_id, '系统管理员', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO audit_logs (
  operator_id, operator_name, operator_role, action, target_type, target_id,
  detail, ip_address, created_at
) VALUES
  (@organizer_id, '组织方账号', 'ORGANIZER', 'ACTIVITY_CREATED', 'ACTIVITY', CAST(@campus_id AS CHAR), '演示数据：创建活动 校园迎新志愿服务', '127.0.0.1', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@organizer_id, '组织方账号', 'ORGANIZER', 'ACTIVITY_REGISTRATION_REVIEWED', 'ACTIVITY_REGISTRATION', 'demo', '演示数据：审核通过陈墨的迎新活动报名', '127.0.0.1', DATE_SUB(NOW(), INTERVAL 12 HOUR)),
  (@admin_id, '系统管理员', 'ADMIN', 'ANNOUNCEMENT_CREATED', 'ANNOUNCEMENT', 'demo', '演示数据：发布公告 服务记录公示规则更新', '127.0.0.1', DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (@organizer_id, '组织方账号', 'ORGANIZER', 'CONTENT_REVIEWED', 'CONTENT', 'demo', '演示数据：审核结果=APPROVED, 审核意见=内容详实，可用于成果展示。', '127.0.0.1', DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (@admin_id, '系统管理员', 'ADMIN', 'FEEDBACK_RESOLVED', 'FEEDBACK', 'demo', '演示数据：处理回复=已在活动执行页补充审核流程，后续会继续优化报名页说明。', '127.0.0.1', DATE_SUB(NOW(), INTERVAL 8 HOUR));

COMMIT;

SELECT 'demo data imported' AS result;
SELECT 'admin@example.com / admin123' AS admin_login;
SELECT 'organizer@example.com / organizer123' AS organizer_login;
SELECT 'liuqi@example.com / volunteer123' AS volunteer_login;
