import api from './client'

export function entityEditPayload(values: any, editing: boolean) {
  const { name, type, base64Cert, conf, replaceConf } = values
  const payload: any = { ...(editing ? {} : { name }), ...(type === undefined ? {} : { type }),
    ...(base64Cert === undefined ? {} : { base64Cert }) }
  if (!editing || replaceConf) {
    if ((editing && !conf?.trim()) || conf?.includes('****') || conf?.includes('<redacted>') || conf?.includes('<sensitive>')) {
      throw new Error('Enter the complete configuration; masked values cannot be saved')
    }
    if (conf !== undefined) payload.conf = conf
    if (editing) payload.replaceConf = true
  }
  return payload
}

function crudApi(base: string) {
  return {
    list: () => api.get(base),
    get: (name: string) => api.get(`${base}/${encodeURIComponent(name)}`),
    create: (data: any) => api.post(base, data),
    update: (name: string, data: any) => api.put(`${base}/${encodeURIComponent(name)}`, data),
    remove: (name: string) => api.delete(`${base}/${encodeURIComponent(name)}`),
  }
}

function crudWithCaApi(base: string) {
  return {
    ...crudApi(base),
    getForCa: (ca: string) => api.get(`${base}/ca/${encodeURIComponent(ca)}`),
    addToCa: (name: string, ca: string, data?: any) =>
      api.post(`${base}/${encodeURIComponent(name)}/ca/${encodeURIComponent(ca)}`, data || {}),
    removeFromCa: (name: string, ca: string) =>
      api.delete(`${base}/${encodeURIComponent(name)}/ca/${encodeURIComponent(ca)}`),
  }
}

export const profileApi = {
  ...crudWithCaApi('/api/profile'),
  associationsForCa: (ca: string) => api.get(`/api/profile/ca/${encodeURIComponent(ca)}/associations`),
}
export const signerApi = crudApi('/api/signer')
export const requestorApi = {
  ...crudWithCaApi('/api/requestor'),
  updateForCa: (name: string, ca: string, data: any) =>
    api.put(`/api/requestor/${encodeURIComponent(name)}/ca/${encodeURIComponent(ca)}`, data),
}
export const publisherApi = crudWithCaApi('/api/publisher')
export const keypairGenApi = crudApi('/api/keypairgen')
