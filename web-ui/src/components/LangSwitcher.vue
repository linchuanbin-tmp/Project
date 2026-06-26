<template>
  <el-radio-group
    v-model="currentLang"
    size="small"
    @change="switchLang"
  >
    <el-radio-button value="zh-CN">简</el-radio-button>
    <el-radio-button value="en">EN</el-radio-button>
    <el-radio-button value="zh-TW">繁</el-radio-button>
  </el-radio-group>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'

const { locale } = useI18n()
const currentLang = ref(locale.value)

const switchLang = (lang: string) => {
  locale.value = lang
  localStorage.setItem('lang', lang)
}
</script>
