<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

const props = defineProps<{ data?: { label?: string; comp?: any; model?: any } }>()
</script>

<template>
  <div class="task-node">
    <Handle id="in-left" type="target" :position="Position.Left" />
    <Handle id="out-right" type="source" :position="Position.Right" />

    <div class="task-label">{{ props.data?.label || '节点' }}</div>
    <div v-if="props.data?.comp" class="task-meta">
      {{ props.data.comp.bean }}.{{ props.data.comp.method }}
    </div>
    <div v-if="props.data?.model" class="task-meta">
      {{ props.data.model.fqcn }}
    </div>
  </div>
</template>

<style scoped>
.task-node {
  min-width: 150px;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid var(--border, #d0d7de);
  background: #ffffff;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #1f2937;
}

.task-label {
  font-weight: 600;
  font-size: 13px;
}

.task-meta {
  color: #64748b;
  line-height: 1.4;
}

.task-node :deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  border: 2px solid #2563eb;
  background: #fff;
}
</style>
