import { createRouter, createWebHistory } from "vue-router";
import { authState, isLoggedIn } from "../stores/auth";

const routes = [
  {
    path: "/login",
    name: "login",
    component: () => import("../views/LoginView.vue"),
    meta: { public: true }
  },
  {
    path: "/register",
    name: "register",
    component: () => import("../views/RegisterView.vue"),
    meta: { public: true }
  },
  {
    path: "/",
    redirect: "/activities"
  },
  {
    path: "/activities",
    name: "activities",
    component: () => import("../views/ActivitiesView.vue")
  },
  {
    path: "/activity-ops",
    name: "activity-ops",
    component: () => import("../views/ActivityOpsView.vue"),
    meta: { roles: ["ADMIN", "ORGANIZER"] }
  },
  {
    path: "/my-registrations",
    name: "my-registrations",
    component: () => import("../views/MyRegistrationsView.vue")
  },
  {
    path: "/ranking",
    name: "ranking",
    component: () => import("../views/RankingView.vue")
  },
  {
    path: "/service-records",
    name: "service-records",
    component: () => import("../views/ServiceRecordsView.vue")
  },
  {
    path: "/contents",
    name: "contents",
    component: () => import("../views/ContentsView.vue")
  },
  {
    path: "/donations",
    name: "donations",
    component: () => import("../views/DonationsView.vue")
  },
  {
    path: "/feedbacks",
    name: "feedbacks",
    component: () => import("../views/FeedbacksView.vue")
  },
  {
    path: "/announcements",
    name: "announcements",
    component: () => import("../views/AnnouncementsView.vue")
  },
  {
    path: "/notifications",
    name: "notifications",
    component: () => import("../views/NotificationsView.vue")
  },
  {
    path: "/dashboard",
    name: "dashboard",
    component: () => import("../views/DashboardView.vue"),
    meta: { roles: ["ADMIN", "ORGANIZER"] }
  },
  {
    path: "/users-admin",
    name: "users-admin",
    component: () => import("../views/UserManagementView.vue"),
    meta: { roles: ["ADMIN"] }
  },
  {
    path: "/audit-logs",
    name: "audit-logs",
    component: () => import("../views/AuditLogsView.vue"),
    meta: { roles: ["ADMIN"] }
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, from, next) => {
  if (to.meta.public) {
    next();
    return;
  }
  if (!isLoggedIn()) {
    next("/login");
    return;
  }
  const roles = to.meta.roles;
  if (Array.isArray(roles) && roles.length > 0) {
    const currentRole = authState.user?.role;
    if (!currentRole || !roles.includes(currentRole)) {
      next("/activities");
      return;
    }
  }
  next();
});

export default router;
