import axios from "axios";
import { authState, clearAuth } from "../stores/auth";

let unauthorizedHandler = null;

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

const http = axios.create({
  baseURL: "",
  timeout: 15000
});

http.interceptors.request.use((config) => {
  if (authState.token) {
    config.headers.Authorization = `Bearer ${authState.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      clearAuth();
      if (unauthorizedHandler) {
        unauthorizedHandler();
      }
    }
    return Promise.reject(error);
  }
);

function unwrapPayload(response) {
  const payload = response.data;
  if (payload?.code !== 200) {
    throw new Error(payload?.message || "请求失败");
  }
  return payload.data;
}

function parseError(error) {
  return error.response?.data?.message || error.message || "请求失败";
}

export async function apiRequest(path, options = {}) {
  try {
    const response = await http.request({
      url: path,
      method: options.method || "GET",
      data: options.body ? JSON.parse(options.body) : options.data,
      headers: options.headers
    });
    return unwrapPayload(response);
  } catch (error) {
    throw new Error(parseError(error));
  }
}

export async function fileRequest(path, options = {}) {
  try {
    const response = await http.request({
      url: path,
      method: options.method || "GET",
      data: options.body,
      headers: options.headers,
      responseType: "blob"
    });
    return response.data;
  } catch (error) {
    throw new Error(parseError(error));
  }
}

export async function uploadRequest(path, formData, options = {}) {
  try {
    const response = await http.request({
      url: path,
      method: options.method || "POST",
      data: formData,
      headers: {
        ...(options.headers || {})
      }
    });
    return unwrapPayload(response);
  } catch (error) {
    throw new Error(parseError(error));
  }
}
