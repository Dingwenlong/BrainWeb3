import { describe, expect, it } from 'vitest'
import {
  buildShaderHeatPayload,
  computeHeatInfluence,
  getHeatRegionStates,
  normalizeRegionIntensity,
  resolveDominantRegionCode,
} from '../brainHeatmap'

describe('brainHeatmap utilities', () => {
  it('normalizes intensities and falls back to zero for invalid values', () => {
    expect(normalizeRegionIntensity(0.42)).toBe(0.42)
    expect(normalizeRegionIntensity(2.4)).toBe(1)
    expect(normalizeRegionIntensity(-1)).toBe(0)
    expect(normalizeRegionIntensity(undefined)).toBe(0)
    expect(normalizeRegionIntensity(Number.NaN)).toBe(0)
  })

  it('builds region states from frame values', () => {
    const states = getHeatRegionStates(
      [
        { code: 'LEFT_FRONTAL', label: 'Left Frontal', electrodes: ['Fp1'] },
        { code: 'RIGHT_FRONTAL', label: 'Right Frontal', electrodes: ['Fp2'] },
      ],
      {
        timestamp: 1,
        intensities: {
          LEFT_FRONTAL: 0.88,
          RIGHT_FRONTAL: 0.22,
        },
      },
    )

    expect(states[0].intensity).toBe(0.88)
    expect(states[1].intensity).toBe(0.22)
    expect(states[0].center[0]).toBeLessThan(0)
    expect(states[1].center[0]).toBeGreaterThan(0)
  })

  it('resolves dominant region from local hit point', () => {
    const states = getHeatRegionStates(
      [
        { code: 'LEFT_FRONTAL', label: 'Left Frontal', electrodes: ['Fp1'] },
        { code: 'RIGHT_FRONTAL', label: 'Right Frontal', electrodes: ['Fp2'] },
      ],
      {
        timestamp: 1,
        intensities: {
          LEFT_FRONTAL: 0.92,
          RIGHT_FRONTAL: 0.31,
        },
      },
    )

    expect(resolveDominantRegionCode([-1.0, 0.58, 0.94], states)).toBe('LEFT_FRONTAL')
    expect(resolveDominantRegionCode([1.0, 0.58, 0.94], states)).toBe('RIGHT_FRONTAL')
    expect(resolveDominantRegionCode([0, -2.8, 0], states, 0.2)).toBeNull()
  })

  it('computes stronger influence for closer, more intense anchors', () => {
    const close = computeHeatInfluence([0, 0, 0], { center: [0.1, 0, 0], radius: 0.8, falloff: 1.3 }, 0.9)
    const far = computeHeatInfluence([0, 0, 0], { center: [1.2, 0, 0], radius: 0.8, falloff: 1.3 }, 0.9)
    const weak = computeHeatInfluence([0, 0, 0], { center: [0.1, 0, 0], radius: 0.8, falloff: 1.3 }, 0.2)

    expect(close).toBeGreaterThan(far)
    expect(close).toBeGreaterThan(weak)
  })

  it('pads shader payload to fixed-length uniforms', () => {
    const payload = buildShaderHeatPayload(
      getHeatRegionStates([{ code: 'CENTRAL_MOTOR', label: 'Motor', electrodes: ['Cz'] }], null),
    )

    expect(payload.positions).toHaveLength(8)
    expect(payload.intensities[0]).toBe(0)
    expect(payload.intensities.slice(1)).toEqual([0, 0, 0, 0, 0, 0, 0])
  })
})
