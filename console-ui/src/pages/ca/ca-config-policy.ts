const ordinary = new Set(['maxValidity','validityMode','crlControl','ctlogControl','revokeSuspendedControl',
  'saveCert','keepExpiredCertDays','expirationPeriod','extraControl','caUris','numCrls','serialNoLen'])

export function configurationPermission(field: string): string | undefined {
  if (ordinary.has(field)) return 'CA_CONFIG_UPDATE'
  if (field === 'status') return 'CA_STATUS_UPDATE'
  if (['key','signerType','signerConf','replaceSigner'].includes(field)) return 'CA_KEY_REPLACE'
  if (['encodedCert','encodedCertchain','replaceCertificates'].includes(field)) return 'CA_CERT_REPLACE'
  if (['crlSignerName','keypairGenNames','saveKeypair'].includes(field)) return 'CA_CRYPTO_CONFIG_UPDATE'
  if (field === 'permissions') return 'CA_ENGINE_PERMISSIONS_UPDATE'
  return undefined
}
const normalized = (value: any): any => Array.isArray(value) ? value.map(normalized)
  : value && typeof value === 'object' ? Object.fromEntries(Object.keys(value).sort().map(key => [key,normalized(value[key])])) : value

/** Keep unchanged read-only values out of PUT; reject accidental edits instead of silently losing them. */
export function configurationChanges(previous: Record<string, any>, next: Record<string, any>, can: (permission: string) => boolean): Record<string, any> {
  const changes: Record<string, any> = {}
  for (const [field,value] of Object.entries(next)) {
    if (value === undefined || value === null || ['replaceSigner','replaceCertificates'].includes(field) && value === false) continue
    const permission = configurationPermission(field)
    if (!permission) throw new Error('Unsupported configuration field')
    if (JSON.stringify(normalized(previous[field])) === JSON.stringify(normalized(value))) continue
    if (!can(permission)) throw new Error('Permission denied')
    changes[field] = value
  }
  if (!Object.keys(changes).length) throw new Error('No configuration changes submitted')
  return changes
}
