package com.example.infinitezoomdrawing

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.infinitezoomdrawing.databinding.ActivityMainBinding
import java.io.IOException
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    companion object {
        private const val DEFAULT_TOOLS_EXPANDED = false
        private const val CONTINUOUS_ZOOM_INITIAL_DELAY_MS = 220L
    }

    private lateinit var binding: ActivityMainBinding
    private var toolsExpanded = DEFAULT_TOOLS_EXPANDED
    private val zoomHoldHandler = Handler(Looper.getMainLooper())
    private var activeZoomHoldRunnable: Runnable? = null
    private var activeZoomFrameCallback: Choreographer.FrameCallback? = null
    private var lastZoomFrameTimeNanos = 0L

    private val requestPermissionCode = 1001
    private val requestOpenImageCode = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        setupToolPanelToggle()
        setupBrushSizeSeekBar()
        setupColorPalette()
        setupBrushTypeButtons()
        setupActionButtons()
        setToolsExpanded(DEFAULT_TOOLS_EXPANDED)
    }

    // ── Brush size ────────────────────────────────────────────────────────────

    private fun setupBrushSizeSeekBar() {
        // SeekBar range is 0..79; add 1 for actual brush size (1..80)
        binding.seekBarBrushSize.progress = 11
        binding.tvBrushSizeLabel.text = getString(R.string.brush_size_label, 12)
        updateToolSummary()
        binding.seekBarBrushSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = (progress + 1).toFloat()
                binding.drawingView.brushSize = size
                binding.tvBrushSizeLabel.text = getString(R.string.brush_size_label, progress + 1)
                updateToolSummary()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // ── Color palette ─────────────────────────────────────────────────────────

    private val paletteColors = listOf(
        Color.BLACK, Color.DKGRAY, Color.GRAY, Color.WHITE,
        Color.RED, Color.rgb(255, 128, 0), Color.YELLOW, Color.GREEN,
        Color.CYAN, Color.BLUE, Color.rgb(128, 0, 255), Color.MAGENTA
    )

    private fun setupColorPalette() {
        val paletteViews = listOf(
            binding.colorSwatch0, binding.colorSwatch1, binding.colorSwatch2,
            binding.colorSwatch3, binding.colorSwatch4, binding.colorSwatch5,
            binding.colorSwatch6, binding.colorSwatch7, binding.colorSwatch8,
            binding.colorSwatch9, binding.colorSwatch10, binding.colorSwatch11
        )

        paletteViews.forEachIndexed { index, view ->
            view.setBackgroundColor(paletteColors[index])
            view.setOnClickListener { selectColor(paletteColors[index]) }
        }

        // Select black by default
        selectColor(Color.BLACK)
    }

    private fun selectColor(color: Int) {
        binding.drawingView.brushColor = color
        binding.viewSelectedColor.setBackgroundColor(color)
        updateToolSummary()
    }

    // ── Brush type buttons ────────────────────────────────────────────────────

    private fun setupBrushTypeButtons() {
        val buttons = mapOf(
            binding.btnBrushPen to BrushType.PEN,
            binding.btnBrushMarker to BrushType.MARKER,
            binding.btnBrushSoft to BrushType.BRUSH,
            binding.btnBrushEraser to BrushType.ERASER
        )

        fun updateSelection(selected: BrushType) {
            buttons.forEach { (btn, type) ->
                btn.isSelected = (type == selected)
                btn.alpha = if (type == selected) 1f else 0.5f
            }
            updateToolSummary()
        }

        buttons.forEach { (btn, type) ->
            btn.setOnClickListener {
                binding.drawingView.brushType = type
                updateSelection(type)
            }
        }

        // Default selection
        updateSelection(BrushType.PEN)
    }

    private fun setupToolPanelToggle() {
        binding.btnToggleTools.setOnClickListener { setToolsExpanded(!toolsExpanded) }
    }

    private fun setToolsExpanded(expanded: Boolean) {
        toolsExpanded = expanded
        binding.layoutToolsContent.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.btnToggleTools.text = getString(if (expanded) R.string.hide_tools else R.string.show_tools)
        binding.btnToggleTools.setIconResource(
            if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )
    }

    private fun updateToolSummary() {
        val brushLabel = getString(
            when (binding.drawingView.brushType) {
                BrushType.PEN -> R.string.brush_pen
                BrushType.MARKER -> R.string.brush_marker
                BrushType.BRUSH -> R.string.brush_soft
                BrushType.ERASER -> R.string.brush_eraser
            }
        )
        binding.tvToolSummary.text = getString(
            R.string.tool_summary,
            brushLabel,
            getString(R.string.brush_size_label, binding.seekBarBrushSize.progress + 1)
        )
    }

    // ── Action buttons (new / save / open / zoom / home / undo / redo / clear)

    private fun setupActionButtons() {
        binding.btnNew.setOnClickListener { confirmNewDrawing() }
        binding.btnSave.setOnClickListener { saveDrawing() }
        binding.btnOpen.setOnClickListener { requestOpenDrawing() }
        setupContinuousZoomButton(binding.btnZoomOut, 1.0 / TOOLBAR_ZOOM_FACTOR)
        setupContinuousZoomButton(binding.btnZoomIn, TOOLBAR_ZOOM_FACTOR)
        binding.btnHome.setOnClickListener {
            if (!binding.drawingView.animateReturnHome()) {
                Toast.makeText(this, R.string.already_home, Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnHome.setOnLongClickListener {
            when {
                binding.drawingView.isAtHome() && binding.drawingView.hasHomeCheckpoints() -> {
                    binding.drawingView.clearHomeCheckpoints()
                    Toast.makeText(this, R.string.home_checkpoints_cleared, Toast.LENGTH_SHORT).show()
                }
                binding.drawingView.isAtHome() -> {
                    Toast.makeText(this, R.string.already_home, Toast.LENGTH_SHORT).show()
                }
                binding.drawingView.addHomeCheckpoint() -> {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.home_checkpoint_added,
                            binding.drawingView.getHomeCheckpointCount()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    Toast.makeText(this, R.string.home_checkpoint_already_saved, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            true
        }
        binding.btnUndo.setOnClickListener {
            binding.drawingView.undo()
            Toast.makeText(this, R.string.undo, Toast.LENGTH_SHORT).show()
        }
        binding.btnRedo.setOnClickListener {
            binding.drawingView.redo()
            Toast.makeText(this, R.string.redo, Toast.LENGTH_SHORT).show()
        }
        binding.btnClear.setOnClickListener { confirmClearCanvas() }
    }

    private fun setupContinuousZoomButton(button: View, zoomFactor: Double) {
        var repeatedDuringHold = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    repeatedDuringHold = false
                    startContinuousZoom(zoomFactor) { repeatedDuringHold = true }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopContinuousZoom()
                    false
                }
                else -> false
            }
        }
        button.setOnClickListener {
            if (repeatedDuringHold) {
                repeatedDuringHold = false
            } else {
                binding.drawingView.zoomBy(zoomFactor)
            }
        }
    }

    private fun startContinuousZoom(zoomFactor: Double, onRepeat: () -> Unit) {
        stopContinuousZoom()
        val startZoomRunnable = Runnable {
            onRepeat()
            lastZoomFrameTimeNanos = System.nanoTime()
            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    if (activeZoomFrameCallback !== this) return

                    val elapsedMs = (frameTimeNanos - lastZoomFrameTimeNanos) / 1_000_000.0
                    lastZoomFrameTimeNanos = frameTimeNanos
                    binding.drawingView.zoomBy(
                        continuousZoomScaleFactor(
                            stepZoomFactor = zoomFactor,
                            stepIntervalMs = CONTINUOUS_ZOOM_REPEAT_DELAY_MS,
                            elapsedMs = elapsedMs
                        )
                    )
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
            activeZoomFrameCallback = frameCallback
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
        activeZoomHoldRunnable = startZoomRunnable
        zoomHoldHandler.postDelayed(startZoomRunnable, CONTINUOUS_ZOOM_INITIAL_DELAY_MS)
    }

    private fun stopContinuousZoom() {
        activeZoomHoldRunnable?.let(zoomHoldHandler::removeCallbacks)
        activeZoomHoldRunnable = null
        activeZoomFrameCallback?.let(Choreographer.getInstance()::removeFrameCallback)
        activeZoomFrameCallback = null
        lastZoomFrameTimeNanos = 0L
    }

    override fun onDestroy() {
        stopContinuousZoom()
        super.onDestroy()
    }

    private fun confirmNewDrawing() {
        AlertDialog.Builder(this)
            .setTitle(R.string.new_drawing)
            .setMessage(R.string.new_drawing_confirm)
            .setPositiveButton(R.string.yes) { _, _ -> binding.drawingView.clearCanvas() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmClearCanvas() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_canvas)
            .setMessage(R.string.clear_canvas_confirm)
            .setPositiveButton(R.string.yes) { _, _ -> binding.drawingView.clearCanvas() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private fun saveDrawing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), requestPermissionCode
                )
                return
            }
        }
        saveToGallery()
    }

    private fun saveToGallery() {
        val bitmap = binding.drawingView.exportBitmap()
        val filename = "drawing_${System.currentTimeMillis()}.png"

        val outputStream: OutputStream?
        val imageUri: Uri?

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/InfiniteZoomDrawing")
            }
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            outputStream = imageUri?.let { contentResolver.openOutputStream(it) }
        } else {
            @Suppress("DEPRECATION")
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val appDir = java.io.File(imagesDir, "InfiniteZoomDrawing").also { it.mkdirs() }
            val file = java.io.File(appDir, filename)
            imageUri = Uri.fromFile(file)
            outputStream = file.outputStream()
        }

        try {
            outputStream?.use { stream ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
            Toast.makeText(this, getString(R.string.drawing_saved, filename), Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.save_failed), Toast.LENGTH_SHORT).show()
        }
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    private fun requestOpenDrawing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), requestPermissionCode
                )
                return
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), requestPermissionCode
                )
                return
            }
        }
        launchImagePicker()
    }

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
        startActivityForResult(intent, requestOpenImageCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestOpenImageCode && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                if (bitmap != null) {
                    binding.drawingView.loadBitmap(bitmap)
                } else {
                    Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, R.string.open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Retry the action that required permission
                Toast.makeText(this, R.string.permission_granted, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
