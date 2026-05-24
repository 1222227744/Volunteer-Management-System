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
