import { computed, onBeforeUnmount, onMounted, ref } from "vue"
import ServiceNode from "../ServiceNode.vue"
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

  function handleConnect(params: any) {
    const sourceNode = nodes.value.find((n: any) => n.id === params.source)
    const targetNode = nodes.value.find((n: any) => n.id === params.target)
    
    // 检查是否跨服务组边界连线
    const sourceParent = sourceNode?.parentNode
    const targetParent = targetNode?.parentNode
    
    // 如果源节点和目标节点的父节点不一致,阻止连线
    if (sourceParent !== targetParent) {
      if (sourceParent || targetParent) {
        window.alert("服务组内的节点只能与同组内的节点连线")
        return
      }
    }
    
    if (targetNode?.type === "transformer") {
      const existingIncomingEdges = edges.value.filter((e: any) => e.target === params.target && e.id !== params.edge?.id)
      if (existingIncomingEdges.length >= 1) {
        window.alert("脚本节点只能有一条输入连线，请先删除现有连线")
        return
      }
    }
    if (params.source === params.target) {
      console.warn("不允许连接到自身")
      return
    }
    const edge = {
      ...params,
      type: "smoothstep",
      id: String(Date.now()),
      data: { label: "" },
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
    if (confirm("确认删除这条连线？")) {
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
    const label =
      nodeType === "branch"
        ? item.title || item.displayName || "Branch"
        : item.displayName || item.title || item.bean || item.fqcn || item.name || "节点"

    const mapInputs = (list: any[] = []) =>
      list.map((input: any) => ({
        name: input.name,
        description: input.description,
        valueType: (input.valueType || "STRING").toUpperCase(),
        typeName: input.typeName || "",
        transformer: input.transformer || "",
        resolver:
          input.resolver || {
            type: input.sourceType || "request",
            path: input.path || "",
            cast: input.cast || "STRING",
            default: input.default || "",
          },
      }))

    const mapOutput = (output: any) => ({
      description: output?.description || "",
      valueType: output?.valueType || "OBJECT",
      typeName: output?.typeName || "",
      fields: output?.fields || [],
      contextKey: output?.contextKey || generateContextKey(),
    })

    const data = (() => {
      if (nodeType === "branch") {
        return { label, branch: true, expression: "", fallbackNote: "" }
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

    // 如果有父节点，需要将绝对坐标转换为相对坐标
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
        // 如果有parentId,设置父节点关系并限制在父节点内
        ...(parentId ? { 
          parentNode: parentId,
          extent: 'parent' as const,  // 限制子节点只能在父节点范围内移动
        } : {}),
        // 服务组需要显式设置容器属性
        ...(nodeType === "serviceGroup" ? {
          style: {
            width: 400,
            height: 250,
            padding: 0,
          },
          draggable: true,
          // 允许其他节点作为子节点
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

