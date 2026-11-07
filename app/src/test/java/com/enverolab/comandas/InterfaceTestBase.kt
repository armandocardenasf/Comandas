package com.enverolab.comandas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import java.io.File
import com.enverolab.comandas.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w1280dp-h720dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
abstract class InterfaceTestBase {
    val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule
    val rules: org.junit.rules.TestRule =
        org.junit.rules.RuleChain.outerRule(
                object : org.junit.rules.ExternalResource() {
                    override fun before() {
                        val context = ApplicationProvider.getApplicationContext<Context>()
                        context.deleteDatabase("restaurant.db")
                        context
                            .getSharedPreferences("session", Context.MODE_PRIVATE)
                            .edit()
                            .clear()
                            .commit()
                    }
                }
            )
            .around(compose)
            .around(
                object : org.junit.rules.TestWatcher() {
                    override fun failed(
                        error: Throwable,
                        description: org.junit.runner.Description,
                    ) {
                        val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
                        roots.fetchSemanticsNodes().indices.forEach {
                            println(roots[it].printToString(maxDepth = 20))
                        }
                    }
                }
            )

    protected fun clickTag(tag: String) =
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick) { it() }

    protected fun click(text: String) =
        compose.onNode(hasText(text) and hasClickAction()).performSemanticsAction(
            SemanticsActions.OnClick
        ) {
            it()
        }

    protected fun navigate(name: String) {
        if (compose.onAllNodesWithText("Secciones").fetchSemanticsNodes().isNotEmpty())
            click("Secciones")
        clickTag("nav-$name")
    }

    protected fun secondPane() {
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("pane-second").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("order-detail").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("summary-list").fetchSemanticsNodes().isNotEmpty() ||
                compose
                    .onAllNodesWithText("Agregar pago / asignar artículos")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        if (compose.onAllNodesWithTag("pane-second").fetchSemanticsNodes().isNotEmpty())
            clickTag("pane-second")
    }

    protected fun firstPane() {
        if (compose.onAllNodesWithTag("pane-first").fetchSemanticsNodes().isNotEmpty())
            clickTag("pane-first")
    }

    protected fun login() {
        // Wait for asynchronous session restoration before deciding whether to log out.
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("app-busy").fetchSemanticsNodes().isEmpty() &&
            (compose.onAllNodesWithText("Entrar").fetchSemanticsNodes().isNotEmpty() ||
             compose.onAllNodesWithTag("product-search").fetchSemanticsNodes().isNotEmpty())
        }
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Entrar").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("COMANDAS").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Entrar").fetchSemanticsNodes().isEmpty()) {
            if (compose.onAllNodesWithText("Secciones").fetchSemanticsNodes().isNotEmpty())
                click("Secciones")
            click("Cerrar sesión")
            compose.waitUntil(15000) {
                compose.onAllNodesWithText("Entrar").fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("app-busy").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("login-card").assertExists()
        snapshot("login")
        compose.onNodeWithText("Usuario").performTextInput("admin")
        compose.onNodeWithText("Contraseña").performTextInput("1234")
        compose.waitUntil(15000) {
            compose.onAllNodes(hasText("Entrar") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        click("Entrar")
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("product-search").fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun summaryScroll(tag: String) {
        secondPane()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("summary-list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("summary-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }

    protected fun detailScroll(tag: String) {
        secondPane()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("order-detail").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("order-detail").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollTo()
    }

    protected fun snapshot(name: String) {
        compose.mainClock.advanceTimeBy(5000)
        compose.waitForIdle()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(image))
            val file = File("build/ui-proof/${javaClass.simpleName}-$name.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
    }
}
