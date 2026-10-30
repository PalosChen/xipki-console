// Run only against a disposable CA/database: creates retained test objects and certificates.
import assert from 'node:assert/strict'
import { X509Certificate } from 'node:crypto'

assert.equal(process.env.LITE_SMOKE_ALLOW_MUTATION, 'yes', 'Explicitly enable disposable-environment writes')
const base = process.env.LITE_SMOKE_URL || 'http://127.0.0.1:28126'
const ca = process.env.LITE_SMOKE_CA || 'lite-demo-ca'
const username = process.env.LITE_SMOKE_USER || 'lite-admin'
let password = process.env.LITE_SMOKE_PASSWORD
assert.ok(password, 'LITE_SMOKE_PASSWORD is required')
let cookie = '', csrf = '', checks = 0
async function request(method, path, body) {
  const headers = { Cookie: cookie }
  if (csrf) headers['X-CSRF-TOKEN'] = csrf
  if (body && !(body instanceof URLSearchParams)) { headers['Content-Type'] = 'application/json'; body = JSON.stringify(body) }
  const response = await fetch(base + path, { method, headers, body, signal: AbortSignal.timeout(15000) })
  for (const value of response.headers.getSetCookie()) if (value.startsWith('LITE_SESSION=')) cookie = value.split(';')[0]
  const text = await response.text()
  assert.equal(response.status, 200, `${method} ${path}: ${response.status} ${text.slice(0, 300)}`)
  checks++
  const result = JSON.parse(text)
  assert.notEqual(result.success, false, `${method} ${path}: operation rejected`)
  return result.data ?? result
}
async function login() {
  csrf = (await request('GET', '/api/auth/csrf')).token
  const user = await request('POST', '/api/auth/login', { username, password })
  csrf = (await request('GET', '/api/auth/csrf')).token
  return user
}
await request('GET', '/health/ready')
if ((await login()).passwordChangeRequired) {
  const next = process.env.LITE_SMOKE_NEW_PASSWORD
  assert.ok(next, 'First login requires LITE_SMOKE_NEW_PASSWORD')
  await request('POST', '/api/auth/password', { currentPassword: password, newPassword: next })
  password = next
  await login()
}
const authorities = await request('GET', '/api/auth/me')
assert.ok(authorities.roles.includes('SUPER_ADMIN'))
await request('PUT', `/api/ca/${ca}/config`, { crlControl: 'fullcrl.intervals=1,interval.hours=24' })
await request('POST', `/api/ca/${ca}/restart`)
for (const path of ['/api/dashboard','/api/users','/api/roles','/api/audit','/api/ca','/api/ca-options',
  '/api/ca-enrollments','/api/ca-enrollments/options','/api/profile','/api/publisher','/api/requestor',
  '/api/signer','/api/keypairgen','/api/dbschema','/api/system/status','/api/system/supported-types',
  `/api/ca/${ca}`,`/api/ca/${ca}/config`,`/api/cert?ca=${ca}`]) await request('GET', path)
const prefix = `lite-${Date.now()}`
const root = await request('POST', '/api/ca/root', { name: prefix, subject: `CN=${prefix} Root CA,O=XiPKI`, profile: 'rootca',
  config: { status: 'inactive', maxValidity: '365d', expirationPeriod: 0, permissions: ['all'] },
  key: { mode: 'generate', algorithm: 'EC_P256' } })
assert.equal(root.selfSigned, true)
assert.equal((await request('GET', `/api/ca/${prefix}`)).status, 'inactive')
await request('POST', '/api/dbschema', { name: prefix, value: 'created by lite runtime smoke' })
await request('PUT', `/api/dbschema/${prefix}`, { value: 'updated by lite runtime smoke' })
assert.equal((await request('GET', '/api/dbschema'))[prefix], 'updated by lite runtime smoke')
await request('POST', '/api/keypairgen', { name: prefix, type: 'software', conf: '' })
assert.equal((await request('GET', `/api/keypairgen/${prefix}`)).name, prefix)
const profile = await request('GET', '/api/profile/tls')
// The disposable CA has no public AIA endpoint. Keep the source template unchanged.
const configuration = JSON.parse(profile.conf)
configuration.extensions.find(e => e.type === 'AuthorityInfoAccess').required = false
await request('POST', '/api/profile', { name: prefix, type: profile.type, conf: JSON.stringify(configuration) })
await request('POST', `/api/profile/${prefix}/ca/${ca}`, { aliases: [] })
assert.ok((await request('GET', `/api/profile/ca/${ca}`)).includes(prefix))
const caInfo = await request('GET', `/api/ca/${ca}`)
await request('POST', '/api/requestor', { name: prefix, type: 'cert', conf: new X509Certificate(caInfo.certPem).raw.toString('base64') })
assert.equal((await request('GET', `/api/requestor/${prefix}`)).name, prefix)
const pair = await request('POST', '/api/cert/enroll-key', new URLSearchParams({ ca, profile: prefix, subject: `CN=${prefix}.example.test` }))
const certificate = new X509Certificate(Buffer.from(pair.cert, 'base64'))
const serial = certificate.serialNumber.toLowerCase()
await request('GET', `/api/cert/${ca}/${serial}`)
await request('POST', `/api/cert/${ca}/${serial}/revoke`, { reason: 'certificateHold' })
await request('POST', `/api/cert/${ca}/${serial}/unsuspend`)
await request('POST', `/api/cert/${ca}/${serial}/revoke`, { reason: 'keyCompromise' })
await request('POST', `/api/crl/${ca}/generate`)
await request('POST', '/api/auth/logout')
console.log(JSON.stringify({ checks, ca, retainedEntity: prefix, serial, result: 'PASS' }))
