<template>
  <div class="kb-config-manage">
    <div class="page-title-row">
      <h1>知识配置</h1>
      <span class="page-subtitle">DMN 场景、寒暄话术与会话模板</span>
    </div>

    <div class="info-banner">
      在此维护 DMN 场景、寒暄、一键服务、关联 FAQ、人工路由等运营配置。修改后部分项需点击「热加载」或重启相关缓存后在前台生效。
    </div>

    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        :class="['tab', { active: activeTab === t.id }]"
        @click="switchTab(t.id)"
      >{{ t.label }}</button>
    </div>

    <!-- DMN 场景 -->
    <div v-if="activeTab === 'scenes'" class="section">
      <div class="section-header">
        <h3>DMN 规则场景</h3>
        <button class="btn btn-outline btn-sm" @click="loadRuleScenes">刷新</button>
      </div>
      <div v-if="loading.scenes" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>场景码</th><th>名称</th><th>DMN 路径</th><th>优先级 DMN</th><th>启用</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in ruleScenes" :key="row.id">
            <td><code>{{ row.sceneCode }}</code></td>
            <td><input v-model="row.sceneName" class="text-input" /></td>
            <td><input v-model="row.dmnResourcePath" class="text-input wide" /></td>
            <td><input v-model="row.priorityDmnPath" class="text-input wide" /></td>
            <td><input v-model.number="row.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveRuleScene(row)" :disabled="row._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 规则版本 -->
    <div v-if="activeTab === 'versions'" class="section">
      <div class="section-header">
        <h3>规则版本历史</h3>
        <div class="filter-row">
          <select v-model="versionSceneFilter" @change="loadRuleVersions">
            <option value="">全部场景</option>
            <option v-for="s in ruleScenes" :key="s.sceneCode" :value="s.sceneCode">{{ s.sceneName || s.sceneCode }}</option>
          </select>
          <button class="btn btn-outline btn-sm" @click="loadRuleVersions">刷新</button>
        </div>
      </div>
      <div v-if="loading.versions" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>场景</th><th>版本</th><th>部署 ID</th><th>发布人</th><th>发布时间</th><th>状态</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in ruleVersions" :key="v.id">
            <td><code>{{ v.sceneCode }}</code></td>
            <td>{{ v.version }}</td>
            <td class="mono clip">{{ v.dmnDeployId || '—' }}</td>
            <td>{{ v.publishedBy || '—' }}</td>
            <td>{{ formatTime(v.publishedAt) }}</td>
            <td>{{ v.status || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading.versions && ruleVersions.length === 0" class="empty-hint">暂无版本记录</p>
    </div>

    <!-- 寒暄 -->
    <div v-if="activeTab === 'chitchat'" class="section">
      <div class="section-header">
        <h3>寒暄配置</h3>
        <div class="filter-row">
          <button class="btn btn-outline btn-sm" @click="loadChitchat">刷新</button>
          <button class="btn btn-primary btn-sm" @click="reloadChitchat">热加载</button>
        </div>
      </div>

      <h4 class="sub-heading">分类</h4>
      <table class="data-table compact">
        <thead><tr><th>分类码</th><th>名称</th><th>启用</th></tr></thead>
        <tbody>
          <tr v-for="c in chitchatCategories" :key="c.id">
            <td><code>{{ c.categoryCode }}</code></td>
            <td>{{ c.categoryName }}</td>
            <td>{{ c.enabled ? '是' : '否' }}</td>
          </tr>
        </tbody>
      </table>

      <h4 class="sub-heading">匹配模式</h4>
      <div v-if="loading.chitchat" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr><th>分类</th><th>模式</th><th>匹配类型</th><th>启用</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="p in chitchatPatterns" :key="p.id">
            <td><code>{{ p.categoryCode }}</code></td>
            <td><input v-model="p.pattern" class="text-input wide" /></td>
            <td><input v-model="p.matchType" class="text-input compact" /></td>
            <td><input v-model.number="p.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveChitchatPattern(p)" :disabled="p._saving">保存</button></td>
          </tr>
        </tbody>
      </table>

      <h4 class="sub-heading">回复模板</h4>
      <table class="data-table">
        <thead>
          <tr><th>分类</th><th>会话阶段</th><th>模板</th><th>FAQ 芯片</th><th>启用</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="r in chitchatReplies" :key="r.id">
            <td><code>{{ r.categoryCode }}</code></td>
            <td><input v-model="r.sessionPhase" class="text-input compact" /></td>
            <td><textarea v-model="r.textTemplate" class="text-input template-area" rows="2" /></td>
            <td><input v-model.number="r.appendFaqChips" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><input v-model.number="r.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveChitchatReply(r)" :disabled="r._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 一键服务 -->
    <div v-if="activeTab === 'quick'" class="section">
      <div class="section-header">
        <h3>一键服务入口</h3>
        <div class="filter-row">
          <button v-if="auth.canManage" class="btn btn-primary btn-sm" @click="addQuickEntry">新增</button>
          <button class="btn btn-outline btn-sm" @click="loadQuickEntries">刷新</button>
        </div>
      </div>
      <div v-if="loading.quick" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>排序</th><th>意图</th><th>标签</th><th>图标</th><th>入口消息</th><th>启用</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in quickEntries" :key="row.id">
            <td><input v-model.number="row.sortOrder" type="number" class="text-input compact" /></td>
            <td><input v-model="row.intentCode" class="text-input" /></td>
            <td><input v-model="row.label" class="text-input" /></td>
            <td><input v-model="row.icon" class="text-input compact" /></td>
            <td><input v-model="row.entryMessage" class="text-input wide" /></td>
            <td><input v-model.number="row.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveQuickEntry(row)" :disabled="row._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
      <p v-if="!loading.quick && quickEntries.length === 0" class="empty-hint">暂无入口，可点击「新增」添加移动端快捷服务按钮</p>
    </div>

    <!-- 人工路由 -->
    <div v-if="activeTab === 'human'" class="section">
      <div class="section-header">
        <h3>人工客服路由</h3>
        <button class="btn btn-outline btn-sm" @click="loadHumanRoutes">刷新</button>
      </div>
      <div v-if="loading.human" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>路由码</th><th>意图匹配</th><th>渠道路径</th><th>优先级</th><th>启用</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in humanRoutes" :key="row.id">
            <td><code>{{ row.routeCode }}</code></td>
            <td><input v-model="row.intentMatch" class="text-input wide" /></td>
            <td><input v-model="row.channelPath" class="text-input wide" /></td>
            <td><input v-model.number="row.priority" type="number" class="text-input compact" /></td>
            <td><input v-model.number="row.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveHumanRoute(row)" :disabled="row._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 说法 → 意图（原 FAQ 别名 + 意图关键词已合并） -->
    <div v-if="activeTab === 'aliases'" class="section">
      <div class="section-header">
        <h3>说法与意图（FAQ 别名）</h3>
        <div class="filter-row">
          <button class="btn btn-outline btn-sm" @click="loadFaqAliases">刷新</button>
          <button class="btn btn-primary btn-sm" @click="reloadFaqAliases">热加载</button>
          <button class="btn btn-outline btn-sm" @click="addFaqAlias">新增</button>
        </div>
      </div>
      <p class="empty-hint">一行一条用户说法，匹配类型建议用 CONTAINS（子串）。复合句识别仅依赖本表 + 分句连接词。</p>
      <div v-if="loading.aliases" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>别名</th><th>意图</th><th>FAQ ID</th><th>匹配类型</th><th>启用</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in faqAliases" :key="row.id">
            <td><input v-model="row.aliasText" class="text-input" /></td>
            <td><input v-model="row.intentCode" class="text-input" /></td>
            <td><input v-model="row.faqId" class="text-input compact" /></td>
            <td><input v-model="row.matchType" class="text-input compact" /></td>
            <td><input v-model.number="row.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveFaqAlias(row)" :disabled="row._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 系统动作 -->
    <div v-if="activeTab === 'actions'" class="section">
      <div class="section-header">
        <h3>系统动作模板</h3>
        <div class="filter-row">
          <button class="btn btn-outline btn-sm" @click="loadActionTemplates">刷新</button>
          <button class="btn btn-primary btn-sm" @click="reloadActionTemplates">热加载</button>
        </div>
      </div>
      <div v-if="loading.actions" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr><th>动作码</th><th>名称</th><th>成功提示</th><th>排序</th><th>启用</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="row in actionTemplates" :key="row.id">
            <td><code>{{ row.actionCode }}</code></td>
            <td><input v-model="row.actionName" class="text-input" /></td>
            <td><input v-model="row.successMessage" class="text-input wide" /></td>
            <td><input v-model.number="row.sortOrder" type="number" class="text-input compact" /></td>
            <td><input v-model.number="row.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveActionTemplate(row)" :disabled="row._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 会话模板 -->
    <div v-if="activeTab === 'templates'" class="section">
      <div class="section-header">
        <h3>会话模板</h3>
        <button class="btn btn-outline btn-sm" @click="loadSessionTemplates">刷新</button>
      </div>
      <div v-if="loading.templates" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr><th>模板码</th><th>文本模板</th><th>版本</th><th>启用</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="t in sessionTemplates" :key="t.id">
            <td><code>{{ t.templateCode }}</code></td>
            <td><textarea v-model="t.textTemplate" class="text-input template-area" rows="2" /></td>
            <td><input v-model="t.version" class="text-input compact" /></td>
            <td><input v-model.number="t.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveSessionTemplate(t)" :disabled="t._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分句连接词 -->
    <div v-if="activeTab === 'connectors'" class="section">
      <div class="section-header">
        <h3>分句连接词</h3>
        <div class="filter-row">
          <button class="btn btn-outline btn-sm" @click="loadUtteranceConnectors">刷新</button>
          <button class="btn btn-primary btn-sm" @click="reloadUtteranceConnectors">热加载</button>
          <button class="btn btn-outline btn-sm" @click="addUtteranceConnector">新增</button>
        </div>
      </div>
      <p class="empty-hint">用于复合句切分（如「还有」「顺便」）。保存后请点击热加载。</p>
      <div v-if="loading.connectors" class="empty-hint">加载中...</div>
      <table v-else class="data-table">
        <thead>
          <tr><th>连接词</th><th>排序</th><th>启用</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="c in utteranceConnectors" :key="c.id">
            <td><input v-model="c.connectorText" class="text-input wide" /></td>
            <td><input v-model.number="c.sortOrder" type="number" class="text-input compact" /></td>
            <td><input v-model.number="c.enabled" type="checkbox" :true-value="1" :false-value="0" /></td>
            <td><button class="action-btn edit" @click="saveUtteranceConnector(c)" :disabled="c._saving">保存</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { adminApi } from '@/api/index'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useToast } from '@/stores/toast'

const toast = useToast()
const auth = useAdminAuthStore()
const route = useRoute()

const loadedTabs = reactive(new Set(['scenes']))

const tabs = [
  { id: 'scenes', label: 'DMN 场景' },
  { id: 'versions', label: '规则版本' },
  { id: 'chitchat', label: '寒暄' },
  { id: 'quick', label: '一键服务' },
  { id: 'human', label: '人工路由' },
  { id: 'aliases', label: '说法与意图' },
  { id: 'actions', label: '系统动作' },
  { id: 'templates', label: '会话模板' },
  { id: 'connectors', label: '分句连接词' }
]
const activeTab = ref('scenes')

const ruleScenes = ref([])
const ruleVersions = ref([])
const versionSceneFilter = ref('')
const chitchatCategories = ref([])
const chitchatPatterns = ref([])
const chitchatReplies = ref([])
const quickEntries = ref([])
const humanRoutes = ref([])
const faqAliases = ref([])
const actionTemplates = ref([])
const sessionTemplates = ref([])
const utteranceConnectors = ref([])

const loading = reactive({
  scenes: false,
  versions: false,
  chitchat: false,
  quick: false,
  human: false,
  aliases: false,
  actions: false,
  templates: false,
  connectors: false
})

function formatTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

async function loadRuleScenes() {
  loading.scenes = true
  try {
    const { data } = await adminApi.listKbRuleScenes()
    ruleScenes.value = data
  } catch (e) {
    toast.error('加载 DMN 场景失败')
  } finally {
    loading.scenes = false
  }
}

async function saveRuleScene(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateKbRuleScene(row.id, {
      sceneName: row.sceneName,
      dmnResourcePath: row.dmnResourcePath,
      priorityDmnPath: row.priorityDmnPath,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('场景已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function loadRuleVersions() {
  loading.versions = true
  try {
    const params = versionSceneFilter.value ? { sceneCode: versionSceneFilter.value } : {}
    const { data } = await adminApi.listKbRuleVersions(params)
    ruleVersions.value = data
  } catch {
    toast.error('加载规则版本失败')
  } finally {
    loading.versions = false
  }
}

async function loadChitchat() {
  loading.chitchat = true
  try {
    const [cat, pat, rep] = await Promise.all([
      adminApi.listChitchatCategories(),
      adminApi.listChitchatPatterns(),
      adminApi.listChitchatReplies()
    ])
    chitchatCategories.value = cat.data
    chitchatPatterns.value = pat.data
    chitchatReplies.value = rep.data
  } catch {
    toast.error('加载寒暄配置失败')
  } finally {
    loading.chitchat = false
  }
}

async function saveChitchatPattern(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateChitchatPattern(row.id, {
      pattern: row.pattern,
      matchType: row.matchType,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('模式已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function saveChitchatReply(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateChitchatReply(row.id, {
      sessionPhase: row.sessionPhase,
      textTemplate: row.textTemplate,
      appendFaqChips: row.appendFaqChips,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('回复已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function reloadChitchat() {
  try {
    await adminApi.reloadChitchat()
    toast.success('寒暄规则已热加载')
  } catch {
    toast.error('热加载失败')
  }
}

async function loadQuickEntries() {
  loading.quick = true
  try {
    const { data } = await adminApi.listQuickEntries()
    quickEntries.value = data
  } catch {
    toast.error('加载一键服务失败')
  } finally {
    loading.quick = false
  }
}

async function addQuickEntry() {
  if (!auth.canManage) return
  try {
    const { data } = await adminApi.createQuickEntry({
      intentCode: 'GENERAL_CHAT',
      label: '新入口',
      icon: '💬',
      entryMessage: '',
      sortOrder: (quickEntries.value.length + 1) * 10,
      enabled: 1
    })
    quickEntries.value.push({ ...data, _saving: false })
    toast.success('已新增入口，请编辑后保存')
  } catch {
    toast.error('新增失败')
  }
}

async function saveQuickEntry(row) {
  if (!row.id) return
  row._saving = true
  try {
    const { data } = await adminApi.updateQuickEntry(row.id, {
      intentCode: row.intentCode,
      label: row.label,
      icon: row.icon,
      entryMessage: row.entryMessage,
      sortOrder: row.sortOrder,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('入口已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function loadHumanRoutes() {
  loading.human = true
  try {
    const { data } = await adminApi.listHumanRoutes()
    humanRoutes.value = data
  } catch {
    toast.error('加载人工路由失败')
  } finally {
    loading.human = false
  }
}

async function saveHumanRoute(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateHumanRoute(row.id, {
      intentMatch: row.intentMatch,
      channelPath: row.channelPath,
      priority: row.priority,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('人工路由已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function loadFaqAliases() {
  loading.aliases = true
  try {
    const { data } = await adminApi.listFaqAliases()
    faqAliases.value = data
  } catch {
    toast.error('加载 FAQ 别名失败')
  } finally {
    loading.aliases = false
  }
}

async function saveFaqAlias(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateFaqAlias(row.id, {
      aliasText: row.aliasText,
      intentCode: row.intentCode,
      faqId: row.faqId,
      matchType: row.matchType,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('别名已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function reloadFaqAliases() {
  try {
    await adminApi.reloadFaqAliases()
    toast.success('FAQ 别名已热加载')
  } catch {
    toast.error('热加载失败')
  }
}

async function addFaqAlias() {
  try {
    const { data } = await adminApi.createFaqAlias({
      aliasText: '新说法',
      intentCode: '其他问题',
      matchType: 'CONTAINS',
      enabled: 1
    })
    faqAliases.value.push({ ...data, _saving: false })
    toast.success('已新增，请修改说法与意图后保存')
  } catch {
    toast.error('新增失败')
  }
}

async function loadActionTemplates() {
  loading.actions = true
  try {
    const { data } = await adminApi.listActionTemplates()
    actionTemplates.value = data
  } catch {
    toast.error('加载系统动作失败')
  } finally {
    loading.actions = false
  }
}

async function saveActionTemplate(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateActionTemplate(row.id, {
      actionName: row.actionName,
      successMessage: row.successMessage,
      sortOrder: row.sortOrder,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('动作模板已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function reloadActionTemplates() {
  try {
    await adminApi.reloadActionTemplates()
    toast.success('系统动作已热加载')
  } catch {
    toast.error('热加载失败')
  }
}

async function loadUtteranceConnectors() {
  loading.connectors = true
  try {
    const { data } = await adminApi.listUtteranceConnectors()
    utteranceConnectors.value = data
  } catch {
    toast.error('加载分句连接词失败')
  } finally {
    loading.connectors = false
  }
}

async function saveUtteranceConnector(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateUtteranceConnector(row.id, {
      connectorText: row.connectorText,
      sortOrder: row.sortOrder,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('连接词已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

async function addUtteranceConnector() {
  try {
    const { data } = await adminApi.createUtteranceConnector({
      connectorText: '新连接词',
      sortOrder: 100,
      enabled: 1
    })
    utteranceConnectors.value.push(data)
    toast.success('已新增，请修改文案后保存')
  } catch {
    toast.error('新增失败')
  }
}

async function reloadUtteranceConnectors() {
  try {
    await adminApi.reloadUtteranceConnectors()
    toast.success('分句连接词已热加载')
  } catch {
    toast.error('热加载失败')
  }
}

async function loadSessionTemplates() {
  loading.templates = true
  try {
    const { data } = await adminApi.listSessionTemplates()
    sessionTemplates.value = data
  } catch {
    toast.error('加载会话模板失败')
  } finally {
    loading.templates = false
  }
}

async function saveSessionTemplate(row) {
  row._saving = true
  try {
    const { data } = await adminApi.updateSessionTemplate(row.id, {
      textTemplate: row.textTemplate,
      version: row.version,
      enabled: row.enabled
    })
    Object.assign(row, data)
    toast.success('模板已保存')
  } catch {
    toast.error('保存失败')
  } finally {
    row._saving = false
  }
}

function switchTab(id) {
  activeTab.value = id
}

async function loadTabData(id) {
  switch (id) {
    case 'scenes': await loadRuleScenes(); break
    case 'versions': await loadRuleVersions(); break
    case 'chitchat': await loadChitchat(); break
    case 'quick': await loadQuickEntries(); break
    case 'human': await loadHumanRoutes(); break
    case 'aliases': await loadFaqAliases(); break
    case 'actions': await loadActionTemplates(); break
    case 'templates': await loadSessionTemplates(); break
    case 'connectors': await loadUtteranceConnectors(); break
  }
}

watch(activeTab, async (id) => {
  if (loadedTabs.has(id)) return
  loadedTabs.add(id)
  await loadTabData(id)
})

onMounted(async () => {
  const tab = route.query.tab
  if (typeof tab === 'string' && tabs.some(t => t.id === tab)) {
    activeTab.value = tab
    loadedTabs.add(tab)
    await loadTabData(tab)
  } else {
    await loadRuleScenes()
  }
})
</script>

<style scoped>
.kb-config-manage { max-width: 1200px; }

.page-title-row { margin-bottom: 20px; }
.page-title-row h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; }
.page-subtitle { color: #64748b; font-size: 13px; }

.info-banner {
  background: #f0fdfa; border: 1px solid #99f6e4; color: #0f766e;
  padding: 10px 14px; border-radius: 6px; font-size: 13px; margin-bottom: 14px;
}

.keyword-area { min-width: 240px; font-size: 12px; }

.tabs {
  display: flex; gap: 4px; margin-bottom: 16px;
  border-bottom: 1px solid #e2e8f0; padding-bottom: 0;
}
.tab {
  padding: 8px 16px; border: none; background: none; cursor: pointer;
  font-size: 13px; color: #64748b; border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.tab.active { color: #0f766e; border-bottom-color: #0f766e; font-weight: 500; }

.section {
  background: #fff; border: 1px solid #e2e8f0; border-radius: 8px;
  padding: 16px 20px; margin-bottom: 16px;
}
.section-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.section-header h3 { margin: 0; font-size: 15px; font-weight: 600; }
.sub-heading { font-size: 13px; font-weight: 600; margin: 16px 0 8px; color: #334155; }

.filter-row { display: flex; gap: 8px; align-items: center; }
.filter-row select {
  padding: 4px 8px; border: 1px solid #cbd5e1; border-radius: 4px; font-size: 13px;
}

.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th, .data-table td {
  padding: 8px 10px; text-align: left; border-bottom: 1px solid #f1f5f9;
}
.data-table th { color: #64748b; font-weight: 500; background: #f8fafc; }
.data-table.compact td { padding: 6px 10px; }

.text-input {
  width: 100%; padding: 4px 8px; border: 1px solid #cbd5e1;
  border-radius: 4px; font-size: 13px; box-sizing: border-box;
}
.text-input.wide { min-width: 180px; }
.text-input.compact { max-width: 100px; }
.template-area { min-width: 240px; resize: vertical; font-family: inherit; }

.btn { padding: 6px 12px; border-radius: 4px; font-size: 13px; cursor: pointer; border: 1px solid transparent; }
.btn-sm { padding: 4px 10px; font-size: 12px; }
.btn-outline { background: #fff; border-color: #cbd5e1; color: #334155; }
.btn-primary { background: #0f766e; color: #fff; }
.btn-outline:hover { background: #f8fafc; }

.action-btn {
  padding: 3px 10px; font-size: 12px; border-radius: 4px; cursor: pointer;
  border: 1px solid #cbd5e1; background: #fff;
}
.action-btn.edit { color: #0f766e; border-color: #99f6e4; }
.action-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.empty-hint { color: #94a3b8; font-size: 13px; padding: 12px 0; }
.mono { font-family: ui-monospace, monospace; font-size: 12px; }
.clip { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
code { font-size: 12px; background: #f1f5f9; padding: 1px 5px; border-radius: 3px; }
</style>
