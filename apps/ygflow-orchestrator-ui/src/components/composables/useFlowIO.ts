import { computed, nextTick, ref, watch } from "vue"
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
  // 保存已保存版本的内容快照，用于检测内容是否有变化
  const savedContentSnapshot = ref<string | null>(null)

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
    savedContentSnapshot.value = null
    publishError.value = null
    saveError.value = null
    loadingFlow.value = false
    saving.value = false
    publishing.value = false
  }

  async function applyLoadedVersion(version: FlowVersion, flowCode: string) {
    console.log("[useFlowIO] applyLoadedVersion called:", { flowCode, versionNo: version.versionNo, contentJsonLength: version.contentJson?.length })
    const payload = safeParseContent<{ nodes?: any[]; edges?: any[]; settings?: FlowSettings }>(version.contentJson)
    console.log("[useFlowIO] Parsed payload:", { 
      hasNodes: Array.isArray(payload.nodes), 
      hasEdges: Array.isArray(payload.edges),
      nodesType: Array.isArray(payload.nodes) ? payload.nodes.length : typeof payload.nodes,
      edgesType: Array.isArray(payload.edges) ? payload.edges.length : typeof payload.edges,
      payloadKeys: Object.keys(payload)
    })
    let loadedNodes = Array.isArray(payload.nodes) ? payload.nodes : []
    const loadedEdges = Array.isArray(payload.edges) ? payload.edges : []
    
    // 确保每个节点都有 position 属性（Vue Flow 必需）
    // 如果保存时移除了 position，这里需要恢复默认值
    loadedNodes = loadedNodes.map((node: any, index: number) => {
      if (!node.position || typeof node.position !== 'object' || 
          typeof node.position.x !== 'number' || typeof node.position.y !== 'number') {
        // 如果没有 position，设置默认位置（网格布局）
        const cols = Math.ceil(Math.sqrt(loadedNodes.length))
        const row = Math.floor(index / cols)
        const col = index % cols
        return {
          ...node,
          position: {
            x: col * 250 + 100,
            y: row * 150 + 100
          }
        }
      }
      return node
    })
    
    console.log("[useFlowIO] Applying loaded version:", { 
      nodesCount: loadedNodes.length, 
      edgesCount: loadedEdges.length,
      nodes: loadedNodes.map(n => ({ 
        id: n.id, 
        type: n.type, 
        hasPosition: !!n.position,
        position: n.position,
        data: n.data ? Object.keys(n.data) : [] 
      })),
      edges: loadedEdges.map(e => ({ id: e.id, source: e.source, target: e.target }))
    })
    
    // 使用 nextTick 确保 Vue Flow 能正确响应数据变化
    await nextTick()
    flowState.setGraph(loadedNodes, loadedEdges)
    
    // 再次等待 nextTick 验证设置是否成功
    await nextTick()
    console.log("[useFlowIO] Graph set successfully, verifying:", {
      nodesCount: flowState.nodes.value.length,
      edgesCount: flowState.edges.value.length,
      nodes: flowState.nodes.value.map(n => ({ 
        id: n.id, 
        type: n.type, 
        hasPosition: !!n.position,
        position: n.position 
      })),
      edges: flowState.edges.value.map(e => ({ id: e.id, source: e.source, target: e.target }))
    })
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
    // 保存当前版本的内容快照
    savedContentSnapshot.value = getCurrentContentSnapshot()
  }

  async function loadFlowDefinition() {
    if (!props.projectKey) return
    const code = props.flowCode
    console.log("[useFlowIO] loadFlowDefinition called:", { projectKey: props.projectKey, flowCode: code })
    if (!code) {
      console.log("[useFlowIO] flowCode is empty, resetting editor")
      resetEditor()
      return
    }
    loadingFlow.value = true
    loadError.value = null
    publishError.value = null
    try {
      console.log("[useFlowIO] Loading flow versions for code:", code)
      const versions = await api.listFlowVersions(props.projectKey, code)
      console.log("[useFlowIO] Loaded versions:", versions.length, versions)
      const publishedVersion = versions.find((item) => item?.published)
      publishedVersionNo.value =
        typeof publishedVersion?.versionNo === "number" ? publishedVersion.versionNo : null
      if (!versions.length) {
        console.log("[useFlowIO] No versions found, resetting editor")
        resetEditor(code)
        return
      }
      // 优先加载已发布的版本，如果没有已发布的版本则加载最新版本（第一个）
      const versionToLoad = publishedVersion || versions[0]
      console.log("[useFlowIO] Loading version:", versionToLoad.versionNo, "published:", !!publishedVersion)
      await applyLoadedVersion(versionToLoad, code)
      
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

  // 生成当前内容的快照（用于比较是否有变化）
  function getCurrentContentSnapshot(): string {
    const normalizedNodes = flowState.nodes.value.map((node: any) => {
      const { position, selected, ...nodeWithoutUI } = node
      return nodeWithoutUI
    })
    const normalizedEdges = flowState.edges.value.map((edge: any) => {
      const { sourcePosition, targetPosition, selected, ...edgeWithoutUI } = edge
      return edgeWithoutUI
    })
    const { entrypoint, ...settingsWithoutEntrypoint } = flowSettings.value
    return JSON.stringify({
      nodes: normalizedNodes,
      edges: normalizedEdges,
      settings: settingsWithoutEntrypoint,
    })
  }

  // 检测内容是否有变化
  const hasContentChanges = computed(() => {
    if (!savedContentSnapshot.value) {
      // 如果没有保存过，检查是否有内容
      return flowState.nodes.value.length > 0 || flowState.edges.value.length > 0
    }
    const currentSnapshot = getCurrentContentSnapshot()
    return currentSnapshot !== savedContentSnapshot.value
  })

  // 发布按钮的显示逻辑：只要不是已发布状态，就显示"发布"按钮
  // 不需要先保存草稿，只要有内容就可以显示发布按钮
  const showPublishButton = computed(() => {
    console.log("[showPublishButton] 计算:", {
      isLatestPublished: isLatestPublished.value,
      currentVersionNo: currentVersionNo.value,
      publishedVersionNo: publishedVersionNo.value,
      hasNodes: flowState.nodes.value.length > 0,
      hasEdges: flowState.edges.value.length > 0
    })
    // 如果当前版本已发布，显示"已发布"
    if (isLatestPublished.value) {
      return false
    }
    // 其他情况都显示"发布"按钮（包括没有保存过的情况）
    return true
  })
  
  // 发布按钮是否可用：只要不是已发布状态就可以发布
  const canPublish = computed(() => {
    // 如果正在保存或发布，不能发布
    if (saving.value || publishing.value) {
      return false
    }
    // 如果当前版本已发布，不能发布
    if (isLatestPublished.value) {
      return false
    }
    // 只要有内容就可以发布（不需要检查是否有变化）
    // 因为用户可能想要重新发布当前版本
    return flowState.nodes.value.length > 0 || flowState.edges.value.length > 0
  })
  const canSave = computed(() => !saving.value && !publishing.value)

  // 静默保存草稿（不显示提示），用于发布前的自动保存
  async function saveDraftSilently() {
    if (!props.projectKey) {
      throw new Error("缺少项目标识，无法保存流程")
    }
    let code = (flowSettings.value.code || "").trim()
    if (!code) {
      code = generateUUID()
      flowSettings.value.code = code
      throw new Error("流程代码为空")
    }
    const name = (flowSettings.value.name || "").trim()
    if (!name) {
      throw new Error("流程名称为空")
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
      throw new Error("事务节点需要成对存在")
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
    try {
      const version = await api.saveFlow(props.projectKey, payload)
      currentVersionNo.value = version.versionNo ?? null
      lastSavedAt.value = version.updateTime || version.createTime || new Date().toISOString()
      // 更新内容快照
      savedContentSnapshot.value = getCurrentContentSnapshot()
      // 不显示 alert，静默保存
      return version
    } catch (err) {
      const message = err instanceof Error ? err.message : "保存失败"
      saveError.value = message
      throw err
    } finally {
      saving.value = false
    }
  }

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
      // 更新内容快照
      savedContentSnapshot.value = getCurrentContentSnapshot()
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
    console.log("[publishFlow] 开始发布流程")
    
    if (!props.projectKey) {
      const msg = "缺少项目标识，无法发布流程"
      console.error("[publishFlow]", msg)
      window.alert(msg)
      return
    }
    
    let code = (flowSettings.value.code || "").trim()
    if (!code) {
      console.log("[publishFlow] 流程代码为空，生成新代码")
      code = generateUUID()
      flowSettings.value.code = code
      window.alert("流程代码为空，请先设置流程代码")
      openFlowSettings()
      return
    }
    
    let versionNo = currentVersionNo.value
    console.log("[publishFlow] 当前版本号:", versionNo)
    
    // 如果没有版本号，先自动保存获取版本号（静默保存，不显示提示）
    if (!versionNo) {
      console.log("[publishFlow] 没有版本号，自动保存并发布")
      try {
        // 静默保存，不显示 alert
        await saveDraftSilently()
        versionNo = currentVersionNo.value
        console.log("[publishFlow] 自动保存后的版本号:", versionNo)
        if (!versionNo) {
          const msg = "自动保存失败，无法发布流程"
          console.error("[publishFlow]", msg)
          window.alert(msg)
          return
        }
        console.log("[publishFlow] 自动保存成功，继续发布")
        // 保存成功后继续执行发布逻辑
      } catch (error) {
        const message = error instanceof Error ? error.message : "自动保存失败"
        console.error("[publishFlow] 自动保存出错:", error)
        window.alert(`自动保存时出错：${message}`)
        return
      }
    }
    
    // 执行发布逻辑
    console.log("[publishFlow] 开始执行发布，版本号:", versionNo)
    const normalizedEntrypoint = normalizeEntrypoint(flowSettings.value.entrypoint)
    flowSettings.value.entrypoint = normalizedEntrypoint ?? null
    publishing.value = true
    publishError.value = null
    
    try {
      console.log("[publishFlow] 调用发布 API")
      const version = await api.publishFlow(props.projectKey, code, {
        versionNo: versionNo!,
        publishedBy: flowSettings.value.owner?.trim() || undefined,
        entrypoint: serializeEntrypointPayload(normalizedEntrypoint ?? null),
      })
      const publishedNo = version?.versionNo ?? versionNo
      publishedVersionNo.value = publishedNo
      // 发布成功后更新内容快照（因为发布会保存当前版本）
      savedContentSnapshot.value = getCurrentContentSnapshot()
      console.log("[publishFlow] 发布成功，版本号:", publishedNo)
      window.alert(`✅ 流程已发布，版本号 v${publishedNo}`)
    } catch (err) {
      const message = err instanceof Error ? err.message : "发布失败"
      console.error("[publishFlow] 发布失败:", err)
      publishError.value = message
      window.alert(`❌ 发布失败：${message}`)
    } finally {
      publishing.value = false
      console.log("[publishFlow] 发布流程结束")
    }
  }

  function openFlowSettings() {
    flowSettingsVisible.value = true
  }

  function closeFlowSettings() {
    flowSettingsVisible.value = false
  }
  
  // 版本列表相关
  const versionListVisible = ref(false)
  
  function openVersionList() {
    versionListVisible.value = true
  }
  
  function closeVersionList() {
    versionListVisible.value = false
  }
  
  // 加载指定版本到画布（仅加载，不保存）
  async function loadVersion(version: FlowVersion) {
    if (!props.projectKey || !flowSettings.value.code) {
      console.error("[useFlowIO] Cannot load version: missing projectKey or flowCode")
      return
    }
    
    try {
      loadingFlow.value = true
      loadError.value = null
      const code = flowSettings.value.code
      console.log("[useFlowIO] Loading version:", version.versionNo, "for flow:", code)
      
      // 使用传入的版本数据，如果缺少 contentJson 则重新获取
      let versionData = version
      if (!version.contentJson) {
        versionData = await api.getFlowVersion(props.projectKey, code, version.versionNo)
      }
      
      await applyLoadedVersion(versionData, code)
      
      // 从 FlowEntryPoint 表加载 entrypoint（如果已发布）
      if (version.published) {
        try {
          const entrypoint = await api.getFlowEntrypoint(props.projectKey, code)
          if (entrypoint) {
            flowSettings.value.entrypoint = normalizeEntrypoint(entrypoint)
          }
        } catch (err) {
          console.debug("未找到 entrypoint:", err)
        }
      }
      
      // 注意：这里不更新 currentVersionNo，因为加载的是历史版本
      // 用户需要手动保存才会创建新版本
      console.log("[useFlowIO] Version loaded successfully:", version.versionNo)
    } catch (err) {
      const message = err instanceof Error ? err.message : "加载版本失败"
      loadError.value = message
      console.error("[useFlowIO] Failed to load version:", err)
      window.alert(`加载版本失败：${message}`)
    } finally {
      loadingFlow.value = false
    }
  }
  
  // 加载版本并保存为新版本
  async function loadVersionAndSave(version: FlowVersion, publishAfterLoad: boolean = false) {
    if (!props.projectKey || !flowSettings.value.code) {
      console.error("[useFlowIO] Cannot load version: missing projectKey or flowCode")
      return
    }
    
    try {
      loadingFlow.value = true
      loadError.value = null
      const code = flowSettings.value.code
      console.log("[useFlowIO] Loading version:", version.versionNo, "for flow:", code)
      
      // 使用传入的版本数据，如果缺少 contentJson 则重新获取
      let versionData = version
      if (!version.contentJson) {
        versionData = await api.getFlowVersion(props.projectKey, code, version.versionNo)
      }
      
      await applyLoadedVersion(versionData, code)
      
      // 从 FlowEntryPoint 表加载 entrypoint（如果已发布）
      if (version.published) {
        try {
          const entrypoint = await api.getFlowEntrypoint(props.projectKey, code)
          if (entrypoint) {
            flowSettings.value.entrypoint = normalizeEntrypoint(entrypoint)
          }
        } catch (err) {
          console.debug("未找到 entrypoint:", err)
        }
      }
      
      // 加载版本后，保存为新版本
      console.log("[useFlowIO] Saving loaded version as new version...")
      await saveDraftSilently()
      
      // 如果选择发布，则发布当前版本
      if (publishAfterLoad) {
        console.log("[useFlowIO] Publishing loaded version...")
        await publishFlow()
        // publishFlow 内部已经有提示，这里不需要重复提示
      }
      
      console.log("[useFlowIO] Version loaded and saved successfully")
    } catch (err) {
      const message = err instanceof Error ? err.message : "加载版本失败"
      loadError.value = message
      console.error("[useFlowIO] Failed to load version:", err)
      window.alert(`加载版本失败：${message}`)
    } finally {
      loadingFlow.value = false
    }
  }

  watch(
    () => props.entrypointHint,
    () => {
      applyEntrypointHintIfNeeded()
    },
    { immediate: true, deep: true }
  )

  // 记录上次加载的 flowCode，避免重复加载
  let lastLoadedFlowCode: string | undefined = undefined
  // 记录是否已经初始化过，避免在初始化时因为 flowCode 为 undefined 而重置编辑器
  let initialized = false

  watch(
    () => [props.projectKey, props.flowCode] as const,
    ([projectKey, flowCode]) => {
      console.log("[useFlowIO] Watch triggered:", { 
        projectKey, 
        flowCode, 
        lastLoadedFlowCode, 
        loadingFlow: loadingFlow.value,
        initialized 
      })
      saveError.value = null
      if (!projectKey) return
      
      // 如果正在加载，跳过
      if (loadingFlow.value) {
        console.log("[useFlowIO] Already loading, skipping")
        return
      }
      
      // 如果 flowCode 没有变化，跳过
      if (flowCode === lastLoadedFlowCode) {
        console.log("[useFlowIO] flowCode unchanged, skipping")
        return
      }
      
      // 如果 flowCode 为空
      if (!flowCode) {
        // 如果已经初始化过，且 flowCode 从有值变为空，才重置编辑器
        // 这样可以避免在 endpoint 加载完成之前就重置编辑器
        if (initialized && lastLoadedFlowCode !== undefined) {
          console.log("[useFlowIO] flowCode changed from", lastLoadedFlowCode, "to undefined, resetting editor")
          lastLoadedFlowCode = undefined
          resetEditor()
        } else {
          console.log("[useFlowIO] flowCode is empty but not initialized yet, waiting for endpoint to load")
        }
        return
      }
      
      // 标记为已初始化
      initialized = true
      lastLoadedFlowCode = flowCode
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
    showPublishButton,
    canPublish,
    canSave,
    loadFlowDefinition,
    saveDraft,
    publishFlow,
    openFlowSettings,
    closeFlowSettings,
    versionListVisible,
    openVersionList,
    closeVersionList,
    loadVersion,
    loadVersionAndSave,
  }
}

