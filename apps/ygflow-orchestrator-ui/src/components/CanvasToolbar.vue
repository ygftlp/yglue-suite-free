<script setup lang="ts">
const props = defineProps<{
  statusMessage: string
  activeError: string | null
  logPreview: string
  canDelete: boolean
  canSave: boolean
  canPublish: boolean
  showPublishButton: boolean
  precheckErrors: number
  precheckWarnings: number
  precheckBlocking: boolean
  precheckHighlights: string[]
  precheckMessage: string
}>()

const emit = defineEmits<{
  (event: "open-flow-settings"): void
  (event: "clear-selection"): void
  (event: "delete-selection"): void
  (event: "save-draft"): void
  (event: "publish-flow"): void
  (event: "open-version-list"): void
}>()

function handlePublishClick() {
  if (!props.canPublish) {
    if (!props.showPublishButton) {
      window.alert("当前版本已发布。若要再次发布，请先修改内容并保存新版本。")
    } else if (props.precheckBlocking) {
      window.alert(`当前无法发布，请先修复编排错误：\n${props.precheckMessage}`)
    } else {
      window.alert("当前无法发布，请先检查流程内容或保存状态。")
    }
    return
  }
  emit("publish-flow")
}

function resolvePublishTitle() {
  if (props.canPublish && !props.precheckBlocking) return "发布当前流程"
  if (!props.showPublishButton) return "当前版本已发布"
  if (props.precheckBlocking) return "请先修复编排错误"
  return "请先修改内容"
}
</script>

<template>
  <div class="canvas-toolbar">
    <div class="toolbar-left">
      <div class="toolbar-title"></div>
      <div class="toolbar-desc"></div>
      <div class="toolbar-status">{{ props.statusMessage }}</div>
      <div v-if="props.activeError" class="toolbar-error">{{ props.activeError }}</div>
      <div
        class="precheck-card"
        :class="{ blocking: props.precheckBlocking, pass: !props.precheckErrors && !props.precheckWarnings }"
        :title="props.precheckMessage || '当前流程未发现明显阻塞项'"
      >
        <div class="precheck-header">
          <span class="precheck-title">
            {{
              props.precheckBlocking
                ? "发布前需修复"
                : (props.precheckErrors || props.precheckWarnings ? "发布前建议复核" : "流程预检通过")
            }}
          </span>
          <div class="precheck-badges">
            <span v-if="props.precheckErrors" class="precheck-badge error">错误 {{ props.precheckErrors }}</span>
            <span v-if="props.precheckWarnings" class="precheck-badge warning">告警 {{ props.precheckWarnings }}</span>
            <span v-if="!props.precheckErrors && !props.precheckWarnings" class="precheck-badge pass">可发布</span>
          </div>
        </div>
        <div v-if="props.precheckHighlights.length" class="precheck-list">
          <div v-for="(item, index) in props.precheckHighlights" :key="`${index}-${item}`" class="precheck-item">
            {{ index + 1 }}. {{ item }}
          </div>
        </div>
        <div v-else class="precheck-item">当前流程未发现明显阻塞项，可以继续保存或发布。</div>
      </div>
      <button class="settings-pill" type="button" @click="emit('open-flow-settings')">
        {{ props.logPreview }}
      </button>
    </div>
    <div class="toolbar-actions">
      <button class="btn ghost" type="button" @click="emit('open-flow-settings')">流程设置</button>
      <button class="btn ghost" type="button" @click="emit('open-version-list')">版本列表</button>
      <button class="btn" type="button" @click="emit('clear-selection')">清空选中</button>
      <button class="btn" type="button" :disabled="!props.canDelete" @click="emit('delete-selection')">
        删除选中
      </button>
      <button class="btn" type="button" :disabled="!props.canSave" @click="emit('save-draft')">保存草稿</button>
      <button
        class="btn primary"
        type="button"
        :class="{ 'btn-disabled': !props.canPublish }"
        @click="handlePublishClick"
        :title="resolvePublishTitle()"
      >
        {{ props.showPublishButton ? "发布" : "已发布" }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.canvas-toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 16px;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  box-shadow: 0 8px 30px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(6px);
  pointer-events: auto;
  flex-wrap: wrap;
  max-width: 100%;
  overflow: hidden;
}

.toolbar-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  flex: 1 1 280px;
}

.toolbar-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.toolbar-desc {
  font-size: 12px;
  color: #64748b;
}

.toolbar-status {
  font-size: 11px;
  color: #475569;
}

.toolbar-error {
  font-size: 11px;
  color: #b91c1c;
}

.precheck-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid rgba(245, 158, 11, 0.28);
  background: rgba(255, 251, 235, 0.95);
  color: #78350f;
}

.precheck-card.blocking {
  border-color: rgba(220, 38, 38, 0.22);
  background: rgba(254, 242, 242, 0.96);
  color: #7f1d1d;
}

.precheck-card.pass {
  border-color: rgba(16, 185, 129, 0.22);
  background: rgba(236, 253, 245, 0.95);
  color: #065f46;
}

.precheck-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.precheck-title {
  font-size: 12px;
  font-weight: 600;
}

.precheck-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.precheck-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.precheck-badge.error {
  background: rgba(220, 38, 38, 0.14);
  color: #b91c1c;
}

.precheck-badge.warning {
  background: rgba(245, 158, 11, 0.16);
  color: #b45309;
}

.precheck-badge.pass {
  background: rgba(16, 185, 129, 0.14);
  color: #047857;
}

.precheck-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.precheck-item {
  font-size: 11px;
  line-height: 1.5;
}

.settings-pill {
  align-self: flex-start;
  border: 1px solid rgba(37, 99, 235, 0.4);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 11px;
  color: #1d4ed8;
  background: rgba(37, 99, 235, 0.08);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.settings-pill:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.15);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  min-width: 0;
  flex: 1 1 420px;
}

.btn.ghost {
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: transparent;
  color: #0f172a;
}

.btn-disabled {
  opacity: 0.6;
  cursor: not-allowed;
  pointer-events: auto;
}

.btn-disabled:hover {
  opacity: 0.6;
}
</style>
