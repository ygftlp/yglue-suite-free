<script setup lang="ts">
import { computed, ref, watch } from "vue"
import TransactionConfig from "./TransactionConfig.vue"

const props = defineProps<{
  label?: string
  enableTransaction?: boolean
  txMode?: string
  transactionManager?: string
}>()

const emit = defineEmits<{
  (event: "update:label", value: string): void
  (event: "update:enableTransaction", value: boolean): void
  (event: "update:txMode", value: string): void
  (event: "update:transactionManager", value: string): void
}>()

const localLabel = computed({
  get: () => props.label || "",
  set: (value) => emit("update:label", value),
})

const localEnableTransaction = computed({
  get: () => props.enableTransaction ?? false,
  set: (value) => emit("update:enableTransaction", value),
})
</script>

<template>
  <div class="task-group-config">
    <div class="config-section">
      <label class="config-label">任务组名称</label>
      <input
        v-model="localLabel"
        type="text"
        class="config-input"
        placeholder="例如: 订单处理任务组"
      />
      <div class="config-hint">用于标识这个任务组的作用</div>
    </div>

    <div class="config-section">
      <label class="config-label config-label-switch">
        <input
          v-model="localEnableTransaction"
          type="checkbox"
          class="config-checkbox"
        />
        <span>启用事务控制</span>
      </label>
      <div class="config-hint">
        开启后,组内所有服务将在同一个事务中执行,任何服务失败都会回滚
      </div>
    </div>

    <transition name="fade">
      <div v-if="localEnableTransaction" class="config-section">
        <label class="config-label">事务配置</label>
        <TransactionConfig
          :tx-mode="txMode"
          :transaction-manager="transactionManager"
          @update:tx-mode="emit('update:txMode', $event)"
          @update:transaction-manager="emit('update:transactionManager', $event)"
        />
      </div>
    </transition>

    <div class="config-tips">
      <div class="tip-title">💡 使用提示</div>
      <ul class="tip-list">
        <li>拖拽服务节点到组内,它们将作为一个整体执行</li>
        <li>开启事务后,任何服务失败都会触发回滚</li>
        <li>Spring 事务:适用于单数据库场景</li>
        <li>Seata 事务:适用于分布式微服务场景</li>
        <li>Saga 事务:适用于长流程补偿场景</li>
        <li>组内节点的执行顺序由连线决定</li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.task-group-config {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.config-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.config-label-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.config-checkbox {
  width: 18px;
  height: 18px;
  cursor: pointer;
  accent-color: #10b981;
}

.config-input,
.config-select {
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 8px;
  font-size: 13px;
  background: #fff;
  transition: border-color 0.2s;
}

.config-input:focus,
.config-select:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.config-hint {
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.config-tips {
  margin-top: 12px;
  padding: 14px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px solid rgba(99, 102, 241, 0.15);
}

.tip-title {
  font-size: 12px;
  font-weight: 600;
  color: #4338ca;
  margin-bottom: 8px;
}

.tip-list {
  margin: 0;
  padding-left: 20px;
  font-size: 11px;
  color: #64748b;
  line-height: 1.8;
}

.tip-list li {
  margin-bottom: 4px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
