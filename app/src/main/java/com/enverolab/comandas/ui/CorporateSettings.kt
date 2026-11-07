package com.enverolab.comandas.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun CorporateSettings(vm: PosViewModel, state: State) {
    val m = vm.corporate
    var url by rememberSaveable { mutableStateOf(m.url()) }
    var number by rememberSaveable { mutableStateOf(m.terminal()) }
    var company by rememberSaveable { mutableStateOf("") }
    var terminalId by rememberSaveable { mutableStateOf("") }
    var activation by remember { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(m.enabled()) }
    var adopt by rememberSaveable { mutableStateOf(false) }
    var show by rememberSaveable { mutableStateOf(false) }
    val info = remember(state.revision) { m.info() }
    OutlinedCard(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Configuración corporativa", style = MaterialTheme.typography.titleLarge)
            Text(info, style = MaterialTheme.typography.bodySmall)
            Row {
                Switch(active, { active = it })
                Text("Modo corporativo opcional", Modifier.padding(12.dp))
            }
            OutlinedTextField(
                url,
                { url = it },
                label = { Text("Servidor HTTPS sin /api/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                number,
                { number = it },
                label = { Text("Número de terminal") },
                singleLine = true,
            )
            Button(
                onClick = {
                    vm.run {
                        vm.repo.requireAdmin()
                        m.configure(url, number, active)
                        vm.message("Configuración guardada")
                    }
                },
                enabled = !state.busy,
            ) {
                Text("Guardar configuración")
            }
            TextButton(onClick = { show = !show }) { Text("Registrar terminal nueva") }
            if (show) {
                Text(
                    "Termine las comandas abiertas. La primera descarga configura el equipo con los datos de la empresa."
                )
                OutlinedTextField(
                    company,
                    { company = it },
                    label = { Text("Código de empresa") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    terminalId,
                    { terminalId = it },
                    label = { Text("ID de terminal del portal") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    activation,
                    { activation = it },
                    label = { Text("Código temporal de activación") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Checkbox(adopt, { adopt = it })
                    Text(
                        "Confirmo la conciliación por códigos del catálogo existente y la conservación de su historial.",
                        Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        vm.run {
                            vm.repo.requireAdmin()
                            m.enroll(url, company, terminalId, activation, adopt)
                            activation = ""
                            vm.repo.logout()
                            active = true
                            number = m.terminal()
                            vm.message("Terminal configurada. Ingrese con su usuario corporativo")
                        }
                    },
                    enabled = !state.busy,
                ) {
                    Text("Activar y descargar empresa")
                }
            }
            TextButton(
                onClick = {
                    vm.run {
                        vm.repo.requireAdmin()
                        vm.message(m.ping())
                    }
                },
                enabled = !state.busy,
            ) {
                Text("Probar comunicación")
            }
            TextButton(
                onClick = {
                    vm.run {
                        vm.repo.requireAdmin()
                        vm.message(m.syncCatalog())
                    }
                },
                enabled = !state.busy,
            ) {
                Text("Actualizar catálogos e imágenes")
            }
            Button(
                onClick = {
                    vm.run {
                        vm.repo.requireAdmin()
                        vm.message(m.sendPending())
                    }
                },
                enabled = !state.busy,
            ) {
                Text("Enviar cortes pendientes")
            }
            Text(
                "Las claves temporales y recuperación se gestionan en el portal. La descarga no reemplaza sus existencias ni impresora.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
