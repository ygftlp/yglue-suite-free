export type LogPolicy = {
  enabled: boolean
  level: "TRACE" | "DEBUG" | "INFO" | "WARN" | "ERROR"
  collectInputs: boolean
  collectOutputs: boolean
  collectContext: boolean
  collectErrors: boolean
  collectDuration: boolean
  sink: "console" | "kafka" | "http"
  sinkTarget: string
  redactKeys: string
}

/**
 * 自定义字段配置
 */
export type CustomField = {
  fieldName: string
}

/**
 * 数据响应格式配置
 */
export type DataResponseFormatConfig = {
  errorCodeField: string
  errorMessageField: string
  successCode?: number // 成功时的错误码值（默认 1）
  defaultErrorCode?: number // 失败时的默认错误码值（默认 -1）
  customFields?: CustomField[] // 自定义数据字段列表
}

export type FlowEntrypoint = {
  path: string
  method: string
  enabled?: boolean
  requestSchemaJson?: string | null
  dataResponseFormat?: DataResponseFormatConfig | null
}

export type FlowSettings = {
  code: string
  name: string
  description: string
  owner: string
  tags: string
  notes: string
  entrypoint: FlowEntrypoint | null
  logPolicy: LogPolicy
}

/**
 * 生成 UUID v4
 * 统一使用 UUID 格式作为流程标识
 */
function generateUUID(): string {
  // 使用浏览器原生 API（现代浏览器支持）
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  
  // 后备方案：手动生成 UUID v4
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === "x" ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function createDefaultFlowSettings(): FlowSettings {
  return {
    code: generateUUID(),
    name: "",
    description: "",
    owner: "",
    tags: "",
    notes: "",
    entrypoint: null,
    logPolicy: {
      enabled: true,
      level: "INFO",
      collectInputs: true,
      collectOutputs: true,
      collectContext: true,
      collectErrors: true,
      collectDuration: true,
      sink: "console",
      sinkTarget: "",
      redactKeys: "",
    },
  }
}

