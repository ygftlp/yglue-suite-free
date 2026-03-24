<script setup lang="ts">
import { ref, watch } from "vue"
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

const props = defineProps<{
  modelValue?: any
  projectKey?: string
  tempVarKeys?: string[] | null
  sourcePathOptions?: string[] | null
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: ConditionTree): void
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

const conditionTree = ref<ConditionTree>(normalizeConditionTree(props.modelValue))

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

function addRule() {
  conditionTree.value.rules.push(createRule())
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

    <div class="rules">
      <div v-for="(rule, index) in conditionTree.rules" :key="rule.id" class="rule-card">
        <div class="rule-header">
          <span class="muted small">规则 {{ index + 1 }}</span>
          <button class="btn mini" @click="removeRule(index)">删除</button>
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
              placeholder="例如 true / 100 / ACTIVE"
            />
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
              placeholder="例如 PAID / 0 / true"
            />
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
