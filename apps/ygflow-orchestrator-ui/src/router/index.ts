import { createRouter, createWebHistory } from 'vue-router'
import ProjectsPage from '../pages/ProjectsPage.vue'
import RestLayout from '../pages/RestLayout.vue'
import StudioPage from '../pages/StudioPage.vue'

const routes = [
  { path: '/', component: ProjectsPage },
  { path: '/projects/:projectKey/rests', component: RestLayout },
  { path: '/projects/:projectKey/rests/:endpointId', component: StudioPage },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router
