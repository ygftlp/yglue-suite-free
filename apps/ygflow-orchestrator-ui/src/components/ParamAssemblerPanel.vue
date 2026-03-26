<script setup lang="ts">
import { computed, ref, watch, type ComponentPublicInstance } from "vue"
import {
  api,
  type ParamAssemblerArgMeta,
  type ParamAssemblerContextResponse,
  type ParamAssemblerIssue,
} from "../api/client"
import ServiceCallEditor from "./ServiceCallEditor.vue"
import SourcePathInput from "./SourcePathInput.vue"

type InputMeta = {
  name?: string
  valueType?: string
  typeName?: string
  description?: string
  required?: boolean
  schema?: Record<string, any> | null
}

type SourceNode = {
  kind: "source"
  sourceType: "request" | "context" | "temp" | "nodeOutput" | "const" | "item"
  path?: string
  constValue?: string
}

type ObjectFieldNode = {
  path: string
  value: ValueNode
}

type ObjectNode = {
  kind: "object"
  fields: ObjectFieldNode[]
}

type ListNode = {
  kind: "list"
  source: SourceNode | CallNode
  item?: ValueNode
  ops?: Array<Record<string, any>>
}

type ListExpressionOp = {
  id: string
  op: "filter"
  expression: string
}

type CallArgNode = {
  name: string
  value: ValueNode
}

type CallNode = {
  kind: "call"
  callType: "service" | "http"
  fn?: string
  ref: Record<string, any>
  args: CallArgNode[]
  resultPath?: string
}

type ValueNode = SourceNode | ObjectNode | ListNode | CallNode | Record<string, any>

type ArgAstNode = {
  name: string
  javaType?: string
  required?: boolean
  value: ValueNode
}

type ParamAssemblerAst = {
  version: "param-ast/v1"
  args: ArgAstNode[]
  temps: TempAstNode[]
}

type ParamPlanSource = {
  kind: "ctx" | "const" | "tempVar" | "listPipeline"
  path?: string
  constValue?: string
  tempKey?: string
}

type TempAstNode = {
  key: string
  javaType?: string
  value: CallNode
}

type TempUsageInfo = {
  label: string
  path: string
}

type ServiceCallEditorModel = {
  fn?: string
  serviceRef?: Record<string, any> | null
  argBindings?: Array<Record<string, any>>
}

type ParamPlansPayload = {
  tempPlans: Array<Record<string, any>>
  argPlans: Array<Record<string, any>>
}

type ParamAssemblerInsight = {
  errors: string[]
  warnings: string[]
}

type SmartFillMode = "all" | "emptyOnly"

type ServiceCallTrace = {
  label: string
  fn: string
  args: number
  resultPath: string
}

type ValuePreviewItem = {
  name: string
  summary: string
  sourceRefs: string[]
  tempRefs: string[]
  serviceCalls: ServiceCallTrace[]
}

type IssueActionKind = "smart-fill-empty" | "smart-fill-all" | "rebuild-draft" | "focus"

type IssueAction = {
  detail: string
  actionLabel?: string
  actionKind?: IssueActionKind
}

const props = defineProps<{
  modelValue?: Record<string, any> | null
  projectKey?: string
  endpointId?: number
  methodKey?: string
  inputDefs?: InputMeta[] | null
  sourcePathOptions?: string[] | null
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: ParamAssemblerAst | null): void
  (e: "update:paramPlans", value: ParamPlansPayload): void
  (e: "insight-change", value: ParamAssemblerInsight): void
}>()

const AST_VERSION = "param-ast/v1"

const localAst = ref<ParamAssemblerAst>({
  version: AST_VERSION,
  args: [],
  temps: [],
})
const contextInfo = ref<ParamAssemblerContextResponse | null>(null)
const loadingContext = ref(false)
const loadingDraft = ref(false)
const validating = ref(false)
const draftError = ref("")
const issues = ref<ParamAssemblerIssue[]>([])
const hasPendingChanges = ref(false)
const lastDraftSignature = ref("")
const syncFromCode = ref(false)
const focusedIssueTarget = ref("")
const lastSmartFillSummary = ref("")
let requestSeq = 0
const argCardRefs = new Map<string, HTMLElement>()
const tempCardRefs = new Map<number, HTMLElement>()
const issueTargetRefs = new Map<string, HTMLElement>()

function toText(value: unknown): string {
  return typeof value === "string" ? value.trim() : ""
}

function firstNonBlank(...values: unknown[]): string {
  for (const value of values) {
    const text = toText(value)
    if (text) return text
  }
  return ""
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}

function uniqueTextList(items: string[]): string[] {
  const result: string[] = []
  const seen = new Set<string>()
  items.forEach((item) => {
    const text = toText(item)
    if (!text || seen.has(text)) return
    seen.add(text)
    result.push(text)
  })
  return result
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value)
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
  if (
    raw.startsWith("java.util.list")
    || raw.startsWith("list<")
    || raw.startsWith("java.util.set")
    || raw.startsWith("set<")
    || raw.endsWith("[]")
    || raw === "array"
  ) {
    return "list"
  }
  return raw
}

function isListType(typeName: string, schema?: Record<string, any> | null): boolean {
  if (schema && isRecord(schema.items)) return true
  if (toText(schema?.type).toLowerCase() === "array") return true
  return normalizeTypeToken(typeName) === "list"
}

function isPrimitiveType(typeName: string): boolean {
  return ["string", "boolean", "int", "long", "double", "float", "number"].includes(normalizeTypeToken(typeName))
}

function isObjectType(typeName: string, schema?: Record<string, any> | null): boolean {
  if (schema && isRecord(schema.properties)) return true
  if (toText(schema?.type).toLowerCase() === "object") return true
  const normalized = normalizeTypeToken(typeName)
  return Boolean(normalized) && normalized !== "list" && !isPrimitiveType(typeName)
}

function deriveListItemType(typeName: string, schema?: Record<string, any> | null): string {
  const text = toText(typeName)
  if (text.endsWith("[]")) return text.slice(0, -2).trim() || "java.lang.Object"
  const left = text.indexOf("<")
  const right = text.lastIndexOf(">")
  if (left >= 0 && right > left) {
    return text.slice(left + 1, right).split(",")[0].trim() || "java.lang.Object"
  }
  if (schema && isRecord(schema.items)) {
    return toText(schema.items["x-javaType"]) || toText(schema.items.javaType) || toText(schema.items.typeName) || toText(schema.items.type) || "java.lang.Object"
  }
  return "java.lang.Object"
}

function normalizeLeaf(value: string): string {
  return value.replace(/^\$\./, "").replace(/^\./, "").trim()
}

function buildFieldId(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`
}

function createListExpressionOp(op: "filter" = "filter", expression = ""): ListExpressionOp {
  return {
    id: buildFieldId(`list_${op}`),
    op,
    expression,
  }
}

function guessSourcePath(name: string): string {
  const normalized = normalizeLeaf(name).replace(/\[\]/g, "")
  return normalized ? `request.body.${normalized}` : "request.body.value"
}

function createSourceNode(sourceType: SourceNode["sourceType"] = "request", path = ""): SourceNode {
  return {
    kind: "source",
    sourceType,
    path,
    constValue: "",
  }
}

function createFieldNode(path = ""): ObjectFieldNode {
  return {
    path,
    value: createSourceNode("request", guessSourcePath(path)),
  }
}

function createCallNode(): CallNode {
  return {
    kind: "call",
    callType: "service",
    fn: "",
    ref: {},
    args: [],
    resultPath: "",
  }
}

function createTempNode(): TempAstNode {
  return {
    key: `temp_${Date.now()}_${Math.random().toString(36).slice(2, 7)}`,
    javaType: "",
    value: createCallNode(),
  }
}

function inferSourceTypeFromPath(path: string): SourceNode["sourceType"] {
  const raw = toText(path)
  if (!raw) return "request"
  if (raw.startsWith("request.")) return "request"
  if (raw.startsWith("context.") || raw.startsWith("ctx.")) return "context"
  if (raw.startsWith("temp.")) return "temp"
  if (raw.startsWith("nodeOutput.")) return "nodeOutput"
  if (raw.startsWith("item.") || raw.startsWith("$.")) return "item"
  return "request"
}

function flattenSchemaLeafPaths(schema: Record<string, any> | null | undefined, prefix = "", output: string[] = []): string[] {
  if (!schema || !isRecord(schema)) return output
  const properties = isRecord(schema.properties) ? schema.properties : null
  if (!properties) {
    if (prefix) output.push(prefix)
    return output
  }

  for (const [key, childRaw] of Object.entries(properties)) {
    const child = isRecord(childRaw) ? childRaw : null
    const nextPath = prefix ? `${prefix}.${key}` : key
    if (child && isRecord(child.properties)) {
      flattenSchemaLeafPaths(child, nextPath, output)
      continue
    }
    if (child && toText(child.type).toLowerCase() === "array") {
      const itemSchema = isRecord(child.items) ? child.items : null
      if (itemSchema && isRecord(itemSchema.properties)) {
        flattenSchemaLeafPaths(itemSchema, `${nextPath}[]`, output)
      } else {
        output.push(`${nextPath}[]`)
      }
      continue
    }
    output.push(nextPath)
  }
  return output
}

function buildArgMeta(input: InputMeta, index: number): ParamAssemblerArgMeta {
  const name = toText(input?.name) || `arg${index + 1}`
  return {
    name,
    javaType: toText(input?.typeName) || "",
    required: Boolean(input?.required),
    schema: isRecord(input?.schema) ? clone(input.schema) : null,
  }
}

const argMetas = computed<ParamAssemblerArgMeta[]>(() =>
  (Array.isArray(props.inputDefs) ? props.inputDefs : []).map((item, index) => buildArgMeta(item || {}, index)),
)

const argMetaByName = computed(() => new Map(argMetas.value.map((item) => [item.name, item])))

function defaultValueForArg(meta: ParamAssemblerArgMeta): ValueNode {
  const schema = isRecord(meta.schema) ? meta.schema : null
  if (isListType(meta.javaType || "", schema)) {
    const source = createSourceNode("request", guessSourcePath(meta.name))
    const itemSchema = schema && isRecord(schema.items) ? schema.items : null
    if (itemSchema && isRecord(itemSchema.properties)) {
      return {
        kind: "list",
        source,
        item: {
          kind: "object",
          fields: flattenSchemaLeafPaths(itemSchema).map((path) => createFieldNode(path)),
        },
        ops: [],
      }
    }
    return { kind: "list", source, ops: [] }
  }

  if (isObjectType(meta.javaType || "", schema)) {
    return {
      kind: "object",
      fields: flattenSchemaLeafPaths(schema).map((path) => createFieldNode(path)),
    }
  }

  return createSourceNode("request", guessSourcePath(meta.name))
}

function normalizeSourceNode(raw: unknown, fallbackPath: string): SourceNode {
  const sourceType = toText((raw as Record<string, any>)?.sourceType) as SourceNode["sourceType"]
  const allowedTypes: SourceNode["sourceType"][] = ["request", "context", "temp", "nodeOutput", "const", "item"]
  return {
    kind: "source",
    sourceType: allowedTypes.includes(sourceType) ? sourceType : "request",
    path: toText((raw as Record<string, any>)?.path) || fallbackPath,
    constValue: (raw as Record<string, any>)?.constValue ?? "",
  }
}

function normalizeCallNode(raw: unknown): CallNode {
  const value = isRecord(raw) ? raw : {}
  const argsRaw = Array.isArray(value.args) ? value.args : []
  return {
    kind: "call",
    callType: toText(value.callType).toLowerCase() === "http" ? "http" : "service",
    fn: toText(value.fn),
    ref: isRecord(value.ref) ? clone(value.ref) : {},
    resultPath: toText(value.resultPath),
    args: argsRaw.map((item) => ({
      name: toText(item?.name),
      value: normalizeNestedValueNode(item?.value),
    })),
  }
}

function normalizeNestedValueNode(raw: unknown): ValueNode {
  if (!isRecord(raw)) return createSourceNode("request", "")
  const kind = toText(raw.kind).toLowerCase()
  if (kind === "source") {
    return normalizeSourceNode(raw, "")
  }
  if (kind === "object") {
    const fieldsRaw = Array.isArray(raw.fields) ? raw.fields : []
    return {
      kind: "object",
      fields: fieldsRaw.map((field) => ({
        path: toText(field?.path),
        value: normalizeNestedValueNode(field?.value),
      })),
    }
  }
  if (kind === "call") {
    return normalizeCallNode(raw)
  }
  return normalizeSourceNode(raw, "")
}

function normalizeListOp(raw: unknown): ListExpressionOp | null {
  if (!isRecord(raw)) return null
  const op = toText(raw.op).toLowerCase()
  if (op !== "filter") return null
  return createListExpressionOp("filter", firstNonBlank(raw.expression, raw.exprText, raw.value))
}

function normalizeValueNode(raw: unknown, meta: ParamAssemblerArgMeta): ValueNode {
  if (!isRecord(raw)) return defaultValueForArg(meta)
  const kind = toText(raw.kind).toLowerCase()
  if (kind === "source") {
    return normalizeSourceNode(raw, guessSourcePath(meta.name))
  }
  if (kind === "object") {
    const fieldsRaw = Array.isArray(raw.fields) ? raw.fields : []
    return {
      kind: "object",
      fields: fieldsRaw.length > 0
        ? fieldsRaw.map((field) => ({
          path: toText(field?.path),
          value: normalizeSourceNode(field?.value, guessSourcePath(field?.path || meta.name)),
        }))
        : flattenSchemaLeafPaths(isRecord(meta.schema) ? meta.schema : null).map((path) => createFieldNode(path)),
    }
  }
  if (kind === "list") {
    const schema = isRecord(meta.schema) ? meta.schema : null
    const itemSchema = schema && isRecord(schema.items) ? schema.items : null
    const itemRaw = isRecord(raw.item) ? raw.item : null
    const itemNode = itemRaw && toText(itemRaw.kind).toLowerCase() === "object"
      ? {
        kind: "object",
        fields: Array.isArray(itemRaw.fields) && itemRaw.fields.length > 0
          ? itemRaw.fields.map((field) => ({
            path: toText(field?.path),
            value: normalizeSourceNode(field?.value, guessSourcePath(field?.path || meta.name)),
          }))
          : flattenSchemaLeafPaths(itemSchema).map((path) => createFieldNode(path)),
      }
      : undefined
    return {
      kind: "list",
      source: isRecord(raw.source) && toText(raw.source.kind).toLowerCase() === "call"
        ? normalizeCallNode(raw.source)
        : normalizeSourceNode(raw.source, guessSourcePath(meta.name)),
      item: itemNode,
      ops: Array.isArray(raw.ops)
        ? raw.ops
          .map((item) => normalizeListOp(item) ?? (isRecord(item) ? clone(item) : null))
          .filter(Boolean) as Array<Record<string, any>>
        : [],
    }
  }
  if (kind === "call") {
    return normalizeCallNode(raw)
  }
  return clone(raw) as ValueNode
}

function normalizeAst(raw: unknown): ParamAssemblerAst {
  const source = isRecord(raw) ? raw : {}
  const argsRaw = Array.isArray(source.args) ? source.args : []
  const byName = new Map<string, Record<string, any>>()
  argsRaw.forEach((item) => {
    const name = toText(item?.name)
    if (name) byName.set(name, item)
  })

  return {
    version: AST_VERSION,
    temps: Array.isArray(source.temps)
      ? source.temps.map((temp) => ({
        key: toText(temp?.key) || createTempNode().key,
        javaType: toText(temp?.javaType),
        value: normalizeCallNode(temp?.value),
      }))
      : [],
    args: argMetas.value.map((meta, index) => {
      const rawArg = byName.get(meta.name) || (isRecord(argsRaw[index]) ? argsRaw[index] : {})
      return {
        name: meta.name,
        javaType: toText(rawArg?.javaType) || meta.javaType || "",
        required: rawArg?.required ?? meta.required ?? false,
        value: normalizeValueNode(rawArg?.value, meta),
      }
    }),
  }
}

function applyAst(next: ParamAssemblerAst, options?: { emitChange?: boolean; pending?: boolean; issues?: ParamAssemblerIssue[] }) {
  syncFromCode.value = true
  localAst.value = normalizeAst(next)
  syncFromCode.value = false
  if (options?.issues) issues.value = clone(options.issues)
  hasPendingChanges.value = Boolean(options?.pending)
  if (options?.emitChange !== false) emitCurrentState()
}

function emitCurrentState() {
  const ast = clone(localAst.value)
  emit("update:modelValue", ast)
  emit("update:paramPlans", compileAstToParamPlans(ast))
}

function uniquePaths(items: string[]): string[] {
  return Array.from(new Set(items.map((item) => toText(item)).filter(Boolean))).sort((a, b) => a.localeCompare(b))
}

const contextSourcePaths = computed(() =>
  uniquePaths([
    ...(contextInfo.value?.sourcePaths || []),
    ...((Array.isArray(props.sourcePathOptions) ? props.sourcePathOptions : []).map((item) => String(item || ""))),
  ]),
)

function findArgMeta(name: string): ParamAssemblerArgMeta {
  return argMetaByName.value.get(name) || { name }
}

function hasNestedArray(schema?: Record<string, any> | null): boolean {
  if (!schema || !isRecord(schema)) return false
  if (toText(schema.type).toLowerCase() === "array") {
    const itemSchema = isRecord(schema.items) ? schema.items : null
    if (itemSchema && toText(itemSchema.type).toLowerCase() === "array") return true
    return hasNestedArray(itemSchema)
  }
  if (isRecord(schema.properties)) {
    return Object.values(schema.properties).some((item) => hasNestedArray(isRecord(item) ? item : null))
  }
  return false
}

function hasDynamicMap(schema?: Record<string, any> | null): boolean {
  if (!schema || !isRecord(schema)) return false
  if (schema.additionalProperties !== undefined && schema.additionalProperties !== false) return true
  if (isRecord(schema.properties)) {
    return Object.values(schema.properties).some((item) => hasDynamicMap(isRecord(item) ? item : null))
  }
  if (isRecord(schema.items)) return hasDynamicMap(schema.items)
  return false
}

const unsupportedNotes = computed(() => {
  const notes = [
    "一期不支持 groupBy / reduce / flatten。",
    "一期不支持嵌套数组的完整可视化装配。",
    "一期不支持动态 Map key 装配。",
  ]
  const schemaDriven = argMetas.value.flatMap((meta) => {
    const current: string[] = []
    if (hasNestedArray(meta.schema || null)) current.push(`参数 ${meta.name} 包含嵌套数组，建议切到高级模式处理。`)
    if (hasDynamicMap(meta.schema || null)) current.push(`参数 ${meta.name} 包含动态 Map 结构，建议切到高级模式处理。`)
    return current
  })
  return [...notes, ...schemaDriven]
})

const tempPathOptions = computed(() =>
  uniquePaths((localAst.value.temps || []).map((item) => `temp.${toText(item.key)}`).filter(Boolean)),
)

const nonItemContextSourcePaths = computed(() =>
  contextSourcePaths.value.filter((item) => !item.startsWith("item.") && !item.startsWith("$.") && !item.startsWith("temp.")),
)

const itemContextSourcePaths = computed(() =>
  contextSourcePaths.value.filter((item) => item.startsWith("item.") || item.startsWith("$.")),
)

function normalizeMatchToken(value: string): string {
  return toText(value)
    .replace(/^request\.(body|query|path)\./, "")
    .replace(/^request\./, "")
    .replace(/^context\./, "")
    .replace(/^ctx\./, "")
    .replace(/^nodeOutput\./, "")
    .replace(/^temp\./, "")
    .replace(/^item\./, "")
    .replace(/^\$\./, "")
    .replace(/\[\]/g, "")
    .replace(/\[\*\]/g, "")
    .toLowerCase()
}

function tailToken(value: string): string {
  const normalized = normalizeMatchToken(value)
  if (!normalized) return ""
  const parts = normalized.split(".").filter(Boolean)
  return parts[parts.length - 1] || normalized
}

function scoreCandidate(target: string, candidate: string): number {
  const normalizedTarget = normalizeMatchToken(target)
  const normalizedCandidate = normalizeMatchToken(candidate)
  if (!normalizedTarget || !normalizedCandidate) return Number.NEGATIVE_INFINITY
  if (normalizedTarget === normalizedCandidate) return 300
  if (normalizedCandidate.endsWith(`.${normalizedTarget}`)) return 220
  if (normalizedTarget.endsWith(`.${normalizedCandidate}`)) return 180
  const targetTail = tailToken(normalizedTarget)
  const candidateTail = tailToken(normalizedCandidate)
  let score = 0
  if (targetTail && targetTail === candidateTail) score += 120
  if (normalizedCandidate.includes(normalizedTarget)) score += 60
  if (normalizedTarget.includes(normalizedCandidate)) score += 40
  return score
}

function findBestCandidate(target: string, candidates: string[], minScore = 1): string {
  let best = ""
  let bestScore = Number.NEGATIVE_INFINITY
  candidates.forEach((candidate) => {
    const score = scoreCandidate(target, candidate)
    if (score > bestScore) {
      bestScore = score
      best = candidate
    }
  })
  return bestScore >= minScore ? best : ""
}

function isSourceNodeBlank(source: SourceNode): boolean {
  if (source.sourceType === "const") return !toText(source.constValue)
  return !toText(source.path)
}

function applySuggestedSourcePath(source: SourceNode, candidate: string): boolean {
  const nextPath = toText(candidate)
  if (!nextPath) return false
  const nextType = inferSourceTypeFromPath(nextPath)
  if (!nextType || nextType === "const") return false
  const changed = source.sourceType !== nextType || toText(source.path) !== nextPath || toText(source.constValue)
  source.sourceType = nextType
  source.path = nextPath
  source.constValue = ""
  return changed
}

function smartFillSourceNode(
  source: SourceNode,
  target: string,
  options: { emptyOnly: boolean; preferItem?: boolean } = { emptyOnly: true },
): boolean {
  if (source.sourceType === "const") return false
  if (options.emptyOnly && !isSourceNodeBlank(source)) return false

  if (options.preferItem) {
    const itemCandidate = findBestCandidate(target, itemContextSourcePaths.value)
    if (itemCandidate && applySuggestedSourcePath(source, itemCandidate)) return true
  }

  const pathCandidate = findBestCandidate(target, nonItemContextSourcePaths.value)
  if (pathCandidate && applySuggestedSourcePath(source, pathCandidate)) return true

  const tempCandidate = findBestCandidate(target, tempPathOptions.value, 80)
  if (tempCandidate && applySuggestedSourcePath(source, tempCandidate)) return true

  return false
}

function smartFillObjectFields(fields: ObjectFieldNode[], emptyOnly: boolean, preferItem = false): number {
  return fields.reduce((count, field) => {
    const source = getFieldSourceValue(field)
    const target = toText(field.path)
    if (!target) return count
    return count + (smartFillSourceNode(source, target, { emptyOnly, preferItem }) ? 1 : 0)
  }, 0)
}

function smartFillArg(arg: ArgAstNode, emptyOnly: boolean): number {
  const kind = toText(arg.value?.kind).toLowerCase()
  if (kind === "source") {
    return smartFillSourceNode(getArgSourceValue(arg), arg.name, { emptyOnly }) ? 1 : 0
  }
  if (kind === "object") {
    return smartFillObjectFields(getObjectFieldList(arg), emptyOnly)
  }
  if (kind !== "list") return 0

  let changed = 0
  if (getListSourceMode(arg) === "source") {
    changed += smartFillSourceNode(getListSourceValue(arg), arg.name, { emptyOnly }) ? 1 : 0
  }
  if (canShowItemBuilder(arg.name)) {
    changed += smartFillObjectFields(getListItemFieldList(arg), emptyOnly, true)
  } else {
    changed += smartFillSourceNode(getPrimitiveListItemSource(arg), arg.name, { emptyOnly, preferItem: true }) ? 1 : 0
  }
  return changed
}

async function applySmartFill(mode: SmartFillMode) {
  if (!props.projectKey || argMetas.value.length === 0) {
    lastSmartFillSummary.value = "当前没有可推荐的参数装配上下文。"
    return
  }
  validating.value = true
  draftError.value = ""
  try {
    const response = await api.suggestParamAssemblerAst(props.projectKey, {
      ast: clone(localAst.value),
      args: argMetas.value,
      sourcePaths: contextSourcePaths.value,
      mode,
    })
    applyAst(response.ast as ParamAssemblerAst, {
      emitChange: true,
      pending: false,
      issues: response.issues || [],
    })
    const touchedArgs = Array.isArray(response.touchedArgs)
      ? response.touchedArgs.map((item) => toText(item)).filter(Boolean)
      : []
    const touchedSummary = touchedArgs.length > 0
      ? `涉及参数：${touchedArgs.slice(0, 4).join("、")}${touchedArgs.length > 4 ? ` 等 ${touchedArgs.length} 个` : ""}。`
      : ""
    const baseSummary = toText(response.summary)
    lastSmartFillSummary.value = baseSummary
      ? `${baseSummary}${touchedSummary ? ` ${touchedSummary}` : ""}`
      : `${mode === "emptyOnly" ? "已自动补齐空白映射" : "已重算推荐映射"}${response.updatedCount > 0 ? `，共更新 ${response.updatedCount} 处。` : "。"}${touchedSummary}`
  } catch (error) {
    draftError.value = error instanceof Error ? error.message : "参数自动补齐失败。"
  } finally {
    validating.value = false
  }
}

function toAstSourceNodeFromLegacy(raw: any): SourceNode {
  const kind = toText(raw?.kind)
  if (kind === "const") {
    return {
      kind: "source",
      sourceType: "const",
      constValue: raw?.constValue ?? "",
      path: "",
    }
  }
  if (kind === "tempVar") {
    const tempKey = toText(raw?.tempKey)
    return {
      kind: "source",
      sourceType: "temp",
      path: tempKey ? `temp.${tempKey}` : "",
      constValue: "",
    }
  }
  const path = toText(raw?.path)
  return {
    kind: "source",
    sourceType: inferSourceTypeFromPath(path),
    path,
    constValue: "",
  }
}

function toAstValueNodeFromServiceBinding(raw: any): ValueNode {
  const mode = toText(raw?.mode)
  if (mode === "objectBuilder" && Array.isArray(raw?.objectFields)) {
    return {
      kind: "object",
      fields: raw.objectFields.map((field: any) => ({
        path: toText(field?.fieldPath),
        value: toAstSourceNodeFromLegacy(field),
      })),
    }
  }
  return toAstSourceNodeFromLegacy(raw)
}

function callNodeToEditorModel(call: CallNode): ServiceCallEditorModel {
  const args = Array.isArray(call.args) ? call.args : []
  return {
    fn: call.fn || "",
    serviceRef: isRecord(call.ref) ? clone(call.ref) : null,
    argBindings: args.map((item, index) => {
      const sourceValue = item.value
      if (isRecord(sourceValue) && toText(sourceValue.kind).toLowerCase() === "object") {
        return {
          id: buildFieldId(`temp_binding_${index}`),
          paramName: toText(item.name),
          paramType: "",
          source: {
            kind: "ctx",
            path: "",
            constValue: "",
            tempKey: "",
            mode: "objectBuilder",
            objectFields: Array.isArray((sourceValue as ObjectNode).fields)
              ? (sourceValue as ObjectNode).fields.map((field, fieldIndex) => {
                const sourceNode = normalizeSourceNode(field.value, "")
                return {
                  id: buildFieldId(`temp_obj_${index}_${fieldIndex}`),
                  fieldPath: toText(field.path),
                  kind: sourceNode.sourceType === "const"
                    ? "const"
                    : sourceNode.sourceType === "temp"
                      ? "tempVar"
                      : "ctx",
                  path: sourceNode.sourceType === "temp" ? "" : toText(sourceNode.path),
                  constValue: sourceNode.constValue ?? "",
                  tempKey: sourceNode.sourceType === "temp" ? toText(sourceNode.path).replace(/^temp\./, "") : "",
                }
              })
              : [],
          },
        }
      }
      const sourceNode = normalizeSourceNode(sourceValue, "")
      return {
        id: buildFieldId(`temp_binding_${index}`),
        paramName: toText(item.name),
        paramType: "",
        source: {
          kind: sourceNode.sourceType === "const"
            ? "const"
            : sourceNode.sourceType === "temp"
              ? "tempVar"
              : "ctx",
          path: sourceNode.sourceType === "temp" ? "" : toText(sourceNode.path),
          constValue: sourceNode.constValue ?? "",
          tempKey: sourceNode.sourceType === "temp" ? toText(sourceNode.path).replace(/^temp\./, "") : "",
          mode: "direct",
          objectFields: [],
        },
      }
    }),
  }
}

function editorModelToCallNode(model: ServiceCallEditorModel, current: CallNode): CallNode {
  const argBindings = Array.isArray(model?.argBindings) ? model.argBindings : []
  return {
    kind: "call",
    callType: "service",
    fn: toText(model?.fn),
    ref: isRecord(model?.serviceRef) ? clone(model.serviceRef) : {},
    resultPath: toText(current?.resultPath),
    args: argBindings.map((binding: any) => ({
      name: toText(binding?.paramName),
      value: toAstValueNodeFromServiceBinding(binding?.source || {}),
    })),
  }
}

function compileSourceToLegacy(source: SourceNode, scope: "arg" | "listItem" = "arg"): ParamPlanSource {
  if (source.sourceType === "const") {
    return {
      kind: "const",
      constValue: source.constValue ?? "",
    }
  }
  if (source.sourceType === "temp") {
    const path = toText(source.path)
    return {
      kind: "tempVar",
      tempKey: path.startsWith("temp.") ? path.slice(5) : path,
    }
  }
  if (source.sourceType === "item" && scope === "listItem") {
    const path = toText(source.path)
    return {
      kind: "ctx",
      path: path.startsWith("$.") ? path : `$.${path.replace(/^item\./, "")}`,
    }
  }
  return {
    kind: "ctx",
    path: toText(source.path),
  }
}

function stripListItemPrefix(path: string): string {
  const raw = toText(path)
  if (!raw) return ""
  if (raw.startsWith("$.")) return raw.slice(2)
  if (raw.startsWith("item.")) return raw.slice(5)
  if (raw === "item") return ""
  return raw
}

function compileListInputValueToLegacy(value: SourceNode | CallNode): Record<string, any> {
  if (isRecord(value) && toText(value.kind).toLowerCase() === "call") {
    return compileCallSourceToLegacy(value as CallNode)
  }
  return compileSourceToLegacy(value as SourceNode)
}

function compileListNodeToPlan(arg: ArgAstNode, value: ListNode): Record<string, any> {
  const itemMeta = findArgMeta(arg.name)
  const listSteps: Array<Record<string, any>> = []
  const composeFields = value.item && isRecord(value.item) && toText(value.item.kind).toLowerCase() === "object"
    ? (Array.isArray((value.item as ObjectNode).fields) ? (value.item as ObjectNode).fields : []).map((field) => ({
      id: buildFieldId("lf"),
      targetField: toText(field.path),
      typeHint: "",
      source: compileSourceToLegacy(normalizeSourceNode(field.value, ""), "listItem"),
    }))
    : []

  if (value.item && isRecord(value.item) && toText(value.item.kind).toLowerCase() === "source") {
    const itemSource = normalizeSourceNode(value.item, "")
    if (itemSource.sourceType === "item") {
      const itemPath = stripListItemPrefix(itemSource.path || "")
      if (itemPath) {
        listSteps.push({
          op: "map",
          exprText: itemPath,
        })
      }
    }
  }

  ;(Array.isArray(value.ops) ? value.ops : []).forEach((raw) => {
    const op = normalizeListOp(raw)
    if (!op) return
    const expression = toText(op.expression)
    if (!expression) return
    listSteps.push({
      op: "filter",
      exprText: expression,
    })
  })

  const plan: Record<string, any> = {
    target: arg.name,
    typeHint: arg.javaType || itemMeta.javaType || "",
    source: {
      kind: "listPipeline",
      path: "",
      constValue: "",
      tempKey: "",
    },
    listInput: compileListInputValueToLegacy(value.source),
  }

  if (composeFields.length > 0) {
    plan.listCompose = {
      itemTypeHint: deriveListItemType(arg.javaType || itemMeta.javaType || "", itemMeta.schema || null),
      fields: composeFields,
    }
  }
  if (listSteps.length > 0) {
    plan.listSteps = listSteps
  }
  return plan
}

function compileCallArgValueToLegacy(value: ValueNode): Record<string, any> {
  const kind = toText((value as Record<string, any>)?.kind).toLowerCase()
  if (kind === "object") {
    const fields = Array.isArray((value as ObjectNode).fields) ? (value as ObjectNode).fields : []
    return {
      kind: "ctx",
      path: "",
      constValue: "",
      tempKey: "",
      mode: "objectBuilder",
      objectFields: fields.map((field) => {
        const sourceNode = normalizeSourceNode(field.value, "")
        return {
          id: buildFieldId("temp_obj"),
          fieldPath: toText(field.path),
          kind: sourceNode.sourceType === "const"
            ? "const"
            : sourceNode.sourceType === "temp"
              ? "tempVar"
              : "ctx",
          path: sourceNode.sourceType === "temp" ? "" : toText(sourceNode.path),
          constValue: sourceNode.constValue ?? "",
          tempKey: sourceNode.sourceType === "temp" ? toText(sourceNode.path).replace(/^temp\./, "") : "",
        }
      }),
    }
  }
  return compileSourceToLegacy(normalizeSourceNode(value, ""))
}

function compileCallSourceToLegacy(call: CallNode): Record<string, any> {
  const ref = isRecord(call.ref) ? clone(call.ref) : {}
  return {
    kind: "serviceCall",
    serviceCall: {
      fn: toText(call.fn),
      serviceRef: ref,
      argBindings: Array.isArray(call.args)
        ? call.args.map((arg) => ({
          name: toText(arg.name),
          source: compileCallArgValueToLegacy(arg.value),
        }))
        : [],
    },
    serviceResultPath: toText(call.resultPath),
  }
}

function compileTempPlan(temp: TempAstNode): Record<string, any> {
  const ref = isRecord(temp.value?.ref) ? temp.value.ref : {}
  return {
    key: toText(temp.key),
    typeHint: toText(temp.javaType) || toText(ref.returnType),
    source: {
      kind: "serviceCall",
      serviceCall: {
        fn: toText(temp.value?.fn),
        serviceRef: clone(ref),
        argBindings: Array.isArray(temp.value?.args)
          ? temp.value.args.map((arg) => ({
            name: toText(arg.name),
            source: compileCallArgValueToLegacy(arg.value),
          }))
          : [],
      },
      serviceResultPath: toText(temp.value?.resultPath),
    },
  }
}

function compileAstToParamPlans(ast: ParamAssemblerAst): ParamPlansPayload {
  const argPlans: Array<Record<string, any>> = []
  for (const arg of ast.args || []) {
    const kind = toText(arg.value?.kind).toLowerCase()
    if (kind === "source") {
      argPlans.push({
        target: arg.name,
        typeHint: arg.javaType || "",
        source: compileSourceToLegacy(arg.value as SourceNode),
      })
      continue
    }
    if (kind === "object") {
      const fields = Array.isArray((arg.value as ObjectNode).fields) ? (arg.value as ObjectNode).fields : []
      fields.forEach((field) => {
        argPlans.push({
          target: `${arg.name}.${toText(field.path)}`,
          typeHint: arg.javaType || "",
          source: compileSourceToLegacy(normalizeSourceNode(field.value, "")),
        })
      })
      continue
    }
    if (kind === "list") {
      argPlans.push(compileListNodeToPlan(arg, arg.value as ListNode))
      continue
    }
  }

  return {
    tempPlans: (ast.temps || [])
      .filter((item) => toText(item?.key))
      .map((item) => compileTempPlan(item)),
    argPlans,
  }
}

async function loadContext() {
  if (!props.projectKey) {
    contextInfo.value = null
    return null
  }
  const currentSeq = ++requestSeq
  loadingContext.value = true
  draftError.value = ""
  try {
    const response = await api.getParamAssemblerContext(props.projectKey, { endpointId: props.endpointId || null })
    if (currentSeq !== requestSeq) return null
    contextInfo.value = response
    return response
  } catch (error) {
    if (currentSeq !== requestSeq) return null
    draftError.value = error instanceof Error ? error.message : "加载参数装配上下文失败。"
    contextInfo.value = null
    return null
  } finally {
    if (currentSeq === requestSeq) loadingContext.value = false
  }
}

function buildDraftSignature(): string {
  return JSON.stringify({
    projectKey: props.projectKey || "",
    endpointId: props.endpointId || 0,
    methodKey: props.methodKey || "",
    args: argMetas.value.map((item) => ({
      name: item.name,
      javaType: item.javaType || "",
      required: Boolean(item.required),
    })),
  })
}

async function buildDraft(force = false) {
  if (!props.projectKey || !props.methodKey || argMetas.value.length === 0) {
    applyAst({ version: AST_VERSION, args: [], temps: [] }, { emitChange: true, pending: false, issues: [] })
    return
  }

  const signature = buildDraftSignature()
  if (!force && lastDraftSignature.value === signature && localAst.value.args.length === argMetas.value.length) {
    return
  }

  loadingDraft.value = true
  draftError.value = ""
  const context = contextInfo.value || await loadContext()
  try {
    const response = await api.buildParamAssemblerDraft(props.projectKey, {
      args: argMetas.value,
      sourcePaths: uniquePaths([
        ...(context?.sourcePaths || []),
        ...((Array.isArray(props.sourcePathOptions) ? props.sourcePathOptions : []).map((item) => String(item || ""))),
      ]),
    })
    lastDraftSignature.value = signature
    const nextAst = clone(response.ast as ParamAssemblerAst)
    nextAst.temps = clone(localAst.value.temps || [])
    applyAst(nextAst, { emitChange: true, pending: false, issues: response.issues })
    await validateAst()
  } catch (error) {
    draftError.value = error instanceof Error ? error.message : "生成参数装配草稿失败。"
  } finally {
    loadingDraft.value = false
  }
}

async function validateAst() {
  if (!props.projectKey || argMetas.value.length === 0) {
    issues.value = []
    return
  }
  validating.value = true
  draftError.value = ""
  try {
    const response = await api.validateParamAssemblerAst(props.projectKey, {
      ast: clone(localAst.value),
      args: argMetas.value,
    })
    issues.value = response.issues || []
    hasPendingChanges.value = false
  } catch (error) {
    draftError.value = error instanceof Error ? error.message : "参数装配校验失败。"
  } finally {
    validating.value = false
  }
}

function replaceObjectFields(argName: string, fieldPaths: string[]) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "object") return
  ;(arg.value as ObjectNode).fields = fieldPaths.map((path) => createFieldNode(path))
}

function replaceListItemFields(argName: string, fieldPaths: string[]) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  listValue.item = {
    kind: "object",
    fields: fieldPaths.map((path) => ({
      path,
      value: createSourceNode("item", path.replace(/\[\]/g, "")),
    })),
  }
}

function rebuildObjectFromSchema(argName: string) {
  const meta = findArgMeta(argName)
  replaceObjectFields(argName, flattenSchemaLeafPaths(meta.schema || null))
}

function rebuildListItemFromSchema(argName: string) {
  const meta = findArgMeta(argName)
  const itemSchema = isRecord(meta.schema?.items) ? meta.schema.items : null
  replaceListItemFields(argName, flattenSchemaLeafPaths(itemSchema))
}

function addObjectField(argName: string) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "object") return
  ;(arg.value as ObjectNode).fields.push(createFieldNode(""))
}

function removeObjectField(argName: string, index: number) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "object") return
  const fields = (arg.value as ObjectNode).fields
  if (fields.length <= 1) return
  fields.splice(index, 1)
}

function addListItemField(argName: string) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  if (!listValue.item || !isRecord(listValue.item) || toText(listValue.item.kind).toLowerCase() !== "object") {
    listValue.item = { kind: "object", fields: [createFieldNode("")] }
    return
  }
  ;(listValue.item as ObjectNode).fields.push({
    path: "",
    value: createSourceNode("item", ""),
  })
}

function removeListItemField(argName: string, index: number) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  if (!listValue.item || !isRecord(listValue.item) || toText(listValue.item.kind).toLowerCase() !== "object") return
  const fields = (listValue.item as ObjectNode).fields
  if (fields.length <= 1) return
  fields.splice(index, 1)
}

function canShowPrimitiveListItemMapper(argName: string): boolean {
  return !canShowItemBuilder(argName)
}

function getPrimitiveListItemSource(arg: ArgAstNode): SourceNode {
  const listValue = getListValue(arg)
  if (!listValue.item || !isRecord(listValue.item) || toText(listValue.item.kind).toLowerCase() !== "source") {
    listValue.item = createSourceNode("item", "")
  }
  return listValue.item as SourceNode
}

function clearPrimitiveListItemSource(argName: string) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  if (listValue.item && isRecord(listValue.item) && toText(listValue.item.kind).toLowerCase() === "source") {
    listValue.item = undefined
  }
}

function getListFilterOps(arg: ArgAstNode): ListExpressionOp[] {
  const listValue = getListValue(arg)
  if (!Array.isArray(listValue.ops)) listValue.ops = []
  listValue.ops = listValue.ops
    .map((item) => normalizeListOp(item) ?? (isRecord(item) ? item : null))
    .filter(Boolean) as Array<Record<string, any>>
  return (listValue.ops.filter((item) => toText(item?.op).toLowerCase() === "filter")) as ListExpressionOp[]
}

function addListFilterOp(argName: string) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  if (!Array.isArray(listValue.ops)) listValue.ops = []
  listValue.ops.push(createListExpressionOp("filter", ""))
}

function removeListFilterOp(argName: string, opId: string) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  if (!Array.isArray(listValue.ops)) return
  const index = listValue.ops.findIndex((item) => toText(item?.id) === toText(opId))
  if (index >= 0) listValue.ops.splice(index, 1)
}

function addTempCall() {
  localAst.value.temps.push(createTempNode())
}

function removeTempCall(index: number) {
  localAst.value.temps.splice(index, 1)
}

function moveTempCall(index: number, offset: number) {
  const nextIndex = index + offset
  if (index < 0 || nextIndex < 0 || nextIndex >= localAst.value.temps.length) return
  const [current] = localAst.value.temps.splice(index, 1)
  localAst.value.temps.splice(nextIndex, 0, current)
}

function canMoveTemp(index: number, offset: number): boolean {
  const nextIndex = index + offset
  return nextIndex >= 0 && nextIndex < localAst.value.temps.length
}

function getTempCallEditorModel(temp: TempAstNode): ServiceCallEditorModel {
  return callNodeToEditorModel(temp.value)
}

function handleTempCallModelUpdate(index: number, model: ServiceCallEditorModel) {
  const current = localAst.value.temps[index]
  if (!current) return
  current.value = editorModelToCallNode(model, current.value)
  current.javaType = toText(current.value.ref?.returnType) || current.javaType || ""
}

function getListSourceMode(arg: ArgAstNode): "source" | "call" {
  const source = getListValue(arg).source
  return isRecord(source) && toText(source.kind).toLowerCase() === "call" ? "call" : "source"
}

function handleListSourceModeChange(argName: string, event: Event) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const nextMode = toText((event.target as HTMLSelectElement | null)?.value)
  const listValue = arg.value as ListNode
  if (nextMode === "call") {
    if (!isRecord(listValue.source) || toText(listValue.source.kind).toLowerCase() !== "call") {
      listValue.source = createCallNode()
    }
    return
  }
  if (!isRecord(listValue.source) || toText(listValue.source.kind).toLowerCase() !== "source") {
    listValue.source = createSourceNode("request", guessSourcePath(arg.name))
  }
}

function getListSourceValue(arg: ArgAstNode): SourceNode {
  const listValue = getListValue(arg)
  if (!isRecord(listValue.source) || toText(listValue.source.kind).toLowerCase() !== "source") {
    listValue.source = createSourceNode("request", guessSourcePath(arg.name))
  }
  return listValue.source as SourceNode
}

function getListSourceCallEditorModel(arg: ArgAstNode): ServiceCallEditorModel {
  const listValue = getListValue(arg)
  if (!isRecord(listValue.source) || toText(listValue.source.kind).toLowerCase() !== "call") {
    listValue.source = createCallNode()
  }
  return callNodeToEditorModel(listValue.source as CallNode)
}

function handleListSourceCallModelUpdate(argName: string, model: ServiceCallEditorModel) {
  const arg = localAst.value.args.find((item) => item.name === argName)
  if (!arg || !isRecord(arg.value) || toText(arg.value.kind).toLowerCase() !== "list") return
  const listValue = arg.value as ListNode
  const current = isRecord(listValue.source) && toText(listValue.source.kind).toLowerCase() === "call"
    ? listValue.source as CallNode
    : createCallNode()
  listValue.source = editorModelToCallNode(model, current)
}

function getSourceOptionsForNode(sourceType: SourceNode["sourceType"], scope: "arg" | "listItem") {
  if (sourceType === "temp") return tempPathOptions.value
  if (sourceType === "item" && scope === "listItem") {
    const itemOptions = contextSourcePaths.value
      .filter((item) => item.startsWith("item.") || item.startsWith("$."))
    return itemOptions.length > 0 ? itemOptions : ["item.id", "item.code", "$.id", "$.code"]
  }
  return contextSourcePaths.value
}

function collectTempRefsFromValue(value: ValueNode, output: Set<string>) {
  if (!isRecord(value)) return
  const kind = toText(value.kind).toLowerCase()
  if (kind === "source") {
    const source = value as SourceNode
    if (source.sourceType === "temp") {
      const path = toText(source.path)
      const tempKey = path.replace(/^temp\./, "").split(".")[0].trim()
      if (tempKey) output.add(tempKey)
    }
    return
  }
  if (kind === "object") {
    const fields = Array.isArray((value as ObjectNode).fields) ? (value as ObjectNode).fields : []
    fields.forEach((field) => collectTempRefsFromValue(field.value, output))
    return
  }
  if (kind === "list") {
    const listValue = value as ListNode
    collectTempRefsFromValue(listValue.source, output)
    if (listValue.item) collectTempRefsFromValue(listValue.item, output)
    return
  }
  if (kind === "call") {
    const callValue = value as CallNode
    ;(Array.isArray(callValue.args) ? callValue.args : []).forEach((arg) => collectTempRefsFromValue(arg.value, output))
  }
}

function collectTempConsumersFromValue(
  value: ValueNode,
  tempKey: string,
  scopeLabel: string,
  output: Map<string, TempUsageInfo>,
) {
  if (!tempKey || !isRecord(value)) return
  const kind = toText(value.kind).toLowerCase()
  if (kind === "source") {
    const source = value as SourceNode
    if (source.sourceType !== "temp") return
    const rawPath = toText(source.path)
    const normalized = rawPath.replace(/^temp\./, "")
    const [refKey] = normalized.split(".")
    if (toText(refKey) !== tempKey) return
    const path = rawPath ? (rawPath.startsWith("temp.") ? rawPath : `temp.${rawPath}`) : `temp.${tempKey}`
    output.set(`${scopeLabel}|${path}`, { label: scopeLabel, path })
    return
  }
  if (kind === "object") {
    const fields = Array.isArray((value as ObjectNode).fields) ? (value as ObjectNode).fields : []
    fields.forEach((field) => {
      const fieldLabel = toText(field.path) ? `${scopeLabel}.${toText(field.path)}` : `${scopeLabel}.(未命名字段)`
      collectTempConsumersFromValue(field.value, tempKey, fieldLabel, output)
    })
    return
  }
  if (kind === "list") {
    const listValue = value as ListNode
    collectTempConsumersFromValue(listValue.source, tempKey, `${scopeLabel} 列表来源`, output)
    if (listValue.item) collectTempConsumersFromValue(listValue.item, tempKey, `${scopeLabel} 元素映射`, output)
    return
  }
  if (kind === "call") {
    const callValue = value as CallNode
    ;(Array.isArray(callValue.args) ? callValue.args : []).forEach((arg) => {
      const argLabel = toText(arg.name) ? `${scopeLabel}.${toText(arg.name)}` : `${scopeLabel}.(未命名入参)`
      collectTempConsumersFromValue(arg.value, tempKey, argLabel, output)
    })
  }
}

const tempDependencyInfo = computed(() => {
  const temps = localAst.value.temps || []
  const infos = temps.map((temp, index) => {
    const refs = new Set<string>()
    collectTempRefsFromValue(temp.value, refs)
    const consumers = new Map<string, TempUsageInfo>()
    const tempKey = toText(temp.key)
    if (tempKey) {
      temps.slice(index + 1).forEach((nextTemp, nextIndex) => {
        const displayIndex = index + nextIndex + 2
        const nextLabel = toText(nextTemp.key) ? `补数 ${displayIndex}(${toText(nextTemp.key)})` : `补数 ${displayIndex}`
        collectTempConsumersFromValue(nextTemp.value, tempKey, `${nextLabel} 入参`, consumers)
      })
      localAst.value.args.forEach((arg) => {
        collectTempConsumersFromValue(arg.value, tempKey, `参数 ${arg.name}`, consumers)
      })
    }
    return {
      index,
      key: tempKey,
      refs: Array.from(refs),
      consumers: Array.from(consumers.values()),
    }
  })

  const keyToIndex = new Map<string, number[]>()
  infos.forEach((item) => {
    if (!item.key) return
    const hit = keyToIndex.get(item.key) || []
    hit.push(item.index)
    keyToIndex.set(item.key, hit)
  })

  return infos.map((item) => {
    const warnings: string[] = []
    if (!item.key) {
      warnings.push("临时变量 key 不能为空。")
    }
    const duplicateIndexes = item.key ? (keyToIndex.get(item.key) || []) : []
    if (item.key && duplicateIndexes.length > 1) {
      warnings.push(`临时变量 key ${item.key} 重复，保存/发布会被拦截。`)
    }
    for (const ref of item.refs) {
      if (ref === item.key && item.key) {
        warnings.push(`当前补数步骤引用了自身 temp.${ref}，会形成循环依赖。`)
        continue
      }
      const targetIndexes = keyToIndex.get(ref) || []
      if (targetIndexes.length === 0) {
        warnings.push(`当前补数步骤引用了 temp.${ref}，但未找到对应补数定义。`)
        continue
      }
      if (targetIndexes.every((targetIndex) => targetIndex >= item.index)) {
        warnings.push(`当前补数步骤引用了后置 temp.${ref}，建议调整顺序，确保依赖先生成。`)
      }
    }
    return {
      ...item,
      warnings,
    }
  })
})

function getTempInfo(index: number) {
  return tempDependencyInfo.value[index] || { refs: [], warnings: [], consumers: [] }
}

function getTempCardState(index: number) {
  const info = getTempInfo(index)
  const key = toText(localAst.value.temps[index]?.key)
  const hits = issues.value.filter((item) => item.path.includes(`temps[${index}]`) || (key && item.message.includes(key)))
  if (hits.some((item) => item.severity === "error") || info.warnings.length > 0) return "warning"
  if (hits.some((item) => item.severity === "warning")) return "warning"
  return "ok"
}

function setArgCardRef(argName: string, element: Element | null) {
  const htmlElement = unwrapTargetElement(element)
  if (htmlElement) {
    argCardRefs.set(argName, htmlElement)
    return
  }
  argCardRefs.delete(argName)
}

function setTempCardRef(index: number, element: Element | null) {
  const htmlElement = unwrapTargetElement(element)
  if (htmlElement) {
    tempCardRefs.set(index, htmlElement)
    return
  }
  tempCardRefs.delete(index)
}

function unwrapTargetElement(target: Element | ComponentPublicInstance | null): HTMLElement | null {
  if (target instanceof HTMLElement) return target
  const root = (target as { $el?: unknown } | null)?.$el
  return root instanceof HTMLElement ? root : null
}

function setIssueTargetRef(key: string, target: Element | ComponentPublicInstance | null) {
  const htmlElement = unwrapTargetElement(target)
  if (htmlElement) {
    issueTargetRefs.set(key, htmlElement)
    return
  }
  issueTargetRefs.delete(key)
}

function isIssueTargetFocused(key: string): boolean {
  return focusedIssueTarget.value === key
}

function isIssueTargetActive(prefix: string): boolean {
  return focusedIssueTarget.value === prefix || focusedIssueTarget.value.startsWith(`${prefix}:`)
}

function activateIssueElement(target: HTMLElement) {
  const focusTarget = target.matches("input, textarea, select, button, [tabindex]")
    ? target
    : target.querySelector<HTMLElement>("input, textarea, select, button, [tabindex]")
  focusTarget?.focus({ preventScroll: true })
}

function findIssueTarget(keys: string[]): { key: string; element: HTMLElement | null } | null {
  for (const key of keys) {
    const element = issueTargetRefs.get(key)
    if (element) {
      return { key, element }
    }
  }
  return null
}

function addTargetKey(targets: string[], key: string) {
  if (key && !targets.includes(key)) targets.push(key)
}

function resolveIssueTarget(issue: ParamAssemblerIssue): { key: string; element: HTMLElement | null } | null {
  const path = toText(issue.path)
  const tempMatch = path.match(/^temps\[(\d+)\](?:\.(.+))?$/)
  if (tempMatch) {
    const index = Number.parseInt(tempMatch[1], 10)
    if (Number.isFinite(index)) {
      const suffix = toText(tempMatch[2])
      const targetKeys: string[] = []
      if (suffix === "key") {
        addTargetKey(targetKeys, `temp:${index}:key`)
      } else if (suffix === "value.resultPath") {
        addTargetKey(targetKeys, `temp:${index}:resultPath`)
      } else if (suffix === "value" || suffix.startsWith("value.")) {
        addTargetKey(targetKeys, `temp:${index}:call`)
      }
      const nestedTarget = findIssueTarget(targetKeys)
      if (nestedTarget) return nestedTarget
      return {
        key: `temp:${index}`,
        element: tempCardRefs.get(index) || null,
      }
    }
  }

  const argMatch = path.match(/^args\[(\d+)\](?:\.(.+))?$/)
  if (argMatch) {
    const index = Number.parseInt(argMatch[1], 10)
    const arg = localAst.value.args[index]
    if (arg?.name) {
      const suffix = toText(argMatch[2])
      const targetKeys: string[] = []
      if (!suffix || suffix === "value") {
        addTargetKey(targetKeys, `arg:${arg.name}`)
      } else if (suffix === "value.fields") {
        addTargetKey(targetKeys, `arg:${arg.name}:fields`)
      } else if (suffix === "value.ops") {
        addTargetKey(targetKeys, `arg:${arg.name}:filters`)
      } else if (suffix === "value.sourceType") {
        addTargetKey(targetKeys, `arg:${arg.name}:source:type`)
      } else if (suffix === "value.path" || suffix === "value.constValue") {
        addTargetKey(targetKeys, `arg:${arg.name}:source:value`)
      } else if (suffix === "value.source") {
        addTargetKey(targetKeys, `arg:${arg.name}:list-source`)
      } else if (suffix === "value.source.kind") {
        addTargetKey(targetKeys, `arg:${arg.name}:list-source:mode`)
      } else if (suffix === "value.source.sourceType") {
        addTargetKey(targetKeys, `arg:${arg.name}:list-source:type`)
      } else if (suffix === "value.source.path" || suffix === "value.source.constValue") {
        addTargetKey(targetKeys, `arg:${arg.name}:list-source:value`)
      } else if (suffix.startsWith("value.source.")) {
        addTargetKey(targetKeys, `arg:${arg.name}:list-source:call`)
        addTargetKey(targetKeys, `arg:${arg.name}:list-source`)
      } else if (suffix === "value.item.sourceType") {
        addTargetKey(targetKeys, `arg:${arg.name}:primitive-item:type`)
      } else if (suffix === "value.item.path" || suffix === "value.item.constValue") {
        addTargetKey(targetKeys, `arg:${arg.name}:primitive-item:value`)
        addTargetKey(targetKeys, `arg:${arg.name}:item`)
      } else if (suffix === "value.item" || suffix === "value.item.fields") {
        addTargetKey(targetKeys, `arg:${arg.name}:item`)
      }

      const objectFieldMatch = suffix.match(/^value\.fields\[(\d+)\](?:\.(.+))?$/)
      if (objectFieldMatch) {
        const fieldIndex = Number.parseInt(objectFieldMatch[1], 10)
        const fieldSuffix = toText(objectFieldMatch[2])
        const fieldKey = `arg:${arg.name}:field:${fieldIndex}`
        if (!fieldSuffix || fieldSuffix === "value") {
          addTargetKey(targetKeys, fieldKey)
        } else if (fieldSuffix === "path") {
          addTargetKey(targetKeys, `${fieldKey}:path`)
        } else if (fieldSuffix === "value.sourceType") {
          addTargetKey(targetKeys, `${fieldKey}:source-type`)
          addTargetKey(targetKeys, fieldKey)
        } else if (fieldSuffix === "value.path" || fieldSuffix === "value.constValue") {
          addTargetKey(targetKeys, `${fieldKey}:source`)
          addTargetKey(targetKeys, fieldKey)
        } else {
          addTargetKey(targetKeys, fieldKey)
        }
      }

      const filterMatch = suffix.match(/^value\.ops\[(\d+)\](?:\.(.+))?$/)
      if (filterMatch) {
        const filterIndex = Number.parseInt(filterMatch[1], 10)
        const filterSuffix = toText(filterMatch[2])
        const filterKey = `arg:${arg.name}:filter:${filterIndex}`
        if (!filterSuffix) {
          addTargetKey(targetKeys, filterKey)
        } else {
          addTargetKey(targetKeys, `${filterKey}:expression`)
          addTargetKey(targetKeys, filterKey)
        }
      }

      const itemFieldMatch = suffix.match(/^value\.item\.fields\[(\d+)\](?:\.(.+))?$/)
      if (itemFieldMatch) {
        const fieldIndex = Number.parseInt(itemFieldMatch[1], 10)
        const fieldSuffix = toText(itemFieldMatch[2])
        const fieldKey = `arg:${arg.name}:item-field:${fieldIndex}`
        if (!fieldSuffix || fieldSuffix === "value") {
          addTargetKey(targetKeys, fieldKey)
        } else if (fieldSuffix === "path") {
          addTargetKey(targetKeys, `${fieldKey}:path`)
        } else if (fieldSuffix === "value.sourceType") {
          addTargetKey(targetKeys, `${fieldKey}:source-type`)
          addTargetKey(targetKeys, fieldKey)
        } else if (fieldSuffix === "value.path" || fieldSuffix === "value.constValue") {
          addTargetKey(targetKeys, `${fieldKey}:source`)
          addTargetKey(targetKeys, fieldKey)
        } else {
          addTargetKey(targetKeys, fieldKey)
        }
        addTargetKey(targetKeys, `arg:${arg.name}:item`)
      }

      const nestedTarget = findIssueTarget(targetKeys)
      if (nestedTarget) return nestedTarget
      return {
        key: `arg:${arg.name}`,
        element: argCardRefs.get(arg.name) || null,
      }
    }
  }

  return null
}

function focusIssue(issue: ParamAssemblerIssue) {
  const target = resolveIssueTarget(issue)
  if (!target?.element) return
  focusedIssueTarget.value = target.key
  target.element.scrollIntoView({ behavior: "smooth", block: "center" })
  requestAnimationFrame(() => activateIssueElement(target.element!))
}

function getIssueAction(issue: ParamAssemblerIssue): IssueAction {
  const code = toText(issue.code)
  if (["value.required", "source.path.required", "list.source.required", "object.field.required"].includes(code)) {
    return {
      detail: "当前缺少来源映射，建议先自动补齐空白项，再人工确认来源路径或 Temp 依赖。",
      actionLabel: "自动补空白",
      actionKind: "smart-fill-empty",
    }
  }
  if (code === "object.field.unknown") {
    return {
      detail: "字段可能拼写不对，或当前 schema / 元数据上报还不完整。可以先重算推荐映射，再检查字段路径。",
      actionLabel: "重算推荐",
      actionKind: "smart-fill-all",
    }
  }
  if (code === "arg.extra") {
    return {
      detail: "当前方法签名和现有草稿不一致，建议重新生成草稿，再确认补数步骤是否仍然适配。",
      actionLabel: "重新生成草稿",
      actionKind: "rebuild-draft",
    }
  }
  if (["source.temp.missing", "source.temp.forward", "source.temp.self", "temp.ref.cycle", "temp.key.required", "temp.key.duplicate"].includes(code)) {
    return {
      detail: "这类问题通常和 Temp 命名、引用链或顺序有关，可先定位到对应位置并结合装配预览检查依赖。",
      actionLabel: "定位问题",
      actionKind: "focus",
    }
  }
  if (["call.service.bean.required", "call.service.method.required", "call.http.url.required", "call.arg.value.required"].includes(code)) {
    return {
      detail: "服务补数配置还不完整，先补齐调用目标和入参，再重新执行校验。",
      actionLabel: "定位问题",
      actionKind: "focus",
    }
  }
  if (["list.nested.unsupported", "list.op.unsupported", "expr.required"].includes(code)) {
    return {
      detail: "当前属于复杂集合或表达式场景，建议先拆成 Temp 补数 + 单层列表装配；更复杂逻辑切到高级模式。",
      actionLabel: "定位问题",
      actionKind: "focus",
    }
  }
  return {
    detail: "建议先定位到对应字段，检查来源路径、Temp 依赖和服务补数配置是否完整。",
    actionLabel: "定位问题",
    actionKind: "focus",
  }
}

async function runIssueAction(issue: ParamAssemblerIssue) {
  const action = getIssueAction(issue)
  if (!action.actionKind) return
  if (action.actionKind === "smart-fill-empty") {
    await applySmartFill("emptyOnly")
    requestAnimationFrame(() => focusIssue(issue))
    return
  }
  if (action.actionKind === "smart-fill-all") {
    await applySmartFill("all")
    requestAnimationFrame(() => focusIssue(issue))
    return
  }
  if (action.actionKind === "rebuild-draft") {
    await buildDraft(true)
    requestAnimationFrame(() => focusIssue(issue))
    return
  }
  focusIssue(issue)
}

function isArgFocused(argName: string): boolean {
  return isIssueTargetActive(`arg:${argName}`)
}

function isTempFocused(index: number): boolean {
  return isIssueTargetActive(`temp:${index}`)
}

function fieldSourceOptions(scope: "arg" | "listItem") {
  const options = [
    { value: "request", label: "请求入参" },
    { value: "context", label: "上下文变量" },
    { value: "temp", label: "临时变量" },
    { value: "nodeOutput", label: "上游节点输出" },
    { value: "const", label: "常量" },
  ]
  if (scope === "listItem") options.unshift({ value: "item", label: "当前列表项" })
  return options
}

function getSourcePlaceholder(sourceType: SourceNode["sourceType"], fallback: string) {
  if (sourceType === "request") return fallback || "request.body.xxx"
  if (sourceType === "context") return "userId / orderContext.customerId"
  if (sourceType === "temp") return "priceProfile / priceProfile.level"
  if (sourceType === "nodeOutput") return "retA.data / retB.items"
  if (sourceType === "item") return "skuId / $.skuId"
  return ""
}

function getArgCardState(arg: ArgAstNode, argIndex: number) {
  const hits = issues.value.filter((item) => item.path.includes(`args[${argIndex}]`) || item.message.includes(arg.name))
  if (hits.some((item) => item.severity === "error")) return "error"
  if (hits.some((item) => item.severity === "warning")) return "warning"
  return "ok"
}

function getArgSummary(arg: ArgAstNode): string {
  const kind = toText(arg.value?.kind).toLowerCase()
  if (kind === "source") {
    const source = arg.value as SourceNode
    return source.sourceType === "const" ? "直接取值 / 常量" : `直接取值 / ${source.sourceType}`
  }
  if (kind === "object") return `对象装配 / ${(arg.value as ObjectNode).fields.length} 个字段`
  if (kind === "list") {
    const listValue = arg.value as ListNode
    const filterCount = (Array.isArray(listValue.ops) ? listValue.ops : []).filter((item) => toText(item?.op) === "filter").length
    const sourceSummary = isRecord(listValue.source) && toText(listValue.source.kind).toLowerCase() === "call" ? "服务补数" : "路径来源"
    const itemFields = listValue.item && isRecord(listValue.item) && toText(listValue.item.kind).toLowerCase() === "object"
      ? (listValue.item as ObjectNode).fields.length
      : 0
    const itemSummary = itemFields > 0
      ? `${itemFields} 个元素字段`
      : (listValue.item && isRecord(listValue.item) && toText(listValue.item.kind).toLowerCase() === "source"
        ? "元素取值映射"
        : "直接透传")
    return filterCount > 0 ? `单层列表 / ${sourceSummary} / ${itemSummary} / ${filterCount} 条过滤` : `单层列表 / ${sourceSummary} / ${itemSummary}`
  }
  return `高级节点 / ${kind || "unknown"}`
}

function getSchemaFieldCount(argName: string): number {
  return flattenSchemaLeafPaths(findArgMeta(argName).schema || null).length
}

function canShowItemBuilder(argName: string): boolean {
  const meta = findArgMeta(argName)
  const itemSchema = isRecord(meta.schema?.items) ? meta.schema.items : null
  return Boolean(itemSchema && isRecord(itemSchema.properties))
}

function getArgSourceValue(arg: ArgAstNode): SourceNode {
  return arg.value as SourceNode
}

function getObjectFieldList(arg: ArgAstNode): ObjectFieldNode[] {
  return Array.isArray((arg.value as ObjectNode).fields) ? (arg.value as ObjectNode).fields : []
}

function getListValue(arg: ArgAstNode): ListNode {
  return arg.value as ListNode
}

function getListItemFieldList(arg: ArgAstNode): ObjectFieldNode[] {
  const listValue = getListValue(arg)
  if (!listValue.item || !isRecord(listValue.item) || toText(listValue.item.kind).toLowerCase() !== "object") return []
  return Array.isArray((listValue.item as ObjectNode).fields) ? (listValue.item as ObjectNode).fields : []
}

function getFieldSourceValue(field: ObjectFieldNode): SourceNode {
  return field.value as SourceNode
}

function pushUniqueText(target: string[], value: string) {
  const text = toText(value)
  if (!text || target.includes(text)) return
  target.push(text)
}

function pushUniqueServiceCall(target: ServiceCallTrace[], call: ServiceCallTrace) {
  const key = `${call.label}|${call.fn}|${call.resultPath}|${call.args}`
  if (target.some((item) => `${item.label}|${item.fn}|${item.resultPath}|${item.args}` === key)) return
  target.push(call)
}

function describeSourceNode(source: SourceNode): string {
  if (source.sourceType === "const") return "常量"
  if (source.sourceType === "temp") {
    const path = toText(source.path)
    return path.startsWith("temp.") ? path : `temp.${path}`
  }
  return toText(source.path)
}

function describeCallNode(call: CallNode): string {
  const ref = isRecord(call.ref) ? call.ref : {}
  return firstNonBlank(
    [toText(ref.serviceBean), toText(ref.methodName)].filter(Boolean).join("."),
    toText(call.fn),
    "未命名服务调用",
  )
}

function collectPreviewFromValue(
  value: ValueNode,
  label: string,
  sourceRefs: string[],
  tempRefs: string[],
  serviceCalls: ServiceCallTrace[],
) {
  if (!isRecord(value)) return
  const kind = toText(value.kind).toLowerCase()
  if (kind === "source") {
    const source = value as SourceNode
    const text = describeSourceNode(source)
    if (source.sourceType === "temp") {
      pushUniqueText(tempRefs, text)
      return
    }
    pushUniqueText(sourceRefs, text)
    return
  }
  if (kind === "object") {
    const fields = Array.isArray((value as ObjectNode).fields) ? (value as ObjectNode).fields : []
    fields.forEach((field) => {
      const fieldLabel = toText(field.path) ? `${label}.${toText(field.path)}` : label
      collectPreviewFromValue(field.value, fieldLabel, sourceRefs, tempRefs, serviceCalls)
    })
    return
  }
  if (kind === "list") {
    const listValue = value as ListNode
    collectPreviewFromValue(listValue.source, `${label} 列表来源`, sourceRefs, tempRefs, serviceCalls)
    if (listValue.item) {
      collectPreviewFromValue(listValue.item, `${label} 元素映射`, sourceRefs, tempRefs, serviceCalls)
    }
    return
  }
  if (kind === "call") {
    const callValue = value as CallNode
    pushUniqueServiceCall(serviceCalls, {
      label,
      fn: describeCallNode(callValue),
      args: Array.isArray(callValue.args) ? callValue.args.length : 0,
      resultPath: toText(callValue.resultPath),
    })
    ;(Array.isArray(callValue.args) ? callValue.args : []).forEach((arg) => {
      const argLabel = toText(arg.name) ? `${label}.${toText(arg.name)}` : `${label}.参数`
      collectPreviewFromValue(arg.value, argLabel, sourceRefs, tempRefs, serviceCalls)
    })
  }
}

const tempPreviewItems = computed<ValuePreviewItem[]>(() =>
  (localAst.value.temps || []).map((temp, index) => {
    const sourceRefs: string[] = []
    const tempRefs: string[] = []
    const serviceCalls: ServiceCallTrace[] = []
    const name = toText(temp.key) || `补数 ${index + 1}`
    collectPreviewFromValue(temp.value, `补数 ${index + 1}`, sourceRefs, tempRefs, serviceCalls)
    return {
      name,
      summary: `补数步骤 ${index + 1}${toText(temp.javaType) ? ` / ${toText(temp.javaType)}` : ""}`,
      sourceRefs,
      tempRefs,
      serviceCalls,
    }
  }),
)

const argPreviewItems = computed<ValuePreviewItem[]>(() =>
  (localAst.value.args || []).map((arg) => {
    const sourceRefs: string[] = []
    const tempRefs: string[] = []
    const serviceCalls: ServiceCallTrace[] = []
    collectPreviewFromValue(arg.value, `参数 ${arg.name}`, sourceRefs, tempRefs, serviceCalls)
    return {
      name: arg.name,
      summary: getArgSummary(arg),
      sourceRefs,
      tempRefs,
      serviceCalls,
    }
  }),
)

const assemblyPreviewSummary = computed(() => ({
  args: argPreviewItems.value.length,
  temps: tempPreviewItems.value.length,
  tempRefs: argPreviewItems.value.reduce((sum, item) => sum + item.tempRefs.length, 0)
    + tempPreviewItems.value.reduce((sum, item) => sum + item.tempRefs.length, 0),
  serviceCalls: argPreviewItems.value.reduce((sum, item) => sum + item.serviceCalls.length, 0)
    + tempPreviewItems.value.reduce((sum, item) => sum + item.serviceCalls.length, 0),
}))

const validationSummary = computed(() => ({
  errors: issues.value.filter((item) => item.severity === "error").length,
  warnings: issues.value.filter((item) => item.severity === "warning").length,
}))
const assemblerInsight = computed<ParamAssemblerInsight>(() => {
  const errors: string[] = []
  const warnings: string[] = []

  if (draftError.value) {
    errors.push(draftError.value)
  }
  issues.value.forEach((item) => {
    const message = [toText(item.path), toText(item.message)].filter(Boolean).join(" : ")
    if (!message) return
    if (item.severity === "error") errors.push(message)
    else warnings.push(message)
  })
  if (hasPendingChanges.value && localAst.value.args.length > 0) {
    warnings.push("参数装配存在未校验修改，请先执行校验。")
  }
  tempDependencyInfo.value.forEach((info, index) => {
    info.warnings.forEach((msg) => warnings.push(`补数 ${index + 1}: ${msg}`))
  })

  return {
    errors: uniqueTextList(errors),
    warnings: uniqueTextList(warnings),
  }
})

const stepStatus = computed(() => ({
  methodReady: Boolean(props.methodKey),
  contextReady: Boolean(contextInfo.value),
  draftReady: localAst.value.args.length > 0,
  validated: issues.value.length > 0 || (!hasPendingChanges.value && localAst.value.args.length > 0),
}))

watch(() => props.modelValue, (next) => {
  if (!next || !isRecord(next) || next.version !== AST_VERSION) return
  applyAst(next as ParamAssemblerAst, { emitChange: false, pending: false })
}, { immediate: true, deep: true })

watch(localAst, () => {
  if (syncFromCode.value) return
  hasPendingChanges.value = true
  emitCurrentState()
}, { deep: true })
watch(assemblerInsight, (next) => emit("insight-change", clone(next)), { deep: true, immediate: true })

watch(
  () => [props.projectKey, props.endpointId, props.methodKey, JSON.stringify(argMetas.value.map((item) => `${item.name}|${item.javaType}|${item.required}`))],
  async ([projectKey, _endpointId, methodKey]) => {
    if (!projectKey || !methodKey || argMetas.value.length === 0) {
      applyAst({ version: AST_VERSION, args: [], temps: [] }, { emitChange: true, pending: false, issues: [] })
      contextInfo.value = null
      issues.value = []
      return
    }

    await loadContext()
    const hasCurrentAst = localAst.value.args.length === argMetas.value.length && localAst.value.args.every((arg) => argMetaByName.value.has(arg.name))
    if (!hasCurrentAst || lastDraftSignature.value !== buildDraftSignature()) {
      await buildDraft(true)
    }
  },
  { immediate: true },
)

</script>

<template>
  <div class="param-assembler-panel">
    <div class="flow-banner">
      <div class="flow-title">基础装配</div>
      <div class="flow-steps">
        <span class="step-pill" :class="{ ready: stepStatus.methodReady }">1 选方法</span>
        <span class="step-pill" :class="{ ready: stepStatus.contextReady }">2 拉 Context</span>
        <span class="step-pill" :class="{ ready: stepStatus.draftReady }">3 生成 Draft</span>
        <span class="step-pill" :class="{ ready: stepStatus.validated }">4 校验并修正</span>
      </div>
      <div class="muted tiny">主流程固定为“选方法 -> 拉 context -> 生成 draft -> 人工修正 -> validate”。高级编排仍保留在下方高级模式中。</div>
    </div>

    <div class="unsupported-panel">
      <div class="section-head">
        <div class="panel-title">一期边界</div>
      </div>
      <ul class="unsupported-list">
        <li v-for="note in unsupportedNotes" :key="note">{{ note }}</li>
      </ul>
    </div>

    <div class="toolbar">
      <div class="context-summary">
        <span class="summary-chip" :class="{ ready: Boolean(contextInfo) }">Context {{ contextInfo ? "已加载" : "未加载" }}</span>
        <span class="summary-chip">来源路径 {{ contextSourcePaths.length }}</span>
        <span class="summary-chip">组件 {{ contextInfo?.componentGroups?.length || 0 }}</span>
        <span class="summary-chip">辅助类 {{ contextInfo?.helperClasses?.length || 0 }}</span>
      </div>
      <div class="toolbar-actions">
        <button type="button" class="btn mini" :disabled="loadingContext || !projectKey || !methodKey" @click="loadContext">刷新 Context</button>
        <button type="button" class="btn mini" :disabled="loadingDraft || !projectKey || !methodKey" @click="buildDraft(true)">重建 Draft</button>
        <button type="button" class="btn mini" :disabled="validating || localAst.args.length === 0" @click="applySmartFill('emptyOnly')">智能补齐空白</button>
        <button type="button" class="btn mini" :disabled="validating || localAst.args.length === 0" @click="applySmartFill('all')">重算推荐</button>
        <button type="button" class="btn mini primary" :disabled="validating || !projectKey || localAst.args.length === 0" @click="validateAst">
          {{ validating ? "校验中..." : "立即校验" }}
        </button>
      </div>
    </div>

    <div v-if="lastSmartFillSummary" class="info-card">
      <div class="info-title">自动补齐</div>
      <div class="muted tiny">{{ lastSmartFillSummary }}</div>
    </div>

    <div v-if="tempPreviewItems.length > 0 || argPreviewItems.length > 0" class="info-card">
      <div class="section-head">
        <div class="panel-title">装配预览</div>
        <div class="context-summary">
          <span class="summary-chip">参数 {{ assemblyPreviewSummary.args }}</span>
          <span class="summary-chip">补数 {{ assemblyPreviewSummary.temps }}</span>
          <span class="summary-chip">Temp 依赖 {{ assemblyPreviewSummary.tempRefs }}</span>
          <span class="summary-chip">服务补数 {{ assemblyPreviewSummary.serviceCalls }}</span>
        </div>
      </div>
      <div class="muted tiny">这里展示每个参数/补数当前依赖了哪些来源路径、哪些临时变量、哪些服务调用，方便检查复杂对象和集合装配链路。</div>

      <div v-if="tempPreviewItems.length > 0" class="preview-list">
        <div class="field-label">补数解释</div>
        <div v-for="item in tempPreviewItems" :key="`temp-preview-${item.name}`" class="preview-card">
          <div class="preview-head">
            <div class="arg-title">{{ item.name }}</div>
            <div class="muted tiny">{{ item.summary }}</div>
          </div>
          <div class="preview-section">
            <span class="preview-label">来源路径</span>
            <div class="preview-chips">
              <span v-if="item.sourceRefs.length === 0" class="summary-chip">无</span>
              <span v-for="source in item.sourceRefs.slice(0, 6)" :key="`${item.name}_${source}`" class="summary-chip">{{ source }}</span>
            </div>
          </div>
          <div class="preview-section">
            <span class="preview-label">依赖 Temp</span>
            <div class="preview-chips">
              <span v-if="item.tempRefs.length === 0" class="summary-chip">无</span>
              <span v-for="tempRef in item.tempRefs.slice(0, 6)" :key="`${item.name}_${tempRef}`" class="summary-chip ready">{{ tempRef }}</span>
            </div>
          </div>
          <div class="preview-section" v-if="item.serviceCalls.length > 0">
            <span class="preview-label">服务调用</span>
            <div class="preview-call-list">
              <div v-for="call in item.serviceCalls" :key="`${item.name}_${call.label}_${call.fn}`" class="preview-call-item">
                <span class="preview-call-name">{{ call.fn }}</span>
                <span class="muted tiny">{{ call.label }} / 入参 {{ call.args }} 个<span v-if="call.resultPath"> / 结果 {{ call.resultPath }}</span></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="argPreviewItems.length > 0" class="preview-list">
        <div class="field-label">参数解释</div>
        <div v-for="item in argPreviewItems" :key="`arg-preview-${item.name}`" class="preview-card">
          <div class="preview-head">
            <div class="arg-title">{{ item.name }}</div>
            <div class="muted tiny">{{ item.summary }}</div>
          </div>
          <div class="preview-section">
            <span class="preview-label">来源路径</span>
            <div class="preview-chips">
              <span v-if="item.sourceRefs.length === 0" class="summary-chip">无</span>
              <span v-for="source in item.sourceRefs.slice(0, 8)" :key="`${item.name}_${source}`" class="summary-chip">{{ source }}</span>
            </div>
          </div>
          <div class="preview-section">
            <span class="preview-label">依赖 Temp</span>
            <div class="preview-chips">
              <span v-if="item.tempRefs.length === 0" class="summary-chip">无</span>
              <span v-for="tempRef in item.tempRefs.slice(0, 8)" :key="`${item.name}_${tempRef}`" class="summary-chip ready">{{ tempRef }}</span>
            </div>
          </div>
          <div class="preview-section" v-if="item.serviceCalls.length > 0">
            <span class="preview-label">服务调用</span>
            <div class="preview-call-list">
              <div v-for="call in item.serviceCalls" :key="`${item.name}_${call.label}_${call.fn}`" class="preview-call-item">
                <span class="preview-call-name">{{ call.fn }}</span>
                <span class="muted tiny">{{ call.label }} / 入参 {{ call.args }} 个<span v-if="call.resultPath"> / 结果 {{ call.resultPath }}</span></span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="loadingContext || loadingDraft" class="info-card">
      <div class="info-title">{{ loadingDraft ? "正在生成装配草稿..." : "正在加载装配上下文..." }}</div>
      <div class="muted tiny">会结合入口请求 schema 和当前方法入参自动补齐对象与单层列表结构。</div>
    </div>

    <div v-if="draftError" class="error-card">{{ draftError }}</div>

    <div class="validation-card" :class="{ pass: validationSummary.errors === 0 && validationSummary.warnings === 0 && !hasPendingChanges && localAst.args.length > 0 }">
      <div class="section-head">
        <div class="panel-title">校验状态</div>
        <div class="muted tiny" v-if="hasPendingChanges">当前有未校验修改</div>
      </div>
      <div class="validation-summary">
        <span class="error-count">错误 {{ validationSummary.errors }}</span>
        <span class="warning-count">告警 {{ validationSummary.warnings }}</span>
        <span class="muted tiny" v-if="!hasPendingChanges && issues.length === 0 && localAst.args.length > 0">当前结构已通过一期规则校验。</span>
      </div>
      <div v-if="issues.length > 0" class="issue-list">
        <div
          v-for="item in issues"
          :key="`${item.code}_${item.path}_${item.message}`"
          class="issue-item clickable"
          :class="item.severity"
          role="button"
          tabindex="0"
          @click="focusIssue(item)"
          @keydown.enter.prevent="focusIssue(item)"
          @keydown.space.prevent="focusIssue(item)"
        >
          <span class="issue-code">{{ item.code }}</span>
          <span class="issue-message">{{ item.message }}</span>
          <span class="issue-path">{{ item.path }}</span>
          <span class="issue-hint">{{ getIssueAction(item).detail }}</span>
          <button
            v-if="getIssueAction(item).actionLabel"
            type="button"
            class="btn mini issue-action"
            :disabled="validating || loadingDraft"
            @click.stop="runIssueAction(item)"
            @keydown.enter.stop
            @keydown.space.stop
          >
            {{ getIssueAction(item).actionLabel }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="methodKey" class="temp-panel">
      <div class="section-head">
        <div class="panel-title">补数步骤</div>
        <button type="button" class="btn mini" @click="addTempCall">+ 新增服务补数</button>
      </div>
      <div class="muted tiny">当对象或列表字段需要先查服务再拼装时，先在这里定义中间结果，再在字段来源里选择 `临时变量` 并填写 `temp.xxx`。</div>
      <div class="muted tiny">数组/对象集合建议先让补数输出完整对象或单层列表，再在目标入参中分别配置“对象字段”或“元素映射”；嵌套数组、动态 Map key、groupBy / reduce / flatten 仍建议切到高级模式。</div>
      <div v-if="localAst.temps.length === 0" class="muted tiny">当前还没有补数步骤，简单场景可以直接用请求、上下文或上游节点输出。</div>
      <div
        v-for="(temp, tempIndex) in localAst.temps"
        :key="`${temp.key}_${tempIndex}`"
        :ref="(el) => setTempCardRef(tempIndex, el)"
        class="temp-card"
        :class="[getTempCardState(tempIndex), { focused: isTempFocused(tempIndex) }]"
      >
        <div class="field-row-head">
          <div class="panel-title">补数 {{ tempIndex + 1 }}</div>
          <div class="row-actions">
            <button type="button" class="btn mini" :disabled="!canMoveTemp(tempIndex, -1)" @click="moveTempCall(tempIndex, -1)">上移</button>
            <button type="button" class="btn mini" :disabled="!canMoveTemp(tempIndex, 1)" @click="moveTempCall(tempIndex, 1)">下移</button>
            <button type="button" class="btn mini" @click="removeTempCall(tempIndex)">删</button>
          </div>
        </div>
        <div class="temp-meta-row">
          <span class="summary-chip">输出 key {{ temp.key || "未命名" }}</span>
          <span class="summary-chip" v-if="getTempInfo(tempIndex).refs.length > 0">依赖 {{ getTempInfo(tempIndex).refs.map((item) => `temp.${item}`).join(" , ") }}</span>
          <span class="summary-chip" v-else>依赖 无</span>
          <span class="summary-chip ready" v-if="getTempInfo(tempIndex).consumers.length > 0">下游使用 {{ getTempInfo(tempIndex).consumers.length }} 处</span>
          <span class="summary-chip" v-else>下游使用 0 处</span>
        </div>
        <input
          :ref="(el) => setIssueTargetRef(`temp:${tempIndex}:key`, el)"
          class="input"
          :class="{ focused: isIssueTargetFocused(`temp:${tempIndex}:key`) }"
          v-model="temp.key"
          placeholder="临时变量 key，例如 profile / skuCatalog / priceProfile"
        />
        <input
          :ref="(el) => setIssueTargetRef(`temp:${tempIndex}:resultPath`, el)"
          class="input"
          :class="{ focused: isIssueTargetFocused(`temp:${tempIndex}:resultPath`) }"
          v-model="temp.value.resultPath"
          placeholder="结果提取路径（可选），例如 data / data.profile / $.data.items"
        />
        <div v-if="getTempInfo(tempIndex).warnings.length > 0" class="plan-warnings">
          <div v-for="msg in getTempInfo(tempIndex).warnings" :key="msg" class="plan-warning">{{ msg }}</div>
        </div>
        <div v-if="getTempInfo(tempIndex).consumers.length > 0" class="temp-usage-list">
          <div v-for="consumer in getTempInfo(tempIndex).consumers.slice(0, 4)" :key="`${consumer.label}_${consumer.path}`" class="temp-usage-item">
            <span class="temp-usage-label">{{ consumer.label }}</span>
            <span class="temp-usage-path">{{ consumer.path }}</span>
          </div>
          <div v-if="getTempInfo(tempIndex).consumers.length > 4" class="muted tiny">
            还有 {{ getTempInfo(tempIndex).consumers.length - 4 }} 处下游引用，避免重复 key 更容易排查依赖。
          </div>
        </div>
        <div v-else class="muted tiny">当前补数还没有被后续补数或目标入参使用，适合先完成依赖顺序调整再继续映射。</div>
        <div
          :ref="(el) => setIssueTargetRef(`temp:${tempIndex}:call`, el)"
          class="focus-anchor"
          :class="{ focused: isIssueTargetActive(`temp:${tempIndex}:call`) }"
        >
          <ServiceCallEditor
            :model-value="getTempCallEditorModel(temp)"
            :project-key="projectKey"
            :temp-keys="tempPathOptions.map((item) => item.replace(/^temp\./, ''))"
            :source-path-options="contextSourcePaths"
            @update:model-value="handleTempCallModelUpdate(tempIndex, $event)"
          />
        </div>
        <div class="muted tiny">生成后可在字段来源中使用 `temp.{{ temp.key || 'yourKey' }}` 或 `temp.{{ temp.key || 'yourKey' }}.xxx`。</div>
      </div>
    </div>

    <div v-if="!methodKey" class="empty-card">
      <div class="panel-title">还没有选中服务方法</div>
      <div class="muted tiny">先在上方“服务方法”区域选择方法，基础装配器才会拉取 context 并生成 draft。</div>
    </div>

    <div v-else-if="localAst.args.length === 0" class="empty-card">
      <div class="panel-title">当前方法没有可编辑入参</div>
      <div class="muted tiny">若方法存在复杂补数需求，请切换到高级模式配置参数管线。</div>
    </div>

    <div v-else class="arg-list">
      <div
        v-for="(arg, argIndex) in localAst.args"
        :key="arg.name"
        :ref="(el) => setArgCardRef(arg.name, el)"
        class="arg-card"
        :class="[getArgCardState(arg, argIndex), { focused: isArgFocused(arg.name) }]"
      >
        <div class="arg-head">
          <div class="arg-meta">
            <div class="arg-title-row">
              <div class="arg-title">{{ arg.name }}</div>
              <span class="type-chip">{{ arg.javaType || "java.lang.Object" }}</span>
              <span class="required-chip" :class="{ optional: !arg.required }">{{ arg.required ? "必填" : "可选" }}</span>
            </div>
            <div class="muted tiny">{{ getArgSummary(arg) }}</div>
          </div>
          <div class="muted tiny">Schema 叶子字段 {{ getSchemaFieldCount(arg.name) }}</div>
        </div>

        <template v-if="arg.value.kind === 'source'">
          <div
            :ref="(el) => setIssueTargetRef(`arg:${arg.name}:source`, el)"
            class="field-block"
            :class="{ focused: isIssueTargetActive(`arg:${arg.name}:source`) }"
          >
            <div class="field-label">来源配置</div>
            <select
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:source:type`, el)"
              class="input"
              :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:source:type`) }"
              v-model="getArgSourceValue(arg).sourceType"
            >
              <option v-for="option in fieldSourceOptions('arg')" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <div
              v-if="getArgSourceValue(arg).sourceType !== 'const'"
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:source:value`, el)"
              class="focus-anchor"
              :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:source:value`) }"
            >
              <SourcePathInput
                :model-value="getArgSourceValue(arg).path || ''"
                :options="getSourceOptionsForNode(getArgSourceValue(arg).sourceType, 'arg')"
                :placeholder="getSourcePlaceholder(getArgSourceValue(arg).sourceType, guessSourcePath(arg.name))"
                @update:model-value="getArgSourceValue(arg).path = $event"
              />
            </div>
            <textarea
              v-else
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:source:value`, el)"
              class="input textarea"
              :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:source:value`) }"
              v-model="getArgSourceValue(arg).constValue"
              placeholder='常量值，例如 "A100" / 1 / {"id":"u1"} / [{"id":"1"}]'
            ></textarea>
          </div>
        </template>

        <template v-else-if="arg.value.kind === 'object'">
          <div
            :ref="(el) => setIssueTargetRef(`arg:${arg.name}:fields`, el)"
            class="field-block"
            :class="{ focused: isIssueTargetActive(`arg:${arg.name}:fields`) }"
          >
            <div class="section-head">
              <div class="field-label">对象字段映射</div>
              <div class="row-actions">
                <button type="button" class="btn mini" @click="rebuildObjectFromSchema(arg.name)">按 Schema 重建</button>
                <button type="button" class="btn mini" @click="addObjectField(arg.name)">+ 字段</button>
              </div>
            </div>
            <div class="muted tiny">对象参数按字段展开显示，避免用户直接面对整段 JSON。</div>
            <div
              v-for="(field, index) in getObjectFieldList(arg)"
              :key="`${arg.name}_${index}`"
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:field:${index}`, el)"
              class="field-row-card"
              :class="{ focused: isIssueTargetActive(`arg:${arg.name}:field:${index}`) }"
            >
              <div class="field-row-head">
                <div class="muted tiny">字段 {{ index + 1 }}</div>
                <button type="button" class="btn mini" @click="removeObjectField(arg.name, index)">删</button>
              </div>
              <input
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:field:${index}:path`, el)"
                class="input"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:field:${index}:path`) }"
                v-model="field.path"
                placeholder="字段路径，例如 profile.name / address.city"
              />
              <select
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:field:${index}:source-type`, el)"
                class="input"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:field:${index}:source-type`) }"
                v-model="getFieldSourceValue(field).sourceType"
              >
                <option v-for="option in fieldSourceOptions('arg')" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <div
                v-if="getFieldSourceValue(field).sourceType !== 'const'"
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:field:${index}:source`, el)"
                class="focus-anchor"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:field:${index}:source`) }"
              >
                <SourcePathInput
                  :model-value="getFieldSourceValue(field).path || ''"
                  :options="getSourceOptionsForNode(getFieldSourceValue(field).sourceType, 'arg')"
                  :placeholder="getSourcePlaceholder(getFieldSourceValue(field).sourceType, guessSourcePath(field.path || arg.name))"
                  @update:model-value="getFieldSourceValue(field).path = $event"
                />
              </div>
              <textarea
                v-else
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:field:${index}:source`, el)"
                class="input textarea mini-textarea"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:field:${index}:source`) }"
                v-model="getFieldSourceValue(field).constValue"
                placeholder='常量值，例如 "Tom" / 18 / {"code":"VIP"}'
              ></textarea>
            </div>
          </div>
        </template>

        <template v-else-if="arg.value.kind === 'list'">
          <div
            :ref="(el) => setIssueTargetRef(`arg:${arg.name}:list-source`, el)"
            class="field-block"
            :class="{ focused: isIssueTargetActive(`arg:${arg.name}:list-source`) }"
          >
            <div class="field-label">列表来源</div>
            <select
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:list-source:mode`, el)"
              class="input"
              :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:list-source:mode`) }"
              :value="getListSourceMode(arg)"
              @change="handleListSourceModeChange(arg.name, $event)"
            >
              <option value="source">路径 / 常量</option>
              <option value="call">服务调用补数</option>
            </select>
            <template v-if="getListSourceMode(arg) === 'source'">
              <select
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:list-source:type`, el)"
                class="input"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:list-source:type`) }"
                v-model="getListSourceValue(arg).sourceType"
              >
                <option v-for="option in fieldSourceOptions('arg')" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <div
                v-if="getListSourceValue(arg).sourceType !== 'const'"
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:list-source:value`, el)"
                class="focus-anchor"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:list-source:value`) }"
              >
                <SourcePathInput
                  :model-value="getListSourceValue(arg).path || ''"
                  :options="getSourceOptionsForNode(getListSourceValue(arg).sourceType, 'arg')"
                  :placeholder="getSourcePlaceholder(getListSourceValue(arg).sourceType, guessSourcePath(arg.name))"
                  @update:model-value="getListSourceValue(arg).path = $event"
                />
              </div>
              <textarea
                v-else
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:list-source:value`, el)"
                class="input textarea"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:list-source:value`) }"
                v-model="getListSourceValue(arg).constValue"
                placeholder='常量列表，例如 [{"skuId":"A100"}]'
              ></textarea>
            </template>
            <template v-else>
              <div class="muted tiny">当列表需要先查商品、用户、库存等服务后再装配时，直接在这里配置补数服务。</div>
              <div
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:list-source:call`, el)"
                class="focus-anchor"
                :class="{ focused: isIssueTargetActive(`arg:${arg.name}:list-source:call`) }"
              >
                <ServiceCallEditor
                  :model-value="getListSourceCallEditorModel(arg)"
                  :project-key="projectKey"
                  :temp-keys="tempPathOptions.map((item) => item.replace(/^temp\./, ''))"
                  :source-path-options="contextSourcePaths"
                  @update:model-value="handleListSourceCallModelUpdate(arg.name, $event)"
                />
              </div>
            </template>
          </div>

          <div
            :ref="(el) => setIssueTargetRef(`arg:${arg.name}:filters`, el)"
            class="field-block"
            :class="{ focused: isIssueTargetActive(`arg:${arg.name}:filters`) }"
          >
            <div class="section-head">
              <div class="field-label">列表过滤</div>
              <div class="row-actions">
                <button type="button" class="btn mini" @click="addListFilterOp(arg.name)">+ 过滤条件</button>
              </div>
            </div>
            <div class="muted tiny">过滤表达式面向当前列表项编写，例如 `item.enabled == true`、`item.qty > 0`。</div>
            <div v-if="getListFilterOps(arg).length === 0" class="muted tiny">当前未配置过滤条件，将透传全部列表项。</div>
            <div
              v-for="(op, index) in getListFilterOps(arg)"
              :key="op.id || `${arg.name}_filter_${index}`"
              class="field-row-card"
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:filter:${index}`, el)"
              :class="{ focused: isIssueTargetActive(`arg:${arg.name}:filter:${index}`) }"
            >
              <div class="field-row-head">
                <div class="muted tiny">过滤 {{ index + 1 }}</div>
                <button type="button" class="btn mini" @click="removeListFilterOp(arg.name, op.id)">删</button>
              </div>
              <textarea
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:filter:${index}:expression`, el)"
                class="input textarea mini-textarea"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:filter:${index}:expression`) }"
                v-model="op.expression"
                placeholder='例如 item.enabled == true && item.stock > 0'
              ></textarea>
            </div>
          </div>

          <div
            v-if="canShowItemBuilder(arg.name)"
            :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item`, el)"
            class="field-block"
            :class="{ focused: isIssueTargetActive(`arg:${arg.name}:item`) }"
          >
            <div class="section-head">
              <div class="field-label">元素结构映射</div>
              <div class="row-actions">
                <button type="button" class="btn mini" @click="rebuildListItemFromSchema(arg.name)">按 Schema 重建</button>
                <button type="button" class="btn mini" @click="addListItemField(arg.name)">+ 元素字段</button>
              </div>
            </div>
            <div class="muted tiny">一期只支持单层列表。元素字段可以来自当前 item、本次请求、上下文、临时变量或上游节点输出。</div>
            <div
              v-for="(field, index) in getListItemFieldList(arg)"
              :key="`${arg.name}_item_${index}`"
              class="field-row-card"
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item-field:${index}`, el)"
              :class="{ focused: isIssueTargetActive(`arg:${arg.name}:item-field:${index}`) }"
            >
              <div class="field-row-head">
                <div class="muted tiny">元素字段 {{ index + 1 }}</div>
                <button type="button" class="btn mini" @click="removeListItemField(arg.name, index)">删</button>
              </div>
              <input
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item-field:${index}:path`, el)"
                class="input"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:item-field:${index}:path`) }"
                v-model="field.path"
                placeholder="元素字段路径，例如 skuId / profile.name"
              />
              <select
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item-field:${index}:source-type`, el)"
                class="input"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:item-field:${index}:source-type`) }"
                v-model="getFieldSourceValue(field).sourceType"
              >
                <option v-for="option in fieldSourceOptions('listItem')" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <div
                v-if="getFieldSourceValue(field).sourceType !== 'const'"
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item-field:${index}:source`, el)"
                class="focus-anchor"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:item-field:${index}:source`) }"
              >
                <SourcePathInput
                  :model-value="getFieldSourceValue(field).path || ''"
                  :options="getSourceOptionsForNode(getFieldSourceValue(field).sourceType, 'listItem')"
                  :placeholder="getSourcePlaceholder(getFieldSourceValue(field).sourceType, field.path || 'skuId')"
                  @update:model-value="getFieldSourceValue(field).path = $event"
                />
              </div>
              <textarea
                v-else
                :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item-field:${index}:source`, el)"
                class="input textarea mini-textarea"
                :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:item-field:${index}:source`) }"
                v-model="getFieldSourceValue(field).constValue"
                placeholder='元素常量，例如 "DEFAULT" / 0'
              ></textarea>
            </div>
          </div>

          <div
            v-else-if="canShowPrimitiveListItemMapper(arg.name)"
            :ref="(el) => setIssueTargetRef(`arg:${arg.name}:item`, el)"
            class="field-block"
            :class="{ focused: isIssueTargetActive(`arg:${arg.name}:item`) }"
          >
            <div class="section-head">
              <div class="field-label">元素取值映射</div>
              <div class="row-actions">
                <button type="button" class="btn mini" @click="clearPrimitiveListItemSource(arg.name)">恢复透传</button>
              </div>
            </div>
            <div class="muted tiny">适用于 `List&lt;String&gt;`、`List&lt;Long&gt;` 或从对象数组中提取单个字段。当前仅支持从当前列表项取值。</div>
            <select
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:primitive-item:type`, el)"
              class="input"
              :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:primitive-item:type`) }"
              v-model="getPrimitiveListItemSource(arg).sourceType"
            >
              <option value="item">当前列表项</option>
            </select>
            <div
              :ref="(el) => setIssueTargetRef(`arg:${arg.name}:primitive-item:value`, el)"
              class="focus-anchor"
              :class="{ focused: isIssueTargetFocused(`arg:${arg.name}:primitive-item:value`) }"
            >
              <SourcePathInput
                :model-value="getPrimitiveListItemSource(arg).path || ''"
                :options="getSourceOptionsForNode('item', 'listItem')"
                :placeholder="getSourcePlaceholder('item', 'item.id / $.id')"
                @update:model-value="getPrimitiveListItemSource(arg).path = $event"
              />
            </div>
          </div>

          <div class="warn-inline">
            List 高级操作（groupBy / reduce / flatten）和嵌套数组场景请切换到高级模式。
          </div>
        </template>

        <template v-else>
          <div class="warn-inline">当前参数存在基础装配器未覆盖的高级节点，建议切换到高级模式处理。</div>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.param-assembler-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.flow-banner,
.unsupported-panel,
.validation-card,
.empty-card,
.info-card,
.temp-panel,
.arg-card {
  border: 1px solid #dbe2ea;
  border-radius: 12px;
  background: #ffffff;
  padding: 12px;
}

.flow-banner {
  background: linear-gradient(180deg, #f8fbff 0%, #ffffff 100%);
}

.flow-title,
.panel-title,
.arg-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.flow-steps,
.context-summary,
.toolbar-actions,
.row-actions,
.validation-summary {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.step-pill,
.summary-chip,
.type-chip,
.required-chip {
  font-size: 11px;
  border-radius: 999px;
  padding: 3px 9px;
  border: 1px solid #dbe2ea;
  background: #f8fafc;
  color: #475569;
}

.step-pill.ready,
.summary-chip.ready {
  color: #0b3b8a;
  border-color: rgba(37, 99, 235, 0.25);
  background: rgba(37, 99, 235, 0.08);
}

.type-chip {
  color: #1d4ed8;
  background: rgba(219, 234, 254, 0.7);
  border-color: rgba(37, 99, 235, 0.22);
}

.required-chip {
  color: #991b1b;
  background: rgba(254, 226, 226, 0.8);
  border-color: rgba(239, 68, 68, 0.3);
}

.required-chip.optional {
  color: #475569;
  background: #f8fafc;
  border-color: #dbe2ea;
}

.unsupported-list,
.issue-list,
.arg-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.temp-panel,
.temp-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.temp-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fbfdff;
  padding: 10px;
}

.temp-card.warning {
  border-color: rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.82);
}

.temp-card.focused,
.arg-card.focused {
  border-color: rgba(37, 99, 235, 0.45);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14);
}

.temp-meta-row,
.plan-warnings {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.plan-warning {
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 11px;
  line-height: 1.4;
  color: #92400e;
  border: 1px solid rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.88);
}

.temp-usage-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.temp-usage-item {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(37, 99, 235, 0.16);
  background: rgba(239, 246, 255, 0.72);
}

.temp-usage-label {
  font-size: 11px;
  color: #1e3a8a;
}

.temp-usage-path {
  font-size: 11px;
  color: #475569;
  word-break: break-all;
}

.unsupported-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 12px;
  line-height: 1.5;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  flex-wrap: wrap;
}

.info-card {
  background: #f8fbff;
}

.info-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.error-card,
.issue-item.error,
.arg-card.error {
  border: 1px solid rgba(239, 68, 68, 0.35);
  background: rgba(254, 242, 242, 0.85);
  color: #991b1b;
}

.validation-card.pass {
  border-color: rgba(16, 185, 129, 0.32);
  background: rgba(236, 253, 245, 0.82);
}

.issue-item.warning,
.arg-card.warning,
.warn-inline {
  border: 1px solid rgba(245, 158, 11, 0.35);
  background: rgba(255, 251, 235, 0.85);
  color: #92400e;
}

.issue-item {
  border-radius: 10px;
  padding: 8px 10px;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 6px 10px;
  align-items: start;
}

.issue-item.clickable {
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.issue-item.clickable:hover,
.issue-item.clickable:focus-visible {
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  outline: none;
}

.issue-code {
  font-size: 10px;
  font-weight: 700;
}

.issue-message,
.issue-path,
.issue-hint {
  font-size: 11px;
  word-break: break-word;
}

.issue-path {
  grid-column: 2;
  opacity: 0.75;
}

.issue-hint {
  grid-column: 2;
  color: #475569;
}

.issue-action {
  grid-column: 3;
  grid-row: 1 / span 3;
  align-self: center;
  white-space: nowrap;
}

.preview-list,
.preview-call-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.preview-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fbfdff;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-head,
.preview-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.preview-label,
.preview-call-name {
  font-size: 11px;
  font-weight: 600;
  color: #334155;
}

.preview-chips {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.preview-call-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: rgba(239, 246, 255, 0.58);
}

.arg-head,
.section-head,
.field-row-head,
.arg-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.arg-meta,
.field-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.field-block.focused,
.field-row-card.focused,
.focus-anchor.focused {
  border-color: rgba(37, 99, 235, 0.4);
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}

.field-row-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fbfdff;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.focus-anchor {
  border: 1px solid transparent;
  border-radius: 10px;
}

.input {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
}

.input.focused {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.14);
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.textarea {
  min-height: 80px;
  resize: vertical;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 11px;
}

.mini-textarea {
  min-height: 56px;
}

.muted {
  color: #64748b;
  font-size: 12px;
}

.muted.tiny {
  font-size: 11px;
}

.error-count {
  color: #b91c1c;
  font-size: 11px;
}

.warning-count {
  color: #92400e;
  font-size: 11px;
}

.btn.mini.primary {
  border-color: #2563eb;
  color: #1d4ed8;
  background: #eff6ff;
}

.warn-inline {
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 11px;
  line-height: 1.45;
}

@media (max-width: 760px) {
  .toolbar {
    flex-direction: column;
  }
}
</style>
