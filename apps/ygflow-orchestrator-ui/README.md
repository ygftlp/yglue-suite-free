# YGFlow Studio — Vue 版 (Vite)

最小可跑前端：项目列表 -> 项目 REST 列表 -> 编排工作台（左侧组件与模型、中间画布、右侧属性面板）。
画布基于 `@vue-flow/core`，支持拖拽节点、连线、保存草稿，并在后端不可用时自动回退到 mock 数据。

## 运行

```bash
npm install
npm run dev
# 默认：http://localhost:5173
# 已配置代理：/api -> http://localhost:8080
```

## 路由

- `/` 项目列表
- `/projects/:pid/rests` 项目下的 REST 列表
- `/projects/:pid/rests/:rid` 编排工作台

## 目录结构

- `src/pages/ProjectsPage.vue`：项目列表入口
- `src/pages/RestLayout.vue`：REST 列表与布局容器
- `src/pages/StudioPage.vue`：流程编排工作台
- `src/components/Palette.vue`：左侧节点与模型面板
- `src/components/CanvasEditor.vue`：中心画布
- `src/components/Inspector.vue`：右侧属性面板
- `src/api/client.ts`：统一 API 层，包含 mock fallback

## 对接后端

- `GET /api/projects`、`POST /api/projects`
- `GET /api/projects/:pid/rests`、`POST /api/rests`
- `POST /api/flows` 保存草稿

## 后续方向

- 接入 IDEA 插件上报的组件与模型元数据
- 增加编译与发布入口
- 提供运行预览与调试面板
- 支持按环境切换 API 代理
