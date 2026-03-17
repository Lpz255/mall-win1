import { createApp } from 'vue';
import 'element-plus/es/components/loading/style/css';
import 'element-plus/es/components/message/style/css';
import 'element-plus/es/components/message-box/style/css';
import App from './App.vue';
import router from './router';
import pinia from './store';
import permissionDirective from './directives/permission';
import './styles/reset.css';
import './styles/main.css';

const app = createApp(App);

app.use(pinia);
app.use(router);
app.directive('permission', permissionDirective);

app.mount('#app');
