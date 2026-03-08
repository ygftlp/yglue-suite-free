<script setup lang="ts">
import { computed } from "vue"
import ParamPlanBuilder from "./ParamPlanBuilder.vue"

const props = defineProps<{
  label?: string
  comp?: any
  inputs?: any[]
  output?: any
  paramPlans?: any
  projectKey?: string
  endpointId?: number
}>()

const emit = defineEmits<{
  (event: "update:label", value: string): void
  (event: "update:comp", value: any): void
  (event: "update:output", value: any): void
  (event: "update:paramPlans", value: any): void
}>()

function formatInputType(input: any) {
  const type = String(input?.valueType || "STRING").toUpperCase()
  if (type === "OBJECT" || type === "ARRAY") return input?.typeName || type
  return type
}

function getInputName(input: any, index: number): string {
  const name = String(input?.name || "").trim()
  return name || `arg${index + 1}`
}

function formatOutputType(output: any) {
  const type = String(output?.valueType || "OBJECT").toUpperCase()
  if (output?.typeName) return output.typeName
  return type
}

function isObjectOutput(output: any) {
  return String(output?.valueType || "").toUpperCase() === "OBJECT"
}

function updateOutputField(partial: Record<string, any>) {
  emit("update:output", { ...(props.output || {}), ...partial })
}

function getServiceName(comp: any): string | null {
  if (!comp) return null
  const endpointType = String(comp.endpointType || "")

  if (endpointType === "SERVICE") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        if (config.bean && typeof config.bean === "string") return config.bean
        if (config.name && typeof config.name === "string") return config.name
      }
    } catch {
      return comp.bean || null
    }
  }

  if (endpointType === "FLOW_OPERATION") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        if (config.serviceBean && typeof config.serviceBean === "string") return config.serviceBean
      }
    } catch {
      return comp.bean || null
    }
  }

  return comp.bean || null
}

const normalizedInputs = computed(() =>
  (Array.isArray(props.inputs) ? props.inputs : []).map((input, index) => ({
    name: getInputName(input, index),
    type: formatInputType(input),
  })),
)

const inputSummaryText = computed(() => {
  const size = normalizedInputs.value.length
  if (size === 0) return "未识别到入参"
  return `已识别 ${size} 个入参（用于 V2 目标参数选择）`
})
</script>

<template>
  <div class="service-node-config">
    <section class="section">
      <div class="muted section-title">显示名称</div>
      <input
        class="input"
        style="width: 100%; box-sizing: border-box;"
        :class="{ locked: Boolean(comp) }"
        :readonly="Boolean(comp)"
        :value="label || ''"
        @input="!comp && emit('update:label', ($event.target as HTMLInputElement).value)"
      />
    </section>

    <section v-if="comp" class="section">
      <div class="muted section-title">组件绑定</div>
      <div class="card binding-card">
        <div>
          <div class="muted tiny">Bean 名称</div>
          <div class="binding-value">{{ getServiceName(comp) || comp.bean || "-" }}</div>
        </div>
        <div>
          <div class="muted tiny">Method 名称</div>
          <div class="binding-value">{{ comp.method || "-" }}</div>
        </div>
        <div v-if="comp.version">
          <div class="muted tiny">版本</div>
          <div class="binding-value">{{ comp.version }}</div>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="row section-head">
        <div class="muted section-title">服务入参签名（摘要）</div>
        <div class="muted tiny">{{ inputSummaryText }}</div>
      </div>
      <div v-if="normalizedInputs.length > 0" class="signature-list">
        <div v-for="(item, index) in normalizedInputs" :key="`sig-row-${index}`" class="signature-row">
          <span class="signature-name" :title="item.name">{{ item.name }}</span>
          <span class="signature-type" :title="item.type">{{ item.type }}</span>
        </div>
        <div class="muted tiny signature-hint">详细参数值请在下方“参数构造管线（V2）”中配置。</div>
      </div>
      <div v-else class="muted empty-tip">当前节点无入参定义。</div>
    </section>

    <section class="section">
      <div class="muted section-title">输出结果</div>
      <div v-if="!output" class="muted empty-tip">暂无返回值。</div>
      <div v-else class="card output-card">
        <div v-if="output.description" class="input-hint">说明：{{ output.description }}</div>
        <div class="type-pill">类型：{{ formatOutputType(output) }}</div>

        <div v-if="isObjectOutput(output)" class="field-table">
          <div class="field-row header">
            <span>字段</span>
            <span>类型</span>
            <span>说明</span>
          </div>
          <div v-for="(field, idx) in output.fields || []" :key="idx" class="field-row">
            <span>{{ field.name }}</span>
            <span>{{ field.type }}</span>
            <span>{{ field.description || "-" }}</span>
          </div>
          <div v-if="(output.fields || []).length === 0" class="muted empty-tip">暂无字段。</div>
        </div>
        <div v-else class="muted tiny">基础类型：{{ formatOutputType(output) }}</div>

        <div class="binding-field">
          <div class="muted tiny">绑定到 ctx 的 key</div>
          <input
            class="input"
            placeholder="默认 retxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            :value="output.contextKey || ''"
            @input="updateOutputField({ contextKey: ($event.target as HTMLInputElement).value })"
          />
        </div>
      </div>
    </section>

    <section class="section split-top">
      <ParamPlanBuilder
        :model-value="paramPlans"
        :input-defs="inputs"
        :project-key="projectKey"
        @update:model-value="emit('update:paramPlans', $event)"
      />
    </section>
  </div>
</template>

<style scoped>
.service-node-config {
  display: flex;
  flex-direction: column;
}

.section {
  margin-top: 12px;
}

.section-title {
  margin-bottom: 6px;
}

.section-head {
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.split-top {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e5e7eb;
}

.row {
  display: flex;
  gap: 8px;
}

.card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
}

.muted {
  color: #64748b;
  font-size: 12px;
}

.muted.tiny {
  font-size: 11px;
}

.input {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.input.locked {
  background: #f3f4f6;
  color: #6b7280;
  cursor: not-allowed;
}

.binding-card,
.output-card {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.binding-value {
  font-size: 13px;
  font-weight: 500;
  color: #1f2937;
}

.empty-tip {
  font-size: 12px;
}

.signature-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.signature-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.6fr);
  gap: 8px;
  align-items: center;
  border: 1px solid #e5e7eb;
  background: #fbfdff;
  border-radius: 8px;
  padding: 6px 8px;
}

.signature-name,
.signature-type {
  font-size: 12px;
  color: #1f2937;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.signature-type {
  color: #475569;
}

.signature-hint {
  margin-top: 2px;
}

.input-hint {
  font-size: 11px;
  color: #94a3b8;
}

.type-pill {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 999px;
  width: fit-content;
  padding: 2px 10px;
}

.binding-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.5fr;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
}

.field-row.header {
  font-weight: 600;
  background: #f8fafc;
  color: #475569;
}

.field-row:not(.header) {
  background: #fefefe;
  border: 1px solid #e5e7eb;
}
</style>
