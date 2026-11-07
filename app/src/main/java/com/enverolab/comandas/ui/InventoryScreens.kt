@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.enverolab.comandas.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.enverolab.comandas.domain.*

@Composable
private fun TableCell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text,
        Modifier.width(width).padding(horizontal = 8.dp, vertical = 12.dp),
        maxLines = 1,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

private fun Row.lastInventoryDate(key: String) = text(key).ifBlank { "Sin registro" }

@Composable
internal fun Inventory(vm: PosViewModel, state: State) {
    var search by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var stockDialog by rememberSaveable { mutableStateOf(false) }
    var movementsDialog by rememberSaveable { mutableStateOf(false) }
    var entry by rememberSaveable { mutableStateOf(true) }
    var quantity by rememberSaveable { mutableStateOf("") }
    val products = loadRows("inventory:${state.revision}") { vm.repo.inventoryProducts() }
    val selected = products.firstOrNull { it.id() == selectedId }
    val horizontal = rememberScrollState()
    ScreenBody(minHeight = 700.dp) {
        Title("Inventario")
        Field(
            "Buscar código, nombre o descripción",
            search,
            { search = it },
            Modifier.fillMaxWidth(),
        )
        Text(
            selected?.let { "Seleccionado: ${it.text("codigo")} · ${it.text("nombre")}" }
                ?: "Selecciona un producto para registrar o consultar movimientos."
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    entry = true
                    quantity = ""
                    stockDialog = true
                },
                enabled = selected != null && !state.busy,
            ) {
                Text("Entrada")
            }
            OutlinedButton(
                onClick = {
                    entry = false
                    quantity = ""
                    stockDialog = true
                },
                enabled = selected != null && !state.busy,
            ) {
                Text("Salida")
            }
            Button(
                onClick = { movementsDialog = true },
                enabled = selected != null && !state.busy,
                modifier = Modifier.testTag("inventory-movements"),
            ) {
                Text("Ver movimientos")
            }
        }
        Text(
            "Desliza horizontalmente para consultar todas las columnas.",
            style = MaterialTheme.typography.bodySmall,
        )
        Column(Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontal)) {
            Row(Modifier.width(1120.dp).background(MaterialTheme.colorScheme.primaryContainer)) {
                TableCell("Código", 90.dp, true)
                TableCell("Nombre", 160.dp, true)
                TableCell("Descripción", 220.dp, true)
                TableCell("Existencia", 110.dp, true)
                TableCell("Precio venta", 140.dp, true)
                TableCell("Última entrada", 200.dp, true)
                TableCell("Última salida", 200.dp, true)
            }
            LazyColumn(Modifier.width(1120.dp).weight(1f).testTag("inventory-products")) {
                items(
                    products.filter { r ->
                        listOf("codigo", "nombre", "descripcion").any {
                            r.text(it).contains(search, true)
                        }
                    },
                    key = { it.id() },
                ) { r ->
                    Surface(
                        color =
                            if (r.id() == selectedId) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            Modifier.fillMaxWidth()
                                .testTag("inventory-product-${r.id()}")
                                .clickable { selectedId = r.id() }
                                .semantics { this.selected = r.id() == selectedId }
                        ) {
                            TableCell(r.text("codigo"), 90.dp)
                            TableCell(r.text("nombre"), 160.dp)
                            TableCell(r.text("descripcion"), 220.dp)
                            TableCell(
                                r.decimal("stock").stripTrailingZeros().toPlainString(),
                                110.dp,
                            )
                            TableCell(currency(r.decimal("precio")), 140.dp)
                            TableCell(r.lastInventoryDate("ultima_entrada"), 200.dp)
                            TableCell(r.lastInventoryDate("ultima_salida"), 200.dp)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    if (stockDialog && selected != null)
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
            onDismissRequest = { stockDialog = false },
            title = { Text("${if(entry) "Entrada" else "Salida"}: ${selected.text("nombre")}") },
            text = {
                Column {
                    MoneyValue(
                        "Existencia actual",
                        selected.decimal("stock").stripTrailingZeros().toPlainString(),
                    )
                    Field(
                        "Cantidad",
                        quantity,
                        { quantity = it },
                        Modifier.fillMaxWidth().testTag("inventory-quantity"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !state.busy,
                    onClick = {
                        vm.run("Inventario actualizado") {
                            vm.repo.adjustStock(selected.id(), amount(quantity), entry)
                            withContext(Dispatchers.Main) { stockDialog = false }
                        }
                    },
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = { TextButton(onClick = { stockDialog = false }) { Text("Cancelar") } },
        )
    if (movementsDialog && selected != null)
        InventoryMovementsDialog(vm, state, selected) { movementsDialog = false }
}

private data class MovementResult(
    val rows: List<Row> = emptyList(),
    val ticket: String = "",
    val loading: Boolean = true,
    val error: String = "",
)

@Composable
private fun InventoryMovementsDialog(
    vm: PosViewModel,
    state: State,
    product: Row,
    onClose: () -> Unit,
) {
    var from by
        rememberSaveable(product.id()) { mutableStateOf(LocalDate.now().minusMonths(1).toString()) }
    var to by rememberSaveable(product.id()) { mutableStateOf(LocalDate.now().toString()) }
    var start by rememberSaveable(product.id()) { mutableStateOf(from) }
    var end by rememberSaveable(product.id()) { mutableStateOf(to) }
    var result by remember { mutableStateOf(MovementResult()) }
    var rangeError by remember { mutableStateOf("") }
    LaunchedEffect(product.id(), start, end, state.revision) {
        result = MovementResult()
        rangeError = ""
        runCatching {
                withContext(Dispatchers.IO) {
                    vm.repo.inventoryMovements(product.id(), start, end) to
                        vm.repo.inventoryMovementTicket(product.id(), start, end)
                }
            }
            .onSuccess { result = MovementResult(it.first, it.second, false) }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                result = MovementResult(loading = false, error = it.message.orEmpty())
                vm.message(result.error)
            }
    }
    val rows = result.rows
    val ticket = result.ticket
    val loading = result.loading
    val error = rangeError.ifBlank { result.error }
    val horizontal = rememberScrollState()
    val height = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(200).dp
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.padding(horizontal = 12.dp)
                .widthIn(max = 1000.dp)
                .fillMaxWidth()
                .height(height)
                .testTag("inventory-movements-dialog"),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Movimientos de inventario",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    TextButton(onClick = onClose) { Text("Cerrar ventana") }
                }
                Box(Modifier.weight(1f)) {
                    ScreenBody(minHeight = 700.dp) {
                        Text(
                            "${product.text("codigo")} · ${product.text("nombre")}",
                            fontWeight = FontWeight.Bold,
                        )
                        MoneyValue(
                            "Precio de venta actual MXN",
                            currency(product.decimal("precio")),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReportDate("Desde", from) { from = it }
                            ReportDate("Hasta", to) { to = it }
                            TextButton(
                                onClick = {
                                    runCatching {
                                            require(
                                                !LocalDate.parse(from).isAfter(LocalDate.parse(to))
                                            ) {
                                                "Rango inválido"
                                            }
                                        }
                                        .onSuccess {
                                            start = from
                                            end = to
                                        }
                                        .onFailure {
                                            rangeError =
                                                "Use fechas válidas AAAA-MM-DD y un rango ordenado"
                                            vm.message(rangeError)
                                        }
                                }
                            ) {
                                Text("Aplicar rango")
                            }
                        }
                        FlowRow {
                            if (!loading && ticket.isNotBlank()) {
                                PrintAction(vm, state, { ticket })
                                ExportPdf(
                                    vm,
                                    state,
                                    "Movimientos-${product.text("codigo")}",
                                    ticket,
                                )
                                XlsxAction(
                                    vm,
                                    state,
                                    "Movimientos-${product.id()}",
                                    linkedMapOf(
                                        "Producto" to
                                            listOf(
                                                mapOf(
                                                    "codigo" to product["codigo"],
                                                    "nombre" to product["nombre"],
                                                    "desde" to start,
                                                    "hasta" to end,
                                                    "existencia_actual" to product["stock"],
                                                    "precio_venta_actual" to product["precio"],
                                                )
                                            ),
                                        "Movimientos" to rows,
                                    ),
                                )
                            }
                        }
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        Text(
                            "Periodo aplicado: $start a $end",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Column(Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontal)) {
                            Row(
                                Modifier.width(690.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                TableCell("Fecha y hora", 180.dp, true)
                                TableCell("Movimiento", 120.dp, true)
                                TableCell("Cantidad", 100.dp, true)
                                TableCell("Precio venta actual", 170.dp, true)
                                TableCell("Venta", 120.dp, true)
                            }
                            LazyColumn(
                                Modifier.width(690.dp).weight(1f).testTag("inventory-movement-list")
                            ) {
                                if (rows.isEmpty() && !loading)
                                    item {
                                        Text(
                                            "Sin movimientos en el periodo seleccionado",
                                            Modifier.padding(12.dp),
                                        )
                                    }
                                items(rows, key = { it.id() }) { r ->
                                    Row {
                                        TableCell("${r.text("fecha")} ${r.text("hora")}", 180.dp)
                                        TableCell(
                                            if (r.text("direction") == "entrada") "Entrada"
                                            else "Salida",
                                            120.dp,
                                        )
                                        TableCell(
                                            r.decimal("quantity")
                                                .stripTrailingZeros()
                                                .toPlainString(),
                                            100.dp,
                                        )
                                        TableCell(
                                            currency(r.decimal("precio_venta_actual")),
                                            170.dp,
                                        )
                                        TableCell(
                                            if (r.id("sale_id") > 0) "#${r.id("sale_id")}"
                                            else "Manual",
                                            120.dp,
                                        )
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                        Text(
                            "${rows.size} movimientos · Entradas: ${rows.filter{it.text("direction")=="entrada"}.fold(BigDecimal.ZERO){a,r->a+r.decimal("quantity")}.stripTrailingZeros().toPlainString()} · Salidas: ${rows.filter{it.text("direction")=="salida"}.fold(BigDecimal.ZERO){a,r->a+r.decimal("quantity")}.stripTrailingZeros().toPlainString()}"
                        )
                    }
                }
            }
        }
    }
}
