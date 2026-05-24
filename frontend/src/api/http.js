import { authState, clearAuth } from "../stores/auth";

let unauthorizedHandler = null;

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

async function parseError(response) {
  try {
    const body = await response.json();
    return body.message || "请求失败";
  } catch {
    return `请求失败(${response.status})`;
  }
}

export async function apiRequest(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {})
  };
  if (authState.token) {
    headers.Authorization = `Bearer ${authState.token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers
  });

  if (!response.ok) {
    const message = await parseError(response);
    if (response.status === 401) {
      clearAuth();
      if (unauthorizedHandler) {
        unauthorizedHandler();
      }
    }
    throw new Error(message);
  }

  const payload = await response.json();
  if (payload.code !== 200) {
    throw new Error(payload.message || "请求失败");
  }
  return payload.data;
}

export async function fileRequest(path, options = {}) {
  const headers = {
    ...(options.headers || {})
  };
  if (authState.token) {
    headers.Authorization = `Bearer ${authState.token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers
  });

  if (!response.ok) {
    const message = await parseError(response);
    if (response.status === 401) {
      clearAuth();
      if (unauthorizedHandler) {
        unauthorizedHandler();
      }
    }
    throw new Error(message);
  }
  return response.blob();
}

export async function uploadRequest(path, formData, options = {}) {
  const headers = {
    ...(options.headers || {})
  };
  if (authState.token) {
    headers.Authorization = `Bearer ${authState.token}`;
  }

  const response = await fetch(path, {
    ...options,
    method: options.method || "POST",
    body: formData,
    headers
  });

  if (!response.ok) {
    const message = await parseError(response);
    if (response.status === 401) {
      clearAuth();
      if (unauthorizedHandler) {
        unauthorizedHandler();
      }
    }
    throw new Error(message);
  }

  const payload = await response.json();
  if (payload.code !== 200) {
    throw new Error(payload.message || "请求失败");
  }
  return payload.data;
}
