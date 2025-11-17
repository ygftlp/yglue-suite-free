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

/**
 * 获取节点的 LiteFlow 组件名称
 */
function getNodeComponentName(node: ExportNode): string {
  const { type, data } = node
  const nodeId = node.id

  // 任务节点：使用 bean.method 格式
  if (type === "task" && data?.comp) {
    const bean = data.comp.bean || data.comp.flowApiBeanName
    const method = data.comp.method
    if (bean && method) {
      return `${bean}.${method}`
    }
    if (bean) {
      return bean
    }
  }

  // 脚本节点
  if (type === "transformer") {
    return `script_${nodeId}`
  }

  // 分支节点：需要映射到条件组件
  if (type === "branch") {
    return `branch_${nodeId}`
  }

  // 事务节点：特殊处理
  if (type === "transaction") {
    const variant = data?.transaction === "end" ? "end" : "begin"
    return `txn_${variant}_${nodeId}`
  }

  // 默认使用节点 ID
  return nodeId
}

/**
 * 生成真正的 LiteFlow EL 表达式
 */
function generateLiteFlowEL(nodes: ExportNode[], edges: ExportEdge[]): string {
  if (nodes.length === 0) {
    return ""
  }

  // 构建邻接表和入度
  const adjacency = new Map<string, string[]>()
  const indegree = new Map<string, number>()
  const nodeMap = new Map<string, ExportNode>()
  
  nodes.forEach((node) => {
    indegree.set(node.id, 0)
    nodeMap.set(node.id, node)
  })

  edges.forEach((edge) => {
    if (!adjacency.has(edge.source)) {
      adjacency.set(edge.source, [])
    }
    adjacency.get(edge.source)!.push(edge.target)
    indegree.set(edge.target, (indegree.get(edge.target) || 0) + 1)
  })

  // 找到所有起始节点
  const starts = nodes.filter((node) => (indegree.get(node.id) || 0) === 0).map((node) => node.id)

  if (starts.length === 0) {
    // 如果没有起始节点，可能是循环依赖，返回第一个节点
    if (nodes.length > 0) {
      starts.push(nodes[0].id)
    }
  }

  const chains: string[] = []

  starts.forEach((start, index) => {
    const chainName = starts.length === 1 ? "chain1" : `chain${index + 1}`
    const visited = new Set<string>()
    const expression = buildChainExpression(start, adjacency, nodeMap, visited)
    if (expression) {
      chains.push(`chain("${chainName}") = ${expression}`)
    }
  })

  return chains.join("\n")
}

/**
 * 构建链表达式
 */
function buildChainExpression(
  nodeId: string,
  adjacency: Map<string, string[]>,
  nodeMap: Map<string, ExportNode>,
  visited: Set<string>
): string {
  if (visited.has(nodeId)) {
    return ""
  }
  visited.add(nodeId)

  const node = nodeMap.get(nodeId)
  if (!node) {
    return ""
  }

  const componentName = getNodeComponentName(node)
  const nextNodes = adjacency.get(nodeId) || []

  // 分支节点处理
  if (node.type === "branch") {
    return buildBranchExpression(nodeId, nextNodes, adjacency, nodeMap, visited, componentName)
  }

  // 单个后续节点：顺序执行
  if (nextNodes.length === 1) {
    const nextExpr = buildChainExpression(nextNodes[0], adjacency, nodeMap, visited)
    if (nextExpr) {
      return `THEN(${componentName}, ${nextExpr})`
    }
    return componentName
  }

  // 多个后续节点：并行执行
  if (nextNodes.length > 1) {
    const nextExprs = nextNodes
      .map((nextId) => buildChainExpression(nextId, adjacency, nodeMap, visited))
      .filter((expr) => expr)
    if (nextExprs.length > 0) {
      return `THEN(${componentName}, WHEN(${nextExprs.join(", ")}))`
    }
    return componentName
  }

  // 没有后续节点
  return componentName
}

/**
 * 构建分支表达式
 */
function buildBranchExpression(
  nodeId: string,
  nextNodes: string[],
  adjacency: Map<string, string[]>,
  nodeMap: Map<string, ExportNode>,
  visited: Set<string>,
  conditionComponent: string
): string {
  if (nextNodes.length === 0) {
    return conditionComponent
  }

  if (nextNodes.length === 1) {
    const nextExpr = buildChainExpression(nextNodes[0], adjacency, nodeMap, visited)
    if (nextExpr) {
      return `THEN(${conditionComponent}, ${nextExpr})`
    }
    return conditionComponent
  }

  // 多个分支：使用 IF-ELSE 或 SWITCH
  // 简化处理：使用 IF-ELSE 结构
  if (nextNodes.length === 2) {
    const trueExpr = buildChainExpression(nextNodes[0], adjacency, nodeMap, visited)
    const falseExpr = buildChainExpression(nextNodes[1], adjacency, nodeMap, visited)
    
    if (trueExpr && falseExpr) {
      return `IF(${conditionComponent}, THEN(${trueExpr}), ELSE(${falseExpr}))`
    }
    if (trueExpr) {
      return `IF(${conditionComponent}, THEN(${trueExpr}))`
    }
    if (falseExpr) {
      return `IF(${conditionComponent}, ELSE(${falseExpr}))`
    }
  }

  // 多个分支：使用 SWITCH
  const branchExprs = nextNodes
    .map((nextId) => buildChainExpression(nextId, adjacency, nodeMap, visited))
    .filter((expr) => expr)
  
  if (branchExprs.length > 0) {
    return `SWITCH(${conditionComponent}).TO(${branchExprs.join(", ")})`
  }

  return conditionComponent
}

export function generateLiteFlowRule(nodes: ExportNode[], edges: ExportEdge[], settings?: FlowSettings) {
  const nodeLines = nodes.map((node) => {
    const componentName = getNodeComponentName(node)
    return `- ${node.id} (${node.type || "task"}): ${node.data?.label || ""} -> ${componentName}`
  })
  const edgeLines = edges.map((edge) => `${edge.source} -> ${edge.target}`)
  const chains = generateChains(nodes, edges)
  
  // 生成真正的 LiteFlow EL 表达式
  const elExpression = generateLiteFlowEL(nodes, edges)

  const sections = [
    `# LiteFlow Rule Preview`,
    `# Generated: ${new Date().toISOString()}`,
    ``,
    "[LiteFlow EL Expression]",
    elExpression || "(无法生成 EL 表达式)",
    ``,
    "[Nodes]",
    ...nodeLines,
    ``,
    "[Edges]",
    ...edgeLines,
    ``,
    "[Chains (Preview)]",
    ...chains,
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
    for (let i = next.length - 1; i >= 0; i -= 1) {
      stack.push(next[i])
    }
  }

  return sequence
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

