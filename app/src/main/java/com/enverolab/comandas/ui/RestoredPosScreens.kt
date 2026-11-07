@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.enverolab.comandas.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.enverolab.comandas.R
import com.enverolab.comandas.domain.*

@Composable
internal fun RestoredMenu(
    vm: PosViewModel,
    state: State,
    orderId: Long?,
    onOpenOrders: () -> Unit = {},
    onDone: () -> Unit,
) {
    var loaded by rememberSaveable(orderId) { mutableStateOf(orderId == null) }
    var lines by
        rememberSaveable(orderId, stateSaver = LinesSaver) { mutableStateOf(emptyList<Line>()) }
    var table by rememberSaveable(orderId) { mutableStateOf("") }
    var people by rememberSaveable(orderId) { mutableStateOf("1") }
    var morePeople by rememberSaveable(orderId) { mutableStateOf("") }
    var client by rememberSaveable(orderId) { mutableStateOf("") }
    var discount by rememberSaveable(orderId) { mutableStateOf("0") }
    var discountType by rememberSaveable(orderId) { mutableStateOf("%") }
    var notes by rememberSaveable(orderId) { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableLongStateOf(0) }
    var choices by
        rememberSaveable(stateSaver = QuantityMapSaver) { mutableStateOf(emptyMap<Long, String>()) }
    var editIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var addClient by rememberSaveable { mutableStateOf(false) }
    var showDiscount by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var photoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(orderId) {
        if (orderId != null && !loaded) {
            runCatching {
                    withContext(Dispatchers.IO) { vm.repo.order(orderId) to vm.repo.lines(orderId) }
                }
                .onSuccess { (row, items) ->
                    lines = items
                    table = row.id("mesa").toString()
                    people = row.id("personas").toString()
                    morePeople = people.takeUnless { (it.toIntOrNull() ?: 0) in 1..4 }.orEmpty()
                    client = row.id("id_cliente").takeIf { it > 0 }?.toString().orEmpty()
                    discount = row.decimal("general_discount").toPlainString()
                    discountType = row.text("discount_type").ifBlank { "%" }
                    notes = row.text("observaciones")
                    loaded = true
                }
                .onFailure {
                    vm.message(it.message.orEmpty())
                    onDone()
                }
        }
    }
    if (!loaded) {
        CircularProgressIndicator()
        return
    }
    val subtotal = lines.fold(BigDecimal.ZERO) { a, l -> a + l.net }
    val total =
        runCatching { Totals.total(lines, discountType, amount(discount)) }.getOrDefault(subtotal)
    val rate = state.company.decimal("tipo_cambio")
    fun changeQuantity(index: Int, quantity: BigDecimal) {
        lines =
            if (quantity.signum() <= 0) lines.filterIndexed { i, _ -> i != index }
            else lines.mapIndexed { i, l -> if (i == index) l.withQuantity(quantity) else l }
    }
    val addProduct: (Row, Row?) -> Unit = { product, variant ->
        val index =
            lines.indexOfFirst { it.productId == product.id() && it.variantId == variant?.id() }
        if (index >= 0) changeQuantity(index, lines[index].quantity + BigDecimal.ONE)
        else
            lines =
                lines +
                    Line(
                        productId = product.id(),
                        variantId = variant?.id(),
                        code = product.text("codigo"),
                        name =
                            product.text("nombre") +
                                (variant?.let { " (${it.text("nombre")})" } ?: ""),
                        description = product.text("descripcion"),
                        price = variant?.decimal("precio") ?: product.decimal("precio"),
                    )
    }
    val menu: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenOrders, modifier = Modifier.testTag("quick-orders")) {
                    Text("Ver comandas →")
                }
            }
            ProductGallery(
                state,
                Modifier.weight(1f),
                search,
                { search = it },
                category,
                { category = it },
                choices,
                { id, value -> choices = choices + (id to value) },
                addProduct,
                { photoId = it },
            )
        }
    }
    val summary: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            modifier,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(24.dp),
        ) {
            LazyColumn(
                Modifier.fillMaxSize().testTag("summary-list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (orderId == null) "Nueva comanda" else "Editar comanda",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        TextButton(
                            onClick = onOpenOrders,
                            modifier = Modifier.testTag("summary-quick-orders"),
                        ) {
                            Text("Comandas →")
                        }
                        if (orderId != null)
                            TextButton(onClick = onDone, enabled = !state.busy) { Text("Volver") }
                    }
                }
                item {
                    PosSelector(
                        "Mesa",
                        table,
                        state.tables.map { it.id().toString() to rowLabel(it) },
                        { table = it },
                        "Selecciona una mesa",
                        "table-selector",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PosSelector(
                            "Cliente",
                            client,
                            listOf("" to "Sin cliente") +
                                state.clients.map { it.id().toString() to rowLabel(it) },
                            { client = it },
                            "Selecciona un cliente",
                            "client-selector",
                            Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { addClient = true },
                            enabled = !state.busy,
                            modifier = Modifier.semantics { contentDescription = "Agregar cliente" },
                        ) {
                            Text("+")
                        }
                    }
                }
                item {
                    Text("Personas", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..4).forEach { n ->
                            FilterChip(
                                selected = people == n.toString(),
                                onClick = {
                                    people = n.toString()
                                    morePeople = ""
                                },
                                label = { Text(n.toString()) },
                                modifier = Modifier.testTag("people-$n"),
                            )
                        }
                        OutlinedTextField(
                            morePeople,
                            {
                                morePeople = it
                                people = it
                            },
                            label = { Text("Más…") },
                            singleLine = true,
                            modifier = Modifier.width(92.dp).testTag("people-input"),
                        )
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Artículos (${lines.size})",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                        )
                        if (lines.isNotEmpty())
                            TextButton(onClick = { confirmClear = true }, enabled = !state.busy) {
                                Text("Vaciar")
                            }
                    }
                    if (lines.isEmpty())
                        Text(
                            "Selecciona productos del menú para comenzar.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                }
                itemsIndexed(lines) { index, line ->
                    CartLine(
                        line,
                        index,
                        !state.busy,
                        onQuantity = { changeQuantity(index, it) },
                        onEdit = { editIndex = index },
                        onRemove = { lines = lines.filterIndexed { i, _ -> i != index } },
                    )
                }
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(onClick = { showDiscount = true }) {
                                Text(
                                    if (amount(discount).signum() > 0) "Descuento •"
                                    else "Descuento",
                                    color = PosOrange,
                                )
                            }
                            TextButton(onClick = { showNote = true }) {
                                Text(
                                    if (notes.isNotBlank()) "Nota •" else "Nota",
                                    color = PosOrange,
                                )
                            }
                        }
                    }
                    if (notes.isNotBlank())
                        Text(
                            notes,
                            Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
                item {
                    PosTotals(subtotal, subtotal - total, total, rate)
                    Button(
                        onClick = {
                            vm.run(
                                if (orderId == null) "Orden creada con éxito"
                                else "Orden actualizada"
                            ) {
                                vm.repo.saveOrder(
                                    Draft(
                                        orderId,
                                        table.toLongOrNull() ?: error("Seleccione mesa"),
                                        people.toIntOrNull() ?: error("Personas inválidas"),
                                        client.toLongOrNull(),
                                        discountType,
                                        amount(discount),
                                        notes,
                                        lines,
                                    )
                                )
                                withContext(Dispatchers.Main) { onDone() }
                            }
                        },
                        enabled =
                            !state.busy &&
                                lines.isNotEmpty() &&
                                table.isNotBlank() &&
                                (people.toIntOrNull() ?: 0) > 0,
                        modifier =
                            Modifier.fillMaxWidth().padding(top = 12.dp).testTag("save-order"),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = PosOrange,
                                contentColor = Color.Black,
                            ),
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        Text(
                            if (orderId == null) "Continuar" else "Guardar cambios",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    OriginalPanes("Menú", "Resumen (${lines.size})", 5f, 3f, menu, summary)
    editIndex?.let { index ->
        lines.getOrNull(index)?.let { original ->
            LineDialog(
                original,
                { editIndex = null },
                { updated ->
                    lines = lines.mapIndexed { i, l -> if (i == index) updated else l }
                    editIndex = null
                },
                onValidationError = { vm.message(it) },
            )
        }
    }
    if (addClient)
        QuickClientDialog(vm, state.busy, { addClient = false }) {
            client = it.toString()
            addClient = false
        }
    if (showDiscount)
        GeneralDiscountDialog(discountType, discount, subtotal, { showDiscount = false }) {
            type,
            value ->
            discountType = type
            discount = value
            showDiscount = false
        }
    if (showNote)
        NoteDialog("Agregar nota", notes, { showNote = false }) {
            notes = it
            showNote = false
        }
    photoId?.let { id -> ProductPhotoDialog(vm, id, { photoId = null }) }
    if (confirmClear)
        PosDialog(
            "Vaciar comanda",
            { confirmClear = false },
            { Text("Se quitarán los artículos del borrador actual.") },
            "Vaciar",
            {
                lines = emptyList()
                discount = "0"
                notes = ""
                confirmClear = false
            },
        )
}

@Composable
private fun OriginalPanes(
    firstTitle: String,
    secondTitle: String,
    firstWeight: Float,
    secondWeight: Float,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    secondRequestedKey: Any? = null,
    initialTab: Int = 0,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    LaunchedEffect(secondRequestedKey) { if (secondRequestedKey != null) tab = 1 }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 900.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                first(Modifier.weight(firstWeight).fillMaxHeight())
                second(Modifier.weight(secondWeight).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                TabRow(tab, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    listOf(firstTitle, secondTitle).forEachIndexed { i, text ->
                        Tab(
                            tab == i,
                            { tab = i },
                            text = { Text(text, maxLines = 1) },
                            modifier = Modifier.testTag(if (i == 0) "pane-first" else "pane-second"),
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (tab == 0) first(Modifier.fillMaxSize()) else second(Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun ProductGallery(
    state: State,
    modifier: Modifier,
    search: String,
    onSearch: (String) -> Unit,
    category: Long,
    onCategory: (Long) -> Unit,
    choices: Map<Long, String>,
    onChoice: (Long, String) -> Unit,
    onAdd: (Row, Row?) -> Unit,
    onPhoto: (Long) -> Unit,
) {
    val products =
        state.products.filter { p ->
            (category == 0L || category == p.id("category_id")) &&
                "${p.text("nombre")} ${p.text("descripcion")} ${p.text("codigo")}"
                    .contains(search, true)
        }
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val greeting: @Composable (Modifier) -> Unit = { m ->
                Column(m.padding(vertical = 8.dp)) {
                    Text(
                        "Bienvenido, ${state.user?.text("username").orEmpty()}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Descubre fácilmente todo lo que necesitas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val searchBox: @Composable (Modifier) -> Unit = { m ->
                OutlinedTextField(
                    search,
                    onSearch,
                    placeholder = { Text("Buscar…") },
                    singleLine = true,
                    shape = RoundedCornerShape(32.dp),
                    trailingIcon = {
                        if (search.isNotEmpty()) TextButton({ onSearch("") }) { Text("×") }
                    },
                    modifier =
                        m.testTag("product-search").semantics {
                            contentDescription = "Buscar producto"
                        },
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                )
            }
            if (maxWidth >= 560.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    greeting(Modifier.weight(1f))
                    searchBox(Modifier.weight(1f))
                }
            else
                Column {
                    greeting(Modifier.fillMaxWidth())
                    searchBox(Modifier.fillMaxWidth())
                }
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(listOf(0L to "Todo") + state.categories.map { it.id() to it.text("name") }) {
                (id, name) ->
                FilterChip(
                    category == id,
                    { onCategory(id) },
                    label = { Text(name) },
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier.testTag("category-$id"),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PosOrange,
                            selectedLabelColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
            }
        }
        if (products.isEmpty())
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No hay productos")
            }
        else
            LazyVerticalGrid(
                GridCells.Adaptive(156.dp),
                Modifier.weight(1f).testTag("product-grid"),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(products, key = { it.id() }) { p ->
                    val variants = state.variants.filter { it.id("producto_id") == p.id() }
                    val selected =
                        variants.firstOrNull { it.id().toString() == choices[p.id()] }
                            ?: variants.firstOrNull {
                                it.text("nombre").trim().equals("Botella", ignoreCase = true)
                            }
                            ?: variants.firstOrNull()
                    val add = { if (!state.busy) onAdd(p, selected) }
                    Card(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 420.dp)
                            .combinedClickable(
                                enabled = !state.busy,
                                onClick = add,
                                onLongClick = { onPhoto(p.id()) },
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                        elevation = CardDefaults.cardElevation(3.dp),
                    ) {
                        ProductPicture(
                            p.text("foto"),
                            Modifier.fillMaxWidth().height(120.dp).padding(8.dp),
                        )
                        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                            Text(
                                p.text("nombre"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                p.text("descripcion"),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(currency(p.decimal("precio")), fontSize = 14.sp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                variants.forEach { v ->
                                    FilterChip(
                                        selected?.id() == v.id(),
                                        { onChoice(p.id(), v.id().toString()) },
                                        label = {
                                            Text(
                                                "${v.text("nombre")} - ${currency(v.decimal("precio"))}",
                                                fontSize = 11.sp,
                                            )
                                        },
                                        modifier = Modifier.testTag("variant-${v.id()}"),
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor =
                                                    MaterialTheme.colorScheme.primaryContainer
                                            ),
                                    )
                                }
                            }
                            TextButton(
                                add,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth().testTag("product-add-${p.id()}"),
                            ) {
                                Text(
                                    "Agregar ${selected?.let { currency(it.decimal("precio")) } ?: currency(p.decimal("precio"))}"
                                )
                            }
                            TextButton(
                                { onPhoto(p.id()) },
                                modifier =
                                    Modifier.fillMaxWidth().semantics {
                                        contentDescription = "Foto de ${p.text("nombre")}"
                                    },
                            ) {
                                Text("Foto", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun ProductPicture(path: String, modifier: Modifier) {
    if (!File(path).isFile)
        Image(
            painterResource(R.drawable.product_placeholder),
            null,
            modifier,
            contentScale = ContentScale.Fit,
        )
    else
        AndroidView(
            factory = { context ->
                ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            },
            update = { view ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                options.inSampleSize = maxOf(1, maxOf(options.outWidth, options.outHeight) / 360)
                options.inJustDecodeBounds = false
                view.setImageBitmap(BitmapFactory.decodeFile(path, options))
            },
            modifier = modifier,
        )
}

@Composable
private fun CartLine(
    line: Line,
    index: Int,
    enabled: Boolean,
    onQuantity: (BigDecimal) -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(line.name, fontWeight = FontWeight.SemiBold)
            if (line.description.isNotBlank())
                Text(line.description, style = MaterialTheme.typography.bodySmall)
            Text(
                "${line.code} • Unit. ${currency(line.price)}",
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    { onQuantity(line.quantity - BigDecimal.ONE) },
                    enabled = enabled,
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.width(48.dp).testTag("line-minus-$index"),
                ) {
                    Text("−")
                }
                Text(
                    line.quantity.stripTrailingZeros().toPlainString(),
                    Modifier.padding(12.dp),
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    { onQuantity(line.quantity + BigDecimal.ONE) },
                    enabled = enabled,
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.width(48.dp).testTag("line-plus-$index"),
                ) {
                    Text("+")
                }
                Text(
                    currency(line.net),
                    Modifier.padding(vertical = 12.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (line.discountAmount.signum() > 0)
                Text("Descuento: −${currency(line.discountAmount)}", color = PosGreen)
            FlowRow {
                TextButton(
                    onEdit,
                    enabled = enabled,
                    modifier = Modifier.testTag("line-edit-$index"),
                ) {
                    Text("Editar / descuento")
                }
                TextButton(
                    onRemove,
                    enabled = enabled,
                    modifier = Modifier.testTag("line-remove-$index"),
                ) {
                    Text("Quitar", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PosSelector(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit,
    placeholder: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.padding(vertical = 4.dp)) {
        Surface(
            onClick = { expanded = true },
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(30.dp),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) {
            Text(
                options.firstOrNull { it.first == value && it.first.isNotEmpty() }?.second
                    ?: placeholder,
                Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            )
        }
        DropdownMenu(expanded, { expanded = false }) {
            if (options.isEmpty())
                DropdownMenuItem(
                    { Text("Sin ${label.lowercase()}s disponibles") },
                    {},
                    enabled = false,
                )
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    { Text(text) },
                    {
                        onChange(id)
                        expanded = false
                    },
                    modifier = Modifier.testTag("$tag-option-$id"),
                )
            }
        }
    }
}

@Composable
private fun PosTotals(
    subtotal: BigDecimal,
    discount: BigDecimal,
    total: BigDecimal,
    rate: BigDecimal,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            MoneyRow("Subtotal", currency(subtotal))
            MoneyRow("Descuento", "−${currency(discount)}")
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            MoneyRow("Total (MXN)", currency(total), true)
            MoneyRow(
                "Total (USD)",
                if (rate.signum() > 0) currency(total.divide(rate, 2, RoundingMode.HALF_UP))
                else "Tipo de cambio no configurado",
                true,
            )
            if (rate.signum() > 0)
                Text(
                    "1 USD = ${currency(rate)} MXN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
    }
}

@Composable
private fun MoneyRow(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
        Text(
            value,
            Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PosDialog(
    title: String,
    dismiss: () -> Unit,
    content: @Composable () -> Unit,
    acceptText: String = "Guardar",
    accept: () -> Unit,
    enabled: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { DialogBody { content() } },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(16.dp).widthIn(max = 560.dp),
        confirmButton = { TextButton(accept, enabled = enabled) { Text(acceptText) } },
        dismissButton = { TextButton(dismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun NoteDialog(title: String, value: String, dismiss: () -> Unit, save: (String) -> Unit) {
    var note by rememberSaveable { mutableStateOf(value) }
    PosDialog(
        title,
        dismiss,
        {
            OutlinedTextField(
                note,
                { note = it },
                label = { Text("Observaciones") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        "Guardar nota",
        { save(note) },
    )
}

@Composable
private fun GeneralDiscountDialog(
    type: String,
    value: String,
    subtotal: BigDecimal,
    dismiss: () -> Unit,
    save: (String, String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(type) }
    var input by rememberSaveable { mutableStateOf(value) }
    val n = input.replace(',', '.').toBigDecimalOrNull()
    val valid =
        n != null && n.signum() >= 0 && n <= if (selected == "%") BigDecimal(100) else subtotal
    PosDialog(
        "Agregar descuento",
        dismiss,
        {
            Selector("Tipo", selected, listOf("%" to "%", "$" to "MXN"), { selected = it })
            Field("Valor", input, { input = it }, Modifier.fillMaxWidth())
            if (!valid)
                Text(
                    "Indique un descuento válido para el subtotal.",
                    color = MaterialTheme.colorScheme.error,
                )
        },
        "Guardar descuento",
        { save(selected, input.replace(',', '.')) },
        valid,
    )
}

@Composable
private fun QuickClientDialog(
    vm: PosViewModel,
    busy: Boolean,
    dismiss: () -> Unit,
    added: (Long) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    PosDialog(
        "Agregar Cliente",
        dismiss,
        {
            Field("Nombre", name, { name = it }, Modifier.fillMaxWidth())
            Field("Teléfono", phone, { phone = it }, Modifier.fillMaxWidth())
            Field("Email", email, { email = it }, Modifier.fillMaxWidth())
        },
        "Agregar",
        {
            vm.run("Cliente agregado") {
                val id = vm.repo.addClient(name, phone, email)
                withContext(Dispatchers.Main) { added(id) }
            }
        },
        !busy && name.isNotBlank() && phone.isNotBlank() && email.isNotBlank(),
    )
}

@Composable
private fun ProductPhotoDialog(vm: PosViewModel, id: Long, dismiss: () -> Unit) {
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null)
                vm.run("Fotografía actualizada") {
                    vm.repo.updateProductPhoto(id, vm.files.importPhoto(uri).path)
                    withContext(Dispatchers.Main) { dismiss() }
                }
        }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success && cameraUri != null)
                vm.run("Fotografía actualizada") {
                    vm.repo.updateProductPhoto(id, vm.files.importPhoto(cameraUri!!).path)
                    withContext(Dispatchers.Main) { dismiss() }
                }
        }
    PosDialog(
        "Fotografía del producto",
        dismiss,
        {
            Button({ gallery.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text("Elegir imagen")
            }
            OutlinedButton(
                {
                    val f =
                        File(
                            vm.files.context.cacheDir,
                            "exports/product-$id-${System.currentTimeMillis()}.jpg",
                        )
                    f.parentFile?.mkdirs()
                    f.createNewFile()
                    cameraUri = vm.files.uri(f)
                    camera.launch(cameraUri!!)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cámara")
            }
        },
        "Cerrar",
        dismiss,
    )
}

@Composable
internal fun RestoredOrders(
    vm: PosViewModel,
    state: State,
    onEdit: (Long) -> Unit,
    onPay: (Long) -> Unit,
    onNewOrder: () -> Unit = {},
) {
    var detailRequest by rememberSaveable { mutableLongStateOf(0L) }
    var filter by rememberSaveable { mutableStateOf("abierto") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val rows =
        loadRows("orders:$filter:${state.revision}") {
            vm.repo.orders(filter.takeIf { it.isNotEmpty() })
        }
    val selected = rows.firstOrNull { it.id() == selectedId }
    val loadedDetails by
        produceState<Pair<Long, List<Line>>?>(null, selectedId, state.revision) {
            value = null
            value =
                selectedId?.let { id -> id to withContext(Dispatchers.IO) { vm.repo.lines(id) } }
        }
    val orderLines = loadedDetails?.takeIf { it.first == selectedId }?.second.orEmpty()
    val list: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Órdenes",
                    Modifier.weight(1f).padding(vertical = 12.dp).testTag("orders-title"),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = onNewOrder, modifier = Modifier.testTag("quick-new-order")) {
                    Text("Nueva comanda →")
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to "Todas", "abierto" to "Abiertos", "pagado" to "Pagados").forEach {
                    (status, name) ->
                    TextButton(
                        {
                            filter = status
                            selectedId = null
                        },
                        modifier = Modifier.testTag("orders-filter-$status"),
                    ) {
                        Text(
                            name,
                            fontWeight =
                                if (filter == status) FontWeight.Bold else FontWeight.Normal,
                            color =
                                if (filter == status) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (rows.isEmpty())
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No hay órdenes.")
                }
            else
                LazyVerticalGrid(
                    GridCells.Adaptive(250.dp),
                    Modifier.weight(1f).testTag("orders-grid"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(rows, key = { it.id() }) { order ->
                        val open = order.text("status") == "abierto"
                        Surface(
                            onClick = {
                                selectedId = order.id()
                                detailRequest++
                            },
                            shape = RoundedCornerShape(24.dp),
                            color =
                                if (selectedId == order.id())
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().testTag("order-${order.id()}"),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = Color(0xffffc107), shape = CircleShape) {
                                        Text(
                                            "M${order.id("mesa")}",
                                            Modifier.padding(10.dp),
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black,
                                        )
                                    }
                                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                        Text(
                                            order.text("mesa_nombre").ifBlank {
                                                "Mesa ${order.id("mesa")}"
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            "#${order.id("folio_comanda").toString().padStart(4,'0')} / ${order.id("personas")} personas",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    Surface(
                                        color =
                                            (if (open) PosGreen
                                                else MaterialTheme.colorScheme.error)
                                                .copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(20.dp),
                                    ) {
                                        Text(
                                            if (open) "Abierto" else "Pagado",
                                            Modifier.padding(6.dp),
                                            fontSize = 12.sp,
                                            color =
                                                if (open) PosGreen
                                                else MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                                FlowRow(
                                    Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(
                                        order.text("fecha"),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        order.text("hora"),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (open)
                                        TextButton(
                                            { deleting = order.id() },
                                            enabled = !state.busy,
                                            modifier =
                                                Modifier.semantics {
                                                    contentDescription =
                                                        "Eliminar comanda ${order.id("folio_comanda")}"
                                                },
                                        ) {
                                            Text(
                                                "Eliminar",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    Text(
                                        "Total ${currency(order.decimal("total"))}",
                                        Modifier.weight(1f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
    val detail: @Composable (Modifier) -> Unit = { modifier ->
        Surface(
            modifier,
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            if (selected == null)
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Selecciona una orden para ver detalles")
                }
            else if (loadedDetails?.first != selected.id())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            else
                LazyColumn(
                    Modifier.fillMaxSize().testTag("order-detail"),
                    contentPadding = PaddingValues(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Folio Comanda: ${selected.id("folio_comanda")}",
                                Modifier.weight(1f),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            TextButton({ selectedId = null }) { Text("×") }
                        }
                        Text(
                            "${selected.text("mesa_nombre").ifBlank { "Mesa ${selected.id("mesa")}" }} • ${selected.id("personas")} personas"
                        )
                        Text(
                            "${selected.text("fecha")} • ${selected.text("hora")}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (selected.text("cliente_nombre").isNotBlank())
                            Text("Cliente: ${selected.text("cliente_nombre")}")
                        Text(
                            if (selected.text("status") == "abierto") "Abierto" else "Pagado",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(orderLines) { line ->
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(
                                    "${line.quantity.stripTrailingZeros().toPlainString()} × ${line.name}",
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (line.description.isNotBlank())
                                    Text(
                                        line.description,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                if (line.code.isNotBlank())
                                    Text(line.code, style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Unit. ${currency(line.price)}")
                                    Text(
                                        "Desc. −${currency(line.discountAmount)}",
                                        color = PosGreen,
                                    )
                                    Text(
                                        "Total ${currency(line.net)}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        val subtotal = orderLines.fold(BigDecimal.ZERO) { a, l -> a + l.net }
                        val total =
                            Totals.total(
                                orderLines,
                                selected.text("discount_type"),
                                selected.decimal("general_discount"),
                            )
                        PosTotals(
                            subtotal,
                            subtotal - total,
                            total,
                            state.company.decimal("tipo_cambio"),
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                { showNotes = true },
                                colors =
                                    ButtonDefaults.buttonColors(
                                        containerColor = PosOrange,
                                        contentColor = Color.Black,
                                    ),
                            ) {
                                Text("Notas")
                            }
                            Button(
                                { onEdit(selected.id()) },
                                enabled = !state.busy && selected.text("status") == "abierto",
                                colors =
                                    ButtonDefaults.buttonColors(containerColor = Color(0xff1976d2)),
                                modifier = Modifier.testTag("edit-order"),
                            ) {
                                Text("Editar")
                            }
                            Button(
                                { onPay(selected.id()) },
                                enabled = !state.busy && selected.text("status") == "abierto",
                                colors = ButtonDefaults.buttonColors(containerColor = PosGreen),
                                modifier = Modifier.testTag("pay-order"),
                            ) {
                                Text("Pagar")
                            }
                            PrintAction(vm, state, { vm.repo.orderReceipt(selected.id()) })
                        }
                    }
                }
        }
    }
    OriginalPanes(
        "Órdenes",
        "Detalle",
        4f,
        3f,
        list,
        detail,
        selectedId?.let { it to detailRequest },
    )
    deleting?.let { id ->
        PosDialog(
            "Eliminar orden",
            { deleting = null },
            { Text("¿Estás seguro de que deseas eliminar esta orden abierta y sus artículos?") },
            "Eliminar",
            {
                vm.run("Comanda eliminada") { vm.repo.deleteOrder(id) }
                if (selectedId == id) selectedId = null
                deleting = null
            },
            !state.busy,
        )
    }
    if (showNotes && selected != null)
        PosDialog(
            "Nota de la Orden",
            { showNotes = false },
            { Text(selected.text("observaciones").ifBlank { "No hay notas registradas." }) },
            "Cerrar",
            { showNotes = false },
        )
}

@Composable
internal fun RestoredPaymentLayout(
    order: Row,
    lines: List<Line>,
    company: Row,
    actions: @Composable () -> Unit,
) {
    OriginalPanes(
        "Resumen",
        "Pago",
        1f,
        1f,
        first = { modifier ->
            Surface(
                modifier,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "Folio: ${order.id("folio_comanda")}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Mesa ${order.id("mesa")} • ${order.id("personas")} personas")
                    }
                    items(lines) { line ->
                        Text(
                            "${line.quantity.stripTrailingZeros().toPlainString()} × ${line.name}",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(line.description, style = MaterialTheme.typography.bodySmall)
                        MoneyRow("Unit. ${currency(line.price)}", currency(line.net))
                        if (line.discountAmount.signum() > 0)
                            MoneyRow("Descuento", currency(line.discountAmount))
                        HorizontalDivider()
                    }
                    item {
                        val subtotal = lines.fold(BigDecimal.ZERO) { a, l -> a + l.net }
                        val total =
                            Totals.total(
                                lines,
                                order.text("discount_type"),
                                order.decimal("general_discount"),
                            )
                        PosTotals(subtotal, subtotal - total, total, company.decimal("tipo_cambio"))
                    }
                }
            }
        },
        initialTab = 1,
        second = { modifier ->
            Surface(
                modifier,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Box(Modifier.fillMaxSize().padding(12.dp)) { actions() }
            }
        },
    )
}
