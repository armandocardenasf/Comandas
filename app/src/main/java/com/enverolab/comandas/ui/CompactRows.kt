@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.enverolab.comandas.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import com.enverolab.comandas.domain.*
import com.enverolab.comandas.io.logoImage

@Composable
internal fun ActionIcon(
    kind: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color =
        if (kind == "delete") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
    ) {
        Canvas(Modifier.size(21.dp)) {
            val w = size.width
            val h = size.height
            val c = if (enabled) color else Color.Gray
            when (kind) {
                "delete" -> {
                    drawRect(
                        c,
                        Offset(w * .23f, h * .28f),
                        androidx.compose.ui.geometry.Size(w * .54f, h * .6f),
                        style = Stroke(2f),
                    )
                    drawLine(c, Offset(w * .1f, h * .23f), Offset(w * .9f, h * .23f), 2f)
                    drawLine(c, Offset(w * .35f, h * .12f), Offset(w * .65f, h * .12f), 2f)
                }
                "edit" -> {
                    drawLine(c, Offset(w * .2f, h * .8f), Offset(w * .8f, h * .2f), 5f)
                    drawLine(c, Offset(w * .15f, h * .9f), Offset(w * .4f, h * .85f), 2f)
                }
                "calendar" -> {
                    drawRect(
                        c,
                        Offset(w * .1f, h * .2f),
                        androidx.compose.ui.geometry.Size(w * .8f, h * .7f),
                        style = Stroke(2f),
                    )
                    drawLine(c, Offset(w * .1f, h * .4f), Offset(w * .9f, h * .4f), 2f)
                    drawLine(c, Offset(w * .3f, h * .05f), Offset(w * .3f, h * .3f), 2f)
                    drawLine(c, Offset(w * .7f, h * .05f), Offset(w * .7f, h * .3f), 2f)
                }
                else -> {
                    drawOval(
                        c,
                        Offset(w * .05f, h * .25f),
                        androidx.compose.ui.geometry.Size(w * .9f, h * .5f),
                        style = Stroke(2f),
                    )
                    drawCircle(c, w * .12f)
                }
            }
        }
    }
}

@Composable
internal fun MoneyValue(label: String, value: String, emphasis: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            value,
            textAlign = TextAlign.End,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (emphasis) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun CompactRecord(
    label: String,
    amount: String = "",
    photo: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (photo.isNotEmpty()) {
                val image = remember(photo) { logoImage(photo) }
                if (image != null)
                    Image(image.asImageBitmap(), "Miniatura del producto", Modifier.size(36.dp))
                else Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            }
            // Una sola línea, desplazable para consultar nombres largos sin aumentar la altura.
            Text(
                label,
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                maxLines = 1,
                softWrap = false,
                fontSize = 13.sp,
            )
            if (amount.isNotEmpty())
                Text(
                    amount,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            actions()
        }
    }
}

@Composable
internal fun ReportDate(label: String, value: String, onChange: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.width(220.dp),
        placeholder = { Text("AAAA-MM-DD") },
        trailingIcon = {
            ActionIcon("calendar", "Calendario $label") {
                val initial = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                DatePickerDialog(
                        context,
                        { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
                        initial.year,
                        initial.monthValue - 1,
                        initial.dayOfMonth,
                    )
                    .show()
            }
        },
    )
}

@Composable
internal fun TicketPreview(text: String, width: Int = 32) {
    Text(
        Ticket.render(text, width),
        Modifier.horizontalScroll(rememberScrollState()),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        softWrap = false,
    )
}

@Composable
internal fun PaymentRecord(
    payment: Payment,
    index: Int,
    enabled: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val usd = payment.currency == "USD"
    val reference = "(${currency(payment.pesos)} MXN)"
    val amount = "${currency(payment.received)} ${payment.currency}"
    val label =
        "${payment.methodName} · ${payment.person.ifBlank { "Pago ${index+1}" }} · Propina ${currency(payment.tip)} ${payment.currency}"
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (usd && maxWidth < 480.dp) {
            Surface(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column {
                    CompactRecord(label) {
                        ActionIcon("edit", "Editar pago", enabled, onEdit)
                        ActionIcon("delete", "Quitar pago", enabled, onRemove)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 10.dp, end = 12.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text("$reference  ", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        Text(
                            amount,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        } else {
            CompactRecord(label, if (usd) "$reference $amount" else amount) {
                ActionIcon("edit", "Editar pago", enabled, onEdit)
                ActionIcon("delete", "Quitar pago", enabled, onRemove)
            }
        }
    }
}
