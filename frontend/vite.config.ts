import { defineConfig,loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(({mode})=>{
 const backend=loadEnv(mode,'.','QINGYU_').QINGYU_DEV_BACKEND||'http://127.0.0.1:8080';
 return {plugins:[react()],server:{port:5173,strictPort:true,proxy:{'/api':{target:backend},'/ws':{target:backend.replace(/^http/,'ws'),ws:true}}}};
});
