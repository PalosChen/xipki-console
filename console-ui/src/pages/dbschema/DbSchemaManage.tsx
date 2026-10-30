import { useI18n } from '../../i18n'
import React, { useEffect, useState } from 'react'
import { Table, Button, Space, Popconfirm, message, Typography, Modal, Form, Input } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { dbSchemaApi } from '../../api/system'
import { useCan } from '../../auth'
import { useReauthentication } from '../../components/Reauthentication'

const DbSchemaManage: React.FC = () => {
  const { t } = useI18n()
  const can=useCan()
  const {authorize,dialog}=useReauthentication()
  const [data, setData] = useState<{ name: string; value: string }[]>([])
  const [loading, setLoading] = useState(true)
  const [modalOpen, setModalOpen] = useState(false)
  const [editItem, setEditItem] = useState<{ name: string; value: string } | null>(null)
  const [form] = Form.useForm()

  const fetchData = () => {
    setLoading(true)
    dbSchemaApi.list()
      .then(res => {
        const map = res.data.data || {}
        setData(Object.entries(map).map(([name, value]) => ({ name, value: value as string })))
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }

  useEffect(() => { fetchData() }, [])

  const openAdd = () => {
    setEditItem(null)
    form.resetFields()
    setModalOpen(true)
  }

  const openEdit = (record: any) => {
    setEditItem(record)
    form.setFieldsValue(record)
    setModalOpen(true)
  }

  const handleSubmit = async (values: any) => {
    try {
      if (editItem) {
        await dbSchemaApi.update(editItem.name, values.value)
        message.success(t("Updated"))
      } else {
        await dbSchemaApi.add(values.name, values.value)
        message.success(t("Added"))
      }
      setModalOpen(false)
      form.resetFields()
      setEditItem(null)
      fetchData()
    } catch (e: any) {
      message.error(e.response?.data?.error || t("Operation failed"))
    }
  }

  const handleDelete = async (name: string) => {
    await dbSchemaApi.remove(name)
    message.success(t("Deleted"))
    fetchData()
  }

  const columns = [
    { title: t("Name"), dataIndex: 'name', key: 'name' },
    { title: t("Value"), dataIndex: 'value', key: 'value' },
    {
      title: t("Actions"), key: 'actions', width: 200,
      render: (_: any, record: any) => (
        <Space>
          {can('DBSCHEMA_UPDATE') && <Button size="small" onClick={() => openEdit(record)}>{t("Edit")}</Button>}
          {can('DBSCHEMA_DELETE') && <Popconfirm title={t("Delete?")} onConfirm={() => authorize(t('Database Schema'),()=>handleDelete(record.name))}>
            <Button size="small" danger>{t("Delete")}</Button>
          </Popconfirm>}
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4}>{t("Database Schema")}</Typography.Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchData}>{t("Refresh")}</Button>
          {can('DBSCHEMA_CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={openAdd}>{t("Add")}</Button>}
        </Space>
      </div>
      <Table columns={columns} dataSource={data} rowKey="name" loading={loading} />

      <Modal title={editItem ? t("Edit DB Schema") : t("Add DB Schema")} open={modalOpen}
             onCancel={() => { setModalOpen(false); setEditItem(null) }} onOk={() => form.submit()}>
        <Form form={form} layout="vertical" onFinish={values=>authorize(t('Database Schema'),()=>handleSubmit(values))}>
          {!editItem && (
            <Form.Item name="name" label={t("Name")} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          )}
          <Form.Item name="value" label={t("Value")} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>
      {dialog}
    </div>
  )
}

export default DbSchemaManage
