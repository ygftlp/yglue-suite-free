<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import CanvasEditor from "../components/CanvasEditor.vue"
import { Network, ArrowLeft } from "lucide-vue-next"
import { api, type EndpointResponseSchema, type EndpointSchemaField, type ProjectEndpoint } from "../api/client"

const route = useRoute()
const router = useRouter()

const projectKey = computed(() => route.params.projectKey as string)
const endpointId = computed(() => Number(route.params.endpointId))

const endpoint = ref<ProjectEndpoint | null>(null)
const endpointLoading = ref(false)
const endpointError = ref<string | null>(null)

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
  const { requestSchema, responseSchema } = extractEndpointSchema(endpoint.value)
  if ((!requestSchema || requestSchema.length === 0) && !responseSchema) return null
  return { requestSchema, responseSchema }
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

function extractEndpointSchema(endpoint: ProjectEndpoint) {
  let requestSchema = normalizeRequestSchema(endpoint.requestSchema)
  let responseSchema = normalizeResponseSchema(endpoint.responseSchema)

  if ((requestSchema?.length ?? 0) === 0 || !responseSchema) {
    const config = safeParseConfig(endpoint.configJson)
    if (config) {
      if (!requestSchema || requestSchema.length === 0) {
        requestSchema = normalizeRequestSchema(config.requestSchema)
      }
      if (!responseSchema) {
        responseSchema = normalizeResponseSchema(config.responseSchema)
      }
    }
  }

  return { requestSchema, responseSchema }
}

function normalizeRequestSchema(
  schema?: EndpointSchemaField[] | null | unknown
): EndpointSchemaField[] | undefined {
  if (!Array.isArray(schema)) return undefined
  const normalized = schema
    .map((item) => {
      if (!item || typeof item !== "object") return null
      const name = typeof (item as any).name === "string" ? (item as any).name : ""
      const type = typeof (item as any).type === "string" ? (item as any).type : ""
      if (!name && !type) return null
      return { name, type }
    })
    .filter((item): item is EndpointSchemaField => Boolean(item))
  return normalized.length ? normalized : undefined
}

function normalizeResponseSchema(
  schema?: EndpointResponseSchema | null | unknown
): EndpointResponseSchema | undefined {
  if (!schema || typeof schema !== "object") return undefined
  const type = typeof (schema as any).type === "string" ? (schema as any).type : undefined
  if (!type) return undefined
  return { type }
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
      />
    </main>
  </div>
</template>

<style scoped>
.error {
  color: #dc2626;
}
</style>
