import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import ServiceNode from "../ServiceNode.vue"
import RestNode from "../RestNode.vue"
import BranchNode from "../BranchNode.vue"
import TransformerNode from "../TransformerNode.vue"
import ServiceGroupNode from "../ServiceGroupNode.vue"
import type { ContextMenuState } from "./canvasTypes"
import { generateContextKey, inferValueType } from "./flowUtils"

export function useFlowState() {
  const nodes = ref<any[]>([])
  const edges = ref<any[]>([])
  const selected = ref<any | null>(null)
  const selectedEdge = ref<any | null>(null)
  const contextMenu = ref<ContextMenuState>({ visible: false, x: 0, y: 0, nodeId: null, label: "" })

  const nodeTypes = {
    service: ServiceNode,
    rest: RestNode,
    branch: BranchNode,
    transformer: TransformerNode,
    serviceGroup: ServiceGroupNode,
  } as const

  const canDelete = computed(() => {
    if (contextMenu.value.visible && contextMenu.value.nodeId) return true
    return Boolean(selected.value || selectedEdge.value)
  })

  function hideContextMenu() {
    contextMenu.value = { visible: false, x: 0, y: 0, nodeId: null, label: "" }
  }

  function setGraph(newNodes: any[], newEdges: any[]) {
    console.log("[useFlowState] setGraph called:", {
      newNodesCount: Array.isArray(newNodes) ? newNodes.length : 0,
      newEdgesCount: Array.isArray(newEdges) ? newEdges.length : 0,
      currentNodesCount: nodes.value.length,
      currentEdgesCount: edges.value.length
    })
    const validNodes = Array.isArray(newNodes) ? newNodes : []
    const validEdges = Array.isArray(newEdges) ? newEdges : []
    nodes.value = validNodes
    edges.value = validEdges
    selected.value = null
    selectedEdge.value = null
    hideContextMenu()
    console.log("[useFlowState] setGraph completed:", {
      nodesCount: nodes.value.length,
      edgesCount: edges.value.length
    })
  }

  function resetGraph() {
    setGraph([], [])
  }

  function removeNodeById(id: string) {
    nodes.value = nodes.value.filter((item) => item.id !== id)
    edges.value = edges.value.filter((edge) => edge.source !== id && edge.target !== id)
    if (selected.value?.id === id) selected.value = null
    if (selectedEdge.value && (selectedEdge.value.source === id || selectedEdge.value.target === id)) {
      selectedEdge.value = null
    }
    hideContextMenu()
  }

  function removeEdgeById(id: string) {
    edges.value = edges.value.filter((edge) => edge.id !== id)
    if (selectedEdge.value?.id === id) selectedEdge.value = null
  }

  function removeContextMenuNode() {
    if (!contextMenu.value.nodeId) return
    removeNodeById(contextMenu.value.nodeId)
    hideContextMenu()
  }

  function deleteSelection() {
    if (contextMenu.value.visible && contextMenu.value.nodeId) {
      removeNodeById(contextMenu.value.nodeId)
      return
    }
    if (selected.value) {
      removeNodeById(selected.value.id)
      return
    }
    if (selectedEdge.value) {
      removeEdgeById(selectedEdge.value.id)
    }
  }

  function clearSelection() {
    selected.value = null
    selectedEdge.value = null
    hideContextMenu()
  }

  function focusNodeById(nodeId: string): boolean {
    const exists = nodes.value.some((node) => node.id === nodeId)
    if (!exists) return false
    nodes.value = nodes.value.map((node) => ({
      ...node,
      selected: node.id === nodeId,
    }))
    selected.value = nodes.value.find((node) => node.id === nodeId) ?? null
    selectedEdge.value = null
    hideContextMenu()
    return true
  }

  function handleGlobalClick() {
    if (contextMenu.value.visible) hideContextMenu()
  }

  function handleKeydown(event: KeyboardEvent) {
    if (event.key !== "Delete" && event.key !== "Backspace") return
    const target = event.target as HTMLElement | null
    const tag = target?.tagName
    const isTyping =
      tag === "INPUT" || tag === "TEXTAREA" || tag === "SELECT" || (target as HTMLElement | null)?.isContentEditable
    if (isTyping || !canDelete.value) return
    event.preventDefault()
    deleteSelection()
  }

  onMounted(() => {
    window.addEventListener("click", handleGlobalClick)
    window.addEventListener("keydown", handleKeydown)
  })

  onBeforeUnmount(() => {
    window.removeEventListener("click", handleGlobalClick)
    window.removeEventListener("keydown", handleKeydown)
  })

  function hasPath(fromNodeId: string, toNodeId: string, adjacency: Map<string, Set<string>>) {
    if (fromNodeId === toNodeId) return true
    const visited = new Set<string>()
    const stack = [fromNodeId]
    while (stack.length) {
      const current = stack.pop()!
      if (current === toNodeId) return true
      if (visited.has(current)) continue
      visited.add(current)
      const nextSet = adjacency.get(current)
      if (!nextSet) continue
      for (const next of nextSet) {
        if (!visited.has(next)) stack.push(next)
      }
    }
    return false
  }

  function createsCycle(sourceId: string, targetId: string) {
    const adjacency = new Map<string, Set<string>>()
    for (const edge of edges.value) {
      const source = String(edge.source || "")
      const target = String(edge.target || "")
      if (!source || !target) continue
      if (!adjacency.has(source)) adjacency.set(source, new Set())
      adjacency.get(source)!.add(target)
    }
    if (!adjacency.has(sourceId)) adjacency.set(sourceId, new Set())
    adjacency.get(sourceId)!.add(targetId)
    return hasPath(targetId, sourceId, adjacency)
  }

  function isSingleOutgoingType(nodeType?: string) {
    return nodeType === "service" || nodeType === "rest" || nodeType === "transaction" || nodeType === "transformer"
  }

  function isSingleIncomingType(nodeType?: string) {
    return nodeType === "service" || nodeType === "rest" || nodeType === "branch" || nodeType === "transaction" || nodeType === "transformer"
  }

  function handleConnect(params: any) {
    if (!params?.source || !params?.target) return

    const sourceNode = nodes.value.find((n: any) => n.id === params.source)
    const targetNode = nodes.value.find((n: any) => n.id === params.target)
    if (!sourceNode || !targetNode) return

    if (sourceNode.type === "serviceGroup" || targetNode.type === "serviceGroup") {
      window.alert("服务组是容器节点，不能直接连线")
      return
    }

    const sourceParent = sourceNode.parentNode
    const targetParent = targetNode.parentNode
    if (sourceParent !== targetParent && (sourceParent || targetParent)) {
      window.alert("服务组内节点只能与同组节点连线")
      return
    }

    if (params.source === params.target) {
      window.alert("不允许节点连接到自身")
      return
    }

    const duplicated = edges.value.some((e: any) => e.source === params.source && e.target === params.target)
    if (duplicated) {
      window.alert("已存在相同连线，不能重复添加")
      return
    }

    if (isSingleOutgoingType(sourceNode.type)) {
      const outCount = edges.value.filter((e: any) => e.source === params.source).length
      if (outCount >= 1) {
        window.alert("该节点仅允许 1 条出线")
        return
      }
    }

    if (isSingleIncomingType(targetNode.type)) {
      const inCount = edges.value.filter((e: any) => e.target === params.target).length
      if (inCount >= 1) {
        window.alert("该节点仅允许 1 条入线")
        return
      }
    }

    if (createsCycle(String(params.source), String(params.target))) {
      window.alert("不允许形成环路")
      return
    }

    const edge = {
      ...params,
      type: "smoothstep",
      id: String(Date.now()),
      data: { label: "", priority: 100, conditionV2: null, expression: "" },
      label: "",
    }
    edges.value = [...edges.value, edge]
    selectedEdge.value = edge
    selected.value = null
  }
  function onNodeClick({ node }: any) {
    selected.value = node
    selectedEdge.value = null
    hideContextMenu()
  }

  function onEdgeClick({ edge }: any) {
    selectedEdge.value = edge
    selected.value = null
    hideContextMenu()
  }

  function updateNode(node: any) {
    nodes.value = nodes.value.map((n) => (n.id === node.id ? node : n))
    selected.value = node
  }

  function updateEdge(edge: any) {
    edges.value = edges.value.map((item) => (item.id === edge.id ? edge : item))
    selectedEdge.value = edge
  }

  function onNodeContextMenu({ node, event }: any) {
    event?.preventDefault()
    contextMenu.value = {
      visible: true,
      x: event.clientX,
      y: event.clientY,
      nodeId: node.id,
      label: node.data?.label || node.label || "",
    }
  }

  function onEdgeContextMenu({ edge, event }: any) {
    event?.preventDefault()
    hideContextMenu()
    if (confirm("确认删除这条连线吗？")) {
      removeEdgeById(edge.id)
    }
  }

  function addNodeFromPalette(item: any, position: { x: number; y: number }, parentId?: string) {
    if (!item) return
    const id = String(Date.now())
    // 根据 endpointType 决定节点类型：FLOW_OPERATION -> service（本地服务调用）
    let nodeType = item.nodeType
    if (!nodeType && item.endpointType) {
      const endpointType = String(item.endpointType).toUpperCase()
      if (endpointType === "FLOW_OPERATION") {
        nodeType = "service"  // 本地服务调用使用 service 节点
      } else if (endpointType === "REST" || endpointType === "HTTP") {
        nodeType = "rest"  // REST 调用使用 rest 节点
      }
    }
    nodeType = nodeType ?? "service"  // 默认使用 service 节点
    if (nodeType === "transformer") {
      window.alert("脚本节点已下线，请使用服务节点或 HTTP 节点。")
      return
    }
    const label =
      nodeType === "branch"
        ? item.title || item.displayName || "Branch"
        : item.displayName || item.title || item.bean || item.fqcn || item.name || "节点"

    const mapInputs = (list: any[] = []) =>
      list.map((input: any) => ({
        name: input.name || "",
        description: input.description || "",
        valueType: (input.valueType || "STRING").toUpperCase(),
        typeName: input.typeName || "",
        // 保留 IDE 上报的完整 JSON Schema，供 ParamPlanBuilder 处理嵌套字段映射
        schema: input.schema || null,
      }))

    const buildOutputFieldsFromSchema = (schema: any) => {
      if (!schema || typeof schema !== "object" || Array.isArray(schema)) return []
      const properties = schema.properties
      if (!properties || typeof properties !== "object" || Array.isArray(properties)) return []
      return Object.entries(properties).map(([name, raw]) => {
        const child = raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, any> : {}
        return {
          name,
          type: child["x-javaType"] || child.typeName || child.type || "object",
          description: child.description || child.title || "",
        }
      })
    }

    const mapOutput = (output: any) => ({
      description: output?.description || "",
      valueType: output?.valueType || "OBJECT",
      typeName: output?.typeName || "",
      fields: output?.fields || [],
      contextKey: output?.contextKey || generateContextKey(),
    })

    const data = (() => {
      if (nodeType === "branch") {
        return { label, branch: true, expression: "", fallbackNote: "", tempVars: [] }
      }
      if (nodeType === "transaction") {
        const variant = item.variant === "end" ? "end" : "begin"
        const defaultLabel = variant === "end" ? "事务结束" : "事务开始"
        return { label: item.title || defaultLabel, transaction: variant }
      }
      if (nodeType === "serviceGroup") {
        return {
          label: item.title || "服务组",
          enableTransaction: false,
          transactionManager: "defaultTransactionManager",
        }
      }
      if (nodeType === "transformer") {
        return {
          label: item.title || "脚本节点",
          mappingConfig: {
            fieldMappings: [],
          },
          script: "",
        }
      }
      if (nodeType === "rest") {
        return {
          label: item.title || item.displayName || "HTTP 调用",
          method: "GET",
          url: "",
          headers: [],
          query: [],
          body: "",
          timeoutSeconds: 10,
          retryCount: 0,
          retryBackoffMs: 0,
          retryOnStatuses: [429, 500, 502, 503, 504],
          as: generateContextKey(),
        }
      }
      const base = {
        label,
        inputs: [],
        output: null,
      }
      if (item.tab === "components") {
        return {
          ...base,
          comp: {
            bean: (() => {
              if (!item.configJson) return item.bean || null
              try {
                const config = typeof item.configJson === "string" ? JSON.parse(item.configJson) : item.configJson
                return config.serviceBean || null
              } catch {
                return item.bean || null
              }
            })(),
            method: (() => {
              if (!item.configJson) return item.method || null
              try {
                const config = typeof item.configJson === "string" ? JSON.parse(item.configJson) : item.configJson
                return config.method || null
              } catch {
                return item.method || null
              }
            })(),
            version: item.version,
            path: item.path,
            configJson: item.configJson,
            endpointType: item.endpointType,
          },
          inputs: (() => {
            if (item.configJson) {
              try {
                const config = typeof item.configJson === "string" ? JSON.parse(item.configJson) : item.configJson
                if (config.params && Array.isArray(config.params) && config.params.length > 0) {
                  return mapInputs(
                    config.params.map((param: any) => ({
                      name: param.name || "",
                      valueType: inferValueType(param.type || ""),
                      typeName: param.type || "",
                      description: param.description || "",
                      schema: param.schema || null,  // 透传 IDE 插件上报的完整入参 JSON Schema
                    }))
                  )
                }
              } catch {
                // 忽略解析错误
              }
            }
            return mapInputs(item.inputs)
          })(),
          output: (() => {
            if (item.configJson) {
              try {
                const config = typeof item.configJson === "string" ? JSON.parse(item.configJson) : item.configJson
                if (config.returnType && config.returnType !== "void") {
                  return mapOutput({
                    valueType: inferValueType(config.returnType),
                    typeName: config.returnType,
                    description: config.description || "",
                    fields: buildOutputFieldsFromSchema(config.returnSchema),
                  })
                }
              } catch {
                // 忽略解析错误
              }
            }
            return mapOutput(item.output)
          })(),
        }
      }
      if (item.tab === "models") {
        return {
          ...base,
          model: { fqcn: item.fqcn, version: item.version },
          inputs: mapInputs(),
          output: mapOutput(null),
        }
      }
      return base
    })()

    // Convert absolute position to relative position when dropped inside a parent node.
    let finalPosition = position
    if (parentId) {
      const parentNode = nodes.value.find((n: any) => n.id === parentId)
      if (parentNode) {
        finalPosition = {
          x: position.x - parentNode.position.x,
          y: position.y - parentNode.position.y,
        }
      }
    }

    nodes.value = [
      ...nodes.value,
      {
        id,
        position: finalPosition,
        data,
        type: nodeType,
        // If dropped into a service group, attach as child node.
        ...(parentId ? {
          parentNode: parentId,
          extent: "parent" as const,
        } : {}),
        // Service group works as a container node.
        ...(nodeType === "serviceGroup" ? {
          style: {
            width: 400,
            height: 250,
            padding: 0,
          },
          draggable: true,
          expandParent: true,
        } : {}),
      },
    ]
  }

  return {
    nodes,
    edges,
    selected,
    selectedEdge,
    contextMenu,
    nodeTypes,
    canDelete,
    setGraph,
    resetGraph,
    hideContextMenu,
    focusNodeById,
    removeContextMenuNode,
    deleteSelection,
    clearSelection,
    handleConnect,
    onNodeClick,
    onEdgeClick,
    updateNode,
    updateEdge,
    onNodeContextMenu,
    onEdgeContextMenu,
    addNodeFromPalette,
  }
}

