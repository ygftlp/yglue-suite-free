<script setup lang="ts">
const props = defineProps<{
  statusMessage: string
  activeError: string | null
  logPreview: string
  canDelete: boolean
  canSave: boolean
  canPublish: boolean
}>()

const emit = defineEmits<{
  (event: "open-flow-settings"): void
  (event: "clear-selection"): void
  (event: "delete-selection"): void
  (event: "save-draft"): void
  (event: "publish-flow"): void
}>()
</script>

<template>
  <div class="canvas-toolbar">
    <div class="toolbar-left">
      <div class="toolbar-title"></div>
      <div class="toolbar-desc"></div>
      <div class="toolbar-status">{{ props.statusMessage }}</div>
      <div v-if="props.activeError" class="toolbar-error">{{ props.activeError }}</div>
      <button class="settings-pill" type="button" @click="emit('open-flow-settings')">
        {{ props.logPreview }}
      </button>
    </div>
    <div class="toolbar-actions">
      <button class="btn ghost" type="button" @click="emit('open-flow-settings')">流程设置</button>
      <button class="btn" type="button" @click="emit('clear-selection')">清空选中</button>
      <button class="btn" type="button" :disabled="!props.canDelete" @click="emit('delete-selection')">
        删除选中
      </button>
      <button class="btn" type="button" :disabled="!props.canSave" @click="emit('save-draft')">保存草稿</button>
      <button class="btn primary" type="button" :disabled="!props.canPublish" @click="emit('publish-flow')">
        发布
      </button>
    </div>
  </div>
</template>

<style scoped>
.canvas-toolbar {
  position: absolute;
  top: 16px;
  left: 24px;
  right: 24px;
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
}

.toolbar-left {
  display: flex;
  flex-direction: column;
  gap: 6px;
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
}

.btn.ghost {
  border: 1px solid rgba(148, 163, 184, 0.6);
  background: transparent;
  color: #0f172a;
}
</style>


