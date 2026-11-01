import React, { useEffect, useRef, useState } from 'react'
import { Alert, Button, DatePicker, Input, InputNumber, Pagination, Segmented, Select, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { Dayjs } from 'dayjs'
import { DownloadOutlined, EyeOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useI18n } from '../../i18n'
import { certApi, type CertificateQuery } from '../../api/cert'
import { caApi } from '../../api/ca'
import { operationalCaNames } from '../../ca-availability'
import { useCan } from '../../auth'
import { certificateValidity, matchesCertificateValidity, type CertificateValidity } from './certificate-validity'
import { certificateOrder, certificatePage, certificateQueryTime, collectCertificatePages, localCertificateTime,
  type CertificateSortDirection, type CertificateSortField } from './certificate-list'
import CertDetailDrawer from './CertDetailDrawer'
import './CertList.css'

type CertificateRow = { ca: string; serialNumber: string; subject: string; notBefore: string; notAfter: string }
type ValidityFilter = CertificateValidity | 'all'
const sortFields: { value: CertificateSortField; label: string }[] = [
  { value: 'notBefore', label: 'Not Before' },
  { value: 'notAfter', label: 'Not After' },
  { value: 'subject', label: 'Subject' },
]
const validityOptions: { value: ValidityFilter; label: string }[] = [
  { value: 'all', label: 'All validity' },
  { value: 'valid', label: 'Within validity period' },
  { value: 'expiring-soon', label: 'Expiring soon' },
  { value: 'expired', label: 'Expired' },
  { value: 'not-yet-valid', label: 'Not yet valid' },
  { value: 'unknown', label: 'Unknown validity' },
]
const validityColors: Record<CertificateValidity, string> = {
  valid: 'green', 'expiring-soon': 'gold', expired: 'red', 'not-yet-valid': 'blue', unknown: 'default',
}

const CertList: React.FC = () => {
  const { t } = useI18n()
  const can = useCan()
  const navigate = useNavigate()
  const [certs, setCerts] = useState<CertificateRow[]>([])
  const [caNames, setCaNames] = useState<string[]>([])
  const [casLoaded, setCasLoaded] = useState(false)
  const [caLoadError, setCaLoadError] = useState(false)
  const [caLoadAttempt, setCaLoadAttempt] = useState(0)
  const [selectedCa, setSelectedCa] = useState('')
  const [subject, setSubject] = useState('')
  const [validFrom, setValidFrom] = useState<Dayjs | null>(null)
  const [validTo, setValidTo] = useState<Dayjs | null>(null)
  const [sortField, setSortField] = useState<CertificateSortField>('notBefore')
  const [sortDirection, setSortDirection] = useState<CertificateSortDirection>('desc')
  const [limit, setLimit] = useState(100)
  const [validityFilter, setValidityFilter] = useState<ValidityFilter>('all')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [detailTarget, setDetailTarget] = useState<{ ca: string; serial: string }>()
  const [loading, setLoading] = useState(false)
  const [now, setNow] = useState(Date.now())
  const requestGeneration = useRef(0)
  const appliedQuery = useRef<CertificateQuery>()

  useEffect(() => {
    let active = true
    setCasLoaded(false)
    setCaLoadError(false)
    void caApi.list().then(res => {
      if (!active) return
      const names = operationalCaNames(res.data.data || [], 'CERT_READ', can)
      setCaNames(names)
      setSelectedCa(current => names.includes(current) ? current : names[0] || '')
    }).catch(() => {
      if (!active) return
      setCaNames([])
      setSelectedCa('')
      setCaLoadError(true)
    })
      .finally(() => { if (active) setCasLoaded(true) })
    return () => { active = false; requestGeneration.current++ }
  }, [caLoadAttempt])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000)
    return () => window.clearInterval(timer)
  }, [])

  const search = async (refresh = false) => {
    const ca = selectedCa
    if (!ca || (loading && !refresh)) return
    if (!refresh && validFrom && validTo && validFrom.startOf('minute').valueOf() >= validTo.startOf('minute').valueOf()) {
      message.error(t('Start time must precede end time'))
      return
    }
    const query: CertificateQuery | undefined = refresh ? appliedQuery.current : {
      ca, subject: subject || undefined, validFrom: certificateQueryTime(validFrom),
      validTo: certificateQueryTime(validTo), orderBy: certificateOrder(sortField, sortDirection), limit,
    }
    if (!query || query.ca !== ca) return
    if (!refresh) appliedQuery.current = query
    const request = ++requestGeneration.current
    setCerts([])
    setPage(1)
    setLoading(true)
    try {
      const rows = await collectCertificatePages<Omit<CertificateRow, 'ca'>>(query, certApi.list,
        () => request === requestGeneration.current)
      if (rows && request === requestGeneration.current)
        setCerts(rows.map(cert => ({ ...cert, ca })))
    } catch { /* The common client reports the error. */ }
    finally { if (request === requestGeneration.current) setLoading(false) }
  }

  const cancelSearch = () => {
    requestGeneration.current++
    setLoading(false)
    setCerts([])
  }

  useEffect(() => {
    setCerts([])
    if (selectedCa) void search()
  }, [selectedCa])

  const changeCa = (ca: string) => {
    requestGeneration.current++
    appliedQuery.current = undefined
    setLoading(false)
    setCerts([])
    setDetailTarget(undefined)
    setValidityFilter('all')
    setPage(1)
    setSelectedCa(ca)
  }

  const visibleCerts = certs.filter(cert => validityFilter === 'all' || matchesCertificateValidity(validityFilter, certificateValidity(cert, now)))
  const paginated = certificatePage(visibleCerts, page, pageSize)
  const columns: ColumnsType<CertificateRow> = [
    { title: t('Subject'), dataIndex: 'subject', width: 340, render: (subject: string) =>
      <span className="cert-subject" title={subject}>{subject}</span> },
    { title: t('Serial Number (hex)'), dataIndex: 'serialNumber', width: 190, render: (serial: string) =>
      <span className="cert-serial" title={serial}>{serial}</span> },
    { title: t('Validity period'), width: 180, render: (_: unknown, cert) => {
      const state = certificateValidity(cert, now)
      return <Tag color={validityColors[state]} title={state === 'expiring-soon' ? t('Expires within 30 days') : undefined}>
        {t(validityOptions.find(option => option.value === state)?.label || 'Unknown validity')}
      </Tag>
    } },
    { title: t('Not Before (local time)'), dataIndex: 'notBefore', width: 175, render: localCertificateTime },
    { title: t('Not After (local time)'), dataIndex: 'notAfter', width: 175, render: localCertificateTime },
    { title: t('Actions'), width: 100, align: 'center', render: (_: unknown, cert) => <>
      <Button type="text" icon={<EyeOutlined />} title={t('Certificate Detail')} aria-label={t('Certificate Detail')}
        onClick={() => setDetailTarget({ ca: cert.ca, serial: cert.serialNumber })} />
      <Button type="text" icon={<DownloadOutlined />} href={certApi.downloadUrl(cert.ca, cert.serialNumber)}
        download title={t('Download')} aria-label={t('Download')} />
    </> },
  ]

  return <div className="cert-list">
    <div className="cert-list-header">
      <Typography.Title level={4}>{can('CERT_READ_ALL', selectedCa) ? t('Certificates') : t('My certificates')}</Typography.Title>
      {(can('CERT_ISSUE') || can('CERT_KEYGEN') || can('CERT_CROSS')) &&
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/cert/enroll')}>{t('Enroll Certificate')}</Button>}
    </div>
    <form className="cert-list-filters" onSubmit={event => { event.preventDefault(); void search() }}>
      <div className="cert-filter-grid">
        <div className="cert-filter-field cert-filter-ca">
          <span className="cert-filter-label">{t('Select CA')}</span>
          <Select aria-label={t('Select CA')} showSearch loading={!casLoaded} value={selectedCa || undefined} onChange={changeCa}
            options={caNames.map(value => ({ value }))} placeholder={t('Select CA')} />
        </div>
        <div className="cert-filter-field cert-filter-subject">
          <span className="cert-filter-label">{t('Subject DN filter')}</span>
          <Input aria-label={t('Subject DN filter')} placeholder={t('Subject DN filter')} allowClear value={subject}
            onChange={event => setSubject(event.target.value)} />
        </div>
        <div className="cert-filter-field cert-filter-validity">
          <span className="cert-filter-label">{t('Validity in results')}</span>
          <Select aria-label={t('Validity in results')} value={validityFilter}
            onChange={value => { setValidityFilter(value); setPage(1) }}
            options={validityOptions.map(option => ({ value: option.value, label: t(option.label) }))} />
        </div>
        <div className="cert-filter-field cert-filter-from">
          <span className="cert-filter-label">{t('Starts before (local time)')}</span>
          <DatePicker aria-label={t('Starts before (local time)')} showTime={{ format: 'HH:mm' }} format="YYYY-MM-DD HH:mm"
            placeholder={t('Select date and time')} value={validFrom} onChange={setValidFrom} />
        </div>
        <div className="cert-filter-field cert-filter-to">
          <span className="cert-filter-label">{t('Expires after (local time)')}</span>
          <DatePicker aria-label={t('Expires after (local time)')} showTime={{ format: 'HH:mm' }} format="YYYY-MM-DD HH:mm"
            placeholder={t('Select date and time')} value={validTo} onChange={setValidTo} />
        </div>
        <div className="cert-filter-field cert-filter-sort">
          <span className="cert-filter-label">{t('Sort by')}</span>
          <Select aria-label={t('Sort by')} value={sortField} onChange={setSortField}
            options={sortFields.map(option => ({ value: option.value, label: t(option.label) }))} />
        </div>
        <div className="cert-filter-field cert-filter-direction">
          <span className="cert-filter-label">{t('Sort order')}</span>
          <Segmented aria-label={t('Sort order')} value={sortDirection}
            onChange={value => setSortDirection(value as CertificateSortDirection)}
            options={[{ value: 'asc', label: t('Ascending') }, { value: 'desc', label: t('Descending') }]} />
        </div>
        <div className="cert-filter-field cert-filter-limit">
          <span className="cert-filter-label">{t('Query limit')}</span>
          <InputNumber aria-label={t('Query limit')} min={1} max={1000} precision={0} value={limit}
            onChange={value => setLimit(value ?? 100)} />
        </div>
      </div>
      <div className="cert-filter-actions">
        <Typography.Text type="secondary">{t('Showing {shown} of {loaded} loaded', {
          shown: visibleCerts.length, loaded: certs.length,
        })}</Typography.Text>
        <div className="cert-filter-buttons">
          <Button htmlType="submit" type="primary" icon={<SearchOutlined />} loading={loading}
            disabled={!selectedCa || loading}>{t('Search')}</Button>
          {loading && <Button onClick={cancelSearch}>{t('Cancel')}</Button>}
        </div>
      </div>
    </form>
    {caLoadError && <Alert type="error" showIcon message={t('Unable to load available CAs')}
      action={<Button onClick={() => setCaLoadAttempt(attempt => attempt + 1)}>{t('Retry')}</Button>}
      style={{ marginBottom: 16 }} />}
    {casLoaded && !caLoadError && caNames.length === 0 && <Alert type="info" showIcon
      message={t('No running CA is available for this operation.')} style={{ marginBottom: 16 }} />}
    <Table rowKey={cert => `${cert.ca}/${cert.serialNumber}`} columns={columns}
      dataSource={paginated.items}
      loading={loading} scroll={{ x: 1160 }} tableLayout="fixed"
      pagination={false} />
    {visibleCerts.length > 0 && <Pagination className="cert-list-pagination" current={paginated.current}
      total={visibleCerts.length} pageSize={pageSize} showSizeChanger pageSizeOptions={['20', '50', '100']}
      responsive showTotal={(total, range) => t('Showing {from}-{to} of {total} loaded', {
        from: range[0], to: range[1], total,
      })}
      onChange={(nextPage, nextSize) => { setPage(nextPage); setPageSize(nextSize) }} />}
    {detailTarget && <CertDetailDrawer key={`${detailTarget.ca}/${detailTarget.serial}`} target={detailTarget}
      onClose={() => setDetailTarget(undefined)} onChanged={() => void search(true)} />}
  </div>
}

export default CertList
