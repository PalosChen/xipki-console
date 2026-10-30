// Uses the retained local demo; reads CA/certificate data and verifies stopped-CA rejection.
import assert from 'node:assert/strict'
import { canUser } from '../../console-ui/src/access-policy.ts'
import { operationalCaNames } from '../../console-ui/src/ca-availability.ts'

const base=process.env.LITE_SMOKE_URL || 'http://127.0.0.1:28126'
assert.ok(process.env.LITE_SMOKE_PASSWORD,'Provide the test account password')
let cookie='',csrf=''
async function call(method,path,body,status=200) {
  const headers={Cookie:cookie,'X-CSRF-TOKEN':csrf}
  if(body){headers['Content-Type']='application/json';body=JSON.stringify(body)}
  const response=await fetch(base+path,{method,headers,body,signal:AbortSignal.timeout(15000)})
  for(const value of response.headers.getSetCookie())if(value.startsWith('LITE_SESSION='))cookie=value.split(';')[0]
  const result=await response.json()
  assert.equal(response.status,status,`${method} ${path}: ${response.status} ${JSON.stringify(result)}`)
  return result.data ?? result
}
csrf=(await call('GET','/api/auth/csrf')).token
await call('POST','/api/auth/login',{username:process.env.LITE_SMOKE_USER || 'lite-admin',password:process.env.LITE_SMOKE_PASSWORD})
csrf=(await call('GET','/api/auth/csrf')).token
const user=await call('GET','/api/auth/me'),cas=await call('GET','/api/ca')
const inactive=cas.find(ca=>ca.runtimeState!=='started')
assert.ok(inactive,'Retained stopped CA fixture is required')
for(const permission of ['CERT_READ','CRL_READ','CERT_REPUBLISH']) {
  const names=operationalCaNames(cas,permission,(p,ca)=>canUser(user,p,ca))
  assert.ok(!names.includes(inactive.name))
  if(permission==='CERT_READ') {
    assert.ok(names.length,'A running CA fixture is required')
    assert.ok(Array.isArray(await call('GET',`/api/cert?ca=${encodeURIComponent(names[0])}`)))
  }
}
for(const path of [`/api/cert?ca=${encodeURIComponent(inactive.name)}`,`/api/cert/${encodeURIComponent(inactive.name)}/1`,
  `/api/crl/${encodeURIComponent(inactive.name)}/current`,`/api/crl/${encodeURIComponent(inactive.name)}/1`]) {
  assert.match((await call('GET',path,undefined,409)).error,/CA is not running/)
}
assert.match((await call('POST',`/api/crl/${encodeURIComponent(inactive.name)}/generate`,undefined,409)).error,/CA is not running/)
assert.match((await call('POST','/api/system/republish',{caName:inactive.name,numThreads:1},409)).error,/CA is not running/)
await call('POST','/api/auth/logout')
console.log('PASS: default certificate query uses a running authorized CA; stopped CAs return actionable 409 for certificate, CRL and republish APIs')
