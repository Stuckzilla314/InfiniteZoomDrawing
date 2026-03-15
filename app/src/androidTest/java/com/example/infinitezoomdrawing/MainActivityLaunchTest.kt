package com.example.infinitezoomdrawing

import android.widget.ImageButton
import android.widget.TextView
import android.view.View
import com.google.android.material.button.MaterialButton
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    companion object {
        private const val EPSILON = 1e-6
    }

    @Test
    fun launchMainActivity_displaysDrawingScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById(R.id.drawingView))
            }
        }
    }

    @Test
    fun launchMainActivity_usesAccessibleIconButtonsForBottomActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val expectedButtons = listOf(
                    R.id.btnNew to R.string.new_drawing,
                    R.id.btnOpen to R.string.open_drawing,
                    R.id.btnSave to R.string.save_drawing,
                    R.id.btnBrushPen to R.string.brush_pen,
                    R.id.btnBrushMarker to R.string.brush_marker,
                    R.id.btnBrushSoft to R.string.brush_soft,
                    R.id.btnBrushEraser to R.string.brush_eraser,
                    R.id.btnUndo to R.string.undo,
                    R.id.btnRedo to R.string.redo,
                    R.id.btnClear to R.string.clear_canvas
                )

                expectedButtons.forEach { (viewId, descriptionId) ->
                    val button = activity.findViewById<ImageButton>(viewId)
                    assertNotNull(button)
                    assertEquals(activity.getString(descriptionId), button.contentDescription)
                }
            }
        }
    }

    @Test
    fun launchMainActivity_startsWithCompactToolsPanelAndCanExpand() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val toolsContent = activity.findViewById<View>(R.id.layoutToolsContent)
                val toggleButton = activity.findViewById<MaterialButton>(R.id.btnToggleTools)
                val summary = activity.findViewById<TextView>(R.id.tvToolSummary)

                assertNotNull(toolsContent)
                assertNotNull(toggleButton)
                assertNotNull(summary)
                assertEquals(View.GONE, toolsContent.visibility)
                assertEquals(activity.getString(R.string.show_tools), toggleButton.text.toString())
                assertTrue(summary.text.contains(activity.getString(R.string.brush_pen)))
                assertTrue(summary.text.contains(activity.getString(R.string.brush_size_label, 12)))

                toggleButton.performClick()

                assertEquals(View.VISIBLE, toolsContent.visibility)
                assertEquals(activity.getString(R.string.hide_tools), toggleButton.text.toString())
            }
        }
    }

    @Test
    fun launchMainActivity_initializesDrawingViewViewportDefaults() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val drawingView = activity.findViewById<DrawingView>(R.id.drawingView)

                assertNotNull(drawingView)
                assertEquals(1.0, drawingView.getViewportScale(), EPSILON)
                assertEquals(0.0, drawingView.getViewportOffsetX(), EPSILON)
                assertEquals(0.0, drawingView.getViewportOffsetY(), EPSILON)
                assertFalse(drawingView.requiresCompositingLayerForTesting())
            }
        }
    }
}
