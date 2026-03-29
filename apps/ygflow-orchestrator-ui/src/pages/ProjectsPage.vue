<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import { RouterLink, useRouter } from "vue-router"
import { Boxes, Plus } from "lucide-vue-next"
import { api, type Project, type ProjectCreatePayload, type ProjectReadinessResponse } from "../api/client"
import { formatTimestamp } from "../utils/formatters"

type ProjectCardState = {
  loading: boolean
  readiness: ProjectReadinessResponse | null
  error: string | null
}

const router = useRouter()

const projects = ref<Array<Project & { projectKey?: string }>>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const readinessByKey = ref<Record<string, ProjectCardState>>({})

const showCreate = ref(false)
const createBusy = ref(false)
const createError = ref<string | null>(null)
const createForm = ref<ProjectCreatePayload>({
  key: "",
  name: "",
})

const hasProjects = computed(() => projects.value.length > 0)

function resolveProjectKey(project: Project & { projectKey?: string }) {
  const key = project.key ?? project.projectKey
  return typeof key === "string" ? key.trim() : ""
}

function getCardState(project: Project & { projectKey?: string }): ProjectCardState {
  const key = resolveProjectKey(project)
  return readinessByKey.value[key] ?? { loading: false, readiness: null, error: null }
}

function stageLabel(readiness: ProjectReadinessResponse | null): string {
  if (!readiness) return "待分析"
  if (readiness.readyForSync) return "已就绪"
  if (readiness.readyForPublishing) return "可发布"
  if (readiness.readyForSmartAssembly) return "可装配"
  if (readiness.readyForOrchestration) return "可编排"
  return "待补齐"
}

function statusChips(readiness: ProjectReadinessResponse | null) {
  if (!readiness) return []
  return [
    { label: "编排", ready: readiness.readyForOrchestration },
    { label: "装配", ready: readiness.readyForSmartAssembly },
    { label: "发布", ready: readiness.readyForPublishing },
    { label: "同步", ready: readiness.readyForSync },
  ]
}

function nextStepText(readiness: ProjectReadinessResponse | null): string {
  if (!readiness) return "等待就绪度分析完成。"
  const firstAction = readiness.nextActions?.[0]
  if (firstAction?.detail) return firstAction.detail
  return readiness.summary || "当前项目已经可以继续向下使用。"
}

async function loadProjectReadiness(projectKey: string) {
  if (!projectKey) return

  readinessByKey.value = {
    ...readinessByKey.value,
    [projectKey]: {
      loading: true,
      readiness: readinessByKey.value[projectKey]?.readiness ?? null,
      error: null,
    },
  }

  try {
    const readiness = await api.getProjectReadiness(projectKey)
    readinessByKey.value = {
      ...readinessByKey.value,
      [projectKey]: {
        loading: false,
        readiness,
        error: null,
      },
    }
  } catch (err) {
    readinessByKey.value = {
      ...readinessByKey.value,
      [projectKey]: {
        loading: false,
        readiness: null,
        error: err instanceof Error ? err.message : "读取就绪度失败",
      },
    }
  }
}

async function loadProjects() {
  loading.value = true
  errorMessage.value = null
  try {
    projects.value = await api.listProjects()
    await Promise.all(projects.value.map((project) => loadProjectReadiness(resolveProjectKey(project))))
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : "加载项目列表失败"
    projects.value = []
  } finally {
    loading.value = false
  }
}

function openProject(project: Project & { projectKey?: string }) {
  const key = resolveProjectKey(project)
  if (!key) {
    window.alert("当前项目缺少标识，暂时无法打开，请先补齐 projectKey。")
    return
  }
  router.push(`/projects/${encodeURIComponent(key)}/rests`)
}

function normalizeProjectKey(raw: string): string {
  return raw
    .trim()
    .replace(/\s+/g, "-")
    .replace(/[^A-Za-z0-9._-]/g, "-")
    .replace(/-+/g, "-")
}

async function handleCreateProject() {
  createBusy.value = true
  createError.value = null

  try {
    const payload: ProjectCreatePayload = {
      key: normalizeProjectKey(createForm.value.key),
      name: createForm.value.name.trim(),
    }

    if (!payload.key) {
      throw new Error("请先填写项目标识")
    }
    if (!payload.name) {
      throw new Error("请先填写项目名称")
    }

    const created = await api.createProject(payload)
    projects.value = [created, ...projects.value]
    createForm.value = { key: "", name: "" }
    showCreate.value = false
    void loadProjectReadiness(created.key)
    router.push(`/projects/${encodeURIComponent(created.key)}/rests`)
  } catch (err) {
    createError.value = err instanceof Error ? err.message : "创建项目失败"
  } finally {
    createBusy.value = false
  }
}

onMounted(() => {
  void loadProjects()
})
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="brand">
        <Boxes :size="22" stroke-width="2.4" />
        <span>YGFlow Studio</span>
      </div>
      <div class="header-actions">
        <button class="header-btn primary" type="button" @click="showCreate = !showCreate">
          <Plus :size="16" />
          <span>{{ showCreate ? "收起创建" : "创建项目" }}</span>
        </button>
        <RouterLink class="link muted" to="/">项目列表</RouterLink>
      </div>
    </header>

    <main class="page-main">
      <section class="hero">
        <p class="eyebrow">Fresh Setup</p>
        <h1>先把项目初始化清楚，再进入服务编排与参数装配</h1>
        <p class="hero-subtitle">
          真正影响落地的不是画布本身，而是第一次进入项目后是否知道下一步该做什么。这里会先帮你看清每个项目当前处在哪个阶段。
        </p>
      </section>

      <section v-if="showCreate || !hasProjects" class="create-card">
        <div class="create-copy">
          <h2>创建一个新的编排项目</h2>
          <p>
            推荐先用稳定、可复用的 `projectKey`，例如 `order-center` 或 `member-domain`。后续 metadata、代码元数据、插件同步都会围绕这个标识聚合。
          </p>
        </div>

        <div class="create-form">
          <label class="field">
            <span>项目标识</span>
            <input
              v-model="createForm.key"
              class="input"
              placeholder="例如 order-center"
              autocomplete="off"
            />
          </label>

          <label class="field">
            <span>项目名称</span>
            <input
              v-model="createForm.name"
              class="input"
              placeholder="例如 订单中心"
              autocomplete="off"
            />
          </label>

          <p class="create-hint">
            实际写入前会自动把空格和非法字符规范成 `-`，避免后续 URL 和同步链路出现歧义。
          </p>

          <p v-if="createError" class="create-error">{{ createError }}</p>

          <div class="create-actions">
            <button class="header-btn primary" type="button" :disabled="createBusy" @click="handleCreateProject">
              {{ createBusy ? "创建中..." : "创建并进入项目" }}
            </button>
            <button
              v-if="showCreate"
              class="header-btn"
              type="button"
              :disabled="createBusy"
              @click="showCreate = false"
            >
              取消
            </button>
          </div>
        </div>
      </section>

      <section class="projects-grid">
        <div v-if="loading" class="project-empty">
          <div class="empty-title">正在加载项目</div>
          <p class="empty-desc">请稍候...</p>
        </div>

        <div v-else-if="errorMessage" class="project-empty error">
          <div class="empty-title">加载失败</div>
          <p class="empty-desc">{{ errorMessage }}</p>
        </div>

        <template v-else-if="hasProjects">
          <article
            v-for="project in projects"
            :key="project.id"
            class="project-card"
            role="button"
            tabindex="0"
            @click="openProject(project)"
            @keyup.enter.prevent="openProject(project)"
          >
            <div class="project-head">
              <div class="project-avatar">
                {{ project.name?.trim()?.[0]?.toUpperCase() || resolveProjectKey(project)[0]?.toUpperCase() || "P" }}
              </div>
              <div class="project-title-group">
                <div class="project-title">{{ project.name }}</div>
                <div class="project-desc">
                  标识：
                  <span class="project-key">{{ resolveProjectKey(project) || "--" }}</span>
                </div>
              </div>
              <div class="project-stage">{{ stageLabel(getCardState(project).readiness) }}</div>
            </div>

            <div class="project-meta">更新时间：{{ formatTimestamp(project.updateTime) }}</div>

            <div class="project-readiness">
              <div v-if="getCardState(project).loading" class="readiness-state">
                正在分析就绪度...
              </div>
              <div v-else-if="getCardState(project).error" class="readiness-state error">
                {{ getCardState(project).error }}
              </div>
              <template v-else>
                <div class="status-row">
                  <span
                    v-for="item in statusChips(getCardState(project).readiness)"
                    :key="`${resolveProjectKey(project)}_${item.label}`"
                    class="status-chip"
                    :class="item.ready ? 'status-chip--ready' : 'status-chip--pending'"
                  >
                    {{ item.label }}
                  </span>
                </div>
                <div class="project-summary">
                  {{ getCardState(project).readiness?.summary || "当前项目状态已同步。" }}
                </div>
                <div class="project-next-step">
                  下一步：{{ nextStepText(getCardState(project).readiness) }}
                </div>
              </template>
            </div>

            <div class="project-arrow">&rarr;</div>
          </article>
        </template>

        <div v-else class="project-empty">
          <div class="empty-title">还没有项目</div>
          <p class="empty-desc">先在上方创建项目，或者让 IDE / 后端同步时自动 ensure 一个项目。</p>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: radial-gradient(circle at top left, #eef2ff 0%, #f8fafc 42%, #ffffff 100%);
  color: #0f172a;
  display: flex;
  flex-direction: column;
}

.page-header {
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px 32px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
}

.brand svg {
  color: #4338ca;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border: 1px solid rgba(148, 163, 184, 0.45);
  background: rgba(255, 255, 255, 0.85);
  color: #334155;
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 13px;
  cursor: pointer;
}

.header-btn.primary {
  border-color: rgba(79, 70, 229, 0.32);
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.96), rgba(37, 99, 235, 0.94));
  color: #ffffff;
}

.header-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.link {
  text-decoration: none;
  font-size: 13px;
}

.page-main {
  flex: 1;
  width: 100%;
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px 32px 56px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.hero {
  max-width: 820px;
}

.eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.2em;
  font-size: 12px;
  color: #6366f1;
  font-weight: 700;
  margin: 0 0 12px;
}

.hero h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.2;
  color: #1e1b4b;
}

.hero-subtitle {
  margin: 14px 0 0;
  font-size: 15px;
  line-height: 1.75;
  color: #475569;
}

.create-card {
  display: grid;
  grid-template-columns: minmax(280px, 1.1fr) minmax(280px, 1fr);
  gap: 18px;
  border: 1px solid rgba(129, 140, 248, 0.25);
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(238, 242, 255, 0.96), rgba(255, 255, 255, 0.98));
  padding: 22px;
}

.create-copy h2 {
  margin: 0 0 10px;
  font-size: 24px;
  color: #1e1b4b;
}

.create-copy p {
  margin: 0;
  color: #475569;
  line-height: 1.7;
}

.create-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.22);
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field span {
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.input {
  border: 1px solid rgba(148, 163, 184, 0.55);
  border-radius: 12px;
  padding: 11px 12px;
  font-size: 14px;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.input:focus {
  outline: none;
  border-color: #4f46e5;
  box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.12);
}

.create-hint {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.create-error {
  margin: 0;
  font-size: 13px;
  color: #b91c1c;
}

.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.projects-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 18px;
}

.project-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 22px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(6px);
  box-shadow: 0 12px 28px -24px rgba(30, 41, 59, 0.65);
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.project-card:focus-visible {
  outline: 2px solid #4338ca;
  outline-offset: 4px;
}

.project-card:hover {
  transform: translateY(-4px);
  border-color: rgba(99, 102, 241, 0.6);
  box-shadow: 0 20px 40px -32px rgba(79, 70, 229, 0.65);
}

.project-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.project-avatar {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(79, 70, 229, 0.22), rgba(14, 165, 233, 0.22));
  border: 1px solid rgba(99, 102, 241, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  color: #312e81;
}

.project-title-group {
  flex: 1;
  min-width: 0;
}

.project-title {
  font-size: 17px;
  font-weight: 700;
  color: #1f2937;
}

.project-desc {
  margin-top: 6px;
  font-size: 13px;
  color: #475569;
  line-height: 1.4;
}

.project-key {
  font-family: "Fira Mono", Consolas, ui-monospace, SFMono-Regular, Menlo, Monaco, "Courier New", monospace;
}

.project-stage {
  border-radius: 999px;
  background: rgba(99, 102, 241, 0.12);
  color: #4338ca;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.project-meta {
  font-size: 12px;
  color: #94a3b8;
}

.project-readiness {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-radius: 16px;
  background: rgba(248, 250, 252, 0.92);
  padding: 14px;
  border: 1px solid rgba(226, 232, 240, 0.85);
}

.readiness-state {
  font-size: 13px;
  color: #475569;
}

.readiness-state.error {
  color: #b91c1c;
}

.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.status-chip {
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  font-weight: 700;
}

.status-chip--ready {
  background: rgba(220, 252, 231, 0.9);
  color: #166534;
}

.status-chip--pending {
  background: rgba(254, 242, 242, 0.92);
  color: #b91c1c;
}

.project-summary {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
}

.project-next-step {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
}

.project-arrow {
  margin-left: auto;
  font-size: 20px;
  color: #94a3b8;
  transition: color 0.18s ease;
}

.project-card:hover .project-arrow {
  color: #4f46e5;
}

.project-empty {
  grid-column: 1 / -1;
  padding: 52px 24px;
  border-radius: 20px;
  border: 1px dashed rgba(148, 163, 184, 0.65);
  background: rgba(255, 255, 255, 0.9);
  text-align: center;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.6);
}

.project-empty.error {
  border-color: rgba(248, 113, 113, 0.4);
}

.empty-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e1b4b;
  margin-bottom: 8px;
}

.empty-desc {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

@media (max-width: 860px) {
  .create-card {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-header {
    padding: 16px 20px 0;
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: space-between;
  }

  .page-main {
    padding: 24px 20px 48px;
  }

  .hero h1 {
    font-size: 28px;
  }

  .project-head {
    flex-direction: column;
  }

  .project-stage {
    align-self: flex-start;
  }
}
</style>
