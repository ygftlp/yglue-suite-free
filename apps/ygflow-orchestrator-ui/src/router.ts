import { createRouter, createWebHistory } from 'vue-router'
import ProjectsPage from './pages/ProjectsPage.vue'
import RestLayout from './pages/RestLayout.vue'
import StudioPage from './pages/StudioPage.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: ProjectsPage },
    { path: '/projects/:projectKey/rests', component: RestLayout },
    { path: '/studio/:projectKey/:flowCode', component: StudioPage }
  ]
})
