import { useVueFlow } from "@vue-flow/core"

/**
 * 画布操作相关的 composable
 * 提供缩放、聚焦等画布操作功能
 */
export function useCanvasOperations() {
  const { zoomIn, zoomOut, fitView } = useVueFlow()

  /**
   * 放大画布
   */
  function zoomInCanvas() {
    zoomIn?.({ duration: 200 })
  }

  /**
   * 缩小画布
   */
  function zoomOutCanvas() {
    zoomOut?.({ duration: 200 })
  }

  /**
   * 聚焦画布（适应视图）
   */
  function focusCanvas() {
    fitView?.({ padding: 0.2, duration: 300 })
  }

  return {
    zoomInCanvas,
    zoomOutCanvas,
    focusCanvas,
  }
}




