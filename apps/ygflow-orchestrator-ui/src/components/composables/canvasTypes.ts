import type { EndpointResponseSchema, EndpointSchemaField, FlowModel, FlowResolver } from "../../api/client"
import type { FlowEntrypoint } from "../../data/flowSettings"

export type InboundInterceptorCatalogItem = {
  code: string
  label?: string
  description?: string
  defaultOrder?: number
  defaultConfig?: Record<string, any>
  configSchema?: Record<string, any> | null
  source?: "builtin" | "project" | string
}

export type EndpointSchemaHint = {
  requestSchema?: EndpointSchemaField[] | null
  requestSchemaJson?: string | null
  responseSchema?: EndpointResponseSchema | null
} | null

export type IssueFocusRequest = {
  path: string
  nonce: number
} | null

export type CanvasEditorProps = {
  projectKey: string
  flowCode?: string
  entrypointHint?: Partial<FlowEntrypoint> | null
  endpointSchema?: EndpointSchemaHint
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
  inboundInterceptorCatalog?: InboundInterceptorCatalogItem[] | null
  endpointId?: number
  issueFocusRequest?: IssueFocusRequest
}

export type ContextMenuState = {
  visible: boolean
  x: number
  y: number
  nodeId: string | null
  label: string
}
