package com.example.infinitezoomdrawing

import android.widget.ImageButton
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

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
}
