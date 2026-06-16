<template>
  <div class="placeholder-page">
    <div class="placeholder-card">
      <div class="icon-wrap">
        <FileText :size="28" :stroke-width="1.4" color="#6b7280" />
      </div>
      <h2 class="placeholder-title">SQL Generator</h2>
      <p class="placeholder-desc">
        Natural language to SQL — describe what data you need in plain English
        and let the agent write the query for you.
      </p>
      <div class="coming-badge">Coming soon</div>
      <ul class="feature-list">
        <li><CheckCircle :size="14" :stroke-width="1.8" /> Natural language input</li>
        <li><CheckCircle :size="14" :stroke-width="1.8" /> Multi-table join support</li>
        <li><CheckCircle :size="14" :stroke-width="1.8" /> Query explanation & optimization</li>
        <li><CheckCircle :size="14" :stroke-width="1.8" /> Export to CSV / JSON</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { FileText, CheckCircle } from 'lucide-vue-next'
</script>

<style scoped>
.placeholder-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100vh - 56px);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.placeholder-card {
  background: #fff;
  border-radius: 20px;
  border: 1px solid #f0f0f0;
  padding: 48px;
  max-width: 440px;
  width: 100%;
  text-align: center;
}

.icon-wrap {
  width: 60px;
  height: 60px;
  background: #f9fafb;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
}

.placeholder-title {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 10px 0;
  letter-spacing: -0.4px;
}

.placeholder-desc {
  font-size: 14px;
  color: #6b7280;
  line-height: 1.6;
  margin: 0 0 24px 0;
}

.coming-badge {
  display: inline-block;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 20px;
  margin-bottom: 28px;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feature-list li {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 13.5px;
  color: #374151;
}

.feature-list li svg { color: #22c55e; flex-shrink: 0; }
</style>