<script setup lang="ts">
import { computed, ref, reactive, onUnmounted, nextTick, watch } from "vue"
import { EditorView, keymap, lineNumbers, drawSelection, highlightActiveLine } from "@codemirror/view"
import { EditorState } from "@codemirror/state"
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands"
import type { FlowModel, FlowResolver } from "../api/client"

interface Props {
  selectedNode: any | null
  nodes?: any[] | null
  edges?: any[] | null
  endpointSchema?: {
    requestSchema?: Array<Record<string, any>> | null
    responseSchema?: { type?: string | null } | null
  } | null
  entrypointPath?: string | null
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
}

interface Emits {
  (e: "update-node", node: any): void
}

type SourceMode = "schema" | "resolver" | "expression"
type ValueCast =
  | "AUTO"
  | "STRING"
  | "BOOLEAN"
  | "INTEGER"
  | "LONG"
  | "DOUBLE"
  | "DECIMAL"

interface FieldStepForm {
  id: string
  target: string
  sourceMode: SourceMode
  schemaPath?: string
  resolverRef?: string
  expression?: string
  defaultValue?: string
  required: boolean
  cast: ValueCast
}

interface CollectionStepForm {
  id: string
  target: string
  sourceMode: SourceMode
  schemaPath?: string
  resolverRef?: string
  expression?: string
  itemAlias: string
  filter?: string
  itemFields: FieldStepForm[]
}

interface ConditionStepForm {
  id: string
  when: string
  thenFields: FieldStepForm[]
  elseFields: FieldStepForm[]
}

interface TransformerEditorState {
  fields: FieldStepForm[]
  collections: CollectionStepForm[]
  conditions: ConditionStepForm[]
}

interface OutputModelState {
  identifier: string
  name?: string
  className?: string
  version?: string
  description?: string
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const isTransformerNode = computed(() => props.selectedNode?.type === "transformer")
const outputType = computed(() => props.selectedNode?.data?.outputType || "object")

const activeStepTab = ref<"fields" | "collections" | "conditions">("fields")
const schemaQuickPick = ref("")
const showGuide = ref(false)
const editorState = reactive<TransformerEditorState>({
  fields: [],
  collections: [],
  conditions: [],
})
const isHydrating = ref(false)
const isApplying = ref(false)

function createId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID()
  }
  return Math.random().toString(36).slice(2, 10)
}

function cloneNode() {
  return JSON.parse(JSON.stringify(props.selectedNode))
}

function mutateNode(updater: (next: any) => void) {
  if (!props.selectedNode) return
  const next = cloneNode()
  updater(next)
  emit("update-node", next)
}

function updateNodeField(key: string, value: any) {
  mutateNode((next) => {
    next.data ||= {}
    next.data[key] = value
  })
}

/* ---------------- Schema & Resolver helpers ---------------- */
function toSchemaPath(field: Record<string, any>): string {
  const source = field.source || "body"
  if (source === "path") {
    return `request.path.${field.pathVariable || field.name}`
  }
  if (source === "query") {
    return `request.query.${field.paramName || field.name}`
  }
  if (source === "header") {
    return `request.headers.${field.name}`
  }
  if (source === "form") {
    return `request.form.${field.name}`
  }
  return `request.body.${field.name}`
}

const schemaOptions = computed(() => {
  const schema = props.endpointSchema?.requestSchema ?? []
  if (!schema) return []
  return schema
    .filter((item) => !!item?.name)
    .map((item) => ({
      label: `${item.name}${item.source ? ` · ${item.source}` : ""}`,
      value: toSchemaPath(item),
      raw: item,
    }))
})

const resolverOptions = computed(() => {
  const resolverList: string[] =
    props.selectedNode?.data?.availableResolvers ??
    props.selectedNode?.data?.resolverRefs ??
    []
  return resolverList.map((name: string) => ({
    label: name,
    value: name,
  }))
})

const hasParamResolvers = computed(() => resolverOptions.value.length > 0)

const availableFlowModels = computed(() => props.flowModels ?? [])
const flowModelOptions = computed(() =>
  availableFlowModels.value.map((model) => {
    const value = `model:${model.identifier}`
    const versionLabel = model.version && model.version.trim().length ? ` · v${model.version}` : ""
    return {
      value,
      label: `${model.name || model.identifier}${versionLabel}`,
      model,
    }
  })
)
const flowModelOptionMap = computed(() => {
  const map = new Map<string, FlowModel>()
  flowModelOptions.value.forEach((item) => map.set(item.value, item.model))
  return map
})
const currentOutputModel = computed<OutputModelState | null>(
  () => props.selectedNode?.data?.outputModel ?? null
)
const selectedOutputModel = computed(() => {
  const state = currentOutputModel.value
  if (!state?.identifier) return null
  const value = `model:${state.identifier}`
  return flowModelOptionMap.value.get(value) ?? state
})

const selectedOutputModelDisplay = computed(() => {
  const model = selectedOutputModel.value as (FlowModel | OutputModelState | null)
  if (!model) return null
  const identifier = (model as any).identifier || ""
  return {
    name: (model as any).name || identifier,
    version: ((model as any).version || "").trim(),
    className: (model as any).className || identifier,
    description: (model as any).description || "",
  }
})

const resolverCatalog = computed(() => props.flowResolvers ?? [])

type OutputSelectOption = { value: string; label: string; disabled?: boolean }
const MODEL_DIVIDER_VALUE = "__divider_flow_model"

function setOutputModel(model: FlowModel | null) {
  mutateNode((next) => {
    next.data ||= {}
    if (!model) {
      if (next.data.outputModel) {
        delete next.data.outputModel
      }
      return
    }
    const payload: OutputModelState = {
      identifier: model.identifier,
      name: model.name || model.identifier,
      className: model.className,
      version: model.version || undefined,
      description: model.description || undefined,
    }
    const current = next.data.outputModel as OutputModelState | undefined
    if (
      !current ||
      current.identifier !== payload.identifier ||
      current.name !== payload.name ||
      current.className !== payload.className ||
      current.version !== payload.version ||
      current.description !== payload.description
    ) {
      next.data.outputModel = payload
    }
  })
}

const castOptions: Array<{ value: ValueCast; label: string }> = [
  { value: "AUTO", label: "自动" },
  { value: "STRING", label: "String" },
  { value: "BOOLEAN", label: "Boolean" },
  { value: "INTEGER", label: "Integer" },
  { value: "LONG", label: "Long" },
  { value: "DOUBLE", label: "Double" },
  { value: "DECIMAL", label: "BigDecimal" },
]

/* ---------------- DSL ↔︎ 表单状态转换 ---------------- */
function createFieldStep(partial?: Partial<FieldStepForm>): FieldStepForm {
  return {
    id: createId(),
    target: "",
    sourceMode: "schema",
    schemaPath: "",
    resolverRef: "",
    expression: "",
    defaultValue: "",
    required: false,
    cast: "AUTO",
    ...partial,
  }
}

function createCollectionStep(partial?: Partial<CollectionStepForm>): CollectionStepForm {
  return {
    id: createId(),
    target: "",
    sourceMode: "schema",
    schemaPath: "",
    resolverRef: "",
    expression: "",
    itemAlias: "item",
    filter: "",
    itemFields: [],
    ...partial,
  }
}

function createConditionStep(partial?: Partial<ConditionStepForm>): ConditionStepForm {
  return {
    id: createId(),
    when: "",
    thenFields: [],
    elseFields: [],
    ...partial,
  }
}

function detectSourceMode(payload: any): SourceMode {
  if (payload?.resolverRef) return "resolver"
  if (payload?.expression) return "expression"
  return "schema"
}

function loadFromNode(node: any | null) {
  if (!node) {
    editorState.fields.splice(0, editorState.fields.length)
    editorState.collections.splice(0, editorState.collections.length)
    editorState.conditions.splice(0, editorState.conditions.length)
    return
  }

  const rawTemplate = node.data?.transformerDsl
  const template =
    typeof rawTemplate === "string"
      ? (() => {
          try {
            return JSON.parse(rawTemplate)
          } catch {
            return null
          }
        })()
      : rawTemplate

  const nextFields: FieldStepForm[] = []
  const nextCollections: CollectionStepForm[] = []
  const nextConditions: ConditionStepForm[] = []

  if (template?.steps && Array.isArray(template.steps)) {
    template.steps.forEach((step: Record<string, any>) => {
      const type = (step.type || step.stepType || "").toLowerCase()
      if (type === "field") {
        nextFields.push(
          createFieldStep({
            target: step.target || "",
            sourceMode: detectSourceMode(step),
            schemaPath: step.source || "",
            resolverRef: step.resolverRef || "",
            expression: step.expression || "",
            defaultValue: step.defaultValue || "",
            required: !!step.required,
            cast: (step.cast || "AUTO").toUpperCase() as ValueCast,
          })
        )
      } else if (type === "collection") {
        const itemFields = Array.isArray(step.itemSteps)
          ? step.itemSteps
              .filter((child: any) => (child.type || "").toLowerCase() === "field")
              .map((child: any) =>
                createFieldStep({
                  target: child.target || "",
                  sourceMode: detectSourceMode(child),
                  schemaPath: child.source || "",
                  resolverRef: child.resolverRef || "",
                  expression: child.expression || "",
                  defaultValue: child.defaultValue || "",
                  required: !!child.required,
                  cast: (child.cast || "AUTO").toUpperCase() as ValueCast,
                })
              )
          : []
        nextCollections.push(
          createCollectionStep({
            target: step.target || "",
            sourceMode: detectSourceMode(step),
            schemaPath: step.source || "",
            resolverRef: step.resolverRef || "",
            expression: step.expression || "",
            itemAlias: step.itemAlias || "item",
            filter: step.filter || "",
            itemFields,
          })
        )
      } else if (type === "condition") {
        const thenFields = Array.isArray(step.thenSteps)
          ? step.thenSteps
              .filter((child: any) => (child.type || "").toLowerCase() === "field")
              .map((child: any) =>
                createFieldStep({
                  target: child.target || "",
                  sourceMode: detectSourceMode(child),
                  schemaPath: child.source || "",
                  resolverRef: child.resolverRef || "",
                  expression: child.expression || "",
                  defaultValue: child.defaultValue || "",
                  required: !!child.required,
                  cast: (child.cast || "AUTO").toUpperCase() as ValueCast,
                })
              )
          : []
        const elseFields = Array.isArray(step.elseSteps)
          ? step.elseSteps
              .filter((child: any) => (child.type || "").toLowerCase() === "field")
              .map((child: any) =>
                createFieldStep({
                  target: child.target || "",
                  sourceMode: detectSourceMode(child),
                  schemaPath: child.source || "",
                  resolverRef: child.resolverRef || "",
                  expression: child.expression || "",
                  defaultValue: child.defaultValue || "",
                  required: !!child.required,
                  cast: (child.cast || "AUTO").toUpperCase() as ValueCast,
                })
              )
          : []
        nextConditions.push(
          createConditionStep({
            when: step.when || "",
            thenFields,
            elseFields,
          })
        )
      }
    })
  } else {
    // 回退到旧的 mappingConfig
    const legacyMappings = node.data?.mappingConfig?.fieldMappings ?? []
    legacyMappings.forEach((mapping: Record<string, any>) => {
      nextFields.push(
        createFieldStep({
          target: mapping.targetField || "",
          sourceMode: mapping.resolverRef ? "resolver" : mapping.expression ? "expression" : "schema",
          schemaPath: mapping.sourceField || "",
          resolverRef: mapping.resolverRef || "",
          expression: mapping.expression || "",
          defaultValue: mapping.defaultValue || "",
          required: !!mapping.required,
          cast: (mapping.cast || "AUTO").toUpperCase() as ValueCast,
        })
      )
    })
  }

  editorState.fields.splice(0, editorState.fields.length, ...nextFields)
  editorState.collections.splice(0, editorState.collections.length, ...nextCollections)
  editorState.conditions.splice(0, editorState.conditions.length, ...nextConditions)
}

function buildFieldStepPayload(step: FieldStepForm) {
  const payload: Record<string, any> = {
    type: "field",
    target: step.target || "",
    defaultValue: step.defaultValue ?? "",
    required: step.required,
  }
  if (step.cast && step.cast !== "AUTO") {
    payload.cast = step.cast
  }
  if (step.sourceMode === "schema") {
    payload.source = step.schemaPath || ""
  } else if (step.sourceMode === "resolver") {
    payload.resolverRef = step.resolverRef || ""
  } else if (step.sourceMode === "expression") {
    payload.expression = step.expression || ""
  }
  return payload
}

function buildTemplateFromState() {
  const steps: any[] = []
  editorState.fields.forEach((field) => {
    steps.push(buildFieldStepPayload(field))
  })
  editorState.collections.forEach((collection) => {
    const payload: Record<string, any> = {
      type: "collection",
      target: collection.target || "",
      itemAlias: collection.itemAlias || "item",
      filter: collection.filter || "",
      itemSteps: collection.itemFields.map((field) => buildFieldStepPayload(field)),
    }
    if (collection.sourceMode === "schema") {
      payload.source = collection.schemaPath || ""
    } else if (collection.sourceMode === "resolver") {
      payload.resolverRef = collection.resolverRef || ""
    } else if (collection.sourceMode === "expression") {
      payload.expression = collection.expression || ""
    }
    steps.push(payload)
  })
  editorState.conditions.forEach((condition) => {
    steps.push({
      type: "condition",
      when: condition.when || "",
      thenSteps: condition.thenFields.map((field) => buildFieldStepPayload(field)),
      elseSteps: condition.elseFields.map((field) => buildFieldStepPayload(field)),
    })
  })

  return {
    name: props.selectedNode?.data?.label || "Transformer",
    outputMode: outputType.value === "single" ? "SINGLE" : "OBJECT",
    outputModel: props.selectedNode?.data?.outputModel ?? null,
    steps,
  }
}

function applyStateToNode() {
  if (isHydrating.value || !props.selectedNode) return
  const template = buildTemplateFromState()
  isApplying.value = true
  mutateNode((next) => {
    next.data ||= {}
    next.data.transformerDsl = template
    const legacyMappings = template.steps
      .filter((step: any) => step.type === "field")
      .map((step: any) => ({
        sourceField: step.source || "",
        targetField: step.target || "",
        resolverRef: step.resolverRef || "",
        expression: step.expression || "",
        defaultValue: step.defaultValue || "",
        required: !!step.required,
        cast: step.cast || "AUTO",
      }))
    next.data.mappingConfig ||= {}
    next.data.mappingConfig.fieldMappings = legacyMappings
  })
  nextTick(() => {
    isApplying.value = false
  })
}

watch(
  () => props.selectedNode,
  (node) => {
    if (!node) return
    if (isApplying.value) return
    isHydrating.value = true
    loadFromNode(node)
    nextTick(() => {
      isHydrating.value = false
    })
  },
  { immediate: true }
)

watch(
  editorState,
  () => {
    applyStateToNode()
  },
  { deep: true }
)

/* ---------------- 添加/移除操作 ---------------- */
function addFieldStepFromSchema(path?: string) {
  const schemaOption = schemaOptions.value.find((item) => item.value === path)
  editorState.fields.push(
    createFieldStep({
      target: schemaOption ? schemaOption.raw?.name || "" : "",
      sourceMode: "schema",
      schemaPath: schemaOption ? schemaOption.value : "",
    })
  )
}

watch(schemaQuickPick, (value) => {
  if (!value) return
  addFieldStepFromSchema(value)
  schemaQuickPick.value = ""
})

function addFieldStep() {
  editorState.fields.push(createFieldStep())
}

function removeFieldStep(id: string) {
  const index = editorState.fields.findIndex((item) => item.id === id)
  if (index >= 0) {
    editorState.fields.splice(index, 1)
  }
}

function addCollectionStep() {
  editorState.collections.push(createCollectionStep())
}

function removeCollectionStep(id: string) {
  const index = editorState.collections.findIndex((item) => item.id === id)
  if (index >= 0) {
    editorState.collections.splice(index, 1)
  }
}

function addCollectionItemField(collectionId: string) {
  const collection = editorState.collections.find((item) => item.id === collectionId)
  if (!collection) return
  collection.itemFields.push(createFieldStep())
}

function removeCollectionItemField(collectionId: string, fieldId: string) {
  const collection = editorState.collections.find((item) => item.id === collectionId)
  if (!collection) return
  const idx = collection.itemFields.findIndex((item) => item.id === fieldId)
  if (idx >= 0) {
    collection.itemFields.splice(idx, 1)
  }
}

function addConditionStep() {
  editorState.conditions.push(createConditionStep())
}

function removeConditionStep(id: string) {
  const index = editorState.conditions.findIndex((item) => item.id === id)
  if (index >= 0) {
    editorState.conditions.splice(index, 1)
  }
}

function addConditionField(conditionId: string, branch: "then" | "else") {
  const condition = editorState.conditions.find((item) => item.id === conditionId)
  if (!condition) return
  if (branch === "then") {
    condition.thenFields.push(createFieldStep())
  } else {
    condition.elseFields.push(createFieldStep())
  }
}

function removeConditionField(conditionId: string, branch: "then" | "else", fieldId: string) {
  const condition = editorState.conditions.find((item) => item.id === conditionId)
  if (!condition) return
  const targetArray = branch === "then" ? condition.thenFields : condition.elseFields
  const idx = targetArray.findIndex((item) => item.id === fieldId)
  if (idx >= 0) {
    targetArray.splice(idx, 1)
  }
}

/* ---------------- JSON 预览 & 校验 ---------------- */
const validationMessages = computed(() => {
  const issues: string[] = []
  editorState.fields.forEach((field, index) => {
    if (!field.target) {
      issues.push(`字段映射 #${index + 1} 缺少目标字段`)
    }
    if (field.sourceMode === "schema" && !field.schemaPath) {
      issues.push(`字段映射 #${index + 1} 未选择 Schema 来源`)
    }
    if (field.sourceMode === "resolver" && !field.resolverRef) {
      issues.push(`字段映射 #${index + 1} 未选择 Resolver`)
    }
    if (field.sourceMode === "expression" && !field.expression) {
      issues.push(`字段映射 #${index + 1} 缺少表达式`)
    }
  })
  editorState.collections.forEach((collection, index) => {
    if (!collection.target) {
      issues.push(`集合步骤 #${index + 1} 缺少目标字段`)
    }
    if (collection.sourceMode === "schema" && !collection.schemaPath) {
      issues.push(`集合步骤 #${index + 1} 未选择集合来源`)
    }
    if (collection.itemFields.length === 0) {
      issues.push(`集合步骤 #${index + 1} 至少需要一个子字段映射`)
    }
  })
  editorState.conditions.forEach((condition, index) => {
    if (!condition.when) {
      issues.push(`条件步骤 #${index + 1} 缺少条件表达式`)
    }
    if (condition.thenFields.length === 0 && condition.elseFields.length === 0) {
      issues.push(`条件步骤 #${index + 1} 至少需要一个 THEN 或 ELSE 动作`)
    }
  })
  return issues
})

const previewJson = computed(() => {
  const template = buildTemplateFromState()
  try {
    return JSON.stringify(template, null, 2)
  } catch {
    return "{}"
  }
})

/* ---------------- 输出类型 & REST 帮助 ---------------- */
const upstreamOutputType = computed(() => {
  if (!props.selectedNode || !props.edges || !props.nodes) {
    return null
  }
  const incomingEdge = props.edges.find((edge: any) => edge.target === props.selectedNode?.id)
  if (!incomingEdge) {
    return null
  }
  const upstreamNode = props.nodes.find((node: any) => node.id === incomingEdge.source)
  if (!upstreamNode) {
    return null
  }
  const output = upstreamNode.data?.output
  if (!output) {
    return null
  }
  const valueType = (output.valueType || "OBJECT").toUpperCase()
  if (output.typeName) {
    return output.typeName
  }
  if (valueType === "OBJECT" || valueType === "ARRAY") {
    return valueType
  }
  return valueType
})

function parseResponseType(responseType?: string | null) {
  if (!responseType) return null
  const responseEntityMatch = responseType.match(/ResponseEntity\s*<\s*(.+?)\s*>/i)
  if (responseEntityMatch) {
    return {
      isResponseEntity: true,
      bodyType: responseEntityMatch[1].trim(),
    }
  }
  return {
    isResponseEntity: false,
    bodyType: responseType.trim(),
  }
}

function isSimpleType(type: string): boolean {
  const simpleTypes = [
    "String",
    "Integer",
    "Long",
    "Double",
    "Float",
    "Boolean",
    "int",
    "long",
    "double",
    "float",
    "boolean",
    "java.lang.String",
    "java.lang.Integer",
    "java.lang.Long",
    "java.lang.Double",
    "java.lang.Float",
    "java.lang.Boolean",
  ]
  return simpleTypes.some((st) => type.includes(st))
}

const selectedOutputTypeValue = computed(() => {
  const currentModel = currentOutputModel.value
  if (currentModel?.identifier) {
    const modelValue = `model:${currentModel.identifier}`
    if (flowModelOptionMap.value.has(modelValue)) {
      return modelValue
    }
  }
  const currentType = outputType.value
  const responseType = props.endpointSchema?.responseSchema?.type
  if (responseType) {
    const parsed = parseResponseType(responseType)
    if (parsed) {
      if (parsed.isResponseEntity && currentType === "object") {
        return "rest-response-entity"
      }
      if (!parsed.isResponseEntity) {
        if (isSimpleType(parsed.bodyType) && currentType === "single") {
          return "rest-single"
        }
        if (!isSimpleType(parsed.bodyType) && currentType === "object") {
          return "rest-object"
        }
      }
    }
  }
  return currentType
})

const outputTypeOptions = computed<OutputSelectOption[]>(() => {
  const options: OutputSelectOption[] = []
  const responseType = props.endpointSchema?.responseSchema?.type
  if (responseType) {
    const parsed = parseResponseType(responseType)
    if (parsed) {
      if (parsed.isResponseEntity) {
        options.push({
          value: "rest-response-entity",
          label: `REST 返回类型：ResponseEntity<${parsed.bodyType}>`,
        })
      } else if (isSimpleType(parsed.bodyType)) {
        options.push({
          value: "rest-single",
          label: `REST 返回类型：${parsed.bodyType}（单值）`,
        })
      } else {
        options.push({
          value: "rest-object",
          label: `REST 返回类型：${parsed.bodyType}（对象）`,
        })
      }
    }
  }
  options.push(
    { value: "object", label: "对象（Map）" },
    { value: "single", label: "单值（String/Number/Boolean）" }
  )
  if (flowModelOptions.value.length) {
    options.push({ value: MODEL_DIVIDER_VALUE, label: "—— FlowModel ——", disabled: true })
    flowModelOptions.value.forEach((item) => {
      options.push({
        value: item.value,
        label: `FlowModel：${item.label}`,
      })
    })
  }
  return options
})

function updateOutputType(type: string) {
  if (type === MODEL_DIVIDER_VALUE) return
  if (type.startsWith("model:")) {
    const model = flowModelOptionMap.value.get(type) ?? null
    setOutputModel(model)
    updateNodeField("outputType", "object")
    return
  }
  setOutputModel(null)
  let actualType: "object" | "single" = "object"
  if (type === "rest-response-entity" || type === "rest-object") {
    actualType = "object"
  } else if (type === "rest-single") {
    actualType = "single"
  } else {
    actualType = type as "object" | "single"
  }
  updateNodeField("outputType", actualType)
}

/* ---------------- Groovy 脚本编辑器 ---------------- */
const showCodeEditor = ref(false)
const codeEditorContainer = ref<HTMLDivElement | null>(null)
let codeEditorView: EditorView | null = null

function openCodeEditor() {
  showCodeEditor.value = true
  nextTick(() => {
    if (codeEditorContainer.value && !codeEditorView) {
      initCodeEditor()
    } else if (codeEditorView) {
      const currentScript = props.selectedNode?.data?.script || ""
      const transaction = codeEditorView.state.update({
        changes: {
          from: 0,
          to: codeEditorView.state.doc.length,
          insert: currentScript,
        },
      })
      codeEditorView.dispatch(transaction)
    }
  })
}

function closeCodeEditor() {
  showCodeEditor.value = false
  if (codeEditorView) {
    codeEditorView.destroy()
    codeEditorView = null
  }
}

function saveAndCloseEditor() {
  if (codeEditorView) {
    const content = codeEditorView.state.doc.toString()
    updateNodeField("script", content)
    closeCodeEditor()
  }
}

function initCodeEditor() {
  if (!codeEditorContainer.value || codeEditorView) return
  const currentScript = props.selectedNode?.data?.script || ""
  const saveKeymap = keymap.of([
    {
      key: "Mod-s",
      preventDefault: true,
      run: () => {
        saveAndCloseEditor()
        return true
      },
    },
  ])
  const extensions = [
    history(),
    lineNumbers(),
    drawSelection(),
    highlightActiveLine(),
    keymap.of([...defaultKeymap, ...historyKeymap]),
    saveKeymap,
    EditorView.lineWrapping,
    EditorView.theme({
      "&": {
        fontSize: "14px",
        height: "100%",
      },
      ".cm-scroller": {
        fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
        overflow: "auto",
      },
      ".cm-content": {
        minHeight: "400px",
        padding: "12px",
        cursor: "text",
      },
      ".cm-editor": {
        height: "100%",
      },
      ".cm-editor.cm-focused": {
        outline: "none",
      },
      ".cm-editor.cm-focused .cm-content": {
        caretColor: "#2563eb",
      },
      ".cm-line": {
        padding: "0 2px",
      },
    }),
  ]
  const state = EditorState.create({
    doc: currentScript,
    extensions,
  })
  codeEditorView = new EditorView({
    state,
    parent: codeEditorContainer.value,
  })
  nextTick(() => {
    codeEditorView?.focus()
  })
}

watch(
  () => props.selectedNode?.data?.script,
  (newScript) => {
    if (codeEditorView && showCodeEditor.value) {
      const currentContent = codeEditorView.state.doc.toString()
      if (currentContent !== (newScript || "")) {
        const transaction = codeEditorView.state.update({
          changes: {
            from: 0,
            to: codeEditorView.state.doc.length,
            insert: newScript || "",
          },
        })
        codeEditorView.dispatch(transaction)
      }
    }
  }
)

onUnmounted(() => {
  if (codeEditorView) {
    codeEditorView.destroy()
    codeEditorView = null
  }
})
</script>

<template>
  <div v-if="isTransformerNode" class="transformer-editor">
    <div class="section">
      <h3 class="section-title">输入来源</h3>
      <div class="tip">
        <div style="margin-bottom: 4px">
          <strong>说明：</strong>转换器会自动接收上游节点的输出作为 <code>input</code>。若需要使用流程上下文，可通过 <code>ctx</code> 访问。
        </div>
        <div v-if="upstreamOutputType" class="divider-tip">
          <strong>上游节点输出类型：</strong>
          <code class="response-type-code">{{ upstreamOutputType }}</code>
        </div>
        <div v-else class="muted tiny">提示：请先连接上游节点以查看输出类型</div>
      </div>
    </div>

    <div class="section">
      <button class="guide-toggle" type="button" @click="showGuide = !showGuide">
        操作提示
        <span class="guide-caret" :class="{ open: showGuide }">⌄</span>
      </button>
      <div v-if="showGuide" class="guide-tip">
        <ol>
          <li>在右侧“节点配置”里的“输入参数”区域，先为所需字段配置解析方式（ParamResolver）。</li>
          <li>返回本页，选择“字段映射 / 集合处理 / 条件判断”，将解析后的值映射到输出。</li>
          <li>如需额外逻辑，可在下方 Groovy 脚本中补充处理（脚本在映射完成后执行）。</li>
        </ol>
        <div class="muted tiny" style="margin-top: 6px">
          来源类型为 “请求 Schema” 时直接引用接口请求字段；选择 “ParamResolver” 需要先在“输入参数”中创建解析规则。
        </div>
      </div>
      <div v-if="resolverCatalog.length" class="resolver-tip">
        <div class="resolver-tip-title">可用解析器</div>
        <ul>
          <li v-for="item in resolverCatalog" :key="item.id ?? item.type">
            <span class="resolver-name">{{ item.name || item.type }}</span>
            <span class="resolver-type">({{ item.type }})</span>
            <span v-if="item.description" class="resolver-desc">- {{ item.description }}</span>
          </li>
        </ul>
      </div>
    </div>

    <div class="section">
      <h3 class="section-title">输出类型</h3>
      <div class="field-group">
        <label class="field-label">输出格式</label>
        <select
          class="input"
          :value="selectedOutputTypeValue"
          @change="updateOutputType(($event.target as HTMLSelectElement).value)"
        >
          <option
            v-for="option in outputTypeOptions"
            :key="option.value"
            :value="option.value"
            :disabled="option.disabled"
          >
            {{ option.label }}
          </option>
        </select>
      </div>
      <div v-if="selectedOutputModelDisplay" class="model-hint">
        <div class="model-hint-title">
          {{ selectedOutputModelDisplay.name }}
          <span v-if="selectedOutputModelDisplay.version" class="model-hint-version">
            v{{ selectedOutputModelDisplay.version }}
          </span>
        </div>
        <div class="model-hint-meta">{{ selectedOutputModelDisplay.className }}</div>
        <div v-if="selectedOutputModelDisplay.description" class="model-hint-desc">
          {{ selectedOutputModelDisplay.description }}
        </div>
      </div>
      <div class="tip">
        <span v-if="outputType === 'object'">输出为对象（Map）</span>
        <span v-else>输出为单值（String/Number/Boolean）</span>
        <div v-if="endpointSchema?.responseSchema?.type" class="muted tiny">
          选择 REST 返回类型选项将参考接口签名生成模板。
        </div>
      </div>
    </div>

    <div class="section">
      <h3 class="section-title">步骤配置</h3>
      <div class="step-tabs">
        <button
          class="step-tab"
          :class="{ active: activeStepTab === 'fields' }"
          type="button"
          @click="activeStepTab = 'fields'"
        >
          字段映射
          <span class="badge" v-if="editorState.fields.length">{{ editorState.fields.length }}</span>
        </button>
        <button
          class="step-tab"
          :class="{ active: activeStepTab === 'collections' }"
          type="button"
          @click="activeStepTab = 'collections'"
        >
          集合处理
          <span class="badge" v-if="editorState.collections.length">{{ editorState.collections.length }}</span>
        </button>
        <button
          class="step-tab"
          :class="{ active: activeStepTab === 'conditions' }"
          type="button"
          @click="activeStepTab = 'conditions'"
        >
          条件判断
          <span class="badge" v-if="editorState.conditions.length">{{ editorState.conditions.length }}</span>
        </button>
      </div>

      <!-- 字段映射 -->
      <div v-if="activeStepTab === 'fields'" class="step-panel">
        <div class="quick-actions">
          <select v-model="schemaQuickPick" class="input">
            <option value="">从请求 Schema 快速添加字段...</option>
            <option v-for="option in schemaOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <button class="btn small" type="button" @click="addFieldStep">自定义字段映射</button>
        </div>

        <div v-if="editorState.fields.length === 0" class="empty-tip">
          当前尚未配置字段映射，可从请求 Schema 中选择字段或自定义输出。
        </div>

        <div v-for="(field, index) in editorState.fields" :key="field.id" class="step-card">
          <div class="step-card-header">
            <div class="step-title">字段映射 #{{ index + 1 }}</div>
            <button class="btn-icon danger" type="button" @click="removeFieldStep(field.id)">✕</button>
          </div>
          <div class="step-card-body">
            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">目标字段</span>
                <input class="input" v-model="field.target" placeholder="例如：order.id" />
              </label>
              <label class="field-block">
                <span class="field-label">来源类型</span>
                <select class="input" v-model="field.sourceMode">
                  <option value="schema">请求 Schema</option>
                  <option value="resolver">ParamResolver</option>
                  <option value="expression">表达式</option>
                </select>
              </label>
            </div>

            <div v-if="field.sourceMode === 'schema'" class="form-grid">
              <label class="field-block">
                <span class="field-label">请求字段</span>
                <select
                  class="input"
                  :value="field.schemaPath"
                  @change="field.schemaPath = ($event.target as HTMLSelectElement).value"
                >
                    <option value="">请选择请求字段</option>
                  <option v-for="option in schemaOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
            </div>

            <div v-else-if="field.sourceMode === 'resolver'" class="form-grid">
              <label class="field-block">
                <span class="field-label">Resolver 引用</span>
                <select
                  class="input"
                  :value="field.resolverRef"
                  @change="field.resolverRef = ($event.target as HTMLSelectElement).value"
                >
                  <option value="">请选择 Resolver 名称</option>
                  <option v-for="option in resolverOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
                <div v-if="!hasParamResolvers" class="inline-hint">
                  尚未配置可用的 ParamResolver，请在“节点配置 → 输入参数”中新增解析规则。
                </div>
              </label>
            </div>

            <div v-else class="form-grid">
              <label class="field-block">
                <span class="field-label">表达式</span>
                <textarea
                  class="input textarea"
                  rows="3"
                  v-model="field.expression"
                  placeholder="例如：#{#ctx.orderNo}"
                ></textarea>
              </label>
            </div>

            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">默认值</span>
                <input class="input" v-model="field.defaultValue" placeholder="可选" />
              </label>
              <label class="field-block">
                <span class="field-label">类型转换</span>
                <select
                  class="input"
                  :value="field.cast"
                  @change="field.cast = ($event.target as HTMLSelectElement).value as ValueCast"
                >
                  <option v-for="option in castOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
              <label class="field-inline">
                <input type="checkbox" v-model="field.required" />
                <span>必填</span>
              </label>
            </div>
            <div class="muted tiny">可在脚本中通过 <code>output.{{ field.target }}</code> 访问结果。</div>
          </div>
        </div>
      </div>

      <!-- 集合处理 -->
      <div v-else-if="activeStepTab === 'collections'" class="step-panel">
        <div class="quick-actions">
          <button class="btn small" type="button" @click="addCollectionStep">新增集合步骤</button>
        </div>
        <div v-if="editorState.collections.length === 0" class="empty-tip">
          集合步骤可用于数组映射、过滤或子项转换。
        </div>
        <div v-for="(collection, index) in editorState.collections" :key="collection.id" class="step-card">
          <div class="step-card-header">
            <div class="step-title">集合步骤 #{{ index + 1 }}</div>
            <button class="btn-icon danger" type="button" @click="removeCollectionStep(collection.id)">✕</button>
          </div>
          <div class="step-card-body">
            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">目标字段</span>
                <input class="input" v-model="collection.target" placeholder="例如：order.items" />
              </label>
              <label class="field-block">
                <span class="field-label">来源类型</span>
                <select class="input" v-model="collection.sourceMode">
                  <option value="schema">请求 Schema</option>
                  <option value="resolver">ParamResolver</option>
                  <option value="expression">表达式</option>
                </select>
              </label>
            </div>
            <div v-if="collection.sourceMode === 'schema'" class="form-grid">
              <label class="field-block">
                <span class="field-label">集合来源</span>
                <select
                  class="input"
                  :value="collection.schemaPath"
                  @change="collection.schemaPath = ($event.target as HTMLSelectElement).value"
                >
                  <option value="">请选择集合路径</option>
                  <option v-for="option in schemaOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
              </label>
            </div>
            <div v-else-if="collection.sourceMode === 'resolver'" class="form-grid">
              <label class="field-block">
                <span class="field-label">Resolver 引用</span>
                <select
                  class="input"
                  :value="collection.resolverRef"
                  @change="collection.resolverRef = ($event.target as HTMLSelectElement).value"
                >
                  <option value="">请选择 Resolver 名称</option>
                  <option v-for="option in resolverOptions" :key="option.value" :value="option.value">
                    {{ option.label }}
                  </option>
                </select>
                <div v-if="!hasParamResolvers" class="inline-hint">
                  尚未配置可用的 ParamResolver，请在“节点配置 → 输入参数”中新增解析规则。
                </div>
              </label>
            </div>
            <div v-else class="form-grid">
              <label class="field-block">
                <span class="field-label">表达式</span>
                <textarea
                  class="input textarea"
                  rows="3"
                  v-model="collection.expression"
                  placeholder="例如：#{#ctx.cart.items}"
                ></textarea>
              </label>
            </div>
            <div class="form-grid">
              <label class="field-block">
                <span class="field-label">元素别名</span>
                <input class="input" v-model="collection.itemAlias" placeholder="默认 item" />
              </label>
              <label class="field-block">
                <span class="field-label">过滤条件（可选）</span>
                <input class="input" v-model="collection.filter" placeholder="例如：#{item.enabled}" />
              </label>
            </div>

            <div class="sub-section">
              <div class="sub-header">
                <span>子项字段映射</span>
                <button class="btn tiny" type="button" @click="addCollectionItemField(collection.id)">新增字段</button>
              </div>
              <div
                v-if="collection.itemFields.length === 0"
                class="muted tiny"
                style="margin-bottom: 8px"
              >
                集合元素将按照此处配置进行转换。
              </div>
              <div
                v-for="(field, subIndex) in collection.itemFields"
                :key="field.id"
                class="nested-card"
              >
                <div class="nested-card-header">
                  <div>字段 #{{ subIndex + 1 }}</div>
                  <button
                    class="btn-icon danger"
                    type="button"
                    @click="removeCollectionItemField(collection.id, field.id)"
                  >
                    ✕
                  </button>
                </div>
                <div class="nested-card-body">
                  <div class="form-grid">
                    <label class="field-block">
                      <span class="field-label">目标字段</span>
                      <input class="input" v-model="field.target" placeholder="例如：sku" />
                    </label>
                    <label class="field-block">
                      <span class="field-label">来源类型</span>
                      <select class="input" v-model="field.sourceMode">
                        <option value="schema">请求 Schema</option>
                        <option value="resolver">ParamResolver</option>
                        <option value="expression">表达式</option>
                      </select>
                    </label>
                  </div>
                  <div v-if="field.sourceMode === 'schema'" class="form-grid">
                  <label class="field-block">
                    <span class="field-label">请求字段</span>
                      <select
                        class="input"
                        :value="field.schemaPath"
                        @change="field.schemaPath = ($event.target as HTMLSelectElement).value"
                      >
                        <option value="">请选择请求字段</option>
                        <option v-for="option in schemaOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                  </div>
                  <div v-else-if="field.sourceMode === 'resolver'" class="form-grid">
                    <label class="field-block">
                      <span class="field-label">Resolver 引用</span>
                      <select
                        class="input"
                        :value="field.resolverRef"
                        @change="field.resolverRef = ($event.target as HTMLSelectElement).value"
                      >
                        <option value="">请选择 Resolver 名称</option>
                        <option v-for="option in resolverOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    <div v-if="!hasParamResolvers" class="inline-hint">
                      尚未配置可用的 ParamResolver，请在“节点配置 → 输入参数”中新增解析规则。
                    </div>
                    </label>
                  </div>
                  <div v-else class="form-grid">
                    <label class="field-block">
                      <span class="field-label">表达式</span>
                      <textarea
                        class="input textarea"
                        rows="2"
                        v-model="field.expression"
                        placeholder="例如：#{item.qty}"
                      ></textarea>
                    </label>
                  </div>
                  <div class="form-grid">
                    <label class="field-block">
                      <span class="field-label">默认值</span>
                      <input class="input" v-model="field.defaultValue" placeholder="可选" />
                    </label>
                    <label class="field-block">
                      <span class="field-label">类型转换</span>
                      <select
                        class="input"
                        :value="field.cast"
                        @change="field.cast = ($event.target as HTMLSelectElement).value as ValueCast"
                      >
                        <option v-for="option in castOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                    <label class="field-inline">
                      <input type="checkbox" v-model="field.required" />
                      <span>必填</span>
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 条件判断 -->
      <div v-else class="step-panel">
        <div class="quick-actions">
          <button class="btn small" type="button" @click="addConditionStep">新增条件步骤</button>
        </div>
        <div v-if="editorState.conditions.length === 0" class="empty-tip">
          条件步骤可用于根据表达式控制字段赋值。
        </div>
        <div v-for="(condition, index) in editorState.conditions" :key="condition.id" class="step-card">
          <div class="step-card-header">
            <div class="step-title">条件步骤 #{{ index + 1 }}</div>
            <button class="btn-icon danger" type="button" @click="removeConditionStep(condition.id)">✕</button>
          </div>
          <div class="step-card-body">
            <label class="field-block">
              <span class="field-label">条件表达式</span>
              <textarea
                class="input textarea"
                rows="2"
                v-model="condition.when"
                placeholder="例如：#{resolved.userType == 'VIP'}"
              ></textarea>
            </label>
            <div class="sub-section">
              <div class="sub-header">
                <span>条件成立时</span>
                <button class="btn tiny" type="button" @click="addConditionField(condition.id, 'then')">新增字段</button>
              </div>
              <div v-if="condition.thenFields.length === 0" class="muted tiny">
                当表达式为 true 时执行的字段赋值。
              </div>
              <div
                v-for="(field, thenIndex) in condition.thenFields"
                :key="field.id"
                class="nested-card"
              >
                <div class="nested-card-header">
                  <div>字段 #{{ thenIndex + 1 }}</div>
                  <button
                    class="btn-icon danger"
                    type="button"
                    @click="removeConditionField(condition.id, 'then', field.id)"
                  >
                    ✕
                  </button>
                </div>
                <div class="nested-card-body">
                  <div class="form-grid">
                    <label class="field-block">
                      <span class="field-label">目标字段</span>
                      <input class="input" v-model="field.target" placeholder="例如：order.discount" />
                    </label>
                    <label class="field-block">
                      <span class="field-label">来源类型</span>
                      <select class="input" v-model="field.sourceMode">
                        <option value="schema">请求 Schema</option>
                        <option value="resolver">ParamResolver</option>
                        <option value="expression">表达式</option>
                      </select>
                    </label>
                  </div>
                  <div v-if="field.sourceMode === 'schema'" class="form-grid">
                  <label class="field-block">
                    <span class="field-label">请求字段</span>
                      <select
                        class="input"
                        :value="field.schemaPath"
                        @change="field.schemaPath = ($event.target as HTMLSelectElement).value"
                      >
                        <option value="">请选择请求字段</option>
                        <option v-for="option in schemaOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                  </div>
                  <div v-else-if="field.sourceMode === 'resolver'" class="form-grid">
                    <label class="field-block">
                      <span class="field-label">Resolver 引用</span>
                      <select
                        class="input"
                        :value="field.resolverRef"
                        @change="field.resolverRef = ($event.target as HTMLSelectElement).value"
                      >
                        <option value="">请选择 Resolver 名称</option>
                        <option v-for="option in resolverOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    <div v-if="!hasParamResolvers" class="inline-hint">
                      尚未配置可用的 ParamResolver，请在“节点配置 → 输入参数”中新增解析规则。
                    </div>
                    </label>
                  </div>
                  <div v-else class="form-grid">
                    <label class="field-block">
                      <span class="field-label">表达式</span>
                      <textarea
                        class="input textarea"
                        rows="2"
                        v-model="field.expression"
                        placeholder="例如：#{value * 0.8}"
                      ></textarea>
                    </label>
                  </div>
                </div>
              </div>
            </div>
            <div class="sub-section">
              <div class="sub-header">
                <span>条件不成立时</span>
                <button class="btn tiny" type="button" @click="addConditionField(condition.id, 'else')">新增字段</button>
              </div>
              <div v-if="condition.elseFields.length === 0" class="muted tiny">
                未配置时将保持原值。
              </div>
              <div
                v-for="(field, elseIndex) in condition.elseFields"
                :key="field.id"
                class="nested-card"
              >
                <div class="nested-card-header">
                  <div>字段 #{{ elseIndex + 1 }}</div>
                  <button
                    class="btn-icon danger"
                    type="button"
                    @click="removeConditionField(condition.id, 'else', field.id)"
                  >
                    ✕
                  </button>
                </div>
                <div class="nested-card-body">
                  <div class="form-grid">
                    <label class="field-block">
                      <span class="field-label">目标字段</span>
                      <input class="input" v-model="field.target" placeholder="例如：order.discount" />
                    </label>
                    <label class="field-block">
                      <span class="field-label">来源类型</span>
                      <select class="input" v-model="field.sourceMode">
                        <option value="schema">请求 Schema</option>
                        <option value="resolver">ParamResolver</option>
                        <option value="expression">表达式</option>
                      </select>
                    </label>
                  </div>
                  <div v-if="field.sourceMode === 'schema'" class="form-grid">
                  <label class="field-block">
                    <span class="field-label">请求字段</span>
                      <select
                        class="input"
                        :value="field.schemaPath"
                        @change="field.schemaPath = ($event.target as HTMLSelectElement).value"
                      >
                        <option value="">请选择请求字段</option>
                        <option v-for="option in schemaOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    </label>
                  </div>
                  <div v-else-if="field.sourceMode === 'resolver'" class="form-grid">
                    <label class="field-block">
                      <span class="field-label">Resolver 引用</span>
                      <select
                        class="input"
                        :value="field.resolverRef"
                        @change="field.resolverRef = ($event.target as HTMLSelectElement).value"
                      >
                        <option value="">请选择 Resolver 名称</option>
                        <option v-for="option in resolverOptions" :key="option.value" :value="option.value">
                          {{ option.label }}
                        </option>
                      </select>
                    <div v-if="!hasParamResolvers" class="inline-hint">
                      尚未配置可用的 ParamResolver，请在“节点配置 → 输入参数”中新增解析规则。
                    </div>
                    </label>
                  </div>
                  <div v-else class="form-grid">
                    <label class="field-block">
                      <span class="field-label">表达式</span>
                      <textarea
                        class="input textarea"
                        rows="2"
                        v-model="field.expression"
                        placeholder="例如：#{0}"
                      ></textarea>
                    </label>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="section">
      <h3 class="section-title">JSON 预览</h3>
      <div v-if="validationMessages.length" class="validation-block">
        <div class="validation-title">校验提示：</div>
        <ul>
          <li v-for="(item, index) in validationMessages" :key="index">{{ item }}</li>
        </ul>
      </div>
      <pre class="preview-block">{{ previewJson }}</pre>
    </div>

    <div class="section">
      <h3 class="section-title">Groovy 脚本</h3>
      <div class="script-editor-wrapper">
        <textarea
          class="input textarea code-editor"
          rows="8"
          placeholder="点击此处或下方按钮打开代码编辑器..."
          :value="selectedNode.data?.script || ''"
          @click="openCodeEditor"
          spellcheck="false"
          readonly
        ></textarea>
        <div class="editor-hint">
          <span class="hint-text">💡 脚本在字段映射之后执行，可访问 <code>ctx</code>、<code>input</code>、<code>output</code>。</span>
          <button class="btn small" type="button" @click="openCodeEditor">打开编辑器</button>
        </div>
      </div>
    </div>

    <div v-if="showCodeEditor" class="code-editor-modal" @click.self="closeCodeEditor">
      <div class="code-editor-modal-content" @click.stop>
        <div class="code-editor-header">
          <h3>Groovy 代码编辑器</h3>
          <div class="code-editor-actions">
            <button class="btn small" type="button" @click="saveAndCloseEditor">保存并关闭</button>
            <button class="btn small" type="button" @click="closeCodeEditor">取消</button>
          </div>
        </div>
        <div class="code-editor-body" @click.stop>
          <div ref="codeEditorContainer" class="code-editor-container"></div>
        </div>
        <div class="code-editor-footer">
          <div class="tip" style="margin: 0; font-size: 11px">
            <strong>提示：</strong>可使用 <kbd>Ctrl+S</kbd>（Mac: <kbd>Cmd+S</kbd>）保存并关闭。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.transformer-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 10px;
  padding: 14px;
  background: #fff;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
}

.tip {
  font-size: 12px;
  color: #475569;
  background: #f8fafc;
  border-left: 2px solid #2563eb;
  padding: 10px;
  border-radius: 6px;
}

.divider-tip {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid rgba(148, 163, 184, 0.2);
}

.muted {
  color: #64748b;
}

.tiny {
  font-size: 11px;
}

.guide-toggle {
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: #fff;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  color: #2563eb;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
  margin-bottom: 6px;
}

.guide-toggle:hover {
  background: #eff6ff;
  border-color: #2563eb;
}

.guide-caret {
  display: inline-block;
  transition: transform 0.2s ease;
}

.guide-caret.open {
  transform: rotate(180deg);
}

.guide-tip {
  background: #f1f5f9;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: #475569;
}

.guide-tip ol {
  margin: 0 0 6px 20px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resolver-tip {
  margin-top: 12px;
  padding: 10px 12px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 8px;
  font-size: 12px;
  color: #475569;
}

.resolver-tip-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.resolver-tip ul {
  margin: 0;
  padding-left: 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.resolver-name {
  font-weight: 600;
  color: #1f2937;
}

.resolver-type {
  color: #64748b;
  margin-left: 4px;
}

.resolver-desc {
  color: #475569;
  margin-left: 6px;
}

.model-hint {
  margin-top: 8px;
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 8px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.model-hint-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 6px;
}

.model-hint-version {
  font-size: 11px;
  padding: 0 6px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;
}

.model-hint-meta {
  font-size: 11px;
  color: #64748b;
}

.model-hint-desc {
  font-size: 11px;
  color: #475569;
  line-height: 1.4;
}

.inline-hint {
  color: #94a3b8;
  font-size: 11px;
  margin-top: 4px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 11px;
  font-weight: 500;
  color: #475569;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
  width: 100%;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.textarea {
  resize: vertical;
  min-height: 60px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
}

.btn {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  background: #fff;
  color: #0f172a;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn:hover {
  background: #f8fafc;
  border-color: #2563eb;
}

.btn.small {
  padding: 4px 10px;
}

.btn.tiny {
  padding: 3px 8px;
  font-size: 11px;
}

.btn-icon {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 4px;
  color: #94a3b8;
  transition: all 0.2s ease;
}

.btn-icon.danger:hover {
  background: rgba(220, 38, 38, 0.12);
  color: #dc2626;
}

.step-tabs {
  display: inline-flex;
  background: rgba(15, 23, 42, 0.03);
  padding: 4px;
  border-radius: 999px;
  gap: 4px;
}

.step-tab {
  border: none;
  background: transparent;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s ease;
}

.step-tab.active {
  background: #2563eb;
  color: #fff;
}

.badge {
  font-size: 10px;
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: 999px;
}

.step-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 8px;
}

.quick-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.empty-tip {
  font-size: 12px;
  color: #94a3b8;
  background: rgba(148, 163, 184, 0.12);
  padding: 10px;
  border-radius: 6px;
}

.step-card,
.nested-card {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.step-card-header,
.nested-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #f8fafc;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
}

.step-title {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.step-card-body,
.nested-card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 10px;
}

.field-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #475569;
}

.sub-section {
  background: rgba(148, 163, 184, 0.08);
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sub-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.nested-card {
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
}

.validation-block {
  background: rgba(248, 113, 113, 0.1);
  border: 1px solid rgba(248, 113, 113, 0.4);
  border-radius: 8px;
  padding: 10px;
  font-size: 12px;
  color: #b91c1c;
}

.validation-block ul {
  margin: 4px 0 0;
  padding-left: 18px;
}

.validation-title {
  font-weight: 600;
}

.preview-block {
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  font-size: 11px;
  overflow: auto;
  max-height: 240px;
}

.response-type-code {
  display: inline-block;
  font-size: 10px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  background: rgba(30, 64, 175, 0.08);
  color: #1d4ed8;
  padding: 2px 6px;
  border-radius: 4px;
}

.script-editor-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.code-editor {
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 12px;
  min-height: 120px;
  cursor: pointer;
}

.editor-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
}

.hint-text {
  display: flex;
  align-items: center;
  gap: 4px;
}

.code-editor-modal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.code-editor-modal-content {
  background: #fff;
  border-radius: 10px;
  width: 100%;
  max-width: 900px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 25px -12px rgba(15, 23, 42, 0.35);
}

.code-editor-header,
.code-editor-footer {
  padding: 14px 18px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.code-editor-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.code-editor-actions {
  display: flex;
  gap: 8px;
}

.code-editor-body {
  flex: 1;
  min-height: 420px;
}

.code-editor-container {
  width: 100%;
  height: 100%;
}

.code-editor-footer {
  border-top: 1px solid #e2e8f0;
  border-bottom: none;
  background: #f8fafc;
  font-size: 11px;
}

@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
  .quick-actions {
    flex-direction: column;
  }
  .step-tabs {
    width: 100%;
  }
  .step-tab {
    flex: 1;
    justify-content: center;
  }
}
</style>