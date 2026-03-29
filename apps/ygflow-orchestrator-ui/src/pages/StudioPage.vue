<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { ArrowLeft, Network } from "lucide-vue-next"
import CanvasEditor from "../components/CanvasEditor.vue"
import {
  api,
  type EndpointResponseSchema,
  type EndpointSchemaField,
  type FlowModel,
  type FlowResolver,
  type ProjectEndpoint,
  type ProjectMetadata,
} from "../api/client"

const route = useRoute()
const router = useRouter()

const projectKey = computed(() => route.params.projectKey as string)
const flowCode = computed(() => route.params.flowCode as string)

const endpoint = ref<ProjectEndpoint | null>(null)
const endpointLoading = ref(false)
const endpointError = ref<string | null>(null)
const flowModels = ref<FlowModel[]>([])
const flowResolvers = ref<FlowResolver[]>([])
const inboundInterceptorCatalog = ref<
  Array<{
    code: string
    label: string
    description: string
    defaultOrder: number
    defaultConfig: Record<string, any>
    configSchema?: Record<string, any> | null
    source: "project"
  }>
>([])

const entrypointHint = computed(() => {
  if (!endpoint.value) return null
  return {
    path: endpoint.value.path || "",
    method: (endpoint.value.method || "").toUpperCase(),
  }
})

const schemaHint = computed(() => {
  if (!endpoint.value) return null
  const { requestSchemaFields, requestSchemaJson, responseSchema } = extractEndpointSchema(endpoint.value)
  const hasRequestSchema = Array.isArray(requestSchemaFields) && requestSchemaFields.length > 0
  if (!hasRequestSchema && !requestSchemaJson && !responseSchema) return null
  return {
    requestSchema: requestSchemaFields ?? null,
    requestSchemaJson: requestSchemaJson ?? null,
    responseSchema: responseSchema ?? null,
  }
})

function goBack() {
  if (!projectKey.value) return
  router.push(`/projects/${encodeURIComponent(projectKey.value)}/rests`)
}

watch([projectKey, flowCode], () => {
  loadEndpointByFlow()
  loadModels()
  loadResolvers()
  loadInboundInterceptorCatalog()
}, { immediate: true })

async function loadEndpointByFlow() {
  if (!projectKey.value || !flowCode.value) return
  endpointLoading.value = true
  endpointError.value = null
  try {
    const endpoints = await api.listEndpointRests(projectKey.value)
    endpoint.value = endpoints.find((item) => item.flowCode === flowCode.value) || null
  } catch (err) {
    endpoint.value = null
    endpointError.value = err instanceof Error ? err.message : "加载入口信息失败"
  } finally {
    endpointLoading.value = false
  }
}

async function loadModels() {
  if (!projectKey.value) return
  try {
    flowModels.value = await api.listFlowModels(projectKey.value)
  } catch (err) {
    console.warn("Failed to load flow models", err)
  }
}

async function loadResolvers() {
  if (!projectKey.value) return
  try {
    flowResolvers.value = await api.listFlowResolvers(projectKey.value)
  } catch (err) {
    console.warn("Failed to load flow resolvers", err)
  }
}

async function loadInboundInterceptorCatalog() {
  if (!projectKey.value) {
    inboundInterceptorCatalog.value = []
    return
  }
  try {
    const metadataList = await api.listMetadata(projectKey.value)
    const latest = pickLatestMetadata(metadataList)
    if (!latest?.contentJson) {
      inboundInterceptorCatalog.value = []
      return
    }
    const root = safeParseJson(latest.contentJson)
    inboundInterceptorCatalog.value = extractInboundInterceptors(root)
  } catch (err) {
    console.warn("Failed to load inbound interceptor catalog from metadata", err)
    inboundInterceptorCatalog.value = []
  }
}

function pickLatestMetadata(items: ProjectMetadata[] | null | undefined): ProjectMetadata | null {
  if (!Array.isArray(items) || items.length === 0) return null
  return items[0] ?? null
}

function extractInboundInterceptors(root: any) {
  if (!root || typeof root !== "object") return []
  const result: Array<{
    code: string
    label: string
    description: string
    defaultOrder: number
    defaultConfig: Record<string, any>
    configSchema?: Record<string, any> | null
    source: "project"
  }> = []
  const seen = new Set<string>()

  const candidatePaths: string[][] = [
    ["inboundInterceptors"],
    ["interceptors"],
    ["entrypointInterceptors"],
    ["runtime", "inboundInterceptors"],
    ["extensions", "inboundInterceptors"],
    ["capabilities", "inboundInterceptors"],
  ]

  for (const path of candidatePaths) {
    const node = readPath(root, path)
    if (!Array.isArray(node)) continue
    for (const item of node) {
      const normalized = normalizeInboundInterceptorItem(item)
      if (!normalized) continue
      if (seen.has(normalized.code)) continue
      seen.add(normalized.code)
      result.push(normalized)
    }
  }
  return result
}

function normalizeInboundInterceptorItem(item: any) {
  if (!item || typeof item !== "object") return null
  const code = readText(item.code ?? item.id ?? item.name)
  if (!code) return null

  const label = readText(item.label ?? item.displayName ?? item.name) || code
  const description = readText(item.description ?? item.desc) || "Project interceptor"
  const defaultOrder = readNumber(item.defaultOrder ?? item.order, 1000)
  const defaultConfigRaw = item.defaultConfig ?? item.configDefault ?? item.config
  const configSchemaRaw = item.configSchema ?? item.schema

  return {
    code,
    label,
    description,
    defaultOrder,
    defaultConfig: asPlainObject(defaultConfigRaw),
    configSchema: asNullablePlainObject(configSchemaRaw),
    source: "project" as const,
  }
}

function readPath(root: any, segments: string[]) {
  let cursor = root
  for (const key of segments) {
    if (!cursor || typeof cursor !== "object") return undefined
    cursor = cursor[key]
  }
  return cursor
}

function readText(value: any): string {
  if (value == null) return ""
  return String(value).trim()
}

function readNumber(value: any, fallback: number): number {
  if (typeof value === "number" && Number.isFinite(value)) return value
  const parsed = Number.parseInt(String(value ?? ""), 10)
  return Number.isFinite(parsed) ? parsed : fallback
}

function asPlainObject(value: any): Record<string, any> {
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, any>
  }
  return {}
}

function asNullablePlainObject(value: any): Record<string, any> | null {
  if (typeof value === "string") {
    const text = value.trim()
    if (!text) return null
    try {
      const parsed = JSON.parse(text)
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        return parsed as Record<string, any>
      }
      return null
    } catch {
      return null
    }
  }
  if (value && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, any>
  }
  return null
}

function extractEndpointSchema(endpoint: ProjectEndpoint) {
  let schemaJson = coerceSchemaJson(endpoint.requestSchemaJson)
  let schemaNode = schemaJson ? safeParseJson(schemaJson) : null
  if (schemaNode && schemaJson) {
    schemaJson = JSON.stringify(schemaNode, null, 2)
  }

  let requestSchemaFields = schemaNode ? flattenSchemaFields(schemaNode) : null
  let responseSchema = normalizeResponseSchema(endpoint.responseSchema)

  if (((requestSchemaFields?.length ?? 0) === 0 || !schemaJson) || !responseSchema) {
    const config = safeParseConfig(endpoint.configJson)
    if (config) {
      if (!schemaJson) {
        schemaJson = coerceSchemaJson(config.requestSchemaJson)
        schemaNode = schemaJson ? safeParseJson(schemaJson) : schemaNode
        if (schemaNode && schemaJson) {
          schemaJson = JSON.stringify(schemaNode, null, 2)
        }
      }
      if (!requestSchemaFields || requestSchemaFields.length === 0) {
        if (schemaNode) requestSchemaFields = flattenSchemaFields(schemaNode)
      }
      if (!responseSchema) {
        responseSchema = normalizeResponseSchema(config.responseSchema)
      }
    }
  }

  return { requestSchemaFields, requestSchemaJson: schemaJson ?? null, responseSchema }
}

function normalizeResponseSchema(
  schema?: EndpointResponseSchema | null | unknown,
): EndpointResponseSchema | undefined {
  if (!schema || typeof schema !== "object") return undefined
  const type = typeof (schema as any).type === "string" ? (schema as any).type : undefined
  if (!type) return undefined
  return { type }
}

function coerceSchemaJson(value?: string | null): string | null {
  if (!value) return null
  const trimmed = value.trim()
  return trimmed.length ? trimmed : null
}

function safeParseJson(value: string | null) {
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function flattenSchemaFields(schema: any, prefix = ""): EndpointSchemaField[] {
  if (!schema || typeof schema !== "object") return []
  const properties = schema.properties
  if (!properties || typeof properties !== "object") return []

  const result: EndpointSchemaField[] = []
  Object.entries<any>(properties).forEach(([key, value]) => {
    if (!value || typeof value !== "object") return

    const fieldName = prefix ? `${prefix}.${key}` : key
    const fieldType = typeof value.type === "string"
      ? value.type.toUpperCase()
      : value.format
        ? value.format
        : "OBJECT"

    let typeName: string | undefined
    if (typeof value["x-javaType"] === "string") {
      typeName = value["x-javaType"].trim()
    } else if (typeof value.typeName === "string") {
      typeName = value.typeName.trim()
    } else if (typeof value["$ref"] === "string") {
      const refMatch = value["$ref"].match(/\/([^/]+)$/)
      typeName = refMatch ? refMatch[1] : value["$ref"]
    } else if (typeof value.className === "string") {
      typeName = value.className.trim()
    } else if (typeof value["x-typeName"] === "string") {
      typeName = value["x-typeName"].trim()
    }

    if (typeof value["x-type"] === "string") {
      const xType = value["x-type"].trim()
      if (xType.includes("<") || !typeName) typeName = xType
    }

    const field: EndpointSchemaField = {
      name: fieldName,
      type: fieldType,
      source: value["x-source"],
      pathVariable: value["x-pathVariable"],
      paramName: value["x-paramName"],
      formField: value["x-formField"],
      typeName,
    } as EndpointSchemaField

    result.push(field)

    if (fieldType === "OBJECT") {
      result.push(...flattenSchemaFields(value, fieldName))
    } else if (fieldType === "ARRAY" && value.items) {
      result.push(...flattenSchemaFields(value.items, `${fieldName}[]`))
    }
  })

  return result
}

function safeParseConfig(configJson?: string | null): any {
  if (!configJson) return null
  try {
    return JSON.parse(configJson)
  } catch {
    return null
  }
}
</script>

<template>
  <div class="col" style="height: 100%">
    <header class="bar" style="justify-content: space-between">
      <div class="row" style="gap: 8px; align-items: center">
        <button type="button" class="btn" style="padding: 4px 8px" @click="goBack">
          <ArrowLeft :size="16" />
        </button>
        <div class="row" style="gap: 6px; font-size: 14px">
          <Network :size="18" /> 流程编排画布
        </div>
      </div>
      <div class="muted" style="display: flex; flex-direction: column; align-items: flex-end">
        <span>项目 {{ projectKey }}</span>
        <span v-if="endpointLoading">正在加载入口...</span>
        <span v-else-if="endpointError" class="error">{{ endpointError }}</span>
        <span v-else-if="endpoint">{{ endpoint.method }} {{ endpoint.path }}</span>
        <span v-else>Flow {{ flowCode }}</span>
      </div>
    </header>

    <main style="flex: 1; min-height: 0">
      <CanvasEditor
        :project-key="projectKey"
        :flow-code="flowCode"
        :entrypoint-hint="entrypointHint"
        :endpoint-schema="schemaHint"
        :flow-models="flowModels"
        :flow-resolvers="flowResolvers"
        :inbound-interceptor-catalog="inboundInterceptorCatalog"
        :endpoint-id="endpoint?.id"
      />
    </main>
  </div>
</template>

<style scoped>
.error {
  color: #dc2626;
}
</style>
