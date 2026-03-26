<script setup lang="ts">
import { computed, ref, watch } from "vue"
import TransformerEditor from "./TransformerEditor.vue"
import ServiceNodeConfig from "./ServiceNodeConfig.vue"
import RestNodeConfig from "./RestNodeConfig.vue"
import ServiceGroupConfig from "./ServiceGroupConfig.vue"
import BranchConditionBuilder from "./BranchConditionBuilder.vue"
import ServiceCallEditor from "./ServiceCallEditor.vue"
import SourcePathInput from "./SourcePathInput.vue"
import type { FlowModel, FlowResolver } from "../api/client"

type BranchTempVarKind = "ctx" | "tempVar" | "const" | "expression" | "serviceCall"
interface BranchServiceCallConfig {
  fn: string
  argsText: string
  argsMode?: "list" | "json"
  args?: Array<Record<string, any>>
  serviceRef?: Record<string, any> | null
  argBindings?: Array<Record<string, any>>
}
interface BranchTempVarPlan {
  id: string
  key: string
  kind: BranchTempVarKind
  path?: string
  tempKey?: string
  constValue?: string
  expression?: string
  serviceResultPath?: string
  serviceCall?: BranchServiceCallConfig
}
interface BranchTempRef {
  rootKey: string
  path: string
}
interface BranchTempUsageInfo {
  label: string
  path: string
}
interface PreflightInsight {
  errors: string[]
  warnings: string[]
}

const props = defineProps<{
  selectedNode: any | null
  selectedEdge: any | null
  nodes?: any[] | null
  edges?: any[] | null
  endpointSchema?: {
    requestSchema?: Array<{ name: string; type: string; source?: string; pathVariable?: string; paramName?: string; formField?: string }> | null
    responseSchema?: { type?: string | null } | null
  } | null
  entrypointPath?: string | null
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
  projectKey?: string
  endpointId?: number
}>()

const emit = defineEmits<{
  (e: "update-node", node: any): void
  (e: "update-edge", edge: any): void
}>()

const resolverCatalog = computed(() => props.flowResolvers ?? [])
const isBranchNode = computed(() => props.selectedNode?.type === "branch" || props.selectedNode?.data?.branch)
const isTransformerNode = computed(() => props.selectedNode?.type === "transformer")
const isServiceGroupNode = computed(() => props.selectedNode?.type === "serviceGroup")
const isRestNode = computed(() => props.selectedNode?.type === "rest")
const isServiceNode = computed(() => props.selectedNode?.type === "service")
const hasSelection = computed(() => Boolean(props.selectedNode || props.selectedEdge))

const selectedEdgeSourceNode = computed(() => {
  if (!props.selectedEdge?.source || !Array.isArray(props.nodes)) return null
  return props.nodes.find((item: any) => item.id === props.selectedEdge.source) ?? null
})

const isBranchEdge = computed(() => {
  const node = selectedEdgeSourceNode.value
  return !!node && (node.type === "branch" || node.data?.branch)
})
const selectedBranchOutgoingEdges = computed(() => {
  if (!isBranchNode.value || !props.selectedNode?.id || !Array.isArray(props.edges)) return []
  return props.edges.filter((edge: any) => edge?.source === props.selectedNode.id)
})
const branchConditionInsight = ref<PreflightInsight>({ errors: [], warnings: [] })
const branchTempServiceInsights = ref<Record<string, PreflightInsight>>({})

function createBranchServiceCall(): BranchServiceCallConfig {
  return {
    fn: "",
    argsText: "[]",
    argsMode: "list",
    args: [],
    serviceRef: null,
    argBindings: [],
  }
}

const branchTempVars = computed<BranchTempVarPlan[]>(() => {
  const list = props.selectedNode?.data?.tempVars
  if (!Array.isArray(list)) return []
  return list.map((item: any, index: number) => ({
    id: item?.id || `tmp_legacy_${index}`,
    key: String(item?.key ?? ""),
    kind: item?.kind === "ctx" || item?.kind === "tempVar" || item?.kind === "const" || item?.kind === "expression" || item?.kind === "serviceCall"
      ? item.kind
      : "ctx",
    path: String(item?.path ?? ""),
    tempKey: String(item?.tempKey ?? ""),
    constValue: String(item?.constValue ?? ""),
    expression: String(item?.expression ?? ""),
    serviceResultPath: String(item?.serviceResultPath ?? item?.resultPath ?? ""),
    serviceCall: { ...createBranchServiceCall(), ...(item?.serviceCall || {}) },
  }))
})

const edgeBranchTempVarKeys = computed(() => {
  const node = selectedEdgeSourceNode.value
  const list = node?.data?.tempVars
  if (!Array.isArray(list)) return []
  return list.map((item: any) => String(item?.key ?? "").trim()).filter(Boolean)
})

const requestPathOptions = computed(() => {
  const fields = Array.isArray(props.endpointSchema?.requestSchema) ? props.endpointSchema?.requestSchema : []
  const values = fields.map((field) => {
    const source = String(field?.source || "").trim().toLowerCase()
    const name = String(field?.pathVariable || field?.paramName || field?.formField || field?.name || "").trim()
    if (!name) return ""
    if (source === "path") return `request.path.${name}`
    if (source === "query") return `request.query.${name}`
    if (source === "header") return `request.headers.${name}`
    return `request.body.${name}`
  }).filter(Boolean)
  return Array.from(new Set(values))
})

function branchTempVarKeysBefore(index: number): string[] {
  return branchTempVars.value
    .slice(0, index)
    .map((item) => String(item?.key ?? "").trim())
    .filter(Boolean)
}

function extractBranchTempRootKey(raw: string): string {
  const text = String(raw || "").trim()
  if (!text) return ""
  return text.replace(/^tempVar\(/, "").replace(/\)$/, "").split(".")[0].trim()
}

function collectBranchTempRefs(raw: any, refs: BranchTempRef[]) {
  if (Array.isArray(raw)) {
    raw.forEach((item) => collectBranchTempRefs(item, refs))
    return
  }
  if (!raw || typeof raw !== "object") return
  const record = raw as Record<string, any>
  const kind = String(record.kind || "").trim().toLowerCase()
  if (kind === "tempvar") {
    const tempPath = String(record.tempKey || "").trim()
    const rootKey = extractBranchTempRootKey(tempPath)
    if (rootKey) refs.push({ rootKey, path: tempPath || rootKey })
  }
  Object.values(record).forEach((value) => collectBranchTempRefs(value, refs))
}

function uniqueTextList(items: string[]): string[] {
  const result: string[] = []
  const seen = new Set<string>()
  items.forEach((item) => {
    const text = String(item || "").trim()
    if (!text || seen.has(text)) return
    seen.add(text)
    result.push(text)
  })
  return result
}

function normalizeInsight(raw: any): PreflightInsight {
  return {
    errors: uniqueTextList(Array.isArray(raw?.errors) ? raw.errors.map((item: any) => String(item || "")) : []),
    warnings: uniqueTextList(Array.isArray(raw?.warnings) ? raw.warnings.map((item: any) => String(item || "")) : []),
  }
}

function branchEdgeDisplay(edge: any, index: number): string {
  const label = String(edge?.data?.label || "").trim()
  const priority = Number.isFinite(Number(edge?.data?.priority)) ? Number(edge.data.priority) : 100
  return label ? `分支 ${index + 1}(${label})` : `分支 ${index + 1}(优先级 ${priority})`
}

const branchTempVarInfo = computed(() => {
  const plans = branchTempVars.value
  const refsPerPlan = plans.map((plan) => {
    const refs: BranchTempRef[] = []
    collectBranchTempRefs(plan, refs)
    return refs
  })

  const keyToIndexes = new Map<string, number[]>()
  plans.forEach((plan, index) => {
    const key = String(plan.key || "").trim()
    if (!key) return
    const hit = keyToIndexes.get(key) || []
    hit.push(index)
    keyToIndexes.set(key, hit)
  })

  return plans.map((plan, index) => {
    const key = String(plan.key || "").trim()
    const refs = refsPerPlan[index]
    const warnings: string[] = []
    if (!key) {
      warnings.push("变量 Key 不能为空。")
    }
    const duplicateIndexes = key ? (keyToIndexes.get(key) || []) : []
    if (key && duplicateIndexes.length > 1) {
      warnings.push(`变量 Key ${key} 重复，后续条件引用会产生歧义。`)
    }
    refs.forEach((ref) => {
      if (key && ref.rootKey === key) {
        warnings.push(`当前变量引用了自身 ${ref.path}，会形成自依赖。`)
        return
      }
      const targetIndexes = keyToIndexes.get(ref.rootKey) || []
      if (targetIndexes.length === 0) {
        warnings.push(`当前变量引用了未定义的前序变量 ${ref.path}。`)
        return
      }
      if (targetIndexes.every((targetIndex) => targetIndex >= index)) {
        warnings.push(`当前变量引用了后置变量 ${ref.path}，建议调整顺序。`)
      }
    })

    const consumers = new Map<string, BranchTempUsageInfo>()
    if (key) {
      refsPerPlan.slice(index + 1).forEach((planRefs, planOffset) => {
        const targetIndex = index + planOffset + 1
        planRefs
          .filter((ref) => ref.rootKey === key)
          .forEach((ref) => {
            const label = `变量 ${targetIndex + 1}${plans[targetIndex]?.key ? `(${plans[targetIndex].key})` : ""}`
            consumers.set(`${label}|${ref.path}`, { label, path: ref.path })
          })
      })
      selectedBranchOutgoingEdges.value.forEach((edge, edgeIndex) => {
        const refsInCondition: BranchTempRef[] = []
        collectBranchTempRefs(edge?.data?.conditionV2 || edge?.data?.condition || null, refsInCondition)
        refsInCondition
          .filter((ref) => ref.rootKey === key)
          .forEach((ref) => {
            const label = branchEdgeDisplay(edge, edgeIndex)
            consumers.set(`${label}|${ref.path}`, { label, path: ref.path })
          })
      })
    }

    return {
      refs,
      warnings,
      consumers: Array.from(consumers.values()),
    }
  })
})
const branchNodePreflight = computed(() => {
  const errors: string[] = []
  const warnings: string[] = []

  branchTempVarInfo.value.forEach((info, index) => {
    info.warnings.forEach((msg) => warnings.push(`变量 ${index + 1}: ${msg}`))
  })
  branchTempVars.value.forEach((plan, index) => {
    const insight = branchTempServiceInsights.value[plan.id]
    if (!insight) return
    insight.errors.forEach((msg) => errors.push(`变量 ${index + 1} 服务调用: ${msg}`))
    insight.warnings.forEach((msg) => warnings.push(`变量 ${index + 1} 服务调用: ${msg}`))
  })

  return {
    errors: uniqueTextList(errors),
    warnings: uniqueTextList(warnings),
  }
})
const branchNodePreflightState = computed(() => {
  if (branchNodePreflight.value.errors.length > 0) return "error"
  if (branchNodePreflight.value.warnings.length > 0) return "warn"
  return "pass"
})
const branchEdgePreflight = computed(() => normalizeInsight(branchConditionInsight.value))
const branchEdgePreflightState = computed(() => {
  if (branchEdgePreflight.value.errors.length > 0) return "error"
  if (branchEdgePreflight.value.warnings.length > 0) return "warn"
  return "pass"
})

function getBranchTempVarInfo(index: number) {
  return branchTempVarInfo.value[index] || { refs: [], warnings: [], consumers: [] }
}

watch(() => props.selectedNode?.id, () => {
  branchTempServiceInsights.value = {}
})

watch(() => props.selectedEdge?.id, () => {
  branchConditionInsight.value = { errors: [], warnings: [] }
})

function cloneNode() {
  return JSON.parse(JSON.stringify(props.selectedNode))
}

function cloneEdge() {
  return JSON.parse(JSON.stringify(props.selectedEdge))
}

function mutateNode(updater: (next: any) => void) {
  if (!props.selectedNode) return
  const next = cloneNode()
  updater(next)
  emit("update-node", next)
}

function mutateEdge(updater: (next: any) => void) {
  if (!props.selectedEdge) return
  const next = cloneEdge()
  updater(next)
  next.label = next.data?.label || ""
  emit("update-edge", next)
}

function updateNodeField(key: string, value: any) {
  mutateNode((next) => {
    next.data ||= {}
    next.data[key] = value
  })
}

function updateEdgeField(partial: Record<string, any>) {
  mutateEdge((next) => {
    next.data = { ...(next.data || {}), ...partial }
  })
}

function createBranchTempVarPlan(): BranchTempVarPlan {
  return {
    id: `tmp_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
    key: "",
    kind: "ctx",
    path: "request.body.xxx",
    tempKey: "",
    constValue: "",
    expression: "",
    serviceResultPath: "",
    serviceCall: createBranchServiceCall(),
  }
}

function addBranchTempVar() {
  mutateNode((next) => {
    next.data ||= {}
    const list = Array.isArray(next.data.tempVars) ? [...next.data.tempVars] : []
    list.push(createBranchTempVarPlan())
    next.data.tempVars = list
  })
}

function updateBranchTempVar(index: number, partial: Partial<BranchTempVarPlan>) {
  mutateNode((next) => {
    next.data ||= {}
    const list = Array.isArray(next.data.tempVars) ? [...next.data.tempVars] : []
    const current = list[index] || createBranchTempVarPlan()
    list[index] = { ...current, ...partial }
    next.data.tempVars = list
  })
}

function removeBranchTempVar(index: number) {
  mutateNode((next) => {
    next.data ||= {}
    const list = Array.isArray(next.data.tempVars) ? [...next.data.tempVars] : []
    next.data.tempVars = list.filter((_: any, i: number) => i !== index)
  })
}

function moveBranchTempVar(index: number, offset: number) {
  mutateNode((next) => {
    next.data ||= {}
    const list = Array.isArray(next.data.tempVars) ? [...next.data.tempVars] : []
    const targetIndex = index + offset
    if (index < 0 || targetIndex < 0 || targetIndex >= list.length) return
    const [current] = list.splice(index, 1)
    list.splice(targetIndex, 0, current)
    next.data.tempVars = list
  })
}

function canMoveBranchTempVar(index: number, offset: number): boolean {
  const targetIndex = index + offset
  return targetIndex >= 0 && targetIndex < branchTempVars.value.length
}

function handleBranchConditionInsightChange(next: PreflightInsight) {
  branchConditionInsight.value = normalizeInsight(next)
}

function handleBranchTempServiceInsightChange(planId: string, next: PreflightInsight) {
  branchTempServiceInsights.value = {
    ...branchTempServiceInsights.value,
    [planId]: normalizeInsight(next),
  }
}
</script>

<template>
  <div v-if="!hasSelection" class="center">请选择节点或连线查看配置。</div>
  <div v-else class="col" style="height: 100%">
    <div class="bar">
      <div style="font-weight: 600; font-size: 13px">{{ selectedEdge ? "连线配置" : "节点配置" }}</div>
    </div>

    <div style="padding: 12px" class="col">
      <template v-if="selectedEdge">
        <div>
          <div class="muted">线条标签</div>
          <input
            class="input"
            placeholder="例如：命中 / 默认分支"
            :value="selectedEdge.data?.label || ''"
            @input="updateEdgeField({ label: ($event.target as HTMLInputElement).value })"
          />
        </div>

        <template v-if="isBranchEdge">
          <div class="preflight-card" :class="branchEdgePreflightState">
            <div class="preflight-head">
              <span>分支条件预检</span>
              <span class="muted tiny">错误 {{ branchEdgePreflight.errors.length }} / 告警 {{ branchEdgePreflight.warnings.length }}</span>
            </div>
            <div v-if="branchEdgePreflightState === 'pass'" class="preflight-pass">当前分支条件未发现明显阻塞项。</div>
            <div v-else class="preflight-list">
              <div v-for="msg in branchEdgePreflight.errors.slice(0, 3)" :key="`branch-edge-error-${msg}`" class="preflight-item error">{{ msg }}</div>
              <div v-for="msg in branchEdgePreflight.warnings.slice(0, 5)" :key="`branch-edge-warning-${msg}`" class="preflight-item warn">{{ msg }}</div>
            </div>
          </div>

          <div>
            <div class="muted">分支优先级（数值越小越优先）</div>
            <input
              class="input"
              type="number"
              :value="selectedEdge.data?.priority ?? 100"
              @input="updateEdgeField({ priority: Number.parseInt(($event.target as HTMLInputElement).value || '100', 10) || 100 })"
            />
          </div>

          <BranchConditionBuilder
            :model-value="selectedEdge.data?.conditionV2"
            :project-key="props.projectKey"
            :temp-var-keys="edgeBranchTempVarKeys"
            :source-path-options="requestPathOptions"
            @update:model-value="updateEdgeField({ conditionV2: $event })"
            @insight-change="handleBranchConditionInsightChange"
          />

          <div class="tip">分支条件配置在线条上，执行时按优先级排序后命中第一条。</div>
        </template>

        <div v-else class="tip" style="margin-top: 8px">当前线条不是分支出口，仅支持标签说明。</div>
      </template>

      <template v-else-if="isTransformerNode">
        <TransformerEditor
          :selected-node="selectedNode"
          :nodes="props.nodes"
          :edges="props.edges"
          :endpoint-schema="props.endpointSchema"
          :entrypoint-path="props.entrypointPath"
          :flow-models="props.flowModels ?? []"
          :flow-resolvers="resolverCatalog"
          @update-node="emit('update-node', $event)"
        />
      </template>

      <template v-else-if="isServiceGroupNode">
        <div>
          <div class="muted">服务组名称</div>
          <input
            class="input"
            :value="selectedNode.data?.label || ''"
            @input="updateNodeField('label', ($event.target as HTMLInputElement).value)"
          />
        </div>
        <div style="margin-top: 16px; padding-top: 16px; border-top: 1px solid #e5e7eb">
          <ServiceGroupConfig
            :label="selectedNode.data?.label"
            :enable-transaction="selectedNode.data?.enableTransaction"
            :tx-mode="selectedNode.data?.txMode"
            :transaction-manager="selectedNode.data?.transactionManager"
            @update:label="updateNodeField('label', $event)"
            @update:enable-transaction="updateNodeField('enableTransaction', $event)"
            @update:tx-mode="updateNodeField('txMode', $event)"
            @update:transaction-manager="updateNodeField('transactionManager', $event)"
          />
        </div>
      </template>

      <template v-else-if="isBranchNode">
        <div class="col" style="gap: 10px">
          <div class="preflight-card" :class="branchNodePreflightState">
            <div class="preflight-head">
              <span>分支变量预检</span>
              <span class="muted tiny">错误 {{ branchNodePreflight.errors.length }} / 告警 {{ branchNodePreflight.warnings.length }}</span>
            </div>
            <div v-if="branchNodePreflightState === 'pass'" class="preflight-pass">当前分支变量配置未发现明显阻塞项。</div>
            <div v-else class="preflight-list">
              <div v-for="msg in branchNodePreflight.errors.slice(0, 4)" :key="`branch-node-error-${msg}`" class="preflight-item error">{{ msg }}</div>
              <div v-for="msg in branchNodePreflight.warnings.slice(0, 6)" :key="`branch-node-warning-${msg}`" class="preflight-item warn">{{ msg }}</div>
            </div>
          </div>

          <div class="bar" style="padding: 8px 10px; border-radius: 8px; border: 1px solid #e5e7eb">
            <div style="font-weight: 600; font-size: 12px">分支节点临时变量</div>
            <button class="btn mini" @click="addBranchTempVar">+ 添加变量</button>
          </div>

          <div v-if="branchTempVars.length === 0" class="tip">
            当前未配置临时变量，可先在此计算中间值，再在线条条件中引用。分支变量会按顺序依次求值，后面的变量可以引用前面已经准备好的结果。
          </div>

          <div v-for="(plan, idx) in branchTempVars" :key="plan.id" class="card" style="padding: 10px; gap: 8px">
            <div class="row" style="justify-content: space-between">
              <span class="muted small">变量 {{ idx + 1 }}</span>
              <div class="row" style="gap: 6px; flex-wrap: wrap">
                <button class="btn mini" :disabled="!canMoveBranchTempVar(idx, -1)" @click="moveBranchTempVar(idx, -1)">上移</button>
                <button class="btn mini" :disabled="!canMoveBranchTempVar(idx, 1)" @click="moveBranchTempVar(idx, 1)">下移</button>
                <button class="btn mini danger" @click="removeBranchTempVar(idx)">删除</button>
              </div>
            </div>

            <div class="row" style="gap: 8px; flex-wrap: wrap">
              <span class="badge">输出 {{ plan.key || "未命名" }}</span>
              <span class="badge" v-if="getBranchTempVarInfo(idx).refs.length > 0">
                依赖 {{ getBranchTempVarInfo(idx).refs.map((item) => item.path).join(" , ") }}
              </span>
              <span class="badge" v-else>依赖 无</span>
              <span class="badge ready" v-if="getBranchTempVarInfo(idx).consumers.length > 0">
                下游使用 {{ getBranchTempVarInfo(idx).consumers.length }} 处
              </span>
              <span class="badge" v-else>下游使用 0 处</span>
            </div>

            <div class="row" style="gap: 8px; flex-wrap: wrap">
              <label class="field" style="flex: 1 1 160px">
                <span>变量 Key</span>
                <input class="input" :value="plan.key" placeholder="例如 userAge" @input="updateBranchTempVar(idx, { key: ($event.target as HTMLInputElement).value })" />
              </label>
              <label class="field" style="flex: 0 0 180px">
                <span>来源</span>
                <select class="input" :value="plan.kind" @change="updateBranchTempVar(idx, { kind: ($event.target as HTMLSelectElement).value as any })">
                  <option value="ctx">上下文路径</option>
                  <option value="tempVar">前序变量</option>
                  <option value="const">常量</option>
                  <option value="expression">表达式</option>
                  <option value="serviceCall">服务调用</option>
                </select>
              </label>
            </div>

            <label v-if="plan.kind === 'ctx'" class="field">
              <span>上下文路径</span>
              <SourcePathInput
                :model-value="plan.path || ''"
                :options="requestPathOptions"
                placeholder="例如 request.body.userId"
                @update:model-value="updateBranchTempVar(idx, { path: $event })"
              />
            </label>

            <label v-if="plan.kind === 'const'" class="field">
              <span>常量值</span>
              <input class="input" :value="plan.constValue || ''" placeholder="例如 ACTIVE / 18 / true" @input="updateBranchTempVar(idx, { constValue: ($event.target as HTMLInputElement).value })" />
            </label>

            <label v-if="plan.kind === 'tempVar'" class="field">
              <span>前序变量</span>
              <input
                class="input"
                :value="plan.tempKey || ''"
                :list="`branch-temp-keys-${idx}`"
                placeholder="例如 userProfile.level / threshold"
                @input="updateBranchTempVar(idx, { tempKey: ($event.target as HTMLInputElement).value })"
              />
              <datalist :id="`branch-temp-keys-${idx}`">
                <option v-for="key in branchTempVarKeysBefore(idx)" :key="`${idx}_${key}`" :value="key" />
              </datalist>
            </label>

            <label v-if="plan.kind === 'expression'" class="field">
              <span>表达式</span>
              <input class="input" :value="plan.expression || ''" placeholder="例如 #{request.body.age ?: 0}" @input="updateBranchTempVar(idx, { expression: ($event.target as HTMLInputElement).value })" />
            </label>

            <template v-if="plan.kind === 'serviceCall'">
              <ServiceCallEditor
                :model-value="plan.serviceCall"
                :project-key="props.projectKey"
                :temp-keys="branchTempVarKeysBefore(idx)"
                :source-path-options="requestPathOptions"
                @update:model-value="updateBranchTempVar(idx, { serviceCall: $event })"
                @insight-change="handleBranchTempServiceInsightChange(plan.id, $event)"
              />
              <label class="field">
                <span>结果提取路径</span>
                <input
                  class="input"
                  :value="plan.serviceResultPath || ''"
                  placeholder="例如 data.status / $.data.score"
                  @input="updateBranchTempVar(idx, { serviceResultPath: ($event.target as HTMLInputElement).value })"
                />
              </label>
            </template>

            <div
              v-if="getBranchTempVarInfo(idx).warnings.length > 0"
              style="display: flex; flex-direction: column; gap: 6px;"
            >
              <div
                v-for="msg in getBranchTempVarInfo(idx).warnings"
                :key="msg"
                style="padding: 8px 10px; border-radius: 8px; border: 1px solid rgba(245, 158, 11, 0.35); background: rgba(255, 251, 235, 0.92); color: #92400e; font-size: 12px; line-height: 1.45;"
              >
                {{ msg }}
              </div>
            </div>

            <div
              v-if="getBranchTempVarInfo(idx).consumers.length > 0"
              style="display: flex; flex-direction: column; gap: 6px;"
            >
              <div
                v-for="consumer in getBranchTempVarInfo(idx).consumers.slice(0, 4)"
                :key="`${consumer.label}_${consumer.path}`"
                style="display: flex; justify-content: space-between; gap: 10px; flex-wrap: wrap; padding: 8px 10px; border-radius: 8px; border: 1px solid rgba(37, 99, 235, 0.16); background: rgba(239, 246, 255, 0.72);"
              >
                <span style="font-size: 11px; color: #1e3a8a;">{{ consumer.label }}</span>
                <span style="font-size: 11px; color: #475569; word-break: break-all;">{{ consumer.path }}</span>
              </div>
              <div v-if="getBranchTempVarInfo(idx).consumers.length > 4" class="muted small">
                还有 {{ getBranchTempVarInfo(idx).consumers.length - 4 }} 处引用，建议保持 Key 稳定并避免重复命名。
              </div>
            </div>
            <div v-else class="muted small">
              当前变量还没有被后续分支变量或分支条件使用，适合继续整理顺序或直接删除。
            </div>

            <div class="tip" style="margin-top: 0">
              当前分支变量按定义顺序依次求值。
              <template v-if="plan.kind === 'tempVar' || plan.kind === 'serviceCall'">
                这一项只建议引用前面已经定义好的变量，避免出现空值或顺序依赖问题。
              </template>
              建议在线条条件中按 `tempVar(变量Key)` 语义引用。
            </div>
          </div>
        </div>
      </template>

      <template v-else-if="isRestNode">
        <RestNodeConfig
          :node-data="selectedNode?.data"
          @update:node-data="mutateNode((n) => (n.data = $event))"
        />
      </template>

      <template v-else-if="isServiceNode">
        <ServiceNodeConfig
          :label="selectedNode.data.label"
          :comp="selectedNode.data.comp"
          :inputs="selectedNode.data.inputs"
          :output="selectedNode.data.output"
          :param-plans="selectedNode.data.paramPlans"
          :project-key="projectKey"
          :endpoint-id="endpointId"
          :node-id="selectedNode.id"
          :nodes="nodes"
          :edges="edges"
          @update:label="mutateNode((n) => (n.data.label = $event))"
          @update:comp="mutateNode((n) => (n.data.comp = $event))"
          @update:inputs="mutateNode((n) => (n.data.inputs = $event))"
          @update:output="mutateNode((n) => (n.data.output = $event))"
          @update:paramPlans="mutateNode((n) => (n.data.paramPlans = $event))"
        />
      </template>

      <template v-else>
        <div class="tip">当前节点类型暂未提供专用配置面板。</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.badge {
  font-size: 11px;
  border-radius: 999px;
  padding: 3px 9px;
  border: 1px solid #dbe2ea;
  background: #f8fafc;
  color: #475569;
}

.badge.ready {
  color: #0b3b8a;
  border-color: rgba(37, 99, 235, 0.25);
  background: rgba(37, 99, 235, 0.08);
}

.tip {
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border: 1px dashed rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 10px;
  line-height: 1.4;
}

.preflight-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  background: #ffffff;
}

.preflight-card.pass {
  border-color: rgba(34, 197, 94, 0.25);
  background: rgba(240, 253, 244, 0.92);
}

.preflight-card.warn {
  border-color: rgba(245, 158, 11, 0.28);
  background: rgba(255, 251, 235, 0.95);
}

.preflight-card.error {
  border-color: rgba(239, 68, 68, 0.24);
  background: rgba(254, 242, 242, 0.96);
}

.preflight-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.preflight-pass {
  font-size: 12px;
  color: #166534;
  line-height: 1.5;
}

.preflight-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preflight-item {
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.45;
}

.preflight-item.error {
  border: 1px solid rgba(239, 68, 68, 0.2);
  background: rgba(254, 226, 226, 0.7);
  color: #991b1b;
}

.preflight-item.warn {
  border: 1px solid rgba(245, 158, 11, 0.2);
  background: rgba(255, 247, 237, 0.84);
  color: #9a3412;
}
</style>
