@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.enverolab.comandas.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.enverolab.comandas.domain.*

@Composable
internal fun XlsxAction(
    vm: PosViewModel,
    state: State,
    title: String,
    sheets: Map<String, List<Row>>,
) {
    val context = LocalContext.current
    var staged by remember { mutableStateOf<File?>(null) }
    val save =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
        ) { uri ->
            if (uri != null)
                vm.run("XLSX guardado") {
                    val file = staged ?: error("Genere el archivo nuevamente")
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    } ?: error("No se pudo guardar el archivo")
                }
        }
    TextButton(
        enabled = !state.busy && sheets.isNotEmpty(),
        onClick = {
            vm.run {
                val file = vm.files.xlsx(title, sheets)
                withContext(Dispatchers.Main) {
                    staged = file
                    save.launch(file.name)
                }
            }
        },
    ) {
        Text("Exportar XLSX")
    }
}

internal fun recordLabel(section: String, r: Row): String =
    when (section) {
        "Ventas" -> "${r.text("fecha")} · ${r.text("cantidad")} × ${r.text("producto")}"
        "Detalles comandas" ->
            "#${r.id("folio_comanda")} · ${r.text("cantidad")} × ${r.text("producto")}"
        "Comandas" ->
            "#${r.id("folio_comanda")} · ${r.text("fecha")} · Mesa ${r.id("mesa")} · ${r.text("status")}"
        "Productos" -> "${r.text("cantidad")} × ${r.text("producto")}"
        "Categorías",
        "General" -> "${r.text("categoria")} · ${r.text("cantidad")} uds."
        "Pagos" -> "${r.text("metodo")} · ${r.text("moneda")}"
        "Cierres" -> "#${r.id()} · ${r.text("fecha")} ${r.text("hora")} · ${r.text("tipo")}"
        else -> r.text("producto")
    }

internal fun recordMoney(section: String, r: Row) =
    currency(
        when (section) {
            "Comandas" -> r.decimal("total")
            "Cierres" -> r.decimal("monto_total")
            "Pagos" -> r.decimal("importe_moneda")
            else -> r.decimal("vendido")
        }
    )

@Composable
private fun PaymentSummary(rows: List<Row>) {
    Text(
        "Pagos recibidos por método y moneda",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp),
    )
    rows.forEach { p ->
        CompactRecord(
            "${p.text("metodo")} · ${p.text("moneda")}",
            currency(p.decimal("importe_moneda")),
        )
        if (p.decimal("propina_moneda").signum() > 0)
            MoneyValue(
                "Propina ${p.text("metodo")} (${p.text("moneda")})",
                currency(p.decimal("propina_moneda")),
            )
    }
    if (rows.isEmpty()) Text("Sin pagos en el periodo")
    Text(
        "Recibido puede incluir cambio. Las ventas excluyen propina.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
internal fun Reports(vm: PosViewModel, state: State) {
    val context = LocalContext.current
    var section by rememberSaveable { mutableStateOf("Ventas") }
    var from by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var to by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var start by rememberSaveable { mutableStateOf(from) }
    var end by rememberSaveable { mutableStateOf(to) }
    var records by remember { mutableStateOf(emptyList<Row>()) }
    var data by remember { mutableStateOf(emptyMap<String, List<Row>>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var closeDay by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf("parcial") }
    var notes by rememberSaveable { mutableStateOf("") }
    var general by remember { mutableStateOf(false) }
    var selectedClosure by remember { mutableStateOf<Row?>(null) }
    var selected by remember { mutableStateOf<Row?>(null) }
    var detail by remember { mutableStateOf("") }
    var ticket by remember { mutableStateOf("") }
    LaunchedEffect(section, start, end, state.revision) {
        loading = true
        error = ""
        records = emptyList()
        data = emptyMap()
        ticket = ""
        runCatching {
                withContext(Dispatchers.IO) {
                    Triple(
                        vm.repo.report(section, start, end),
                        vm.repo.detailedReport(start, end),
                        vm.repo.reportTicket(section, start, end),
                    )
                }
            }
            .onSuccess {
                records = it.first
                data = it.second
                ticket = it.third
            }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                error = it.message.orEmpty()
                vm.message(error)
            }
        loading = false
    }
    LaunchedEffect(selected, section) {
        detail = ""
        selected?.let { r ->
            runCatching {
                    withContext(Dispatchers.IO) {
                        when (section) {
                            "Comandas" -> vm.repo.receipt(r.id("sale_id"))
                            "Detalles comandas" -> vm.repo.receipt(r.id("sale_id"))
                            "Ventas" ->
                                vm.repo.salesForProductDay(r.text("fecha"), r.text("producto"))
                            else ->
                                buildString {
                                    appendLine(Ticket.center(section.uppercase()))
                                    appendLine(recordLabel(section, r))
                                    appendLine(Ticket.pair("Importe", recordMoney(section, r)))
                                    if ("cantidad" in r)
                                        appendLine(Ticket.pair("Cantidad", r.text("cantidad")))
                                    if ("moneda" in r) appendLine("Moneda: ${r.text("moneda")}")
                                    listOf(
                                            "propina_moneda" to "Propina",
                                            "total_moneda" to "Total con propina",
                                        )
                                        .forEach { (key, label) ->
                                            if (key in r)
                                                appendLine(
                                                    Ticket.pair(label, currency(r.decimal(key)))
                                                )
                                        }
                                    if ("equivalente_mxn" in r) {
                                        if (r.decimal("pagos_sin_factor").signum() > 0)
                                            appendLine("Equivalencia MXN no disponible")
                                        else
                                            appendLine(
                                                Ticket.pair(
                                                    "Equivalente MXN",
                                                    currency(r.decimal("equivalente_mxn")),
                                                )
                                            )
                                    }
                                }
                        }
                    }
                }
                .onSuccess { detail = it }
                .onFailure {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    detail = it.message.orEmpty()
                    vm.message(detail)
                }
        }
    }
    fun applyRange() {
        runCatching {
                require(!LocalDate.parse(from).isAfter(LocalDate.parse(to))) {
                    "Desde no debe ser posterior a Hasta"
                }
            }
            .onSuccess {
                start = from
                end = to
            }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                error = "Use fechas válidas AAAA-MM-DD. ${it.message.orEmpty()}"
                vm.message(error)
            }
    }
    val summary = data["Resumen"]?.firstOrNull().orEmpty()
    ScreenBody(minHeight = 900.dp) {
        Title("Reportes y cierres")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { closeDay = true },
                enabled = !state.busy,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = PosOrange,
                        contentColor = Color.Black,
                    ),
            ) {
                Text("Realizar corte")
            }
            Button(
                onClick = {
                    selectedClosure = null
                    general = true
                },
                enabled = !loading && data.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PosGreen),
            ) {
                Text("Reporte general / detallado y XLSX")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Selector(
                "Reporte",
                section,
                listOf(
                        "Ventas",
                        "Comandas",
                        "Detalles comandas",
                        "Productos",
                        "Categorías",
                        "Pagos",
                        "Cierres",
                    )
                    .map { it to it },
                { section = it },
            )
            ReportDate("Desde", from) { from = it }
            ReportDate("Hasta", to) { to = it }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { applyRange() }) { Text("Aplicar rango") }
            listOf("Día", "Semana", "Mes").forEach { p ->
                TextButton(
                    onClick = {
                        val now = LocalDate.now()
                        from =
                            when (p) {
                                "Semana" -> now.minusDays((now.dayOfWeek.value - 1).toLong())
                                "Mes" -> now.withDayOfMonth(1)
                                else -> now
                            }.toString()
                        to = now.toString()
                        applyRange()
                    }
                ) {
                    Text(p)
                }
            }
            if (!loading && data.isNotEmpty()) {
                TextButton(
                    onClick = {
                        vm.run {
                            val file = vm.files.csv(section, records)
                            withContext(Dispatchers.Main) {
                                share(context, vm.files.uri(file), "text/csv")
                            }
                        }
                    },
                    enabled = !state.busy,
                ) {
                    Text("CSV")
                }
                PrintAction(vm, state, { ticket })
                ExportPdf(vm, state, section, ticket)
                XlsxAction(
                    vm,
                    state,
                    section,
                    linkedMapOf(
                        "Resumen" to data.getValue("Resumen"),
                        "Registros" to records,
                        "Pagos" to data.getValue("Pagos"),
                    ),
                )
            }
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text("$section · $start a $end", fontWeight = FontWeight.Bold)
                MoneyValue("Total ventas MXN", currency(summary.decimal("total_mxn")), true)
                Text(
                    "${summary.id("ventas")} ventas · Propinas ${currency(summary.decimal("propinas_mxn"))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            if (records.isEmpty() && !loading)
                item {
                    Text("No hay registros en el periodo seleccionado", Modifier.padding(12.dp))
                }
            items(records) { r ->
                CompactRecord(
                    recordLabel(section, r),
                    recordMoney(section, r),
                    if (section == "Productos") r.text("foto").ifBlank { "missing-product" } else "",
                ) {
                    ActionIcon(
                        "view",
                        if (section in listOf("Comandas", "Ventas", "Detalles comandas"))
                            "Ver comanda"
                        else "Ver detalle",
                    ) {
                        if (section == "Cierres") {
                            selectedClosure = r
                            general = true
                        } else selected = r
                    }
                }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 12.dp))
                MoneyValue(
                    "Total ventas del periodo (MXN)",
                    currency(summary.decimal("total_mxn")),
                    true,
                )
                PaymentSummary(data["Pagos"].orEmpty())
            }
        }
    }
    if (general) SalesReportDialog(vm, state, start, end, selectedClosure) { general = false }
    selected?.let {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = {
                Text(
                    if (section in listOf("Comandas", "Detalles comandas"))
                        "Detalle de comanda ${it.id("folio_comanda")}"
                    else "Detalle · $section"
                )
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(12.dp).widthIn(max = 680.dp),
            text = {
                DialogBody {
                    TicketPreview(detail, vm.printer.columns())
                    if (detail.isNotBlank()) {
                        PrintAction(vm, state, { detail })
                        ExportPdf(vm, state, "Detalle", detail)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar ventana") } },
        )
    }
    if (closeDay)
        AlertDialog(
            onDismissRequest = { closeDay = false },
            title = { Text("Cierre del día") },
            text = {
                DialogBody {
                    Selector(
                        "Tipo",
                        type,
                        listOf("parcial" to "Parcial", "total" to "Total"),
                        { type = it },
                    )
                    Field("Observaciones", notes, { notes = it })
                    Text(
                        "Corte acumulado de las ventas de hoy. Un cierre total impide nuevos cortes del día."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.run("Cierre registrado") {
                            val id = vm.repo.closeDay(type, notes)
                            val row =
                                vm.database
                                    .rows("SELECT * FROM cierres WHERE id=?", listOf(id))
                                    .first()
                            withContext(Dispatchers.Main) {
                                closeDay = false
                                section = "Cierres"
                                selectedClosure = row
                                general = true
                            }
                        }
                    },
                    enabled = !state.busy,
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = { TextButton(onClick = { closeDay = false }) { Text("Cancelar") } },
        )
}

@Composable
internal fun SalesReportDialog(
    vm: PosViewModel,
    state: State,
    from: String,
    to: String,
    closure: Row?,
    onClose: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf("General") }
    var data by remember { mutableStateOf(emptyMap<String, List<Row>>()) }
    var ticket by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(from, to, closure, mode, state.revision) {
        data = emptyMap()
        ticket = ""
        error = ""
        runCatching {
                withContext(Dispatchers.IO) {
                    vm.repo.detailedReport(from, to, closure) to
                        vm.repo.reportTicket(
                            if (closure == null) {
                                if (mode == "General") "Categorías" else "Detalles comandas"
                            } else mode,
                            from,
                            to,
                            closure,
                        )
                }
            }
            .onSuccess {
                data = it.first
                ticket = it.second
            }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                error = it.message.orEmpty()
                vm.message(error)
            }
    }
    val keys =
        if (mode == "General") listOf("Resumen", "Categorías", "Pagos")
        else listOf("Resumen", "Comandas", "Detalle", "Pagos")
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (closure == null) "Reporte de ventas" else "Corte #${closure.id()}") },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(12.dp).widthIn(max = 820.dp),
        text = {
            DialogBody {
                Selector(
                    "Vista",
                    mode,
                    listOf(
                        "General" to "General / categorías",
                        "Detalle" to "Detalle / comandas y productos",
                    ),
                    { mode = it },
                )
                Text(
                    if (closure == null) "$from a $to"
                    else "${closure.text("fecha")} ${closure.text("hora")}",
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow {
                    if (ticket.isNotBlank()) PrintAction(vm, state, { ticket })
                    XlsxAction(vm, state, "Corte-$mode", data.filterKeys { it in keys })
                    if (ticket.isNotBlank()) ExportPdf(vm, state, "Corte-$mode", ticket)
                }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                MoneyValue(
                    "Total de ventas MXN",
                    currency(data["Resumen"]?.firstOrNull().orEmpty().decimal("total_mxn")),
                    true,
                )
                if (mode == "General") {
                    Text("Ventas por categoría", style = MaterialTheme.typography.titleMedium)
                    data["Categorías"].orEmpty().forEach { r ->
                        CompactRecord(recordLabel("Categorías", r), recordMoney("Categorías", r))
                    }
                } else {
                    data["Comandas"].orEmpty().forEach { sale ->
                        Text(
                            "Comanda #${sale.id("folio_comanda")} · ${sale.text("fecha")}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        data["Detalle"]
                            .orEmpty()
                            .filter { it.id("sale_id") == sale.id() }
                            .forEach { r ->
                                CompactRecord(
                                    "${r.text("cantidad")} × ${r.text("producto")}",
                                    currency(r.decimal("vendido")),
                                )
                            }
                        MoneyValue(
                            "Total comanda MXN",
                            currency(sale.decimal("monto_pagado")),
                            true,
                        )
                        val payments =
                            loadRows("sale-payments:${sale.id()}:${state.revision}") {
                                vm.repo.paymentsForSale(sale.id())
                            }
                        payments.forEach { p ->
                            MoneyValue(
                                "${p.text("metodo")} (${p.text("moneda")})",
                                currency(p.decimal("monto_pagado")),
                            )
                        }
                    }
                }
                PaymentSummary(data["Pagos"].orEmpty())
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Cerrar ventana") } },
    )
}
