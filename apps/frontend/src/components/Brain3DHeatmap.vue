<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import {
  ACESFilmicToneMapping,
  AdditiveBlending,
  AmbientLight,
  Box3,
  CircleGeometry,
  Color,
  DirectionalLight,
  Fog,
  Group,
  HemisphereLight,
  Mesh,
  MeshBasicMaterial,
  MeshPhysicalMaterial,
  PerspectiveCamera,
  PlaneGeometry,
  PointLight,
  Raycaster,
  Scene,
  SphereGeometry,
  SRGBColorSpace,
  TorusGeometry,
  Vector2,
  Vector3,
  WebGLRenderer,
  type BufferAttribute,
  type Object3D,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import type { ActivityFrame, BrainRegion } from '../types/api'
import {
  HEAT_ANCHORS,
  buildShaderHeatPayload,
  getHeatRegionStates,
  resolveDominantRegionCode,
} from '../utils/brainHeatmap'
import { formatBandLabel, formatSecondsLabel } from '../utils/labels'

const props = defineProps<{
  regions: BrainRegion[]
  frame: ActivityFrame | null
  band: string
  timestamp?: number
}>()

const emit = defineEmits<{
  'hover-region': [regionCode: string | null]
}>()

const MODEL_URL = '/models/brain-cortex.gltf'
const MAX_HEAT_REGIONS = 8

interface HeatShaderUniformSet {
  uAnchorPositions: { value: Vector3[] }
  uAnchorRadii: { value: number[] }
  uAnchorFalloffs: { value: number[] }
  uAnchorIntensities: { value: number[] }
  uHoverAnchor: { value: Vector3 }
  uHoverStrength: { value: number }
  uHeatCool: { value: Color }
  uHeatWarm: { value: Color }
  uHeatHot: { value: Color }
}

interface ViewerRuntime {
  scene: Scene
  camera: PerspectiveCamera
  renderer: WebGLRenderer
  controls: OrbitControls
  raycaster: Raycaster
  pointer: Vector2
  brainGroup: Group
  cortexMesh: Mesh
  cortexMaterial: MeshPhysicalMaterial
  shaderUniforms: HeatShaderUniformSet
  resizeObserver: ResizeObserver
  animationFrame: number | null
  interacting: boolean
  dispose: () => void
}

const stageRef = ref<HTMLDivElement | null>(null)
const hoveredRegionCode = ref<string | null>(null)
const renderError = ref<string | null>(null)
const runtime = shallowRef<ViewerRuntime | null>(null)

const heatStates = computed(() => getHeatRegionStates(props.regions, props.frame))

function createFallbackGeometry() {
  const geometry = new SphereGeometry(1, 160, 120)
  const position = geometry.attributes.position as BufferAttribute
  const point = new Vector3()
  const normal = new Vector3()

  for (let index = 0; index < position.count; index += 1) {
    point.fromBufferAttribute(position, index)
    normal.copy(point).normalize()

    const azimuth = Math.atan2(normal.z, normal.x)
    const elevation = Math.asin(normal.y)
    const bilateral = Math.abs(normal.x)
    const seamIndent = Math.exp(-Math.pow(normal.x / 0.14, 2)) * 0.12
    const foldA = Math.sin(azimuth * 9 + elevation * 5) * 0.034
    const foldB = Math.sin(azimuth * 17 - elevation * 7) * 0.016
    const foldC = Math.cos(azimuth * 11 + elevation * 13) * 0.012
    const frontalBulge = Math.max(0, normal.z) * 0.08
    const occipitalBulge = Math.max(0, -normal.z) * 0.045
    const temporalFalloff = (1 - Math.abs(normal.y)) * bilateral * 0.055
    const inferiorFlatten = Math.max(0, -normal.y) * 0.14
    const crownLift = Math.max(0, normal.y) * 0.07
    const scale =
      1 +
      foldA +
      foldB +
      foldC +
      frontalBulge +
      occipitalBulge +
      temporalFalloff +
      crownLift -
      inferiorFlatten -
      seamIndent

    point.multiplyScalar(scale)
    point.x *= 1.32
    point.y *= 1.05
    point.z *= 1.58
    point.y += Math.cos(azimuth * 2) * 0.03 * Math.max(0, normal.y)
    point.z += Math.sin(azimuth * 2.6) * 0.025 * (1 - Math.abs(normal.y))
    point.x += Math.sign(point.x || 1) * bilateral * 0.05

    position.setXYZ(index, point.x, point.y, point.z)
  }

  position.needsUpdate = true
  geometry.computeVertexNormals()
  return geometry
}

function createHeatShaderUniforms(): HeatShaderUniformSet {
  return {
    uAnchorPositions: {
      value: Array.from({ length: MAX_HEAT_REGIONS }, () => new Vector3()),
    },
    uAnchorRadii: {
      value: Array.from({ length: MAX_HEAT_REGIONS }, () => 0),
    },
    uAnchorFalloffs: {
      value: Array.from({ length: MAX_HEAT_REGIONS }, () => 1),
    },
    uAnchorIntensities: {
      value: Array.from({ length: MAX_HEAT_REGIONS }, () => 0),
    },
    uHoverAnchor: {
      value: new Vector3(),
    },
    uHoverStrength: {
      value: 0,
    },
    uHeatCool: {
      value: new Color('#1f8da8'),
    },
    uHeatWarm: {
      value: new Color('#efb06f'),
    },
    uHeatHot: {
      value: new Color('#f46f5c'),
    },
  }
}

function buildCortexMaterial(uniforms: HeatShaderUniformSet) {
  const material = new MeshPhysicalMaterial({
    color: '#b8c4c8',
    roughness: 0.5,
    metalness: 0.02,
    clearcoat: 0.9,
    clearcoatRoughness: 0.24,
    sheen: 0.18,
    sheenRoughness: 0.5,
    transmission: 0.03,
    thickness: 0.4,
    ior: 1.28,
    emissive: '#06131a',
    emissiveIntensity: 0.12,
  })

  material.onBeforeCompile = (shader) => {
    Object.assign(shader.uniforms, uniforms)

    shader.vertexShader =
      `
      varying vec3 vHeatPosition;
      varying vec3 vHeatNormal;
    ` + shader.vertexShader

    shader.vertexShader = shader.vertexShader.replace(
      '#include <begin_vertex>',
      `
      #include <begin_vertex>
      vHeatPosition = transformed;
      vHeatNormal = normalize(objectNormal);
    `,
    )

    shader.fragmentShader =
      `
      varying vec3 vHeatPosition;
      varying vec3 vHeatNormal;
      uniform vec3 uAnchorPositions[${MAX_HEAT_REGIONS}];
      uniform float uAnchorRadii[${MAX_HEAT_REGIONS}];
      uniform float uAnchorFalloffs[${MAX_HEAT_REGIONS}];
      uniform float uAnchorIntensities[${MAX_HEAT_REGIONS}];
      uniform vec3 uHoverAnchor;
      uniform float uHoverStrength;
      uniform vec3 uHeatCool;
      uniform vec3 uHeatWarm;
      uniform vec3 uHeatHot;

      float brainHeatField() {
        float field = 0.0;
        for (int i = 0; i < ${MAX_HEAT_REGIONS}; i += 1) {
          float radius = max(uAnchorRadii[i], 0.001);
          float distanceFactor = distance(vHeatPosition, uAnchorPositions[i]) / radius;
          float influence = exp(-pow(distanceFactor * uAnchorFalloffs[i], 2.0)) * uAnchorIntensities[i];
          field += influence;
        }
        return clamp(field, 0.0, 1.0);
      }
    ` + shader.fragmentShader

    shader.fragmentShader = shader.fragmentShader.replace(
      '#include <color_fragment>',
      `
      #include <color_fragment>
      float heatField = brainHeatField();
      float warmMix = smoothstep(0.08, 0.56, heatField);
      float hotMix = smoothstep(0.58, 0.96, heatField);
      vec3 heatColor = mix(uHeatCool, uHeatWarm, warmMix);
      heatColor = mix(heatColor, uHeatHot, hotMix);
      float rim = pow(1.0 - abs(vHeatNormal.z), 2.0) * 0.14;
      float hoverGlow = exp(-pow(distance(vHeatPosition, uHoverAnchor) / 0.65, 2.0)) * uHoverStrength;
      diffuseColor.rgb = mix(diffuseColor.rgb, heatColor, heatField * 0.82);
      diffuseColor.rgb += heatColor * rim * 0.12;
      totalEmissiveRadiance += heatColor * (heatField * 0.18 + hoverGlow * 0.26);
    `,
    )
  }

  material.customProgramCacheKey = () => 'brain-cortex-heat-v2'
  return material
}

async function loadCortexMesh() {
  const loader = new GLTFLoader()

  try {
    const gltf = await loader.loadAsync(MODEL_URL)
    const sourceMesh = gltf.scene.getObjectByProperty('type', 'Mesh') as Mesh | undefined
    if (!sourceMesh) {
      throw new Error('No mesh found in bundled cortex model.')
    }

    const mesh = sourceMesh.clone() as Mesh
    mesh.geometry = sourceMesh.geometry.clone()
    return mesh
  } catch {
    const fallbackMaterial = new MeshBasicMaterial()
    return new Mesh(createFallbackGeometry(), fallbackMaterial)
  }
}

function frameCortexMesh(mesh: Mesh) {
  const bounds = new Box3().setFromObject(mesh)
  const center = bounds.getCenter(new Vector3())
  mesh.position.sub(center)

  const size = bounds.getSize(new Vector3())
  const scale = 4.2 / Math.max(size.z, 0.001)
  mesh.scale.setScalar(scale)
  mesh.rotation.set(-0.08, -0.18, 0.06)
  mesh.userData.baseScale = mesh.scale.clone()
  mesh.userData.basePosition = mesh.position.clone()
  mesh.castShadow = true
  mesh.receiveShadow = true
}

function syncRendererSize(current: ViewerRuntime) {
  if (!stageRef.value) {
    return
  }

  const width = stageRef.value.clientWidth
  const height = stageRef.value.clientHeight
  if (!width || !height) {
    return
  }

  current.camera.aspect = width / height
  current.camera.updateProjectionMatrix()
  current.renderer.setSize(width, height, false)
  current.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
}

function applyHeatState() {
  const current = runtime.value
  if (!current) {
    return
  }

  const payload = buildShaderHeatPayload(heatStates.value)
  payload.positions.forEach((position, index) => {
    current.shaderUniforms.uAnchorPositions.value[index].set(...position)
    current.shaderUniforms.uAnchorRadii.value[index] = payload.radii[index]
    current.shaderUniforms.uAnchorFalloffs.value[index] = payload.falloffs[index]
    current.shaderUniforms.uAnchorIntensities.value[index] = payload.intensities[index]
  })

  const hoverAnchor = hoveredRegionCode.value ? HEAT_ANCHORS[hoveredRegionCode.value] : null
  if (hoverAnchor) {
    current.shaderUniforms.uHoverAnchor.value.set(...hoverAnchor.center)
    current.shaderUniforms.uHoverStrength.value = 1
  } else {
    current.shaderUniforms.uHoverAnchor.value.set(0, 0, 0)
    current.shaderUniforms.uHoverStrength.value = 0
  }
}

async function createViewerRuntime() {
  if (!stageRef.value) {
    return null
  }

  const scene = new Scene()
  scene.fog = new Fog('#041017', 7.6, 13.2)

  const camera = new PerspectiveCamera(30, 1, 0.1, 100)
  camera.position.set(0, 0.42, 7.2)

  const renderer = new WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  })
  renderer.outputColorSpace = SRGBColorSpace
  renderer.toneMapping = ACESFilmicToneMapping
  renderer.toneMappingExposure = 1.05
  renderer.shadowMap.enabled = true
  renderer.domElement.className = 'heatmap__canvas'
  stageRef.value.appendChild(renderer.domElement)

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.enablePan = false
  controls.dampingFactor = 0.06
  controls.rotateSpeed = 0.72
  controls.minDistance = 5.4
  controls.maxDistance = 8.8
  controls.minPolarAngle = Math.PI * 0.26
  controls.maxPolarAngle = Math.PI * 0.74
  controls.target.set(0, 0.18, 0)

  const raycaster = new Raycaster()
  const pointer = new Vector2(2, 2)
  const brainGroup = new Group()
  brainGroup.rotation.set(-0.04, -0.28, 0.03)
  scene.add(brainGroup)

  const ambient = new AmbientLight('#d4ecf0', 0.82)
  const sky = new HemisphereLight('#c9f5f6', '#041116', 0.74)
  const key = new DirectionalLight('#f6ffff', 1.66)
  const rim = new DirectionalLight('#ffbf7a', 1.02)
  const fill = new PointLight('#4cd9de', 1.08, 18, 2)

  key.position.set(4.2, 5.1, 5.8)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  rim.position.set(-5.2, 1.8, -4.5)
  fill.position.set(0, -1.6, 2.2)

  scene.add(ambient, sky, key, rim, fill)

  const floorGlow = new Mesh(
    new CircleGeometry(3.1, 96),
    new MeshBasicMaterial({
      color: '#5ed5db',
      transparent: true,
      opacity: 0.11,
      blending: AdditiveBlending,
    }),
  )
  floorGlow.rotation.x = -Math.PI / 2
  floorGlow.position.set(0, -2.4, 0.2)
  scene.add(floorGlow)

  const orbitRing = new Mesh(
    new TorusGeometry(3.25, 0.028, 18, 180),
    new MeshBasicMaterial({
      color: '#6adce2',
      transparent: true,
      opacity: 0.18,
    }),
  )
  orbitRing.rotation.x = Math.PI / 2
  orbitRing.position.set(0, -2.38, 0.18)
  scene.add(orbitRing)

  const backPlate = new Mesh(
    new PlaneGeometry(8.4, 6.8),
    new MeshBasicMaterial({
      color: '#061118',
      transparent: true,
      opacity: 0.35,
    }),
  )
  backPlate.position.set(0, 0.2, -3.1)
  scene.add(backPlate)

  const shaderUniforms = createHeatShaderUniforms()
  const cortexMaterial = buildCortexMaterial(shaderUniforms)
  const cortexMesh = await loadCortexMesh()
  cortexMesh.material = cortexMaterial
  frameCortexMesh(cortexMesh)
  brainGroup.add(cortexMesh)

  let current: ViewerRuntime
  const resizeObserver = new ResizeObserver(() => syncRendererSize(current))

  const setHoveredRegion = (value: string | null) => {
    hoveredRegionCode.value = value
    emit('hover-region', value)
    renderer.domElement.style.cursor = value ? 'grab' : 'default'
  }

  const onPointerMove = (event: PointerEvent) => {
    const rect = renderer.domElement.getBoundingClientRect()
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

    raycaster.setFromCamera(pointer, camera)
    const intersections = raycaster.intersectObject(cortexMesh, false)
    const hit = intersections[0]

    if (!hit) {
      setHoveredRegion(null)
      return
    }

    const localHit = cortexMesh.worldToLocal(hit.point.clone())
    setHoveredRegion(resolveDominantRegionCode([localHit.x, localHit.y, localHit.z], heatStates.value))
  }

  const onPointerLeave = () => setHoveredRegion(null)
  const onControlStart = () => {
    current.interacting = true
  }
  const onControlEnd = () => {
    current.interacting = false
  }

  renderer.domElement.addEventListener('pointermove', onPointerMove)
  renderer.domElement.addEventListener('pointerleave', onPointerLeave)
  controls.addEventListener('start', onControlStart)
  controls.addEventListener('end', onControlEnd)
  resizeObserver.observe(stageRef.value)

  current = {
    scene,
    camera,
    renderer,
    controls,
    raycaster,
    pointer,
    brainGroup,
    cortexMesh,
    cortexMaterial,
    shaderUniforms,
    resizeObserver,
    animationFrame: null,
    interacting: false,
    dispose: () => {
      resizeObserver.disconnect()
      renderer.domElement.removeEventListener('pointermove', onPointerMove)
      renderer.domElement.removeEventListener('pointerleave', onPointerLeave)
      controls.removeEventListener('start', onControlStart)
      controls.removeEventListener('end', onControlEnd)
      controls.dispose()

      scene.traverse((object: Object3D) => {
        const mesh = object as Mesh
        if ('geometry' in mesh && mesh.geometry) {
          mesh.geometry.dispose()
        }

        if ('material' in mesh && mesh.material) {
          const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material]
          materials.forEach((material) => material.dispose())
        }
      })

      renderer.dispose()
      renderer.domElement.remove()
    },
  }

  syncRendererSize(current)
  applyHeatState()

  const tick = (time: number) => {
    const seconds = time * 0.001
    const idleOffset = Math.sin(seconds * 0.48) * 0.04
    const breathing = 1 + Math.sin(seconds * 0.72) * 0.008
    const baseScale = current.cortexMesh.userData.baseScale as Vector3
    const basePosition = current.cortexMesh.userData.basePosition as Vector3

    if (!current.interacting) {
      current.brainGroup.rotation.y = -0.28 + Math.sin(seconds * 0.18) * 0.09
      current.brainGroup.rotation.x = -0.04 + Math.sin(seconds * 0.22) * 0.015
    }

    current.cortexMesh.position.set(basePosition.x, basePosition.y + idleOffset, basePosition.z)
    current.cortexMesh.scale.set(baseScale.x * breathing, baseScale.y * breathing, baseScale.z * breathing)
    orbitRing.rotation.z += 0.0012

    controls.update()
    renderer.render(scene, camera)
    current.animationFrame = window.requestAnimationFrame(tick)
  }

  current.animationFrame = window.requestAnimationFrame(tick)
  return current
}

function destroyViewerRuntime() {
  if (!runtime.value) {
    return
  }

  if (runtime.value.animationFrame !== null) {
    window.cancelAnimationFrame(runtime.value.animationFrame)
  }

  runtime.value.dispose()
  runtime.value = null
}

watch(heatStates, applyHeatState, { deep: true })
watch(hoveredRegionCode, applyHeatState)

onMounted(async () => {
  try {
    runtime.value = await createViewerRuntime()
  } catch (error) {
    renderError.value = error instanceof Error ? error.message : 'WebGL 渲染器启动失败。'
  }
})

onBeforeUnmount(() => {
  destroyViewerRuntime()
})
</script>

<template>
  <section class="heatmap">
    <div class="heatmap__header">
      <div>
        <p class="section-kicker">皮层视图</p>
        <h2 class="section-title">连续热力脑模型</h2>
      </div>
      <div class="heatmap__chips">
        <span class="status-chip">{{ formatBandLabel(band) }}</span>
        <span class="status-chip" v-if="timestamp !== undefined">{{ formatSecondsLabel(timestamp) }}</span>
        <span class="status-chip status-chip--ghost">拖拽旋转</span>
      </div>
    </div>

    <div class="heatmap__viewport-shell">
      <div ref="stageRef" class="heatmap__viewport"></div>
      <div v-if="renderError" class="heatmap__fallback error-state">{{ renderError }}</div>
      <div class="heatmap__overlay">
        <span>连续热力</span>
        <span>局部悬停</span>
        <span>真实皮层</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.heatmap {
  display: grid;
  gap: 16px;
}

.heatmap__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.heatmap__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.heatmap__viewport-shell {
  position: relative;
  min-height: 520px;
  border-radius: 24px;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 0%, rgba(199, 245, 246, 0.12), transparent 26%),
    radial-gradient(circle at 50% 100%, rgba(94, 213, 219, 0.16), transparent 40%),
    linear-gradient(180deg, rgba(5, 16, 22, 0.98), rgba(2, 7, 11, 0.98));
}

.heatmap__viewport {
  position: absolute;
  inset: 0;
}

.heatmap__viewport :deep(canvas) {
  width: 100%;
  height: 100%;
  display: block;
}

.heatmap__fallback {
  position: absolute;
  inset: 18px;
}

.heatmap__overlay {
  position: absolute;
  inset: auto 16px 16px 16px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  pointer-events: none;
}

.heatmap__overlay span {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(119, 235, 237, 0.12);
  background: rgba(2, 12, 16, 0.56);
  color: var(--text-muted);
  font-family: var(--display);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

@media (max-width: 760px) {
  .heatmap__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .heatmap__viewport-shell {
    min-height: 380px;
  }

  .heatmap__overlay {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
}
</style>
