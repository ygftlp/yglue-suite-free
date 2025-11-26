<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from "vue"
import { EditorState } from "@codemirror/state"
import { EditorView, keymap, lineNumbers, drawSelection, highlightActiveLine } from "@codemirror/view"
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands"
import { autocompletion, completionKeymap, CompletionContext, CompletionResult } from "@codemirror/autocomplete"
import { api } from "../api/client"

type HelperTab = "variables" | "functions"

interface HelperItem {
  label: string
  snippet: string
  description: string
  example?: string
  tooltip?: string // 用于 tooltip 显示的额外信息
}

interface HelperGroup {
  title: string
  items: HelperItem[]
}

interface HelperItemInput {
  label?: string
  snippet?: string
  description?: string
  example?: string
}

interface HelperGroupInput {
  title?: string
  items?: HelperItemInput[]
}

const props = defineProps<{
  title?: string
  script?: string | null
  helperTab?: HelperTab
  variableGroups?: HelperGroupInput[]
  functionGroups?: HelperGroupInput[]
  hidePreview?: boolean // 隐藏预览区域，只显示弹框
  endpointSchema?: {
    requestSchema?: Array<{ name: string; type: string }> | null
    responseSchema?: { type?: string | null } | null
  } | null
  projectKey?: string
  endpointId?: number
  upstreamOutputType?: string | null
}>()

const emit = defineEmits<{
  (e: "update:script", value: string): void
  (e: "open"): void
}>()

const resolvedTitle = computed(() => (props.title?.trim()?.length ? props.title!.trim() : "Groovy 脚本"))
const showCodeEditor = ref(false)
const helperTabState = ref<HelperTab>(props.helperTab ?? "variables")
const codeEditorContainer = ref<HTMLDivElement | null>(null)
let codeEditorView: EditorView | null = null

watch(
  () => props.helperTab,
  (tab) => {
    if (tab && tab !== helperTabState.value) {
      helperTabState.value = tab
    }
  }
)

const scriptValue = computed(() => props.script ?? "")
const externalVariableGroups = ref<HelperGroupInput[]>([])
const variableGroups = computed<HelperGroup[]>(() => normalizeHelperGroups([...(props.variableGroups || []), ...externalVariableGroups.value]))
const externalFunctionGroups = ref<HelperGroupInput[]>([])
const functionGroups = computed<HelperGroup[]>(() => normalizeHelperGroups([...(props.functionGroups || []), ...externalFunctionGroups.value]))
const activeHelperGroups = computed<HelperGroup[]>(() =>
  helperTabState.value === "variables" ? variableGroups.value : functionGroups.value
)

/**
 * 构建自动补全源
 * 支持变量属性自动补全，如 ctx.、input.、output. 等
 */
function createCompletionSource(
  getVariableGroups: () => HelperGroup[],
  endpointSchema?: { requestSchema?: Array<{ name: string; type: string }> | null } | null
): (context: CompletionContext) => CompletionResult | null {
  return (context: CompletionContext) => {
    const { state, pos } = context
    const line = state.doc.lineAt(pos)
    const textBefore = line.text.slice(0, pos - line.from)
    
    // 提取所有变量名
    const allVariables = new Map<string, { label: string; description: string; properties?: string[] }>()
    getVariableGroups()?.forEach((group) => {
      group.items?.forEach((item) => {
        if (item.label && item.snippet) {
          const varName = item.snippet.trim().split(/[.\[]/)[0] // 提取变量名（如 ctx['xxx'] -> ctx）
          if (varName && !allVariables.has(varName)) {
            allVariables.set(varName, {
              label: item.label,
              description: item.description || "",
            })
          }
        }
      })
    })

    // 匹配变量. 或变量[ 的模式
    const dotMatch = textBefore.match(/(\w+)(\.|\[['"]?)$/)
    const bracketMatch = textBefore.match(/(\w+)\['([^']*)$/)
    
    if (dotMatch) {
      const varName = dotMatch[1]
      const completions = getVariableCompletions(varName, allVariables, getVariableGroups, "", endpointSchema)
      if (completions.length > 0) {
        return {
          from: pos - (dotMatch[0].length - dotMatch[1].length),
          options: completions,
        }
      }
    } else if (bracketMatch) {
      const varName = bracketMatch[1]
      const prefix = bracketMatch[2]
      const completions = getVariableCompletions(varName, allVariables, getVariableGroups, prefix, endpointSchema)
      if (completions.length > 0) {
        return {
          from: pos - prefix.length,
          options: completions,
        }
      }
    }

    // 如果没有匹配到变量. 模式，提供变量名补全
    const wordMatch = textBefore.match(/(\w*)$/)
    if (wordMatch) {
      const prefix = wordMatch[1]
      const variableCompletions = Array.from(allVariables.entries())
        .filter(([name]) => name.startsWith(prefix) && name !== prefix)
        .map(([name, info]) => ({
          label: name,
          type: "variable",
          detail: info.description,
        }))
      
      if (variableCompletions.length > 0) {
        return {
          from: pos - prefix.length,
          options: variableCompletions,
        }
      }
    }

    return null
  }
}

/**
 * 获取变量的属性补全列表
 */
function getVariableCompletions(
  varName: string,
  allVariables: Map<string, { label: string; description: string }>,
  getVariableGroups: () => HelperGroup[],
  prefix: string = "",
  endpointSchema?: { requestSchema?: Array<{ name: string; type: string }> | null } | null
): Array<{ label: string; type: string; detail?: string }> {
  const completions: Array<{ label: string; type: string; detail?: string }> = []

  // ctx 变量的属性
  if (varName === "ctx") {
    const ctxProperties = [
      { label: "_lastNodeResult", detail: "最后一个节点的输出结果" },
      { label: "request", detail: "请求对象（包含 path、query、body、headers）" },
    ]
    
    // 从 variableGroups 中提取 ctx 相关的属性
    getVariableGroups()?.forEach((group) => {
      group.items?.forEach((item) => {
        if (item.snippet?.startsWith("ctx")) {
          const snippet = item.snippet.trim()
          // 提取 ctx['xxx'] 或 ctx.xxx 中的属性名
          const match = snippet.match(/ctx\[['"]([^'"]+)['"]\]|ctx\.(\w+)/)
          if (match) {
            const propName = match[1] || match[2]
            if (propName && !ctxProperties.some((p) => p.label === propName)) {
              ctxProperties.push({
                label: propName,
                detail: item.description || "",
              })
            }
          }
        }
      })
    })

    completions.push(
      ...ctxProperties
        .filter((p) => p.label.toLowerCase().includes(prefix.toLowerCase()))
        .map((p) => ({
          label: p.label,
          type: "property",
          detail: p.detail,
        }))
    )
  }

  // request 对象的属性
  if (varName === "request") {
    const requestProperties = [
      { label: "path", detail: "路径变量（Map）" },
      { label: "query", detail: "查询参数（Map）" },
      { label: "body", detail: "请求体（Map/Object）" },
      { label: "headers", detail: "请求头（Map）" },
    ]

    // 从 endpointSchema 中提取 requestSchema 字段
    if (endpointSchema && endpointSchema.requestSchema) {
      endpointSchema.requestSchema.forEach((field) => {
        const fieldName = field.name
        // 根据 source 确定路径
        const source = (field as any).source
        if (source === "path" || (field as any).pathVariable) {
          const pathVar = (field as any).pathVariable || fieldName
          if (!requestProperties.some((p) => p.label === `path.${pathVar}`)) {
            requestProperties.push({
              label: `path.${pathVar}`,
              detail: `路径变量：${fieldName} (${field.type})`,
            })
          }
        } else if (source === "query" || (field as any).paramName) {
          const paramName = (field as any).paramName || fieldName
          if (!requestProperties.some((p) => p.label === `query.${paramName}`)) {
            requestProperties.push({
              label: `query.${paramName}`,
              detail: `查询参数：${fieldName} (${field.type})`,
            })
          }
        } else if (source === "body" || source === "form") {
          if (!requestProperties.some((p) => p.label === `body.${fieldName}`)) {
            requestProperties.push({
              label: `body.${fieldName}`,
              detail: `请求体字段：${fieldName} (${field.type})`,
            })
          }
        }
      })
    }

    completions.push(
      ...requestProperties
        .filter((p) => p.label.toLowerCase().includes(prefix.toLowerCase()))
        .map((p) => ({
          label: p.label,
          type: "property",
          detail: p.detail,
        }))
    )
  }

  // input 变量的属性（从上游节点输出类型推断）
  if (varName === "input") {
    completions.push(
      { label: "toString()", type: "method", detail: "转换为字符串" },
      { label: "size()", type: "method", detail: "获取集合大小（如果是 List 或 Map）" },
      { label: "get(key)", type: "method", detail: "获取 Map 中的值" },
      { label: "keySet()", type: "method", detail: "获取 Map 的键集合" },
      { label: "values()", type: "method", detail: "获取 Map 的值集合" },
    )
  }


  // resolved 变量的属性（从 requestSchema 推断）
  if (varName === "resolved") {
    // 可以从 variableGroups 中提取 resolved 相关的属性
    getVariableGroups()?.forEach((group) => {
      group.items?.forEach((item) => {
        if (item.snippet?.startsWith("resolved.")) {
          const propName = item.snippet.replace("resolved.", "").trim()
          if (propName && !completions.some((c) => c.label === propName)) {
            completions.push({
              label: propName,
              type: "property",
              detail: item.description || "",
            })
          }
        }
      })
    })
  }

  return completions.filter((c) => c.label.toLowerCase().includes(prefix.toLowerCase()))
}

function normalizeHelperGroups(groups?: HelperGroupInput[]): HelperGroup[] {
  if (!Array.isArray(groups)) return []
  return groups
    .map((group, index) => {
      const title = group?.title?.trim()?.length ? group.title!.trim() : `分组 ${index + 1}`
      const items = Array.isArray(group?.items)
        ? group.items
            .map((item) => normalizeHelperItem(item))
            .filter((item): item is HelperItem => !!item)
        : []
      if (!items.length) return null
      return { title, items }
    })
    .filter(Boolean) as HelperGroup[]
}

function normalizeHelperItem(item?: HelperItemInput): HelperItem | null {
  if (!item) return null
  const label = typeof item.label === "string" ? item.label.trim() : ""
  const snippet = typeof item.snippet === "string" ? item.snippet : ""
  if (!label || !snippet) return null
  return {
    label,
    snippet,
    description: typeof item.description === "string" ? item.description : "",
    example: typeof item.example === "string" ? item.example : undefined,
  }
}

function openCodeEditor() {
  showCodeEditor.value = true
  emit("open")
  loadScriptHelpersInternal()
  loadMembersForUpstreamTypeInternal()
  nextTick(() => {
    if (codeEditorContainer.value && !codeEditorView) {
      initCodeEditor()
    } else if (codeEditorView) {
      const transaction = codeEditorView.state.update({
        changes: {
          from: 0,
          to: codeEditorView.state.doc.length,
          insert: scriptValue.value,
        },
      })
      codeEditorView.dispatch(transaction)
    }
  })
}

async function loadScriptHelpersInternal() {
  if (!props.projectKey) return
  try {
    const helpers = await api.getScriptHelpers(
      props.projectKey,
      props.endpointId ? { endpointId: props.endpointId } : undefined
    )
    const depGroup: HelperGroupInput = {
      title: "依赖",
      items: (helpers.selectedJars || []).map((j) => ({
        label: j.name,
        snippet: j.coordinate || "",
        description: (j.coordinate || "") || "依赖坐标",
      })),
    }
    const classGroup: HelperGroupInput = {
      title: "类引用",
      items: (helpers.classes || []).slice(0, 500).map((c) => ({
        label: c.simpleName || c.qualifiedName,
        snippet: c.qualifiedName,
        description: c.packageName ? `${c.packageName}` : "",
        tooltip: c.kind,
      })),
    }
    externalFunctionGroups.value = [classGroup]
    externalVariableGroups.value = [...externalVariableGroups.value, depGroup]
  } catch (e) {
    // ignore
  }
}

function isSimpleType(type: string): boolean {
  const simpleTypes = [
    "String","Integer","Long","Double","Float","Boolean",
    "int","long","double","float","boolean",
    "java.lang.String","java.lang.Integer","java.lang.Long","java.lang.Double","java.lang.Float","java.lang.Boolean",
  ]
  return simpleTypes.some((st) => type.includes(st))
}

async function loadMembersForUpstreamTypeInternal() {
  const qn = props.upstreamOutputType
  if (!qn || qn === "OBJECT" || qn === "ARRAY" || typeof qn !== "string" || isSimpleType(qn)) return
  if (!props.projectKey) return
  try {
    const fields = await api.getClassMembers(props.projectKey, { qualifiedName: qn, kind: "fields", page: 1, size: 100 })
    const methods = await api.getClassMembers(props.projectKey, { qualifiedName: qn, kind: "methods", page: 1, size: 200 })
    if (fields.items?.length) {
      const fieldGroup: HelperGroupInput = {
        title: "字段",
        items: fields.items.slice(0, 200).map((f: any) => ({
          label: String(f.name),
          snippet: `${qn}.${String(f.name)}`,
          description: String(f.type || ""),
        })),
      }
      externalVariableGroups.value = [...externalVariableGroups.value, fieldGroup]
    }
    if (methods.items?.length) {
      const methodGroup: HelperGroupInput = {
        title: "方法",
        items: methods.items.slice(0, 300).map((m: any) => ({
          label: String(m.name),
          snippet: `${qn}.${String(m.name)}()`,
          description: String(m.returnType || ""),
          tooltip: m.parametersJson ? String(m.parametersJson) : undefined,
        })),
      }
      externalFunctionGroups.value = [...externalFunctionGroups.value, methodGroup]
    }
  } catch (e) {
    // ignore
  }
}

function closeCodeEditor() {
  showCodeEditor.value = false
  if (codeEditorView) {
    codeEditorView.destroy()
    codeEditorView = null
  }
}

function saveAndCloseEditor() {
  if (codeEditorView) {
    const content = codeEditorView.state.doc.toString()
    emit("update:script", content)
    closeCodeEditor()
  }
}

function initCodeEditor() {
  if (!codeEditorContainer.value || codeEditorView) return
  const currentScript = scriptValue.value
  const saveKeymap = keymap.of([
    {
      key: "Mod-s",
      preventDefault: true,
      run: () => {
        saveAndCloseEditor()
        return true
      },
    },
  ])
  // 构建自动补全源（需要访问 props，所以放在函数内部）
  const completionSource = createCompletionSource(() => variableGroups.value, props.endpointSchema)
  
  const extensions = [
    history(),
    lineNumbers(),
    drawSelection(),
    highlightActiveLine(),
    autocompletion({
      override: [completionSource],
    }),
    keymap.of([...defaultKeymap, ...historyKeymap, ...completionKeymap]),
    saveKeymap,
    EditorView.lineWrapping,
    EditorView.theme({
      "&": {
        fontSize: "14px",
        height: "100%",
      },
      ".cm-scroller": {
        fontFamily: "'Consolas', 'Monaco', 'Courier New', monospace",
        overflow: "auto",
      },
      ".cm-content": {
        height: "400px",
        padding: "12px",
        cursor: "text",
      },
      ".cm-editor": {
        height: "100%",
      },
      ".cm-editor.cm-focused": {
        outline: "none",
      },
      ".cm-editor.cm-focused .cm-content": {
        caretColor: "#2563eb",
      },
      ".cm-line": {
        padding: "0 2px",
      },
    }),
  ]
  const state = EditorState.create({
    doc: currentScript,
    extensions,
  })
  codeEditorView = new EditorView({
    state,
    parent: codeEditorContainer.value,
  })
  nextTick(() => {
    codeEditorView?.focus()
  })
}

function insertSnippet(snippet: string) {
  const content = snippet || ""
  if (codeEditorView) {
    const { state } = codeEditorView
    const selection = state.selection.main
    const transaction = state.update({
      changes: { from: selection.from, to: selection.to, insert: content },
      selection: { anchor: selection.from + content.length },
      scrollIntoView: true,
    })
    codeEditorView.dispatch(transaction)
    codeEditorView.focus()
  } else {
    emit("update:script", `${scriptValue.value}${content}`)
  }
}

watch(
  () => props.script,
  (newScript) => {
    if (codeEditorView && showCodeEditor.value) {
      const currentContent = codeEditorView.state.doc.toString()
      const incoming = newScript ?? ""
      if (currentContent !== incoming) {
        const transaction = codeEditorView.state.update({
          changes: { from: 0, to: codeEditorView.state.doc.length, insert: incoming },
        })
        codeEditorView.dispatch(transaction)
      }
    }
  }
)

onUnmounted(() => {
  if (codeEditorView) {
    codeEditorView.destroy()
    codeEditorView = null
  }
})

// 暴露方法给父组件
defineExpose({
  openCodeEditor,
  closeCodeEditor,
})
</script>

<template>
  <div v-if="!hidePreview" class="section">
    <h3 v-if="resolvedTitle" class="section-title">{{ resolvedTitle }}</h3>
    <div class="script-editor-wrapper">
      <textarea
        class="input textarea code-editor"
        rows="8"
        placeholder="点击此处或下方按钮打开代码编辑器..."
        :value="scriptValue"
        @click="openCodeEditor"
        spellcheck="false"
        readonly
      ></textarea>
      <div class="editor-hint">
        <span class="hint-text">💡 脚本在字段映射之后执行，可访问 <code>ctx</code>、<code>input</code>、<code>output</code>。</span>
        <button class="btn small" type="button" @click="openCodeEditor">打开编辑器</button>
      </div>
    </div>
  </div>

  <!-- 弹框始终渲染，即使 hidePreview 为 true -->

    <div v-if="showCodeEditor" class="code-editor-modal" @click.self="closeCodeEditor">
      <div class="code-editor-modal-content" @click.stop>
        <div class="code-editor-header">
          <h3>{{ resolvedTitle }}</h3>
          <div class="code-editor-actions">
            <button class="btn small" type="button" @click="saveAndCloseEditor">保存并关闭</button>
            <button class="btn small" type="button" @click="closeCodeEditor">取消</button>
          </div>
        </div>
    <div class="code-editor-body" @click.stop>
      <aside class="helper-sidebar">
        <div class="helper-tab-bar" role="tablist">
          <button
            class="helper-tab"
            type="button"
            role="tab"
            :aria-selected="helperTabState === 'variables'"
            :class="{ active: helperTabState === 'variables' }"
            @click="helperTabState = 'variables'"
          >
            变量
          </button>
          <button
            class="helper-tab"
            type="button"
            role="tab"
            :aria-selected="helperTabState === 'functions'"
            :class="{ active: helperTabState === 'functions' }"
            @click="helperTabState = 'functions'"
          >
            函数
          </button>
        </div>
        <div class="helper-scroll">
          <template v-if="activeHelperGroups.length">
            <div v-for="group in activeHelperGroups" :key="group.title" class="helper-group">
              <div class="helper-group-title">{{ group.title }}</div>
              <div class="helper-list">
                <div 
                  v-for="item in group.items" 
                  :key="item.label" 
                  class="helper-item"
                  @click="insertSnippet(item.snippet)"
                  :title="item.tooltip || item.description"
                >
                  <div class="helper-item-header">
                    <div class="helper-item-title">{{ item.label }}</div>
                    <pre class="helper-item-snippet">{{ item.snippet }}</pre>
                  </div>
                  <div 
                    v-if="item.description && !item.tooltip"
                    class="helper-item-desc"
                  >
                    {{ item.description }}
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="helper-empty">当前分组没有可用提示，可通过父组件动态传入。</div>
        </div>
      </aside>
      <div class="code-editor-main">
        <div ref="codeEditorContainer" class="code-editor-container"></div>
      </div>
    </div>
        <div class="code-editor-footer">
          <div class="tip" style="margin: 0; font-size: 11px">
            <strong>提示：</strong>可使用 <kbd>Ctrl+S</kbd>（Mac: <kbd>Cmd+S</kbd>）保存并关闭。
          </div>
        </div>
      </div>
    </div>
</template>

<style scoped>
.script-editor-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.code-editor {
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  font-size: 12px;
  height: 200px;
  cursor: pointer;
  resize: none;
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
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  z-index: 1000;
}

.code-editor-modal-content {
  background: #fff;
  border-radius: 10px;
  width: 1000px;
  max-width: 1000px;
  max-height: 720px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 20px 25px -12px rgba(15, 23, 42, 0.35);
}

.code-editor-header,
.code-editor-footer {
  padding: 14px 18px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.code-editor-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.code-editor-actions {
  display: flex;
  gap: 8px;
}

.code-editor-body {
  height: 520px;
  display: flex;
  gap: 20px;
  overflow: hidden;
}

.helper-sidebar {
  width: 280px;
  border-right: 1px solid #e2e8f0;
  padding-right: 12px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.helper-tab-bar {
  display: flex;
  border-bottom: 1px solid #e2e8f0;
}

.helper-tab {
  flex: 1;
  border: none;
  background: transparent;
  padding: 10px 0;
  font-size: 12px;
  color: #64748b;
  cursor: pointer;
  position: relative;
  transition: color 0.2s ease;
}

.helper-tab::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  background: transparent;
  transition: background 0.2s ease;
}

.helper-tab.active {
  color: #2563eb;
  font-weight: 600;
}

.helper-tab.active::after {
  background: #2563eb;
}

.helper-scroll {
  flex: 1;
  overflow: auto;
  padding-right: 6px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.helper-groups {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.helper-group {
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 6px;
  background: #fafbfc;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  margin-bottom: 8px;
}

.helper-group-title {
  font-size: 11px;
  font-weight: 600;
  color: #334155;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  margin-bottom: 2px;
}

.helper-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.helper-item {
  border: 1px solid rgba(148, 163, 184, 0.15);
  border-radius: 4px;
  padding: 5px 6px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  background: #fff;
  cursor: pointer;
  transition: all 0.15s ease;
  position: relative;
}

.helper-item:hover {
  border-color: #2563eb;
  background: #f8fafc;
  box-shadow: 0 1px 2px rgba(37, 99, 235, 0.08);
}

.helper-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.helper-item-title {
  font-size: 11px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.3;
  white-space: nowrap;
}

.helper-item-desc {
  font-size: 9px;
  color: #64748b;
  line-height: 1.3;
  margin-top: 2px;
}

.helper-item-desc.has-tooltip {
  cursor: help;
}

.helper-item-desc.has-tooltip:hover {
  color: #2563eb;
  text-decoration: underline;
  text-decoration-style: dotted;
}

.helper-item-snippet {
  margin: 0;
  font-size: 10px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  background: #1e293b;
  color: #e2e8f0;
  padding: 2px 5px;
  border-radius: 3px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
  line-height: 1.4;
  border-left: 2px solid #2563eb;
}

.helper-item-example {
  margin: 0;
  font-size: 9px;
  font-family: "JetBrains Mono", "Fira Code", Consolas, monospace;
  background: #f1f5f9;
  color: #475569;
  padding: 3px 5px;
  border-radius: 3px;
  white-space: pre-wrap;
  word-break: break-word;
  border-left: 2px solid #cbd5e1;
  line-height: 1.3;
}

.helper-empty {
  font-size: 11px;
  color: #94a3b8;
  padding: 10px;
  border: 1px dashed rgba(148, 163, 184, 0.5);
  border-radius: 8px;
  background: #f8fafc;
}

.code-editor-main {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.code-editor-container {
  width: 100%;
  height: 100%;
  overflow: auto;
}

.code-editor-footer {
  border-top: 1px solid #e2e8f0;
  border-bottom: none;
  background: #f8fafc;
  font-size: 11px;
}
</style>

