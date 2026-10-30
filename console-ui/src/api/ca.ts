import api from './client'

export const caApi = {
  list: () => api.get('/api/ca'),
  get: (name: string) => api.get(`/api/ca/${encodeURIComponent(name)}`),
  options: () => api.get('/api/ca-options'),
  preflight: (data: {certificate:string;certificateChain?:string[]}) => api.post('/api/ca/preflight',data),
  create: (data: any) => api.post('/api/ca', data, { timeout: 120000 }),
  createRoot: (data: any) => api.post('/api/ca/root', data, { timeout: 120000 }),
  config: (name: string) => api.get(`/api/ca/${encodeURIComponent(name)}/config`),
  update: (name: string, data: any) => api.put(`/api/ca/${encodeURIComponent(name)}/config`, data, { timeout: 120000 }),
  certificates: (name: string, format = 'pem') => api.get(`/api/ca/${encodeURIComponent(name)}/certificates`, { params: { format }, responseType: 'blob' }),
  deleteImpact: (name: string) => api.get(`/api/ca/${encodeURIComponent(name)}/delete-impact`),
  remove: (name: string, confirmName: string) => api.delete(`/api/ca/${encodeURIComponent(name)}`, { params: { confirmName } }),
  revoke: (name: string, reason: string, invalidityTime?: string) => api.post(`/api/ca/${encodeURIComponent(name)}/revoke`, { reason, invalidityTime }),
  unrevoke: (name: string) => api.post(`/api/ca/${encodeURIComponent(name)}/unrevoke`),
  restart: (name: string) => api.post(`/api/ca/${encodeURIComponent(name)}/restart`),
  listAliases: () => api.get('/api/ca/aliases'),
  addAlias: (alias: string, caName: string) => api.post('/api/ca/aliases', { alias, caName }),
  removeAlias: (alias: string) => api.delete(`/api/ca/aliases/${encodeURIComponent(alias)}`),
}

export interface CaEnrollment {
  operationId: string; ownerId: number; purpose: string; status: string; version: number
  data: {name?: string; mode: string; parentCa?: string; parentFingerprint?: string; profile?: string; csr: string;
    certificate?: string; certificateChain: string[]; diagnostic?: string; stage: string; subjectKeyFingerprint: string;
    issuedSubject?: string; issuedIssuer?: string; issuedNotBefore?: string; issuedNotAfter?: string; issuedPathLength?: number}
}
export const caEnrollmentApi = {
  list: () => api.get('/api/ca-enrollments'),
  options: () => api.get('/api/ca-enrollments/options'),
  prepare: (data: any) => api.post('/api/ca-enrollments', data, {timeout: 120000}),
  action: (op: CaEnrollment, action: string, data: any = {}) => api.post(`/api/ca-enrollments/${encodeURIComponent(op.operationId)}/${action}`, {version: op.version, ...data}, {timeout: 120000}),
}
