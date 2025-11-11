<script setup lang="ts">
import { computed, ref, onUnmounted, nextTick, watch } from 'vue'
import { EditorView, keymap, lineNumbers, drawSelection, highlightActiveLine } from '@codemirror/view'
import { EditorState } from '@codemirror/state'
import { defaultKeymap, history, historyKeymap } from '@codemirror/commands'
import ParamResolverEditor from './ParamResolverEditor.vue'

interface Props {
  selectedNode: any | null
  nodes?: any[] | null
  edges?: any[] | null
  endpointSchema?: {
    requestSchema?: Array<{ name: string; type: string }> | null
    responseSchema?: { type?: string | null } | null
  } | null
  entrypointPath?: string | null
}

interface Emits {
  (e: 'update-node', node: any): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const isTransformerNode = computed(() => props.selectedNode?.type === 'transformer')

// 输出类型：object（对象）或 single（单值）
const outputType = computed(() => props.selectedNode?.data?.outputType || 'object')

/**
 * 获取上游节点的输出类型信息
 */
const upstreamOutputType = computed(() => {
  if (!props.selectedNode || !props.edges || !props.nodes) {
    return null
  }
  
  // 找到连接到当前转换器节点的上游节点
  const incomingEdge = props.edges.find((edge: any) => edge.target === props.selectedNode?.id)
  if (!incomingEdge) {
    return null
  }
  
  // 找到上游节点
  const upstreamNode = props.nodes.find((node: any) => node.id === incomingEdge.source)
  if (!upstreamNode) {
    return null
  }
  
  // 获取上游节点的输出类型
  const output = upstreamNode.data?.output
  if (!output) {
    return null
  }
  
  // 格式化输出类型
  const valueType = (output.valueType || 'OBJECT').toUpperCase()
  if (output.typeName) {
    return output.typeName
  }
  if (valueType === 'OBJECT' || valueType === 'ARRAY') {
    return valueType
  }
  return valueType
})

/**
 * 获取当前选中的输出类型选项值
 * 如果存在 REST 返回类型，优先返回对应的 REST 选项值
 */
const selectedOutputTypeValue = computed(() => {
  const currentType = outputType.value
  const responseType = props.endpointSchema?.responseSchema?.type
  
  if (responseType) {
    const parsed = parseResponseType(responseType)
    if (parsed) {
      if (parsed.isResponseEntity) {
        // 如果是 ResponseEntity，检查当前类型是否匹配
        if (currentType === 'object') {
          return 'rest-response-entity'
        }
      } else {
        if (isSimpleType(parsed.bodyType)) {
          if (currentType === 'single') {
            return 'rest-single'
          }
        } else {
          if (currentType === 'object') {
            return 'rest-object'
          }
        }
      }
    }
  }
  
  // 如果没有 REST 类型或类型不匹配，返回通用类型
  return currentType
})

/**
 * 获取输出类型选项列表
 * 如果存在 REST 接口返回类型，则优先显示它
 */
const outputTypeOptions = computed(() => {
  const options: Array<{ value: string; label: string }> = []
  
  // 如果存在 REST 接口返回类型，添加为第一个选项
  const responseType = props.endpointSchema?.responseSchema?.type
  if (responseType) {
    const parsed = parseResponseType(responseType)
    if (parsed) {
      if (parsed.isResponseEntity) {
        // ResponseEntity 类型
        options.push({
          value: 'rest-response-entity',
          label: `REST 返回类型：ResponseEntity<${parsed.bodyType}>`
        })
      } else {
        // 普通返回类型
        if (isSimpleType(parsed.bodyType)) {
          options.push({
            value: 'rest-single',
            label: `REST 返回类型：${parsed.bodyType}（单值）`
          })
        } else {
          options.push({
            value: 'rest-object',
            label: `REST 返回类型：${parsed.bodyType}（对象）`
          })
        }
      }
    }
  }
  
  // 添加通用选项
  options.push(
    { value: 'object', label: '对象（Map）' },
    { value: 'single', label: '单值（String/Number/Boolean）' }
  )
  
  return options
})

function cloneNode() {
  return JSON.parse(JSON.stringify(props.selectedNode))
}

function mutateNode(updater: (next: any) => void) {
  if (!props.selectedNode) return
  const next = cloneNode()
  updater(next)
  emit('update-node', next)
}

function updateNodeField(key: string, value: any) {
  mutateNode((next) => {
    next.data ||= {}
    next.data[key] = value
  })
}

function updateGroovyScript(script: string) {
  updateNodeField('script', script)
}

// 代码编辑器相关
const showCodeEditor = ref(false)
const codeEditorContainer = ref<HTMLDivElement | null>(null)
let codeEditorView: EditorView | null = null

/**
 * 打开代码编辑器
 */
function openCodeEditor() {
  showCodeEditor.value = true
  nextTick(() => {
    if (codeEditorContainer.value && !codeEditorView) {
      initCodeEditor()
    } else if (codeEditorView) {
      // 如果编辑器已存在，更新内容
      const currentScript = props.selectedNode?.data?.script || ''
      const transaction = codeEditorView.state.update({
        changes: {
          from: 0,
          to: codeEditorView.state.doc.length,
          insert: currentScript
        }
      })
      codeEditorView.dispatch(transaction)
    }
  })
}

/**
 * 关闭代码编辑器
 */
function closeCodeEditor() {
  showCodeEditor.value = false
  if (codeEditorView) {
    codeEditorView.destroy()
    codeEditorView = null
  }
}

/**
 * 保存并关闭编辑器
 */
function saveAndCloseEditor() {
  if (codeEditorView) {
    const content = codeEditorView.state.doc.toString()
    updateGroovyScript(content)
    closeCodeEditor()
  }
}

/**
 * 初始化 CodeMirror 编辑器
 */
function initCodeEditor() {
  if (!codeEditorContainer.value || codeEditorView) return
  
  const currentScript = props.selectedNode?.data?.script || ''
  
  // 定义保存快捷键
  const saveKeymap = keymap.of([
    {
      key: 'Mod-s',
      preventDefault: true,
      run: () => {
        saveAndCloseEditor()
        return true
      }
    }
  ])
  
  // 基础扩展配置（确保编辑器可编辑）
  // CodeMirror 6 默认是可编辑的，但需要确保有正确的扩展
  const extensions = [
    // 历史记录（撤销/重做）- 这是编辑功能的基础
    history(),
    // 行号
    lineNumbers(),
    // 文本选择
    drawSelection(),
    // 高亮当前行
    highlightActiveLine(),
    // 键盘快捷键（包含所有默认编辑快捷键）
    keymap.of([...defaultKeymap, ...historyKeymap]),
    // 保存快捷键（单独添加）
    saveKeymap,
    // 自动换行
    EditorView.lineWrapping,
    // 主题样式
    EditorView.theme({
      '&': {
        fontSize: '14px',
        height: '100%'
      },
      '.cm-scroller': {
        fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
        overflow: 'auto'
      },
      '.cm-content': {
        minHeight: '400px',
        padding: '12px',
        cursor: 'text'
      },
      '.cm-editor': {
        height: '100%'
      },
      '.cm-editor.cm-focused': {
        outline: 'none'
      },
      '.cm-editor.cm-focused .cm-content': {
        caretColor: '#2563eb'
      },
      '.cm-line': {
        padding: '0 2px'
      }
    })
  ]
  
  const state = EditorState.create({
    doc: currentScript,
    extensions
  })
  
  codeEditorView = new EditorView({
    state,
    parent: codeEditorContainer.value
  })
  
  // 确保编辑器可以编辑并自动聚焦
  nextTick(() => {
    if (codeEditorView) {
      // 强制聚焦
      codeEditorView.focus()
      
      // 调试：检查编辑器状态
      console.log('CodeMirror editor initialized:', {
        hasView: !!codeEditorView,
        hasState: !!codeEditorView.state,
        hasDom: !!codeEditorView.dom,
        isReadOnly: codeEditorView.state.readOnly
      })
      
      // 确保编辑器 DOM 可以接收输入事件
      const editorDom = codeEditorView.dom
      if (editorDom) {
        // 移除可能阻止输入的任何属性
        editorDom.removeAttribute('readonly')
        editorDom.removeAttribute('disabled')
        
        // 确保内容区域可以接收输入
        const content = editorDom.querySelector('.cm-content') as HTMLElement
        if (content) {
          // CodeMirror 6 使用 contenteditable，但应该由 CodeMirror 自己管理
          // 我们只需要确保没有阻止输入的属性
          content.removeAttribute('readonly')
          content.removeAttribute('disabled')
          
          // 添加点击事件监听，确保可以聚焦
          content.addEventListener('click', () => {
            codeEditorView?.focus()
          }, { once: true })
        }
      }
    }
  })
}

// 监听节点变化，更新编辑器内容
watch(() => props.selectedNode?.data?.script, (newScript) => {
  if (codeEditorView && showCodeEditor.value) {
    const currentContent = codeEditorView.state.doc.toString()
    if (currentContent !== (newScript || '')) {
      const transaction = codeEditorView.state.update({
        changes: {
          from: 0,
          to: codeEditorView.state.doc.length,
          insert: newScript || ''
        }
      })
      codeEditorView.dispatch(transaction)
    }
  }
})

// 清理编辑器
onUnmounted(() => {
  if (codeEditorView) {
    codeEditorView.destroy()
    codeEditorView = null
  }
})

function updateOutputType(type: string) {
  // 处理 REST 返回类型选项
  let actualType: 'object' | 'single' = 'object'
  
  if (type === 'rest-response-entity' || type === 'rest-object') {
    actualType = 'object'
  } else if (type === 'rest-single') {
    actualType = 'single'
  } else {
    actualType = type as 'object' | 'single'
  }
  
  updateNodeField('outputType', actualType)
  
  // 如果选择了 REST 返回类型，自动生成配置（无需确认）
  if (type.startsWith('rest-')) {
    const responseType = props.endpointSchema?.responseSchema?.type
    if (responseType) {
      // 直接生成配置，不弹出确认对话框
      generateTargetStructureDirectly(responseType, actualType)
    }
  }
  
  // 如果切换到单值模式，且当前有多个映射，只保留第一个
  if (actualType === 'single') {
    mutateNode((next) => {
      next.data ||= {}
      next.data.mappingConfig ||= {}
      const mappings = next.data.mappingConfig.fieldMappings || []
      if (mappings.length > 1) {
        next.data.mappingConfig.fieldMappings = [mappings[0]]
      }
      // 单值模式下，第一个映射的目标字段可以为空（直接返回值）
      if (mappings.length > 0 && mappings[0].targetField) {
        mappings[0].targetField = ''
      }
    })
  }
}

/**
 * 根据响应类型解析返回类型
 * 例如：ResponseEntity<Map<String, Object>> -> { isResponseEntity: true, bodyType: 'Map<String, Object>' }
 * 例如：Map<String, Object> -> { isResponseEntity: false, bodyType: 'Map<String, Object>' }
 * 例如：String -> { isResponseEntity: false, bodyType: 'String' }
 */
function parseResponseType(responseType?: string | null) {
  if (!responseType) return null
  
  // 检查是否是 ResponseEntity
  const responseEntityMatch = responseType.match(/ResponseEntity\s*<\s*(.+?)\s*>/i)
  if (responseEntityMatch) {
    return {
      isResponseEntity: true,
      bodyType: responseEntityMatch[1].trim()
    }
  }
  
  return {
    isResponseEntity: false,
    bodyType: responseType.trim()
  }
}

/**
 * 判断是否为简单类型
 */
function isSimpleType(type: string): boolean {
  const simpleTypes = [
    'String', 'Integer', 'Long', 'Double', 'Float', 'Boolean',
    'int', 'long', 'double', 'float', 'boolean',
    'java.lang.String', 'java.lang.Integer', 'java.lang.Long',
    'java.lang.Double', 'java.lang.Float', 'java.lang.Boolean'
  ]
  return simpleTypes.some(st => type.includes(st))
}

/**
 * 直接生成目标对象结构（无需确认）
 * 用于从下拉框选择时自动生成
 */
function generateTargetStructureDirectly(responseType: string, outputType: 'object' | 'single') {
  const parsed = parseResponseType(responseType)
  if (!parsed) {
    return
  }
  
  mutateNode((next) => {
    next.data ||= {}
    next.data.mappingConfig ||= {}
    next.data.mappingConfig.fieldMappings = []
    next.data.outputType = outputType
    
    // 如果是简单类型，生成单值模式
    if (isSimpleType(parsed.bodyType)) {
      next.data.mappingConfig.fieldMappings.push({
        sourceField: 'input',
        targetField: '',
        transformation: 'direct',
        value: '',
        script: '',
        defaultValue: ''
      })
      
      // 如果是 ResponseEntity，需要在 Groovy 脚本中包装
      if (parsed.isResponseEntity) {
        next.data.script = `// 自动生成：将输入转换为 ResponseEntity<${parsed.bodyType}>
def result = ''
if (input != null) {
    if (input instanceof String) {
        result = input
    } else if (input instanceof Map || input instanceof List) {
        // 如果是 Map 或 List，转换为 JSON 字符串
        import groovy.json.JsonBuilder
        def json = new JsonBuilder(input)
        result = json.toString()
    } else {
        result = input.toString()
    }
}
[
    statusCode: 200,
    headers: ['Content-Type': 'application/json'],
    body: result
]`
      } else {
        // 简单类型：根据输入类型智能转换
        next.data.script = `// 自动生成：将输入转换为 ${parsed.bodyType}
if (input == null) {
    return ''
} else if (input instanceof String) {
    return input
} else if (input instanceof Map || input instanceof List) {
    // 如果是 Map 或 List，转换为 JSON 字符串
    import groovy.json.JsonBuilder
    def json = new JsonBuilder(input)
    return json.toString()
} else {
    // 其他类型转换为字符串
    return input.toString()
}`
      }
    } else {
      // 复杂类型（Map、List 等），生成对象模式
      // 如果是 ResponseEntity，需要包装 body
      if (parsed.isResponseEntity) {
        // 生成一个基础映射：将 input 映射到 body
        next.data.mappingConfig.fieldMappings.push({
          sourceField: 'input',
          targetField: 'body',
          transformation: 'direct',
          value: '',
          script: '',
          defaultValue: ''
        })
        
        // 生成 Groovy 脚本包装为 ResponseEntity
        next.data.script = `// 自动生成：将字段映射结果包装为 ResponseEntity<${parsed.bodyType}>
def responseBody = output.body ?: output
[
    statusCode: 200,
    headers: ['Content-Type': 'application/json'],
    body: responseBody
]`
      } else {
        // 非 ResponseEntity，对于 Map 类型，不需要字段映射，直接在 Groovy 脚本中处理
        // 因为后端在对象模式下，如果 targetField 为空会跳过映射
        // 所以对于 Map 类型，我们直接在脚本中返回 input 或转换后的结果
        
        // 如果 input 是 Map，直接使用；否则尝试转换
        next.data.script = `// 自动生成：将输入转换为 ${parsed.bodyType}
if (input instanceof Map) {
    return input
} else if (input instanceof List) {
    return input
} else {
    // 尝试将输入转换为 Map
    def result = [:]
    if (input != null) {
        result['value'] = input
    }
    return result
}`
      }
    }
  })
}

/**
 * 自动生成目标对象结构（带确认对话框）
 * 根据 REST 接口的返回类型自动创建字段映射配置
 */
function autoGenerateTargetStructure() {
  const responseType = props.endpointSchema?.responseSchema?.type
  if (!responseType) {
    window.alert('无法自动生成：未找到 REST 接口的返回类型信息')
    return
  }
  
  const parsed = parseResponseType(responseType)
  if (!parsed) {
    window.alert('无法自动生成：无法解析返回类型')
    return
  }
  
  // 检查是否已有映射配置
  const existingMappings = props.selectedNode?.data?.mappingConfig?.fieldMappings || []
  const hasExistingConfig = existingMappings.length > 0 || props.selectedNode?.data?.script
  
  // 如果已有配置，提示用户会覆盖
  let confirmMsg = ''
  if (hasExistingConfig) {
    confirmMsg = parsed.isResponseEntity
      ? `检测到返回类型为 ResponseEntity<${parsed.bodyType}>，将自动生成响应结构。\n\n注意：这将覆盖现有的字段映射和 Groovy 脚本配置。\n\n是否继续？`
      : `检测到返回类型为 ${parsed.bodyType}，将自动生成映射配置。\n\n注意：这将覆盖现有的字段映射和 Groovy 脚本配置。\n\n是否继续？`
  } else {
    confirmMsg = parsed.isResponseEntity
      ? `检测到返回类型为 ResponseEntity<${parsed.bodyType}>，将自动生成响应结构。\n\n是否继续？`
      : `检测到返回类型为 ${parsed.bodyType}，将自动生成映射配置。\n\n是否继续？`
  }
  
  if (!window.confirm(confirmMsg)) {
    return
  }
  
  // 确定输出类型
  const outputType: 'object' | 'single' = isSimpleType(parsed.bodyType) ? 'single' : 'object'
  
  // 调用直接生成函数
  generateTargetStructureDirectly(responseType, outputType)
  
  window.alert('已自动生成目标对象结构和字段映射配置，请根据实际需求调整。')
}

/**
 * 清空所有配置（字段映射和 Groovy 脚本）
 */
function clearAllMappings() {
  if (!window.confirm('确定要清空所有配置（字段映射和 Groovy 脚本）吗？此操作不可撤销。')) {
    return
  }
  
  mutateNode((next) => {
    next.data ||= {}
    next.data.mappingConfig ||= {}
    next.data.mappingConfig.fieldMappings = []
    next.data.script = ''
  })
}

</script>

<template>
  <div v-if="isTransformerNode" class="transformer-editor">
    <!-- 输入来源说明 -->
    <div class="section">
      <h3 class="section-title">输入来源</h3>
      <div class="tip">
        <div style="margin-bottom: 4px;">
          <strong>说明：</strong>转换器的输入来源自动从连接到它的上游节点获取。转换器只能有一条输入连线，上游节点的返回值会自动作为转换器的输入数据（通过 <code>_lastNodeResult</code> 或 <code>_node_{节点ID}</code> 访问）。
        </div>
        <div v-if="upstreamOutputType" style="margin-top: 4px; padding-top: 4px; border-top: 1px solid rgba(148, 163, 184, 0.2);">
          <strong>上游节点输出类型：</strong>
          <code class="response-type-code">{{ upstreamOutputType }}</code>
        </div>
        <div v-else style="margin-top: 4px; font-size: 10px; color: #94a3b8;">
          提示：请先连接上游节点以查看输出类型
        </div>
      </div>
    </div>

    <!-- 输出类型配置 -->
    <div class="section">
      <h3 class="section-title">输出类型</h3>
      <div class="field-group">
        <label class="field-label">输出格式</label>
        <select
          class="input"
          :value="selectedOutputTypeValue"
          @change="updateOutputType(($event.target as HTMLSelectElement).value)"
        >
          <option
            v-for="option in outputTypeOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </div>
      <div class="tip">
        <span v-if="outputType === 'object'">
          输出为对象格式（Map）
        </span>
        <span v-else>
          输出为单值格式（String/Number/Boolean）
        </span>
        <div v-if="endpointSchema?.responseSchema?.type" style="margin-top: 4px; font-size: 10px; color: #64748b;">
          <strong>提示：</strong>选择 REST 返回类型选项将自动生成对应的 Groovy 脚本模板。
        </div>
      </div>
    </div>

    <!-- 自动生成配置 -->
    <div class="section" v-if="endpointSchema?.responseSchema?.type">
      <h3 class="section-title">自动生成配置</h3>
      <div class="mapping-controls">
        <button 
          class="btn small primary" 
          @click="autoGenerateTargetStructure"
          style="margin-right: 8px;"
        >
          🎯 自动生成目标结构
        </button>
        <button 
          v-if="(selectedNode.data?.mappingConfig?.fieldMappings || []).length > 0 || selectedNode.data?.script"
          class="btn small danger" 
          @click="clearAllMappings"
          title="清空所有配置（字段映射和 Groovy 脚本）"
        >
          清空配置
        </button>
      </div>
      <div class="tip response-type-tip" style="margin-top: 8px; font-size: 11px;">
        <div style="margin-bottom: 4px;">
          <strong>提示：</strong>点击"自动生成目标结构"可根据 REST 接口返回类型自动创建字段映射和 Groovy 脚本模板。复杂的数据转换逻辑可在 Groovy 脚本中编写。
        </div>
        <div style="margin-top: 4px;">
          <strong>返回类型：</strong>
          <code class="response-type-code">{{ endpointSchema.responseSchema.type }}</code>
        </div>
      </div>
    </div>

    <div class="section">
      <h3 class="section-title">Groovy脚本</h3>
      <div class="script-editor-wrapper">
        <textarea
          class="input textarea code-editor"
          rows="10"
          placeholder="点击此处打开代码编辑器进行编写..."
          :value="selectedNode.data?.script || ''"
          @click="openCodeEditor"
          @input="updateGroovyScript(($event.target as HTMLTextAreaElement).value)"
          spellcheck="false"
          readonly
        ></textarea>
        <div class="editor-hint">
          <span class="hint-text">💡 点击上方文本框打开代码编辑器</span>
          <button class="btn small" @click="openCodeEditor" style="margin-left: 8px;">
            打开编辑器
          </button>
        </div>
      </div>
      <div class="tip">
        <div style="margin-bottom: 4px;">
          <strong>提示：</strong>Groovy脚本将在字段映射完成后执行，可以访问以下变量：
        </div>
        <ul style="margin: 4px 0; padding-left: 20px; font-size: 11px;">
          <li><code>ctx</code> - 流程上下文（如 <code>ctx['request.path.projectKey']</code>）</li>
          <li><code>input</code> - 输入数据（前一个节点的输出或指定的输入源）</li>
          <li><code>output</code> - 字段映射的结果（Map 格式）</li>
        </ul>
        <div style="margin-top: 8px; padding-top: 8px; border-top: 1px solid rgba(148, 163, 184, 0.2);">
          <strong>生成 ResponseEntity：</strong>如需返回 <code>ResponseEntity&lt;Map&lt;String, Object&gt;&gt;</code> 结构，请在脚本中返回包含 <code>statusCode</code>、<code>headers</code>、<code>body</code> 的 Map，例如：
          <pre style="margin-top: 4px; font-size: 11px; background: #f1f5f9; padding: 8px; border-radius: 4px; overflow-x: auto;">[
    statusCode: 200,
    headers: ['Content-Type': 'application/json'],
    body: output
]</pre>
        </div>
      </div>
    </div>

    <div class="section">
      <h3 class="section-title">输入输出结构预览</h3>
      <div v-if="selectedNode.data?.inputSchema" class="schema-preview">
        <h4>输入结构</h4>
        <pre>{{ JSON.stringify(selectedNode.data.inputSchema, null, 2) }}</pre>
      </div>
      <div v-if="selectedNode.data?.outputSchema" class="schema-preview">
        <h4>输出结构</h4>
        <pre>{{ JSON.stringify(selectedNode.data.outputSchema, null, 2) }}</pre>
      </div>
      <div v-if="!selectedNode.data?.inputSchema && !selectedNode.data?.outputSchema" class="muted" style="font-size: 12px;">
        输入输出结构基于入口节点配置或手动设置
      </div>
    </div>

    <!-- 代码编辑器模态框 -->
    <div v-if="showCodeEditor" class="code-editor-modal" @click.self="closeCodeEditor">
      <div class="code-editor-modal-content" @click.stop>
        <div class="code-editor-header">
          <h3>Groovy 代码编辑器</h3>
          <div class="code-editor-actions">
            <button class="btn small" @click="saveAndCloseEditor">保存并关闭</button>
            <button class="btn small" @click="closeCodeEditor">取消</button>
          </div>
        </div>
        <div class="code-editor-body" @click.stop>
          <div ref="codeEditorContainer" class="code-editor-container"></div>
        </div>
        <div class="code-editor-footer">
          <div class="tip" style="margin: 0; font-size: 11px;">
            <strong>提示：</strong>可以使用 <kbd>Ctrl+S</kbd>（Mac: <kbd>Cmd+S</kbd>）保存并关闭，或点击"保存并关闭"按钮。
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.transformer-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 0;
}

.section {
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 8px;
  padding: 12px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  margin: 0;
}

.mapping-controls {
  display: flex;
  justify-content: flex-end;
}

.mapping-item {
  margin-top: 12px;
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
  transition: all 0.2s ease;
}

.mapping-item:hover {
  border-color: rgba(37, 99, 235, 0.3);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.04);
}

.mapping-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid rgba(148, 163, 184, 0.15);
}

.mapping-index {
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.5px;
}

.btn-icon {
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.2s ease;
  color: #94a3b8;
}

.btn-icon:hover {
  background: rgba(220, 38, 38, 0.1);
  color: #dc2626;
}

.btn-icon.danger {
  color: #ef4444;
}

.btn-icon.danger:hover {
  background: rgba(220, 38, 38, 0.15);
  color: #dc2626;
}

.mapping-body {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mapping-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.mapping-arrow-row {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 4px 0;
}

.mapping-arrow {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #94a3b8;
  font-weight: 300;
}

.mapping-field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 11px;
  font-weight: 500;
  color: #475569;
  display: flex;
  align-items: center;
  gap: 4px;
}

.required {
  color: #ef4444;
}

.hint {
  color: #64748b;
  font-size: 10px;
  font-weight: normal;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

@media (max-width: 600px) {
  .mapping-row {
    grid-template-columns: 1fr;
    gap: 10px;
  }
  
  .mapping-arrow {
    transform: rotate(90deg);
  }
}

.row {
  display: flex;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 8px;
  padding: 6px 8px;
  font-size: 12px;
  min-width: 0;
  width: 100%;
  box-sizing: border-box;
}

.input:focus {
  outline: none;
  border-color: #2563eb;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.1);
}

.btn {
  border: 1px solid rgba(148, 163, 184, 0.6);
  border-radius: 6px;
  padding: 6px 12px;
  font-size: 12px;
  background: #fff;
  color: #0f172a;
  cursor: pointer;
  transition: all 0.2s ease;
}

.btn:hover {
  background: #f8fafc;
  border-color: #2563eb;
}

.btn.small {
  font-size: 12px;
  padding: 4px 8px;
}

.btn.primary {
  background: #2563eb;
  color: white;
  border-color: #2563eb;
}

.btn.primary:hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.btn.danger.small {
  background: #dc2626;
  color: white;
  border: none;
}

.btn.danger.small:hover {
  background: #b91c1c;
}

.muted {
  color: #64748b;
  font-size: 12px;
}

.textarea {
  width: 100%;
  font-family: monospace;
  font-size: 12px;
  resize: vertical;
}

.textarea.code-editor {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.5;
  tab-size: 2;
  white-space: pre;
  overflow-wrap: normal;
  overflow-x: auto;
}

.tip {
  font-size: 12px;
  color: #64748b;
  background: #f8fafc;
  border-left: 2px solid #2563eb;
  padding: 8px;
}

.response-type-tip {
  word-wrap: break-word;
  overflow-wrap: break-word;
}

.response-type-code {
  display: inline-block;
  max-width: 100%;
  word-break: break-all;
  word-wrap: break-word;
  overflow-wrap: break-word;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 10px;
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  margin-top: 2px;
}

.schema-preview {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.schema-preview h4 {
  font-size: 12px;
  font-weight: 600;
  margin: 0 0 4px 0;
  color: #475569;
}

.schema-preview pre {
  background: #f1f5f9;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  padding: 8px;
  font-size: 11px;
  overflow-x: auto;
  margin: 0;
}

.schema-table {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.schema-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.field-name {
  font-weight: 500;
  color: #0f172a;
}

.field-type {
  color: #64748b;
  font-family: monospace;
  font-size: 10px;
}

.schema-info {
  padding: 8px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 11px;
}

.script-editor-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.editor-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
}

.hint-text {
  display: flex;
  align-items: center;
  gap: 4px;
}

.code-editor-modal {
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
  padding: 20px;
}

.code-editor-modal-content {
  background: white;
  border-radius: 8px;
  width: 100%;
  max-width: 900px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
}

.code-editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.code-editor-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.code-editor-actions {
  display: flex;
  gap: 8px;
}

.code-editor-body {
  flex: 1;
  overflow: hidden;
  min-height: 400px;
  max-height: calc(90vh - 140px);
}

.code-editor-container {
  width: 100%;
  height: 100%;
  overflow: auto;
  position: relative;
  /* 确保可以接收鼠标和键盘事件 */
  pointer-events: auto;
  user-select: text;
}

.code-editor-container :deep(.cm-editor) {
  height: 100%;
  font-size: 14px;
  /* 确保编辑器可以接收事件 */
  pointer-events: auto;
}

.code-editor-container :deep(.cm-scroller) {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  /* 确保滚动容器可以接收事件 */
  pointer-events: auto;
}

.code-editor-container :deep(.cm-content) {
  cursor: text;
  /* 确保内容区域可以接收事件 */
  pointer-events: auto;
  user-select: text;
}

.code-editor-container :deep(.cm-editor.cm-focused) {
  outline: none;
}

.code-editor-container :deep(.cm-editor.cm-focused .cm-content) {
  caret-color: #2563eb;
}

.code-editor-container :deep(.cm-line) {
  pointer-events: auto;
}

.code-editor-footer {
  padding: 12px 20px;
  border-top: 1px solid #e2e8f0;
  background: #f8fafc;
}

.code-editor-footer kbd {
  display: inline-block;
  padding: 2px 6px;
  font-size: 11px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  background: #e2e8f0;
  border: 1px solid #cbd5e1;
  border-radius: 3px;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.1);
  margin: 0 2px;
}
</style>