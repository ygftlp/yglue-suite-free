<script setup lang="ts">
import { computed, ref } from "vue"
import TransformerEditor from "./TransformerEditor.vue"
import ScriptEditor from "./ScriptEditor.vue"
import { transactionManagers } from "../data/transactionManagers"
import type { FlowModel, FlowResolver } from "../api/client"

type IOType = "inputs"

const props = defineProps<{
  selectedNode: any | null
  selectedEdge: any | null
  nodes?: any[] | null
  edges?: any[] | null
  endpointSchema?: {
    requestSchema?: Array<{ name: string; type: string; source?: string; pathVariable?: string; paramName?: string; formField?: string }> | null
    responseSchema?: { type?: string | null } | null
  } | null
  entrypointPath?: string | null
  flowModels?: FlowModel[] | null
  flowResolvers?: FlowResolver[] | null
}>()

const emit = defineEmits<{
  (e: "update-node", node: any): void
  (e: "update-edge", edge: any): void
}>()

const modelOptions = computed(() =>
  (props.flowModels ?? []).map((model) => ({
    label: model.name ?? model.identifier,
    value: model.className || model.identifier,
  }))
)

const resolverCatalog = computed(() => props.flowResolvers ?? [])

const isBranchNode = computed(() => props.selectedNode?.type === "branch" || props.selectedNode?.data?.branch)
const transactionVariant = computed(() => props.selectedNode?.data?.transaction as "begin" | "end" | undefined)
const isTransactionNode = computed(() => Boolean(transactionVariant.value))
const isTransformerNode = computed(() => props.selectedNode?.type === "transformer")
const allowCustomIO = computed(() => !props.selectedNode?.data?.comp)
const hasSelection = computed(() => Boolean(props.selectedNode || props.selectedEdge))
const outputInfo = computed(() => props.selectedNode?.data?.output || null)

// 脚本编辑器 refs
const scriptEditorRefs = ref<Array<{ openCodeEditor: () => void } | null>>([])

// 已移除条件分支连线功能

function cloneNode() {
  return JSON.parse(JSON.stringify(props.selectedNode))
}

function cloneEdge() {
  return JSON.parse(JSON.stringify(props.selectedEdge))
}

function ensureArray(next: any, key: IOType) {
  next.data ||= {}
  next.data[key] ||= []
}

// 已移除条件分支连线功能

function mutateNode(updater: (next: any) => void) {
  if (!props.selectedNode) return
  const next = cloneNode()
  updater(next)
  emit("update-node", next)
}

function mutateEdge(updater: (next: any) => void) {
  if (!props.selectedEdge) return
  const next = cloneEdge()
  updater(next)
  const display = next.data?.label || ""
  next.label = display
  emit("update-edge", next)
}

function updateNodeField(key: string, value: any) {
  mutateNode((next) => {
    next.data ||= {}
    next.data[key] = value
  })
}

function updateCompField(partial: Record<string, any>) {
  mutateNode((next) => {
    next.data ||= {}
    next.data.comp = { ...(next.data.comp || {}), ...partial }
  })
}

function addIO(type: IOType) {
  mutateNode((next) => {
    ensureArray(next, type)
    const defaults = {
      name: "",
      valueType: "STRING",
      typeName: "",
      transformer: "",
      resolver: { type: "request", path: "", cast: "STRING", default: "" },
      converter: {
        kind: "GENERAL",
        targetType: "STRING",
        targetTypeName: "",
        script: "",
        arrayElementType: "STRING",
        arrayElementTypeName: "",
      },
    }
    next.data[type].push(defaults)
  })
}

function updateIO(type: IOType, index: number, partial: Record<string, any>) {
  mutateNode((next) => {
    ensureArray(next, type)
    const list = next.data[type]
    list[index] = { ...(list[index] || {}), ...partial }
  })
}

function removeIO(type: IOType, index: number) {
  mutateNode((next) => {
    ensureArray(next, type)
    next.data[type].splice(index, 1)
  })
}

function getInputScript(input: any): string {
  if (!input) return ""
  // 优先使用 script，如果没有则使用 transformer（兼容旧数据）
  return input.script || input.transformer || ""
}

function updateInputScript(index: number, script: string) {
  updateIO("inputs", index, { script, transformer: script })
}

function getInputScriptVariableGroups(input: any) {
  const groups = [
    {
      title: "请求参数",
      items: [
        { label: "request.path.xxx", snippet: "request.path.xxx", description: "路径变量，如 request.path.projectKey" },
        { label: "request.query.xxx", snippet: "request.query.xxx", description: "查询参数，如 request.query.page" },
        { label: "request.body.xxx", snippet: "request.body.xxx", description: "请求体字段，如 request.body.name" },
        { label: "request.headers.xxx", snippet: "request.headers.xxx", description: "请求头，如 request.headers.Authorization" },
      ],
    },
    {
      title: "流程上下文",
      items: [
        { label: "ctx", snippet: "ctx", description: "流程上下文，可读写共享变量，如 ctx['userId']" },
        { label: "ctx['_lastNodeResult']", snippet: "ctx['_lastNodeResult']", description: "最后一个节点的输出结果" },
        { label: "ctx['_node_xxx']", snippet: "ctx['_node_xxx']", description: "指定节点ID的输出结果" },
      ],
    },
  ]
  return groups
}

function getInputScriptFunctionGroups() {
  return [
    {
      title: "内置函数",
      items: [
        { label: "jsonPath(value, path)", snippet: "jsonPath(request.body, \"$.data.field\")", description: "按 JSONPath 提取字段，适合 JSON 结构快速取值。" },
        { label: "assert(condition, message)", snippet: "assert(request.path.projectKey != null, \"项目标识不能为空\")", description: "当条件不满足时抛出异常，中断后续执行。" },
        { label: "formatDate(value, pattern)", snippet: "formatDate(request.body.orderTime, \"yyyy-MM-dd HH:mm:ss\")", description: "格式化日期/时间对象为指定字符串。" },
        { label: "safeNumber(value, defaultValue)", snippet: "safeNumber(request.query.page, 1)", description: "安全转换为数字，无法转换时给定默认值。" },
      ],
    },
  ]
}

/**
 * 格式化输出类型显示
 * 优先显示具体的Java类型（typeName），如果没有则显示通用类型
 */
function formatOutputType(output: any) {
  const type = (output?.valueType || "OBJECT").toUpperCase()
  // 如果有具体的Java类型名称，优先显示
  if (output?.typeName) {
    return output.typeName
  }
  // 对于OBJECT和ARRAY类型，如果没有typeName，显示通用类型
  if (type === "OBJECT" || type === "ARRAY") {
    return type
  }
  // 基础类型直接返回
  return type
}

function updateOutputField(partial: Record<string, any>) {
  mutateNode((next) => {
    next.data ||= {}
    next.data.output = { ...(next.data.output || {}), ...partial }
  })
}

function isObjectOutput(output: any) {
  return (output?.valueType || "").toUpperCase() === "OBJECT"
}

function formatInputType(input: any) {
  const type = (input?.valueType || "STRING").toUpperCase()
  if (type === "OBJECT" || type === "ARRAY") {
    return input?.typeName || type
  }
  return type
}

function updateInputType(index: number, value: string) {
  updateIO("inputs", index, { valueType: value })
}

function updateInputTypeName(index: number, value: string) {
  updateIO("inputs", index, { typeName: value })
}


function updateEdgeField(partial: Record<string, any>) {
  mutateEdge((next) => {
    next.data = { ...(next.data || {}), ...partial }
  })
}

/**
 * 获取 FlowApi 的 Service name（bean 名称）
 * bean 名称的优先级：
 * 1. 从 Spring 注解（@Service、@Component）的 value 属性获取
 * 2. 从 @FlowApi 注解的 value 属性获取
 * 3. 从 @FlowApi 注解的 name 属性获取
 * 4. 默认使用类名首字母小写
 * 
 * 对于 FLOW_API 类型：从 configJson 中解析 beanName 字段
 * 对于 FLOW_OPERATION 类型：从 configJson 中解析 flowApiBeanName 字段（优先）或 flowApiName 字段
 */
function getServiceName(comp: any): string | null {
  if (!comp) return null
  
  const endpointType = comp.endpointType
  
  // 如果是 FLOW_API 类型，从 configJson 中解析 beanName
  if (endpointType === "FLOW_API") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        // 优先使用 beanName（从 Spring 注解获取）
        if (config.beanName && typeof config.beanName === "string") {
          return config.beanName
        }
        // 回退到使用 name
        if (config.name && typeof config.name === "string") {
          return config.name
        }
      }
    } catch {
      // 忽略解析错误
    }
  }
  
  // 如果是 FLOW_OPERATION 类型，从 configJson 中解析 flowApiBeanName
  // 后端已经在 FlowOperation 的 configJson 中添加了 flowApiBeanName 字段（从 Spring 注解获取）
  if (endpointType === "FLOW_OPERATION") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        // 优先使用 flowApiBeanName（从 @Service/@Component 获取的 bean 名称）
        if (config.flowApiBeanName && typeof config.flowApiBeanName === "string") {
          return config.flowApiBeanName
        }
        // 回退到使用 flowApiName
        if (config.flowApiName && typeof config.flowApiName === "string") {
          return config.flowApiName
        }
      }
    } catch {
      // 忽略解析错误
    }
  }
  
  // 回退到使用 bean 字段
  // 注意：对于 FlowOperation，bean 字段可能不是 flowApi 的 Service name
  // 但如果没有其他方式获取，暂时使用 bean 作为显示
  return comp.bean || null
}
</script>

<template>
  <div v-if="!hasSelection" class="center">请选择节点或连线查看配置</div>
  <div v-else class="col" style="height:100%">
    <div class="bar">
      <div style="font-weight:600; font-size:13px">{{ selectedEdge ? "连线配置" : "节点配置" }}</div>
    </div>
    <div style="padding:12px" class="col">
      <template v-if="selectedEdge">
      <div>
        <div class="muted">线条标签</div>
        <input
          class="input"
          placeholder="示例：成功 / 失败"
          :value="selectedEdge.data?.label || ''"
          @input="updateEdgeField({ label: ($event.target as HTMLInputElement).value })"
        />
      </div>
      <div class="tip" style="margin-top: 8px;">
        提示：线条标签仅用于显示，不影响流程执行逻辑。
      </div>
    </template>

    <template v-else-if="isTransformerNode">
      <TransformerEditor
        :selected-node="selectedNode"
        :nodes="props.nodes"
        :edges="props.edges"
        :endpoint-schema="props.endpointSchema"
        :entrypoint-path="props.entrypointPath"
        :flow-models="props.flowModels ?? []"
        :flow-resolvers="resolverCatalog"
        @update-node="emit('update-node', $event)"
      />
      <!-- 脚本节点不需要显示通用的输入参数和输出结果配置 -->
      <!-- 脚本节点的输入来自上游节点（自动），输出由脚本生成 -->
    </template>
    <template v-else-if="isBranchNode">
      <div class="tip">
        条件分支节点默认作为"无条件"出口，你可以在右键连线时补充条件表达式，也可以保持为空（即未命中则走该分支）。
      </div>
    </template>
    <template v-else-if="isTransactionNode">
      <div class="tip">
        {{ transactionVariant === "end" ? "事务结束：用于提交或回滚前面事务范围内的节点。" : "事务开始：从此节点之后的服务共享同一事务上下文。" }}
      </div>
      <div v-if="transactionVariant === 'begin'">
        <div class="muted">事务管理器</div>
        <select
          class="input"
          :value="props.selectedNode?.data?.txManager || ''"
          @change="updateNodeField('txManager', ($event.target as HTMLSelectElement).value)"
        >
          <option value="">默认</option>
          <option v-for="mgr in transactionManagers" :key="mgr.value" :value="mgr.value">
            {{ mgr.label }}
          </option>
        </select>
      </div>
    </template>

      <template v-else-if="selectedNode && !isTransformerNode">
        <div>
          <div class="muted">显示名称</div>
        <input
          class="input"
          :class="{ locked: Boolean(selectedNode.data?.comp) }"
          :readonly="Boolean(selectedNode.data?.comp)"
          :value="selectedNode.data?.label || selectedNode.label || ''"
          @input="!selectedNode.data?.comp && updateNodeField('label', ($event.target as HTMLInputElement).value)"
        />
      </div>

      <div v-if="selectedNode.data?.comp" style="margin-top:12px">
        <div class="muted" style="margin-bottom:6px">组件绑定</div>
        <div class="card" style="padding:10px; display:flex; flex-direction:column; gap:8px">
          <div>
            <div class="muted" style="font-size:11px; margin-bottom:4px">bean 名称（Service name）</div>
            <div style="font-size:13px; font-weight:500; color:#1f2937">
              {{ getServiceName(selectedNode.data?.comp) || selectedNode.data?.comp?.bean || '-' }}
            </div>
          </div>
          <div>
            <div class="muted" style="font-size:11px; margin-bottom:4px">method 名称</div>
            <div style="font-size:13px; font-weight:500; color:#1f2937">
              {{ selectedNode.data?.comp?.method || '-' }}
            </div>
          </div>
          <div v-if="selectedNode.data?.comp?.version">
            <div class="muted" style="font-size:11px; margin-bottom:4px">版本</div>
            <div style="font-size:13px; font-weight:500; color:#1f2937">
              {{ selectedNode.data?.comp?.version }}
            </div>
          </div>
        </div>
      </div>

        <div class="col" style="gap:8px; margin-top:12px">
          <div class="row" style="justify-content:space-between; align-items:center">
            <div class="muted">输入参数</div>
            <button
              v-if="allowCustomIO"
              class="btn"
              style="font-size:12px"
              @click="addIO('inputs')"
            >
              新增输入
            </button>
            <span v-else class="muted" style="font-size:12px">来自组件方法，无法增删</span>
          </div>
          <div v-if="(selectedNode.data?.inputs || []).length === 0" class="muted" style="font-size:12px">暂无输入参数</div>
          <div
            v-for="(input, index) in selectedNode.data?.inputs || []"
            :key="index"
            class="card input-block"
          >
            <div class="input-header">
              <div class="input-main-info">
                <div class="input-label">
                  <div class="muted small">参数</div>
                  <template v-if="allowCustomIO">
                    <input
                      class="input"
                      :value="input.name || ''"
                      @input="updateIO('inputs', index, { name: ($event.target as HTMLInputElement).value })"
                    />
                  </template>
                  <span v-else class="pill-text">{{ input.name }}</span>
                </div>
                <div class="input-label">
                  <div class="muted small">类型</div>
                  <template v-if="allowCustomIO">
                    <div class="type-input-group">
                      <select
                        class="input"
                        :value="input.valueType || 'STRING'"
                        @change="updateInputType(index, ($event.target as HTMLSelectElement).value)"
                      >
                        <option value="STRING">String</option>
                        <option value="NUMBER">Number</option>
                        <option value="BOOLEAN">Boolean</option>
                        <option value="OBJECT">Object</option>
                        <option value="ARRAY">Array</option>
                      </select>
                      <input
                        v-if="['OBJECT','ARRAY'].includes((input.valueType || '').toUpperCase())"
                        class="input type-name-input"
                        placeholder="类型名（如 com.example.User）"
                        :value="input.typeName || ''"
                        @input="updateInputTypeName(index, ($event.target as HTMLInputElement).value)"
                      />
                    </div>
                  </template>
                  <div v-else class="type-display">
                    <span class="pill-text type-text">{{ formatInputType(input) }}</span>
                  </div>
                  <!-- 脚本值显示 -->
                  <div class="script-value-preview">
                    <div class="muted small" style="margin-bottom: 4px;">取值</div>
                    <textarea
                      class="input script-value-input"
                      :value="getInputScript(input)"
                      placeholder="点击此处打开脚本编辑器..."
                      readonly
                      @click="scriptEditorRefs[index]?.openCodeEditor()"
                    ></textarea>
                  </div>
                </div>
              </div>
              <div class="input-actions">
                <button
                  v-if="allowCustomIO"
                  class="btn"
                  style="font-size:12px"
                  @click="removeIO('inputs', index)"
                >
                  删除
                </button>
              </div>
            </div>
            <!-- 脚本编辑器（隐藏预览，只显示弹框） -->
            <ScriptEditor
              :ref="(el) => { scriptEditorRefs[index] = el as any }"
              :script="getInputScript(input)"
              title="参数取值脚本编辑器"
              :variable-groups="getInputScriptVariableGroups(input)"
              :function-groups="getInputScriptFunctionGroups()"
              :hide-preview="true"
              @update:script="(script) => updateInputScript(index, script)"
            />
          </div>
        </div>

        <div class="col" style="gap:8px; margin-top:12px">
          <div class="row" style="justify-content:space-between; align-items:center">
            <div class="muted">输出结果</div>
          </div>
          <div v-if="!outputInfo" class="muted" style="font-size:12px">暂无返回值</div>
          <div
            v-else
            class="card"
            style="padding:10px; display:flex; flex-direction:column; gap:8px"
          >
            <div v-if="outputInfo.description" class="input-hint">说明：{{ outputInfo.description }}</div>
            <div class="type-pill">类型：{{ formatOutputType(outputInfo) }}</div>
            <div v-if="isObjectOutput(outputInfo)" class="field-table">
              <div class="field-row header">
                <span>字段</span>
                <span>类型</span>
                <span>说明</span>
              </div>
              <div
                v-for="(field, idx) in outputInfo.fields || []"
                :key="idx"
                class="field-row"
              >
                <span>{{ field.name }}</span>
                <span>{{ field.type }}</span>
                <span>{{ field.description || '-' }}</span>
              </div>
              <div v-if="(outputInfo.fields || []).length === 0" class="muted" style="font-size:12px">暂无字段</div>
            </div>
            <div v-else class="muted" style="font-size:12px">基础类型：{{ formatOutputType(outputInfo) }}</div>
            <div class="binding-field">
              <div class="muted" style="font-size:11px">绑定到 ctx 的 key</div>
              <input
                class="input"
                placeholder="默认 retxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                :value="outputInfo.contextKey || ''"
                @input="updateOutputField({ contextKey: ($event.target as HTMLInputElement).value })"
              />
            </div>
          </div>
        </div>

        <div style="margin-top:12px">
          <div class="muted">执行参数</div>
          <div class="row" style="flex-wrap:wrap; gap:8px">
            <input
              class="input"
              style="flex:1 1 140px"
              placeholder="重试次数"
              :value="selectedNode.data?.retry || ''"
              @input="updateNodeField('retry', ($event.target as HTMLInputElement).value)"
            />
            <input
              class="input"
              style="flex:1 1 140px"
              placeholder="超时（ms）"
              :value="selectedNode.data?.timeout || ''"
              @input="updateNodeField('timeout', ($event.target as HTMLInputElement).value)"
            />
            <select
              class="input"
              style="flex:1 1 140px"
              :value="selectedNode.data?.isolation || 'SERIAL'"
              @change="updateNodeField('isolation', ($event.target as HTMLSelectElement).value)"
            >
              <option value="SERIAL">串行</option>
              <option value="PARALLEL">并行</option>
            </select>
          </div>
        </div>
        <div>
          <div class="muted">事务模式</div>
          <select
            class="input"
            :value="selectedNode.data?.txMode || 'NONE'"
            @change="updateNodeField('txMode', ($event.target as HTMLSelectElement).value)"
          >
            <option value="NONE">无</option>
            <option value="SPRING">Spring</option>
            <option value="SEATA">Seata</option>
            <option value="SAGA">Saga</option>
          </select>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.tip {
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border: 1px dashed rgba(148, 163, 184, 0.6);
  border-radius: 10px;
  padding: 10px;
  line-height: 1.4;
}

/* 已移除条件分支连线相关样式 */

.input-hint {
  font-size: 11px;
  color: #94a3b8;
}

.input.locked {
  background: #f3f4f6;
  color: #6b7280;
  cursor: not-allowed;
}

:deep(.resolver) {
  flex: 1;
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

.binding-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.input-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-header {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.input-main-info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.input-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
  margin-top: 4px;
}

.type-input-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}

.type-input-group .input {
  flex: 0 0 auto;
}

.type-input-group select {
  min-width: 120px;
  max-width: 200px;
}

.type-input-group .type-name-input {
  flex: 1;
  min-width: 200px;
  max-width: 100%;
}

.type-display {
  display: flex;
  align-items: center;
  min-height: 32px;
}

.type-text {
  display: inline-block;
  max-width: 100%;
  word-break: break-word;
  white-space: normal;
  line-height: 1.4;
}

.script-value-preview {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.script-value-input {
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 11px;
  min-height: 60px;
  max-height: 120px;
  resize: vertical;
  cursor: pointer;
  background: #f8fafc;
  color: #475569;
}

.script-value-input:hover {
  background: #f1f5f9;
  border-color: rgba(148, 163, 184, 0.5);
}

.script-value-input:focus {
  outline: none;
  border-color: #2563eb;
  background: #fff;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.btn-icon {
  background: transparent;
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.btn-icon:hover {
  background: #f8fafc;
  border-color: rgba(148, 163, 184, 0.6);
}

.icon-expand {
  font-size: 10px;
  color: #64748b;
  transition: transform 0.2s ease;
  display: inline-block;
}

.icon-expand.expanded {
  transform: rotate(180deg);
}



.input-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.pill-text {
  padding: 4px 10px;
  background: rgba(226, 232, 240, 0.6);
  border-radius: 999px;
  font-size: 12px;
}

.resolver-label {
  font-size: 11px;
  color: #94a3b8;
}

.field-table {
  border: 1px solid rgba(148, 163, 184, 0.4);
  border-radius: 8px;
  overflow: hidden;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 2fr;
  padding: 6px 8px;
  font-size: 11px;
  align-items: center;
  gap: 6px;
}

.field-row:nth-child(even) {
  background: rgba(226, 232, 240, 0.4);
}

.field-row.header {
  font-weight: 600;
  background: rgba(226, 232, 240, 0.8);
}
</style>
