<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import ProjectReadinessPanel from "../components/ProjectReadinessPanel.vue"
import {
  api,
  type EndpointEntrypointConfig,
  type FlowEntrypointPayload,
  type Project,
  type ProjectEndpoint,
} from "../api/client"
import { formatTimestamp, safeParseJson } from "../utils/formatters"

const route = useRoute()
const router = useRouter()

const projectKey = computed(() => route.params.projectKey as string)
const guide = computed(() => (typeof route.query.guide === "string" ? route.query.guide : ""))

const project = ref<Project | null>(null)
const projectLoading = ref(false)
const projectError = ref<string | null>(null)

const restEndpoints = ref<ProjectEndpoint[]>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)

const showCreate = ref(false)
const form = ref({ method: "GET", path: "", name: "", description: "" })

type EntrypointMeta = {
  enabled: boolean
  hasFlow: boolean
  flowCode: string | null
  raw: EndpointEntrypointConfig
}

type RestCard = {
  endpoint: ProjectEndpoint
  meta: EntrypointMeta
}

const restCards = computed<RestCard[]>(() =>
  restEndpoints.value.map((endpoint) => ({
    endpoint,
    meta: extractEntrypointMeta(endpoint),
  }))
)

const guideMessage = computed(() => {
  switch (guide.value) {
    case "metadata":
      return "当前最优先的是同步 metadata。只有 metadata 进来后，服务目录、模型和 resolver 才会完整，后面的装配提示才不会失真。"
    case "components":
      return "当前缺的是服务组件目录。先让 IDE 或后端把服务组件同步上来，否则服务节点只能手工输入，复杂补数场景会很难用。"
    case "create-rest":
      return "建议先补一个 REST 入口。这样用户至少能从入口列表进入托管编排，后续再继续绑定 Flow。"
    case "design-flow":
      return "入口已经有了，下一步建议尽快沉淀第一条 Flow，把入口、流程和发布链路真正接起来。"
    case "code-metadata":
      return "当前缺的是代码元数据。复杂对象、对象数组、集合和多接口补数都需要辅助类或依赖元数据来降低配置成本。"
    case "plugin":
      return "发布链路已经具备基础条件，下一步建议连接在线插件实例，把 Flow 自动同步回业务工程。"
    default:
      return ""
  }
})

const toggleBusy = ref<Record<number, boolean>>({})

function displayEndpointName(endpoint: ProjectEndpoint & Record<string, any>) {
  const raw =
    endpoint.displayName ??
    endpoint.alias ??
    endpoint.title ??
    endpoint.name ??
    ""

  if (typeof raw === "string" && raw.trim()) {
    if (raw.includes("#")) {
      const parts = raw.split("#")
      return parts[parts.length - 1] || raw
    }
    return raw.trim()
  }

  const path = endpoint.path || ""
  if (path) {
    const segments = path.split("/").filter(Boolean)
    if (segments.length > 0) {
      const lastSegment = segments[segments.length - 1]
      const name = lastSegment.replace(/\{[^}]+\}/g, "").replace(/[^a-zA-Z0-9]/g, "")
      if (name) {
        return name.charAt(0).toLowerCase() + name.slice(1)
      }
    }
  }

  return "未命名接口"
}

function displayEndpointDesc(endpoint: ProjectEndpoint & Record<string, any>) {
  const raw =
    endpoint.description ??
    endpoint.summary ??
    endpoint.remark ??
    endpoint.comment ??
    ""

  if (typeof raw === "string" && raw.trim()) {
    return raw.trim()
  }

  const method = (endpoint.method || "GET").toUpperCase()
  const path = endpoint.path || ""
  if (path) {
    return `${method} ${path}`
  }
  return ""
}

async function loadProject() {
  if (!projectKey.value) return

  projectLoading.value = true
  projectError.value = null
  try {
    project.value = await api.getProject(projectKey.value)
  } catch (err) {
    project.value = null
    projectError.value = err instanceof Error ? err.message : "无法加载项目信息"
  } finally {
    projectLoading.value = false
  }
}

async function loadRestEndpoints() {
  if (!projectKey.value) return

  loading.value = true
  errorMessage.value = null
  try {
    restEndpoints.value = await api.listEndpointRests(projectKey.value)
  } catch (err) {
    restEndpoints.value = []
    errorMessage.value = err instanceof Error ? err.message : "无法加载 REST 列表"
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!projectKey.value) return

  if (!form.value.path.trim() || !form.value.name.trim()) {
    window.alert("请先填写接口路径和显示名称")
    return
  }

  try {
    const payload = {
      endpointType: "REST",
      method: form.value.method,
      path: form.value.path.trim(),
      name: form.value.name.trim(),
      description: form.value.description.trim(),
    }
    const created = await api.createEndpoint(projectKey.value, payload)
    restEndpoints.value = [created, ...restEndpoints.value]
    showCreate.value = false
    form.value = { method: "GET", path: "", name: "", description: "" }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err)
    window.alert(`创建失败：${message}`)
  }
}

function openEndpoint(endpoint: ProjectEndpoint) {
  const meta = extractEntrypointMeta(endpoint)
  if (meta.hasFlow && meta.flowCode) {
    router.push(`/studio/${encodeURIComponent(projectKey.value)}/${encodeURIComponent(meta.flowCode)}`)
  }
}

function refresh() {
  loadProject()
  loadRestEndpoints()
}

function clearGuide() {
  const nextQuery = { ...route.query }
  delete nextQuery.guide
  router.replace({ query: nextQuery })
}

watch([projectKey, guide], () => {
  refresh()
  if (guide.value === "create-rest") {
    showCreate.value = true
  }
}, { immediate: true })

function extractEntrypointMeta(endpoint: ProjectEndpoint & Record<string, any>): EntrypointMeta {
  const config = safeParseConfig<Record<string, any>>(endpoint.configJson)
  const rawEntry =
    (endpoint as Record<string, any>).entrypoint ??
    (config as any)?.entrypoint ??
    (config as any)?.entryPoint ??
    {}

  const normalized: EndpointEntrypointConfig = {
    ...((config as any)?.entrypoint || {}),
    ...((config as any)?.entryPoint || {}),
    ...(rawEntry as EndpointEntrypointConfig),
  }

  const path = normalized.path || endpoint.path || ""
  const method = (normalized.method || endpoint.method || "GET").toUpperCase()

  const flowCode =
    endpoint.flowCode ??
    normalized.flowCode ??
    (normalized as Record<string, any>).flow_code ??
    (normalized as Record<string, any>).flow ??
    null

  const enabledRaw =
    (endpoint as Record<string, any>).enabled ??
    normalized.enabled

  const enabled = enabledRaw !== false && enabledRaw !== 0

  return {
    enabled,
    hasFlow: Boolean(flowCode),
    flowCode,
    raw: {
      path,
      method,
      enabled,
      flowCode: flowCode ?? undefined,
    },
  }
}

const safeParseConfig = safeParseJson

function updateEndpointInList(updated: ProjectEndpoint) {
  restEndpoints.value = restEndpoints.value.map((item) => (item.id === updated.id ? updated : item))
}

function applyEntrypointUpdate(endpoint: ProjectEndpoint, entrypoint: FlowEntrypointPayload): ProjectEndpoint {
  const currentEntry = endpoint.entrypoint ?? {}
  return {
    ...endpoint,
    entrypointId: entrypoint.id ?? endpoint.entrypointId ?? null,
    flowCode: entrypoint.flowCode ?? endpoint.flowCode ?? null,
    enabled: entrypoint.enabled ?? endpoint.enabled ?? false,
    entrypoint: {
      ...currentEntry,
      path: entrypoint.path ?? currentEntry.path ?? endpoint.path ?? null,
      method: entrypoint.method ?? currentEntry.method ?? endpoint.method ?? null,
      enabled: entrypoint.enabled ?? currentEntry.enabled ?? endpoint.enabled ?? false,
      flowCode: entrypoint.flowCode ?? currentEntry.flowCode ?? endpoint.flowCode ?? null,
    },
  }
}

async function onToggleEntrypoint(card: RestCard, event: Event) {
  event.stopPropagation()
  const enabled = (event.target as HTMLInputElement).checked
  await setEntrypointEnabled(card, enabled)
}

async function setEntrypointEnabled(card: RestCard, enabled: boolean) {
  if (!projectKey.value || !card.meta.hasFlow) return
  if (!card.endpoint.entrypointId) {
    window.alert("当前接口缺少 entrypointId，暂时无法切换托管状态。")
    return
  }

  toggleBusy.value[card.endpoint.id] = true
  try {
    const updatedEntrypoint = await api.toggleEntrypoint(projectKey.value, card.endpoint.entrypointId, {
      enabled,
    })
    const updated = applyEntrypointUpdate(card.endpoint, updatedEntrypoint)
    updateEndpointInList(updated)
  } catch (err) {
    window.alert(`切换托管状态失败：${err instanceof Error ? err.message : err}`)
  } finally {
    toggleBusy.value[card.endpoint.id] = false
  }
}
</script>

<template>
  <div class="layout">
    <header class="bar layout-header">
      <div class="header-info">
        <div class="title">
          {{ project?.name ?? (projectLoading ? "加载中..." : "未加载项目") }}
        </div>
        <div class="subtitle">
          标识：<span class="code">{{ projectKey }}</span>
          <span v-if="projectError" class="error">（{{ projectError }}）</span>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn" type="button" @click="loadRestEndpoints">刷新列表</button>
        <button class="btn primary" type="button" @click="showCreate = !showCreate">
          {{ showCreate ? "收起" : "新增 REST 入口" }}
        </button>
      </div>
    </header>

    <main class="layout-main">
      <p class="lead">
        推荐顺序：先看项目就绪度，再创建或检查 REST 入口，然后进入 Studio 绑定 flow 并继续编排。
      </p>

      <div v-if="guideMessage" class="guide-banner">
        <div class="guide-copy">
          <div class="guide-title">当前推荐动作</div>
          <div class="guide-text">{{ guideMessage }}</div>
        </div>
        <button class="btn" type="button" @click="clearGuide">知道了</button>
      </div>

      <ProjectReadinessPanel :project-key="projectKey" compact />

      <div v-if="showCreate" class="create-card">
        <div class="field-row">
          <label class="field">
            <span>HTTP 方法</span>
            <select v-model="form.method" class="input">
              <option v-for="m in ['GET', 'POST', 'PUT', 'DELETE', 'PATCH']" :key="m" :value="m">
                {{ m }}
              </option>
            </select>
          </label>
          <label class="field flex-1">
            <span>接口路径</span>
            <input v-model="form.path" class="input" placeholder="例如 /api/order/checkout" />
          </label>
        </div>
        <label class="field">
          <span>显示名称</span>
          <input v-model="form.name" class="input" placeholder="给接口起一个易懂的名字" />
        </label>
        <label class="field">
          <span>描述（可选）</span>
          <textarea
            v-model="form.description"
            class="input textarea"
            rows="2"
            placeholder="补充用途、调用说明或业务备注"
          ></textarea>
        </label>
        <div class="create-actions">
          <button class="btn primary" type="button" @click="handleCreate">确认创建</button>
          <button class="btn" type="button" @click="showCreate = false">取消</button>
        </div>
      </div>

      <div v-if="loading" class="placeholder">
        <div class="placeholder-title">正在加载 REST 列表</div>
        <p class="placeholder-desc">请稍候...</p>
      </div>

      <div v-else-if="errorMessage" class="placeholder error">
        <div class="placeholder-title">加载失败</div>
        <p class="placeholder-desc">{{ errorMessage }}</p>
      </div>

      <template v-else-if="restCards.length">
        <div class="endpoint-grid">
          <article
            v-for="card in restCards"
            :key="card.endpoint.id"
            class="endpoint-card"
            @click="openEndpoint(card.endpoint)"
          >
            <header class="endpoint-headline">
              <div class="headline-left">
                <span class="method-chip">{{ (card.endpoint.method || "GET").toUpperCase() }}</span>
                <span class="path" :title="card.endpoint.path || ''">{{ card.endpoint.path || '' }}</span>
              </div>
              <span class="status-pill" :class="card.meta.enabled ? 'status-pill--on' : 'status-pill--off'">
                <span class="status-dot"></span>
                {{ card.meta.enabled ? "托管中" : "已停用" }}
              </span>
            </header>

            <div class="endpoint-main">
              <div class="endpoint-info">
                <div class="endpoint-name">
                  {{ displayEndpointName(card.endpoint as ProjectEndpoint & Record<string, any>) }}
                </div>
                <p class="endpoint-desc">
                  {{ displayEndpointDesc(card.endpoint as ProjectEndpoint & Record<string, any>) }}
                </p>
              </div>

              <div class="flow-inline">
                <div class="flow-info">
                  <span class="flow-label">关联 Flow</span>
                  <span class="flow-value">{{ card.meta.flowCode || "尚未绑定" }}</span>
                </div>
                <label v-if="card.meta.hasFlow" class="switch" @click.stop>
                  <input
                    type="checkbox"
                    :checked="card.meta.enabled"
                    :disabled="toggleBusy[card.endpoint.id]"
                    @change="onToggleEntrypoint(card, $event)"
                  />
                  <span class="slider"></span>
                </label>
                <span v-else class="flow-note">还没有绑定 flow</span>
              </div>
            </div>

            <footer class="endpoint-meta">
              <div class="meta-item">
                <span class="meta-label">最近更新</span>
                <span class="meta-value">
                  {{ formatTimestamp(card.endpoint.updateTime || card.endpoint.createTime) }}
                </span>
              </div>
              <span class="meta-link">
                ID #{{ card.endpoint.id }}
                <span class="arrow">&rarr;</span>
              </span>
            </footer>
          </article>
        </div>
      </template>

      <div v-else class="placeholder">
        <div class="placeholder-title">还没有 REST 入口</div>
        <p class="placeholder-desc">先创建一个入口，再把它绑定到具体 flow 上，就能继续进入 Studio。</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.layout-header {
  justify-content: space-between;
  align-items: center;
}

.header-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
}

.subtitle {
  font-size: 13px;
  color: #64748b;
}

.subtitle .code {
  font-family: "Fira Mono", Consolas, ui-monospace, SFMono-Regular, Menlo, Monaco, "Courier New", monospace;
}

.subtitle .error {
  color: #b91c1c;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.layout-main {
  flex: 1;
  padding: 16px 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  overflow: auto;
}

.lead {
  font-size: 13px;
  color: #475569;
  margin: 0;
}

.guide-banner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  border-radius: 16px;
  border: 1px solid rgba(99, 102, 241, 0.22);
  background: linear-gradient(135deg, rgba(238, 242, 255, 0.92), rgba(255, 255, 255, 0.98));
  padding: 16px;
}

.guide-copy {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.guide-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #4f46e5;
}

.guide-text {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.create-card {
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 16px;
  padding: 16px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.field-row {
  display: flex;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: #1f2937;
}

.field span {
  font-weight: 700;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.textarea {
  resize: vertical;
}

.flex-1 {
  flex: 1;
}

.create-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.endpoint-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 400px));
  gap: 16px;
  justify-content: start;
}

.endpoint-card {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 12px;
  background: #fff;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  cursor: pointer;
  transition: box-shadow 0.12s ease, border-color 0.12s ease;
}

.endpoint-card:hover {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 10px 18px -16px rgba(15, 23, 42, 0.55);
}

.endpoint-headline {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  min-height: 28px;
}

.headline-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
  overflow: hidden;
}

.method-chip {
  font-weight: 700;
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.1);
  border-radius: 999px;
  padding: 1px 8px;
  font-size: 11px;
  letter-spacing: 0.03em;
}

.path {
  font-family: "Fira Mono", Consolas, ui-monospace, SFMono-Regular, Menlo, Monaco, "Courier New", monospace;
  font-size: 12px;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.endpoint-main {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.endpoint-name {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.endpoint-desc {
  font-size: 11px;
  color: #64748b;
  margin: 2px 0 0;
}

.flow-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  color: #475569;
  flex: 1;
  min-width: 0;
}

.flow-label {
  font-size: 11px;
  color: #94a3b8;
}

.flow-value {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flow-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(248, 250, 252, 0.6);
  border: 1px solid rgba(148, 163, 184, 0.2);
}

.flow-note {
  font-size: 12px;
  color: #94a3b8;
  flex: 1;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 6px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
  white-space: nowrap;
}

.status-pill--on {
  background: rgba(34, 197, 94, 0.15);
  color: #15803d;
}

.status-pill--off {
  background: rgba(248, 113, 113, 0.18);
  color: #b91c1c;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.switch {
  position: relative;
  display: inline-flex;
  align-items: center;
  width: 40px;
  height: 20px;
  flex-shrink: 0;
}

.switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.slider {
  position: absolute;
  cursor: pointer;
  inset: 0;
  background-color: #cbd5e1;
  transition: 0.2s;
  border-radius: 999px;
}

.slider:before {
  position: absolute;
  content: "";
  height: 16px;
  width: 16px;
  left: 2px;
  bottom: 2px;
  background-color: white;
  transition: 0.2s;
  border-radius: 50%;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.switch input:disabled + .slider {
  opacity: 0.5;
  cursor: not-allowed;
}

.switch input:checked + .slider {
  background-color: #2563eb;
}

.switch input:checked + .slider:before {
  transform: translateX(20px);
}

.endpoint-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #475569;
  border-top: 1px solid rgba(15, 23, 42, 0.05);
  padding-top: 8px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-label {
  font-size: 10px;
  color: #94a3b8;
}

.meta-value {
  font-weight: 700;
  color: #0f172a;
}

.meta-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #2563eb;
  font-weight: 700;
  font-size: 11px;
}

.meta-link .arrow {
  font-size: 14px;
  color: #2563eb;
}

.placeholder {
  border: 1px dashed rgba(148, 163, 184, 0.5);
  border-radius: 16px;
  padding: 48px 24px;
  text-align: center;
  background: rgba(255, 255, 255, 0.9);
}

.placeholder.error {
  border-color: rgba(248, 113, 113, 0.4);
  color: #b91c1c;
}

.placeholder-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e1b4b;
  margin-bottom: 8px;
}

.placeholder-desc {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

@media (max-width: 768px) {
  .field-row {
    flex-direction: column;
  }

  .guide-banner {
    flex-direction: column;
  }

  .endpoint-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}
</style>
