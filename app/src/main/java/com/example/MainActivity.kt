package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PassportPhotoMakerMainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PassportPhotoMakerMainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ----------------------------------------------------
    // User Configuration & Transform States
    // ----------------------------------------------------
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Processed Bitmap holding pixel color replacement (Magic Tap Filter)
    var finalBaseBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Transform states for viewport manipulation
    var scale by remember { mutableStateOf(1.0f) }
    var rotation by remember { mutableStateOf(0.0f) }
    var offsetX by remember { mutableStateOf(0.0f) }
    var offsetY by remember { mutableStateOf(0.0f) }

    // Adjustment sliders base values
    var brightness by remember { mutableStateOf(0.0f) } // -100 to 100
    var contrast by remember { mutableStateOf(1.0f) }   // 0.5 to 2.0
    var saturation by remember { mutableStateOf(1.0f) } // 0.0 to 2.0
    var sharpness by remember { mutableStateOf(0.0f) }  // 0.0 to 3.0

    // Custom background state
    var selectedBgPreset by remember { mutableStateOf(BackgroundPreset.WHITE) }
    var customBgColor by remember { mutableStateOf(Color.White) }
    var magicKeyColor by remember { mutableStateOf<Int?>(null) }
    var magicTolerance by remember { mutableStateOf(35) } // threshold out of 100
    var isMagicTapModeActive by remember { mutableStateOf(false) }

    // Border Settings
    var selectedBorderPreset by remember { mutableStateOf(BorderPreset.THIN_BLACK) }
    var customBorderColor by remember { mutableStateOf(Color.Black) }
    var customBorderWidth by remember { mutableStateOf(2) }

    // Layout configuration
    var currentTemplate by remember { mutableStateOf(PhotoTemplate.INDIAN_PASSPORT) }
    var copiesCount by remember { mutableStateOf(8) }
    var isHorizontalSheet by remember { mutableStateOf(true) }

    // Validation Status Engine
    var qualityCheckResult by remember { mutableStateOf<QualityCheckResult?>(null) }
    var isAnalyzingQuality by remember { mutableStateOf(false) }
    var isAutoAligning by remember { mutableStateOf(false) }

    // Active Control Tab index (0: Adjust, 1: Background, 2: Borders, 3: Layout Sheet)
    var activeTabIndex by remember { mutableIntStateOf(0) }

    // Modal Sheet Export success target dialog
    var exportSuccessDialogDetails by remember { mutableStateOf<ExportDialogDetails?>(null) }

    // File selection launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            coroutineScope.launch {
                isAnalyzingQuality = true
                val loaded = withContext(Dispatchers.IO) {
                    loadUriToBitmap(context, uri)
                }
                if (loaded != null) {
                    originalBitmap = loaded
                    finalBaseBitmap = loaded
                    // Reset editing states
                    scale = 1.0f
                    rotation = 0.0f
                    offsetX = 0.0f
                    offsetY = 0.0f
                    brightness = 0.0f
                    contrast = 1.0f
                    saturation = 1.0f
                    sharpness = 0.0f
                    magicKeyColor = null

                    // Auto face-detection and alert centering proposal
                    PhotoProcessor.performQualityCheck(loaded) { result ->
                        qualityCheckResult = result
                        isAnalyzingQuality = false
                    }
                } else {
                    isAnalyzingQuality = false
                    Toast.makeText(context, "Could not load selected image.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ----------------------------------------------------
    // Color Replacement & Adjustment Effects Trigger
    // ----------------------------------------------------
    // Whenever originalBitmap, magicKeyColor, or brightness changes, we re-process our image base!
    LaunchedEffect(originalBitmap, magicKeyColor, magicTolerance, selectedBgPreset, customBgColor, brightness, contrast, saturation, sharpness) {
        val src = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            var proc = src
            // 1. apply magic key replacement if key is selected
            val key = magicKeyColor
            if (key != null) {
                val repColor = if (selectedBgPreset == BackgroundPreset.CUSTOM) {
                    customBgColor.toArgb()
                } else {
                    (selectedBgPreset.color ?: Color.White).toArgb()
                }
                proc = PhotoProcessor.replaceColorTap(
                    sourceBitmap = src,
                    tappedX = 0, tappedY = 0, // coordinates ignored placeholder as key contains actual color now
                    replacementColor = repColor,
                    tolerance = magicTolerance
                )
            }
            // 2. apply visual image adjustments
            val refined = PhotoProcessor.applyEnhancements(
                sourceBitmap = proc,
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                sharpness = sharpness
            )
            withContext(Dispatchers.Main) {
                finalBaseBitmap = refined
            }
        }
    }

    // Recalculates quality checker rules when base image gets modified
    LaunchedEffect(finalBaseBitmap) {
        val base = finalBaseBitmap ?: return@LaunchedEffect
        isAnalyzingQuality = true
        PhotoProcessor.performQualityCheck(base) { result ->
            qualityCheckResult = result
            isAnalyzingQuality = false
        }
    }

    // ----------------------------------------------------
    // Layout Layout Content Builder (Responsive Grid/Columns)
    // ----------------------------------------------------
    Box(modifier = modifier.background(Color(0xFFF3F4F6))) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header
            HeaderBar(
                onLoadClick = { fileLauncher.launch("image/*") },
                onResetClick = {
                    scale = 1.0f
                    rotation = 0.0f
                    offsetX = 0.0f
                    offsetY = 0.0f
                    brightness = 0.0f
                    contrast = 1.0f
                    saturation = 1.0f
                    sharpness = 0.0f
                    magicKeyColor = null
                    selectedBgPreset = BackgroundPreset.WHITE
                    selectedBorderPreset = BorderPreset.THIN_BLACK
                    Toast.makeText(context, "All enhancements reset to neutral.", Toast.LENGTH_SHORT).show()
                }
            )

            // Split View: Main Workspace Viewport + Control Tab panel below
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.Center
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isTablet = maxWidth > 650.dp

                    if (isTablet) {
                        // Landscape / Tablet Split layout (side-by-side)
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                WorkspaceViewerPanel(
                                    finalBaseBitmap = finalBaseBitmap,
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    rotation = rotation,
                                    currentTemplate = currentTemplate,
                                    isMagicTapModeActive = isMagicTapModeActive,
                                    onTransformChange = { s, r, ox, oy ->
                                        scale = s
                                        rotation = r
                                        offsetX = ox
                                        offsetY = oy
                                    },
                                    onTapExtractColor = { color ->
                                        magicKeyColor = color
                                        isMagicTapModeActive = false
                                        Toast.makeText(context, "Background color selected for replacement!", Toast.LENGTH_SHORT).show()
                                    },
                                    onLoadImageRequest = { fileLauncher.launch("image/*") }
                                )

                                // Floating zoom slider (right side)
                                FloatingZoomSlider(
                                    scale = scale,
                                    onScaleChange = { scale = it },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 12.dp)
                                )
                            }

                            // Left tab panel for controls
                            Card(
                                modifier = Modifier
                                    .weight(0.9f)
                                    .fillMaxHeight(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                            ) {
                                ControlsPanel(
                                    activeTabIndex = activeTabIndex,
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    sharpness = sharpness,
                                    onBrightnessChange = { brightness = it },
                                    onContrastChange = { contrast = it },
                                    onSaturationChange = { saturation = it },
                                    onSharpnessChange = { sharpness = it },
                                    onRotateClick = { rotation = (rotation + 90f) % 360f },
                                    selectedBgPreset = selectedBgPreset,
                                    onBgPresetChange = { selectedBgPreset = it },
                                    customBgColor = customBgColor,
                                    onCustomBgColorChange = { customBgColor = it },
                                    magicKeyColor = magicKeyColor,
                                    onClearMagicKey = { magicKeyColor = null },
                                    magicTolerance = magicTolerance,
                                    onToleranceChange = { magicTolerance = it },
                                    isMagicTapActive = isMagicTapModeActive,
                                    onToggleMagicTap = { isMagicTapModeActive = it },
                                    selectedBorderPreset = selectedBorderPreset,
                                    onBorderPresetChange = { selectedBorderPreset = it },
                                    customBorderColor = customBorderColor,
                                    onCustomBorderColorChange = { customBorderColor = it },
                                    customBorderWidth = customBorderWidth,
                                    onCustomBorderWidthChange = { customBorderWidth = it },
                                    currentTemplate = currentTemplate,
                                    onTemplateChange = { currentTemplate = it },
                                    copiesCount = copiesCount,
                                    onCopiesCountChange = { copiesCount = it },
                                    isHorizontalSheet = isHorizontalSheet,
                                    onHorizontalSheetChange = { isHorizontalSheet = it },
                                    qualityCheckResult = qualityCheckResult,
                                    isAnalyzingQuality = isAnalyzingQuality,
                                    onAutoAlign = {
                                        val bmp = finalBaseBitmap
                                        if (bmp != null) {
                                            isAutoAligning = true
                                            PhotoProcessor.autoCenterAndAlign(bmp) { align ->
                                                if (align != null) {
                                                    scale = align.scale
                                                    offsetX = align.offsetX
                                                    offsetY = align.offsetY
                                                    rotation = align.rotation
                                                    Toast.makeText(context, align.msg, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "No clear outline found for auto align.", Toast.LENGTH_SHORT).show()
                                                }
                                                isAutoAligning = false
                                            }
                                        }
                                    },
                                    onGenerateSheet = {
                                        coroutineScope.launch {
                                            val source = finalBaseBitmap
                                            if (source == null) {
                                                Toast.makeText(context, "Please upload a photo first.", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            // Process single passport with precise borders
                                            val singlePassport = PhotoProcessor.generateSinglePassport(
                                                sourceBitmap = source,
                                                scale = scale,
                                                offsetX = offsetX,
                                                offsetY = offsetY,
                                                rotation = rotation,
                                                template = currentTemplate,
                                                borderPreset = selectedBorderPreset,
                                                customBorderColor = customBorderColor,
                                                customBorderSizeDp = customBorderWidth,
                                                solidBgColor = if (selectedBgPreset == BackgroundPreset.ORIGINAL) null else {
                                                    if (selectedBgPreset == BackgroundPreset.CUSTOM) customBgColor.toArgb() else (selectedBgPreset.color ?: Color.White).toArgb()
                                                }
                                            )
                                            // Generate printable composite card
                                            val layoutSheet = PhotoProcessor.generate4x6Sheet(
                                                singlePassport = singlePassport,
                                                copiesCount = copiesCount,
                                                horizontalOrientation = isHorizontalSheet
                                            )

                                            exportSuccessDialogDetails = ExportDialogDetails(
                                                singleBitmap = singlePassport,
                                                sheetBitmap = layoutSheet
                                            )
                                        }
                                    },
                                    onTabIndexChange = { activeTabIndex = it }
                                )
                            }
                        }
                    } else {
                        // Portrait Layout (vertical stacking)
                        Column(modifier = Modifier.fillMaxSize()) {
                            
                            // Workspace area (Top)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                WorkspaceViewerPanel(
                                    finalBaseBitmap = finalBaseBitmap,
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    rotation = rotation,
                                    currentTemplate = currentTemplate,
                                    isMagicTapModeActive = isMagicTapModeActive,
                                    onTransformChange = { s, r, ox, oy ->
                                        scale = s
                                        rotation = r
                                        offsetX = ox
                                        offsetY = oy
                                    },
                                    onTapExtractColor = { color ->
                                        magicKeyColor = color
                                        isMagicTapModeActive = false
                                        Toast.makeText(context, "Background color selected for replacement!", Toast.LENGTH_SHORT).show()
                                    },
                                    onLoadImageRequest = { fileLauncher.launch("image/*") }
                                )

                                // Floating zoom slider (right side)
                                FloatingZoomSlider(
                                    scale = scale,
                                    onScaleChange = { scale = it },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 12.dp)
                                )
                            }

                            // Controls panel (Bottom)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
                                    .shadow(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                            ) {
                                ControlsPanel(
                                    activeTabIndex = activeTabIndex,
                                    brightness = brightness,
                                    contrast = contrast,
                                    saturation = saturation,
                                    sharpness = sharpness,
                                    onBrightnessChange = { brightness = it },
                                    onContrastChange = { contrast = it },
                                    onSaturationChange = { saturation = it },
                                    onSharpnessChange = { sharpness = it },
                                    onRotateClick = { rotation = (rotation + 90f) % 360f },
                                    selectedBgPreset = selectedBgPreset,
                                    onBgPresetChange = { selectedBgPreset = it },
                                    customBgColor = customBgColor,
                                    onCustomBgColorChange = { customBgColor = it },
                                    magicKeyColor = magicKeyColor,
                                    onClearMagicKey = { magicKeyColor = null },
                                    magicTolerance = magicTolerance,
                                    onToleranceChange = { magicTolerance = it },
                                    isMagicTapActive = isMagicTapModeActive,
                                    onToggleMagicTap = { isMagicTapModeActive = it },
                                    selectedBorderPreset = selectedBorderPreset,
                                    onBorderPresetChange = { selectedBorderPreset = it },
                                    customBorderColor = customBorderColor,
                                    onCustomBorderColorChange = { customBorderColor = it },
                                    customBorderWidth = customBorderWidth,
                                    onCustomBorderWidthChange = { customBorderWidth = it },
                                    currentTemplate = currentTemplate,
                                    onTemplateChange = { currentTemplate = it },
                                    copiesCount = copiesCount,
                                    onCopiesCountChange = { copiesCount = it },
                                    isHorizontalSheet = isHorizontalSheet,
                                    onHorizontalSheetChange = { isHorizontalSheet = it },
                                    qualityCheckResult = qualityCheckResult,
                                    isAnalyzingQuality = isAnalyzingQuality,
                                    onAutoAlign = {
                                        val bmp = finalBaseBitmap
                                        if (bmp != null) {
                                            isAutoAligning = true
                                            PhotoProcessor.autoCenterAndAlign(bmp) { align ->
                                                if (align != null) {
                                                    scale = align.scale
                                                    offsetX = align.offsetX
                                                    offsetY = align.offsetY
                                                    rotation = align.rotation
                                                    Toast.makeText(context, align.msg, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "No clear outline found for auto align.", Toast.LENGTH_SHORT).show()
                                                }
                                                isAutoAligning = false
                                            }
                                        }
                                    },
                                    onGenerateSheet = {
                                        coroutineScope.launch {
                                            val source = finalBaseBitmap
                                            if (source == null) {
                                                Toast.makeText(context, "Please upload a photo first.", Toast.LENGTH_SHORT).show()
                                                return@launch
                                            }
                                            // Process single passport with precise borders
                                            val singlePassport = PhotoProcessor.generateSinglePassport(
                                                sourceBitmap = source,
                                                scale = scale,
                                                offsetX = offsetX,
                                                offsetY = offsetY,
                                                rotation = rotation,
                                                template = currentTemplate,
                                                borderPreset = selectedBorderPreset,
                                                customBorderColor = customBorderColor,
                                                customBorderSizeDp = customBorderWidth,
                                                solidBgColor = if (selectedBgPreset == BackgroundPreset.ORIGINAL) null else {
                                                    if (selectedBgPreset == BackgroundPreset.CUSTOM) customBgColor.toArgb() else (selectedBgPreset.color ?: Color.White).toArgb()
                                                }
                                            )
                                            // Generate printable composite card
                                            val layoutSheet = PhotoProcessor.generate4x6Sheet(
                                                singlePassport = singlePassport,
                                                copiesCount = copiesCount,
                                                horizontalOrientation = isHorizontalSheet
                                            )

                                            exportSuccessDialogDetails = ExportDialogDetails(
                                                singleBitmap = singlePassport,
                                                sheetBitmap = layoutSheet
                                            )
                                        }
                                    },
                                    onTabIndexChange = { activeTabIndex = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Output Preview Modal Dialog for export action
        exportSuccessDialogDetails?.let { details ->
            ExportLayoutSuccessDialog(
                details = details,
                onDismiss = { exportSuccessDialogDetails = null },
                onSaveToGallery = { isSheet, format, filename ->
                    val file = if (isSheet) {
                        PhotoProcessor.saveBitmapToGallery(context, details.sheetBitmap, filename, format)
                    } else {
                        PhotoProcessor.saveBitmapToGallery(context, details.singleBitmap, filename, format)
                    }
                    if (file != null) {
                        Toast.makeText(context, "Saved successfully! Location: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to save file.", Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveAsPdf = { filename ->
                    val file = PhotoProcessor.savePrintSheetToPdf(details.sheetBitmap, context, filename)
                    if (file != null) {
                        Toast.makeText(context, "Print PDF Saved successfully! Location: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Could not write PDF document.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

// ----------------------------------------------------
// Core Composable Screen Blocks
// ----------------------------------------------------

@Composable
fun HeaderBar(onLoadClick: () -> Unit, onResetClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        color = Color.White,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Title block
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF005A9C), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(16.dp.toPx(), 16.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
                Column {
                    Text(
                        text = "PhotoGen India",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "PASSPORT STANDARD V2.4",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF005A9C),
                        letterSpacing = 1.2.sp
                    )
                }
            }

            // Quick actions
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onResetClick,
                    modifier = Modifier.background(Color(0xFFF1F5F9), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset adjustments",
                        tint = Color(0xFF64748B)
                    )
                }

                Button(
                    onClick = onLoadClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005A9C)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("upload_main_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoAlbum,
                        contentDescription = "Upload",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun WorkspaceViewerPanel(
    finalBaseBitmap: Bitmap?,
    scale: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    currentTemplate: PhotoTemplate,
    isMagicTapModeActive: Boolean,
    onTransformChange: (scale: Float, rotation: Float, offsetX: Float, offsetY: Float) -> Unit,
    onTapExtractColor: (colorInt: Int) -> Unit,
    onLoadImageRequest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)) // high contrast workspace gray
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (finalBaseBitmap == null) {
            // Upload initial empty layout state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onLoadImageRequest() }
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.White.copy(alpha = 0.5f), CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoAlbum,
                        contentDescription = "Select photography asset",
                        modifier = Modifier.size(54.dp),
                        tint = Color(0xFF005A9C)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tap to load your portrait photo",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1E293B)
                )
                Text(
                    text = "Supports JPG, PNG, WEBP. Local Canvas processor ensures 100% privacy.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                )
            }
        } else {
            // Interactive transformation card workspace
            // Passport standard aspects ratios: 35x45mm matches approx ratio 7:9.
            val ratio = 35f / 45f
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .height(260.dp / ratio)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
                    .border(1.dp, Color.White)
                    .shadow(12.dp)
                    .pointerInput(isMagicTapModeActive) {
                        if (isMagicTapModeActive) {
                            detectTapGestures { offset ->
                                // Grab pixel color under tap by evaluating canvas pixel coordinate relative to current transformed bitmap
                                val px = (offset.x / size.width) * finalBaseBitmap.width
                                val py = (offset.y / size.height) * finalBaseBitmap.height

                                val safeX = px.toInt().coerceIn(0, finalBaseBitmap.width - 1)
                                val safeY = py.toInt().coerceIn(0, finalBaseBitmap.height - 1)

                                val pickedColor = finalBaseBitmap.getPixel(safeX, safeY)
                                onTapExtractColor(pickedColor)
                            }
                        } else {
                            detectTransformGestures { _, pan, zoom, gestureRotation ->
                                val newScale = (scale * zoom).coerceIn(0.4f, 6.0f)
                                val newRotation = (rotation + gestureRotation) % 360f
                                onTransformChange(
                                    newScale,
                                    newRotation,
                                    offsetX + pan.x,
                                    offsetY + pan.y
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background layer: original transformed portrait
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    withTransform({
                        translate(canvasWidth / 2f + offsetX, canvasHeight / 2f + offsetY)
                        rotate(rotation)
                        scale(scale, scale)
                    }) {
                        val bmpW = finalBaseBitmap.width.toFloat()
                        val bmpH = finalBaseBitmap.height.toFloat()

                        drawImage(
                            image = finalBaseBitmap.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (-bmpW / 2).toInt(),
                                (-bmpH / 2).toInt()
                            )
                        )
                    }
                }

                // Passport Standard Viewport Overlay overlay (Crop bounds indicator)
                PassportTemplateGuidesOverlay(template = currentTemplate)

                // Indication Badge if Magic tap color selector is active
                if (isMagicTapModeActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Tap on background to replace color",
                            fontSize = 9.sp,
                            color = Color.Yellow,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PassportTemplateGuidesOverlay(template: PhotoTemplate) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Outer margin / standard crop guide rect (dotted cyan border)
        val insetX = w * 0.05f
        val insetY = h * 0.05f

        val pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        // Draw dotted guidelines showing the passport safe center
        drawRect(
            color = Color(0xFF00F0FF),
            topLeft = Offset(insetX, insetY),
            size = androidx.compose.ui.geometry.Size(w - (insetX * 2), h - (insetY * 2)),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), pathEffect = pathEffect)
        )

        // Draw abstract centered human head guide line matching Indian Passport standards (75% height)
        val headWidth = w * 0.40f
        val headHeight = h * 0.48f
        val headLeft = (w - headWidth) / 2f
        val headTop = h * 0.22f

        // Head oval standard alignment indicator
        drawOval(
            color = Color(0x6000F0FF),
            topLeft = Offset(headLeft, headTop),
            size = androidx.compose.ui.geometry.Size(headWidth, headHeight),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = pathEffect)
        )

        // Shoulders base guideline
        drawLine(
            color = Color(0x6000F0FF),
            start = Offset(w * 0.22f, h * 0.72f),
            end = Offset(w * 0.78f, h * 0.72f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = pathEffect
        )

        // Guidelines are drawn
    }
}

@Composable
fun FloatingZoomSlider(
    scale: Float,
    onScaleChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(52.dp)
            .wrapContentHeight(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = { onScaleChange((scale + 0.2f).coerceIn(0.4f, 6.0f)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = Color(0xFF1E293B)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Vertical scale slider representation
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(130.dp)
                    .width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                // Rotate standard Compose horizontal Slider into vertical layout using Canvas or Modifier rotate
                Slider(
                    value = scale,
                    onValueChange = onScaleChange,
                    valueRange = 0.4f..6.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF005A9C),
                        activeTrackColor = Color(0xFF005A9C),
                        inactiveTrackColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .graphicsLayer {
                            rotationZ = -90f
                        }
                        .width(130.dp) // length becomes height due to -90 turn
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            IconButton(
                onClick = { onScaleChange((scale - 0.2f).coerceIn(0.4f, 6.0f)) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = Color(0xFF1E293B)
                )
            }
        }
    }
}

@Composable
fun ControlsPanel(
    activeTabIndex: Int,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sharpness: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onSharpnessChange: (Float) -> Unit,
    onRotateClick: () -> Unit,
    selectedBgPreset: BackgroundPreset,
    onBgPresetChange: (BackgroundPreset) -> Unit,
    customBgColor: Color,
    onCustomBgColorChange: (Color) -> Unit,
    magicKeyColor: Int?,
    onClearMagicKey: () -> Unit,
    magicTolerance: Int,
    onToleranceChange: (Int) -> Unit,
    isMagicTapActive: Boolean,
    onToggleMagicTap: (Boolean) -> Unit,
    selectedBorderPreset: BorderPreset,
    onBorderPresetChange: (BorderPreset) -> Unit,
    customBorderColor: Color,
    onCustomBorderColorChange: (Color) -> Unit,
    customBorderWidth: Int,
    onCustomBorderWidthChange: (Int) -> Unit,
    currentTemplate: PhotoTemplate,
    onTemplateChange: (PhotoTemplate) -> Unit,
    copiesCount: Int,
    onCopiesCountChange: (Int) -> Unit,
    isHorizontalSheet: Boolean,
    onHorizontalSheetChange: (Boolean) -> Unit,
    qualityCheckResult: QualityCheckResult?,
    isAnalyzingQuality: Boolean,
    onAutoAlign: () -> Unit,
    onGenerateSheet: () -> Unit,
    onTabIndexChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Selection Tab Header
        TabRow(
            selectedTabIndex = activeTabIndex,
            containerColor = Color.White,
            contentColor = Color(0xFF005A9C),
            modifier = Modifier.fillMaxWidth()
        ) {
            val tabs = listOf("Adjust", "Background", "Borders", "Layout Sheet")
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = activeTabIndex == i,
                    onClick = { onTabIndexChange(i) },
                    text = {
                        Text(
                            text = title,
                            fontSize = 12.sp,
                            fontWeight = if (activeTabIndex == i) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        // Tab Content Area scrolling list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            when (activeTabIndex) {
                0 -> {
                    // Adjust parameters panel: zoom / manual coordinates brightness saturation
                    AdjustTabControls(
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        sharpness = sharpness,
                        onBrightnessChange = onBrightnessChange,
                        onContrastChange = onContrastChange,
                        onSaturationChange = onSaturationChange,
                        onSharpnessChange = onSharpnessChange,
                        onRotateClick = onRotateClick,
                        onAutoAlign = onAutoAlign,
                        qualityCheckResult = qualityCheckResult,
                        isAnalyzingQuality = isAnalyzingQuality
                    )
                }

                1 -> {
                    // Background Color Picker & intelligent Chroma Keyer tool
                    BackgroundTabControls(
                        selectedBgPreset = selectedBgPreset,
                        onBgPresetChange = onBgPresetChange,
                        customBgColor = customBgColor,
                        onCustomBgColorChange = onCustomBgColorChange,
                        magicKeyColor = magicKeyColor,
                        onClearMagicKey = onClearMagicKey,
                        magicTolerance = magicTolerance,
                        onToleranceChange = onToleranceChange,
                        isMagicTapActive = isMagicTapActive,
                        onToggleMagicTap = onToggleMagicTap
                    )
                }

                2 -> {
                    // Borders configurations panel
                    BordersTabControls(
                        selectedBorderPreset = selectedBorderPreset,
                        onBorderPresetChange = onBorderPresetChange,
                        customBorderColor = customBorderColor,
                        onCustomBorderColorChange = onCustomBorderColorChange,
                        customBorderWidth = customBorderWidth,
                        onCustomBorderWidthChange = onCustomBorderWidthChange
                    )
                }

                3 -> {
                    // Template sizes & layout settings
                    LayoutSheetTabControls(
                        currentTemplate = currentTemplate,
                        onTemplateChange = onTemplateChange,
                        copiesCount = copiesCount,
                        onCopiesCountChange = onCopiesCountChange,
                        isHorizontalSheet = isHorizontalSheet,
                        onHorizontalSheetChange = onHorizontalSheetChange
                    )
                }
            }
        }

        // Generate printer composite actions bar (Matches the Geometric Balance Action Bar)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            color = Color.White,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onGenerateSheet,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("generate_sheet_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005A9C)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Print,
                            contentDescription = "Print layout",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generate 4x6 Sheet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Status verification badge footer
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Ready to Export • 300 DPI Ultra High Definition",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// Tab Subcomponent Controllers
// ----------------------------------------------------

@Composable
fun AdjustTabControls(
    brightness: Float,
    contrast: Float,
    saturation: Float,
    sharpness: Float,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onSharpnessChange: (Float) -> Unit,
    onRotateClick: () -> Unit,
    onAutoAlign: () -> Unit,
    qualityCheckResult: QualityCheckResult?,
    isAnalyzingQuality: Boolean
) {
    Column {
        // Quick Auto alignment & rotation guides row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onAutoAlign,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF005A9C))
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Auto Align face",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Auto Face Align", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onRotateClick,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155))
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate 90 deg",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Rotate 90°", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Image Quality analysis alert block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (qualityCheckResult?.faceDetected == true) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (qualityCheckResult?.faceDetected == true) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Quality status",
                    tint = if (qualityCheckResult?.faceDetected == true) Color(0xFF16A34A) else Color(0xFFD97706),
                    modifier = Modifier.size(24.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAnalyzingQuality) "Analyzing Photo..." else {
                            if (qualityCheckResult?.faceDetected == true) "Format Checklist Passed" else "Quality Warnings Discovered"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    if (isAnalyzingQuality) {
                        Text("Reading local biometric boundaries...", fontSize = 11.sp, color = Color(0xFF64748B))
                    } else {
                        val warnings = qualityCheckResult?.warnings ?: emptyList()
                        if (warnings.isEmpty()) {
                            Text(
                                text = "✓ Resolution is good (${qualityCheckResult?.currentResolutionString})\n✓ Head is centered and complies with 35x45mm scale.",
                                fontSize = 11.sp,
                                color = Color(0xFF16A34A)
                            )
                        } else {
                            warnings.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Enhancement Sliders List
        Text(
            text = "PHOTO ENHANCEMENTS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Brightness Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Brightness", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Text("${brightness.toInt()}%", fontSize = 12.sp, color = Color(0xFF005A9C), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = -80f..80f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF005A9C), activeTrackColor = Color(0xFF005A9C))
            )
        }

        // Contrast Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Contrast", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Text(String.format("%.1fx", contrast), fontSize = 12.sp, color = Color(0xFF005A9C), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = contrast,
                onValueChange = onContrastChange,
                valueRange = 0.5f..1.8f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF005A9C), activeTrackColor = Color(0xFF005A9C))
            )
        }

        // Saturation Slider
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Saturation", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                Text(String.format("%.1fx", saturation), fontSize = 12.sp, color = Color(0xFF005A9C), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = saturation,
                onValueChange = onSaturationChange,
                valueRange = 0.0f..2.0f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF005A9C), activeTrackColor = Color(0xFF005A9C))
            )
        }

        // Sharpness Slider (Ultra HD Filter)
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Sharpness (Ultra HD)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF1E293B))
                }
                Text(String.format("%.1f", sharpness), fontSize = 12.sp, color = Color(0xFF005A9C), fontWeight = FontWeight.Bold)
            }
            Slider(
                value = sharpness,
                onValueChange = onSharpnessChange,
                valueRange = 0.0f..3.0f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF005A9C), activeTrackColor = Color(0xFF005A9C))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundTabControls(
    selectedBgPreset: BackgroundPreset,
    onBgPresetChange: (BackgroundPreset) -> Unit,
    customBgColor: Color,
    onCustomBgColorChange: (Color) -> Unit,
    magicKeyColor: Int?,
    onClearMagicKey: () -> Unit,
    magicTolerance: Int,
    onToleranceChange: (Int) -> Unit,
    isMagicTapActive: Boolean,
    onToggleMagicTap: (Boolean) -> Unit
) {
    Column {
        Text(
            text = "CHOOSE BACKGROUND STYLE",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Preset color row
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BackgroundPreset.values().forEach { preset ->
                val isSelected = selectedBgPreset == preset
                val chipColor = if (isSelected) Color(0xFF005A9C) else Color(0xFFF1F5F9)
                val textCol = if (isSelected) Color.White else Color(0xFF1E293B)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipColor)
                        .clickable { onBgPresetChange(preset) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Sample dot
                        if (preset.color != null) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(preset.color, CircleShape)
                                    .border(1.dp, Color.LightGray, CircleShape)
                            )
                        } else if (preset == BackgroundPreset.CUSTOM) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(customBgColor, CircleShape)
                                    .border(1.dp, Color.LightGray, CircleShape)
                            )
                        }
                        Text(
                            text = preset.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                    }
                }
            }
        }

        // Custom Color Picker slider shown if preset is custom
        if (selectedBgPreset == BackgroundPreset.CUSTOM) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Adjust Custom Color Hues:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(6.dp))

            // Fast gradient color slider
            var hueValue by remember { mutableStateOf(180f) } // 0 to 360
            LaunchedEffect(hueValue) {
                // Approximate RGB transformation from hue
                onCustomBgColorChange(Color.hsv(hueValue, 0.45f, 0.98f))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                // Hue base background canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (x in 0 until size.width.toInt() step 4) {
                        val frac = x / size.width
                        val c = Color.hsv(frac * 360f, 0.45f, 0.95f)
                        drawRect(
                            color = c,
                            topLeft = Offset(x.toFloat(), 0f),
                            size = androidx.compose.ui.geometry.Size(4f, size.height)
                        )
                    }
                }

                // Slider over preview background
                Slider(
                    value = hueValue,
                    onValueChange = { hueValue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Intelligent Tap Background Remover Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Magic Color Replacement",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            text = "Tap any background pixel to wipe / replace it.",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Switch(
                        checked = isMagicTapActive,
                        onCheckedChange = onToggleMagicTap,
                        modifier = Modifier.testTag("magic_toggle")
                    )
                }

                if (magicKeyColor != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Active Target: ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(magicKeyColor), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(6.dp))
                            )
                        }

                        IconButton(
                            onClick = onClearMagicKey,
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(0xFFFEE2E2), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear extracted color",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Tolerance slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Area Tolerance Limit", fontSize = 11.sp, color = Color(0xFF334155))
                        Text("$magicTolerance%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF005A9C))
                    }
                    Slider(
                        value = magicTolerance.toFloat(),
                        onValueChange = { onToleranceChange(it.toInt()) },
                        valueRange = 10f..85f,
                        colors = SliderDefaults.colors(thumbColor = Color(0xFF005A9C), activeTrackColor = Color(0xFF005A9C))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BordersTabControls(
    selectedBorderPreset: BorderPreset,
    onBorderPresetChange: (BorderPreset) -> Unit,
    customBorderColor: Color,
    onCustomBorderColorChange: (Color) -> Unit,
    customBorderWidth: Int,
    onCustomBorderWidthChange: (Int) -> Unit
) {
    Column {
        Text(
            text = "PASSPORT BORDER PRESETS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Preset grid color options
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BorderPreset.values().forEach { preset ->
                val isSelected = selectedBorderPreset == preset
                val chipColor = if (isSelected) Color(0xFF005A9C) else Color(0xFFF1F5F9)
                val textCol = if (isSelected) Color.White else Color(0xFF1E293B)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipColor)
                        .clickable { onBorderPresetChange(preset) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = preset.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                }
            }
        }

        // Custom details if preset Custom is active
        if (selectedBorderPreset == BorderPreset.CUSTOM) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("CUSTOM BORDER PARAMETERS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(12.dp))

            // Thickness custom slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Border Size", fontSize = 12.sp, color = Color(0xFF334155))
                Text("$customBorderWidth px", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF005A9C))
            }
            Slider(
                value = customBorderWidth.toFloat(),
                onValueChange = { onCustomBorderWidthChange(it.toInt()) },
                valueRange = 0f..10f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFF005A9C), activeTrackColor = Color(0xFF005A9C))
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Border color palettes
            Text("Border Ink Color", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
            Spacer(modifier = Modifier.height(8.dp))

            val borderColors = listOf(Color.Black, Color.DarkGray, Color.Gray, Color(0xFF005A9C), Color(0xFF1E3A8A), Color(0xFF0F172A))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                borderColors.forEach { color ->
                    val isChecked = customBorderColor == color
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(color, RoundedCornerShape(10.dp))
                            .border(
                                width = if (isChecked) 3.dp else 1.dp,
                                color = if (isChecked) Color.Yellow else Color.LightGray,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onCustomBorderColorChange(color) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LayoutSheetTabControls(
    currentTemplate: PhotoTemplate,
    onTemplateChange: (PhotoTemplate) -> Unit,
    copiesCount: Int,
    onCopiesCountChange: (Int) -> Unit,
    isHorizontalSheet: Boolean,
    onHorizontalSheetChange: (Boolean) -> Unit
) {
    Column {
        Text(
            text = "PASSPORT TEMPLATE SPECIFICATION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Radio buttons for dimensions standards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PhotoTemplate.values().forEach { template ->
                val isSelected = currentTemplate == template
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTemplateChange(template) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.White, CircleShape)
                                .border(
                                    width = if (isSelected) 6.dp else 1.5.dp,
                                    color = if (isSelected) Color(0xFF005A9C) else Color.Gray,
                                    shape = CircleShape
                                )
                        )

                        Column {
                            Text(
                                text = template.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Required dimension standard for all physical submissions.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Grid copy quantity switcher row
        Text(
            text = "PRINTSHEET COPIES LIMIT COUNT",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        val copyPresets = listOf(8, 10, 12, 16)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            copyPresets.forEach { count ->
                val isSelected = copiesCount == count
                val chipColor = if (isSelected) Color(0xFF005A9C) else Color(0xFFF1F5F9)
                val textCol = if (isSelected) Color.White else Color(0xFF1E293B)

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(chipColor)
                        .clickable { onCopiesCountChange(count) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$count Copies",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textCol
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Printable card orientation landscape/portrait toggling standard
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Horizontal / Landscape Sheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Standard 6\" x 4\" photo layout", fontSize = 10.sp, color = Color(0xFF64748B))
            }

            Switch(
                checked = isHorizontalSheet,
                onCheckedChange = onHorizontalSheetChange,
                modifier = Modifier.testTag("orientation_switch")
            )
        }
    }
}

// ----------------------------------------------------
// Share / Download Output Dialog
// ----------------------------------------------------

data class ExportDialogDetails(
    val singleBitmap: Bitmap,
    val sheetBitmap: Bitmap
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportLayoutSuccessDialog(
    details: ExportDialogDetails,
    onDismiss: () -> Unit,
    onSaveToGallery: (isSheet: Boolean, format: Bitmap.CompressFormat, filename: String) -> Unit,
    onSaveAsPdf: (filename: String) -> Unit
) {
    var selectedFormat by remember { mutableStateOf("PNG") } // "PNG", "PDF", "JPEG"
    var customFilename by remember { mutableStateOf("Passport_PrintSheet_" + (System.currentTimeMillis() % 100000)) }
    var isFullSheet by remember { mutableStateOf(true) } // true = full 4x6 print sheet, false = single photo

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header badge icon with format-aware accenting
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = when (selectedFormat) {
                                "PDF" -> Color(0xFFFEE2E2) // Red for PDF
                                "PNG" -> Color(0xFFE0F2FE) // Light blue for PNG
                                else -> Color(0xFFEFF6FF)  // Blue for JPEG
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (selectedFormat) {
                            "PDF" -> Icons.Default.Print
                            else -> Icons.Default.Download
                        },
                        contentDescription = "Format-specific download icon",
                        tint = when (selectedFormat) {
                            "PDF" -> Color(0xFFEF4444) // Red icon
                            "PNG" -> Color(0xFF0284C7) // Sky blue icon
                            else -> Color(0xFF005A9C)  // Royal blue icon
                        },
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Download Sheet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )

                Text(
                    text = "Render and export verified Indian passport-size photos with absolute pixel correctness.",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // FORMAT SELECTION SEGMENTED TABS
                Text(
                    text = "SELECT EXPORT FORMAT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.Start),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("PNG", "PDF", "JPEG").forEach { format ->
                        val isSelected = selectedFormat == format
                        val isPdf = format == "PDF"
                        
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedFormat = format
                                    if (isPdf) {
                                        isFullSheet = true // PDF standard is always full sheet
                                    }
                                }
                                .testTag("format_tab_$format"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    if (isPdf) Color(0xFFFEF2F2) else Color(0xFFF0F9FF)
                                } else Color(0xFFF8FAFC)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) {
                                    if (isPdf) Color(0xFFEF4444) else Color(0xFF005A9C)
                                } else Color(0xFFE2E8F0)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = format,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) {
                                        if (isPdf) Color(0xFFB91C1C) else Color(0xFF005A9C)
                                    } else Color(0xFF475569)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (format) {
                                        "PNG" -> "Lossless PNG"
                                        "PDF" -> "Direct PDF"
                                        else -> "Optimized JPG"
                                    },
                                    fontSize = 8.sp,
                                    color = Color(0xFF94A3B8),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // FILENAME TEXT FIELD INPUT WITH HELPER
                OutlinedTextField(
                    value = customFilename,
                    onValueChange = { customFilename = it },
                    label = { Text("Filename", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    placeholder = { Text("e.g. Rahul_Passport_Card") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("filename_input"),
                    trailingIcon = {
                        if (customFilename.isNotEmpty()) {
                            IconButton(onClick = { customFilename = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear input",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // TARGET SPECIFICATION (Only for PNG and JPEG)
                if (selectedFormat != "PDF") {
                    Text(
                        text = "SELECT EXPORT BOUND RANGE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.Start),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Full Sheet option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isFullSheet = true }
                                .testTag("target_sheet_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isFullSheet) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isFullSheet) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = isFullSheet,
                                    onClick = { isFullSheet = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF005A9C))
                                )
                                Column {
                                    Text(
                                        text = "Full Print Sheet (4 x 6\")",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "Composite card layout containing multiple alignment-guided copies.",
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }

                        // Single photo option
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isFullSheet = false }
                                .testTag("target_single_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (!isFullSheet) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (!isFullSheet) Color(0xFF3B82F6) else Color(0xFFE2E8F0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = !isFullSheet,
                                    onClick = { isFullSheet = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF005A9C))
                                )
                                Column {
                                    Text(
                                        text = "Single Passport Photo Only",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "Download a single studio photo cropped matching your passport specifications.",
                                        fontSize = 9.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // PDF info chip notice
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "PDF Tip",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PDF scale is locked directly to precise 4 x 6\" physically printed measurements, perfectly preserving layout dimensions for print studios.",
                                fontSize = 9.5.sp,
                                color = Color(0xFFB45309),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // HIGH RESOLUTION CANVAS PREVIEW RENDERING WINDOW
                Text(
                    text = "LIVE EXPORT PREVIEW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.align(Alignment.Start),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E8F0))
                        .background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewBitmap = if (isFullSheet) details.sheetBitmap else details.singleBitmap
                    val isBmpHorizontal = previewBitmap.width > previewBitmap.height
                    
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.85f)
                            .width(if (isBmpHorizontal) 150.dp else 90.dp)
                            .height(if (isBmpHorizontal) 100.dp else 120.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFFCBD5E1))
                            .background(Color.White)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawImage(
                                image = previewBitmap.asImageBitmap(),
                                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                    }

                    // DPI Standard tag watermark label overlay on preview window top-right
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0xFF0F172A).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = when (selectedFormat) {
                                "PNG" -> "300 DPI LOSSLESS"
                                "PDF" -> "VECTOR SCALED PDF"
                                else -> "COMPACT JPEG"
                            },
                            color = Color.White,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // EXPORT PRIMARY ACTION BUTTON
                Button(
                    onClick = {
                        val safeFilename = if (customFilename.isBlank()) {
                            "Passport_${System.currentTimeMillis()}"
                        } else {
                            customFilename.trim().replace("\\s+".toRegex(), "_")
                        }
                        
                        if (selectedFormat == "PDF") {
                            onSaveAsPdf(safeFilename)
                        } else {
                            val format = if (selectedFormat == "PNG") {
                                Bitmap.CompressFormat.PNG
                            } else {
                                Bitmap.CompressFormat.JPEG
                            }
                            onSaveToGallery(isFullSheet, format, safeFilename)
                        }
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("download_action_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when (selectedFormat) {
                            "PDF" -> Color(0xFFEF4444) // Red for PDF
                            "PNG" -> Color(0xFF0284C7) // Sky blue for PNG
                            else -> Color(0xFF005A9C)  // Blue for JPEG
                        }
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = if (selectedFormat == "PDF") Icons.Default.Print else Icons.Default.Download,
                        contentDescription = "Download button icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (selectedFormat) {
                            "PDF" -> "EXPORT PRINT-READY PDF"
                            "PNG" -> "DOWNLOAD LOSSLESS PNG"
                            else -> "DOWNLOAD COMPACT JPEG"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DISMISS CAPABLE OUTLINED BACKUP BUTTON
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Text(
                        text = "Close",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// Low-level Image Decoding Helpers
// ----------------------------------------------------

fun loadUriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            val original = MediaStore.Images.Media.getBitmap(resolver, uri)
            original.copy(Bitmap.Config.ARGB_8888, true)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
