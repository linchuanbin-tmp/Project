<template>
  <div class="documents-page">
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ $t('document.title') }}</h1>
        <p class="page-sub">{{ $t('document.subtitle') }}</p>
      </div>
      <div class="header-actions" style="display: flex; gap: 10px; align-items: center;">
        <el-button
          v-if="isAdminOrDeptAdmin"
          type="primary"
          class="create-btn"
          @click="openCreateDialog"
        >
          <Plus :size="14" />
          {{ $t('document.createDoc') }}
        </el-button>
        <el-button class="refresh-btn" @click="fetchDocuments" :loading="loading">
          <RefreshCw :size="14" :class="{ 'spin': loading }" />
          {{ $t('document.refreshLib') }}
        </el-button>
      </div>
    </div>

    <!-- Main Listing View Container (Border-card style) -->
    <div class="library-container-relative">
      <el-tabs v-model="activeTab" class="library-tabs" type="border-card">
        <!-- 1. System Guides Tab -->
        <el-tab-pane name="system">
          <template #label>
            <div class="tab-label">
              <BookOpen :size="15" />
              <span>{{ $t('document.systemManuals') }}</span>
            </div>
          </template>

          <div class="tab-content-inner">
            <div v-if="filteredSystemDocs.length === 0 && !loading" class="empty-state-box">
              <FolderOpen :size="48" class="empty-icon" />
              <h3>{{ $t('document.noSystemDocs') }}</h3>
              <p>{{ $t('document.noSystemDocsDesc') }}</p>
            </div>

            <div v-else class="cards-grid">
              <div
                v-for="doc in filteredSystemDocs"
                :key="doc.id"
                class="document-card system-doc"
              >
                <div class="card-header">
                  <div class="icon-box" :class="iconBoxClass(doc)">
                    <BookOpen v-if="isMarkdown(doc)" :size="18" />
                    <FileText v-else-if="isPdf(doc)" :size="18" />
                    <FileText v-else-if="isDocx(doc)" :size="18" />
                    <MonitorPlay v-else :size="18" />
                  </div>
                  <div class="header-right-side" style="display: flex; flex-wrap: wrap; align-items: center; gap: 8px;">
                    <span class="security-badge global">{{ $t('document.global') }}</span>
                    <el-tooltip :content="$t('document.ragInfoTooltip')" placement="top">
                      <el-button class="icon-action-btn rag-info" :class="{ indexed: getDocIndexStatus(doc)?.indexed }" @click.stop="openRagInfoDialog(doc)">
                        <Database :size="12" />
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="canManage(doc)" :content="$t('document.editDocTooltip')" placement="top">
                      <el-button v-if="isMarkdown(doc)" class="icon-action-btn edit" @click.stop="openEditDialog(doc)">
                        <Edit :size="12" />
                      </el-button>
                    </el-tooltip>
                    <el-tooltip v-if="canManage(doc)" :content="$t('document.deleteDocTooltip')" placement="top">
                      <el-button class="icon-action-btn delete" @click.stop="handleDeleteDoc(doc)">
                        <Trash2 :size="12" />
                      </el-button>
                    </el-tooltip>
                  </div>
                </div>
                <div class="card-body">
                  <h3 class="doc-title">{{ doc.title }}</h3>
                  <p class="doc-excerpt">{{ doc.content || $t('document.openAccessDesc') }}</p>
                </div>
                <div class="card-footer">
                  <span class="doc-date">{{ $t('document.created') }}: {{ formatDate(doc.createTime) }}</span>
                  <div style="display: flex; gap: 6px;">
                    <el-button v-if="isPdf(doc)" class="read-action-btn" @click="previewDocument(doc)">
                      <Eye :size="14" />
                      {{ $t('document.previewFile') }}
                    </el-button>
                    <el-button v-if="isDocx(doc) || isPpt(doc)" class="read-action-btn" @click="downloadDocument(doc)">
                      <Download :size="14" />
                      {{ $t('document.downloadFile') }}
                    </el-button>
                    <el-button v-if="isMarkdown(doc)" class="read-action-btn" @click="enterReadingMode(doc)">
                      {{ $t('document.readDoc') }}
                      <ChevronRight :size="14" />
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 2. Department Assets Tab -->
        <el-tab-pane name="department">
          <template #label>
            <div class="tab-label">
              <Briefcase :size="15" />
              <span>{{ $t('document.deptAssets') }}</span>
            </div>
          </template>

          <div class="tab-content-inner">
            <!-- Case: Regular user with no department -->
            <div v-if="!userStore.userInfo?.deptId && !isAdmin" class="empty-state-box">
              <Briefcase :size="48" class="empty-icon text-rose" />
              <h3>{{ $t('document.noDeptAssigned') }}</h3>
              <p>{{ $t('document.noDeptAssignedDesc') }}</p>
            </div>

            <template v-else>
              <div v-if="filteredDeptDocs.length === 0 && !loading" class="empty-state-box">
                <FolderOpen :size="48" class="empty-icon" />
                <h3>{{ $t('document.emptyDeptLib') }}</h3>
                <p>{{ $t('document.emptyDeptLibDesc', { dept: userStore.userInfo?.deptName }) }}</p>
              </div>

              <div v-else class="cards-grid">
                <div
                  v-for="doc in filteredDeptDocs"
                  :key="doc.id"
                  class="document-card"
                  :class="{ 'restricted-card': !doc.accessible }"
                >
                  <div class="card-header">
                    <div class="icon-box" :class="iconBoxClass(doc)">
                      <Lock v-if="!doc.accessible" :size="18" />
                      <BookOpen v-else-if="isMarkdown(doc)" :size="18" />
                      <FileText v-else-if="isPdf(doc)" :size="18" />
                      <FileText v-else-if="isDocx(doc)" :size="18" />
                      <MonitorPlay v-else :size="18" />
                    </div>
                    <div class="header-right-side" style="display: flex; flex-wrap: wrap; align-items: center; gap: 8px;">
                      <span class="dept-badge" v-if="doc.deptId">{{ getDeptName(doc.deptId) }}</span>
                      <span class="security-badge" :class="'level-' + doc.securityLevel">
                        Level-{{ doc.securityLevel }} ({{ getClearanceLabel(doc.securityLevel) }})
                      </span>
                      <el-tooltip :content="$t('document.ragInfoTooltip')" placement="top">
                        <el-button class="icon-action-btn rag-info" :class="{ indexed: getDocIndexStatus(doc)?.indexed }" @click.stop="openRagInfoDialog(doc)">
                          <Database :size="12" />
                        </el-button>
                      </el-tooltip>
                      <el-tooltip v-if="canManage(doc)" :content="$t('document.editDocTooltip')" placement="top">
                        <el-button v-if="isMarkdown(doc)" class="icon-action-btn edit" @click.stop="openEditDialog(doc)">
                          <Edit :size="12" />
                        </el-button>
                      </el-tooltip>
                      <el-tooltip v-if="canManage(doc)" :content="$t('document.deleteDocTooltip')" placement="top">
                        <el-button class="icon-action-btn delete" @click.stop="handleDeleteDoc(doc)">
                          <Trash2 :size="12" />
                        </el-button>
                      </el-tooltip>
                    </div>
                  </div>
                  <div class="card-body">
                    <h3 class="doc-title">{{ doc.title }}</h3>
                    <p class="doc-excerpt" v-if="doc.accessible">{{ doc.content || '' }}</p>
                    <p class="doc-excerpt restricted-text" v-else>
                      {{ $t('document.restrictedAccessDesc', { level: doc.securityLevel }) }}
                    </p>
                  </div>
                  <div class="card-footer">
                    <span class="doc-date">{{ $t('document.created') }}: {{ formatDate(doc.createTime) }}</span>

                    <div v-if="doc.accessible" style="display: flex; gap: 6px;">
                      <el-button v-if="!isMarkdown(doc)" class="read-action-btn" @click="downloadDocument(doc)">
                        <Download :size="14" />
                        {{ $t('document.downloadFile') }}
                      </el-button>
                      <el-button v-if="isMarkdown(doc)" class="read-action-btn" @click="enterReadingMode(doc)">
                        {{ $t('document.readDoc') }}
                        <ChevronRight :size="14" />
                      </el-button>
                    </div>
                    <el-button
                      v-else
                      type="warning"
                      class="request-action-btn"
                      @click="enterReadingMode(doc)"
                    >
                      {{ $t('document.requestAccess') }}
                      <ShieldAlert :size="14" />
                    </el-button>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- Search Input positioned in the header area -->
      <div class="header-search-wrap">
        <el-input
          v-model="searchQuery"
          :placeholder="$t('document.searchPlaceholder')"
          class="search-input"
          clearable
        >
          <template #prefix>
            <Search :size="16" class="search-icon" />
          </template>
        </el-input>
      </div>
    </div>

    <!-- IMMERSIVE FULL-SCREEN ZEN READER MODE OVERLAY -->
    <div v-if="readerVisible && selectedDoc" class="zen-reader-overlay animate-slide-up">
      <!-- Fixed Reader Header / Navigation -->
      <div class="zen-nav-bar">
        <div class="nav-left">
          <!-- Clickable Document Title with Info Popover -->
          <el-popover
            placement="bottom-start"
            :width="360"
            trigger="click"
            popper-class="meta-popover"
          >
            <template #reference>
              <div class="nav-title-trigger" :title="$t('document.clickViewMeta')">
                <span class="nav-center-title">{{ selectedDoc.title }}</span>
                <ChevronDown :size="16" class="arrow-icon" />
              </div>
            </template>

            <!-- Metadata Popover Content -->
            <div class="meta-popover-content">
              <h4 class="popover-title">{{ $t('document.clearanceStatus') }}</h4>
              <div class="popover-section">
                <div class="popover-row">
                  <span class="lbl">{{ $t('document.accessLevel') }}:</span>
                  <span class="val security-badge" :class="selectedDoc.deptId ? 'level-' + selectedDoc.securityLevel : 'global'">
                    {{ selectedDoc.deptId ? 'Level-' + selectedDoc.securityLevel : $t('document.globalPolicy') }}
                  </span>
                </div>
                <div class="popover-row">
                  <span class="lbl">{{ $t('document.clearanceTag') }}:</span>
                  <span class="val">{{ selectedDoc.deptId ? getClearanceLabel(selectedDoc.securityLevel) : $t('document.publicSystem') }}</span>
                </div>
                <div class="popover-row">
                  <span class="lbl">{{ $t('document.department') }}:</span>
                  <span class="val">{{ selectedDoc.deptId ? userStore.userInfo?.deptName : $t('document.allDepartments') }}</span>
                </div>
                <div class="popover-row">
                  <span class="lbl">{{ $t('document.createdOn') }}:</span>
                  <span class="val">{{ formatDate(selectedDoc.createTime) }}</span>
                </div>
              </div>

              <div class="popover-divider"></div>

              <h4 class="popover-title">{{ $t('document.ragVectorInfo') }}</h4>
              <div class="popover-section">
                <div class="popover-row">
                  <span class="lbl">{{ $t('document.vectorIndex') }}:</span>
                  <span class="val">Milvus</span>
                </div>
                <div class="popover-row">
                  <span class="lbl">Chunks:</span>
                  <span class="val">{{ getDocIndexStatus(selectedDoc)?.chunkCount || 0 }}</span>
                </div>
                <div class="popover-row">
                  <span class="lbl">{{ $t('document.groundingStatus') }}:</span>
                  <span class="val" :class="getDocIndexStatus(selectedDoc)?.indexed ? 'text-success' : 'text-red'">
                    {{ getDocIndexStatus(selectedDoc)?.indexed ? 'Indexed' : 'Not indexed' }}
                  </span>
                </div>
                <div class="popover-row">
                  <span class="lbl">Last Indexed:</span>
                  <span class="val">{{ formatDateTime(getDocIndexStatus(selectedDoc)?.lastIndexedAt) }}</span>
                </div>
              </div>
            </div>
          </el-popover>
        </div>

        <div class="nav-right">
          <button class="nav-close-btn" @click="exitReadingMode">
            <X :size="16" />
            {{ $t('document.closeReader') }}
          </button>
        </div>
      </div>

      <!-- Immersive Reader Body -->
      <div class="zen-workspace">

        <!-- Case A: Restricted Document (Lock Screen inside reader) -->
        <div v-if="!selectedDoc.accessible" class="immersive-lock-view">
          <div class="lock-panel animate-fade-in">
            <div class="lock-icon-circle">
              <Lock :size="40" />
            </div>
            <h2>{{ $t('document.confidentialDoc') }}</h2>
            <p class="lock-msg">
              {{ $t('document.confidentialDesc') }}
            </p>

            <div class="lock-meta-table">
              <div class="meta-row">
                <span class="lbl">{{ $t('document.assetTitle') }}:</span>
                <span class="val">{{ selectedDoc.title }}</span>
              </div>
              <div class="meta-row" v-if="selectedDoc.deptId">
                <span class="lbl">{{ $t('document.department') }}:</span>
                <span class="val">{{ getDeptName(selectedDoc.deptId) }}</span>
              </div>
              <div class="meta-row">
                <span class="lbl">{{ $t('document.requiredClearance') }}:</span>
                <span class="val text-red">Level-{{ selectedDoc.securityLevel }} ({{ getClearanceLabel(selectedDoc.securityLevel) }})</span>
              </div>
              <div class="meta-row">
                <span class="lbl">{{ $t('document.yourClearance') }}:</span>
                <span class="val text-blue">Level-{{ userStore.userInfo?.clearanceLevel || 1 }}</span>
              </div>
            </div>

            <el-button
              type="warning"
              class="dialog-btn-confirm scale-btn"
              @click="openRequestDialog(selectedDoc)"
            >
              <ShieldAlert :size="14" />
              {{ $t('document.requestTempAccess') }}
            </el-button>
          </div>
        </div>

        <!-- Case B: Document Authorized (Pure Markdown Zen Workspace) -->
        <template v-else>
          <!-- Left/Center: Document Scroll Sheet -->
          <div class="zen-paper-scroll" id="zen-paper-scroll">
            <div class="zen-paper-sheet">
              <!-- Rendered Markdown Body -->
              <div
                class="markdown-body"
                v-html="parsedMarkdown"
              ></div>
            </div>
          </div>

          <!-- Right: Document Navigation Outline / TOC -->
          <div class="zen-outline-sidebar" v-if="docToc.length > 0">
            <div class="outline-title">{{ $t('document.outlineNav') }}</div>
            <ul class="outline-list">
              <li
                v-for="item in docToc"
                :key="item.id"
                :class="'outline-l' + item.level"
                @click="scrollToHeading(item.id)"
              >
                {{ item.text }}
              </li>
            </ul>
          </div>
        </template>

      </div>
    </div>

    <!-- Request Access Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="$t('document.requestDocAccess')"
      width="460px"
      class="custom-dialog"
    >
      <div class="dialog-body" v-if="requestDoc">
        <p class="dialog-desc">
          {{ $t('document.requestAccessDesc') }}
        </p>
        <div class="request-doc-preview">
          <p class="req-title"><strong>{{ requestDoc.title }}</strong></p>
          <p class="req-meta">{{ $t('document.requestClearance', { req: requestDoc.securityLevel, user: userStore.userInfo?.clearanceLevel || 1 }) }}</p>
        </div>
        <p class="dialog-desc" style="margin-top: 16px;" v-if="deptManager">
          {{ $t('document.requestRouteTo', { manager: deptManager.username }) }}
        </p>
        <p class="dialog-desc" style="margin-top: 16px; color: #dc2626;" v-else>
          {{ $t('document.noManagerAvailable') }}
        </p>

        <el-form label-position="top" style="margin-top: 16px;">
          <el-form-item :label="$t('document.requestReason')">
            <el-input
              v-model="requestReason"
              type="textarea"
              :rows="3"
              :placeholder="$t('document.requestReasonPlaceholder')"
              class="custom-textarea"
            />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            @click="handleRequestSubmit"
            :loading="submitLoading"
            class="dialog-btn-confirm"
          >
            {{ $t('document.submitRequest') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Create/Edit Document Dialog -->
    <el-dialog
      v-model="manageDialogVisible"
      :title="manageDialogTitle"
      width="800px"
      class="custom-dialog"
      :close-on-click-modal="false"
    >
      <div class="dialog-body">
        <div class="create-layout">
          <!-- Left: Settings panel -->
          <div class="create-left">
            <!-- File type selector (create mode only) -->
            <div v-if="!manageForm.id" class="create-section">
              <div class="section-label">{{ $t('document.docType') }}</div>
              <div class="type-cards">
                <div
                  class="type-card"
                  :class="{ active: createMode === 'markdown' }"
                  @click="createMode = 'markdown'"
                >
                  <BookOpen :size="20" />
                  <span class="type-name">Markdown</span>
                  <span class="type-desc">{{ $t('document.typeMarkdownDesc') }}</span>
                </div>
                <div
                  class="type-card"
                  :class="{ active: createMode === 'pdf' }"
                  @click="createMode = 'pdf'"
                >
                  <FileText :size="20" />
                  <span class="type-name">PDF</span>
                  <span class="type-desc">{{ $t('document.typePdfDesc') }}</span>
                </div>
                <div
                  class="type-card"
                  :class="{ active: createMode === 'docx' }"
                  @click="createMode = 'docx'"
                >
                  <FileText :size="20" />
                  <span class="type-name">DOCX</span>
                  <span class="type-desc">{{ $t('document.typeDocxDesc') }}</span>
                </div>
                <div
                  class="type-card"
                  :class="{ active: createMode === 'ppt' }"
                  @click="createMode = 'ppt'"
                >
                  <MonitorPlay :size="20" />
                  <span class="type-name">PPT</span>
                  <span class="type-desc">{{ $t('document.typePptDesc') }}</span>
                </div>
              </div>
            </div>

            <!-- Title -->
            <div class="create-section">
              <div class="section-label">{{ $t('document.docTitle') }}</div>
              <el-input
                v-model="manageForm.title"
                :placeholder="$t('document.docTitlePlaceholder')"
                class="custom-input"
              />
            </div>

            <!-- Security & Dept -->
            <div class="create-section">
              <div class="section-label">{{ $t('document.securityLevel') }}</div>
              <el-select v-model="manageForm.securityLevel" style="width: 100%;">
                <el-option :label="$t('document.levelPublic')" :value="1" />
                <el-option :label="$t('document.levelInternal')" :value="2" />
                <el-option :label="$t('document.levelConfidential')" :value="3" />
              </el-select>
            </div>

            <div class="create-section" v-if="isAdmin || isDeptAdmin">
              <div class="section-label">{{ $t('document.targetDept') }}</div>
              <el-select
                v-if="isAdmin"
                v-model="manageForm.deptId"
                :placeholder="$t('document.globalSystem')"
                clearable
                style="width: 100%;"
              >
                <el-option v-for="dept in departments" :key="dept.id" :label="dept.deptName" :value="dept.id" />
              </el-select>
              <el-input v-else :value="userStore.userInfo?.deptName || $t('document.yourDept')" disabled class="custom-input" />
            </div>

            <!-- File info (for file types) -->
            <div v-if="selectedFile && !manageForm.id" class="create-section">
              <div class="section-label">{{ $t('document.fileSizeLabel') }}</div>
              <div class="file-meta">{{ selectedFile.name }}</div>
              <div class="file-meta-size">{{ formatFileSize(selectedFile.size) }}</div>
            </div>
          </div>

          <!-- Right: Content editor / Upload area -->
          <div class="create-right">
            <!-- Markdown mode -->
            <template v-if="createMode === 'markdown' || manageForm.id">
              <el-input
                v-model="manageForm.content"
                type="textarea"
                :rows="18"
                :placeholder="$t('document.docContentPlaceholder')"
                class="custom-textarea markdown-editor content-editor"
              />
            </template>

            <!-- File upload mode -->
            <template v-else>
              <div class="file-drop-zone" @click="triggerFileInput" @dragover.prevent @drop.prevent="onFileDrop">
                <input ref="fileInputRef" type="file" :accept="fileAccept" style="display: none" @change="onFileSelected" />
                <template v-if="selectedFile">
                  <div class="file-preview-card">
                    <div class="file-preview-icon">
                      <FileText v-if="createMode === 'pdf'" :size="40" />
                      <FileText v-else-if="createMode === 'docx'" :size="40" />
                      <MonitorPlay v-else :size="40" />
                    </div>
                    <div class="file-preview-name">{{ selectedFile.name }}</div>
                    <div class="file-preview-size">{{ formatFileSize(selectedFile.size) }}</div>
                    <el-button size="small" class="file-change-btn" @click.stop="clearFile">
                      {{ $t('document.changeFile') }}
                    </el-button>
                  </div>
                </template>
                <template v-else>
                  <Upload :size="40" class="drop-icon" />
                  <div class="drop-text">{{ $t('document.dropFileHere') }}</div>
                  <div class="drop-hint">PDF, DOCX, PPT — Max 50MB</div>
                </template>
              </div>
            </template>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="manageDialogVisible = false" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button
            type="primary"
            @click="handleManageSubmit"
            :loading="manageSubmitLoading"
            class="dialog-btn-confirm"
          >
            {{ $t('common.save') }}
          </el-button>
        </span>
      </template>
    </el-dialog>

    <!-- RAG Index Info Dialog -->
    <el-dialog
      v-model="ragInfoDialogVisible"
      :title="$t('document.ragInfoTitle') + ' - ' + (ragInfoDoc?.title || '')"
      width="760px"
      class="custom-dialog"
    >
      <div class="dialog-body">
        <!-- Index Status Summary -->
        <div class="rag-info-summary">
          <div class="rag-info-item">
            <span class="rag-info-label">{{ $t('document.ragIndexStatus') }}</span>
            <span class="rag-info-value" :class="getDocIndexStatus(ragInfoDoc)?.indexed ? 'text-success' : 'text-red'">
              {{ getDocIndexStatus(ragInfoDoc)?.indexed ? $t('document.ragIndexed') : $t('document.ragNotIndexed') }}
            </span>
          </div>
          <div class="rag-info-item">
            <span class="rag-info-label">File Type</span>
            <span class="rag-info-value">{{ getDocIndexStatus(ragInfoDoc)?.fileType || ragInfoDoc?.fileType || 'MARKDOWN' }}</span>
          </div>
          <div class="rag-info-item">
            <span class="rag-info-label">Parse Status</span>
            <span
              class="rag-info-value"
              :class="parseStatusClass(getDocIndexStatus(ragInfoDoc)?.parseStatus || ragInfoDoc?.parseStatus)"
            >
              {{ getDocIndexStatus(ragInfoDoc)?.parseStatus || ragInfoDoc?.parseStatus || 'READY' }}
            </span>
          </div>
          <div class="rag-info-item">
            <span class="rag-info-label">{{ $t('document.ragChunkCount') }}</span>
            <span class="rag-info-value">{{ getDocIndexStatus(ragInfoDoc)?.chunkCount || 0 }}</span>
          </div>
          <div class="rag-info-item">
            <span class="rag-info-label">{{ $t('document.ragLastIndexed') }}</span>
            <span class="rag-info-value">{{ formatDateTime(getDocIndexStatus(ragInfoDoc)?.lastIndexedAt) }}</span>
          </div>
        </div>

        <!-- Chunk List -->
        <div v-if="chunkLoading" class="chunk-loading">
          <RefreshCw :size="16" class="spin" />
          <span>Loading chunks...</span>
        </div>
        <div v-else-if="selectedChunks.length === 0" class="chunk-empty">
          No chunks indexed for this document.
        </div>
        <div v-else class="chunk-list">
          <div v-for="chunk in selectedChunks" :key="chunk.chunkId" class="chunk-item">
            <div class="chunk-meta">
              <span>#{{ chunk.chunkIndex }}</span>
              <span>chunkId={{ chunk.chunkId }}</span>
              <span>tokens={{ chunk.tokenCount || 0 }}</span>
              <span>level={{ chunk.securityLevel || 1 }}</span>
            </div>
            <div class="chunk-vector">{{ chunk.vectorId }}</div>
            <p class="chunk-text">{{ chunk.chunkText }}</p>
          </div>
        </div>
      </div>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="ragInfoDialogVisible = false" class="dialog-btn-cancel">{{ $t('common.cancel') }}</el-button>
          <el-button
            v-if="ragInfoDoc && canManage(ragInfoDoc)"
            type="primary"
            @click="handleReindexDoc(ragInfoDoc)"
            :loading="isIndexingDoc(ragInfoDoc.id)"
            class="dialog-btn-confirm"
          >
            {{ $t('document.ragReindex') }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@stores/modules/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  FileText, Lock,
  FolderOpen, RefreshCw, ShieldAlert, Briefcase, BookOpen,
  Search, ChevronRight, ShieldCheck, Database, X, ChevronLeft, ChevronDown,
  Plus, Edit, Trash2, Upload, Download, MonitorPlay
} from 'lucide-vue-next'
import { getDeptDocuments, createDocument, updateDocument, deleteDocument, getDepartmentsList, uploadDocument, getDocumentDownloadUrl } from '@/api/department'
import { sendNotification, getUsers } from '@/api/notification'
import {
  getRagDocumentChunks,
  getRagDocumentIndexStatus,
  indexRagDocument,
  reprocessRagDocument,
  type RagDocumentChunkDetail,
  type RagDocumentIndexStatus
} from '@/api/rag'
import { marked } from 'marked'

// Configure marked with a custom heading renderer to inject IDs for TOC anchoring
const customRenderer = new marked.Renderer()
customRenderer.heading = function (arg1: any, arg2?: any) {
  let text = ''
  let depth = 1
  if (typeof arg1 === 'object' && arg1 !== null) {
    text = arg1.text || ''
    depth = arg1.depth || 1
  } else {
    text = arg1 || ''
    depth = arg2 || 1
  }

  const id = text.toLowerCase()
    .replace(/[^一-龥\w\s-]/g, '') // Support Chinese/alphanumeric/spaces
    .trim()
    .replace(/\s+/g, '-')
  return `<h${depth} id="${id}">${text}</h${depth}>`
}
marked.use({ renderer: customRenderer })

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const documents = ref<any[]>([])
const deptMembers = ref<any[]>([])
const loading = ref(false)
const submitLoading = ref(false)
const activeTab = ref('system')
const searchQuery = ref('')
const selectedDoc = ref<any>(null)
const readerVisible = ref(false)
const ragIndexStatusMap = ref<Record<number, RagDocumentIndexStatus>>({})
const indexingDocIds = ref<Set<number>>(new Set())
const ragInfoDialogVisible = ref(false)
const chunkLoading = ref(false)
const ragInfoDoc = ref<any>(null)
const selectedChunks = ref<RagDocumentChunkDetail[]>([])

const dialogVisible = ref(false)
const requestDoc = ref<any>(null)
const requestReason = ref('')

// In-place Management state
const manageDialogVisible = ref(false)
const manageSubmitLoading = ref(false)
const departments = ref<any[]>([])

const manageForm = ref({
  id: undefined as number | undefined,
  title: '',
  content: '',
  securityLevel: 1,
  deptId: null as number | null
})

const createMode = ref<'markdown' | 'pdf' | 'docx' | 'ppt'>('markdown')
const selectedFile = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const fileAccept = computed(() => {
  switch (createMode.value) {
    case 'pdf': return '.pdf'
    case 'docx': return '.docx'
    case 'ppt': return '.pptx,.ppt'
    default: return ''
  }
})

// Role check computed properties
const isAdmin = computed(() => {
  return userStore.userInfo?.roles?.includes('ROLE_ADMIN') || false
})

const isDeptAdmin = computed(() => {
  return userStore.userInfo?.roles?.includes('ROLE_DEPT_ADMIN') || false
})

const isAdminOrDeptAdmin = computed(() => {
  return isAdmin.value || isDeptAdmin.value
})

const manageDialogTitle = computed(() => {
  return manageForm.value.id ? t('document.editDoc') : t('document.createDoc')
})

// Document management authorization scoping check
const canManage = (doc: any) => {
  if (isAdmin.value) return true
  if (isDeptAdmin.value) {
    return doc.deptId !== null && doc.deptId === userStore.userInfo?.deptId
  }
  return false
}

// Compute filtered document lists based on search query
const systemDocs = computed(() => {
  return documents.value.filter(doc => doc.deptId === null)
})

const departmentDocs = computed(() => {
  return documents.value.filter(doc => doc.deptId !== null)
})

const filteredSystemDocs = computed(() => {
  if (!searchQuery.value) return systemDocs.value
  const query = searchQuery.value.toLowerCase()
  return systemDocs.value.filter(doc => doc.title.toLowerCase().includes(query))
})

const filteredDeptDocs = computed(() => {
  if (!searchQuery.value) return departmentDocs.value
  const query = searchQuery.value.toLowerCase()
  return departmentDocs.value.filter(doc => doc.title.toLowerCase().includes(query))
})

// Find Department Administrator to route notification
const deptManager = computed(() => {
  // Find the manager of the requested document's department
  if (!requestDoc.value) return null
  const docDeptId = requestDoc.value.deptId

  if (docDeptId) {
    // Document belongs to a department — find its admin
    const deptAdmin = deptMembers.value.find(
      m => m.deptId === docDeptId && (m.roles?.includes('ROLE_DEPT_ADMIN') || m.roles?.includes('ROLE_ADMIN'))
    )
    if (deptAdmin) return deptAdmin
  }

  // Fallback: any system admin
  return deptMembers.value.find(m => m.roles?.includes('ROLE_ADMIN')) || null
})

const noManagerAvailable = computed(() => !deptManager.value)

// Render selected document markdown to HTML
const parsedMarkdown = computed(() => {
  if (!selectedDoc.value || !selectedDoc.value.content) return ''
  return marked.parse(selectedDoc.value.content)
})

// Extract headings from markdown content dynamically
const docToc = computed(() => {
  if (!selectedDoc.value || !selectedDoc.value.content || !selectedDoc.value.accessible) return []
  const lines = selectedDoc.value.content.split('\n')
  const headings: any[] = []
  let inCodeBlock = false

  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      inCodeBlock = !inCodeBlock
      continue
    }
    if (inCodeBlock) continue

    const match = line.match(/^(#{1,3})\s+(.*)$/)
    if (match) {
      const level = match[1].length
      const text = match[2].trim()
      const id = text.toLowerCase()
        .replace(/[^一-龥\w\s-]/g, '')
        .trim()
        .replace(/\s+/g, '-')
      headings.push({ level, text, id })
    }
  }
  return headings
})

const enterReadingMode = (doc: any) => {
  selectedDoc.value = doc
  readerVisible.value = true
}

const exitReadingMode = () => {
  readerVisible.value = false
  selectedDoc.value = null
}

const fetchDocuments = async () => {
  loading.value = true
  try {
    const res: any = await getDeptDocuments()
    documents.value = res || []
    await fetchRagIndexStatus()

    // Maintain selection if already in reader mode
    if (selectedDoc.value) {
      const updatedDoc = documents.value.find(d => d.id === selectedDoc.value.id)
      if (updatedDoc) {
        selectedDoc.value = updatedDoc
      }
    }
  } catch (error: any) {
    console.error('Failed to load documents:', error)
    ElMessage.error(t('document.loadError'))
  } finally {
    loading.value = false
  }
}

const fetchRagIndexStatus = async () => {
  try {
    const statuses = await getRagDocumentIndexStatus()
    ragIndexStatusMap.value = (statuses || []).reduce((acc: Record<number, RagDocumentIndexStatus>, item: RagDocumentIndexStatus) => {
      acc[item.documentId] = item
      return acc
    }, {})
  } catch (error) {
    console.warn('Failed to load RAG index status:', error)
    ragIndexStatusMap.value = {}
  }
}

const fetchAllUsers = async () => {
  try {
    const allUsers: any = await getUsers()
    deptMembers.value = allUsers || []
  } catch (error) {
    console.error('Failed to load users:', error)
  }
}

const getClearanceLabel = (level: number) => {
  if (level === 3) return t('document.confidential')
  if (level === 2) return t('document.internal')
  return t('document.public')
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

const formatDateTime = (dateStr?: string) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('en-US', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getDocIndexStatus = (doc: any) => {
  if (!doc?.id) return undefined
  return ragIndexStatusMap.value[doc.id]
}

const isIndexingDoc = (documentId: number) => {
  return indexingDocIds.value.has(documentId)
}

const setIndexingDoc = (documentId: number, indexing: boolean) => {
  const next = new Set(indexingDocIds.value)
  if (indexing) {
    next.add(documentId)
  } else {
    next.delete(documentId)
  }
  indexingDocIds.value = next
}

const scrollToHeading = (id: string) => {
  const el = document.getElementById(id)
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

const openRequestDialog = (doc: any) => {
  requestDoc.value = doc
  requestReason.value = ''
  dialogVisible.value = true
}

const handleRequestSubmit = async () => {
  if (!requestDoc.value) return
  if (!deptManager.value) {
    ElMessage.warning(t('document.noManagerAvailable'))
    return
  }
  if (!requestReason.value.trim()) {
    ElMessage.warning(t('document.reasonRequired'))
    return
  }

  submitLoading.value = true
  try {
    const payload = JSON.stringify({
      documentId: requestDoc.value.id,
      title: requestDoc.value.title,
      clearanceLevel: requestDoc.value.securityLevel,
      reason: requestReason.value || 'Required for standard business procedures.'
    })

    await sendNotification({
      receiverId: deptManager.value.id,
      title: `RAG Permission Escalation Request`,
      content: `Employee @${userStore.userInfo?.username} requests temporary access to "${requestDoc.value.title}" (Security: Level-${requestDoc.value.securityLevel}).`,
      notifyType: 'RAG_APPLY',
      payload: payload
    })

    ElMessage.success(t('document.requestSubmitSuccess'))
    dialogVisible.value = false
  } catch (error: any) {
    console.error('Failed to submit request:', error)
    ElMessage.error(error.message || t('document.requestSubmitError'))
  } finally {
    submitLoading.value = false
  }
}

// Fetch departments for Super Admin target selection
const fetchDepartments = async () => {
  try {
    const res: any = await getDepartmentsList()
    departments.value = res || []
  } catch (error) {
    console.error('Failed to load departments list:', error)
  }
}

// Handlers for Document management dialog triggers
const openCreateDialog = () => {
  manageForm.value = {
    id: undefined,
    title: '',
    content: '',
    securityLevel: 1,
    deptId: isDeptAdmin.value ? userStore.userInfo?.deptId : null
  }
  createMode.value = 'markdown'
  selectedFile.value = null
  manageDialogVisible.value = true
}

const openEditDialog = (doc: any) => {
  manageForm.value = {
    id: doc.id,
    title: doc.title,
    content: doc.content || '',
    securityLevel: doc.securityLevel || 1,
    deptId: doc.deptId
  }
  createMode.value = 'markdown'
  selectedFile.value = null
  manageDialogVisible.value = true
}

const triggerFileInput = () => {
  fileInputRef.value?.click()
}

const onFileSelected = (e: Event) => {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return
  selectedFile.value = file
  // Auto-fill title from filename
  if (!manageForm.value.title.trim()) {
    const nameWithoutExt = file.name.replace(/\.[^/.]+$/, '')
    manageForm.value.title = nameWithoutExt
  }
}

const clearFile = () => {
  selectedFile.value = null
  if (fileInputRef.value) fileInputRef.value.value = ''
}

const onFileDrop = (e: DragEvent) => {
  const file = e.dataTransfer?.files?.[0]
  if (!file) return
  selectedFile.value = file
  if (!manageForm.value.title.trim()) {
    manageForm.value.title = file.name.replace(/\.[^/.]+$/, '')
  }
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1048576).toFixed(1) + ' MB'
}

const handleManageSubmit = async () => {
  if (!manageForm.value.title.trim()) {
    ElMessage.warning(t('document.titleRequired'))
    return
  }

  // File upload mode
  if (!manageForm.value.id && createMode.value !== 'markdown') {
    if (!selectedFile.value) {
      ElMessage.warning(t('document.contentRequired'))
      return
    }
    manageSubmitLoading.value = true
    try {
      await uploadDocument(
        selectedFile.value,
        manageForm.value.title,
        manageForm.value.securityLevel,
        manageForm.value.deptId
      )
      ElMessage.success(t('document.createSuccess'))
      manageDialogVisible.value = false
      await fetchDocuments()
    } catch (error: any) {
      console.error('Failed to upload document:', error)
      ElMessage.error(error.message || t('document.saveError'))
    } finally {
      manageSubmitLoading.value = false
    }
    return
  }

  // Markdown text mode
  if (!manageForm.value.content.trim()) {
    ElMessage.warning(t('document.contentRequired'))
    return
  }

  manageSubmitLoading.value = true
  try {
    const payload = {
      title: manageForm.value.title,
      content: manageForm.value.content,
      securityLevel: manageForm.value.securityLevel,
      deptId: manageForm.value.deptId
    }

    if (manageForm.value.id) {
      await updateDocument({
        id: manageForm.value.id,
        ...payload
      })
      ElMessage.success(t('document.updateSuccess'))
    } else {
      await createDocument(payload)
      ElMessage.success(t('document.createSuccess'))
    }
    manageDialogVisible.value = false
    await fetchDocuments()
  } catch (error: any) {
    console.error('Failed to save document:', error)
    ElMessage.error(error.message || t('document.saveError'))
  } finally {
    manageSubmitLoading.value = false
  }
}

// File type helpers
const isMarkdown = (doc: any) => !doc.fileType || doc.fileType === 'MARKDOWN'
const isPdf = (doc: any) => doc.fileType === 'PDF'
const isDocx = (doc: any) => doc.fileType === 'DOCX'
const isPpt = (doc: any) => doc.fileType === 'PPT'

const getDeptName = (deptId: number | null | undefined) => {
  if (!deptId) return ''
  const dept = departments.value.find((d: any) => d.id === deptId)
  return dept?.deptName || ''
}

const iconBoxClass = (doc: any) => ({
  'system': isMarkdown(doc),
  'pdf-file': isPdf(doc),
  'docx-file': isDocx(doc),
  'ppt-file': isPpt(doc),
  'locked': !doc.accessible && !isMarkdown(doc)
})

const previewDocument = async (doc: any) => {
  try {
    const token = userStore.token
    const response = await fetch(`/api/user/document/download/${doc.id}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || 'Preview failed')
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to preview document')
  }
}

const downloadDocument = async (doc: any) => {
  try {
    const token = userStore.token
    const response = await fetch(`/api/user/document/download/${doc.id}`, {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || 'Download failed')
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = doc.title || `document-${doc.id}`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error.message || 'Failed to download document')
  }
}

const handleReindexDoc = async (doc: any) => {
  if (!doc?.id) return
  setIndexingDoc(doc.id, true)
  try {
    const status = getDocIndexStatus(doc)
    const result = status?.hasStoredFile
      ? await reprocessRagDocument(doc.id)
      : await indexRagDocument(doc.id)
    if (result.status === 'SUCCESS') {
      ElMessage.success(result.message || 'RAG index updated.')
    } else {
      ElMessage.warning(result.message || 'RAG index task did not complete successfully.')
    }
    await fetchRagIndexStatus()
    if (ragInfoDialogVisible.value && ragInfoDoc.value?.id === doc.id) {
      await loadDocumentChunks(doc)
    }
  } catch (error: any) {
    console.error('Failed to reindex document:', error)
    ElMessage.error(error.message || 'Failed to reindex document.')
  } finally {
    setIndexingDoc(doc.id, false)
  }
}

const parseStatusClass = (status?: string) => {
  if (status === 'DONE') return 'text-success'
  if (status === 'FAILED') return 'text-red'
  if (status === 'PENDING') return 'text-warning'
  return ''
}

const openRagInfoDialog = async (doc: any) => {
  ragInfoDoc.value = doc
  ragInfoDialogVisible.value = true
  await loadDocumentChunks(doc)
}

const loadDocumentChunks = async (doc: any) => {
  if (!doc?.id) return
  chunkLoading.value = true
  try {
    selectedChunks.value = await getRagDocumentChunks(doc.id)
  } catch (error: any) {
    console.error('Failed to load document chunks:', error)
    selectedChunks.value = []
    ElMessage.error(error.message || 'Failed to load document chunks.')
  } finally {
    chunkLoading.value = false
  }
}

const handleDeleteDoc = (doc: any) => {
  ElMessageBox.confirm(
    t('document.deleteConfirm', { title: doc.title }),
    t('document.warning'),
    {
      confirmButtonText: t('common.delete'),
      cancelButtonText: t('common.cancel'),
      type: 'warning',
      confirmButtonClass: 'el-button--danger'
    }
  ).then(async () => {
    try {
      await deleteDocument(doc.id)
      ElMessage.success(t('document.deleteSuccess'))
      await fetchDocuments()
    } catch (error: any) {
      console.error('Failed to delete document:', error)
      ElMessage.error(error.message || t('document.deleteError'))
    }
  }).catch(() => {})
}

onMounted(async () => {
  await fetchDocuments()
  fetchAllUsers()
  fetchDepartments()
  // Auto-open reader if navigated with document ID from RAG page
  const docId = route.query.id
  if (docId) {
    const doc = documents.value.find((d: any) => d.id === Number(docId) || String(d.id) === docId)
    if (doc) {
      enterReadingMode(doc)
    }
  }
})

watch(
  () => route.query.query,
  (newQuery) => {
    if (newQuery) {
      searchQuery.value = newQuery as string
      activeTab.value = 'dept'
      router.replace({ query: {} })
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.documents-page {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', sans-serif;
  padding: 16px 0;
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
}

/* ── Page Header ── */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-top: 20px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  margin: 0 0 6px 0;
  letter-spacing: -0.5px;
}

.page-sub {
  font-size: 14px;
  color: #6b7280;
  margin: 0;
}

.create-btn {
  background: #111827 !important;
  border: 1px solid #111827 !important;
  border-radius: 9px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.create-btn:hover {
  background: #1f2937 !important;
  border-color: #1f2937 !important;
  color: #fff !important;
}

.refresh-btn {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  height: 38px !important;
  padding: 0 16px !important;
  transition: all 0.15s;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.refresh-btn:hover {
  background: #f9fafb !important;
  border-color: #cbd5e1 !important;
  color: #111827 !important;
}

.create-btn :deep(span),
.refresh-btn :deep(span) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 100%;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── Library Container (Border-card tabs style) ── */
.library-container-relative {
  position: relative;
}

/* Modern Pill-Shaped Right-Aligned Search Box */
.header-search-wrap {
  position: absolute;
  top: 10px; /* Centered vertically inside 56px header */
  right: 16px;
  z-index: 10;
  width: 280px;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 99px; /* Pill shape */
  background: #f3f4f6; /* Modern soft gray */
  border: none !important;
  box-shadow: none !important;
  padding: 4px 14px;
  height: 36px;
  transition: all 0.25s ease;
}

.search-input :deep(.el-input__wrapper:hover) {
  background: #e5e7eb;
}

.search-input :deep(.el-input__wrapper.is-focus) {
  background: #ffffff;
  box-shadow: 0 0 0 1px #111827 !important; /* Elegant thin border on focus */
}

.search-icon {
  color: #9ca3af;
  margin-right: 6px;
  transition: color 0.2s;
}

.search-input :deep(.el-input__wrapper.is-focus) .search-icon {
  color: #111827;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-content-inner {
  padding: 24px;
}

/* Custom Sleek Tabs Overrides for Library */
:deep(.el-tabs--border-card) {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 10px 30px -10px rgba(0,0,0,0.04);
  overflow: hidden;
}

:deep(.el-tabs--border-card > .el-tabs__header) {
  background-color: #ffffff; /* Modern pure white background */
  border-bottom: 1px solid #f3f4f6; /* Super faint modern divider */
  padding: 0 16px;
  height: 56px; /* Increased height for spacious breathing room */
  display: flex;
  align-items: center;
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__nav-wrap) {
  margin-bottom: 0;
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item) {
  color: #6b7280;
  font-weight: 500;
  font-size: 14px;
  height: 56px;
  line-height: 56px;
  transition: all 0.25s ease;
  border: none !important;
  margin: 0 8px;
  padding: 0 4px !important;
  border-bottom: 2px solid transparent !important;
}

:deep(.el-tabs--border-card > .el-tabs__header .el-tabs__item.is-active) {
  color: #111827;
  background-color: transparent !important;
  font-weight: 600;
  border-bottom: 2px solid #111827 !important;
}

:deep(.el-tabs--border-card > .el-tabs__content) {
  padding: 0;
}

/* Document Grid Cards */
.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 20px;
}

.document-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  padding: 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 220px;
  height: auto;
  box-sizing: border-box;
  transition: all 0.2s ease;
}

.document-card:hover {
  border-color: #94a3b8;
  background: #f8fafc;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.02);
}

.document-card.restricted-card {
  background: #fafafb;
  border-color: #f1f5f9;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.icon-box {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
}

.icon-box.system {
  background: #f0fdfa;
  color: #0d9488;
}

.icon-box.locked {
  background: #fff1f2;
  color: #e11d48;
}

.icon-box.pdf-file {
  background: #fef2f2;
  color: #dc2626;
}

.icon-box.docx-file {
  background: #eff6ff;
  color: #2563eb;
}

.icon-box.ppt-file {
  background: #fff7ed;
  color: #ea580c;
}

.security-badge {
  font-size: 10px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 20px;
  letter-spacing: 0.3px;
  display: inline-block;
  white-space: nowrap;
}

.dept-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 20px;
  background: #f0fdfa;
  color: #0d9488;
  white-space: nowrap;
}

.security-badge.global {
  background: #f0fdfa;
  color: #0d9488;
}

.security-badge.level-1 {
  background: #f0fdf4;
  color: #16a34a;
}

.security-badge.level-2 {
  background: #eff6ff;
  color: #2563eb;
}

.security-badge.level-3 {
  background: #fff1f2;
  color: #e11d48;
}

.card-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
}

.doc-title {
  font-size: 15px;
  font-weight: 650;
  color: #1e293b;
  margin: 0 0 8px 0;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  min-height: 21px;
}

.doc-excerpt {
  font-size: 12.5px;
  color: #64748b;
  line-height: 1.5;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.doc-excerpt.restricted-text {
  color: #94a3b8;
  font-style: italic;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
  margin-top: auto;
}

.doc-date {
  font-size: 12px;
  color: #94a3b8;
}

.read-action-btn {
  background: #ffffff !important;
  border: 1px solid #cbd5e1 !important;
  border-radius: 8px !important;
  color: #334155 !important;
  font-weight: 600 !important;
  font-size: 12.5px !important;
  height: 32px !important;
  padding: 0 12px !important;
  display: flex;
  align-items: center;
  gap: 4px;
}

.read-action-btn:hover {
  background: #f8fafc !important;
  color: #0f172a !important;
  border-color: #94a3b8 !important;
}

.request-action-btn {
  border-radius: 8px !important;
  font-weight: 600 !important;
  font-size: 12.5px !important;
  height: 32px !important;
  padding: 0 12px !important;
  display: flex;
  align-items: center;
  gap: 4px;
}

.empty-state-box {
  text-align: center;
  padding: 60px 40px;
  color: #64748b;
  max-width: 460px;
  margin: 40px auto;
}

.empty-icon {
  color: #cbd5e1;
  margin-bottom: 16px;
}

.empty-icon.text-rose {
  color: #fecdd3;
}

.empty-state-box h3 {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 8px 0;
}

.empty-state-box p {
  font-size: 13px;
  line-height: 1.5;
  margin: 0;
  color: #94a3b8;
}

/* ── IMMERSIVE FULL-SCREEN ZEN READER OVERLAY ── */
.zen-reader-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: #f8fafc;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* Nav Bar */
.zen-nav-bar {
  height: 60px;
  border-bottom: 1px solid #e2e8f0;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.01);
  flex-shrink: 0;
}

.nav-left {
  display: flex;
  align-items: center;
}

.nav-title-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 8px;
  transition: all 0.2s;
  user-select: none;
}

.nav-title-trigger:hover {
  background: #f1f5f9;
}

.nav-center-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  max-width: 600px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.arrow-icon {
  color: #64748b;
  transition: all 0.2s;
}

.nav-title-trigger:hover .arrow-icon {
  color: #0f172a;
}

.nav-right {
  display: flex;
  align-items: center;
}

.nav-close-btn {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
  height: 36px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.nav-close-btn:hover {
  background: #ffe4e6;
  color: #e11d48;
  border-color: #fecdd3;
}

/* Metadata Popover Styling */
.meta-popover-content {
  padding: 6px 4px;
}

.popover-title {
  font-size: 11px;
  font-weight: 750;
  color: #94a3b8;
  letter-spacing: 0.5px;
  margin: 0 0 12px 0;
}

.popover-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.popover-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
}

.popover-row .lbl {
  color: #64748b;
}

.popover-row .val {
  font-weight: 600;
  color: #1e293b;
}

.popover-divider {
  height: 1px;
  background: #e2e8f0;
  margin: 14px 0;
}

.text-success {
  color: #16a34a;
  font-weight: 700;
}

/* Zen Workspace Layout */
.zen-workspace {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* Immersive Lock screen */
.immersive-lock-view {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  padding: 40px;
}

.lock-panel {
  max-width: 440px;
  width: 100%;
  background: #ffffff;
  border: 1px solid #fecdd3;
  border-radius: 20px;
  padding: 36px;
  text-align: center;
  box-shadow: 0 20px 25px -5px rgba(225, 29, 72, 0.04);
}

.lock-icon-circle {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: #ffe4e6;
  color: #e11d48;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px auto;
}

.lock-panel h2 {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin: 0 0 8px 0;
}

.lock-msg {
  font-size: 13.5px;
  color: #64748b;
  line-height: 1.5;
  margin: 0 0 24px 0;
}

.lock-meta-table {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 28px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 12.5px;
}

.meta-row .lbl {
  color: #64748b;
}

.meta-row .val {
  font-weight: 650;
  color: #1e293b;
}

.text-red {
  color: #e11d48;
}

.text-blue {
  color: #2563eb;
}

.scale-btn {
  width: 100%;
  height: 42px !important;
  border-radius: 10px !important;
  font-weight: 600 !important;
}

/* Zen Reader Scroll Canvas */
.zen-paper-scroll {
  flex: 1;
  overflow-y: auto;
  background: #f1f5f9;
  padding: 32px 16px;
}

.zen-paper-sheet {
  max-width: 800px;
  width: 100%;
  margin: 0 auto;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 48px;
  box-shadow: 0 4px 6px -1px rgba(0,0,0,0.03), 0 2px 4px -1px rgba(0,0,0,0.02);
  min-height: 1130px; /* A4 aspect ratio height (800 * 1.414) */
  box-sizing: border-box;
}

/* Outline Sidebar */
.zen-outline-sidebar {
  width: 260px;
  border-left: 1px solid #cbd5e1;
  background: #ffffff;
  padding: 32px 20px;
  overflow-y: auto;
  flex-shrink: 0;
}

.outline-title {
  font-size: 11px;
  font-weight: 700;
  color: #94a3b8;
  letter-spacing: 0.5px;
  margin-bottom: 16px;
}

.outline-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.outline-list li {
  font-size: 12.5px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.outline-list li:hover {
  color: #0f172a;
}

.outline-l1 {
  font-weight: 650;
  padding-left: 0;
}

.outline-l2 {
  padding-left: 12px;
  font-size: 12px !important;
}

.outline-l3 {
  padding-left: 24px;
  font-size: 11.5px !important;
  color: #94a3b8 !important;
}

/* ── Markdown Rendering Typography ── */
.markdown-body {
  font-size: 15px;
  line-height: 1.75;
  color: #334155;
  word-wrap: break-word;
}

.markdown-body :deep(h1) {
  font-size: 24px;
  font-weight: 700;
  border-bottom: 1px solid #e2e8f0;
  padding-bottom: 10px;
  margin-top: 32px;
  margin-bottom: 18px;
  color: #0f172a;
}

.markdown-body :deep(h2) {
  font-size: 18px;
  font-weight: 650;
  margin-top: 28px;
  margin-bottom: 14px;
  color: #1e293b;
}

.markdown-body :deep(h3) {
  font-size: 15px;
  font-weight: 600;
  margin-top: 22px;
  margin-bottom: 10px;
  color: #334155;
}

.markdown-body :deep(p) {
  margin-top: 0;
  margin-bottom: 16px;
}

.markdown-body :deep(hr) {
  height: 1px;
  border: none;
  background-color: #cbd5e1;
  margin: 24px 0;
}

.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin-bottom: 16px;
}

.markdown-body :deep(li) {
  margin-bottom: 6px;
}

.markdown-body :deep(code) {
  font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
  background-color: #f1f5f9;
  color: #e11d48;
  padding: 2px 6px;
  border-radius: 4px;
}

.markdown-body :deep(pre) {
  background-color: #0f172a;
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin-bottom: 16px;
}

.markdown-body :deep(pre code) {
  background-color: transparent;
  color: #f1f5f9;
  padding: 0;
  font-size: 13px;
}

.markdown-body :deep(blockquote) {
  border-left: 4px solid #cbd5e1;
  padding-left: 16px;
  color: #475569;
  margin: 0 0 16px 0;
  font-style: italic;
}

.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 20px;
}

.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 10px 12px;
  text-align: left;
  font-size: 13.5px;
}

.markdown-body :deep(th) {
  background-color: #f8fafc;
  font-weight: 600;
  color: #1e293b;
}

/* ── Custom Dialog ── */
.custom-dialog :deep(.el-dialog) {
  border-radius: 16px;
  overflow: hidden;
}

.custom-dialog :deep(.el-dialog__header) {
  margin: 0;
  padding: 24px;
  border-bottom: 1px solid #f3f4f6;
}

.custom-dialog :deep(.el-dialog__title) {
  font-weight: 600;
  color: #111827;
}

.dialog-body {
  padding: 24px 24px 16px;
}

.dialog-desc {
  font-size: 13.5px;
  color: #4b5563;
  line-height: 1.5;
  margin: 0;
}

.request-doc-preview {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px 16px;
  margin: 14px 0;
}

.req-title {
  font-size: 14px;
  color: #111827;
  margin: 0 0 4px 0;
}

.req-meta {
  font-size: 12px;
  color: #9ca3af;
  margin: 0;
}

.custom-textarea :deep(.el-textarea__inner) {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: none !important;
  padding: 10px 14px;
}

.custom-textarea :deep(.el-textarea__inner:focus) {
  border-color: #111827;
  background: #fff;
}

.dialog-btn-cancel {
  background: #fff !important;
  border: 1px solid #e5e7eb !important;
  border-radius: 9px !important;
  color: #374151 !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 8px 16px !important;
  height: 38px;
}

.dialog-btn-cancel:hover {
  background: #f9fafb !important;
  border-color: #d1d5db !important;
}

.dialog-btn-confirm {
  background: #111827 !important;
  border: none !important;
  border-radius: 9px !important;
  color: #fff !important;
  font-size: 13px !important;
  font-weight: 500 !important;
  padding: 8px 16px !important;
  height: 38px;
}

.dialog-btn-confirm:hover {
  opacity: 0.9;
}

/* Animations */
.animate-fade-in {
  animation: fadeIn 0.35s ease;
}

.animate-slide-up {
  animation: slideUp 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.animate-slide-down {
  animation: slideDown 0.25s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: scale(0.97); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes slideDown {
  from { opacity: 0; max-height: 0; overflow: hidden; }
  to { opacity: 1; max-height: 200px; }
}

/* Card Management Actions Stylings */
.icon-action-btn {
  background: transparent !important;
  border: none !important;
  padding: 0 !important;
  width: 24px !important;
  height: 24px !important;
  min-width: auto !important;
  border-radius: 6px !important;
  color: #64748b !important;
  transition: all 0.15s !important;
  display: inline-flex !important;
  align-items: center;
  justify-content: center;
}

.icon-action-btn:hover {
  background: #f1f5f9 !important;
  color: #0f172a !important;
}

.icon-action-btn.delete:hover {
  background: #ffe4e6 !important;
  color: #e11d48 !important;
}

.icon-action-btn.rag-info {
  color: #94a3b8 !important;
}
.icon-action-btn.rag-info:hover {
  background: #e0f2fe !important;
  color: #0369a1 !important;
}
.icon-action-btn.rag-info.indexed {
  color: #15803d !important;
}
.icon-action-btn.rag-info.indexed:hover {
  background: #dcfce7 !important;
  color: #15803d !important;
}

/* RAG Info Dialog Summary */
.rag-info-summary {
  display: flex;
  gap: 40px;
  padding: 20px 24px;
  margin-bottom: 20px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
}

.rag-info-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rag-info-label {
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.rag-info-value {
  font-size: 16px;
  font-weight: 700;
  color: #111827;
}

.rag-info-value.text-success {
  color: #16a34a;
}

.rag-info-value.text-red {
  color: #dc2626;
}

.rag-info-value.text-warning {
  color: #b45309;
}

.chunk-loading,
.chunk-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 120px;
  color: #64748b;
  font-size: 13px;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 480px;
  overflow-y: auto;
}

.chunk-item {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.chunk-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

.chunk-vector {
  margin-bottom: 8px;
  color: #64748b;
  font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 11px;
  word-break: break-all;
}

.chunk-text {
  margin: 0;
  color: #1f2937;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.markdown-editor :deep(.el-textarea__inner) {
  font-family: SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13.5px;
  line-height: 1.5;
}

/* Create dialog layout */
.create-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 24px;
  min-height: 420px;
}

.create-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 20px;
  border-right: 1px solid #f1f5f9;
}

.create-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  letter-spacing: 0.3px;
}

/* Type selector cards */
.type-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}

.type-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  color: #94a3b8;
  text-align: center;
}
.type-card:hover { border-color: #c9cdd4; color: #64748b; }
.type-card.active {
  border-color: #111827;
  background: #111827;
  color: #fff;
}
.type-card .type-name { font-size: 11px; font-weight: 700; }
.type-card .type-desc { font-size: 9px; opacity: 0.6; display: none; }

.create-right {
  display: flex;
  flex-direction: column;
}

.content-editor :deep(.el-textarea__inner) {
  min-height: 380px !important;
  resize: none;
}

/* File drop zone */
.file-drop-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  min-height: 380px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
}
.file-drop-zone:hover {
  border-color: #111827;
  background: #f5f5f5;
}

.drop-icon { color: #c9cdd4; margin-bottom: 12px; }
.drop-text { font-size: 14px; font-weight: 600; color: #64748b; margin-bottom: 4px; }
.drop-hint { font-size: 11px; color: #c9cdd4; }

.file-preview-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.file-preview-icon { color: #4f46e5; }
.file-preview-name { font-size: 14px; font-weight: 600; color: #1e293b; }
.file-preview-size { font-size: 12px; color: #94a3b8; }
.file-change-btn { margin-top: 8px; }

.file-meta { font-size: 12px; color: #64748b; word-break: break-all; }
.file-meta-size { font-size: 11px; color: #94a3b8; }

.custom-input :deep(.el-input__wrapper) {
  border-radius: 10px;
  background: #f9fafb;
  box-shadow: none !important;
  border: 1px solid #e5e7eb;
  padding: 6px 12px;
}

.custom-input :deep(.el-input__wrapper.is-focus) {
  border-color: #111827;
  background: #fff;
}
</style>
