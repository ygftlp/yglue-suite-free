export interface EntryNodeData {
  nodeType: 'entry'
  method?: string
  path?: string
  requestSchema?: EndpointSchemaField[]
}

export interface ExitNodeData {
  nodeType: 'exit'
  responseMapping: ResponseMappingConfig
}

export interface ResponseMappingConfig {
  type: 'field' | 'constant' | 'expression' | 'groovy'
  value: string
  fieldMappings?: FieldMapping[]
}

export interface FieldMapping {
  source: string
  target: string
  transform?: string
}

export interface EndpointSchemaField {
  name: string
  type: string
}