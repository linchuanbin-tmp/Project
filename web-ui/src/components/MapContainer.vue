<template>
  <div ref="mapContainer" style="width: 100%; height: 400px; border-radius: 8px;"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  path: number[][]
  startPoint: number[]
  endPoint: number[]
}>()

const mapContainer = ref<HTMLDivElement | null>(null)
let map: any = null
let polyline: any = null
let startMarker: any = null
let endMarker: any = null

// ========== 动态加载高德地图（不依赖 index.html） ==========
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
      // 轮询等待 AMap 挂载到 window（最多等 5 秒）
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
      center: props.startPoint?.length === 2 ? props.startPoint : [116.397, 39.903]
    })

    // 地图加载完成后绘制路线
    map.on('complete', () => {
      updateRoute()
    })
  } catch (err: any) {
    console.error('地图初始化失败:', err)
    // 加载失败时在页面上显示红色提示，方便排查
    if (mapContainer.value) {
      mapContainer.value.innerHTML = `
        <div style="display:flex;justify-content:center;align-items:center;height:100%;color:#F56C6C;font-size:14px;">
          ${t('map.initFailed', { message: err.message })}
        </div>`
    }
  }
}

const clearLayers = () => {
  if (!map) return
  if (polyline) { map.remove(polyline); polyline = null }
  if (startMarker) { map.remove(startMarker); startMarker = null }
  if (endMarker) { map.remove(endMarker); endMarker = null }
}

const updateRoute = () => {
  if (!map || !props.path || props.path.length === 0) return

  clearLayers()

  const AMap = window.AMap

  // 绘制路线
  polyline = new AMap.Polyline({
    path: props.path,
    strokeColor: '#409EFF',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    showDir: true
  })
  map.add(polyline)

  // 起点标记
  if (props.startPoint?.length === 2) {
    startMarker = new AMap.Marker({
      position: props.startPoint,
      label: {
        content: `<div style="background:#409EFF;color:#fff;padding:2px 6px;border-radius:4px;font-size:12px;">${t('map.startPoint')}</div>`,
        direction: 'top',
        offset: new AMap.Pixel(0, -5)
      }
    })
    map.add(startMarker)
  }

  // 终点标记
  if (props.endPoint?.length === 2) {
    endMarker = new AMap.Marker({
      position: props.endPoint,
      label: {
        content: `<div style="background:#F56C6C;color:#fff;padding:2px 6px;border-radius:4px;font-size:12px;">${t('map.endPoint')}</div>`,
        direction: 'top',
        offset: new AMap.Pixel(0, -5)
      }
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
  if (map) updateRoute()
}, { deep: true })

watch(() => props.startPoint, () => {
  if (map) updateRoute()
}, { deep: true })

watch(() => props.endPoint, () => {
  if (map) updateRoute()
}, { deep: true })

onUnmounted(() => {
  if (map) {
    map.destroy()
    map = null
  }
})
</script>
