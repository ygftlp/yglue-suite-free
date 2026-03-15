<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue"
import { api, type EndpointComponentGroup } from "../api/client"
import SourcePathInput from "./SourcePathInput.vue"

type ArgSourceKind = "ctx" | "const" | "tempVar"
type ArgSourceMode = "direct" | "objectBuilder"

interface ObjectFieldSource {
  kind: ArgSourceKind
  path: string
  constValue: string
  tempKey: string
}

interface ObjectFieldBinding {
  id: string
  fieldPath: string
  source: ObjectFieldSource
}

interface ArgBindingSource {
  kind: ArgSourceKind
  path: string
  constValue: string
  tempKey: string
  mode: ArgSourceMode
  objectFields: ObjectFieldBinding[]
}

interface ArgBinding {
  id: string
  paramName: string
  paramType: string
  source: ArgBindingSource
}

interface ServiceRef {
  endpointId?: number | string
  serviceKey: string
  serviceBean: string
  serviceName: string
  serviceClass: string
  methodName: string
  methodSignature: string
  methodSignatureHash?: string
  returnType: string
}

interface ServiceCallModel {
  fn: string
  serviceRef: ServiceRef | null
  argBindings: ArgBinding[]
  argsMode: "list"
  argsText: string
  args: Array<Record<string, any>>
}

interface CatalogParam {
  name: string
  type: string
  required: boolean
  description: string
  schema: Record<string, any> | null
  listItemType: string
}

interface ParamTypeProperty {
  path: string
  typeLabel: string
  required: boolean
  description: string
  constraintText: string
  depth: number
}

interface CatalogMethodOption {
  optionKey: string
  endpointId?: number | string
  serviceName: string
  serviceBean: string
  serviceClass: string
  methodName: string
  methodSignature: string
  methodSignatureHash: string
  displayName: string
  description: string
  returnType: string
  returnSchema?: Record<string, any> | null
  params: CatalogParam[]
}

const props = defineProps<{
  modelValue?: any
  projectKey?: string
  tempKeys?: string[]
  sourcePathOptions?: string[]
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: any): void
  (e: "select-method", value: any | null): void
}>()

function toText(value: unknown): string {
  return typeof value === "string" ? value.trim() : ""
}

function safeParseJson(raw: unknown): any | null {
  if (typeof raw !== "string" || !raw.trim()) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value)
}

function parseBoolean(value: unknown, fallback = false): boolean {
  if (typeof value === "boolean") return value
  if (typeof value === "string") {
    const text = value.trim().toLowerCase()
    if (text === "true") return true
    if (text === "false") return false
  }
  return fallback
}

function toNumberLike(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) return value
  if (typeof value === "string" && value.trim()) {
    const num = Number(value)
    if (Number.isFinite(num)) return num
  }
  return null
}

function buildConstraintText(schema: Record<string, any> | null): string {
  if (!schema) return ""
  const parts: string[] = []

  const minLength = toNumberLike(schema.minLength)
  const maxLength = toNumberLike(schema.maxLength)
  const minItems = toNumberLike(schema.minItems)
  const maxItems = toNumberLike(schema.maxItems)
  const minimum = toNumberLike(schema.minimum)
  const maximum = toNumberLike(schema.maximum)
  const exclusiveMinimum = toNumberLike(schema.exclusiveMinimum)
  const exclusiveMaximum = toNumberLike(schema.exclusiveMaximum)
  const pattern = toText(schema.pattern)
  const format = toText(schema.format)
  const enumValues = Array.isArray(schema.enum) ? schema.enum : []

  if (minLength !== null || maxLength !== null) {
    const left = minLength !== null ? `${minLength}` : "-"
    const right = maxLength !== null ? `${maxLength}` : "-"
    parts.push(`len[${left},${right}]`)
  }

  if (minItems !== null || maxItems !== null) {
    const left = minItems !== null ? `${minItems}` : "-"
    const right = maxItems !== null ? `${maxItems}` : "-"
    parts.push(`items[${left},${right}]`)
  }

  if (minimum !== null || exclusiveMinimum !== null) {
    const value = exclusiveMinimum !== null ? exclusiveMinimum : minimum
    const op = exclusiveMinimum !== null ? ">" : ">="
    parts.push(`min ${op} ${value}`)
  }

  if (maximum !== null || exclusiveMaximum !== null) {
    const value = exclusiveMaximum !== null ? exclusiveMaximum : maximum
    const op = exclusiveMaximum !== null ? "<" : "<="
    parts.push(`max ${op} ${value}`)
  }

  if (pattern) parts.push(`pattern: ${pattern}`)
  if (format) parts.push(`format: ${format}`)
  if (enumValues.length > 0) {
    const shown = enumValues.slice(0, 3).map((item) => String(item))
    const suffix = enumValues.length > 3 ? ` ... +${enumValues.length - 3}` : ""
    parts.push(`enum: ${shown.join(" | ")}${suffix}`)
  }

  return parts.join("; ")
}

function isPrimitiveType(typeName: string): boolean {
  const raw = typeName.trim().toLowerCase()
  if (!raw) return false
  return ["string", "java.lang.string", "boolean", "java.lang.boolean", "int", "integer", "java.lang.integer", "long", "java.lang.long", "double", "java.lang.double", "float", "java.lang.float", "number", "java.lang.number"].includes(raw)
}

function isListType(typeName: string): boolean {
  const raw = typeName.trim().toLowerCase()
  if (!raw) return false
  return raw.startsWith("java.util.list") || raw.startsWith("list<") || raw.endsWith("[]") || raw === "array"
}

function isObjectLikeType(typeName: string): boolean {
  const raw = typeName.trim().toLowerCase()
  if (!raw) return false
  if (isListType(raw) || isPrimitiveType(raw)) return false
  return true
}

function defaultModeByType(typeName: string): ArgSourceMode {
  return isObjectLikeType(typeName) ? "objectBuilder" : "direct"
}

function createObjectFieldSource(kind: ArgSourceKind = "ctx"): ObjectFieldSource {
  return { kind, path: "", constValue: "", tempKey: "" }
}

function createObjectField(fieldPath = ""): ObjectFieldBinding {
  return {
    id: `obj_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    fieldPath,
    source: createObjectFieldSource("ctx"),
  }
}

function createSource(kind: ArgSourceKind = "ctx", mode: ArgSourceMode = "direct"): ArgBindingSource {
  return {
    kind,
    path: "request.body.xxx",
    constValue: "",
    tempKey: "",
    mode,
    objectFields: [createObjectField()],
  }
}

function createBinding(name = "", type = ""): ArgBinding {
  return {
    id: `binding_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    paramName: name,
    paramType: type,
    source: createSource("ctx", defaultModeByType(type)),
  }
}

function createModel(): ServiceCallModel {
  return { fn: "", serviceRef: null, argBindings: [], argsMode: "list", argsText: "[]", args: [] }
}

function normalizeObjectField(raw: any): ObjectFieldBinding {
  const next = createObjectField(toText(raw?.fieldPath || raw?.name || ""))
  return {
    ...next,
    ...raw,
    source: { ...createObjectFieldSource("ctx"), ...(raw?.source || {}) },
  }
}

function normalizeBinding(raw: any): ArgBinding {
  const next = createBinding(raw?.paramName || raw?.name || "", raw?.paramType || raw?.typeHint || "")
  const nextMode = toText(raw?.source?.mode)
  const merged: ArgBinding = {
    ...next,
    ...raw,
    source: {
      ...createSource("ctx", defaultModeByType(next.paramType)),
      ...(raw?.source || {}),
      mode: nextMode === "objectBuilder" || nextMode === "direct" ? nextMode : defaultModeByType(next.paramType),
      objectFields: Array.isArray(raw?.source?.objectFields)
        ? raw.source.objectFields.map((item: any) => normalizeObjectField(item))
        : [createObjectField()],
    },
  }
  if (!isObjectLikeType(merged.paramType) && merged.source.mode === "objectBuilder") merged.source.mode = "direct"
  if (!Array.isArray(merged.source.objectFields) || merged.source.objectFields.length === 0) {
    merged.source.objectFields = [createObjectField()]
  }
  return merged
}

function toLegacyArgs(argBindings: ArgBinding[]): Array<Record<string, any>> {
  return argBindings.map((binding) => ({
    id: binding.id,
    name: binding.paramName,
    typeHint: binding.paramType,
    kind: binding.source.kind,
    path: binding.source.path,
    constValue: binding.source.constValue,
    tempKey: binding.source.tempKey,
    mode: binding.source.mode,
    objectFields: binding.source.objectFields.map((field) => ({
      id: field.id,
      fieldPath: field.fieldPath,
      kind: field.source.kind,
      path: field.source.path,
      constValue: field.source.constValue,
      tempKey: field.source.tempKey,
    })),
  }))
}

function normalizeModel(raw: any): ServiceCallModel {
  const base = createModel()
  const next: ServiceCallModel = {
    ...base,
    ...(raw || {}),
    serviceRef: raw?.serviceRef ? { ...raw.serviceRef } : null,
    argBindings: Array.isArray(raw?.argBindings) ? raw.argBindings.map((item: any) => normalizeBinding(item)) : [],
  }
  if (!next.fn && next.serviceRef?.serviceBean && next.serviceRef?.methodName) {
    next.fn = `${next.serviceRef.serviceBean}.${next.serviceRef.methodName}`
  }
  next.argsMode = "list"
  next.argsText = "[]"
  next.args = toLegacyArgs(next.argBindings)
  return next
}

function getListItemType(typeName: string): string {
  const text = toText(typeName)
  if (!text) return ""
  if (text.endsWith("[]")) return text.slice(0, -2)
  const left = text.indexOf("<")
  const right = text.lastIndexOf(">")
  if (left > 0 && right > left) {
    return text.slice(left + 1, right).trim()
  }
  return ""
}

function normalizeSchemaNode(raw: unknown): Record<string, any> | null {
  if (!isRecord(raw)) return null
  const schema = clone(raw)
  if (!toText(schema["x-javaType"])) {
    const javaType = toText(schema.javaType) || toText(schema.typeName)
    if (javaType) schema["x-javaType"] = javaType
  }
  return schema
}

function buildSchemaFromParamRaw(raw: any, fallbackType: string): Record<string, any> | null {
  if (!isRecord(raw)) return null
  const directSchema = normalizeSchemaNode(raw.schema)
  if (directSchema) {
    if (!toText(directSchema["x-javaType"]) && fallbackType) {
      directSchema["x-javaType"] = fallbackType
    }
    return directSchema
  }

  const hasSchemaShape =
    isRecord(raw.properties) ||
    isRecord(raw.items) ||
    raw.items !== undefined ||
    raw.additionalProperties !== undefined ||
    toText(raw["x-javaType"]) ||
    toText(raw.type) === "object" ||
    toText(raw.type) === "array"

  if (!hasSchemaShape) return null

  const schema: Record<string, any> = {}
  const schemaType = toText(raw.schemaType) || toText(raw.jsonType)
  if (schemaType) schema.type = schemaType
  else if (isRecord(raw.properties)) schema.type = "object"
  else if (raw.items !== undefined) schema.type = "array"

  if (isRecord(raw.properties)) schema.properties = clone(raw.properties)
  if (Array.isArray(raw.required)) schema.required = [...raw.required]
  if (raw.items !== undefined) schema.items = clone(raw.items)
  if (raw.additionalProperties !== undefined) schema.additionalProperties = clone(raw.additionalProperties)

  const javaType = toText(raw["x-javaType"]) || toText(raw.javaType) || toText(raw.typeName) || fallbackType
  if (javaType) schema["x-javaType"] = javaType
  return Object.keys(schema).length > 0 ? schema : null
}

function inferListItemType(typeName: string, schema: Record<string, any> | null): string {
  const fromType = getListItemType(typeName)
  if (fromType) return fromType
  const items = schema && isRecord(schema.items) ? schema.items : null
  if (!items) return "java.lang.Object"
  return toText(items["x-javaType"]) || toText(items.javaType) || toText(items.typeName) || toText(items.type) || "java.lang.Object"
}

function normalizeParam(item: any, index: number): CatalogParam {
  const name = toText(item?.name) || toText(item?.paramName) || `arg${index + 1}`
  const type = toText(item?.type) || toText(item?.typeName) || toText(item?.javaType) || toText(item?.["x-javaType"]) || "java.lang.Object"
  const required = parseBoolean(item?.required, parseBoolean(item?.["x-required"], false))
  const description = toText(item?.description) || toText(item?.desc) || toText(item?.title)
  const schema = buildSchemaFromParamRaw(item, type)
  const listItemType = isListType(type) ? inferListItemType(type, schema) : ""
  return { name, type, required, description, schema, listItemType }
}

function buildMethodSignature(methodName: string, params: CatalogParam[]): string {
  return `${methodName}(${params.map((item) => item.type || "java.lang.Object").join(",")})`
}

function buildCatalog(groups: EndpointComponentGroup[]): CatalogMethodOption[] {
  const options: CatalogMethodOption[] = []
  const seen = new Set<string>()
  for (const group of groups || []) {
    for (const item of group.items || []) {
      const endpointType = String((item as any).endpointType || "").toUpperCase()
      const config = safeParseJson((item as any).configJson)
      if (endpointType !== "SERVICE" && endpointType !== "FLOW_OPERATION") continue

      const serviceBean = endpointType === "SERVICE" ? toText(config?.bean) || toText((item as any).bean) : toText(config?.serviceBean) || toText((item as any).bean)
      const serviceName = endpointType === "SERVICE"
        ? toText(config?.name) || toText((item as any).displayName) || serviceBean || "未命名服务"
        : toText(config?.serviceName) || toText((item as any).displayName) || serviceBean || "未命名服务"
      const serviceClass = endpointType === "SERVICE" ? toText(config?.class) || toText((item as any).path) : toText(config?.serviceClass) || toText((item as any).path)
      const ops = endpointType === "SERVICE" ? (Array.isArray(config?.operations) ? config.operations : []) : [config]

      for (const op of ops) {
        const methodName = toText(op?.method) || toText(op?.name)
        if (!methodName) continue
        const paramsRaw = Array.isArray(op?.params) ? op.params : Array.isArray(op?.parameters) ? op.parameters : []
        const params = paramsRaw.map((param: any, idx: number) => normalizeParam(param, idx))
        const methodSignature = toText(op?.methodSignature) || buildMethodSignature(methodName, params)
        const methodSignatureHash = toText(op?.methodSignatureHash)
        const key = `${serviceBean}|${methodSignature}`
        if (seen.has(key)) continue
        seen.add(key)
        options.push({
          optionKey: key,
          endpointId: (item as any).id,
          serviceName,
          serviceBean,
          serviceClass,
          methodName,
          methodSignature,
          methodSignatureHash,
          displayName: toText(op?.name) || methodName,
          description: toText(op?.description),
          returnType: toText(op?.returnType) || "java.lang.Object",
          returnSchema: buildSchemaFromParamRaw(op?.returnSchema || op?.responseSchema, toText(op?.returnType) || "java.lang.Object"),
          params,
        })
      }
    }
  }
  return options.sort((a, b) => `${a.serviceName}.${a.methodName}`.localeCompare(`${b.serviceName}.${b.methodName}`))
}

function truncateText(value: string, max = 60): string {
  const text = toText(value)
  return text.length <= max ? text : `${text.slice(0, max)}...`
}

function flattenTemplateLeafPaths(value: any, prefix = "", output: string[] = []): string[] {
  if (value === null || value === undefined || Array.isArray(value) || typeof value !== "object") {
    if (prefix) output.push(prefix)
    return output
  }
  const entries = Object.entries(value)
  if (entries.length === 0) {
    if (prefix) output.push(prefix)
    return output
  }
  for (const [key, child] of entries) {
    const nextPath = prefix ? `${prefix}.${key}` : key
    if (child !== null && typeof child === "object" && !Array.isArray(child)) flattenTemplateLeafPaths(child, nextPath, output)
    else output.push(nextPath)
  }
  return output
}

function getSchemaTypeLabel(schema: unknown): string {
  if (!isRecord(schema)) return ""
  const javaType = toText(schema["x-javaType"]) || toText(schema.javaType) || toText(schema.typeName)
  if (javaType) return javaType
  const schemaType = toText(schema.type)
  if (schemaType === "array") {
    const itemLabel = getSchemaTypeLabel(schema.items)
    return itemLabel ? `List<${itemLabel}>` : "array"
  }
  return schemaType || "object"
}

function flattenSchemaLeafPaths(schema: unknown, prefix = "", output: string[] = []): string[] {
  if (!isRecord(schema)) return output
  const properties = isRecord(schema.properties) ? schema.properties : null
  if (!properties) return output
  for (const [key, child] of Object.entries(properties)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (isRecord(child) && isRecord(child.properties)) {
      flattenSchemaLeafPaths(child, path, output)
      continue
    }
    if (isRecord(child) && toText(child.type) === "array" && isRecord(child.items) && isRecord(child.items.properties)) {
      flattenSchemaLeafPaths(child.items, `${path}[]`, output)
      continue
    }
    output.push(path)
  }
  return output
}

function collectTypeProperties(schema: unknown, prefix = "", depth = 0, output: ParamTypeProperty[] = []): ParamTypeProperty[] {
  if (!isRecord(schema)) return output
  const properties = isRecord(schema.properties) ? schema.properties : null
  const requiredSet = new Set(Array.isArray(schema.required) ? schema.required.map((item) => String(item)) : [])
  if (!properties) return output
  for (const [key, childRaw] of Object.entries(properties)) {
    const child = isRecord(childRaw) ? childRaw : null
    const path = prefix ? `${prefix}.${key}` : key
    const required = requiredSet.has(key) || parseBoolean(child?.["x-required"], false)
    const description = toText(child?.description) || toText(child?.title)
    const typeLabel = getSchemaTypeLabel(child) || "object"
    const constraintText = buildConstraintText(child)
    output.push({ path, typeLabel, required, description, constraintText, depth })

    if (child && isRecord(child.properties)) {
      collectTypeProperties(child, path, depth + 1, output)
      continue
    }
    if (child && toText(child.type) === "array" && isRecord(child.items)) {
      const itemType = getSchemaTypeLabel(child.items) || "object"
      output.push({
        path: `${path}[]`,
        typeLabel: itemType,
        required: false,
        description: toText((child.items as Record<string, any>).description),
        constraintText: buildConstraintText(child.items as Record<string, any>),
        depth: depth + 1,
      })
      if (isRecord((child.items as Record<string, any>).properties)) {
        collectTypeProperties(child.items, `${path}[]`, depth + 2, output)
      }
    }
  }
  return output
}

const local = ref<ServiceCallModel>(normalizeModel(props.modelValue))
const showPicker = ref(false)
const searchKeyword = ref("")
const selectedOptionKey = ref("")
const options = ref<CatalogMethodOption[]>([])
const loading = ref(false)
const loadError = ref("")
const tempDataListId = `tmp_${Math.random().toString(36).slice(2, 9)}`

const editingBindingId = ref("")
const editingBindingIndex = ref(-1)
const objectTemplateText = ref("{\n  \"profile\": {\n    \"name\": \"\",\n    \"age\": 0\n  }\n}")
const objectTemplateError = ref("")

const tempKeys = computed(() =>
  (Array.isArray(props.tempKeys) ? props.tempKeys : []).map((item) => String(item || "").trim()).filter((item) => Boolean(item))
)
const sourcePathOptions = computed(() =>
  (Array.isArray(props.sourcePathOptions) ? props.sourcePathOptions : [])
    .map((item) => String(item || "").trim())
    .filter((item) => Boolean(item))
)

const filteredOptions = computed(() => {
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (!keyword) return options.value
  return options.value.filter((item) => [item.serviceName, item.serviceBean, item.serviceClass, item.methodName, item.displayName, item.methodSignature, item.returnType].join(" ").toLowerCase().includes(keyword))
})

const selectedOption = computed(() => options.value.find((item) => item.optionKey === selectedOptionKey.value) || null)
const editingBinding = computed(() => local.value.argBindings.find((item) => item.id === editingBindingId.value) || null)
const currentMethodOption = computed(() => {
  const refInfo = local.value.serviceRef
  if (!refInfo?.serviceBean || !refInfo?.methodSignature) return null
  const key = `${refInfo.serviceBean}|${refInfo.methodSignature}`
  return options.value.find((item) => item.optionKey === key) || null
})
const hasSelectedMethodButCatalogMissing = computed(() => Boolean(local.value.serviceRef?.methodSignature) && !currentMethodOption.value)
const serviceCallErrors = computed(() => {
  const errors: string[] = []
  if (hasSelectedMethodButCatalogMissing.value) {
    errors.push("已选方法在最新目录中不存在，请重新选择服务方法。")
  }
  return errors
})
const editingParamMeta = computed(() => {
  if (!editingBinding.value) return null
  return getParamMeta(editingBinding.value, editingBindingIndex.value)
})
const editingParamTypeProperties = computed(() => getParamTypeProperties(editingParamMeta.value))

const currentMethodLabel = computed(() => {
  const refInfo = local.value.serviceRef
  if (!refInfo?.methodSignature) return ""
  return `${refInfo.serviceBean || "服务"}.${refInfo.methodSignature}`
})

function getParamMeta(binding: ArgBinding, index = -1): CatalogParam | null {
  const option = currentMethodOption.value
  if (!option) return null
  if (index >= 0 && option.params[index]) return option.params[index]
  return (
    option.params.find((param) => param.name === binding.paramName && param.type === binding.paramType) ||
    option.params.find((param) => param.name === binding.paramName) ||
    null
  )
}

function getParamTypeProperties(meta: CatalogParam | null): ParamTypeProperty[] {
  if (!meta || !meta.schema) return []
  if (isListType(meta.type)) {
    const items = isRecord(meta.schema.items) ? meta.schema.items : null
    if (!items) return []
    const rows: ParamTypeProperty[] = []
    rows.push({
      path: "[item]",
      typeLabel: getSchemaTypeLabel(items) || meta.listItemType || "java.lang.Object",
      required: false,
      description: toText(items.description),
      constraintText: buildConstraintText(items),
      depth: 0,
    })
    if (isRecord(items.properties)) {
      collectTypeProperties(items, "[item]", 1, rows)
    }
    return rows
  }
  return collectTypeProperties(meta.schema)
}

function getParamConstraintSummary(binding: ArgBinding, index: number): string {
  const meta = getParamMeta(binding, index)
  if (!meta) return "未知"
  return meta.required ? "必填" : "可选"
}

function getParamTypeSummary(binding: ArgBinding, index: number): string {
  const meta = getParamMeta(binding, index)
  if (!meta) return "未知"
  if (isListType(meta.type)) {
    return `List<${meta.listItemType || "java.lang.Object"}>`
  }
  if (isObjectLikeType(meta.type)) {
    const count = getParamTypeProperties(meta).length
    return count > 0 ? `对象字段 ${count}` : "对象"
  }
  return "基础类型"
}

function canUseObjectBuilder(binding: ArgBinding): boolean {
  return isObjectLikeType(binding.paramType)
}

function isConstJsonCompatible(binding: ArgBinding): boolean {
  if (binding.source.kind !== "const") return true
  if (!binding.source.constValue.trim()) return false
  const parsed = safeParseJson(binding.source.constValue)
  if (isListType(binding.paramType)) return Array.isArray(parsed)
  if (canUseObjectBuilder(binding)) return parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)
  return true
}

function validateObjectField(field: ObjectFieldBinding): string[] {
  const errors: string[] = []
  if (!toText(field.fieldPath)) errors.push("字段路径不能为空。")
  if (field.source.kind === "ctx" && !toText(field.source.path)) errors.push("来源为 ctx 时，路径不能为空。")
  if (field.source.kind === "tempVar" && !toText(field.source.tempKey)) errors.push("来源为临时变量时，tempKey 不能为空。")
  if (field.source.kind === "const" && !toText(field.source.constValue)) errors.push("来源为常量时，值不能为空。")
  return errors
}

function getBindingErrors(binding: ArgBinding): string[] {
  const errors: string[] = []
  if (hasSelectedMethodButCatalogMissing.value) {
    errors.push("当前方法签名已失效，请重新选择服务方法。")
    return errors
  }
  if (binding.source.mode === "direct") {
    if (binding.source.kind === "ctx" && !toText(binding.source.path)) errors.push("ctx 来源路径不能为空。")
    if (binding.source.kind === "tempVar" && !toText(binding.source.tempKey)) errors.push("tempVar 来源的 tempKey 不能为空。")
    if (binding.source.kind === "const" && !toText(binding.source.constValue)) errors.push("常量值不能为空。")
    if (binding.source.kind === "const" && !isConstJsonCompatible(binding)) {
      if (isListType(binding.paramType)) errors.push(`常量值需要是 JSON 数组（目标类型：${binding.paramType}）。`)
      else if (canUseObjectBuilder(binding)) errors.push(`常量值需要是 JSON 对象（目标类型：${binding.paramType}）。`)
    }
    return errors
  }
  if (!canUseObjectBuilder(binding)) return ["当前参数类型不支持对象构造器模式。"]
  if (!Array.isArray(binding.source.objectFields) || binding.source.objectFields.length === 0) return ["至少需要一条对象字段映射。"]
  const used = new Set<string>()
  for (const field of binding.source.objectFields) {
    const key = toText(field.fieldPath)
    if (key) {
      if (used.has(key)) errors.push(`字段路径重复：${key}`)
      used.add(key)
    }
    errors.push(...validateObjectField(field))
  }
  return errors
}

function getBindingWarnings(binding: ArgBinding): string[] {
  if (canUseObjectBuilder(binding) && binding.source.mode === "direct") {
    return ["对象类型建议使用“对象构造器”模式。"]
  }
  return []
}

function getBindingState(binding: ArgBinding): "error" | "warn" | "ok" {
  if (getBindingErrors(binding).length > 0) return "error"
  if (getBindingWarnings(binding).length > 0) return "warn"
  return "ok"
}

function getBindingStateText(binding: ArgBinding): string {
  const state = getBindingState(binding)
  if (state === "error") return "错误"
  if (state === "warn") return "告警"
  return "正常"
}

function getSourceSummary(binding: ArgBinding): string {
  if (binding.source.mode === "objectBuilder") return `对象构造器：${binding.source.objectFields.length} 个字段`
  if (binding.source.kind === "ctx") return `来源 ctx/request：${truncateText(binding.source.path || "空")}`
  if (binding.source.kind === "tempVar") return `来源 tempVar：${truncateText(binding.source.tempKey || "空")}`
  return `来源常量：${truncateText(binding.source.constValue || "空")}`
}

function getModeSummary(binding: ArgBinding): string {
  return binding.source.mode === "objectBuilder" ? "对象构造器模式" : "直接取值模式"
}

type BindingPreset = "ctx" | "const" | "tempVar" | "objectBuilder"

function getDefaultCtxPath(binding: ArgBinding): string {
  const key = toText(binding.paramName) || "value"
  return `request.body.${key}`
}

function getDefaultConstValue(binding: ArgBinding): string {
  if (isListType(binding.paramType)) return "[]"
  if (canUseObjectBuilder(binding)) return "{}"
  const raw = toText(binding.paramType).toLowerCase()
  if (raw.includes("bool")) return "false"
  if (raw.includes("int") || raw.includes("long") || raw.includes("double") || raw.includes("float") || raw.includes("number")) return "0"
  return "\"\""
}

function applyBindingPreset(binding: ArgBinding, preset: BindingPreset) {
  objectTemplateError.value = ""
  if (preset === "ctx") {
    binding.source.mode = "direct"
    binding.source.kind = "ctx"
    binding.source.path = getDefaultCtxPath(binding)
    return
  }
  if (preset === "const") {
    binding.source.mode = "direct"
    binding.source.kind = "const"
    binding.source.constValue = getDefaultConstValue(binding)
    return
  }
  if (preset === "tempVar") {
    binding.source.mode = "direct"
    binding.source.kind = "tempVar"
    binding.source.tempKey = toText(binding.paramName) || "tmpValue"
    return
  }
  if (!canUseObjectBuilder(binding)) return
  binding.source.mode = "objectBuilder"
  const meta = getParamMeta(binding, editingBindingIndex.value)
  if (meta?.schema) {
    const paths = flattenSchemaLeafPaths(meta.schema)
    if (paths.length > 0) {
      binding.source.objectFields = paths.map((path) => ({
        ...createObjectField(path),
        source: { ...createObjectFieldSource("ctx"), kind: "ctx", path: "" },
      }))
      return
    }
  }
  if (!Array.isArray(binding.source.objectFields) || binding.source.objectFields.length === 0) {
    binding.source.objectFields = [createObjectField()]
  }
}

function addObjectField(binding: ArgBinding) {
  binding.source.objectFields.push(createObjectField())
}

function removeObjectField(binding: ArgBinding, index: number) {
  if (binding.source.objectFields.length <= 1) return
  binding.source.objectFields.splice(index, 1)
}

function openBindingEditor(binding: ArgBinding, index: number) {
  editingBindingId.value = binding.id
  editingBindingIndex.value = index
  objectTemplateError.value = ""
}

function closeBindingEditor() {
  editingBindingId.value = ""
  editingBindingIndex.value = -1
  objectTemplateError.value = ""
}

function buildFieldsFromTemplate(binding: ArgBinding) {
  objectTemplateError.value = ""
  const parsed = safeParseJson(objectTemplateText.value)
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    objectTemplateError.value = "模板 JSON 必须是对象。"
    return
  }
  const paths = flattenTemplateLeafPaths(parsed)
  if (paths.length === 0) {
    objectTemplateError.value = "模板 JSON 未识别到叶子字段。"
    return
  }
  binding.source.objectFields = paths.map((path) => ({
    ...createObjectField(path),
    source: { ...createObjectFieldSource("ctx"), kind: "ctx", path: "" },
  }))
}

function buildFieldsFromParamType(binding: ArgBinding) {
  objectTemplateError.value = ""
  const meta = getParamMeta(binding, editingBindingIndex.value)
  if (!meta?.schema) {
    objectTemplateError.value = "当前参数缺少 Schema。"
    return
  }
  const paths = flattenSchemaLeafPaths(meta.schema)
  if (paths.length === 0) {
    objectTemplateError.value = "Schema 未提取到字段，请手工填写模板 JSON。"
    return
  }
  binding.source.objectFields = paths.map((path) => ({
    ...createObjectField(path),
    source: { ...createObjectFieldSource("ctx"), kind: "ctx", path: "" },
  }))
}

function buildEmitPayload(model: ServiceCallModel): ServiceCallModel {
  const payload: ServiceCallModel = {
    fn: model.fn || "",
    serviceRef: model.serviceRef ? clone(model.serviceRef) : null,
    argBindings: clone(model.argBindings || []),
    argsMode: "list",
    argsText: "[]",
    args: toLegacyArgs(model.argBindings || []),
  }
  if (payload.serviceRef?.serviceBean && payload.serviceRef?.methodName) payload.fn = `${payload.serviceRef.serviceBean}.${payload.serviceRef.methodName}`
  return payload
}

async function loadCatalog() {
  if (!props.projectKey) {
    options.value = []
    loadError.value = "缺少 projectKey，无法加载服务目录。"
    return
  }
  loading.value = true
  loadError.value = ""
  try {
    options.value = buildCatalog((await api.listEndpointComponents(props.projectKey)) || [])
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : "加载服务目录失败。"
    options.value = []
  } finally {
    loading.value = false
  }
}

function openPicker() {
  showPicker.value = true
  searchKeyword.value = ""
  const refInfo = local.value.serviceRef
  selectedOptionKey.value = refInfo?.serviceBean && refInfo?.methodSignature ? `${refInfo.serviceBean}|${refInfo.methodSignature}` : ""
  if (!options.value.length && !loading.value) loadCatalog()
}

function closePicker() {
  showPicker.value = false
}

function applySelectedMethod() {
  const option = selectedOption.value
  if (!option) return
  emit("select-method", clone(option))
  const existing = new Map(local.value.argBindings.map((item) => [item.paramName, item]))
  local.value.argBindings = option.params.map((param) => {
    const prev = existing.get(param.name)
    return prev ? normalizeBinding({ ...prev, paramName: param.name, paramType: param.type }) : createBinding(param.name, param.type)
  })
  local.value.serviceRef = {
    endpointId: option.endpointId,
    serviceKey: `${option.serviceBean}:${option.serviceClass}`,
    serviceBean: option.serviceBean,
    serviceName: option.serviceName,
    serviceClass: option.serviceClass,
    methodName: option.methodName,
    methodSignature: option.methodSignature,
    methodSignatureHash: option.methodSignatureHash,
    returnType: option.returnType,
  }
  local.value.fn = `${option.serviceBean}.${option.methodName}`
  closeBindingEditor()
  closePicker()
}

watch(() => props.modelValue, (next) => { local.value = normalizeModel(next) }, { deep: true, immediate: true })
watch(local, (next) => emit("update:modelValue", buildEmitPayload(next)), { deep: true })
watch(currentMethodOption, (next) => emit("select-method", next ? clone(next) : null), { immediate: true })
watch(() => props.projectKey, () => { loadCatalog() }, { immediate: true })

onMounted(() => { loadCatalog() })
</script>

<template>
  <div class="service-call-editor">
    <div class="editor-head">
      <div class="muted tiny">先选服务方法，再逐个配置参数。</div>
      <button type="button" class="btn mini" @click="openPicker">选择方法</button>
    </div>
    <div class="muted tiny">快速配置：1 选择方法 2 配置参数来源 3 完成并返回。</div>

    <div v-if="currentMethodLabel" class="method-pill" :class="{ 'method-missing': hasSelectedMethodButCatalogMissing }">{{ currentMethodLabel }}</div>
    <div v-else class="muted tiny">尚未选择方法</div>
    <div v-if="loadError" class="warn">{{ loadError }}</div>
    <div v-if="serviceCallErrors.length > 0" class="plan-errors">
      <div v-for="msg in serviceCallErrors" :key="msg" class="plan-error">{{ msg }}</div>
    </div>

    <div class="arg-list">
      <div class="muted tiny">参数列表（每行一个参数）</div>
      <div v-if="local.argBindings.length === 0" class="muted tiny">当前方法无参数</div>

      <div v-for="(binding, index) in local.argBindings" :key="binding.id" class="arg-row">
        <div class="arg-row-left" :title="getSourceSummary(binding)">
          <span class="arg-index">{{ index + 1 }}.</span>
          <span class="arg-name">{{ binding.paramName }}</span>
          <span class="arg-type">{{ binding.paramType }}</span>
          <span class="status-pill" :class="getBindingState(binding)">{{ getBindingStateText(binding) }}</span>
        </div>
        <div class="arg-row-right">
          <button type="button" class="btn mini" @click="openBindingEditor(binding, index)">配置</button>
        </div>
      </div>
    </div>

    <div v-if="editingBinding" class="binding-mask" @click.self="closeBindingEditor">
      <div class="binding-panel">
        <div class="binding-head">
          <div>
            <div class="binding-title">配置参数：{{ editingBinding.paramName }}</div>
            <div class="muted tiny">{{ editingBinding.paramType }}</div>
            <div v-if="editingParamMeta" class="meta-inline">
              <span class="meta-chip" :class="editingParamMeta.required ? 'required' : ''">{{ editingParamMeta.required ? "必填" : "可选" }}</span>
              <span class="meta-chip">{{ isListType(editingBinding.paramType) ? ('List<' + (editingParamMeta.listItemType || "java.lang.Object") + '>') : (isObjectLikeType(editingBinding.paramType) ? "对象" : "基础类型") }}</span>
              <span v-if="editingParamMeta.schema" class="meta-chip">属性 {{ editingParamTypeProperties.length }}</span>
            </div>
            <div v-if="editingParamMeta?.description" class="muted tiny">{{ editingParamMeta.description }}</div>
          </div>
          <button type="button" class="btn mini" @click="closeBindingEditor">关闭</button>
        </div>

        <div class="binding-content">
          <div class="type-prop-panel">
            <div class="type-prop-head">
              <div class="tool-title">类型属性</div>
              <div class="muted tiny" v-if="editingParamMeta?.schema">来自 Schema 的只读提示</div>
            </div>
            <div v-if="editingParamTypeProperties.length > 0" class="type-prop-list">
              <div v-for="row in editingParamTypeProperties" :key="row.path + '_' + row.depth" class="type-prop-row" :style="{ paddingLeft: (Math.min(row.depth, 5) * 12) + 'px' }">
                <div class="type-prop-path">{{ row.path }}</div>
                <div class="type-prop-type">{{ row.typeLabel }}</div>
                <div class="type-prop-required">{{ row.required ? "必填" : "可选" }}</div>
                <div class="type-prop-desc">{{ row.description || "-" }}</div>
                <div class="type-prop-constraints">{{ row.constraintText || "-" }}</div>
              </div>
            </div>
            <div v-else class="muted tiny">当前无可展开 Schema，请使用模板 JSON 或手工添加映射。</div>
          </div>

          <div class="preset-row">
            <div class="muted tiny">场景模板</div>
            <div class="preset-actions">
              <button type="button" class="btn mini" @click="applyBindingPreset(editingBinding, 'ctx')">常规取值</button>
              <button type="button" class="btn mini" @click="applyBindingPreset(editingBinding, 'const')">常量默认</button>
              <button type="button" class="btn mini" @click="applyBindingPreset(editingBinding, 'tempVar')">临时变量</button>
              <button
                type="button"
                class="btn mini"
                :disabled="!canUseObjectBuilder(editingBinding)"
                @click="applyBindingPreset(editingBinding, 'objectBuilder')"
              >
                对象构造
              </button>
            </div>
          </div>

          <select v-if="canUseObjectBuilder(editingBinding)" class="input" v-model="editingBinding.source.mode">
            <option value="objectBuilder">对象构造器</option>
            <option value="direct">直接取值</option>
          </select>

          <template v-if="editingBinding.source.mode === 'direct'">
            <select class="input" v-model="editingBinding.source.kind">
              <option value="ctx">来源：ctx/request</option>
              <option value="const">来源：常量</option>
              <option value="tempVar">来源：临时变量</option>
            </select>
            <template v-if="editingBinding.source.kind === 'ctx'">
              <SourcePathInput
                :model-value="editingBinding.source.path"
                :options="sourcePathOptions"
                placeholder="request.body.userId / request.query.pageNo"
                @update:model-value="editingBinding.source.path = $event"
              />
            </template>
            <textarea v-if="editingBinding.source.kind === 'const'" class="input textarea mini-textarea" v-model="editingBinding.source.constValue" placeholder='常量值，例如 "abc" / 100 / {"id":"u1"} / [{"id":"1"}]'></textarea>
            <template v-if="editingBinding.source.kind === 'tempVar'">
              <input class="input" v-model="editingBinding.source.tempKey" :list="tempDataListId" placeholder="变量 Key，例如 userId" />
              <datalist :id="tempDataListId"><option v-for="key in tempKeys" :key="key" :value="key" /></datalist>
            </template>
          </template>

          <template v-else>
            <div class="tool-block">
              <div class="tool-title">对象模板与字段生成</div>
              <textarea class="input textarea" v-model="objectTemplateText" placeholder='模板 JSON，例如 {"profile":{"name":"","age":0}}'></textarea>
              <div class="row-grid">
                <button type="button" class="btn mini" @click="buildFieldsFromParamType(editingBinding)">按类型生成字段</button>
                <button type="button" class="btn mini" @click="buildFieldsFromTemplate(editingBinding)">按模板生成字段</button>
              </div>
              <div class="muted tiny">这里只做字段映射初始化，后续可逐字段调整来源。</div>
              <div v-if="objectTemplateError" class="plan-error">{{ objectTemplateError }}</div>
            </div>

            <div class="object-builder">
              <div class="object-builder-head">
                <div class="tool-title">字段映射</div>
                <button type="button" class="btn mini" @click="addObjectField(editingBinding)">+ 字段</button>
              </div>
              <div v-for="(field, fieldIndex) in editingBinding.source.objectFields" :key="field.id" class="object-field-card">
                <div class="object-field-head">
                  <div class="muted tiny">字段 {{ fieldIndex + 1 }}</div>
                  <button type="button" class="btn mini" @click="removeObjectField(editingBinding, fieldIndex)">删除</button>
                </div>
                <input class="input" v-model="field.fieldPath" placeholder="目标字段路径，例如 profile.name / address.city / tags" />
                <select class="input" v-model="field.source.kind">
                  <option value="ctx">来源：ctx/request</option>
                  <option value="const">来源：常量</option>
                  <option value="tempVar">来源：临时变量</option>
                </select>
                <template v-if="field.source.kind === 'ctx'">
                  <SourcePathInput
                    :model-value="field.source.path"
                    :options="sourcePathOptions"
                    placeholder="request.body.xxx / request.query.xxx"
                    @update:model-value="field.source.path = $event"
                  />
                </template>
                <textarea v-if="field.source.kind === 'const'" class="input textarea mini-textarea" v-model="field.source.constValue" placeholder='常量值，例如 "abc" / 123 / {"k":"v"} / [1,2]'></textarea>
                <input v-if="field.source.kind === 'tempVar'" class="input" v-model="field.source.tempKey" :list="tempDataListId" placeholder="变量 Key，例如 userId" />
              </div>
            </div>
          </template>

          <div class="muted tiny">若字段依赖服务调用，请先在临时变量中计算，再在此引用。</div>
          <div v-if="getBindingErrors(editingBinding).length > 0" class="plan-errors"><div v-for="msg in getBindingErrors(editingBinding)" :key="msg" class="plan-error">{{ msg }}</div></div>
          <div v-if="getBindingWarnings(editingBinding).length > 0" class="plan-warnings"><div v-for="msg in getBindingWarnings(editingBinding)" :key="msg" class="plan-warning">{{ msg }}</div></div>
        </div>

        <div class="binding-foot"><button type="button" class="btn" @click="closeBindingEditor">完成</button></div>
      </div>
    </div>

    <div v-if="showPicker" class="picker-mask" @click.self="closePicker">
      <div class="picker-panel">
        <div class="picker-head">
          <div class="picker-title">选择服务方法</div>
          <button type="button" class="btn mini" @click="closePicker">关闭</button>
        </div>
        <input class="input" v-model="searchKeyword" placeholder="搜索 serviceBean / method / signature" />
        <div class="picker-content">
          <div v-if="loading" class="muted tiny">正在加载服务目录...</div>
          <div v-else-if="filteredOptions.length === 0" class="muted tiny">未匹配到方法</div>
          <button v-for="item in filteredOptions" :key="item.optionKey" type="button" class="option-item" :class="{ active: item.optionKey === selectedOptionKey }" @click="selectedOptionKey = item.optionKey">
            <div class="option-main">{{ item.serviceBean }}.{{ item.methodName }}</div>
            <div class="option-sign">{{ item.methodSignature }}</div>
            <div class="option-meta">{{ item.params.length }} 个参数 - 返回 {{ item.returnType }}</div>
          </button>
        </div>
        <div class="picker-foot">
          <div class="muted tiny" v-if="selectedOption">已选择：{{ selectedOption.serviceBean }}.{{ selectedOption.methodSignature }}</div>
          <button type="button" class="btn" :disabled="!selectedOption" @click="applySelectedMethod">应用方法</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.service-call-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}

.service-call-editor .input {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.editor-head,
.binding-head,
.binding-foot,
.picker-head,
.picker-foot,
.object-builder-head,
.object-field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.method-pill {
  font-size: 12px;
  line-height: 1.4;
  color: #0b3b8a;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.25);
  border-radius: 8px;
  padding: 6px 8px;
  word-break: break-word;
}

.method-pill.method-missing {
  color: #92400e;
  background: #fff7ed;
  border-color: #f59e0b;
}

.warn {
  font-size: 12px;
  color: #b45309;
  background: #fff7ed;
  border: 1px solid #f59e0b;
  border-radius: 8px;
  padding: 6px 8px;
}

.arg-list,
.object-builder,
.binding-content,
.tool-block,
.plan-errors,
.plan-warnings {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preset-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  background: #fbfdff;
  padding: 8px;
}

.preset-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tool-block,
.object-field-card {
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  padding: 8px;
  background: #fbfdff;
}

.arg-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  padding: 8px;
  background: #fbfdff;
}

.arg-row-left {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
}

.arg-row-right {
  flex: 0 0 auto;
}

.arg-index {
  color: #64748b;
  font-size: 12px;
}

.arg-name {
  color: #0f172a;
  font-size: 12px;
  white-space: nowrap;
  font-weight: 600;
}

.arg-type {
  color: #64748b;
  font-size: 12px;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.meta-chip {
  font-size: 10px;
  line-height: 1.4;
  border-radius: 999px;
  padding: 2px 8px;
  color: #1d4ed8;
  border: 1px solid rgba(37, 99, 235, 0.25);
  background: rgba(219, 234, 254, 0.8);
}

.meta-chip.required {
  color: #991b1b;
  border-color: rgba(239, 68, 68, 0.35);
  background: rgba(254, 226, 226, 0.85);
}

.status-pill {
  font-size: 10px;
  border-radius: 999px;
  padding: 2px 8px;
  border: 1px solid rgba(37, 99, 235, 0.25);
  color: #0b3b8a;
  background: rgba(37, 99, 235, 0.08);
}

.status-pill.error {
  color: #b91c1c;
  border-color: rgba(239, 68, 68, 0.35);
  background: rgba(254, 242, 242, 0.8);
}

.status-pill.warn {
  color: #92400e;
  border-color: rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.85);
}

@media (max-width: 900px) {
  .arg-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .arg-row-right {
    width: 100%;
    display: flex;
    justify-content: flex-end;
  }
}

.binding-mask,
.picker-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.36);
  z-index: 1200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.binding-panel,
.picker-panel {
  width: min(920px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  background: #ffffff;
  border-radius: 12px;
  border: 1px solid #cbd5e1;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.2);
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.binding-title,
.picker-title,
.tool-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.binding-content,
.picker-content {
  min-height: 120px;
  overflow: auto;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 8px;
}

.picker-content {
  max-height: 52vh;
}

.option-item {
  width: 100%;
  text-align: left;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  padding: 8px;
  background: #fff;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.option-item:hover {
  border-color: #93c5fd;
  background: #f8fbff;
}

.option-item.active {
  border-color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
}

.option-main {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.option-sign {
  font-size: 12px;
  color: #334155;
  word-break: break-word;
}

.option-meta {
  font-size: 11px;
  color: #64748b;
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

.type-prop-panel {
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  padding: 8px;
  background: #f8fbff;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.type-prop-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.type-prop-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 180px;
  overflow: auto;
}

.type-prop-row {
  display: grid;
  grid-template-columns: minmax(140px, 1fr) minmax(160px, 1fr) 56px minmax(160px, 1fr) minmax(200px, 1.2fr);
  gap: 6px;
  align-items: start;
  font-size: 11px;
}

.type-prop-path {
  color: #0f172a;
  word-break: break-word;
}

.type-prop-type {
  color: #334155;
  word-break: break-all;
}

.type-prop-required {
  color: #64748b;
  text-align: right;
}

.type-prop-desc,
.type-prop-constraints {
  color: #475569;
  word-break: break-word;
}

.row-grid {
  display: grid;
  grid-template-columns: auto auto;
  gap: 6px;
}

.textarea {
  min-height: 84px;
  resize: vertical;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 11px;
}

.mini-textarea {
  min-height: 56px;
}

@media (max-width: 700px) {
  .binding-panel,
  .picker-panel {
    width: calc(100vw - 16px);
    max-height: calc(100vh - 16px);
    padding: 10px;
  }
  .row-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .type-prop-row {
    grid-template-columns: minmax(0, 1fr);
  }
  .type-prop-required {
    text-align: left;
  }

  .picker-foot,
  .binding-head,
  .picker-head {
    flex-wrap: wrap;
  }

  .picker-foot .muted,
  .method-pill {
    max-width: 100%;
    white-space: normal;
    word-break: break-word;
  }
}
</style>



