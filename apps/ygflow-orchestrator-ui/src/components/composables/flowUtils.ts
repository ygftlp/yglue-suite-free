import type { FlowEntrypoint } from "../../data/flowSettings"
import type { FlowEntrypointPayload } from "../../api/client"

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

export function isValidUUID(str: string | null | undefined): boolean {
  if (!str || typeof str !== "string") return false
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i
  return uuidRegex.test(str.trim())
}

export function ensureUUIDCode(code: string | null | undefined): string {
  if (isValidUUID(code)) {
    return String(code).trim()
  }
  return generateUUID()
}

export function generateContextKey() {
  const chars = "abcdef0123456789"
  let suffix = ""
  for (let i = 0; i < 12; i++) {
    suffix += chars[Math.floor(Math.random() * chars.length)]
  }
  return `node_result_${suffix}`
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

export function normalizeEntrypoint(
  input?: Partial<FlowEntrypoint> | FlowEntrypointPayload | null
): FlowEntrypoint | null {
  if (!input) return null
  const path = (input.path || "").trim()
  if (!path) return null
  const method = (input.method || "").trim().toUpperCase()

  const requestSchemaJson = typeof input.requestSchemaJson === "string"
    ? input.requestSchemaJson
    : input.requestSchemaJson
      ? JSON.stringify(input.requestSchemaJson)
      : typeof (input as any).requestSchema === "string"
        ? (input as any).requestSchema
        : (input as any).requestSchema
          ? JSON.stringify((input as any).requestSchema)
          : null

  let dataResponseFormat: FlowEntrypoint["dataResponseFormat"] = null
  if ((input as any).dataResponseFormat != null) {
    const format = (input as any).dataResponseFormat
    if (typeof format === "string") {
      const preset = format.trim()
      dataResponseFormat = preset || null
    } else if (typeof format === "object" && !Array.isArray(format)) {
      dataResponseFormat = format as NonNullable<FlowEntrypoint["dataResponseFormat"]>
    }
  }

  const inboundInterceptors = Array.isArray((input as any).inboundInterceptors)
    ? (input as any).inboundInterceptors
    : null

  return {
    path,
    method,
    enabled: input.enabled !== false,
    requestSchemaJson: requestSchemaJson ?? null,
    dataResponseFormat,
    inboundInterceptors,
  }
}

export function serializeEntrypointPayload(entrypoint: FlowEntrypoint | null) {
  if (!entrypoint) return null
  return {
    path: entrypoint.path,
    method: entrypoint.method || null,
    enabled: entrypoint.enabled !== false,
    requestSchemaJson: entrypoint.requestSchemaJson ?? null,
    dataResponseFormat: entrypoint.dataResponseFormat ?? null,
    inboundInterceptors: entrypoint.inboundInterceptors ?? null,
  }
}

export function safeParseContent<T = Record<string, any>>(contentJson: string | null | undefined): T | Record<string, any> {
  if (!contentJson) return {}
  try {
    return JSON.parse(contentJson) as T
  } catch (err) {
    console.warn("Failed to parse flow content JSON", err)
    return {}
  }
}
