package com.seedlessds.app.emu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.seedlessds.app.R
import kotlin.math.min

@Composable
internal fun PhantomControls(player: PhantomPlayer) {
    Box(Modifier.fillMaxSize().padding(8.dp)) {
        Box(Modifier.align(Alignment.BottomStart)) {
            Tag(stringResource(if (player.primary) R.string.phantom_playing_main else R.string.phantom_playing_clone), TagKind.NEUTRAL)
        }
        TextButton(onClick = { player.notify(PhantomPlayer.SWITCH) }, modifier = Modifier.align(Alignment.BottomEnd)) {
            Text(stringResource(if (player.primary) R.string.phantom_to_peer else R.string.phantom_to_primary))
        }
    }
}

internal class PhantomFrame(context: Context, private val rectangles: () -> List<FloatArray>) : View(context) {
    private val border = 6f * resources.displayMetrics.density
    private val paint = Paint()
    private val band = Path()

    override fun onDraw(canvas: Canvas) {
        for (rect in rectangles()) {
            val width = rect[2]
            val height = rect[3]
            val depth = min(border, min(width, height) / 2)
            if (depth <= 0) continue
            canvas.save()
            canvas.translate(rect[0], rect[1])
            drawSide(canvas, width, depth)
            canvas.translate(width, 0f)
            canvas.rotate(90f)
            drawSide(canvas, height, depth)
            canvas.translate(height, 0f)
            canvas.rotate(90f)
            drawSide(canvas, width, depth)
            canvas.translate(width, 0f)
            canvas.rotate(90f)
            drawSide(canvas, height, depth)
            canvas.restore()
        }
    }

    private fun drawSide(canvas: Canvas, length: Float, depth: Float) {
        band.reset()
        band.moveTo(0f, 0f)
        band.lineTo(length, 0f)
        band.lineTo(length - depth, depth)
        band.lineTo(depth, depth)
        band.close()
        paint.shader = LinearGradient(0f, 0f, 0f, depth, Color.argb(85, 255, 255, 255),
            Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        canvas.drawPath(band, paint)
    }
}
