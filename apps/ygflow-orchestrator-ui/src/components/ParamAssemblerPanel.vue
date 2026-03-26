<script setup lang="ts">
import { computed, ref, watch } from "vue"
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
  source: SourceNode
  item?: ValueNode
  ops?: Array<Record<string, any>>
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
let requestSeq = 0

function toText(value: unknown): string {
  return typeof value === "string" ? value.trim() : ""
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
      source: normalizeSourceNode(raw.source, guessSourcePath(meta.name)),
      item: itemNode,
      ops: Array.isArray(raw.ops) ? clone(raw.ops) : [],
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

function compileListNodeToPlan(arg: ArgAstNode, value: ListNode): Record<string, any> {
  const itemMeta = findArgMeta(arg.name)
  const composeFields = value.item && isRecord(value.item) && toText(value.item.kind).toLowerCase() === "object"
    ? (Array.isArray((value.item as ObjectNode).fields) ? (value.item as ObjectNode).fields : []).map((field) => ({
      id: buildFieldId("lf"),
      targetField: toText(field.path),
      typeHint: "",
      source: compileSourceToLegacy(normalizeSourceNode(field.value, ""), "listItem"),
    }))
    : []

  return {
    target: arg.name,
    typeHint: arg.javaType || itemMeta.javaType || "",
    source: {
      kind: "listPipeline",
      path: "",
      constValue: "",
      tempKey: "",
    },
    listInput: compileSourceToLegacy(value.source),
    listCompose: {
      itemTypeHint: deriveListItemType(arg.javaType || itemMeta.javaType || "", itemMeta.schema || null),
      fields: composeFields,
    },
    listSteps: [],
  }
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

function getArgCardState(arg: ArgAstNode) {
  const hits = issues.value.filter((item) => item.path.includes(arg.name))
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
    const itemFields = listValue.item && isRecord(listValue.item) && toText(listValue.item.kind).toLowerCase() === "object"
      ? (listValue.item as ObjectNode).fields.length
      : 0
    return itemFields > 0 ? `单层列表 / ${itemFields} 个元素字段` : "单层列表 / 直接透传"
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
        <button type="button" class="btn mini primary" :disabled="validating || !projectKey || localAst.args.length === 0" @click="validateAst">
          {{ validating ? "校验中..." : "立即校验" }}
        </button>
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
        <div v-for="item in issues" :key="`${item.code}_${item.path}_${item.message}`" class="issue-item" :class="item.severity">
          <span class="issue-code">{{ item.code }}</span>
          <span class="issue-message">{{ item.message }}</span>
          <span class="issue-path">{{ item.path }}</span>
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
      <div v-for="(temp, tempIndex) in localAst.temps" :key="`${temp.key}_${tempIndex}`" class="temp-card" :class="getTempCardState(tempIndex)">
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
        <input class="input" v-model="temp.key" placeholder="临时变量 key，例如 profile / skuCatalog / priceProfile" />
        <input
          class="input"
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
        <ServiceCallEditor
          :model-value="getTempCallEditorModel(temp)"
          :project-key="projectKey"
          :temp-keys="tempPathOptions.map((item) => item.replace(/^temp\./, ''))"
          :source-path-options="contextSourcePaths"
          @update:model-value="handleTempCallModelUpdate(tempIndex, $event)"
        />
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
      <div v-for="arg in localAst.args" :key="arg.name" class="arg-card" :class="getArgCardState(arg)">
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
          <div class="field-block">
            <div class="field-label">来源配置</div>
            <select class="input" v-model="getArgSourceValue(arg).sourceType">
              <option v-for="option in fieldSourceOptions('arg')" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <SourcePathInput
              v-if="getArgSourceValue(arg).sourceType !== 'const'"
              :model-value="getArgSourceValue(arg).path || ''"
              :options="getSourceOptionsForNode(getArgSourceValue(arg).sourceType, 'arg')"
              :placeholder="getSourcePlaceholder(getArgSourceValue(arg).sourceType, guessSourcePath(arg.name))"
              @update:model-value="getArgSourceValue(arg).path = $event"
            />
            <textarea
              v-else
              class="input textarea"
              v-model="getArgSourceValue(arg).constValue"
              placeholder='常量值，例如 "A100" / 1 / {"id":"u1"} / [{"id":"1"}]'
            ></textarea>
          </div>
        </template>

        <template v-else-if="arg.value.kind === 'object'">
          <div class="field-block">
            <div class="section-head">
              <div class="field-label">对象字段映射</div>
              <div class="row-actions">
                <button type="button" class="btn mini" @click="rebuildObjectFromSchema(arg.name)">按 Schema 重建</button>
                <button type="button" class="btn mini" @click="addObjectField(arg.name)">+ 字段</button>
              </div>
            </div>
            <div class="muted tiny">对象参数按字段展开显示，避免用户直接面对整段 JSON。</div>
            <div v-for="(field, index) in getObjectFieldList(arg)" :key="`${arg.name}_${index}`" class="field-row-card">
              <div class="field-row-head">
                <div class="muted tiny">字段 {{ index + 1 }}</div>
                <button type="button" class="btn mini" @click="removeObjectField(arg.name, index)">删</button>
              </div>
              <input class="input" v-model="field.path" placeholder="字段路径，例如 profile.name / address.city" />
              <select class="input" v-model="getFieldSourceValue(field).sourceType">
                <option v-for="option in fieldSourceOptions('arg')" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <SourcePathInput
                v-if="getFieldSourceValue(field).sourceType !== 'const'"
                :model-value="getFieldSourceValue(field).path || ''"
                :options="getSourceOptionsForNode(getFieldSourceValue(field).sourceType, 'arg')"
                :placeholder="getSourcePlaceholder(getFieldSourceValue(field).sourceType, guessSourcePath(field.path || arg.name))"
                @update:model-value="getFieldSourceValue(field).path = $event"
              />
              <textarea
                v-else
                class="input textarea mini-textarea"
                v-model="getFieldSourceValue(field).constValue"
                placeholder='常量值，例如 "Tom" / 18 / {"code":"VIP"}'
              ></textarea>
            </div>
          </div>
        </template>

        <template v-else-if="arg.value.kind === 'list'">
          <div class="field-block">
            <div class="field-label">列表来源</div>
            <select class="input" v-model="getListValue(arg).source.sourceType">
              <option v-for="option in fieldSourceOptions('arg')" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <SourcePathInput
              v-if="getListValue(arg).source.sourceType !== 'const'"
              :model-value="getListValue(arg).source.path || ''"
              :options="getSourceOptionsForNode(getListValue(arg).source.sourceType, 'arg')"
              :placeholder="getSourcePlaceholder(getListValue(arg).source.sourceType, guessSourcePath(arg.name))"
              @update:model-value="getListValue(arg).source.path = $event"
            />
            <textarea
              v-else
              class="input textarea"
              v-model="getListValue(arg).source.constValue"
              placeholder='常量列表，例如 [{"skuId":"A100"}]'
            ></textarea>
          </div>

          <div v-if="canShowItemBuilder(arg.name)" class="field-block">
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
            >
              <div class="field-row-head">
                <div class="muted tiny">元素字段 {{ index + 1 }}</div>
                <button type="button" class="btn mini" @click="removeListItemField(arg.name, index)">删</button>
              </div>
              <input class="input" v-model="field.path" placeholder="元素字段路径，例如 skuId / profile.name" />
              <select class="input" v-model="getFieldSourceValue(field).sourceType">
                <option v-for="option in fieldSourceOptions('listItem')" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
              <SourcePathInput
                v-if="getFieldSourceValue(field).sourceType !== 'const'"
                :model-value="getFieldSourceValue(field).path || ''"
                :options="getSourceOptionsForNode(getFieldSourceValue(field).sourceType, 'listItem')"
                :placeholder="getSourcePlaceholder(getFieldSourceValue(field).sourceType, field.path || 'skuId')"
                @update:model-value="getFieldSourceValue(field).path = $event"
              />
              <textarea
                v-else
                class="input textarea mini-textarea"
                v-model="getFieldSourceValue(field).constValue"
                placeholder='元素常量，例如 "DEFAULT" / 0'
              ></textarea>
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
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px 10px;
  align-items: start;
}

.issue-code {
  font-size: 10px;
  font-weight: 700;
}

.issue-message,
.issue-path {
  font-size: 11px;
  word-break: break-word;
}

.issue-path {
  grid-column: 2;
  opacity: 0.75;
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
