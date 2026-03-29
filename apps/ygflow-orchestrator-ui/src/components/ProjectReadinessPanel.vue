<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { RouterLink } from "vue-router"
import { api, type ProjectReadinessResponse } from "../api/client"

const props = defineProps<{
  projectKey: string
  compact?: boolean
}>()

const loading = ref(false)
const errorMessage = ref<string | null>(null)
const readiness = ref<ProjectReadinessResponse | null>(null)

const statusCards = computed(() => {
  const current = readiness.value
  if (!current) return []
  return [
    { key: "orchestration", label: "服务编排", ready: current.readyForOrchestration },
    { key: "smartAssembly", label: "智能装配", ready: current.readyForSmartAssembly },
    { key: "publishing", label: "发布", ready: current.readyForPublishing },
    { key: "sync", label: "同步", ready: current.readyForSync },
  ]
})

const metricItems = computed(() => {
  const current = readiness.value
  if (!current) return []
  return [
    { label: "元数据", value: current.metadataCount },
    { label: "服务组件", value: current.componentEndpointCount },
    { label: "REST 入口", value: current.restEndpointCount },
    { label: "Flows", value: current.flowCount },
    { label: "辅助类", value: current.helperClassCount },
    { label: "已选依赖", value: current.selectedJarCount },
  ]
})

async function loadReadiness() {
  if (!props.projectKey) return
  loading.value = true
  errorMessage.value = null
  try {
    readiness.value = await api.getProjectReadiness(props.projectKey)
  } catch (err) {
    readiness.value = null
    errorMessage.value = err instanceof Error ? err.message : "加载项目就绪度失败"
  } finally {
    loading.value = false
  }
}

watch(
  () => props.projectKey,
  () => {
    void loadReadiness()
  },
  { immediate: true }
)

defineExpose({ reload: loadReadiness })
</script>

<template>
  <section class="readiness-card" :class="{ compact }">
    <header class="readiness-header">
      <div>
        <p class="eyebrow">项目就绪度</p>
        <h3>现在离顺手使用还差什么</h3>
      </div>
      <button class="refresh-btn" type="button" @click="loadReadiness">刷新</button>
    </header>

    <div v-if="loading" class="state-card">正在分析当前项目状态...</div>
    <div v-else-if="errorMessage" class="state-card error">{{ errorMessage }}</div>
    <template v-else-if="readiness">
      <div class="summary-card">
        <div class="summary-title">架构师建议</div>
        <p class="summary-text">{{ readiness.summary || "当前项目状态已同步，可继续检查下面的细项。" }}</p>
      </div>

      <div class="status-grid">
        <article
          v-for="item in statusCards"
          :key="item.key"
          class="status-pill"
          :class="item.ready ? 'status-pill--ready' : 'status-pill--pending'"
        >
          <span class="status-dot"></span>
          <span>{{ item.label }}</span>
        </article>
      </div>

      <div class="metric-grid">
        <div v-for="item in metricItems" :key="item.label" class="metric-item">
          <div class="metric-label">{{ item.label }}</div>
          <div class="metric-value">{{ item.value }}</div>
        </div>
      </div>

      <div class="action-block">
        <div class="issue-title">下一步动作</div>
        <div v-if="readiness.nextActions.length === 0" class="issue-empty success">
          当前没有额外动作建议，可以直接继续编排和发布。
        </div>
        <div v-else class="action-list">
          <article
            v-for="item in readiness.nextActions"
            :key="item.code"
            class="action-item"
            :class="`action-item--${item.level || 'secondary'}`"
          >
            <div class="action-copy">
              <div class="action-name">{{ item.label }}</div>
              <div class="action-detail">{{ item.detail }}</div>
            </div>
            <RouterLink v-if="item.route" class="action-link" :to="item.route">
              去处理
            </RouterLink>
          </article>
        </div>
      </div>

      <div class="issue-section">
        <div class="issue-block">
          <div class="issue-title">阻塞项</div>
          <div v-if="readiness.blockingItems.length === 0" class="issue-empty success">
            当前没有关键阻塞，可以继续编排。
          </div>
          <div v-else class="issue-list">
            <article v-for="item in readiness.blockingItems" :key="item.code" class="issue-item blocking">
              <div class="issue-name">{{ item.title }}</div>
              <div class="issue-detail">{{ item.detail }}</div>
            </article>
          </div>
        </div>

        <div class="issue-block">
          <div class="issue-title">提醒项</div>
          <div v-if="readiness.warningItems.length === 0" class="issue-empty">
            当前没有额外提醒项。
          </div>
          <div v-else class="issue-list">
            <article v-for="item in readiness.warningItems" :key="item.code" class="issue-item warning">
              <div class="issue-name">{{ item.title }}</div>
              <div class="issue-detail">{{ item.detail }}</div>
            </article>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.readiness-card {
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.94);
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.readiness-card.compact {
  padding: 16px;
}

.readiness-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.eyebrow {
  margin: 0 0 6px;
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #4f46e5;
  font-weight: 700;
}

.readiness-header h3 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.refresh-btn {
  border: 1px solid rgba(99, 102, 241, 0.28);
  background: rgba(99, 102, 241, 0.08);
  color: #4338ca;
  border-radius: 999px;
  padding: 8px 14px;
  font-size: 12px;
  cursor: pointer;
}

.state-card,
.summary-card {
  border-radius: 14px;
  padding: 18px;
}

.state-card {
  background: rgba(241, 245, 249, 0.9);
  color: #475569;
}

.state-card.error {
  background: rgba(254, 226, 226, 0.85);
  color: #b91c1c;
}

.summary-card {
  background: linear-gradient(135deg, rgba(238, 242, 255, 0.9), rgba(255, 255, 255, 0.96));
  border: 1px solid rgba(129, 140, 248, 0.2);
}

.summary-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #4f46e5;
}

.summary-text {
  margin: 10px 0 0;
  line-height: 1.7;
  color: #334155;
}

.status-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 10px;
}

.status-pill {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 14px;
  font-size: 13px;
  font-weight: 600;
}

.status-pill--ready {
  background: rgba(220, 252, 231, 0.9);
  color: #166534;
}

.status-pill--pending {
  background: rgba(254, 242, 242, 0.9);
  color: #b91c1c;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
  gap: 10px;
}

.metric-item {
  border-radius: 14px;
  background: linear-gradient(180deg, rgba(238, 242, 255, 0.85), rgba(248, 250, 252, 0.95));
  padding: 12px;
}

.metric-label {
  font-size: 12px;
  color: #64748b;
}

.metric-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 700;
  color: #1e1b4b;
}

.action-block,
.issue-block {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.issue-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.action-list,
.issue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-item,
.issue-item {
  border-radius: 14px;
  padding: 14px;
  border: 1px solid transparent;
}

.action-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  background: rgba(248, 250, 252, 0.92);
}

.action-item--primary {
  border-color: rgba(99, 102, 241, 0.24);
  background: rgba(238, 242, 255, 0.86);
}

.action-item--secondary {
  border-color: rgba(251, 191, 36, 0.24);
  background: rgba(255, 251, 235, 0.9);
}

.action-copy {
  flex: 1;
}

.action-name,
.issue-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.action-detail,
.issue-detail {
  margin-top: 6px;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.action-link {
  flex-shrink: 0;
  border-radius: 999px;
  padding: 8px 12px;
  background: #4338ca;
  color: #fff;
  text-decoration: none;
  font-size: 12px;
  font-weight: 600;
}

.issue-section {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 14px;
}

.issue-empty {
  border-radius: 14px;
  background: rgba(241, 245, 249, 0.82);
  padding: 14px;
  font-size: 13px;
  color: #475569;
}

.issue-empty.success {
  background: rgba(220, 252, 231, 0.72);
  color: #166534;
}

.issue-item.blocking {
  background: rgba(254, 242, 242, 0.9);
  border-color: rgba(248, 113, 113, 0.25);
}

.issue-item.warning {
  background: rgba(255, 247, 237, 0.88);
  border-color: rgba(251, 191, 36, 0.25);
}

@media (max-width: 768px) {
  .readiness-header,
  .action-item {
    flex-direction: column;
  }

  .action-link {
    width: 100%;
    text-align: center;
  }
}
</style>
