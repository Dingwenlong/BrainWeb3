import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import RegionMetricsPanel from '../RegionMetricsPanel.vue'

describe('RegionMetricsPanel', () => {
  it('shows hovered region details and sorted activity bars', () => {
    const wrapper = mount(RegionMetricsPanel, {
      props: {
        regions: [
          { code: 'LEFT_FRONTAL', label: 'Left Frontal', electrodes: ['Fp1', 'F3'] },
          { code: 'RIGHT_FRONTAL', label: 'Right Frontal', electrodes: ['Fp2', 'F4'] },
        ],
        frame: {
          timestamp: 4,
          intensities: {
            LEFT_FRONTAL: 0.81,
            RIGHT_FRONTAL: 0.46,
          },
        },
        hoveredRegionCode: 'RIGHT_FRONTAL',
        qualityFlags: ['real-data'],
        band: 'alpha',
        timestamp: 4,
      },
    })

    expect(wrapper.text()).toContain('悬停脑区')
    expect(wrapper.text()).toContain('右额叶')
    expect(wrapper.text()).toContain('Fp2, F4')

    const labels = wrapper.findAll('.metrics__copy span').map((node) => node.text())
    expect(labels).toEqual(['左额叶', '右额叶'])
  })
})
