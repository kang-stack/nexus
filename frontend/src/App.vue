<template>
  <router-view />
  <ToastContainer />
</template>

<script setup>
import ToastContainer from '@/components/ToastContainer.vue'
</script>

<style>
:root {
  /* Core palette — Navy / Sky Blue theme */
  --bg-deep: #e8ecf0;
  --bg: #fafaf8;
  --bg-elevated: #f0f2f5;
  --card: #ffffff;
  --card-solid: #ffffff;
  --sidebar-bg: #1e2d3d;
  --sidebar-active: rgba(56, 189, 248, 0.12);

  --primary: #38bdf8;
  --primary-hover: #0ea5e9;
  --primary-glow: rgba(56, 189, 248, 0.2);
  --accent: #1e2d3d;
  --accent-glow: rgba(30, 45, 61, 0.12);

  --text: #1e2d3d;
  --text-secondary: #4a5c6c;
  --text-muted: #8899a6;
  --border: #e4e8ec;
  --border-strong: rgba(0, 0, 0, 0.1);

  --success: #10b981;
  --success-bg: rgba(16, 185, 129, 0.1);
  --error: #ef4444;
  --error-bg: rgba(239, 68, 68, 0.08);
  --warning: #f59e0b;
  --warning-bg: rgba(245, 158, 11, 0.1);
  --info: #38bdf8;
  --info-bg: rgba(56, 189, 248, 0.1);

  --radius: 4px;
  --radius-sm: 4px;
  --radius-xs: 2px;
  --radius-lg: 4px;

  --shadow-sm: 0 1px 2px rgba(0,0,0,0.04);
  --shadow: 0 2px 12px rgba(0,0,0,0.05);
  --shadow-md: 0 8px 28px rgba(0,0,0,0.08);
  --shadow-glow: 0 0 20px rgba(56, 189, 248, 0.18);

  --font-display: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;
  --font-body: 'PingFang SC', 'Microsoft YaHei', -apple-system, BlinkMacSystemFont, sans-serif;

  --header-height: 52px;
}

* { margin: 0; padding: 0; box-sizing: border-box; }

html { font-size: 15px; }

body {
  font-family: var(--font-body);
  color: var(--text);
  background: var(--bg);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Subtle animated background */
body::before {
  content: '';
  position: fixed;
  inset: 0;
  background:
    radial-gradient(ellipse 80% 60% at 15% 10%, rgba(30, 45, 61, 0.03) 0%, transparent 55%),
    radial-gradient(ellipse 60% 80% at 85% 75%, rgba(56, 189, 248, 0.03) 0%, transparent 55%),
    radial-gradient(ellipse 50% 50% at 50% 50%, rgba(30, 45, 61, 0.02) 0%, transparent 70%);
  pointer-events: none;
  z-index: -1;
  animation: bgPulse 14s ease-in-out infinite alternate;
}

@keyframes bgPulse {
  0% { opacity: 0.6; }
  100% { opacity: 1; }
}

/* Scrollbar */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.12); border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: rgba(0,0,0,0.2); }

a { color: var(--primary); text-decoration: none; transition: color 0.2s; }
a:hover { color: var(--primary-hover); }

::selection { background: rgba(56, 189, 248, 0.2); color: var(--text); }
</style>
