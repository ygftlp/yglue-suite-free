import { useFlowState } from "./composables/useFlowState"
import { useFlowIO } from "./composables/useFlowIO"
import { useLayoutState } from "./composables/useLayoutState"
import type { CanvasEditorProps } from "./composables/canvasTypes"

export type { CanvasEditorProps, EndpointSchemaHint, ContextMenuState } from "./composables/canvasTypes"

export function useCanvasEditor(props: CanvasEditorProps) {
  const flowState = useFlowState()
  const flowIO = useFlowIO(props, {
    nodes: flowState.nodes,
    edges: flowState.edges,
    setGraph: flowState.setGraph,
    resetGraph: flowState.resetGraph,
  })

  const layout = useLayoutState({
    flowSettings: flowIO.flowSettings,
    loadError: flowIO.loadError,
    loadingFlow: flowIO.loadingFlow,
    saving: flowIO.saving,
    publishing: flowIO.publishing,
    lastSavedAt: flowIO.lastSavedAt,
    currentVersionNo: flowIO.currentVersionNo,
    publishedVersionNo: flowIO.publishedVersionNo,
    publishError: flowIO.publishError,
    saveError: flowIO.saveError,
  })

  return {
    nodes: flowState.nodes,
    edges: flowState.edges,
    selected: flowState.selected,
    selectedEdge: flowState.selectedEdge,
    contextMenu: flowState.contextMenu,
    nodeTypes: flowState.nodeTypes,
    canDelete: flowState.canDelete,
    handleConnect: flowState.handleConnect,
    onNodeClick: flowState.onNodeClick,
    onEdgeClick: flowState.onEdgeClick,
    updateNode: flowState.updateNode,
    updateEdge: flowState.updateEdge,
    onNodeContextMenu: flowState.onNodeContextMenu,
    onEdgeContextMenu: flowState.onEdgeContextMenu,
    deleteSelection: flowState.deleteSelection,
    clearSelection: flowState.clearSelection,
    hideContextMenu: flowState.hideContextMenu,
    removeContextMenuNode: flowState.removeContextMenuNode,
    addNodeFromPalette: flowState.addNodeFromPalette,

    flowSettings: flowIO.flowSettings,
    flowSettingsVisible: flowIO.flowSettingsVisible,
    loadingFlow: flowIO.loadingFlow,
    loadError: flowIO.loadError,
    saving: flowIO.saving,
    publishing: flowIO.publishing,
    saveError: flowIO.saveError,
    publishError: flowIO.publishError,
    lastSavedAt: flowIO.lastSavedAt,
    currentVersionNo: flowIO.currentVersionNo,
    publishedVersionNo: flowIO.publishedVersionNo,
    isLatestPublished: flowIO.isLatestPublished,
    canPublish: flowIO.canPublish,
    showPublishButton: flowIO.showPublishButton,
    canSave: flowIO.canSave,
    loadFlowDefinition: flowIO.loadFlowDefinition,
    saveDraft: flowIO.saveDraft,
    publishFlow: flowIO.publishFlow,
    openFlowSettings: flowIO.openFlowSettings,
    closeFlowSettings: flowIO.closeFlowSettings,
    versionListVisible: flowIO.versionListVisible,
    openVersionList: flowIO.openVersionList,
    closeVersionList: flowIO.closeVersionList,
    loadVersion: flowIO.loadVersion,
    loadVersionAndSave: flowIO.loadVersionAndSave,
    currentVersionNo: flowIO.currentVersionNo,
    publishedVersionNo: flowIO.publishedVersionNo,

    paletteCollapsed: layout.paletteCollapsed,
    inspectorCollapsed: layout.inspectorCollapsed,
    paletteWidth: layout.paletteWidth,
    inspectorWidth: layout.inspectorWidth,
    statusMessage: layout.statusMessage,
    activeError: layout.activeError,
    logPreview: layout.logPreview,
    togglePalette: layout.togglePalette,
    toggleInspector: layout.toggleInspector,
  }
}

