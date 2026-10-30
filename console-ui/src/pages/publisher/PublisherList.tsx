import { useI18n } from '../../i18n'
import React, { useState } from 'react'
import { Button, Modal, Form, Select, message } from 'antd'
import EntityCrudPage from '../../components/EntityCrudPage'
import { publisherApi } from '../../api/entities'
import { caApi } from '../../api/ca'
import { useCan } from '../../auth'

const PublisherList: React.FC = () => {
  const { t } = useI18n()
  const can = useCan()
  const [name, setName] = useState<string | null>(null)
  const [caNames, setCaNames] = useState<string[]>([])
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()
  const open = async (publisher: string) => {
    try {
      const res = await caApi.list()
      setCaNames((res.data.data || []).filter((ca: any) => can('CA_PUBLISHER_BIND', ca)).map((ca: any) => ca.name))
      form.resetFields()
      setName(publisher)
    } catch (e: any) { message.error(e.response?.data?.error || e.message || t("Could not load CAs")) }
  }
  const bind = async (values: any) => {
    if (!name || saving) return
    setSaving(true)
    try {
      await publisherApi.addToCa(name, values.ca)
      message.success(t("Publisher bound to CA"))
      setName(null)
      form.resetFields()
    } catch (e: any) { message.error(e.response?.data?.error || e.message || t("Binding failed")) }
    finally { setSaving(false) }
  }
  return <>
    <EntityCrudPage resource="PUBLISHER" title={t("Publishers")} api={publisherApi}
      extraActions={record => can('CA_PUBLISHER_BIND') && <Button size="small" onClick={() => open(record.name)}>{t("Add to CA")}</Button>} />
    <Modal title={t('Bind {name} to CA', { name: name || '' })} open={!!name} confirmLoading={saving}
      onCancel={() => { if (!saving) { setName(null); form.resetFields() } }} onOk={() => form.submit()}>
      <Form form={form} layout="vertical" onFinish={bind}>
        <Form.Item name="ca" label="CA" rules={[{ required: true }]}>
          <Select options={caNames.map(value => ({ value }))} />
        </Form.Item>
      </Form>
    </Modal>
  </>
}
export default PublisherList
