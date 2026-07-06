package com.kite.app.browser.automation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream

class BrowserAutomationController(
    private val webView: WebView,
    private val store: BrowserAutomationSessionStore,
    private val onEvent: (BrowserAutomationEvent) -> Unit
) {
    @Volatile
    private var activeSessionId: String? = null

    fun prepareLoad(
        enabled: Boolean,
        recipeId: String?,
        recipeName: String?,
        instanceId: String?,
        source: String?,
        url: String
    ) {
        if (!enabled) {
            closeActiveSession()
            return
        }
        val session = store.startSession(
            recipeId = recipeId,
            recipeName = recipeName,
            instanceId = instanceId,
            source = source,
            url = url
        )
        activeSessionId = session.sessionId
        BrowserAutomationControllerRegistry.register(session.sessionId, this)
        onEvent(
            BrowserAutomationEvent(
                kind = BrowserAutomationEventKind.SessionOpening,
                session = session,
                message = "自动浏览器正在打开页面"
            )
        )
    }

    fun onPageFinished(url: String, title: String?) {
        val sessionId = activeSessionId ?: return
        val session = store.get(sessionId) ?: return
        if (session.status == BrowserAutomationSessionStatus.Closed ||
            session.status == BrowserAutomationSessionStatus.Failed
        ) {
            return
        }
        captureSnapshot(sessionId, url, title)
    }

    fun markNavigationBlocked(url: String, reason: String) {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        val errorCode = if (reason == "auth_handoff") {
            "navigation_blocked_for_auth"
        } else {
            "navigation_left_webview"
        }
        emitFailure(
            sessionId = sessionId,
            errorCode = errorCode,
            detail = "$reason:${BrowserAutomationRedactor.redactUrl(url)}"
        )
    }

    fun closeActiveSession() {
        activeSessionId?.let {
            store.close(it)
            BrowserAutomationControllerRegistry.unregister(it)
        }
        activeSessionId = null
    }

    fun performAction(
        action: BrowserAutomationAction,
        onResult: (BrowserAutomationActionResult) -> Unit
    ) {
        val session = resolveSession(action)
        if (session == null) {
            onResult(
                BrowserAutomationActionScript.rejectedResult(
                    action = action,
                    sessionId = action.sessionId ?: action.instanceId ?: "missing_session",
                    errorCode = "session_not_found",
                    detail = "自动浏览器 session 不存在"
                )
            )
            return
        }
        if (session.status == BrowserAutomationSessionStatus.Closed) {
            onResult(
                BrowserAutomationActionScript.rejectedResult(
                    action = action,
                    sessionId = session.sessionId,
                    errorCode = "session_closed",
                    detail = "自动浏览器 session 已关闭"
                )
            )
            return
        }
        activeSessionId = session.sessionId
        store.markActionRunning(session.sessionId, action)
        if (action.type == BrowserAutomationActionType.Snapshot) {
            captureSnapshotForAction(session.sessionId, action, onResult)
            return
        }
        if (action.type == BrowserAutomationActionType.Screenshot) {
            captureScreenshotForAction(session.sessionId, action, onResult)
            return
        }
        if (action.type == BrowserAutomationActionType.Evaluate &&
            !BrowserAutomationPageTrust.evaluateAllowed(webView.url ?: session.url)
        ) {
            finishAction(
                BrowserAutomationActionScript.rejectedResult(
                    action = action,
                    sessionId = session.sessionId,
                    errorCode = "untrusted_evaluate_blocked",
                    detail = "evaluate 只允许本地或可信页面"
                ).copy(url = webView.url.orEmpty(), title = webView.title),
                onResult,
                refreshSnapshot = false
            )
            return
        }
        val startedAt = System.currentTimeMillis()
        runActionAttempt(
            sessionId = session.sessionId,
            action = action,
            startedAt = startedAt,
            onResult = onResult
        )
    }

    fun recordConsoleMessage(
        level: String,
        message: String,
        sourceId: String?,
        lineNumber: Int
    ) {
        val sessionId = activeSessionId ?: return
        val session = store.get(sessionId) ?: return
        if (session.status == BrowserAutomationSessionStatus.Closed) return
        val now = System.currentTimeMillis()
        store.saveConsoleEntry(
            BrowserAutomationConsoleEntry(
                entryId = "console_${sessionId}_$now",
                sessionId = sessionId,
                level = level,
                message = message,
                sourceId = sourceId,
                lineNumber = lineNumber,
                capturedAt = now
            )
        )
    }

    fun recordNetworkRequest(
        method: String,
        url: String,
        isForMainFrame: Boolean
    ) {
        recordNetworkEntry(
            kind = "request",
            method = method,
            url = url,
            isForMainFrame = isForMainFrame,
            statusCode = null,
            reasonPhrase = null
        )
    }

    fun recordNetworkHttpError(
        method: String,
        url: String,
        isForMainFrame: Boolean,
        statusCode: Int,
        reasonPhrase: String?
    ) {
        recordNetworkEntry(
            kind = "httpError",
            method = method,
            url = url,
            isForMainFrame = isForMainFrame,
            statusCode = statusCode,
            reasonPhrase = reasonPhrase
        )
    }

    private fun recordNetworkEntry(
        kind: String,
        method: String,
        url: String,
        isForMainFrame: Boolean,
        statusCode: Int?,
        reasonPhrase: String?
    ) {
        val sessionId = activeSessionId ?: return
        val session = store.get(sessionId) ?: return
        if (session.status == BrowserAutomationSessionStatus.Closed) return
        val now = System.currentTimeMillis()
        val fingerprint = Integer.toHexString((kind + method + url + (statusCode ?: 0)).hashCode())
        store.saveNetworkEntry(
            BrowserAutomationNetworkEntry(
                entryId = "net_${sessionId}_${now}_$fingerprint",
                sessionId = sessionId,
                kind = kind,
                method = method.ifBlank { "GET" }.uppercase().take(16),
                url = url,
                isForMainFrame = isForMainFrame,
                statusCode = statusCode,
                reasonPhrase = reasonPhrase,
                capturedAt = now
            )
        )
    }

    private fun captureSnapshot(
        sessionId: String,
        fallbackUrl: String,
        fallbackTitle: String?
    ) {
        webView.evaluateJavascript(SNAPSHOT_SCRIPT) { rawResult ->
            runCatching {
                BrowserAutomationSnapshotParser.parseEvaluateJavascriptResult(
                    sessionId = sessionId,
                    rawResult = rawResult,
                    fallbackUrl = fallbackUrl,
                    fallbackTitle = fallbackTitle
                )
            }.onSuccess { snapshot ->
                val session = store.markReady(sessionId, snapshot) ?: return@onSuccess
                onEvent(
                    BrowserAutomationEvent(
                        kind = BrowserAutomationEventKind.SnapshotReady,
                        session = session,
                        snapshot = snapshot,
                        message = "自动浏览器已采集页面快照"
                    )
                )
            }.onFailure { error ->
                emitFailure(
                    sessionId = sessionId,
                    errorCode = "snapshot_failed",
                    detail = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    private fun captureSnapshotForAction(
        sessionId: String,
        action: BrowserAutomationAction,
        onResult: (BrowserAutomationActionResult) -> Unit
    ) {
        val startedAt = System.currentTimeMillis()
        webView.evaluateJavascript(SNAPSHOT_SCRIPT) { rawResult ->
            val result = runCatching {
                val snapshot = BrowserAutomationSnapshotParser.parseEvaluateJavascriptResult(
                    sessionId = sessionId,
                    rawResult = rawResult,
                    fallbackUrl = webView.url.orEmpty(),
                    fallbackTitle = webView.title
                )
                val session = store.markReady(sessionId, snapshot)
                if (session != null) {
                    onEvent(
                        BrowserAutomationEvent(
                            kind = BrowserAutomationEventKind.SnapshotReady,
                            session = session,
                            snapshot = snapshot,
                            message = "自动浏览器已采集页面快照"
                        )
                    )
                }
                BrowserAutomationActionResult(
                    actionId = action.actionId,
                    sessionId = sessionId,
                    type = action.type,
                    status = BrowserAutomationResultStatus.Succeeded,
                    durationMs = System.currentTimeMillis() - startedAt,
                    url = snapshot.url,
                    title = snapshot.title,
                    message = "snapshot captured",
                    snapshotId = snapshot.snapshotId
                )
            }.getOrElse { error ->
                BrowserAutomationActionResult(
                    actionId = action.actionId,
                    sessionId = sessionId,
                    type = action.type,
                    status = BrowserAutomationResultStatus.Failed,
                    durationMs = System.currentTimeMillis() - startedAt,
                    url = webView.url.orEmpty(),
                    title = webView.title,
                    message = "snapshot failed",
                    errorCode = "snapshot_failed",
                    errorDetail = error.message ?: error.javaClass.simpleName
                )
            }
            finishAction(result, onResult)
        }
    }

    private fun captureScreenshotForAction(
        sessionId: String,
        action: BrowserAutomationAction,
        onResult: (BrowserAutomationActionResult) -> Unit
    ) {
        val startedAt = System.currentTimeMillis()
        val result = runCatching {
            val width = webView.width.takeIf { it > 0 } ?: webView.measuredWidth
            val height = webView.height.takeIf { it > 0 } ?: webView.measuredHeight
            if (width <= 0 || height <= 0) {
                error("webview_has_no_size")
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                webView.draw(Canvas(bitmap))
                val dir = File(webView.context.filesDir, "browser-automation/screenshots").apply {
                    mkdirs()
                }
                val file = File(dir, "shot_${sessionId}_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { output ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                }
                BrowserAutomationActionResult(
                    actionId = action.actionId,
                    sessionId = sessionId,
                    type = action.type,
                    status = BrowserAutomationResultStatus.Succeeded,
                    durationMs = System.currentTimeMillis() - startedAt,
                    url = webView.url.orEmpty(),
                    title = webView.title,
                    message = "screenshot captured",
                    artifactPath = file.absolutePath
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrElse { error ->
            BrowserAutomationActionResult(
                actionId = action.actionId,
                sessionId = sessionId,
                type = action.type,
                status = BrowserAutomationResultStatus.Failed,
                durationMs = System.currentTimeMillis() - startedAt,
                url = webView.url.orEmpty(),
                title = webView.title,
                message = "screenshot failed",
                errorCode = "screenshot_failed",
                errorDetail = error.message ?: error.javaClass.simpleName
            )
        }
        finishAction(result, onResult, refreshSnapshot = false)
    }

    private fun runActionAttempt(
        sessionId: String,
        action: BrowserAutomationAction,
        startedAt: Long,
        onResult: (BrowserAutomationActionResult) -> Unit
    ) {
        webView.evaluateJavascript(BrowserAutomationActionScript.scriptFor(action)) { rawResult ->
            val result = runCatching {
                BrowserAutomationActionScript.parseResult(
                    sessionId = sessionId,
                    action = action,
                    rawResult = rawResult,
                    startedAt = startedAt
                )
            }.getOrElse { error ->
                BrowserAutomationActionResult(
                    actionId = action.actionId,
                    sessionId = sessionId,
                    type = action.type,
                    status = BrowserAutomationResultStatus.Failed,
                    durationMs = System.currentTimeMillis() - startedAt,
                    url = webView.url.orEmpty(),
                    title = webView.title,
                    message = "action failed",
                    errorCode = "script_error",
                    errorDetail = error.message ?: error.javaClass.simpleName
                )
            }
            if (action.type == BrowserAutomationActionType.WaitFor &&
                !result.succeeded &&
                System.currentTimeMillis() - startedAt < action.timeoutMs
            ) {
                webView.postDelayed(
                    {
                        runActionAttempt(
                            sessionId = sessionId,
                            action = action,
                            startedAt = startedAt,
                            onResult = onResult
                        )
                    },
                    WAIT_POLL_MS
                )
            } else if (action.type == BrowserAutomationActionType.WaitFor && !result.succeeded) {
                finishAction(
                    BrowserAutomationActionScript.timedOutResult(action, sessionId, startedAt),
                    onResult
                )
            } else {
                finishAction(result, onResult)
            }
        }
    }

    private fun finishAction(
        result: BrowserAutomationActionResult,
        onResult: (BrowserAutomationActionResult) -> Unit,
        refreshSnapshot: Boolean = result.succeeded &&
            result.type != BrowserAutomationActionType.Snapshot &&
            result.type != BrowserAutomationActionType.Screenshot
    ) {
        if (refreshSnapshot) {
            captureSnapshotAfterAction(result, onResult)
            return
        }
        completeAction(result, onResult)
    }

    private fun captureSnapshotAfterAction(
        result: BrowserAutomationActionResult,
        onResult: (BrowserAutomationActionResult) -> Unit
    ) {
        webView.evaluateJavascript(SNAPSHOT_SCRIPT) { rawResult ->
            val resultWithSnapshot = runCatching {
                val snapshot = BrowserAutomationSnapshotParser.parseEvaluateJavascriptResult(
                    sessionId = result.sessionId,
                    rawResult = rawResult,
                    fallbackUrl = webView.url.orEmpty(),
                    fallbackTitle = webView.title
                )
                val session = store.markReady(result.sessionId, snapshot)
                if (session != null) {
                    onEvent(
                        BrowserAutomationEvent(
                            kind = BrowserAutomationEventKind.SnapshotReady,
                            session = session,
                            snapshot = snapshot,
                            message = "自动浏览器已采集页面快照"
                        )
                    )
                }
                result.copy(
                    url = snapshot.url,
                    title = snapshot.title,
                    snapshotId = snapshot.snapshotId,
                    completedAt = System.currentTimeMillis()
                )
            }.getOrDefault(result)
            completeAction(resultWithSnapshot, onResult)
        }
    }

    private fun completeAction(
        result: BrowserAutomationActionResult,
        onResult: (BrowserAutomationActionResult) -> Unit
    ) {
        val session = store.markActionResult(result) ?: store.get(result.sessionId)
        if (session != null) {
            onEvent(
                BrowserAutomationEvent(
                    kind = BrowserAutomationEventKind.ActionFinished,
                    session = session,
                    actionResult = result,
                    message = if (result.succeeded) {
                        "自动浏览器动作完成：${result.type.wireName}"
                    } else {
                        "自动浏览器动作失败：${result.errorCode ?: result.status.name}"
                    },
                    errorCode = result.errorCode
                )
            )
        }
        onResult(result)
    }

    private fun resolveSession(action: BrowserAutomationAction): BrowserAutomationSession? =
        action.sessionId
            ?.takeIf { it.isNotBlank() }
            ?.let(store::get)
            ?: action.instanceId
                ?.takeIf { it.isNotBlank() }
                ?.let(store::latestForInstance)
            ?: activeSessionId
                ?.takeIf { it.isNotBlank() }
                ?.let(store::get)
            ?: store.latestOpenSession()

    private fun emitFailure(
        sessionId: String,
        errorCode: String,
        detail: String
    ) {
        val session = store.markFailed(sessionId, "$errorCode:$detail") ?: return
        onEvent(
            BrowserAutomationEvent(
                kind = BrowserAutomationEventKind.Failed,
                session = session,
                message = "自动浏览器失败：$detail",
                errorCode = errorCode
            )
        )
    }

    companion object {
        private val SNAPSHOT_SCRIPT = """
            (function () {
              try {
                var clean = function (value, limit) {
                  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, limit);
                };
                var rectOf = function (el, context) {
                  var rect = el.getBoundingClientRect();
                  var offsetX = context ? Number(context.offsetX || 0) : 0;
                  var offsetY = context ? Number(context.offsetY || 0) : 0;
                  return {
                    x: Math.round((rect.x + offsetX) * 10) / 10,
                    y: Math.round((rect.y + offsetY) * 10) / 10,
                    width: Math.round(rect.width * 10) / 10,
                    height: Math.round(rect.height * 10) / 10
                  };
                };
                var visible = function (el) {
                  return !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);
                };
                var safeValueOf = function (el) {
                  var tag = String(el.tagName || '').toLowerCase();
                  var type = String(el.getAttribute('type') || '').toLowerCase();
                  return tag === 'input' && type === 'password' ? '' : (el.value || '');
                };
                var roleOf = function (el) {
                  var explicit = el.getAttribute('role') || '';
                  if (explicit) return explicit;
                  var tag = String(el.tagName || '').toLowerCase();
                  var type = String(el.getAttribute('type') || '').toLowerCase();
                  if (tag === 'a' && el.getAttribute('href')) return 'link';
                  if (tag === 'button') return 'button';
                  if (tag === 'input') {
                    if (type === 'button' || type === 'submit' || type === 'reset') return 'button';
                    if (type === 'checkbox') return 'checkbox';
                    if (type === 'radio') return 'radio';
                    if (type === 'range') return 'slider';
                    return 'textbox';
                  }
                  if (tag === 'textarea') return 'textbox';
                  if (tag === 'select') return 'combobox';
                  if (/^h[1-6]$/.test(tag)) return 'heading';
                  if (tag === 'main') return 'main';
                  if (tag === 'nav') return 'navigation';
                  if (tag === 'section') return 'region';
                  if (tag === 'img') return 'img';
                  if (tag === 'label') return 'label';
                  return 'generic';
                };
                var rootOf = function (el) {
                  return (el && el.getRootNode && el.getRootNode()) || ((el && el.ownerDocument) || document);
                };
                var findIdInRoot = function (root, doc, id) {
                  if (!id) return null;
                  var found = null;
                  if (root && root.getElementById) found = root.getElementById(id);
                  if (!found && root && root.querySelector) {
                    try {
                      found = root.querySelector('[id="' + String(id).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"]');
                    } catch (ignored) {}
                  }
                  if (!found && doc && doc.getElementById) found = doc.getElementById(id);
                  return found;
                };
                var labelledBy = function (el) {
                  var ids = String(el.getAttribute('aria-labelledby') || '').split(/\s+/).filter(Boolean);
                  var doc = (el && el.ownerDocument) || document;
                  var root = rootOf(el);
                  return ids.map(function (id) {
                    return clean((findIdInRoot(root, doc, id) || {}).innerText || '', 160);
                  }).filter(Boolean).join(' ');
                };
                var labelFor = function (el) {
                  var id = el.getAttribute('id') || '';
                  if (!id) return '';
                  var doc = (el && el.ownerDocument) || document;
                  var root = rootOf(el);
                  var labelSelector = 'label[for="' + String(id).replace(/"/g, '\\"') + '"]';
                  var label = null;
                  if (root && root.querySelector) label = root.querySelector(labelSelector);
                  if (!label && doc.querySelector) label = doc.querySelector(labelSelector);
                  return label ? clean(label.innerText || '', 160) : '';
                };
                var accessibleNameOf = function (el) {
                  var tag = String(el.tagName || '').toLowerCase();
                  var type = String(el.getAttribute('type') || '').toLowerCase();
                  var pieces = [
                    el.getAttribute('aria-label') || '',
                    labelledBy(el),
                    el.getAttribute('alt') || '',
                    labelFor(el),
                    el.getAttribute('placeholder') || '',
                    el.innerText || ''
                  ];
                  return clean(pieces.filter(Boolean).join(' '), 200);
                };
                var stateValue = function (el, attr) {
                  var value = el.getAttribute(attr);
                  return value === null ? '' : String(value);
                };
                var frameNameOf = function (frameEl, index) {
                  return clean(
                    frameEl.getAttribute('title') ||
                    frameEl.getAttribute('aria-label') ||
                    frameEl.getAttribute('name') ||
                    frameEl.getAttribute('id') ||
                    ('frame-' + index),
                    160
                  );
                };
                var frameUrlOf = function (frameEl, childDoc) {
                  try {
                    return String(frameEl.getAttribute('src') || (childDoc && childDoc.location && childDoc.location.href) || '');
                  } catch (ignored) {
                    return String(frameEl.getAttribute('src') || '');
                  }
                };
                var collectDocumentContexts = function () {
                  var contexts = [];
                  var frames = [];
                  var shadowHostLabel = function (host) {
                    if (!host) return '';
                    var tag = String(host.tagName || '').toLowerCase();
                    var id = host.getAttribute('id') || '';
                    var label = host.getAttribute('aria-label') || host.getAttribute('title') || '';
                    return clean([tag, id ? '#' + id : '', label].join(' '), 160);
                  };
                  var visitOpenShadows = function (root, baseContext, depth) {
                    if (!root || depth > 4) return;
                    Array.prototype.slice.call(root.querySelectorAll('*')).forEach(function (host, index) {
                      if (!host.shadowRoot) return;
                      var shadowPath = (baseContext.shadowPath || '') + '/shadow[' + index + ']';
                      var context = {
                        doc: baseContext.doc,
                        root: host.shadowRoot,
                        framePath: baseContext.framePath,
                        frameUrl: baseContext.frameUrl,
                        frameName: baseContext.frameName,
                        shadowPath: shadowPath,
                        shadowHost: shadowHostLabel(host),
                        offsetX: baseContext.offsetX || 0,
                        offsetY: baseContext.offsetY || 0
                      };
                      contexts.push(context);
                      visitOpenShadows(host.shadowRoot, context, depth + 1);
                    });
                  };
                  var visit = function (doc, path, frameEl, depth, offsetX, offsetY) {
                    if (!doc || depth > 4) return;
                    var context = {
                      doc: doc,
                      root: doc,
                      framePath: path,
                      frameUrl: frameEl ? frameUrlOf(frameEl, doc) : String(location.href || ''),
                      frameName: frameEl ? frameNameOf(frameEl, contexts.length) : '',
                      shadowPath: '',
                      shadowHost: '',
                      offsetX: offsetX || 0,
                      offsetY: offsetY || 0
                    };
                    contexts.push(context);
                    visitOpenShadows(doc, context, depth);
                    Array.prototype.slice.call(doc.querySelectorAll('iframe,frame')).forEach(function (childFrame, index) {
                      var childPath = path + '/frame[' + index + ']';
                      var childName = frameNameOf(childFrame, index);
                      var rect = rectOf(childFrame, context);
                      var summary = {
                        role: 'iframe',
                        name: childName,
                        tag: String(childFrame.tagName || 'iframe').toLowerCase(),
                        type: '',
                        level: null,
                        visible: visible(childFrame),
                        enabled: true,
                        checked: '',
                        selected: null,
                        expanded: null,
                        rect: rect,
                        framePath: childPath,
                        frameUrl: frameUrlOf(childFrame, null),
                        frameName: childName,
                        frameAccessible: false
                      };
                      try {
                        var childDoc = childFrame.contentDocument || (childFrame.contentWindow && childFrame.contentWindow.document);
                        if (childDoc && childDoc.documentElement) {
                          summary.frameAccessible = true;
                          summary.frameUrl = frameUrlOf(childFrame, childDoc);
                          frames.push(summary);
                          visit(childDoc, childPath, childFrame, depth + 1, rect.x, rect.y);
                          return;
                        }
                      } catch (ignored) {}
                      frames.push(summary);
                    });
                  };
                  visit(document, 'top', null, 0, 0, 0);
                  return { contexts: contexts, frames: frames };
                };
                var frameData = collectDocumentContexts();
                var contexts = frameData.contexts;
                var controls = [];
                contexts.forEach(function (context) {
                  Array.prototype.slice.call(
                    context.root.querySelectorAll('a,button,input,textarea,select,[role],[aria-label],[placeholder]')
                  ).forEach(function (el) {
                    if (controls.length >= 80) return;
                    controls.push({
                      index: controls.length,
                      tag: String(el.tagName || '').toLowerCase(),
                      type: el.getAttribute('type') || '',
                      role: el.getAttribute('role') || '',
                      text: clean(el.innerText || safeValueOf(el) || '', 160),
                      placeholder: clean(el.getAttribute('placeholder') || '', 160),
                      ariaLabel: clean(el.getAttribute('aria-label') || '', 160),
                      visible: visible(el),
                      enabled: !el.disabled,
                      framePath: context.framePath === 'top' ? '' : context.framePath,
                      frameUrl: context.framePath === 'top' ? '' : context.frameUrl,
                      frameName: context.framePath === 'top' ? '' : context.frameName,
                      shadowPath: context.shadowPath || '',
                      shadowHost: context.shadowHost || '',
                      rect: rectOf(el, context)
                    });
                  });
                });
                var semanticSelector = 'a[href],button,input,textarea,select,[role],[aria-label],[aria-labelledby],[placeholder],label,h1,h2,h3,h4,h5,h6,main,nav,section,img';
                var accessibility = [];
                frameData.frames.forEach(function (frame) {
                  if (accessibility.length >= 120) return;
                  frame.index = accessibility.length;
                  accessibility.push(frame);
                });
                contexts.forEach(function (context) {
                  Array.prototype.slice.call(context.root.querySelectorAll(semanticSelector))
                  .filter(function (el) { return visible(el) && el.getAttribute('aria-hidden') !== 'true'; })
                  .forEach(function (el) {
                    if (accessibility.length >= 120) return;
                    var tag = String(el.tagName || '').toLowerCase();
                    var type = String(el.getAttribute('type') || '').toLowerCase();
                    var role = roleOf(el);
                    var checked = '';
                    if (role === 'checkbox' || role === 'radio' || role === 'switch') {
                      checked = stateValue(el, 'aria-checked') || String(!!el.checked);
                    }
                    var node = {
                      index: accessibility.length,
                      role: role,
                      name: accessibleNameOf(el),
                      tag: tag,
                      type: type,
                      level: /^h[1-6]$/.test(tag) ? Number(tag.substring(1)) : Number(el.getAttribute('aria-level') || 0) || null,
                      visible: visible(el),
                      enabled: !el.disabled && el.getAttribute('aria-disabled') !== 'true',
                      checked: checked,
                      selected: el.hasAttribute('aria-selected') ? el.getAttribute('aria-selected') === 'true' : null,
                      expanded: el.hasAttribute('aria-expanded') ? el.getAttribute('aria-expanded') === 'true' : null,
                      rect: rectOf(el, context),
                      framePath: context.framePath === 'top' ? '' : context.framePath,
                      frameUrl: context.framePath === 'top' ? '' : context.frameUrl,
                      frameName: context.framePath === 'top' ? '' : context.frameName,
                      frameAccessible: context.framePath === 'top' ? null : true,
                      shadowPath: context.shadowPath || '',
                      shadowHost: context.shadowHost || ''
                    };
                    if (node.role !== 'generic' || node.name) accessibility.push(node);
                  });
                });
                var visibleText = contexts.map(function (context) {
                  if (context.root === context.doc) return context.doc.body ? context.doc.body.innerText : '';
                  return context.root.textContent || '';
                }).filter(Boolean).join(' ');
                var totalElementCount = contexts.reduce(function (count, context) {
                  return count + context.root.querySelectorAll('*').length;
                }, 0);
                return JSON.stringify({
                  ok: true,
                  url: String(location.href || ''),
                  title: String(document.title || ''),
                  readyState: String(document.readyState || ''),
                  text: clean(visibleText, 4000),
                  elementCount: totalElementCount,
                  elements: controls,
                  accessibility: accessibility
                });
              } catch (error) {
                return JSON.stringify({
                  ok: false,
                  error: String(error && error.message ? error.message : error)
                });
              }
            })();
        """.trimIndent()

        private const val WAIT_POLL_MS = 200L
    }
}
