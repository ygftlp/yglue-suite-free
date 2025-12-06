﻿<script setup lang="ts">
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
  if (!endpoint.value) {
    console.log("[StudioPage] flowCode computed: endpoint is null")
    return undefined
  }
  // 优先使用 endpoint 中存储的 flowCode（从 FlowEntryPoint 表获取）
  if (endpoint.value.flowCode) {
    console.log("[StudioPage] flowCode computed: using endpoint.flowCode:", endpoint.value.flowCode)
    return endpoint.value.flowCode
  }
  // 如果没有 flowCode，尝试从 entrypoint 配置中获取
  const entrypoint = endpoint.value.entrypoint
  if (entrypoint && typeof entrypoint === "object" && (entrypoint as any).flowCode) {
    console.log("[StudioPage] flowCode computed: using entrypoint.flowCode:", (entrypoint as any).flowCode)
    return (entrypoint as any).flowCode
  }
  // 如果都没有 flowCode，返回 undefined（不动态生成）
  // 这样前端会显示空白画布，提示用户需要先发布流程
  console.log("[StudioPage] flowCode computed: no flowCode found, returning undefined")
  return undefined
})

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

async function loadEndpoint() {
  if (!projectKey.value || !endpointId.value) return
  endpointLoading.value = true
  endpointError.value = null
  try {
    console.log("[StudioPage] Loading endpoint:", projectKey.value, endpointId.value)
    endpoint.value = await api.getEndpoint(projectKey.value, endpointId.value)
    console.log("[StudioPage] Endpoint loaded:", endpoint.value?.id, "flowCode:", endpoint.value?.flowCode)
  } catch (err) {
    endpoint.value = null
    endpointError.value = err instanceof Error ? err.message : "加载接口信息失败"
    console.error("[StudioPage] Failed to load endpoint:", err)
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
        if (schemaNode) {
          requestSchemaFields = flattenSchemaFields(schemaNode)
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
          typeName: (item as any).typeName,
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
    
    // 提取类型名：优先使用 x-javaType，然后是 typeName、$ref、className 等
    let typeName: string | undefined = undefined
    if (value["x-javaType"] && typeof value["x-javaType"] === "string") {
      // 优先使用 x-javaType（后端提供的 Java 类型）
      typeName = value["x-javaType"].trim()
    } else if (value.typeName && typeof value.typeName === "string") {
      typeName = value.typeName.trim()
    } else if (value["$ref"] && typeof value["$ref"] === "string") {
      // 从 $ref 中提取类型名，例如 "#/components/schemas/UserDto" -> "UserDto"
      const refMatch = value["$ref"].match(/\/([^/]+)$/)
      if (refMatch) {
        typeName = refMatch[1]
      } else {
        typeName = value["$ref"]
      }
    } else if (value.className && typeof value.className === "string") {
      typeName = value.className.trim()
    } else if (value["x-typeName"] && typeof value["x-typeName"] === "string") {
      typeName = value["x-typeName"].trim()
    }
    
    // 处理 List<Type> 或 Map<String, Type> 等泛型类型
    if (value["x-type"] && typeof value["x-type"] === "string") {
      const xType = value["x-type"].trim()
      if (xType.includes("<") || !typeName) {
        typeName = xType
      }
    }
    
    const field: EndpointSchemaField = {
      name: fieldName,
      type: fieldType,
      source: value["x-source"],
      pathVariable: value["x-pathVariable"],
      paramName: value["x-paramName"],
      formField: value["x-formField"],
      typeName: typeName,
    } as EndpointSchemaField & { typeName?: string }
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
        :endpoint-id="endpointId"
      />
    </main>
  </div>
</template>

<style scoped>
.error {
  color: #dc2626;
}
</style>
