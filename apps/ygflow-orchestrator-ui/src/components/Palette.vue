<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue"
import { Book, ChevronDown, GitBranch, Layers, Search } from "lucide-vue-next"
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
  SERVICE: "服务",
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
  nodeType: "branch" | "transaction" | "transformer" | "serviceGroup"
  variant?: "begin" | "end"
}

const props = defineProps<{ projectKey: string }>()

const nav = ref<"flow" | "docs">("flow")
const expanded = ref<Record<string, boolean>>({})
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const componentGroups = ref<EndpointComponentGroup[]>([])
const searchKeyword = ref("")

const logicItems: LogicItem[] = [
  { id: "branch-node", title: "条件分支", description: LABELS.branchDesc, nodeType: "branch" },
  { id: "service-group", title: "服务组", description: "将多个服务组合在一起,可启用统一事务控制", nodeType: "serviceGroup" },
  { id: "txn-begin", title: "事务开始", description: "创建事务上下文", nodeType: "transaction", variant: "begin" },
  { id: "txn-end", title: "事务结束", description: "提交或回滚事务", nodeType: "transaction", variant: "end" },
  { id: "transformer-node", title: "脚本节点", description: "通过 Groovy 脚本处理数据转换和响应构造", nodeType: "transformer" },
]

function normalizeType(value?: string | null) {
  return String(value ?? "").toUpperCase()
}

/**
 * 从 FlowOperation 的 path 中提取所属的服务类名
 * path 格式：className#methodName 或 className:version
 */
function extractApiClassFromPath(path?: string): string | null {
  if (!path) return null
  // 处理 FlowOperation：className#methodName
  const hashIndex = path.indexOf("#")
  if (hashIndex > 0) {
    return path.substring(0, hashIndex)
  }
  // 处理 Service：className:version（去除版本号）
  const colonIndex = path.indexOf(":")
  if (colonIndex > 0) {
    return path.substring(0, colonIndex)
  }
  return path || null
}

/**
 * 从组件的 configJson 中提取服务类名（如果 path 无法提取）
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
 * - SERVICE 作为类别分组（不可拖拽，仅用于组织）
 * - FLOW_OPERATION 作为子项显示在对应的服务下面
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

    // 如果是业务组件，统一采用两级结构：第一级是服务，第二级是操作
    if (type === "BUSINESS") {
      const serviceItems = filteredItems.filter((item) => normalizeType(item.endpointType) === "SERVICE")
      const otherItems = filteredItems.filter(
        (item) => normalizeType(item.endpointType) !== "SERVICE"
      )

      // 统一采用两级结构：从 SERVICE 的 configJson 中解析 operations
      if (serviceItems.length > 0) {
        // 按服务分组操作方法
        const serviceMap = new Map<string, { service: EndpointComponent; operations: EndpointComponent[] }>()
        
        // 从每个 SERVICE 中解析 operations
        serviceItems.forEach((serviceItem) => {
          const serviceName =
            extractApiClassFromPath(serviceItem.path) ||
            extractApiClassFromConfig(serviceItem) ||
            serviceItem.bean ||
            serviceItem.displayName ||
            "未知服务"
          
          // 从 SERVICE 的 configJson 中解析 operations
          const operations: EndpointComponent[] = []
          try {
            const configJson = serviceItem.configJson
            if (typeof configJson === "string" && configJson) {
              const serviceConfig = JSON.parse(configJson)
              if (serviceConfig.operations && Array.isArray(serviceConfig.operations)) {
                // 将每个 operation 转换为 EndpointComponent
                serviceConfig.operations.forEach((op: any) => {
                  operations.push({
                    id: `${serviceItem.id}-${op.method}`,
                    type: "BUSINESS",
                    displayName: op.name || op.method || "操作",
                    description: op.description || "",
                    bean: serviceConfig.bean || serviceItem.bean,
                    method: op.method,
                    endpointType: "FLOW_OPERATION",
                    configJson: JSON.stringify({
                      ...op,
                      serviceBean: serviceConfig.bean,
                      serviceName: serviceConfig.name,
                      serviceClass: serviceConfig.class,
                    }),
                    path: serviceConfig.class ? `${serviceConfig.class}#${op.method}` : undefined,
                  })
                })
              }
            }
          } catch (e) {
            console.warn("Failed to parse service configJson:", e)
          }
          
          if (operations.length > 0) {
            serviceMap.set(serviceName, { service: serviceItem, operations })
          }
        })

        // 为每个服务创建一个分组（两级结构）
        serviceMap.forEach(({ service, operations }, serviceName) => {
          if (operations.length === 0) return // 跳过没有操作的服务
          
          const key = `components-business-service-${serviceName}-${groupIndex}`
          if (!(key in expanded.value)) expanded.value[key] = true
          
          sections.push({
            key,
            type: "BUSINESS",
            label: service?.displayName || service?.name || serviceName,
            color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
            icon: Layers,
            items: service ? [service] : [], // 服务本身（如果有，不可拖拽）
            isCategory: true,
            children: [
              {
                apiClass: serviceName,
                items: operations, // 操作方法（可拖拽）
              },
            ],
          })
        })

        // 如果有独立的服务（没有操作方法），单独显示
        serviceItems.forEach((serviceItem) => {
          const serviceName =
            extractApiClassFromPath(serviceItem.path) ||
            extractApiClassFromConfig(serviceItem) ||
            serviceItem.bean ||
            serviceItem.displayName ||
            "未知服务"
          if (!serviceMap.has(serviceName) || serviceMap.get(serviceName)!.operations.length === 0) {
            const key = `components-business-service-solo-${serviceName}-${groupIndex}`
            if (!(key in expanded.value)) expanded.value[key] = true
            sections.push({
              key,
              type: "BUSINESS",
              label: serviceItem.displayName || serviceItem.name || serviceName,
              color: typeColorMap[type] ?? paletteColors[groupIndex % paletteColors.length],
              icon: Layers,
              items: [serviceItem],
              isCategory: true,
            })
          }
        })
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

// 过滤后的组件分组（根据搜索关键词）
const filteredComponentSections = computed(() => {
  if (!searchKeyword.value.trim()) {
    return componentSections.value
  }
  
  const keyword = searchKeyword.value.toLowerCase().trim()
  return componentSections.value
    .map((section) => {
      if (section.isCategory && section.children) {
        // 两级结构：过滤操作项
        const filteredChildren = section.children.map((child) => ({
          ...child,
          items: child.items.filter((item) => {
            const name = displayName(item).toLowerCase()
            const desc = displayDesc(item, section.label).toLowerCase()
            const bean = (item.bean || "").toLowerCase()
            const method = (item.method || "").toLowerCase()
            return name.includes(keyword) || desc.includes(keyword) || bean.includes(keyword) || method.includes(keyword)
          }),
        })).filter((child) => child.items.length > 0)
        
        // 检查服务名称是否匹配
        const serviceMatch = section.items.some((item) => {
          const name = displayName(item).toLowerCase()
          const desc = displayDesc(item, section.label).toLowerCase()
          return name.includes(keyword) || desc.includes(keyword)
        })
        
        if (filteredChildren.length > 0 || serviceMatch) {
          return {
            ...section,
            children: filteredChildren.length > 0 ? filteredChildren : section.children,
          }
        }
        return null
      } else {
        // 普通列表：过滤项
        const filteredItems = section.items.filter((item) => {
          if (section.key === "logic") {
            const title = (item.title || "").toLowerCase()
            const desc = (item.description || "").toLowerCase()
            return title.includes(keyword) || desc.includes(keyword)
          } else {
            const name = displayName(item as EndpointComponent).toLowerCase()
            const desc = displayDesc(item as EndpointComponent, section.label).toLowerCase()
            const bean = ((item as EndpointComponent).bean || "").toLowerCase()
            const method = ((item as EndpointComponent).method || "").toLowerCase()
            return name.includes(keyword) || desc.includes(keyword) || bean.includes(keyword) || method.includes(keyword)
          }
        })
        
        if (filteredItems.length > 0) {
          return {
            ...section,
            items: filteredItems,
          }
        }
        return null
      }
    })
    .filter((section) => section !== null) as typeof componentSections.value
})

const sections = computed(() => [
  ...filteredComponentSections.value,
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
 * 判断是否为 SERVICE 类型的组件
 * SERVICE 是类级别组件，不能拖拽到画布（只有方法级别的 FlowOperation 可以拖拽）
 */
function isService(item: EndpointComponent): boolean {
  return normalizeType(item.endpointType) === "SERVICE"
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
      <!-- 搜索框 -->
      <div class="search-box">
        <Search :size="14" class="search-icon" />
        <input
          v-model="searchKeyword"
          type="text"
          class="search-input"
          placeholder="搜索服务或操作..."
        />
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
              <!-- 层级结构：服务名称已在顶部标题显示，这里直接显示操作方法 -->
              <template v-if="section.isCategory && section.children">
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
                  :class="{ 'item-card--disabled': isService(item) || section.isCategory }"
                  :draggable="!isService(item) && !section.isCategory"
                  @dragstart="handleDragStart($event, item, section.key)"
                >
                  <div class="item-bullet" :style="{ background: section.color }"></div>
                  <div class="item-content">
                    <div class="item-title">
                      {{ section.key === "logic" ? item.title : displayName(item as EndpointComponent) }}
                    </div>
                    <div class="item-desc">
                      {{ section.key === "logic" ? item.description : displayDesc(item as EndpointComponent, section.label) }}
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
  line-height: 1.4;
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
  position: relative;
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
  font-weight: 500;
  color: #1e293b;
  line-height: 1.4;
}

.item-card--child .item-title {
  font-weight: 500;
  color: #334155;
}

.item-desc {
  font-size: 11px;
  color: #64748b;
  line-height: 1.4;
  margin-top: 2px;
}

.item-card--child .item-desc {
  color: #64748b;
}

.item-meta {
  font-size: 10px;
  color: #94a3b8;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid rgba(226, 232, 240, 0.5);
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
  margin-left: 0;
  border-left: 3px solid rgba(148, 163, 184, 0.2);
  padding-left: 14px;
  background: #fafbfc;
}

.item-card--child:hover {
  border-left-color: #2563eb;
  background: #f1f5f9;
}

.category-children {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 2px;
}

.item-bullet--child {
  width: 6px;
  height: 6px;
  margin-top: 6px;
  flex-shrink: 0;
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
