package com.example.util

import android.content.Context
import android.graphics.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleDataGenerator {

    fun generateIfNeeded(context: Context, force: Boolean = false): String {
        val demoDir = File(context.filesDir, "DemoGallery")
        if (demoDir.exists() && !force) {
            return demoDir.absolutePath
        }
        demoDir.mkdirs()

        // 1. Generate beautiful standalone images
        createGradientImage(demoDir, "Sunset_Gradient.jpg", Color.parseColor("#FF5722"), Color.parseColor("#FFC107"), "Sunset Gradient")
        createGradientImage(demoDir, "Ocean_Deep.jpg", Color.parseColor("#00BCD4"), Color.parseColor("#3F51B5"), "Ocean Deep")
        createGradientImage(demoDir, "Cyberpunk_Neon.jpg", Color.parseColor("#E91E63"), Color.parseColor("#9C27B0"), "Cyberpunk Neon")

        // 2. Generate a ZIP file with images inside
        val zipFile = File(demoDir, "Travel_Archive.zip")
        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                addEmptyImageToZip(zos, "Japan_Kyoto.jpg", Color.parseColor("#E57373"), "Kyoto Pagoda")
                addEmptyImageToZip(zos, "France_Paris.jpg", Color.parseColor("#64B5F6"), "Eiffel Tower")
                addEmptyImageToZip(zos, "Iceland_Aurora.jpg", Color.parseColor("#81C784"), "Aurora Borealis")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Generate RAW files - using real file extensions, utilizing EXIF-friendly structure
        createGradientImage(demoDir, "Raw_landscape.dng", Color.parseColor("#4DB6AC"), Color.parseColor("#00796B"), "RAW Camera Frame (.DNG)")
        createGradientImage(demoDir, "Raw_portrait.cr2", Color.parseColor("#BA68C8"), Color.parseColor("#512DA8"), "RAW Sensor Preview (.CR2)")

        return demoDir.absolutePath
    }

    private fun createGradientImage(dir: File, name: String, colorStart: Int, colorEnd: Int, title: String) {
        val file = File(dir, name)
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Radial/Linear Gradient
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 800f, 600f, colorStart, colorEnd, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 800f, 600f, paint)

        // Draw geometric design patterns
        val patternPaint = Paint().apply {
            color = Color.WHITE
            alpha = 30
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawCircle(400f, 300f, 150f, patternPaint)
        canvas.drawCircle(400f, 300f, 250f, patternPaint)
        canvas.drawLine(0f, 0f, 800f, 600f, patternPaint)
        canvas.drawLine(0f, 600f, 800f, 0f, patternPaint)

        // Draw textual elements
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(title, 400f, 310f, textPaint)

        val descPaint = Paint().apply {
            color = Color.WHITE
            alpha = 180
            textSize = 20f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("LOCAL HIGH- FIDELITY CACHED RENDER", 400f, 480f, descPaint)

        try {
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }
    }

    private fun addEmptyImageToZip(zos: ZipOutputStream, entryName: String, bgColor: Int, text: String) {
        val bitmap = Bitmap.createBitmap(600, 450, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        // Draw geometric hills to mimic mountains/scenery visually
        val hillPaint = Paint().apply {
            color = Color.WHITE
            alpha = 70
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val path = Path().apply {
            moveTo(50f, 450f)
            lineTo(250f, 150f)
            lineTo(450f, 450f)
            close()
        }
        canvas.drawPath(path, hillPaint)

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(text, 300f, 230f, textPaint)

        try {
            zos.putNextEntry(ZipEntry(entryName))
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, zos)
            zos.closeEntry()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            bitmap.recycle()
        }
    }
}
