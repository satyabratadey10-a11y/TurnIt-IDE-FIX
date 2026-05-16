package com.turnit.ide.ui

import android.graphics.BitmapFactory
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.RuntimeShader
import androidx.compose.ui.graphics.RuntimeShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize

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
        BitmapFactory.decodeResource(context.resources, imageResId)?.asImageBitmap()
    } ?: return@composed this.background(fallbackColor)

    val imageShader = remember(bitmap) { ImageShader(bitmap, TileMode.Clamp, TileMode.Clamp) }
    val runtimeShader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }
    val shaderBrush = remember(runtimeShader) { RuntimeShaderBrush(runtimeShader) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var mouse by remember { mutableStateOf(Offset.Unspecified) }

    LaunchedEffect(imageShader) {
        runtimeShader.setInputShader("image", imageShader)
    }

    this
        .onSizeChanged { newSize ->
            size = newSize
            if (mouse.isUnspecified) {
                mouse = Offset(newSize.width / 2f, newSize.height / 2f)
            }
        }
        .pointerInput(size) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.position?.let { mouse = it }
                }
            }
        }
        .drawWithContent {
            if (size.width > 0 && size.height > 0) {
                runtimeShader.setFloatUniform("resolution", size.width.toFloat(), size.height.toFloat())
                val mousePoint = if (mouse.isUnspecified) {
                    Offset(size.width / 2f, size.height / 2f)
                } else {
                    mouse
                }
                runtimeShader.setFloatUniform("mouse", mousePoint.x, mousePoint.y)
            }
            drawRect(shaderBrush)
            drawContent()
        }
}
