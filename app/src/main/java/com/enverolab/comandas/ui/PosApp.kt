@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.enverolab.comandas.ui

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.enverolab.comandas.domain.*
import com.enverolab.comandas.io.PdfPrinter
/* */
@Composable
fun PosApp(vm: PosViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val drafts = rememberSaveableStateHolder()
    var screen by rememberSaveable { mutableStateOf("Menú") }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var payId by rememberSaveable { mutableStateOf<Long?>(null) }
    val colors = originalColors(androidx.compose.foundation.isSystemInDarkTheme())
    MaterialTheme(colorScheme = colors, shapes = originalShapes()) {
        Scaffold(modifier = Modifier.fillMaxSize().imePadding()) { padding ->
            FloatingNotice(vm, state)
            BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                val compactNavigation = maxWidth < 840.dp
                Column(Modifier.fillMaxSize()) {
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("app-busy"))
                    if (state.user == null) Login(vm, state.busy)
                    else {
                        val admin = state.user!!.text("role") in listOf("admin", "super_cajero")
                        if (compactNavigation) {
                            CompactNavigation(
                                state.user!!,
                                screen,
                                admin,
                                !state.busy,
                                { name ->
                                    screen = name
                                    editing = false
                                    payId = null
                                },
                                {
                                    vm.logout()
                                    screen = "Menú"
                                    editing = false
                                    payId = null
                                },
                            )
                        }
                        Row(Modifier.weight(1f).fillMaxWidth()) {
                            if (!compactNavigation)
                                OriginalNavigation(
                                    state.user!!,
                                    screen,
                                    admin,
                                    !state.busy,
                                    logoPath = state.company.text("logo"),
                                    companyName = state.company.text("nombre"),
                                    onSelect = { name ->
                                        screen = name
                                        editing = false
                                        payId = null
                                    },
                                    onLogout = {
                                        vm.logout()
                                        screen = "Menú"
                                        editing = false
                                        payId = null
                                    },
                                )
                            Box(Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
                                if (editing)
                                    RestoredMenu(
                                        vm,
                                        state,
                                        editId,
                                        onOpenOrders = {
                                            editing = false
                                            screen = "Comandas"
                                        },
                                    ) {
                                        editing = false
                                        screen = "Comandas"
                                    }
                                else if (payId != null)
                                    PaymentScreen(vm, state, payId!!) {
                                        payId = null
                                        screen = "Comandas"
                                    }
                                else
                                    when (screen) {
                                        "Menú" ->
                                            drafts.SaveableStateProvider("menu") {
                                                RestoredMenu(
                                                    vm,
                                                    state,
                                                    null,
                                                    onOpenOrders = { screen = "Comandas" },
                                                ) {
                                                    drafts.removeState("menu")
                                                    screen = "Comandas"
                                                }
                                            }
                                        "Comandas" ->
                                            RestoredOrders(
                                                vm,
                                                state,
                                                onEdit = {
                                                    editId = it
                                                    editing = true
                                                },
                                                onPay = { payId = it },
                                                onNewOrder = { screen = "Menú" },
                                            )
                                        "Inventario" -> Inventory(vm, state)
                                        "Reportes" -> Reports(vm, state)
                                        "Historial" -> History(vm, state)
                                        "Catálogos" -> Catalogs(vm, state, false)
                                        "Permisos" -> Catalogs(vm, state, true)
                                        "Configuración" -> Settings(vm, state)
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactNavigation(
    user: Row,
    screen: String,
    admin: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            BrandLogo(Modifier.size(36.dp).padding(end = 6.dp))
            Column(Modifier.weight(1f)) {
                Text("COMANDAS", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${user.text("username")} • $screen",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box {
                TextButton(onClick = { expanded = true }, enabled = enabled) { Text("Secciones") }
                DropdownMenu(expanded, { expanded = false }) {
                    (listOf(
                            "Menú",
                            "Comandas",
                            "Inventario",
                            "Reportes",
                            "Historial",
                            "Configuración",
                        ) + if (admin) listOf("Permisos", "Catálogos") else emptyList())
                        .forEach { name ->
                            DropdownMenuItem(
                                modifier = Modifier.testTag("nav-$name"),
                                text = { Text(name) },
                                onClick = {
                                    expanded = false
                                    onSelect(name)
                                },
                            )
                        }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Cerrar sesión") },
                        onClick = {
                            expanded = false
                            onLogout()
                        },
                    )
                }
            }
        }
    }
}

/** Uses actual content width, including split-screen; compact panes stay full width. */
@Composable
private fun AdaptivePanes(
    modifier: Modifier,
    firstTitle: String,
    secondTitle: String,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 720.dp) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selected) {
                    Tab(selected == 0, onClick = { selected = 0 }, text = { Text(firstTitle) })
                    Tab(selected == 1, onClick = { selected = 1 }, text = { Text(secondTitle) })
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (selected == 0) first(Modifier.fillMaxSize())
                    else second(Modifier.fillMaxSize())
                }
            }
        } else {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.weight(1f).fillMaxHeight())
                second(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Keeps lazy lists bounded while allowing short windows/keyboard to scroll to actions. */
@Composable
internal fun ScreenBody(
    minHeight: androidx.compose.ui.unit.Dp = 600.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bodyHeight = maxHeight.coerceAtLeast(minHeight)
        Column(
            Modifier.fillMaxSize().testTag("screen-scroll").verticalScroll(rememberScrollState())
        ) {
            Column(Modifier.fillMaxWidth().height(bodyHeight), content = content)
        }
    }
}

@Composable
internal fun DialogBody(content: @Composable ColumnScope.() -> Unit) {
    val height = (LocalConfiguration.current.screenHeightDp - 200).coerceIn(96, 430).dp
    Column(Modifier.heightIn(max = height).verticalScroll(rememberScrollState()), content = content)
}

@Composable
private fun Login(vm: PosViewModel, busy: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = viewportHeight).padding(24.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                OutlinedCard(
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().testTag("login-card"),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        BrandLogo(Modifier.size(72.dp))
                        Text("COMANDAS", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Acceso al punto de venta",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            name,
                            { name = it },
                            label = { Text("Usuario") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            password,
                            { password = it },
                            label = { Text("Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                vm.login(name.trim(), password)
                                password = ""
                            },
                            enabled = !busy && name.isNotBlank() && password.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Entrar")
                        }
                        Text(
                            "Ingresa con tu usuario para poder continuar",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Title(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
internal fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        modifier = modifier.padding(3.dp),
        singleLine = true,
    )
}

@Composable
internal fun Selector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box(modifier.padding(3.dp)) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.widthIn(max = 320.dp)) {
            Text(
                "$label: ${options.firstOrNull { it.first==value }?.second ?: value.ifEmpty { "Seleccionar" }}"
            )
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun loadRows(key: Any, block: () -> List<Row>): List<Row> {
    val result by
        produceState(emptyList<Row>(), key) { value = withContext(Dispatchers.IO) { block() } }
    return result
}

@Composable
private fun RowCard(row: Row, onClick: (() -> Unit)? = null) {
    Card(
        Modifier.fillMaxWidth()
            .padding(vertical = 3.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Text(
            row.entries
                .filter { it.key !in listOf("password", "foto") }
                .joinToString("   •   ") { "${it.key}: ${it.value ?: "—"}" },
            Modifier.padding(12.dp),
        )
    }
}

internal fun rowLabel(row: Row) =
    listOf("nombre", "name", "username", "numero").firstNotNullOfOrNull { row[it]?.toString() }
        ?: row.id().toString()

@Composable
private fun Photo(path: String) {
    AndroidView(
        factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
        update = {
            val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opt)
            opt.inSampleSize = maxOf(1, maxOf(opt.outWidth, opt.outHeight) / 160)
            opt.inJustDecodeBounds = false
            it.setImageBitmap(BitmapFactory.decodeFile(path, opt))
        },
        modifier = Modifier.size(72.dp).padding(end = 8.dp),
    )
}

@Composable
internal fun LineDialog(
    original: Line,
    onDismiss: () -> Unit,
    onSave: (Line) -> Unit,
    extra: @Composable () -> Unit = {},
    onValidationError: (String) -> Unit = {},
) {
    var quantity by rememberSaveable { mutableStateOf(original.quantity.toPlainString()) }
    var discount by rememberSaveable {
        mutableStateOf(
            if (original.discountPercent.signum() > 0) original.discountPercent.toPlainString()
            else original.discountAmount.toPlainString()
        )
    }
    var discountType by rememberSaveable {
        mutableStateOf(if (original.discountPercent.signum() > 0) "%" else "$")
    }
    var error by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
        onDismissRequest = onDismiss,
        title = { Text(original.name) },
        text = {
            DialogBody {
                extra()
                Text("Precio ${currency(original.price)}")
                Field("Cantidad", quantity, { quantity = it })
                Selector(
                    "Descuento artículo",
                    discountType,
                    listOf("$" to "MXN", "%" to "%"),
                    { discountType = it },
                )
                Field("Valor", discount, { discount = it })
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                            val q = amount(quantity)
                            val d = amount(discount)
                            require(q > BigDecimal.ZERO && d >= BigDecimal.ZERO)
                            val reduction =
                                if (discountType == "%")
                                    money(original.price * q * d / BigDecimal(100))
                                else d
                            require(reduction <= original.price * q) { "Descuento supera importe" }
                            onSave(
                                original.copy(
                                    quantity = q,
                                    discountAmount = reduction,
                                    discountPercent =
                                        if (discountType == "%") d else BigDecimal.ZERO,
                                )
                            )
                        }
                        .onFailure {
                            error = it.message.orEmpty()
                            onValidationError(error)
                        }
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun PaymentScreen(vm: PosViewModel, state: State, orderId: Long, onDone: () -> Unit) {
    var order by remember { mutableStateOf<Row?>(null) }
    var lines by remember { mutableStateOf(emptyList<Line>()) }
    var payments by
        rememberSaveable(orderId, stateSaver = PaymentsSaver) {
            mutableStateOf(emptyList<Payment>())
        }
    var addPayment by rememberSaveable { mutableStateOf(false) }
    var split by rememberSaveable { mutableStateOf("1") }
    var receipt by rememberSaveable(orderId) { mutableStateOf<String?>(null) }
    var editedPaymentIndex by rememberSaveable { mutableIntStateOf(-1) }
    LaunchedEffect(orderId) {
        withContext(Dispatchers.IO) {
            order = vm.repo.order(orderId)
            lines = vm.repo.lines(orderId)
        }
    }
    val row =
        order
            ?: run {
                CircularProgressIndicator()
                return
            }
    val total = Totals.total(lines, row.text("discount_type"), row.decimal("general_discount"))
    val received = payments.fold(BigDecimal.ZERO) { a, p -> a + p.pesos }
    val missing = (total - received).max(BigDecimal.ZERO)
    RestoredPaymentLayout(row, lines, state.company) {
        ScreenBody {
            Row {
                Text(
                    "Cobrar comanda ${row.id("folio_comanda")}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                TextButton(onClick = onDone, enabled = !state.busy) { Text("Volver") }
            }
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    MoneyValue("Total (MXN)", currency(total), true)
                    val rate = state.company.decimal("tipo_cambio")
                    MoneyValue(
                        "Total (USD)",
                        if (rate.signum() > 0) currency(total.divide(rate, 2, RoundingMode.HALF_UP))
                        else "Sin tipo de cambio",
                        true,
                    )
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    MoneyValue("Recibido (MXN)", currency(received))
                    MoneyValue("Falta (MXN)", currency(missing))
                    MoneyValue("Cambio (MXN)", currency(Totals.change(total, payments)), true)
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { addPayment = true }, enabled = !state.busy) {
                    Text("Agregar pago / asignar artículos")
                }
                Field("Personas", split, { split = it }, Modifier.width(115.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                                check(payments.isEmpty()) { "Quite los pagos antes de dividir" }
                                val method =
                                    state.methods.firstOrNull {
                                        it.decimal("factor").compareTo(BigDecimal.ONE) == 0
                                    } ?: error("Configure un método MXN")
                                payments =
                                    Totals.split(total, split.toIntOrNull() ?: 0).mapIndexed {
                                        index,
                                        value ->
                                        Payment(
                                            method.id(),
                                            method.text("nombre"),
                                            value,
                                            BigDecimal.ONE,
                                            person = "Persona ${index+1}",
                                        )
                                    }
                            }
                            .onFailure { vm.message(it.message.orEmpty()) }
                    },
                    enabled = !state.busy,
                ) {
                    Text("Dividir en partes iguales")
                }
            }
            Text(
                "Cada pago permite elegir método, propina, persona y cantidades de artículos. Los pagos no se registran hasta confirmar."
            )
            LazyColumn(Modifier.weight(1f)) {
                items(payments.indices.toList()) { index ->
                    val p = payments[index]
                    PaymentRecord(
                        p,
                        index,
                        !state.busy,
                        onEdit = { editedPaymentIndex = index },
                        onRemove = { payments = payments.filterIndexed { i, _ -> i != index } },
                    )
                }
            }
            Button(
                onClick = {
                    vm.run("Venta registrada") {
                        val saleId = vm.repo.pay(orderId, payments)
                        val text = vm.repo.receipt(saleId)
                        withContext(Dispatchers.Main) { receipt = text }
                    }
                },
                enabled =
                    !state.busy &&
                        receipt == null &&
                        payments.isNotEmpty() &&
                        (missing.signum() == 0 || payments.any { it.complimentary }),
            ) {
                Text("Confirmar cobro")
            }
        }
    }
    if (addPayment)
        PaymentDialog(vm, state.methods, lines, total, missing, null, { addPayment = false }) {
            payments = payments + it
            addPayment = false
        }
    payments.getOrNull(editedPaymentIndex)?.let { p ->
        PaymentDialog(
            vm,
            state.methods,
            lines,
            total,
            p.received,
            p,
            { editedPaymentIndex = -1 },
        ) { updated ->
            payments =
                payments.mapIndexed { index, old ->
                    if (index == editedPaymentIndex) updated else old
                }
            editedPaymentIndex = -1
        }
    }
    receipt?.let { text ->
        AlertDialog(
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
            onDismissRequest = onDone,
            title = { Text("Venta registrada") },
            text = {
                DialogBody {
                    TicketPreview(text, vm.printer.columns())
                    PrintAction(vm, state, { text })
                    ExportPdf(vm, state, "Ticket", text)
                }
            },
            confirmButton = { TextButton(onClick = onDone) { Text("Finalizar") } },
        )
    }
}

@Composable
private fun PaymentDialog(
    vm: PosViewModel,
    methods: List<Row>,
    lines: List<Line>,
    total: BigDecimal,
    suggested: BigDecimal,
    initial: Payment?,
    onDismiss: () -> Unit,
    onSave: (Payment) -> Unit,
) {
    var methodId by
        rememberSaveable(initial) {
            mutableStateOf(
                initial?.methodId?.toString() ?: methods.firstOrNull()?.id()?.toString().orEmpty()
            )
        }
    fun suggestion(pesos: BigDecimal, method: Row?): String {
        if (pesos.signum() < 0) return ""
        if (method?.text("nombre").orEmpty().lowercase().contains("gratis")) return "0.00"
        val factor = method?.decimal("factor") ?: BigDecimal.ONE
        return if (factor.signum() > 0) TenderAmounts.suggested(pesos, factor).toPlainString()
        else ""
    }
    var automaticAmount by rememberSaveable(initial) { mutableStateOf(initial == null) }
    var received by
        rememberSaveable(initial) {
            mutableStateOf(
                initial?.received?.toPlainString() ?: suggestion(suggested, methods.firstOrNull())
            )
        }
    var tip by rememberSaveable(initial) { mutableStateOf(initial?.tip?.toPlainString() ?: "0") }
    var tipType by rememberSaveable { mutableStateOf("$") }
    var person by rememberSaveable(initial) { mutableStateOf(initial?.person.orEmpty()) }
    var itemMode by rememberSaveable { mutableStateOf(initial?.assignments?.isNotEmpty() ?: false) }
    var quantities by
        rememberSaveable(initial, stateSaver = QuantityMapSaver) {
            mutableStateOf(
                lines.associate {
                    it.id to (initial?.assignments?.get(it.id)?.toPlainString() ?: "0")
                }
            )
        }
    var error by rememberSaveable { mutableStateOf("") }
    val method = methods.firstOrNull { it.id().toString() == methodId }
    val factor = method?.decimal("factor") ?: BigDecimal.ONE
    val methodName = method?.text("nombre").orEmpty().lowercase()
    val isUsd =
        methodName.contains("dolar") || methodName.contains("dólar") || methodName.contains("usd")
    val subtotal = lines.fold(BigDecimal.ZERO) { a, l -> a + l.net }
    val assignedNet =
        lines.fold(BigDecimal.ZERO) { a, l ->
            val q = quantities[l.id]?.toBigDecimalOrNull() ?: BigDecimal.ZERO
            a +
                if (l.quantity.signum() > 0)
                    l.net.multiply(q).divide(l.quantity, 8, RoundingMode.HALF_UP)
                else BigDecimal.ZERO
        }
    val assignedTotal =
        money(
            if (subtotal.signum() > 0)
                assignedNet.multiply(total).divide(subtotal, 8, RoundingMode.HALF_UP)
            else BigDecimal.ZERO
        )
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
        onDismissRequest = onDismiss,
        title = { Text("Pago") },
        text = {
            DialogBody {
                Selector(
                    "Método",
                    methodId,
                    methods.map {
                        it.id().toString() to "${it.text("nombre")} (factor ${it.text("factor")})"
                    },
                    { selected ->
                        val next = methods.firstOrNull { it.id().toString() == selected }
                        val base =
                            if (automaticAmount) suggested
                            else runCatching { amount(received) }.getOrNull()?.multiply(factor)
                        received = if (base != null) suggestion(base, next) else ""
                        val nextFactor = next?.decimal("factor") ?: BigDecimal.ZERO
                        if (tipType == "$" && factor.signum() > 0 && nextFactor.signum() > 0) {
                            runCatching { amount(tip) }
                                .getOrNull()
                                ?.let {
                                    tip =
                                        it.multiply(factor)
                                            .divide(nextFactor, 2, RoundingMode.HALF_UP)
                                            .toPlainString()
                                }
                        }
                        methodId = selected
                        error = ""
                    },
                )
                Text(
                    if (isUsd) "Importes en USD • factor ${factor.toPlainString()}"
                    else "Importes en MXN"
                )
                OutlinedTextField(
                    received,
                    {
                        received = it
                        automaticAmount = false
                        error = ""
                    },
                    label = { Text("Monto recibido, sin propina") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("payment-amount"),
                    suffix = { Text(if (isUsd) "USD" else "MXN") },
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                        ),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                received = ""
                                automaticAmount = false
                                error = ""
                            },
                            modifier =
                                Modifier.testTag("clear-payment-amount").semantics {
                                    contentDescription = "Limpiar monto recibido"
                                },
                        ) {
                            Text("×")
                        }
                    },
                )
                if (isUsd)
                    Text(
                        "El sugerido cubre el saldo; cualquier excedente se refleja como cambio.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                Selector(
                    "Propina",
                    tipType,
                    listOf("$" to "Importe", "%" to "% del pago"),
                    { tipType = it },
                )
                Field("Valor propina", tip, { tip = it })
                Field("Persona", person, { person = it })
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(itemMode, { itemMode = it })
                    Text("Dividir por artículos y cantidades")
                }
                if (itemMode) {
                    lines.forEach { line ->
                        Field(
                            "${line.name} (máx. ${line.quantity})",
                            quantities[line.id].orEmpty(),
                            { quantities = quantities + (line.id to it) },
                        )
                    }
                    Text("Parte asignada con descuento general: ${currency(assignedTotal)} MXN")
                    TextButton(
                        onClick = {
                            received = suggestion(assignedTotal, method)
                            automaticAmount = false
                        }
                    ) {
                        Text("Usar importe asignado")
                    }
                }
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching {
                            val m = method ?: error("Seleccione método")
                            require(
                                m.decimal("factor").signum() > 0 ||
                                    m.text("nombre").lowercase().contains("gratis")
                            ) {
                                "Configure un factor de pago mayor que cero"
                            }
                            require(received.isNotBlank()) { "Capture el monto recibido" }
                            val r = amount(received)
                            val t = amount(tip)
                            require(r >= BigDecimal.ZERO && t >= BigDecimal.ZERO)
                            val assignments =
                                if (itemMode)
                                    quantities
                                        .mapValues { amount(it.value) }
                                        .filterValues { it > BigDecimal.ZERO }
                                else emptyMap()
                            assignments.forEach { (id, q) ->
                                require(q <= lines.first { it.id == id }.quantity) {
                                    "Cantidad excede comanda"
                                }
                            }
                            onSave(
                                Payment(
                                    m.id(),
                                    m.text("nombre"),
                                    r,
                                    m.decimal("factor"),
                                    if (tipType == "%") money(r * t / BigDecimal(100)) else t,
                                    if (isUsd) "USD" else "MXN",
                                    person,
                                    assignments,
                                )
                            )
                        }
                        .onFailure {
                            error = it.message.orEmpty()
                            vm.message(error)
                        }
                }
            ) {
                Text("Agregar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Catalogs(vm: PosViewModel, state: State, usersOnly: Boolean) {
    var table by
        rememberSaveable(usersOnly) { mutableStateOf(if (usersOnly) "users" else "productos") }
    var search by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable(stateSaver = NullableRowSaver) { mutableStateOf<Row?>(null) }
    var create by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable(stateSaver = NullableRowSaver) { mutableStateOf<Row?>(null) }
    val rows = loadRows("$table:${state.revision}") { vm.repo.catalog(table) }
    ScreenBody {
        Title(if (usersOnly) "Usuarios y permisos" else "Catálogos")
        Field("Buscar", search, { search = it }, Modifier.fillMaxWidth())
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!usersOnly)
                Selector(
                    "Catálogo",
                    table,
                    vm.repo.tables.map { it.value to it.key },
                    { table = it },
                )
            Button(onClick = { create = true }, enabled = !state.busy) { Text("Nuevo") }
        }
        if (usersOnly)
            Text(
                "Roles: admin y super_cajero administran catálogos y usuarios; cajero opera el POS. Se conserva el campo permissions del origen."
            )
        LazyColumn {
            items(
                rows.filter {
                    it.values.any { value -> value?.toString()?.contains(search, true) == true }
                }
            ) { row ->
                val classification =
                    when (table) {
                        "productos" ->
                            state.categories
                                .firstOrNull { it.id() == row.id("category_id") }
                                ?.text("name")
                                .orEmpty()
                        "users" -> row.text("role")
                        else -> row.text("categoria").ifEmpty { row.text("status") }
                    }
                val label =
                    listOf("#${row.id()}", row.text("codigo"), rowLabel(row), classification)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                CompactRecord(label, if ("precio" in row) currency(row.decimal("precio")) else "") {
                    ActionIcon("edit", "Editar ${rowLabel(row)}", !state.busy) { editing = row }
                    ActionIcon("delete", "Eliminar ${rowLabel(row)}", !state.busy) {
                        deleting = row
                    }
                }
            }
        }
    }
    if (create || editing != null)
        CatalogDialog(
            vm,
            state,
            table,
            editing,
            {
                create = false
                editing = null
            },
        )
    deleting?.let { row ->
        AlertDialog(
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
            onDismissRequest = { deleting = null },
            title = { Text("Eliminar ${rowLabel(row)}") },
            text = {
                Text(
                    "Los registros relacionados pueden impedir la eliminación. Para productos en uso puede cambiar el estado a No Activo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.run("Registro eliminado") { vm.repo.deleteCatalog(table, row.id()) }
                        deleting = null
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CatalogDialog(
    vm: PosViewModel,
    state: State,
    table: String,
    initial: Row?,
    onDismiss: () -> Unit,
) {
    val columns = loadRows(table) { vm.repo.columns(table) }
    val context = LocalContext.current
    var values by
        rememberSaveable(table, initial?.id(), stateSaver = StringMapSaver) {
            mutableStateOf<Map<String, String>>(
                initial?.mapValues { it.value?.toString().orEmpty() } ?: emptyMap()
            )
        }
    var error by rememberSaveable { mutableStateOf("") }
    val photo =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                vm.run {
                    val file =
                        if (table == "Empresa") vm.files.importLogo(uri)
                        else vm.files.importPhoto(uri)
                    withContext(Dispatchers.Main) {
                        values =
                            values + ((if (table == "Empresa") "logo" else "foto") to file.path)
                    }
                }
        }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success)
                cameraUri?.let { uri ->
                    vm.run {
                        val file =
                            if (table == "Empresa") vm.files.importLogo(uri)
                            else vm.files.importPhoto(uri)
                        withContext(Dispatchers.Main) {
                            values =
                                values + ((if (table == "Empresa") "logo" else "foto") to file.path)
                        }
                    }
                }
        }
    val references =
        loadRows(table + ":references:" + state.revision) {
            listOf("categories", "productos", "clientes", "mesas").flatMap { name ->
                vm.repo.catalog(name).map { it + ("ref_table" to name) }
            }
        }
    AlertDialog(
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo registro" else "Editar ${rowLabel(initial)}") },
        text = {
            DialogBody {
                if (table == "Empresa" && values["logo"].orEmpty().isNotBlank()) {
                    val preview =
                        remember(values["logo"]) {
                            com.enverolab.comandas.io.logoImage(values["logo"].orEmpty())
                        }
                    preview?.let { Image(it.asImageBitmap(), "Logotipo", Modifier.size(100.dp)) }
                    TextButton(onClick = { values = values + ("logo" to "") }) {
                        Text("Quitar logotipo")
                    }
                }
                columns.forEach { column ->
                    val name = column.text("name")
                    val value = values[name].orEmpty()
                    val choices =
                        when (name) {
                            "status" -> listOf("Activo", "No Activo").map { it to it }
                            "role" -> listOf("admin", "super_cajero", "cajero").map { it to it }
                            "categoria" -> listOf("Administrador", "Atencion").map { it to it }
                            "disponibilidad" -> listOf("Disponible", "Ocupada").map { it to it }
                            "category_id" ->
                                references
                                    .filter { it.text("ref_table") == "categories" }
                                    .map { it.id().toString() to rowLabel(it) }
                            "producto_id",
                            "product_id" ->
                                references
                                    .filter { it.text("ref_table") == "productos" }
                                    .map { it.id().toString() to rowLabel(it) }
                            else -> emptyList()
                        }
                    if (choices.isNotEmpty())
                        Selector(
                            name,
                            value.ifEmpty {
                                if (
                                    initial == null &&
                                        name in
                                            listOf("status", "role", "categoria", "disponibilidad")
                                )
                                    when (name) {
                                        "role" -> "cajero"
                                        "categoria" -> "Atencion"
                                        else -> choices.first().first
                                    }
                                else ""
                            },
                            choices,
                            { values = values + (name to it) },
                        )
                    else if (name == "password")
                        OutlinedTextField(
                            value,
                            { values = values + (name to it) },
                            label = {
                                Text(
                                    if (initial != null) "Nueva contraseña (vacío conserva)"
                                    else "Contraseña"
                                )
                            },
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    else
                        Field(
                            name,
                            value,
                            { values = values + (name to it) },
                            Modifier.fillMaxWidth(),
                        )
                    if (name == "foto" || name == "logo")
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TextButton(
                                onClick = { photo.launch("image/*") },
                                enabled = !state.busy,
                            ) {
                                Text(
                                    if (table == "Empresa") "Seleccionar logotipo"
                                    else "Seleccionar foto"
                                )
                            }
                            TextButton(
                                onClick = {
                                    val file =
                                        File(
                                            context.filesDir,
                                            "photos/camera-${System.currentTimeMillis()}.jpg",
                                        )
                                    file.parentFile!!.mkdirs()
                                    cameraUri = vm.files.uri(file)
                                    camera.launch(cameraUri!!)
                                },
                                enabled = !state.busy,
                            ) {
                                Text("Tomar foto")
                            }
                        }
                }
                if (error.isNotEmpty() || state.message.isNotEmpty())
                    Text(error.ifEmpty { state.message }, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    vm.run("Registro guardado") {
                        val defaults =
                            mapOf(
                                "status" to "Activo",
                                "role" to "cajero",
                                "categoria" to "Atencion",
                                "disponibilidad" to "Disponible",
                            )
                        val data =
                            columns.associate { col ->
                                val name = col.text("name")
                                val value =
                                    values[name].orEmpty().ifEmpty {
                                        if (initial == null) defaults[name].orEmpty() else ""
                                    }
                                name to
                                    when {
                                        value.isEmpty() && name == "password" -> ""
                                        value.isEmpty() && (col.id("notnull") == 0L) -> null
                                        col.text("type").contains("INT", true) ->
                                            value.toLongOrNull() ?: error("$name requiere entero")
                                        col.text("type").contains("REAL", true) ->
                                            if (value.isEmpty() && initial == null) {
                                                if (
                                                    name == "factor" ||
                                                        name == "tipo_cambio" ||
                                                        name == "factor_descarga"
                                                )
                                                    BigDecimal.ONE
                                                else BigDecimal.ZERO
                                            } else amount(value)
                                        else -> value
                                    }
                            }
                        vm.repo.saveCatalog(table, initial?.id(), data)
                        withContext(Dispatchers.Main) { onDismiss() }
                    }
                },
                enabled = !state.busy && columns.isNotEmpty(),
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Cancelar") }
        },
    )
}

@Composable
private fun History(vm: PosViewModel, state: State) {
    val rows = loadRows(state.revision) { vm.database.rows("SELECT * FROM sales ORDER BY id DESC") }
    var selected by rememberSaveable(stateSaver = NullableRowSaver) { mutableStateOf<Row?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(selected) {
        if (selected != null)
            text = withContext(Dispatchers.IO) { vm.repo.receipt(selected!!.id()) }
    }
    ScreenBody {
        Title("Historial de ventas")
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { row ->
                CompactRecord(
                    "#${row.id("folio_venta")} · Comanda ${row.id("folio_comanda")} · ${row.text("fecha")} ${row.text("hora")}",
                    currency(row.decimal("monto_pagado")),
                ) {
                    ActionIcon("view", "Ver venta ${row.id("folio_venta")}") { selected = row }
                }
            }
        }
    }
    selected?.let { row ->
        AlertDialog(
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
            onDismissRequest = { selected = null },
            title = { Text("Venta ${row.id("folio_venta")}") },
            text = {
                DialogBody {
                    TicketPreview(text, vm.printer.columns())
                    PrintAction(vm, state, { text })
                    ExportPdf(vm, state, "Ticket", text)
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } },
        )
    }
}

@Composable
private fun Settings(vm: PosViewModel, state: State) {
    val context = LocalContext.current
    val admin = state.user!!.text("role") in listOf("admin", "super_cajero")
    var companyDialog by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Uri?>(null) }
    var width by rememberSaveable { mutableStateOf("32") }
    var cut by rememberSaveable { mutableStateOf(false) }
    if (companyDialog)
        CatalogDialog(vm, state, "Empresa", state.company.takeIf { it.isNotEmpty() }) {
            companyDialog = false
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            pending = uri
        }
    var exportFile by remember { mutableStateOf<File?>(null) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri ->
            if (uri != null)
                vm.run("Respaldo guardado") {
                    val file = exportFile ?: error("Sin respaldo")
                    context.contentResolver.openOutputStream(uri)!!.use { out ->
                        file.inputStream().use { it.copyTo(out) }
                    }
                }
        }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Title("Configuración y respaldos")
        if (admin) CorporateSettings(vm,state)
        if (admin)
            TextButton(onClick = { companyDialog = true }) {
                Text("Datos de la empresa y logotipo")
            }
        Text(
            "La aplicación funciona con datos locales. El respaldo ZIP incluye restaurant.db y las imágenes disponibles."
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(
                onClick = {
                    vm.run {
                        val file = vm.files.backup()
                        withContext(Dispatchers.Main) {
                            exportFile = file
                            export.launch(file.name)
                        }
                    }
                },
                enabled = !state.busy,
            ) {
                Text("Guardar respaldo ZIP")
            }
            TextButton(
                onClick = {
                    vm.run {
                        val file = vm.files.backup()
                        withContext(Dispatchers.Main) {
                            share(context, vm.files.uri(file), "application/zip")
                        }
                    }
                },
                enabled = !state.busy,
            ) {
                Text("Compartir respaldo")
            }
            if (admin)
                TextButton(onClick = { import.launch(arrayOf("*/*")) }, enabled = !state.busy) {
                    Text("Importar ZIP / restaurant.db")
                }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Impresora Bluetooth ESC/POS", style = MaterialTheme.typography.titleMedium)
        Text(
            "Empareje la impresora desde los ajustes de Android. Después utilice Imprimir para seleccionarla."
        )
        Selector(
            "Columnas",
            width,
            listOf("32" to "32 (58 mm)", "42" to "42", "48" to "48 (80 mm)"),
            { width = it },
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Checkbox(cut, { cut = it })
            Text("Enviar orden de corte (si la impresora lo admite)")
        }
        Button(
            onClick = {
                vm.printer.setOptions(width.toInt(), cut)
                vm.message("Opciones guardadas")
            }
        ) {
            Text("Guardar impresión")
        }
        PrintAction(
            vm,
            state,
            {
                "COMANDAS POS\nPrueba de impresión\náéíóú ñ Ñ $\n${LocalDate.now()}\n--------------------"
            },
        )
    }
    pending?.let { uri ->
        AlertDialog(
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
            onDismissRequest = { pending = null },
            title = { Text("Importar datos") },
            text = {
                Text(
                    "Reemplazará los datos actuales. Se conservará una copia previa en almacenamiento privado. La sesión se cerrará al terminar. No desinstale la aplicación original antes de verificar el respaldo."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.run {
                            vm.repo.requireAdmin()
                            val result = vm.files.restore(uri)
                            vm.repo.logout()
                            withContext(Dispatchers.Main) {
                                pending = null
                                vm.message(result)
                            }
                        }
                    },
                    enabled = !state.busy,
                ) {
                    Text("Importar")
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } },
        )
    }
}

internal fun share(context: android.content.Context, uri: Uri, type: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Compartir",
        )
    )
}

@Composable
internal fun ExportPdf(vm: PosViewModel, state: State, title: String, text: String) {
    val context = LocalContext.current
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(
            onClick = {
                vm.run {
                    val file = vm.files.pdf(title, text)
                    withContext(Dispatchers.Main) {
                        share(context, vm.files.uri(file), "application/pdf")
                    }
                }
            },
            enabled = !state.busy,
        ) {
            Text("PDF")
        }
        TextButton(
            onClick = {
                vm.run {
                    val file = vm.files.pdf(title, text)
                    withContext(Dispatchers.Main) { PdfPrinter.print(context, file) }
                }
            },
            enabled = !state.busy,
        ) {
            Text("Imprimir PDF")
        }
    }
}

@Composable
internal fun PrintAction(vm: PosViewModel, state: State, text: () -> String) {
    var devices by remember { mutableStateOf<List<Pair<String, String>>?>(null) }
    var selected by rememberSaveable { mutableStateOf("") }
    fun load() {
        vm.run {
            val result = vm.printer.devices()
            withContext(Dispatchers.Main) {
                devices = result
                selected = result.firstOrNull()?.second.orEmpty()
            }
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) load() else vm.message("Permiso Bluetooth denegado")
        }
    TextButton(
        onClick = { permission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
        enabled = !state.busy,
    ) {
        Text("Imprimir")
    }
    devices?.let { options ->
        AlertDialog(
            properties =
                androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 560.dp),
            onDismissRequest = { devices = null },
            title = { Text("Seleccionar impresora") },
            text = {
                Column {
                    if (options.isEmpty())
                        Text("No hay dispositivos emparejados. Empareje la impresora en Ajustes.")
                    Selector(
                        "Impresora",
                        selected,
                        options.map { it.second to "${it.first} (${it.second})" },
                        { selected = it },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val mac = selected
                        devices = null
                        vm.run("Datos enviados a la impresora") { vm.printer.print(text(), mac) }
                    },
                    enabled = selected.isNotEmpty() && !state.busy,
                ) {
                    Text("Enviar")
                }
            },
            dismissButton = { TextButton(onClick = { devices = null }) { Text("Cancelar") } },
        )
    }
}
