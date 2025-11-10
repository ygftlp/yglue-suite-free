<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'
import type { ExitNodeData } from '../data/entryExitNodes'

const props = defineProps<{ 
  data?: ExitNodeData 
}>()
</script>

<template>
  <div class="exit-node">
    <Handle id="in" type="target" :position="Position.Top" />
    
    <div class="exit-icon">🏁</div>
    <div class="exit-label">出口节点</div>
    <div class="exit-hint">结果写入 ctx['ret']</div>
  </div>
</template>

<style scoped>
.exit-node {
  min-width: 120px;
  padding: 12px;
  border-radius: 8px;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: white;
  text-align: center;
  box-shadow: 0 4px 12px rgba(245, 158, 11, 0.3);
}

.exit-icon {
  font-size: 20px;
  margin-bottom: 4px;
}

.exit-label {
  font-weight: 600;
  font-size: 13px;
}

.exit-hint {
  font-size: 11px;
  opacity: 0.9;
  margin-top: 4px;
}
</style>