export function formatTimestamp(value: string) {
  if (!value) return ""
  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString()
  } catch {
    return value
  }
}

export function generateUUID(): string {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === "x" ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

export function generateContextKey() {
  const chars = "abcdef0123456789"
  let suffix = ""
  for (let i = 0; i < 32; i++) {
    suffix += chars[Math.floor(Math.random() * chars.length)]
  }
  return "ret" + suffix
}

export function inferValueType(javaType: string): string {
  if (!javaType) return "STRING"
  const type = javaType.toLowerCase()
  if (
    type === "int" ||
    type === "integer" ||
    type === "long" ||
    type === "short" ||
    type === "byte" ||
    type === "float" ||
    type === "double" ||
    type === "java.lang.integer" ||
    type === "java.lang.long" ||
    type === "java.lang.short" ||
    type === "java.lang.byte" ||
    type === "java.lang.float" ||
    type === "java.lang.double" ||
    type === "java.math.bigdecimal" ||
    type === "java.math.biginteger"
  ) {
    return "NUMBER"
  }
  if (type === "boolean" || type === "java.lang.boolean") {
    return "BOOLEAN"
  }
  if (
    type.includes("[]") ||
    type.includes("array") ||
    type.includes("list") ||
    type.includes("set") ||
    type.includes("collection") ||
    type.includes("java.util.list") ||
    type.includes("java.util.set") ||
    type.includes("java.util.collection")
  ) {
    return "ARRAY"
  }
  if (type.includes("map") || type.includes("java.util.map")) {
    return "OBJECT"
  }
  if (type === "string" || type === "java.lang.string" || type === "char" || type === "java.lang.character") {
    return "STRING"
  }
  return "OBJECT"
}

import type { FlowEntrypoint } from "../../data/flowSettings"

export function normalizeEntrypoint(input?: Partial<FlowEntrypoint> | null): FlowEntrypoint | null {
  if (!input) return null
  const path = (input.path || "").trim()
  if (!path) return null
  const method = (input.method || "").trim().toUpperCase()
  const requestSchema =
    typeof input.requestSchema === "string" ? input.requestSchema : input.requestSchema ? JSON.stringify(input.requestSchema) : null
  return {
    path,
    method,
    replaceResponse: Boolean(input.replaceResponse),
    enabled: input.enabled !== false,
    requestSchema: requestSchema ?? null,
  }
}

export function serializeEntrypointPayload(entrypoint: FlowEntrypoint | null) {
  if (!entrypoint) return null
  return {
    path: entrypoint.path,
    method: entrypoint.method || null,
    replaceResponse: Boolean(entrypoint.replaceResponse),
    enabled: entrypoint.enabled !== false,
    requestSchema: entrypoint.requestSchema ?? null,
  }
}

export function safeParseContent<T = Record<string, any>>(contentJson: string | null | undefined): T | Record<string, any> {
  if (!contentJson) return {}
  try {
    return JSON.parse(contentJson) as T
  } catch (err) {
    console.warn("解析流程内容失败", err)
    return {}
  }
}

