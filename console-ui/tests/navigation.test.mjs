import assert from 'node:assert/strict'
import test from 'node:test'
import {landingPath} from '../src/access-policy.ts'
import {navigationPermission, selectedNavigationKey, visibleNavigationGroups} from '../src/navigation.ts'

const keys = user => visibleNavigationGroups(user).flatMap(group => group.items.map(item => item.key))

test('navigation groups hide unavailable sections and honor password change', () => {
  assert.deepEqual(keys({roles:['CUSTOM'],permissions:['CA_READ','PROFILE_READ','AUDIT_READ']}),
    ['/dashboard','/ca','/profile','/audit'])
  assert.deepEqual(visibleNavigationGroups({roles:['CUSTOM'],permissions:['AUDIT_READ']}).map(group => group.label),
    ['Administration'])
  assert.deepEqual(visibleNavigationGroups({roles:['CUSTOM'],permissions:['CA_READ'],passwordChangeRequired:true}),[])
})

test('CA enrollment and certificate issuance have usable navigation and landing routes', () => {
  for (const permission of ['CA_ISSUE_SUBCA']) {
    const user={roles:['CUSTOM'],permissions:[permission]}
    assert.ok(keys(user).includes('/ca'))
    assert.equal(landingPath(user),'/ca')
    assert.ok(navigationPermission('/ca').split('|').includes(permission))
  }
  for (const permission of ['CERT_ISSUE','CERT_KEYGEN','CERT_CROSS']) {
    const user={roles:['CUSTOM'],permissions:[permission]}
    assert.deepEqual(keys(user),['/cert/enroll'])
    assert.equal(landingPath(user),'/cert/enroll')
    assert.ok(navigationPermission('/cert/enroll').split('|').includes(permission))
  }
})

test('nested routes select their matching navigation item', () => {
  assert.equal(selectedNavigationKey('/cert/enroll'),'/cert/enroll')
  assert.equal(selectedNavigationKey('/cert/example'),'/cert')
  assert.equal(selectedNavigationKey('/ca/demo-ca'),'/ca')
  assert.equal(selectedNavigationKey('/ca/operations/enrollments'),'/ca')
  assert.equal(selectedNavigationKey('/access/roles'),'/access/roles')
  assert.equal(selectedNavigationKey('/ca-options'),undefined)
})
