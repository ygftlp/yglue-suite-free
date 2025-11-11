<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from "vue"
import type { editor } from "monaco-editor"
import * as monaco from "monaco-editor"
import type { FlowEntrypoint, FlowSettings, LogPolicy } from "../data/flowSettings"
type SchemaField = { name: string; type: string }
type ResponseSchema = { type?: string | null }

const props = defineProps<{
  modelValue: FlowSettings
  visible: boolean
  requestSchemaFields?: SchemaField[] | null
  requestSchemaJson?: string | null
  responseSchema?: ResponseSchema | null
}>()

const emit = defineEmits<{
  (e: "update:modelValue", value: FlowSettings): void
  (e: "close"): void
}>()

const logLevels: LogPolicy["level"][] = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
const logSinks: LogPolicy["sink"][] = ["console", "kafka", "http"]

const settings = computed(() => props.modelValue)
const logPolicy = computed(() => settings.value.logPolicy)
const lastEntrypoint = ref<FlowEntrypoint | null>(null)
const entrypoint = computed<FlowEntrypoint>(
  () => settings.value.entrypoint ?? lastEntrypoint.value ?? createEntrypoint()
)
const entrypointEnabled = computed(() => entrypoint.value.enabled !== false)
const requestSchemaFields = computed(() => props.requestSchemaFields ?? [])
const responseSchema = computed<ResponseSchema>(() => props.responseSchema ?? { type: "void" })
const hasRequestSchema = computed(() => requestSchemaFields.value.length > 0)
const responseSchemaType = computed(() => {
  const type = responseSchema.value?.type?.trim()
  if (!type) return "void"
  return type
})

watch(
  () => settings.value.entrypoint,
  (value) => {
    if (value) {
      lastEntrypoint.value = value
    }
  },
  { immediate: true }
)

function updateField<K extends keyof FlowSettings>(key: K, value: FlowSettings[K]) {
  emit("update:modelValue", { ...settings.value, [key]: value })
}

function updateLogPolicy(partial: Partial<LogPolicy>) {
  emit("update:modelValue", {
    ...settings.value,
    logPolicy: { ...logPolicy.value, ...partial },
  })
}

function closePanel() {
  emit("close")
}

function createEntrypoint(): FlowEntrypoint {
  return { path: "", method: "GET", replaceResponse: false, enabled: true, requestSchema: null }
}

function toggleEntrypoint(enabled: boolean) {
  const current = settings.value.entrypoint ?? lastEntrypoint.value ?? createEntrypoint()
  const next = { ...current, enabled }
  lastEntrypoint.value = next
  emit("update:modelValue", {
    ...settings.value,
    entrypoint: next,
  })
}

function updateEntrypointField<K extends keyof FlowEntrypoint>(key: K, value: FlowEntrypoint[K]) {
  const current = settings.value.entrypoint ?? lastEntrypoint.value ?? createEntrypoint()
  const next = { ...current, [key]: value }
  lastEntrypoint.value = next
  emit("update:modelValue", {
    ...settings.value,
    entrypoint: next,
  })
}

const schemaEditorContainer = ref<HTMLElement | null>(null)
let schemaEditor: editor.IStandaloneCodeEditor | null = null
const entrypointSchemaText = ref("")

watch(
  () => settings.value.entrypoint?.requestSchema ?? "",
  (value) => {
    if (value !== entrypointSchemaText.value) {
      entrypointSchemaText.value = value ?? ""
      if (schemaEditor && schemaEditor.getValue() !== entrypointSchemaText.value) {
        schemaEditor.setValue(entrypointSchemaText.value)
      }
    }
  },
  { immediate: true }
)

watch(
  () => props.requestSchemaJson,
  (value) => {
    if (!value) return
    if (!entrypointSchemaText.value) {
      const formatted = prettifyJson(value)
      entrypointSchemaText.value = formatted
      schemaEditor?.setValue(formatted)
      updateEntrypointField("requestSchema", formatted)
    }
  },
  { immediate: true }
)

onMounted(() => {
  if (!schemaEditorContainer.value) return
  schemaEditor = monaco.editor.create(schemaEditorContainer.value, {
    value: entrypointSchemaText.value,
    language: "json",
    theme: "vs-light",
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    tabSize: 2,
    readOnly: !entrypointEnabled.value,
  })
  schemaEditor.onDidChangeModelContent(() => {
    const value = schemaEditor?.getValue() ?? ""
    entrypointSchemaText.value = value
    updateEntrypointField("requestSchema", value)
  })
})

onBeforeUnmount(() => {
  schemaEditor?.dispose()
  schemaEditor = null
})

watch(entrypointEnabled, (enabled) => {
  if (schemaEditor) {
    schemaEditor.updateOptions({ readOnly: !enabled })
  }
})

function prettifyJson(value: string | null | undefined): string {
  if (!value) return ""
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function formatEntrypointSchema() {
  if (!entrypointSchemaText.value) return
  entrypointSchemaText.value = prettifyJson(entrypointSchemaText.value)
}

function inferSourceLabel(item: SchemaField): string {
  const path = (item as any).name || ""
  if (path.includes("[]")) {
    return "数组元素"
  }
  const source = (item as any).source
  switch (source) {
    case "path":
      return "路径"
    case "query":
      return "查询"
    case "header":
      return "请求头"
    case "form":
      return "表单"
    case "body":
      return "请求体"
    default:
      return source || ""
  }
}
</script>

<template>
  <transition name="flow-settings-fade">
    <div v-if="visible" class="flow-settings-overlay" @click.self="closePanel">
      <section class="flow-settings-panel" @click.stop>
        <header class="panel-header">
          <div>
            <div class="panel-title">流程设置</div>
            <p class="panel-desc">维护流程级别的基础信息与日志策略。</p>
          </div>
          <button class="ghost-btn" type="button" @click="closePanel">关闭</button>
        </header>

        <section class="panel-section">
          <div class="section-title">基础信息</div>
          <div class="section-grid">
            <label class="field">
              <span>流程标识</span>
              <input
                class="input readonly-input"
                type="text"
                placeholder="自动生成的 UUID"
                :value="settings.code"
                readonly
              />
              <span class="field-hint">流程标识由系统自动生成，无需手动输入。</span>
            </label>
            <label class="field">
              <span>流程名称</span>
              <input
                class="input"
                type="text"
                placeholder="例如：订单创建 LiteFlow"
                :value="settings.name"
                @input="updateField('name', ($event.target as HTMLInputElement).value)"
              />
            </label>
            <label class="field">
              <span>负责人</span>
              <input
                class="input"
                type="text"
                placeholder="owner@demo.com"
                :value="settings.owner"
                @input="updateField('owner', ($event.target as HTMLInputElement).value)"
              />
            </label>
            <label class="field full">
              <span>标签</span>
              <input
                class="input"
                type="text"
                placeholder="用英文逗号分隔，例如：pay,core"
                :value="settings.tags"
                @input="updateField('tags', ($event.target as HTMLInputElement).value)"
              />
            </label>
            <label class="field full">
              <span>描述</span>
              <textarea
                class="input textarea"
                rows="2"
                placeholder="给这个流程一个简单的说明"
                :value="settings.description"
                @input="updateField('description', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
            <label class="field full">
              <span>备注</span>
              <textarea
                class="input textarea"
                rows="2"
                placeholder="记录特殊约束、上线注意事项等"
                :value="settings.notes"
                @input="updateField('notes', ($event.target as HTMLTextAreaElement).value)"
              />
            </label>
          </div>
        </section>

        <section class="panel-section">
          <div class="section-title">日志策略</div>
          <div class="log-grid">
            <label class="field">
              <span>启用流程日志</span>
              <div class="toggle">
                <input
                  type="checkbox"
                  :checked="logPolicy.enabled"
                  @change="updateLogPolicy({ enabled: ($event.target as HTMLInputElement).checked })"
                />
                <span>{{ logPolicy.enabled ? "已启用" : "未启用" }}</span>
              </div>
            </label>

            <label class="field">
              <span>日志级别</span>
              <select
                class="input"
                :disabled="!logPolicy.enabled"
                :value="logPolicy.level"
                @change="updateLogPolicy({ level: ($event.target as HTMLSelectElement).value as LogPolicy['level'] })"
              >
                <option v-for="level in logLevels" :key="level" :value="level">{{ level }}</option>
              </select>
            </label>

            <label class="field">
              <span>落地通道</span>
              <select
                class="input"
                :disabled="!logPolicy.enabled"
                :value="logPolicy.sink"
                @change="updateLogPolicy({ sink: ($event.target as HTMLSelectElement).value as LogPolicy['sink'] })"
              >
                <option v-for="sink in logSinks" :key="sink" :value="sink">
                  {{ sink === "console" ? "Console" : sink === "kafka" ? "Kafka" : "HTTP Hook" }}
                </option>
              </select>
            </label>

            <label class="field full">
              <span>通道配置</span>
              <input
                class="input"
                type="text"
                :disabled="!logPolicy.enabled"
                placeholder="topic / url / service name"
                :value="logPolicy.sinkTarget"
                @input="updateLogPolicy({ sinkTarget: ($event.target as HTMLInputElement).value })"
              />
            </label>
          </div>

          <div class="collect-grid">
            <label class="checkbox">
              <input
                type="checkbox"
                :checked="logPolicy.collectInputs"
                :disabled="!logPolicy.enabled"
                @change="updateLogPolicy({ collectInputs: ($event.target as HTMLInputElement).checked })"
              />
              <span>记录节点输入</span>
            </label>
            <label class="checkbox">
              <input
                type="checkbox"
                :checked="logPolicy.collectOutputs"
                :disabled="!logPolicy.enabled"
                @change="updateLogPolicy({ collectOutputs: ($event.target as HTMLInputElement).checked })"
              />
              <span>记录节点输出</span>
            </label>
            <label class="checkbox">
              <input
                type="checkbox"
                :checked="logPolicy.collectContext"
                :disabled="!logPolicy.enabled"
                @change="updateLogPolicy({ collectContext: ($event.target as HTMLInputElement).checked })"
              />
              <span>记录上下文</span>
            </label>
            <label class="checkbox">
              <input
                type="checkbox"
                :checked="logPolicy.collectErrors"
                :disabled="!logPolicy.enabled"
                @change="updateLogPolicy({ collectErrors: ($event.target as HTMLInputElement).checked })"
              />
              <span>捕获异常</span>
            </label>
            <label class="checkbox">
              <input
                type="checkbox"
                :checked="logPolicy.collectDuration"
                :disabled="!logPolicy.enabled"
                @change="updateLogPolicy({ collectDuration: ($event.target as HTMLInputElement).checked })"
              />
              <span>记录耗时</span>
            </label>
          </div>

          <label class="field full">
            <span>脱敏字段（英文逗号）</span>
            <input
              class="input"
              type="text"
              :disabled="!logPolicy.enabled"
              placeholder="如 password,cardNo"
              :value="logPolicy.redactKeys"
              @input="updateLogPolicy({ redactKeys: ($event.target as HTMLInputElement).value })"
            />
          </label>
        </section>

        <section class="panel-section">
          <div class="section-title">REST 入口</div>
          <p class="section-desc">为当前流程绑定唯一一个 REST 接口入口，用于对外发布。</p>
          <label class="checkbox">
            <input
              type="checkbox"
              :checked="entrypointEnabled"
              @change="toggleEntrypoint(($event.target as HTMLInputElement).checked)"
            />
            <span>{{ entrypointEnabled ? "已启用入口" : "未启用入口" }}</span>
          </label>
          <div class="section-grid entrypoint-grid">
            <label class="field full">
              <span>路径</span>
              <input
                class="input readonly-input"
                type="text"
                placeholder="/api/users/{id}"
                readonly
                :value="entrypoint.path"
              />
              <span class="field-hint">支持路径变量，需确保在项目中唯一。</span>
            </label>
            <label class="field">
              <span>方法</span>
              <input
                class="input readonly-input"
                type="text"
                readonly
                :value="entrypoint.method || 'GET'"
              />
            </label>
          </div>
        </section>

        <section class="panel-section">
          <div class="section-title">REST 数据结构</div>
          <div class="schema-block">
            <div class="schema-heading">请求参数</div>
            <table v-if="hasRequestSchema" class="schema-table">
              <thead>
                <tr>
                  <th>参数名</th>
                  <th>类型</th>
                  <th>来源</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in requestSchemaFields" :key="item.name">
                  <td>{{ item.name || "-" }}</td>
                  <td>{{ item.type || "-" }}</td>
                  <td>{{ inferSourceLabel(item) }}</td>
                </tr>
              </tbody>
            </table>
            <div v-else class="schema-placeholder">未提供请求参数结构</div>
            <label class="field full">
              <span>请求 Schema（JSON）</span>
              <div ref="schemaEditorContainer" class="schema-editor" :class="{ disabled: !entrypointEnabled }"></div>
              <div class="schema-actions">
                <button type="button" class="ghost-btn" :disabled="!entrypointEnabled" @click="formatEntrypointSchema">
                  格式化
                </button>
                <span class="field-hint">Schema 会随流程发布一并提交，可按需调整。</span>
              </div>
            </label>
          </div>
          <div class="schema-block">
            <div class="schema-heading">响应类型</div>
            <div class="schema-response">
              {{ responseSchemaType || "void" }}
            </div>
          </div>
        </section>

        <footer class="panel-footer">
          <div class="hint">修改会立即应用到当前流程草稿。</div>
          <button class="primary-btn" type="button" @click="closePanel">完成</button>
        </footer>
      </section>
    </div>
  </transition>
</template>

<style scoped>
.flow-settings-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  justify-content: flex-end;
}

.flow-settings-panel {
  width: min(480px, 100vw);
  background: #fff;
  height: 100%;
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  box-shadow: -8px 0 30px rgba(15, 23, 42, 0.2);
  overflow-y: auto;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.panel-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

.panel-section {
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 16px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #fdfefe;
}

.section-title {
  font-weight: 600;
  color: #1f2937;
}

.section-desc {
  font-size: 12px;
  color: #64748b;
  margin: -6px 0 0;
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.section-grid .full {
  grid-column: span 2;
}

.log-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.log-grid .field.full {
  grid-column: span 3;
}

.entrypoint-grid {
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
}

.entrypoint-grid .field.full {
  grid-column: 1 / -1;
}

.collect-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 8px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  color: #1f2937;
}

.field-hint {
  font-size: 11px;
  color: #94a3b8;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.textarea {
  resize: vertical;
}

.toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #475569;
}

.checkbox {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #1f2937;
}

.replace-flag {
  grid-column: 1 / -1;
}

.schema-block {
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 12px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.schema-heading {
  font-size: 12px;
  font-weight: 600;
  color: #0f172a;
}

.schema-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.schema-table th,
.schema-table td {
  border: 1px solid rgba(226, 232, 240, 0.8);
  padding: 6px 8px;
  text-align: left;
}

.schema-table th {
  background: #f8fafc;
  font-weight: 600;
  color: #475569;
}

.schema-placeholder {
  font-size: 12px;
  color: #94a3b8;
}

.schema-editor {
  height: 220px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  border-radius: 8px;
  overflow: hidden;
}

.schema-editor.disabled {
  pointer-events: none;
  opacity: 0.6;
}

.schema-actions {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.schema-response {
  font-size: 12px;
  color: #2563eb;
  font-weight: 600;
  padding: 6px 8px;
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 8px;
  background: #f8fafc;
  word-break: break-all;
}

.readonly-input {
  background: #f8fafc;
  color: #94a3b8;
  cursor: not-allowed;
}

.ghost-btn {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 999px;
  padding: 6px 16px;
  background: transparent;
  cursor: pointer;
  font-size: 12px;
  color: #0f172a;
}

.ghost-btn:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.primary-btn {
  border: none;
  padding: 8px 18px;
  border-radius: 999px;
  background: #2563eb;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hint {
  font-size: 12px;
  color: #94a3b8;
}

.flow-settings-fade-enter-active,
.flow-settings-fade-leave-active {
  transition: opacity 0.2s ease;
}

.flow-settings-fade-enter-from,
.flow-settings-fade-leave-to {
  opacity: 0;
}
</style>










