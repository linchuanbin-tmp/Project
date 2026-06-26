import { createI18n } from 'vue-i18n'
import zhCN from './locales/zh-CN'
import zhTW from './locales/zh-TW'
import en from './locales/en'

// Element Plus 语言包
import elementZhCN from 'element-plus/dist/locale/zh-cn.mjs'
import elementZhTW from 'element-plus/dist/locale/zh-tw.mjs'
import elementEn from 'element-plus/dist/locale/en.mjs'

// 从 localStorage 读取用户语言偏好，默认简体中文
const savedLang = localStorage.getItem('lang') || 'zh-CN'

// Element Plus locale 映射（供 App.vue ElConfigProvider 使用）
export const elLocaleMap: Record<string, any> = {
  'zh-CN': elementZhCN,
  'zh-TW': elementZhTW,
  en: elementEn,
}

const i18n = createI18n({
  legacy: false, // Composition API 模式
  locale: savedLang,
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'zh-TW': zhTW,
    en: en,
  },
  globalInjection: true, // 模板中可使用 $t()
})

export default i18n
