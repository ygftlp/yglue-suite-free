<script setup lang="ts">
import { computed, watch } from "vue"

type ConverterValue = {
  kind?: string
  targetType?: "STRING" | "NUMBER" | "BOOLEAN" | "OBJECT" | "ARRAY"
  targetTypeName?: string
  script?: string
  arrayElementType?: "STRING" | "NUMBER" | "BOOLEAN" | "OBJECT"
  arrayElementTypeName?: string
}

const props = defineProps<{
  value?: ConverterValue
  modelOptions?: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  (e: "update:value", value: ConverterValue): void
}>()

const kind = computed(() => props.value?.kind || "GENERAL")
const targetType = computed(
  () => ((props.value?.targetType || "STRING") as ConverterValue["targetType"])
)
const arrayElementType = computed(
  () => ((props.value?.arrayElementType || "STRING") as ConverterValue["arrayElementType"])
)

function update(partial: Partial<ConverterValue>) {
  // 基线默认 + 旧值 + 新改动（partial 最后，确保覆盖）
  emit("update:value", {
    kind: kind.value,
    targetType: targetType.value,
    targetTypeName: props.value?.targetTypeName || "",
    script: props.value?.script || "",
    arrayElementType: props.value?.arrayElementType || "STRING",
    arrayElementTypeName: props.value?.arrayElementTypeName || "",
    ...props.value,
    ...partial,
  })
}

function onKindChange(event: Event) {
  const newKind = (event.target as HTMLSelectElement).value
  update({ kind: newKind })
}

function onTargetChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value as ConverterValue["targetType"]
  // 切换目标类型时做联动清理
  const patch: Partial<ConverterValue> = { targetType: value }
  if (value !== "OBJECT" && (props.value?.targetTypeName || "") !== "") {
    patch.targetTypeName = ""
  }
  if (value !== "ARRAY") {
    patch.arrayElementType = "STRING"
    patch.arrayElementTypeName = ""
  }
  update(patch)
}

function onTypeNameInput(event: Event) {
  update({ targetTypeName: (event.target as HTMLInputElement).value })
}

function onElementTypeChange(event: Event) {
  const value = (event.target as HTMLSelectElement).value as ConverterValue["arrayElementType"]
  const patch: Partial<ConverterValue> = { arrayElementType: value }
  if (value !== "OBJECT" && (props.value?.arrayElementTypeName || "") !== "") {
    patch.arrayElementTypeName = ""
  }
  update(patch)
}

function onElementTypeNameInput(event: Event) {
  update({ arrayElementTypeName: (event.target as HTMLInputElement).value })
}

function onScriptChange(event: Event) {
  update({ script: (event.target as HTMLTextAreaElement).value })
}

/* ---------- 防御性 watch：处理外部直接改 props.value 的场景 ---------- */
watch(
  () => targetType.value,
  (nv) => {
    // 非 OBJECT 时，清空 targetTypeName
    if (nv !== "OBJECT" && (props.value?.targetTypeName || "") !== "") {
      update({ targetTypeName: "" })
    }
    // 非 ARRAY 时，复位 arrayElement*
    if (nv !== "ARRAY" && (props.value?.arrayElementType !== "STRING" || (props.value?.arrayElementTypeName || "") !== "")) {
      update({ arrayElementType: "STRING", arrayElementTypeName: "" })
    }
  }
)

watch(
  () => arrayElementType.value,
  (nv) => {
    if (targetType.value === "ARRAY" && nv !== "OBJECT" && (props.value?.arrayElementTypeName || "") !== "") {
      update({ arrayElementTypeName: "" })
    }
  }
)
</script>

<template>
  <div class="converter">
    <!-- 规则类型 -->
    <div class="converter-row">
      <div class="target-config">
        <div class="muted small">规则类型</div>
        <div class="target-inputs">
          <select class="input" :value="kind" @change="onKindChange">
            <option value="GENERAL">GENERAL（常规转换）</option>
            <option value="SCRIPT">SCRIPT（仅脚本，不可改目标类型）</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 目标类型 -->
    <div class="converter-row">
      <div class="target-config">
        <div class="muted small">目标类型</div>
        <div class="target-inputs">
          <select class="input" :value="targetType" @change="onTargetChange" :disabled="kind !== 'GENERAL'">
            <option value="STRING">String</option>
            <option value="NUMBER">Number</option>
            <option value="BOOLEAN">Boolean</option>
            <option value="OBJECT">Object</option>
            <option value="ARRAY">Array</option>
          </select>

          <!-- OBJECT: 可输入 + 候选模型 -->
          <div v-if="targetType === 'OBJECT'" class="type-row">
            <input
              class="input"
              list="modelTypeList"
              :value="value?.targetTypeName || ''"
              placeholder="类型名称（如 com.demo.Model）"
              @input="onTypeNameInput"
            />
            <datalist id="modelTypeList">
              <option
                v-for="opt in modelOptions || []"
                :key="opt.value"
                :value="opt.value"
              >模型：{{ opt.label }}</option>
            </datalist>
          </div>

          <!-- ARRAY: 选择元素类型 +（若为 OBJECT 时）元素模型 -->
          <div v-if="targetType === 'ARRAY'" class="type-row">
            <select
              class="input"
              style="min-width:140px"
              :value="value?.arrayElementType || 'STRING'"
              @change="onElementTypeChange"
            >
              <option value="STRING">元素：String</option>
              <option value="NUMBER">元素：Number</option>
              <option value="BOOLEAN">元素：Boolean</option>
              <option value="OBJECT">元素：Object</option>
            </select>

            <template v-if="(value?.arrayElementType || 'STRING') === 'OBJECT'">
              <input
                class="input"
                list="modelElementTypeList"
                :value="value?.arrayElementTypeName || ''"
                placeholder="元素模型（如 com.demo.Item）"
                @input="onElementTypeNameInput"
              />
              <datalist id="modelElementTypeList">
                <option
                  v-for="opt in modelOptions || []"
                  :key="opt.value"
                  :value="opt.value"
                >模型：{{ opt.label }}</option>
              </datalist>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 脚本 -->
    <div class="muted" style="font-size:11px">转换规则（可选）</div>
    <textarea
      class="input script"
      :value="value?.script || ''"
      placeholder="示例：return JSON.parse(request.body);"
      @input="onScriptChange"
    ></textarea>
  </div>
</template>

<style scoped>
.converter {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.converter-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
}

.target-config {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.target-inputs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 12px;
  min-width: 140px;
}

.type-row {
  display: flex;
  gap: 6px;
  flex: 1 1 200px;
  flex-wrap: wrap;
}

.type-row .input {
  flex: 1 1 220px;
}

.input.script {
  min-height: 60px;
  resize: vertical;
}

.small {
  font-size: 11px;
}

.muted {
  color: #64748b;
}
</style>
