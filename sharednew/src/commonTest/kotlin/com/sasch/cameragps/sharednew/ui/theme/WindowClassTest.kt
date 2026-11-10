package com.sasch.cameragps.sharednew.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowClassTest {
    @Test
    fun phonePortraitIsClassA() {
        assertEquals(WindowClass.Portrait, windowClassOf(360f, 800f))
        assertEquals(WindowClass.Portrait, windowClassOf(411f, 891f))
    }

    @Test
    fun widthBreakpointIs600() {
        assertEquals(WindowClass.Portrait, windowClassOf(599.9f, 900f))
        assertEquals(WindowClass.NearSquare, windowClassOf(600f, 900f))
    }

    @Test
    fun heightBreakpointIs480() {
        assertEquals(WindowClass.NearSquare, windowClassOf(700f, 480f))
        assertEquals(WindowClass.Landscape, windowClassOf(700f, 479.9f))
        assertEquals(WindowClass.Portrait, windowClassOf(400f, 480f))
    }

    @Test
    fun foldableAndTabletAreClassB() {
        assertEquals(WindowClass.NearSquare, windowClassOf(673f, 841f))
        assertEquals(WindowClass.NearSquare, windowClassOf(1280f, 800f))
    }

    @Test
    fun shortWindowIsClassCRegardlessOfWidth() {
        assertEquals(WindowClass.Landscape, windowClassOf(891f, 411f))
        // Narrow and short (split screen half): height wins.
        assertEquals(WindowClass.Landscape, windowClassOf(360f, 400f))
    }
}
