<script setup lang="ts">
import { computed, ref } from "vue"
import ScriptEditor from "./ScriptEditor.vue"
import ValidationEditor from "./ValidationEditor.vue"
import TransactionConfig from "./TransactionConfig.vue"

/**
 * 校验规则类型
 */
type ValidatorType = 
  | "required" 
  | "notEmpty" 
  | "notBlank" 
  | "type" 
  | "range" 
  | "length" 
  | "regex" 
  | "expression" 
  | "custom"

/**
 * 校验规则
 */
interface ValidationRule {
  id: string
  type: ValidatorType
  enabled: boolean
  message?: string
  config?: Record<string, any>
}

const props = defineProps<{
  label?: string
  comp?: any
  inputs?: any[]
  output?: any
  retry?: string | number
  timeout?: string | number
  isolation?: string
  txMode?: string
  transactionManager?: string
  projectKey?: string
  endpointId?: number
}>()

const emit = defineEmits<{
  (event: "update:label", value: string): void
  (event: "update:comp", value: any): void
  (event: "update:inputs", value: any[]): void
  (event: "update:output", value: any): void
  (event: "update:retry", value: string): void
  (event: "update:timeout", value: string): void
  (event: "update:isolation", value: string): void
  (event: "update:txMode", value: string): void
  (event: "update:transactionManager", value: string): void
}>()

const allowCustomIO = computed(() => !props.comp)

// 脚本编辑器 refs
const scriptEditorRefs = ref<Array<{ openCodeEditor: () => void } | null>>([])

// 校验器编辑器 refs
const validationEditorRefs = ref<Array<{ open: () => void } | null>>([])

// 当前编辑的校验器索引和规则
const editingInputIndex = ref<number | null>(null)
const editingValidatorIndex = ref<number | null>(null)
const editingValidatorRule = ref<ValidationRule | null>(null)

function addInput() {
  const newInputs = [...(props.inputs || []), {
    name: "",
    valueType: "STRING",
    typeName: "",
    script: "",
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
  }]
  emit("update:inputs", newInputs)
}

function updateInput(index: number, partial: Record<string, any>) {
  const newInputs = [...(props.inputs || [])]
  newInputs[index] = { ...(newInputs[index] || {}), ...partial }
  emit("update:inputs", newInputs)
}

function removeInput(index: number) {
  const newInputs = [...(props.inputs || [])]
  newInputs.splice(index, 1)
  emit("update:inputs", newInputs)
}

function getInputScript(input: any): string {
  if (!input) return ""
  return input.script || input.transformer || ""
}

function updateInputScript(index: number, script: string) {
  updateInput(index, { script, transformer: script })
}

function getInputValidators(input: any): ValidationRule[] {
  if (!input || !input.validators) return []
  return Array.isArray(input.validators) ? input.validators : []
}

function addValidator(inputIndex: number) {
  const newRule: ValidationRule = {
    id: `validator-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    type: "required",
    enabled: true,
  }
  
  const newInputs = [...(props.inputs || [])]
  const input = newInputs[inputIndex]
  if (!input.validators) {
    input.validators = []
  }
  input.validators.push(newRule)
  emit("update:inputs", newInputs)
}

function removeValidator(inputIndex: number, validatorIndex: number) {
  const newInputs = [...(props.inputs || [])]
  const input = newInputs[inputIndex]
  if (input.validators) {
    input.validators.splice(validatorIndex, 1)
  }
  emit("update:inputs", newInputs)
}

function editValidator(inputIndex: number, validatorIndex: number) {
  const input = props.inputs?.[inputIndex]
  if (!input) return
  
  const validators = getInputValidators(input)
  const rule = validators[validatorIndex]
  if (!rule) return
  
  editingInputIndex.value = inputIndex
  editingValidatorIndex.value = validatorIndex
  editingValidatorRule.value = { ...rule }
  
  const editorRef = validationEditorRefs.value[inputIndex]
  if (editorRef) {
    editorRef.open()
  }
}

function saveValidator(updatedRule: ValidationRule) {
  if (editingInputIndex.value === null) return
  
  const newInputs = [...(props.inputs || [])]
  const input = newInputs[editingInputIndex.value]
  if (!input.validators) {
    input.validators = []
  }
  
  const validatorIndex = editingValidatorIndex.value
  if (validatorIndex !== null && validatorIndex >= 0 && validatorIndex < input.validators.length) {
    input.validators[validatorIndex] = updatedRule
  }
  
  emit("update:inputs", newInputs)
  
  editingInputIndex.value = null
  editingValidatorIndex.value = null
  editingValidatorRule.value = null
}

function cancelEditValidator() {
  editingInputIndex.value = null
  editingValidatorIndex.value = null
  editingValidatorRule.value = null
}

function getValidatorTypeLabel(type: ValidatorType): string {
  const labels: Record<ValidatorType, string> = {
    required: "必填",
    notEmpty: "非空",
    notBlank: "非空白",
    type: "类型",
    range: "范围",
    length: "长度",
    regex: "正则",
    expression: "表达式",
    custom: "自定义",
  }
  return labels[type] || type
}

function updateInputType(index: number, value: string) {
  updateInput(index, { valueType: value })
}

function updateInputTypeName(index: number, value: string) {
  updateInput(index, { typeName: value })
}

function formatInputType(input: any) {
  const type = (input?.valueType || "STRING").toUpperCase()
  if (type === "OBJECT" || type === "ARRAY") {
    return input?.typeName || type
  }
  return type
}

function updateOutputField(partial: Record<string, any>) {
  emit("update:output", { ...(props.output || {}), ...partial })
}

function formatOutputType(output: any) {
  const type = (output?.valueType || "OBJECT").toUpperCase()
  if (output?.typeName) {
    return output.typeName
  }
  if (type === "OBJECT" || type === "ARRAY") {
    return type
  }
  return type
}

function isObjectOutput(output: any) {
  return (output?.valueType || "").toUpperCase() === "OBJECT"
}

function getServiceName(comp: any): string | null {
  if (!comp) return null
  
  const endpointType = comp.endpointType
  
  if (endpointType === "SERVICE") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        if (config.bean && typeof config.bean === "string") {
          return config.bean
        }
        if (config.name && typeof config.name === "string") {
          return config.name
        }
      }
    } catch {
      // ignore
    }
  }
  
  if (endpointType === "FLOW_OPERATION") {
    try {
      const configJson = comp.configJson
      if (typeof configJson === "string" && configJson) {
        const config = JSON.parse(configJson)
        if (config.serviceBean && typeof config.serviceBean === "string") {
          return config.serviceBean
        }
      }
    } catch {
      // ignore
    }
  }
  
  return comp.bean || null
}

function getInputScriptVariableGroups(input: any) {
  return [
    {
      title: "请求参数",
      items: [
        { label: "request.path.xxx", snippet: "request.path.xxx", description: "路径变量" },
        { label: "request.query.xxx", snippet: "request.query.xxx", description: "查询参数" },
        { label: "request.body.xxx", snippet: "request.body.xxx", description: "请求体字段" },
        { label: "request.headers.xxx", snippet: "request.headers.xxx", description: "请求头" },
      ],
    },
    {
      title: "流程上下文",
      items: [
        { label: "ctx", snippet: "ctx", description: "流程上下文" },
        { label: "ctx['_lastNodeResult']", snippet: "ctx['_lastNodeResult']", description: "最后节点输出" },
        { label: "ctx['_node_xxx']", snippet: "ctx['_node_xxx']", description: "指定节点输出" },
      ],
    },
  ]
}

function getInputScriptFunctionGroups() {
  return [
    {
      title: "内置函数",
      items: [
        { label: "jsonPath(value, path)", snippet: "jsonPath(request.body, \"$.data.field\")", description: "JSONPath提取" },
        { label: "assert(condition, message)", snippet: "assert(request.path.projectKey != null, \"项目标识不能为空\")", description: "断言" },
        { label: "formatDate(value, pattern)", snippet: "formatDate(request.body.orderTime, \"yyyy-MM-dd HH:mm:ss\")", description: "格式化日期" },
        { label: "safeNumber(value, defaultValue)", snippet: "safeNumber(request.query.page, 1)", description: "安全转数字" },
      ],
    },
  ]
}
</script>

<template>
  <div class="service-node-config">
    <!-- 显示名称 -->
    <div>
      <div class="muted">显示名称</div>
      <input
        class="input"
        :class="{ locked: Boolean(comp) }"
        :readonly="Boolean(comp)"
        :value="label || ''"
        @input="!comp && emit('update:label', ($event.target as HTMLInputElement).value)"
      />
    </div>

    <!-- 组件绑定信息 -->
    <div v-if="comp" style="margin-top:12px">
      <div class="muted" style="margin-bottom:6px">组件绑定</div>
      <div class="card" style="padding:10px; display:flex; flex-direction:column; gap:8px">
        <div>
          <div class="muted" style="font-size:11px; margin-bottom:4px">bean 名称</div>
          <div style="font-size:13px; font-weight:500; color:#1f2937">
            {{ getServiceName(comp) || comp.bean || '-' }}
          </div>
        </div>
        <div>
          <div class="muted" style="font-size:11px; margin-bottom:4px">method 名称</div>
          <div style="font-size:13px; font-weight:500; color:#1f2937">
            {{ comp.method || '-' }}
          </div>
        </div>
        <div v-if="comp.version">
          <div class="muted" style="font-size:11px; margin-bottom:4px">版本</div>
          <div style="font-size:13px; font-weight:500; color:#1f2937">
            {{ comp.version }}
          </div>
        </div>
      </div>
    </div>

    <!-- 输入参数 -->
    <div class="col" style="gap:8px; margin-top:12px">
      <div class="row" style="justify-content:space-between; align-items:center">
        <div class="muted">输入参数</div>
        <button
          v-if="allowCustomIO"
          class="btn"
          style="font-size:12px"
          @click="addInput"
        >
          新增输入
        </button>
        <span v-else class="muted" style="font-size:12px">来自组件方法，无法增删</span>
      </div>
      <div v-if="(inputs || []).length === 0" class="muted" style="font-size:12px">暂无输入参数</div>
      <div
        v-for="(input, index) in inputs || []"
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
                  @input="updateInput(index, { name: ($event.target as HTMLInputElement).value })"
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
            </div>
            <div class="input-label">
              <div class="muted small">值</div>
              <textarea
                class="input script-value-input"
                :value="getInputScript(input)"
                placeholder="点击此处打开脚本编辑器配置参数值..."
                readonly
                @click="scriptEditorRefs[index]?.openCodeEditor()"
              ></textarea>
            </div>
          </div>
          <div class="input-actions">
            <button
              v-if="allowCustomIO"
              class="btn"
              style="font-size:12px"
              @click="removeInput(index)"
            >
              删除
            </button>
          </div>
        </div>
        
        <!-- 脚本编辑器 -->
        <ScriptEditor
          :ref="(el) => { scriptEditorRefs[index] = el as any }"
          :script="getInputScript(input)"
          title="参数取值脚本编辑器"
          :variable-groups="getInputScriptVariableGroups(input)"
          :function-groups="getInputScriptFunctionGroups()"
          :hide-preview="true"
          :project-key="projectKey"
          :endpoint-id="endpointId"
          @update:script="(script) => updateInputScript(index, script)"
        />
        
        <!-- 校验器配置 -->
        <div style="margin-top: 12px; padding-top: 12px; border-top: 1px solid #e5e7eb">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
            <div class="muted small">校验器</div>
            <button
              class="btn"
              style="font-size: 11px; padding: 4px 8px"
              @click="addValidator(index)"
            >
              添加校验器
            </button>
          </div>
          
          <div v-if="getInputValidators(input).length === 0" class="muted" style="font-size: 11px">
            暂无校验器
          </div>
          
          <div
            v-for="(validator, validatorIndex) in getInputValidators(input)"
            :key="validator.id || validatorIndex"
            class="card"
            style="padding: 8px; margin-bottom: 6px; display: flex; justify-content: space-between; align-items: center"
          >
            <div style="flex: 1">
              <div style="display: flex; align-items: center; gap: 8px">
                <span style="font-size: 12px; font-weight: 500">{{ getValidatorTypeLabel(validator.type) }}</span>
                <span v-if="!validator.enabled" style="font-size: 11px; color: #9ca3af">（已禁用）</span>
              </div>
              <div v-if="validator.message" style="font-size: 11px; color: #6b7280; margin-top: 2px">
                {{ validator.message }}
              </div>
            </div>
            <div style="display: flex; gap: 4px">
              <button
                class="btn"
                style="font-size: 11px; padding: 4px 8px"
                @click="editValidator(index, validatorIndex)"
              >
                编辑
              </button>
              <button
                class="btn"
                style="font-size: 11px; padding: 4px 8px"
                @click="removeValidator(index, validatorIndex)"
              >
                删除
              </button>
            </div>
          </div>
          
          <!-- 校验器编辑器 -->
          <ValidationEditor
            :ref="(el) => { validationEditorRefs[index] = el as any }"
            :rule="editingValidatorRule"
            :value-type="input.valueType || 'STRING'"
            @save="saveValidator($event)"
            @cancel="cancelEditValidator"
          />
        </div>
      </div>
    </div>

    <!-- 输出结果 -->
    <div class="col" style="gap:8px; margin-top:12px">
      <div class="row" style="justify-content:space-between; align-items:center">
        <div class="muted">输出结果</div>
      </div>
      <div v-if="!output" class="muted" style="font-size:12px">暂无返回值</div>
      <div
        v-else
        class="card"
        style="padding:10px; display:flex; flex-direction:column; gap:8px"
      >
        <div v-if="output.description" class="input-hint">说明：{{ output.description }}</div>
        <div class="type-pill">类型：{{ formatOutputType(output) }}</div>
        <div v-if="isObjectOutput(output)" class="field-table">
          <div class="field-row header">
            <span>字段</span>
            <span>类型</span>
            <span>说明</span>
          </div>
          <div
            v-for="(field, idx) in output.fields || []"
            :key="idx"
            class="field-row"
          >
            <span>{{ field.name }}</span>
            <span>{{ field.type }}</span>
            <span>{{ field.description || '-' }}</span>
          </div>
          <div v-if="(output.fields || []).length === 0" class="muted" style="font-size:12px">暂无字段</div>
        </div>
        <div v-else class="muted" style="font-size:12px">基础类型：{{ formatOutputType(output) }}</div>
        <div class="binding-field">
          <div class="muted" style="font-size:11px">绑定到 ctx 的 key</div>
          <input
            class="input"
            placeholder="默认 retxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            :value="output.contextKey || ''"
            @input="updateOutputField({ contextKey: ($event.target as HTMLInputElement).value })"
          />
        </div>
      </div>
    </div>

    <!-- 执行参数 -->
    <div style="margin-top:12px">
      <div class="muted">执行参数</div>
      <div class="row" style="flex-wrap:wrap; gap:8px">
        <input
          class="input"
          style="flex:1 1 140px"
          placeholder="重试次数"
          :value="retry || ''"
          @input="emit('update:retry', ($event.target as HTMLInputElement).value)"
        />
        <input
          class="input"
          style="flex:1 1 140px"
          placeholder="超时（ms）"
          :value="timeout || ''"
          @input="emit('update:timeout', ($event.target as HTMLInputElement).value)"
        />
        <select
          class="input"
          style="flex:1 1 140px"
          :value="isolation || 'SERIAL'"
          @change="emit('update:isolation', ($event.target as HTMLSelectElement).value)"
        >
          <option value="SERIAL">串行</option>
          <option value="PARALLEL">并行</option>
        </select>
      </div>
    </div>
    
    <!-- 事务配置 -->
    <div style="margin-top:12px">
      <div class="muted">事务配置</div>
      <TransactionConfig
        :tx-mode="txMode"
        :transaction-manager="transactionManager"
        @update:tx-mode="emit('update:txMode', $event)"
        @update:transaction-manager="emit('update:transactionManager', $event)"
      />
    </div>
  </div>
</template>

<style scoped>
.service-node-config {
  display: flex;
  flex-direction: column;
}

.input.locked {
  background: #f3f4f6;
  color: #6b7280;
  cursor: not-allowed;
}

.input-hint {
  font-size: 11px;
  color: #94a3b8;
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

.input-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 11px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1.5fr;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 4px;
}

.field-row.header {
  font-weight: 600;
  background: #f8fafc;
  color: #475569;
}

.field-row:not(.header) {
  background: #fefefe;
  border: 1px solid #e5e7eb;
}
</style>
