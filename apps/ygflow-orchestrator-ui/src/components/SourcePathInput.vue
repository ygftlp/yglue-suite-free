<script setup lang="ts">
import { computed } from "vue"

const props = defineProps<{
  modelValue?: string
  options?: string[]
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void
}>()

const listId = `path_${Math.random().toString(36).slice(2, 9)}`

const groupedOptions = computed(() => {
  const rawOptions = (Array.isArray(props.options) ? props.options : [])
    .map((item) => String(item || "").trim())
    .filter((item) => Boolean(item))

  const bucket = new Map<string, string[]>()
  for (const path of rawOptions) {
    const group = path.startsWith("request.path.")
      ? "Path"
      : path.startsWith("request.query.")
        ? "Query"
        : path.startsWith("request.headers.")
          ? "Header"
          : path.startsWith("request.body.")
            ? "Body"
            : "Upstream"
    if (!bucket.has(group)) bucket.set(group, [])
    bucket.get(group)!.push(path)
  }

  return Array.from(bucket.entries()).map(([title, items]) => ({
    title,
    items: items.slice(0, 8),
  }))
})
const flatOptions = computed(() => groupedOptions.value.flatMap((group) => group.items))

function updateValue(value: string) {
  emit("update:modelValue", value)
}
</script>

<template>
  <div class="source-path-input">
    <input
      class="input"
      :value="modelValue || ''"
      :list="listId"
      :placeholder="placeholder || 'request.body.xxx'"
      @input="updateValue(($event.target as HTMLInputElement).value)"
    />
    <datalist :id="listId">
      <option v-for="path in flatOptions" :key="path" :value="path" />
    </datalist>
    <div v-if="groupedOptions.length > 0" class="quick-source-panel">
      <div v-for="group in groupedOptions" :key="group.title" class="quick-source-group">
        <span class="quick-source-title">{{ group.title }}</span>
        <div class="quick-source-list">
          <button
            v-for="path in group.items"
            :key="path"
            type="button"
            class="path-chip"
            @click="updateValue(path)"
          >
            {{ path }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.source-path-input {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.input {
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.quick-source-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: 1px dashed #dbe2ea;
  border-radius: 8px;
  background: #f8fbff;
}

.quick-source-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.quick-source-title {
  font-size: 10px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.quick-source-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.path-chip {
  border: 1px solid rgba(37, 99, 235, 0.18);
  background: #ffffff;
  color: #1d4ed8;
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 10px;
  line-height: 1.4;
  cursor: pointer;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.path-chip:hover {
  background: rgba(219, 234, 254, 0.75);
  border-color: rgba(37, 99, 235, 0.28);
}
</style>
