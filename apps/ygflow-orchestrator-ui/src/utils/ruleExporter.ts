import type { FlowSettings } from "../data/flowSettings"

export type ExportNode = {
  id: string
  type?: string
  data?: Record<string, any>
}

export type ExportEdge = {
  id?: string
  source: string
  target: string
  data?: Record<string, any>
}

function describeNodeBinding(node: ExportNode): string {
  const { type, data } = node

  if (type === "service" && data?.comp) {
    const bean = data.comp.bean || data.comp.serviceBean
    const method = data.comp.method
    if (bean && method) {
      return `${bean}.${method}`
    }
    if (bean) {
      return bean
    }
  }

  if (type === "transformer") {
    return `script:${node.id}`
  }

  if (type === "branch") {
    return `branch:${node.id}`
  }

  if (type === "rest") {
    return `rest:${node.id}`
  }

  return node.id
}

function generateChains(nodes: ExportNode[], edges: ExportEdge[]) {
  const adjacency = new Map<string, string[]>()
  const indegree = new Map<string, number>()
  nodes.forEach((node) => indegree.set(node.id, 0))

  edges.forEach((edge) => {
    if (!adjacency.has(edge.source)) adjacency.set(edge.source, [])
    adjacency.get(edge.source)!.push(edge.target)
    indegree.set(edge.target, (indegree.get(edge.target) || 0) + 1)
  })

  const starts = nodes.filter((node) => (indegree.get(node.id) || 0) === 0).map((node) => node.id)
  const visited = new Set<string>()
  const chains: string[] = []

  starts.forEach((start) => {
    const chain = traverseChain(start, adjacency, visited)
    if (chain.length) {
      chains.push(`chain(${start}) = ${chain.join(" -> ")}`)
    }
  })

  return chains
}

function traverseChain(nodeId: string, adjacency: Map<string, string[]>, visited: Set<string>) {
  const sequence: string[] = []
  const stack: string[] = [nodeId]

  while (stack.length) {
    const current = stack.pop()!
    if (visited.has(current)) continue
    visited.add(current)
    sequence.push(current)
    const next = adjacency.get(current) || []
    for (let index = next.length - 1; index >= 0; index -= 1) {
      stack.push(next[index])
    }
  }

  return sequence
}

export function generateRulePreview(nodes: ExportNode[], edges: ExportEdge[], settings?: FlowSettings) {
  const nodeLines = nodes.map((node) => {
    const binding = describeNodeBinding(node)
    return `- ${node.id} (${node.type || "service"}): ${node.data?.label || ""} -> ${binding}`
  })
  const edgeLines = edges.map((edge) => `${edge.source} -> ${edge.target}`)
  const chains = generateChains(nodes, edges)

  const sections = [
    "# YGFlow Rule Preview",
    `# Generated: ${new Date().toISOString()}`,
    "",
    "[Node Bindings]",
    ...(nodeLines.length ? nodeLines : ["(empty)"]),
    "",
    "[Edges]",
    ...(edgeLines.length ? edgeLines : ["(empty)"]),
    "",
    "[Execution Chains]",
    ...(chains.length ? chains : ["(empty)"]),
  ]

  if (settings) {
    const log = settings.logPolicy
    const collectParts = [
      log.collectInputs && "inputs",
      log.collectOutputs && "outputs",
      log.collectContext && "context",
      log.collectErrors && "errors",
      log.collectDuration && "duration",
    ]
      .filter(Boolean)
      .join(", ")

    sections.push(
      "",
      "[Flow Settings]",
      `Name: ${settings.name || "-"}`,
      `Code: ${settings.code || "-"}`,
      `Owner: ${settings.owner || "-"}`,
      `Log: ${
        log.enabled
          ? `${log.level} -> ${log.sink}${log.sinkTarget ? `(${log.sinkTarget})` : ""}`
          : "disabled"
      }`,
      `Collect: ${log.enabled ? collectParts || "none" : "n/a"}`
    )

    if (settings.tags) {
      sections.push(`Tags: ${settings.tags}`)
    }
    if (settings.notes) {
      sections.push(`Notes: ${settings.notes}`)
    }
  }

  const text = sections.join("\n")

  return {
    text,
    nodes: nodeLines,
    edges: edgeLines,
    chains,
  }
}

export function downloadRuleFile(content: string, filename: string) {
  if (typeof window === "undefined") return
  const blob = new Blob([content], { type: "text/plain;charset=utf-8" })
  const url = URL.createObjectURL(blob)
  const link = document.createElement("a")
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
