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

export function formatActivityStatus(status) {
  return activityStatusLabels[status] || status || "-";
}

export function formatRegistrationStatus(status) {
  return registrationStatusLabels[status] || status || "-";
}

export function formatRole(role) {
  return roleLabels[role] || role || "-";
}
