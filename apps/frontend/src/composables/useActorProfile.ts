import { ref } from 'vue'

export interface ActorProfile {
  actorId: string
  actorRole: string
  actorOrg: string
}

const STORAGE_KEY = 'brainweb3-actor-profile'

const DEFAULT_PROFILE: ActorProfile = {
  actorId: 'researcher-01',
  actorRole: 'researcher',
  actorOrg: 'Sichuan Neuro Lab',
}

const actorProfile = ref<ActorProfile>(loadInitialProfile())

function loadInitialProfile(): ActorProfile {
  if (typeof window === 'undefined') {
    return DEFAULT_PROFILE
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return DEFAULT_PROFILE
    }

    const parsed = JSON.parse(raw) as Partial<ActorProfile>
    if (!parsed.actorId || !parsed.actorRole || !parsed.actorOrg) {
      return DEFAULT_PROFILE
    }

    return {
      actorId: parsed.actorId,
      actorRole: parsed.actorRole,
      actorOrg: parsed.actorOrg,
    }
  } catch {
    return DEFAULT_PROFILE
  }
}

function persist(profile: ActorProfile) {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(profile))
}

export function useActorProfile() {
  function setActorProfile(profile: ActorProfile) {
    actorProfile.value = profile
    persist(profile)
  }

  return {
    actorProfile,
    setActorProfile,
    defaultActorProfile: DEFAULT_PROFILE,
  }
}
