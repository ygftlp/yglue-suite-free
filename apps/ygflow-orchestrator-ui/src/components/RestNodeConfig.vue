<script setup lang="ts">
import { computed } from "vue"

type KvBinding = {
  key: string
  value: string
}

const props = defineProps<{
  nodeData?: Record<string, any> | null
}>()

const emit = defineEmits<{
  (event: "update:nodeData", value: Record<string, any>): void
}>()

const methodOptions = ["GET", "POST", "PUT", "PATCH", "DELETE"]

const normalized = computed(() => {
  const raw = props.nodeData || {}
  return {
    label: String(raw.label || "HTTP 调用"),
    method: String(raw.method || "GET").toUpperCase(),
    url: String(raw.url || ""),
    headers: normalizeKvList(raw.headers),
    query: normalizeKvList(raw.query),
    body: raw.body == null ? "" : typeof raw.body === "string" ? raw.body : JSON.stringify(raw.body, null, 2),
    timeoutSeconds: normalizeNumber(raw.timeoutSeconds, 10),
    retryCount: normalizeNumber(raw.retryCount, 0),
    retryBackoffMs: normalizeNumber(raw.retryBackoffMs, 0),
    retryOnStatuses: normalizeStatusText(raw.retryOnStatuses),
    as: String(raw.as || ""),
  }
})

const allowBody = computed(() => {
  const method = normalized.value.method
  return method !== "GET" && method !== "DELETE"
})

function updateNodeData(partial: Record<string, any>) {
  emit("update:nodeData", { ...(props.nodeData || {}), ...partial })
}

function normalizeKvList(raw: unknown): KvBinding[] {
  if (Array.isArray(raw)) {
    const list = raw
      .map((item) => ({
        key: String((item as any)?.key || ""),
        value: String((item as any)?.value || ""),
      }))
      .filter((item) => item.key || item.value)
    return list.length ? list : [{ key: "", value: "" }]
  }
  if (raw && typeof raw === "object") {
    const list = Object.entries(raw as Record<string, any>).map(([key, value]) => ({
      key,
      value: value == null ? "" : String(value),
    }))
    return list.length ? list : [{ key: "", value: "" }]
  }
  return [{ key: "", value: "" }]
}

function normalizeNumber(raw: unknown, fallback: number): number {
  const n = Number(raw)
  return Number.isFinite(n) && n >= 0 ? n : fallback
}

function normalizeStatusText(raw: unknown): string {
  if (Array.isArray(raw)) {
    return raw.map((item) => String(item)).filter(Boolean).join(",")
  }
  if (raw == null) return "429,500,502,503,504"
  return String(raw)
}

function onMethodChange(value: string) {
  const method = String(value || "GET").toUpperCase()
  if (method === "GET" || method === "DELETE") {
    updateNodeData({ method, body: "" })
    return
  }
  updateNodeData({ method })
}

function updateKv(listKey: "headers" | "query", index: number, field: "key" | "value", value: string) {
  const next = [...normalized.value[listKey]]
  if (!next[index]) next[index] = { key: "", value: "" }
  next[index] = { ...next[index], [field]: value }
  updateNodeData({ [listKey]: next })
}

function addKv(listKey: "headers" | "query") {
  updateNodeData({
    [listKey]: [...normalized.value[listKey], { key: "", value: "" }],
  })
}

function removeKv(listKey: "headers" | "query", index: number) {
  const next = [...normalized.value[listKey]]
  if (next.length <= 1) {
    updateNodeData({ [listKey]: [{ key: "", value: "" }] })
    return
  }
  next.splice(index, 1)
  updateNodeData({ [listKey]: next })
}

function onRetryStatusInput(value: string) {
  const parsed = value
    .split(",")
    .map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0)
  updateNodeData({ retryOnStatuses: parsed })
}
</script>

<template>
  <div class="rest-node-config">
    <section class="section">
      <div class="muted section-title">显示名称</div>
      <input
        class="input"
        :value="normalized.label"
        @input="updateNodeData({ label: ($event.target as HTMLInputElement).value })"
      />
    </section>

    <section class="section">
      <div class="muted section-title">HTTP 请求配置</div>
      <div class="card stack">
        <label class="field">
          <span>请求方法</span>
          <select class="input" :value="normalized.method" @change="onMethodChange(($event.target as HTMLSelectElement).value)">
            <option v-for="method in methodOptions" :key="method" :value="method">{{ method }}</option>
          </select>
        </label>

        <label class="field">
          <span>URL</span>
          <input
            class="input"
            :value="normalized.url"
            placeholder="例如 https://api.example.com/users/{id}"
            @input="updateNodeData({ url: ($event.target as HTMLInputElement).value })"
          />
          <span class="muted tiny">支持表达式，例如 `#{request.path.userId}`。</span>
        </label>
      </div>
    </section>

    <section class="section">
      <div class="muted section-title">Query 参数</div>
      <div class="card stack">
        <div class="row row-head">
          <div class="muted tiny">按 key/value 配置，value 支持表达式。</div>
          <button class="btn mini" type="button" @click="addKv('query')">+ 添加</button>
        </div>
        <div v-for="(item, idx) in normalized.query" :key="`query-${idx}`" class="kv-row">
          <input
            class="input"
            :value="item.key"
            placeholder="参数名"
            @input="updateKv('query', idx, 'key', ($event.target as HTMLInputElement).value)"
          />
          <input
            class="input"
            :value="item.value"
            placeholder="参数值 / 表达式"
            @input="updateKv('query', idx, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button class="btn mini danger" type="button" @click="removeKv('query', idx)">删除</button>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="muted section-title">请求头</div>
      <div class="card stack">
        <div class="row row-head">
          <div class="muted tiny">按 key/value 配置，value 支持表达式。</div>
          <button class="btn mini" type="button" @click="addKv('headers')">+ 添加</button>
        </div>
        <div v-for="(item, idx) in normalized.headers" :key="`header-${idx}`" class="kv-row">
          <input
            class="input"
            :value="item.key"
            placeholder="Header 名称"
            @input="updateKv('headers', idx, 'key', ($event.target as HTMLInputElement).value)"
          />
          <input
            class="input"
            :value="item.value"
            placeholder="Header 值 / 表达式"
            @input="updateKv('headers', idx, 'value', ($event.target as HTMLInputElement).value)"
          />
          <button class="btn mini danger" type="button" @click="removeKv('headers', idx)">删除</button>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="muted section-title">请求体</div>
      <div v-if="allowBody" class="card stack">
        <label class="field">
          <span>Body（JSON 或字符串）</span>
          <textarea
            class="input input-textarea"
            :value="normalized.body"
            placeholder='{"id":"#{request.path.userId}"}'
            @input="updateNodeData({ body: ($event.target as HTMLTextAreaElement).value })"
          />
        </label>
      </div>
      <div v-else class="muted tiny">当前方法 {{ normalized.method }} 不发送 Body。</div>
    </section>

    <section class="section">
      <div class="muted section-title">执行参数</div>
      <div class="card stack">
        <div class="grid three-col">
          <label class="field">
            <span>超时（秒）</span>
            <input
              class="input"
              type="number"
              min="1"
              :value="normalized.timeoutSeconds"
              @input="updateNodeData({ timeoutSeconds: normalizeNumber(($event.target as HTMLInputElement).value, 10) })"
            />
          </label>
          <label class="field">
            <span>重试次数</span>
            <input
              class="input"
              type="number"
              min="0"
              :value="normalized.retryCount"
              @input="updateNodeData({ retryCount: normalizeNumber(($event.target as HTMLInputElement).value, 0) })"
            />
          </label>
          <label class="field">
            <span>重试退避（ms）</span>
            <input
              class="input"
              type="number"
              min="0"
              :value="normalized.retryBackoffMs"
              @input="updateNodeData({ retryBackoffMs: normalizeNumber(($event.target as HTMLInputElement).value, 0) })"
            />
          </label>
        </div>
        <label class="field">
          <span>重试状态码（csv）</span>
          <input
            class="input"
            :value="normalized.retryOnStatuses"
            placeholder="429,500,502,503,504"
            @input="onRetryStatusInput(($event.target as HTMLInputElement).value)"
          />
        </label>
      </div>
    </section>

    <section class="section">
      <div class="muted section-title">输出结果</div>
      <div class="card stack">
        <div class="type-pill">类型：HTTP 响应结果</div>
        <label class="field">
          <span>绑定到 ctx 的 key</span>
          <input
            class="input"
            :value="normalized.as"
            placeholder="例如 retHttpUser"
            @input="updateNodeData({ as: ($event.target as HTMLInputElement).value })"
          />
        </label>
      </div>
    </section>
  </div>
</template>

<style scoped>
.rest-node-config {
  display: flex;
  flex-direction: column;
}

.section {
  margin-top: 12px;
}

.section-title {
  margin-bottom: 6px;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.row-head {
  justify-content: space-between;
}

.grid {
  display: grid;
  gap: 10px;
}

.grid.three-col {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
  font-size: 12px;
  color: #1f2937;
}

.muted {
  color: #64748b;
  font-size: 12px;
}

.muted.tiny {
  font-size: 11px;
}

.card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  background: #fff;
}

.input {
  width: 100%;
  min-width: 0;
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 8px 10px;
  font-size: 13px;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.input-textarea {
  min-height: 110px;
  resize: vertical;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 12px;
}

.kv-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.btn {
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  border: 1px solid rgba(148, 163, 184, 0.55);
  background: #fff;
  color: #334155;
  padding: 4px 10px;
}

.btn.mini {
  font-size: 12px;
  padding: 4px 10px;
}

.btn.danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #dc2626;
}

.type-pill {
  font-size: 11px;
  color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
  border: 1px solid rgba(37, 99, 235, 0.2);
  border-radius: 999px;
  width: fit-content;
  padding: 2px 10px;
}

@media (max-width: 1100px) {
  .grid.three-col {
    grid-template-columns: 1fr;
  }

  .kv-row {
    grid-template-columns: 1fr;
  }
}
</style>
