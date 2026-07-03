import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import i18n from './i18n'

// Element Plus global styles
import 'element-plus/dist/index.css'

const app = createApp(App)

app.use(i18n)
app.use(pinia)
app.use(router)

app.mount('#app')