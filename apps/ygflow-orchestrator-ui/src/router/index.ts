import { createRouter, createWebHistory } from 'vue-router'
const routes = [
  { path: '/', component: () => import('../pages/ProjectsPage.vue') },
  { path: '/projects/:projectKey/rests', component: () => import('../pages/RestLayout.vue') },
  { path: '/projects/:projectKey/rests/:endpointId', component: () => import('../pages/StudioPage.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
