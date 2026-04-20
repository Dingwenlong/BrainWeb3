import { createRouter, createWebHistory } from 'vue-router'
import OverviewPage from '../pages/OverviewPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'overview',
      component: OverviewPage,
    },
    {
      path: '/datasets/:datasetId',
      name: 'dataset-detail',
      component: () => import('../pages/DatasetDetailPage.vue'),
      props: true,
    },
    {
      path: '/access-requests',
      name: 'access-requests',
      component: () => import('../pages/AccessRequestsPage.vue'),
    },
  ],
})

export default router
