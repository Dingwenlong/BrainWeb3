import type { ActivityFrame, BrainRegion } from '../types/api'

export interface HeatAnchor {
  code: string
  center: [number, number, number]
  radius: number
  falloff: number
}

export interface HeatRegionState extends HeatAnchor {
  intensity: number
}

const DEFAULT_RADIUS = 0.88
const DEFAULT_FALLOFF = 1.36

export const HEAT_ANCHORS: Record<string, HeatAnchor> = {
  LEFT_FRONTAL: {
    code: 'LEFT_FRONTAL',
    center: [-0.76, 0.44, -1.24],
    radius: 0.66,
    falloff: 1.34,
  },
  RIGHT_FRONTAL: {
    code: 'RIGHT_FRONTAL',
    center: [0.76, 0.44, -1.24],
    radius: 0.66,
    falloff: 1.34,
  },
  LEFT_PARIETAL: {
    code: 'LEFT_PARIETAL',
    center: [-0.78, 0.92, 0.18],
    radius: 0.68,
    falloff: 1.36,
  },
  RIGHT_PARIETAL: {
    code: 'RIGHT_PARIETAL',
    center: [0.78, 0.92, 0.18],
    radius: 0.68,
    falloff: 1.36,
  },
  LEFT_TEMPORAL: {
    code: 'LEFT_TEMPORAL',
    center: [-1.18, -0.38, 0.02],
    radius: 0.66,
    falloff: 1.3,
  },
  RIGHT_TEMPORAL: {
    code: 'RIGHT_TEMPORAL',
    center: [1.18, -0.38, 0.02],
    radius: 0.66,
    falloff: 1.3,
  },
  OCCIPITAL: {
    code: 'OCCIPITAL',
    center: [-0.08, 0.14, 1.54],
    radius: 0.74,
    falloff: 1.34,
  },
  CENTRAL_MOTOR: {
    code: 'CENTRAL_MOTOR',
    center: [0, 0.74, -0.12],
    radius: 0.58,
    falloff: 1.28,
  },
}

function clamp01(value: number) {
  return Math.max(0, Math.min(1, value))
}

export function normalizeRegionIntensity(value: number | undefined | null) {
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return 0
  }

  return clamp01(value)
}

export function getHeatRegionStates(regions: BrainRegion[], frame: ActivityFrame | null): HeatRegionState[] {
  return regions.map((region) => {
    const anchor = HEAT_ANCHORS[region.code] ?? {
      code: region.code,
      center: [0, 0.44, 0.16] as [number, number, number],
      radius: DEFAULT_RADIUS,
      falloff: DEFAULT_FALLOFF,
    }

    return {
      ...anchor,
      intensity: normalizeRegionIntensity(frame?.intensities[region.code]),
    }
  })
}

export function computeHeatInfluence(
  point: [number, number, number],
  anchor: Pick<HeatAnchor, 'center' | 'radius' | 'falloff'>,
  intensity: number,
) {
  const dx = point[0] - anchor.center[0]
  const dy = point[1] - anchor.center[1]
  const dz = point[2] - anchor.center[2]
  const distance = Math.sqrt(dx * dx + dy * dy + dz * dz)
  const normalizedDistance = distance / Math.max(anchor.radius, 0.001)
  const gaussian = Math.exp(-Math.pow(normalizedDistance * anchor.falloff, 2))
  return gaussian * normalizeRegionIntensity(intensity)
}

export function resolveDominantRegionCode(
  point: [number, number, number],
  states: HeatRegionState[],
  threshold = 0.04,
) {
  let winnerCode: string | null = null
  let winnerScore = -1

  states.forEach((state) => {
    const score = computeHeatInfluence(point, state, Math.max(state.intensity, 0.12))
    if (score > winnerScore) {
      winnerCode = state.code
      winnerScore = score
    }
  })

  if (!winnerCode || winnerScore < threshold) {
    return null
  }

  return winnerCode
}

export function buildShaderHeatPayload(states: HeatRegionState[]) {
  const positions = states.map((state) => state.center)
  const radii = states.map((state) => state.radius)
  const falloffs = states.map((state) => state.falloff)
  const intensities = states.map((state) => state.intensity)

  while (positions.length < 8) {
    positions.push([0, 0, 0])
    radii.push(0)
    falloffs.push(1)
    intensities.push(0)
  }

  return {
    positions,
    radii,
    falloffs,
    intensities,
  }
}
