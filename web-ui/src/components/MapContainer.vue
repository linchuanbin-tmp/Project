<template>
  <div ref="mapContainer" style="width: 100%; height: 400px; border-radius: 8px;"></div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'

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

const initMap = () => {
  if (!mapContainer.value || !window.AMap) return

  map = new window.AMap.Map(mapContainer.value, {
    zoom: 12,
    center: props.startPoint.length === 2 ? props.startPoint : [116.397, 39.903]
  })

  // 等待地图加载完成再绘制
  map.on('complete', () => {
    updateRoute()
  })
}

const clearLayers = () => {
  if (polyline) {
    map.remove(polyline)
    polyline = null
  }
  if (startMarker) {
    map.remove(startMarker)
    startMarker = null
  }
  if (endMarker) {
    map.remove(endMarker)
    endMarker = null
  }
}

const updateRoute = () => {
  if (!map) return

  clearLayers()

  // 无数据时不绘制
  if (!props.path || props.path.length === 0) {
    log.warn('MapContainer: path 为空，不绘制路线')
    return
  }

  // 绘制路线
  polyline = new window.AMap.Polyline({
    path: props.path,
    strokeColor: '#409EFF',
    strokeWeight: 6,
    strokeOpacity: 0.9,
    lineJoin: 'round',
    showDir: true
  })
  map.add(polyline)

  // 起点标记
  if (props.startPoint && props.startPoint.length === 2) {
    startMarker = new window.AMap.Marker({
      position: props.startPoint,
      label: {
        content: '<div style="background:#409EFF;color:#fff;padding:2px 6px;border-radius:4px;font-size:12px;">起点</div>',
        direction: 'top',
        offset: new window.AMap.Pixel(0, -5)
      }
    })
    map.add(startMarker)
  }

  // 终点标记
  if (props.endPoint && props.endPoint.length === 2) {
    endMarker = new window.AMap.Marker({
      position: props.endPoint,
      label: {
        content: '<div style="background:#F56C6C;color:#fff;padding:2px 6px;border-radius:4px;font-size:12px;">终点</div>',
        direction: 'top',
        offset: new window.AMap.Pixel(0, -5)
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
  // 确保高德 JS API 已加载
  if (window.AMap) {
    initMap()
  } else {
    // 如果异步加载，轮询等待
    const timer = setInterval(() => {
      if (window.AMap) {
        clearInterval(timer)
        initMap()
      }
    }, 200)
  }
})

// 关键：深度监听 path 变化，重新绘制
watch(() => props.path, (newPath) => {
  console.log('MapContainer path 变化:', newPath?.length, '个点')
  if (!map) {
    initMap()
  } else {
    updateRoute()
  }
}, { deep: true, immediate: true })

watch(() => props.startPoint, () => updateRoute(), { deep: true })
watch(() => props.endPoint, () => updateRoute(), { deep: true })

onUnmounted(() => {
  if (map) {
    map.destroy()
    map = null
  }
})
</script>