<template>
  <el-config-provider :locale="elLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { elLocaleMap } from '@/i18n'

const { locale } = useI18n()
const elLocale = computed(() => elLocaleMap[locale.value] || elLocaleMap['zh-CN'])

// 同步 document.documentElement.lang 和 localStorage
watch(locale, (val) => {
  document.documentElement.lang = val
  localStorage.setItem('lang', val)
}, { immediate: true })
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB',
  'Microsoft YaHei', '微软雅黑', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
</style>
