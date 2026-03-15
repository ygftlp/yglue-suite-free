<script setup lang="ts">
import { computed } from "vue"
import TransformerEditor from "./TransformerEditor.vue"
import ServiceNodeConfig from "./ServiceNodeConfig.vue"
import RestNodeConfig from "./RestNodeConfig.vue"
import ServiceGroupConfig from "./ServiceGroupConfig.vue"
import BranchConditionBuilder from "./BranchConditionBuilder.vue"
import SourcePathInput from "./SourcePathInput.vue"
import type { FlowModel, FlowResolver } from "../api/client"

type BranchTempVarKind = "ctx" | "const" | "expression"
interface BranchTempVarPlan {
  id: string
  key: string
  kind: BranchTempVarKind
  path?: string
  constValue?: string
  expression?: string
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

const branchTempVars = computed<BranchTempVarPlan[]>(() => {
  const list = props.selectedNode?.data?.tempVars
  if (!Array.isArray(list)) return []
  return list.map((item: any, index: number) => ({
    id: item?.id || `tmp_${Date.now()}_${index}`,
    key: String(item?.key ?? ""),
    kind: item?.kind === "ctx" || item?.kind === "const" || item?.kind === "expression" ? item.kind : "ctx",
    path: String(item?.path ?? ""),
    constValue: String(item?.constValue ?? ""),
    expression: String(item?.expression ?? ""),
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
    constValue: "",
    expression: "",
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
          <div class="bar" style="padding: 8px 10px; border-radius: 8px; border: 1px solid #e5e7eb">
            <div style="font-weight: 600; font-size: 12px">分支节点临时变量</div>
            <button class="btn mini" @click="addBranchTempVar">+ 添加变量</button>
          </div>

          <div v-if="branchTempVars.length === 0" class="tip">
            当前未配置临时变量，可先在此计算中间值，再在线条条件中引用。
          </div>

          <div v-for="(plan, idx) in branchTempVars" :key="plan.id" class="card" style="padding: 10px; gap: 8px">
            <div class="row" style="justify-content: space-between">
              <span class="muted small">变量 {{ idx + 1 }}</span>
              <button class="btn mini danger" @click="removeBranchTempVar(idx)">删除</button>
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
                  <option value="const">常量</option>
                  <option value="expression">表达式</option>
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

            <label v-if="plan.kind === 'expression'" class="field">
              <span>表达式</span>
              <input class="input" :value="plan.expression || ''" placeholder="例如 #{request.body.age ?: 0}" @input="updateBranchTempVar(idx, { expression: ($event.target as HTMLInputElement).value })" />
            </label>

            <div class="tip" style="margin-top: 0">建议在条件中按 `tempVar(变量Key)` 语义引用。</div>
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
.tip {
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border: 1px dashed rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 10px;
  line-height: 1.4;
}
</style>
