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

export type FlowEntrypoint = {
  path: string
  method: string
  replaceResponse: boolean
  enabled?: boolean
  requestSchema?: string | null
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

