import type { Ref } from "vue"
import type { FlowSettings } from "../../data/flowSettings"
import { downloadRuleFile } from "../../utils/ruleExporter"
import type { CanvasEditorProps } from "./canvasTypes"

export function useRulePreview(flowSettings: Ref<FlowSettings>, props: CanvasEditorProps) {
  function openRulePreview(text: string) {
    const modal = document.createElement("div")
    modal.className = "rule-preview-modal"
    modal.innerHTML = `
    <div class="rule-preview-content">
      <div class="rule-preview-header">
        <div style="font-weight:600">YGFlow 规则预览</div>
        <button class="rule-preview-close" type="button">×</button>
      </div>
      <div class="rule-preview-body">
        <pre>${text.replace(/</g, "&lt;").replace(/>/g, "&gt;")}</pre>
      </div>
      <div class="rule-preview-footer">
        <button class="btn download" type="button">下载 TXT</button>
        <button class="btn close" type="button">关闭</button>
      </div>
    </div>
  `
    document.body.appendChild(modal)
    const close = () => modal.remove()
    modal.addEventListener("click", (event) => {
      if ((event.target as HTMLElement).classList.contains("rule-preview-modal")) close()
    })
    modal.querySelector(".rule-preview-close")?.addEventListener("click", close)
    modal.querySelector(".btn.close")?.addEventListener("click", close)
    modal.querySelector(".btn.download")?.addEventListener("click", () => {
      const filename = flowSettings.value.code || props.flowCode || "flow"
      downloadRuleFile(text, `ygflow-rule-${filename}.txt`)
    })
  }

  return {
    openRulePreview,
  }
}




