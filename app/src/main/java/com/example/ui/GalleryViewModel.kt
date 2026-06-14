package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class FilterMode {
    ALL,
    FOLDER,
    ZIP,
    CUSTOM_GROUP,
    TAG
}

class GalleryViewModel(private val repository: GalleryRepository) : ViewModel() {

    val folders: StateFlow<List<ScannedFolder>> = repository.foldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customGroups: StateFlow<List<CustomGroup>> = repository.customGroupsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _selectedZipPath = MutableStateFlow<String?>(null)
    val selectedZipPath: StateFlow<String?> = _selectedZipPath.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Full screen detail viewer state
    private val _activeMediaFile = MutableStateFlow<MediaFile?>(null)
    val activeMediaFile: StateFlow<MediaFile?> = _activeMediaFile.asStateFlow()

    // Reactive selection of files depending on active filters
    val mediaFiles: StateFlow<List<MediaFile>> = combine(
        repository.mediaFilesFlow,
        _filterMode,
        _selectedFolder,
        _selectedZipPath,
        combine(_selectedGroupId, _selectedTag) { g, t -> g to t }
    ) { allFiles, mode, folderPath, zipPath, extra ->
        val (groupId, tag) = extra
        when (mode) {
            FilterMode.ALL -> allFiles
            FilterMode.FOLDER -> {
                if (folderPath == null) emptyList()
                else allFiles.filter { it.parentFolder == folderPath }
            }
            FilterMode.ZIP -> {
                if (zipPath == null) emptyList()
                else allFiles.filter { it.zipFilePath == zipPath }
            }
            FilterMode.CUSTOM_GROUP -> {
                if (groupId == null) emptyList()
                else allFiles.filter { it.customGroupId == groupId }
            }
            FilterMode.TAG -> {
                if (tag == null) emptyList()
                else allFiles.filter { media ->
                    media.tags.split(",")
                        .map { it.trim() }
                        .contains(tag)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of distinct tag names computed reactively
    val allTags: StateFlow<List<String>> = repository.mediaFilesFlow
        .map { files ->
            files.flatMap { file ->
                file.tags.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of zip archives found in the media list, used for categorization listing in UI
    val zipArchives: StateFlow<List<String>> = repository.mediaFilesFlow
        .map { files -> files.mapNotNull { it.zipFilePath }.distinct() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun scanAll(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                // Read currently saved folders and scan them
                folders.value.forEach { folder ->
                    repository.scanFolder(context, folder.path)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun addFolder(path: String, context: Context) {
        viewModelScope.launch {
            repository.addFolder(path, context)
            // Auto switch to this folder view
            selectFolder(path)
        }
    }

    fun removeFolder(folder: ScannedFolder) {
        viewModelScope.launch {
            repository.removeFolder(folder)
            if (_selectedFolder.value == folder.path) {
                showAll()
            }
        }
    }

    fun createCustomGroup(name: String) {
        viewModelScope.launch {
            repository.addCustomGroup(name)
        }
    }

    fun deleteGroupId(group: CustomGroup) {
        viewModelScope.launch {
            repository.deleteCustomGroup(group)
            if (_selectedGroupId.value == group.id) {
                showAll()
            }
        }
    }

    fun assignFileToGroup(filePath: String, groupId: Long?) {
        viewModelScope.launch {
            repository.updateFileGroup(filePath, groupId)
        }
    }

    fun updateFileTags(filePath: String, tags: String) {
        viewModelScope.launch {
            repository.updateFileTags(filePath, tags)
            if (_activeMediaFile.value?.filePath == filePath) {
                _activeMediaFile.value = _activeMediaFile.value?.copy(tags = tags)
            }
        }
    }

    fun selectFolder(path: String) {
        _selectedFolder.value = path
        _filterMode.value = FilterMode.FOLDER
    }

    fun selectZip(path: String) {
        _selectedZipPath.value = path
        _filterMode.value = FilterMode.ZIP
    }

    fun selectCustomGroup(id: Long) {
        _selectedGroupId.value = id
        _filterMode.value = FilterMode.CUSTOM_GROUP
    }

    fun selectTag(tag: String) {
        _selectedTag.value = tag
        _filterMode.value = FilterMode.TAG
    }

    fun showAll() {
        _filterMode.value = FilterMode.ALL
        _selectedFolder.value = null
        _selectedZipPath.value = null
        _selectedGroupId.value = null
        _selectedTag.value = null
    }

    fun openMediaDetail(file: MediaFile?) {
        _activeMediaFile.value = file
    }
}
