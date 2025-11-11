import type { EndpointResponseSchema, EndpointSchemaField } from "../../api/client"
import type { FlowEntrypoint } from "../../data/flowSettings"

export type EndpointSchemaHint = {
  requestSchema?: EndpointSchemaField[] | null
  requestSchemaJson?: string | null
  responseSchema?: EndpointResponseSchema | null
} | null

export type CanvasEditorProps = {
  projectKey: string
  flowCode?: string
  entrypointHint?: Partial<FlowEntrypoint> | null
  endpointSchema?: EndpointSchemaHint
}

export type ContextMenuState = {
  visible: boolean
  x: number
  y: number
  nodeId: string | null
  label: string
}

