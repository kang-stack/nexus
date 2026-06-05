<template>
  <div class="document-manage">
    <div class="page-title-row">
      <h1>文档管理</h1>
      <span class="page-subtitle">管理知识库文档</span>
    </div>

    <div class="section">
      <h3>上传新文档</h3>
      <form @submit.prevent="handleUpload" class="upload-form">
        <div class="form-row">
          <input v-model="uploadForm.title" placeholder="文档标题" required />
          <select v-model="uploadForm.knowledgeBaseType" required>
            <option value="">选择知识库类型</option>
            <option value="DOCUMENT_SEARCH">文档搜索库</option>
            <option value="DATA_QUERY">数据查询库</option>
          </select>
        </div>
        <div class="form-row">
          <input v-model="uploadForm.description" placeholder="文档描述（选填）" />
          <input v-model="uploadForm.accessibleBy" placeholder="可见范围（选填）" />
        </div>
        <div class="form-row">
          <label :class="['file-input-label', { 'has-file': selectedFile }]">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 1.5v9M4.5 5L8 1.5 11.5 5M2 10.5v2a.5.5 0 00.5.5h11a.5.5 0 00.5-.5v-2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span v-if="selectedFile" class="file-name">{{ selectedFile.name }}</span>
            <span v-else class="file-placeholder">选择文件 (.pdf/.doc/.md/.txt/.xlsx/.csv)</span>
            <input type="file" @change="onFileChange" required accept=".pdf,.doc,.docx,.md,.txt,.xlsx,.csv" />
            <button v-if="selectedFile" type="button" class="file-clear" @click.stop="clearFile">x</button>
          </label>
          <button type="submit" :disabled="uploading || !selectedFile" :class="{ 'btn-ready': selectedFile }">
            {{ uploading ? '上传中...' : '上传文档' }}
          </button>
        </div>
      </form>
    </div>

    <div class="section">
      <div class="section-title-row">
        <div>
          <h3>FAQ Excel 导入</h3>
          <p class="section-hint">批量导入常见问题到知识库，列名支持「问题/答案」或 question/answer，导入后自动分段并触发向量化</p>
        </div>
        <button type="button" class="btn-link" @click="downloadFaqTemplate">下载模板</button>
      </div>
      <form @submit.prevent="handleFaqImport" class="upload-form">
        <div class="form-row">
          <label :class="['file-input-label', { 'has-file': faqFile }]">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 1.5v9M4.5 5L8 1.5 11.5 5M2 10.5v2a.5.5 0 00.5.5h11a.5.5 0 00.5-.5v-2" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span v-if="faqFile" class="file-name">{{ faqFile.name }}</span>
            <span v-else class="file-placeholder">选择 FAQ Excel 文件 (.xlsx)</span>
            <input type="file" @change="onFaqFileChange" accept=".xlsx" />
            <button v-if="faqFile" type="button" class="file-clear" @click.stop="clearFaqFile">x</button>
          </label>
          <button type="submit" :disabled="faqImporting || !faqFile" :class="{ 'btn-ready': faqFile }">
            {{ faqImporting ? '导入中...' : '导入 FAQ' }}
          </button>
        </div>
      </form>
    </div>

    <div class="section">
      <h3>文档列表</h3>
      <div v-if="loading" class="empty-hint">加载中...</div>
      <div v-else-if="documents.length > 0" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th><th>标题</th><th>上传者</th><th>状态</th><th>类型</th><th>创建时间</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="doc in documents" :key="doc.docId">
              <td class="mono">{{ String(doc.docId).substring(0, 10) }}…</td>
              <td>{{ doc.docTitle }}</td>
              <td>{{ doc.uploadUser }}</td>
              <td><span :class="['status', doc.status]">{{ doc.status }}</span></td>
              <td><span class="kb-type">{{ doc.knowledgeBaseType === 'DOCUMENT_SEARCH' ? '文档搜索' : '数据查询' }}</span></td>
              <td>{{ doc.createdAt?.substring(0, 10) }}</td>
              <td class="actions">
                <button v-if="doc.status === 'CONVERTED'" @click="doSplit(doc)">分段</button>
                <button v-if="doc.status === 'CHUNKED'" @click="doEmbedding(doc)">向量化</button>
                <button v-if="!['DISABLED','CONVERTING','SPLITTING','EMBEDDING'].includes(doc.status)" @click="doDisable(doc)" class="btn-warn">停用</button>
                <button @click="doDelete(doc)" class="btn-danger">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-else class="empty-hint">暂无文档，请上传</p>

      <Pagination :page-num="pageNum" :page-size="pageSize" :total="total" @change="handlePageChange" />
    </div>
    <!-- Split Modal -->
    <div v-if="splitModal.visible" class="modal-overlay" @click.self="cancelSplit">
      <div class="modal-card split-modal">
        <h4>分段设置</h4>
        <p class="modal-doc-name">{{ splitModal.docTitle }}</p>

        <!-- 切片方式 -->
        <div class="form-item">
          <label class="form-item-label required">切片方式</label>
          <div class="method-grid">
            <div
              v-for="m in splitMethods"
              :key="m.type"
              :class="['method-card', { selected: splitForm.splitType === m.type }]"
              @click="splitForm.splitType = m.type"
            >
              <div class="method-card-top">
                <span class="method-name">{{ m.label }}</span>
                <span :class="['method-radio', { checked: splitForm.splitType === m.type }]"></span>
              </div>
              <p class="method-desc">{{ m.description }}</p>
            </div>
          </div>
        </div>

        <!-- 最大分段长度 -->
        <div class="form-item">
          <label class="form-item-label required">最大分段长度</label>
          <div class="slider-input-row">
            <div class="slider-wrap">
              <input
                type="range"
                v-model.number="splitForm.chunkSize"
                min="10" max="6000" step="10"
                class="slider"
              />
              <div class="slider-marks">
                <span>10</span><span>6000</span>
              </div>
            </div>
            <input
              type="number"
              v-model.number="splitForm.chunkSize"
              min="10" max="6000"
              class="num-input"
            />
          </div>
        </div>

        <!-- 重叠字数 (hidden for SMART) -->
        <div v-if="splitForm.splitType !== 'SMART'" class="form-item">
          <label class="form-item-label">重叠字数</label>
          <div class="slider-input-row">
            <div class="slider-wrap">
              <input
                type="range"
                v-model.number="splitForm.overlap"
                min="0" :max="Math.max(0, splitForm.chunkSize - 1)"
                step="5"
                class="slider"
              />
              <div class="slider-marks">
                <span>0</span><span>{{ Math.max(0, splitForm.chunkSize - 1) }}</span>
              </div>
            </div>
            <input
              type="number"
              v-model.number="splitForm.overlap"
              min="0" :max="Math.max(0, splitForm.chunkSize - 1)"
              class="num-input"
            />
          </div>
        </div>
        <p v-else class="form-hint">智能切分将自动计算 10% 重叠，无需手动设置。</p>

        <!-- 条件字段：分隔符 -->
        <div v-if="splitForm.splitType === 'SEPARATOR'" class="form-item">
          <label class="form-item-label required">分隔符</label>
          <input
            v-model="splitForm.separator"
            placeholder="例如：\n\n 或 ---"
            class="text-input"
          />
        </div>

        <!-- 条件字段：正则表达式 -->
        <div v-if="splitForm.splitType === 'REGEX'" class="form-item">
          <label class="form-item-label required">正则表达式</label>
          <input
            v-model="splitForm.regex"
            placeholder="例如：第[一二三四五六七八九十]+章"
            class="text-input"
          />
        </div>

        <!-- Metadata抽取 Toggle -->
        <div class="form-item toggle-row">
          <label class="form-item-label">Metadata抽取</label>
          <button
            type="button"
            role="switch"
            :aria-checked="splitForm.metadataEnabled"
            :class="['toggle', { on: splitForm.metadataEnabled }]"
            @click="splitForm.metadataEnabled = !splitForm.metadataEnabled"
          >
            <span class="toggle-knob"></span>
          </button>
        </div>

        <!-- Excel表头拼装 Toggle -->
        <div class="form-item toggle-row">
          <label class="form-item-label">Excel表头拼装</label>
          <button
            type="button"
            role="switch"
            :aria-checked="splitForm.excelHeaderEnabled"
            :class="['toggle', { on: splitForm.excelHeaderEnabled }]"
            @click="splitForm.excelHeaderEnabled = !splitForm.excelHeaderEnabled"
          >
            <span class="toggle-knob"></span>
          </button>
        </div>

        <div class="modal-btns">
          <button class="btn-cancel" @click="cancelSplit">取消</button>
          <button class="btn-confirm" @click="confirmSplit" :disabled="splitting">
            {{ splitting ? '分段中...' : '开始分段' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, reactive, watch } from 'vue'
import { adminApi } from '@/api'
import { useToast } from '@/stores/toast'
import { useAdminAuthStore } from '@/stores/adminAuth'
import Pagination from '@/components/Pagination.vue'

const toast = useToast()
const auth = useAdminAuthStore()
const documents = ref([])
const loading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const uploadForm = ref({ title: '', description: '', accessibleBy: '', knowledgeBaseType: '' })
const selectedFile = ref(null)
const uploading = ref(false)
const faqFile = ref(null)
const faqImporting = ref(false)

// ---- Split Modal ----

const splitMethods = [
  { type: 'SMART', label: '智能切分', description: '在通用文档上的较优chunk切分方法，经过评测可在多数文档上获得较佳的检索效果' },
  { type: 'LENGTH', label: '按长度切分', description: '适合对 Token 数量有严格要求的场景，比如使用上下文长度较小的模型时。' },
  { type: 'TITLE', label: '按标题切分', description: '适合于用标题划分并传达独立主题的文档，要求不同级标题下的内容不会混杂在同一文本切片中。' },
  { type: 'BROTHER', label: '按兄弟标题切分', description: '按 Markdown 同级标题切分，同级标题下内容聚合为独立切片，保留层级兄弟关系。' },
  { type: 'REGEX', label: '按照正则切分', description: '依据设置的正则表达式，对知识库中的文本进行切分。' },
  { type: 'SEPARATOR', label: '按照符号切分', description: '适用于根据特定标识符区分内容的文档，根据文档文件中特殊标识符进行切分。' },
]

const splitModal = reactive({ visible: false, docId: 0, docTitle: '' })

const splitForm = reactive({
  splitType: 'SMART',
  chunkSize: 600,
  overlap: 50,
  separator: '',
  regex: '',
  metadataEnabled: false,
  excelHeaderEnabled: false,
})

const splitting = ref(false)

watch(() => splitForm.chunkSize, (newSize) => {
  if (splitForm.overlap >= newSize) {
    splitForm.overlap = Math.max(0, newSize - 1)
  }
})

onMounted(() => loadDocs())
onUnmounted(() => { if (pollingTimer) { clearInterval(pollingTimer); pollingTimer = null } })

// ---- auto-poll when any document is being processed ----
let pollingTimer = null

function checkAndPoll() {
  const hasProcessing = documents.value.some(d =>
    ['CONVERTING', 'SPLITTING', 'EMBEDDING'].includes(d.status)
  )
  if (hasProcessing && !pollingTimer) {
    pollingTimer = setInterval(loadDocs, 5000)
  } else if (!hasProcessing && pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

async function loadDocs() {
  loading.value = true
  try {
    const res = await adminApi.listDocuments(pageNum.value, pageSize.value)
    const data = res.data
    documents.value = data.records || []
    total.value = data.total || 0
    checkAndPoll()
  } catch { toast.error('加载文档列表失败') }
  finally { loading.value = false }
}
function handlePageChange(page) { pageNum.value = page; loadDocs() }
function onFileChange(e) { const input = e.target; selectedFile.value = input.files?.[0] || null }
function clearFile() { selectedFile.value = null }
function onFaqFileChange(e) { faqFile.value = e.target.files?.[0] || null }
function clearFaqFile() { faqFile.value = null }

function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  window.URL.revokeObjectURL(url)
}

async function downloadFaqTemplate() {
  try {
    const res = await adminApi.downloadFaqTemplate()
    downloadBlob(res.data, 'faq-template.xlsx')
  } catch (e) {
    toast.error('下载模板失败: ' + (e.response?.data?.message || e.message))
  }
}

async function handleFaqImport() {
  if (!faqFile.value) return
  faqImporting.value = true
  try {
    const fd = new FormData()
    fd.append('file', faqFile.value)
    fd.append('uploadUser', auth.username || 'admin')
    const res = await adminApi.importFaq(fd)
    const data = res.data
    if (data.success) {
      toast.success(`FAQ 导入成功：${data.segmentCount} 条，文档「${data.title}」`)
      faqFile.value = null
      pageNum.value = 1
      loadDocs()
    } else {
      toast.error(data.message || '导入失败')
    }
  } catch (e) {
    toast.error('FAQ 导入失败: ' + (e.response?.data?.message || e.message))
  } finally {
    faqImporting.value = false
  }
}

async function handleUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    fd.append('title', uploadForm.value.title)
    fd.append('description', uploadForm.value.description || '')
    fd.append('accessibleBy', uploadForm.value.accessibleBy || '')
    fd.append('knowledgeBaseType', uploadForm.value.knowledgeBaseType)
    fd.append('uploadUser', 'admin')
    await adminApi.uploadDocument(fd)
    toast.success('上传成功')
    uploadForm.value = { title: '', description: '', accessibleBy: '', knowledgeBaseType: '' }
    selectedFile.value = null; pageNum.value = 1; loadDocs()
  } catch (e) { toast.error('上传失败: ' + (e.response?.data?.message || e.message)) }
  finally { uploading.value = false }
}
function doSplit(doc) {
  splitModal.docId = doc.docId
  splitModal.docTitle = doc.docTitle
  splitModal.visible = true
  splitForm.splitType = 'SMART'
  splitForm.chunkSize = 600
  splitForm.overlap = 50
  splitForm.separator = ''
  splitForm.regex = ''
  splitForm.metadataEnabled = false
  splitForm.excelHeaderEnabled = false
}

function cancelSplit() {
  splitModal.visible = false
}

async function confirmSplit() {
  if (splitForm.splitType === 'SEPARATOR' && !splitForm.separator.trim()) {
    toast.error('请输入分隔符')
    return
  }
  if (splitForm.splitType === 'REGEX' && !splitForm.regex.trim()) {
    toast.error('请输入正则表达式')
    return
  }

  splitting.value = true
  try {
    const params = {
      splitType: splitForm.splitType,
      chunkSize: splitForm.chunkSize,
      overlap: splitForm.overlap,
    }
    if (splitForm.splitType === 'SEPARATOR') params.separator = splitForm.separator
    if (splitForm.splitType === 'REGEX') params.regex = splitForm.regex

    await adminApi.splitDocument(splitModal.docId, params)
    toast.success('分段任务已启动')
    cancelSplit()
    loadDocs()
  } catch (e) {
    toast.error('分段失败: ' + (e.response?.data?.message || e.message))
  } finally {
    splitting.value = false
  }
}

async function doEmbedding(doc) {
  try { await adminApi.embeddingDocument(doc.docId); toast.success('向量化任务已启动'); loadDocs() }
  catch (e) { toast.error('向量化失败: ' + e.message) }
}
async function doDisable(doc) {
  if (!confirm('确定停用该文档吗？')) return
  await adminApi.disableDocument(doc.docId); toast.success('文档已停用'); loadDocs()
}
async function doDelete(doc) {
  if (!confirm('确定删除该文档吗？')) return
  await adminApi.deleteDocument(doc.docId); toast.success('文档已删除'); loadDocs()
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
.section-title-row {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: 16px; margin-bottom: 14px;
}
.section-title-row h3 { margin-bottom: 4px; }
.section-hint { font-size: 12px; color: var(--text-muted); margin: 0; line-height: 1.5; }
.btn-link {
  flex-shrink: 0; padding: 6px 12px; border: 1px solid var(--border-strong);
  background: transparent; border-radius: 2px; cursor: pointer;
  font-size: 12px; color: #38bdf8; font-family: var(--font-body);
  transition: all 0.2s;
}
.btn-link:hover { background: rgba(56,189,248,0.06); border-color: #38bdf8; }

/* Upload form */
.upload-form .form-row { display: flex; gap: 10px; margin-bottom: 10px; }
.upload-form input, .upload-form select {
  flex: 1; padding: 10px 14px;
  background: #fff; border: 1px solid #e4e8ec;
  border-radius: 2px; font-size: 14px; color: var(--text);
  outline: none; transition: all 0.2s; font-family: var(--font-body);
}
.upload-form input::placeholder { color: #8899a6; }
.upload-form input:focus, .upload-form select:focus {
  border-color: #38bdf8; box-shadow: 0 0 0 3px rgba(56,189,248,0.1); background: #fff;
}
.upload-form select { color: var(--text); cursor: pointer; }
.upload-form select option { background: #fff; color: var(--text); }

.file-input-label {
  flex: 1; padding: 10px 14px;
  background: #fff; border: 1px dashed #bcc5cd;
  border-radius: 2px; font-size: 13px; color: #8899a6;
  cursor: pointer; transition: all 0.2s;
  display: flex; align-items: center; gap: 8px;
  overflow: hidden;
}
.file-input-label:hover { border-color: #38bdf8; color: #38bdf8; }
.file-input-label.has-file {
  border-color: #38bdf8; border-style: solid;
}
.file-input-label input[type="file"] { display: none; }
.file-placeholder { color: #8899a6; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-name {
  color: #38bdf8; font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  flex: 1;
}
.file-clear {
  flex-shrink: 0; width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center;
  border: none; border-radius: 50%;
  background: transparent; color: #8899a6;
  cursor: pointer; transition: all 0.15s;
  padding: 0; margin-left: auto;
}
.file-clear:hover { background: rgba(239,68,68,0.1); color: #ef4444; }

.upload-form button {
  padding: 10px 24px;
  background: #c4cdd4;
  color: #fff; border: none; border-radius: 2px;
  cursor: pointer; font-size: 14px; font-weight: 500;
  transition: all 0.2s; white-space: nowrap; font-family: var(--font-body);
}
.upload-form button.btn-ready {
  background: #1e2d3d; cursor: pointer;
}
.upload-form button.btn-ready:hover { background: #243447; box-shadow: 0 4px 20px rgba(30,45,61,0.25); }
.upload-form button:disabled { cursor: not-allowed; box-shadow: none; }

/* Table */
.table-wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--border); }
th {
  background: #f8f9fa; font-weight: 600; color: #4a5c6c;
  font-size: 11px; text-transform: uppercase; letter-spacing: 1px;
}
tr:hover td { background: #f8f9fa; }
.mono { font-family: 'SF Mono', 'Consolas', 'Courier New', monospace; font-size: 11px; color: #8899a6; }

/* Status badges */
.status { padding: 3px 10px; border-radius: 10px; font-size: 11px; font-weight: 500; }
.status.UPLOADED { background: var(--info-bg); color: var(--info); }
.status.CONVERTED { background: var(--success-bg); color: var(--success); }
.status.CHUNKED { background: var(--warning-bg); color: var(--warning); }
.status.VECTOR_STORED { background: var(--success-bg); color: var(--success); }
.status.DISABLED { background: var(--error-bg); color: var(--error); }
.status.CONVERTING,
.status.SPLITTING,
.status.EMBEDDING {
  background: var(--warning-bg); color: var(--warning);
  animation: processingPulse 1.5s ease-in-out infinite;
}
@keyframes processingPulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.55; }
}

.kb-type { padding: 2px 8px; border-radius: 8px; font-size: 11px; background: rgba(56,189,248,0.08); color: #38bdf8; }

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

.empty-hint { text-align: center; padding: 40px; color: var(--text-muted); font-size: 14px; }

/* ---- Split Modal ---- */
.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.3); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }

.modal-card {
  background: #fff; border: 1px solid #e4e8ec;
  padding: 28px; border-radius: 4px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.15);
  animation: modalIn 0.25s ease;
  max-height: 85vh; overflow-y: auto;
}
@keyframes modalIn {
  from { opacity: 0; transform: translateY(20px) scale(0.97); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.split-modal { width: 640px; max-width: 95vw; }

.modal-card h4 {
  font-family: var(--font-display); font-size: 20px;
  font-weight: 400; color: var(--text);
  margin-bottom: 4px; letter-spacing: 1px;
}
.modal-doc-name {
  font-size: 13px; color: var(--text-muted);
  margin-bottom: 24px;
}

/* Form items */
.form-item { margin-bottom: 20px; }
.form-item-label {
  display: block; margin-bottom: 8px;
  font-size: 13px; color: #4a5c6c; font-weight: 500;
}
.form-item-label.required::after {
  content: '*'; color: #ef4444; margin-left: 2px; font-size: 12px;
}
.form-hint {
  font-size: 12px; color: var(--text-muted);
  margin-top: -12px; margin-bottom: 16px;
}

/* Method card grid */
.method-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}
.method-card {
  border: 1px solid #e4e8ec;
  border-radius: 4px;
  padding: 14px 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafbfc;
  min-height: 90px;
}
.method-card:hover {
  border-color: #38bdf8;
  background: rgba(56,189,248,0.03);
}
.method-card.selected {
  border-color: #1e2d3d;
  background: rgba(30,45,61,0.04);
  box-shadow: 0 0 0 1px #1e2d3d;
}
.method-card-top {
  display: flex; align-items: center;
  justify-content: space-between; gap: 8px;
  margin-bottom: 6px;
}
.method-name { font-size: 13px; font-weight: 600; color: var(--text); }
.method-desc {
  font-size: 11px; color: #8899a6;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* Radio indicator */
.method-radio {
  width: 16px; height: 16px;
  border: 1.5px solid #bcc5cd;
  border-radius: 50%;
  flex-shrink: 0;
  transition: all 0.2s;
}
.method-radio.checked {
  border-color: #1e2d3d;
  border-width: 5px;
}

/* Slider + number input row */
.slider-input-row {
  display: flex; align-items: center; gap: 14px;
}
.slider-wrap { flex: 1; }
.slider {
  width: 100%; margin: 0;
  -webkit-appearance: none; appearance: none;
  height: 6px; background: #e4e8ec;
  border-radius: 3px; outline: none; cursor: pointer;
}
.slider::-webkit-slider-thumb {
  -webkit-appearance: none; appearance: none;
  width: 18px; height: 18px; border-radius: 50%;
  background: #1e2d3d; cursor: pointer; transition: background 0.2s;
}
.slider::-webkit-slider-thumb:hover { background: #243447; }
.slider::-moz-range-thumb {
  width: 18px; height: 18px; border-radius: 50%;
  background: #1e2d3d; cursor: pointer; border: none;
}
.slider-marks {
  display: flex; justify-content: space-between;
  font-size: 11px; color: #8899a6; margin-top: 2px;
}
.num-input {
  width: 80px; padding: 8px 10px;
  background: #fff; border: 1px solid #e4e8ec;
  border-radius: 2px; font-size: 14px; color: var(--text);
  outline: none; text-align: center; font-family: var(--font-body);
  transition: all 0.2s; box-sizing: border-box;
  -moz-appearance: textfield;
}
.num-input:focus {
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56,189,248,0.1);
}
.num-input::-webkit-inner-spin-button,
.num-input::-webkit-outer-spin-button { -webkit-appearance: none; margin: 0; }

/* Text input */
.text-input {
  width: 100%; padding: 10px 14px;
  background: #fff; border: 1px solid #e4e8ec;
  border-radius: 2px; font-size: 14px; color: var(--text);
  outline: none; font-family: var(--font-body);
  transition: all 0.2s; box-sizing: border-box;
}
.text-input:focus {
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56,189,248,0.1);
}
.text-input::placeholder { color: #8899a6; }

/* Toggle switch */
.toggle-row {
  display: flex; align-items: center;
  justify-content: space-between;
  padding: 8px 0;
}
.toggle {
  width: 40px; height: 22px;
  border-radius: 11px;
  border: none; cursor: pointer;
  background: #c4cdd4;
  position: relative; transition: background 0.2s;
  flex-shrink: 0;
}
.toggle.on { background: #1e2d3d; }
.toggle-knob {
  position: absolute; top: 2px; left: 2px;
  width: 18px; height: 18px; border-radius: 50%;
  background: #fff; transition: transform 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.toggle.on .toggle-knob { transform: translateX(18px); }

/* Modal buttons */
.modal-btns {
  display: flex; justify-content: flex-end; gap: 10px;
  margin-top: 28px; padding-top: 20px;
  border-top: 1px solid #e4e8ec;
}
.btn-cancel {
  padding: 10px 24px; background: transparent;
  border: 1px solid #e4e8ec; border-radius: 2px;
  cursor: pointer; font-size: 14px; color: #4a5c6c;
  font-family: var(--font-body); transition: all 0.2s;
}
.btn-cancel:hover { border-color: #bcc5cd; background: #f8f9fa; }
.btn-confirm {
  padding: 10px 28px; background: #1e2d3d;
  color: #fff; border: none; border-radius: 2px;
  cursor: pointer; font-size: 14px; font-weight: 500;
  font-family: var(--font-body); transition: all 0.2s;
}
.btn-confirm:hover { background: #243447; box-shadow: 0 4px 20px rgba(30,45,61,0.25); }
.btn-confirm:disabled { background: #c4cdd4; color: #fff; cursor: not-allowed; box-shadow: none; }
</style>
