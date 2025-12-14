<script setup lang="ts">
import { computed, ref, watch } from "vue"

const props = defineProps<{
  txMode?: string
  transactionManager?: string
}>()

const emit = defineEmits<{
  (event: "update:txMode", value: string): void
  (event: "update:transactionManager", value: string): void
}>()

// 事务管理器列表
const transactionManagers = [
  { value: "transactionManager", label: "transactionManager (默认)" },
  { value: "custom", label: "自定义..." },
]

// 事务模式
const localTxMode = computed({
  get: () => props.txMode || "NONE",
  set: (value) => emit("update:txMode", value),
})

// 是否显示事务管理器配置
const showTransactionManager = computed(() => {
  return localTxMode.value === "SPRING"
})

// 是否显示自定义输入框
const showCustomInput = ref(false)

// 事务管理器选择
const selectedTransactionManager = computed({
  get: () => {
    if (showCustomInput.value) return "custom"
    return props.transactionManager || "transactionManager"
  },
  set: (value) => {
    if (value === "custom") {
      showCustomInput.value = true
      // 不改变实际的值
    } else {
      showCustomInput.value = false
      emit("update:transactionManager", value)
    }
  },
})

// 自定义事务管理器输入
const customTransactionManager = computed({
  get: () => props.transactionManager || "transactionManager",
  set: (value) => emit("update:transactionManager", value),
})

// 当 props.transactionManager 变化时,检查是否需要显示自定义输入框
watch(() => props.transactionManager, (newValue) => {
  if (newValue && newValue !== "transactionManager") {
    // 如果值不是默认的,可能是自定义的,检查是否在预定义列表中
    const isPredefined = transactionManagers.some(m => m.value === newValue && m.value !== "custom")
    if (!isPredefined) {
      showCustomInput.value = true
    }
  }
}, { immediate: true })
</script>

<template>
  <div class="transaction-config">
    <!-- 事务模式 -->
    <div class="config-field">
      <label class="config-label">事务模式</label>
      <select v-model="localTxMode" class="config-select">
        <option value="NONE">无</option>
        <option value="SPRING">Spring 声明式事务</option>
        <option value="SEATA">Seata 分布式事务</option>
        <option value="SAGA">Saga 长事务</option>
      </select>
    </div>

    <!-- 事务管理器 (仅Spring模式) -->
    <transition name="fade">
      <div v-if="showTransactionManager" class="config-field">
        <label class="config-label">事务管理器 (可选)</label>
        <select v-model="selectedTransactionManager" class="config-select">
          <option
            v-for="mgr in transactionManagers"
            :key="mgr.value"
            :value="mgr.value"
          >
            {{ mgr.label }}
          </option>
        </select>
        <div class="config-hint">
          Spring 事务管理器 Bean 名称,默认使用 transactionManager
        </div>
        
        <!-- 自定义事务管理器输入框 -->
        <transition name="fade">
          <div v-if="showCustomInput" class="custom-input-wrapper">
            <input
              v-model="customTransactionManager"
              type="text"
              class="config-input"
              placeholder="输入自定义事务管理器 Bean 名称"
            />
            <div class="config-hint">
              输入在 Spring 容器中注册的事务管理器 Bean 名称
            </div>
          </div>
        </transition>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.transaction-config {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.config-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.config-label {
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}

.config-select,
.config-input {
  padding: 10px 12px;
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 8px;
  font-size: 13px;
  background: #fff;
  transition: border-color 0.2s;
}

.config-select:focus,
.config-input:focus {
  outline: none;
  border-color: #6366f1;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1);
}

.config-hint {
  font-size: 11px;
  color: #64748b;
  line-height: 1.5;
}

.custom-input-wrapper {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(99, 102, 241, 0.05);
  border: 1px dashed rgba(99, 102, 241, 0.3);
}

.custom-input-wrapper .config-input {
  width: 100%;
  box-sizing: border-box;
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
