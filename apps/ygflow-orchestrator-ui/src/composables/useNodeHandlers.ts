import type { Ref } from "vue"

/**
 * 节点处理相关的 composable
 * 提供节点和边的增删改查、选择、连接等功能
 */
export function useNodeHandlers(
  nodes: Ref<any[]>,
  edges: Ref<any[]>,
  selected: Ref<any | null>,
  selectedEdge: Ref<any | null>,
  contextMenu: Ref<{ visible: boolean; x: number; y: number; nodeId: string | null; label: string }>
) {
  /**
   * 隐藏上下文菜单
   */
  function hideContextMenu() {
    contextMenu.value = {
      visible: false,
      x: 0,
      y: 0,
      nodeId: null,
      label: "",
    }
  }

  /**
   * 根据ID删除节点
   */
  function removeNodeById(id: string) {
    nodes.value = nodes.value.filter((item) => item.id !== id)
    edges.value = edges.value.filter((edge) => edge.source !== id && edge.target !== id)

    if (selected.value?.id === id) selected.value = null

    if (selectedEdge.value && (selectedEdge.value.source === id || selectedEdge.value.target === id)) {
      selectedEdge.value = null
    }

    hideContextMenu()
  }

  /**
   * 根据ID删除边
   */
  function removeEdgeById(id: string) {
    edges.value = edges.value.filter((edge) => edge.id !== id)

    if (selectedEdge.value?.id === id) selectedEdge.value = null
  }

  /**
   * 删除选中的节点或边
   */
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

  /**
   * 清除选择
   */
  function clearSelection() {
    selected.value = null
    selectedEdge.value = null
    hideContextMenu()
  }

  /**
   * 处理全局点击事件
   */
  function handleGlobalClick() {
    if (contextMenu.value.visible) hideContextMenu()
  }

  /**
   * 处理节点点击事件
   */
  function onNodeClick({ node }: any) {
    selected.value = node
    selectedEdge.value = null
    hideContextMenu()
  }

  /**
   * 处理边点击事件
   */
  function onEdgeClick({ edge }: any) {
    selectedEdge.value = edge
    selected.value = null
    hideContextMenu()
  }

  /**
   * 处理节点右键菜单
   */
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

  /**
   * 处理边右键菜单
   */
  function onEdgeContextMenu({ edge, event }: any) {
    event?.preventDefault()
    hideContextMenu()

    if (confirm("确认删除这条连线？")) {
      removeEdgeById(edge.id)
    }
  }

  /**
   * 处理连接创建
   */
  function handleConnect(params: any) {
    if (params.source === params.target) {
      console.warn("")
      return
    }

    const defaultGroup = {
      id: `${Date.now()}-${Math.random().toString(16).slice(2, 6)}`,
      operator: "AND",
      conditions: [""],
    }

    const edge = {
      ...params,
      type: "smoothstep",
      id: String(Date.now()),
      data: { label: "", expression: "", expressionGroups: [defaultGroup], groupJoiner: "OR" },
      label: "",
    }

    edges.value = [...edges.value, edge]
    selectedEdge.value = edge
    selected.value = null
  }

  return {
    hideContextMenu,
    removeNodeById,
    removeEdgeById,
    deleteSelection,
    clearSelection,
    handleGlobalClick,
    onNodeClick,
    onEdgeClick,
    onNodeContextMenu,
    onEdgeContextMenu,
    handleConnect,
  }
}




