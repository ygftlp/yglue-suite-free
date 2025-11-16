<script setup lang="ts">
import { ChevronLeft, ChevronRight } from "lucide-vue-next"
import Inspector from "./Inspector.vue"
import type { EndpointSchemaHint } from "./useCanvasEditor"
import type { FlowModel, FlowResolver } from "../api/client"

const props = defineProps<{
  collapsed: boolean
  width: number
  endpointSchema?: EndpointSchemaHint
  entrypointPath?: string | null
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
  selectedNode: any | null
  selectedEdge: any | null
  nodes: any[]
  edges: any[]
}>()

const emit = defineEmits<{
  (event: "toggle"): void
  (event: "update-node", node: any): void
  (event: "update-edge", edge: any): void
}>()
</script>

<template>
  <div class="inspector-sider" :class="{ collapsed: props.collapsed }" :style="{ width: props.width + 'px' }">
    <div class="inspector-toggle">
      <button class="drawer-btn inspector-btn" type="button" @click="emit('toggle')">
        <ChevronLeft v-if="props.collapsed" :size="14" />
        <ChevronRight v-else :size="14" />
      </button>
      <span v-if="props.collapsed" class="collapsed-text">设置</span>
      <span v-else class="inspector-title">节点配置</span>
    </div>
    <div v-if="!props.collapsed" class="inspector-body">
      <div class="inspector-scroll">
        <Inspector
          :endpoint-schema="props.endpointSchema"
          :entrypoint-path="props.entrypointPath ?? undefined"
          :flow-models="props.flowModels ?? []"
          :flow-resolvers="props.flowResolvers ?? []"
          :selected-node="props.selectedNode"
          :selected-edge="props.selectedEdge"
          :nodes="props.nodes"
          :edges="props.edges"
          @update-node="emit('update-node', $event)"
          @update-edge="emit('update-edge', $event)"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.inspector-sider {
  border-left: 1px solid var(--border);
  background: #fff;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  transition: width 0.2s ease;
}

.inspector-sider.collapsed {
  align-items: center;
  justify-content: center;
}

.inspector-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border);
}

.inspector-sider.collapsed .inspector-toggle {
  flex-direction: column;
  padding: 8px 4px;
}

.drawer-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #475569;
}

.drawer-btn:hover {
  color: #2563eb;
}

.inspector-title {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
}

.collapsed-text {
  font-size: 11px;
  color: #94a3b8;
}

.inspector-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.inspector-scroll {
  flex: 1;
  padding: 12px;
  overflow-y: auto;
}
</style>




