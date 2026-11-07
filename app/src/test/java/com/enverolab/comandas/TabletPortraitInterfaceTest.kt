package com.enverolab.comandas

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w800dp-h1280dp-port")
@LooperMode(LooperMode.Mode.PAUSED)
class TabletPortraitInterfaceTest : InterfaceTest()
