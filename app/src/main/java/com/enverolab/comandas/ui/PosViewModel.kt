package com.enverolab.comandas.ui

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.enverolab.comandas.data.*
import com.enverolab.comandas.domain.*
import com.enverolab.comandas.io.*

data class State(
    val user: Row? = null,
    val products: List<Row> = emptyList(),
    val orders: List<Row> = emptyList(),
    val categories: List<Row> = emptyList(),
    val variants: List<Row> = emptyList(),
    val company: Row = emptyMap(),
    val clients: List<Row> = emptyList(),
    val tables: List<Row> = emptyList(),
    val methods: List<Row> = emptyList(),
    val busy: Boolean = false,
    val message: String = "",
    val messageId: Long = 0,
    val revision: Int = 0,
)

class PosViewModel(app: Application) : AndroidViewModel(app) {
    val database = PosDatabase(app)
    val repo = PosRepository(database)
    val corporate = com.enverolab.comandas.corporate.CorporateManager(app,database)
    val files = PosFiles(app, database)
    val printer = Printer(app)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val preferences =
        app.getSharedPreferences("session", android.content.Context.MODE_PRIVATE)

    init {
        run {
            val id = preferences.getLong("user_id", 0)
            if (id > 0) { repo.restoreSession(id); if (repo.user?.let { corporate.canRestore(it.text("username")) } == false) repo.logout() }
            if (corporate.enabled()) { com.enverolab.comandas.corporate.CorporateWorker.periodic(app); com.enverolab.comandas.corporate.CorporateWorker.schedule(app) }
        }
    }

    fun clearMessage(expectedId: Long? = null) {
        mutable.update {
            if (expectedId == null || it.messageId == expectedId) it.copy(message = "") else it
        }
    }

    fun message(text: String) {
        mutable.update { it.copy(message = text, messageId = it.messageId + 1) }
    }

    fun run(success: String = "", block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    block()
                    refresh()
                    if (corporate.enabled()) com.enverolab.comandas.corporate.CorporateWorker.schedule(getApplication())
                }
                if (success.isNotEmpty()) message(success)
            } catch (e: Exception) {
                message(e.message ?: "Error desconocido")
            } finally {
                preferences.edit { putLong("user_id", repo.user?.id() ?: 0) }
                mutable.value = mutable.value.copy(busy = false, user = repo.user)
            }
        }
    }

    private fun refresh() {
        if (repo.user == null) {
            mutable.value =
                State(
                    message = mutable.value.message,
                    messageId = mutable.value.messageId,
                    busy = true,
                    revision = mutable.value.revision + 1,
                )
            return
        }
        repo.requireUser()
        mutable.value =
            mutable.value.copy(
                user = repo.user,
                products = repo.products(),
                categories = repo.catalog("categories"),
                variants = repo.catalog("producto_variantes"),
                company = repo.catalog("Empresa").firstOrNull().orEmpty(),
                orders = repo.orders(),
                clients = repo.catalog("clientes"),
                tables = repo.catalog("mesas"),
                methods = repo.catalog("tipo_pago"),
                revision = mutable.value.revision + 1,
            )
    }

    fun login(name: String, password: String) = run { corporate.login(repo,name,password) }

    fun logout() = run { corporate.logout();repo.logout() }

    override fun onCleared() {
        database.close()
    }
}
