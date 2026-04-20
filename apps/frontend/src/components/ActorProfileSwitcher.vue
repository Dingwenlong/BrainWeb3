<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useActorProfile } from '../composables/useActorProfile'
import { formatOrganizationLabel, formatRoleLabel } from '../utils/labels'

const presets = [
  {
    label: '研究员',
    description: '提交访问申请并查看获批后的脑区数据。',
    profile: {
      actorId: 'researcher-01',
      actorRole: 'researcher',
      actorOrg: 'Sichuan Neuro Lab',
    },
  },
  {
    label: '归属方',
    description: '代表资产机构审核请求并直接查看详情。',
    profile: {
      actorId: 'owner-01',
      actorRole: 'owner',
      actorOrg: 'Huaxi Medical Union',
    },
  },
  {
    label: '审批人',
    description: '进入审批台处理待审核访问申请。',
    profile: {
      actorId: 'approver-01',
      actorRole: 'approver',
      actorOrg: 'Huaxi Medical Union',
    },
  },
]

const { actorProfile, setActorProfile } = useActorProfile()

const draft = reactive({
  actorId: actorProfile.value.actorId,
  actorRole: actorProfile.value.actorRole,
  actorOrg: actorProfile.value.actorOrg,
})

const activePresetLabel = computed(
  () =>
    presets.find(
      (preset) =>
        preset.profile.actorId === actorProfile.value.actorId &&
        preset.profile.actorRole === actorProfile.value.actorRole &&
        preset.profile.actorOrg === actorProfile.value.actorOrg,
    )?.label ?? '自定义',
)

watch(
  actorProfile,
  (profile) => {
    draft.actorId = profile.actorId
    draft.actorRole = profile.actorRole
    draft.actorOrg = profile.actorOrg
  },
  { deep: true },
)

function applyPreset(index: number) {
  setActorProfile(presets[index].profile)
}

function applyDraft() {
  setActorProfile({
    actorId: draft.actorId.trim() || actorProfile.value.actorId,
    actorRole: draft.actorRole.trim() || actorProfile.value.actorRole,
    actorOrg: draft.actorOrg.trim() || actorProfile.value.actorOrg,
  })
}
</script>

<template>
  <div class="switcher">
    <div class="switcher__summary">
      <div class="switcher__summary-card">
        <span>当前预设</span>
        <strong>{{ activePresetLabel }}</strong>
      </div>
      <div class="switcher__summary-card">
        <span>角色</span>
        <strong>{{ formatRoleLabel(actorProfile.actorRole) }}</strong>
      </div>
      <div class="switcher__summary-card">
        <span>机构</span>
        <strong>{{ formatOrganizationLabel(actorProfile.actorOrg) }}</strong>
      </div>
    </div>

    <div class="switcher__presets">
      <button
        v-for="(preset, index) in presets"
        :key="preset.label"
        type="button"
        class="switcher__preset"
        :class="{ 'switcher__preset--active': preset.label === activePresetLabel }"
        @click="applyPreset(index)"
      >
        <strong>{{ preset.label }}</strong>
        <p>{{ preset.description }}</p>
      </button>
    </div>

    <div class="switcher__fields">
      <label>
        <span>Actor ID</span>
        <input v-model="draft.actorId" type="text" />
      </label>
      <label>
        <span>角色</span>
        <input v-model="draft.actorRole" type="text" />
      </label>
      <label>
        <span>机构</span>
        <input v-model="draft.actorOrg" type="text" />
      </label>
    </div>

    <div class="switcher__actions">
      <button type="button" class="switcher__apply" @click="applyDraft">应用当前身份</button>
    </div>
  </div>
</template>

<style scoped>
.switcher {
  display: grid;
  gap: 14px;
}

.switcher__summary,
.switcher__presets,
.switcher__fields {
  display: grid;
  gap: 12px;
}

.switcher__summary {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.switcher__summary-card,
.switcher__preset {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(110, 222, 227, 0.1);
  background: var(--bg-panel-soft);
}

.switcher__summary-card span,
.switcher__fields span {
  display: block;
  color: var(--text-faint);
  font-size: 0.72rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.switcher__summary-card strong,
.switcher__preset strong {
  display: block;
  margin-top: 10px;
  font-family: var(--display);
}

.switcher__presets {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.switcher__preset {
  text-align: left;
}

.switcher__preset--active {
  border-color: var(--line-strong);
  box-shadow: inset 0 0 0 1px rgba(110, 222, 227, 0.12);
}

.switcher__preset p {
  margin: 8px 0 0;
  color: var(--text-muted);
  font-size: 0.84rem;
  line-height: 1.45;
}

.switcher__fields {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.switcher__fields label {
  display: grid;
  gap: 8px;
}

.switcher__fields input {
  width: 100%;
  min-height: 44px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: rgba(5, 16, 22, 0.92);
  color: var(--text-main);
}

.switcher__actions {
  display: flex;
  justify-content: flex-end;
}

.switcher__apply {
  min-height: 42px;
  padding: 0 16px;
  border: 1px solid var(--line-warm);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(255, 180, 108, 0.22), rgba(255, 180, 108, 0.12));
  color: var(--text-main);
  font-family: var(--display);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

@media (max-width: 900px) {
  .switcher__summary,
  .switcher__presets,
  .switcher__fields {
    grid-template-columns: 1fr;
  }

  .switcher__actions {
    justify-content: stretch;
  }

  .switcher__apply {
    width: 100%;
  }
}
</style>
