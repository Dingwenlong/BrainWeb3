import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ActivityTimeline from '../ActivityTimeline.vue'

describe('ActivityTimeline', () => {
  it('emits range and seek events from controls', async () => {
    const wrapper = mount(ActivityTimeline, {
      props: {
        bands: ['alpha', 'beta'],
        selectedBand: 'alpha',
        windowSize: 2,
        stepSize: 0.5,
        timeStart: 0,
        timeEnd: 30,
        playing: false,
        loading: false,
        error: null,
        frameIndex: 0,
        frameCount: 2,
        currentTimestamp: 0,
        qualityFlags: ['real-data'],
        frames: [
          { timestamp: 0, intensities: { LEFT_FRONTAL: 0.4 } },
          { timestamp: 0.5, intensities: { LEFT_FRONTAL: 0.5 } },
        ],
      },
    })

    expect(wrapper.text()).toContain('阿尔法频段')

    await wrapper.findAll('.band-switch__item')[1]?.trigger('click')
    expect(wrapper.emitted('update:selectedBand')?.[0]).toEqual(['beta'])

    const rangeInputs = wrapper.findAll('.range-grid input')
    await rangeInputs[0]?.setValue('12')
    await rangeInputs[1]?.setValue('18')
    await wrapper.find('.range-grid__button').trigger('click')

    expect(wrapper.emitted('update:timeStart')?.at(-1)).toEqual([12])
    expect(wrapper.emitted('update:timeEnd')?.at(-1)).toEqual([18])
    expect(wrapper.emitted('apply-range')).toHaveLength(1)

    await wrapper.find('.timeline input').setValue('1')
    expect(wrapper.emitted('seek-frame')?.at(-1)).toEqual([1])
  })
})
