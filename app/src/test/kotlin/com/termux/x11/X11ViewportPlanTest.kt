package com.termux.x11

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X11ViewportPlanTest {
    @Test
    fun portraitViewUsesNonNegativeTargetAndFullDesktopSource() {
        val layout = X11ViewportPlan.fitLandscapeDesktop(360, 800)

        assertEquals(1920, layout.desktopWidth)
        assertEquals(1080, layout.desktopHeight)
        assertEquals(0, layout.viewportLeft)
        assertEquals(299, layout.viewportTop)
        assertEquals(360, layout.viewportWidth)
        assertEquals(203, layout.viewportHeight)
        assertEquals(0f, layout.sourceLeft, 0.001f)
        assertEquals(0f, layout.sourceTop, 0.001f)
        assertEquals(1920f, layout.sourceWidth, 0.001f)
        assertEquals(1080f, layout.sourceHeight, 0.001f)
        assertEquals(100, layout.rendererZoomPercent)
    }

    @Test
    fun landscapeViewUsesNonNegativeTargetAndFullDesktopSource() {
        val layout = X11ViewportPlan.fitLandscapeDesktop(900, 360)

        assertEquals(130, layout.viewportLeft)
        assertEquals(0, layout.viewportTop)
        assertEquals(640, layout.viewportWidth)
        assertEquals(360, layout.viewportHeight)
        assertEquals(0f, layout.sourceLeft, 0.001f)
        assertEquals(0f, layout.sourceTop, 0.001f)
        assertEquals(1920f, layout.sourceWidth, 0.001f)
        assertEquals(1080f, layout.sourceHeight, 0.001f)
        assertEquals(100, layout.rendererZoomPercent)
    }

    @Test
    fun exact1080pViewUsesFullTargetAndSource() {
        val layout = X11ViewportPlan.fitLandscapeDesktop(1920, 1080)

        assertEquals(0, layout.viewportLeft)
        assertEquals(0, layout.viewportTop)
        assertEquals(1920, layout.viewportWidth)
        assertEquals(1080, layout.viewportHeight)
        assertEquals(0f, layout.sourceLeft, 0.001f)
        assertEquals(0f, layout.sourceTop, 0.001f)
        assertEquals(1920f, layout.sourceWidth, 0.001f)
        assertEquals(1080f, layout.sourceHeight, 0.001f)
        assertEquals(100, layout.rendererZoomPercent)
    }

    @Test
    fun allTargetRectanglesStayInsideAndroidSurface() {
        listOf(
            X11ViewportPlan.CameraState.initial(360, 800),
            X11ViewportPlan.CameraState.initial(900, 360),
            X11ViewportPlan.CameraState.initial(1920, 1080)
        ).forEach { camera ->
            assertTrue(camera.viewportLeft >= 0)
            assertTrue(camera.viewportTop >= 0)
            assertTrue(camera.viewportLeft + camera.viewportWidth <= camera.viewWidth)
            assertTrue(camera.viewportTop + camera.viewportHeight <= camera.viewHeight)
        }
    }

    @Test
    fun cameraPinchZoomKeepsDesktopFocusStableInSourceRect() {
        val start = X11ViewportPlan.CameraState.initial(360, 800)
        val focusX = start.viewportLeft + start.viewportWidth / 2f
        val focusY = start.viewportTop + start.viewportHeight / 2f

        val zoomed = X11ViewportPlan.CameraState.fromGesture(
            360,
            800,
            start,
            focusX,
            focusY,
            focusX,
            focusY,
            2f
        )

        assertEquals(2f, zoomed.zoom, 0.001f)
        assertEquals(480f, zoomed.sourceLeft, 0.001f)
        assertEquals(270f, zoomed.sourceTop, 0.001f)
        assertEquals(960f, zoomed.sourceWidth, 0.001f)
        assertEquals(540f, zoomed.sourceHeight, 0.001f)
        assertEquals(200, zoomed.rendererZoomPercent)
        assertEquals(960f, zoomed.mapX(focusX), 0.001f)
        assertEquals(540f, zoomed.mapY(focusY), 0.001f)
    }

    @Test
    fun cameraPanIsClampedInsideDesktopSourceBounds() {
        val initial = X11ViewportPlan.CameraState.initial(360, 800)
        val start = X11ViewportPlan.CameraState.fromGesture(
            360,
            800,
            initial,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            2f
        )

        val rightEdge = start.panBy(-5000f, 0f)
        val leftEdge = start.panBy(5000f, 0f)

        assertEquals(960f, rightEdge.sourceLeft, 0.001f)
        assertEquals(0f, leftEdge.sourceLeft, 0.001f)
        assertEquals(270f, rightEdge.sourceTop, 0.001f)
        assertEquals(270f, leftEdge.sourceTop, 0.001f)
    }

    @Test
    fun landscapeCameraCanPanVerticallyAfterZoom() {
        val initial = X11ViewportPlan.CameraState.initial(900, 360)
        val camera = X11ViewportPlan.CameraState.fromGesture(
            900,
            360,
            initial,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            2f
        )

        assertEquals(270f, camera.sourceTop, 0.001f)
        assertEquals(540f, camera.mapY(180f), 0.001f)

        val top = camera.panBy(0f, 5000f)
        val bottom = camera.panBy(0f, -5000f)

        assertEquals(0f, top.sourceTop, 0.001f)
        assertEquals(540f, bottom.sourceTop, 0.001f)
    }

    @Test
    fun cameraMapClampsCoordinatesToDesktopBounds() {
        val camera = X11ViewportPlan.CameraState.initial(360, 800)

        assertEquals(0f, camera.mapY(-100f), 0.001f)
        assertEquals(1080f, camera.mapY(900f), 0.001f)
        assertEquals(0f, camera.mapX(-1000f), 0.001f)
        assertEquals(1920f, camera.mapX(2000f), 0.001f)
    }

    @Test
    fun cameraSingleFingerPanKeepsZoomedSourceInsideBounds() {
        val initial = X11ViewportPlan.CameraState.initial(360, 800)
        val camera = X11ViewportPlan.CameraState.fromGesture(
            360,
            800,
            initial,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            2f
        )

        val right = camera.panBy(-5000f, 0f)
        val left = camera.panBy(5000f, 0f)

        assertEquals(960f, right.sourceLeft, 0.001f)
        assertEquals(0f, left.sourceLeft, 0.001f)
        assertEquals(270f, right.sourceTop, 0.001f)
        assertEquals(270f, left.sourceTop, 0.001f)
    }

    @Test
    fun cameraZoomIsClampedToMaximumRelativeToAutoFit() {
        val initial = X11ViewportPlan.CameraState.initial(360, 800)
        val camera = X11ViewportPlan.CameraState.fromGesture(
            360,
            800,
            initial,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            initial.viewportLeft + initial.viewportWidth / 2f,
            initial.viewportTop + initial.viewportHeight / 2f,
            99f
        )

        assertEquals(4f, camera.zoom, 0.001f)
        assertEquals(720f, camera.sourceLeft, 0.001f)
        assertEquals(405f, camera.sourceTop, 0.001f)
        assertEquals(480f, camera.sourceWidth, 0.001f)
        assertEquals(270f, camera.sourceHeight, 0.001f)
        assertEquals(400, camera.rendererZoomPercent)
    }
}
