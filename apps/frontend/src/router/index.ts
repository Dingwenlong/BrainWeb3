import { createRouter, createWebHistory } from 'vue-router'
import { ensureAuthSessionLoaded, useActorProfile } from '../composables/useActorProfile'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../pages/LoginPage.vue'),
      meta: {
        requiresAuth: false,
      },
    },
    {
      path: '/',
      name: 'overview',
      component: () => import('../pages/OverviewPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/datasets/:datasetId',
      name: 'dataset-detail',
      component: () => import('../pages/DatasetDetailPage.vue'),
      props: true,
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/access-requests',
      name: 'access-requests',
      component: () => import('../pages/AccessRequestsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/destruction-requests',
      name: 'destruction-requests',
      component: () => import('../pages/DestructionRequestsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/accounts',
      name: 'accounts',
      component: () => import('../pages/AccountsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/identity-center',
      name: 'identity-center',
      component: () => import('../pages/IdentityCenterPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/audits',
      name: 'audits',
      component: () => import('../pages/AuditsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/chain-records',
      name: 'chain-records',
      component: () => import('../pages/ChainRecordsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/training-jobs',
      name: 'training-jobs',
      component: () => import('../pages/TrainingJobsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
    {
      path: '/model-records',
      name: 'model-records',
      component: () => import('../pages/ModelRecordsPage.vue'),
      meta: {
        requiresAuth: true,
      },
    },
  ],
})

router.beforeEach(async (to) => {
  await ensureAuthSessionLoaded()
  const { isAuthenticated } = useActorProfile()
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !isAuthenticated.value) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (to.name === 'login' && isAuthenticated.value) {
    return {
      name: 'overview',
    }
  }

  return true
})

export default router
