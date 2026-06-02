package com.turnit.ide.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer

private const val WAVE_FREQUENCY = 6.2831855f
private const val WAVE_AMPLITUDE = 6.0f
private const val VIGNETTE_BOOST = 0.08f
private const val ANIMATION_DURATION_MS = 6000

const val LIQUID_GLASS_SHADER = """
uniform shader contents;
uniform float2 resolution;
uniform float time;
uniform float waveFrequency;
uniform float waveAmplitude;
uniform float vignetteBoost;

half4 main(float2 fragCoord) {
    float2 safeResolution = max(resolution, float2(1.0, 1.0));
    float2 uv = fragCoord / safeResolution;
    float waveX = sin((uv.y + time) * waveFrequency) * waveAmplitude;
    float waveY = cos((uv.x + time) * waveFrequency) * waveAmplitude;
    float2 distorted = fragCoord + float2(waveX, waveY);
    half4 color = contents.eval(distorted);
    float vignette = smoothstep(0.85, 0.25, distance(uv, float2(0.5, 0.5)));
    color.rgb += vignetteBoost * vignette;
    return color;
}
"""

fun Modifier.liquidGlassBackground(
    fallbackColor: Color = IdeColors.BgSurface
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this.background(fallbackColor)
    }

    val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
    val runtimeEffect = remember(shader) {
        shader.setFloatUniform("waveFrequency", WAVE_FREQUENCY)
        shader.setFloatUniform("waveAmplitude", WAVE_AMPLITUDE)
        shader.setFloatUniform("vignetteBoost", VIGNETTE_BOOST)
        RenderEffect.createRuntimeShaderEffect(shader, "contents")
    }
    val composeEffect = remember(runtimeEffect) { runtimeEffect.asComposeRenderEffect() }
    val transition = rememberInfiniteTransition(label = "liquid_glass_transition")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = ANIMATION_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_glass_time"
    )

    this
        .graphicsLayer(renderEffect = composeEffect)
        .drawWithCache {
            shader.setFloatUniform("resolution", size.width, size.height)
            onDrawWithContent {
                shader.setFloatUniform("time", time)
                drawContent()
            }
        }
}
