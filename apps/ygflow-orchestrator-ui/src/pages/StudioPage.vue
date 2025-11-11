<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import CanvasEditor from "../components/CanvasEditor.vue"
import { Network, ArrowLeft } from "lucide-vue-next"
import {
  api,
  type EndpointResponseSchema,
  type EndpointSchemaField,
  type FlowModel,
  type FlowResolver,
  type ProjectEndpoint,
} from "../api/client"

const route = useRoute()
const router = useRouter()

const projectKey = computed(() => route.params.projectKey as string)
const endpointId = computed(() => Number(route.params.endpointId))

const endpoint = ref<ProjectEndpoint | null>(null)
const endpointLoading = ref(false)
const endpointError = ref<string | null>(null)
const flowModels = ref<FlowModel[]>([])
const flowResolvers = ref<FlowResolver[]>([])

const flowCode = computed(() => {
  if (!endpoint.value) return undefined
  const method = (endpoint.value.method || "REST").toLowerCase()
  const raw = endpoint.value.path || `endpoint-${endpoint.value.id}`
  const normalized = raw.replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-+|-+$/g, "") || `endpoint-${endpoint.value.id}`
  return `${method}-${normalized}`
})

const entrypointHint = computed(() => {
  if (!endpoint.value) return null
  return {
    path: endpoint.value.path || "",
    method: (endpoint.value.method || "").toUpperCase(),
    replaceResponse: false,
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

async function loadEndpoint() {
  if (!projectKey.value || !endpointId.value) return
  endpointLoading.value = true
  endpointError.value = null
  try {
    endpoint.value = await api.getEndpoint(projectKey.value, endpointId.value)
  } catch (err) {
    endpoint.value = null
    endpointError.value = err instanceof Error ? err.message : "加载接口信息失败"
  } finally {
    endpointLoading.value = false
  }
}

watch([projectKey, endpointId], loadEndpoint, { immediate: true })
watch(projectKey, loadModels, { immediate: true })
watch(projectKey, loadResolvers, { immediate: true })

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

function extractEndpointSchema(endpoint: ProjectEndpoint) {
  let schemaJson = coerceSchemaJson(endpoint.requestSchemaJson)
  let schemaNode = schemaJson ? safeParseJson(schemaJson) : null
  if (schemaNode && schemaJson) {
    schemaJson = JSON.stringify(schemaNode, null, 2)
  }
  let requestSchemaFields = schemaNode ? flattenSchemaFields(schemaNode) : normalizeRequestSchema(endpoint.requestSchema)
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
        if (schemaNode) {
          requestSchemaFields = flattenSchemaFields(schemaNode)
        } else {
          requestSchemaFields = normalizeRequestSchema(config.requestSchema)
        }
      }
      if (!responseSchema) {
        responseSchema = normalizeResponseSchema(config.responseSchema)
      }
    }
  }

  return { requestSchemaFields, requestSchemaJson: schemaJson ?? null, responseSchema }
}

function normalizeRequestSchema(
  schema?: EndpointSchemaField[] | null | unknown
): EndpointSchemaField[] | undefined {
  if (!schema) return undefined
  if (Array.isArray(schema)) {
    const normalized = schema
      .map((item) => {
        if (!item || typeof item !== "object") return null
        const name = typeof (item as any).name === "string" ? (item as any).name : ""
        const type = typeof (item as any).type === "string" ? (item as any).type : ""
        if (!name && !type) return null
        return {
          name,
          type,
          source: (item as any).source,
          pathVariable: (item as any).pathVariable,
          paramName: (item as any).paramName,
          formField: (item as any).formField,
        } as EndpointSchemaField
      })
      .filter((item): item is EndpointSchemaField => Boolean(item))
    return normalized.length ? normalized : undefined
  }
  if (typeof schema === "string" && schema.trim()) {
    const parsed = safeParseJson(schema)
    if (parsed) {
      return flattenSchemaFields(parsed)
    }
  }
  if (typeof schema === "object") {
    return flattenSchemaFields(schema as any)
  }
  return undefined
}

function normalizeResponseSchema(
  schema?: EndpointResponseSchema | null | unknown
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
    const fieldType = typeof value.type === "string" ? value.type.toUpperCase() : value.format ? value.format : "OBJECT"
    const field: EndpointSchemaField = {
      name: fieldName,
      type: fieldType,
      source: value["x-source"],
      pathVariable: value["x-pathVariable"],
      paramName: value["x-paramName"],
      formField: value["x-formField"],
    }
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
  } catch (_) {
    return null
  }
}
</script>

<template>
  <div class="col" style="height:100%">
    <header class="bar" style="justify-content:space-between">
      <div class="row" style="gap:8px; align-items:center">
        <button type="button" class="btn" style="padding:4px 8px" @click="goBack">
          <ArrowLeft :size="16" />
        </button>
        <div class="row" style="gap:6px; font-size:14px">
          <Network :size="18" /> 流程编排画布
        </div>
      </div>
      <div class="muted" style="display:flex; flex-direction:column; align-items:flex-end">
        <span>项目 {{ projectKey }}</span>
        <span v-if="endpointLoading">正在加载接口...</span>
        <span v-else-if="endpointError" class="error">{{ endpointError }}</span>
        <span v-else-if="endpoint">
          {{ endpoint.method }} {{ endpoint.path }}
        </span>
      </div>
    </header>
    <main style="flex:1; min-height:0">
      <CanvasEditor
        :project-key="projectKey"
        :flow-code="flowCode"
        :entrypoint-hint="entrypointHint"
        :endpoint-schema="schemaHint"
        :flow-models="flowModels"
        :flow-resolvers="flowResolvers"
      />
    </main>
  </div>
</template>

<style scoped>
.error {
  color: #dc2626;
}
</style>
