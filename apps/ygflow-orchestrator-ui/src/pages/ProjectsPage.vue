<script setup lang="ts">
import { computed, onMounted, ref } from "vue"
import { useRouter, RouterLink } from "vue-router"
import { Boxes } from "lucide-vue-next"
import { api, type Project } from "../api/client"
import { formatTimestamp } from "../utils/formatters"

const router = useRouter()

const projects = ref<Array<Project & { projectKey?: string }>>([])
const loading = ref(false)
const errorMessage = ref<string | null>(null)

const hasProjects = computed(() => projects.value.length > 0)

function resolveProjectKey(project: Project & { projectKey?: string }) {
  const key = project.key ?? project.projectKey
  return typeof key === "string" ? key.trim() : ""
}

async function loadProjects() {
  loading.value = true
  errorMessage.value = null
  try {
    projects.value = await api.listProjects()
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
    window.alert("当前项目缺少标识，无法打开，请先在后端补齐。")
    return
  }
  router.push(`/projects/${encodeURIComponent(key)}/rests`)
}


onMounted(loadProjects)
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div class="brand">
        <Boxes :size="22" stroke-width="2.4" />
        <span>YGFlow Studio</span>
      </div>
      <RouterLink class="link muted" to="/">项目列表</RouterLink>
    </header>

    <main class="page-main">
      <section class="hero">
        <p class="eyebrow">项目入口</p>
        <h1>选择项目管理 REST 接口入口</h1>
        <p class="hero-subtitle">
          数据来自 Orchestrator 后端，进入项目即可维护 REST 接口并继续编排流程。
        </p>
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
          <div
            v-for="project in projects"
            :key="project.id"
            class="project-card"
            role="button"
            tabindex="0"
            @click="openProject(project)"
            @keyup.enter.prevent="openProject(project)"
          >
            <div class="project-avatar">
              {{ project.name?.trim()?.[0]?.toUpperCase() || resolveProjectKey(project)[0]?.toUpperCase() || "P" }}
            </div>
            <div class="project-body">
              <div class="project-title">{{ project.name }}</div>
              <div class="project-desc">
                标识：<span class="project-key">{{ resolveProjectKey(project) || "—" }}</span>
              </div>
              <div class="project-meta">
                更新时间：{{ formatTimestamp(project.updateTime) }}
              </div>
            </div>
            <div class="project-arrow">&rarr;</div>
          </div>
        </template>

        <div v-else class="project-empty">
          <div class="empty-title">暂无项目</div>
          <p class="empty-desc">请在后端或 IDE 中创建项目后刷新页面。</p>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.page {
  min-height: 100%;
  background: radial-gradient(circle at top left, #eef2ff 0%, #f8fafc 40%, #ffffff 100%);
  color: #0f172a;
  display: flex;
  flex-direction: column;
}

.page-header {
  width: 100%;
  max-width: 1120px;
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
  font-weight: 600;
}

.brand svg {
  color: #4338ca;
}

.link {
  text-decoration: none;
  font-size: 13px;
}

.link:hover {
  color: #4338ca;
}

.page-main {
  flex: 1;
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 32px 32px 56px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 28px;
}

.hero {
  text-align: center;
  max-width: 640px;
}

.eyebrow {
  text-transform: uppercase;
  letter-spacing: 0.2em;
  font-size: 12px;
  color: #6366f1;
  font-weight: 600;
  margin-bottom: 12px;
}

.hero h1 {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  color: #1e1b4b;
}

.hero-subtitle {
  margin: 12px 0 0;
  font-size: 15px;
  line-height: 1.7;
  color: #475569;
}

.projects-grid {
  width: 100%;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.project-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 22px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: rgba(255, 255, 255, 0.85);
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

.project-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.project-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
}

.project-desc {
  font-size: 13px;
  color: #475569;
  line-height: 1.4;
}

.project-key {
  font-family: "Fira Mono", Consolas, ui-monospace, SFMono-Regular, Menlo, Monaco, "Courier New", monospace;
}

.project-meta {
  font-size: 12px;
  color: #94a3b8;
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
  color: #b91c1c;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e1b4b;
  margin-bottom: 8px;
}

.empty-desc {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

@media (max-width: 640px) {
  .page-header {
    padding: 16px 20px 0;
  }

  .page-main {
    padding: 24px 20px 48px;
  }

  .project-card {
    padding: 18px 20px;
  }
}
</style>
