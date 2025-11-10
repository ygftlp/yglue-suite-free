<script setup lang="ts">
import { computed } from "vue"
import { Handle, Position } from "@vue-flow/core"

const props = defineProps<{ data?: { transaction?: "begin" | "end"; label?: string } }>()
const isBegin = computed(() => (props.data?.transaction || "begin") === "begin")
const label = computed(() => props.data?.label || (isBegin.value ? "事务开始" : "事务结束"))
</script>

<template>
  <div class="txn-node" :class="{ end: !isBegin }">
    <Handle id="in-left" type="target" :position="Position.Left" />
    <Handle id="in-top" type="target" :position="Position.Top" />
    <Handle id="out-right" type="source" :position="Position.Right" />
    <Handle id="out-bottom" type="source" :position="Position.Bottom" />
    <div class="title">{{ label }}</div>
    <div class="meta">{{ isBegin ? "开启事务上下文" : "提交 / 回滚事务" }}</div>
  </div>
</template>

<style scoped>
.txn-node {
  min-width: 160px;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px dashed rgba(16, 185, 129, 0.7);
  background: rgba(16, 185, 129, 0.08);
  color: #065f46;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  text-align: center;
}

.txn-node.end {
  border-color: rgba(59, 130, 246, 0.7);
  background: rgba(59, 130, 246, 0.08);
  color: #1d4ed8;
}

.title {
  font-weight: 600;
  font-size: 13px;
}

.meta {
  font-size: 11px;
}

.txn-node :deep(.vue-flow__handle) {
  width: 10px;
  height: 10px;
  border: 2px solid currentColor;
  background: #fff;
}
</style>
