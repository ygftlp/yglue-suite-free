/**
 * 格式化工具函数
 */

/**
 * 格式化时间戳
 */
export function formatTimestamp(value?: string | null): string {
  if (!value) return "-"
  try {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return value
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
    })
  } catch (_) {
    return value
  }
}

/**
 * 安全解析 JSON
 */
export function safeParseJson<T = unknown>(value?: string | null): T | null {
  if (!value) return null
  try {
    return JSON.parse(value) as T
  } catch (_) {
    return null
  }
}

/**
 * 规范化路径（去除首尾空格，确保以 / 开头）
 */
export function normalizePath(path: string | null | undefined): string {
  if (!path) return "/"
  const trimmed = path.trim()
  if (!trimmed) return "/"
  return trimmed.startsWith("/") ? trimmed : `/${trimmed}`
}




