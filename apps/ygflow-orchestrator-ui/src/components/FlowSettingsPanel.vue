
<script setup lang="ts">
import { computed, ref, watch } from "vue"
import type { DataResponseFormatConfig, FlowEntrypoint, FlowSettings, InboundInterceptorConfig, LogPolicy } from "../data/flowSettings"
import type { InboundInterceptorCatalogItem } from "./composables/canvasTypes"

type SchemaField = { name: string; type: string; typeName?: string; source?: string }
type ResponseSchema = { type?: string | null }
type JsonObject = Record<string, any>
type SchemaProperty = {
  key: string
  title: string
  description: string
  type: "string" | "number" | "integer" | "boolean" | "array" | "object"
  required: boolean
  enumValues: any[] | null
  defaultValue: any
  minimum: number | null
  maximum: number | null
  pattern: string | null
}
type CatalogItem = {
  code: string
  label: string
  description: string
  defaultOrder: number
  defaultConfig: JsonObject
  configSchema: JsonObject | null
  source: "builtin" | "project"
}

const props = defineProps<{
  modelValue: FlowSettings
  visible: boolean
  requestSchemaFields?: SchemaField[] | null
  responseSchema?: ResponseSchema | null
  inboundInterceptorCatalog?: InboundInterceptorCatalogItem[] | null
}>()
const emit = defineEmits<{ (e: "update:modelValue", value: FlowSettings): void; (e: "close"): void }>()

const builtins: CatalogItem[] = [
  { code: "trace", label: "trace", description: "注入 traceId 到请求上下文", defaultOrder: 100, defaultConfig: { traceHeader: "X-Trace-Id", outputPath: "request.traceId" }, configSchema: { type: "object", properties: { traceHeader: { type: "string", title: "Header 名称", default: "X-Trace-Id" }, outputPath: { type: "string", title: "写入路径", default: "request.traceId" } }, required: ["traceHeader", "outputPath"] }, source: "builtin" },
  { code: "auth", label: "auth", description: "注入登录态用户信息", defaultOrder: 200, defaultConfig: { required: true, principalPath: "request.auth.user", userIdPath: "request.auth.userId" }, configSchema: { type: "object", properties: { required: { type: "boolean", title: "是否必须登录", default: true }, principalPath: { type: "string", title: "用户对象路径", default: "request.auth.user" }, userIdPath: { type: "string", title: "用户 ID 路径", default: "request.auth.userId" } }, required: ["required", "principalPath", "userIdPath"] }, source: "builtin" },
  { code: "multipart", label: "multipart", description: "解析 multipart/form-data 并写入上下文", defaultOrder: 300, defaultConfig: { maxFileSizeMb: 20, maxFileCount: 10, formPath: "request.form", filesPath: "request.files" }, configSchema: { type: "object", properties: { maxFileSizeMb: { type: "integer", title: "单文件大小上限(MB)", minimum: 1, default: 20 }, maxFileCount: { type: "integer", title: "文件数上限", minimum: 1, default: 10 }, formPath: { type: "string", title: "表单字段路径", default: "request.form" }, filesPath: { type: "string", title: "文件列表路径", default: "request.files" } }, required: ["formPath", "filesPath"] }, source: "builtin" },
  { code: "schemaNormalize", label: "schemaNormalize", description: "请求参数校验与归一化", defaultOrder: 400, defaultConfig: { outputPath: "request.params" }, configSchema: { type: "object", properties: { outputPath: { type: "string", title: "输出路径", default: "request.params" } }, required: ["outputPath"] }, source: "builtin" },
]

const settings = computed(() => props.modelValue)
const logPolicy = computed(() => settings.value.logPolicy)
const requestSchemaFields = computed(() => props.requestSchemaFields ?? [])
const responseSchemaType = computed(() => props.responseSchema?.type?.trim() || "void")
const lastEntrypoint = ref<FlowEntrypoint | null>(null)
const planJsonDraft = ref<Record<string, string>>({})
const planJsonError = ref<Record<string, string>>({})
const fieldJsonDraft = ref<Record<string, string>>({})
const fieldJsonError = ref<Record<string, string>>({})

const logLevels: LogPolicy["level"][] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
const logSinks: LogPolicy["sink"][] = ["console", "kafka", "http"]
const httpMethods = ["GET", "POST", "PUT", "PATCH", "DELETE"]

const entrypoint = computed(() => settings.value.entrypoint ?? lastEntrypoint.value ?? createEntrypoint())
const dataResponseSyncing = ref(false)
const dataResponse = ref({ errorCodeField: "errorCode", errorMessageField: "message", successCode: 1, defaultErrorCode: -1, customDataField: "data" })
const redactSyncing = ref(false)
const redactList = ref<string[]>([])
const redactInput = ref("")
const sinkSyncing = ref(false)
const kafkaSink = ref({ topic: "", bootstrapServers: "", acks: "1", username: "", password: "" })
const httpSink = ref({
  url: "",
  method: "POST",
  timeoutMs: 3000,
  responsePath: "",
  headers: [{ key: "Content-Type", value: "application/json" }],
})

const catalog = computed<CatalogItem[]>(() => {
  const map = new Map<string, CatalogItem>()
  for (const item of builtins) map.set(item.code, item)
  for (const item of props.inboundInterceptorCatalog ?? []) {
    const code = String(item.code ?? "").trim()
    if (!code) continue
    map.set(code, {
      code,
      label: String(item.label ?? code).trim() || code,
      description: String(item.description ?? "项目上报拦截器").trim() || "项目上报拦截器",
      defaultOrder: Number.isFinite(item.defaultOrder as number) ? Number(item.defaultOrder) : 1000,
      defaultConfig: asObj(item.defaultConfig),
      configSchema: asSchema(item.configSchema),
      source: "project",
    })
  }
  return Array.from(map.values()).sort((a, b) => a.defaultOrder - b.defaultOrder)
})

const schemaMap = computed(() => {
  const map = new Map<string, SchemaProperty[]>()
  for (const item of catalog.value) map.set(item.code, schemaProps(item.configSchema))
  return map
})
const canAddPlan = computed(() => {
  const used = new Set(plans().map((x) => x.code))
  return catalog.value.some((x) => !used.has(x.code))
})

watch(() => settings.value.entrypoint, (v) => { if (v) lastEntrypoint.value = v }, { immediate: true })
watch(() => entrypoint.value.inboundInterceptors, () => syncDrafts(), { immediate: true, deep: true })
watch(() => entrypoint.value.dataResponseFormat, (v) => {
  dataResponseSyncing.value = true
  if (!v || typeof v === "string") {
    dataResponse.value = { errorCodeField: "errorCode", errorMessageField: "message", successCode: 1, defaultErrorCode: -1, customDataField: "data" }
  } else {
    dataResponse.value = {
      errorCodeField: v.errorCodeField || "errorCode",
      errorMessageField: v.errorMessageField || "message",
      successCode: v.successCode ?? 1,
      defaultErrorCode: v.defaultErrorCode ?? -1,
      customDataField: v.customFields?.[0]?.fieldName || "data",
    }
  }
  dataResponseSyncing.value = false
}, { immediate: true })
watch(dataResponse, () => { if (!dataResponseSyncing.value) updateDataResponse() }, { deep: true })
watch(() => logPolicy.value.redactKeys, (value) => {
  redactSyncing.value = true
  redactList.value = parseRedactKeys(value || "")
  redactSyncing.value = false
}, { immediate: true })
watch(redactList, () => {
  if (redactSyncing.value) return
  const next = redactList.value.join(",")
  if (next !== (logPolicy.value.redactKeys || "")) {
    updateLogPolicy({ redactKeys: next })
  }
}, { deep: true })
watch(() => [logPolicy.value.sink, logPolicy.value.sinkTarget] as const, ([sink, target]) => {
  sinkSyncing.value = true
  loadSinkForms(sink, target || "")
  sinkSyncing.value = false
}, { immediate: true })
watch([kafkaSink, httpSink], () => {
  if (sinkSyncing.value) return
  syncSinkTarget()
}, { deep: true })

function closePanel() { emit("close") }
function createEntrypoint(): FlowEntrypoint { return { path: "", method: "GET", enabled: true, requestSchemaJson: null, dataResponseFormat: null, inboundInterceptors: [{ code: "schemaNormalize", enabled: true, order: 400, config: { outputPath: "request.params" } }] } }
function updateField<K extends keyof FlowSettings>(key: K, value: FlowSettings[K]) { emit("update:modelValue", { ...settings.value, [key]: value }) }
function updateLogPolicy(partial: Partial<LogPolicy>) { emit("update:modelValue", { ...settings.value, logPolicy: { ...logPolicy.value, ...partial } }) }
function buildSinkTargetBySink(sink: LogPolicy["sink"]): string {
  if (sink === "console") return ""
  if (sink === "kafka") {
    return JSON.stringify({
      topic: kafkaSink.value.topic.trim(),
      bootstrapServers: kafkaSink.value.bootstrapServers.trim(),
      acks: kafkaSink.value.acks,
      username: kafkaSink.value.username.trim(),
      password: kafkaSink.value.password,
    })
  }
  return JSON.stringify({
    url: httpSink.value.url.trim(),
    method: httpSink.value.method.toUpperCase(),
    timeoutMs: Number.isFinite(Number(httpSink.value.timeoutMs)) ? Number(httpSink.value.timeoutMs) : 3000,
    responsePath: httpSink.value.responsePath.trim(),
    headers: httpSink.value.headers
      .map((x) => ({ key: String(x.key || "").trim(), value: String(x.value || "") }))
      .filter((x) => x.key),
  })
}
function updateSink(sink: LogPolicy["sink"]) {
  const sinkTarget = buildSinkTargetBySink(sink)
  emit("update:modelValue", {
    ...settings.value,
    logPolicy: {
      ...logPolicy.value,
      sink,
      sinkTarget,
    },
  })
}
function updateEntrypoint<K extends keyof FlowEntrypoint>(key: K, value: FlowEntrypoint[K]) { const cur = settings.value.entrypoint ?? lastEntrypoint.value ?? createEntrypoint(); const next = { ...cur, [key]: value }; lastEntrypoint.value = next; emit("update:modelValue", { ...settings.value, entrypoint: next }) }
function toggleEntrypoint(enabled: boolean) { updateEntrypoint("enabled", enabled) }

function parseRedactKeys(text: string) {
  const raw = String(text || "")
    .split(/[,;\n]/g)
    .map((x) => x.trim())
    .filter(Boolean)
  const seen = new Set<string>()
  const out: string[] = []
  for (const item of raw) {
    if (seen.has(item)) continue
    seen.add(item)
    out.push(item)
  }
  return out
}
function addRedactKey() {
  const keys = parseRedactKeys(redactInput.value)
  if (!keys.length) return
  const exists = new Set(redactList.value)
  const merged = [...redactList.value]
  for (const key of keys) {
    if (exists.has(key)) continue
    exists.add(key)
    merged.push(key)
  }
  redactList.value = merged
  redactInput.value = ""
}
function removeRedactKey(index: number) {
  redactList.value = redactList.value.filter((_, i) => i !== index)
}

function parseSinkTarget(target: string): JsonObject | null {
  const text = String(target || "").trim()
  if (!text) return null
  try {
    const parsed = JSON.parse(text)
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null
    return parsed as JsonObject
  } catch {
    return null
  }
}
function loadSinkForms(sink: LogPolicy["sink"], target: string) {
  if (sink === "console") return
  const parsed = parseSinkTarget(target)
  if (sink === "kafka") {
    kafkaSink.value = {
      topic: String(parsed?.topic ?? target ?? ""),
      bootstrapServers: String(parsed?.bootstrapServers ?? ""),
      acks: String(parsed?.acks ?? "1"),
      username: String(parsed?.username ?? ""),
      password: String(parsed?.password ?? ""),
    }
    return
  }
  httpSink.value = {
    url: String(parsed?.url ?? target ?? ""),
    method: String(parsed?.method ?? "POST").toUpperCase(),
    timeoutMs: Number.isFinite(parsed?.timeoutMs) ? Number(parsed?.timeoutMs) : 3000,
    responsePath: String(parsed?.responsePath ?? ""),
    headers: Array.isArray(parsed?.headers)
      ? parsed.headers
          .filter((x: any) => x && typeof x === "object")
          .map((x: any) => ({ key: String(x.key ?? "").trim(), value: String(x.value ?? "") }))
      : [{ key: "Content-Type", value: "application/json" }],
  }
}
function addHttpHeader() {
  httpSink.value.headers = [...httpSink.value.headers, { key: "", value: "" }]
}
function removeHttpHeader(index: number) {
  httpSink.value.headers = httpSink.value.headers.filter((_, i) => i !== index)
}
function syncSinkTarget(forceSink?: LogPolicy["sink"]) {
  const sink = forceSink ?? logPolicy.value.sink
  const next = buildSinkTargetBySink(sink)
  if (next !== (logPolicy.value.sinkTarget || "")) {
    updateLogPolicy({ sinkTarget: next })
  }
}
function sinkError() {
  if (!logPolicy.value.enabled) return ""
  if (logPolicy.value.sink === "kafka") {
    if (!kafkaSink.value.topic.trim()) return "Kafka topic 不能为空。"
    return ""
  }
  if (logPolicy.value.sink === "http") {
    if (!httpSink.value.url.trim()) return "HTTP URL 不能为空。"
    return ""
  }
  return ""
}

function updateDataResponse() {
  const cfg: DataResponseFormatConfig = {
    errorCodeField: dataResponse.value.errorCodeField || "errorCode",
    errorMessageField: dataResponse.value.errorMessageField || "message",
    successCode: Number.isFinite(dataResponse.value.successCode) ? dataResponse.value.successCode : 1,
    defaultErrorCode: Number.isFinite(dataResponse.value.defaultErrorCode) ? dataResponse.value.defaultErrorCode : -1,
    customFields: dataResponse.value.customDataField.trim() ? [{ fieldName: dataResponse.value.customDataField.trim() }] : [],
  }
  updateEntrypoint("dataResponseFormat", cfg)
}

function plans(): InboundInterceptorConfig[] {
  const list = entrypoint.value.inboundInterceptors
  if (!Array.isArray(list)) return []
  return list.filter((x) => x?.code).map((x) => ({ code: String(x.code).trim(), enabled: x.enabled !== false, order: typeof x.order === "number" ? x.order : 1000, config: asObj(x.config) }))
}
function setPlans(next: InboundInterceptorConfig[]) { updateEntrypoint("inboundInterceptors", next) }
function findCatalog(code: string) { return catalog.value.find((x) => x.code === code) ?? null }
function sourceLabel(source: string) { return source === "builtin" ? "内置" : "项目" }
function availableCatalog(index: number) { const used = new Set(plans().map((x, i) => i === index ? "" : x.code).filter(Boolean)); return catalog.value.filter((x) => !used.has(x.code)) }
function addPlan() { const used = new Set(plans().map((x) => x.code)); const pick = catalog.value.find((x) => !used.has(x.code)); if (!pick) return; setPlans([...plans(), { code: pick.code, enabled: true, order: pick.defaultOrder, config: { ...pick.defaultConfig } }]); syncDrafts() }
function removePlan(index: number) { setPlans(plans().filter((_, i) => i !== index)); syncDrafts() }
function updatePlanCode(index: number, code: string) {
  const n = code.trim()
  if (!n || plans().some((x, i) => i !== index && x.code === n)) return
  const pick = findCatalog(n)
  setPlans(plans().map((x, i) => i === index ? { ...x, code: n, order: pick?.defaultOrder ?? x.order, config: pick ? { ...pick.defaultConfig } : x.config } : x))
  syncDrafts()
}
function updatePlanOrder(index: number, value: string) { const order = Number.parseInt(value, 10); if (!Number.isFinite(order)) return; setPlans(plans().map((x, i) => i === index ? { ...x, order } : x)) }
function updatePlanEnabled(index: number, enabled: boolean) { setPlans(plans().map((x, i) => i === index ? { ...x, enabled } : x)) }
function patchPlanConfig(index: number, fn: (cfg: JsonObject) => void) { const list = plans(); const t = list[index]; if (!t) return; const next = asObj(t.config); fn(next); setPlans(list.map((x, i) => i === index ? { ...x, config: next } : x)); syncDrafts() }

function readPlanValue(plan: InboundInterceptorConfig, field: SchemaProperty): any {
  const cfg = asObj(plan.config)
  if (Object.prototype.hasOwnProperty.call(cfg, field.key)) return cfg[field.key]
  if (field.defaultValue !== undefined) return field.defaultValue
  if (field.type === "boolean") return false
  if (field.type === "array") return []
  if (field.type === "object") return {}
  return ""
}
function updateSchemaString(index: number, field: SchemaProperty, value: string) { patchPlanConfig(index, (cfg) => { cfg[field.key] = value }) }
function updateSchemaNumber(index: number, field: SchemaProperty, value: string) { patchPlanConfig(index, (cfg) => { const t = value.trim(); if (!t) { delete cfg[field.key]; return }; const n = field.type === "integer" ? Number.parseInt(t, 10) : Number.parseFloat(t); if (Number.isFinite(n)) cfg[field.key] = n }) }
function updateSchemaBoolean(index: number, field: SchemaProperty, value: boolean) { patchPlanConfig(index, (cfg) => { cfg[field.key] = value }) }
function updateSchemaEnum(index: number, field: SchemaProperty, value: string) { const hit = (field.enumValues ?? []).find((x) => String(x) === value); patchPlanConfig(index, (cfg) => { cfg[field.key] = hit ?? value }) }

function pkey(index: number, code: string) { return `${index}:${code}` }
function fkey(planCode: string, fieldKey: string) { return `${planCode}::${fieldKey}` }

function planConfigDraft(index: number, code: string) { return planJsonDraft.value[pkey(index, code)] ?? "{}" }
function updatePlanConfigDraft(index: number, code: string, value: string) { const key = pkey(index, code); planJsonDraft.value[key] = value; planJsonError.value[key] = "" }
function blurPlanConfigDraft(index: number, code: string) {
  const key = pkey(index, code)
  const text = (planJsonDraft.value[key] ?? "{}").trim()
  if (!text) { patchPlanConfig(index, (cfg) => Object.keys(cfg).forEach((k) => delete cfg[k])); planJsonDraft.value[key] = "{}"; return }
  try {
    const parsed = JSON.parse(text)
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) { planJsonError.value[key] = "必须是 JSON 对象。"; return }
    patchPlanConfig(index, (cfg) => { Object.keys(cfg).forEach((k) => delete cfg[k]); Object.assign(cfg, parsed) })
    planJsonDraft.value[key] = JSON.stringify(parsed, null, 2)
    planJsonError.value[key] = ""
  } catch { planJsonError.value[key] = "JSON 格式无效。" }
}

function updateComplexFieldDraft(index: number, planCode: string, field: SchemaProperty, value: string) {
  const key = fkey(planCode, field.key)
  fieldJsonDraft.value[key] = value
  fieldJsonError.value[key] = ""
  const text = value.trim()
  if (!text) { patchPlanConfig(index, (cfg) => { delete cfg[field.key] }); return }
  try {
    const parsed = JSON.parse(text)
    const ok = field.type === "array" ? Array.isArray(parsed) : parsed && typeof parsed === "object" && !Array.isArray(parsed)
    if (!ok) { fieldJsonError.value[key] = field.type === "array" ? "必须是 JSON 数组。" : "必须是 JSON 对象。"; return }
    patchPlanConfig(index, (cfg) => { cfg[field.key] = parsed })
  } catch { fieldJsonError.value[key] = "JSON 格式无效。" }
}

function schemaFields(code: string) { return schemaMap.value.get(code) ?? [] }
function syncDrafts() {
  const nPlan: Record<string, string> = {}
  const nPlanErr: Record<string, string> = {}
  const nField: Record<string, string> = {}
  const nFieldErr: Record<string, string> = {}
  for (const [i, p] of plans().entries()) {
    const pk = pkey(i, p.code)
    nPlan[pk] = JSON.stringify(asObj(p.config), null, 2)
    nPlanErr[pk] = planJsonError.value[pk] ?? ""
    for (const f of schemaFields(p.code)) {
      if (f.type !== "array" && f.type !== "object") continue
      const fk = fkey(p.code, f.key)
      nField[fk] = JSON.stringify(readPlanValue(p, f) ?? (f.type === "array" ? [] : {}), null, 2)
      nFieldErr[fk] = fieldJsonError.value[fk] ?? ""
    }
  }
  planJsonDraft.value = nPlan
  planJsonError.value = nPlanErr
  fieldJsonDraft.value = nField
  fieldJsonError.value = nFieldErr
}

function inferSourceLabel(item: SchemaField): string { const source = item.source; if ((item.name || "").includes("[]")) return "数组元素"; if (source === "path") return "路径"; if (source === "query") return "查询"; if (source === "header") return "请求头"; if (source === "form") return "表单"; if (source === "body") return "请求体"; return source || "-" }
function formatTypeDisplay(item: SchemaField): string { return item.type || "-" }
function getTypeTooltip(item: SchemaField): string | null { return item.typeName?.trim() || null }
function stringify(value: any) { return value == null ? "" : String(value) }
function constraints(field: SchemaProperty) { const list: string[] = []; if (field.minimum != null) list.push(`min: ${field.minimum}`); if (field.maximum != null) list.push(`max: ${field.maximum}`); if (field.pattern) list.push(`pattern: ${field.pattern}`); return list.join(" | ") }

function asSchema(value: any): JsonObject | null {
  if (!value) return null
  if (typeof value === "string") { try { const p = JSON.parse(value.trim()); return p && typeof p === "object" && !Array.isArray(p) ? p : null } catch { return null } }
  return typeof value === "object" && !Array.isArray(value) ? value as JsonObject : null
}
function stype(type: any): SchemaProperty["type"] { const t = String(type || "string"); if (t === "number" || t === "integer" || t === "boolean" || t === "array" || t === "object") return t; return "string" }
function schemaProps(schema: JsonObject | null): SchemaProperty[] {
  const root = asSchema(schema)
  if (!root || !root.properties || typeof root.properties !== "object" || Array.isArray(root.properties)) return []
  const req = Array.isArray(root.required) ? new Set(root.required.map((x: any) => String(x))) : new Set<string>()
  return Object.entries(root.properties).flatMap(([key, raw]) => {
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return []
    const n = raw as JsonObject
    return [{ key, title: String(n.title ?? key), description: String(n.description ?? ""), type: stype(n.type), required: req.has(key), enumValues: Array.isArray(n.enum) ? n.enum : null, defaultValue: n.default, minimum: typeof n.minimum === "number" ? n.minimum : null, maximum: typeof n.maximum === "number" ? n.maximum : null, pattern: typeof n.pattern === "string" ? n.pattern : null }]
  })
}
function asObj(value: any): JsonObject { return value && typeof value === "object" && !Array.isArray(value) ? { ...(value as JsonObject) } : {} }
</script>

<template>
  <transition name="flow-settings-fade">
    <div v-if="visible" class="overlay" @click.self="closePanel">
      <section class="panel" @click.stop>
        <header class="head">
          <div>
            <div class="title">流程设置</div>
            <p class="desc">维护流程基础信息、入口拦截器链与数据响应格式。</p>
          </div>
          <button class="btn ghost" type="button" @click="closePanel">关闭</button>
        </header>

        <section class="card">
          <div class="st">基础信息</div>
          <div class="grid">
            <label class="field"><span>流程标识</span><input class="input ro" type="text" :value="settings.code" readonly /></label>
            <label class="field"><span>流程名称</span><input class="input" type="text" :value="settings.name" @input="updateField('name', ($event.target as HTMLInputElement).value)" /></label>
            <label class="field"><span>负责人</span><input class="input" type="text" :value="settings.owner" @input="updateField('owner', ($event.target as HTMLInputElement).value)" /></label>
            <label class="field full"><span>标签</span><input class="input" type="text" :value="settings.tags" @input="updateField('tags', ($event.target as HTMLInputElement).value)" /></label>
            <label class="field full"><span>描述</span><textarea class="input" rows="2" :value="settings.description" @input="updateField('description', ($event.target as HTMLTextAreaElement).value)" /></label>
            <label class="field full"><span>备注</span><textarea class="input" rows="2" :value="settings.notes" @input="updateField('notes', ($event.target as HTMLTextAreaElement).value)" /></label>
          </div>
        </section>

        <section class="card">
          <div class="st">日志策略</div>
          <div class="grid compact">
            <label class="field inline"><span>启用日志</span><input type="checkbox" :checked="logPolicy.enabled" @change="updateLogPolicy({ enabled: ($event.target as HTMLInputElement).checked })" /></label>
            <label class="field"><span>日志级别</span><select class="input" :disabled="!logPolicy.enabled" :value="logPolicy.level" @change="updateLogPolicy({ level: ($event.target as HTMLSelectElement).value as LogPolicy['level'] })"><option v-for="l in logLevels" :key="l" :value="l">{{ l }}</option></select></label>
          </div>
          <div class="checks">
            <label class="ck"><input type="checkbox" :checked="logPolicy.collectInputs" :disabled="!logPolicy.enabled" @change="updateLogPolicy({ collectInputs: ($event.target as HTMLInputElement).checked })" /><span>记录输入</span></label>
            <label class="ck"><input type="checkbox" :checked="logPolicy.collectOutputs" :disabled="!logPolicy.enabled" @change="updateLogPolicy({ collectOutputs: ($event.target as HTMLInputElement).checked })" /><span>记录输出</span></label>
            <label class="ck"><input type="checkbox" :checked="logPolicy.collectContext" :disabled="!logPolicy.enabled" @change="updateLogPolicy({ collectContext: ($event.target as HTMLInputElement).checked })" /><span>记录上下文</span></label>
            <label class="ck"><input type="checkbox" :checked="logPolicy.collectErrors" :disabled="!logPolicy.enabled" @change="updateLogPolicy({ collectErrors: ($event.target as HTMLInputElement).checked })" /><span>记录异常</span></label>
            <label class="ck"><input type="checkbox" :checked="logPolicy.collectDuration" :disabled="!logPolicy.enabled" @change="updateLogPolicy({ collectDuration: ($event.target as HTMLInputElement).checked })" /><span>记录耗时</span></label>
          </div>
          <div class="field">
            <span>脱敏字段</span>
            <div class="tags">
              <span v-for="(item, i) in redactList" :key="`${item}-${i}`" class="tag">
                <span>{{ item }}</span>
                <button class="tag-del" type="button" :disabled="!logPolicy.enabled" @click="removeRedactKey(i)">×</button>
              </span>
              <span v-if="redactList.length === 0" class="hint">未配置脱敏字段</span>
            </div>
            <div class="add-row">
              <input
                class="input"
                type="text"
                :disabled="!logPolicy.enabled"
                v-model="redactInput"
                placeholder="例如 request.password / response.token"
                @keydown.enter.prevent="addRedactKey"
                @blur="addRedactKey"
              />
            </div>
            <p class="hint">按路径配置，逗号分隔字符串会自动转换为列表。</p>
          </div>
        </section>
        <section class="card">
          <div class="st">REST 入口</div>
          <label class="ck"><input type="checkbox" :checked="entrypoint.enabled !== false" @change="toggleEntrypoint(($event.target as HTMLInputElement).checked)" /><span>{{ entrypoint.enabled !== false ? "已启用入口" : "未启用入口" }}</span></label>
          <div class="grid compact">
            <label class="field"><span>路径</span><input class="input ro" type="text" readonly :value="entrypoint.path" /></label>
            <label class="field"><span>方法</span><input class="input ro" type="text" readonly :value="entrypoint.method || 'GET'" /></label>
          </div>

          <div class="row"><span class="st">入口拦截器链</span><button class="btn ghost sm" type="button" :disabled="!canAddPlan" @click="addPlan">+ 添加</button></div>
          <div v-if="plans().length === 0" class="empty">暂无拦截器</div>

          <div v-for="(plan, index) in plans()" :key="`${plan.code}-${index}`" class="plan">
            <div class="plan-head">
              <label class="field"><span>拦截器</span><select class="input" :value="plan.code" @change="updatePlanCode(index, ($event.target as HTMLSelectElement).value)"><option v-for="item in availableCatalog(index)" :key="item.code" :value="item.code">{{ item.label }}（{{ sourceLabel(item.source) }}）</option></select></label>
              <label class="field"><span>顺序</span><input class="input" type="number" :value="plan.order ?? 1000" @input="updatePlanOrder(index, ($event.target as HTMLInputElement).value)" /></label>
              <label class="field inline"><span>启用</span><input type="checkbox" :checked="plan.enabled !== false" @change="updatePlanEnabled(index, ($event.target as HTMLInputElement).checked)" /></label>
              <button class="btn danger plan-delete-btn" type="button" @click="removePlan(index)">删除</button>
            </div>
            <p class="hint">{{ findCatalog(plan.code)?.description || "拦截器配置" }}</p>

            <div v-if="schemaFields(plan.code).length" class="schema">
              <div v-for="field in schemaFields(plan.code)" :key="field.key" class="field">
                <span>{{ field.title }}<b v-if="field.required" class="req">*</b><small class="k">{{ field.key }}</small></span>
                <select v-if="field.enumValues?.length" class="input" :value="stringify(readPlanValue(plan, field))" @change="updateSchemaEnum(index, field, ($event.target as HTMLSelectElement).value)"><option v-for="ev in field.enumValues" :key="stringify(ev)" :value="stringify(ev)">{{ stringify(ev) }}</option></select>
                <input v-else-if="field.type === 'string'" class="input" type="text" :value="stringify(readPlanValue(plan, field))" @input="updateSchemaString(index, field, ($event.target as HTMLInputElement).value)" />
                <input v-else-if="field.type === 'number' || field.type === 'integer'" class="input" type="number" :step="field.type === 'integer' ? '1' : 'any'" :value="stringify(readPlanValue(plan, field))" @input="updateSchemaNumber(index, field, ($event.target as HTMLInputElement).value)" />
                <label v-else-if="field.type === 'boolean'" class="ck"><input type="checkbox" :checked="Boolean(readPlanValue(plan, field))" @change="updateSchemaBoolean(index, field, ($event.target as HTMLInputElement).checked)" /><span>{{ Boolean(readPlanValue(plan, field)) ? 'true' : 'false' }}</span></label>
                <textarea v-else class="input wfull" rows="4" :value="fieldJsonDraft[fkey(plan.code, field.key)] ?? '{}'" @input="updateComplexFieldDraft(index, plan.code, field, ($event.target as HTMLTextAreaElement).value)" />
                <span v-if="field.description" class="hint">{{ field.description }}</span>
                <span v-if="constraints(field)" class="hint">{{ constraints(field) }}</span>
                <span v-if="fieldJsonError[fkey(plan.code, field.key)]" class="err">{{ fieldJsonError[fkey(plan.code, field.key)] }}</span>
              </div>
            </div>

            <details class="adv"><summary>高级 JSON（可选，调试用）</summary>
              <textarea class="input wfull" rows="8" :value="planConfigDraft(index, plan.code)" @input="updatePlanConfigDraft(index, plan.code, ($event.target as HTMLTextAreaElement).value)" @blur="blurPlanConfigDraft(index, plan.code)" />
              <p v-if="planJsonError[pkey(index, plan.code)]" class="err">{{ planJsonError[pkey(index, plan.code)] }}</p>
            </details>
          </div>

          <div class="st">数据响应格式</div>
          <div class="grid compact">
            <label class="field"><span>错误码字段</span><input class="input" type="text" v-model="dataResponse.errorCodeField" /></label>
            <label class="field"><span>错误消息字段</span><input class="input" type="text" v-model="dataResponse.errorMessageField" /></label>
            <label class="field"><span>成功码</span><input class="input" type="number" v-model.number="dataResponse.successCode" /></label>
            <label class="field"><span>失败码</span><input class="input" type="number" v-model.number="dataResponse.defaultErrorCode" /></label>
            <label class="field full"><span>数据字段名</span><input class="input" type="text" v-model="dataResponse.customDataField" /></label>
          </div>
        </section>

        <section class="card">
          <div class="st">REST 数据结构</div>
          <table v-if="requestSchemaFields.length" class="tbl"><thead><tr><th>参数名</th><th>类型</th><th>来源</th></tr></thead><tbody><tr v-for="item in requestSchemaFields" :key="item.name"><td>{{ item.name || '-' }}</td><td :title="getTypeTooltip(item) || undefined">{{ formatTypeDisplay(item) || '-' }}</td><td>{{ inferSourceLabel(item) }}</td></tr></tbody></table>
          <div v-else class="empty">未提供请求参数结构</div>
          <div class="hint">响应类型：<b>{{ responseSchemaType || 'void' }}</b></div>
        </section>

        <footer class="foot"><span class="hint">修改会立即应用到当前草稿。</span><button class="btn primary" type="button" @click="closePanel">完成</button></footer>
      </section>
    </div>
  </transition>
</template>

<style scoped>
.overlay { position: fixed; inset: 0; z-index: 80; background: rgba(15, 23, 42, 0.45); display: flex; justify-content: flex-end; }
.panel { width: min(620px, 100vw); height: 100%; background: #fff; padding: 20px; box-sizing: border-box; overflow: auto; display: flex; flex-direction: column; gap: 14px; }
.head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; }
.title { font-size: 18px; font-weight: 600; color: #0f172a; }
.desc { margin: 4px 0 0; color: #64748b; font-size: 12px; }
.card { border: 1px solid rgba(148, 163, 184, 0.35); border-radius: 12px; padding: 12px; display: flex; flex-direction: column; gap: 10px; }
.st { font-weight: 600; color: #1f2937; }
.row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.compact { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
.field { display: flex; flex-direction: column; gap: 6px; min-width: 0; font-size: 12px; color: #1f2937; }
.field.full { grid-column: 1 / -1; }
.inline { flex-direction: row; align-items: center; gap: 6px; }
.input { width: 100%; min-width: 0; box-sizing: border-box; border: 1px solid rgba(148, 163, 184, 0.6); border-radius: 10px; padding: 8px 10px; font-size: 13px; }
.input:focus { outline: none; border-color: #2563eb; box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1); }
.ro { background: #f8fafc; color: #64748b; }
.ck { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.checks { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 6px; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag { display: inline-flex; align-items: center; gap: 6px; padding: 3px 8px; border: 1px solid #bfdbfe; background: #eff6ff; color: #1e3a8a; border-radius: 999px; font-size: 12px; }
.tag-del { border: none; background: transparent; color: #1d4ed8; cursor: pointer; padding: 0 2px; line-height: 1; }
.add-row { display: grid; grid-template-columns: 1fr; gap: 8px; align-items: center; }
.adv { border: 1px dashed rgba(148, 163, 184, 0.55); border-radius: 10px; padding: 10px; }
.adv > summary { cursor: pointer; font-weight: 600; font-size: 12px; color: #334155; }
.plan { border: 1px solid rgba(148, 163, 184, 0.4); border-radius: 10px; padding: 10px; display: flex; flex-direction: column; gap: 8px; overflow: hidden; }
.plan-head { display: grid; grid-template-columns: minmax(0, 1fr) 120px 80px auto; gap: 10px; align-items: end; }
.plan-delete-btn { height: 38px; align-self: end; }
.schema { border: 1px solid rgba(148, 163, 184, 0.3); border-radius: 10px; padding: 8px; display: grid; gap: 8px; }
.k { margin-left: 6px; color: #64748b; }
.req { color: #dc2626; margin-left: 2px; }
.tbl { width: 100%; border-collapse: collapse; font-size: 12px; }
.tbl th, .tbl td { border: 1px solid rgba(226, 232, 240, 0.8); padding: 6px 8px; text-align: left; }
.tbl th { background: #f8fafc; color: #475569; }
.wfull { width: 100%; box-sizing: border-box; min-height: 110px; resize: vertical; }
.hint { color: #64748b; font-size: 12px; margin: 0; }
.err { color: #dc2626; font-size: 12px; margin: 0; }
.empty { border: 1px dashed rgba(148, 163, 184, 0.6); border-radius: 10px; padding: 8px; color: #64748b; font-size: 12px; }
.btn { border-radius: 999px; font-size: 13px; cursor: pointer; white-space: nowrap; }
.btn.ghost { border: 1px solid rgba(148, 163, 184, 0.55); background: #fff; color: #334155; padding: 6px 12px; }
.btn.sm { padding: 4px 10px; }
.btn.primary { border: none; background: #2563eb; color: #fff; padding: 8px 16px; }
.btn.danger { border: 1px solid #fecaca; background: #fef2f2; color: #dc2626; padding: 6px 12px; }
.header-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; margin-bottom: 6px; }
.foot { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.flow-settings-fade-enter-active, .flow-settings-fade-leave-active { transition: opacity 0.2s ease; }
.flow-settings-fade-enter-from, .flow-settings-fade-leave-to { opacity: 0; }
@media (max-width: 900px) {
  .panel { width: 100vw; padding: 14px; }
  .grid, .compact, .plan-head { grid-template-columns: 1fr; }
  .add-row, .header-row { grid-template-columns: 1fr; }
}
</style>
