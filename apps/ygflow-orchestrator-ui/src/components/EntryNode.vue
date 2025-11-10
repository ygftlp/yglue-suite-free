<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { EntryNodeData } from '../data/entryExitNodes'

const props = defineProps<{ 
  data?: EntryNodeData 
}>()
</script>

<template>
  <div class="entry-node">
    <Handle id="out" type="source" :position="Position.Bottom" />
    
    <div class="entry-icon">🚪</div>
    <div class="entry-label">入口节点</div>
    <div v-if="data?.method" class="entry-meta">
      {{ data.method }} {{ data.path }}
    </div>
  </div>
</template>

<style scoped>
.entry-node {
  min-width: 120px;
  padding: 12px;
  border-radius: 8px;
  background: linear-gradient(135deg, #10b981, #059669);
  color: white;
  text-align: center;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.entry-icon {
  font-size: 20px;
  margin-bottom: 4px;
}

.entry-label {
  font-weight: 600;
  font-size: 13px;
}

.entry-meta {
  font-size: 11px;
  opacity: 0.9;
  margin-top: 4px;
}
</style>