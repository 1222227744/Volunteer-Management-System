import { apiRequest, fileRequest, uploadRequest } from "./http";

export const authApi = {
  login: (payload) =>
    apiRequest("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  register: (payload) =>
    apiRequest("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  me: () => apiRequest("/api/auth/me"),
  logout: () =>
    apiRequest("/api/auth/logout", {
      method: "POST"
    })
};

export const activityApi = {
  list: (params = {}) => {
    const query = new URLSearchParams();
    if (params.status) {
      query.set("status", params.status);
    }
    if (params.keyword) {
      query.set("keyword", params.keyword);
    }
    if (params.location) {
      query.set("location", params.location);
    }
    if (params.startFrom) {
      query.set("startFrom", params.startFrom);
    }
    if (params.startTo) {
      query.set("startTo", params.startTo);
    }
    const suffix = query.toString();
    return apiRequest(`/api/activities${suffix ? `?${suffix}` : ""}`);
  },
  create: (payload) =>
    apiRequest("/api/activities", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  update: (activityId, payload) =>
    apiRequest(`/api/activities/${activityId}`, {
      method: "PUT",
      body: JSON.stringify(payload)
    }),
  register: (activityId) =>
    apiRequest(`/api/activities/${activityId}/register`, {
      method: "POST"
    }),
  cancelRegistration: (activityId, reason) =>
    apiRequest(`/api/activities/${activityId}/cancel-registration`, {
      method: "POST",
      body: JSON.stringify({ reason })
    }),
  myRegistrations: () => apiRequest("/api/activities/my-registrations"),
  registrations: (activityId) => apiRequest(`/api/activities/${activityId}/registrations`),
  reviewRegistration: (activityId, userId, status, comment = "") =>
    apiRequest(`/api/activities/${activityId}/registrations/${userId}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status, comment })
    }),
  checkIn: (activityId, userId) =>
    apiRequest(`/api/activities/${activityId}/check-in/${userId}`, {
      method: "POST"
    }),
  checkOut: (activityId, userId) =>
    apiRequest(`/api/activities/${activityId}/check-out/${userId}`, {
      method: "POST"
    }),
  updateStatus: (activityId, status) =>
    apiRequest(`/api/activities/${activityId}/status`, {
      method: "PATCH",
      body: JSON.stringify({ status })
    })
};

export const userApi = {
  ranking: () => apiRequest("/api/users/ranking"),
  list: () => apiRequest("/api/users"),
  updateMyProfile: (payload) =>
    apiRequest("/api/users/me/profile", {
      method: "PATCH",
      body: JSON.stringify(payload)
    }),
  updateRole: (userId, role) =>
    apiRequest(`/api/users/${userId}/role`, {
      method: "PATCH",
      body: JSON.stringify({ role })
    }),
  updateAccountStatus: (userId, accountStatus) =>
    apiRequest(`/api/users/${userId}/account-status`, {
      method: "PATCH",
      body: JSON.stringify({ accountStatus })
    }),
  updateVerification: (userId, verificationStatus, comment = "") =>
    apiRequest(`/api/users/${userId}/verification`, {
      method: "PATCH",
      body: JSON.stringify({ verificationStatus, comment })
    })
};

export const fileApi = {
  upload: ({ file, category, businessType = null, businessId = null }) => {
    const formData = new FormData();
    formData.append("file", file);
    formData.append("category", category);
    if (businessType) {
      formData.append("businessType", businessType);
    }
    if (businessId) {
      formData.append("businessId", String(businessId));
    }
    return uploadRequest("/api/files/upload", formData);
  },
  download: async (fileId, filename = `file-${fileId}`) => {
    const blob = await fileRequest(`/api/files/${fileId}`);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }
};

export const announcementApi = {
  list: () => apiRequest("/api/announcements"),
  create: (payload) =>
    apiRequest("/api/announcements", {
      method: "POST",
      body: JSON.stringify(payload)
    })
};

export const notificationApi = {
  my: () => apiRequest("/api/notifications/my"),
  markRead: (id) =>
    apiRequest(`/api/notifications/${id}/read`, {
      method: "PATCH"
    }),
  websocketUrl: (token) => {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}/ws/notifications?token=${encodeURIComponent(token)}`;
  }
};

export const dashboardApi = {
  stats: () => apiRequest("/api/dashboard/stats")
};

export const serviceRecordApi = {
  my: () => apiRequest("/api/service-records/me"),
  byUser: (userId, activityId = null) => {
    const query = new URLSearchParams();
    if (activityId) {
      query.set("activityId", String(activityId));
    }
    const suffix = query.toString();
    return apiRequest(`/api/service-records/user/${userId}${suffix ? `?${suffix}` : ""}`);
  },
  create: (payload) =>
    apiRequest("/api/service-records", {
      method: "POST",
      body: JSON.stringify(payload)
    })
};

export const contentApi = {
  submit: (payload) =>
    apiRequest("/api/contents", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  my: () => apiRequest("/api/contents/my"),
  approved: () => apiRequest("/api/contents/approved"),
  pending: () => apiRequest("/api/contents/pending"),
  review: (contentId, payload) =>
    apiRequest(`/api/contents/${contentId}/review`, {
      method: "PATCH",
      body: JSON.stringify(payload)
    })
};

export const donationApi = {
  donate: (payload) =>
    apiRequest("/api/donations", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  createOrder: (payload) =>
    apiRequest("/api/donations/orders", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  myOrders: () => apiRequest("/api/donations/orders/my"),
  simulatePayment: (orderId, payload) =>
    apiRequest(`/api/donations/orders/${orderId}/simulate-payment`, {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  my: () => apiRequest("/api/donations/my"),
  all: () => apiRequest("/api/donations")
};

export const feedbackApi = {
  submit: (payload) =>
    apiRequest("/api/feedbacks", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  my: () => apiRequest("/api/feedbacks/my"),
  all: () => apiRequest("/api/feedbacks"),
  resolve: (id, payload) =>
    apiRequest(`/api/feedbacks/${id}/resolve`, {
      method: "PATCH",
      body: JSON.stringify(payload)
    })
};

export const resourceApi = {
  board: () => apiRequest("/api/resources"),
  createResource: (payload) =>
    apiRequest("/api/resources", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  createNeed: (payload) =>
    apiRequest("/api/resources/needs", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  createMatch: (payload) =>
    apiRequest("/api/resources/matches", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  updateMatchStatus: (matchId, payload) =>
    apiRequest(`/api/resources/matches/${matchId}/status`, {
      method: "PATCH",
      body: JSON.stringify(payload)
    })
};

export const activityFeedbackApi = {
  submit: (payload) =>
    apiRequest("/api/activity-feedbacks", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  my: () => apiRequest("/api/activity-feedbacks/my"),
  byActivity: (activityId) => apiRequest(`/api/activity-feedbacks/activity/${activityId}`)
};

export const honorApi = {
  candidates: () => apiRequest("/api/honors/candidates"),
  award: (payload) =>
    apiRequest("/api/honors", {
      method: "POST",
      body: JSON.stringify(payload)
    }),
  my: () => apiRequest("/api/honors/my"),
  showcase: () => apiRequest("/api/honors/showcase"),
  all: () => apiRequest("/api/honors")
};

export const auditApi = {
  paged: (params = {}) => {
    const query = new URLSearchParams();
    if (params.action) {
      query.set("action", params.action);
    }
    if (params.keyword) {
      query.set("keyword", params.keyword);
    }
    if (params.operatorName) {
      query.set("operatorName", params.operatorName);
    }
    if (params.targetType) {
      query.set("targetType", params.targetType);
    }
    if (params.from) {
      query.set("from", params.from);
    }
    if (params.to) {
      query.set("to", params.to);
    }
    if (params.page) {
      query.set("page", String(params.page));
    }
    if (params.size) {
      query.set("size", String(params.size));
    }
    const suffix = query.toString();
    return apiRequest(`/api/audit-logs/paged${suffix ? `?${suffix}` : ""}`);
  },
  list: (params = {}) => {
    const query = new URLSearchParams();
    if (params.action) {
      query.set("action", params.action);
    }
    if (params.keyword) {
      query.set("keyword", params.keyword);
    }
    if (params.operatorName) {
      query.set("operatorName", params.operatorName);
    }
    if (params.targetType) {
      query.set("targetType", params.targetType);
    }
    if (params.from) {
      query.set("from", params.from);
    }
    if (params.to) {
      query.set("to", params.to);
    }
    if (params.limit) {
      query.set("limit", String(params.limit));
    }
    const suffix = query.toString();
    return apiRequest(`/api/audit-logs${suffix ? `?${suffix}` : ""}`);
  },
  exportCsv: async (params = {}) => {
    const query = new URLSearchParams();
    if (params.action) {
      query.set("action", params.action);
    }
    if (params.keyword) {
      query.set("keyword", params.keyword);
    }
    if (params.operatorName) {
      query.set("operatorName", params.operatorName);
    }
    if (params.targetType) {
      query.set("targetType", params.targetType);
    }
    if (params.from) {
      query.set("from", params.from);
    }
    if (params.to) {
      query.set("to", params.to);
    }
    if (params.limit) {
      query.set("limit", String(params.limit));
    }
    const suffix = query.toString();
    const blob = await fileRequest(`/api/audit-logs/export${suffix ? `?${suffix}` : ""}`);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "audit-logs.csv";
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }
};
