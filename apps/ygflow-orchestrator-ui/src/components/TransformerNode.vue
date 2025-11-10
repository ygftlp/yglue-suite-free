<script setup lang="ts">
import { Handle, Position } from '@vue-flow/core'

interface Props {
  data?: {
    label?: string
    inputs?: any[]
    output?: any
    mappingConfig?: any
    script?: string
  }
}

const props = defineProps<Props>()
</script>

<template>
  <div class="transformer-node">
    <Handle id="in-top" type="target" :position="Position.Top" />
    <Handle id="out-bottom" type="source" :position="Position.Bottom" />

    <div class="node-icon">🔄</div>
    <div class="node-label">{{ props.data?.label || '转换器' }}</div>
    <div class="node-meta">
      <div v-if="props.data?.mappingConfig" class="mapping-info">
        字段映射: {{ (props.data.mappingConfig.fieldMappings || []).length }}个
      </div>
      <div v-if="props.data?.script" class="script-info">
        Groovy脚本: 已配置
      </div>
    </div>
  </div>
</template>

<style scoped>
/**
 * 通用转换器节点样式
 * 与其他节点保持一致的视觉风格，包括边框、阴影和定位
 */
.transformer-node {
  position: relative;
  min-width: 160px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--border, #d0d7de);
  background: linear-gradient(135deg, #8b5cf6, #7c3aed);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: white;
}

.node-icon {
  font-size: 20px;
  margin-bottom: 4px;
}

.node-label {
  font-weight: 600;
  font-size: 13px;
}

.node-meta {
  font-size: 11px;
  opacity: 0.9;
  margin-top: 4px;
}

.mapping-info, .script-info {
  margin-top: 2px;
}

.transformer-node :deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  border: 2px solid #7c3aed;
  background: #fff;
}
</style>