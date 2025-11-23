<script setup lang="ts">
import { computed, ref, watch } from "vue"
import { api, type FlowVersion } from "../api/client"
import { Clock, CheckCircle2, Circle } from "lucide-vue-next"

const props = defineProps<{
  projectKey: string
  flowCode: string
  currentVersionNo: number | null
  publishedVersionNo: number | null
  visible: boolean
}>()

const emit = defineEmits<{
  (e: "close"): void
  (e: "load-version", version: FlowVersion): void
  (e: "load-version-and-save", version: FlowVersion, publishAfterLoad: boolean): void
}>()

const versions = ref<FlowVersion[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// 格式化时间
function formatTime(timeStr?: string): string {
  if (!timeStr) return "-"
  try {
    const date = new Date(timeStr)
    return date.toLocaleString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit",
    })
  } catch {
    return timeStr
  }
}

// 加载版本列表
async function loadVersions() {
  if (!props.projectKey || !props.flowCode) return
  
  loading.value = true
  error.value = null
  try {
    versions.value = await api.listFlowVersions(props.projectKey, props.flowCode)
    // 按版本号降序排列（最新版本在前）
    versions.value.sort((a, b) => (b.versionNo || 0) - (a.versionNo || 0))
  } catch (err) {
    error.value = err instanceof Error ? err.message : "加载版本列表失败"
    console.error("[VersionList] Failed to load versions:", err)
  } finally {
    loading.value = false
  }
}

// 当弹窗显示时加载版本列表
watch(() => props.visible, (visible) => {
  if (visible) {
    loadVersions()
  }
})

function handleLoadVersion(version: FlowVersion) {
  // 直接加载版本到画布
  emit("load-version", version)
  emit("close")
}

function handleLoadVersionAndSave(version: FlowVersion, publishAfterLoad: boolean = false) {
  // 加载版本并保存为新版本
  emit("load-version-and-save", version, publishAfterLoad)
  emit("close")
}

function handleClose() {
  emit("close")
}
</script>

<template>
  <div v-if="visible" class="version-list-overlay" @click.self="handleClose">
    <div class="version-list-modal">
      <div class="modal-header">
        <h3 class="modal-title">版本列表</h3>
        <button class="close-btn" type="button" @click="handleClose">×</button>
      </div>
      
      <div class="modal-body">
        <div v-if="loading" class="loading">正在加载版本列表...</div>
        <div v-else-if="error" class="error">{{ error }}</div>
        <div v-else-if="versions.length === 0" class="empty">暂无版本</div>
        <div v-else class="version-list">
          <div
            v-for="version in versions"
            :key="version.id"
            class="version-item"
            :class="{
              'version-current': version.versionNo === currentVersionNo,
              'version-published': version.published,
            }"
          >
            <div class="version-content" @click="handleLoadVersion(version, false)">
              <div class="version-header">
                <div class="version-number">
                  <span class="version-no">v{{ version.versionNo }}</span>
                  <span v-if="version.published" class="version-badge published">已发布</span>
                  <span v-if="version.versionNo === currentVersionNo" class="version-badge current">当前</span>
                </div>
                <div class="version-time">
                  <Clock :size="12" />
                  <span>{{ formatTime(version.createTime) }}</span>
                </div>
              </div>
              <div v-if="version.createBy" class="version-author">
                创建者：{{ version.createBy }}
              </div>
            </div>
            <div v-if="version.versionNo !== currentVersionNo" class="version-actions" @click.stop>
              <button 
                class="btn-action btn-publish" 
                type="button"
                @click="handleLoadVersionAndSave(version, true)"
                title="加载并发布为新版本"
              >
                保存并发布
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.version-list-overlay {
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

.version-list-modal {
  background: white;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.modal-header {
  padding: 20px 24px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.modal-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #0f172a;
}

.close-btn {
  background: none;
  border: none;
  font-size: 28px;
  color: #64748b;
  cursor: pointer;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: all 0.2s;
}

.close-btn:hover {
  background: rgba(148, 163, 184, 0.1);
  color: #0f172a;
}

.modal-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.loading,
.error,
.empty {
  text-align: center;
  padding: 40px 20px;
  color: #64748b;
}

.error {
  color: #b91c1c;
}

.version-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.version-item {
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.2);
  border-radius: 8px;
  transition: all 0.2s;
  background: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.version-item:hover {
  border-color: #2563eb;
  background: rgba(37, 99, 235, 0.05);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.15);
}

.version-content {
  flex: 1;
  cursor: pointer;
  min-width: 0;
}

.version-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.btn-action {
  padding: 6px 12px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.btn-save {
  background: white;
  color: #64748b;
}

.btn-save:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
  color: #0f172a;
}

.btn-publish {
  background: #2563eb;
  color: white;
  border-color: #2563eb;
}

.btn-publish:hover {
  background: #1d4ed8;
  border-color: #1d4ed8;
}

.version-item.version-current {
  border-color: #2563eb;
  background: rgba(37, 99, 235, 0.08);
}

.version-item.version-published {
  border-left: 3px solid #10b981;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.version-number {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-no {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.version-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.version-badge.published {
  background: #10b981;
  color: white;
}

.version-badge.current {
  background: #2563eb;
  color: white;
}

.version-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #64748b;
}

.version-author {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}
</style>

