<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { useRoute, useRouter } from "vue-router"
import { Network, ArrowLeft } from "lucide-vue-next"
import CanvasEditor from "../components/CanvasEditor.vue"
import {
  api,
  type EndpointResponseSchema,
  type EndpointSchemaField,
  type FlowModel,
  type FlowResolver,
  type FlowServiceSignatureIssue,
  type ProjectEndpoint,
  type ProjectMetadata,
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
const signatureIssues = ref<FlowServiceSignatureIssue[]>([])
const signatureIssuesLoading = ref(false)
const signatureIssuesError = ref<string | null>(null)
const signatureIssueScope = ref<"current" | "all">("current")
const issueFocusRequest = ref<{ path: string; nonce: number } | null>(null)
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

const flowCode = computed(() => {
  if (!endpoint.value) return undefined
  if (endpoint.value.flowCode) return endpoint.value.flowCode
  const ep = endpoint.value.entrypoint
  if (ep && typeof ep === "object" && (ep as any).flowCode) {
    return (ep as any).flowCode
  }
  return undefined
})

const hasAnySignatureIssue = computed(() => signatureIssues.value.length > 0)
const currentFlowSignatureIssue = computed(() => {
  const code = flowCode.value
  if (!code) return null
  return signatureIssues.value.find((item) => item.flowCode === code) || null
})
const otherFlowIssueCount = computed(() => {
  const code = flowCode.value
  if (!code) return signatureIssues.value.length
  return signatureIssues.value.filter((item) => item.flowCode !== code).length
})
const sortedAllFlowIssues = computed(() =>
  [...signatureIssues.value].sort((a, b) => (b.issueCount || 0) - (a.issueCount || 0))
)
const visibleIssueItems = computed(() => {
  if (signatureIssueScope.value === "current") {
    return currentFlowSignatureIssue.value ? [currentFlowSignatureIssue.value] : []
  }
  return sortedAllFlowIssues.value.slice(0, 6)
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

watch([projectKey, endpointId], loadEndpoint, { immediate: true })
watch(projectKey, loadModels, { immediate: true })
watch(projectKey, loadResolvers, { immediate: true })
watch(projectKey, loadInboundInterceptorCatalog, { immediate: true })
watch(projectKey, loadServiceSignatureIssues, { immediate: true })

async function loadEndpoint() {
  if (!projectKey.value || !endpointId.value) return
  endpointLoading.value = true
  endpointError.value = null
  try {
    endpoint.value = await api.getEndpoint(projectKey.value, endpointId.value)
  } catch (err) {
    endpoint.value = null
    endpointError.value = err instanceof Error ? err.message : "Failed to load endpoint."
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

async function loadServiceSignatureIssues() {
  if (!projectKey.value) {
    signatureIssues.value = []
    signatureIssuesError.value = null
    return
  }
  signatureIssuesLoading.value = true
  signatureIssuesError.value = null
  try {
    signatureIssues.value = await api.listFlowServiceSignatureIssues(projectKey.value)
  } catch (err) {
    signatureIssuesError.value = err instanceof Error ? err.message : "加载服务签名问题失败。"
    signatureIssues.value = []
  } finally {
    signatureIssuesLoading.value = false
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

function normalizeInboundInterceptorItem(item: any): {
  code: string
  label: string
  description: string
  defaultOrder: number
  defaultConfig: Record<string, any>
  configSchema?: Record<string, any> | null
  source: "project"
} | null {
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
    source: "project",
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

async function copyIssueSamplePath(item: FlowServiceSignatureIssue) {
  const sample = Array.isArray(item.issueSamples) ? item.issueSamples[0] : ""
  const text = String(sample || "").trim()
  if (!text) {
    window.alert("当前没有可复制的路径样例。")
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    window.alert("已复制路径样例。")
  } catch {
    window.alert(`复制失败，请手动复制：${text}`)
  }
}

function canLocateIssuePath(item: FlowServiceSignatureIssue): boolean {
  if (!item || !Array.isArray(item.issueSamples) || item.issueSamples.length === 0) return false
  if (item.flowCode !== flowCode.value) return false
  return item.issueSamples.some((sample) => parseNodeIndexFromIssuePath(sample) != null)
}

function locateIssueNode(item: FlowServiceSignatureIssue) {
  if (item.flowCode !== flowCode.value) {
    window.alert("仅支持定位当前流程的问题路径。")
    return
  }
  const sample = (item.issueSamples || []).find((value) => parseNodeIndexFromIssuePath(value) != null)
  if (!sample) {
    window.alert("样例中没有可定位的节点路径（需满足 $.nodes[0] 格式）。")
    return
  }
  issueFocusRequest.value = {
    path: String(sample),
    nonce: Date.now(),
  }
}

function parseNodeIndexFromIssuePath(path: unknown): number | null {
  const text = String(path ?? "").trim()
  if (!text) return null
  const match = text.match(/^\$\.nodes\[(\d+)\]/)
  if (!match) return null
  const index = Number.parseInt(match[1], 10)
  return Number.isFinite(index) && index >= 0 ? index : null
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
        <span v-if="endpointLoading">正在加载接口...</span>
        <span v-else-if="endpointError" class="error">{{ endpointError }}</span>
        <span v-else-if="endpoint">{{ endpoint.method }} {{ endpoint.path }}</span>
      </div>
    </header>

    <main style="flex: 1; min-height: 0">
      <section
        v-if="signatureIssuesLoading || signatureIssuesError || hasAnySignatureIssue"
        class="signature-banner"
        :class="{ warn: signatureIssuesError || hasAnySignatureIssue }"
      >
        <div class="signature-main">
          <div v-if="signatureIssuesLoading" class="muted">正在加载签名问题...</div>
          <div v-else-if="signatureIssuesError" class="error">加载签名问题失败：{{ signatureIssuesError }}</div>
          <template v-else>
            <div class="signature-tabs">
              <button
                type="button"
                class="tab-btn"
                :class="{ active: signatureIssueScope === 'current' }"
                @click="signatureIssueScope = 'current'"
              >
                当前流程
              </button>
              <button
                type="button"
                class="tab-btn"
                :class="{ active: signatureIssueScope === 'all' }"
                @click="signatureIssueScope = 'all'"
              >
                全部流程
              </button>
            </div>
            <div v-if="currentFlowSignatureIssue" class="error">
              当前流程存在 {{ currentFlowSignatureIssue.issueCount }} 个问题。
            </div>
            <div v-else class="muted">
              当前流程无问题，其他流程有 {{ otherFlowIssueCount }} 个问题。
            </div>
            <div v-if="visibleIssueItems.length > 0" class="issue-list">
              <div v-for="item in visibleIssueItems" :key="`${item.flowCode}_${item.versionNo ?? 0}`" class="issue-item">
                <span class="issue-flow">{{ item.flowCode }} <span v-if="item.versionNo">v{{ item.versionNo }}</span></span>
                <span class="issue-count">{{ item.issueCount }} 个问题</span>
                <span class="issue-sample">{{ (item.issueSamples || []).slice(0, 2).join(" | ") }}</span>
                <button
                  v-if="canLocateIssuePath(item)"
                  type="button"
                  class="issue-locate-btn"
                  @click="locateIssueNode(item)"
                >
                  定位节点
                </button>
                <button
                  v-if="item.issueSamples?.length"
                  type="button"
                  class="issue-copy-btn"
                  @click="copyIssueSamplePath(item)"
                >
                  复制路径
                </button>
              </div>
            </div>
          </template>
        </div>
        <button type="button" class="btn" style="padding: 4px 10px" @click="loadServiceSignatureIssues">
          刷新
        </button>
      </section>

      <CanvasEditor
        :project-key="projectKey"
        :flow-code="flowCode"
        :entrypoint-hint="entrypointHint"
        :endpoint-schema="schemaHint"
        :flow-models="flowModels"
        :flow-resolvers="flowResolvers"
        :inbound-interceptor-catalog="inboundInterceptorCatalog"
        :endpoint-id="endpointId"
        :issue-focus-request="issueFocusRequest"
      />
    </main>
  </div>
</template>

<style scoped>
.error {
  color: #dc2626;
}

.signature-banner {
  margin: 8px 12px 0;
  border: 1px solid #dbe2ea;
  background: #f8fafc;
  border-radius: 10px;
  padding: 8px 10px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.signature-banner.warn {
  border-color: #f59e0b;
  background: #fff7ed;
}

.signature-main {
  min-width: 0;
  flex: 1 1 auto;
}

.signature-tabs {
  display: inline-flex;
  gap: 6px;
  margin-bottom: 4px;
}

.tab-btn {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 11px;
  line-height: 1.5;
  cursor: pointer;
}

.tab-btn.active {
  border-color: #1d4ed8;
  color: #1d4ed8;
  background: #eff6ff;
}

.issue-list {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.issue-item {
  font-size: 11px;
  line-height: 1.5;
  color: #475569;
  display: flex;
  gap: 8px;
  min-width: 0;
}

.issue-flow {
  color: #0f172a;
  white-space: nowrap;
}

.issue-count {
  color: #b45309;
  white-space: nowrap;
}

.issue-sample {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-copy-btn {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  border-radius: 6px;
  padding: 0 8px;
  font-size: 10px;
  line-height: 20px;
  cursor: pointer;
  white-space: nowrap;
}

.issue-copy-btn:hover {
  border-color: #94a3b8;
  background: #f8fafc;
}

.issue-locate-btn {
  border: 1px solid rgba(13, 148, 136, 0.45);
  background: rgba(20, 184, 166, 0.08);
  color: #0f766e;
  border-radius: 6px;
  padding: 0 8px;
  font-size: 10px;
  line-height: 20px;
  cursor: pointer;
  white-space: nowrap;
}

.issue-locate-btn:hover {
  border-color: rgba(13, 148, 136, 0.75);
  background: rgba(20, 184, 166, 0.14);
}

@media (max-width: 900px) {
  .signature-banner {
    flex-direction: column;
    align-items: stretch;
  }

  .issue-item {
    flex-wrap: wrap;
  }

  .issue-sample {
    flex: 1 0 100%;
    white-space: normal;
    word-break: break-word;
  }
}
</style>

