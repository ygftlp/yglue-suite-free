# LitFlow Studio — Vue 版 (Vite)

最小可跑前端：项目列表 → 项目REST列表 → 编排工作台（左组件/模型 + 中画布 + 右属性）。
画布基于 **@vue-flow/core**，支持从左侧拖拽节点到画布、连线、保存草稿（调用 /api/flows）。
无后端时自动使用 **mock** 数据。

## 运行
```bash
npm i
npm run dev
# 默认：http://localhost:5173
# 已配置代理：/api -> http://localhost:8080 （对应你后端）
```

## 路由
- `/` 项目列表
- `/projects/:pid/rests` 项目下的 REST 列表（左侧固定）
- `/projects/:pid/rests/:rid` 编排工作台

## 目录结构
- `src/pages/ProjectsPage.vue` — 项目列表（新建项目）
- `src/pages/RestLayout.vue`  — 左侧 REST 树（新建 REST）+ 右侧内容
- `src/pages/StudioPage.vue`   — 工作台入口（含顶部栏）
- `src/components/Palette.vue` — 左侧：组件 & 数据模型（可拖拽）
- `src/components/CanvasEditor.vue` — 中间画布（Vue Flow）
- `src/components/Inspector.vue` — 右侧节点属性面板
- `src/api/client.ts` — 统一 API（含 mock fallback）

## 对接后端
已对接接口：
- `GET /api/projects`、`POST /api/projects`
- `GET /api/projects/:pid/rests`、`POST /api/rests`
- `POST /api/flows` 保存草稿（`graphJson` 字符串）

## 下一步可扩展
- 接入 IDEA 插件上报：新增 `/api/registry/components`、`/api/registry/models` 数据源替换 Palette 的假数据
- 编译 & 发布按钮：调用后端 `compile` / `release`
- Dry-Run：新增调试面板，展示节点耗时/上下文快照
- 鉴权/多环境：在 `vite.config.ts` 中按环境切换 API 代理