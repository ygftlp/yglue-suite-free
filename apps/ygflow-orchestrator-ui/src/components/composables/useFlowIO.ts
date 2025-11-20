import { computed, ref, watch } from "vue"
import type { Ref } from "vue"
import { api, type FlowVersion } from "../../api/client"
import { createDefaultFlowSettings, type FlowEntrypoint, type FlowSettings } from "../../data/flowSettings"
import { generateLiteFlowRule } from "../../utils/ruleExporter"
import type { CanvasEditorProps } from "./canvasTypes"
import { generateUUID, generateContextKey, normalizeEntrypoint, serializeEntrypointPayload, safeParseContent } from "./flowUtils"
import { useRulePreview } from "./useRulePreview"

type FlowStateBridge = {
  nodes: Ref<any[]>
  edges: Ref<any[]>
  setGraph: (nodes: any[], edges: any[]) => void
  resetGraph: () => void
}

export function useFlowIO(props: CanvasEditorProps, flowState: FlowStateBridge) {
  const flowSettings = ref<FlowSettings>(createDefaultFlowSettings())
  const flowSettingsVisible = ref(false)
  const loadingFlow = ref(false)
  const loadError = ref<string | null>(null)
  const saving = ref(false)
  const publishing = ref(false)
  const saveError = ref<string | null>(null)
  const publishError = ref<string | null>(null)
  const lastSavedAt = ref<string | null>(null)
  const currentVersionNo = ref<number | null>(null)
  const publishedVersionNo = ref<number | null>(null)

  const rulePreview = useRulePreview(flowSettings, props)

  function applyEntrypointHintIfNeeded(force = false) {
    const hinted = normalizeEntrypoint(props.entrypointHint as Partial<FlowEntrypoint> | null)
    if (!hinted) return
    if (force || !flowSettings.value.entrypoint) {
      flowSettings.value.entrypoint = hinted
    }
  }

  function resetEditor(flowCode?: string) {
    flowState.resetGraph()
    const defaults = createDefaultFlowSettings()
    flowSettings.value = {
      ...defaults,
      code: flowCode || generateUUID(),
      name: flowCode || defaults.name,
      entrypoint: normalizeEntrypoint(props.entrypointHint) ?? defaults.entrypoint,
    }
    currentVersionNo.value = null
    lastSavedAt.value = null
    publishedVersionNo.value = null
    publishError.value = null
    saveError.value = null
    loadingFlow.value = false
    saving.value = false
    publishing.value = false
  }

  function applyLoadedVersion(version: FlowVersion, flowCode: string) {
    const payload = safeParseContent<{ nodes?: any[]; edges?: any[]; settings?: FlowSettings }>(version.contentJson)
    const loadedNodes = Array.isArray(payload.nodes) ? payload.nodes : []
    const loadedEdges = Array.isArray(payload.edges) ? payload.edges : []
    flowState.setGraph(loadedNodes, loadedEdges)
    const defaults = createDefaultFlowSettings()
    const loadedSettings = payload.settings && typeof payload.settings === "object" ? payload.settings : {}
    // 从 settings 中移除 entrypoint（如果存在），entrypoint 从 FlowEntryPoint 表加载
    const { entrypoint: _, ...settingsWithoutEntrypoint } = loadedSettings as FlowSettings
    flowSettings.value = {
      ...defaults,
      ...settingsWithoutEntrypoint,
      code: (loadedSettings as FlowSettings).code || flowCode || defaults.code,
      entrypoint: null, // entrypoint 从 FlowEntryPoint 表单独加载
    }
    applyEntrypointHintIfNeeded()
    if (!flowSettings.value.name) {
      flowSettings.value.name = flowCode || defaults.name
    }
    currentVersionNo.value = version.versionNo ?? null
    lastSavedAt.value = version.updateTime || version.createTime || null
  }

  async function loadFlowDefinition() {
    if (!props.projectKey) return
    const code = props.flowCode
    if (!code) {
      resetEditor()
      return
    }
    loadingFlow.value = true
    loadError.value = null
    publishError.value = null
    try {
      const versions = await api.listFlowVersions(props.projectKey, code)
      const publishedVersion = versions.find((item) => item?.published)
      publishedVersionNo.value =
        typeof publishedVersion?.versionNo === "number" ? publishedVersion.versionNo : null
      if (!versions.length) {
        resetEditor(code)
        return
      }
      applyLoadedVersion(versions[0], code)
      
      // 从 FlowEntryPoint 表加载 entrypoint
      try {
        const entrypoint = await api.getFlowEntrypoint(props.projectKey, code)
        if (entrypoint) {
          flowSettings.value.entrypoint = normalizeEntrypoint(entrypoint)
        }
      } catch (err) {
        // 如果 entrypoint 不存在，忽略错误（可能还没有发布过）
        console.debug("未找到 entrypoint:", err)
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : ""
      if (message.includes("404")) {
        resetEditor(code)
        loadError.value = null
      } else {
        loadError.value = message
        resetEditor(code)
      }
    } finally {
      loadingFlow.value = false
    }
  }

  const isLatestPublished = computed(
    () => currentVersionNo.value != null && publishedVersionNo.value === currentVersionNo.value
  )

  const canPublish = computed(() => !saving.value && !publishing.value && !isLatestPublished.value)
  const canSave = computed(() => !saving.value && !publishing.value)

  async function saveDraft() {
    if (!props.projectKey) {
      window.alert("缺少项目标识，无法保存流程")
      return
    }
    let code = (flowSettings.value.code || "").trim()
    if (!code) {
      code = generateUUID()
      flowSettings.value.code = code
      openFlowSettings()
      return
    }
    const name = (flowSettings.value.name || "").trim()
    if (!name) {
      window.alert("请在流程设置中填写流程名称")
      openFlowSettings()
      return
    }
    const txnCounts = flowState.nodes.value.reduce(
      (acc, node) => {
        if (node.type === "transaction") {
          if (node.data?.transaction === "begin") acc.begin += 1
          else if (node.data?.transaction === "end") acc.end += 1
        }
        return acc
      },
      { begin: 0, end: 0 }
    )
    if (txnCounts.begin !== txnCounts.end) {
      window.alert("事务节点需要成对存在，请检查是否缺少开始或结束节点")
      return
    }
    // 清理节点数据：移除 UI 相关字段（position 等）
    const normalizedNodes = flowState.nodes.value.map((node: any) => {
      const { position, ...nodeWithoutPosition } = node
      const data = node.data ? { ...node.data } : {}
      if (data.inputs) {
        data.inputs = data.inputs.map((input: any) => ({
          ...input,
          resolver:
            input.resolver || {
              type: input.sourceType || "request",
              path: input.source || "",
              cast: input.cast || "STRING",
              default: input.default || "",
            },
          converter:
            input.converter || {
              kind: "GENERAL",
              targetType: (input.valueType || "STRING").toUpperCase(),
              targetTypeName: input.typeName || "",
              script: input.transformer || "",
              arrayElementType: input.converter?.arrayElementType || "STRING",
              arrayElementTypeName: input.converter?.arrayElementTypeName || "",
            },
          transformer: input.transformer ?? input.converter?.script ?? "",
        }))
      }
      if (data.output) {
        data.output = {
          ...data.output,
          contextKey: data.output.contextKey || generateContextKey(),
        }
      }
      return { ...nodeWithoutPosition, data }
    })
    
    // 清理边数据：移除 UI 相关字段
    const normalizedEdges = flowState.edges.value.map((edge: any) => {
      const { sourcePosition, targetPosition, ...edgeWithoutUI } = edge
      return edgeWithoutUI
    })
    
    flowSettings.value.code = code
    flowSettings.value.name = name
    
    // 从 settings 中移除 entrypoint，entrypoint 只存储在 FlowEntryPoint 表中
    const { entrypoint, ...settingsWithoutEntrypoint } = flowSettings.value
    const payload = {
      code,
      name,
      contentJson: JSON.stringify({
        nodes: normalizedNodes,
        edges: normalizedEdges,
        settings: { ...settingsWithoutEntrypoint, code, name },
      }),
      createdBy: flowSettings.value.owner?.trim() || undefined,
    }
    saving.value = true
    saveError.value = null
    publishError.value = null
    let savedVersion: FlowVersion | null = null
    try {
      const version = await api.saveFlow(props.projectKey, payload)
      savedVersion = version
      currentVersionNo.value = version.versionNo ?? null
      lastSavedAt.value = version.updateTime || version.createTime || new Date().toISOString()
      window.alert(`草稿已保存，版本号 v${version.versionNo}`)
    } catch (err) {
      const message = err instanceof Error ? err.message : "保存失败"
      saveError.value = message
      window.alert(`保存失败：${message}`)
    } finally {
      saving.value = false
    }
    if (!savedVersion) return
    const preview = generateLiteFlowRule(normalizedNodes, flowState.edges.value, flowSettings.value)
    rulePreview.openRulePreview(preview.text)
  }

  async function publishFlow() {
    if (!props.projectKey) {
      window.alert("缺少项目标识，无法发布流程")
      return
    }
    let code = (flowSettings.value.code || "").trim()
    if (!code) {
      code = generateUUID()
      flowSettings.value.code = code
      openFlowSettings()
      return
    }
    let versionNo = currentVersionNo.value
    if (!versionNo) {
      try {
        await saveDraft()
        versionNo = currentVersionNo.value
        if (!versionNo) {
          window.alert("保存草稿失败，无法发布流程")
          return
        }
      } catch (error) {
        const message = error instanceof Error ? error.message : "保存草稿失败"
        window.alert(`保存草稿时出错：${message}`)
        return
      }
      return
    }
    const normalizedEntrypoint = normalizeEntrypoint(flowSettings.value.entrypoint)
    flowSettings.value.entrypoint = normalizedEntrypoint ?? null
    publishing.value = true
    publishError.value = null
    try {
      const version = await api.publishFlow(props.projectKey, code, {
        versionNo,
        publishedBy: flowSettings.value.owner?.trim() || undefined,
        entrypoint: serializeEntrypointPayload(normalizedEntrypoint ?? null),
      })
      const publishedNo = version?.versionNo ?? versionNo
      publishedVersionNo.value = publishedNo
      window.alert(`流程已发布，版本号 v${publishedNo}`)
    } catch (err) {
      const message = err instanceof Error ? err.message : "发布失败"
      publishError.value = message
      window.alert(`发布失败：${message}`)
    } finally {
      publishing.value = false
    }
  }

  function openFlowSettings() {
    flowSettingsVisible.value = true
  }

  function closeFlowSettings() {
    flowSettingsVisible.value = false
  }

  watch(
    () => props.entrypointHint,
    () => {
      applyEntrypointHintIfNeeded()
    },
    { immediate: true, deep: true }
  )

  watch(
    () => [props.projectKey, props.flowCode] as const,
    () => {
      saveError.value = null
      if (!props.projectKey) return
      if (!props.flowCode) {
        resetEditor()
        return
      }
      loadFlowDefinition()
    },
    { immediate: true }
  )

  return {
    flowSettings,
    flowSettingsVisible,
    loadingFlow,
    loadError,
    saving,
    publishing,
    saveError,
    publishError,
    lastSavedAt,
    currentVersionNo,
    publishedVersionNo,
    isLatestPublished,
    canPublish,
    canSave,
    loadFlowDefinition,
    saveDraft,
    publishFlow,
    openFlowSettings,
    closeFlowSettings,
  }
}

