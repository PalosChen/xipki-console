import { useI18n } from '../../i18n'
import React, { useState, useEffect } from 'react'
import { Alert, Table, Button, Select, Input, InputNumber, Space, Tag, Popconfirm, message, Typography, Modal, Form } from 'antd'
import { SearchOutlined, PlusOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { certApi } from '../../api/cert'
import { caApi } from '../../api/ca'
import { operationalCaNames } from '../../ca-availability'
import { useCan } from '../../auth'
import { useReauthentication } from '../../components/Reauthentication'

type Certificate = { ca: string; serialNumber: string; subject: string; issuer?: string; notBefore: string; notAfter: string;
  profile?: string; revoked?: boolean; revocationReason?: string; revocationTime?: string; invalidityTime?: string; certPem?: string }
const reasons = ['unspecified', 'keyCompromise', 'cACompromise', 'affiliationChanged', 'superseded', 'cessationOfOperation', 'certificateHold', 'privilegeWithdrawn', 'aACompromise']
const orders = ['notBefore', 'notBefore-desc', 'notAfter', 'notAfter-desc', 'subject', 'subject-desc']

const CertList: React.FC = () => {
  const { t } = useI18n()
  const can = useCan()
  const {authorize,dialog}=useReauthentication()
  const [certs, setCerts] = useState<Certificate[]>([])
  const [caNames, setCaNames] = useState<string[]>([])
  const [casLoaded, setCasLoaded] = useState(false)
  const [selectedCa, setSelectedCa] = useState('')
  const [subject, setSubject] = useState('')
  const [validFrom, setValidFrom] = useState('')
  const [validTo, setValidTo] = useState('')
  const [orderBy, setOrderBy] = useState('notBefore')
  const [limit, setLimit] = useState(50)
  const [loading, setLoading] = useState(false)
  const [working, setWorking] = useState(false)
  const [detail, setDetail] = useState<Certificate>()
  const [revokeOpen, setRevokeOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [revokeForm] = Form.useForm<{ reason: string; invalidityTime?: string }>()
  const [deleteForm] = Form.useForm<{ serial: string }>()
  const navigate = useNavigate()

  useEffect(() => {
    caApi.list().then(res => {
      const names = operationalCaNames(res.data.data || [], 'CERT_READ', can)
      setCaNames(names)
      setSelectedCa(names[0] || '')
    }).catch(() => {}).finally(() => setCasLoaded(true))
  }, [])

  const search = async () => {
    if (!selectedCa) return
    if (validFrom && validTo && Date.parse(validFrom) >= Date.parse(validTo)) { message.error(t("Valid From must precede Valid To")); return }
    setLoading(true)
    try {
      const res = await certApi.list({ ca: selectedCa, subject: subject || undefined, validFrom: validFrom || undefined,
        validTo: validTo || undefined, orderBy, limit })
      setCerts(res.data.data || [])
    } catch { /* Reported by common client. */ }
    finally { setLoading(false) }
  }
  useEffect(() => { setDetail(undefined); setCerts([]); if (selectedCa) void search() }, [selectedCa])

  const showDetail = async (serial: string) => {
    try { const res = await certApi.get(selectedCa, serial); setDetail({ ...res.data.data, ca: selectedCa }) }
    catch { /* Reported by common client. */ }
  }

  const mutate = async (action: 'revoke' | 'unsuspend' | 'delete') => {
    if (!detail || working) return
    const target = detail
    setWorking(true)
    try {
      if (action === 'revoke') {
        const values = await revokeForm.validateFields()
        await certApi.revoke(target.ca, target.serialNumber, values.reason, values.invalidityTime || undefined)
        setRevokeOpen(false)
        revokeForm.resetFields()
      } else if (action === 'unsuspend') await certApi.unsuspend(target.ca, target.serialNumber)
      else {
        await deleteForm.validateFields()
        await certApi.remove(target.ca, target.serialNumber)
        setDeleteOpen(false)
        deleteForm.resetFields()
      }
      if (action === 'delete') setDetail(undefined)
      else {
        const refreshed = await certApi.get(target.ca, target.serialNumber)
        setDetail({ ...refreshed.data.data, ca: target.ca })
      }
      message.success(action === 'delete' ? t("Certificate deleted") : t("Certificate state refreshed"))
      await search()
    } catch { /* Do not automatically retry certificate mutations. */ }
    finally { setWorking(false) }
  }

  const columns = [
    { title: t("Serial Number (hex)"), dataIndex: 'serialNumber', width: 180 },
    { title: t("Subject"), dataIndex: 'subject', ellipsis: true },
    { title: t("Not Before"), dataIndex: 'notBefore', width: 200 },
    { title: t("Not After"), dataIndex: 'notAfter', width: 200 },
    { title: t("Actions"), render: (_: unknown, record: Certificate) => <Space>
      <Button size="small" onClick={() => void showDetail(record.serialNumber)}>{t("Detail / Manage")}</Button>
      <a href={certApi.downloadUrl(selectedCa, record.serialNumber)} download>{t("Download")}</a>
    </Space> },
  ]
  const held = detail?.revoked && detail.revocationReason === 'certificateHold'

  return <div>
    <Space style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
      <Typography.Title level={4}>{can('CERT_READ_ALL', selectedCa) ? t('Certificates') : t('My certificates')}</Typography.Title>
      {(can('CERT_ISSUE') || can('CERT_KEYGEN') || can('CERT_CROSS')) && <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/cert/enroll')}>{t("Enroll Certificate")}</Button>}
    </Space>
    <Space wrap style={{ marginBottom: 16 }}>
      <Select value={selectedCa} onChange={setSelectedCa} style={{ width: 200 }} options={caNames.map(value => ({ value }))} placeholder={t("Select CA")} />
      <Input placeholder={t("Subject DN filter")} value={subject} onChange={e => setSubject(e.target.value)} style={{ width: 250 }} />
      <Input aria-label={t("Valid From")} placeholder={t("Valid From (ISO UTC)")} value={validFrom} onChange={e => setValidFrom(e.target.value)} style={{ width: 240 }} />
      <Input aria-label={t("Valid To")} placeholder={t("Valid To (ISO UTC)")} value={validTo} onChange={e => setValidTo(e.target.value)} style={{ width: 240 }} />
      <Select aria-label={t("Sort order")} value={orderBy} onChange={setOrderBy} style={{ width: 180 }} options={orders.map(value => ({ value, label: t(value) }))} />
      <InputNumber aria-label={t("Query limit")} min={1} max={1000} precision={0} value={limit} onChange={value => setLimit(value ?? 50)} />
      <Button type="primary" icon={<SearchOutlined />} onClick={() => void search()} loading={loading} disabled={!selectedCa}>{t("Search")}</Button>
    </Space>
    {casLoaded && caNames.length === 0 && <Alert type="info" showIcon message={t('No running CA is available for this operation.')} style={{ marginBottom: 16 }} />}
    <Table columns={columns} dataSource={certs} rowKey="serialNumber" loading={loading} />
    <Modal title={t("Certificate Detail")} open={!!detail} onCancel={() => !working && setDetail(undefined)} footer={null} width={750}>
      {detail && <div>
        <p><b>{t("Subject:")}</b> {detail.subject}</p><p><b>{t("Issuer:")}</b> {detail.issuer}</p>
        <p><b>{t("Serial (hex):")}</b> {detail.serialNumber}</p><p><b>{t("Not Before:")}</b> {detail.notBefore}</p>
        <p><b>{t("Not After:")}</b> {detail.notAfter}</p><p><b>{t("Profile:")}</b> {detail.profile}</p>
        <Tag color={held ? 'orange' : detail.revoked ? 'red' : 'green'}>{held ? t("ON HOLD") : detail.revoked ? t("REVOKED") : t("NOT REVOKED")}</Tag>
        {detail.revoked && <><p>{t("Reason:")} {t(detail.revocationReason || '')}</p><p>{t("Revocation Time:")} {detail.revocationTime}</p><p>{t("Invalidity Time:")} {detail.invalidityTime || '-'}</p></>}
        <Space style={{ marginTop: 16 }}>
          {(can('CERT_REVOKE',detail.ca) || can('CERT_SUSPEND',detail.ca)) && (!detail.revoked || held) && <Button danger disabled={working} onClick={() => {revokeForm.resetFields();revokeForm.setFieldValue('reason',can('CERT_REVOKE',detail.ca)?'unspecified':'certificateHold');setRevokeOpen(true)}}>{t("Revoke / Hold")}</Button>}
          {held && can('CERT_UNSUSPEND',detail.ca) && <Popconfirm title={t("Remove certificate hold?")} onConfirm={() => mutate('unsuspend')}><Button loading={working}>{t("Unsuspend")}</Button></Popconfirm>}
        </Space>
        {can('CERT_DELETE', detail.ca) && <Button danger style={{ marginTop: 16 }} disabled={working} onClick={() => { deleteForm.resetFields(); setDeleteOpen(true) }}>{t('Delete certificate')}</Button>}
        {detail.certPem && <Input.TextArea value={detail.certPem} rows={8} readOnly style={{ fontFamily: 'monospace', marginTop: 16 }} />}
      </div>}
    </Modal>
    <Modal title={t("Revoke / Hold Certificate")} open={revokeOpen} onCancel={() => !working && setRevokeOpen(false)} onOk={() => void mutate('revoke')} confirmLoading={working}>
      <Form form={revokeForm} layout="vertical" initialValues={{ reason: 'unspecified' }}>
        <Form.Item name="reason" label={t("Reason")} rules={[{ required: true }]}><Select options={reasons.filter(value=>can(value==='certificateHold'?'CERT_SUSPEND':'CERT_REVOKE',detail?.ca)).map(value => ({ value, label: t(value) }))} /></Form.Item>
        <Form.Item name="invalidityTime" label={t("Invalidity Time (optional ISO-8601 UTC)")} rules={[{ pattern: /^\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d(?:\.\d+)?Z$/, message: t("Use UTC ending in Z") }]}>
          <Input placeholder="2026-10-23T00:00:00Z" />
        </Form.Item>
      </Form>
    </Modal>
    <Modal title={t("Delete Certificate")} open={deleteOpen} onCancel={() => !working && setDeleteOpen(false)} onOk={() => {void deleteForm.validateFields().then(()=>authorize(t('Delete Certificate'),()=>mutate('delete'))).catch(()=>{})}} confirmLoading={working} okText={t("Delete")} okButtonProps={{ danger: true }}>
      <p>{t("Deletion removes the CA record. Enter the hexadecimal serial number to confirm:")} {detail?.serialNumber}</p>
      <Form form={deleteForm}><Form.Item name="serial" rules={[{ required: true }, { validator: (_, value) => value === detail?.serialNumber ? Promise.resolve() : Promise.reject(new Error(t("Serial number must match exactly"))) }]}>
        <Input autoComplete="off" placeholder={t("Certificate serial number")} />
      </Form.Item></Form>
    </Modal>
    {dialog}
  </div>
}

export default CertList
