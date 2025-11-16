import { computed, ref } from "vue"
import type { Ref } from "vue"
import type { FlowSettings } from "../../data/flowSettings"
import { formatTimestamp } from "./flowUtils"

type LayoutDeps = {
  flowSettings: Ref<FlowSettings>
  loadError: Ref<string | null>
  loadingFlow: Ref<boolean>
  saving: Ref<boolean>
  publishing: Ref<boolean>
  lastSavedAt: Ref<string | null>
  currentVersionNo: Ref<number | null>
  publishedVersionNo: Ref<number | null>
  publishError: Ref<string | null>
  saveError: Ref<string | null>
}

export function useLayoutState(deps: LayoutDeps) {
  const paletteCollapsed = ref(false)
  const inspectorCollapsed = ref(false)

  const paletteWidth = computed(() => (paletteCollapsed.value ? 54 : 320))
  const inspectorWidth = computed(() => (inspectorCollapsed.value ? 32 : 360))

  const statusMessage = computed(() => {
    if (deps.loadError.value) return `${deps.loadError.value}`
    if (deps.loadingFlow.value) return "流程加载中..."
    if (deps.saving.value) return "保存草稿中..."
    if (deps.publishing.value) return "发布中..."
    const publishedStatus = (() => {
      if (!deps.publishedVersionNo.value) return ""
      if (deps.currentVersionNo.value && deps.publishedVersionNo.value === deps.currentVersionNo.value) {
        return `已发布 v${deps.publishedVersionNo.value}`
      }
      return `最新发布 v${deps.publishedVersionNo.value}`
    })()
    if (deps.currentVersionNo.value) {
      const suffix = deps.lastSavedAt.value ? `，保存于 ${formatTimestamp(deps.lastSavedAt.value)}` : ""
      const current = `当前版本 v${deps.currentVersionNo.value}${suffix}`
      return publishedStatus ? `${current} / ${publishedStatus}` : current
    }
    return publishedStatus
  })

  const activeError = computed(() => deps.publishError.value || deps.saveError.value)

  const logPreview = computed(() => {
    const policy = deps.flowSettings.value.logPolicy
    if (!policy?.enabled) return "Logging disabled"
    const target = policy.sink === "console" ? "Console" : policy.sink === "kafka" ? "Kafka" : "HTTP Hook"
    return `${policy.level}  -  ${target}`
  })

  function togglePalette() {
    paletteCollapsed.value = !paletteCollapsed.value
  }

  function toggleInspector() {
    inspectorCollapsed.value = !inspectorCollapsed.value
  }

  return {
    paletteCollapsed,
    inspectorCollapsed,
    paletteWidth,
    inspectorWidth,
    statusMessage,
    activeError,
    logPreview,
    togglePalette,
    toggleInspector,
  }
}




