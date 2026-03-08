<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue"
import { Book, ChevronDown, GitBranch, Layers, Search } from "lucide-vue-next"
import { api, type EndpointComponent, type EndpointComponentGroup } from "../api/client"

type LogicNodeType = "branch" | "transaction" | "serviceGroup" | "rest"

type LogicItem = {
  id: string
  title: string
  description: string
  nodeType: LogicNodeType
  variant?: "begin" | "end"
}

type PaletteCardItem = {
  id: string
  title: string
  description: string
  draggable: boolean
  payload: Record<string, any>
  meta?: string
  version?: string
}

type PaletteSection = {
  key: string
  label: string
  color: string
  icon: any
  items: PaletteCardItem[]
}

const props = defineProps<{ projectKey: string }>()

const nav = ref<"flow" | "docs">("flow")
const expanded = ref<Record<string, boolean>>({})
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const componentGroups = ref<EndpointComponentGroup[]>([])
const searchKeyword = ref("")

const typeColorMap: Record<string, string> = {
  BUSINESS: "#16a34a",
  SYSTEM: "#0ea5e9",
  CUSTOM: "#d946ef",
}

const typeLabelMap: Record<string, string> = {
  BUSINESS: "业务组件",
  SYSTEM: "系统组件",
  CUSTOM: "自定义组件",
}

const logicItems: LogicItem[] = [
  { id: "branch-node", title: "条件分支", description: "根据条件命中不同分支", nodeType: "branch" },
  { id: "service-group", title: "服务组", description: "将多个服务组合执行，可启用统一事务", nodeType: "serviceGroup" },
  { id: "http-node", title: "HTTP 调用", description: "调用外部 HTTP 接口，支持超时与重试", nodeType: "rest" },
  { id: "txn-begin", title: "事务开始", description: "开启事务上下文", nodeType: "transaction", variant: "begin" },
  { id: "txn-end", title: "事务结束", description: "提交或回滚事务", nodeType: "transaction", variant: "end" },
]

function normalizeType(value?: string | null) {
  return String(value ?? "").trim().toUpperCase()
}

function safeParseJson(input: unknown): Record<string, any> | null {
  if (!input) return null
  if (typeof input === "object") return input as Record<string, any>
  if (typeof input !== "string") return null
  try {
    return JSON.parse(input)
  } catch {
    return null
  }
}

function resolveItemTitle(item: EndpointComponent) {
  return item.displayName || item.bean || item.method || "未命名组件"
}

function resolveItemDesc(item: EndpointComponent, fallback: string) {
  return item.description || item.domain || fallback
}

function toComponentCard(item: EndpointComponent, groupLabel: string): PaletteCardItem {
  const endpointType = normalizeType(item.endpointType)
  const title = resolveItemTitle(item)
  const description = resolveItemDesc(item, groupLabel)
  const meta = [item.bean, item.method].filter(Boolean).join(".")
  return {
    id: String(item.id ?? `${title}-${meta || "item"}`),
    title,
    description,
    draggable: endpointType !== "SERVICE",
    payload: { tab: "components", ...item },
    meta: meta || undefined,
    version: item.version,
  }
}

function toOperationCards(serviceItem: EndpointComponent): PaletteCardItem[] {
  const serviceConfig = safeParseJson(serviceItem.configJson)
  const operations = Array.isArray(serviceConfig?.operations) ? serviceConfig.operations : []
  if (!operations.length) return []

  const serviceBean = String(serviceConfig?.bean || serviceItem.bean || "")
  const serviceName = String(serviceConfig?.name || serviceItem.displayName || serviceBean || "服务")
  const serviceClass = String(serviceConfig?.class || "")
  return operations.map((op: any, index: number) => {
    const method = String(op?.method || "")
    const title = String(op?.name || method || `操作${index + 1}`)
    const description = String(op?.description || `来自 ${serviceName}`)
    const opConfig = {
      ...op,
      serviceBean: serviceBean || undefined,
      serviceName: serviceName || undefined,
      serviceClass: serviceClass || undefined,
    }
    const payload: Record<string, any> = {
      tab: "components",
      ...serviceItem,
      id: `${serviceItem.id ?? serviceName}-${method || index}`,
      displayName: title,
      description,
      endpointType: "FLOW_OPERATION",
      bean: serviceBean || serviceItem.bean,
      method: method || serviceItem.method,
      configJson: JSON.stringify(opConfig),
      path: serviceClass && method ? `${serviceClass}#${method}` : serviceItem.path,
    }
    return {
      id: String(payload.id),
      title,
      description,
      draggable: true,
      payload,
      meta: [serviceBean, method].filter(Boolean).join(".") || serviceName,
      version: serviceItem.version,
    }
  })
}

const componentSections = computed<PaletteSection[]>(() => {
  const sections: PaletteSection[] = []
  componentGroups.value.forEach((group, index) => {
    const type = normalizeType(group.type)
    const color = typeColorMap[type] || ["#16a34a", "#0ea5e9", "#d946ef", "#f97316"][index % 4]
    const label = group.displayName || typeLabelMap[type] || type || "组件"
    const sectionItems: PaletteCardItem[] = []
    for (const item of group.items ?? []) {
      const endpointType = normalizeType(item.endpointType)
      if (endpointType === "REST") continue
      if (endpointType === "SERVICE") {
        const operationCards = toOperationCards(item)
        if (operationCards.length) {
          sectionItems.push(...operationCards)
          continue
        }
      }
      sectionItems.push(toComponentCard(item, label))
    }
    if (!sectionItems.length) return
    sections.push({
      key: `components-${index}-${type || "unknown"}`,
      label,
      color,
      icon: Layers,
      items: sectionItems,
    })
  })
  return sections
})

const logicSection = computed<PaletteSection>(() => ({
  key: "logic",
  label: "流程节点",
  color: "#6366f1",
  icon: GitBranch,
  items: logicItems.map((item) => ({
    id: item.id,
    title: item.title,
    description: item.description,
    draggable: true,
    payload: { tab: "logic", ...item },
  })),
}))

const sections = computed<PaletteSection[]>(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  const all = [...componentSections.value, logicSection.value]
  if (!keyword) return all
  return all
    .map((section) => ({
      ...section,
      items: section.items.filter((item) => {
        const haystack = [item.title, item.description, item.meta || ""].join(" ").toLowerCase()
        return haystack.includes(keyword)
      }),
    }))
    .filter((section) => section.items.length > 0)
})

watch(
  sections,
  (value) => {
    const next = { ...expanded.value }
    for (const section of value) {
      if (!(section.key in next)) next[section.key] = true
    }
    expanded.value = next
  },
  { immediate: true },
)

async function loadComponents(projectKey: string) {
  if (!projectKey) {
    componentGroups.value = []
    return
  }
  loading.value = true
  errorMessage.value = null
  try {
    componentGroups.value = await api.listEndpointComponents(projectKey)
  } catch (err) {
    componentGroups.value = []
    errorMessage.value = err instanceof Error ? err.message : "组件加载失败"
  } finally {
    loading.value = false
  }
}

function toggleSection(key: string) {
  expanded.value[key] = !expanded.value[key]
}

function handleDragStart(event: DragEvent, payload: Record<string, any>, draggable: boolean) {
  if (!draggable || !event.dataTransfer) return
  const json = JSON.stringify(payload)
  event.dataTransfer.setData("application/json", json)
  event.dataTransfer.setData("text/plain", json)
  event.dataTransfer.effectAllowed = "copyMove"
}

onMounted(() => {
  if (props.projectKey) loadComponents(props.projectKey)
})

watch(
  () => props.projectKey,
  (key) => loadComponents(key),
)
</script>

<template>
  <div class="palette">
    <div class="palette-nav">
      <button class="nav-btn" :class="{ active: nav === 'flow' }" @click="nav = 'flow'">
        <GitBranch :size="14" /> 控件
      </button>
      <button class="nav-btn" :class="{ active: nav === 'docs' }" @click="nav = 'docs'">
        <Book :size="14" /> 文档
      </button>
    </div>

    <div v-if="nav === 'flow'" class="section-list">
      <div class="search-box">
        <Search :size="14" class="search-icon" />
        <input v-model="searchKeyword" type="text" class="search-input" placeholder="搜索服务或操作..." />
      </div>

      <div v-if="loading" class="placeholder">正在加载组件...</div>
      <div v-else-if="errorMessage" class="placeholder error">加载失败：{{ errorMessage }}</div>
      <template v-else>
        <div v-for="section in sections" :key="section.key" class="section">
          <button class="section-header" @click="toggleSection(section.key)">
            <component :is="section.icon" :size="16" :style="{ color: section.color }" />
            <span class="section-title">{{ section.label }}</span>
            <span class="section-count">{{ section.items.length }}</span>
            <ChevronDown :size="14" class="chevron" :class="{ collapsed: !expanded[section.key] }" />
          </button>
          <transition name="section">
            <div v-show="expanded[section.key]" class="item-stack">
              <div
                v-for="item in section.items"
                :key="item.id"
                class="item-card"
                :class="{ 'item-card--disabled': !item.draggable }"
                :draggable="item.draggable"
                @dragstart="handleDragStart($event, item.payload, item.draggable)"
              >
                <div class="item-bullet" :style="{ background: section.color }"></div>
                <div class="item-content">
                  <div class="item-title">{{ item.title }}</div>
                  <div class="item-desc">{{ item.description }}</div>
                  <div v-if="item.meta" class="item-meta">{{ item.meta }}</div>
                  <div v-if="item.version" class="item-tag">版本 {{ item.version }}</div>
                </div>
              </div>
            </div>
          </transition>
        </div>
        <div v-if="!sections.length" class="placeholder">暂无可用组件，请先同步本地项目元数据。</div>
      </template>
    </div>

    <div v-else class="docs-placeholder">
      <Book :size="24" />
      <div style="font-size:13px">文档建设中，敬请期待</div>
    </div>
  </div>
</template>

<style scoped>
.palette {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.palette-nav {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}

.nav-btn {
  flex: 1;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 999px;
  padding: 6px 10px;
  font-size: 12px;
  background: #fff;
  cursor: pointer;
}

.nav-btn.active {
  background: #111827;
  color: #fff;
  border-color: #111827;
}

.search-box {
  position: relative;
  margin: 12px;
  margin-bottom: 8px;
}

.search-icon {
  position: absolute;
  left: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 8px 10px 8px 32px;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 6px;
  font-size: 12px;
  background: #fff;
  transition: border-color 0.2s;
}

.search-input:focus {
  outline: none;
  border-color: #2563eb;
}

.section-list {
  padding: 0 12px 12px;
  overflow: auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section {
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.04);
}

.section-header {
  width: 100%;
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: none;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.section-header:hover {
  background: rgba(148, 163, 184, 0.05);
  border-radius: 14px 14px 0 0;
}

.section-title {
  font-weight: 600;
  font-size: 13px;
  flex: 1;
  text-align: left;
  color: #1e293b;
}

.section-count {
  font-size: 11px;
  color: #94a3b8;
}

.chevron {
  transition: transform 0.2s ease;
  color: #94a3b8;
}

.chevron.collapsed {
  transform: rotate(-90deg);
}

.item-stack {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px 14px 14px;
}

.item-card {
  display: flex;
  gap: 10px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 8px;
  padding: 10px 12px;
  cursor: grab;
  background: #fff;
  transition: all 0.2s ease;
}

.item-card:active {
  cursor: grabbing;
}

.item-card:hover {
  border-color: #2563eb;
  background: #f8fafc;
  box-shadow: 0 2px 4px rgba(37, 99, 235, 0.1);
}

.item-card--disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: rgba(148, 163, 184, 0.05);
}

.item-card--disabled:hover {
  border-color: rgba(226, 232, 240, 0.8);
  background: rgba(148, 163, 184, 0.05);
  box-shadow: none;
}

.item-bullet {
  width: 8px;
  border-radius: 999px;
  margin-top: 4px;
}

.item-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.item-title {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
}

.item-desc {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
}

.item-meta {
  font-size: 10px;
  color: #94a3b8;
  font-family: "Monaco", "Menlo", "Consolas", monospace;
  margin-top: 2px;
}

.item-tag {
  width: max-content;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.08);
  color: #2563eb;
  border: 1px solid rgba(37, 99, 235, 0.2);
}

.placeholder {
  border: 1px dashed rgba(148, 163, 184, 0.5);
  border-radius: 16px;
  padding: 32px 16px;
  text-align: center;
  color: #94a3b8;
}

.placeholder.error {
  color: #b91c1c;
}

.docs-placeholder {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.section-enter-active,
.section-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.section-enter-from,
.section-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
