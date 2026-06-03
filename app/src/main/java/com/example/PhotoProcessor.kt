package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// Photo Templates
enum class PhotoTemplate(
    val displayName: String,
    val widthMm: Int,
    val heightMm: Int,
    val targetHeadPercentMin: Float = 0.70f,
    val targetHeadPercentMax: Float = 0.80f
) {
    INDIAN_PASSPORT("Indian Passport (35x45 mm)", 35, 45, 0.70f, 0.80f),
    INDIAN_VISA("Indian Visa (51x51 mm / 2x2\")", 51, 51, 0.55f, 0.70f),
    CUSTOM("Custom Size (35x35 mm)", 35, 35, 0.60f, 0.80f)
}

// Border Preset options
enum class BorderPreset(val displayName: String, val thicknessDp: Int, val colorHex: Color) {
    NONE("No Border", 0, Color.Transparent),
    THIN_BLACK("Thin Black", 1, Color.Black),
    THICK_BLACK("Thick Black", 3, Color.Black),
    THIN_BLUE("Thin Royal Blue", 1, Color(0xFF005A9C)),
    CUSTOM("Custom", 2, Color.Black)
}

// Background preset options
enum class BackgroundPreset(val displayName: String, val color: Color?) {
    ORIGINAL("Original", null),
    WHITE("White", Color.White),
    LIGHT_BLUE("Light Blue", Color(0xFFE0F2FE)), // Tailwind light blue 100
    LIGHT_GRAY("Light Gray", Color(0xFFF1F5F9)), // Tailwind slate 100
    ROYAL_BLUE("Royal Blue", Color(0xFF1E3A8A)), // deep formal
    CUSTOM("Color Picker", Color.White)
}

// Result model for face auto detect and alignment
data class AutoAlignResult(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val rotation: Float,
    val msg: String
)

// Face Quality Check Result
data class QualityCheckResult(
    val isResolutionOk: Boolean,
    val currentResolutionString: String,
    val faceDetected: Boolean,
    val faceCentered: Boolean,
    val headSizeOk: Boolean,
    val warnings: List<String>
)

object PhotoProcessor {

    // Runs Face Detection locally via ML Kit to evaluate the passport alignment rules
    fun performQualityCheck(
        bitmap: Bitmap,
        cropRectPercentOffset: Float = 0.15f, // guide area width/height margin
        onComplete: (QualityCheckResult) -> Unit
    ) {
        val width = bitmap.width
        val height = bitmap.height
        val isResolutionOk = width >= 600 && height >= 600
        val resString = "${width}x${height} px"
        val warnings = mutableListOf<String>()

        if (!isResolutionOk) {
            warnings.add("Image resolution is low ($resString). 300+ DPI requires at least 600px width.")
        }

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()

        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onComplete(
                        QualityCheckResult(
                            isResolutionOk = isResolutionOk,
                            currentResolutionString = resString,
                            faceDetected = false,
                            faceCentered = false,
                            headSizeOk = false,
                            warnings = warnings + "No face detected in the image. Ensure proper lighting."
                        )
                    )
                } else {
                    val face = faces[0]
                    val faceBox = face.boundingBox

                    // Evaluate centering
                    val faceCenterX = faceBox.centerX().toFloat()
                    val faceCenterY = faceBox.centerY().toFloat()
                    val imgCenterX = width / 2f
                    val imgCenterY = height / 2f

                    // Center tolerance: face center must be within 15% of background center
                    val dx = abs(faceCenterX - imgCenterX) / width
                    val dy = abs(faceCenterY - imgCenterY) / height
                    val faceCentered = dx < 0.15f && dy < 0.15f
                    if (!faceCentered) {
                        warnings.add("Face is not centered. Adjust image position.")
                    }

                    // Height bounds evaluation (70% - 80% for Passport standard)
                    val faceHeight = faceBox.height().toFloat()
                    val ratio = faceHeight / height
                    val headSizeOk = ratio in 0.45f..0.85f // wide acceptable scale for flexible crop
                    if (ratio < 0.50f) {
                        warnings.add("Head size is too small in the frame. Zoom In.")
                    } else if (ratio > 0.85f) {
                        warnings.add("Head size is too large. Zoom Out.")
                    }

                    onComplete(
                        QualityCheckResult(
                            isResolutionOk = isResolutionOk,
                            currentResolutionString = resString,
                            faceDetected = true,
                            faceCentered = faceCentered,
                            headSizeOk = headSizeOk,
                            warnings = warnings
                        )
                    )
                }
            }
            .addOnFailureListener {
                onComplete(
                    QualityCheckResult(
                        isResolutionOk = isResolutionOk,
                        currentResolutionString = resString,
                        faceDetected = false,
                        faceCentered = false,
                        headSizeOk = false,
                        warnings = warnings + "Face validation engine errored: ${it.localizedMessage}"
                    )
                )
            }
    }

    // Try to automatically align and center the face if found in standard sizing
    fun autoCenterAndAlign(
        bitmap: Bitmap,
        onComplete: (AutoAlignResult?) -> Unit
    ) {
        val width = bitmap.width
        val height = bitmap.height

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()

        val detector = FaceDetection.getClient(options)
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    onComplete(null)
                } else {
                    val face = faces[0]
                    val box = face.boundingBox

                    // Target head size in the crop viewport is roughly 70% of viewport height.
                    // Calculate target viewport size = face.height / 0.70f
                    val targetViewportHeight = box.height() / 0.70f
                    val currentViewportHeight = min(width, height).toFloat()

                    // Ideal scale to fit
                    val scale = currentViewportHeight / targetViewportHeight

                    // Offsets to center face.centerX() and face.centerY()
                    val faceCenterX = box.centerX()
                    val faceCenterY = box.centerY()

                    // Offset should be whatever centers this target point
                    val dx = (width / 2f) - faceCenterX
                    val dy = (height / 2f - (box.height() * 0.1f)) - faceCenterY // align slightly below center

                    onComplete(
                        AutoAlignResult(
                            scale = scale.coerceIn(0.5f, 4.0f),
                            offsetX = dx * scale,
                            offsetY = dy * scale,
                            rotation = -face.headEulerAngleZ, // correct tilt roll dynamically!
                            msg = "Face auto-aligned and tilt corrected!"
                        )
                    )
                }
            }
            .addOnFailureListener {
                onComplete(null)
            }
    }

    // Replaces background color locally using tap color with distance check
    fun replaceColorTap(
        sourceBitmap: Bitmap,
        tappedX: Int,
        tappedY: Int,
        replacementColor: Int,
        tolerance: Int
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val safeX = tappedX.coerceIn(0, width - 1)
        val safeY = tappedY.coerceIn(0, height - 1)

        val targetColor = sourceBitmap.getPixel(safeX, safeY)
        val tr = (targetColor shr 16) and 0xFF
        val tg = (targetColor shr 8) and 0xFF
        val tb = targetColor and 0xFF

        val pixels = IntArray(width * height)
        sourceBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val toleranceSq = tolerance * tolerance * 3

        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF

            val dr = r - tr
            val dg = g - tg
            val db = b - tb

            if (dr * dr + dg * dg + db * db < toleranceSq) {
                pixels[i] = replacementColor
            }
        }

        val result = Bitmap.createBitmap(width, height, sourceBitmap.config ?: Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    // Applies full image enhancements (Brightness, Contrast, Saturation, Sharpness) in high resolution
    fun applyEnhancements(
        sourceBitmap: Bitmap,
        brightness: Float, // -100 to +100 offset
        contrast: Float, // 0.5 to 2.0 multiplier
        saturation: Float, // 0.0 to 2.0 multiplier
        sharpness: Float // 0.0 to 5.0 (0 means original, >0 means high definition filter)
    ): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        // 1. Build advanced ColorMatrix combining Brightness, Contrast & Saturation
        val matrix = ColorMatrix()

        // Saturation matrix
        matrix.setSaturation(saturation)

        // Contrast and brightness matrix
        // Contrast scales around 128 (midpoint gray). Contrast formula: R' = (R - 128) * contrast + 128 + brightness_offset
        val scale = contrast
        val translate = 127.5f * (1.0f - scale) + brightness

        val cm = ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))

        // Concat contrast-brightness into result matrix
        matrix.postConcat(cm)
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        // Draw enhanced image onto the new bitmap
        canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)

        // 2. Sharpness Filter (Unsharp Mask/Kernel Convolution) if sharpness value > 0
        if (sharpness > 0f) {
            return applyConvolutionSharpness(result, sharpness)
        }

        return result
    }

    // Fast Convoluted Sharpness Kernel application
    private fun applyConvolutionSharpness(src: Bitmap, value: Float): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        val outPixels = IntArray(width * height)

        // Sharpen kernel definition based on slider value
        val center = 1f + 4f * value
        val edge = -value

        // Simple 3x3 sharpen matrix
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var r = 0f
                var g = 0f
                var b = 0f

                // Center pixel: (x, y)
                // Boundary pixels: top, bottom, left, right
                val pCenter = pixels[y * width + x]
                val pTop = pixels[(y - 1) * width + x]
                val pBottom = pixels[(y + 1) * width + x]
                val pLeft = pixels[y * width + (x - 1)]
                val pRight = pixels[y * width + (x + 1)]

                val rc = ((pCenter shr 16) and 0xFF) * center
                val gc = ((pCenter shr 8) and 0xFF) * center
                val bc = (pCenter and 0xFF) * center

                val re = (((pTop shr 16) and 0xFF) + ((pBottom shr 16) and 0xFF) + ((pLeft shr 16) and 0xFF) + ((pRight shr 16) and 0xFF)) * edge
                val ge = (((pTop shr 8) and 0xFF) + ((pBottom shr 8) and 0xFF) + ((pLeft shr 8) and 0xFF) + ((pRight shr 8) and 0xFF)) * edge
                val be = ((pTop and 0xFF) + (pBottom and 0xFF) + (pLeft and 0xFF) + (pRight and 0xFF)) * edge

                r = (rc + re).coerceIn(0f, 255f)
                g = (gc + ge).coerceIn(0f, 255f)
                b = (bc + be).coerceIn(0f, 255f)

                outPixels[y * width + x] = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
            }
        }

        // Fill border pixels with original value
        for (x in 0 until width) {
            outPixels[x] = pixels[x]
            outPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            outPixels[y * width] = pixels[y * width]
            outPixels[y * width + (width - 1)] = pixels[y * width + (width - 1)]
        }

        val sharpBmp = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        sharpBmp.setPixels(outPixels, 0, width, 0, 0, width, height)
        return sharpBmp
    }

    // Crops and extracts the user photo matching the chosen template sizing
    fun generateSinglePassport(
        sourceBitmap: Bitmap,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        rotation: Float,
        template: PhotoTemplate,
        borderPreset: BorderPreset,
        customBorderColor: Color?,
        customBorderSizeDp: Int?,
        solidBgColor: Int? // replaced background color if any
    ): Bitmap {
        // Target single passport width & height based on aspect ratio
        val outWidth = 413 // standard 35mm at 300 DPI (approx 413)
        val outHeight = 532 // standard 45mm at 300 DPI (approx 531.5)

        val passportBmp = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(passportBmp)

        // Fill background color if active
        val bgPaint = Paint().apply {
            color = solidBgColor ?: android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, outWidth.toFloat(), outHeight.toFloat(), bgPaint)

        // Draw source photo with User's Transforms
        canvas.save()
        canvas.translate(outWidth / 2f + offsetX, outHeight / 2f + offsetY)
        canvas.rotate(rotation)
        canvas.scale(scale, scale)

        // Centralise source image
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, -sourceBitmap.width / 2f, -sourceBitmap.height / 2f, p)
        canvas.restore()

        // Draw Passport standard border around photo
        val borderThick = customBorderSizeDp ?: borderPreset.thicknessDp
        if (borderThick > 0) {
            val borderColor = customBorderColor?.toArgb() ?: borderPreset.colorHex.toArgb()
            val borderPaint = Paint().apply {
                color = borderColor
                style = Paint.Style.STROKE
                strokeWidth = borderThick * 3f // scale for 300 DPI definition
            }
            // Draw slightly inside to avoid edge truncation
            val halfStroke = (borderThick * 3f) / 2f
            canvas.drawRect(
                halfStroke,
                halfStroke,
                outWidth.toFloat() - halfStroke,
                outHeight.toFloat() - halfStroke,
                borderPaint
            )
        }

        return passportBmp
    }

    // Generates a fully printable 4x6" Sheet with uniform spacing and margins
    fun generate4x6Sheet(
        singlePassport: Bitmap,
        copiesCount: Int, // 8, 10, 12, 16 or automatic maximum
        horizontalOrientation: Boolean = true
    ): Bitmap {
        // Print Sheet dimensions at 300 DPI
        // 4 inch x 6 inch is 1200 x 1800.
        val sheetWidth = if (horizontalOrientation) 1800 else 1200
        val sheetHeight = if (horizontalOrientation) 1200 else 1800

        val sheetBmp = Bitmap.createBitmap(sheetWidth, sheetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheetBmp)

        // White card background
        val bgPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, sheetWidth.toFloat(), sheetHeight.toFloat(), bgPaint)

        // Draw alignment grid
        val passportWidth = singlePassport.width // 413
        val passportHeight = singlePassport.height // 532

        // Determine columns and rows dynamically based on copies requested
        var cols = 4
        var rows = 2

        when (copiesCount) {
            8 -> {
                cols = if (horizontalOrientation) 4 else 2
                rows = if (horizontalOrientation) 2 else 4
            }
            10 -> {
                cols = if (horizontalOrientation) 5 else 2
                rows = if (horizontalOrientation) 2 else 5
            }
            12 -> {
                cols = if (horizontalOrientation) 4 else 3
                rows = if (horizontalOrientation) 3 else 4
            }
            16 -> {
                // If 16 copies, we might need smaller gaps or slightly squeeze them
                cols = if (horizontalOrientation) 4 else 4
                rows = if (horizontalOrientation) 4 else 4
            }
            else -> {
                // Auto maximum layout check
                cols = sheetWidth / (passportWidth + 15)
                rows = sheetHeight / (passportHeight + 15)
            }
        }

        // Limit loop count to either user decision or practical dimensions limit
        val actualCopies = min(copiesCount, cols * rows)

        // Calculate auto centering margins
        val totalGridWidth = cols * passportWidth + (cols - 1) * 30
        val totalGridHeight = rows * passportHeight + (rows - 1) * 30

        val startX = (sheetWidth - totalGridWidth) / 2f
        val startY = (sheetHeight - totalGridHeight) / 2f

        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        // Helper guides / cutting bounds (light gray)
        val cuttingP = Paint().apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        for (i in 0 until actualCopies) {
            val r = i / cols
            val c = i % cols

            val px = startX + c * (passportWidth + 30)
            val py = startY + r * (passportHeight + 30)

            // Draw passport copy
            canvas.drawBitmap(singlePassport, px, py, paint)

            // Draw clean cutting border around copies to make scissors styling simple
            canvas.drawRect(
                px - 2,
                py - 2,
                px + passportWidth + 2,
                py + passportHeight + 2,
                cuttingP
            )
        }

        // Add small elegant title text at the minimal bottom margin to feel like standard photostudio cards
        val textPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 20f
            isAntiAlias = true
        }
        val watermark = "PhotoGen India • Print Ready 4x6\" Card • 300 DPI Ultra HD"
        canvas.drawText(
            watermark,
            (sheetWidth - textPaint.measureText(watermark)) / 2f,
            sheetHeight - 20f,
            textPaint
        )

        return sheetBmp
    }

    // Save Bitmap directly to local storage (Pictures directory) and scan it
    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileName: String, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG): File? {
        val extension = if (format == Bitmap.CompressFormat.PNG) "png" else "jpg"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        val imageFile = File(storageDir, "$fileName.$extension")

        return try {
            val fos = FileOutputStream(imageFile)
            bitmap.compress(format, 95, fos)
            fos.flush()
            fos.close()
            imageFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Generate standard print-ready PDF using standard Android PdfDocument class
    fun savePrintSheetToPdf(
        sheetBitmap: Bitmap,
        context: Context,
        fileName: String
    ): File? {
        val pdfDocument = PdfDocument()

        // 4x6" print standard layout. A postscript point represents 1/72 inch.
        // 4 x 72 = 288 points
        // 6 x 72 = 432 points
        val isHorizontal = sheetBitmap.width > sheetBitmap.height
        val pageWidth = if (isHorizontal) 432 else 288
        val pageHeight = if (isHorizontal) 288 else 432

        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas

        // Stretch/draw the high resolution bitmap down onto the 300 DPI scaled container space
        val rect = android.graphics.Rect(0, 0, pageWidth, pageHeight)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sheetBitmap, null, rect, paint)

        pdfDocument.finishPage(page)

        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val pdfFile = File(storageDir, "$fileName.pdf")

        return try {
            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
