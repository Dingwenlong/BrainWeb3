import { computed, ref } from 'vue'
import type { AuthActor, AuthSession } from '../types/api'

export interface ActorProfile {
  actorId: string
  actorRole: string
  actorOrg: string
}

interface AuthSessionPayload {
  token: string
  expiresAt: string
  refreshToken: string
  refreshExpiresAt: string
  actorProfile: ActorProfile
  displayName: string
}

interface SessionActorPayload extends AuthActor {}

const STORAGE_KEY = 'brainweb3-auth-session'
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

const initialSession = loadInitialSession()

const actorProfile = ref<ActorProfile>(initialSession?.actorProfile ?? emptyProfile())
const displayName = ref(initialSession?.displayName ?? '')
const authToken = ref(initialSession?.token ?? '')
const expiresAt = ref(initialSession?.expiresAt ?? '')
const refreshToken = ref(initialSession?.refreshToken ?? '')
const refreshExpiresAt = ref(initialSession?.refreshExpiresAt ?? '')
const authReady = ref(false)
const restorePromise = ref<Promise<void> | null>(null)

const isAuthenticated = computed(() => Boolean(authToken.value))
const isAdmin = computed(() => actorProfile.value.actorRole.toLowerCase() === 'admin')

function emptyProfile(): ActorProfile {
  return {
    actorId: '',
    actorRole: '',
    actorOrg: '',
  }
}

function loadInitialSession(): AuthSessionPayload | null {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return null
    }

    const parsed = JSON.parse(raw) as Partial<AuthSessionPayload>
    if (
      !parsed.token ||
      !parsed.expiresAt ||
      !parsed.refreshToken ||
      !parsed.refreshExpiresAt ||
      !parsed.displayName ||
      !parsed.actorProfile?.actorId ||
      !parsed.actorProfile?.actorRole ||
      !parsed.actorProfile?.actorOrg
    ) {
      return null
    }

    return {
      token: parsed.token,
      expiresAt: parsed.expiresAt,
      refreshToken: parsed.refreshToken,
      refreshExpiresAt: parsed.refreshExpiresAt,
      displayName: parsed.displayName,
      actorProfile: {
        actorId: parsed.actorProfile.actorId,
        actorRole: parsed.actorProfile.actorRole,
        actorOrg: parsed.actorProfile.actorOrg,
      },
    }
  } catch {
    return null
  }
}

function persistSession(session: AuthSessionPayload | null) {
  if (typeof window === 'undefined') {
    return
  }

  if (!session) {
    window.localStorage.removeItem(STORAGE_KEY)
    return
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

function applySession(session: AuthSessionPayload | null) {
  if (!session) {
    actorProfile.value = emptyProfile()
    displayName.value = ''
    authToken.value = ''
    expiresAt.value = ''
    refreshToken.value = ''
    refreshExpiresAt.value = ''
    persistSession(null)
    return
  }

  actorProfile.value = session.actorProfile
  displayName.value = session.displayName
  authToken.value = session.token
  expiresAt.value = session.expiresAt
  refreshToken.value = session.refreshToken
  refreshExpiresAt.value = session.refreshExpiresAt
  persistSession(session)
}

async function login(actorId: string, password: string) {
  const payload = await requestAuth<AuthSession>('/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      actorId,
      password,
    }),
  })

  applySession({
    token: payload.token,
    expiresAt: payload.expiresAt,
    refreshToken: payload.refreshToken,
    refreshExpiresAt: payload.refreshExpiresAt,
    displayName: payload.actor.displayName,
    actorProfile: {
      actorId: payload.actor.actorId,
      actorRole: payload.actor.actorRole,
      actorOrg: payload.actor.actorOrg,
    },
  })
}

async function register(input: {
  actorId: string
  displayName: string
  actorOrg: string
  password: string
}) {
  const payload = await requestAuth<AuthSession>('/auth/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(input),
  })

  applySession({
    token: payload.token,
    expiresAt: payload.expiresAt,
    refreshToken: payload.refreshToken,
    refreshExpiresAt: payload.refreshExpiresAt,
    displayName: payload.actor.displayName,
    actorProfile: {
      actorId: payload.actor.actorId,
      actorRole: payload.actor.actorRole,
      actorOrg: payload.actor.actorOrg,
    },
  })
}

async function restoreSession() {
  if (!authToken.value) {
    authReady.value = true
    return
  }

  try {
    const payload = await requestAuth<SessionActorPayload>('/auth/session', {
      headers: {
        Authorization: `Bearer ${authToken.value}`,
      },
    })

    applySession({
      token: authToken.value,
      expiresAt: expiresAt.value,
      refreshToken: refreshToken.value,
      refreshExpiresAt: refreshExpiresAt.value,
      displayName: payload.displayName,
      actorProfile: {
        actorId: payload.actorId,
        actorRole: payload.actorRole,
        actorOrg: payload.actorOrg,
      },
    })
  } catch {
    try {
      const refreshed = await refreshAuthSession()
      if (!refreshed) {
        applySession(null)
      }
    } catch {
      applySession(null)
    }
  } finally {
    authReady.value = true
  }
}

export async function refreshAuthSession() {
  if (!refreshToken.value) {
    return false
  }

  try {
    const payload = await requestAuth<AuthSession>('/auth/refresh', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        refreshToken: refreshToken.value,
      }),
    })

    applySession({
      token: payload.token,
      expiresAt: payload.expiresAt,
      refreshToken: payload.refreshToken,
      refreshExpiresAt: payload.refreshExpiresAt,
      displayName: payload.actor.displayName,
      actorProfile: {
        actorId: payload.actor.actorId,
        actorRole: payload.actor.actorRole,
        actorOrg: payload.actor.actorOrg,
      },
    })
    return true
  } catch {
    applySession(null)
    return false
  }
}

async function parseResponse<T>(response: Response): Promise<T> {
  const text = await response.text()
  const payload = text ? (JSON.parse(text) as unknown) : null

  if (!response.ok) {
    const message =
      typeof payload === 'object' && payload && 'message' in payload
        ? String((payload as { message: string }).message)
        : `请求失败，状态码 ${response.status}`
    throw new Error(message)
  }

  return payload as T
}

async function requestAuth<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, init)
  return parseResponse<T>(response)
}

function logout() {
  applySession(null)
  authReady.value = true
}

export async function ensureAuthSessionLoaded() {
  if (authReady.value) {
    return
  }

  if (!restorePromise.value) {
    restorePromise.value = restoreSession().finally(() => {
      restorePromise.value = null
    })
  }

  await restorePromise.value
}

export function getAuthToken() {
  return authToken.value
}

export function getRefreshToken() {
  return refreshToken.value
}

export function useActorProfile() {
  return {
    actorProfile,
    displayName,
    authReady,
    isAuthenticated,
    isAdmin,
    login,
    register,
    logout,
  }
}
