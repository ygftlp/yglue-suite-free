<script setup lang="ts">
import { computed } from "vue"
import FlowSettingsPanel from "./FlowSettingsPanel.vue"
import CanvasPalettePanel from "./CanvasPalettePanel.vue"
import CanvasToolbar from "./CanvasToolbar.vue"
import CanvasSurface from "./CanvasSurface.vue"
import CanvasInspectorPanel from "./CanvasInspectorPanel.vue"
import { useCanvasEditor, type CanvasEditorProps } from "./useCanvasEditor"

const props = defineProps<CanvasEditorProps>()

const {
  nodes,
  edges,
  selected,
  selectedEdge,
  flowSettings,
  flowSettingsVisible,
  paletteCollapsed,
  paletteWidth,
  inspectorCollapsed,
  inspectorWidth,
  statusMessage,
  activeError,
  canDelete,
  canSave,
  canPublish,
  contextMenu,
  nodeTypes,
  logPreview,
  togglePalette,
  toggleInspector,
  deleteSelection,
  clearSelection,
  saveDraft,
  publishFlow,
  openFlowSettings,
  closeFlowSettings,
  handleConnect,
  onNodeClick,
  onNodeContextMenu,
  onEdgeClick,
  onEdgeContextMenu,
  updateNode,
  updateEdge,
  removeContextMenuNode,
  addNodeFromPalette,
  hideContextMenu,
} = useCanvasEditor(props)

const gridColumns = computed(() => `${paletteWidth.value}px 1fr ${inspectorWidth.value}px`)

function handleDropNode(payload: { item: any; position: { x: number; y: number } }) {
  addNodeFromPalette(payload.item, payload.position)
}
</script>

<template>
  <div class="canvas-wrapper" :style="{ gridTemplateColumns: gridColumns }">
    <CanvasPalettePanel
      :collapsed="paletteCollapsed"
      :project-key="props.projectKey"
      @toggle="togglePalette"
    />
    <div class="canvas-area">
      <CanvasToolbar
        :status-message="statusMessage"
        :active-error="activeError"
        :log-preview="logPreview"
        :can-delete="canDelete"
        :can-save="canSave"
        :can-publish="canPublish"
        @open-flow-settings="openFlowSettings"
        @clear-selection="clearSelection"
        @delete-selection="deleteSelection"
        @save-draft="saveDraft"
        @publish-flow="publishFlow"
      />
      <CanvasSurface
        v-model:nodes="nodes"
        v-model:edges="edges"
        :node-types="nodeTypes"
        :context-menu="contextMenu"
        :can-delete="canDelete"
        @connect="handleConnect"
        @node-click="onNodeClick"
        @node-contextmenu="onNodeContextMenu"
        @edge-click="onEdgeClick"
        @edge-contextmenu="onEdgeContextMenu"
        @drop-node="handleDropNode"
        @hide-context-menu="hideContextMenu"
        @delete-selection="deleteSelection"
        @remove-context-menu-node="removeContextMenuNode"
      />
    </div>
    <CanvasInspectorPanel
      :collapsed="inspectorCollapsed"
      :width="inspectorWidth"
      :endpoint-schema="props.endpointSchema"
      :entrypoint-path="props.entrypointHint?.path ?? null"
      :flow-models="props.flowModels ?? []"
      :selected-node="selected"
      :selected-edge="selectedEdge"
      :nodes="nodes"
      :edges="edges"
      @toggle="toggleInspector"
      @update-node="updateNode"
      @update-edge="updateEdge"
    />
  </div>
  <FlowSettingsPanel
    :visible="flowSettingsVisible"
    v-model="flowSettings"
    :request-schema-fields="props.endpointSchema?.requestSchema ?? undefined"
    :response-schema="props.endpointSchema?.responseSchema ?? undefined"
    @close="closeFlowSettings"
  />
</template>

<style scoped>
.canvas-wrapper {
  display: grid;
  grid-template-columns: 320px 1fr 360px;
  height: 100%;
}

.canvas-area {
  position: relative;
  background: #f9fafb;
}

:global(.rule-preview-modal) {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 50;
}

:global(.rule-preview-content) {
  width: min(720px, 90vw);
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.25);
  display: flex;
  flex-direction: column;
}

:global(.rule-preview-header) {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.3);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:global(.rule-preview-header .rule-preview-close) {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
}

:global(.rule-preview-body) {
  padding: 16px;
  max-height: 60vh;
  overflow: auto;
}

:global(.rule-preview-body pre) {
  white-space: pre-wrap;
  font-size: 12px;
  background: #f8fafc;
  border-radius: 8px;
  padding: 12px;
}

:global(.rule-preview-footer) {
  padding: 12px 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.3);
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

:global(.rule-preview-footer .btn.download) {
  background: #2563eb;
  border: none;
  color: #fff;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
}

:global(.rule-preview-footer .btn.close) {
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: transparent;
  color: #0f172a;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
}
</style>

