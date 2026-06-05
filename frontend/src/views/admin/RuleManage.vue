<template>
  <div class="rule-manage">
    <div class="page-title-row">
      <h1>知识分支</h1>
      <span class="page-subtitle">V2：意图 + kb_branch（condition_json）+ Excel 导入；话术在 kb_reply_script</span>
    </div>

    <div class="info-banner">
      旧版 Playbook / kb_rule 已移除。用户说法 → 意图请在「知识配置 → 说法与意图」维护；分句连接词同页；本页仅管理意图与 kb_branch。
    </div>

    <!-- Excel 导入 -->
    <div class="section">
      <div class="section-header">
        <h3>Excel 批量导入分支</h3>
        <div class="excel-toolbar">
          <button type="button" class="btn btn-outline btn-sm" @click="downloadTemplate">下载模板</button>
          <button type="button" class="btn btn-outline btn-sm" @click="exportBranches" :disabled="exporting">导出全部分支</button>
          <button class="btn-refresh" @click="loadTree">刷新列表</button>
        </div>
      </div>
      <form @submit.prevent="handleImport" class="upload-form">
        <div class="form-row">
          <label :class="['file-input-label', { 'has-file': selectedFile }]">
            <span v-if="selectedFile" class="file-name">{{ selectedFile.name }}</span>
            <span v-else class="file-placeholder">选择 Excel 文件 (.xlsx)</span>
            <input type="file" @change="onFileChange" accept=".xlsx" />
            <button v-if="selectedFile" type="button" class="file-clear" @click.stop="clearFile">×</button>
          </label>
          <button type="submit" :disabled="importing || !selectedFile" :class="{ 'btn-ready': selectedFile }">
            {{ importing ? '导入中...' : '导入' }}
          </button>
        </div>
      </form>
      <div v-if="importResult" class="import-result">
        <h4 :class="importResult.success ? 'success' : 'error'">{{ importResult.success ? '导入成功' : '导入失败' }}</h4>
        <div v-if="importResult.success" class="result-stats">
          <div class="stat"><span class="stat-num">{{ importResult.branchesCreated ?? 0 }}</span> 分支</div>
          <div class="stat"><span class="stat-num">{{ importResult.skipped ?? 0 }}</span> 跳过</div>
        </div>
        <p v-if="importResult.message" class="result-msg">{{ importResult.message }}</p>
      </div>
    </div>

    <!-- 意图-分支树 -->
    <div class="section">
      <div class="section-header">
        <h3>意图与分支</h3>
      </div>
      <div v-if="loading" class="empty-hint">加载中...</div>
      <div v-else-if="tree.length === 0" class="empty-hint">暂无数据，请通过 Excel 导入</div>
      <div v-else class="tree-list">
        <div v-for="intent in tree" :key="intent.id" class="tree-intent">
          <div class="intent-header" @click="toggleIntent(intent.id)">
            <span class="toggle-icon">{{ expandedIntents.has(intent.id) ? '▼' : '▶' }}</span>
            <span class="intent-name">{{ intent.intentName }}</span>
            <code class="intent-code">{{ intent.intentCode }}</code>
            <span :class="['status-dot', intent.enabled ? 'on' : 'off']"></span>
            <span class="rule-count">{{ (intent.branches || []).length }} 分支</span>
            <span class="node-actions" @click.stop>
              <button class="action-btn edit" title="编辑意图" @click="openEditIntent(intent)">✎</button>
              <button class="action-btn add" title="添加分支" @click="openCreateBranch(intent)">+</button>
            </span>
          </div>
          <div v-if="expandedIntents.has(intent.id)" class="intent-rules">
            <div v-for="branch in intent.branches" :key="branch.id" class="tree-branch">
              <div class="rule-header">
                <span class="rule-name">{{ branch.branchName || branch.branchCode }}</span>
                <code class="branch-code">{{ branch.branchCode }}</code>
                <span class="rule-priority">P{{ branch.priority }}</span>
                <span :class="['status-dot', branch.enabled ? 'on' : 'off']"></span>
                <span class="node-actions">
                  <button class="action-btn edit" @click="openEditBranch(branch, intent.intentCode)">✎</button>
                  <button class="action-btn del" @click="confirmDeleteBranch(branch)">✕</button>
                </span>
              </div>
              <pre v-if="branch.conditionJson" class="cond-preview">{{ formatJson(branch.conditionJson) }}</pre>
              <span v-else class="cond-empty">无条件（默认命中）</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 意图弹窗 -->
    <div v-if="intentModal.visible" class="modal-overlay" @click.self="intentModal.visible = false">
      <div class="modal-card">
        <h4>编辑意图</h4>
        <div class="form-item">
          <label class="form-item-label">意图编码</label>
          <input v-model="intentModal.form.intentCode" class="text-input" disabled />
        </div>
        <div class="form-item">
          <label class="form-item-label required">意图名称</label>
          <input v-model="intentModal.form.intentName" class="text-input" />
        </div>
        <div class="form-item toggle-row">
          <label class="form-item-label">启用</label>
          <button type="button" :class="['toggle', { on: intentModal.form.enabled === 1 }]"
            @click="intentModal.form.enabled = intentModal.form.enabled === 1 ? 0 : 1">
            <span class="toggle-knob"></span>
          </button>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="intentModal.visible = false">取消</button>
          <button class="btn btn-primary" @click="saveIntent" :disabled="intentModal.saving">保存</button>
        </div>
      </div>
    </div>

    <!-- 分支弹窗 -->
    <div v-if="branchModal.visible" class="modal-overlay" @click.self="branchModal.visible = false">
      <div class="modal-card branch-modal">
        <h4>{{ branchModal.isEdit ? '编辑分支' : '新建分支' }}</h4>
        <div class="form-item">
          <label class="form-item-label">意图编码</label>
          <input v-model="branchModal.form.intentCode" class="text-input" :disabled="branchModal.isEdit" />
        </div>
        <div class="form-item">
          <label class="form-item-label required">分支编码</label>
          <input v-model="branchModal.form.branchCode" class="text-input" placeholder="如 NET_SIGNAL_WEAK" />
        </div>
        <div class="form-item">
          <label class="form-item-label required">分支名称</label>
          <input v-model="branchModal.form.branchName" class="text-input" />
        </div>
        <div class="form-item">
          <label class="form-item-label">优先级（越小越优先）</label>
          <input v-model.number="branchModal.form.priority" type="number" class="text-input" min="1" />
        </div>
        <div class="form-item">
          <label class="form-item-label">condition_json</label>
          <textarea v-model="branchModal.form.conditionJson" class="text-input expr-textarea" rows="6"
            placeholder='{"match":"AND","items":[{"field":"primaryRootCause","op":"eq","val":"SIGNAL_WEAK"}]}'></textarea>
          <p class="field-hint">匹配字段见 BranchConditionMatcher：primaryRootCause、intent_code、activeTask、dialogueStep、rnSlotsComplete 等</p>
        </div>
        <div class="form-item toggle-row">
          <label class="form-item-label">启用</label>
          <button type="button" :class="['toggle', { on: branchModal.form.enabled === 1 }]"
            @click="branchModal.form.enabled = branchModal.form.enabled === 1 ? 0 : 1">
            <span class="toggle-knob"></span>
          </button>
        </div>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="branchModal.visible = false">取消</button>
          <button class="btn btn-primary" @click="saveBranch" :disabled="branchModal.saving">保存</button>
        </div>
      </div>
    </div>

    <!-- 删除确认 -->
    <div v-if="deleteModal.visible" class="modal-overlay" @click.self="deleteModal.visible = false">
      <div class="modal-card confirm-modal">
        <h4>确认删除分支</h4>
        <p class="confirm-text">删除「{{ deleteModal.name }}」？关联话术脚本需另行清理。</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="deleteModal.visible = false">取消</button>
          <button class="btn btn-danger" @click="doDeleteBranch" :disabled="deleteModal.deleting">删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { adminApi } from '@/api'

const loading = ref(false)
const tree = ref([])
const expandedIntents = reactive(new Set())

const exporting = ref(false)

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function downloadTemplate() {
  try {
    const res = await adminApi.downloadBranchTemplate()
    downloadBlob(res.data, 'kb_branch_template.xlsx')
  } catch (e) {
    alert('下载模板失败: ' + (e.message || e))
  }
}

async function exportBranches() {
  exporting.value = true
  try {
    const res = await adminApi.exportBranchesExcel()
    downloadBlob(res.data, 'kb_branch_export.xlsx')
  } catch (e) {
    alert('导出失败: ' + (e.message || e))
  } finally {
    exporting.value = false
  }
}

const selectedFile = ref(null)
const importing = ref(false)
const importResult = ref(null)

function onFileChange(e) {
  const input = e.target
  if (input.files?.length) selectedFile.value = input.files[0]
}

function clearFile() {
  selectedFile.value = null
}

async function handleImport() {
  if (!selectedFile.value) return
  importing.value = true
  importResult.value = null
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    const res = await adminApi.importExcel(fd)
    importResult.value = { ...res.data, success: res.data.success !== false }
    if (res.data.success !== false) {
      clearFile()
      await loadTree()
    }
  } catch (e) {
    importResult.value = { success: false, message: e.message || '导入失败' }
  } finally {
    importing.value = false
  }
}

async function loadTree() {
  loading.value = true
  try {
    const res = await adminApi.getRuleTree()
    tree.value = res.data || []
  } finally {
    loading.value = false
  }
}

function toggleIntent(id) {
  if (expandedIntents.has(id)) expandedIntents.delete(id)
  else expandedIntents.add(id)
}

function formatJson(raw) {
  if (!raw) return ''
  try {
    return JSON.stringify(typeof raw === 'string' ? JSON.parse(raw) : raw, null, 2)
  } catch {
    return String(raw)
  }
}

const intentModal = reactive({
  visible: false,
  saving: false,
  editId: 0,
  form: { intentCode: '', intentName: '', enabled: 1 }
})

function openEditIntent(intent) {
  intentModal.visible = true
  intentModal.editId = intent.id
  intentModal.form = {
    intentCode: intent.intentCode,
    intentName: intent.intentName,
    enabled: intent.enabled ?? 1
  }
}

async function saveIntent() {
  if (!intentModal.form.intentName) {
    alert('意图名称不能为空')
    return
  }
  intentModal.saving = true
  try {
    await adminApi.updateIntent(intentModal.editId, intentModal.form)
    intentModal.visible = false
    await loadTree()
  } catch (e) {
    alert('保存失败: ' + (e.message || e))
  } finally {
    intentModal.saving = false
  }
}

const branchModal = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  editId: 0,
  form: {
    intentCode: '',
    branchCode: '',
    branchName: '',
    priority: 100,
    conditionJson: '',
    enabled: 1
  }
})

function openCreateBranch(intent) {
  branchModal.visible = true
  branchModal.isEdit = false
  branchModal.editId = 0
  branchModal.form = {
    intentCode: intent.intentCode,
    branchCode: '',
    branchName: '',
    priority: 100,
    conditionJson: '',
    enabled: 1
  }
}

function openEditBranch(branch, intentCode) {
  branchModal.visible = true
  branchModal.isEdit = true
  branchModal.editId = branch.id
  branchModal.form = {
    intentCode: branch.intentCode || intentCode,
    branchCode: branch.branchCode || '',
    branchName: branch.branchName || '',
    priority: branch.priority ?? 100,
    conditionJson: branch.conditionJson || '',
    enabled: branch.enabled ?? 1
  }
}

async function saveBranch() {
  const f = branchModal.form
  if (!f.intentCode || !f.branchCode || !f.branchName) {
    alert('意图、分支编码、名称不能为空')
    return
  }
  if (f.conditionJson?.trim()) {
    try {
      JSON.parse(f.conditionJson)
    } catch {
      alert('condition_json 不是合法 JSON')
      return
    }
  }
  branchModal.saving = true
  const payload = { ...f }
  try {
    if (branchModal.isEdit) {
      await adminApi.updateBranch(branchModal.editId, payload)
    } else {
      await adminApi.createBranch(payload)
    }
    branchModal.visible = false
    await loadTree()
  } catch (e) {
    alert('保存失败: ' + (e.message || e))
  } finally {
    branchModal.saving = false
  }
}

const deleteModal = reactive({ visible: false, deleting: false, id: 0, name: '' })

function confirmDeleteBranch(branch) {
  deleteModal.visible = true
  deleteModal.id = branch.id
  deleteModal.name = branch.branchName || branch.branchCode
}

async function doDeleteBranch() {
  deleteModal.deleting = true
  try {
    await adminApi.deleteBranch(deleteModal.id)
    deleteModal.visible = false
    await loadTree()
  } catch (e) {
    alert('删除失败: ' + (e.message || e))
  } finally {
    deleteModal.deleting = false
  }
}

loadTree()
</script>

<style scoped>
.info-banner {
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  padding: 12px 16px;
  margin-bottom: 20px;
  font-size: 13px;
  color: #0369a1;
  border-radius: 2px;
}
.excel-toolbar { display: flex; gap: 8px; align-items: center; }
.btn-sm { padding: 6px 12px; font-size: 12px; }

.rule-manage { max-width: 1200px; }
.page-title-row { margin-bottom: 24px; }
.page-title-row h1 { font-size: 20px; margin: 0 0 4px; }
.page-subtitle { font-size: 13px; color: #8899a6; }
.section {
  background: #fff; border: 1px solid #e4e8ec; border-radius: 2px;
  padding: 20px 24px; margin-bottom: 20px;
}
.section h3 { font-size: 15px; margin: 0 0 14px; }
.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
.section-header h3 { margin: 0; }
.section-hint { font-size: 12px; color: #8899a6; }

.btn {
  padding: 8px 18px; border-radius: 2px; font-size: 13px;
  cursor: pointer; border: none;
}
.btn-primary { background: #38bdf8; color: #fff; }
.btn-outline { background: #fff; color: #4b5563; border: 1px solid #c8d2da; }
.btn-danger { background: #ef4444; color: #fff; }
.btn-refresh {
  background: none; border: 1px solid #c8d2da; padding: 5px 14px;
  font-size: 12px; cursor: pointer;
}

.upload-form { margin-bottom: 16px; }
.form-row { display: flex; gap: 12px; align-items: center; }
.file-input-label {
  flex: 1; display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border: 1px dashed #c8d2da; cursor: pointer; position: relative;
}
.file-input-label input { display: none; }
.file-clear { position: absolute; right: 8px; background: none; border: none; cursor: pointer; }
button[type="submit"] {
  padding: 10px 22px; background: #e4e8ec; border: none; border-radius: 2px; cursor: pointer;
}
button[type="submit"].btn-ready { background: #38bdf8; color: #fff; }

.import-result { margin-top: 14px; padding: 14px; background: #f8fafc; border: 1px solid #e4e8ec; }
.import-result h4.success { color: #16a34a; }
.import-result h4.error { color: #dc2626; }
.result-stats { display: flex; gap: 20px; }
.stat-num { font-weight: 600; font-size: 18px; }

.tree-list { margin-top: 8px; }
.intent-header {
  display: flex; align-items: center; gap: 8px; padding: 10px 12px; cursor: pointer;
  background: #f8fafc; border: 1px solid #e4e8ec;
}
.intent-header:hover { background: #eff6ff; }
.intent-name { font-weight: 500; }
.intent-code, .branch-code { font-size: 11px; color: #8899a6; font-family: monospace; }
.rule-count { font-size: 12px; color: #8899a6; margin-left: auto; }
.toggle-icon { font-size: 10px; width: 14px; color: #8899a6; }
.status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-dot.on { background: #22c55e; }
.status-dot.off { background: #c8d2da; }

.node-actions { display: flex; gap: 2px; margin-left: 8px; opacity: 0; }
.intent-header:hover .node-actions,
.rule-header:hover .node-actions { opacity: 1; }
.action-btn {
  width: 24px; height: 24px; border: none; background: none; cursor: pointer; font-size: 12px;
}
.action-btn.edit { color: #0369a1; }
.action-btn.add { color: #16a34a; }
.action-btn.del { color: #b91c1c; }

.intent-rules { margin-left: 20px; border-left: 2px solid #e4e8ec; padding-left: 12px; }
.tree-branch { margin-bottom: 8px; }
.rule-header {
  display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
  padding: 8px 10px; border: 1px solid #e4e8ec;
}
.rule-name { font-size: 13px; font-weight: 500; }
.rule-priority { font-size: 11px; color: #8899a6; }
.cond-preview {
  margin: 6px 0 0; padding: 8px; font-size: 11px;
  background: #f8fafc; border: 1px solid #e4e8ec; overflow-x: auto;
}
.cond-empty { font-size: 12px; color: #8899a6; margin-left: 10px; }
.empty-hint { font-size: 13px; color: #8899a6; padding: 16px 0; }

.modal-overlay {
  position: fixed; inset: 0; z-index: 1000; background: rgba(0,0,0,0.3);
  display: flex; align-items: flex-start; justify-content: center; padding-top: 80px;
}
.modal-card {
  background: #fff; padding: 28px 32px; width: 520px; max-width: 95vw;
  max-height: 80vh; overflow-y: auto; box-shadow: 0 8px 30px rgba(0,0,0,0.12);
}
.branch-modal { width: 640px; }
.confirm-modal { width: 400px; }
.modal-card h4 { margin: 0 0 20px; }
.form-item { margin-bottom: 16px; }
.form-item-label { display: block; font-size: 13px; margin-bottom: 6px; }
.form-item-label.required::after { content: ' *'; color: #dc2626; }
.text-input {
  width: 100%; padding: 9px 12px; border: 1px solid #c8d2da; box-sizing: border-box;
}
.expr-textarea { font-family: monospace; font-size: 12px; min-height: 100px; }
.field-hint { font-size: 11px; color: #8899a6; margin-top: 6px; }
.toggle-row { display: flex; justify-content: space-between; align-items: center; }
.toggle {
  width: 44px; height: 24px; border-radius: 12px; border: none; background: #c8d2da;
  cursor: pointer; position: relative;
}
.toggle.on { background: #38bdf8; }
.toggle-knob {
  position: absolute; top: 2px; left: 2px; width: 20px; height: 20px;
  border-radius: 50%; background: #fff; transition: transform 0.2s;
}
.toggle.on .toggle-knob { transform: translateX(20px); }
.modal-actions { display: flex; gap: 10px; justify-content: flex-end; margin-top: 20px; }
.confirm-text { font-size: 14px; }
</style>
