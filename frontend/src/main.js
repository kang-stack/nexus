import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { migrateLegacyAuth } from '@/auth/storage'

migrateLegacyAuth()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
