<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ExitNodeData, ResponseMappingConfig } from '../data/entryExitNodes'

const props = defineProps<{
  data: ExitNodeData
}>()

const emit = defineEmits<{
  update: [data: ExitNodeData]
}>()

const mappingTypes = [
  { value: 'field', label: '字段映射' },
  { value: 'constant', label: '常量赋值' },
  { value: 'expression', label: '表达式' },
  { value: 'groovy', label: 'Groovy脚本' }
]

function updateMapping(config: Partial<ResponseMappingConfig>) {
  const newData = {
    ...props.data,
    responseMapping: { ...props.data.responseMapping, ...config }
  }
  emit('update', newData)
}

function addFieldMapping() {
  const mappings = [...(props.data.responseMapping.fieldMappings || [])]
  mappings.push({ source: '', target: '', transform: '' })
  updateMapping({ fieldMappings: mappings })
}

function removeFieldMapping(index: number) {
  const mappings = [...(props.data.responseMapping.fieldMappings || [])]
  mappings.splice(index, 1)
  updateMapping({ fieldMappings: mappings })
}
</script>

<template>
  <div class="exit-config">
    <h3>出口节点配置</h3>
    
    <div class="form-group">
      <label>返回值映射方式</label>
      <select 
        :value="data.responseMapping.type" 
        @change="updateMapping({ type: ($event.target as HTMLSelectElement).value })"
      >
        <option v-for="type in mappingTypes" :key="type.value" :value="type.value">
          {{ type.label }}
        </option>
      </select>
    </div>

    <div class="form-group">
      <label>映射值/表达式</label>
      <textarea
        :value="data.responseMapping.value"
        @input="updateMapping({ value: ($event.target as HTMLTextAreaElement).value })"
        placeholder="输入常量值、表达式或Groovy脚本"
        rows="3"
      />
    </div>

    <div v-if="data.responseMapping.type === 'field'" class="field-mappings">
      <div class="mapping-header">
        <span>字段映射</span>
        <button @click="addFieldMapping">添加映射</button>
      </div>
      
      <div v-for="(mapping, index) in data.responseMapping.fieldMappings" :key="index" class="mapping-row">
        <input
          :value="mapping.source"
          @input="mapping.source = ($event.target as HTMLInputElement).value; updateMapping({})"
          placeholder="源字段"
        />
        <span>→</span>
        <input
          :value="mapping.target"
          @input="mapping.target = ($event.target as HTMLInputElement).value; updateMapping({})"
          placeholder="目标字段"
        />
        <input
          :value="mapping.transform"
          @input="mapping.transform = ($event.target as HTMLInputElement).value; updateMapping({})"
          placeholder="转换表达式"
        />
        <button @click="removeFieldMapping(index)">删除</button>
      </div>
    </div>

    <div class="hint">
      💡 仅出口节点会固定写入 <code>ctx['ret']</code>；普通服务节点应使用自定义输出变量名（ctx key）
    </div>
  </div>
</template>

<style scoped>
.exit-config {
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.exit-config h3 {
  margin: 0 0 16px 0;
  font-size: 16px;
  color: #1e293b;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}

.form-group select,
.form-group textarea,
.form-group input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
}

.form-group textarea {
  resize: vertical;
  min-height: 80px;
}

.field-mappings {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  background: white;
}

.mapping-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.mapping-header span {
  font-weight: 500;
  color: #374151;
}

.mapping-header button {
  padding: 4px 8px;
  background: #3b82f6;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.mapping-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.mapping-row input {
  flex: 1;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 12px;
}

.mapping-row span {
  color: #6b7280;
  font-size: 14px;
}

.mapping-row button {
  padding: 4px 8px;
  background: #ef4444;
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.hint {
  margin-top: 16px;
  padding: 12px;
  background: #dbeafe;
  border: 1px solid #93c5fd;
  border-radius: 6px;
  font-size: 13px;
  color: #1e40af;
}

.hint code {
  background: #eff6ff;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: #1e40af;
}
</style>
