<script setup lang="ts">
import { computed, ref } from "vue"
import TransformerEditor from "./TransformerEditor.vue"
import ServiceNodeConfig from "./ServiceNodeConfig.vue"
import ServiceGroupConfig from "./ServiceGroupConfig.vue"
import { transactionManagers } from "../data/transactionManagers"
import type { FlowModel, FlowResolver } from "../api/client"

/**
 * 校验规则类型
 */
type ValidatorType = 
  | "required" 
  | "notEmpty" 
  | "notBlank" 
  | "type" 
  | "range" 
  | "length" 
  | "regex" 
  | "expression" 
  | "custom"

/**
 * 校验规则
 */
interface ValidationRule {
  id: string
  type: ValidatorType
  enabled: boolean
  message?: string
  config?: Record<string, any>
}

type IOType = "inputs"

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
  projectKey?: string  // 项目标识，用于加载项目类信息
  endpointId?: number  // 端点ID，用于加载端点相关类信息
}>()

const emit = defineEmits<{
  (e: "update-node", node: any): void
  (e: "update-edge", edge: any): void
}>()

const modelOptions = computed(() =>
  (props.flowModels ?? []).map((model) => ({
    label: model.name ?? model.identifier,
    value: model.className || model.identifier,
  }))
)

const resolverCatalog = computed(() => props.flowResolvers ?? [])

const isBranchNode = computed(() => props.selectedNode?.type === "branch" || props.selectedNode?.data?.branch)
const isTransformerNode = computed(() => props.selectedNode?.type === "transformer")
const isServiceGroupNode = computed(() => props.selectedNode?.type === "serviceGroup")
const allowCustomIO = computed(() => !props.selectedNode?.data?.comp)
const hasSelection = computed(() => Boolean(props.selectedNode || props.selectedEdge))
const outputInfo = computed(() => props.selectedNode?.data?.output || null)

// 脚本编辑器 refs
const scriptEditorRefs = ref<Array<{ openCodeEditor: () => void } | null>>([])

// 校验器编辑器 refs
const validationEditorRefs = ref<Array<{ open: () => void } | null>>([])

// 当前编辑的校验器索引和规则
const editingValidatorIndex = ref<number | null>(null)
const editingValidatorRule = ref<ValidationRule | null>(null)

// 已移除条件分支连线功能

function cloneNode() {
  return JSON.parse(JSON.stringify(props.selectedNode))
}

function cloneEdge() {
  return JSON.parse(JSON.stringify(props.selectedEdge))
}

function ensureArray(next: any, key: IOType) {
  next.data ||= {}
  next.data[key] ||= []
}

// 已移除条件分支连线功能

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
  const display = next.data?.label || ""
  next.label = display
  emit("update-edge", next)
}

function updateNodeField(key: string, value: any) {
  mutateNode((next) => {
    next.data ||= {}
    next.data[key] = value
  })
}

function updateCompField(partial: Record<string, any>) {
  mutateNode((next) => {
    next.data ||= {}
    next.data.comp = { ...(next.data.comp || {}), ...partial }
  })
}

function addIO(type: IOType) {
  mutateNode((next) => {
    ensureArray(next, type)
    const defaults = {
      name: "",
      valueType: "STRING",
      typeName: "",
      transformer: "",
      resolver: { type: "request", path: "", cast: "STRING", default: "" },
      converter: {
        kind: "GENERAL",
        targetType: "STRING",
        targetTypeName: "",
        script: "",
        arrayElementType: "STRING",
        arrayElementTypeName: "",
      },
    }
    next.data[type].push(defaults)
  })
}

function updateIO(type: IOType, index: number, partial: Record<string, any>) {
  mutateNode((next) => {
    ensureArray(next, type)
    const list = next.data[type]
    list[index] = { ...(list[index] || {}), ...partial }
  })
}

function removeIO(type: IOType, index: number) {
  mutateNode((next) => {
    ensureArray(next, type)
    next.data[type].splice(index, 1)
  })
}

function getInputScript(input: any): string {
  if (!input) return ""
  // 优先使用 script，如果没有则使用 transformer（兼容旧数据）
  return input.script || input.transformer || ""
}

function getInputValidators(input: any): ValidationRule[] {
  if (!input || !input.validators) return []
  return Array.isArray(input.validators) ? input.validators : []
}

function addValidator(inputIndex: number) {
  const newRule: ValidationRule = {
    id: `validator-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    type: "required",
    enabled: true,
  }
  
  mutateNode((next) => {
    ensureArray(next, "inputs")
    const input = next.data.inputs[inputIndex]
    if (!input.validators) {
      input.validators = []
    }
    input.validators.push(newRule)
  })
}

function removeValidator(inputIndex: number, validatorIndex: number) {
  mutateNode((next) => {
    ensureArray(next, "inputs")
    const input = next.data.inputs[inputIndex]
    if (input.validators) {
      input.validators.splice(validatorIndex, 1)
    }
  })
}

function editValidator(inputIndex: number, validatorIndex: number) {
  const input = props.selectedNode?.data?.inputs?.[inputIndex]
  if (!input) return
  
  const validators = getInputValidators(input)
  const rule = validators[validatorIndex]
  if (!rule) return
  
  editingValidatorIndex.value = validatorIndex
  editingValidatorRule.value = { ...rule }
  
  // 打开校验器编辑器
  const editorRef = validationEditorRefs.value[inputIndex]
  if (editorRef) {
    editorRef.open()
  }
}

function saveValidator(inputIndex: number, updatedRule: ValidationRule) {
  mutateNode((next) => {
    ensureArray(next, "inputs")
    const input = next.data.inputs[inputIndex]
    if (!input.validators) {
      input.validators = []
    }
    
    const validatorIndex = editingValidatorIndex.value
    if (validatorIndex !== null && validatorIndex >= 0 && validatorIndex < input.validators.length) {
      input.validators[validatorIndex] = updatedRule
    }
  })
  
  editingValidatorIndex.value = null
  editingValidatorRule.value = null
}

function cancelEditValidator() {
  editingValidatorIndex.value = null
  editingValidatorRule.value = null
}

function getValidatorTypeLabel(type: ValidatorType): string {
  const labels: Record<ValidatorType, string> = {
    required: "必填",
    notEmpty: "非空",
    notBlank: "非空白",
    type: "类型",
    range: "范围",
    length: "长度",
    regex: "正则",
    expression: "表达式",
    custom: "自定义",
  }
  return labels[type] || type
}

function updateInputScript(index: number, script: string) {
  updateIO("inputs", index, { script, transformer: script })
}

function getInputScriptVariableGroups(input: any) {
  const groups = [
    {
      title: "请求参数",
      items: [
        { label: "request.path.xxx", snippet: "request.path.xxx", description: "路径变量，如 request.path.projectKey" },
        { label: "request.query.xxx", snippet: "request.query.xxx", description: "查询参数，如 request.query.page" },
        { label: "request.body.xxx", snippet: "request.body.xxx", description: "请求体字段，如 request.body.name" },
        { label: "request.headers.xxx", snippet: "request.headers.xxx", description: "请求头，如 request.headers.Authorization" },
      ],
    },
    {
      title: "流程上下文",
      items: [
        { label: "ctx", snippet: "ctx", description: "流程上下文，可读写共享变量，如 ctx['userId']" },
        { label: "ctx['_lastNodeResult']", snippet: "ctx['_lastNodeResult']", description: "最后一个节点的输出结果" },
        { label: "ctx['_node_xxx']", snippet: "ctx['_node_xxx']", description: "指定节点ID的输出结果" },
      ],
    },
  ]
  return groups
}

function getInputScriptFunctionGroups() {
  return [
    {
      title: "内置函数",
      items: [
        { label: "jsonPath(value, path)", snippet: "jsonPath(request.body, \"$.data.field\")", description: "按 JSONPath 提取字段，适合 JSON 结构快速取值。" },
        { label: "assert(condition, message)", snippet: "assert(request.path.projectKey != null, \"项目标识不能为空\")", description: "当条件不满足时抛出异常，中断后续执行。" },
        { label: "formatDate(value, pattern)", snippet: "formatDate(request.body.orderTime, \"yyyy-MM-dd HH:mm:ss\")", description: "格式化日期/时间对象为指定字符串。" },
        { label: "safeNumber(value, defaultValue)", snippet: "safeNumber(request.query.page, 1)", description: "安全转换为数字，无法转换时给定默认值。" },
      ],
    },
  ]
}

/**
 * 格式化输出类型显示
 * 优先显示具体的Java类型（typeName），如果没有则显示通用类型
 */
function formatOutputType(output: any) {
  const type = (output?.valueType || "OBJECT").toUpperCase()
  // 如果有具体的Java类型名称，优先显示
  if (output?.typeName) {
    return output.typeName
  }
  // 对于OBJECT和ARRAY类型，如果没有typeName，显示通用类型
  if (type === "OBJECT" || type === "ARRAY") {
    return type
  }
  // 基础类型直接返回
  return type
}

function updateOutputField(partial: Record<string, any>) {
  mutateNode((next) => {
    next.data ||= {}
    next.data.output = { ...(next.data.output || {}), ...partial }
  })
}

function isObjectOutput(output: any) {
  return (output?.valueType || "").toUpperCase() === "OBJECT"
}

function formatInputType(input: any) {
  const type = (input?.valueType || "STRING").toUpperCase()
  if (type === "OBJECT" || type === "ARRAY") {
    return input?.typeName || type
  }
  return type
}

function updateInputType(index: number, value: string) {
  updateIO("inputs", index, { valueType: value })
}

function updateInputTypeName(index: number, value: string) {
  updateIO("inputs", index, { typeName: value })
}


function updateEdgeField(partial: Record<string, any>) {
  mutateEdge((next) => {
    next.data = { ...(next.data || {}), ...partial }
  })
}

/**
 * 获取 Service 的 bean 名称
 * bean 名称的优先级：
 * 1. 从 Spring 注解（@Service、@Component）的 value 属性获取
 * 2. 从 @FlowApi 注解的 value 属性获取
 * 3. 从 @FlowApi 注解的 name 属性获取
 * 4. 默认使用类名首字母小写
 * 
 * 对于 SERVICE 类型：从 configJson 中解析 bean 字段
 * 对于 FLOW_OPERATION 类型：从 configJson 中解析 serviceBean 字段
 */
function getServiceName(comp: any): string | null {
  if (!comp) return null
  
  const endpointType = comp.endpointType
  
  // 如果是 SERVICE 类型，从 configJson 中解析 bean
  if (endpointType === "SERVICE") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        if (config.bean && typeof config.bean === "string") {
          return config.bean
        }
        // 回退到使用 name
        if (config.name && typeof config.name === "string") {
          return config.name
        }
      }
    } catch {
      // 忽略解析错误
    }
  }
  
  // 如果是 FLOW_OPERATION 类型，从 configJson 中解析 serviceBean
  // 后端已经在 FlowOperation 的 configJson 中添加了 serviceBean 字段（从父级 service 对象获取）
  if (endpointType === "FLOW_OPERATION") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        if (config.serviceBean && typeof config.serviceBean === "string") {
          return config.serviceBean
        }
        // 回退到使用 serviceName
        if (config.serviceName && typeof config.serviceName === "string") {
          return config.serviceName
        }
      }
    } catch {
      // 忽略解析错误
    }
  }
  
  // 回退到使用 bean 字段
  return comp.bean || null
}
</script>

<template>
  <div v-if="!hasSelection" class="center">请选择节点或连线查看配置</div>
  <div v-else class="col" style="height:100%">
    <div class="bar">
      <div style="font-weight:600; font-size:13px">{{ selectedEdge ? "连线配置" : "节点配置" }}</div>
    </div>
    <div style="padding:12px" class="col">
      <template v-if="selectedEdge">
      <div>
        <div class="muted">线条标签</div>
        <input
          class="input"
          placeholder="示例：成功 / 失败"
          :value="selectedEdge.data?.label || ''"
          @input="updateEdgeField({ label: ($event.target as HTMLInputElement).value })"
        />
      </div>
      <div class="tip" style="margin-top: 8px;">
        提示：线条标签仅用于显示，不影响流程执行逻辑。
      </div>
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
      <!-- 脚本节点不需要显示通用的输入参数和输出结果配置 -->
      <!-- 脚本节点的输入来自上游节点（自动），输出由脚本生成 -->
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
      <div class="tip">
        条件分支节点默认作为"无条件"出口，你可以在右键连线时补充条件表达式，也可以保持为空（即未命中则走该分支）。
      </div>
    </template>

      <template v-else-if="selectedNode && !isTransformerNode">
        <ServiceNodeConfig
          :label="selectedNode.data?.label || selectedNode.label || ''"
          :comp="selectedNode.data?.comp"
          :inputs="selectedNode.data?.inputs"
          :output="selectedNode.data?.output"
          :retry="selectedNode.data?.retry"
          :timeout="selectedNode.data?.timeout"
          :isolation="selectedNode.data?.isolation"
          :tx-mode="selectedNode.data?.txMode"
          :transaction-manager="selectedNode.data?.transactionManager"
          :project-key="props.projectKey"
          :endpoint-id="props.endpointId"
          @update:label="updateNodeField('label', $event)"
          @update:inputs="updateNodeField('inputs', $event)"
          @update:output="updateNodeField('output', $event)"
          @update:retry="updateNodeField('retry', $event)"
          @update:timeout="updateNodeField('timeout', $event)"
          @update:isolation="updateNodeField('isolation', $event)"
          @update:tx-mode="updateNodeField('txMode', $event)"
          @update:transaction-manager="updateNodeField('transactionManager', $event)"
        />
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

/* 已移除条件分支连线相关样式 */

.input-hint {
  font-size: 11px;
  color: #94a3b8;
}

.input.locked {
  background: #f3f4f6;
  color: #6b7280;
  cursor: not-allowed;
}

:deep(.resolver) {
  flex: 1;
}

.type-pill {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 999px;
  width: fit-content;
  padding: 2px 10px;
}

.binding-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.input-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.input-main-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.input-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 4px;
}

.type-input-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.type-input-group .input {
  flex: 0 0 auto;
}

.type-input-group select {
  min-width: 120px;
  max-width: 200px;
}

.type-input-group .type-name-input {
  flex: 1;
  min-width: 200px;
  max-width: 100%;
}

.type-display {
  display: flex;
  align-items: center;
  min-height: 32px;
}

.type-text {
  display: inline-block;
  max-width: 100%;
  word-break: break-word;
  white-space: normal;
  line-height: 1.4;
}

.script-value-preview {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.script-value-input {
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 11px;
  min-height: 60px;
  max-height: 120px;
  resize: vertical;
  cursor: pointer;
  background: #f8fafc;
  color: #475569;
}

.script-value-input:hover {
  background: #f1f5f9;
  border-color: rgba(148, 163, 184, 0.5);
}

.script-value-input:focus {
  outline: none;
  border-color: #2563eb;
  background: #fff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.btn-icon {
  background: transparent;
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.btn-icon:hover {
  background: #f8fafc;
  border-color: rgba(148, 163, 184, 0.6);
}

.icon-expand {
  font-size: 10px;
  color: #64748b;
  transition: transform 0.2s ease;
  display: inline-block;
}

.icon-expand.expanded {
  transform: rotate(180deg);
}



.input-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pill-text {
  padding: 4px 10px;
  background: rgba(226, 232, 240, 0.6);
  border-radius: 999px;
  font-size: 12px;
}

.resolver-label {
  font-size: 11px;
  color: #94a3b8;
}

.field-table {
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 8px;
  overflow: hidden;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 2fr;
  padding: 6px 8px;
  font-size: 11px;
  align-items: center;
  gap: 6px;
}

.field-row:nth-child(even) {
  background: rgba(226, 232, 240, 0.4);
}

.field-row.header {
  font-weight: 600;
  background: rgba(226, 232, 240, 0.8);
}
</style>
