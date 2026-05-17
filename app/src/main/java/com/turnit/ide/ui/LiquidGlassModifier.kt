package com.turnit.ide.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

private const val MIN_VALID_DRAWABLE_SIZE = 10
private const val FALLBACK_GRID_SIZE = 800
private const val FALLBACK_GRID_SPACING = 40

const val LIQUID_GLASS_SHADER = """
// ===== Uniforms (inputs from Kotlin) =====
uniform float2 resolution;
uniform float2 mouse;
uniform shader image;
// ===== Effect Parameters =====
const float REFRACTIVE_INDEX = 1.5;
const float CHROMATIC_ABERRATION = 0.02;
const float LENS_SIZE_MULTIPLIER = 200000.0;
const float BLUR_SAMPLES = 4.0;
half4 main(float2 fragCoord) {
    vec2 textureCoords = fragCoord / resolution.xy;
    vec2 mouseNormalized = mouse / resolution.xy;
    vec2 distanceFromMouse = textureCoords - mouseNormalized;
    float aspectRatio = resolution.x / resolution.y;
    float distanceX = distanceFromMouse.x * aspectRatio;
    float distanceField = pow(abs(distanceX), 8.0) + pow(abs(distanceFromMouse.y), 8.0);
    float lensBody = clamp((1.0 - distanceField * LENS_SIZE_MULTIPLIER) * 8.0, 0.0, 1.0);
    float borderOuter = clamp((0.95 - distanceField * (LENS_SIZE_MULTIPLIER * 0.95)) * 16.0, 0.0, 1.0);
    float borderInner = clamp(pow(0.9 - distanceField * (LENS_SIZE_MULTIPLIER * 0.95), 1.0) * 16.0, 0.0, 1.0);
    float lensBorder = borderOuter - borderInner;
    float shadowOuter = clamp((1.5 - distanceField * (LENS_SIZE_MULTIPLIER * 1.1)) * 2.0, 0.0, 1.0);
    float shadowInner = clamp(pow(1.0 - distanceField * (LENS_SIZE_MULTIPLIER * 1.1), 1.0) * 2.0, 0.0, 1.0);
    float shadowGradient = shadowOuter - shadowInner;
    vec4 finalColor = vec4(0.0);
    if (lensBody + lensBorder > 0.0) {
        vec2 centeredCoords = textureCoords - 0.5;
        float distortionAmount = 1.0 + (REFRACTIVE_INDEX - 1.0) * (1.0 - distanceField * 100000.0);
        vec2 distortedCoords = centeredCoords * distortionAmount + 0.5;
        vec2 aberrationOffset = CHROMATIC_ABERRATION * distanceFromMouse;
        float sampleCount = 0.0;
        for (float x = -BLUR_SAMPLES; x <= BLUR_SAMPLES; x++) {
            for (float y = -BLUR_SAMPLES; y <= BLUR_SAMPLES; y++) {
                vec2 sampleOffset = vec2(x, y) * 0.5 / resolution.xy;
                vec3 sampledColor;
                vec2 redSamplePos = (sampleOffset + distortedCoords + aberrationOffset) * resolution;
                sampledColor.r = image.eval(redSamplePos).r;
                vec2 greenSamplePos = (sampleOffset + distortedCoords) * resolution;
                sampledColor.g = image.eval(greenSamplePos).g;
                vec2 blueSamplePos = (sampleOffset + distortedCoords - aberrationOffset) * resolution;
                sampledColor.b = image.eval(blueSamplePos).b;
                finalColor += vec4(sampledColor, 1.0);
                sampleCount += 1.0;
            }
        }
        finalColor /= sampleCount;
        float topHighlight = clamp((clamp(distanceFromMouse.y, 0.0, 0.2) + 0.1) / 2.0, 0.0, 1.0);
        float bottomShadow = clamp((clamp(-distanceFromMouse.y, -1000.0, 0.2) * shadowGradient + 0.1) / 2.0, 0.0, 1.0);
        float lightingGradient = topHighlight + bottomShadow;
        finalColor = clamp(finalColor + vec4(lensBody) * lightingGradient + vec4(lensBorder) * 0.3, 0.0, 1.0);
    } else {
        finalColor = image.eval(fragCoord);
    }
    return half4(finalColor);
}
"""

fun Modifier.liquidGlassBackground(
    @DrawableRes imageResId: Int,
    fallbackColor: Color = IdeColors.BgSurface
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return@composed this.background(fallbackColor)
    }

    val context = LocalContext.current
    val bitmap = remember(imageResId) {
        val drawable = ContextCompat.getDrawable(context, imageResId)
        if (
            drawable != null &&
            drawable.intrinsicWidth > MIN_VALID_DRAWABLE_SIZE &&
            drawable.intrinsicHeight > MIN_VALID_DRAWABLE_SIZE
        ) {
            drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
        } else {
            // Generate a 800x800 textured grid bitmap so the liquid glass has something to refract
            val gridBitmap = Bitmap.createBitmap(FALLBACK_GRID_SIZE, FALLBACK_GRID_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(gridBitmap)
            val paint = android.graphics.Paint()

            // Dark background
            paint.color = android.graphics.Color.parseColor("#121212")
            canvas.drawRect(0f, 0f, FALLBACK_GRID_SIZE.toFloat(), FALLBACK_GRID_SIZE.toFloat(), paint)

            // Subtle grid lines
            paint.color = android.graphics.Color.parseColor("#2A2A2A")
            paint.strokeWidth = 2f
            for (i in 0..FALLBACK_GRID_SIZE step FALLBACK_GRID_SPACING) {
                canvas.drawLine(i.toFloat(), 0f, i.toFloat(), FALLBACK_GRID_SIZE.toFloat(), paint)
                canvas.drawLine(0f, i.toFloat(), FALLBACK_GRID_SIZE.toFloat(), i.toFloat(), paint)
            }
            gridBitmap
        }
    }

    val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
    var pointerPosition by remember { mutableStateOf(Offset.Unspecified) }

    val brush = remember(shader, pointerPosition, bitmap) {
        // Bind the image to the AGSL shader
        shader.setInputShader("image", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))

        // Pass resolution (requires layout size, assuming you pass it or hardcode for now)
        shader.setFloatUniform("resolution", bitmap.width.toFloat(), bitmap.height.toFloat())

        // Update mouse coordinates
        if (!pointerPosition.isUnspecified) {
            shader.setFloatUniform("mouse", pointerPosition.x, pointerPosition.y)
        } else {
            // Default to center if untouched
            shader.setFloatUniform("mouse", bitmap.width / 2f, bitmap.height / 2f)
        }

        // Bridge the native shader to Compose
        ShaderBrush(shader)
    }

    this
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.position?.let { pointerPosition = it }
                }
            }
        }
        .drawWithContent {
            drawRect(brush)
            drawContent()
        }
}
