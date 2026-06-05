<template>
  <div class="device-manage">
    <div class="page-title-row">
      <h1>设备管理</h1>
      <span class="page-subtitle">绑定设备并编辑 DMN 诊断维度，调试智能客服分支话术</span>
    </div>

    <div class="toolbar">
      <button class="btn-seed" :disabled="seeding" @click="handleSeedAll">
        {{ seeding ? '生成中...' : '为全部用户生成调试设备' }}
      </button>
      <span class="toolbar-hint">仅为尚未绑定设备的用户创建；默认「已激活 + 已实名 + 在线」</span>
    </div>

    <div class="section">
      <h3>{{ editingDevice ? '编辑设备' : '添加设备' }}</h3>
      <form @submit.prevent="handleSave" class="device-form">
        <div class="form-row">
          <input v-model="form.deviceModel" placeholder="设备型号" required />
          <input v-model="form.sn" placeholder="序列号(SN)" />
          <input v-model="form.imei" placeholder="IMEI" />
          <select v-model="form.bindUserId" class="select-input">
            <option value="">绑定用户（可选）</option>
            <option v-for="u in userOptions" :key="u.userId" :value="u.userId">{{ u.label }}</option>
          </select>
        </div>
        <div class="form-row">
          <input v-model="form.dataPlan" placeholder="流量套餐" />
          <input v-model.number="form.dataTotalGb" type="number" step="0.01" placeholder="总流量(GB)" />
          <input v-model.number="form.dataUsedGb" type="number" step="0.01" placeholder="已用流量(GB)" />
          <input v-model="form.expireTime" type="date" placeholder="到期日" />
          <select v-model="form.status" class="select-input">
            <option value="active">active</option>
            <option value="disabled">disabled</option>
          </select>
        </div>

        <div class="diag-block">
          <h4>诊断维度（覆盖 DMN 输入，留空则按上方基础字段自动推导）</h4>
          <div class="form-row diag-row">
            <label>激活状态<select v-model="form.diagActivationStatus"><option value="">自动</option><option v-for="o in ACTIVATION_OPTS" :key="o" :value="o">{{ o }}</option></select></label>
            <label>实名状态<select v-model="form.diagRealnameStatus"><option value="">自动</option><option v-for="o in REALNAME_OPTS" :key="o" :value="o">{{ o }}</option></select></label>
            <label>在线状态<select v-model="form.diagOnlineStatus"><option value="">自动</option><option v-for="o in ONLINE_OPTS" :key="o" :value="o">{{ o }}</option></select></label>
            <label>信号值<select v-model="form.diagSignalValue"><option value="">自动</option><option v-for="o in SIGNAL_OPTS" :key="o" :value="o">{{ o }}</option></select></label>
            <label>设备状态<select v-model="form.diagDeviceStatus"><option value="">自动</option><option v-for="o in DEVICE_STATUS_OPTS" :key="o" :value="o">{{ o }}</option></select></label>
          </div>
        </div>

        <div class="form-actions">
          <button type="submit">{{ editingDevice ? '更新' : '添加' }}</button>
          <button v-if="editingDevice" type="button" @click="cancelEdit" class="btn-cancel">取消</button>
        </div>
      </form>
    </div>

    <div class="section">
      <h3>设备列表</h3>
      <div v-if="loading" class="empty-hint">加载中...</div>
      <div v-else-if="devices.length > 0" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户</th><th>型号</th><th>SN</th>
              <th>激活</th><th>实名</th><th>在线</th><th>信号</th>
              <th>流量</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in devices" :key="d.deviceId">
              <td>
                <div class="user-cell">{{ d.userId || '—' }}</div>
              </td>
              <td>{{ d.deviceModel }}</td>
              <td class="mono">{{ d.sn || '—' }}</td>
              <td>{{ dim(d, 'activation') }}</td>
              <td>{{ dim(d, 'realname') }}</td>
              <td>{{ dim(d, 'online') }}</td>
              <td>{{ dim(d, 'signal') }}</td>
              <td>
                <span class="data-used">{{ d.dataUsedGb }}</span>
                <span class="data-sep"> / </span>
                <span class="data-total">{{ d.dataTotalGb }}</span>
              </td>
              <td class="actions">
                <button @click="startEdit(d)">编辑</button>
                <button @click="doUnbind(d)" v-if="d.userId" class="btn-warn">解绑</button>
                <button @click="doDelete(d)" class="btn-danger">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="empty-hint">暂无设备，请点击上方「为全部用户生成调试设备」</p>

      <Pagination :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { adminApi } from '@/api'
import { useToast } from '@/stores/toast'
import Pagination from '@/components/Pagination.vue'

const ACTIVATION_OPTS = ['已激活', '未激活']
const REALNAME_OPTS = ['全部实名', '部分实名', '未实名']
const ONLINE_OPTS = ['在线', '离线']
const SIGNAL_OPTS = ['信号好', '信号差']
const DEVICE_STATUS_OPTS = ['可用', '未激活', '设备禁用', '服务到期', '流量用完']

const toast = useToast()
const devices = ref([])
const userOptions = ref([])
const loading = ref(false)
const seeding = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const editingDevice = ref(null)

const emptyForm = () => ({
  deviceModel: '',
  sn: '',
  imei: '',
  bindUserId: '',
  dataPlan: '月享30GB',
  dataTotalGb: 30,
  dataUsedGb: 5,
  expireTime: '',
  status: 'active',
  diagActivationStatus: '已激活',
  diagRealnameStatus: '全部实名',
  diagOnlineStatus: '在线',
  diagSignalValue: '信号好',
  diagDeviceStatus: ''
})

const form = reactive(emptyForm())

onMounted(async () => {
  await loadUserOptions()
  await loadDevices()
})

async function loadUserOptions() {
  try {
    const res = await adminApi.listDeviceUserOptions()
    userOptions.value = res.data || []
  } catch {
    userOptions.value = []
  }
}

async function loadDevices() {
  loading.value = true
  try {
    const res = await adminApi.listDevices(pageNum.value, pageSize.value)
    const data = res.data
    devices.value = data.records || []
    total.value = data.total || 0
  } catch {
    toast.error('加载设备列表失败')
  } finally {
    loading.value = false
  }
}

function handlePageChange(page) {
  pageNum.value = page
  loadDevices()
}

function dim(d, key) {
  const map = {
    activation: d.diagActivationStatus || (d.activationTime ? '已激活*' : '未激活*'),
    realname: d.diagRealnameStatus || (d.sn ? '实名*' : '未实名*'),
    online: d.diagOnlineStatus || (d.status === 'active' ? '在线*' : '离线*'),
    signal: d.diagSignalValue || '—'
  }
  return map[key] || '—'
}

async function handleSeedAll() {
  if (!confirm('为所有尚未绑定设备的用户各创建一台调试设备？')) return
  seeding.value = true
  try {
    const res = await adminApi.seedAllUserDevices()
    toast.success(res.data?.message || `已创建 ${res.data?.created ?? 0} 台`)
    await loadDevices()
  } catch (e) {
    toast.error(e.response?.data?.message || e.message || '生成失败')
  } finally {
    seeding.value = false
  }
}

function buildPayload() {
  const payload = {
    deviceModel: form.deviceModel,
    sn: form.sn,
    imei: form.imei,
    dataPlan: form.dataPlan,
    dataTotalGb: form.dataTotalGb,
    dataUsedGb: form.dataUsedGb,
    expireTime: form.expireTime ? form.expireTime + 'T23:59:59' : null,
    status: form.status,
    diagActivationStatus: form.diagActivationStatus || null,
    diagRealnameStatus: form.diagRealnameStatus || null,
    diagOnlineStatus: form.diagOnlineStatus || null,
    diagSignalValue: form.diagSignalValue || null,
    diagDeviceStatus: form.diagDeviceStatus || null
  }
  return payload
}

async function handleSave() {
  try {
    const payload = buildPayload()
    if (editingDevice.value) {
      await adminApi.updateDevice(editingDevice.value.deviceId, payload)
      if (form.bindUserId && form.bindUserId !== editingDevice.value.userId) {
        await adminApi.bindDevice(editingDevice.value.deviceId, form.bindUserId)
      }
      toast.success('设备已更新')
    } else {
      const res = await adminApi.createDevice(payload)
      const created = res.data
      if (form.bindUserId && created?.deviceId) {
        await adminApi.bindDevice(created.deviceId, form.bindUserId)
      }
      toast.success('设备已添加')
    }
    cancelEdit()
    await loadDevices()
  } catch (e) {
    toast.error('保存失败: ' + (e.response?.data?.message || e.message))
  }
}

function startEdit(device) {
  editingDevice.value = device
  Object.assign(form, {
    deviceModel: device.deviceModel || '',
    sn: device.sn || '',
    imei: device.imei || '',
    bindUserId: device.userId || '',
    dataPlan: device.dataPlan || '',
    dataTotalGb: device.dataTotalGb ?? 0,
    dataUsedGb: device.dataUsedGb ?? 0,
    expireTime: device.expireTime?.substring(0, 10) || '',
    status: device.status || 'active',
    diagActivationStatus: device.diagActivationStatus || '',
    diagRealnameStatus: device.diagRealnameStatus || '',
    diagOnlineStatus: device.diagOnlineStatus || '',
    diagSignalValue: device.diagSignalValue || '',
    diagDeviceStatus: device.diagDeviceStatus || ''
  })
}

function cancelEdit() {
  editingDevice.value = null
  Object.assign(form, emptyForm())
}

async function doUnbind(device) {
  if (!confirm('确定解绑该设备吗？')) return
  try {
    await adminApi.unbindDevice(device.deviceId)
    toast.success('设备已解绑')
    await loadDevices()
  } catch (e) {
    toast.error('解绑失败: ' + e.message)
  }
}

async function doDelete(device) {
  if (!confirm('确定删除该设备吗？')) return
  try {
    await adminApi.deleteDevice(device.deviceId)
    toast.success('设备已删除')
    await loadDevices()
  } catch (e) {
    toast.error('删除失败: ' + e.message)
  }
}
</script>

<style scoped>
.page-title-row { margin-bottom: 16px; }
.page-title-row h1 {
  font-family: var(--font-display); font-size: 26px; font-weight: 400;
  color: var(--text); letter-spacing: 1px; margin-bottom: 4px;
}
.page-subtitle { font-size: 13px; color: var(--text-muted); }

.toolbar {
  display: flex; align-items: center; gap: 12px; margin-bottom: 16px; flex-wrap: wrap;
}
.btn-seed {
  padding: 8px 16px; background: #0f766e; color: #fff; border: none;
  border-radius: 4px; cursor: pointer; font-size: 13px;
}
.btn-seed:disabled { opacity: 0.6; cursor: not-allowed; }
.toolbar-hint { font-size: 12px; color: #64748b; }

.section {
  background: #fff; border: 1px solid var(--border);
  padding: 22px 24px; border-radius: var(--radius);
  margin-bottom: 18px; box-shadow: var(--shadow-sm);
}
.section h3 { margin-bottom: 14px; font-size: 15px; color: var(--text); font-weight: 500; }

.diag-block {
  margin: 12px 0; padding: 12px 14px; background: #f0f9ff; border-radius: 6px;
  border: 1px solid #bae6fd;
}
.diag-block h4 { font-size: 13px; color: #0369a1; margin: 0 0 10px; font-weight: 600; }
.diag-row label {
  flex: 1; display: flex; flex-direction: column; gap: 4px;
  font-size: 11px; color: #64748b; min-width: 120px;
}
.diag-row select { font-size: 13px; padding: 6px 8px; }

.device-form .form-row { display: flex; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
.device-form input, .select-input {
  flex: 1; min-width: 120px; padding: 10px 14px; background: #fff;
  border: 1px solid #e4e8ec; border-radius: 2px; font-size: 14px;
}
.form-actions { display: flex; gap: 8px; margin-top: 8px; }
.device-form button[type="submit"] {
  padding: 10px 24px; background: #1e2d3d; color: #fff; border: none;
  border-radius: 2px; cursor: pointer; font-size: 14px;
}
.device-form .btn-cancel { background: #c4cdd4; color: #fff; border: none; padding: 10px 20px; border-radius: 2px; cursor: pointer; }

.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--border); }
th { background: #f8f9fa; font-size: 11px; font-weight: 600; color: #4a5c6c; }
.mono { font-family: monospace; font-size: 11px; }
.user-cell { font-size: 11px; word-break: break-all; max-width: 100px; }
.data-used { color: #38bdf8; }
.data-sep { color: #8899a6; }

.actions { display: flex; gap: 5px; flex-wrap: wrap; }
.actions button {
  padding: 4px 10px; border: 1px solid var(--border-strong); background: transparent;
  border-radius: 2px; cursor: pointer; font-size: 12px;
}
.actions .btn-warn { color: var(--warning); }
.actions .btn-danger { color: var(--error); }
.empty-hint { text-align: center; padding: 40px; color: var(--text-muted); }
</style>
