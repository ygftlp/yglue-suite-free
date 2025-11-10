<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { api, type Project, type ProjectEndpoint, type EndpointEntrypointConfig, type ProjectEndpointUpdatePayload } from "../api/client"
import { formatTimestamp, safeParseJson } from "../utils/formatters"

const route = useRoute()
const router = useRouter()

const projectKey = computed(() => route.params.projectKey as string)

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

const toggleBusy = ref<Record<number, boolean>>({})

/**
 * 显示端点名称（主标签）
 * 优先显示有意义的名称，如果没有则从路径中提取合理的名称
 */
function displayEndpointName(endpoint: ProjectEndpoint & Record<string, any>) {
  // 优先使用显示名称、别名、标题或名称字段
  const raw =
    endpoint.displayName ??
    endpoint.alias ??
    endpoint.title ??
    endpoint.name ??
    ""
  
  if (typeof raw === "string" && raw.trim()) {
    // 处理包含 # 的情况（如 "com.example#methodName"）
    if (raw.includes("#")) {
      const parts = raw.split("#")
      return parts[parts.length - 1] || raw
    }
    return raw.trim()
  }
  
  // 如果没有名称，尝试从路径中提取有意义的名称
  const path = endpoint.path || ""
  if (path) {
    // 提取路径的最后一段作为名称（去除参数）
    const segments = path.split("/").filter(Boolean)
    if (segments.length > 0) {
      const lastSegment = segments[segments.length - 1]
      // 移除路径参数（如 {projectKey}）
      const name = lastSegment.replace(/\{[^}]+\}/g, "").replace(/[^a-zA-Z0-9]/g, "")
      if (name) {
        // 转换为驼峰命名（首字母小写）
        return name.charAt(0).toLowerCase() + name.slice(1)
      }
    }
  }
  
  return "未命名接口"
}

/**
 * 显示端点描述
 * 如果有描述则显示描述，否则显示 HTTP 方法和路径的组合（如 "GET /api/health"）
 */
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
  // 如果没有描述，显示 HTTP 方法和路径的组合
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
    window.alert("请填写接口路径和名称")
    return
  }

  try {
    const payload = {
      endpointType: "REST",
      method: form.value.method,
      path: form.value.path.trim(),
      name: form.value.name,
      description: form.value.description,
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
  router.push(`/projects/${encodeURIComponent(projectKey.value)}/rests/${endpoint.id}`)
}

function refresh() {
  loadProject()
  loadRestEndpoints()
}

watch(projectKey, refresh, { immediate: true })

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
  const replaceResponse =
    typeof (endpoint as Record<string, any>).replaceResponse === "boolean"
      ? Boolean((endpoint as Record<string, any>).replaceResponse)
      : Boolean(normalized.replaceResponse)

  return {
    enabled,
    hasFlow: Boolean(flowCode),
    flowCode,
    raw: {
      path,
      method,
      replaceResponse,
      enabled,
      flowCode: flowCode ?? undefined,
    },
  }
}

const safeParseConfig = safeParseJson

function updateEndpointInList(updated: ProjectEndpoint) {
  restEndpoints.value = restEndpoints.value.map((item) => (item.id === updated.id ? updated : item))
}

async function onToggleEntrypoint(card: RestCard, event: Event) {
  event.stopPropagation()
  const enabled = (event.target as HTMLInputElement).checked
  await setEntrypointEnabled(card, enabled)
}

async function setEntrypointEnabled(card: RestCard, enabled: boolean) {
  if (!projectKey.value || !card.meta.hasFlow) return

  toggleBusy.value[card.endpoint.id] = true
  try {
    const payload: ProjectEndpointUpdatePayload = {
      entrypoint: {
        ...card.meta.raw,
        enabled,
      },
    }
    const updated = await api.updateEndpoint(projectKey.value, card.endpoint.id, payload)
    updateEndpointInList(updated)
  } catch (err) {
    window.alert(`切换托管状态失败：${err instanceof Error ? err.message : err}`)
  } finally {
    toggleBusy.value[card.endpoint.id] = false
  }
}

</script>

﻿<template>
  <div class="layout">
    <header class="bar layout-header">
      <div class="header-info">
        <div class="title">
          {{ project?.name ?? (projectLoading ? "加载中..." : "尚未加载项目") }}
        </div>
        <div class="subtitle">
          标识：<span class="code">{{ projectKey }}</span>
          <span v-if="projectError" class="error">（{{ projectError }}）</span>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn" type="button" @click="loadRestEndpoints">刷新</button>
        <button class="btn primary" type="button" @click="showCreate = !showCreate">
          {{ showCreate ? "取消" : "新增 REST" }}
        </button>
      </div>
    </header>

    <main class="layout-main">
      <p class="lead">
        注册 REST 接口后即可在画布中编排流程，并保持与 IDE 插件的数据同步。
      </p>

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
          <span>展示名称</span>
          <input v-model="form.name" class="input" placeholder="给接口起一个易懂的名字" />
        </label>
        <label class="field">
          <span>描述（可选）</span>
          <textarea
            v-model="form.description"
            class="input textarea"
            rows="2"
            placeholder="补充调用说明、业务备注等"
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
                  <span class="flow-label">关联流程</span>
                  <span class="flow-value">{{ card.meta.flowCode || "未绑定" }}</span>
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
                <span v-else class="flow-note">尚未绑定流程</span>
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
        <div class="placeholder-title">尚未配置 REST 接口</div>
        <p class="placeholder-desc">点击右上角“新增 REST”即可录入新的接口入口。</p>
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
  font-weight: 600;
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
  font-weight: 600;
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
  font-weight: 600;
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
  font-weight: 600;
  color: #0f172a;
}

.endpoint-desc {
  font-size: 11px;
  color: #64748b;
  margin: 2px 0 0;
}

.flow-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 10px;
  border-radius: 9px;
  border: 1px dashed rgba(99, 102, 241, 0.25);
  background: rgba(248, 250, 252, 0.4);
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
  font-weight: 600;
  color: #0f172a;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.flow-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
  font-weight: 600;
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
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
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
  font-weight: 600;
  color: #0f172a;
}

.meta-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #2563eb;
  font-weight: 600;
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
  font-weight: 600;
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

  .endpoint-grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}

@media (min-width: 769px) and (max-width: 1200px) {
  .endpoint-grid {
    grid-template-columns: repeat(auto-fill, minmax(300px, 380px));
  }
}

</style>

