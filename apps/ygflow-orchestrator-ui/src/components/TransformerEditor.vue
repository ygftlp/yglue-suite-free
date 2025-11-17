<script setup lang="ts">
import { computed } from "vue"
import type { FlowModel, FlowResolver } from "../api/client"
import ScriptEditor from "./ScriptEditor.vue"

interface Props {
  selectedNode: any | null
  nodes?: any[] | null
  edges?: any[] | null
  endpointSchema?: {
    requestSchema?: Array<{ name: string; type: string; typeName?: string; source?: string; pathVariable?: string; paramName?: string; formField?: string }> | null
    responseSchema?: { type?: string | null } | null
  } | null
  entrypointPath?: string | null
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
}

interface Emits {
  (e: "update-node", node: any): void
}

interface OutputModelState {
  identifier: string
  name?: string
  className?: string
  version?: string
  description?: string
}

interface ScriptHelperItem {
  label: string
  snippet: string
  description: string
  example?: string
  tooltip?: string
}

interface ScriptHelperGroup {
  title: string
  items: ScriptHelperItem[]
}

type RawHelperItem = Partial<ScriptHelperItem>
type RawHelperGroup = {
  title?: string
  items?: RawHelperItem[]
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const isTransformerNode = computed(() => props.selectedNode?.type === "transformer")
const outputType = computed(() => props.selectedNode?.data?.outputType || "object")

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

function normalizeExternalHelperItem(entry: any): ScriptHelperItem | null {
  if (!entry) return null
  const label = typeof entry.label === "string" ? entry.label.trim() : ""
  const snippet = typeof entry.snippet === "string" ? entry.snippet : ""
  if (!label || !snippet) return null
  return {
    label,
    snippet,
    description: typeof entry.description === "string" ? entry.description : "",
    example: typeof entry.example === "string" ? entry.example : undefined,
  }
}

function normalizeExternalHelperGroups(payload: unknown): ScriptHelperGroup[] {
  if (!Array.isArray(payload)) return []
  const groups: ScriptHelperGroup[] = []
  payload.forEach((entry, index) => {
    if (entry && Array.isArray((entry as RawHelperGroup).items)) {
      const items = (entry as RawHelperGroup).items!
        .map((item) => normalizeExternalHelperItem(item))
        .filter((item): item is ScriptHelperItem => !!item)
      if (items.length) {
        const title =
          typeof (entry as RawHelperGroup).title === "string" && (entry as RawHelperGroup).title!.trim().length
            ? (entry as RawHelperGroup).title!.trim()
            : `自定义分组 ${index + 1}`
        groups.push({ title, items })
      }
  } else {
      const item = normalizeExternalHelperItem(entry)
      if (item) {
        const title =
          typeof (entry as RawHelperGroup)?.title === "string" && (entry as RawHelperGroup).title!.trim().length
            ? (entry as RawHelperGroup).title!.trim()
            : `自定义分组 ${index + 1}`
        groups.push({ title, items: [item] })
      }
    }
  })
  return groups
}

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


const scriptVariableGroups = computed<ScriptHelperGroup[]>(() => {
  const groups: ScriptHelperGroup[] = [
    {
      title: "临时变量",
      items: [
        { 
          label: "input", 
          snippet: "input", 
          description: `上游节点输出（对象/数组），由前一个节点提供。${upstreamOutputType.value ? `类型：${upstreamOutputType.value}` : ""}` 
        },
      ],
    },
    {
      title: "流程上下文",
      items: [
        { label: "ctx", snippet: "ctx", description: "流程上下文，可读写共享变量，例如 ctx['orderId']。" },
        { label: "env", snippet: "env", description: "运行环境信息（租户、操作人等）。" },
      ],
    },
  ]

  // 添加 REST 请求参数提示（作为变量定义）
  if (props.endpointSchema?.requestSchema && props.endpointSchema.requestSchema.length > 0) {
    const requestItems: ScriptHelperItem[] = []
    
    props.endpointSchema.requestSchema.forEach((field) => {
      const fieldName = field.name
      const fieldType = field.type
      const typeName = (field as any).typeName // x-javaType
      const source = field.source
      
      let snippet = ""
      let description = ""
      let example = ""
      
      if (source === "path" || field.pathVariable) {
        const pathVar = field.pathVariable || fieldName
        snippet = `def ${fieldName} = ctx['request.path.${pathVar}']`
        description = `路径变量：${fieldName} (${fieldType})`
        if (typeName) {
          example = `// 类型：${typeName}`
        }
      } else if (source === "query" || field.paramName) {
        const paramName = field.paramName || fieldName
        snippet = `def ${fieldName} = ctx['request.query.${paramName}']`
        description = `查询参数：${fieldName} (${fieldType})`
        if (typeName) {
          example = `// 类型：${typeName}`
        }
      } else if (source === "body" || source === "form") {
        snippet = `def ${fieldName} = ctx['request.body.${fieldName}']`
        description = `请求体字段：${fieldName} (${fieldType})`
        if (typeName) {
          example = `// 类型：${typeName}`
        }
      } else {
        // 默认作为请求体字段
        snippet = `def ${fieldName} = ctx['request.body.${fieldName}']`
        description = `请求参数：${fieldName} (${fieldType})`
        if (typeName) {
          example = `// 类型：${typeName}`
        }
      }
      
      requestItems.push({
        label: fieldName,
        snippet,
        description,
        example: example || undefined,
        tooltip: typeName && typeName !== fieldType ? `完整类型：${typeName}` : undefined,
      })
    })
    
    if (requestItems.length > 0) {
      groups.push({
        title: "REST 请求参数",
        items: requestItems,
      })
    }
  }

  const customGroups = [
    ...normalizeExternalHelperGroups(props.selectedNode?.data?.scriptVariableGroups),
    ...normalizeExternalHelperGroups(props.selectedNode?.data?.scriptVariables),
  ]
  return groups.concat(customGroups)
})

const scriptFunctionGroups = computed<ScriptHelperGroup[]>(() => {
  const groups: ScriptHelperGroup[] = [
    {
      title: "系统函数",
      items: [
        {
          label: "jsonPath(value, path)",
          snippet: 'jsonPath(input, "$.data.field")',
          description: "从 JSON/Map 中按路径提取字段。",
        },
        {
          label: "assert(condition, message)",
          snippet: 'assert(resolved.user != null, "用户不能为空")',
          description: "条件不满足时抛出异常，终止流程。",
        },
        {
          label: "formatDate(value, pattern)",
          snippet: 'formatDate(resolved.orderTime, "yyyy-MM-dd HH:mm:ss")',
          description: "格式化日期/时间为指定字符串。",
        },
      ],
    },
    {
      title: "内置工具",
      items: [
        {
          label: "mapValues(collection) { ... }",
          snippet: "mapValues(resolved.items) { item ->\n  [id: item.id, qty: item.count]\n}",
          description: "遍历集合并映射为新的 List。",
        },
        {
          label: "safeNumber(value, defaultValue)",
          snippet: "safeNumber(resolved.totalAmount, 0)",
          description: "安全转换为数字，失败时返回默认值。",
        },
      ],
    },
  ]

  const customGroups = [
    ...normalizeExternalHelperGroups(props.selectedNode?.data?.scriptFunctionGroups),
    ...normalizeExternalHelperGroups(props.selectedNode?.data?.scriptFunctions),
  ]
  return groups.concat(customGroups)
})

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


function handleScriptUpdate(value: string) {
  updateNodeField("script", value)
}

/**
 * 生成包含 REST 参数的初始脚本
 */
function generateInitialScript(): string {
  if (!props.endpointSchema?.requestSchema || props.endpointSchema.requestSchema.length === 0) {
    return ""
  }
  
  const lines: string[] = []
  lines.push("// REST 请求参数")
  
  props.endpointSchema.requestSchema.forEach((field) => {
    const fieldName = field.name
    const fieldType = field.type
    const typeName = (field as any).typeName // x-javaType
    const source = field.source
    
    let ctxPath = ""
    if (source === "path" || field.pathVariable) {
      const pathVar = field.pathVariable || fieldName
      ctxPath = `ctx['request.path.${pathVar}']`
    } else if (source === "query" || field.paramName) {
      const paramName = field.paramName || fieldName
      ctxPath = `ctx['request.query.${paramName}']`
    } else if (source === "body" || source === "form") {
      ctxPath = `ctx['request.body.${fieldName}']`
    } else {
      ctxPath = `ctx['request.body.${fieldName}']`
    }
    
    // 添加类型注释：显示 type，如果有 typeName 也显示
    if (typeName && typeName !== fieldType) {
      lines.push(`// ${fieldName}: ${fieldType} (${typeName})`)
    } else {
      lines.push(`// ${fieldName}: ${fieldType}`)
    }
    lines.push(`def ${fieldName} = ${ctxPath}`)
  })
  
  lines.push("")
  lines.push("// 在此处编写脚本逻辑")
  
  return lines.join("\n")
}

/**
 * 检查并初始化脚本（如果为空）
 */
function ensureScriptInitialized() {
  const currentScript = props.selectedNode?.data?.script || ""
  if (!currentScript.trim() && props.endpointSchema?.requestSchema && props.endpointSchema.requestSchema.length > 0) {
    const initialScript = generateInitialScript()
    if (initialScript) {
      updateNodeField("script", initialScript)
    }
  }
}

</script>

<template>
  <div v-if="isTransformerNode" class="transformer-editor">
    <div v-if="resolverCatalog.length" class="section">
      <div class="resolver-tip">
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

    <ScriptEditor
      title="Groovy 脚本"
      :script="selectedNode.data?.script || ''"
      :variable-groups="scriptVariableGroups"
      :function-groups="scriptFunctionGroups"
      :endpoint-schema="props.endpointSchema"
      @update:script="handleScriptUpdate"
      @open="ensureScriptInitialized"
    />
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

.response-type-code {
  display: inline-block;
  font-size: 10px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  background: rgba(30, 64, 175, 0.08);
  color: #1d4ed8;
  padding: 2px 6px;
  border-radius: 4px;
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