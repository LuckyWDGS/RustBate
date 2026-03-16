<template>
  <div class="project-manager">
    <header class="header">
      <h2>我的项目</h2>
      <div class="search-bar">
        <input type="text" v-model="searchQuery" placeholder="请输入项目名称" />
        <span class="search-icon">
          <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round" class="css-i6dzq1"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
        </span>
      </div>
    </header>

    <div class="actions-bar" style="margin-bottom: 24px;">
      <h3 style="margin: 0; color: #303133; font-size: 16px; font-weight: 600;">项目列表</h3>
    </div>

    <div class="project-grid">
      <!-- New Project Card -->
      <div class="card new-project-card" @click="openTemplateModal">
        <div class="new-project-content">
          <div class="plus-icon">
            <svg viewBox="0 0 24 24" width="48" height="48" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
          </div>
          <div class="new-project-text">新建项目</div>
        </div>
      </div>

      <!-- Project Cards -->
      <div class="card project-card" v-for="(project, index) in filteredProjects" :key="index">
        <div class="thumbnail">
          <img v-if="project.preview_image" :src="convertFileSrc(project.preview_image)" class="preview-target project-preview-img" />
          <div v-else class="thumbnail-placeholder">
            <div class="placeholder-scene"></div>
          </div>
          
          <!-- Hover Overlay -->
        <div class="project-overlay" v-if="!runningProjects.has(project.path)">
          <button class="action-btn main-btn" @click="openProject(project)">项目编辑</button>
          <button class="action-btn sub-btn" @click="startDuplicate(project, '副本')">创建副本</button>
        </div>
          
          <!-- Running Overlay -->
          <div class="running-overlay" v-if="runningProjects.has(project.path)">
            <div class="spinner"></div>
            <span>运行中...</span>
          </div>
        </div>
        
        <div class="card-footer">
          <div class="project-info-left" @click="!runningProjects.has(project.path) && startRename(project)">
            <span class="edit-icon">
              <svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"></path><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"></path></svg>
            </span>
            <span class="name-text">
              {{ project.name }}
            </span>
          </div>
          <div class="update-time">
            更新：{{ formatTime(project.last_modified) }}
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-if="filteredProjects.length === 0 && !isScanning" class="empty-state">
        <p>未找到项目，请重新查找</p>
      </div>
    </div>
    
    <!-- Custom Clone Modal -->
    <!-- Duplicate Modal -->
    <div class="modal-overlay" v-if="isCloneModalOpen">
      <div class="modal-content">
        <h3>创建项目副本</h3>
        <p class="modal-desc">请输入新副本的项目名称</p>
        <input 
          type="text" 
          v-model="cloneInputName" 
          class="modal-input" 
          :class="{ 'input-error': cloneValidationError }"
          placeholder="请输入项目名称"
          @keyup.enter="!cloneValidationError && confirmCloneName()"
          autoFocus
        />
        <div v-if="cloneValidationError" class="validation-error">{{ cloneValidationError }}</div>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="cancelClone">取消</button>
          <button class="modal-btn confirm" :disabled="!!cloneValidationError" @click="confirmCloneName">确认创建</button>
        </div>
      </div>
    </div>

    <!-- Template Naming Modal -->
    <div class="modal-overlay" v-if="isCreateNameModalOpen">
      <div class="modal-content">
        <h3>基于模板创建</h3>
        <p class="modal-desc">请输入新项目的名称</p>
        <input 
          type="text" 
          v-model="createInputName" 
          class="modal-input" 
          :class="{ 'input-error': createValidationError }"
          placeholder="请输入项目名称"
          @keyup.enter="!createValidationError && confirmCreateName()"
          autoFocus
        />
        <div v-if="createValidationError" class="validation-error">{{ createValidationError }}</div>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="cancelCreateName">取消</button>
          <button class="modal-btn confirm" :disabled="!!createValidationError" @click="confirmCreateName">确认创建</button>
        </div>
      </div>
    </div>
    
    <!-- Rename Modal -->
    <div class="modal-overlay" v-if="isRenameModalOpen">
      <div class="modal-content">
        <h3>重命名项目</h3>
        <p class="modal-desc">请输入新的项目名称</p>
        <input 
          type="text" 
          v-model="renameInputName" 
          class="modal-input" 
          :class="{ 'input-error': renameValidationError }"
          placeholder="请输入项目名称"
          @keyup.enter="!renameValidationError && confirmRename()"
          autoFocus
        />
        <div v-if="renameValidationError" class="validation-error">{{ renameValidationError }}</div>
        <div class="modal-actions">
          <button class="modal-btn cancel" @click="cancelRename">取消</button>
          <button class="modal-btn confirm" :disabled="!!renameValidationError" @click="confirmRename">确认重命名</button>
        </div>
      </div>
    </div>
    
    <!-- Template Selection Modal -->
    <div class="template-modal-overlay" v-if="isTemplateModalOpen">
      <div class="template-modal-content">
        <div class="template-modal-header">
          <h3>新建项目</h3>
          <button class="close-btn" @click="closeTemplateModal">×</button>
        </div>
        
        <div class="template-modal-body">
          <!-- Left Sidebar: Categories -->
          <div class="category-sidebar">
            <div 
              v-for="category in categories" 
              :key="category"
              class="category-card"
              :class="{ 
                'active': selectedCategory === category, 
                'game-bg': category.includes('园区'), 
                'movie-bg': category.includes('能源'),
                'disabled': category.includes('（未建设）')
              }"
              @click="!category.includes('（未建设）') && (selectedCategory = category)"
            >
              <div class="category-title">{{ category }}</div>
            </div>
            
            <div class="template-path-setting" style="margin-top: auto; border: none; background: transparent;">
            </div>
          </div>
          
          <!-- Right Content: Templates Grid -->
          <div class="template-content">
            <div class="template-grid" v-if="currentTemplates.length > 0">
              <div 
                v-for="(template, index) in currentTemplates" 
                :key="index"
                class="template-card"
                :class="{ 'selected': selectedTemplate === template }"
                @click="selectedTemplate = template"
              >
                <div class="template-thumbnail">
                  <img v-if="template.preview_image" :src="convertFileSrc(template.preview_image)" class="preview-target" />
                  <div v-else class="fallback-thumbnail">
                    <!-- Template Layout Icon -->
                    <svg viewBox="0 0 24 24" width="48" height="48" stroke="#606266" stroke-width="1.5" fill="none" stroke-linecap="round" stroke-linejoin="round">
                      <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                      <line x1="3" y1="9" x2="21" y2="9"></line>
                      <line x1="9" y1="21" x2="9" y2="9"></line>
                    </svg>
                  </div>
                </div>
                <div class="template-name">{{ template.name }}</div>
              </div>
            </div>
            <div v-else class="empty-templates">
              未找到此模板内容 (尝试检查模板路径)。
            </div>
          </div>
        </div>
        
        <div class="template-modal-footer">
          <button class="modal-btn cancel" @click="closeTemplateModal">取消</button>
          <button class="modal-btn confirm" :disabled="!selectedTemplate" @click="createFromTemplate">创建</button>
        </div>
      </div>
    </div>
    <!-- Toast Container -->
    <div class="toast-container">
      <transition-group name="toast">
        <div 
          v-for="toast in toasts" 
          :key="toast.id" 
          class="toast-message"
          :class="'toast-' + toast.type"
        >
          <div class="toast-icon">
            <svg v-if="toast.type === 'success'" viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg>
            <svg v-else-if="toast.type === 'error'" viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="15" y1="9" x2="9" y2="15"></line><line x1="9" y1="9" x2="15" y2="15"></line></svg>
            <svg v-else viewBox="0 0 24 24" width="18" height="18" stroke="currentColor" stroke-width="2" fill="none" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
          </div>
          <div class="toast-text">{{ toast.message }}</div>
        </div>
      </transition-group>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { invoke, convertFileSrc } from '@tauri-apps/api/core';
import { listen, UnlistenFn } from '@tauri-apps/api/event';

interface ProjectInfo {
  name: string;
  path: string;
  last_modified: number;
  preview_image?: string;
}

interface ToastMessage {
  id: number;
  message: string;
  type: 'success' | 'error' | 'info';
}

interface TemplateInfo {
  category: string;
  name: string;
  path: string;
  preview_image?: string;
}

const searchQuery = ref('');
const projects = ref<ProjectInfo[]>([]);
const isScanning = ref(false);

const runningProjects = ref<Set<string>>(new Set());
let unlistenFn: UnlistenFn | null = null;

// Toast System
const toasts = ref<ToastMessage[]>([]);
let toastIdCounter = 0;

function showToast(message: string, type: 'success' | 'error' | 'info' = 'info') {
  const id = toastIdCounter++;
  toasts.value.push({ id, message, type });
  setTimeout(() => {
    toasts.value = toasts.value.filter(t => t.id !== id);
  }, 3000);
}

// Clone Modal State
const isCloneModalOpen = ref(false);
const cloneInputName = ref('');
const pendingCloneProject = ref<ProjectInfo | null>(null);

// Template Modal State
const isTemplateModalOpen = ref(false);
const templates = ref<TemplateInfo[]>([]);
const categories = ['站场数字孪生', '园区数字孪生（未建设）', '能源数字孪生（未建设）'];
const selectedCategory = ref('站场数字孪生');
const selectedTemplate = ref<TemplateInfo | null>(null);

// Template Creation Modal State
const isCreateNameModalOpen = ref(false);
const createInputName = ref('');

// Rename Modal State
const isRenameModalOpen = ref(false);
const renameInputName = ref('');
const renameValidationError = ref('');
const pendingRenameProject = ref<ProjectInfo | null>(null);

// Validation Strings
const createValidationError = ref('');
const cloneValidationError = ref('');

// Unified Naming Validation (Sync part)
function checkNameFormat(name: string): { valid: boolean; message: string } {
  const trimmed = name.trim();
  if (!trimmed) {
    return { valid: false, message: '名称不能为空！' };
  }
  if (trimmed.length > 12) {
    return { valid: false, message: '名称不能超过 12 个字符！' };
  }
  const regex = /^[a-zA-Z0-9\u4e00-\u9fa5_]+$/;
  if (!regex.test(trimmed)) {
    return { valid: false, message: '名称只能包含中文、英文、数字和下划线！' };
  }
  return { valid: true, message: '' };
}

// Watch rename name for real-time validation
watch(renameInputName, async (newVal) => {
  const format = checkNameFormat(newVal);
  if (!format.valid) {
    renameValidationError.value = format.message;
    return;
  }
  
  if (pendingRenameProject.value && newVal.trim() === pendingRenameProject.value.name) {
    renameValidationError.value = '';
    return;
  }

  const exists = await invoke<boolean>('check_project_folder_exists', { name: newVal.trim() });
  if (exists) {
    renameValidationError.value = '目录下已存在同名项目';
  } else {
    renameValidationError.value = '';
  }
});

// Watch create name for real-time validation
watch(createInputName, async (newVal) => {
  const format = checkNameFormat(newVal);
  if (!format.valid) {
    createValidationError.value = format.message;
    return;
  }
  
  const exists = await invoke<boolean>('check_project_folder_exists', { name: newVal.trim() });
  if (exists) {
    createValidationError.value = '目录下已存在同名项目';
  } else {
    createValidationError.value = '';
  }
});

// Watch clone name for real-time validation
watch(cloneInputName, async (newVal) => {
  const format = checkNameFormat(newVal);
  if (!format.valid) {
    cloneValidationError.value = format.message;
    return;
  }
  
  const exists = await invoke<boolean>('check_project_folder_exists', { name: newVal.trim() });
  if (exists) {
    cloneValidationError.value = '目录下已存在同名项目';
  } else {
    cloneValidationError.value = '';
  }
});


const filteredProjects = computed(() => {
  if (!searchQuery.value) return projects.value;
  const q = searchQuery.value.toLowerCase();
  return projects.value.filter(p => p.name.toLowerCase().includes(q));
});

// Clone Modal State
// (Removed duplicates)

// Template Modal State
// (Removed duplicates)

const currentTemplates = computed(() => {
  if (!selectedCategory.value) return [];
  // For other categories, we might not have templates in backend yet
  return templates.value.filter(t => t.category === selectedCategory.value);
});

async function fetchTemplates() {
  try {
    const result = await invoke<TemplateInfo[]>('get_templates');
    templates.value = result;
  } catch (error) {
    console.error('Failed to fetch templates:', error);
  }
}

async function openTemplateModal() {
  isTemplateModalOpen.value = true;
  selectedTemplate.value = null;
  await fetchTemplates();
}

function closeTemplateModal() {
  isTemplateModalOpen.value = false;
  selectedTemplate.value = null;
}

// Template Creation Modal State
// (Removed duplicates)

function createFromTemplate() {
  if (!selectedTemplate.value) return;
  const template = selectedTemplate.value;
  createInputName.value = `My${template.name}`;
  isCreateNameModalOpen.value = true;
}

function cancelCreateName() {
  isCreateNameModalOpen.value = false;
}

async function confirmCreateName() {
  const template = selectedTemplate.value;
  if (!template) return;
  
  if (createValidationError.value) return;
  
  const newName = createInputName.value.trim();
  isCreateNameModalOpen.value = false;
  isTemplateModalOpen.value = false;
  
  try {
    await invoke('clone_project', {
      sourceUprojectPath: template.path, 
      targetDir: "", // Backend will default to UnrealProject
      newName: newName
    });
    showToast('项目创建成功！', 'success');
    await scanProjects();
  } catch (error) {
    console.error('Failed to create from template:', error);
    showToast('创建项目失败: ' + error, 'error');
  }
}

async function scanProjects() {
  isScanning.value = true;
  try {
    const result = await invoke<ProjectInfo[]>('scan_projects');
    projects.value = result;
  } catch (error) {
    console.error('Failed to scan projects:', error);
    showToast('扫描项目时出现错误', 'error');
  } finally {
    isScanning.value = false;
  }
}


// (Legacy startEditing removed)


function startRename(project: ProjectInfo) {
  pendingRenameProject.value = project;
  renameInputName.value = project.name;
  isRenameModalOpen.value = true;
}

function cancelRename() {
  isRenameModalOpen.value = false;
  pendingRenameProject.value = null;
}

async function confirmRename() {
  const project = pendingRenameProject.value;
  if (!project || renameValidationError.value) return;

  const newName = renameInputName.value.trim();
  
  if (newName === project.name) {
    isRenameModalOpen.value = false;
    return;
  }

  try {
    await invoke('rename_project', {
      oldPath: project.path,
      newName: newName
    });
    showToast('重命名成功', 'success');
    isRenameModalOpen.value = false;
    await scanProjects();
  } catch (error) {
    console.error('Failed to rename project:', error);
    showToast('重命名失败: ' + error, 'error');
  }
}

async function openProject(project: ProjectInfo) {
  if (runningProjects.value.has(project.path)) return;
  
  try {
    runningProjects.value.add(project.path);
    await invoke('run_project', { path: project.path });
  } catch (error) {
    console.error('Failed to open project:', error);
    showToast('打开项目失败: ' + error, 'error');
    runningProjects.value.delete(project.path);
  }
}

async function findAvailableName(baseName: string, suffix: string): Promise<string> {
  // Logic: base_副本, then base_副本2, base_副本3...
  // Use underscore only for the first one if it's the standard "副本"
  let attempt = "";
  if (suffix === "副本") {
    attempt = `${baseName}_${suffix}`;
  } else {
    attempt = `${baseName}_${suffix}`;
  }
  
  // Truncate base if total length exceeds 12
  if (attempt.length > 12) {
    attempt = attempt.slice(0, 12);
  }

  let count = 1;
  while (true) {
    const checkName = count === 1 ? attempt : `${attempt}${count}`;
    const exists = await invoke<boolean>('check_project_folder_exists', { name: checkName });
    if (!exists) return checkName;
    count++;
    if (count > 20) return attempt; // Guard
  }
}

async function startDuplicate(project: ProjectInfo, modeSuffix: string) {
  pendingCloneProject.value = project;
  cloneInputName.value = await findAvailableName(project.name, modeSuffix);
  isCloneModalOpen.value = true;
}

function cancelClone() {
  isCloneModalOpen.value = false;
  pendingCloneProject.value = null;
  cloneInputName.value = '';
}

async function confirmCloneName() {
  const project = pendingCloneProject.value;
  if (!project) return;
  
  if (cloneValidationError.value) return;
  
  const newName = cloneInputName.value.trim();
  isCloneModalOpen.value = false;
  
  try {
    await invoke('clone_project', {
      sourceUprojectPath: project.path,
      targetDir: "", // Defaults to UnrealProject
      newName: newName
    });
    
    showToast('项目副本创建成功！', 'success');
    pendingCloneProject.value = null;
    await scanProjects();
  } catch (error) {
    console.error('Failed to duplicate project:', error);
    showToast('创建副本失败: ' + error, 'error');
    pendingCloneProject.value = null;
  }
}

function formatTime(unixTimestamp: number): string {
  if (unixTimestamp === 0) return '未知';
  const date = new Date(unixTimestamp * 1000);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${year}${month}${day} ${hours}:${minutes}`;
}

onMounted(async () => {
  // Listen for project closed events
  try {
    unlistenFn = await listen('project-closed', (event) => {
      const payload = event.payload as { path: string };
      if (payload && payload.path) {
        runningProjects.value.delete(payload.path);
      }
    });

    // Auto-scan projects on startup
    await scanProjects();
  } catch (e) {
    console.error("Failed to setup event listener", e);
  }
});

onUnmounted(() => {
  if (unlistenFn) {
    unlistenFn();
  }
});
</script>

<style scoped>
html, body {
  margin: 0;
  padding: 0;
  background-color: #f7f9fc;
}

.project-manager {
  max-width: 100%;
  margin: 0;
  padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  color: #333;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eaeaea;
}

.header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: bold;
  color: #1a1a1a;
  display: flex;
  align-items: center;
}

.search-bar {
  display: flex;
  align-items: center;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 12px;
  width: 260px;
  background: white;
  transition: border-color 0.2s;
}

.search-bar:focus-within {
  border-color: #409eff;
}

.search-bar input {
  border: none;
  outline: none;
  flex: 1;
  font-size: 14px;
  color: #606266;
}

.search-bar input::placeholder {
  color: #c0c4cc;
}

.search-icon {
  color: #a8abb2;
  display: flex;
  align-items: center;
  margin-left: 8px;
}

.actions-bar {
  margin-bottom: 24px;
  display: flex;
  align-items: center;
  gap: 12px;
  background: white;
  padding: 16px;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
}

.actions-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.path-input {
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  width: 350px;
  font-size: 14px;
  color: #606266;
  transition: border-color 0.2s;
}

.path-input:focus {
  border-color: #409eff;
  outline: none;
}

.scan-button {
  padding: 8px 16px;
  background-color: #409eff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 500;
}

.scan-button:hover:not(:disabled) {
  background-color: #66b1ff;
}

.scan-button:disabled {
  background-color: #a0cfff;
  cursor: not-allowed;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.card {
  background: white;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  height: 220px;
  transition: all 0.3s;
}

.project-card:hover {
  box-shadow: 0 8px 16px rgba(0,0,0,0.08);
  border-color: #c6e2ff;
}

.new-project-card {
  border: 1px dashed #dcdfe6;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  background: #fbfdff;
  border-radius: 4px;
}

.new-project-card:hover {
  background: #f0f7ff;
  border-color: #409eff;
  color: #409eff;
}

.new-project-content {
  text-align: center;
  color: #909399;
  transition: color 0.3s;
}

.new-project-card:hover .new-project-content {
  color: #409eff;
}

.plus-icon {
  display: inline-flex;
  justify-content: center;
  align-items: center;
  width: 60px;
  height: 60px;
  border: 2px dashed currentcolor;
  margin-bottom: 12px;
  border-radius: 4px;
}

.new-project-text {
  font-weight: bold;
  font-size: 15px;
}

.thumbnail {
  flex: 1;
  position: relative;
  background: #e1f3d8;
  background: linear-gradient(135deg, #e1f3d8 0%, #c6e2ff 100%);
  overflow: hidden;
}

.project-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumbnail-placeholder {
  width: 100%;
  height: 100%;
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
}

.placeholder-scene {
  width: 120px;
  height: 120px;
  background: rgba(255, 255, 255, 0.4);
  transform: perspective(400px) rotateX(60deg) rotateZ(-45deg);
  box-shadow: 10px 10px 20px rgba(0,0,0,0.1);
}

.project-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.2s ease;
  padding: 12px;
  gap: 4px;
}

.project-card:hover .project-overlay {
  opacity: 1;
}

.action-btn {
  box-sizing: border-box;
  border: none;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-btn {
  width: 180px;
  height: 40px;
  background: #aab2bd;
  color: white;
  border-radius: 6px;
  font-size: 15px;
  margin-bottom: 2px;
  border: none;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.sub-btn {
  width: 180px;
  height: 30px;
  background: white;
  color: #333;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 0px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.action-btn:hover {
  transform: translateY(-2px);
  filter: brightness(1.02);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}

.action-btn:active {
  transform: translateY(0);
}

.running-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.75);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 12px;
  backdrop-filter: blur(2px);
  color: #409eff;
  font-weight: bold;
  font-size: 15px;
  z-index: 10;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #ebeef5;
  border-top: 3px solid #409eff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.card-footer {
  padding: 0 16px;
  height: 50px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
  border-top: 1px solid #ebeef5;
}

.project-info-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.edit-icon {
  color: #909399;
  display: flex;
  align-items: center;
}

.name-text {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  max-width: 140px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.name-text:hover {
  background-color: #f0f2f5;
}

.name-input {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
  max-width: 140px;
  padding: 2px 4px;
  border: 1px solid #409eff;
  border-radius: 4px;
  outline: none;
}

.update-time {
  font-size: 12px;
  color: #909399;
}

.empty-state {
  grid-column: 1 / -1;
  text-align: center;
  padding: 40px;
  color: #909399;
  font-size: 14px;
  background: white;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 200;
  backdrop-filter: blur(4px);
}

.modal-content {
  background: white;
  width: 400px;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
}

.modal-content h3 {
  margin: 0 0 8px 0;
  font-size: 18px;
  color: #303133;
}

.modal-desc {
  margin: 0 0 20px 0;
  font-size: 14px;
  color: #606266;
}

.modal-input {
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 14px;
  color: #303133;
  margin-bottom: 24px;
  outline: none;
  transition: border-color 0.2s;
}

.modal-input:focus {
  border-color: #409eff;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.modal-btn {
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: none;
  font-weight: 500;
  transition: all 0.2s;
}

.modal-btn.cancel {
  background: white;
  color: #606266;
  border: 1px solid #dcdfe6;
}

.modal-btn.cancel:hover {
  color: #409eff;
  border-color: #c6e2ff;
  background: #ecf5ff;
}

.modal-btn.confirm {
  background: #409eff;
  color: white;
}

.modal-btn.confirm:hover {
  background: #66b1ff;
}

.modal-btn.confirm:disabled {
  background: #a0cfff;
  cursor: not-allowed;
}

/* Template Modal Styles */
.template-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 100;
  backdrop-filter: blur(4px);
}

.template-modal-content {
  background: #252526; /* Dark theme to match UE / User's reference */
  width: 800px;
  height: 560px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.3);
  color: #e5e5e5;
}

.template-modal-header {
  padding: 16px 24px;
  background: #1e1e1e;
  border-bottom: 1px solid #333;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
}

.template-modal-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: bold;
}

.close-btn {
  position: absolute;
  right: 20px;
  background: transparent;
  color: #888;
  border: none;
  font-size: 24px;
  cursor: pointer;
  line-height: 1;
  transition: color 0.2s;
}

.close-btn:hover {
  color: #fff;
}

.template-modal-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.category-sidebar {
  width: 250px;
  background: #18181A;
  border-right: 1px solid #333;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  padding: 16px;
  gap: 12px;
}

.category-card {
  height: 90px;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  border: 1px solid #333;
  display: flex;
  align-items: flex-end;
  padding: 12px;
  transition: all 0.2s;
  background-size: cover;
  background-position: center;
}

.category-card:hover {
  border-color: #555;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.5);
}

.category-card.active {
  border: 1px solid #007fd4;
  box-shadow: 0 0 0 1px #007fd4;
}

.category-card.active .category-title {
  color: #fff;
  font-weight: bold;
}

.category-card.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  filter: grayscale(0.5);
}

.category-card.disabled:hover {
  transform: none;
  box-shadow: none;
  border-color: #333;
}

.category-card.game-bg {
  background: linear-gradient(rgba(0,0,0,0.3), rgba(0,0,0,0.8)), url('https://images.unsplash.com/photo-1542751371-adc38448a05e?q=80&w=200&auto=format&fit=crop');
}

.category-card.movie-bg {
  background: linear-gradient(rgba(0,0,0,0.3), rgba(0,0,0,0.8)), url('https://images.unsplash.com/photo-1440404653325-ab127d49abc1?q=80&w=200&auto=format&fit=crop');
}

.category-title {
  position: relative;
  z-index: 2;
  font-size: 15px;
  color: white;
  font-weight: 500;
}

.template-path-setting {
  margin-top: auto;
  padding: 15px;
  background: #252526;
  border: 1px solid #3c3c3c;
  display: flex;
  flex-direction: column;
  border-radius: 4px;
}

.small-path-input {
  width: 100%;
  padding: 4px;
  border: 1px solid #3c3c3c;
  background: #1e1e1e;
  color: #ccc;
  border-radius: 2px;
  font-size: 11px;
  margin-bottom: 6px;
  box-sizing: border-box;
}

.refresh-btn {
  background: #2d2d2d;
  color: #ccc;
  border: 1px solid #444;
  font-size: 11px;
  padding: 4px;
  cursor: pointer;
  border-radius: 2px;
  transition: all 0.2s;
}

.refresh-btn:hover {
  background: #3a3d41;
  color: #fff;
}

.template-content {
  flex: 1;
  padding: 30px;
  overflow-y: auto;
  background: #1e1e1e;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 20px;
}

.template-card {
  background: #fff;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: all 0.2s;
  height: 150px;
}

.template-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0,0,0,0.4);
}

.template-card.selected {
  border-color: #007fd4;
}

.template-thumbnail {
  height: 100px;
  flex: 1;
  background: #333;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

.preview-target {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.template-name {
  padding: 10px;
  font-size: 13px;
  text-align: center;
  background: #fff;
  color: #1a1a1a;
  font-weight: 600;
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.template-card.selected .template-name {
  background: #007fd4;
  color: #fff;
}

.empty-templates {
  color: #888;
  text-align: center;
  margin-top: 40px;
  font-size: 14px;
}

.template-modal-footer {
  padding: 16px 24px;
  background: #252526;
  border-top: 1px solid #333;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Redesign Modal Actions specifically for the Dark Template Modal */
.template-modal-footer .modal-btn {
  padding: 6px 20px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: bold;
  height: 32px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  cursor: pointer;
  transition: background 0.2s;
}

.template-modal-footer .modal-btn.cancel {
  background: transparent;
  color: #ccc;
  border: 1px solid #444;
}

.template-modal-footer .modal-btn.cancel:hover {
  background: #333;
  color: #fff;
  border-color: #555;
}

.template-modal-footer .modal-btn.confirm {
  background: #007fd4;
  color: #fff;
}

.template-modal-footer .modal-btn.confirm:hover {
  background: #0060a0;
}

.template-modal-footer .modal-btn.confirm:disabled {
  background: #3a3d41;
  color: #777;
  cursor: not-allowed;
}

.fallback-thumbnail {
  width: 100%;
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #252526;
}

/* Custom Scrollbar for Dark Layouts */
.category-sidebar::-webkit-scrollbar,
.template-content::-webkit-scrollbar {
  width: 12px;
  background: #1e1e1e;
}

.category-sidebar::-webkit-scrollbar-thumb,
.template-content::-webkit-scrollbar-thumb {
  background: #424346;
  border: 3px solid #1e1e1e;
  border-radius: 6px;
}

.category-sidebar::-webkit-scrollbar-thumb:hover,
.template-content::-webkit-scrollbar-thumb:hover {
  background: #4f5054;
}

/* Toast System Styles */
.toast-container {
  position: fixed;
  top: 24px;
  right: 24px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  z-index: 9999;
  pointer-events: none;
}

.toast-message {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: #252526;
  border-left: 4px solid #409eff;
  border-radius: 6px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2);
  color: #e5e5e5;
  font-size: 14px;
  font-weight: 500;
  pointer-events: auto;
}

.toast-success {
  border-left-color: #67c23a;
}

.toast-error {
  border-left-color: #f56c6c;
}

.toast-info {
  border-left-color: #909399;
}

.toast-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.toast-success .toast-icon {
  color: #67c23a;
}

.toast-error .toast-icon {
  color: #f56c6c;
}

.toast-info .toast-icon {
  color: #909399;
}

/* Toast Transitions */
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(50px);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

.modal-input.input-error {
  border-color: #f56c6c;
}

.validation-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: -8px;
  margin-bottom: 8px;
  text-align: left;
}

</style>