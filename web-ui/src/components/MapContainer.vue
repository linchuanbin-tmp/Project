<template>
  <div ref="mapContainer" :style="{ width: '100%', height: props.height, borderRadius: '12px' }"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  path: number[][]
  startPoint: number[]
  endPoint: number[]
  height?: string
}>(), {
  height: '400px'
})

const mapContainer = ref<HTMLDivElement | null>(null)
const isMapLoaded = ref(false)
let map: any = null
let polyline: any = null
let startMarker: any = null
let endMarker: any = null
let stepPolylineOverlay: any = null
let animationFrameId: number | null = null

// Dynamically load AMap SDK (avoids polluting index.html)
const loadAMap = (): Promise<any> => {
  return new Promise((resolve, reject) => {
    // 如果已经加载过，直接返回
    if (window.AMap) {
      resolve(window.AMap)
      return
    }

    window._AMapSecurityConfig = {
      securityJsCode: 'ac829f0a4df12dfdcb2440e8bb9c2338'
    }

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = 'https://webapi.amap.com/maps?v=2.0&key=145fda194168c12fa8ed56b309cdd6c9'
    script.async = true

    script.onerror = () => {
      reject(new Error(t('map.loadFailed')))
    }

    script.onload = () => {
      // Poll until AMap is available on window (up to 5s)
      let attempts = 0
      const check = setInterval(() => {
        attempts++
        if (window.AMap) {
          clearInterval(check)
          resolve(window.AMap)
        } else if (attempts >= 50) {
          clearInterval(check)
          reject(new Error(t('map.loadTimeout')))
        }
      }, 100)
    }

    document.head.appendChild(script)
  })
}

const initMap = async () => {
  if (!mapContainer.value) return

  try {
    const AMap = await loadAMap()

    map = new AMap.Map(mapContainer.value, {
      zoom: 12,
      center: props.startPoint?.length === 2 ? props.startPoint : [114.137, 22.283],
      lang: 'en'
    })

    // 地图加载完成后绘制路线
    map.on('complete', () => {
      try {
        map.setLang('en')
      } catch (e) {
        console.warn('Failed to set map language to English:', e)
      }
      isMapLoaded.value = true
      updateRoute()
    })
  } catch (err: any) {
    console.error(t('map.initFailed'), err)
    // Show error toast on map container if load fails
    if (mapContainer.value) {
      mapContainer.value.innerHTML = `
        <div style="display:flex;justify-content:center;align-items:center;height:100%;color:#F56C6C;font-size:14px;">
          ${t('map.initFailed')}: ${err.message}
        </div>`
    }
  }
}

const clearLayers = () => {
  if (!map) return
  if (polyline) { map.remove(polyline); polyline = null }
  if (startMarker) { map.remove(startMarker); startMarker = null }
  if (endMarker) { map.remove(endMarker); endMarker = null }
  if (stepPolylineOverlay) { map.remove(stepPolylineOverlay); stepPolylineOverlay = null }
  if (animationFrameId) { cancelAnimationFrame(animationFrameId); animationFrameId = null }
}

const updateRoute = () => {
  if (!map || !isMapLoaded.value || !props.path || props.path.length === 0) return

  clearLayers()

  const AMap = window.AMap

  // 绘制路线 (Sleek dark indigo line)
  polyline = new AMap.Polyline({
    path: props.path,
    strokeColor: '#4f46e5',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    showDir: true
  })
  map.add(polyline)

  // 起点标记 (Custom HTML pill with a pointer pin style replacing default pin)
  if (props.startPoint?.length === 2) {
    startMarker = new AMap.Marker({
      position: props.startPoint,
      content: `
        <div style="position: relative; display: flex; flex-direction: column; align-items: center; transform: translate(-50%, -100%); pointer-events: none;">
          <div style="background: #10b981; color: #fff; padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; box-shadow: 0 2px 6px rgba(0,0,0,0.2); white-space: nowrap; display: flex; align-items: center; border: 1.5px solid #fff;">
            <span style="width: 5px; height: 5px; background: #fff; border-radius: 50%; margin-right: 5px;"></span>
            ${t('map.startPoint')}
          </div>
          <div style="width: 0; height: 0; border-left: 5px solid transparent; border-right: 5px solid transparent; border-top: 5px solid #10b981; margin-top: -1px;"></div>
        </div>
      `
    })
    map.add(startMarker)
  }

  // 终点标记 (Custom HTML pill with a pointer pin style replacing default pin)
  if (props.endPoint?.length === 2) {
    endMarker = new AMap.Marker({
      position: props.endPoint,
      content: `
        <div style="position: relative; display: flex; flex-direction: column; align-items: center; transform: translate(-50%, -100%); pointer-events: none;">
          <div style="background: #ef4444; color: #fff; padding: 4px 10px; border-radius: 20px; font-size: 11px; font-weight: 600; box-shadow: 0 2px 6px rgba(0,0,0,0.2); white-space: nowrap; display: flex; align-items: center; border: 1.5px solid #fff;">
            <span style="width: 5px; height: 5px; background: #fff; border-radius: 50%; margin-right: 5px;"></span>
            ${t('map.endPoint')}
          </div>
          <div style="width: 0; height: 0; border-left: 5px solid transparent; border-right: 5px solid transparent; border-top: 5px solid #ef4444; margin-top: -1px;"></div>
        </div>
      `
    })
    map.add(endMarker)
  }

  // 调整视野到完整路线
  const fitArr = [polyline]
  if (startMarker) fitArr.push(startMarker)
  if (endMarker) fitArr.push(endMarker)
  map.setFitView(fitArr)
}

onMounted(() => {
  initMap()
})

// 监听数据变化，重新绘制
watch(() => props.path, () => {
  if (map && isMapLoaded.value) updateRoute()
}, { deep: true })

watch(() => props.startPoint, () => {
  if (map && isMapLoaded.value) updateRoute()
}, { deep: true })

watch(() => props.endPoint, () => {
  if (map && isMapLoaded.value) updateRoute()
}, { deep: true })

const parsePolylineString = (str: string): number[][] => {
  const coords: number[][] = []
  if (!str) return coords
  const points = str.split(';')
  for (const p of points) {
    const parts = p.split(',')
    if (parts.length === 2) {
      coords.push([parseFloat(parts[0]), parseFloat(parts[1])])
    }
  }
  return coords
}

const focusOnCoordinate = (lnglat: number[], stepPolyline?: string, zoom = 16) => {
  if (!map) return

  // 1. Center map zoom
  if (lnglat && lnglat.length === 2) {
    map.setZoomAndCenter(zoom, lnglat)
  }

  // 2. Cancel active highlight animation
  if (animationFrameId) {
    cancelAnimationFrame(animationFrameId)
    animationFrameId = null
  }

  // 3. Remove old highlight layer
  if (stepPolylineOverlay) {
    map.remove(stepPolylineOverlay)
    stepPolylineOverlay = null
  }

  // 4. Create and animate new path overlay
  if (stepPolyline) {
    const pathPoints = parsePolylineString(stepPolyline)
    if (pathPoints.length > 0) {
      const AMap = window.AMap
      stepPolylineOverlay = new AMap.Polyline({
        path: pathPoints,
        strokeColor: '#ff9500', // Apple maps orange/gold highlight
        strokeWeight: 2,
        strokeOpacity: 0.1,
        lineJoin: 'round',
        zIndex: 55
      })
      map.add(stepPolylineOverlay)

      const duration = 350 // ms
      const startTime = performance.now()

      const animate = (now: number) => {
        const elapsed = now - startTime
        const progress = Math.min(elapsed / duration, 1)
        const easeProgress = progress * (2 - progress) // easeOutQuad

        if (stepPolylineOverlay) {
          stepPolylineOverlay.setOptions({
            strokeOpacity: 0.1 + easeProgress * 0.85,
            strokeWeight: 2 + easeProgress * 7
          })
        }

        if (progress < 1) {
          animationFrameId = requestAnimationFrame(animate)
        } else {
          animationFrameId = null
        }
      }

      animationFrameId = requestAnimationFrame(animate)
    }
  }
}

defineExpose({ focusOnCoordinate })

onUnmounted(() => {
  if (map) {
    map.destroy()
    map = null
  }
})
</script>