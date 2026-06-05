import axios from 'axios'
import {
  getPortalToken,
  getAdminToken,
  clearSession,
  PORTAL_KEYS,
  ADMIN_KEYS
} from '@/auth/storage'

function createHttpClient(getToken, clearAuth, loginPath) {
  const client = axios.create({
    baseURL: '/api',
    timeout: 60000
  })

  client.interceptors.request.use(config => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  client.interceptors.response.use(
    response => response,
    error => {
      if (error.response?.status === 401) {
        clearAuth()
        const path = window.location.pathname
        if (!path.startsWith(loginPath)) {
          window.location.href = loginPath
        }
      }
      return Promise.reject(error)
    }
  )

  return client
}

/** C 端 / 客服聊天 API */
export const portalHttp = createHttpClient(
  getPortalToken,
  () => clearSession(PORTAL_KEYS),
  '/login'
)

/** 管理后台 API */
export const adminHttp = createHttpClient(
  getAdminToken,
  () => clearSession(ADMIN_KEYS),
  '/admin/login'
)

export const authApi = {
  login: (data) => portalHttp.post('/portal/auth/login', data),
  register: (data) => portalHttp.post('/portal/auth/register', data),
  me: () => portalHttp.get('/portal/auth/me'),
  adminLogin: (data) => adminHttp.post('/admin/auth/login', data),
  adminMe: () => adminHttp.get('/admin/auth/me')
}

export const adminApi = {
  // Documents
  uploadDocument: (formData) => adminHttp.post('/admin/document/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  splitDocument: (documentId, data) =>
    adminHttp.post(`/admin/document/split/${documentId}`, data),
  embeddingDocument: (documentId) =>
    adminHttp.post('/admin/document/embedding', null, { params: { documentId } }),
  listDocuments: (pageNum = 1, pageSize = 10) =>
    adminHttp.get('/admin/document/page', { params: { pageNum, pageSize } }),
  getDocument: (id) => adminHttp.get(`/admin/document/${id}`),
  deleteDocument: (id) => adminHttp.delete(`/admin/document/${id}`),
  disableDocument: (id) => adminHttp.put(`/admin/document/${id}/disable`),
  importFaq: (formData) => adminHttp.post('/admin/document/import/faq', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  downloadFaqTemplate: () => adminHttp.get('/admin/document/import/faq/template', { responseType: 'blob' }),

  // Devices
  listDevices: (pageNum = 1, pageSize = 10) =>
    adminHttp.get('/admin/device/page', { params: { pageNum, pageSize } }),
  getDevice: (deviceId) => adminHttp.get(`/admin/device/${deviceId}`),
  createDevice: (data) => adminHttp.post('/admin/device', data),
  updateDevice: (deviceId, data) =>
    adminHttp.put(`/admin/device/${deviceId}`, data),
  deleteDevice: (deviceId) => adminHttp.delete(`/admin/device/${deviceId}`),
  bindDevice: (deviceId, userId) =>
    adminHttp.put(`/admin/device/${deviceId}/bind`, null, { params: { userId } }),
  unbindDevice: (deviceId) => adminHttp.put(`/admin/device/${deviceId}/unbind`),
  seedAllUserDevices: () => adminHttp.post('/admin/device/seed-all-users'),
  listDeviceUserOptions: () => adminHttp.get('/admin/device/user-options'),

  // Knowledge：意图 + kb_branch（V2，Playbook 已废弃）
  listIntents: () => adminHttp.get('/admin/knowledge/intents'),
  getRuleTree: () => adminHttp.get('/admin/knowledge/tree'),
  listBranches: (intentCode) => adminHttp.get(`/admin/knowledge/intents/${intentCode}/branches`),
  createBranch: (data) => adminHttp.post('/admin/knowledge/branches', data),
  updateBranch: (id, data) => adminHttp.put(`/admin/knowledge/branches/${id}`, data),
  deleteBranch: (id) => adminHttp.delete(`/admin/knowledge/branches/${id}`),
  updateIntent: (id, data) => adminHttp.put(`/admin/knowledge/intents/${id}`, data),
  listDimensions: () => adminHttp.get('/admin/knowledge/dimensions'),
  listQuickEntries: () => adminHttp.get('/admin/kb-config/quick-entries'),
  createQuickEntry: (data) => adminHttp.post('/admin/kb-config/quick-entries', data),
  updateQuickEntry: (id, data) => adminHttp.put(`/admin/kb-config/quick-entries/${id}`, data),
  importExcel: (formData) => adminHttp.post('/admin/knowledge/import/excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  }),
  downloadBranchTemplate: () =>
    adminHttp.get('/admin/knowledge/export/template', { responseType: 'blob' }),
  exportBranchesExcel: () =>
    adminHttp.get('/admin/knowledge/export/branches', { responseType: 'blob' }),

  // 复合句规则分句关键词（运营可维护）
  createFaqAlias: (data) => adminHttp.post('/admin/kb-config/faq-aliases', data),

  // V2 知识运营：DMN / 寒暄 / 会话模板
  listKbRuleScenes: () => adminHttp.get('/admin/kb-config/rule-scenes'),
  updateKbRuleScene: (id, data) => adminHttp.put(`/admin/kb-config/rule-scenes/${id}`, data),
  listKbRuleVersions: (params) => adminHttp.get('/admin/kb-config/rule-versions', { params }),
  listChitchatCategories: () => adminHttp.get('/admin/kb-config/chitchat/categories'),
  listChitchatPatterns: () => adminHttp.get('/admin/kb-config/chitchat/patterns'),
  updateChitchatPattern: (id, data) => adminHttp.put(`/admin/kb-config/chitchat/patterns/${id}`, data),
  listChitchatReplies: () => adminHttp.get('/admin/kb-config/chitchat/replies'),
  updateChitchatReply: (id, data) => adminHttp.put(`/admin/kb-config/chitchat/replies/${id}`, data),
  reloadChitchat: () => adminHttp.post('/admin/kb-config/chitchat/reload'),
  listHumanRoutes: () => adminHttp.get('/admin/kb-config/human-routes'),
  updateHumanRoute: (id, data) => adminHttp.put(`/admin/kb-config/human-routes/${id}`, data),
  listFaqAliases: () => adminHttp.get('/admin/kb-config/faq-aliases'),
  updateFaqAlias: (id, data) => adminHttp.put(`/admin/kb-config/faq-aliases/${id}`, data),
  reloadFaqAliases: () => adminHttp.post('/admin/kb-config/faq-aliases/reload'),
  listSessionTemplates: () => adminHttp.get('/admin/kb-config/session-templates'),
  updateSessionTemplate: (id, data) => adminHttp.put(`/admin/kb-config/session-templates/${id}`, data),

  searchMessages: (params) => adminHttp.get('/admin/messages/search', { params }),
  listTurnTraces: (params) => adminHttp.get('/admin/messages/traces', { params }),
  getTurnTrace: (turnId) => adminHttp.get(`/admin/messages/traces/${turnId}`),
  listMessageReviews: (params) => adminHttp.get('/admin/messages/reviews', { params }),
  decideMessageReview: (id, data) => adminHttp.put(`/admin/messages/reviews/${id}`, data),
  listActionTemplates: () => adminHttp.get('/admin/kb-config/action-templates'),
  updateActionTemplate: (id, data) => adminHttp.put(`/admin/kb-config/action-templates/${id}`, data),
  reloadActionTemplates: () => adminHttp.post('/admin/kb-config/action-templates/reload'),
  listUtteranceConnectors: () => adminHttp.get('/admin/kb-config/utterance-connectors'),
  createUtteranceConnector: (data) => adminHttp.post('/admin/kb-config/utterance-connectors', data),
  updateUtteranceConnector: (id, data) => adminHttp.put(`/admin/kb-config/utterance-connectors/${id}`, data),
  reloadUtteranceConnectors: () => adminHttp.post('/admin/kb-config/utterance-connectors/reload'),

  // Admin Accounts (SUPER_ADMIN only)
  listAccounts: () => adminHttp.get('/admin/account'),
  createAccount: (data) => adminHttp.post('/admin/account', data),
  updateAccountRole: (adminId, role) =>
    adminHttp.put(`/admin/account/${adminId}/role`, { role }),
  updateAccountStatus: (adminId, status) =>
    adminHttp.put(`/admin/account/${adminId}/status`, { status }),
  resetAccountPassword: (adminId, password) =>
    adminHttp.put(`/admin/account/${adminId}/password`, { password }),
  deleteAccount: (adminId) => adminHttp.delete(`/admin/account/${adminId}`)
}

/** @deprecated 请使用 portalHttp 或 adminHttp */
export default portalHttp
