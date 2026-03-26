<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from "vue"
import CanvasPalettePanel from "./CanvasPalettePanel.vue"
import CanvasToolbar from "./CanvasToolbar.vue"
import CanvasSurface from "./CanvasSurface.vue"
import CanvasInspectorPanel from "./CanvasInspectorPanel.vue"
import { useCanvasEditor, type CanvasEditorProps } from "./useCanvasEditor"

const FlowSettingsPanel = defineAsyncComponent(() => import("./FlowSettingsPanel.vue"))
const VersionList = defineAsyncComponent(() => import("./VersionList.vue"))

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
  showPublishButton,
  flowPrecheckSummary,
  flowPrecheckBlocking,
  flowPrecheckHighlights,
  flowPrecheckMessage,
  contextMenu,
  nodeTypes,
  logPreview,
  togglePalette,
  toggleInspector,
  deleteSelection,
  clearSelection,
  focusNodeById,
  saveDraft,
  publishFlow,
  openFlowSettings,
  closeFlowSettings,
  versionListVisible,
  openVersionList,
  closeVersionList,
  loadVersion,
  loadVersionAndSave,
  currentVersionNo,
  publishedVersionNo,
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
const surfaceFocusNodeId = ref<string | null>(null)
const surfaceFocusNonce = ref(0)

watch(
  () => props.issueFocusRequest,
  (request) => {
    const path = String(request?.path || "").trim()
    if (!path) return
    const nodeIndex = parseNodeIndexFromIssuePath(path)
    if (nodeIndex == null) {
      window.alert(`Cannot locate non-node issue path: ${path}`)
      return
    }
    const targetNode = nodes.value[nodeIndex]
    if (!targetNode?.id) {
      window.alert(`Cannot find node for index=${nodeIndex}. Refresh signature check and try again.`)
      return
    }
    const focused = focusNodeById(String(targetNode.id))
    if (!focused) {
      window.alert(`Node focus failed: ${targetNode.id}`)
      return
    }
    surfaceFocusNodeId.value = String(targetNode.id)
    surfaceFocusNonce.value = Number(request?.nonce || Date.now())
  },
  { deep: true },
)

function parseNodeIndexFromIssuePath(path: string): number | null {
  const match = path.match(/^\$\.nodes\[(\d+)\]/)
  if (!match) return null
  const index = Number.parseInt(match[1], 10)
  return Number.isFinite(index) && index >= 0 ? index : null
}

function handleDropNode(payload: { item: any; position: { x: number; y: number }; parentId?: string }) {
  addNodeFromPalette(payload.item, payload.position, payload.parentId)
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
        :show-publish-button="showPublishButton"
        :precheck-errors="flowPrecheckSummary.errors"
        :precheck-warnings="flowPrecheckSummary.warnings"
        :precheck-blocking="flowPrecheckBlocking"
        :precheck-highlights="flowPrecheckHighlights"
        :precheck-message="flowPrecheckMessage"
        @open-flow-settings="openFlowSettings"
        @clear-selection="clearSelection"
        @delete-selection="deleteSelection"
        @save-draft="saveDraft"
        @publish-flow="publishFlow"
        @open-version-list="openVersionList"
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
        :focus-node-id="surfaceFocusNodeId"
        :focus-nonce="surfaceFocusNonce"
      />
    </div>
    <CanvasInspectorPanel
      :collapsed="inspectorCollapsed"
      :width="inspectorWidth"
      :endpoint-schema="props.endpointSchema"
      :entrypoint-path="props.entrypointHint?.path ?? null"
      :flow-models="props.flowModels ?? []"
      :flow-resolvers="props.flowResolvers ?? []"
      :selected-node="selected"
      :selected-edge="selectedEdge"
      :nodes="nodes"
      :edges="edges"
      :project-key="props.projectKey"
      :endpoint-id="props.endpointId"
      @toggle="toggleInspector"
      @update-node="updateNode"
      @update-edge="updateEdge"
    />
  </div>
  <Suspense>
    <FlowSettingsPanel
      v-if="flowSettingsVisible"
      :visible="flowSettingsVisible"
      v-model="flowSettings"
      :request-schema-fields="props.endpointSchema?.requestSchema ?? undefined"
      :response-schema="props.endpointSchema?.responseSchema ?? undefined"
      :inbound-interceptor-catalog="props.inboundInterceptorCatalog ?? undefined"
      @close="closeFlowSettings"
    />
    <template #fallback>
      <div v-if="flowSettingsVisible" class="async-overlay-loading">正在加载流程设置...</div>
    </template>
  </Suspense>
  <Suspense>
    <VersionList
      v-if="versionListVisible && flowSettings.code"
      :visible="versionListVisible"
      :project-key="props.projectKey"
      :flow-code="flowSettings.code"
      :current-version-no="currentVersionNo"
      :published-version-no="publishedVersionNo"
      @close="closeVersionList"
      @load-version="loadVersion"
      @load-version-and-save="loadVersionAndSave"
    />
    <template #fallback>
      <div v-if="versionListVisible" class="async-overlay-loading">正在加载版本列表...</div>
    </template>
  </Suspense>
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
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
}

.async-overlay-loading {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.2);
  color: #0f172a;
  font-size: 13px;
  backdrop-filter: blur(2px);
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

