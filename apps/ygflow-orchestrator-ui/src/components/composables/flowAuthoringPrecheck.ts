export type FlowPrecheckSeverity = "error" | "warning"
export type FlowPrecheckScope = "flow" | "node" | "edge"

export interface FlowPrecheckIssue {
  scope: FlowPrecheckScope
  severity: FlowPrecheckSeverity
  code: string
  message: string
  nodeId?: string
  edgeId?: string
}

interface TempRef {
  path: string
  rootKey: string
}

function text(value: unknown): string {
  return typeof value === "string" ? value.trim() : ""
}

function asArray<T = any>(value: unknown): T[] {
  return Array.isArray(value) ? value as T[] : []
}

function isRecord(value: unknown): value is Record<string, any> {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value)
}

function isBranchNode(node: any): boolean {
  return node?.type === "branch" || Boolean(node?.data?.branch)
}

function normalizeTempRootKey(raw: string): string {
  const value = text(raw)
  if (!value) return ""
  return value.replace(/^tempVar\(/, "").replace(/\)$/, "").replace(/^temp\./, "").split(".")[0].trim()
}

function extractServiceCallRef(serviceCall: any): { bean: string; method: string } {
  const ref = isRecord(serviceCall?.serviceRef) ? serviceCall.serviceRef : {}
  return {
    bean: text(ref.serviceBean),
    method: text(ref.methodName) || text(serviceCall?.fn).split(".").filter(Boolean).pop() || "",
  }
}

function isServiceCallConfigured(serviceCall: any): boolean {
  const ref = extractServiceCallRef(serviceCall)
  return Boolean(ref.bean && ref.method)
}

function hasNestedCollection(typeName: string): boolean {
  const raw = text(typeName).toLowerCase()
  if (!raw) return false
  const listTokens = ["java.util.list", "list<", "[]", "java.util.set", "set<", "collection<"]
  const tokenHits = listTokens.reduce((count, token) => count + (raw.includes(token) ? 1 : 0), 0)
  return tokenHits >= 2
}

function pushIssue(target: FlowPrecheckIssue[], issue: FlowPrecheckIssue) {
  target.push(issue)
}

function collectTempRefsFromDynamicSource(raw: any, refs: TempRef[]) {
  if (!isRecord(raw)) return
  const kind = text(raw.kind)
  if (kind === "tempVar") {
    const tempKey = text(raw.tempKey)
    const rootKey = normalizeTempRootKey(tempKey)
    if (rootKey) refs.push({ path: tempKey, rootKey })
    return
  }
  if (kind === "serviceCall") {
    collectTempRefsFromServiceCall(raw.serviceCall, refs)
  }
}

function collectTempRefsFromServiceCall(serviceCall: any, refs: TempRef[]) {
  for (const binding of asArray(serviceCall?.argBindings)) {
    const source = isRecord(binding?.source) ? binding.source : {}
    if (text(source.kind) === "tempVar") {
      const tempKey = text(source.tempKey)
      const rootKey = normalizeTempRootKey(tempKey)
      if (rootKey) refs.push({ path: tempKey, rootKey })
    }
    for (const field of asArray(source.objectFields)) {
      const fieldSource = isRecord(field?.source) ? field.source : {}
      if (text(fieldSource.kind) === "tempVar") {
        const tempKey = text(fieldSource.tempKey)
        const rootKey = normalizeTempRootKey(tempKey)
        if (rootKey) refs.push({ path: tempKey, rootKey })
      }
    }
  }
}

function collectTempRefsFromBranchTemp(plan: any, refs: TempRef[]) {
  const kind = text(plan?.kind)
  if (kind === "tempVar") {
    const tempKey = text(plan?.tempKey)
    const rootKey = normalizeTempRootKey(tempKey)
    if (rootKey) refs.push({ path: tempKey, rootKey })
    return
  }
  if (kind === "serviceCall") {
    collectTempRefsFromServiceCall(plan?.serviceCall, refs)
  }
}

function validateDynamicSource(
  source: any,
  issues: FlowPrecheckIssue[],
  target: { nodeId?: string; edgeId?: string },
  pathLabel: string,
) {
  if (!isRecord(source)) return
  const kind = text(source.kind)
  if (kind === "ctx" && !text(source.path)) {
    pushIssue(issues, {
      scope: target.edgeId ? "edge" : "node",
      severity: "error",
      code: "source.ctx.path.required",
      message: `${pathLabel} 缺少上下文路径。`,
      ...target,
    })
  }
  if (kind === "tempVar" && !text(source.tempKey)) {
    pushIssue(issues, {
      scope: target.edgeId ? "edge" : "node",
      severity: "error",
      code: "source.temp.key.required",
      message: `${pathLabel} 缺少临时变量 key。`,
      ...target,
    })
  }
  if (kind === "serviceCall" && !isServiceCallConfigured(source.serviceCall)) {
    pushIssue(issues, {
      scope: target.edgeId ? "edge" : "node",
      severity: "error",
      code: "source.serviceCall.required",
      message: `${pathLabel} 的服务调用尚未完成方法配置。`,
      ...target,
    })
  }
}

function validateBranchTempVars(node: any, issues: FlowPrecheckIssue[]) {
  const tempVars = asArray(node?.data?.tempVars)
  const keyToIndex = new Map<string, number>()
  const refsPerPlan = tempVars.map((plan) => {
    const refs: TempRef[] = []
    collectTempRefsFromBranchTemp(plan, refs)
    return refs
  })

  tempVars.forEach((plan, index) => {
    const key = text(plan?.key)
    if (!key) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "branch.temp.key.required",
        message: `分支临时变量 ${index + 1} 缺少 key。`,
        nodeId: String(node?.id || ""),
      })
      return
    }
    if (keyToIndex.has(key)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "branch.temp.key.duplicate",
        message: `分支临时变量 key 重复：${key}。`,
        nodeId: String(node?.id || ""),
      })
    } else {
      keyToIndex.set(key, index)
    }
  })

  tempVars.forEach((plan, index) => {
    const kind = text(plan?.kind) || "ctx"
    const key = text(plan?.key) || `temp#${index + 1}`
    const target = { nodeId: String(node?.id || "") }

    if (kind === "ctx" && !text(plan?.path)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "branch.temp.ctx.path.required",
        message: `分支临时变量 ${key} 缺少上下文路径。`,
        ...target,
      })
    }
    if (kind === "tempVar" && !text(plan?.tempKey)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "branch.temp.ref.required",
        message: `分支临时变量 ${key} 缺少前序变量引用。`,
        ...target,
      })
    }
    if (kind === "expression" && !text(plan?.expression)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "branch.temp.expression.required",
        message: `分支临时变量 ${key} 缺少表达式。`,
        ...target,
      })
    }
    if (kind === "serviceCall" && !isServiceCallConfigured(plan?.serviceCall)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "branch.temp.serviceCall.required",
        message: `分支临时变量 ${key} 的服务调用尚未完成方法配置。`,
        ...target,
      })
    }

    refsPerPlan[index].forEach((ref) => {
      const refIndex = keyToIndex.get(ref.rootKey)
      if (!refIndex && refIndex !== 0) {
        pushIssue(issues, {
          scope: "node",
          severity: "error",
          code: "branch.temp.ref.missing",
          message: `分支临时变量 ${key} 引用了未定义的变量 ${ref.path}。`,
          ...target,
        })
        return
      }
      if (ref.rootKey === key) {
        pushIssue(issues, {
          scope: "node",
          severity: "error",
          code: "branch.temp.ref.self",
          message: `分支临时变量 ${key} 不能引用自身。`,
          ...target,
        })
        return
      }
      if (refIndex >= index) {
        pushIssue(issues, {
          scope: "node",
          severity: "error",
          code: "branch.temp.ref.forward",
          message: `分支临时变量 ${key} 不能引用后置变量 ${ref.path}。`,
          ...target,
        })
      }
    })
  })
}

function validateBranchConditionEdge(edge: any, issues: FlowPrecheckIssue[], tempKeys: string[]) {
  const condition = isRecord(edge?.data?.conditionV2) ? edge.data.conditionV2 : isRecord(edge?.data?.condition) ? edge.data.condition : null
  if (!condition) return

  const rules = asArray(condition.rules)
  let directServiceCallCount = 0
  for (const rule of rules) {
    const left = isRecord(rule?.left) ? rule.left : {}
    const right = isRecord(rule?.right) ? rule.right : {}
    validateDynamicSource(left, issues, { edgeId: String(edge?.id || "") }, "分支条件左值")
    validateDynamicSource(right, issues, { edgeId: String(edge?.id || "") }, "分支条件右值")

    const refs: TempRef[] = []
    collectTempRefsFromDynamicSource(left, refs)
    collectTempRefsFromDynamicSource(right, refs)
    refs.forEach((ref) => {
      if (!tempKeys.includes(ref.rootKey)) {
        pushIssue(issues, {
          scope: "edge",
          severity: "error",
          code: "branch.condition.temp.missing",
          message: `分支条件引用了未定义的变量 ${ref.path}。`,
          edgeId: String(edge?.id || ""),
        })
      }
    })
    if (text(left.kind) === "serviceCall") directServiceCallCount += 1
    if (text(right.kind) === "serviceCall") directServiceCallCount += 1
  }

  if (directServiceCallCount > 1) {
    pushIssue(issues, {
      scope: "edge",
      severity: "warning",
      code: "branch.condition.serviceCall.tooMany",
      message: `当前分支条件包含 ${directServiceCallCount} 处直接服务调用，建议先下沉到分支临时变量。`,
      edgeId: String(edge?.id || ""),
    })
  }
}

function validateServiceNode(node: any, issues: FlowPrecheckIssue[]) {
  const data = isRecord(node?.data) ? node.data : {}
  const comp = isRecord(data.comp) ? data.comp : {}
  const inputs = asArray(data.inputs)
  const paramPlans = isRecord(data.paramPlans) ? data.paramPlans : {}
  const argPlans = asArray(paramPlans.argPlans)
  const tempPlans = asArray(paramPlans.tempPlans)
  const nodeId = String(node?.id || "")

  const serviceBean = text(comp?.bean || comp?.serviceBean)
  const methodName = text(comp?.method || comp?.methodName)
  if (!serviceBean || !methodName) {
    pushIssue(issues, {
      scope: "node",
      severity: "error",
      code: "service.method.required",
      message: "服务节点尚未完成服务方法选择。",
      nodeId,
    })
  }

  const configuredTargets = new Set<string>()
  for (const plan of argPlans) {
    const target = text(plan?.target)
    if (!target) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.arg.target.required",
        message: "存在未指定目标参数的入参计划。",
        nodeId,
      })
      continue
    }
    if (configuredTargets.has(target)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.arg.target.duplicate",
        message: `目标参数重复装配：${target}。`,
        nodeId,
      })
    } else {
      configuredTargets.add(target)
    }
    const source = isRecord(plan?.source) ? plan.source : {}
    if (text(source.kind) === "ctx" && !text(source.path)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.arg.ctx.path.required",
        message: `参数 ${target} 缺少来源路径。`,
        nodeId,
      })
    }
    if (text(source.kind) === "tempVar" && !text(source.tempKey)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.arg.temp.required",
        message: `参数 ${target} 缺少临时变量 key。`,
        nodeId,
      })
    }
    if (text(source.kind) === "serviceCall" && !isServiceCallConfigured(source.serviceCall)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.arg.serviceCall.required",
        message: `参数 ${target} 的服务调用来源尚未完成方法配置。`,
        nodeId,
      })
    }
  }

  inputs.forEach((input) => {
    const name = text(input?.name)
    if (!name) return
    if (Boolean(input?.required) && !configuredTargets.has(name)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.arg.required",
        message: `必填参数尚未装配：${name}。`,
        nodeId,
      })
    }
    if (hasNestedCollection(text(input?.typeName))) {
      pushIssue(issues, {
        scope: "node",
        severity: "warning",
        code: "service.arg.collection.complex",
        message: `参数 ${name} 是嵌套集合类型，建议发布前做人工复核。`,
        nodeId,
      })
    }
  })

  const tempKeySet = new Set<string>()
  for (const plan of tempPlans) {
    const key = text(plan?.key)
    if (!key) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.temp.key.required",
        message: "存在未命名的服务节点临时变量。",
        nodeId,
      })
      continue
    }
    if (tempKeySet.has(key)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.temp.key.duplicate",
        message: `服务节点临时变量 key 重复：${key}。`,
        nodeId,
      })
    } else {
      tempKeySet.add(key)
    }
    const source = isRecord(plan?.source) ? plan.source : {}
    if (text(source.kind) === "serviceCall" && !isServiceCallConfigured(source.serviceCall)) {
      pushIssue(issues, {
        scope: "node",
        severity: "error",
        code: "service.temp.serviceCall.required",
        message: `临时变量 ${key} 的服务调用尚未完成方法配置。`,
        nodeId,
      })
    }
  }
}

export function collectFlowAuthoringPrecheck(nodes: any[], edges: any[]): FlowPrecheckIssue[] {
  const issues: FlowPrecheckIssue[] = []
  const branchNodeIds = new Set<string>()
  const outgoingBySource = new Map<string, any[]>()

  nodes.forEach((node) => {
    const nodeId = String(node?.id || "")
    if (isBranchNode(node) && nodeId) {
      branchNodeIds.add(nodeId)
    }
  })

  edges.forEach((edge) => {
    const source = String(edge?.source || "")
    const bucket = outgoingBySource.get(source) || []
    bucket.push(edge)
    outgoingBySource.set(source, bucket)
  })

  nodes.forEach((node) => {
    if (node?.type === "service") {
      validateServiceNode(node, issues)
      return
    }
    if (isBranchNode(node)) {
      validateBranchTempVars(node, issues)
      const tempKeys = asArray(node?.data?.tempVars).map((item) => text(item?.key)).filter(Boolean)
      for (const edge of outgoingBySource.get(String(node?.id || "")) || []) {
        validateBranchConditionEdge(edge, issues, tempKeys)
      }
    }
  })

  edges.forEach((edge) => {
    if (!branchNodeIds.has(String(edge?.source || ""))) return
    if (!text(edge?.id)) return
    if (!Number.isFinite(Number(edge?.data?.priority ?? 100))) {
      pushIssue(issues, {
        scope: "edge",
        severity: "warning",
        code: "branch.edge.priority.invalid",
        message: "分支优先级不是有效数字，系统将回退为默认值 100。",
        edgeId: String(edge.id),
      })
    }
  })

  return issues
}

export function summarizeFlowPrecheck(issues: FlowPrecheckIssue[]) {
  return {
    errors: issues.filter((item) => item.severity === "error").length,
    warnings: issues.filter((item) => item.severity === "warning").length,
  }
}

export function filterNodePrecheckIssues(issues: FlowPrecheckIssue[], nodeId: string) {
  return issues.filter((item) => item.nodeId === nodeId)
}

export function filterEdgePrecheckIssues(issues: FlowPrecheckIssue[], edgeId: string) {
  return issues.filter((item) => item.edgeId === edgeId)
}
