<script setup lang="ts">
import { computed, ref, watch } from "vue"

const props = defineProps<{
  value: {
    type?: string
    path?: string
    constant?: string
    expression?: string
    cast?: "STRING" | "NUMBER" | "BOOLEAN" | "OBJECT" | "ARRAY"
    default?: string
  } | null
  disabled?: boolean
  placeholder?: string
  requestSchema?: Array<{ name: string; type: string }> | null
  entrypointPath?: string | null
  resolverCatalog?: Array<{ type: string; name?: string | null; description?: string | null }> | null
}>()

const emit = defineEmits<{
  (e: "update:value", payload: any): void
}>()

const mode = computed(() => (props.value?.type || "request").toLowerCase())

const builtinOptions = [
  { value: "request", label: "请求参数" },
  { value: "context", label: "上下文" },
  { value: "expression", label: "表达式" },
  { value: "constant", label: "常量" },
]

const modeOptions = computed(() => {
  const map = new Map<string, { value: string; label: string; description?: string | null }>()
  builtinOptions.forEach((item) => map.set(item.value, item))
  ;(props.resolverCatalog ?? []).forEach((item) => {
    if (!item?.type) return
    const key = item.type.toLowerCase()
    map.set(key, {
      value: key,
      label: item.name || item.type,
      description: item.description,
    })
  })
  return Array.from(map.values())
})

const selectedOption = computed(() => modeOptions.value.find((item) => item.value === mode.value))

// 从路径中提取路径变量（如 /api/projects/{projectKey}/flows/{code} -> [projectKey, code]）
function extractPathVariables(path: string | null | undefined): string[] {
  if (!path) return []
  const matches = path.match(/\{([^}]+)\}/g)
  if (!matches) return []
  return matches.map(m => m.slice(1, -1)) // 移除 { 和 }
}

// 生成参数路径提示
const pathHints = computed(() => {
  if (mode.value !== "request") {
    return []
  }
  
  const hints: string[] = []
  
  // 添加整个对象的路径（用于映射整个对象）
  hints.push("request.body", "request.path", "request.query", "request.headers", "request")
  
  // 从入口点路径中提取路径变量
  const pathVars = extractPathVariables(props.entrypointPath)
  pathVars.forEach(varName => {
    hints.push(`request.path.${varName}`)
  })
  
  // 保留字段名，这些字段不应该出现在 request.body.xxx 中
  const reservedFields = new Set(['request', 'body', 'path', 'query', 'headers'])
  
  // 从请求参数结构生成提示（根据 source 字段分类）
  if (props.requestSchema && props.requestSchema.length > 0) {
    props.requestSchema.forEach(field => {
      if (!field.name) return
      
      // 跳过保留字段名，避免生成 request.body.request 这种错误的提示
      if (reservedFields.has(field.name)) {
        return
      }
      
      const source = (field as any).source // path, query, body, header
      const pathVariable = (field as any).pathVariable // @PathVariable 的变量名
      const paramName = (field as any).paramName // @RequestParam 的参数名
      
      if (source === "path") {
        // 路径变量：使用 pathVariable（如果存在）或参数名
        // 路径变量只应该出现在 request.path 中，不应该出现在 request.body 中
        const varName = pathVariable || field.name
        // 再次检查，避免路径变量名也是保留字段
        if (!reservedFields.has(varName)) {
          hints.push(`request.path.${varName}`)
        }
      } else if (source === "query") {
        // 查询参数：使用 paramName（如果存在）或参数名
        const queryName = paramName || field.name
        // 再次检查，避免查询参数名也是保留字段
        if (!reservedFields.has(queryName)) {
          hints.push(`request.query.${queryName}`)
        }
      } else if (source === "body") {
        // 请求体字段：只有 source 为 "body" 的字段才应该出现在 request.body 中
        // 字段名已经在上面检查过了，这里直接添加
        hints.push(`request.body.${field.name}`)
      } else if (source === "header") {
        // 请求头
        if (!reservedFields.has(field.name)) {
          hints.push(`request.headers.${field.name}`)
        }
      } else {
        // 如果没有 source 字段，需要检查是否有 pathVariable
        // 如果有 pathVariable，说明是路径变量，不应该出现在 body 中
        if (pathVariable) {
          // 有 pathVariable 但没有 source，可能是旧数据，按路径变量处理
          if (!reservedFields.has(pathVariable)) {
            hints.push(`request.path.${pathVariable}`)
          }
        } else {
          // 没有 source 也没有 pathVariable，默认作为请求体字段（向后兼容）
          // 字段名已经在上面检查过了
          hints.push(`request.body.${field.name}`)
        }
      }
    })
  }
  
  // 注意：request.query 和 request.headers 已经在上面的 hints.push 中添加了
  // 不需要添加 request.query.* 和 request.headers.*，因为 * 会被当作键名处理
  
  return hints
})

function update(partial: Record<string, any>) {
  if (props.disabled) return
  const next = {
    type: mode.value,
    cast: props.value?.cast || "STRING",
    default: props.value?.default || "",
    ...props.value,
    ...partial,
  }
  emit("update:value", next)
}

function changeMode(nextType: string) {
  if (props.disabled) return
  update({ type: nextType })
}

const showHints = ref(false)
const isPathFromHint = ref(false) // 标记路径是否来自提示选择

// 监听 value.path 的变化，如果路径与提示列表中的完全匹配，自动设置为只读
watch(() => props.value?.path, (newPath) => {
  if (newPath && pathHints.value.includes(newPath)) {
    // 如果路径在提示列表中，且之前不是只读状态，说明可能是从 datalist 选择的
    if (!isPathFromHint.value) {
      isPathFromHint.value = true
    }
  } else if (newPath && !pathHints.value.includes(newPath)) {
    // 如果路径不在提示列表中，说明是手动输入的，取消只读状态
    if (isPathFromHint.value) {
      isPathFromHint.value = false
    }
  }
}, { immediate: true })

function getPathPlaceholder() {
  if (props.placeholder) return props.placeholder
  if (mode.value === "request") {
    return "路径，如 request.body（整个对象）或 request.body.versionNo（单个字段）"
  }
  return "路径，如 ctx.userId"
}

function selectHint(hint: string) {
  update({ path: hint })
  isPathFromHint.value = true // 标记路径来自提示选择
  showHints.value = false
}

// 监听路径变化
function onPathInput(event: Event) {
  const input = event.target as HTMLInputElement
  const newPath = input.value
  const oldPath = props.value?.path || ''
  
  // 如果当前是只读状态（来自提示选择），且用户手动修改了内容，取消只读状态
  // 但只有当用户真正输入了不同内容时才取消（不是通过 datalist 选择）
  if (isPathFromHint.value && newPath !== oldPath) {
    // 检查新路径是否在提示列表中，如果不在，说明是手动输入
    const isInHints = pathHints.value.includes(newPath)
    if (!isInHints || newPath.length < oldPath.length) {
      // 不在提示列表中，或者长度变短（说明删除了字符），说明是手动编辑
      isPathFromHint.value = false
    }
  }
  
  update({ path: newPath })
}

// 监听 change 事件（datalist 选择会触发 change）
function onPathChange(event: Event) {
  const input = event.target as HTMLInputElement
  const newPath = input.value
  const oldPath = props.value?.path || ''
  
  // 如果新路径在提示列表中，且与旧路径不同，说明是从 datalist 选择的
  if (newPath !== oldPath && pathHints.value.includes(newPath)) {
    isPathFromHint.value = true
  }
}

// 双击输入框时，允许编辑（取消只读状态）
function onDoubleClick() {
  if (isPathFromHint.value && !props.disabled) {
    isPathFromHint.value = false
  }
}
</script>

<template>
  <div class="resolver" :class="{ disabled }">
    <select class="input mode" :value="mode" @change="changeMode(($event.target as HTMLSelectElement).value as any)">
      <option v-for="option in modeOptions" :key="option.value" :value="option.value">
        {{ option.label }}
      </option>
    </select>

    <template v-if="mode === 'request' || mode === 'context'">
      <div class="path-input-wrapper">
        <input
          class="input flex"
          :class="{ 'readonly-hint': isPathFromHint && !disabled }"
          :placeholder="getPathPlaceholder()"
          :value="value?.path || ''"
          @input="onPathInput"
          @change="onPathChange"
          @dblclick="onDoubleClick"
          @focus="showHints = true"
          @blur="setTimeout(() => showHints = false, 200)"
          :readonly="disabled || isPathFromHint"
          list="param-path-hints"
          :title="isPathFromHint && !disabled ? '双击可编辑' : ''"
        />
        <datalist id="param-path-hints" v-if="mode === 'request'">
          <option v-for="hint in pathHints" :key="hint" :value="hint" />
        </datalist>
        <div v-if="mode === 'request' && showHints && pathHints.length > 0" class="path-hints">
          <div class="hint-title">可用参数路径：</div>
          <div class="hint-item" v-for="hint in pathHints" :key="hint" @mousedown.prevent="selectHint(hint)">
            {{ hint }}
          </div>
        </div>
      </div>
    </template>

    <template v-else-if="mode === 'expression'">
      <input
        class="input flex"
        placeholder="表达式，如 ctx.price * 0.9"
        :value="value?.expression || ''"
        @input="update({ expression: ($event.target as HTMLInputElement).value })"
        :readonly="disabled"
      />
    </template>

    <template v-else>
      <input
        class="input flex"
        placeholder="常量值"
        :value="value?.constant || ''"
        @input="update({ constant: ($event.target as HTMLInputElement).value })"
        :readonly="disabled"
      />
    </template>

    <div v-if="selectedOption?.description" class="mode-hint">{{ selectedOption.description }}</div>
  </div>
</template>

<style scoped>
.resolver {
  display: flex;
  gap: 6px;
  width: 100%;
  align-items: center;
  flex-wrap: wrap;
}

.resolver.disabled {
  opacity: 0.6;
  pointer-events: none;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 12px;
}

.input.flex {
  flex: 1 1 200px;
  min-width: 150px;
}

.mode {
  flex: 0 0 140px;
  min-width: 120px;
}

.path-input-wrapper {
  position: relative;
  flex: 1 1 200px;
  min-width: 150px;
}

.path-hints {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  margin-top: 4px;
  background: #fff;
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 6px;
  box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
  z-index: 100;
  max-height: 200px;
  overflow-y: auto;
  padding: 6px;
}

.hint-title {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  padding: 4px 8px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  margin-bottom: 4px;
}

.hint-item {
  padding: 6px 8px;
  font-size: 12px;
  color: #0f172a;
  cursor: pointer;
  border-radius: 4px;
  font-family: monospace;
}

.hint-item:hover {
  background: #f1f5f9;
}

.input.readonly-hint {
  background-color: #f8fafc;
  cursor: not-allowed;
  color: #64748b;
}

.input.readonly-hint:focus {
  outline: none;
  border-color: rgba(148, 163, 184, 0.6);
}

.mode-hint {
  font-size: 11px;
  color: #64748b;
  width: 100%;
}

</style>
