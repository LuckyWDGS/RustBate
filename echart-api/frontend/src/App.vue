<script setup lang="ts">
import { ref } from 'vue';
import { getPowerData, getMowData, type PowerData, type MowData } from './api';

const powerData = ref<PowerData | null>(null);
const mowData = ref<MowData | null>(null);
const loading = ref(false);

const fetchPowerData = async () => {
  loading.value = true;
  try {
    const response = await getPowerData();
    powerData.value = response.data;
  } catch (error) {
    console.error('获取供电数据失败:', error);
  } finally {
    loading.value = false;
  }
};

const fetchMowData = async () => {
  loading.value = true;
  try {
    const response = await getMowData();
    mowData.value = response.data;
  } catch (error) {
    console.error('获取工务数据失败:', error);
  } finally {
    loading.value = false;
  }
};
</script>

<template>
  <div class="container">
    <h1>Echart API 测试</h1>

    <div class="buttons">
      <button @click="fetchPowerData" :disabled="loading">获取供电数据</button>
      <button @click="fetchMowData" :disabled="loading">获取工务数据</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>

    <div v-if="powerData" class="data-section">
      <h2>供电模块数据</h2>
      <details open>
        <summary>设备信息 ({{ powerData.equipment.length }} 条)</summary>
        <pre>{{ JSON.stringify(powerData.equipment, null, 2) }}</pre>
      </details>
      <details>
        <summary>病害历史 ({{ powerData.defects.length }} 条)</summary>
        <pre>{{ JSON.stringify(powerData.defects, null, 2) }}</pre>
      </details>
      <details>
        <summary>病害详情 ({{ powerData.defects_detail.length }} 条)</summary>
        <pre>{{ JSON.stringify(powerData.defects_detail, null, 2) }}</pre>
      </details>
      <details>
        <summary>养护概要 ({{ powerData.maintenance_summary.length }} 条)</summary>
        <pre>{{ JSON.stringify(powerData.maintenance_summary, null, 2) }}</pre>
      </details>
      <details>
        <summary>养护详情 ({{ powerData.maintenance.length }} 条)</summary>
        <pre>{{ JSON.stringify(powerData.maintenance, null, 2) }}</pre>
      </details>
    </div>

    <div v-if="mowData" class="data-section">
      <h2>工务模块数据</h2>
      <details open>
        <summary>设备信息 ({{ mowData.equipment.length }} 条)</summary>
        <pre>{{ JSON.stringify(mowData.equipment, null, 2) }}</pre>
      </details>
      <details>
        <summary>病害历史 ({{ mowData.defects.length }} 条)</summary>
        <pre>{{ JSON.stringify(mowData.defects, null, 2) }}</pre>
      </details>
      <details>
        <summary>养护历史 ({{ mowData.maintenance.length }} 条)</summary>
        <pre>{{ JSON.stringify(mowData.maintenance, null, 2) }}</pre>
      </details>
    </div>
  </div>
</template>

<style scoped>
.container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

h1 {
  color: #333;
  margin-bottom: 20px;
}

.buttons {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

button {
  padding: 10px 20px;
  background: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

button:hover:not(:disabled) {
  background: #359268;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.loading {
  padding: 20px;
  text-align: center;
  color: #666;
}

.data-section {
  margin-top: 20px;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 15px;
}

h2 {
  color: #42b983;
  margin-top: 0;
}

details {
  margin: 10px 0;
  border: 1px solid #eee;
  border-radius: 4px;
  padding: 10px;
}

summary {
  cursor: pointer;
  font-weight: bold;
  color: #555;
  user-select: none;
}

summary:hover {
  color: #42b983;
}

pre {
  background: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
  margin-top: 10px;
}
</style>
