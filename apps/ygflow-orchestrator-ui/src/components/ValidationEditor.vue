<script setup lang="ts">
import { computed, ref, watch } from "vue"
import ScriptEditor from "./ScriptEditor.vue"

/**
 * 校验器类型
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
 * 校验规则配置
 */
interface ValidationRule {
  id: string
  type: ValidatorType
  enabled: boolean
  message?: string
  config?: Record<string, any>
}

/**
 * 预设正则表达式
 */
const REGEX_PRESETS = {
  email: { pattern: "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", flags: "", label: "邮箱", description: "标准邮箱格式" },
  phone: { pattern: "^1[3-9]\\d{9}$", flags: "", label: "手机号", description: "中国手机号格式（11位数字，1开头）" },
  idCard: { pattern: "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]$", flags: "", label: "身份证号", description: "中国18位身份证号格式" },
  url: { pattern: "^https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?$", flags: "i", label: "URL", description: "HTTP/HTTPS URL 格式" },
  ip: { pattern: "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$", flags: "", label: "IP地址", description: "IPv4 地址格式" },
  numeric: { pattern: "^\\d+$", flags: "", label: "纯数字", description: "只包含数字" },
  alphanumeric: { pattern: "^[A-Za-z0-9]+$", flags: "", label: "字母数字", description: "只包含字母和数字" },
}

const props = defineProps<{
  rule: ValidationRule | null
  valueType?: string
}>()

const emit = defineEmits<{
  (e: "save", rule: ValidationRule): void
  (e: "cancel"): void
}>()

const show = ref(false)
const ruleType = ref<ValidatorType>("required")
const enabled = ref(true)
const message = ref("")
const config = ref<Record<string, any>>({})

// 正则相关配置
const regexPreset = ref<string>("")
const regexPattern = ref("")
const regexFlags = ref("")

// 范围相关配置
const rangeMin = ref<number | null>(null)
const rangeMax = ref<number | null>(null)

// 长度相关配置
const lengthMin = ref<number | null>(null)
const lengthMax = ref<number | null>(null)

// 类型相关配置
const expectedType = ref("")
const expectedTypeName = ref("")

// 表达式相关配置
const expression = ref("")

// 自定义校验器相关配置
const customValidator = ref("")
const customValidatorConfigJson = ref("")

watch(() => props.rule, (newRule) => {
  if (newRule) {
    ruleType.value = newRule.type
    enabled.value = newRule.enabled
    message.value = newRule.message || ""
    config.value = newRule.config || {}
    
    // 根据类型加载配置
    if (newRule.type === "regex") {
      regexPreset.value = config.value.preset || ""
      regexPattern.value = config.value.pattern || ""
      regexFlags.value = config.value.flags || ""
    } else if (newRule.type === "range") {
      rangeMin.value = config.value.min ?? null
      rangeMax.value = config.value.max ?? null
    } else if (newRule.type === "length") {
      lengthMin.value = config.value.minLength ?? null
      lengthMax.value = config.value.maxLength ?? null
    } else if (newRule.type === "type") {
      expectedType.value = config.value.expectedType || ""
      expectedTypeName.value = config.value.expectedTypeName || ""
    } else if (newRule.type === "expression") {
      expression.value = config.value.expression || ""
    } else if (newRule.type === "custom") {
      customValidator.value = config.value.validator || ""
      customValidatorConfigJson.value = config.value.validatorConfig 
        ? JSON.stringify(config.value.validatorConfig, null, 2)
        : ""
    }
  } else {
    resetForm()
  }
}, { immediate: true })

function resetForm() {
  ruleType.value = "required"
  enabled.value = true
  message.value = ""
  config.value = {}
  regexPreset.value = ""
  regexPattern.value = ""
  regexFlags.value = ""
  rangeMin.value = null
  rangeMax.value = null
  lengthMin.value = null
  lengthMax.value = null
  expectedType.value = ""
  expectedTypeName.value = ""
  expression.value = ""
  customValidator.value = ""
  customValidatorConfigJson.value = ""
}

function open() {
  show.value = true
}

function close() {
  show.value = false
  clearErrors()
  emit("cancel")
}

function handleSave() {
  if (!props.rule) {
    return
  }
  
  // 执行表单验证
  if (!validateForm()) {
    return
  }
  
  const ruleConfig: Record<string, any> = {}
  
  // 根据类型构建配置
  if (ruleType.value === "regex") {
    if (regexPreset.value) {
      ruleConfig.preset = regexPreset.value
    } else if (regexPattern.value) {
      ruleConfig.pattern = regexPattern.value
      ruleConfig.flags = regexFlags.value
    }
  } else if (ruleType.value === "range") {
    if (rangeMin.value != null) ruleConfig.min = rangeMin.value
    if (rangeMax.value != null) ruleConfig.max = rangeMax.value
  } else if (ruleType.value === "length") {
    if (lengthMin.value != null) ruleConfig.minLength = lengthMin.value
    if (lengthMax.value != null) ruleConfig.maxLength = lengthMax.value
  } else if (ruleType.value === "type") {
    if (expectedType.value) ruleConfig.expectedType = expectedType.value
    if (expectedTypeName.value) ruleConfig.expectedTypeName = expectedTypeName.value
  } else if (ruleType.value === "expression") {
    if (expression.value) ruleConfig.expression = expression.value
  } else if (ruleType.value === "custom") {
    if (customValidator.value) ruleConfig.validator = customValidator.value
    if (customValidatorConfigJson.value.trim()) {
      try {
        ruleConfig.validatorConfig = JSON.parse(customValidatorConfigJson.value)
      } catch (e) {
        // JSON 解析失败，忽略配置
      }
    }
  }
  
  const updatedRule: ValidationRule = {
    ...props.rule,
    type: ruleType.value,
    enabled: enabled.value,
    message: message.value.trim() || undefined,
    config: Object.keys(ruleConfig).length > 0 ? ruleConfig : undefined,
  }
  
  emit("save", updatedRule)
  close()
}

function handlePresetChange() {
  if (regexPreset.value && REGEX_PRESETS[regexPreset.value as keyof typeof REGEX_PRESETS]) {
    const preset = REGEX_PRESETS[regexPreset.value as keyof typeof REGEX_PRESETS]
    regexPattern.value = preset.pattern
    regexFlags.value = preset.flags
  }
}

const validatorTypeOptions = computed(() => {
  const options: Array<{ value: ValidatorType; label: string; description: string }> = [
    { value: "required", label: "必填", description: "检查参数是否存在且不为 null" },
    { value: "notEmpty", label: "非空", description: "检查集合/数组/Map 是否不为空" },
    { value: "notBlank", label: "非空白", description: "检查字符串是否不为空白" },
    { value: "type", label: "类型", description: "检查参数类型是否匹配" },
    { value: "range", label: "范围", description: "检查数字是否在指定范围内" },
    { value: "length", label: "长度", description: "检查字符串/数组长度是否在指定范围内" },
    { value: "regex", label: "正则", description: "检查字符串是否匹配正则表达式" },
    { value: "expression", label: "表达式", description: "使用 Groovy 表达式进行自定义校验" },
    { value: "custom", label: "自定义", description: "使用自定义校验器类进行校验" },
  ]
  
  // 根据 valueType 过滤支持的校验器类型
  if (props.valueType) {
    const valueTypeUpper = props.valueType.toUpperCase()
    return options.filter(opt => {
      if (opt.value === "notBlank" && valueTypeUpper !== "STRING") return false
      if (opt.value === "range" && valueTypeUpper !== "NUMBER") return false
      if (opt.value === "length" && valueTypeUpper !== "STRING" && valueTypeUpper !== "ARRAY") return false
      if (opt.value === "regex" && valueTypeUpper !== "STRING") return false
      return true
    })
  }
  
  return options
})

const regexPresetOptions = computed(() => {
  return Object.entries(REGEX_PRESETS).map(([key, preset]) => ({
    value: key,
    label: preset.label,
    description: preset.description,
  }))
})

const needsConfig = computed(() => {
  return ["type", "range", "length", "regex", "expression", "custom"].includes(ruleType.value)
})

// 表达式编辑器 ref
const expressionEditorRef = ref<{ openCodeEditor: () => void } | null>(null)

// 错误消息
const errors = ref<Record<string, string>>({})

function validateForm(): boolean {
  errors.value = {}
  
  // 长度校验器验证
  if (ruleType.value === "length") {
    if (lengthMin.value != null && lengthMin.value < 0) {
      errors.value.lengthMin = "最小长度不能为负数"
    }
    if (lengthMax.value != null && lengthMax.value < 0) {
      errors.value.lengthMax = "最大长度不能为负数"
    }
    if (lengthMin.value != null && lengthMax.value != null && lengthMin.value > lengthMax.value) {
      errors.value.lengthRange = "最小长度不能大于最大长度"
    }
    if (lengthMin.value == null && lengthMax.value == null) {
      errors.value.lengthRequired = "至少需要设置最小长度或最大长度"
    }
  }
  
  // 范围校验器验证
  if (ruleType.value === "range") {
    if (rangeMin.value != null && rangeMax.value != null && rangeMin.value > rangeMax.value) {
      errors.value.rangeValue = "最小值不能大于最大值"
    }
    if (rangeMin.value == null && rangeMax.value == null) {
      errors.value.rangeRequired = "至少需要设置最小值或最大值"
    }
  }
  
  // 正则表达式校验器验证
  if (ruleType.value === "regex") {
    if (!regexPreset.value && (!regexPattern.value || regexPattern.value.trim() === "")) {
      errors.value.regexPattern = "请选择预设正则表达式或输入自定义正则表达式"
    }
  }
  
  // 表达式校验器验证
  if (ruleType.value === "expression") {
    if (!expression.value || expression.value.trim() === "") {
      errors.value.expression = "表达式不能为空"
    }
  }
  
  // 类型校验器验证
  if (ruleType.value === "type") {
    if (!expectedType.value && !expectedTypeName.value) {
      errors.value.typeRequired = "至少需要设置期望类型或完整类型名"
    }
  }
  
  // 自定义校验器验证
  if (ruleType.value === "custom") {
    if (!customValidator.value || customValidator.value.trim() === "") {
      errors.value.customValidator = "校验器类名不能为空"
    }
    if (customValidatorConfigJson.value.trim()) {
      try {
        JSON.parse(customValidatorConfigJson.value)
      } catch (e) {
        errors.value.customValidatorConfig = "校验器配置必须是有效的 JSON 格式"
      }
    }
  }
  
  return Object.keys(errors.value).length === 0
}

function clearErrors() {
  errors.value = {}
}

// 监听配置变化，清除相关错误
watch([ruleType, lengthMin, lengthMax, rangeMin, rangeMax, regexPreset, regexPattern, expression, expectedType, expectedTypeName, customValidator, customValidatorConfigJson], () => {
  clearErrors()
})

defineExpose({
  open,
  close,
})
</script>

<template>
  <div v-if="show" class="modal-overlay" @click.self="close">
    <div class="modal-content" style="max-width: 600px; max-height: 80vh; overflow-y: auto">
      <div class="modal-header">
        <h3>校验器配置</h3>
        <button class="btn-close" @click="close">×</button>
      </div>
      
      <div class="modal-body" style="display: flex; flex-direction: column; gap: 16px; padding: 16px">
        <!-- 校验器类型 -->
        <div>
          <label class="label">类型</label>
          <select class="input" v-model="ruleType">
            <option v-for="opt in validatorTypeOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }} - {{ opt.description }}
            </option>
          </select>
        </div>
        
        <!-- 启用开关 -->
        <div style="display: flex; align-items: center; gap: 8px">
          <input type="checkbox" id="enabled" v-model="enabled" />
          <label for="enabled">启用此校验器</label>
        </div>
        
        <!-- 错误消息 -->
        <div>
          <label class="label">错误消息</label>
          <input 
            class="input" 
            v-model="message" 
            placeholder="校验失败时显示的错误消息（可选）"
          />
        </div>
        
        <!-- 配置区域 -->
        <div v-if="needsConfig">
          <!-- 正则表达式配置 -->
          <div v-if="ruleType === 'regex'" style="display: flex; flex-direction: column; gap: 12px">
            <div>
              <label class="label">预设正则表达式</label>
              <select class="input" :class="{ 'input-error': errors.regexPattern }" v-model="regexPreset" @change="handlePresetChange">
                <option value="">自定义</option>
                <option v-for="opt in regexPresetOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }} - {{ opt.description }}
                </option>
              </select>
            </div>
            <div v-if="!regexPreset">
              <label class="label">正则表达式模式</label>
              <input class="input" :class="{ 'input-error': errors.regexPattern }" v-model="regexPattern" placeholder="例如: ^[0-9]+$" />
            </div>
            <div v-if="!regexPreset">
              <label class="label">标志（可选）</label>
              <input class="input" v-model="regexFlags" placeholder="例如: i (忽略大小写)" />
            </div>
            <div v-if="errors.regexPattern" class="error-message">{{ errors.regexPattern }}</div>
          </div>
          
          <!-- 范围配置 -->
          <div v-if="ruleType === 'range'" style="display: flex; flex-direction: column; gap: 8px">
            <div style="display: flex; gap: 12px">
              <div style="flex: 1">
                <label class="label">最小值</label>
                <input type="number" class="input" :class="{ 'input-error': errors.rangeValue }" v-model.number="rangeMin" placeholder="最小值" />
              </div>
              <div style="flex: 1">
                <label class="label">最大值</label>
                <input type="number" class="input" :class="{ 'input-error': errors.rangeValue }" v-model.number="rangeMax" placeholder="最大值" />
              </div>
            </div>
            <div v-if="errors.rangeValue" class="error-message">{{ errors.rangeValue }}</div>
            <div v-if="errors.rangeRequired" class="error-message">{{ errors.rangeRequired }}</div>
          </div>
          
          <!-- 长度配置 -->
          <div v-if="ruleType === 'length'" style="display: flex; flex-direction: column; gap: 8px">
            <div style="display: flex; gap: 12px">
              <div style="flex: 1">
                <label class="label">最小长度</label>
                <input type="number" class="input" :class="{ 'input-error': errors.lengthMin || errors.lengthRange }" v-model.number="lengthMin" placeholder="最小长度" min="0" />
              </div>
              <div style="flex: 1">
                <label class="label">最大长度</label>
                <input type="number" class="input" :class="{ 'input-error': errors.lengthMax || errors.lengthRange }" v-model.number="lengthMax" placeholder="最大长度" min="0" />
              </div>
            </div>
            <div v-if="errors.lengthMin" class="error-message">{{ errors.lengthMin }}</div>
            <div v-if="errors.lengthMax" class="error-message">{{ errors.lengthMax }}</div>
            <div v-if="errors.lengthRange" class="error-message">{{ errors.lengthRange }}</div>
            <div v-if="errors.lengthRequired" class="error-message">{{ errors.lengthRequired }}</div>
          </div>
          
          <!-- 类型配置 -->
          <div v-if="ruleType === 'type'" style="display: flex; flex-direction: column; gap: 12px">
            <div>
              <label class="label">期望类型</label>
              <select class="input" :class="{ 'input-error': errors.typeRequired }" v-model="expectedType">
                <option value="">请选择</option>
                <option value="STRING">STRING</option>
                <option value="NUMBER">NUMBER</option>
                <option value="BOOLEAN">BOOLEAN</option>
                <option value="OBJECT">OBJECT</option>
                <option value="ARRAY">ARRAY</option>
              </select>
            </div>
            <div>
              <label class="label">完整类型名（可选）</label>
              <input 
                class="input" 
                :class="{ 'input-error': errors.typeRequired }"
                v-model="expectedTypeName" 
                placeholder="例如: java.lang.String"
              />
            </div>
            <div v-if="errors.typeRequired" class="error-message">{{ errors.typeRequired }}</div>
          </div>
          
          <!-- 表达式配置 -->
          <div v-if="ruleType === 'expression'" style="display: flex; flex-direction: column; gap: 8px">
            <label class="label">Groovy 表达式</label>
            <textarea
              class="input script-value-input"
              :class="{ 'input-error': errors.expression }"
              :value="expression"
              placeholder="点击此处打开脚本编辑器配置校验表达式..."
              readonly
              @click="expressionEditorRef?.openCodeEditor()"
              rows="3"
            ></textarea>
            <div v-if="errors.expression" class="error-message">{{ errors.expression }}</div>
            <ScriptEditor
              :ref="(el) => { expressionEditorRef = el as any }"
              :script="expression"
              title="校验表达式编辑器"
              :variable-groups="[
                {
                  title: '变量',
                  items: [
                    { label: 'value', snippet: 'value', description: '当前参数值' },
                    { label: 'ctx', snippet: 'ctx', description: '流程上下文' },
                    { label: 'input', snippet: 'input', description: '当前 input 对象' },
                  ],
                },
              ]"
              :hide-preview="true"
              @update:script="expression = $event"
            />
          </div>
          
          <!-- 自定义校验器配置 -->
          <div v-if="ruleType === 'custom'" style="display: flex; flex-direction: column; gap: 12px">
            <div>
              <label class="label">校验器类名</label>
              <input 
                class="input" 
                :class="{ 'input-error': errors.customValidator }"
                v-model="customValidator" 
                placeholder="例如: com.example.EmailValidator"
              />
              <div v-if="errors.customValidator" class="error-message">{{ errors.customValidator }}</div>
            </div>
            <div>
              <label class="label">校验器配置（JSON）</label>
              <textarea 
                class="input" 
                :class="{ 'input-error': errors.customValidatorConfig }"
                v-model="customValidatorConfigJson"
                placeholder='{"key": "value"}'
                rows="4"
              />
              <div v-if="errors.customValidatorConfig" class="error-message">{{ errors.customValidatorConfig }}</div>
            </div>
          </div>
        </div>
      </div>
      
      <div class="modal-footer">
        <button class="btn" @click="close">取消</button>
        <button class="btn btn-primary" @click="handleSave">确定</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid #e5e7eb;
}

.modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.btn-close {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #6b7280;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-close:hover {
  color: #1f2937;
}

.modal-body {
  padding: 16px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px;
  border-top: 1px solid #e5e7eb;
}

.label {
  display: block;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 4px;
}

.input {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
}

.input:focus {
  outline: none;
  border-color: #3b82f6;
}

.btn {
  padding: 6px 12px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: white;
  cursor: pointer;
  font-size: 13px;
}

.btn-primary {
  background: #3b82f6;
  color: white;
  border-color: #3b82f6;
}

.btn-primary:hover {
  background: #2563eb;
}

.input-error {
  border-color: #ef4444 !important;
}

.error-message {
  font-size: 12px;
  color: #ef4444;
  margin-top: -4px;
}
</style>

