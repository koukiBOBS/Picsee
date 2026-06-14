package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.*
import com.example.ui.*
import com.example.ui.components.DirectoryChooserDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.util.RawImageDecoder
import com.example.util.SampleDataGenerator
import com.example.util.ZipCacheManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: GalleryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 1. Initialize DB and Repository
        val database = GalleryDatabase.getDatabase(this)
        val repository = GalleryRepository(database)

        // 2. Generate local high-fidelity mock files and auto-add to folder DAO
        val demoPath = SampleDataGenerator.generateIfNeeded(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val existingFolders = database.folderDao().getAllFoldersFlow().first()
                if (existingFolders.isEmpty()) {
                    repository.addFolder(demoPath, this@MainActivity)
                }
            }
        }

        // 3. Setup ViewModel with custom provider factory
        val factory = GalleryViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[GalleryViewModel::class.java]

        setContent {
            // Emphasize sleek slate & high contrast look
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GalleryAppScreen(viewModel = viewModel)
                }
            }
        }
    }
}

class GalleryViewModelFactory(private val repository: GalleryRepository) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Utility extension to resolve loadable File on runtime
fun MediaFile.getLoadableFile(context: android.content.Context): File {
    return if (this.zipFilePath != null) {
        val entryName = this.filePath.substringAfter("::")
        ZipCacheManager.getCachedFileForZipEntry(context, this.zipFilePath, entryName)
    } else if (this.isRaw) {
        RawImageDecoder.getCachedFileForRaw(context, this.filePath)
    } else {
        File(this.filePath)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryAppScreen(viewModel: GalleryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val mediaFiles by viewModel.mediaFiles.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val zipArchives by viewModel.zipArchives.collectAsState()
    val customGroups by viewModel.customGroups.collectAsState()
    val allTags by viewModel.allTags.collectAsState()

    val filterMode by viewModel.filterMode.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val selectedZipPath by viewModel.selectedZipPath.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()

    val isScanning by viewModel.isScanning.collectAsState()
    val activeMediaFile by viewModel.activeMediaFile.collectAsState()

    // Dialog & toggle States
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showCreateGroupNameDialog by remember { mutableStateOf(false) }
    var groupToAssignFile by remember { mutableStateOf<MediaFile?>(null) }
    var drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentTitle = when (filterMode) {
        FilterMode.ALL -> "全部图片"
        FilterMode.FOLDER -> "目录: ${selectedFolder?.substringAfterLast('/')}"
        FilterMode.ZIP -> "压缩包: ${selectedZipPath?.substringAfterLast('/')}"
        FilterMode.CUSTOM_GROUP -> "分组: ${customGroups.find { it.id == selectedGroupId }?.name ?: "分类"}"
        FilterMode.TAG -> "标签: $selectedTag"
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // App Logo Core Branding with Sophisticated Dark gradient
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFD0BCFF), Color(0xFF381E72))
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Logo",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Picsee",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE6E1E5),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                "Sophisticated Picsee Explorer",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF938F99)
                            )
                        }
                    }

                    Divider(color = Color(0xFF49454F), modifier = Modifier.padding(vertical = 12.dp))

                    // 1. All Photos Primary Item styled beautifully
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home", tint = if (filterMode == FilterMode.ALL) Color(0xFFD0BCFF) else Color(0xFFE6E1E5)) },
                        label = { Text("全部展示 (${mediaFiles.size})", fontWeight = FontWeight.Bold, color = if (filterMode == FilterMode.ALL) Color(0xFF381E72) else Color(0xFFE6E1E5)) },
                        selected = filterMode == FilterMode.ALL,
                        onClick = {
                            viewModel.showAll()
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = Color(0xFFD0BCFF),
                            unselectedContainerColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. Folder Scanning Header & Items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "文件扫描目录",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF938F99),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showAddFolderDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Folder", tint = Color(0xFFD0BCFF))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (folders.isEmpty()) {
                        Text(
                            "暂无指定目录，请点击 + 添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        folders.forEach { folder ->
                            val isSelected = filterMode == FilterMode.FOLDER && selectedFolder == folder.path
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF49454F) else Color.Transparent)
                                    .clickable {
                                        viewModel.selectFolder(folder.path)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.List, contentDescription = "Folder", tint = Color(0xFFD0BCFF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = folder.path.substringAfterLast('/'),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = folder.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF938F99),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (folder.path != context.filesDir.absolutePath + "/DemoGallery") {
                                    IconButton(
                                        onClick = { viewModel.removeFolder(folder) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. ZIP Archive Categorizations
                    Text(
                        "检测到的压缩包分类",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF938F99),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val zips = zipArchives
                    if (zips.isEmpty()) {
                        Text(
                            "未扫描到压缩包格式 (*.zip)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        zips.forEach { zipPath ->
                            val isSelected = filterMode == FilterMode.ZIP && selectedZipPath == zipPath
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF49454F) else Color.Transparent)
                                    .clickable {
                                        viewModel.selectZip(zipPath)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.List, contentDescription = "Zip", tint = Color(0xFFCAC4D0), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = zipPath.substringAfterLast('/'),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE6E1E5),
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Custom User Groups
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "自定义照片分组",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF938F99),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { showCreateGroupNameDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Group", tint = Color(0xFFD0BCFF))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (customGroups.isEmpty()) {
                        Text(
                            "暂无分组，点击 + 新建专属相册",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        customGroups.forEach { group ->
                            val isSelected = filterMode == FilterMode.CUSTOM_GROUP && selectedGroupId == group.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF49454F) else Color.Transparent)
                                    .clickable {
                                        viewModel.selectCustomGroup(group.id)
                                        scope.launch { drawerState.close() }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Group", tint = Color(0xFFD0BCFF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = group.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE6E1E5),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.deleteGroupId(group) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Group", tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "照片标签分类",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF938F99),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (allTags.isEmpty()) {
                        Text(
                            "暂无任何标签。可以在照片详情界面中为照片添加自定义标签。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99),
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            allTags.forEach { tag ->
                                val isSelected = filterMode == FilterMode.TAG && selectedTag == tag
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Color(0xFF49454F) else Color.Transparent)
                                        .clickable {
                                            viewModel.selectTag(tag)
                                            scope.launch { drawerState.close() }
                                        }
                                        .padding(vertical = 8.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FavoriteBorder,
                                        contentDescription = "Tag",
                                        tint = Color(0xFFD0BCFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFE6E1E5),
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu Drawer", tint = Color(0xFFE6E1E4))
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFE6E1E5),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${mediaFiles.size} ASSETS • Picsee Gallery",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF938F99),
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.scanAll(context) }) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFD0BCFF))
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Refresh Scan", tint = Color(0xFFE6E1E5))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background // Main workspace background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (mediaFiles.isEmpty()) {
                    // Styled empty display state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF2B2930), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Empty",
                                tint = Color(0xFF938F99),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "当前无显示照片",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFFE6E1E5),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "侧边栏中可以导入额外的文件夹。如果无显示，请点击上方刷新按钮重新索引。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF938F99),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                } else {
                    // Fully adaptive, responsive Material 3 layout for scrolling
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 110.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(mediaFiles) { index, item ->
                            MediaItemCard(
                                item = item,
                                onClick = { viewModel.openMediaDetail(item) },
                                onGroupClicked = { groupToAssignFile = item }
                            )
                        }
                    }
                }
            }
        }
    }

    // Interactive Dialogs & Popups
    if (showAddFolderDialog) {
        DirectoryChooserDialog(
            onDismiss = { showAddFolderDialog = false },
            onConfirm = { absolutePath ->
                viewModel.addFolder(absolutePath, context)
                showAddFolderDialog = false
                Toast.makeText(context, "已开始索引目录 $absolutePath", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showCreateGroupNameDialog) {
        var newGroupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupNameDialog = false },
            title = { Text("新建分组") },
            text = {
                Column {
                    Text("输入自定义分组的名称:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        singleLine = true,
                        placeholder = { Text("例如：我的最爱、工作档案") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.createCustomGroup(newGroupName.trim())
                            showCreateGroupNameDialog = false
                            Toast.makeText(context, "分组 ${newGroupName} 已创建", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("创建")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupNameDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (groupToAssignFile != null) {
        val activeFile = groupToAssignFile!!
        AlertDialog(
            onDismissRequest = { groupToAssignFile = null },
            title = {
                Text(
                    text = "更改照片分组",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE6E1E5)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (activeFile.zipFilePath != null) {
                        Text(
                            text = "注意: 此文件由于是压缩包内图档，仅作为压缩包分类展示。无法放入自定义分组。",
                            color = Color(0xFFEF5350),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = "选择要将照片加入的分组：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE6E1E5)
                        )

                        // Option to clear group association
                        Button(
                            onClick = {
                                viewModel.assignFileToGroup(activeFile.filePath, null)
                                groupToAssignFile = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF49454F))
                        ) {
                            Text("移出任何自定义分组", color = Color(0xFFE6E1E5))
                        }

                        Divider(color = Color(0xFF49454F))

                        if (customGroups.isEmpty()) {
                            Text(
                                "暂无任何自定义分组，请先在侧边栏创建相册分组。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF938F99)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = 250.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(customGroups) { group ->
                                    val isCurrentGroup = activeFile.customGroupId == group.id
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.assignFileToGroup(activeFile.filePath, group.id)
                                            groupToAssignFile = null
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        border = if (isCurrentGroup) BorderStroke(2.dp, Color(0xFFD0BCFF)) else ButtonDefaults.outlinedButtonBorder
                                    ) {
                                        Text(group.name, color = if (isCurrentGroup) Color(0xFFD0BCFF) else Color(0xFFE6E1E5))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { groupToAssignFile = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Immersive detail photo slide viewport Overlay
    if (activeMediaFile != null) {
        ImmersiveDetailsView(
            selectedFile = activeMediaFile!!,
            mediaList = mediaFiles,
            onClose = { viewModel.openMediaDetail(null) },
            onUpdateGroup = { file, gId -> viewModel.assignFileToGroup(file.filePath, gId) },
            onUpdateTags = { file, tags -> viewModel.updateFileTags(file.filePath, tags) },
            customGroups = customGroups
        )
    }
}

@Composable
fun MediaItemCard(
    item: MediaFile,
    onClick: () -> Unit,
    onGroupClicked: () -> Unit
) {
    val context = LocalContext.current
    val imageFile = remember(item) { item.getLoadableFile(context) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("media_item_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Coil async thumbnail
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageFile)
                    .crossfade(true)
                    .build(),
                contentDescription = item.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic format metadata banners
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.isRaw) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFD0BCFF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "RAW",
                            fontSize = 9.sp,
                            color = Color(0xFF381E72),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (item.zipFilePath != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF49454F), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ARCHIVE",
                            fontSize = 8.5.sp,
                            color = Color(0xFFCAC4D0),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            // Bottom Title & Categorized status strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE6E1E5),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sizeString = android.text.format.Formatter.formatShortFileSize(context, item.size)
                        Text(
                            text = sizeString,
                            fontSize = 8.6.sp,
                            color = Color(0xFF938F99)
                        )
                    }
                    if (item.zipFilePath == null) {
                        IconButton(
                            onClick = { onGroupClicked() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (item.customGroupId != null) Icons.Default.Star else Icons.Default.MoreVert,
                                contentDescription = "Grouping Action",
                                tint = if (item.customGroupId != null) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImmersiveDetailsView(
    selectedFile: MediaFile,
    mediaList: List<MediaFile>,
    onClose: () -> Unit,
    onUpdateGroup: (MediaFile, Long?) -> Unit,
    onUpdateTags: (MediaFile, String) -> Unit,
    customGroups: List<CustomGroup>
) {
    val context = LocalContext.current
    var currentIndex by remember(selectedFile, mediaList) {
        val index = mediaList.indexOfFirst { it.filePath == selectedFile.filePath }
        mutableStateOf(if (index >= 0) index else 0)
    }

    val activeItem = if (mediaList.isNotEmpty() && currentIndex in mediaList.indices) {
        mediaList[currentIndex]
    } else {
        selectedFile
    }

    val imageFile = remember(activeItem) { activeItem.getLoadableFile(context) }
    var showFullInfoDetails by remember { mutableStateOf(false) }

    // Gestures dragging switching state tracking
    var dragOffsetX by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(activeItem) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragOffsetX > 150f) {
                            // Swipe Right - Move to Previous
                            if (currentIndex > 0) {
                                currentIndex -= 1
                            }
                        } else if (dragOffsetX < -150f) {
                            // Swipe Left - Move to Next
                            if (currentIndex < mediaList.size -1) {
                                currentIndex += 1
                            }
                        }
                        dragOffsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount.x
                    }
                )
            }
    ) {
        // High fidelity single preview
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageFile)
                .build(),
            contentDescription = activeItem.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .padding(vertical = 48.dp)
        )

        // Top Control Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close View", tint = Color.White)
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
            ) {
                Text(
                    text = activeItem.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${currentIndex + 1} / ${mediaList.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }

            IconButton(
                onClick = { showFullInfoDetails = !showFullInfoDetails },
                modifier = Modifier.background(
                    if (showFullInfoDetails) Color(0xFF2DD4BF) else Color.Black.copy(alpha = 0.5f),
                    CircleShape
                )
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Metadata Info",
                    tint = if (showFullInfoDetails) Color.Black else Color.White
                )
            }
        }

        // Swipe & Navigation Overlay Arrows for rapid switching
        if (currentIndex > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { currentIndex -= 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Previous Photo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (currentIndex < mediaList.size -1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { currentIndex += 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Next Photo",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Metadata specs info bottom slide-up Drawer panel
        AnimatedVisibility(
            visible = showFullInfoDetails,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "详细文件信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6E1E5)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val formattedDate = remember(activeItem.dateModified) {
                        try {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                .format(Date(activeItem.dateModified))
                        } catch (e: Exception) {
                            "未知修改期"
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("物理文件路径：", style = MaterialTheme.typography.bodySmall, color = Color(0xFF938F99), modifier = Modifier.weight(1f))
                        Text(activeItem.filePath, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6E1E5), modifier = Modifier.weight(2f))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("文件格式：", style = MaterialTheme.typography.bodySmall, color = Color(0xFF938F99), modifier = Modifier.weight(1f))
                        Text(
                            text = if (activeItem.isRaw) "RAW Camera Raw Format" else "Standard Raster Image",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeItem.isRaw) Color(0xFFD0BCFF) else Color(0xFFCAC4D0),
                            modifier = Modifier.weight(2f)
                        )
                    }
                    if (activeItem.zipFilePath != null) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("来源于压缩包：", style = MaterialTheme.typography.bodySmall, color = Color(0xFF938F99), modifier = Modifier.weight(1f))
                            Text(activeItem.zipFilePath.substringAfterLast('/'), style = MaterialTheme.typography.bodySmall, color = Color(0xFFD0BCFF), modifier = Modifier.weight(2f))
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("修改日期：", style = MaterialTheme.typography.bodySmall, color = Color(0xFF938F99), modifier = Modifier.weight(1f))
                        Text(formattedDate, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6E1E5), modifier = Modifier.weight(2f))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("图档大小：", style = MaterialTheme.typography.bodySmall, color = Color(0xFF938F99), modifier = Modifier.weight(1f))
                        val byteString = android.text.format.Formatter.formatShortFileSize(context, activeItem.size)
                        Text(byteString, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6E1E5), modifier = Modifier.weight(2f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (activeItem.zipFilePath == null) {
                        Divider(color = Color(0xFF49454F))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "分配/更改此照片的自定义分组：",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (customGroups.isEmpty()) {
                            Text(
                                "暂无任何自定义分组，请关闭此界面到侧边栏创建分组后分配。",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF938F99)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Dynamic chips
                                FilterChip(
                                    selected = activeItem.customGroupId == null,
                                    onClick = { onUpdateGroup(activeItem, null) },
                                    label = { Text("不分配") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFD0BCFF),
                                        selectedLabelColor = Color(0xFF381E72),
                                        containerColor = Color(0xFF49454F),
                                        labelColor = Color(0xFFE6E1E5)
                                    )
                                )

                                customGroups.forEach { group ->
                                    FilterChip(
                                        selected = activeItem.customGroupId == group.id,
                                        onClick = { onUpdateGroup(activeItem, group.id) },
                                        label = { Text(group.name) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFFD0BCFF),
                                            selectedLabelColor = Color(0xFF381E72),
                                            containerColor = Color(0xFF49454F),
                                            labelColor = Color(0xFFE6E1E5)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF49454F))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "自定义标签（点击 x 移除）：",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val itemTagsList = remember(activeItem.tags) {
                        activeItem.tags.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    }

                    if (itemTagsList.isEmpty()) {
                        Text(
                            "暂无任何标签。在下方输入框添加标签。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF938F99)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemTagsList.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        val updatedTags = itemTagsList.filter { it != tag }.joinToString(",")
                                        onUpdateTags(activeItem, updatedTags)
                                    },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Remove Tag",
                                            modifier = Modifier.size(12.dp),
                                            tint = Color(0xFF381E72)
                                        )
                                    },
                                    colors = InputChipDefaults.inputChipColors(
                                        selectedContainerColor = Color(0xFFD0BCFF),
                                        selectedLabelColor = Color(0xFF381E72)
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    var newTagText by remember { mutableStateOf("") }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newTagText,
                            onValueChange = { newTagText = it },
                            placeholder = { Text("输入新标签", color = Color(0xFF938F99)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE6E1E5)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFD0BCFF),
                                unfocusedBorderColor = Color(0xFF49454F)
                            )
                        )
                        Button(
                            onClick = {
                                val trimmed = newTagText.trim()
                                if (trimmed.isNotEmpty() && !itemTagsList.contains(trimmed)) {
                                    val updatedTags = (itemTagsList + trimmed).joinToString(",")
                                    onUpdateTags(activeItem, updatedTags)
                                    newTagText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF49454F))
                        ) {
                            Text("添加", color = Color(0xFFE6E1E5))
                        }
                    }
                }
            }
        }
    }
}
