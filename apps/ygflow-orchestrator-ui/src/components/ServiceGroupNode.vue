<script setup lang="ts">
import { computed, ref } from "vue"
import { Handle, Position, useVueFlow } from "@vue-flow/core"
import { NodeResizer } from "@vue-flow/node-resizer"
import '@vue-flow/node-resizer/dist/style.css'
import { Package } from "lucide-vue-next"

const props = defineProps<{ 
  id: string
  data?: { 
    label?: string
    enableTransaction?: boolean
    transactionManager?: string
  } 
}>()

const { getNodes } = useVueFlow()

// 计算组内的子节点数量
const childCount = computed(() => {
  const nodes = getNodes.value
  return nodes.filter(n => n.parentNode === props.id).length
})

const label = computed(() => props.data?.label || "服务组")
const hasTransaction = computed(() => props.data?.enableTransaction ?? false)
const transactionMgr = computed(() => props.data?.transactionManager || "默认事务")
</script>

<template>
  <div class="task-group-node" :class="{ 'with-transaction': hasTransaction }">
    <!-- 调整大小的句柄 -->
    <NodeResizer 
      :min-width="320" 
      :min-height="200"
      color="#6366f1"
      :handle-style="{ width: '12px', height: '12px', pointerEvents: 'auto' }"
      style="pointer-events: auto;"
    />
    
    <!-- 服务组的连接点,用于与外部节点连线 -->
    <Handle id="in-left" type="target" :position="Position.Left" />
    <Handle id="in-top" type="target" :position="Position.Top" />
    <Handle id="out-right" type="source" :position="Position.Right" />
    <Handle id="out-bottom" type="source" :position="Position.Bottom" />
    
    <div class="group-header">
      <div class="header-left">
        <Package :size="16" class="group-icon" />
        <div class="group-title">{{ label }}</div>
      </div>
      <div class="header-right">
        <span v-if="hasTransaction" class="transaction-badge">
          <span class="badge-icon">🔒</span>
          事务
        </span>
        <span class="child-count">{{ childCount }} 个服务</span>
      </div>
    </div>
    
    <div class="group-body">
      <div v-if="childCount === 0" class="empty-hint">
        拖拽服务节点到此处
      </div>
      <!-- 子节点会自动渲染在这里 -->
      <slot />
    </div>
    
    <div v-if="hasTransaction" class="transaction-info">
      <span class="info-label">事务管理器:</span>
      <span class="info-value">{{ transactionMgr }}</span>
    </div>
  </div>
</template>

<style scoped>
.task-group-node {
  width: 100%;
  height: 100%;
  min-width: 320px;
  min-height: 200px;
  border-radius: 12px;
  border: 2px solid rgba(99, 102, 241, 0.4);
  background: transparent;
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.12);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: visible;
}

.task-group-node.with-transaction {
  border-color: rgba(16, 185, 129, 0.6);
  background: transparent;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.15);
}

.group-header {
  padding: 12px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 10px 10px 0 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-icon {
  color: #6366f1;
}

.task-group-node.with-transaction .group-icon {
  color: #10b981;
}

.group-title {
  font-weight: 600;
  font-size: 14px;
  color: #1e293b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.transaction-badge {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
  color: #065f46;
  font-size: 11px;
  font-weight: 600;
}

.badge-icon {
  font-size: 12px;
}

.child-count {
  font-size: 11px;
  color: #64748b;
  padding: 3px 8px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.08);
}

.group-body {
  flex: 1;
  padding: 16px;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}

.empty-hint {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
  border: 2px dashed rgba(148, 163, 184, 0.3);
  border-radius: 8px;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.5);
  pointer-events: none;
}

.transaction-info {
  padding: 10px 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.15);
  background: rgba(16, 185, 129, 0.05);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  border-radius: 0 0 10px 10px;
}

.info-label {
  color: #64748b;
  font-weight: 500;
}

.info-value {
  color: #065f46;
  font-weight: 600;
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
}

/* 服务组的连接点样式 */
.task-group-node :deep(.vue-flow__handle) {
  width: 12px;
  height: 12px;
  border: 2px solid #6366f1;
  background: #fff;
  box-shadow: 0 2px 4px rgba(99, 102, 241, 0.2);
}

.task-group-node.with-transaction :deep(.vue-flow__handle) {
  border-color: #10b981;
}

/* 允许子节点拖拽到组内并确保连接点可点击 */
.group-body :deep(.vue-flow__node) {
  pointer-events: auto;
  z-index: 2;
}

/* 确保子节点的连接点清晰可见 */
.group-body :deep(.vue-flow__handle) {
  pointer-events: auto;
  z-index: 100 !important;
  width: 14px !important;
  height: 14px !important;
  border: 3px solid #ef4444 !important;
  background: #ffffff !important;
  opacity: 1 !important;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2), 0 3px 8px rgba(0, 0, 0, 0.3) !important;
}

.group-body :deep(.vue-flow__handle:hover) {
  border-color: #dc2626 !important;
  box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.3), 0 4px 12px rgba(0, 0, 0, 0.4) !important;
  transform: scale(1.2);
}
</style>
