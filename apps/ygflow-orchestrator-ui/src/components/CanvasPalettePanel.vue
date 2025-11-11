<script setup lang="ts">
import { ChevronLeft, ChevronRight } from "lucide-vue-next"
import Palette from "./Palette.vue"

const props = defineProps<{
  collapsed: boolean
  projectKey: string
}>()

const emit = defineEmits<{
  (event: "toggle"): void
}>()
</script>

<template>
  <div class="sider palette-sider" :class="{ collapsed: props.collapsed }">
    <div class="drawer-toggle">
      <button class="drawer-btn" type="button" @click="emit('toggle')">
        <ChevronRight v-if="props.collapsed" :size="14" />
        <ChevronLeft v-else :size="14" />
      </button>
      <span v-if="props.collapsed" class="collapsed-text">组件</span>
    </div>
    <template v-if="!props.collapsed">
      <div class="bar">
        <div class="title">组件面板</div>
      </div>
      <Palette :project-key="props.projectKey" />
    </template>
  </div>
</template>

<style scoped>
.palette-sider {
  position: relative;
  overflow: hidden;
  transition: width 0.2s ease;
}

.palette-sider.collapsed {
  border-right: 1px solid var(--border);
  display: flex;
  align-items: center;
  justify-content: center;
}

.drawer-toggle {
  position: absolute;
  top: 10px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  z-index: 5;
}

.palette-sider.collapsed .drawer-toggle {
  position: static;
  flex-direction: column;
}

.drawer-btn {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 50%;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.12);
}

.collapsed-text {
  writing-mode: vertical-rl;
  font-size: 12px;
  color: #475569;
  letter-spacing: 2px;
}

.bar {
  padding: 12px 16px 8px;
}

.title {
  font-weight: 600;
  font-size: 13px;
  color: #0f172a;
}
</style>

