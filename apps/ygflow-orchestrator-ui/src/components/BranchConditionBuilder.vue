<script setup lang="ts">
import { computed, ref, watch } from "vue"
import ServiceCallEditor from "./ServiceCallEditor.vue"
import SourcePathInput from "./SourcePathInput.vue"

type SourceKind = "ctx" | "const" | "serviceCall" | "tempVar"
type ConditionOp = "eq" | "ne" | "gt" | "ge" | "lt" | "le" | "contains" | "in"
type GroupOp = "and" | "or"
type EditorMode = "quick" | "advanced"

interface ServiceCallConfig {
  fn: string
  argsText: string
  argsMode?: "list" | "json"
  args?: Array<Record<string, any>>
  serviceRef?: Record<string, any> | null
  argBindings?: Array<Record<string, any>>
}

interface SourceConfig {
  kind: SourceKind
  path: string
  tempKey: string
  constValue: string
  serviceResultPath: string
  serviceCall: ServiceCallConfig
}

interface ConditionRule {
  id: string
  op: ConditionOp
  left: SourceConfig
  right: SourceConfig
}

interface ConditionTree {
  op: GroupOp
  rules: ConditionRule[]
}
interface ConditionInsight {
  warnings: string[]
  serviceCallCount: number
}
interface ConditionPreflightInsight {
  errors: string[]
  warnings: string[]
}
interface RuleDraftSuggestion {
  id: string
  label: string
  description: string
  sourceKind: "ctx" | "tempVar"
  value: string
}
interface RuleOperatorSuggestion {
  op: ConditionOp
  label: string
  reason: string
}
interface ConstantSuggestion {
  value: string
  reason: string
}
interface RuleTemplateSuggestion {
  id: string
  label: string
  description: string
  op: ConditionOp
  constValue: string
}

const props = defineProps<{
  modelValue?: any
  projectKey?: string
  tempVarKeys?: string[] | null
  sourcePathOptions?: string[] | null
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: ConditionTree): void
  (e: "insight-change", value: ConditionPreflightInsight): void
}>()
const sourceEditorMode = ref<EditorMode>("quick")
const quickSourceKinds: SourceKind[] = ["ctx", "tempVar", "const"]

function createSource(kind: SourceKind = "ctx"): SourceConfig {
  return {
    kind,
    path: "request.body.xxx",
    tempKey: "",
    constValue: "",
    serviceResultPath: "",
    serviceCall: {
      fn: "",
      argsMode: "list",
      argsText: "[]",
      args: [],
      serviceRef: null,
      argBindings: [],
    },
  }
}

function createRule(): ConditionRule {
  return {
    id: `rule_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    op: "eq",
    left: createSource("ctx"),
    right: createSource("const"),
  }
}

function normalizeConditionTree(raw: any): ConditionTree {
  const op: GroupOp = raw?.op === "or" ? "or" : "and"
  const rulesRaw = Array.isArray(raw?.rules) ? raw.rules : []
  const rules = rulesRaw.length
    ? rulesRaw.map((r: any) => ({
        id: r?.id || createRule().id,
        op: r?.op || "eq",
        left: {
          ...createSource("ctx"),
          ...(r?.left || {}),
          serviceCall: { ...createSource("ctx").serviceCall, ...(r?.left?.serviceCall || {}) },
        },
        right: {
          ...createSource("const"),
          ...(r?.right || {}),
          serviceCall: { ...createSource("const").serviceCall, ...(r?.right?.serviceCall || {}) },
        },
      }))
    : [createRule()]
  return { op, rules }
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
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

function hasServiceCallSource(tree: ConditionTree): boolean {
  return tree.rules.some((rule) => rule.left.kind === "serviceCall" || rule.right.kind === "serviceCall")
}

function setSourceEditorMode(mode: EditorMode) {
  sourceEditorMode.value = mode
}

function getSourceOptions(currentKind?: SourceKind) {
  const kinds = sourceEditorMode.value === "advanced"
    ? [...quickSourceKinds, "serviceCall" as const]
    : [...quickSourceKinds]
  if (currentKind && !kinds.includes(currentKind)) {
    kinds.push(currentKind)
  }
  return kinds.map((kind) => ({
    value: kind,
    label: kind === "ctx" ? "上下文" : kind === "tempVar" ? "临时变量" : kind === "const" ? "常量" : "服务调用",
    advanced: kind === "serviceCall",
  }))
}

function formatSourceOptionLabel(option: { label: string; advanced: boolean }) {
  if (sourceEditorMode.value === "quick" && option.advanced) {
    return `${option.label}（高级）`
  }
  return option.label
}

function normalizeTempVarRootKey(raw: string): string {
  const text = String(raw || "").trim()
  if (!text) return ""
  return text.replace(/^tempVar\(/, "").replace(/\)$/, "").split(".")[0].trim()
}

function sourceWarnings(source: SourceConfig, sideLabel: string): string[] {
  const warnings: string[] = []
  if (source.kind === "tempVar") {
    const rootKey = normalizeTempVarRootKey(source.tempKey)
    if (!rootKey) {
      warnings.push(`${sideLabel} 选择了临时变量，但还没有填写变量 key。`)
    } else if (!(props.tempVarKeys ?? []).includes(rootKey)) {
      warnings.push(`${sideLabel} 引用了未定义的分支变量 ${source.tempKey}。`)
    }
  }
  if (source.kind === "serviceCall" && !source.serviceCall?.fn?.trim() && !source.serviceCall?.serviceRef?.methodName) {
    warnings.push(`${sideLabel} 选择了服务调用，但还没有完成方法配置。`)
  }
  return warnings
}

function ruleWarnings(rule: ConditionRule): string[] {
  return [
    ...sourceWarnings(rule.left, "左值"),
    ...sourceWarnings(rule.right, "右值"),
  ]
}

function createRuleFromSuggestion(
  sourceKind: "ctx" | "tempVar",
  value: string,
  options?: { op?: ConditionOp; constValue?: string },
): ConditionRule {
  const rule = createRule()
  if (sourceKind === "tempVar") {
    rule.left = {
      ...createSource("tempVar"),
      tempKey: value,
      path: "",
    }
  } else {
    rule.left = {
      ...createSource("ctx"),
      path: value,
    }
  }
  rule.right = createSource("const")
  rule.op = options?.op || "eq"
  rule.right.constValue = options?.constValue || ""
  return rule
}

function sourceSemanticHint(source: SourceConfig): string {
  if (source.kind === "tempVar") return normalizeTempVarRootKey(source.tempKey)
  if (source.kind === "ctx") return String(source.path || "").trim().split(".").pop() || ""
  if (source.kind === "serviceCall") {
    return String(source.serviceResultPath || source.serviceCall?.fn || source.serviceCall?.serviceRef?.methodName || "").trim()
      .split(".")
      .pop() || ""
  }
  return ""
}

function inferRuleOperatorSuggestions(rule: ConditionRule): RuleOperatorSuggestion[] {
  const hint = `${sourceSemanticHint(rule.left)} ${sourceSemanticHint(rule.right)}`.toLowerCase()
  if (!hint.trim()) {
    return [
      { op: "eq", label: "=", reason: "通用相等判断" },
      { op: "contains", label: "contains", reason: "适合文本包含判断" },
    ]
  }
  if (/(status|state|type|flag|code|result|route|level|phase|scene)/.test(hint)) {
    return [
      { op: "eq", label: "=", reason: "状态/枚举值常用相等判断" },
      { op: "ne", label: "!=", reason: "适合排除某个状态" },
      { op: "in", label: "in", reason: "适合枚举集合判断" },
    ]
  }
  if (/(age|score|amount|count|qty|num|size|total|price|rate)/.test(hint)) {
    return [
      { op: "ge", label: ">=", reason: "数值阈值判断最常见" },
      { op: "gt", label: ">", reason: "适合严格大于" },
      { op: "le", label: "<=", reason: "适合上限判断" },
    ]
  }
  if (/(list|ids|items|codes|tags|roles)/.test(hint)) {
    return [
      { op: "contains", label: "contains", reason: "适合集合或字符串包含" },
      { op: "in", label: "in", reason: "适合值落在候选集合中" },
      { op: "eq", label: "=", reason: "少量场景直接比对" },
    ]
  }
  if (/(name|title|remark|desc|message|content)/.test(hint)) {
    return [
      { op: "contains", label: "contains", reason: "文本字段更常见包含判断" },
      { op: "eq", label: "=", reason: "适合精确匹配" },
    ]
  }
  return [
    { op: "eq", label: "=", reason: "通用相等判断" },
    { op: "ne", label: "!=", reason: "通用不等判断" },
    { op: "contains", label: "contains", reason: "适合文本/集合包含" },
  ]
}

function constantPlaceholder(rule: ConditionRule, side: "left" | "right"): string {
  const opposite = side === "left" ? rule.right : rule.left
  const hint = sourceSemanticHint(opposite).toLowerCase()
  const op = rule.op
  if (op === "in") return "例如 A,B,C 或 1,2,3"
  if (/(status|state|type|flag|code|result|route)/.test(hint)) return "例如 APPROVED / READY / SUCCESS"
  if (/(age|score|amount|count|qty|num|size|total|price|rate)/.test(hint)) return "例如 10 / 100 / 0.85"
  if (/(name|title|remark|desc|message|content)/.test(hint)) {
    return op === "contains" ? "例如 VIP / urgent / fail" : "例如 Tom / 已完成"
  }
  if (/(enabled|deleted|valid|active|success)/.test(hint)) return "例如 true / false"
  return "例如 true / 100 / ACTIVE"
}

function inferConstantSuggestions(rule: ConditionRule, side: "left" | "right"): ConstantSuggestion[] {
  const opposite = side === "left" ? rule.right : rule.left
  const hint = sourceSemanticHint(opposite).toLowerCase()
  const op = rule.op

  if (op === "in") {
    return [
      { value: "A,B,C", reason: "适合枚举集合判断" },
      { value: "1,2,3", reason: "适合数值集合判断" },
    ]
  }
  if (/(status|state|type|flag|code|result|route)/.test(hint)) {
    return [
      { value: "READY", reason: "常见状态值" },
      { value: "APPROVED", reason: "常见审批结果" },
      { value: "SUCCESS", reason: "常见结果状态" },
    ]
  }
  if (/(enabled|deleted|valid|active|success)/.test(hint)) {
    return [
      { value: "true", reason: "布尔开关判断" },
      { value: "false", reason: "布尔开关判断" },
    ]
  }
  if (/(age|score|amount|count|qty|num|size|total|price|rate)/.test(hint)) {
    return [
      { value: "0", reason: "零值边界" },
      { value: "1", reason: "常见最小阈值" },
      { value: "100", reason: "常见数量/金额阈值" },
    ]
  }
  if (/(name|title|remark|desc|message|content)/.test(hint)) {
    return op === "contains"
      ? [
          { value: "VIP", reason: "常见文本标签" },
          { value: "urgent", reason: "常见文本关键字" },
        ]
      : [
          { value: "Tom", reason: "示例精确文本" },
          { value: "已完成", reason: "示例状态文案" },
        ]
  }
  return [
    { value: "true", reason: "通用布尔值" },
    { value: "100", reason: "通用数值示例" },
    { value: "ACTIVE", reason: "通用状态示例" },
  ]
}

function summarizeSource(source: SourceConfig): string {
  if (source.kind === "ctx") return source.path || "ctx"
  if (source.kind === "tempVar") return source.tempKey || "tempVar"
  if (source.kind === "const") return source.constValue || "常量"
  if (source.kind === "serviceCall") {
    const fn = source.serviceCall?.fn?.trim()
      || source.serviceCall?.serviceRef?.methodName
      || "serviceCall"
    const resultPath = source.serviceResultPath?.trim()
    return resultPath ? `${fn}.${resultPath}` : fn
  }
  return "unknown"
}

function summarizeRule(rule: ConditionRule): string {
  const left = summarizeSource(rule.left)
  const right = summarizeSource(rule.right)
  const opLabel = operatorOptions.find((item) => item.value === rule.op)?.label || rule.op
  return `${left} ${opLabel} ${right}`
}

const conditionTree = ref<ConditionTree>(normalizeConditionTree(props.modelValue))
const conditionInsight = computed<ConditionInsight>(() => {
  const warnings: string[] = []
  const serviceCallCount = conditionTree.value.rules.reduce((count, rule) => {
    return count
      + (rule.left.kind === "serviceCall" ? 1 : 0)
      + (rule.right.kind === "serviceCall" ? 1 : 0)
  }, 0)

  const missingTempRefs = new Set<string>()
  conditionTree.value.rules.forEach((rule) => {
    const refs = [rule.left, rule.right]
      .filter((item) => item.kind === "tempVar")
      .map((item) => String(item.tempKey || "").trim())
      .filter(Boolean)
    refs.forEach((ref) => {
      const rootKey = normalizeTempVarRootKey(ref)
      if (rootKey && !(props.tempVarKeys ?? []).includes(rootKey)) {
        missingTempRefs.add(ref)
      }
    })
  })

  if (serviceCallCount > 1) {
    warnings.push(`当前条件中有 ${serviceCallCount} 处直接服务调用。复杂场景建议先在分支节点临时变量中补数，再在线条条件里只引用 tempVar。`)
  } else if (serviceCallCount === 1) {
    warnings.push("当前条件中包含 1 处直接服务调用。若这个结果会被多个规则复用，建议提取到分支节点临时变量。")
  }

  if (missingTempRefs.size > 0) {
    warnings.push(`当前条件引用了未定义的分支变量：${Array.from(missingTempRefs).join(" , ")}。`)
  }

  return {
    warnings,
    serviceCallCount,
  }
})
const conditionPreflightInsight = computed<ConditionPreflightInsight>(() => {
  const warnings = [...conditionInsight.value.warnings]
  conditionTree.value.rules.forEach((rule, index) => {
    const label = `规则 ${index + 1}`
    ruleWarnings(rule).forEach((msg) => warnings.push(`${label}: ${msg}`))
  })
  return {
    errors: [],
    warnings: uniqueTextList(warnings),
  }
})
const ruleDraftSuggestions = computed<RuleDraftSuggestion[]>(() => {
  const tempVarSuggestions = (props.tempVarKeys ?? [])
    .slice(0, 4)
    .map((key, index) => ({
      id: `temp_${index}_${key}`,
      label: `变量 ${key}`,
      description: "生成“tempVar = 常量”规则骨架",
      sourceKind: "tempVar" as const,
      value: key,
    }))

  const pathSuggestions = (props.sourcePathOptions ?? [])
    .slice(0, 3)
    .map((path, index) => ({
      id: `ctx_${index}_${path}`,
      label: `路径 ${path}`,
      description: "生成“ctx = 常量”规则骨架",
      sourceKind: "ctx" as const,
      value: path,
    }))

  return [...tempVarSuggestions, ...pathSuggestions]
})
const ruleTemplateSuggestions = computed<RuleTemplateSuggestion[]>(() => ([
  {
    id: "status_eq",
    label: "状态命中",
    description: "适合 status/state/type/code 等状态判断",
    op: "eq",
    constValue: "READY",
  },
  {
    id: "threshold_ge",
    label: "阈值判断",
    description: "适合 score/amount/count/age 等数值比较",
    op: "ge",
    constValue: "100",
  },
  {
    id: "contains_text",
    label: "包含判断",
    description: "适合文本标签或集合成员判断",
    op: "contains",
    constValue: "VIP",
  },
]))

watch(
  () => props.modelValue,
  (next) => {
    const normalized = normalizeConditionTree(next)
    conditionTree.value = normalized
    if (hasServiceCallSource(normalized)) {
      sourceEditorMode.value = "advanced"
    }
  },
  { deep: true, immediate: true }
)

watch(
  conditionTree,
  (next) => {
    if (hasServiceCallSource(next)) {
      sourceEditorMode.value = "advanced"
    }
    emit("update:modelValue", clone(next))
  },
  { deep: true }
)
watch(conditionPreflightInsight, (next) => emit("insight-change", clone(next)), { deep: true, immediate: true })

function addRule() {
  const firstTempVar = (props.tempVarKeys ?? []).find((item) => String(item || "").trim())
  if (firstTempVar) {
    conditionTree.value.rules.push(createRuleFromSuggestion("tempVar", firstTempVar))
    return
  }
  const firstPath = (props.sourcePathOptions ?? []).find((item) => String(item || "").trim())
  if (firstPath) {
    conditionTree.value.rules.push(createRuleFromSuggestion("ctx", firstPath))
    return
  }
  conditionTree.value.rules.push(createRule())
}

function addRuleFromSuggestion(suggestion: RuleDraftSuggestion) {
  conditionTree.value.rules.push(createRuleFromSuggestion(suggestion.sourceKind, suggestion.value))
}

function addRuleFromTemplate(template: RuleTemplateSuggestion) {
  const firstTempVar = (props.tempVarKeys ?? []).find((item) => String(item || "").trim())
  if (firstTempVar) {
    conditionTree.value.rules.push(createRuleFromSuggestion("tempVar", firstTempVar, {
      op: template.op,
      constValue: template.constValue,
    }))
    return
  }
  const firstPath = (props.sourcePathOptions ?? []).find((item) => String(item || "").trim())
  if (firstPath) {
    conditionTree.value.rules.push(createRuleFromSuggestion("ctx", firstPath, {
      op: template.op,
      constValue: template.constValue,
    }))
    return
  }
  const fallback = createRule()
  fallback.op = template.op
  fallback.right.constValue = template.constValue
  conditionTree.value.rules.push(fallback)
}

function removeRule(index: number) {
  if (conditionTree.value.rules.length <= 1) return
  conditionTree.value.rules.splice(index, 1)
}

const operatorOptions = [
  { value: "eq", label: "=" },
  { value: "ne", label: "!=" },
  { value: "gt", label: ">" },
  { value: "ge", label: ">=" },
  { value: "lt", label: "<" },
  { value: "le", label: "<=" },
  { value: "contains", label: "contains" },
  { value: "in", label: "in" },
]
</script>

<template>
  <div class="condition-builder">
    <div class="header">
      <div class="title">结构化条件树（V2）</div>
      <div class="header-tools">
        <div class="row">
          <span class="muted small">组合关系</span>
          <select class="input small-select" v-model="conditionTree.op">
            <option value="and">AND（全部满足）</option>
            <option value="or">OR（任一满足）</option>
          </select>
        </div>
        <div class="mode-switch" role="group" aria-label="分支条件模式">
          <button class="btn mini" :class="{ active: sourceEditorMode === 'quick' }" @click="setSourceEditorMode('quick')">快速</button>
          <button class="btn mini" :class="{ active: sourceEditorMode === 'advanced' }" @click="setSourceEditorMode('advanced')">高级</button>
        </div>
      </div>
    </div>
    <div class="mode-note">
      {{
        sourceEditorMode === "quick"
          ? "快速模式：建议使用 ctx / 常量 / tempVar 组合分支条件。"
          : "高级模式：允许在分支线上直接服务调用。若方法返回包装对象，建议补充结果提取路径，避免直接比较整包返回值。"
      }}
    </div>

    <div v-if="conditionInsight.warnings.length > 0" class="insight-list">
      <div v-for="msg in conditionInsight.warnings" :key="msg" class="insight-item">
        {{ msg }}
      </div>
    </div>

    <div v-if="ruleDraftSuggestions.length > 0" class="draft-panel">
      <div class="muted small">快速起草</div>
      <div class="draft-list">
        <button
          v-for="suggestion in ruleDraftSuggestions"
          :key="suggestion.id"
          type="button"
          class="draft-chip"
          :title="suggestion.description"
          @click="addRuleFromSuggestion(suggestion)"
        >
          + {{ suggestion.label }}
        </button>
      </div>
      <div class="muted tiny">点击后会生成“左值 + 操作符 + 常量”的规则骨架，适合先起草再细化。</div>
    </div>

    <div class="draft-panel">
      <div class="muted small">常用模板</div>
      <div class="draft-list">
        <button
          v-for="template in ruleTemplateSuggestions"
          :key="template.id"
          type="button"
          class="draft-chip"
          :title="template.description"
          @click="addRuleFromTemplate(template)"
        >
          + {{ template.label }}
        </button>
      </div>
      <div class="muted tiny">优先用已有 tempVar 起草；如果还没有分支变量，就退化为请求路径规则骨架。</div>
    </div>

    <div class="rules">
      <div v-for="(rule, index) in conditionTree.rules" :key="rule.id" class="rule-card">
        <div class="rule-header">
          <div class="rule-head-main">
            <span class="muted small">规则 {{ index + 1 }}</span>
            <span class="rule-summary">{{ summarizeRule(rule) }}</span>
          </div>
          <button class="btn mini" @click="removeRule(index)">删除</button>
        </div>

        <div v-if="ruleWarnings(rule).length > 0" class="rule-warning-list">
          <div v-for="msg in ruleWarnings(rule)" :key="msg" class="rule-warning-item">{{ msg }}</div>
        </div>

        <div class="rule-grid">
          <div class="source-card">
            <div class="muted tiny">左值</div>
            <select class="input" v-model="rule.left.kind">
              <option
                v-for="option in getSourceOptions(rule.left.kind)"
                :key="`left_${rule.id}_${option.value}`"
                :value="option.value"
              >
                {{ formatSourceOptionLabel(option) }}
              </option>
            </select>

            <SourcePathInput
              v-if="rule.left.kind === 'ctx'"
              :model-value="rule.left.path"
              :options="props.sourcePathOptions ?? []"
              placeholder="request.body.userId / request.query.qty"
              @update:model-value="rule.left.path = $event"
            />
            <input
              v-if="rule.left.kind === 'tempVar'"
              class="input"
              v-model="rule.left.tempKey"
              :list="`temp_left_${rule.id}`"
              placeholder="临时变量 key，例如 userAge"
            />
            <datalist v-if="rule.left.kind === 'tempVar'" :id="`temp_left_${rule.id}`">
              <option v-for="k in (props.tempVarKeys ?? [])" :key="`left-${rule.id}-${k}`" :value="k" />
            </datalist>
            <input
              v-if="rule.left.kind === 'const'"
              class="input"
              v-model="rule.left.constValue"
              :placeholder="constantPlaceholder(rule, 'left')"
            />
            <div v-if="rule.left.kind === 'const'" class="const-suggestion-list">
              <button
                v-for="suggestion in inferConstantSuggestions(rule, 'left')"
                :key="`${rule.id}_left_${suggestion.value}`"
                type="button"
                class="const-suggestion-chip"
                :title="suggestion.reason"
                @click="rule.left.constValue = suggestion.value"
              >
                {{ suggestion.value }}
              </button>
            </div>
            <template v-if="rule.left.kind === 'serviceCall'">
              <ServiceCallEditor
                v-model="rule.left.serviceCall"
                :project-key="props.projectKey"
                :temp-keys="props.tempVarKeys ?? []"
                :source-path-options="props.sourcePathOptions"
              />
              <input
                class="input"
                v-model="rule.left.serviceResultPath"
                placeholder="结果提取路径（可选），例如 data.status / $.data.score"
              />
              <div class="muted tiny hint-line">
                服务方法返回包装对象时，在这里提取真正参与比较的字段；留空则直接比较整个返回值。
              </div>
            </template>
          </div>

          <div class="op-card">
            <div class="muted tiny">操作符</div>
            <select class="input" v-model="rule.op">
              <option v-for="op in operatorOptions" :key="op.value" :value="op.value">
                {{ op.label }}
              </option>
            </select>
            <div class="op-suggestion-list">
              <button
                v-for="suggestion in inferRuleOperatorSuggestions(rule)"
                :key="`${rule.id}_${suggestion.op}`"
                type="button"
                class="op-suggestion-chip"
                :class="{ active: rule.op === suggestion.op }"
                :title="suggestion.reason"
                @click="rule.op = suggestion.op"
              >
                {{ suggestion.label }}
              </button>
            </div>
          </div>

          <div class="source-card">
            <div class="muted tiny">右值</div>
            <select class="input" v-model="rule.right.kind">
              <option
                v-for="option in getSourceOptions(rule.right.kind)"
                :key="`right_${rule.id}_${option.value}`"
                :value="option.value"
              >
                {{ formatSourceOptionLabel(option) }}
              </option>
            </select>

            <SourcePathInput
              v-if="rule.right.kind === 'ctx'"
              :model-value="rule.right.path"
              :options="props.sourcePathOptions ?? []"
              placeholder="request.body.status / request.query.price"
              @update:model-value="rule.right.path = $event"
            />
            <input
              v-if="rule.right.kind === 'tempVar'"
              class="input"
              v-model="rule.right.tempKey"
              :list="`temp_right_${rule.id}`"
              placeholder="临时变量 key，例如 userAge"
            />
            <datalist v-if="rule.right.kind === 'tempVar'" :id="`temp_right_${rule.id}`">
              <option v-for="k in (props.tempVarKeys ?? [])" :key="`right-${rule.id}-${k}`" :value="k" />
            </datalist>
            <input
              v-if="rule.right.kind === 'const'"
              class="input"
              v-model="rule.right.constValue"
              :placeholder="constantPlaceholder(rule, 'right')"
            />
            <div v-if="rule.right.kind === 'const'" class="const-suggestion-list">
              <button
                v-for="suggestion in inferConstantSuggestions(rule, 'right')"
                :key="`${rule.id}_right_${suggestion.value}`"
                type="button"
                class="const-suggestion-chip"
                :title="suggestion.reason"
                @click="rule.right.constValue = suggestion.value"
              >
                {{ suggestion.value }}
              </button>
            </div>
            <template v-if="rule.right.kind === 'serviceCall'">
              <ServiceCallEditor
                v-model="rule.right.serviceCall"
                :project-key="props.projectKey"
                :temp-keys="props.tempVarKeys ?? []"
                :source-path-options="props.sourcePathOptions"
              />
              <input
                class="input"
                v-model="rule.right.serviceResultPath"
                placeholder="结果提取路径（可选），例如 data.status / $.data.score"
              />
              <div class="muted tiny hint-line">
                服务方法返回包装对象时，在这里提取真正参与比较的字段；留空则直接比较整个返回值。
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <div class="footer">
      <button class="btn" @click="addRule">+ 添加规则</button>
    </div>
  </div>
</template>

<style scoped>
.condition-builder {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  container-type: inline-size;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  min-width: 0;
}

.mode-switch {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.mode-switch .btn.active {
  border-color: #2563eb;
  color: #1d4ed8;
  background: #eff6ff;
}

.mode-note {
  font-size: 11px;
  color: #475569;
  line-height: 1.45;
}

.insight-list,
.rule-warning-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.insight-item,
.rule-warning-item {
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.92);
  color: #92400e;
  font-size: 11px;
  line-height: 1.45;
}

.draft-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.draft-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.draft-chip {
  border: 1px solid rgba(37, 99, 235, 0.2);
  background: rgba(239, 246, 255, 0.75);
  color: #1d4ed8;
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  cursor: pointer;
}

.draft-chip:hover {
  background: rgba(219, 234, 254, 0.92);
}

.rules {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rule-card {
  border: 1px solid #dbe2ea;
  border-radius: 10px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #fbfdff;
  min-width: 0;
}

.rule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.rule-head-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.rule-summary {
  font-size: 11px;
  color: #334155;
  line-height: 1.4;
  word-break: break-word;
}

.rule-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 130px minmax(0, 1fr);
  gap: 8px;
}

.source-card,
.op-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.op-suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.op-suggestion-chip {
  border: 1px solid #dbe2ea;
  background: #f8fafc;
  color: #475569;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 10px;
  cursor: pointer;
}

.op-suggestion-chip.active {
  color: #0b3b8a;
  border-color: rgba(37, 99, 235, 0.25);
  background: rgba(37, 99, 235, 0.08);
}

.const-suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.const-suggestion-chip {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #f8fafc;
  color: #334155;
  border-radius: 999px;
  padding: 3px 8px;
  font-size: 10px;
  cursor: pointer;
}

.const-suggestion-chip:hover {
  background: #eef2ff;
  color: #1d4ed8;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.small-select {
  width: min(170px, 100%);
}

.muted.small {
  font-size: 11px;
}

.muted.tiny {
  font-size: 10px;
}

.hint-line {
  line-height: 1.4;
}

.btn.mini {
  padding: 4px 8px;
  font-size: 11px;
}

.footer {
  display: flex;
  justify-content: flex-end;
}

.condition-builder .input {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

@container (max-width: 560px) {
  .rule-grid {
    grid-template-columns: 1fr;
  }

  .op-card {
    max-width: 180px;
  }
}
</style>
