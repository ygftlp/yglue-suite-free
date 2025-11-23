const API_PREFIX = "/api"

const JSON_HEADERS: HeadersInit = {
  Accept: "application/json",
  "Content-Type": "application/json",
}

type Primitive = string | number | boolean | null | undefined

function resolveUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) return path
  if (!path.startsWith("/")) return `${API_PREFIX}/${path}`
  return `${API_PREFIX}${path}`
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const url = resolveUrl(path)
  
  try {
    const response = await fetch(url, init)

    if (!response.ok) {
      const message = await safeReadText(response)
      const errorMessage = message || response.statusText || "未知错误"
      throw new Error(
        `请求失败 ${response.status} ${response.statusText}: ${errorMessage}`
      )
    }

    if (response.status === 204) {
      return undefined as T
    }

    const contentType = response.headers.get("content-type") || ""
    if (contentType.includes("application/json")) {
      const data = (await response.json()) as unknown
      return normalizeResponse<T>(data)
    }

    const text = await response.text()
    return text as unknown as T
  } catch (error) {
    if (error instanceof Error) {
      throw error
    }
    throw new Error(`网络请求失败: ${String(error)}`)
  }
}

async function safeReadText(response: Response): Promise<string | null> {
  try {
    const text = await response.text()
    return text || null
  } catch (_) {
    return null
  }
}

function normalizeResponse<T>(payload: unknown): T {
  if (payload && typeof payload === "object" && "data" in (payload as any)) {
    return (payload as Record<string, unknown>).data as T
  }
  return payload as T
}

function jsonRequest<T>(path: string, body?: unknown, init: RequestInit = {}): Promise<T> {
  const headers: HeadersInit = { ...JSON_HEADERS, ...(init.headers || {}) }
  const nextInit: RequestInit = {
    ...init,
    headers,
  }
  if (body !== undefined) {
    nextInit.body = JSON.stringify(body)
  }
  return request<T>(path, nextInit)
}

export interface Project {
  id: number
  key: string
  name: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  delFlag?: number
}

export interface FlowSummary {
  id: number
  projectId: number
  code: string
  name: string
  latestVersionId?: number
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  delFlag?: number
}

export interface FlowVersion {
  id: number
  flowId: number
  versionNo: number
  contentJson: string
  published?: boolean
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  delFlag?: number
}

export interface FlowSavePayload {
  code: string
  name: string
  contentJson: string
  createdBy?: string
}

export interface FlowEntrypointPayload {
  path: string
  method?: string | null
  requestSchemaJson?: string | null
  enabled?: boolean
  flowCode?: string | null
  dataResponseFormat?: string | Record<string, any> | null
}

export interface FlowPublishPayload {
  versionNo: number
  publishedBy?: string
  comment?: string
  entrypoint?: FlowEntrypointPayload | null
}

export interface EndpointComponent {
  id?: string | number
  type: string
  displayName: string
  description?: string
  version?: string
  bean?: string
  method?: string
  domain?: string
  tags?: string
  endpointType?: string
  [key: string]: unknown
}

export interface EndpointComponentGroup {
  type: string
  displayName?: string
  count?: number
  items: EndpointComponent[]
}

export interface EndpointEntrypointConfig {
  path?: string | null
  method?: string | null
  enabled?: boolean
  flowCode?: string | null
}

export interface EndpointSchemaField {
  name: string
  type: string
  source?: string
  pathVariable?: string
  paramName?: string
  formField?: string
  typeName?: string
}

export interface EndpointResponseSchema {
  type?: string | null
}

export interface ProjectEndpoint {
  id: number
  projectId: number
  endpointType: string
  method?: string | null
  path?: string | null
  name: string
  description?: string | null
  configJson?: string | null
  requestSchemaJson?: string | null
  enabled?: boolean | null
  flowCode?: string | null
  entrypoint?: EndpointEntrypointConfig | null
  requestSchema?: EndpointSchemaField[] | null
  responseSchema?: EndpointResponseSchema | null
  createTime?: string
  updateTime?: string
  createBy?: string | null
  updateBy?: string | null
  delFlag?: number | null
}

export interface FlowModel {
  id: number
  identifier: string
  name: string
  className: string
  description?: string | null
  category?: string | null
  version?: string | null
  tags?: string[] | null
  schemaJson?: string | null
  updateTime?: string | null
}

export interface FlowResolver {
  id: number
  type: string
  name?: string | null
  description?: string | null
  category?: string | null
  builtin?: boolean | null
  configSchema?: string | null
  className?: string | null
  rawJson?: string | null
  updateTime?: string | null
}

export interface ProjectEndpointCreatePayload {
  endpointType?: string
  method?: string
  path?: string
  name: string
  description?: string
  configJson?: string
  createdBy?: string
}

export interface ProjectEndpointUpdatePayload {
  name?: string
  description?: string
  configJson?: string | null
  entrypoint?: EndpointEntrypointConfig | null
  requestSchema?: EndpointSchemaField[] | null
  responseSchema?: EndpointResponseSchema | null
}

export interface ProjectMetadata {
  id: number
  projectId: number
  contentJson: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  delFlag?: number
}

export interface MetadataUploadPayload {
  contentJson: string
}

export interface StatEvent {
  id: number
  projectId: number
  flowId?: number
  eventType: string
  eventTs?: string
  source?: string
  payloadJson?: string
  createTime?: string
  updateTime?: string
  createBy?: string
  updateBy?: string
  delFlag?: number
}

export interface StatEventPayload {
  eventType: string
  flowCode?: string
  source?: string
  payloadJson?: string
}

export interface PluginHeartbeatPayload {
  instanceKey: string
  ideType?: string
  ideVersion?: string
  status?: string
  hostIp?: string
  extraInfo?: string
}

export interface FlowSyncAckPayload {
  flowCode: string
  versionNo: number
}

export interface PluginHeartbeatResponse {
  needSync: boolean
  pendingFlows: FlowSyncInstruction[]
}

export interface FlowSyncInstruction {
  flowCode: string
  versionNo: number
  contentJson: string
}

export interface PluginInstance {
  instanceKey: string
  ideType?: string
  ideVersion?: string
  status?: string
  lastHeartbeat?: string
  lastIp?: string
  pendingFlows?: number
}

export const api = {
  listProjects(): Promise<Project[]> {
    return request<Project[]>("/projects")
  },

  getProject(projectKey: string): Promise<Project> {
    return request<Project>(`/projects/${encodeURIComponent(projectKey)}`)
  },

  listEndpointComponents(projectKey: string): Promise<EndpointComponentGroup[]> {
    return request<EndpointComponentGroup[]>(
      `/projects/${encodeURIComponent(projectKey)}/endpoints/components`
    )
  },

  listEndpoints(projectKey: string): Promise<ProjectEndpoint[]> {
    return request<ProjectEndpoint[]>(`/projects/${encodeURIComponent(projectKey)}/endpoints`)
  },

  listEndpointRests(projectKey: string): Promise<ProjectEndpoint[]> {
    return request<ProjectEndpoint[]>(`/projects/${encodeURIComponent(projectKey)}/endpoints/rests`)
  },

  getEndpoint(projectKey: string, endpointId: number): Promise<ProjectEndpoint> {
    return request<ProjectEndpoint>(
      `/projects/${encodeURIComponent(projectKey)}/endpoints/${encodeURIComponent(String(endpointId))}`
    )
  },

  createEndpoint(projectKey: string, payload: ProjectEndpointCreatePayload): Promise<ProjectEndpoint> {
    return jsonRequest<ProjectEndpoint>(`/projects/${encodeURIComponent(projectKey)}/endpoints`, payload, {
      method: "POST",
    })
  },

  updateEndpoint(
    projectKey: string,
    endpointId: number,
    payload: ProjectEndpointUpdatePayload
  ): Promise<ProjectEndpoint> {
    return jsonRequest<ProjectEndpoint>(
      `/projects/${encodeURIComponent(projectKey)}/endpoints/${encodeURIComponent(String(endpointId))}`,
      payload,
      { method: "PATCH" }
    )
  },

  listFlows(projectKey: string): Promise<FlowSummary[]> {
    return request<FlowSummary[]>(`/projects/${encodeURIComponent(projectKey)}/flows`)
  },

  saveFlow(projectKey: string, payload: FlowSavePayload): Promise<FlowVersion> {
    return jsonRequest<FlowVersion>(`/projects/${encodeURIComponent(projectKey)}/flows`, payload, {
      method: "POST",
    })
  },

  publishFlow(projectKey: string, flowCode: string, payload: FlowPublishPayload): Promise<FlowVersion> {
    return jsonRequest<FlowVersion>(
      `/projects/${encodeURIComponent(projectKey)}/flows/${encodeURIComponent(flowCode)}/publish`,
      payload,
      { method: "POST" }
    )
  },

  listFlowModels(projectKey: string): Promise<FlowModel[]> {
    return request<FlowModel[]>(`/projects/${encodeURIComponent(projectKey)}/models`)
  },

  listFlowResolvers(projectKey: string): Promise<FlowResolver[]> {
    return request<FlowResolver[]>(`/projects/${encodeURIComponent(projectKey)}/resolvers`)
  },

  listFlowVersions(projectKey: string, flowCode: string): Promise<FlowVersion[]> {
    return request<FlowVersion[]>(
      `/projects/${encodeURIComponent(projectKey)}/flows/${encodeURIComponent(flowCode)}/versions`
    )
  },

  getFlowEntrypoint(projectKey: string, flowCode: string): Promise<FlowEntrypointPayload | null> {
    return request<FlowEntrypointPayload | null>(
      `/projects/${encodeURIComponent(projectKey)}/entrypoints/flows/${encodeURIComponent(flowCode)}`
    )
  },

  getFlowVersion(projectKey: string, flowCode: string, versionNo: number): Promise<FlowVersion> {
    return request<FlowVersion>(
      `/projects/${encodeURIComponent(projectKey)}/flows/${encodeURIComponent(flowCode)}/versions/${versionNo}`
    )
  },

  uploadMetadata(projectKey: string, payload: MetadataUploadPayload): Promise<ProjectMetadata> {
    return jsonRequest<ProjectMetadata>(`/projects/${encodeURIComponent(projectKey)}/metadata`, payload, {
      method: "POST",
    })
  },

  listMetadata(projectKey: string): Promise<ProjectMetadata[]> {
    return request<ProjectMetadata[]>(`/projects/${encodeURIComponent(projectKey)}/metadata`)
  },

  recordStatEvent(projectKey: string, payload: StatEventPayload): Promise<StatEvent> {
    return jsonRequest<StatEvent>(`/projects/${encodeURIComponent(projectKey)}/stats/events`, payload, {
      method: "POST",
    })
  },

  listStatEvents(projectKey: string): Promise<StatEvent[]> {
    return request<StatEvent[]>(`/projects/${encodeURIComponent(projectKey)}/stats/events`)
  },

  pluginHeartbeat(projectKey: string, payload: PluginHeartbeatPayload): Promise<PluginHeartbeatResponse> {
    return jsonRequest<PluginHeartbeatResponse>(
      `/projects/${encodeURIComponent(projectKey)}/plugins/heartbeat`,
      payload,
      { method: "POST" }
    )
  },

  acknowledgeFlowSync(
    projectKey: string,
    instanceKey: string,
    payload: FlowSyncAckPayload
  ): Promise<void> {
    return jsonRequest<void>(
      `/projects/${encodeURIComponent(projectKey)}/plugins/${encodeURIComponent(instanceKey)}/sync/ack`,
      payload,
      { method: "POST" }
    )
  },

  listPluginInstances(projectKey: string): Promise<PluginInstance[]> {
    return request<PluginInstance[]>(`/projects/${encodeURIComponent(projectKey)}/plugins`)
  },
}

export type ApiProject = Project
export type ApiFlow = FlowSummary
export type ApiFlowVersion = FlowVersion
export type ApiPluginInstance = PluginInstance
export type ApiProjectEndpoint = ProjectEndpoint
