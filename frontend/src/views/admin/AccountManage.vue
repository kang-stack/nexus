<template>
  <div class="account-manage">
    <div class="page-title-row">
      <h1>账号管理</h1>
      <span class="page-subtitle">管理后台账号</span>
    </div>

    <div class="section">
      <h3>添加后台账号</h3>
      <form @submit.prevent="handleCreate" class="account-form">
        <div class="form-row">
          <input v-model="createForm.username" placeholder="用户名" required maxlength="20" />
          <input v-model="createForm.password" type="password" placeholder="密码（至少6位）" required minlength="6" maxlength="20" />
          <input v-model="createForm.realName" placeholder="姓名（选填）" maxlength="20" />
          <select v-model="createForm.role" required>
            <option value="">选择角色</option>
            <option v-for="r in roleOptions" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>
          <button type="submit" :disabled="creating">{{ creating ? '创建中...' : '创建' }}</button>
        </div>
      </form>
    </div>

    <div class="section">
      <h3>账号列表</h3>
      <div v-if="loading" class="empty-hint">加载中...</div>
      <div v-else-if="accounts.length > 0" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>用户名</th><th>姓名</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="acc in accounts" :key="acc.adminId">
              <td class="username-cell">{{ acc.username }}</td>
              <td>{{ acc.realName || '—' }}</td>
              <td><span :class="['role-badge', acc.role]">{{ roleMap[acc.role] || acc.role }}</span></td>
              <td><span :class="['status-badge', acc.status]">{{ acc.status === 'active' ? '正常' : '已禁用' }}</span></td>
              <td>{{ acc.createdAt?.substring(0, 10) }}</td>
              <td class="actions">
                <button @click="startChangeRole(acc)" v-if="auth.isSuperAdmin && acc.role !== 'SUPER_ADMIN'">改角色</button>
                <button @click="doToggleStatus(acc)" :class="{ 'btn-warn': acc.status === 'active' }">{{ acc.status === 'active' ? '禁用' : '启用' }}</button>
                <button @click="doResetPassword(acc)">重置密码</button>
                <button @click="doDelete(acc)" class="btn-danger" v-if="acc.role !== 'SUPER_ADMIN'">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="empty-hint">暂无其他后台账号</p>
    </div>

    <!-- Role Modal -->
    <div v-if="roleModal.visible" class="modal-overlay" @click.self="roleModal.visible = false">
      <div class="modal-card">
        <h4>修改角色</h4>
        <p class="modal-user">{{ roleModal.username }}</p>
        <select v-model="roleModal.newRole">
          <option v-for="r in roleOptions" :key="r.value" :value="r.value">{{ r.label }}</option>
        </select>
        <div class="modal-btns">
          <button class="btn-cancel" @click="roleModal.visible = false">取消</button>
          <button class="btn-confirm" @click="doChangeRole">确认</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import { adminApi } from '@/api'
import { useToast } from '@/stores/toast'
import { useAdminAuthStore } from '@/stores/adminAuth'

const toast = useToast()
const auth = useAdminAuthStore()

/** 创建时可选择的角色 */
const roleOptions = computed(() =>
  auth.isSuperAdmin
    ? [{ value: 'ADMIN', label: '管理员' }, { value: 'OPERATOR', label: '运营人员' }]
    : [{ value: 'OPERATOR', label: '运营人员' }]
)
const accounts = ref([])
const loading = ref(false)
const creating = ref(false)

const roleMap = { SUPER_ADMIN: '超级管理员', ADMIN: '管理员', OPERATOR: '运营人员' }
const createForm = reactive({ username: '', password: '', realName: '', role: '' })
const roleModal = reactive({ visible: false, adminId: '', username: '', newRole: 'ADMIN' })

onMounted(loadAccounts)

async function loadAccounts() {
  loading.value = true
  try { const res = await adminApi.listAccounts(); accounts.value = (res.data || []) }
  catch { toast.error('加载账号列表失败') }
  finally { loading.value = false }
}
async function handleCreate() {
  creating.value = true
  try {
    await adminApi.createAccount({ ...createForm }); toast.success('账号已创建')
    createForm.username = ''; createForm.password = ''; createForm.realName = ''; createForm.role = ''
    loadAccounts()
  } catch (e) { toast.error(e.response?.data?.error || '创建失败') }
  finally { creating.value = false }
}
function startChangeRole(acc) {
  roleModal.adminId = acc.adminId; roleModal.username = acc.username
  roleModal.newRole = acc.role === 'OPERATOR' ? 'ADMIN' : 'OPERATOR'
  roleModal.visible = true
}
async function doChangeRole() {
  try { await adminApi.updateAccountRole(roleModal.adminId, roleModal.newRole); toast.success('角色已更新'); roleModal.visible = false; loadAccounts() }
  catch (e) { toast.error('操作失败') }
}
async function doToggleStatus(acc) {
  const newStatus = acc.status === 'active' ? 'disabled' : 'active'
  const action = newStatus === 'active' ? '启用' : '禁用'
  if (!confirm(`确定${action}该账号吗？`)) return
  try { await adminApi.updateAccountStatus(acc.adminId, newStatus); toast.success(`账号已${action}`); loadAccounts() }
  catch (e) { toast.error('操作失败') }
}
async function doResetPassword(acc) {
  const pwd = prompt(`请输入 ${acc.username} 的新密码（至少6位）:`)
  if (!pwd || pwd.length < 6) return toast.error('密码至少6位')
  try { await adminApi.resetAccountPassword(acc.adminId, pwd); toast.success('密码已重置') }
  catch (e) { toast.error('操作失败') }
}
async function doDelete(acc) {
  if (!confirm(`确定删除账号「${acc.username}」吗？此操作不可恢复。`)) return
  try { await adminApi.deleteAccount(acc.adminId); toast.success('账号已删除'); loadAccounts() }
  catch (e) { toast.error('删除失败') }
}
</script>

<style scoped>
.page-title-row { margin-bottom: 24px; }
.page-title-row h1 {
  font-family: var(--font-display); font-size: 26px; font-weight: 400;
  color: var(--text); letter-spacing: 1px; margin-bottom: 4px;
}
.page-subtitle { font-size: 13px; color: var(--text-muted); }

.section {
  background: #fff; border: 1px solid var(--border);
  padding: 22px 24px; border-radius: var(--radius);
  margin-bottom: 18px; box-shadow: var(--shadow-sm);
}
.section h3 { margin-bottom: 14px; font-size: 15px; color: var(--text); font-weight: 500; }

/* Form */
.account-form .form-row { display: flex; gap: 10px; }
.account-form input, .account-form select {
  flex: 1; padding: 10px 14px; background: #fff;
  border: 1px solid #e4e8ec; border-radius: 2px;
  font-size: 14px; color: var(--text); outline: none;
  transition: all 0.2s; font-family: var(--font-body); min-width: 0;
}
.account-form input::placeholder { color: #8899a6; }
.account-form input:focus, .account-form select:focus { border-color: #38bdf8; box-shadow: 0 0 0 3px rgba(56,189,248,0.1); background: #fff; }
.account-form select { color: var(--text); cursor: pointer; }
.account-form select option { background: #fff; color: var(--text); }
.account-form button {
  padding: 10px 20px; background: #1e2d3d;
  color: #fff; border: none; border-radius: 2px;
  cursor: pointer; font-size: 14px; font-weight: 500;
  transition: all 0.2s; white-space: nowrap; font-family: var(--font-body);
}
.account-form button:hover { background: #243447; box-shadow: 0 4px 20px rgba(30,45,61,0.25); }
.account-form button:disabled { background: #c4cdd4; color: #fff; cursor: not-allowed; box-shadow: none; }

/* Table */
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--border); }
th {
  background: #f8f9fa; font-weight: 600; color: #4a5c6c;
  font-size: 11px; text-transform: uppercase; letter-spacing: 1px;
}
tr:hover td { background: #f8f9fa; }
.username-cell { font-weight: 500; color: var(--text); }

/* Badges */
.role-badge { padding: 3px 10px; border-radius: 10px; font-size: 11px; font-weight: 500; }
.role-badge.SUPER_ADMIN { background: rgba(56,189,248,0.1); color: #38bdf8; }
.role-badge.ADMIN { background: var(--success-bg); color: var(--success); }
.role-badge.OPERATOR { background: rgba(0,0,0,0.04); color: #4a5c6c; }

.status-badge { padding: 3px 10px; border-radius: 10px; font-size: 11px; font-weight: 500; }
.status-badge.active { background: var(--success-bg); color: var(--success); }
.status-badge.disabled { background: var(--error-bg); color: var(--error); }

/* Actions */
.actions { display: flex; gap: 5px; }
.actions button {
  padding: 4px 12px; border: 1px solid var(--border-strong);
  background: transparent; border-radius: 2px;
  cursor: pointer; font-size: 12px; transition: all 0.2s;
  color: #4a5c6c; font-family: var(--font-body);
}
.actions button:hover { border-color: #38bdf8; color: #38bdf8; background: rgba(56,189,248,0.04); }
.actions .btn-warn { color: var(--warning); border-color: rgba(245,158,11,0.2); }
.actions .btn-warn:hover { background: var(--warning-bg); }
.actions .btn-danger { color: var(--error); border-color: rgba(239,68,68,0.2); }
.actions .btn-danger:hover { background: var(--error-bg); }

.empty-hint { text-align: center; padding: 40px; color: #8899a6; font-size: 14px; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.3);
  backdrop-filter: blur(4px); -webkit-backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
  animation: fadeIn 0.2s ease;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.modal-card {
  background: #fff; border: 1px solid #e4e8ec;
  padding: 28px; border-radius: 4px; min-width: 340px;
  box-shadow: var(--shadow-md);
  animation: modalIn 0.3s ease;
}
@keyframes modalIn {
  from { opacity: 0; transform: translateY(12px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.modal-card h4 { margin-bottom: 8px; font-size: 16px; color: var(--text); }
.modal-user { font-size: 14px; color: #4a5c6c; margin-bottom: 18px; }
.modal-card select {
  width: 100%; padding: 10px 14px; background: #fff;
  border: 1px solid #e4e8ec; border-radius: 2px;
  font-size: 14px; color: var(--text); outline: none;
  margin-bottom: 18px; font-family: var(--font-body); cursor: pointer;
}
.modal-card select option { background: #fff; color: var(--text); }
.modal-card select:focus { border-color: #38bdf8; box-shadow: 0 0 0 3px rgba(56,189,248,0.1); }
.modal-btns { display: flex; gap: 10px; justify-content: flex-end; }
.btn-cancel {
  padding: 8px 20px; border: 1px solid var(--border-strong);
  background: transparent; border-radius: 2px;
  cursor: pointer; font-size: 14px; color: #4a5c6c;
  transition: all 0.2s; font-family: var(--font-body);
}
.btn-cancel:hover { background: rgba(0,0,0,0.03); }
.btn-confirm {
  padding: 8px 20px; background: #1e2d3d;
  color: #fff; border: none; border-radius: 2px;
  cursor: pointer; font-size: 14px; font-weight: 500;
  transition: all 0.2s; font-family: var(--font-body);
}
.btn-confirm:hover { background: #243447; box-shadow: 0 4px 20px rgba(30,45,61,0.25); }
</style>
