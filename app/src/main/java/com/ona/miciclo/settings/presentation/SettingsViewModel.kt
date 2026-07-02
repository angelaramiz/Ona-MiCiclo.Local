package com.ona.miciclo.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ona.miciclo.auth.domain.repository.AuthRepository
import com.ona.miciclo.auth.domain.usecase.SignOutUseCase
import com.ona.miciclo.settings.domain.repository.ExportImportRepository
import com.ona.miciclo.settings.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.ona.miciclo.core.sync.SupabaseSyncManager
import com.ona.miciclo.data.local.dao.UserPreferencesDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportDataUseCase: ExportDataUseCase,
    private val exportImportRepository: ExportImportRepository,
    private val signOutUseCase: SignOutUseCase,
    private val authRepository: AuthRepository,
    private val updateManager: com.ona.miciclo.core.update.UpdateManager,
    private val userPreferencesDao: UserPreferencesDao,
    private val syncManager: SupabaseSyncManager,
    private val modelDownloader: com.ona.miciclo.ai.data.ModelDownloader,
    private val inferenceEngine: com.ona.miciclo.ai.domain.IInferenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var aiDownloadJob: kotlinx.coroutines.Job? = null

    private val userId: String
        get() = authRepository.currentUser.value?.uid ?: ""

    val userEmail: String
        get() = authRepository.currentUser.value?.email ?: ""

    val userPreferencesState = userPreferencesDao.observeByUserId(userId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        _uiState.update {
            it.copy(isAiModelDownloaded = modelDownloader.isModelDownloaded())
        }
    }

    fun downloadAiModel() {
        aiDownloadJob?.cancel()
        aiDownloadJob = viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingAi = true, aiDownloadError = null) }
            modelDownloader.downloadModel().collect { state ->
                when (state) {
                    is com.ona.miciclo.ai.data.DownloadState.Downloading -> {
                        _uiState.update { it.copy(aiDownloadProgress = state.progress) }
                    }
                    com.ona.miciclo.ai.data.DownloadState.Success -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingAi = false,
                                isAiModelDownloaded = true,
                                message = "Modelo de IA descargado e inicializado correctamente ✓"
                            )
                        }
                    }
                    is com.ona.miciclo.ai.data.DownloadState.Error -> {
                        _uiState.update {
                            it.copy(
                                isDownloadingAi = false,
                                aiDownloadError = state.error.message ?: "Error desconocido"
                            )
                        }
                    }
                    com.ona.miciclo.ai.data.DownloadState.Idle -> {
                        _uiState.update { it.copy(isDownloadingAi = false) }
                    }
                }
            }
        }
    }

    fun deleteAiModel() {
        viewModelScope.launch {
            val success = modelDownloader.deleteModel()
            if (success) {
                _uiState.update {
                    it.copy(
                        isAiModelDownloaded = false,
                        message = "Modelo de IA eliminado para liberar espacio."
                    )
                }
            } else {
                _uiState.update { it.copy(error = "No se pudo eliminar el modelo.") }
            }
        }
    }

    fun cancelAiDownload() {
        aiDownloadJob?.cancel()
        _uiState.update { it.copy(isDownloadingAi = false, aiDownloadProgress = 0f) }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingForUpdates = true, updateInfo = null, updateError = null) }
            when (val result = updateManager.checkForUpdates()) {
                is com.ona.miciclo.core.update.UpdateManager.UpdateResult.UpdateAvailable -> {
                    _uiState.update {
                        it.copy(isCheckingForUpdates = false, updateInfo = result.info)
                    }
                }
                com.ona.miciclo.core.update.UpdateManager.UpdateResult.UpToDate -> {
                    _uiState.update {
                        it.copy(isCheckingForUpdates = false, message = "La aplicación está actualizada a la última versión")
                    }
                }
                is com.ona.miciclo.core.update.UpdateManager.UpdateResult.Error -> {
                    _uiState.update {
                        it.copy(isCheckingForUpdates = false, updateError = "Error al buscar actualizaciones: ${result.error.localizedMessage}")
                    }
                }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val downloadUrl = _uiState.value.updateInfo?.downloadUrl ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingUpdate = true, downloadProgress = 0f, updateError = null) }
            when (val result = updateManager.downloadAndInstallUpdate(downloadUrl) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }) {
                com.ona.miciclo.core.update.UpdateManager.DownloadResult.Success -> {
                    _uiState.update { it.copy(isDownloadingUpdate = false) }
                }
                is com.ona.miciclo.core.update.UpdateManager.DownloadResult.Error -> {
                    _uiState.update {
                        it.copy(isDownloadingUpdate = false, updateError = "Error al descargar actualización: ${result.error.localizedMessage}")
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateInfo = null) }
    }

    fun exportData(masterPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            exportDataUseCase(userId, masterPassword)
                .onSuccess { data ->
                    _uiState.update {
                        it.copy(isExporting = false, exportedData = data, message = "Datos exportados correctamente")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isExporting = false, error = error.message) }
                }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            exportImportRepository.deleteAllData(userId)
                .onSuccess {
                    _uiState.update {
                        it.copy(isDeleting = false, message = "Todos los datos han sido eliminados")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isDeleting = false, error = error.message) }
                }
        }
    }

    fun generateInvitationCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingCode = true, error = null) }
            try {
                val code = syncManager.generateInvitationCode(userId)
                _uiState.update { it.copy(isGeneratingCode = false, invitationCode = code) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isGeneratingCode = false, error = "Error al generar código: ${e.localizedMessage}") }
            }
        }
    }

    fun linkPartner(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLinking = true, error = null) }
            try {
                val hostessId = syncManager.linkPartnerWithCode(userId, code)
                // Iniciar escucha del sync
                syncManager.startPartnerSyncListener(userId, hostessId)
                _uiState.update { it.copy(isLinking = false, message = "Vinculación con pareja exitosa") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLinking = false, error = "Error al vincular: ${e.localizedMessage}") }
            }
        }
    }

    fun clearInvitationCode() {
        _uiState.update { it.copy(invitationCode = null) }
    }

    fun reloadAndInitializeAiModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingAi = true, aiDownloadError = null) }
            try {
                inferenceEngine.unloadModel()
                val result = inferenceEngine.loadModel("")
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            isDownloadingAi = false,
                            isAiModelDownloaded = true,
                            message = "Modelo inicializado correctamente ✓"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isDownloadingAi = false,
                            aiDownloadError = "Error de inicialización: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDownloadingAi = false,
                        aiDownloadError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    fun repairAiModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingAi = true, aiDownloadError = null) }
            val deletedAny = modelDownloader.deleteCorruptFiles()
            val statusMessage = if (deletedAny) "Reparando y descargando archivos corruptos..." else "Verificando archivos de modelo..."
            _uiState.update { it.copy(message = statusMessage) }
            downloadAiModel()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
            _uiState.update { it.copy(isSignedOut = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearUpdateError() {
        _uiState.update { it.copy(updateError = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}

data class SettingsUiState(
    val isExporting: Boolean = false,
    val isDeleting: Boolean = false,
    val isSignedOut: Boolean = false,
    val exportedData: ByteArray? = null,
    val message: String? = null,
    val error: String? = null,
    val isCheckingForUpdates: Boolean = false,
    val updateInfo: com.ona.miciclo.core.update.UpdateManager.UpdateInfo? = null,
    val isDownloadingUpdate: Boolean = false,
    val downloadProgress: Float = 0f,
    val updateError: String? = null,
    val isGeneratingCode: Boolean = false,
    val invitationCode: String? = null,
    val isLinking: Boolean = false,
    val isAiModelDownloaded: Boolean = false,
    val isDownloadingAi: Boolean = false,
    val aiDownloadProgress: Float = 0f,
    val aiDownloadError: String? = null
)
