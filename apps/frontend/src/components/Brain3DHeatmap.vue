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
  type BufferGeometry,
  type Object3D,
} from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { mergeGeometries } from 'three/examples/jsm/utils/BufferGeometryUtils.js'
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

const MODEL_URL = '/models/brain-human-nih-mr.glb'
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

function clamp01(value: number) {
  return Math.max(0, Math.min(1, value))
}

function smoothstep(edge0: number, edge1: number, value: number) {
  const t = clamp01((value - edge0) / (edge1 - edge0 || 1))
  return t * t * (3 - 2 * t)
}

function sculptCortexGeometry(source: BufferGeometry) {
  const geometry = source.clone()
  geometry.computeBoundingBox()

  if (!geometry.boundingBox) {
    return geometry
  }

  const position = geometry.attributes.position as BufferAttribute
  const normal = geometry.attributes.normal as BufferAttribute | undefined
  const bounds = geometry.boundingBox
  const center = bounds.getCenter(new Vector3())
  const size = bounds.getSize(new Vector3())
  const halfSize = size.multiplyScalar(0.5)
  const point = new Vector3()
  const local = new Vector3()
  const direction = new Vector3()

  for (let index = 0; index < position.count; index += 1) {
    point.fromBufferAttribute(position, index)
    local.copy(point).sub(center)

    const nx = local.x / Math.max(halfSize.x, 0.001)
    const ny = local.y / Math.max(halfSize.y, 0.001)
    const nz = local.z / Math.max(halfSize.z, 0.001)
    const absX = Math.abs(nx)
    const hemisphereSign = Math.sign(nx || 1)

    if (normal) {
      direction.fromBufferAttribute(normal, index).normalize()
    } else {
      direction.set(nx, ny, nz).normalize()
    }

    const upperMask = smoothstep(-0.08, 0.74, ny)
    const crownMask = smoothstep(0.12, 0.95, ny)
    const lowerMask = smoothstep(-0.92, -0.08, ny)
    const frontalMask = smoothstep(0.04, 0.94, nz)
    const occipitalMask = smoothstep(-0.94, -0.08, nz)
    const sideMask = smoothstep(0.2, 0.96, absX)
    const hemisphereMask = smoothstep(0.08, 0.94, absX) * smoothstep(-0.14, 0.9, ny)
    const temporalMask = sideMask * smoothstep(-0.78, 0.14, -ny) * smoothstep(-0.28, 0.84, nz + 0.08)
    const ventralMask = lowerMask * (0.6 + sideMask * 0.4)
    const dorsalFissureMask =
      Math.exp(-Math.pow(nx / 0.13, 2)) * smoothstep(0.0, 0.94, ny) * (0.56 + frontalMask * 0.2 + occipitalMask * 0.24)
    const anteriorFissureMask = Math.exp(-Math.pow(nx / 0.17, 2)) * frontalMask * smoothstep(-0.06, 0.56, ny)
    const sylvianMask = sideMask * smoothstep(-0.1, 0.42, -ny) * smoothstep(-0.24, 0.78, nz + 0.04)
    const lateralTaper = sideMask * smoothstep(-0.16, 0.88, ny) * smoothstep(-0.95, 0.64, -nz)
    const cerebellarMask = occipitalMask * lowerMask * (0.28 + sideMask * 0.72)
    const posteriorTaperMask = occipitalMask * smoothstep(-0.1, 0.78, ny)
    const basalTrimMask =
      smoothstep(-0.96, -0.32, -ny) * Math.exp(-Math.pow(nx / 0.52, 2)) * smoothstep(-0.88, 0.16, -nz)
    const parietalShelfMask = hemisphereMask * crownMask * smoothstep(-0.52, 0.42, -nz)
    const superiorArchMask = smoothstep(0.28, 0.96, ny) * smoothstep(-0.42, 0.72, -nz)
    const frontalTaperMask = frontalMask * smoothstep(-0.08, 0.56, ny) * (0.42 + sideMask * 0.58)
    const hemisphereAsymmetry =
      hemisphereSign *
      (frontalMask * 0.011 + occipitalMask * 0.008 + temporalMask * 0.01 + Math.sin(ny * 9.2 + nz * 4.4) * 0.004)
    const frontalLift = frontalMask * crownMask

    const foldPrimary = Math.sin(nz * 15.2 + ny * 6.8 + absX * 5.6) * 0.022
    const foldSecondary = Math.sin(nz * 27.0 - ny * 10.4 + nx * 9.8) * 0.012
    const foldTertiary = Math.cos(nz * 20.5 + nx * 14.0 - ny * 5.8) * 0.008
    const foldQuaternary = Math.sin(absX * 18.0 + nz * 9.4 - ny * 13.0) * 0.005
    const asymmetryFold = Math.sin(nz * 11.0 + ny * 7.6 + hemisphereSign * 1.4) * hemisphereAsymmetry * 0.85
    const gyralFold =
      (foldPrimary + foldSecondary + foldTertiary + foldQuaternary + asymmetryFold) * (0.38 + upperMask * 0.62)

    const radialScale =
      1 +
      frontalMask * 0.028 +
      occipitalMask * 0.012 +
      temporalMask * 0.082 +
      parietalShelfMask * 0.048 +
      crownMask * 0.018 -
      superiorArchMask * 0.03 -
      frontalTaperMask * 0.022 -
      ventralMask * 0.13 -
      dorsalFissureMask * 0.11 -
      anteriorFissureMask * 0.052 -
      sylvianMask * 0.072 -
      posteriorTaperMask * 0.044 -
      lateralTaper * 0.046 -
      basalTrimMask * 0.062 +
      gyralFold

    local.multiplyScalar(radialScale)
    local.x *= 1 - dorsalFissureMask * 0.18 - anteriorFissureMask * 0.08 - upperMask * 0.014
    local.x += hemisphereSign * hemisphereMask * halfSize.x * 0.072
    local.x += hemisphereSign * temporalMask * halfSize.x * 0.118
    local.x -= hemisphereSign * sylvianMask * halfSize.x * 0.05
    local.x -= hemisphereSign * lateralTaper * halfSize.x * 0.034
    local.x += hemisphereAsymmetry * halfSize.x * 0.32
    local.y += frontalLift * halfSize.y * 0.05
    local.y += parietalShelfMask * halfSize.y * 0.032
    local.y += hemisphereAsymmetry * halfSize.y * 0.07 * (0.4 + crownMask * 0.6)
    local.y -= ventralMask * halfSize.y * 0.18
    local.y -= basalTrimMask * halfSize.y * 0.11
    local.y -= cerebellarMask * halfSize.y * 0.08
    local.y -= sylvianMask * halfSize.y * 0.06
    local.z += frontalMask * halfSize.z * 0.034
    local.z -= occipitalMask * halfSize.z * 0.042
    local.z -= posteriorTaperMask * halfSize.z * 0.028
    local.z -= cerebellarMask * halfSize.z * 0.07
    local.z += hemisphereAsymmetry * halfSize.z * 0.12
    local.z += direction.z * gyralFold * halfSize.z * 0.12

    position.setXYZ(index, center.x + local.x, center.y + local.y, center.z + local.z)
  }

  position.needsUpdate = true
  geometry.computeVertexNormals()
  return geometry
}

function createFallbackGeometry() {
  const geometry = new SphereGeometry(1, 192, 144)
  const position = geometry.attributes.position as BufferAttribute
  const point = new Vector3()
  const normal = new Vector3()

  for (let index = 0; index < position.count; index += 1) {
    point.fromBufferAttribute(position, index)
    normal.copy(point).normalize()

    const azimuth = Math.atan2(normal.z, normal.x)
    const elevation = Math.asin(normal.y)
    const bilateral = Math.abs(normal.x)
    const seamIndent = Math.exp(-Math.pow(normal.x / 0.16, 2)) * 0.1
    const foldA = Math.sin(azimuth * 7 + elevation * 4) * 0.028
    const foldB = Math.sin(azimuth * 15 - elevation * 6) * 0.014
    const foldC = Math.cos(azimuth * 10 + elevation * 11) * 0.01
    const frontalBulge = Math.max(0, normal.z) * 0.065
    const occipitalBulge = Math.max(0, -normal.z) * 0.03
    const temporalFalloff = (1 - Math.abs(normal.y)) * bilateral * 0.05
    const inferiorFlatten = Math.max(0, -normal.y) * 0.16
    const crownLift = Math.max(0, normal.y) * 0.05
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
    point.x *= 1.18
    point.y *= 0.96
    point.z *= 1.46
    point.y += Math.cos(azimuth * 2) * 0.022 * Math.max(0, normal.y)
    point.z += Math.sin(azimuth * 2.4) * 0.018 * (1 - Math.abs(normal.y))
    point.x += Math.sign(point.x || 1) * bilateral * 0.026

    position.setXYZ(index, point.x, point.y, point.z)
  }

  position.needsUpdate = true
  geometry.computeVertexNormals()
  return sculptCortexGeometry(geometry)
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
    color: '#cbb9ad',
    roughness: 0.79,
    metalness: 0.01,
    clearcoat: 0.12,
    clearcoatRoughness: 0.84,
    sheen: 0.04,
    sheenRoughness: 0.84,
    transmission: 0,
    thickness: 0,
    ior: 1.18,
    emissive: '#1c1410',
    emissiveIntensity: 0.028,
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
      vec3 sculptLightDirection = normalize(vec3(0.26, 0.94, 0.34));
      float sculptLight = clamp(dot(normalize(vHeatNormal), sculptLightDirection), 0.0, 1.0);
      float fissureShadow = exp(-pow(vHeatPosition.x / 0.34, 2.0)) * smoothstep(-0.1, 1.15, vHeatPosition.y + 0.24) * 0.14;
      float ventralShadow = smoothstep(0.0, 1.0, -vHeatPosition.y - 0.08) * 0.1;
      float temporalPocket = smoothstep(0.22, 1.0, abs(vHeatPosition.x)) * smoothstep(-0.02, 0.7, -vHeatPosition.y) * 0.07;
      float sulcusPatternA = sin(vHeatPosition.z * 8.4 + vHeatPosition.y * 3.8) * sin(vHeatPosition.x * 11.2 - vHeatPosition.z * 2.8);
      float sulcusPatternB = cos(vHeatPosition.z * 12.6 - vHeatPosition.y * 6.2 + vHeatPosition.x * 8.8);
      float sulcusPattern = sulcusPatternA * 0.68 + sulcusPatternB * 0.32;
      float sulcusShadow = smoothstep(0.28, 0.94, sulcusPattern) * (0.03 + (1.0 - sculptLight) * 0.05);
      float crownHighlight = smoothstep(0.3, 1.08, vHeatPosition.y + sculptLight * 0.22) * 0.08;
      float rim = pow(1.0 - abs(vHeatNormal.z), 2.0) * 0.1;
      float hoverGlow = exp(-pow(distance(vHeatPosition, uHoverAnchor) / 0.65, 2.0)) * uHoverStrength;
      diffuseColor.rgb *= 0.84 + sculptLight * 0.24;
      diffuseColor.rgb *= 1.0 - fissureShadow - ventralShadow - temporalPocket - sulcusShadow;
      diffuseColor.rgb += vec3(0.07, 0.055, 0.04) * crownHighlight;
      diffuseColor.rgb = mix(diffuseColor.rgb, heatColor, heatField * 0.7);
      diffuseColor.rgb += heatColor * rim * 0.08;
      totalEmissiveRadiance += heatColor * (heatField * 0.12 + hoverGlow * 0.18);
    `,
    )
  }

  material.customProgramCacheKey = () => 'brain-cortex-heat-v2'
  return material
}

async function loadCortexMesh(): Promise<Mesh> {
  const loader = new GLTFLoader()

  try {
    const gltf = await loader.loadAsync(MODEL_URL)
    gltf.scene.updateMatrixWorld(true)
    const sourceMeshes: Mesh[] = []
    gltf.scene.traverse((node) => {
      if ((node as Mesh).isMesh) {
        sourceMeshes.push(node as Mesh)
      }
    })

    if (!sourceMeshes.length) {
      throw new Error('No mesh found in bundled cortex model.')
    }

    const geometries = sourceMeshes.map((sourceMesh) => sourceMesh.geometry.clone().applyMatrix4(sourceMesh.matrixWorld))
    const mergedGeometry = mergeGeometries(geometries, false)
    if (!mergedGeometry) {
      throw new Error('Unable to merge bundled cortex meshes.')
    }

    const mesh = new Mesh(mergedGeometry, new MeshBasicMaterial())
    mesh.position.set(0, 0, 0)
    mesh.rotation.set(0, 0, 0)
    mesh.scale.set(1, 1, 1)
    mesh.visible = true
    mesh.frustumCulled = false
    return mesh as Mesh
  } catch {
    const fallbackMaterial = new MeshBasicMaterial()
    return new Mesh(createFallbackGeometry(), fallbackMaterial) as Mesh
  }
}

function frameCortexMesh(mesh: Mesh) {
  const bounds = new Box3().setFromObject(mesh)
  const center = bounds.getCenter(new Vector3())
  mesh.geometry.translate(-center.x, -center.y, -center.z)
  mesh.position.set(0, 0, 0)

  const size = bounds.getSize(new Vector3())
  const scale = 4.4 / Math.max(size.z, 0.001)
  mesh.geometry.scale(scale, scale, scale)
  mesh.scale.setScalar(1)
  mesh.geometry.computeBoundingBox()
  mesh.geometry.computeBoundingSphere()
  mesh.rotation.set(-0.12, -0.64, 0.08)
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
  scene.fog = new Fog('#12161a', 8.4, 14.6)

  const camera = new PerspectiveCamera(30, 1, 0.1, 100)
  camera.position.set(0.18, 0.34, 6.75)

  const renderer = new WebGLRenderer({
    antialias: true,
    alpha: true,
    powerPreference: 'high-performance',
  })
  renderer.outputColorSpace = SRGBColorSpace
  renderer.toneMapping = ACESFilmicToneMapping
  renderer.toneMappingExposure = 0.96
  renderer.shadowMap.enabled = true
  renderer.domElement.className = 'heatmap__canvas'
  stageRef.value.appendChild(renderer.domElement)

  const controls = new OrbitControls(camera, renderer.domElement)
  controls.enableDamping = true
  controls.enablePan = false
  controls.dampingFactor = 0.06
  controls.rotateSpeed = 0.58
  controls.minDistance = 5.1
  controls.maxDistance = 8.2
  controls.minPolarAngle = Math.PI * 0.28
  controls.maxPolarAngle = Math.PI * 0.72
  controls.target.set(0, 0.04, 0)

  const raycaster = new Raycaster()
  const pointer = new Vector2(2, 2)
  const brainGroup = new Group()
  brainGroup.rotation.set(-0.06, -0.42, 0.04)
  scene.add(brainGroup)

  const ambient = new AmbientLight('#efe6da', 0.82)
  const sky = new HemisphereLight('#f8f1e5', '#12161a', 0.48)
  const key = new DirectionalLight('#fff7ee', 1.52)
  const rim = new DirectionalLight('#d1b89b', 0.62)
  const fill = new PointLight('#8da0a6', 0.32, 18, 2)

  key.position.set(3.4, 4.8, 5.2)
  key.castShadow = true
  key.shadow.mapSize.set(1024, 1024)
  rim.position.set(-4.6, 1.4, -4.1)
  fill.position.set(0.2, -1.2, 1.6)

  scene.add(ambient, sky, key, rim, fill)

  const floorGlow = new Mesh(
    new CircleGeometry(3.1, 96),
    new MeshBasicMaterial({
      color: '#5ed5db',
      transparent: true,
      opacity: 0.05,
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
      opacity: 0.07,
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
      opacity: 0.18,
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
    const idleOffset = Math.sin(seconds * 0.42) * 0.018
    const baseScale = current.cortexMesh.userData.baseScale as Vector3
    const basePosition = current.cortexMesh.userData.basePosition as Vector3

    if (!current.interacting) {
      current.brainGroup.rotation.y = -0.42 + Math.sin(seconds * 0.14) * 0.035
      current.brainGroup.rotation.x = -0.06 + Math.sin(seconds * 0.18) * 0.01
    }

    current.cortexMesh.position.set(basePosition.x, basePosition.y + idleOffset, basePosition.z)
    current.cortexMesh.scale.copy(baseScale)
    orbitRing.rotation.z += 0.00035

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
