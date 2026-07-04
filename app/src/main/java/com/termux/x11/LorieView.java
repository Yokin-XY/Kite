package com.termux.x11;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewConfiguration;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;

@Keep
public class LorieView extends SurfaceView {
    private static final int BUTTON_LEFT = 1;
    private static final int BGRA_8888 = 5;
    private static LorieView activeView;
    private ClipboardManager clipboard;
    private int touchSlop;
    private int inputViewportLeft;
    private int inputViewportTop;
    private int inputViewportWidth;
    private int inputViewportHeight;
    private float inputSourceLeft;
    private float inputSourceTop;
    private float inputSourceWidth;
    private float inputSourceHeight;
    private X11ViewportPlan.CameraState cameraState;
    private X11ViewportPlan.CameraState gestureStartCamera;
    private float gestureStartFocusX;
    private float gestureStartFocusY;
    private float gestureStartSpan;
    private X11ViewportPlan.CameraState singleTouchStartCamera;
    private float singleTouchStartX;
    private float singleTouchStartY;
    private boolean singleTouchPending;
    private boolean singleFingerCameraDragActive;
    private boolean remoteMouseDown;
    private boolean cameraGestureActive;
    private boolean waitForSingleTouchReset;
    private int lastRendererZoomPercent = -1;
    private int lastWindowChangeWidth = -1;
    private int lastWindowChangeHeight = -1;
    private int lastWindowChangeFramerate = -1;

    static {
        System.loadLibrary("Xlorie");
    }

    public LorieView(Context context) {
        super(context);
        init();
    }

    private void init() {
        activeView = this;
        clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                resetWindowChangeState();
                holder.setFormat(BGRA_8888);
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                LorieView.this.surfaceChanged(holder.getSurface());
                updateViewport();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                resetWindowChangeState();
                LorieView.this.surfaceChanged(null);
            }
        });
        setBackground(new ColorDrawable(Color.TRANSPARENT));
        setFocusable(true);
        setFocusableInTouchMode(true);
        nativeInit();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateViewport();
    }

    public void triggerCallback() {
        requestFocus();
        post(this::hideSoftKeyboard);
        updateViewport();
    }

    private void updateViewport() {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (width <= 0 || height <= 0) return;
        if (cameraState == null) {
            cameraState = X11ViewportPlan.CameraState.initial(width, height);
        } else {
            cameraState = cameraState.reframe(width, height);
        }
        applyViewport(cameraState, true);
    }

    private void applyViewport(X11ViewportPlan.CameraState layout) {
        applyViewport(layout, false);
    }

    private void applyViewport(X11ViewportPlan.CameraState layout, boolean notifyWindowChange) {
        updateInputViewport(
                layout.viewportLeft,
                layout.viewportTop,
                layout.viewportWidth,
                layout.viewportHeight,
                layout.sourceLeft,
                layout.sourceTop,
                layout.sourceWidth,
                layout.sourceHeight
        );
        setViewport(
                layout.viewportLeft,
                layout.viewportTop,
                layout.viewportWidth,
                layout.viewportHeight,
                layout.desktopWidth,
                layout.desktopHeight
        );
        applyRendererZoomIfNeeded(layout.rendererZoomPercent);
        if (notifyWindowChange) {
            sendWindowChangeIfNeeded(layout);
        }
    }

    private void applyRendererZoomIfNeeded(int percent) {
        if (lastRendererZoomPercent == percent) return;
        setRendererZoom(percent);
        lastRendererZoomPercent = percent;
    }

    private void sendWindowChangeIfNeeded(X11ViewportPlan.CameraState layout) {
        int framerate = getDisplay() != null ? Math.round(getDisplay().getRefreshRate()) : 60;
        framerate = Math.max(30, framerate);
        if (lastWindowChangeWidth == layout.desktopWidth
                && lastWindowChangeHeight == layout.desktopHeight
                && lastWindowChangeFramerate == framerate) {
            return;
        }
        sendWindowChange(layout.desktopWidth, layout.desktopHeight, framerate, "kite");
        lastWindowChangeWidth = layout.desktopWidth;
        lastWindowChangeHeight = layout.desktopHeight;
        lastWindowChangeFramerate = framerate;
    }

    private void resetWindowChangeState() {
        lastRendererZoomPercent = -1;
        lastWindowChangeWidth = -1;
        lastWindowChangeHeight = -1;
        lastWindowChangeFramerate = -1;
    }

    private void updateInputViewport(
            int viewportLeft,
            int viewportTop,
            int viewportWidth,
            int viewportHeight,
            float sourceLeft,
            float sourceTop,
            float sourceWidth,
            float sourceHeight
    ) {
        inputViewportLeft = viewportLeft;
        inputViewportTop = viewportTop;
        inputViewportWidth = Math.max(1, viewportWidth);
        inputViewportHeight = Math.max(1, viewportHeight);
        inputSourceLeft = sourceLeft;
        inputSourceTop = sourceTop;
        inputSourceWidth = sourceWidth > 0 ? sourceWidth : inputViewportWidth;
        inputSourceHeight = sourceHeight > 0 ? sourceHeight : inputViewportHeight;
    }

    @Keep
    @SuppressWarnings("unused")
    private static void setRendererViewport(
            int viewportLeft,
            int viewportTop,
            int viewportWidth,
            int viewportHeight,
            float sourceLeft,
            float sourceTop,
            float sourceWidth,
            float sourceHeight
    ) {
        LorieView view = activeView;
        if (view == null) return;
        view.post(() -> view.updateInputViewport(
                viewportLeft,
                viewportTop,
                viewportWidth,
                viewportHeight,
                sourceLeft,
                sourceTop,
                sourceWidth,
                sourceHeight
        ));
    }

    private float mapInputX(float x) {
        float relative = (x - inputViewportLeft) / Math.max(1, inputViewportWidth);
        return X11ViewportPlan.clamp(inputSourceLeft + relative * inputSourceWidth, inputSourceLeft, inputSourceLeft + inputSourceWidth);
    }

    private float mapInputY(float y) {
        float relative = (y - inputViewportTop) / Math.max(1, inputViewportHeight);
        return X11ViewportPlan.clamp(inputSourceTop + relative * inputSourceHeight, inputSourceTop, inputSourceTop + inputSourceHeight);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        requestFocus();
        hideSoftKeyboard();
        int action = event.getActionMasked();
        if (event.getPointerCount() >= 2 || cameraGestureActive) {
            return handleCameraGesture(event, action);
        }
        if (waitForSingleTouchReset) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                waitForSingleTouchReset = false;
            }
            return true;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                beginSingleTouch(event);
                return true;
            case MotionEvent.ACTION_MOVE:
                return handleSingleTouchMove(event);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                finishSingleTouch(event, action == MotionEvent.ACTION_CANCEL);
                return true;
            default:
                return true;
        }
    }

    private void beginSingleTouch(MotionEvent event) {
        singleTouchStartX = event.getX();
        singleTouchStartY = event.getY();
        singleTouchStartCamera = cameraState != null
                ? cameraState
                : X11ViewportPlan.CameraState.initial(getMeasuredWidth(), getMeasuredHeight());
        singleTouchPending = true;
        singleFingerCameraDragActive = false;
        remoteMouseDown = false;
    }

    private boolean handleSingleTouchMove(MotionEvent event) {
        if (singleFingerCameraDragActive) {
            panCameraFromSingleTouch(event);
            return true;
        }
        if (!singleTouchPending) {
            if (remoteMouseDown) {
                sendMouseEvent(mapInputX(event.getX()), mapInputY(event.getY()), 0, false, false);
            }
            return true;
        }
        float dx = event.getX() - singleTouchStartX;
        float dy = event.getY() - singleTouchStartY;
        if (Math.hypot(dx, dy) < touchSlop) {
            return true;
        }
        if (shouldStartSingleFingerCameraDrag(singleTouchStartX, singleTouchStartY)) {
            singleTouchPending = false;
            singleFingerCameraDragActive = true;
            panCameraFromSingleTouch(event);
            return true;
        }
        if (!isInsideInputViewport(singleTouchStartX, singleTouchStartY)) {
            singleTouchPending = false;
            return true;
        }
        remoteMouseDown = true;
        singleTouchPending = false;
        sendMouseEvent(mapInputX(singleTouchStartX), mapInputY(singleTouchStartY), BUTTON_LEFT, true, false);
        sendMouseEvent(mapInputX(event.getX()), mapInputY(event.getY()), 0, false, false);
        return true;
    }

    private boolean shouldStartSingleFingerCameraDrag(float x, float y) {
        X11ViewportPlan.CameraState state = cameraState;
        if (state == null || !state.canPan()) return false;
        return !state.containsViewPoint(x, y) || state.hasCroppedAxis();
    }

    private void panCameraFromSingleTouch(MotionEvent event) {
        X11ViewportPlan.CameraState start = singleTouchStartCamera != null
                ? singleTouchStartCamera
                : X11ViewportPlan.CameraState.initial(getMeasuredWidth(), getMeasuredHeight());
        cameraState = start.panBy(event.getX() - singleTouchStartX, event.getY() - singleTouchStartY);
        applyViewport(cameraState);
        driveNativePanTo(start, cameraState);
    }

    private void driveNativePanTo(X11ViewportPlan.CameraState start, X11ViewportPlan.CameraState target) {
        if (start == null || target == null || !target.canPan()) return;
        float x = cursorForSourceAxis(start.sourceLeft, target.sourceLeft, target.sourceWidth, target.desktopWidth);
        float y = cursorForSourceAxis(start.sourceTop, target.sourceTop, target.sourceHeight, target.desktopHeight);
        sendMouseEvent(x, y, 0, false, false);
    }

    private float cursorForSourceAxis(float startSource, float targetSource, float sourceSize, float desktopSize) {
        float edge = Math.max(1f, sourceSize * 0.05f);
        if (targetSource < startSource - 0.5f) {
            return X11ViewportPlan.clamp(targetSource + edge, 0f, desktopSize);
        }
        if (targetSource > startSource + 0.5f) {
            return X11ViewportPlan.clamp(targetSource + sourceSize - edge, 0f, desktopSize);
        }
        return X11ViewportPlan.clamp(targetSource + sourceSize / 2f, 0f, desktopSize);
    }

    private void finishSingleTouch(MotionEvent event, boolean canceled) {
        if (singleFingerCameraDragActive) {
            clearSingleTouchState();
            return;
        }
        if (singleTouchPending && !canceled && isInsideInputViewport(event.getX(), event.getY())) {
            float x = mapInputX(event.getX());
            float y = mapInputY(event.getY());
            sendMouseEvent(x, y, BUTTON_LEFT, true, false);
            sendMouseEvent(x, y, BUTTON_LEFT, false, false);
        } else if (remoteMouseDown) {
            sendMouseEvent(mapInputX(event.getX()), mapInputY(event.getY()), BUTTON_LEFT, false, false);
        }
        clearSingleTouchState();
    }

    private void clearSingleTouchState() {
        singleTouchPending = false;
        singleFingerCameraDragActive = false;
        singleTouchStartCamera = null;
        remoteMouseDown = false;
    }

    private boolean isInsideInputViewport(float x, float y) {
        return x >= inputViewportLeft
                && x <= inputViewportLeft + inputViewportWidth
                && y >= inputViewportTop
                && y <= inputViewportTop + inputViewportHeight;
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getWindowToken() != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
    }

    private boolean handleCameraGesture(MotionEvent event, int action) {
        if (action == MotionEvent.ACTION_POINTER_DOWN || !cameraGestureActive) {
            releaseRemoteMouseIfNeeded(event);
            clearSingleTouchState();
            cameraGestureActive = true;
            waitForSingleTouchReset = true;
            gestureStartCamera = cameraState != null
                    ? cameraState
                    : X11ViewportPlan.CameraState.initial(getMeasuredWidth(), getMeasuredHeight());
            gestureStartFocusX = pointerFocusX(event);
            gestureStartFocusY = pointerFocusY(event);
            gestureStartSpan = Math.max(1f, pointerSpan(event));
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() >= 2) {
            float span = Math.max(1f, pointerSpan(event));
            cameraState = X11ViewportPlan.CameraState.fromGesture(
                    getMeasuredWidth(),
                    getMeasuredHeight(),
                    gestureStartCamera,
                    gestureStartFocusX,
                    gestureStartFocusY,
                    pointerFocusX(event),
                    pointerFocusY(event),
                    span / Math.max(1f, gestureStartSpan)
            );
            sendMouseEvent(cameraState.mapX(pointerFocusX(event)), cameraState.mapY(pointerFocusY(event)), 0, false, false);
            applyViewport(cameraState);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() <= 2) {
            cameraGestureActive = false;
            gestureStartCamera = null;
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cameraGestureActive = false;
            gestureStartCamera = null;
            waitForSingleTouchReset = false;
            return true;
        }
        return true;
    }

    private void releaseRemoteMouseIfNeeded(MotionEvent event) {
        if (!remoteMouseDown) return;
        sendMouseEvent(mapInputX(event.getX()), mapInputY(event.getY()), BUTTON_LEFT, false, false);
        remoteMouseDown = false;
    }

    private float pointerFocusX(MotionEvent event) {
        float total = 0f;
        int count = Math.max(1, event.getPointerCount());
        for (int i = 0; i < count; i++) total += event.getX(i);
        return total / count;
    }

    private float pointerFocusY(MotionEvent event) {
        float total = 0f;
        int count = Math.max(1, event.getPointerCount());
        for (int i = 0; i < count; i++) total += event.getY(i);
        return total / count;
    }

    private float pointerSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) return 1f;
        float dx = event.getX(1) - event.getX(0);
        float dy = event.getY(1) - event.getY(0);
        return (float) Math.hypot(dx, dy);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
            return sendKeyEvent(event.getScanCode(), event.getKeyCode(), action == KeyEvent.ACTION_DOWN);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                if (text != null && text.length() > 0) {
                    sendTextEvent(text.toString().getBytes(StandardCharsets.UTF_8));
                }
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                return LorieView.this.dispatchKeyEvent(event);
            }
        };
    }

    @Keep
    public void setClipboardText(String text) {
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("X11 clipboard", text == null ? "" : text));
        }
    }

    @Keep
    public void requestClipboard() {
        CharSequence text = clipboard != null && clipboard.getText() != null ? clipboard.getText() : "";
        sendClipboardEvent(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Keep
    public void resetIme() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            post(() -> ((android.view.inputmethod.InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).invalidateInput(this));
        }
    }

    public boolean sendKeyEvent(int scanCode, int keyCode, boolean keyDown) {
        return sendKeyEvent(scanCode, keyCode, keyDown, 0);
    }

    private native void nativeInit();
    private native void surfaceChanged(Surface surface);
    private native void setFiltering(int filtering);
    public static native void connect(int fd);
    public static native boolean connected();
    public static native void startLogcat(int fd);
    public static native void setClipboardSyncEnabled(boolean enabled, boolean ignored);
    public native void sendClipboardAnnounce();
    public native void sendClipboardEvent(byte[] text);
    public static native void sendWindowChange(int width, int height, int framerate, String name);
    public static native void setViewport(int x, int y, int width, int height, int expectedWidth, int expectedHeight);
    private static native void setRendererZoom(int percent);
    public native void sendMouseEvent(float x, float y, int whichButton, boolean buttonDown, boolean relative);
    public native void sendTouchEvent(int action, int id, int x, int y);
    public native void sendStylusEvent(float x, float y, int pressure, int tiltX, int tiltY, int orientation, int buttons, boolean eraser, boolean mouseMode);
    public static native void requestStylusEnabled(boolean enabled);
    public native boolean sendKeyEvent(int scanCode, int keyCode, boolean keyDown, int a);
    public native void sendTextEvent(byte[] text);
    public static native boolean requestConnection();
}

final class X11ViewportPlan {
    private static final int LANDSCAPE_DESKTOP_WIDTH = 1920;
    private static final int LANDSCAPE_DESKTOP_HEIGHT = 1080;
    private static final float MIN_CAMERA_ZOOM = 1f;
    private static final float MAX_CAMERA_ZOOM = 4f;
    private static final int MIN_RENDERER_ZOOM_PERCENT = 100;
    private static final int MAX_RENDERER_ZOOM_PERCENT = 2000;

    private X11ViewportPlan() {}

    static Layout fitLandscapeDesktop(int viewWidth, int viewHeight) {
        return fitInsideSurface(viewWidth, viewHeight, LANDSCAPE_DESKTOP_WIDTH, LANDSCAPE_DESKTOP_HEIGHT);
    }

    private static Layout fitInsideSurface(int viewWidth, int viewHeight, int desktopWidth, int desktopHeight) {
        if (viewWidth <= 0 || viewHeight <= 0) {
            return new Layout(0, 0, 1, 1, desktopWidth, desktopHeight, 0f, 0f, desktopWidth, desktopHeight, 100);
        }
        float scale = Math.min(viewWidth / (float) desktopWidth, viewHeight / (float) desktopHeight);
        int viewportWidth = Math.max(1, Math.round(desktopWidth * scale));
        int viewportHeight = Math.max(1, Math.round(desktopHeight * scale));
        int viewportLeft = Math.max(0, Math.round((viewWidth - viewportWidth) / 2f));
        int viewportTop = Math.max(0, Math.round((viewHeight - viewportHeight) / 2f));
        return new Layout(
                viewportLeft,
                viewportTop,
                viewportWidth,
                viewportHeight,
                desktopWidth,
                desktopHeight,
                0f,
                0f,
                desktopWidth,
                desktopHeight,
                MIN_RENDERER_ZOOM_PERCENT
        );
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampPercent(int value) {
        return Math.max(MIN_RENDERER_ZOOM_PERCENT, Math.min(MAX_RENDERER_ZOOM_PERCENT, value));
    }

    private static SourceRect centeredSourceFor(int desktopWidth, int desktopHeight, float zoom) {
        float safeZoom = clamp(zoom, MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM);
        float width = clamp(desktopWidth / safeZoom, 1f, desktopWidth);
        float height = clamp(desktopHeight / safeZoom, 1f, desktopHeight);
        float left = (desktopWidth - width) / 2f;
        float top = (desktopHeight - height) / 2f;
        return new SourceRect(left, top, width, height);
    }

    static class Layout {
        final int viewportLeft;
        final int viewportTop;
        final int viewportWidth;
        final int viewportHeight;
        final int desktopWidth;
        final int desktopHeight;
        final float sourceLeft;
        final float sourceTop;
        final float sourceWidth;
        final float sourceHeight;
        final int rendererZoomPercent;

        Layout(
                int viewportLeft,
                int viewportTop,
                int viewportWidth,
                int viewportHeight,
                int desktopWidth,
                int desktopHeight,
                float sourceLeft,
                float sourceTop,
                float sourceWidth,
                float sourceHeight,
                int rendererZoomPercent
        ) {
            this.viewportLeft = viewportLeft;
            this.viewportTop = viewportTop;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.desktopWidth = desktopWidth;
            this.desktopHeight = desktopHeight;
            this.sourceLeft = sourceLeft;
            this.sourceTop = sourceTop;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.rendererZoomPercent = rendererZoomPercent;
        }

        float relativeX(float x) {
            return clamp((x - viewportLeft) / Math.max(1f, viewportWidth), 0f, 1f);
        }

        float relativeY(float y) {
            return clamp((y - viewportTop) / Math.max(1f, viewportHeight), 0f, 1f);
        }
    }

    static final class CameraState extends Layout {
        final int viewWidth;
        final int viewHeight;
        final float zoom;

        private CameraState(
                int viewWidth,
                int viewHeight,
                int viewportLeft,
                int viewportTop,
                int viewportWidth,
                int viewportHeight,
                int desktopWidth,
                int desktopHeight,
                float sourceLeft,
                float sourceTop,
                float sourceWidth,
                float sourceHeight,
                float zoom
        ) {
            super(
                    viewportLeft,
                    viewportTop,
                    viewportWidth,
                    viewportHeight,
                    desktopWidth,
                    desktopHeight,
                    sourceLeft,
                    sourceTop,
                    sourceWidth,
                    sourceHeight,
                    rendererZoomPercentFor(viewWidth, viewHeight, viewportWidth, viewportHeight, zoom)
            );
            this.viewWidth = Math.max(1, viewWidth);
            this.viewHeight = Math.max(1, viewHeight);
            this.zoom = clamp(zoom, MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM);
        }

        static CameraState initial(int viewWidth, int viewHeight) {
            Layout base = fitLandscapeDesktop(viewWidth, viewHeight);
            return new CameraState(
                    viewWidth,
                    viewHeight,
                    base.viewportLeft,
                    base.viewportTop,
                    base.viewportWidth,
                    base.viewportHeight,
                    base.desktopWidth,
                    base.desktopHeight,
                    base.sourceLeft,
                    base.sourceTop,
                    base.sourceWidth,
                    base.sourceHeight,
                    MIN_CAMERA_ZOOM
            );
        }

        static CameraState fromGesture(
                int viewWidth,
                int viewHeight,
                CameraState start,
                float startFocusX,
                float startFocusY,
                float currentFocusX,
                float currentFocusY,
                float zoomFactor
        ) {
            CameraState safeStart = start != null ? start : initial(viewWidth, viewHeight);
            float anchorDesktopX = safeStart.mapX(startFocusX);
            float anchorDesktopY = safeStart.mapY(startFocusY);
            float nextZoom = clamp(safeStart.zoom * zoomFactor, MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM);
            Layout base = fitLandscapeDesktop(viewWidth, viewHeight);
            SourceRect initialSource = centeredSourceFor(base.desktopWidth, base.desktopHeight, nextZoom);
            float relativeX = base.relativeX(currentFocusX);
            float relativeY = base.relativeY(currentFocusY);
            float desiredLeft = anchorDesktopX - relativeX * initialSource.width;
            float desiredTop = anchorDesktopY - relativeY * initialSource.height;
            return fromSource(viewWidth, viewHeight, base, desiredLeft, desiredTop, initialSource.width, initialSource.height, nextZoom);
        }

        CameraState reframe(int nextViewWidth, int nextViewHeight) {
            float centerDesktopX = mapX(viewWidth / 2f);
            float centerDesktopY = mapY(viewHeight / 2f);
            Layout base = fitLandscapeDesktop(nextViewWidth, nextViewHeight);
            SourceRect initialSource = centeredSourceFor(base.desktopWidth, base.desktopHeight, zoom);
            float desiredLeft = centerDesktopX - initialSource.width / 2f;
            float desiredTop = centerDesktopY - initialSource.height / 2f;
            return fromSource(nextViewWidth, nextViewHeight, base, desiredLeft, desiredTop, initialSource.width, initialSource.height, zoom);
        }

        CameraState panBy(float deltaX, float deltaY) {
            Layout base = fitLandscapeDesktop(viewWidth, viewHeight);
            float desiredLeft = sourceLeft - deltaX * (sourceWidth / Math.max(1f, viewportWidth));
            float desiredTop = sourceTop - deltaY * (sourceHeight / Math.max(1f, viewportHeight));
            return fromSource(
                    viewWidth,
                    viewHeight,
                    base,
                    desiredLeft,
                    desiredTop,
                    sourceWidth,
                    sourceHeight,
                    zoom
            );
        }

        boolean canPan() {
            return sourceWidth < desktopWidth || sourceHeight < desktopHeight;
        }

        boolean hasCroppedAxis() {
            return canPan();
        }

        boolean containsViewPoint(float x, float y) {
            return x >= viewportLeft
                    && x <= viewportLeft + viewportWidth
                    && y >= viewportTop
                    && y <= viewportTop + viewportHeight;
        }

        float mapX(float x) {
            float relative = relativeX(x);
            return clamp(sourceLeft + relative * sourceWidth, 0f, desktopWidth);
        }

        float mapY(float y) {
            float relative = relativeY(y);
            return clamp(sourceTop + relative * sourceHeight, 0f, desktopHeight);
        }

        private static CameraState fromSource(
                int viewWidth,
                int viewHeight,
                Layout base,
                float sourceLeft,
                float sourceTop,
                float sourceWidth,
                float sourceHeight,
                float zoom
        ) {
            float safeSourceWidth = clamp(sourceWidth, 1f, base.desktopWidth);
            float safeSourceHeight = clamp(sourceHeight, 1f, base.desktopHeight);
            float left = clamp(sourceLeft, 0f, base.desktopWidth - safeSourceWidth);
            float top = clamp(sourceTop, 0f, base.desktopHeight - safeSourceHeight);
            return new CameraState(
                    viewWidth,
                    viewHeight,
                    base.viewportLeft,
                    base.viewportTop,
                    base.viewportWidth,
                    base.viewportHeight,
                    base.desktopWidth,
                    base.desktopHeight,
                    left,
                    top,
                    safeSourceWidth,
                    safeSourceHeight,
                    zoom
            );
        }

        private static int rendererZoomPercentFor(int viewWidth, int viewHeight, int viewportWidth, int viewportHeight, float zoom) {
            return clampPercent(Math.round(MIN_RENDERER_ZOOM_PERCENT * clamp(zoom, MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)));
        }
    }

    private static final class SourceRect {
        final float left;
        final float top;
        final float width;
        final float height;

        SourceRect(float left, float top, float width, float height) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
        }
    }
}
