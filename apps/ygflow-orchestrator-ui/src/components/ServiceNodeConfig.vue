<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { api } from "../api/client"
import ParamAssemblerPanel from "./ParamAssemblerPanel.vue"
import ParamPlanBuilder from "./ParamPlanBuilder.vue"
import ServiceCallEditor from "./ServiceCallEditor.vue"

const props = defineProps<{
  label?: string
  comp?: any
  inputs?: any[]
  output?: any
  paramPlans?: any
  projectKey?: string
  endpointId?: number
  nodeId?: string
  nodes?: any[] | null
  edges?: any[] | null
}>()

const emit = defineEmits<{
  (event: "update:label", value: string): void
  (event: "update:comp", value: any): void
  (event: "update:inputs", value: any[]): void
  (event: "update:output", value: any): void
  (event: "update:paramPlans", value: any): void
}>()

type ServiceCallModel = {
  fn?: string
  serviceRef?: {
    endpointId?: number | string
    serviceKey?: string
    serviceBean?: string
    serviceName?: string
    serviceClass?: string
    methodName?: string
    methodSignature?: string
    methodSignatureHash?: string
    returnType?: string
  } | null
  argBindings?: Array<{
    id?: string
    paramName?: string
    paramType?: string
    source?: {
      kind?: "ctx" | "const" | "tempVar"
      path?: string
      constValue?: string
      tempKey?: string
      mode?: "direct" | "objectBuilder"
      objectFields?: Array<{
        id?: string
        fieldPath?: string
        source?: {
          kind?: "ctx" | "const" | "tempVar"
          path?: string
          constValue?: string
          tempKey?: string
        }
      }>
    }
  }>
}

type MethodMeta = {
  endpointId?: number | string
  serviceName?: string
  serviceBean?: string
  serviceClass?: string
  methodName?: string
  methodSignature?: string
  methodSignatureHash?: string
  description?: string
  returnType?: string
  returnSchema?: Record<string, any> | null
  params?: Array<{
    name?: string
    type?: string
    required?: boolean
    description?: string
    schema?: Record<string, any> | null
    listItemType?: string
  }>
} | null

const selectedMethodMeta = ref<MethodMeta>(null)
const requestPathOptions = ref<string[]>([])
const activeAssemblerMode = ref<"basic" | "advanced">("basic")

function toText(value: unknown): string {
  return typeof value === "string" ? value.trim() : ""
}

function safeParseJson(raw: unknown): Record<string, any> | null {
  if (typeof raw !== "string" || !raw.trim()) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : null
  } catch {
    return null
  }
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}

function hasUsefulParamPlans(value: any): boolean {
  return Boolean(
    (Array.isArray(value?.argPlans) && value.argPlans.length > 0)
    || (Array.isArray(value?.tempPlans) && value.tempPlans.length > 0),
  )
}

function getStoredParamAssemblerAst(): Record<string, any> | null {
  const config = safeParseJson(props.comp?.configJson)
  const ast = config?.paramAssemblerAst
  return ast && typeof ast === "object" && !Array.isArray(ast) ? ast : null
}

function detectPreferredAssemblerMode(): "basic" | "advanced" {
  const config = safeParseJson(props.comp?.configJson)
  const storedMode = toText(config?.paramAssemblerMode)
  if (storedMode === "advanced") return "advanced"
  if (storedMode === "basic") return "basic"
  if (getStoredParamAssemblerAst()) return "basic"
  if (hasUsefulParamPlans(props.paramPlans)) return "advanced"
  return "basic"
}

function patchCompConfig(partial: Record<string, any>) {
  if (!props.comp || typeof props.comp !== "object") return
  const current = clone(props.comp)
  const currentConfig = safeParseJson(current.configJson) || {}
  current.configJson = JSON.stringify({
    ...currentConfig,
    ...partial,
  })
  emit("update:comp", current)
}

function inferValueType(typeName: string): string {
  const raw = toText(typeName).toLowerCase()
  if (!raw) return "STRING"
  if (raw.includes("boolean")) return "BOOLEAN"
  if (
    raw === "int"
    || raw === "integer"
    || raw === "long"
    || raw === "double"
    || raw === "float"
    || raw === "short"
    || raw === "byte"
    || raw.includes("java.lang.integer")
    || raw.includes("java.lang.long")
    || raw.includes("java.lang.double")
    || raw.includes("java.lang.float")
    || raw.includes("java.lang.short")
    || raw.includes("java.lang.byte")
    || raw.includes("java.math.bigdecimal")
    || raw.includes("java.math.biginteger")
    || raw.includes("number")
  ) {
    return "NUMBER"
  }
  if (raw.endsWith("[]") || raw.startsWith("java.util.list") || raw.startsWith("list<") || raw === "array") {
    return "ARRAY"
  }
  if (raw === "void") return "VOID"
  if (raw.includes(".")) return "OBJECT"
  return "STRING"
}

function uniquePaths(items: string[]): string[] {
  return Array.from(new Set(items.filter((item) => Boolean(toText(item))))).sort((a, b) => a.localeCompare(b))
}

function flattenRequestSchemaNode(schema: any, basePath: string, output: string[]) {
  if (!schema || typeof schema !== "object" || Array.isArray(schema)) {
    if (basePath) output.push(basePath)
    return
  }
  const properties = schema.properties
  if (!properties || typeof properties !== "object" || Array.isArray(properties)) {
    if (basePath) output.push(basePath)
    return
  }

  const entries = Object.entries(properties)
  if (entries.length === 0) {
    if (basePath) output.push(basePath)
    return
  }

  for (const [key, raw] of entries) {
    const child = raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, any> : {}
    const childPath = basePath ? `${basePath}.${key}` : key
    const childType = toText(child.type).toLowerCase()
    if (childType === "object" && child.properties && typeof child.properties === "object" && !Array.isArray(child.properties)) {
      flattenRequestSchemaNode(child, childPath, output)
      continue
    }
    if (childType === "array") {
      output.push(`${childPath}[]`)
      const items = child.items
      if (items && typeof items === "object" && !Array.isArray(items)) {
        const itemType = toText((items as Record<string, any>).type).toLowerCase()
        if (itemType === "object" && (items as Record<string, any>).properties) {
          flattenRequestSchemaNode(items, `${childPath}[]`, output)
          continue
        }
      }
      continue
    }
    output.push(childPath)
  }
}

function buildPathsFromRequestSchemaJson(raw: string): string[] {
  if (!raw || !raw.trim()) return []
  try {
    const schema = JSON.parse(raw)
    const properties = schema?.properties
    if (!properties || typeof properties !== "object" || Array.isArray(properties)) return []
    const output: string[] = []
    for (const [name, value] of Object.entries(properties)) {
      const field = value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, any> : {}
      const source = toText(field["x-source"]).toLowerCase()
      if (source === "path") {
        output.push(`request.path.${toText(field["x-pathVariable"]) || name}`)
        continue
      }
      if (source === "query") {
        output.push(`request.query.${toText(field["x-paramName"]) || name}`)
        continue
      }
      if (source === "header") {
        output.push(`request.headers.${toText(field["x-headerName"]) || name}`)
        continue
      }
      if (source === "form") {
        output.push(`request.body.${toText(field["x-formField"]) || name}`)
        continue
      }
      if (source === "body") {
        const childType = toText(field.type).toLowerCase()
        if (childType === "object" && field.properties && typeof field.properties === "object" && !Array.isArray(field.properties)) {
          flattenRequestSchemaNode(field, "request.body", output)
        } else if (childType === "array") {
          output.push("request.body[]")
        } else {
          output.push(`request.body.${name}`)
        }
        continue
      }
      output.push(`request.body.${name}`)
    }
    return uniquePaths(output)
  } catch {
    return []
  }
}

function buildPathsFromRequestSchemaFields(fields: any[]): string[] {
  const output: string[] = []
  for (const field of fields || []) {
    const source = toText(field?.source).toLowerCase()
    const name = toText(field?.pathVariable) || toText(field?.paramName) || toText(field?.formField) || toText(field?.name)
    if (!name) continue
    if (source === "path") output.push(`request.path.${name}`)
    else if (source === "query") output.push(`request.query.${name}`)
    else if (source === "header") output.push(`request.headers.${name}`)
    else output.push(`request.body.${name}`)
  }
  return uniquePaths(output)
}

async function loadRequestPathOptions() {
  if (!props.projectKey || !props.endpointId) {
    requestPathOptions.value = []
    return
  }
  try {
    const endpoint = await api.getEndpoint(props.projectKey, props.endpointId)
    const byJson = buildPathsFromRequestSchemaJson(endpoint.requestSchemaJson || "")
    if (byJson.length > 0) {
      requestPathOptions.value = byJson
      return
    }
    requestPathOptions.value = buildPathsFromRequestSchemaFields(Array.isArray(endpoint.requestSchema) ? endpoint.requestSchema : [])
  } catch {
    requestPathOptions.value = []
  }
}

watch(() => [props.projectKey, props.endpointId], loadRequestPathOptions, { immediate: true })

function getInputName(input: any, index: number): string {
  const name = String(input?.name || "").trim()
  return name || `arg${index + 1}`
}

function formatInputType(input: any) {
  const type = String(input?.valueType || "STRING").toUpperCase()
  if (type === "OBJECT" || type === "ARRAY") return input?.typeName || type
  return type
}

function formatOutputType(output: any) {
  const type = String(output?.valueType || "OBJECT").toUpperCase()
  if (output?.typeName) return output.typeName
  return type
}

function isObjectOutput(output: any) {
  return String(output?.valueType || "").toUpperCase() === "OBJECT"
}

function updateOutputField(partial: Record<string, any>) {
  emit("update:output", { ...(props.output || {}), ...partial })
}

function buildOutputFieldsFromSchema(schema: Record<string, any> | null | undefined): Array<{ name: string; type: string; description: string }> {
  if (!schema || typeof schema !== "object") return []
  const properties = schema.properties
  if (!properties || typeof properties !== "object" || Array.isArray(properties)) return []
  return Object.entries(properties).map(([name, raw]) => {
    const child = raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, any> : {}
    return {
      name,
      type: toText(child["x-javaType"]) || toText(child.typeName) || toText(child.type) || "object",
      description: toText(child.description) || toText(child.title),
    }
  })
}

function buildServiceName(comp: any): string {
  const config = safeParseJson(comp?.configJson)
  return toText(config?.serviceName) || toText(config?.name) || toText(comp?.bean) || "-"
}

function buildMethodName(comp: any): string {
  const config = safeParseJson(comp?.configJson)
  return toText(config?.method) || toText(comp?.method) || "-"
}

function buildEditorModel(): ServiceCallModel {
  const config = safeParseJson(props.comp?.configJson)
  const inputList = Array.isArray(props.inputs) ? props.inputs : []
  const argPlans = Array.isArray(props.paramPlans?.argPlans) ? props.paramPlans.argPlans : []

  const argBindings = inputList.map((input, index) => {
    const name = getInputName(input, index)
    const typeName = toText(input?.typeName) || formatInputType(input)
    const exactPlan = argPlans.find((plan: any) => toText(plan?.target) === name)
    const nestedPlans = argPlans.filter((plan: any) => {
      const target = toText(plan?.target)
      return target.startsWith(`${name}.`)
    })

    if (nestedPlans.length > 0) {
      return {
        id: `binding_${name}`,
        paramName: name,
        paramType: typeName,
        source: {
          kind: "ctx",
          path: "",
          constValue: "",
          tempKey: "",
          mode: "objectBuilder",
          objectFields: nestedPlans.map((plan: any, idx: number) => ({
            id: `field_${name}_${idx}`,
            fieldPath: toText(plan?.target).slice(name.length + 1),
            source: {
              kind: toText(plan?.source?.kind) || "ctx",
              path: toText(plan?.source?.path),
              constValue: plan?.source?.constValue ?? "",
              tempKey: toText(plan?.source?.tempKey),
            },
          })),
        },
      }
    }

    return {
      id: `binding_${name}`,
      paramName: name,
      paramType: typeName,
      source: {
        kind: toText(exactPlan?.source?.kind) || "ctx",
        path: toText(exactPlan?.source?.path) || `request.body.${name}`,
        constValue: exactPlan?.source?.constValue ?? "",
        tempKey: toText(exactPlan?.source?.tempKey),
        mode: "direct",
        objectFields: [],
      },
    }
  })

  return {
    fn: props.comp?.bean && props.comp?.method ? `${props.comp.bean}.${props.comp.method}` : "",
    serviceRef: props.comp ? {
      endpointId: props.comp?.id,
      serviceKey: `${toText(props.comp?.bean)}:${toText(config?.serviceClass) || toText(config?.class)}`,
      serviceBean: toText(props.comp?.bean),
      serviceName: toText(config?.serviceName) || toText(config?.name) || toText(props.label),
      serviceClass: toText(config?.serviceClass) || toText(config?.class),
      methodName: toText(props.comp?.method),
      methodSignature: toText(config?.methodSignature),
      methodSignatureHash: toText(config?.methodSignatureHash),
      returnType: toText(config?.returnType) || toText(props.output?.typeName),
    } : null,
    argBindings,
  }
}

const serviceCallModel = computed(() => buildEditorModel())
const currentMethodKey = computed(() => {
  const refInfo = serviceCallModel.value?.serviceRef
  const bean = toText(selectedMethodMeta.value?.serviceBean) || toText(refInfo?.serviceBean)
  const signature = toText(selectedMethodMeta.value?.methodSignature) || toText(refInfo?.methodSignature) || toText(props.comp?.method)
  return bean && signature ? `${bean}|${signature}` : ""
})
const currentParamAssemblerAst = computed(() => getStoredParamAssemblerAst())
const tempKeys = computed(() =>
  (Array.isArray(props.paramPlans?.tempPlans) ? props.paramPlans.tempPlans : [])
    .map((item: any) => toText(item?.key))
    .filter((item: string) => Boolean(item))
)
const upstreamPathOptions = computed(() => {
  if (!props.nodeId || !Array.isArray(props.edges) || !Array.isArray(props.nodes)) return []
  const sourceIds = props.edges
    .filter((edge: any) => String(edge?.target || "") === String(props.nodeId))
    .map((edge: any) => String(edge?.source || "").trim())
    .filter((item: string) => Boolean(item))
  if (sourceIds.length === 0) return []

  const options: string[] = []
  for (const sourceId of sourceIds) {
    const node = props.nodes.find((item: any) => String(item?.id || "") === sourceId)
    const output = node?.data?.output
    const contextKey = toText(output?.contextKey)
    if (!contextKey) continue
    options.push(contextKey)
    if (Array.isArray(output?.fields)) {
      output.fields.forEach((field: any) => {
        const fieldName = toText(field?.name)
        if (fieldName) options.push(`${contextKey}.${fieldName}`)
      })
    }
  }
  return uniquePaths(options)
})
const sourcePathOptions = computed(() => uniquePaths([...requestPathOptions.value, ...upstreamPathOptions.value]))

const normalizedInputs = computed(() =>
  (Array.isArray(props.inputs) ? props.inputs : []).map((input, index) => ({
    name: getInputName(input, index),
    type: formatInputType(input),
  })),
)

const inputSummaryText = computed(() => {
  const size = normalizedInputs.value.length
  if (size === 0) return "未识别到入参"
  return `已识别 ${size} 个入参`
})

watch(() => props.nodeId, () => {
  activeAssemblerMode.value = detectPreferredAssemblerMode()
}, { immediate: true })

function buildCompPayload(model: ServiceCallModel, meta: MethodMeta) {
  const current = props.comp && typeof props.comp === "object" ? clone(props.comp) : {}
  const currentConfig = safeParseJson(current.configJson) || {}
  const nextConfig: Record<string, any> = {
    ...currentConfig,
    bean: toText(model.serviceRef?.serviceBean) || toText(current.bean),
    serviceBean: toText(model.serviceRef?.serviceBean) || toText(current.bean),
    serviceName: toText(meta?.serviceName) || toText(model.serviceRef?.serviceName) || toText(currentConfig.serviceName),
    serviceClass: toText(meta?.serviceClass) || toText(model.serviceRef?.serviceClass) || toText(currentConfig.serviceClass),
    method: toText(model.serviceRef?.methodName) || toText(current.method),
    methodSignature: toText(meta?.methodSignature) || toText(model.serviceRef?.methodSignature),
    methodSignatureHash: toText(meta?.methodSignatureHash) || toText(model.serviceRef?.methodSignatureHash),
    returnType: toText(meta?.returnType) || toText(model.serviceRef?.returnType) || toText(currentConfig.returnType),
  }
  if (meta?.returnSchema && typeof meta.returnSchema === "object") {
    nextConfig.returnSchema = clone(meta.returnSchema)
  }
  if (Array.isArray(meta?.params) && meta?.params.length > 0) {
    nextConfig.params = meta.params.map((param) => ({
      name: toText(param?.name),
      type: toText(param?.type),
      required: Boolean(param?.required),
      description: toText(param?.description),
      schema: param?.schema || null,
    }))
  }

  return {
    ...current,
    bean: toText(model.serviceRef?.serviceBean) || current.bean || null,
    method: toText(model.serviceRef?.methodName) || current.method || null,
    configJson: JSON.stringify(nextConfig),
    endpointType: current.endpointType || "FLOW_OPERATION",
  }
}

function buildInputsPayload(model: ServiceCallModel, meta: MethodMeta) {
  const existing = Array.isArray(props.inputs) ? props.inputs : []
  const metaParams = Array.isArray(meta?.params) ? meta.params : []
  return (model.argBindings || []).map((binding, index) => {
    const methodParam = metaParams[index] || metaParams.find((item) => toText(item?.name) === toText(binding?.paramName))
    const previous = existing.find((item) => getInputName(item, index) === toText(binding?.paramName)) || {}
    const typeName = toText(methodParam?.type) || toText(binding?.paramType)
    return {
      ...previous,
      name: toText(binding?.paramName),
      valueType: inferValueType(typeName),
      typeName,
      description: toText(methodParam?.description) || toText(previous?.description),
      schema: methodParam?.schema || previous?.schema || null,
      required: Boolean(methodParam?.required ?? previous?.required),
    }
  })
}

function buildOutputPayload(meta: MethodMeta) {
  const previous = props.output && typeof props.output === "object" ? clone(props.output) : {}
  const returnType = toText(meta?.returnType) || toText(previous?.typeName)
  const returnSchema = meta?.returnSchema && typeof meta.returnSchema === "object" ? clone(meta.returnSchema) : null
  const output = {
    ...previous,
    description: toText(meta?.description) || toText(previous?.description),
    valueType: inferValueType(returnType || "java.lang.Object"),
    typeName: returnType,
    fields: returnSchema
      ? buildOutputFieldsFromSchema(returnSchema)
      : Array.isArray(previous?.fields) ? previous.fields : [],
    contextKey: toText(previous?.contextKey) || "",
  }
  if (output.valueType !== "OBJECT") {
    output.fields = []
  }
  return output
}

function toPlanSource(source: any) {
  const kind = toText(source?.kind) || "ctx"
  return {
    kind,
    path: toText(source?.path),
    constValue: source?.constValue ?? "",
    tempKey: toText(source?.tempKey),
  }
}

function buildParamPlansPayload(model: ServiceCallModel) {
  const tempPlans = Array.isArray(props.paramPlans?.tempPlans) ? clone(props.paramPlans.tempPlans) : []
  const argPlans: any[] = []

  for (const binding of model.argBindings || []) {
    const name = toText(binding?.paramName)
    if (!name) continue
    const source = binding?.source || {}
    const mode = toText(source.mode) || "direct"

    if (mode === "objectBuilder" && Array.isArray(source.objectFields)) {
      for (const field of source.objectFields) {
        const fieldPath = toText(field?.fieldPath)
        if (!fieldPath) continue
        argPlans.push({
          target: `${name}.${fieldPath}`,
          typeHint: toText(binding?.paramType),
          source: toPlanSource(field?.source),
        })
      }
      continue
    }

    argPlans.push({
      target: name,
      typeHint: toText(binding?.paramType),
      source: toPlanSource(source),
    })
  }

  return { tempPlans, argPlans }
}

function handleMethodMeta(next: MethodMeta) {
  selectedMethodMeta.value = next ? clone(next) : null
}

function setAssemblerMode(mode: "basic" | "advanced") {
  activeAssemblerMode.value = mode
  patchCompConfig({
    paramAssemblerMode: mode,
    paramAssemblerAst: mode === "basic" ? getStoredParamAssemblerAst() : undefined,
  })
}

function handleServiceCallUpdate(model: ServiceCallModel) {
  const meta = selectedMethodMeta.value
  if (!model?.serviceRef?.serviceBean || !model?.serviceRef?.methodName) {
    emit("update:comp", null)
    emit("update:inputs", [])
    emit("update:output", null)
    emit("update:paramPlans", { tempPlans: [], argPlans: [] })
    return
  }

  const nextComp = buildCompPayload(model, meta)
  const nextInputs = buildInputsPayload(model, meta)
  const nextOutput = buildOutputPayload(meta)

  emit("update:comp", nextComp)
  emit("update:inputs", nextInputs)
  emit("update:output", nextOutput)

  if (!toText(props.label)) {
    const fallbackLabel = toText(meta?.serviceName) || toText(model.serviceRef?.serviceName) || toText(model.serviceRef?.methodName)
    if (fallbackLabel) emit("update:label", fallbackLabel)
  }
}

function handleBasicAssemblerAstUpdate(ast: Record<string, any> | null) {
  if (!props.comp || typeof props.comp !== "object") return
  patchCompConfig({
    paramAssemblerMode: "basic",
    paramAssemblerAst: ast,
  })
}

function handleBasicParamPlansUpdate(paramPlans: any) {
  emit("update:paramPlans", paramPlans)
}
</script>

<template>
  <div class="service-node-config">
    <section class="section">
      <div class="muted section-title">显示名称</div>
      <input
        class="input"
        style="width: 100%; box-sizing: border-box;"
        :class="{ locked: Boolean(comp) }"
        :readonly="Boolean(comp)"
        :value="label || ''"
        @input="!comp && emit('update:label', ($event.target as HTMLInputElement).value)"
      />
    </section>

    <section class="section">
      <div class="row section-head">
        <div class="muted section-title">服务方法</div>
        <div class="muted tiny">主配置入口：先选服务，再自动回填签名</div>
      </div>
      <div class="card primary-card">
        <ServiceCallEditor
          :model-value="serviceCallModel"
          :project-key="projectKey"
          :temp-keys="tempKeys"
          :source-path-options="sourcePathOptions"
          :show-bindings="false"
          @update:model-value="handleServiceCallUpdate"
          @select-method="handleMethodMeta"
        />
      </div>
    </section>

    <section v-if="comp" class="section">
      <div class="muted section-title">当前绑定</div>
      <div class="card binding-card">
        <div>
          <div class="muted tiny">服务/Bean</div>
          <div class="binding-value">{{ buildServiceName(comp) }}</div>
        </div>
        <div>
          <div class="muted tiny">Method</div>
          <div class="binding-value">{{ buildMethodName(comp) }}</div>
        </div>
        <div v-if="comp.version">
          <div class="muted tiny">版本</div>
          <div class="binding-value">{{ comp.version }}</div>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="row section-head">
        <div class="muted section-title">服务入参签名（摘要）</div>
        <div class="muted tiny">{{ inputSummaryText }}</div>
      </div>
      <div v-if="normalizedInputs.length > 0" class="signature-list">
        <div v-for="(item, index) in normalizedInputs" :key="`sig-row-${index}`" class="signature-row">
          <span class="signature-name" :title="item.name">{{ item.name }}</span>
          <span class="signature-type" :title="item.type">{{ item.type }}</span>
        </div>
        <div class="muted tiny signature-hint">上方服务选择器会自动回填签名；这里只保留摘要预览。</div>
      </div>
      <div v-else class="muted empty-tip">当前节点无入参定义。</div>
    </section>

    <section class="section">
      <div class="row section-head">
        <div class="muted section-title">参数装配方式</div>
        <div class="muted tiny">基础装配适合绝大多数对象和单层集合场景；复杂编排仍走高级模式。</div>
      </div>
      <div class="assembler-mode-switch">
        <button
          type="button"
          class="btn mini"
          :class="{ active: activeAssemblerMode === 'basic' }"
          @click="setAssemblerMode('basic')"
        >
          基础装配
        </button>
        <button
          type="button"
          class="btn mini"
          :class="{ active: activeAssemblerMode === 'advanced' }"
          @click="setAssemblerMode('advanced')"
        >
          高级模式
        </button>
      </div>

      <div v-if="activeAssemblerMode === 'basic'" class="card primary-card">
        <ParamAssemblerPanel
          :model-value="currentParamAssemblerAst"
          :project-key="projectKey"
          :endpoint-id="endpointId"
          :method-key="currentMethodKey"
          :input-defs="inputs"
          :source-path-options="sourcePathOptions"
          @update:model-value="handleBasicAssemblerAstUpdate"
          @update:paramPlans="handleBasicParamPlansUpdate"
        />
      </div>
      <div v-else class="muted tiny mode-hint">
        高级模式会直接编辑原始参数计划，适合基础装配未覆盖的复杂补数、特殊列表管线和兼容性调试场景。
      </div>
    </section>

    <section class="section">
      <div class="muted section-title">输出结果</div>
      <div v-if="!output" class="muted empty-tip">暂无返回值。</div>
      <div v-else class="card output-card">
        <div v-if="output.description" class="input-hint">说明：{{ output.description }}</div>
        <div class="type-pill">类型：{{ formatOutputType(output) }}</div>

        <div v-if="isObjectOutput(output)" class="field-table">
          <div class="field-row header">
            <span>字段</span>
            <span>类型</span>
            <span>说明</span>
          </div>
          <div v-for="(field, idx) in output.fields || []" :key="idx" class="field-row">
            <span>{{ field.name }}</span>
            <span>{{ field.type }}</span>
            <span>{{ field.description || "-" }}</span>
          </div>
          <div v-if="(output.fields || []).length === 0" class="muted empty-tip">当前返回对象暂无字段摘要。</div>
        </div>
        <div v-else class="muted tiny">基础类型：{{ formatOutputType(output) }}</div>

        <div class="binding-field">
          <div class="muted tiny">绑定到 ctx 的 key</div>
          <input
            class="input"
            placeholder="默认 retxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            :value="output.contextKey || ''"
            @input="updateOutputField({ contextKey: ($event.target as HTMLInputElement).value })"
          />
        </div>
      </div>
    </section>

    <details class="section advanced-panel">
      <summary class="advanced-summary">高级参数管线（可选）</summary>
      <div class="muted tiny advanced-hint">用于保留复杂场景的原始参数计划编辑。常规服务节点优先使用上方“服务方法”入口。</div>
      <div class="split-top">
        <ParamPlanBuilder
          :model-value="paramPlans"
          :input-defs="inputs"
          :project-key="projectKey"
          :source-path-options="sourcePathOptions"
          @update:model-value="emit('update:paramPlans', $event)"
        />
      </div>
    </details>
  </div>
</template>

<style scoped>
.service-node-config {
  display: flex;
  flex-direction: column;
}

.section {
  margin-top: 12px;
}

.section-title {
  margin-bottom: 6px;
}

.section-head {
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.split-top {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}

.assembler-mode-switch {
  display: inline-flex;
  gap: 8px;
  margin-bottom: 10px;
}

.assembler-mode-switch .btn.active {
  border-color: #2563eb;
  color: #1d4ed8;
  background: #eff6ff;
}

.mode-hint {
  line-height: 1.5;
}

.row {
  display: flex;
  gap: 8px;
}

.card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.primary-card,
.binding-card,
.output-card {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.muted {
  color: #64748b;
  font-size: 12px;
}

.muted.tiny {
  font-size: 11px;
}

.input {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.input.locked {
  background: #f3f4f6;
  color: #6b7280;
  cursor: not-allowed;
}

.binding-value {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
}

.empty-tip {
  font-size: 12px;
}

.signature-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.signature-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.6fr);
  gap: 8px;
  align-items: center;
  border: 1px solid #e5e7eb;
  background: #fbfdff;
  border-radius: 8px;
  padding: 6px 8px;
}

.signature-name,
.signature-type {
  font-size: 12px;
  color: #1f2937;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.signature-type {
  color: #475569;
}

.signature-hint {
  margin-top: 2px;
}

.input-hint {
  font-size: 11px;
  color: #94a3b8;
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

.field-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.5fr;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
}

.field-row.header {
  font-weight: 600;
  background: #f8fafc;
  color: #475569;
}

.field-row:not(.header) {
  background: #fefefe;
  border: 1px solid #e5e7eb;
}

.advanced-panel {
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  padding: 10px;
  background: #fcfdff;
}

.advanced-summary {
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.advanced-hint {
  margin-top: 8px;
}
</style>
