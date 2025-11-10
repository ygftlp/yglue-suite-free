<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue"
import { Book, ChevronDown, GitBranch, Layers } from "lucide-vue-next"
import { api, type EndpointComponent, type EndpointComponentGroup } from "../api/client"

const LABELS = {
  logic: "流程节点",
  branchDesc: "通过条件字段控制分支",
} as const

const typeLabelMap: Record<string, string> = {
  BUSINESS: "业务组件",
  SYSTEM: "系统组件",
  CUSTOM: "自定义组件",
}

/**
 * 端点类型标签映射
 * 用于在业务组件中进一步细分显示
 */
const endpointTypeLabelMap: Record<string, string> = {
  FLOW_API: "API 类",
  FLOW_OPERATION: "操作方法",
}

const typeColorMap: Record<string, string> = {
  BUSINESS: "#16a34a",
  SYSTEM: "#0ea5e9",
  CUSTOM: "#d946ef",
}

const paletteColors = ["#16a34a", "#0ea5e9", "#d946ef", "#f97316"]

type LogicItem = {
  id: string
  title: string
  description: string
  nodeType: "branch" | "transaction" | "transformer"
  variant?: "begin" | "end"
}

const props = defineProps<{ projectKey: string }>()

const nav = ref<"flow" | "docs">("flow")
const expanded = ref<Record<string, boolean>>({})
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const componentGroups = ref<EndpointComponentGroup[]>([])

const logicItems: LogicItem[] = [
  { id: "branch-node", title: "条件分支", description: LABELS.branchDesc, nodeType: "branch" },
  { id: "txn-begin", title: "事务开始", description: "创建事务上下文", nodeType: "transaction", variant: "begin" },
  { id: "txn-end", title: "事务结束", description: "提交或回滚事务", nodeType: "transaction", variant: "end" },
  { id: "transformer-node", title: "通用转换器", description: "将流程输出转换为目标接口的请求/响应结构", nodeType: "transformer" },
]

function normalizeType(value?: string | null) {
  return String(value ?? "").toUpperCase()
}

/**
 * 从 FlowOperation 的 path 中提取所属的 API 类名
 * path 格式：className#methodName 或 className:version
 */
function extractApiClassFromPath(path?: string): string | null {
  if (!path) return null
  // 处理 FlowOperation：className#methodName
  const hashIndex = path.indexOf("#")
  if (hashIndex > 0) {
    return path.substring(0, hashIndex)
  }
  // 处理 FlowApi：className:version（去除版本号）
  const colonIndex = path.indexOf(":")
  if (colonIndex > 0) {
    return path.substring(0, colonIndex)
  }
  return path || null
}

/**
 * 从组件的 configJson 中提取类名（如果 path 无法提取）
 */
function extractApiClassFromConfig(item: EndpointComponent): string | null {
  try {
    const configJson = item.configJson
    if (typeof configJson === "string" && configJson) {
      const config = JSON.parse(configJson)
      return config.class || null
    }
  } catch {
    // 忽略解析错误
  }
  return null
}

/**
 * 组件分组计算属性
 * 对于业务组件（BUSINESS），采用层级结构：
 * - FLOW_API 作为类别分组（不可拖拽，仅用于组织）
 * - FLOW_OPERATION 作为子项显示在对应的 API 类下面
 */
const componentSections = computed(() => {
  const sections: Array<{
    key: string
    type: string
    label: string
    color: string
    icon: typeof Layers
    items: EndpointComponent[]
    isCategory?: boolean // 标记是否为类别（不可拖拽）
    children?: Array<{
      apiClass: string
      items: EndpointComponent[]
    }>
  }> = []

  componentGroups.value.forEach((group, groupIndex) => {
    const type = normalizeType(group.type)
    const filteredItems = (group.items ?? []).filter((item) => normalizeType(item.endpointType) !== "REST")
    if (!filteredItems.length) return

    // 如果是业务组件，采用层级结构
    if (type === "BUSINESS") {
      const apiItems = filteredItems.filter((item) => normalizeType(item.endpointType) === "FLOW_API")
      const operationItems = filteredItems.filter((item) => normalizeType(item.endpointType) === "FLOW_OPERATION")
      const otherItems = filteredItems.filter(
        (item) =>
          normalizeType(item.endpointType) !== "FLOW_API" && normalizeType(item.endpointType) !== "FLOW_OPERATION"
      )

      // 如果有 API 类和操作方法，采用层级结构
      if (apiItems.length > 0 && operationItems.length > 0) {
        // 按 API 类分组操作方法
        const apiClassMap = new Map<string, EndpointComponent[]>()
        
        apiItems.forEach((apiItem) => {
          const apiClass =
            extractApiClassFromPath(apiItem.path) ||
            extractApiClassFromConfig(apiItem) ||
            apiItem.bean ||
            apiItem.displayName ||
            "未知类"
          if (!apiClassMap.has(apiClass)) {
            apiClassMap.set(apiClass, [])
          }
        })

        // 将操作方法分配到对应的 API 类
        operationItems.forEach((opItem) => {
          const apiClass =
            extractApiClassFromPath(opItem.path) || extractApiClassFromConfig(opItem)
          if (apiClass && apiClassMap.has(apiClass)) {
            apiClassMap.get(apiClass)!.push(opItem)
          } else {
            // 如果找不到对应的 API 类，创建一个"未分类"分组
            if (!apiClassMap.has("未分类")) {
              apiClassMap.set("未分类", [])
            }
            apiClassMap.get("未分类")!.push(opItem)
          }
        })

        // 为每个 API 类创建一个分组
        apiClassMap.forEach((operations, apiClass) => {
          if (operations.length === 0) return // 跳过没有操作方法的 API 类
          
          const apiItem = apiItems.find(
            (item) =>
              extractApiClassFromPath(item.path) === apiClass ||
              extractApiClassFromConfig(item) === apiClass ||
              item.bean === apiClass ||
              item.displayName === apiClass
          )
          
          const key = `components-business-api-${apiClass}-${groupIndex}`
          if (!(key in expanded.value)) expanded.value[key] = true
          
          sections.push({
            key,
            type: "BUSINESS",
            label: apiItem?.displayName || apiItem?.name || apiClass,
            color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
            icon: Layers,
            items: [apiItem].filter(Boolean) as EndpointComponent[], // API 类本身（不可拖拽）
            isCategory: true,
            children: [
              {
                apiClass,
                items: operations, // 操作方法（可拖拽）
              },
            ],
          })
        })

        // 如果有独立的 API 类（没有操作方法），单独显示
        apiItems.forEach((apiItem) => {
          const apiClass =
            extractApiClassFromPath(apiItem.path) ||
            extractApiClassFromConfig(apiItem) ||
            apiItem.bean ||
            apiItem.displayName ||
            "未知类"
          if (!apiClassMap.has(apiClass) || apiClassMap.get(apiClass)!.length === 0) {
            const key = `components-business-api-solo-${apiClass}-${groupIndex}`
            if (!(key in expanded.value)) expanded.value[key] = true
            sections.push({
              key,
              type: "BUSINESS",
              label: apiItem.displayName || apiItem.name || apiClass,
              color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
              icon: Layers,
              items: [apiItem],
              isCategory: true,
            })
          }
        })
      } else {
        // 如果没有层级关系，保持原有分组方式
        if (apiItems.length > 0) {
          const key = `components-business-api-${groupIndex}`
          if (!(key in expanded.value)) expanded.value[key] = true
          sections.push({
            key,
            type: "BUSINESS",
            label: "API 类",
            color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
            icon: Layers,
            items: apiItems,
            isCategory: true,
          })
        }

        if (operationItems.length > 0) {
          const key = `components-business-operation-${groupIndex}`
          if (!(key in expanded.value)) expanded.value[key] = true
          sections.push({
            key,
            type: "BUSINESS",
            label: "操作方法",
            color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
            icon: Layers,
            items: operationItems,
          })
        }
      }

      // 添加其他业务组件分组
      if (otherItems.length > 0) {
        const key = `components-business-other-${groupIndex}`
        if (!(key in expanded.value)) expanded.value[key] = true
        sections.push({
          key,
          type: "BUSINESS",
          label: group.displayName || typeLabelMap[type] || "其他业务组件",
          color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
          icon: Layers,
          items: otherItems,
        })
      }
    } else {
      // 非业务组件，保持原有逻辑
      const key = `components-${type.toLowerCase() || groupIndex}`
      if (!(key in expanded.value)) expanded.value[key] = true
      sections.push({
        key,
        type,
        label: group.displayName || typeLabelMap[type] || type || "组件",
        color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
        icon: Layers,
        items: filteredItems,
      })
    }
  })

  return sections
})

const sections = computed(() => [
  ...componentSections.value,
  { key: "logic", label: LABELS.logic, color: "#6366f1", icon: GitBranch, items: logicItems },
])

async function loadComponents(projectKey: string) {
  if (!projectKey) return
  loading.value = true
  errorMessage.value = null
  try {
    componentGroups.value = await api.listEndpointComponents(projectKey)
  } catch (err) {
    componentGroups.value = []
    errorMessage.value = err instanceof Error ? err.message : "组件数据加载失败"
  } finally {
    loading.value = false
  }
}

function toggleSection(key: string) {
  expanded.value[key] = !expanded.value[key]
}

function handleDragStart(event: DragEvent, item: EndpointComponent | LogicItem, sourceKey: string) {
  if (!event.dataTransfer) return
  const payload = { tab: sourceKey.startsWith("components") ? "components" : "logic", ...item }
  const json = JSON.stringify(payload)
  event.dataTransfer.setData("application/json", json)
  event.dataTransfer.setData("text/plain", json)
  event.dataTransfer.effectAllowed = "copyMove"
}

function displayName(item: EndpointComponent) {
  return item.displayName || item.bean || item.method || "未命名组件"
}

function displayDesc(item: EndpointComponent, fallback: string) {
  return item.description || item.domain || fallback
}

/**
 * 判断是否为 FLOW_API 类型的组件
 * FLOW_API 是类级别组件，不能拖拽到画布（只有方法级别的 FlowOperation 可以拖拽）
 */
function isFlowApi(item: EndpointComponent): boolean {
  return normalizeType(item.endpointType) === "FLOW_API"
}

onMounted(() => {
  if (props.projectKey) loadComponents(props.projectKey)
})

watch(
  () => props.projectKey,
  (key) => {
    if (key) loadComponents(key)
    else componentGroups.value = []
  }
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
              <!-- 层级结构：API 类作为类别，操作方法作为子项 -->
              <template v-if="section.isCategory && section.children">
                <!-- API 类本身（不可拖拽，仅显示） -->
                <div
                  v-for="item in section.items"
                  :key="item.id ?? item.displayName ?? item.bean ?? item.method"
                  class="item-card item-card--category"
                >
                  <div class="item-bullet" :style="{ background: section.color }"></div>
                  <div class="item-content">
                    <div class="item-title">
                      {{ displayName(item as EndpointComponent) }}
                      <span class="item-badge">（类别）</span>
                    </div>
                    <div class="item-desc">
                      {{ displayDesc(item as EndpointComponent, section.label) }}
                    </div>
                  </div>
                </div>
                <!-- 操作方法子项（可拖拽） -->
                <div
                  v-for="child in section.children"
                  :key="child.apiClass"
                  class="category-children"
                >
                  <div
                    v-for="opItem in child.items"
                    :key="opItem.id ?? opItem.displayName ?? opItem.bean ?? opItem.method"
                    class="item-card item-card--child"
                    draggable="true"
                    @dragstart="handleDragStart($event, opItem, section.key)"
                  >
                    <div class="item-bullet item-bullet--child" :style="{ background: section.color }"></div>
                    <div class="item-content">
                      <div class="item-title">
                        {{ displayName(opItem as EndpointComponent) }}
                      </div>
                      <div class="item-desc">
                        {{ displayDesc(opItem as EndpointComponent, section.label) }}
                      </div>
                      <div v-if="opItem.bean" class="item-meta">
                        {{ opItem.bean }}{{ opItem.method ? `.${opItem.method}` : "" }}
                      </div>
                    </div>
                  </div>
                </div>
              </template>
              <!-- 普通列表结构 -->
              <template v-else>
                <div
                  v-for="item in section.items"
                  :key="item.id ?? item.displayName ?? item.bean ?? item.method ?? item.title"
                  class="item-card"
                  :class="{ 'item-card--disabled': isFlowApi(item) || section.isCategory }"
                  :draggable="!isFlowApi(item) && !section.isCategory"
                  @dragstart="handleDragStart($event, item, section.key)"
                >
                  <div class="item-bullet" :style="{ background: section.color }"></div>
                  <div class="item-content">
                    <div class="item-title">
                      {{ section.key === "logic" ? item.title : displayName(item as EndpointComponent) }}
                      <span v-if="section.key !== 'logic' && (isFlowApi(item) || section.isCategory)" class="item-badge">
                        （{{ section.isCategory ? "类别" : "类定义" }}）
                      </span>
                    </div>
                    <div class="item-desc">
                      {{ section.key === "logic" ? item.description : displayDesc(item as EndpointComponent, section.label) }}
                      <span v-if="section.key !== 'logic' && (isFlowApi(item) || section.isCategory)" class="item-hint">
                        （仅查看，请使用操作方法）
                      </span>
                    </div>
                    <div
                      v-if="section.key !== 'logic' && (item as EndpointComponent).bean"
                      class="item-meta"
                    >
                      {{ (item as EndpointComponent).bean }}{{ (item as EndpointComponent).method ? `.${(item as EndpointComponent).method}` : "" }}
                    </div>
                    <div
                      v-if="section.key !== 'logic' && (item as EndpointComponent).version"
                      class="item-tag"
                    >
                      版本 {{ (item as EndpointComponent).version }}
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </transition>
        </div>
        <div v-if="!componentSections.length" class="placeholder">暂无可用组件，请先在后端注册。</div>
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

.section-list {
  padding: 12px;
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
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
}

.section-title {
  font-weight: 600;
  flex: 1;
  text-align: left;
  color: #1f2937;
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
  gap: 8px;
  padding: 0 12px 12px;
}

.item-card {
  display: flex;
  gap: 10px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 10px;
  cursor: grab;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.item-card:active {
  cursor: grabbing;
}

.item-card:hover {
  border-color: #2563eb;
  transform: translateX(2px);
}

.item-card--disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: rgba(148, 163, 184, 0.05);
}

.item-card--disabled:hover {
  border-color: rgba(226, 232, 240, 0.8);
  transform: none;
}

.item-bullet {
  width: 10px;
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
  font-weight: 600;
  color: #0f172a;
}

.item-desc {
  font-size: 12px;
  color: #475569;
}

.item-meta {
  font-size: 11px;
  color: #94a3b8;
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

.item-badge {
  font-size: 11px;
  color: #94a3b8;
  font-weight: normal;
  margin-left: 4px;
}

.item-hint {
  font-size: 11px;
  color: #94a3b8;
  font-style: italic;
  margin-left: 4px;
}

/**
 * 层级结构样式
 * API 类作为类别，操作方法作为子项
 */
.item-card--category {
  background: rgba(22, 163, 74, 0.05);
  border-left: 3px solid #16a34a;
  font-weight: 600;
  cursor: default;
}

.item-card--category:hover {
  border-color: #16a34a;
  transform: none;
}

.item-card--child {
  margin-left: 20px;
  border-left: 2px solid rgba(148, 163, 184, 0.3);
  padding-left: 12px;
}

.category-children {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
  margin-bottom: 8px;
}

.item-bullet--child {
  width: 8px;
  height: 8px;
  margin-top: 6px;
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
