<script setup lang="ts">
import { Handle, Position } from "@vue-flow/core"

const props = defineProps<{ data?: { label?: string; expression?: string } }>()
</script>

<template>
  <div class="branch-node">
    <div class="diamond">
      <div class="content">
        <div class="label">{{ props.data?.label || '条件分支' }}</div>
        <div class="meta">{{ props.data?.expression || '请配置分支条件' }}</div>
      </div>
    </div>

    <div class="handle handle-top">
      <span class="box"></span>
      <Handle id="in-top" type="target" :position="Position.Top" class="hit-area" />
    </div>
    <div class="handle handle-left">
      <span class="box"></span>
      <Handle id="in-left" type="target" :position="Position.Left" class="hit-area" />
    </div>
    <div class="handle handle-right">
      <span class="box"></span>
      <Handle id="out-right" type="source" :position="Position.Right" class="hit-area" />
    </div>
    <div class="handle handle-bottom">
      <span class="box"></span>
      <Handle id="out-bottom" type="source" :position="Position.Bottom" class="hit-area" />
    </div>
  </div>
</template>

<style scoped>
.branch-node {
  position: relative;
  width: 168px;
  height: 168px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.diamond {
  width: 132px;
  height: 132px;
  background: #ffffff;
  border: 1px solid var(--border, #d0d7de);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  transform: rotate(45deg);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.content {
  transform: rotate(-45deg);
  width: 78%;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: #1f2937;
}

.label {
  font-size: 13px;
  font-weight: 600;
}

.meta {
  font-size: 12px;
  color: #2563eb;
}

.handle {
  position: absolute;
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.handle-top {
  top: -2px;
  left: 50%;
  transform: translate(-50%, -100%);
}

.handle-bottom {
  bottom: -2px;
  left: 50%;
  transform: translate(-50%, 100%);
}

.handle-left {
  left: -2px;
  top: 50%;
  transform: translate(-100%, -50%);
}

.handle-right {
  right: -2px;
  top: 50%;
  transform: translate(100%, -50%);
}

.box {
  width: 14px;
  height: 14px;
  border: 2px solid #2563eb;
  background: #fff;
  border-radius: 4px;
}

.hit-area {
  position: absolute;
  inset: 0;
  opacity: 0;
}
</style>
