package com.enverolab.comandas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

/** Aviso sobre el contenido y los diálogos, sin bloquear captura ni navegación. */
@Composable
internal fun FloatingNotice(vm: PosViewModel, state: State) {
    if (state.message.isBlank()) return
    LaunchedEffect(state.messageId) {
        delay(5000)
        vm.clearMessage(state.messageId)
    }
    Popup(
        alignment = Alignment.Center,
        properties =
            PopupProperties(
                focusable = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            Modifier.padding(24.dp).widthIn(max = 400.dp).testTag("floating-notice").semantics {
                liveRegion = LiveRegionMode.Polite
            },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 12.dp,
        ) {
            Row(
                Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(state.message, Modifier.weight(1f, false))
                TextButton(
                    onClick = { vm.clearMessage(state.messageId) },
                    modifier = Modifier.semantics { contentDescription = "Cerrar aviso" },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface
                        ),
                ) {
                    Text("×")
                }
            }
        }
    }
}
