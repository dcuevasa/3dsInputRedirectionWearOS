package com.example.a3dsinputredirectionwearos

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketEncodingTest {

    @Test
    fun neutralFrameMatchesProtocolSpec() {
        val cfg = InputRedirectionConfig()
        val snap = InputRedirectionBridge.Companion.InputSnapshot()
        val bytes = InputRedirectionBridge.buildPacketBytes(cfg, snap)
        assertEquals(20, bytes.size)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x00000FFF, buf.int) // hid_pad sentinel
        assertEquals(0x02000000, buf.int) // touch sentinel
        assertEquals(0x007FF7FF, buf.int) // circle_pad sentinel
        assertEquals(0x80800081.toInt(), buf.int) // cpp_ir neutral
        assertEquals(0x00000000, buf.int) // special neutral
    }

    @Test
    fun buttonAPressedClearsBitZero() {
        val cfg = InputRedirectionConfig()
        val snap = InputRedirectionBridge.Companion.InputSnapshot(
            pressedKeys = setOf(KeyEvent.KEYCODE_BUTTON_A),
        )
        val bytes = InputRedirectionBridge.buildPacketBytes(cfg, snap)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x00000FFE, buf.int)
    }

    @Test
    fun xboxPositionalRemapClearsBitZeroWhenButtonBPressed() {
        // In Xbox positional mapping, physical B (right) triggers 3DS A (bit 0)
        val cfg = InputRedirectionConfig(
            mapA = KeyEvent.KEYCODE_BUTTON_B,
            mapB = KeyEvent.KEYCODE_BUTTON_A,
        )
        val snap = InputRedirectionBridge.Companion.InputSnapshot(
            pressedKeys = setOf(KeyEvent.KEYCODE_BUTTON_B),
        )
        val bytes = InputRedirectionBridge.buildPacketBytes(cfg, snap)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x00000FFE, buf.int) // Bit 0 cleared -> 3DS A pressed
    }

    @Test
    fun dpadHatInputClearsDpadBit() {
        val cfg = InputRedirectionConfig()
        // D-Pad Left is bit 5
        val snap = InputRedirectionBridge.Companion.InputSnapshot(
            pressedKeys = setOf(KeyEvent.KEYCODE_DPAD_LEFT),
        )
        val bytes = InputRedirectionBridge.buildPacketBytes(cfg, snap)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val hid = buf.int
        val bit5 = (hid and (1 shl 5)) == 0
        assertEquals(true, bit5)
    }

    @Test
    fun touchCoordinatesPackedCorrectly() {
        val cfg = InputRedirectionConfig()
        val snap = InputRedirectionBridge.Companion.InputSnapshot(
            touchActive = true,
            touchX = 0x123,
            touchY = 0x456,
        )
        val bytes = InputRedirectionBridge.buildPacketBytes(cfg, snap)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.int // skip hid_pad
        val expectedTouch = (1 shl 24) or (0x456 shl 12) or 0x123
        assertEquals(expectedTouch, buf.int)
    }
}
