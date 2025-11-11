<script setup lang="ts">
import { computed } from "vue"
import { ConnectionMode, VueFlow, useVueFlow } from "@vue-flow/core"
import { Controls } from "@vue-flow/controls"
import { MiniMap } from "@vue-flow/minimap"
import { Background } from "@vue-flow/background"
import { Maximize2, Minus, Plus, Trash2 } from "lucide-vue-next"
import type { ContextMenuState } from "./useCanvasEditor"

const props = defineProps<{
  nodes: any[]
  edges: any[]
  nodeTypes: Record<string, any>
  contextMenu: ContextMenuState
  canDelete: boolean
}>()

const emit = defineEmits<{
  (event: "update:nodes", value: any[]): void
  (event: "update:edges", value: any[]): void
  (event: "connect", payload: any): void
  (event: "node-click", payload: any): void
  (event: "node-contextmenu", payload: any): void
  (event: "edge-click", payload: any): void
  (event: "edge-contextmenu", payload: any): void
  (event: "drop-node", payload: { item: any; position: { x: number; y: number } }): void
  (event: "hide-context-menu"): void
  (event: "delete-selection"): void
  (event: "remove-context-menu-node"): void
}>()

const nodesModel = computed({
  get: () => props.nodes,
  set: (value: any[]) => emit("update:nodes", value),
})

const edgesModel = computed({
  get: () => props.edges,
  set: (value: any[]) => emit("update:edges", value),
})

const { project, fitView, zoomIn, zoomOut } = useVueFlow()

function handleDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = "copy"
  }
}

function handleDrop(event: DragEvent) {
  event.preventDefault()
  emit("hide-context-menu")
  const raw = event.dataTransfer?.getData("application/json") || event.dataTransfer?.getData("text/plain")
  if (!raw) return
  let item: any
  try {
    item = JSON.parse(raw)
  } catch (error) {
    console.warn("解析拖拽数据失败", error)
    return
  }
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const position = project
    ? project({ x: event.clientX - bounds.left, y: event.clientY - bounds.top })
    : { x: event.clientX - bounds.left, y: event.clientY - bounds.top }
  emit("drop-node", { item, position })
}

function handleZoomIn() {
  zoomIn?.({ duration: 200 })
}

function handleZoomOut() {
  zoomOut?.({ duration: 200 })
}

function handleFocus() {
  fitView?.({ padding: 0.2, duration: 300 })
}
</script>

<template>
  <div class="canvas-surface">
    <VueFlow
      class="flow-root"
      v-model:nodes="nodesModel"
      v-model:edges="edgesModel"
      :node-types="props.nodeTypes"
      :connection-mode="ConnectionMode.Loose"
      @connect="emit('connect', $event)"
      @node-click="emit('node-click', $event)"
      @node-contextmenu="emit('node-contextmenu', $event)"
      @edge-click="emit('edge-click', $event)"
      @edge-contextmenu="emit('edge-contextmenu', $event)"
      @dragover="handleDragOver"
      @drop="handleDrop"
    >
      <Background />
      <MiniMap />
      <Controls />
    </VueFlow>
    <div class="action-rail">
      <button class="rail-btn" type="button" title="放大" @click="handleZoomIn">
        <Plus :size="14" />
      </button>
      <button class="rail-btn" type="button" title="缩小" @click="handleZoomOut">
        <Minus :size="14" />
      </button>
      <button class="rail-btn" type="button" title="聚焦" @click="handleFocus">
        <Maximize2 :size="14" />
      </button>
      <button class="rail-btn danger" type="button" title="删除选中" :disabled="!props.canDelete" @click="emit('delete-selection')">
        <Trash2 :size="14" />
      </button>
    </div>
    <div
      v-if="props.contextMenu.visible"
      class="context-menu"
      :style="{ top: props.contextMenu.y + 'px', left: props.contextMenu.x + 'px' }"
      @click.stop
    >
      <div class="menu-title">{{ props.contextMenu.label }}</div>
      <button class="menu-item" type="button" @click="emit('remove-context-menu-node')">删除节点</button>
    </div>
  </div>
</template>

<style scoped>
.canvas-surface {
  position: absolute;
  inset: 0;
}

.flow-root {
  width: 100%;
  height: 100%;
}

.flow-root :deep(.vue-flow__viewport) {
  transition: transform 0.15s ease;
}

.flow-root :deep(.vue-flow__pane) {
  background-image: radial-gradient(circle, rgba(148, 163, 184, 0.22) 1px, transparent 1px);
  background-size: 24px 24px;
}

.action-rail {
  position: absolute;
  right: 20px;
  top: 130px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  z-index: 25;
}

.rail-btn {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  border: 1px solid rgba(148, 163, 184, 0.4);
  background: rgba(255, 255, 255, 0.92);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
  transition: transform 0.1s ease, box-shadow 0.1s ease;
}

.rail-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.rail-btn:not(:disabled):hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.15);
}

.rail-btn.danger {
  color: #dc2626;
  border-color: rgba(220, 38, 38, 0.35);
}

.context-menu {
  position: fixed;
  z-index: 30;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.1);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.18);
  border-radius: 8px;
  padding: 8px 0;
  width: 160px;
  color: #1f2937;
  display: flex;
  flex-direction: column;
}

.menu-title {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 12px 8px;
  color: #475569;
}

.menu-item {
  background: transparent;
  border: none;
  text-align: left;
  padding: 6px 12px;
  width: 100%;
  cursor: pointer;
  font-size: 12px;
  color: #dc2626;
}

.menu-item:hover {
  background: rgba(220, 38, 38, 0.08);
}
</style>

