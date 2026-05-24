export const activityStatusLabels = {
  DRAFT: "草稿",
  PUBLISHED: "报名中",
  ONGOING: "进行中",
  OFFLINE: "已下架",
  FINISHED: "已结束",
  CANCELLED: "已取消"
};

export const registrationStatusLabels = {
  PENDING: "待审核",
  APPROVED: "已通过",
  REJECTED: "已驳回",
  CHECKED_IN: "已签到",
  CHECKED_OUT: "已签退",
  COMPLETED: "已完成",
  CANCELLED: "已取消"
};

export const roleLabels = {
  VOLUNTEER: "志愿者",
  ORGANIZER: "组织方",
  ADMIN: "管理员"
};

export const accountStatusLabels = {
  ENABLED: "正常",
  DISABLED: "已停用",
  LOCKED: "已锁定"
};

export const verificationStatusLabels = {
  UNVERIFIED: "未认证",
  PENDING: "待审核",
  VERIFIED: "已认证",
  REJECTED: "认证未通过"
};

export const resourceStatusLabels = {
  AVAILABLE: "可用",
  RESERVED: "已预留",
  USED: "已使用",
  CLOSED: "已关闭"
};

export const needStatusLabels = {
  OPEN: "待匹配",
  MATCHED: "已匹配",
  IN_PROGRESS: "推进中",
  COMPLETED: "已完成",
  CANCELLED: "已取消"
};

export const matchStatusLabels = {
  MATCHED: "已匹配",
  ALLOCATED: "已分配",
  COMPLETED: "已完成",
  CANCELLED: "已取消"
};

export const donationOrderStatusLabels = {
  PENDING: "待支付",
  PAID: "支付成功",
  FAILED: "支付失败",
  CANCELLED: "已取消",
  CLOSED: "已关闭"
};

export const honorTypeLabels = {
  EXCELLENT_VOLUNTEER: "优秀志愿者",
  SERVICE_STAR: "服务之星",
  SPECIAL_CONTRIBUTION: "特殊贡献"
};

export const externalNotificationChannelLabels = {
  EMAIL: "邮件",
  SMS: "短信"
};

export const externalNotificationStatusLabels = {
  PENDING: "待发送",
  SENT: "已发送",
  FAILED: "发送失败"
};

export const attendanceCorrectionActionLabels = {
  SET_APPROVED: "恢复为已通过",
  SET_CHECKED_IN: "更正为已签到",
  SET_CHECKED_OUT: "更正为已签退",
  CLEAR_CHECK_IN: "清除签到",
  CLEAR_CHECK_OUT: "清除签退",
  SET_CANCELLED: "更正为已取消"
};

export function formatActivityStatus(status) {
  return activityStatusLabels[status] || status || "-";
}

export function formatRegistrationStatus(status) {
  return registrationStatusLabels[status] || status || "-";
}

export function formatRole(role) {
  return roleLabels[role] || role || "-";
}

export function formatAccountStatus(status) {
  return accountStatusLabels[status] || status || "-";
}

export function formatVerificationStatus(status) {
  return verificationStatusLabels[status] || status || "-";
}

export function formatResourceStatus(status) {
  return resourceStatusLabels[status] || status || "-";
}

export function formatNeedStatus(status) {
  return needStatusLabels[status] || status || "-";
}

export function formatMatchStatus(status) {
  return matchStatusLabels[status] || status || "-";
}

export function formatDonationOrderStatus(status) {
  return donationOrderStatusLabels[status] || status || "-";
}

export function formatHonorType(type) {
  return honorTypeLabels[type] || type || "-";
}

export function formatExternalNotificationChannel(channel) {
  return externalNotificationChannelLabels[channel] || channel || "-";
}

export function formatExternalNotificationStatus(status) {
  return externalNotificationStatusLabels[status] || status || "-";
}

export function formatAttendanceCorrectionAction(action) {
  return attendanceCorrectionActionLabels[action] || action || "-";
}
