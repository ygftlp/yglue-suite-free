<script setup lang="ts">
import { computed, ref, watch } from "vue"
import ServiceCallEditor from "./ServiceCallEditor.vue"
import SourcePathInput from "./SourcePathInput.vue"

type SourceKind = "ctx" | "const" | "serviceCall" | "httpCall" | "listPipeline"
type StepOp = "filter" | "map" | "enrich" | "groupBy" | "reduce"
type ServiceArgKind = "ctx" | "const" | "tempVar"
type ServiceArgsMode = "json" | "list"
type ListComposeSourceKind = "ctx" | "const" | "tempVar" | "serviceCall"
type HttpValueKind = "ctx" | "const" | "tempVar"
type EditorMode = "quick" | "advanced"

interface HttpKvBinding {
  id: string
  key: string
  valueKind: HttpValueKind
  valuePath: string
  valueConst: string
  tempKey: string
}

interface ServiceCallArg {
  id: string
  name: string
  typeHint: string
  kind: ServiceArgKind
  path: string
  constValue: string
  tempKey: string
}

interface ServiceCallConfig {
  fn: string
  argsMode: ServiceArgsMode
  argsText: string
  args: ServiceCallArg[]
}

interface HttpCallConfig {
  method: "GET" | "POST" | "PUT" | "DELETE" | "PATCH"
  url: string
  headers: HttpKvBinding[]
  query: HttpKvBinding[]
  bodyJson: string
  resultPath: string
}

interface ValueSource {
  kind: SourceKind
  path: string
  constValue: string
  serviceResultPath: string
  serviceCall: ServiceCallConfig
  httpCall: HttpCallConfig
}

interface ListPipelineStep {
  id: string
  op: StepOp
  exprText: string
  mappingText: string
  enrichFn: string
}

interface ListComposeFieldSource {
  kind: ListComposeSourceKind
  path: string
  constValue: string
  tempKey: string
  serviceResultPath: string
  serviceCall: ServiceCallConfig
}

interface ListComposeField {
  id: string
  targetField: string
  typeHint: string
  source: ListComposeFieldSource
}

interface ListComposeConfig {
  itemTypeHint: string
  fields: ListComposeField[]
}

interface ArgPlan {
  id: string
  target: string
  typeHint: string
  source: ValueSource
  listInput: ValueSource
  listCompose: ListComposeConfig
  listSteps: ListPipelineStep[]
}

interface TempPlan {
  id: string
  key: string
  typeHint: string
  source: ValueSource
  listInput: ValueSource
  listCompose: ListComposeConfig
  listSteps: ListPipelineStep[]
}

interface ParamPlansModel {
  tempPlans: TempPlan[]
  argPlans: ArgPlan[]
}

type QuickCheckLevel = "error" | "warning"

interface QuickCheckItem {
  level: QuickCheckLevel
  category: "类型不匹配" | "缺少必填映射" | "重复字段" | "其他问题"
  message: string
}

interface InputMeta {
  name: string
  valueType?: string
  typeName?: string
}

const props = defineProps<{
  modelValue?: any
  inputDefs?: InputMeta[]
  projectKey?: string
  sourcePathOptions?: string[] | null
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: ParamPlansModel): void
}>()

function createServiceCall(): ServiceCallConfig {
  return {
    fn: "",
    argsMode: "json",
    argsText: "[]",
    args: [createServiceCallArg()],
  }
}

function createHttpKvBinding(valueKind: HttpValueKind = "const"): HttpKvBinding {
  return {
    id: `http_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    key: "",
    valueKind,
    valuePath: "ctx.xxx",
    valueConst: "",
    tempKey: "",
  }
}

function createHttpCall(): HttpCallConfig {
  return {
    method: "GET",
    url: "",
    headers: [createHttpKvBinding("const")],
    query: [createHttpKvBinding("const")],
    bodyJson: "{}",
    resultPath: "",
  }
}

function createServiceCallArg(): ServiceCallArg {
  return {
    id: `arg_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    name: "",
    typeHint: "",
    kind: "ctx",
    path: "request.body.xxx",
    constValue: "",
    tempKey: "",
  }
}

function createValueSource(kind: SourceKind = "ctx"): ValueSource {
  return {
    kind,
    path: "ctx.xxx",
    constValue: "",
    serviceResultPath: "",
    serviceCall: createServiceCall(),
    httpCall: createHttpCall(),
  }
}

function createStep(op: StepOp = "map"): ListPipelineStep {
  return {
    id: `step_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    op,
    exprText: "",
    mappingText: "{\n  \"field\": \"$.field\"\n}",
    enrichFn: "",
  }
}

function createListComposeFieldSource(kind: ListComposeSourceKind = "ctx"): ListComposeFieldSource {
  return {
    kind,
    path: "$.field",
    constValue: "",
    tempKey: "",
    serviceResultPath: "",
    serviceCall: createServiceCall(),
  }
}

function createListComposeField(): ListComposeField {
  return {
    id: `lf_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    targetField: "",
    typeHint: "",
    source: createListComposeFieldSource("ctx"),
  }
}

function createListCompose(): ListComposeConfig {
  return {
    itemTypeHint: "java.util.Map<java.lang.String,java.lang.Object>",
    fields: [createListComposeField()],
  }
}

function createArgPlan(): ArgPlan {
  return {
    id: `arg_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    target: "",
    typeHint: "",
    source: createValueSource("ctx"),
    listInput: createValueSource("serviceCall"),
    listCompose: createListCompose(),
    listSteps: [createStep("filter"), createStep("map")],
  }
}

function createTempPlan(): TempPlan {
  return {
    id: `tmp_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    key: "tmpValue",
    typeHint: "",
    source: createValueSource("ctx"),
    listInput: createValueSource("serviceCall"),
    listCompose: createListCompose(),
    listSteps: [createStep("filter"), createStep("map")],
  }
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}

function normalizeHttpKvBinding(raw: any): HttpKvBinding {
  return {
    ...createHttpKvBinding("const"),
    ...(raw || {}),
  }
}

function parseJsonObjectEntries(raw: unknown): Array<{ key: string; value: unknown }> {
  if (typeof raw !== "string" || !raw.trim()) return []
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return []
    return Object.entries(parsed).map(([key, value]) => ({ key, value }))
  } catch {
    return []
  }
}

function toConstText(value: unknown): string {
  if (typeof value === "string") return value
  if (value === null || value === undefined) return ""
  if (typeof value === "number" || typeof value === "boolean") return String(value)
  try {
    return JSON.stringify(value)
  } catch {
    return ""
  }
}

function normalizeHttpBindings(rawList: any, legacyJsonRaw: unknown): HttpKvBinding[] {
  if (Array.isArray(rawList) && rawList.length > 0) {
    return rawList.map((item: any) => normalizeHttpKvBinding(item))
  }

  const fromLegacy = parseJsonObjectEntries(legacyJsonRaw).map(({ key, value }) => ({
    ...createHttpKvBinding("const"),
    key,
    valueKind: "const" as const,
    valueConst: toConstText(value),
  }))
  return fromLegacy.length > 0 ? fromLegacy : [createHttpKvBinding("const")]
}

function normalizeHttpCall(rawHttpCall: any): HttpCallConfig {
  const next: HttpCallConfig = {
    ...createHttpCall(),
    ...(rawHttpCall || {}),
    headers: normalizeHttpBindings(rawHttpCall?.headers, rawHttpCall?.headersJson),
    query: normalizeHttpBindings(rawHttpCall?.query, rawHttpCall?.queryJson),
  }
  if (next.method === "GET") {
    next.bodyJson = ""
  }
  return next
}

function normalizeSource(raw: any, defaultKind: SourceKind): ValueSource {
  const argsRaw = Array.isArray(raw?.serviceCall?.args) ? raw.serviceCall.args : []
  const rawServiceCall = raw?.serviceCall || {}
  const { timeoutMs: _timeoutMs, fallbackText: _fallbackText, ...serviceCallRest } = rawServiceCall
  const rawHttpCall = raw?.httpCall || {}
  return {
    ...createValueSource(raw?.kind || defaultKind),
    ...(raw || {}),
    serviceCall: {
      ...createServiceCall(),
      ...serviceCallRest,
      args: argsRaw.length > 0
        ? argsRaw.map((item: any) => ({ ...createServiceCallArg(), ...item }))
        : [createServiceCallArg()],
    },
    httpCall: {
      ...normalizeHttpCall(rawHttpCall),
    },
  }
}

function normalizeListSteps(raw: any): ListPipelineStep[] {
  if (!Array.isArray(raw) || raw.length === 0) return [createStep("filter"), createStep("map")]
  return raw.map((s: any) => ({ ...createStep(s?.op || "map"), ...s }))
}

function normalizeListComposeField(raw: any): ListComposeField {
  const base = createListComposeField()
  const sourceRaw = raw?.source || {}
  return {
    ...base,
    ...(raw || {}),
    source: {
      ...createListComposeFieldSource(sourceRaw?.kind || "ctx"),
      ...sourceRaw,
      serviceCall: {
        ...createServiceCall(),
        ...(sourceRaw?.serviceCall || {}),
      },
    },
  }
}

function normalizeListCompose(raw: any): ListComposeConfig {
  const fieldsRaw = Array.isArray(raw?.fields) ? raw.fields : []
  return {
    ...createListCompose(),
    ...(raw || {}),
    fields: fieldsRaw.length > 0 ? fieldsRaw.map((item: any) => normalizeListComposeField(item)) : [createListComposeField()],
  }
}

function normalizeArgPlan(raw: any): ArgPlan {
  const base = createArgPlan()
  const { onError: _onError, ...rest } = raw || {}
  const plan: ArgPlan = {
    ...base,
    ...rest,
    source: normalizeSource(rest?.source, "ctx"),
    listInput: normalizeSource(rest?.listInput, "serviceCall"),
    listCompose: normalizeListCompose(rest?.listCompose),
    listSteps: normalizeListSteps(rest?.listSteps),
  }
  applyDerivedListItemType(plan)
  return plan
}

function normalizeTempPlan(raw: any): TempPlan {
  const base = createTempPlan()
  const { onError: _onError, ...rest } = raw || {}
  const plan: TempPlan = {
    ...base,
    ...rest,
    source: normalizeSource(rest?.source, "ctx"),
    listInput: normalizeSource(rest?.listInput, "serviceCall"),
    listCompose: normalizeListCompose(rest?.listCompose),
    listSteps: normalizeListSteps(rest?.listSteps),
  }
  applyDerivedListItemType(plan)
  return plan
}

function normalizeModel(raw: any): ParamPlansV2Model {
  const tempPlansRaw = Array.isArray(raw?.tempPlans) ? raw.tempPlans : []
  const argPlansRaw = Array.isArray(raw?.argPlans) ? raw.argPlans : []

  const tempPlans = tempPlansRaw.map((item: any) => normalizeTempPlan(item))
  const argPlans = argPlansRaw.length > 0
    ? argPlansRaw.map((item: any) => normalizeArgPlan(item))
    : [createArgPlan()]

  return { tempPlans, argPlans }
}

const form = ref<ParamPlansV2Model>(normalizeModel(props.modelValue))
const tempKeyOptions = computed(() =>
  form.value.tempPlans
    .map((item) => String(item.key || "").trim())
    .filter((item) => Boolean(item))
)
const editingTempPlanId = ref("")
const editingTempPlan = computed(() => form.value.tempPlans.find((item) => item.id === editingTempPlanId.value) || null)
const editingArgPlanId = ref("")
const editingArgPlan = computed(() => form.value.argPlans.find((item) => item.id === editingArgPlanId.value) || null)
const sourceEditorMode = ref<EditorMode>("quick")

const inputDefs = computed<InputMeta[]>(() => {
  if (!Array.isArray(props.inputDefs)) return []
  return props.inputDefs
    .map((item: any) => ({
      name: String(item?.name || "").trim(),
      valueType: typeof item?.valueType === "string" ? item.valueType : "",
      typeName: typeof item?.typeName === "string" ? item.typeName : "",
    }))
    .filter((item) => Boolean(item.name))
})

const strictTargetMode = computed(() => inputDefs.value.length > 0)
const maxArgPlanCount = computed(() => (strictTargetMode.value ? inputDefs.value.length : Number.MAX_SAFE_INTEGER))
const canAddArgPlan = computed(() => form.value.argPlans.length < maxArgPlanCount.value)
const inputNameSet = computed(() => new Set(inputDefs.value.map((item) => item.name)))
const advancedSourceKinds: SourceKind[] = ["httpCall", "listPipeline"]
const quickSourceKinds: SourceKind[] = ["ctx", "const", "serviceCall"]
const sourceEditorHint = computed(() =>
  sourceEditorMode.value === "quick"
    ? "快速模式：仅展示 ctx / 常量 / 服务调用，适合绝大多数入参配置。"
    : "高级模式：额外开放 HTTP 调用与 List 管线，适合复杂补数与列表组装场景。"
)
const sourceGuideKindsText = computed(() =>
  sourceEditorMode.value === "quick"
    ? "ctx / const / serviceCall"
    : "ctx / const / serviceCall / httpCall / listPipeline"
)

function isAdvancedSourceKind(kind: SourceKind): boolean {
  return advancedSourceKinds.includes(kind)
}

function setSourceEditorMode(mode: EditorMode) {
  sourceEditorMode.value = mode
}

function ensureSourceEditorModeForKind(kind?: SourceKind) {
  if (kind && isAdvancedSourceKind(kind)) {
    sourceEditorMode.value = "advanced"
  }
}

function getSourceOptions(currentKind?: SourceKind) {
  const kinds = sourceEditorMode.value === "advanced"
    ? [...quickSourceKinds, ...advancedSourceKinds]
    : [...quickSourceKinds]
  if (currentKind && !kinds.includes(currentKind)) {
    kinds.push(currentKind)
  }
  return kinds.map((kind) => ({
    value: kind,
    label: getSourceKindLabel(kind),
    advanced: isAdvancedSourceKind(kind),
  }))
}

function formatSourceOptionLabel(option: { label: string; advanced: boolean }) {
  if (sourceEditorMode.value === "quick" && option.advanced) {
    return `${option.label}（高级）`
  }
  return option.label
}

function normalizeTypeToken(value: string): string {
  const raw = value.trim().toLowerCase()
  if (!raw) return ""
  if (raw === "string" || raw === "java.lang.string") return "string"
  if (raw === "boolean" || raw === "java.lang.boolean" || raw === "bool") return "boolean"
  if (raw === "int" || raw === "integer" || raw === "java.lang.integer") return "int"
  if (raw === "long" || raw === "java.lang.long") return "long"
  if (raw === "double" || raw === "java.lang.double") return "double"
  if (raw === "float" || raw === "java.lang.float") return "float"
  if (raw === "number" || raw === "java.lang.number" || raw === "bigdecimal" || raw === "java.math.bigdecimal") return "number"
  if (raw === "object") return "object"
  if (raw === "array" || raw.startsWith("java.util.list") || raw.startsWith("list<")) return "list"
  return raw
}

function getMetaByTarget(target: string): InputMeta | undefined {
  if (!target) return undefined
  return inputDefs.value.find((item) => item.name === target)
}

function getExpectedType(meta?: InputMeta): string {
  if (!meta) return ""
  return (meta.typeName || meta.valueType || "").trim()
}

function isTypeCompatible(expectedType: string, actualType: string): boolean {
  const expected = normalizeTypeToken(expectedType)
  const actual = normalizeTypeToken(actualType)
  if (!expected || !actual) return true
  if (expected === actual) return true

  if (expected === "number" && ["int", "long", "double", "float", "number"].includes(actual)) return true
  if (actual === "number" && ["int", "long", "double", "float", "number"].includes(expected)) return true

  if (expected === "list" && actual === "list") return true
  return false
}

function defaultTypeHint(meta?: InputMeta): string {
  if (!meta) return ""
  if (meta.typeName && meta.typeName.trim()) return meta.typeName.trim()
  const vt = (meta.valueType || "").toUpperCase()
  if (vt === "STRING") return "java.lang.String"
  if (vt === "BOOLEAN") return "java.lang.Boolean"
  if (vt === "NUMBER") return "java.lang.Number"
  if (vt === "ARRAY") return "java.util.List<java.lang.Object>"
  if (vt === "OBJECT") return "java.lang.Object"
  return vt || ""
}

function extractFirstGenericArg(value: string): string {
  const text = (value || "").trim()
  if (!text) return ""
  let depth = 0
  for (let i = 0; i < text.length; i += 1) {
    const ch = text[i]
    if (ch === "<") depth += 1
    if (ch === ">") depth = Math.max(0, depth - 1)
    if (ch === "," && depth === 0) {
      return text.slice(0, i).trim()
    }
  }
  return text
}

function deriveListItemType(typeHint: string): string {
  const raw = (typeHint || "").trim()
  if (!raw) return "java.lang.Object"

  if (raw.endsWith("[]")) {
    const elementType = raw.slice(0, -2).trim()
    return elementType || "java.lang.Object"
  }

  const normalized = raw.toLowerCase().replace(/\s+/g, "")
  const isListLike =
    normalized === "array"
    || normalized === "list"
    || normalized === "java.util.list"
    || normalized.startsWith("list<")
    || normalized.startsWith("java.util.list<")
  if (!isListLike) {
    return "java.lang.Object"
  }

  const left = raw.indexOf("<")
  const right = raw.lastIndexOf(">")
  if (left >= 0 && right > left) {
    const inner = raw.slice(left + 1, right).trim()
    const firstArg = extractFirstGenericArg(inner)
    return firstArg || "java.lang.Object"
  }
  return "java.lang.Object"
}

function applyDerivedListItemType(plan: { typeHint: string; listCompose: ListComposeConfig }) {
  plan.listCompose.itemTypeHint = deriveListItemType(plan.typeHint)
}

function applyDerivedListItemTypes(model: ParamPlansV2Model): ParamPlansV2Model {
  model.tempPlans.forEach((plan) => applyDerivedListItemType(plan))
  model.argPlans.forEach((plan) => applyDerivedListItemType(plan))
  return model
}

function getResolvedListComposeItemType(plan: { typeHint: string; listCompose: ListComposeConfig } | null): string {
  if (!plan) return "java.lang.Object"
  return deriveListItemType(plan.typeHint)
}

function isArgTargetTaken(target: string, planId: string, plans: ArgPlan[] = form.value.argPlans): boolean {
  if (!target) return false
  return plans.some((plan) => plan.id !== planId && plan.target === target)
}

function firstAvailableArgTarget(planId?: string, plans: ArgPlan[] = form.value.argPlans): string {
  for (const item of inputDefs.value) {
    const used = plans.some((plan) => plan.id !== planId && plan.target === item.name)
    if (!used) return item.name
  }
  return inputDefs.value[0]?.name || ""
}

function ensureArgTarget(plan: ArgPlan, plans: ArgPlan[] = form.value.argPlans) {
  if (!strictTargetMode.value) return
  const invalid = !plan.target || !inputNameSet.value.has(plan.target) || isArgTargetTaken(plan.target, plan.id, plans)
  if (invalid) {
    plan.target = firstAvailableArgTarget(plan.id, plans)
  }
  if (!plan.typeHint) {
    plan.typeHint = defaultTypeHint(getMetaByTarget(plan.target))
  }
}

function getArgPlanErrors(plan: ArgPlan): string[] {
  const errors: string[] = []
  if (!plan.target?.trim()) {
    errors.push("目标参数不能为空。")
  }
  if (strictTargetMode.value && plan.target && !inputNameSet.value.has(plan.target)) {
    errors.push(`目标参数 ${plan.target} 不在当前服务入参中。`)
  }
  if (plan.target && isArgTargetTaken(plan.target, plan.id)) {
    errors.push(`目标参数 ${plan.target} 已被其他计划占用，不能重复。`)
  }
  if (plan.source?.kind === "httpCall") {
    pushHttpCallErrors(errors, plan.source.httpCall)
  }
  return errors
}


function getArgPlanWarnings(plan: ArgPlan): string[] {
  if (!strictTargetMode.value) return []
  const target = plan.target?.trim()
  if (!target) return []
  const meta = getMetaByTarget(target)
  if (!meta) return []

  const expectedType = getExpectedType(meta)
  const actualType = (plan.typeHint || "").trim()
  if (!expectedType || !actualType) return []
  if (isTypeCompatible(expectedType, actualType)) return []

  return [`类型提示可能不匹配：入参期望 ${expectedType}，当前为 ${actualType}。`]
}

function isValidJsonText(value: string): boolean {
  const text = (value || "").trim()
  if (!text) return false
  try {
    JSON.parse(text)
    return true
  } catch {
    return false
  }
}

function pushHttpCallErrors(errors: string[], httpRaw?: HttpCallConfig) {
  const http = normalizeHttpCall(httpRaw || createHttpCall())
  if (!http.url?.trim()) {
    errors.push("HTTP 调用 URL 不能为空。")
  }
  for (const item of http.headers) {
    const hasAnyValue = Boolean(
      item.key?.trim()
      || item.valuePath?.trim()
      || item.valueConst?.trim()
      || item.tempKey?.trim()
    )
    if (!hasAnyValue) continue
    if (!item.key?.trim()) {
      errors.push("HTTP Header 存在空 Key，请补全或删除。")
      continue
    }
    if (item.valueKind === "ctx" && !item.valuePath?.trim()) {
      errors.push(`HTTP Header ${item.key} 来源为 ctx 时路径不能为空。`)
    }
    if (item.valueKind === "tempVar" && !item.tempKey?.trim()) {
      errors.push(`HTTP Header ${item.key} 来源为 tempVar 时 tempKey 不能为空。`)
    }
  }
  for (const item of http.query) {
    const hasAnyValue = Boolean(
      item.key?.trim()
      || item.valuePath?.trim()
      || item.valueConst?.trim()
      || item.tempKey?.trim()
    )
    if (!hasAnyValue) continue
    if (!item.key?.trim()) {
      errors.push("HTTP Query 存在空 Key，请补全或删除。")
      continue
    }
    if (item.valueKind === "ctx" && !item.valuePath?.trim()) {
      errors.push(`HTTP Query ${item.key} 来源为 ctx 时路径不能为空。`)
    }
    if (item.valueKind === "tempVar" && !item.tempKey?.trim()) {
      errors.push(`HTTP Query ${item.key} 来源为 tempVar 时 tempKey 不能为空。`)
    }
  }
  if (http.method !== "GET" && http.bodyJson?.trim() && !isValidJsonText(http.bodyJson)) {
    errors.push("HTTP Body 必须是合法 JSON。")
  }
}


function isTempKeyTaken(key: string, planId: string): boolean {
  if (!key) return false
  return form.value.tempPlans.some((plan) => plan.id !== planId && plan.key === key)
}

function getTempPlanErrors(plan: TempPlan): string[] {
  const errors: string[] = []
  const key = (plan.key || "").trim()
  if (!key) {
    errors.push("临时变量 Key 不能为空。")
    return errors
  }
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(key)) {
    errors.push("临时变量 Key 仅支持字母/数字/下划线，且不能数字开头。")
  }
  if (isTempKeyTaken(key, plan.id)) {
    errors.push(`临时变量 Key ${key} 已存在，不能重复。`)
  }
  if (plan.source?.kind === "httpCall") {
    pushHttpCallErrors(errors, plan.source.httpCall)
  }
  return errors
}


function getTempPlanWarnings(plan: TempPlan): string[] {
  const warnings: string[] = []
  const key = (plan.key || "").trim()
  if (!key) return warnings
  if (key.toLowerCase().startsWith("ctx") || key.toLowerCase().startsWith("request")) {
    warnings.push("建议避免使用 ctx/request 开头，防止和系统上下文命名冲突。")
  }
  return warnings
}

function formatInputOption(meta: InputMeta): string {
  const typeText = meta.typeName || meta.valueType || ""
  return typeText ? `${meta.name} - ${typeText}` : meta.name
}

function applyArgConstraints(argPlans: ArgPlan[]): ArgPlan[] {
  if (!strictTargetMode.value) return argPlans
  const limited = argPlans.slice(0, maxArgPlanCount.value)
  limited.forEach((plan) => {
    ensureArgTarget(plan, limited)
    applyDerivedListItemType(plan)
  })
  if (limited.length === 0 && inputDefs.value.length > 0) {
    const plan = createArgPlan()
    ensureArgTarget(plan, [plan])
    applyDerivedListItemType(plan)
    return [plan]
  }
  return limited
}

watch(
  () => props.modelValue,
  (next) => {
    const normalized = normalizeModel(next)
    normalized.argPlans = applyArgConstraints(normalized.argPlans)
    form.value = applyDerivedListItemTypes(normalized)
  },
  { deep: true, immediate: true }
)

watch(
  inputDefs,
  () => {
    const next = clone(form.value)
    next.argPlans = applyArgConstraints(next.argPlans)
    form.value = applyDerivedListItemTypes(next)
  },
  { deep: true, immediate: true }
)

watch(
  form,
  (next) => {
    const payload = applyDerivedListItemTypes(clone(next))
    emit("update:modelValue", payload)
  },
  { deep: true }
)

watch(
  () => form.value.tempPlans.map((item) => item.id),
  (ids) => {
    if (!editingTempPlanId.value) return
    if (!ids.includes(editingTempPlanId.value)) {
      editingTempPlanId.value = ""
    }
  },
  { deep: true }
)

watch(
  () => form.value.argPlans.map((item) => item.id),
  (ids) => {
    if (!editingArgPlanId.value) return
    if (!ids.includes(editingArgPlanId.value)) {
      editingArgPlanId.value = ""
    }
  },
  { deep: true }
)

function openTempPlanEditor(planId: string) {
  editingTempPlanId.value = planId
  const target = form.value.tempPlans.find((item) => item.id === planId)
  ensureSourceEditorModeForKind(target?.source?.kind)
}

function closeTempPlanEditor() {
  editingTempPlanId.value = ""
}

function openArgPlanEditor(planId: string) {
  editingArgPlanId.value = planId
  const target = form.value.argPlans.find((item) => item.id === planId)
  ensureSourceEditorModeForKind(target?.source?.kind)
}

function closeArgPlanEditor() {
  editingArgPlanId.value = ""
}

function addArgPlan() {
  if (!canAddArgPlan.value) return
  const next = createArgPlan()
  if (strictTargetMode.value) {
    ensureArgTarget(next)
  }
  applyDerivedListItemType(next)
  form.value.argPlans.push(next)
  openArgPlanEditor(next.id)
}

function removeArgPlan(index: number) {
  if (form.value.argPlans.length <= 1) return
  const removed = form.value.argPlans[index]
  form.value.argPlans.splice(index, 1)
  form.value.argPlans = applyArgConstraints(form.value.argPlans)
  if (removed && removed.id === editingArgPlanId.value) {
    closeArgPlanEditor()
  }
}

function addTempPlan() {
  const next = createTempPlan()
  const existingKeys = new Set(form.value.tempPlans.map((item) => item.key))
  let idx = 1
  while (existingKeys.has(next.key)) {
    idx += 1
    next.key = `tmpValue${idx}`
  }
  applyDerivedListItemType(next)
  form.value.tempPlans.push(next)
  openTempPlanEditor(next.id)
}

function removeTempPlan(index: number) {
  const removed = form.value.tempPlans[index]
  form.value.tempPlans.splice(index, 1)
  if (removed && removed.id === editingTempPlanId.value) {
    closeTempPlanEditor()
  }
}

function addStep(plan: { listSteps: ListPipelineStep[] }) {
  plan.listSteps.push(createStep("map"))
}

function removeStep(plan: { listSteps: ListPipelineStep[] }, stepIndex: number) {
  if (plan.listSteps.length <= 1) return
  plan.listSteps.splice(stepIndex, 1)
}

function addListComposeField(plan: { listCompose: ListComposeConfig }) {
  plan.listCompose.fields.push(createListComposeField())
}

function removeListComposeField(plan: { listCompose: ListComposeConfig }, fieldIndex: number) {
  if (plan.listCompose.fields.length <= 1) return
  plan.listCompose.fields.splice(fieldIndex, 1)
}

function addHttpHeader(http: HttpCallConfig) {
  http.headers.push(createHttpKvBinding("const"))
}

function removeHttpHeader(http: HttpCallConfig, index: number) {
  if (http.headers.length <= 1) return
  http.headers.splice(index, 1)
}

function addHttpQuery(http: HttpCallConfig) {
  http.query.push(createHttpKvBinding("const"))
}

function removeHttpQuery(http: HttpCallConfig, index: number) {
  if (http.query.length <= 1) return
  http.query.splice(index, 1)
}

function shouldShowHttpBody(method: HttpCallConfig["method"]): boolean {
  return method !== "GET"
}

function getHttpPayloadModeHint(method: HttpCallConfig["method"]): string {
  if (method === "GET") return "发送内容：Header + Query（GET 不发送 Body）。"
  if (method === "DELETE") return "发送内容：Header + Query + Body（Body 可选）。"
  return "发送内容：Header + Query + Body。"
}

function onHttpMethodChange(http: HttpCallConfig) {
  if (!shouldShowHttpBody(http.method)) {
    http.bodyJson = ""
  }
}

function getListComposeFieldErrors(field: ListComposeField): string[] {
  const errors: string[] = []
  if (!field.targetField?.trim()) {
    errors.push("目标字段不能为空。")
  }
  if (field.source.kind === "ctx" && !field.source.path?.trim()) {
    errors.push("来源为 ctx 时，路径不能为空。")
  }
  if (field.source.kind === "const" && !field.source.constValue?.trim()) {
    errors.push("来源为常量时，值不能为空。")
  }
  if (field.source.kind === "tempVar" && !field.source.tempKey?.trim()) {
    errors.push("来源为临时变量时，tempKey 不能为空。")
  }
  if (field.source.kind === "serviceCall" && !field.source.serviceCall?.fn?.trim()) {
    errors.push("来源为服务调用时，需要先选择服务方法。")
  }
  return errors
}

function getSourceHint(kind: SourceKind) {
  if (kind === "ctx") return "从 request/ctx 读取，开销最低。"
  if (kind === "const") return "使用常量 JSON，适合默认值和开关。"
  if (kind === "serviceCall") return "通过函数别名调用项目内服务；若返回对象可再配置结果提取路径。"
  if (kind === "httpCall") return "通过 HTTP 请求取数；支持结果路径提取，适合临时变量补数。"
  return "先拿 List，再用字段映射组装目标元素，最后可选执行 filter/map/enrich/groupBy/reduce。"
}

function getSourceKindLabel(kind: SourceKind) {
  if (kind === "ctx") return "上下文"
  if (kind === "const") return "常量"
  if (kind === "serviceCall") return "服务调用"
  if (kind === "httpCall") return "HTTP 调用"
  return "List 管线"
}

function getArgPlanStatusText(plan: ArgPlan) {
  const errors = getArgPlanErrors(plan)
  if (errors.length > 0) return "待修正"
  const warnings = getArgPlanWarnings(plan)
  if (warnings.length > 0) return "有告警"
  return "已配置"
}

function getTempPlanStatusText(plan: TempPlan) {
  const errors = getTempPlanErrors(plan)
  if (errors.length > 0) return "待修正"
  const warnings = getTempPlanWarnings(plan)
  if (warnings.length > 0) return "有告警"
  return "已配置"
}

function getTempRefPath(key: string) {
  const safe = (key || "").trim()
  return safe ? `ctx.__tmp.<nodeId>.${safe}` : "ctx.__tmp.<nodeId>.<key>"
}

function classifyQuickCheckCategory(message: string): QuickCheckItem["category"] {
  if (message.includes("类型提示可能不匹配")) return "类型不匹配"
  if (message.includes("重复") || message.includes("占用")) return "重复字段"
  if (message.includes("不能为空") || message.includes("不在当前服务入参中") || message.includes("需要先选择服务方法") || message.includes("URL 不能为空")) {
    return "缺少必填映射"
  }
  return "其他问题"
}

function pushQuickCheckItem(
  bucket: QuickCheckItem[],
  dedupe: Set<string>,
  level: QuickCheckLevel,
  message: string,
  category?: QuickCheckItem["category"],
) {
  const nextCategory = category || classifyQuickCheckCategory(message)
  const key = `${level}|${nextCategory}|${message}`
  if (dedupe.has(key)) return
  dedupe.add(key)
  bucket.push({ level, category: nextCategory, message })
}

function collectDuplicateFieldIssues(
  bucket: QuickCheckItem[],
  dedupe: Set<string>,
  fields: ListComposeField[],
  scopeLabel: string,
) {
  const countMap = new Map<string, number>()
  for (const field of fields) {
    const key = (field.targetField || "").trim()
    if (!key) continue
    countMap.set(key, (countMap.get(key) || 0) + 1)
  }
  for (const [fieldPath, count] of countMap.entries()) {
    if (count <= 1) continue
    pushQuickCheckItem(
      bucket,
      dedupe,
      "error",
      `${scopeLabel} 目标字段 ${fieldPath} 重复 ${count} 次。`,
      "重复字段",
    )
  }
}

function collectQuickCheckItems(): QuickCheckItem[] {
  const items: QuickCheckItem[] = []
  const dedupe = new Set<string>()

  if (strictTargetMode.value) {
    const configuredTargets = new Set(
      form.value.argPlans
        .map((plan) => (plan.target || "").trim())
        .filter((target) => Boolean(target)),
    )
    for (const input of inputDefs.value) {
      if (configuredTargets.has(input.name)) continue
      pushQuickCheckItem(items, dedupe, "error", `入参 ${input.name} 尚未配置映射。`, "缺少必填映射")
    }
  }

  form.value.argPlans.forEach((plan, index) => {
    const prefix = `入参计划${index + 1}`
    getArgPlanErrors(plan).forEach((msg) => pushQuickCheckItem(items, dedupe, "error", `${prefix}：${msg}`))
    getArgPlanWarnings(plan).forEach((msg) => pushQuickCheckItem(items, dedupe, "warning", `${prefix}：${msg}`))

    if (plan.source.kind === "listPipeline") {
      plan.listCompose.fields.forEach((field) => {
        getListComposeFieldErrors(field).forEach((msg) => pushQuickCheckItem(items, dedupe, "error", `${prefix}：${msg}`))
      })
      collectDuplicateFieldIssues(items, dedupe, plan.listCompose.fields, `${prefix}（List 映射）`)
    }
  })

  form.value.tempPlans.forEach((plan, index) => {
    const prefix = `临时变量${index + 1}`
    getTempPlanErrors(plan).forEach((msg) => pushQuickCheckItem(items, dedupe, "error", `${prefix}：${msg}`))
    getTempPlanWarnings(plan).forEach((msg) => pushQuickCheckItem(items, dedupe, "warning", `${prefix}：${msg}`))

    if (plan.source.kind === "listPipeline") {
      plan.listCompose.fields.forEach((field) => {
        getListComposeFieldErrors(field).forEach((msg) => pushQuickCheckItem(items, dedupe, "error", `${prefix}：${msg}`))
      })
      collectDuplicateFieldIssues(items, dedupe, plan.listCompose.fields, `${prefix}（List 映射）`)
    }
  })

  return items
}

const quickCheckTick = ref(0)
const quickCheckItems = computed(() => {
  void quickCheckTick.value
  return collectQuickCheckItems()
})
const quickCheckErrorCount = computed(() => quickCheckItems.value.filter((item) => item.level === "error").length)
const quickCheckWarningCount = computed(() => quickCheckItems.value.filter((item) => item.level === "warning").length)
const quickCheckTopItems = computed(() => quickCheckItems.value.slice(0, 8))
const quickCheckCategoryStats = computed(() => {
  const map = new Map<QuickCheckItem["category"], { error: number; warning: number }>()
  quickCheckItems.value.forEach((item) => {
    if (!map.has(item.category)) {
      map.set(item.category, { error: 0, warning: 0 })
    }
    const bucket = map.get(item.category)!
    if (item.level === "error") bucket.error += 1
    else bucket.warning += 1
  })
  return Array.from(map.entries()).map(([category, count]) => ({
    category,
    error: count.error,
    warning: count.warning,
    total: count.error + count.warning,
  }))
})

function refreshQuickCheck() {
  quickCheckTick.value += 1
}
</script>

<template>
  <div class="param-builder">
    <div class="header">
      <div class="header-main">
        <div class="title">参数构造管线（V2）</div>
        <div class="mode-switch" role="group" aria-label="参数配置模式">
          <button class="btn mini" :class="{ active: sourceEditorMode === 'quick' }" @click="setSourceEditorMode('quick')">快速</button>
          <button class="btn mini" :class="{ active: sourceEditorMode === 'advanced' }" @click="setSourceEditorMode('advanced')">高级</button>
        </div>
      </div>
      <button class="btn" :disabled="!canAddArgPlan" @click="addArgPlan">+ 添加入参计划</button>
    </div>
    <div class="mode-note">{{ sourceEditorHint }}</div>

    <div v-if="strictTargetMode" class="constraint-note">
      当前服务入参数量：{{ inputDefs.length }}，已配置入参计划：{{ form.argPlans.length }}。
      <span v-if="inputDefs.length === 1">单入参场景仅允许 1 条入参计划。</span>
    </div>

    <div class="quick-check-card" :class="{ pass: quickCheckItems.length === 0 }">
      <div class="quick-check-head">
        <div class="quick-check-title">提交前快速体检</div>
        <button class="btn mini" @click="refreshQuickCheck">重新体检</button>
      </div>
      <div v-if="quickCheckItems.length === 0" class="quick-check-pass">未发现问题，可提交。</div>
      <template v-else>
        <div class="quick-check-summary">
          <span class="quick-check-error">错误 {{ quickCheckErrorCount }}</span>
          <span class="quick-check-warning">告警 {{ quickCheckWarningCount }}</span>
        </div>
        <div class="quick-check-categories">
          <span v-for="item in quickCheckCategoryStats" :key="item.category" class="category-pill">
            {{ item.category }}：{{ item.total }}
          </span>
        </div>
        <div class="quick-check-list">
          <div
            v-for="(item, index) in quickCheckTopItems"
            :key="`${item.level}_${index}_${item.message}`"
            class="quick-check-item"
            :class="item.level"
          >
            {{ item.message }}
          </div>
        </div>
      </template>
    </div>

    <details class="guide" open>
      <summary>使用步骤（节点内两阶段）</summary>
      <ol class="guide-list">
        <li>先配置临时变量计划（可选）：通过 {{ sourceGuideKindsText }} 预计算中间结果。</li>
        <li>临时变量会以 <code>ctx.__tmp.&lt;nodeId&gt;.&lt;key&gt;</code> 形式被引用。</li>
        <li>再配置入参计划：将每个真实入参（如 <code>a</code>）绑定到最终值来源。</li>
        <li>List 管线优先使用“目标元素字段映射”：每个字段独立选来源，天然支持分散参数组装。</li>
        <li>在严格模式下（识别到服务入参）会限制计划数量、禁止重复目标参数并做类型提示。</li>
      </ol>
      <div class="guide-note">
        当前接入状态：配置会保存到节点 <code>data.paramPlansV2</code>；发布编译与运行时执行按 V2 继续接入。
      </div>
    </details>

    <div class="section-head">
      <div class="section-title">临时变量计划（Temp）</div>
      <button class="btn mini" @click="addTempPlan">+ 添加临时变量</button>
    </div>

    <div v-if="form.tempPlans.length === 0" class="muted tiny">暂无临时变量计划</div>

    <div class="plans" v-if="form.tempPlans.length > 0">
      <div v-for="(plan, planIndex) in form.tempPlans" :key="plan.id" class="plan-card plan-summary-card">
        <div class="plan-header">
          <div class="muted small">临时变量 {{ planIndex + 1 }}</div>
          <span class="status-pill">{{ getTempPlanStatusText(plan) }}</span>
        </div>

        <div class="summary-grid">
          <div class="summary-item">
            <span class="muted tiny">变量 Key</span>
            <span class="summary-value">{{ plan.key || "未设置" }}</span>
          </div>
          <div class="summary-item">
            <span class="muted tiny">类型提示</span>
            <span class="summary-value">{{ plan.typeHint || "未设置" }}</span>
          </div>
          <div class="summary-item">
            <span class="muted tiny">数据来源</span>
            <span class="summary-value">{{ getSourceKindLabel(plan.source.kind) }}</span>
          </div>
        </div>

        <div class="muted tiny hint-line">引用路径：{{ getTempRefPath(plan.key) }}</div>

        <div v-if="getTempPlanErrors(plan).length > 0" class="plan-errors">
          <div v-for="msg in getTempPlanErrors(plan)" :key="msg" class="plan-error">{{ msg }}</div>
        </div>
        <div v-if="getTempPlanWarnings(plan).length > 0" class="plan-warnings">
          <div v-for="msg in getTempPlanWarnings(plan)" :key="msg" class="plan-warning">{{ msg }}</div>
        </div>

        <div class="row" style="justify-content:flex-end">
          <button class="btn mini" @click="openTempPlanEditor(plan.id)">编辑</button>
          <button class="btn mini" @click="removeTempPlan(planIndex)">删除</button>
        </div>
      </div>
    </div>

    <div v-if="editingTempPlan" class="arg-editor-mask" @click.self="closeTempPlanEditor">
      <div class="arg-editor-panel">
        <div class="arg-editor-header">
          <div>
            <div class="arg-editor-title">临时变量配置</div>
            <div class="muted tiny">以临时变量为入口，在弹框中完成完整配置。</div>
          </div>
          <button class="btn mini" @click="closeTempPlanEditor">关闭</button>
        </div>

        <div class="arg-editor-content">
          <div class="grid-2">
            <div class="field">
              <div class="muted tiny">变量 Key</div>
              <input class="input" v-model="editingTempPlan.key" placeholder="例如 userId / userName" />
            </div>
            <div class="field">
              <div class="muted tiny">类型提示（可选）</div>
              <input class="input" v-model="editingTempPlan.typeHint" placeholder="java.lang.String / java.util.List<com.xx.Dto>" />
            </div>
          </div>

          <div class="muted tiny hint-line">引用路径：{{ getTempRefPath(editingTempPlan.key) }}</div>

          <div v-if="getTempPlanErrors(editingTempPlan).length > 0" class="plan-errors">
            <div v-for="msg in getTempPlanErrors(editingTempPlan)" :key="msg" class="plan-error">{{ msg }}</div>
          </div>
          <div v-if="getTempPlanWarnings(editingTempPlan).length > 0" class="plan-warnings">
            <div v-for="msg in getTempPlanWarnings(editingTempPlan)" :key="msg" class="plan-warning">{{ msg }}</div>
          </div>

          <div class="source-section">
            <div class="section-title">数据来源</div>
            <select class="input" v-model="editingTempPlan.source.kind">
              <option
                v-for="option in getSourceOptions(editingTempPlan.source.kind)"
                :key="`temp_${option.value}`"
                :value="option.value"
              >
                {{ formatSourceOptionLabel(option) }}
              </option>
            </select>
            <div class="muted tiny hint-line">{{ getSourceHint(editingTempPlan.source.kind) }}</div>

            <SourcePathInput
              v-if="editingTempPlan.source.kind === 'ctx'"
              :model-value="editingTempPlan.source.path"
              :options="props.sourcePathOptions ?? []"
              placeholder="ctx.user / request.body.items"
              @update:model-value="editingTempPlan.source.path = $event"
            />
            <input
              v-if="editingTempPlan.source.kind === 'const'"
              class="input"
              v-model="editingTempPlan.source.constValue"
              placeholder="常量值，JSON 字符串"
            />

            <template v-if="editingTempPlan.source.kind === 'serviceCall'">
              <ServiceCallEditor
                v-model="editingTempPlan.source.serviceCall"
                :project-key="props.projectKey"
                :temp-keys="tempKeyOptions"
                :source-path-options="props.sourcePathOptions"
              />
              <input
                class="input"
                v-model="editingTempPlan.source.serviceResultPath"
                placeholder="结果提取路径（可选），例如 data.name / $.data.name"
              />
              <div class="muted tiny hint-line">当服务返回对象但目标是单值时，配置要提取的属性路径。</div>
            </template>

            <template v-if="editingTempPlan.source.kind === 'httpCall'">
              <div class="pipeline">
                <div class="muted tiny">HTTP 请求配置</div>
                <div class="muted tiny">请求方法</div>
                <select class="input" v-model="editingTempPlan.source.httpCall.method" @change="onHttpMethodChange(editingTempPlan.source.httpCall)">
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                  <option value="PUT">PUT</option>
                  <option value="DELETE">DELETE</option>
                  <option value="PATCH">PATCH</option>
                </select>
                <div class="muted tiny hint-line">{{ getHttpPayloadModeHint(editingTempPlan.source.httpCall.method) }}</div>
                <div class="muted tiny">URL</div>
                <input
                  class="input"
                  v-model="editingTempPlan.source.httpCall.url"
                  placeholder="URL，例如 http://localhost:8080/api/user/query"
                />
                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">请求头（Header）</div>
                    <button class="btn mini" @click="addHttpHeader(editingTempPlan.source.httpCall)">+ Header</button>
                  </div>
                  <div v-for="(item, idx) in editingTempPlan.source.httpCall.headers" :key="item.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">Header {{ idx + 1 }}</div>
                      <button class="btn mini" @click="removeHttpHeader(editingTempPlan.source.httpCall, idx)">删</button>
                    </div>
                    <input class="input" v-model="item.key" placeholder="Key，例如 Authorization / X-Tenant-Id" />
                    <select class="input" v-model="item.valueKind">
                      <option value="ctx">值来源：ctx/request</option>
                      <option value="const">值来源：常量</option>
                      <option value="tempVar">值来源：临时变量</option>
                    </select>
                    <input v-if="item.valueKind === 'ctx'" class="input" v-model="item.valuePath" placeholder="request.headers.token / ctx.userId" />
                    <input v-if="item.valueKind === 'const'" class="input" v-model="item.valueConst" placeholder="常量值，例如 Bearer xxx" />
                    <input v-if="item.valueKind === 'tempVar'" class="input" v-model="item.tempKey" placeholder="临时变量 Key，例如 userToken" />
                  </div>
                </div>
                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">查询参数（Query）</div>
                    <button class="btn mini" @click="addHttpQuery(editingTempPlan.source.httpCall)">+ Query</button>
                  </div>
                  <div v-for="(item, idx) in editingTempPlan.source.httpCall.query" :key="item.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">Query {{ idx + 1 }}</div>
                      <button class="btn mini" @click="removeHttpQuery(editingTempPlan.source.httpCall, idx)">删</button>
                    </div>
                    <input class="input" v-model="item.key" placeholder="Key，例如 userId / pageNo" />
                    <select class="input" v-model="item.valueKind">
                      <option value="ctx">值来源：ctx/request</option>
                      <option value="const">值来源：常量</option>
                      <option value="tempVar">值来源：临时变量</option>
                    </select>
                    <input v-if="item.valueKind === 'ctx'" class="input" v-model="item.valuePath" placeholder="request.body.userId / ctx.pageNo" />
                    <input v-if="item.valueKind === 'const'" class="input" v-model="item.valueConst" placeholder="常量值，例如 1 / abc" />
                    <input v-if="item.valueKind === 'tempVar'" class="input" v-model="item.tempKey" placeholder="临时变量 Key，例如 uid" />
                  </div>
                </div>
                <div v-if="shouldShowHttpBody(editingTempPlan.source.httpCall.method)" class="muted tiny">请求体（Body JSON）</div>
                <textarea
                  v-if="shouldShowHttpBody(editingTempPlan.source.httpCall.method)"
                  class="input textarea"
                  v-model="editingTempPlan.source.httpCall.bodyJson"
                  placeholder='Body JSON，例如 {"name":"Tom"}'
                ></textarea>
                <div class="muted tiny">结果提取路径（可选）</div>
                <input
                  class="input"
                  v-model="editingTempPlan.source.httpCall.resultPath"
                  placeholder="结果提取路径（可选），例如 data.name / $.data.name"
                />
                <div class="muted tiny hint-line">建议用于临时变量补数，后续通过 tempVar 引用。</div>
              </div>
            </template>

            <template v-if="editingTempPlan.source.kind === 'listPipeline'">
              <div class="pipeline">
                <div class="muted tiny">List 输入源</div>
                <select class="input" v-model="editingTempPlan.listInput.kind">
                  <option value="ctx">上下文 List</option>
                  <option value="serviceCall">服务返回 List</option>
                  <option value="const">常量 List</option>
                </select>
                <SourcePathInput
                  v-if="editingTempPlan.listInput.kind === 'ctx'"
                  :model-value="editingTempPlan.listInput.path"
                  :options="props.sourcePathOptions ?? []"
                  placeholder="ctx.items / request.body.lines"
                  @update:model-value="editingTempPlan.listInput.path = $event"
                />
                <input
                  v-if="editingTempPlan.listInput.kind === 'const'"
                  class="input"
                  v-model="editingTempPlan.listInput.constValue"
                  placeholder="[{skuId:A}]"
                />
                <template v-if="editingTempPlan.listInput.kind === 'serviceCall'">
                  <ServiceCallEditor
                    v-model="editingTempPlan.listInput.serviceCall"
                    :project-key="props.projectKey"
                    :temp-keys="tempKeyOptions"
                    :source-path-options="props.sourcePathOptions"
                  />
                  <input
                    class="input"
                    v-model="editingTempPlan.listInput.serviceResultPath"
                    placeholder="列表提取路径（可选），例如 data.items / $.data.list"
                  />
                  <div class="muted tiny hint-line">当服务返回包装对象时，提取其中的 List 字段作为管线输入。</div>
                </template>

                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">目标元素字段映射（推荐）</div>
                    <button class="btn mini" @click="addListComposeField(editingTempPlan)">+ 字段</button>
                  </div>
                  <div class="muted tiny hint-line">适合分散来源：每个字段可独立来自 ctx/常量/临时变量/服务调用。</div>
                  <input
                    class="input"
                    :value="getResolvedListComposeItemType(editingTempPlan)"
                    readonly
                  />
                  <div class="muted tiny hint-line">目标元素类型由当前计划“类型提示”自动推导（只读）。</div>
                  <div v-for="(field, fieldIndex) in editingTempPlan.listCompose.fields" :key="field.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">字段 {{ fieldIndex + 1 }}</div>
                      <button class="btn mini" @click="removeListComposeField(editingTempPlan, fieldIndex)">删</button>
                    </div>
                    <input class="input" v-model="field.targetField" placeholder="目标字段，例如 skuId / profile.name" />
                    <input class="input" v-model="field.typeHint" placeholder="字段类型提示（可选），例如 java.lang.String" />
                    <select class="input" v-model="field.source.kind">
                      <option value="ctx">来源：ctx/request</option>
                      <option value="const">来源：常量</option>
                      <option value="tempVar">来源：临时变量</option>
                      <option value="serviceCall">来源：服务调用</option>
                    </select>
                    <SourcePathInput
                      v-if="field.source.kind === 'ctx'"
                      :model-value="field.source.path"
                      :options="props.sourcePathOptions ?? []"
                      placeholder="读取路径，例如 $.skuId / request.body.user.id"
                      @update:model-value="field.source.path = $event"
                    />
                    <input
                      v-if="field.source.kind === 'const'"
                      class="input"
                      v-model="field.source.constValue"
                      placeholder="常量值（JSON 字符串）"
                    />
                    <input
                      v-if="field.source.kind === 'tempVar'"
                      class="input"
                      v-model="field.source.tempKey"
                      :list="`tmp_field_${field.id}`"
                      placeholder="临时变量 key"
                    />
                    <datalist v-if="field.source.kind === 'tempVar'" :id="`tmp_field_${field.id}`">
                      <option v-for="key in tempKeyOptions" :key="key" :value="key" />
                    </datalist>
                    <template v-if="field.source.kind === 'serviceCall'">
                      <ServiceCallEditor
                        v-model="field.source.serviceCall"
                        :project-key="props.projectKey"
                        :temp-keys="tempKeyOptions"
                        :source-path-options="props.sourcePathOptions"
                      />
                      <input
                        class="input"
                        v-model="field.source.serviceResultPath"
                        placeholder="结果提取路径（可选），例如 data.name / $.data.name"
                      />
                    </template>
                    <div v-if="getListComposeFieldErrors(field).length > 0" class="plan-errors">
                      <div v-for="msg in getListComposeFieldErrors(field)" :key="msg" class="plan-error">{{ msg }}</div>
                    </div>
                  </div>
                </div>

                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">后处理步骤（可选）</div>
                    <button class="btn mini" @click="addStep(editingTempPlan)">+ 步骤</button>
                  </div>
                  <div v-for="(step, stepIndex) in editingTempPlan.listSteps" :key="step.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">步骤 {{ stepIndex + 1 }}</div>
                      <button class="btn mini" @click="removeStep(editingTempPlan, stepIndex)">删</button>
                    </div>
                    <select class="input" v-model="step.op">
                      <option value="filter">filter</option>
                      <option value="map">map</option>
                      <option value="enrich">enrich</option>
                      <option value="groupBy">groupBy</option>
                      <option value="reduce">reduce</option>
                    </select>
                    <input
                      v-if="step.op === 'filter' || step.op === 'groupBy' || step.op === 'reduce'"
                      class="input"
                      v-model="step.exprText"
                      placeholder="结构化表达式（占位），例如 $.qty > 0"
                    />
                    <textarea
                      v-if="step.op === 'map'"
                      class="input textarea"
                      v-model="step.mappingText"
                      placeholder="映射JSON（占位）"
                    ></textarea>
                    <input
                      v-if="step.op === 'enrich'"
                      class="input"
                      v-model="step.enrichFn"
                      placeholder="补数函数别名，例如 price.batchQuery"
                    />
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div class="arg-editor-footer">
          <button class="btn" @click="closeTempPlanEditor">完成</button>
        </div>
      </div>
    </div>

    <div class="section-head" style="margin-top: 4px">
      <div class="section-title">入参计划（Arg）</div>
    </div>

    <div class="plans arg-plan-list">
      <div v-for="(plan, planIndex) in form.argPlans" :key="plan.id" class="plan-card plan-summary-card">
        <div class="plan-header">
          <div class="muted small">入参计划 {{ planIndex + 1 }}</div>
          <span class="status-pill">{{ getArgPlanStatusText(plan) }}</span>
        </div>

        <div class="summary-grid">
          <div class="summary-item">
            <span class="muted tiny">{{ strictTargetMode ? "目标参数" : "目标路径" }}</span>
            <span class="summary-value">{{ plan.target || "未设置" }}</span>
          </div>
          <div class="summary-item">
            <span class="muted tiny">类型提示</span>
            <span class="summary-value">{{ plan.typeHint || "未设置" }}</span>
          </div>
          <div class="summary-item">
            <span class="muted tiny">数据来源</span>
            <span class="summary-value">{{ getSourceKindLabel(plan.source.kind) }}</span>
          </div>
        </div>

        <div v-if="getArgPlanErrors(plan).length > 0" class="plan-errors">
          <div v-for="msg in getArgPlanErrors(plan)" :key="msg" class="plan-error">{{ msg }}</div>
        </div>
        <div v-if="getArgPlanWarnings(plan).length > 0" class="plan-warnings">
          <div v-for="msg in getArgPlanWarnings(plan)" :key="msg" class="plan-warning">{{ msg }}</div>
        </div>

        <div class="row" style="justify-content:flex-end">
          <button class="btn mini" @click="openArgPlanEditor(plan.id)">编辑</button>
          <button class="btn mini" @click="removeArgPlan(planIndex)">删除</button>
        </div>
      </div>
    </div>

    <div v-if="editingArgPlan" class="arg-editor-mask" @click.self="closeArgPlanEditor">
      <div class="arg-editor-panel">
        <div class="arg-editor-header">
          <div>
            <div class="arg-editor-title">入参配置</div>
            <div class="muted tiny">以入参为入口，在弹框中完成完整配置。</div>
          </div>
          <button class="btn mini" @click="closeArgPlanEditor">关闭</button>
        </div>

        <div class="arg-editor-content">
          <div class="grid-2">
            <div class="field">
              <div class="muted tiny">{{ strictTargetMode ? "目标参数" : "目标路径" }}</div>
              <template v-if="strictTargetMode">
                <select
                  class="input"
                  v-model="editingArgPlan.target"
                  @change="!editingArgPlan.typeHint && (editingArgPlan.typeHint = defaultTypeHint(getMetaByTarget(editingArgPlan.target)))"
                >
                  <option value="">请选择入参</option>
                  <option
                    v-for="meta in inputDefs"
                    :key="meta.name"
                    :value="meta.name"
                    :disabled="isArgTargetTaken(meta.name, editingArgPlan.id)"
                  >
                    {{ formatInputOption(meta) }}
                  </option>
                </select>
              </template>
              <input
                v-else
                class="input"
                v-model="editingArgPlan.target"
                placeholder="req.items / req.user.profile"
              />
            </div>
            <div class="field">
              <div class="muted tiny">类型提示</div>
              <input class="input" v-model="editingArgPlan.typeHint" placeholder="java.lang.String / java.util.List<com.example.ItemDto>" />
            </div>
          </div>

          <div v-if="getArgPlanErrors(editingArgPlan).length > 0" class="plan-errors">
            <div v-for="msg in getArgPlanErrors(editingArgPlan)" :key="msg" class="plan-error">{{ msg }}</div>
          </div>
          <div v-if="getArgPlanWarnings(editingArgPlan).length > 0" class="plan-warnings">
            <div v-for="msg in getArgPlanWarnings(editingArgPlan)" :key="msg" class="plan-warning">{{ msg }}</div>
          </div>

          <div class="source-section">
            <div class="section-title">数据来源</div>
            <select class="input" v-model="editingArgPlan.source.kind">
              <option
                v-for="option in getSourceOptions(editingArgPlan.source.kind)"
                :key="`arg_${option.value}`"
                :value="option.value"
              >
                {{ formatSourceOptionLabel(option) }}
              </option>
            </select>
            <div class="muted tiny hint-line">{{ getSourceHint(editingArgPlan.source.kind) }}</div>

            <SourcePathInput
              v-if="editingArgPlan.source.kind === 'ctx'"
              :model-value="editingArgPlan.source.path"
              :options="props.sourcePathOptions ?? []"
              placeholder="ctx.user / request.body.items"
              @update:model-value="editingArgPlan.source.path = $event"
            />
            <input
              v-if="editingArgPlan.source.kind === 'const'"
              class="input"
              v-model="editingArgPlan.source.constValue"
              placeholder="常量值，JSON 字符串"
            />

            <template v-if="editingArgPlan.source.kind === 'serviceCall'">
              <ServiceCallEditor
                v-model="editingArgPlan.source.serviceCall"
                :project-key="props.projectKey"
                :temp-keys="tempKeyOptions"
                :source-path-options="props.sourcePathOptions"
              />
              <input
                class="input"
                v-model="editingArgPlan.source.serviceResultPath"
                placeholder="结果提取路径（可选），例如 data.name / $.data.name"
              />
              <div class="muted tiny hint-line">当服务返回对象但目标是单值时，配置要提取的属性路径。</div>
            </template>

            <template v-if="editingArgPlan.source.kind === 'httpCall'">
              <div class="pipeline">
                <div class="muted tiny">HTTP 请求配置</div>
                <div class="muted tiny">请求方法</div>
                <select class="input" v-model="editingArgPlan.source.httpCall.method" @change="onHttpMethodChange(editingArgPlan.source.httpCall)">
                  <option value="GET">GET</option>
                  <option value="POST">POST</option>
                  <option value="PUT">PUT</option>
                  <option value="DELETE">DELETE</option>
                  <option value="PATCH">PATCH</option>
                </select>
                <div class="muted tiny hint-line">{{ getHttpPayloadModeHint(editingArgPlan.source.httpCall.method) }}</div>
                <div class="muted tiny">URL</div>
                <input
                  class="input"
                  v-model="editingArgPlan.source.httpCall.url"
                  placeholder="URL，例如 http://localhost:8080/api/user/query"
                />
                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">请求头（Header）</div>
                    <button class="btn mini" @click="addHttpHeader(editingArgPlan.source.httpCall)">+ Header</button>
                  </div>
                  <div v-for="(item, idx) in editingArgPlan.source.httpCall.headers" :key="item.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">Header {{ idx + 1 }}</div>
                      <button class="btn mini" @click="removeHttpHeader(editingArgPlan.source.httpCall, idx)">删</button>
                    </div>
                    <input class="input" v-model="item.key" placeholder="Key，例如 Authorization / X-Tenant-Id" />
                    <select class="input" v-model="item.valueKind">
                      <option value="ctx">值来源：ctx/request</option>
                      <option value="const">值来源：常量</option>
                      <option value="tempVar">值来源：临时变量</option>
                    </select>
                    <input v-if="item.valueKind === 'ctx'" class="input" v-model="item.valuePath" placeholder="request.headers.token / ctx.userId" />
                    <input v-if="item.valueKind === 'const'" class="input" v-model="item.valueConst" placeholder="常量值，例如 Bearer xxx" />
                    <input v-if="item.valueKind === 'tempVar'" class="input" v-model="item.tempKey" placeholder="临时变量 Key，例如 userToken" />
                  </div>
                </div>
                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">查询参数（Query）</div>
                    <button class="btn mini" @click="addHttpQuery(editingArgPlan.source.httpCall)">+ Query</button>
                  </div>
                  <div v-for="(item, idx) in editingArgPlan.source.httpCall.query" :key="item.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">Query {{ idx + 1 }}</div>
                      <button class="btn mini" @click="removeHttpQuery(editingArgPlan.source.httpCall, idx)">删</button>
                    </div>
                    <input class="input" v-model="item.key" placeholder="Key，例如 userId / pageNo" />
                    <select class="input" v-model="item.valueKind">
                      <option value="ctx">值来源：ctx/request</option>
                      <option value="const">值来源：常量</option>
                      <option value="tempVar">值来源：临时变量</option>
                    </select>
                    <input v-if="item.valueKind === 'ctx'" class="input" v-model="item.valuePath" placeholder="request.body.userId / ctx.pageNo" />
                    <input v-if="item.valueKind === 'const'" class="input" v-model="item.valueConst" placeholder="常量值，例如 1 / abc" />
                    <input v-if="item.valueKind === 'tempVar'" class="input" v-model="item.tempKey" placeholder="临时变量 Key，例如 uid" />
                  </div>
                </div>
                <div v-if="shouldShowHttpBody(editingArgPlan.source.httpCall.method)" class="muted tiny">请求体（Body JSON）</div>
                <textarea
                  v-if="shouldShowHttpBody(editingArgPlan.source.httpCall.method)"
                  class="input textarea"
                  v-model="editingArgPlan.source.httpCall.bodyJson"
                  placeholder='Body JSON，例如 {"name":"Tom"}'
                ></textarea>
                <div class="muted tiny">结果提取路径（可选）</div>
                <input
                  class="input"
                  v-model="editingArgPlan.source.httpCall.resultPath"
                  placeholder="结果提取路径（可选），例如 data.name / $.data.name"
                />
              </div>
            </template>

            <template v-if="editingArgPlan.source.kind === 'listPipeline'">
              <div class="pipeline">
                <div class="muted tiny">List 输入源</div>
                <select class="input" v-model="editingArgPlan.listInput.kind">
                  <option value="ctx">上下文 List</option>
                  <option value="serviceCall">服务返回 List</option>
                  <option value="const">常量 List</option>
                </select>
                <SourcePathInput
                  v-if="editingArgPlan.listInput.kind === 'ctx'"
                  :model-value="editingArgPlan.listInput.path"
                  :options="props.sourcePathOptions ?? []"
                  placeholder="ctx.items / request.body.lines"
                  @update:model-value="editingArgPlan.listInput.path = $event"
                />
                <input
                  v-if="editingArgPlan.listInput.kind === 'const'"
                  class="input"
                  v-model="editingArgPlan.listInput.constValue"
                  placeholder="[{skuId:A}]"
                />
                <template v-if="editingArgPlan.listInput.kind === 'serviceCall'">
                  <ServiceCallEditor
                    v-model="editingArgPlan.listInput.serviceCall"
                    :project-key="props.projectKey"
                    :temp-keys="tempKeyOptions"
                    :source-path-options="props.sourcePathOptions"
                  />
                  <input
                    class="input"
                    v-model="editingArgPlan.listInput.serviceResultPath"
                    placeholder="列表提取路径（可选），例如 data.items / $.data.list"
                  />
                  <div class="muted tiny hint-line">当服务返回包装对象时，提取其中的 List 字段作为管线输入。</div>
                </template>

                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">目标元素字段映射（推荐）</div>
                    <button class="btn mini" @click="addListComposeField(editingArgPlan)">+ 字段</button>
                  </div>
                  <div class="muted tiny hint-line">适合分散来源：每个字段可独立来自 ctx/常量/临时变量/服务调用。</div>
                  <input
                    class="input"
                    :value="getResolvedListComposeItemType(editingArgPlan)"
                    readonly
                  />
                  <div class="muted tiny hint-line">目标元素类型由当前计划“类型提示”自动推导（只读）。</div>
                  <div v-for="(field, fieldIndex) in editingArgPlan.listCompose.fields" :key="field.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">字段 {{ fieldIndex + 1 }}</div>
                      <button class="btn mini" @click="removeListComposeField(editingArgPlan, fieldIndex)">删</button>
                    </div>
                    <input class="input" v-model="field.targetField" placeholder="目标字段，例如 skuId / profile.name" />
                    <input class="input" v-model="field.typeHint" placeholder="字段类型提示（可选），例如 java.lang.String" />
                    <select class="input" v-model="field.source.kind">
                      <option value="ctx">来源：ctx/request</option>
                      <option value="const">来源：常量</option>
                      <option value="tempVar">来源：临时变量</option>
                      <option value="serviceCall">来源：服务调用</option>
                    </select>
                    <SourcePathInput
                      v-if="field.source.kind === 'ctx'"
                      :model-value="field.source.path"
                      :options="props.sourcePathOptions ?? []"
                      placeholder="读取路径，例如 $.skuId / request.body.user.id"
                      @update:model-value="field.source.path = $event"
                    />
                    <input
                      v-if="field.source.kind === 'const'"
                      class="input"
                      v-model="field.source.constValue"
                      placeholder="常量值（JSON 字符串）"
                    />
                    <input
                      v-if="field.source.kind === 'tempVar'"
                      class="input"
                      v-model="field.source.tempKey"
                      :list="`tmp_field_${field.id}`"
                      placeholder="临时变量 key"
                    />
                    <datalist v-if="field.source.kind === 'tempVar'" :id="`tmp_field_${field.id}`">
                      <option v-for="key in tempKeyOptions" :key="key" :value="key" />
                    </datalist>
                    <template v-if="field.source.kind === 'serviceCall'">
                      <ServiceCallEditor
                        v-model="field.source.serviceCall"
                        :project-key="props.projectKey"
                        :temp-keys="tempKeyOptions"
                        :source-path-options="props.sourcePathOptions"
                      />
                      <input
                        class="input"
                        v-model="field.source.serviceResultPath"
                        placeholder="结果提取路径（可选），例如 data.name / $.data.name"
                      />
                    </template>
                    <div v-if="getListComposeFieldErrors(field).length > 0" class="plan-errors">
                      <div v-for="msg in getListComposeFieldErrors(field)" :key="msg" class="plan-error">{{ msg }}</div>
                    </div>
                  </div>
                </div>

                <div class="steps">
                  <div class="steps-header">
                    <div class="muted tiny">后处理步骤（可选）</div>
                    <button class="btn mini" @click="addStep(editingArgPlan)">+ 步骤</button>
                  </div>
                  <div v-for="(step, stepIndex) in editingArgPlan.listSteps" :key="step.id" class="step-card">
                    <div class="step-header">
                      <div class="muted tiny">步骤 {{ stepIndex + 1 }}</div>
                      <button class="btn mini" @click="removeStep(editingArgPlan, stepIndex)">删</button>
                    </div>
                    <select class="input" v-model="step.op">
                      <option value="filter">filter</option>
                      <option value="map">map</option>
                      <option value="enrich">enrich</option>
                      <option value="groupBy">groupBy</option>
                      <option value="reduce">reduce</option>
                    </select>
                    <input
                      v-if="step.op === 'filter' || step.op === 'groupBy' || step.op === 'reduce'"
                      class="input"
                      v-model="step.exprText"
                      placeholder="结构化表达式（占位），例如 $.qty > 0"
                    />
                    <textarea
                      v-if="step.op === 'map'"
                      class="input textarea"
                      v-model="step.mappingText"
                      placeholder="映射JSON（占位）"
                    ></textarea>
                    <input
                      v-if="step.op === 'enrich'"
                      class="input"
                      v-model="step.enrichFn"
                      placeholder="补数函数别名，例如 price.batchQuery"
                    />
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <div class="arg-editor-footer">
          <button class="btn" @click="closeArgPlanEditor">完成</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.param-builder {
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
  gap: 8px;
  flex-wrap: wrap;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.header .btn[disabled] {
  opacity: 0.55;
  cursor: not-allowed;
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

.constraint-note {
  font-size: 11px;
  color: #475569;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 6px 8px;
  line-height: 1.45;
}

.quick-check-card {
  border: 1px solid rgba(245, 158, 11, 0.35);
  border-radius: 10px;
  background: rgba(255, 251, 235, 0.7);
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.quick-check-card.pass {
  border-color: rgba(16, 185, 129, 0.35);
  background: rgba(236, 253, 245, 0.78);
}

.quick-check-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.quick-check-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.quick-check-pass {
  font-size: 11px;
  color: #047857;
}

.quick-check-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 11px;
}

.quick-check-error {
  color: #b91c1c;
}

.quick-check-warning {
  color: #92400e;
}

.quick-check-categories {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.category-pill {
  font-size: 10px;
  color: #1f2937;
  background: #ffffff;
  border: 1px solid #dbe2ea;
  border-radius: 999px;
  padding: 2px 8px;
}

.quick-check-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quick-check-item {
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 11px;
  line-height: 1.4;
}

.quick-check-item.error {
  color: #b91c1c;
  border: 1px solid rgba(239, 68, 68, 0.35);
  background: rgba(254, 242, 242, 0.8);
}

.quick-check-item.warning {
  color: #92400e;
  border: 1px solid rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.85);
}

.guide {
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  background: #f8fafc;
  padding: 8px 10px;
  color: #334155;
}

.guide summary {
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.guide-list {
  margin: 8px 0 0;
  padding-left: 18px;
  display: grid;
  gap: 4px;
  font-size: 11px;
  line-height: 1.45;
}

.guide-note {
  margin-top: 8px;
  font-size: 11px;
  color: #475569;
  line-height: 1.45;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-top: 2px;
}

.plans {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plan-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.02);
}

.plan-card:hover {
  border-color: #bfdbfe;
  box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.1), 0 2px 4px -1px rgba(59, 130, 246, 0.06);
}

.plan-summary-card {
  gap: 8px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.summary-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.summary-value {
  font-size: 12px;
  color: #0f172a;
  word-break: break-word;
}

.status-pill {
  font-size: 10px;
  color: #0b3b8a;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.25);
  border-radius: 999px;
  padding: 2px 8px;
}

.arg-editor-mask {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: rgba(15, 23, 42, 0.36);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.arg-editor-panel {
  width: min(980px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 20px 25px -5px rgba(15, 23, 42, 0.1), 0 8px 10px -6px rgba(15, 23, 42, 0.1);
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
}

.arg-editor-header,
.arg-editor-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.arg-editor-title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  letter-spacing: 0.3px;
}

.arg-editor-content {
  min-height: 140px;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 2px;
}

.plan-header,
.steps-header,
.step-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.grid-2 {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 8px;
}

.field,
.source-section,
.pipeline,
.steps,
.step-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.args-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.args-header,
.arg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.arg-card {
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 10px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.02);
}

.arg-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

.mini-textarea {
  min-height: 56px;
}

.plan-errors,
.plan-warnings {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.plan-error,
.plan-warning {
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 11px;
  line-height: 1.4;
}

.plan-error {
  color: #b91c1c;
  border: 1px solid rgba(239, 68, 68, 0.35);
  background: rgba(254, 242, 242, 0.8);
}

.plan-warning {
  color: #92400e;
  border: 1px solid rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.85);
}

.hint-line {
  line-height: 1.4;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.row.compact .input {
  flex: 1 1 140px;
}

.row.compact {
  flex-wrap: wrap;
}

.muted.small {
  font-size: 11px;
}

.muted.tiny {
  font-size: 10px;
}

.btn.mini {
  padding: 4px 8px;
  font-size: 11px;
}

.step-card {
  border: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 10px;
  padding: 12px;
  transition: all 0.2s ease;
  box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.02);
}

.step-card:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}

.param-builder .input {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.textarea {
  min-height: 80px;
  resize: vertical;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 11px;
}

@container (max-width: 430px) {
  .grid-2 {
    grid-template-columns: minmax(0, 1fr);
  }
  .summary-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
