package com.enverolab.comandas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enverolab.comandas.R
import com.enverolab.comandas.domain.*

internal val PosOrange = Color(0xffff9800)
internal val PosGreen = Color(0xff388e3c)

internal fun originalColors(dark: Boolean): ColorScheme =
    if (dark)
        darkColorScheme(
            primary = Color(0xffc9ac84),
            onPrimary = Color(0xff030303),
            primaryContainer = Color(0xff5c421d),
            onPrimaryContainer = Color.White,
            secondary = Color(0xff1d4f5c),
            background = Color(0xff030303),
            surface = Color(0xff030303),
            surfaceContainer = Color(0xff161616),
            surfaceContainerHigh = Color(0xff161616),
            surfaceContainerHighest = Color(0xff161616),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xffe4e4e4),
            error = Color(0xffb5545e),
        )
    else
        lightColorScheme(
            primary = Color(0xff5c421d),
            onPrimary = Color.White,
            primaryContainer = Color(0xfffff3e6),
            onPrimaryContainer = Color.Black,
            secondary = Color(0xffc9ac84),
            onSecondary = Color(0xff5c421d),
            secondaryContainer = Color(0xfffff3e6),
            onSecondaryContainer = Color(0xff5c421d),
            background = Color(0xfff2f2f2),
            surface = Color(0xfff2f2f2),
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            surfaceContainerHighest = Color.White,
            onSurface = Color.Black,
            onSurfaceVariant = Color(0xff484848),
            error = Color(0xffd32f2f),
        )

internal fun originalShapes() =
    Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

@Composable
internal fun BrandLogo(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.logo_comandas), "Comandas", modifier)
}

@Composable
internal fun OriginalNavigation(
    user: Row,
    screen: String,
    admin: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onLogout: () -> Unit,
    logoPath: String = "",
    companyName: String,
) {
    Column(
        Modifier.width(90.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 5.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bitmap =
            androidx.compose.runtime.remember(logoPath) {
                com.enverolab.comandas.io.logoImage(logoPath)
            }
        if (bitmap != null)
            Image(bitmap.asImageBitmap(), "Logotipo de la empresa", Modifier.size(60.dp))
        else BrandLogo(Modifier.size(52.dp).padding(bottom = 8.dp))
        /*Text("COMANDAS", fontSize = 10.sp, lineHeight = 12.sp)*/
        Text(
            companyName.trim().ifEmpty { "COMANDAS" },
             modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
             fontSize = 11.sp,
             lineHeight = 14.sp,
             textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))
        (listOf("Menú", "Comandas", "Inventario", "Reportes", "Historial", "Configuración") +
                if (admin) listOf("Permisos", "Catálogos") else emptyList())
            .forEach { name ->
                Surface(
                    onClick = { onSelect(name) },
                    enabled = enabled,
                    color =
                        if (name == screen) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).testTag("nav-$name"),
                ) {
                    Column(
                        Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NavGlyph(name)
                        Text(name, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
                    }
                }
            }
        Text(
            user.text("username"),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 16.dp),
        )
        TextButton(onClick = onLogout, enabled = enabled, contentPadding = PaddingValues(2.dp)) {
            Text("Cerrar sesión", fontSize = 10.sp, lineHeight = 12.sp)
        }
    }
}

@Composable
private fun NavGlyph(name: String) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        fun line(x: Float, y: Float, x2: Float, y2: Float) =
            drawLine(
                ink,
                androidx.compose.ui.geometry.Offset(x * u, y * u),
                androidx.compose.ui.geometry.Offset(x2 * u, y2 * u),
                2 * u,
            )
        when (name) {
            "Menú" -> {
                drawPath(
                    Path().apply {
                        moveTo(6 * u, 3 * u)
                        lineTo(18 * u, 3 * u)
                        lineTo(17 * u, 10 * u)
                        quadraticTo(12 * u, 17 * u, 7 * u, 10 * u)
                        close()
                    },
                    ink,
                    style = Stroke(2 * u),
                )
                line(12f, 14f, 12f, 21f)
                line(8f, 21f, 16f, 21f)
            }
            "Comandas",
            "Inventario" -> {
                drawRoundRect(
                    ink,
                    androidx.compose.ui.geometry.Offset(3 * u, 4 * u),
                    androidx.compose.ui.geometry.Size(18 * u, 17 * u),
                    androidx.compose.ui.geometry.CornerRadius(2 * u),
                    style = Stroke(2 * u),
                )
                line(7f, 9f, 17f, 9f)
                line(7f, 13f, 17f, 13f)
                line(7f, 17f, 14f, 17f)
            }
            "Reportes" -> {
                line(4f, 21f, 21f, 21f)
                line(6f, 19f, 6f, 10f)
                line(12f, 19f, 12f, 3f)
                line(18f, 19f, 18f, 7f)
            }
            "Permisos" -> {
                drawCircle(
                    ink,
                    4 * u,
                    androidx.compose.ui.geometry.Offset(12 * u, 6 * u),
                    style = Stroke(2 * u),
                )
                drawArc(
                    ink,
                    180f,
                    180f,
                    false,
                    androidx.compose.ui.geometry.Offset(4 * u, 13 * u),
                    androidx.compose.ui.geometry.Size(16 * u, 12 * u),
                    style = Stroke(2 * u),
                )
            }
            else -> {
                drawCircle(ink, 8 * u, style = Stroke(2 * u))
                line(12f, 6f, 12f, 12f)
                line(12f, 12f, 17f, 14f)
            }
        }
    }
}
