import type { EndpointResponseSchema, EndpointSchemaField, FlowModel, FlowResolver } from "../../api/client"
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
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
  endpointId?: number  // 端点ID，用于脚本编辑器加载类成员信息
}

export type ContextMenuState = {
  visible: boolean
  x: number
  y: number
  nodeId: string | null
  label: string
}

