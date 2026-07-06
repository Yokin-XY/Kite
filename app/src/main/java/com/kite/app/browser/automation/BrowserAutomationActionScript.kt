package com.kite.app.browser.automation

import org.json.JSONArray
import org.json.JSONObject

object BrowserAutomationActionScript {
    fun scriptFor(action: BrowserAutomationAction): String {
        val actionJson = JSONObject.quote(action.toJson().toString())
        return """
            (function () {
              var startedAt = Date.now();
              try {
                var action = JSON.parse($actionJson);
                var clean = function (value, limit) {
                  return String(value || '').replace(/\s+/g, ' ').trim().slice(0, limit);
                };
                var visible = function (el) {
                  return !!(el && (el.offsetWidth || el.offsetHeight || el.getClientRects().length));
                };
                var hasAriaState = function (el, attr) {
                  var node = el;
                  while (node && node.nodeType === 1) {
                    if (String(node.getAttribute(attr) || '').toLowerCase() === 'true') return true;
                    node = node.parentElement;
                  }
                  return false;
                };
                var disabledForAutomation = function (el) {
                  if (!el) return false;
                  if (el.disabled === true) return true;
                  try {
                    if (el.matches && el.matches(':disabled')) return true;
                  } catch (ignored) {}
                  return hasAriaState(el, 'aria-disabled');
                };
                var readonlyForAutomation = function (el) {
                  if (!el) return false;
                  if (el.readOnly === true) return true;
                  return hasAriaState(el, 'aria-readonly');
                };
                var safeValueOf = function (el) {
                  var tag = String((el && el.tagName) || '').toLowerCase();
                  var type = String((el && el.getAttribute('type')) || '').toLowerCase();
                  return tag === 'input' && type === 'password' ? '' : ((el && el.value) || '');
                };
                var labelOf = function (el) {
                  if (!el) return '';
                  return clean([
                    el.innerText || '',
                    safeValueOf(el),
                    el.getAttribute('placeholder') || '',
                    el.getAttribute('aria-label') || '',
                    el.getAttribute('role') || ''
                  ].join(' '), 500);
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
                  var selectorId = String(id).replace(/"/g, '\\"');
                  var doc = (el && el.ownerDocument) || document;
                  var root = rootOf(el);
                  var label = null;
                  if (root && root.querySelector) label = root.querySelector('label[for="' + selectorId + '"]');
                  if (!label && doc.querySelector) label = doc.querySelector('label[for="' + selectorId + '"]');
                  return label ? clean(label.innerText || '', 160) : '';
                };
                var accessibleNameOf = function (el) {
                  if (!el) return '';
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
                  return clean(pieces.filter(Boolean).join(' '), 500);
                };
                var matchesText = function (value, query, match) {
                  var left = String(value || '').toLowerCase();
                  var right = String(query || '').toLowerCase();
                  if (!right) return false;
                  return match === 'exact' ? left === right : left.indexOf(right) >= 0;
                };
                var targetDetail = function (target) {
                  var detail = String(target.kind || 'none') + '=' + clean(target.value || '', 120);
                  if (target.name) detail += ' name=' + clean(target.name || '', 120);
                  return detail;
                };
                var frameNameOf = function (frameEl, index) {
                  if (!frameEl) return '';
                  return clean(
                    frameEl.getAttribute('title') ||
                    frameEl.getAttribute('aria-label') ||
                    frameEl.getAttribute('name') ||
                    frameEl.getAttribute('id') ||
                    ('frame-' + index),
                    160
                  );
                };
                var collectDocumentContexts = function () {
                  var contexts = [];
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
                        shadowHost: shadowHostLabel(host)
                      };
                      contexts.push(context);
                      visitOpenShadows(host.shadowRoot, context, depth + 1);
                    });
                  };
                  var visit = function (doc, path, frameEl, depth) {
                    if (!doc || depth > 4) return;
                    var context = {
                      doc: doc,
                      root: doc,
                      framePath: path,
                      frameUrl: frameEl ? String(frameEl.getAttribute('src') || doc.location.href || '') : String(location.href || ''),
                      frameName: frameEl ? frameNameOf(frameEl, contexts.length) : '',
                      shadowPath: '',
                      shadowHost: ''
                    };
                    contexts.push(context);
                    visitOpenShadows(doc, context, depth);
                    Array.prototype.slice.call(doc.querySelectorAll('iframe,frame')).forEach(function (childFrame, index) {
                      try {
                        var childDoc = childFrame.contentDocument || (childFrame.contentWindow && childFrame.contentWindow.document);
                        if (childDoc && childDoc.documentElement) {
                          visit(childDoc, path + '/frame[' + index + ']', childFrame, depth + 1);
                        }
                      } catch (ignored) {}
                    });
                  };
                  visit(document, 'top', null, 0);
                  return contexts;
                };
                var frameContextOf = function (el) {
                  var doc = (el && el.ownerDocument) || document;
                  var root = (el && el.getRootNode && el.getRootNode()) || doc;
                  var contexts = collectDocumentContexts();
                  for (var index = 0; index < contexts.length; index += 1) {
                    if (contexts[index].root === root) return contexts[index];
                  }
                  return { framePath: 'top', frameUrl: String(location.href || ''), frameName: '', shadowPath: '', shadowHost: '' };
                };
                var queryElements = function (target) {
                  var kind = String(target.kind || 'none');
                  var value = String(target.value || '');
                  var targetName = String(target.name || '');
                  var match = String(target.match || 'contains');
                  if (kind === 'none') return [];
                  var contexts = collectDocumentContexts();
                  if (kind === 'css') {
                    return contexts.reduce(function (all, ctx) {
                      try {
                        return all.concat(Array.prototype.slice.call(ctx.root.querySelectorAll(value)));
                      } catch (ignored) {
                        return all;
                      }
                    }, []);
                  }
                  var pool = contexts.reduce(function (all, ctx) {
                    try {
                      return all.concat(Array.prototype.slice.call(
                        ctx.root.querySelectorAll('a[href],button,input,textarea,select,[role],[aria-label],[aria-labelledby],[placeholder],label,span,div,p,h1,h2,h3,h4,h5,h6,main,nav,section,img')
                      ));
                    } catch (ignored) {
                      return all;
                    }
                  }, []);
                  return pool.filter(function (el) {
                    if (!visible(el)) return false;
                    if (kind === 'role') {
                      var role = roleOf(el);
                      var name = accessibleNameOf(el);
                      if (targetName) {
                        var roleMatches = value ? matchesText(role, value, 'exact') : true;
                        return roleMatches && matchesText(name, targetName, match);
                      }
                      var roleText = clean([role, name, labelOf(el)].join(' '), 500);
                      return matchesText(roleText, value, match);
                    }
                    return matchesText(labelOf(el), value, match);
                  });
                };
                var elementSummary = function (el) {
                  if (!el) return null;
                  var rect = el.getBoundingClientRect();
                  var frame = frameContextOf(el);
                  return {
                    tag: String(el.tagName || '').toLowerCase(),
                    text: clean(labelOf(el), 160),
                    visible: visible(el),
                    enabled: !disabledForAutomation(el),
                    framePath: frame.framePath || '',
                    frameUrl: frame.frameUrl || '',
                    frameName: frame.frameName || '',
                    shadowPath: frame.shadowPath || '',
                    shadowHost: frame.shadowHost || '',
                    rect: {
                      x: Math.round(rect.x * 10) / 10,
                      y: Math.round(rect.y * 10) / 10,
                      width: Math.round(rect.width * 10) / 10,
                      height: Math.round(rect.height * 10) / 10
                    }
                  };
                };
                var stringifyValue = function (value) {
                  if (value === undefined) return 'undefined';
                  if (value === null) return 'null';
                  if (typeof value === 'string') return value;
                  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
                  try {
                    return JSON.stringify(value);
                  } catch (ignored) {
                    return Object.prototype.toString.call(value);
                  }
                };
                var normalizePressKey = function (raw) {
                  var original = String(raw || '').trim();
                  if (!original) return { supported: false, errorCode: 'missing_press_key', errorDetail: 'press requires value' };
                  var compact = original.toLowerCase().replace(/[\s_\-]+/g, '');
                  var aliases = {
                    enter: { key: 'Enter', code: 'Enter', keyCode: 13, label: 'Enter', sendKeyPress: true },
                    return: { key: 'Enter', code: 'Enter', keyCode: 13, label: 'Enter', sendKeyPress: true },
                    escape: { key: 'Escape', code: 'Escape', keyCode: 27, label: 'Escape', sendKeyPress: false },
                    esc: { key: 'Escape', code: 'Escape', keyCode: 27, label: 'Escape', sendKeyPress: false },
                    tab: { key: 'Tab', code: 'Tab', keyCode: 9, label: 'Tab', sendKeyPress: false },
                    space: { key: ' ', code: 'Space', keyCode: 32, label: 'Space', sendKeyPress: true },
                    spacebar: { key: ' ', code: 'Space', keyCode: 32, label: 'Space', sendKeyPress: true },
                    backspace: { key: 'Backspace', code: 'Backspace', keyCode: 8, label: 'Backspace', sendKeyPress: false },
                    delete: { key: 'Delete', code: 'Delete', keyCode: 46, label: 'Delete', sendKeyPress: false },
                    del: { key: 'Delete', code: 'Delete', keyCode: 46, label: 'Delete', sendKeyPress: false },
                    arrowup: { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38, label: 'ArrowUp', sendKeyPress: false },
                    up: { key: 'ArrowUp', code: 'ArrowUp', keyCode: 38, label: 'ArrowUp', sendKeyPress: false },
                    arrowdown: { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, label: 'ArrowDown', sendKeyPress: false },
                    down: { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, label: 'ArrowDown', sendKeyPress: false },
                    arrowleft: { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37, label: 'ArrowLeft', sendKeyPress: false },
                    left: { key: 'ArrowLeft', code: 'ArrowLeft', keyCode: 37, label: 'ArrowLeft', sendKeyPress: false },
                    arrowright: { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39, label: 'ArrowRight', sendKeyPress: false },
                    right: { key: 'ArrowRight', code: 'ArrowRight', keyCode: 39, label: 'ArrowRight', sendKeyPress: false }
                  };
                  if (aliases[compact]) return Object.assign({ supported: true }, aliases[compact]);
                  if (original.length === 1) {
                    var keyCode = original.toUpperCase().charCodeAt(0);
                    var code = /^[a-z]$/i.test(original) ? 'Key' + original.toUpperCase() : (/^[0-9]$/.test(original) ? 'Digit' + original : '');
                    return { supported: true, key: original, code: code, keyCode: keyCode, label: original, sendKeyPress: true };
                  }
                  return {
                    supported: false,
                    errorCode: 'unsupported_key',
                    errorDetail: 'press supports Enter, Escape, Tab, Space, Backspace, Delete, Arrow keys and single characters'
                  };
                };
                var dispatchKeyboard = function (el, type, keyInfo) {
                  var event = new KeyboardEvent(type, {
                    key: keyInfo.key,
                    code: keyInfo.code || '',
                    keyCode: keyInfo.keyCode || 0,
                    which: keyInfo.keyCode || 0,
                    bubbles: true,
                    cancelable: true
                  });
                  return el.dispatchEvent(event);
                };
                var eventPointOf = function (el) {
                  var rect = el.getBoundingClientRect();
                  return {
                    x: Math.round((rect.left + Math.max(1, rect.width) / 2) * 10) / 10,
                    y: Math.round((rect.top + Math.max(1, rect.height) / 2) * 10) / 10
                  };
                };
                var dispatchPointerMouse = function (el, type, point, pointer, pressed, detail) {
                  var view = (el.ownerDocument && el.ownerDocument.defaultView) || window;
                  var init = {
                    view: view,
                    bubbles: true,
                    cancelable: true,
                    composed: true,
                    clientX: point.x,
                    clientY: point.y,
                    screenX: point.x,
                    screenY: point.y,
                    detail: detail || 0,
                    button: 0,
                    buttons: pressed ? 1 : 0
                  };
                  try {
                    if (pointer && view.PointerEvent) {
                      init.pointerId = 1;
                      init.pointerType = 'mouse';
                      init.isPrimary = true;
                      el.dispatchEvent(new view.PointerEvent(type, init));
                    } else {
                      el.dispatchEvent(new view.MouseEvent(type, init));
                    }
                  } catch (ignored) {}
                };
                var dispatchClickPrelude = function (el) {
                  var point = eventPointOf(el);
                  dispatchPointerMouse(el, 'pointerdown', point, true, true);
                  dispatchPointerMouse(el, 'mousedown', point, false, true);
                  dispatchPointerMouse(el, 'pointerup', point, true, false);
                  dispatchPointerMouse(el, 'mouseup', point, false, false);
                  return point;
                };
                var dispatchDoubleClick = function (el) {
                  var point = dispatchClickPrelude(el);
                  el.click();
                  point = dispatchClickPrelude(el);
                  el.click();
                  dispatchPointerMouse(el, 'dblclick', point, false, false, 2);
                  return point;
                };
                var dispatchHoverPrelude = function (el) {
                  var point = eventPointOf(el);
                  dispatchPointerMouse(el, 'pointerover', point, true, false);
                  dispatchPointerMouse(el, 'pointerenter', point, true, false);
                  dispatchPointerMouse(el, 'mouseover', point, false, false);
                  dispatchPointerMouse(el, 'mouseenter', point, false, false);
                  dispatchPointerMouse(el, 'pointermove', point, true, false);
                  dispatchPointerMouse(el, 'mousemove', point, false, false);
                  return point;
                };
                var optionLabelOf = function (option) {
                  return clean([option.text || '', option.label || '', option.value || ''].join(' '), 500);
                };
                var matchSelectOption = function (select, raw) {
                  var requested = String(raw || '').trim();
                  if (!requested) {
                    return { ok: false, errorCode: 'missing_select_value', errorDetail: 'select requires value' };
                  }
                  var options = Array.prototype.slice.call(select.options || []);
                  if (!options.length) {
                    return { ok: false, errorCode: 'select_option_not_found', errorDetail: 'select has no options' };
                  }
                  var indexMatch = requested.match(/^index[:=](\d+)$/i);
                  if (indexMatch) {
                    var index = Number(indexMatch[1]);
                    if (index >= 0 && index < options.length) {
                      return { ok: true, index: index, option: options[index] };
                    }
                    return { ok: false, errorCode: 'select_option_not_found', errorDetail: 'option index out of range' };
                  }
                  var requestedLower = requested.toLowerCase();
                  var exact = options.find(function (option) {
                    return String(option.value || '') === requested || clean(option.text || option.label || '', 500) === requested;
                  });
                  if (exact) return { ok: true, index: options.indexOf(exact), option: exact };
                  var caseFolded = options.find(function (option) {
                    return String(option.value || '').toLowerCase() === requestedLower || clean(option.text || option.label || '', 500).toLowerCase() === requestedLower;
                  });
                  if (caseFolded) return { ok: true, index: options.indexOf(caseFolded), option: caseFolded };
                  var partial = options.find(function (option) {
                    var value = String(option.value || '').toLowerCase();
                    var text = clean(option.text || option.label || '', 500).toLowerCase();
                    return value.indexOf(requestedLower) >= 0 || text.indexOf(requestedLower) >= 0;
                  });
                  if (partial) return { ok: true, index: options.indexOf(partial), option: partial };
                  return { ok: false, errorCode: 'select_option_not_found', errorDetail: 'option not found: ' + clean(requested, 80) };
                };
                var normalizeCheckValue = function (raw) {
                  var original = raw === undefined || raw === null ? '' : String(raw).trim();
                  if (!original) return { ok: true, mode: 'set', checked: true, label: 'true' };
                  var compact = original.toLowerCase().replace(/[\s_\-]+/g, '');
                  if (compact === 'toggle') return { ok: true, mode: 'toggle', label: 'toggle' };
                  if (['true', 'checked', 'check', 'on', 'yes', '1'].indexOf(compact) >= 0) {
                    return { ok: true, mode: 'set', checked: true, label: 'true' };
                  }
                  if (['false', 'unchecked', 'uncheck', 'off', 'no', '0'].indexOf(compact) >= 0) {
                    return { ok: true, mode: 'set', checked: false, label: 'false' };
                  }
                  return {
                    ok: false,
                    errorCode: 'unsupported_check_value',
                    errorDetail: 'check value supports true, false and toggle'
                  };
                };
                var checkTargetInfo = function (el) {
                  if (!el) return { ok: false };
                  var tag = String(el.tagName || '').toLowerCase();
                  var type = String(el.getAttribute('type') || '').toLowerCase();
                  var role = String(roleOf(el) || '').toLowerCase();
                  if (tag === 'input' && type === 'checkbox') {
                    return { ok: true, kind: 'checkbox', htmlInput: true, radio: false, current: !!el.checked };
                  }
                  if (tag === 'input' && type === 'radio') {
                    return { ok: true, kind: 'radio', htmlInput: true, radio: true, current: !!el.checked };
                  }
                  if (role === 'checkbox' || role === 'switch' || role === 'radio') {
                    var ariaChecked = String(el.getAttribute('aria-checked') || '').toLowerCase();
                    var current = ariaChecked === 'true';
                    if (!ariaChecked && 'checked' in el) current = !!el.checked;
                    return { ok: true, kind: role, htmlInput: false, radio: role === 'radio', current: current };
                  }
                  return { ok: false };
                };
                var clearTargetInfo = function (el) {
                  if (!el) return { ok: false };
                  if (el.isContentEditable) {
                    return { ok: true, kind: 'contenteditable', current: String(el.textContent || '') };
                  }
                  var tag = String(el.tagName || '').toLowerCase();
                  var type = String(el.getAttribute('type') || '').toLowerCase();
                  if (tag === 'textarea') {
                    return { ok: true, kind: 'textarea', current: String(el.value || '') };
                  }
                  if (tag === 'input') {
                    if (['button', 'submit', 'reset', 'checkbox', 'radio', 'range', 'image', 'hidden'].indexOf(type) >= 0) {
                      return { ok: false };
                    }
                    return { ok: true, kind: 'input', current: String(el.value || '') };
                  }
                  return { ok: false };
                };
                var requiresEnabled = function (type) {
                  return ['click', 'doubleClick', 'type', 'clear', 'select', 'check'].indexOf(String(type || '')) >= 0;
                };
                var finish = function (status, data) {
                  data = data || {};
                  return JSON.stringify({
                    ok: status === 'Succeeded',
                    status: status,
                    url: String(location.href || ''),
                    title: String(document.title || ''),
                    message: clean(data.message || '', 500),
                    matchedCount: data.matchedCount || 0,
                    errorCode: data.errorCode || '',
                    errorDetail: clean(data.errorDetail || '', 500),
                    element: data.element || null,
                    durationMs: Math.max(0, Date.now() - startedAt)
                  });
                };
                var target = action.target || { kind: 'none' };
                var targetKind = String(target.kind || 'none');
                if (targetKind === 'state') {
                  var stateValue = String(target.value || action.value || 'domReady');
                  var stateName = stateValue.toLowerCase();
                  var idleMs = 500;
                  var idleMatch = stateName.match(/^idle[:=](\d+)$/);
                  if (idleMatch) {
                    stateName = 'idle';
                    idleMs = Math.max(0, Math.min(5000, Number(idleMatch[1]) || idleMs));
                  } else if (stateName === 'idle' || stateName === 'idlems') {
                    idleMs = Math.max(0, Math.min(5000, Number(action.value || target.name || idleMs) || idleMs));
                    stateName = 'idle';
                  }
                  if (action.type !== 'find' && action.type !== 'waitFor') {
                    return finish('Rejected', {
                      errorCode: 'target_not_actionable',
                      errorDetail: 'state target only supports find and waitFor'
                    });
                  }
                  var readyState = String(document.readyState || '');
                  var readyEnough = readyState === 'interactive' || readyState === 'complete';
                  var matchedState = false;
                  var message = '';
                  if (stateName === 'domready' || stateName === 'ready' || stateName === 'interactive') {
                    matchedState = readyEnough;
                    message = 'state domReady readyState=' + readyState;
                  } else if (stateName === 'complete' || stateName === 'load' || stateName === 'loaded') {
                    matchedState = readyState === 'complete';
                    message = 'state complete readyState=' + readyState;
                  } else if (stateName === 'idle') {
                    var idleKey = '__kiteAutomationIdleState';
                    var idleState = window[idleKey];
                    if (!idleState) {
                      idleState = { lastChangedAt: Date.now(), observer: null };
                      window[idleKey] = idleState;
                      try {
                        var observedRoot = document.documentElement || document.body;
                        if (observedRoot && window.MutationObserver) {
                          idleState.observer = new MutationObserver(function () {
                            idleState.lastChangedAt = Date.now();
                          });
                          idleState.observer.observe(observedRoot, {
                            subtree: true,
                            childList: true,
                            attributes: true,
                            characterData: true
                          });
                        }
                      } catch (ignored) {}
                    }
                    var idleFor = Math.max(0, Date.now() - Number(idleState.lastChangedAt || 0));
                    matchedState = readyEnough && idleFor >= idleMs;
                    message = 'state idle ' + idleFor + 'ms of ' + idleMs + 'ms';
                  } else {
                    return finish('Failed', {
                      errorCode: 'target_not_found',
                      errorDetail: 'state not supported: ' + clean(stateValue, 80)
                    });
                  }
                  if (matchedState) {
                    return finish('Succeeded', {
                      message: message,
                      matchedCount: 1
                    });
                  }
                  return finish('Failed', {
                    errorCode: 'target_not_found',
                    errorDetail: message,
                    matchedCount: 0
                  });
                }
                if (targetKind === 'url') {
                  var currentUrl = String(location.href || '');
                  var expectedUrl = String(target.value || target.name || '');
                  var urlMatch = matchesText(currentUrl, expectedUrl, String(target.match || 'contains'));
                  if (action.type === 'find' || action.type === 'waitFor') {
                    if (urlMatch) {
                      return finish('Succeeded', {
                        message: 'matched url',
                        matchedCount: 1
                      });
                    }
                    return finish('Failed', {
                      errorCode: 'target_not_found',
                      errorDetail: 'url not matched',
                      matchedCount: 0
                    });
                  }
                  return finish('Rejected', {
                    errorCode: 'target_not_actionable',
                    errorDetail: 'url target only supports find and waitFor'
                  });
                }
                if (action.type === 'navigate') {
                  if (targetKind !== 'none') {
                    return finish('Rejected', {
                      errorCode: 'target_not_actionable',
                      errorDetail: 'navigate only supports target kind none'
                    });
                  }
                  var navValue = String(action.value || '').trim().toLowerCase().replace(/[\s_\-]+/g, '');
                  if (navValue === 'reload' || navValue === 'refresh') {
                    location.reload();
                    return finish('Succeeded', {
                      message: 'navigation reload requested',
                      matchedCount: 1
                    });
                  }
                  if (navValue === 'back' || navValue === 'goback') {
                    history.back();
                    return finish('Succeeded', {
                      message: 'navigation back requested',
                      matchedCount: 1
                    });
                  }
                  if (navValue === 'forward' || navValue === 'goforward') {
                    history.forward();
                    return finish('Succeeded', {
                      message: 'navigation forward requested',
                      matchedCount: 1
                    });
                  }
                  return finish('Failed', {
                    errorCode: 'unsupported_navigation_value',
                    errorDetail: 'navigate supports back, forward and reload'
                  });
                }
                var elements = queryElements(target);
                var index = Math.max(0, Number(target.index || 0));
                var element = elements[index] || null;
                if (action.type === 'evaluate') {
                  var source = String(action.value || '');
                  if (!source.trim()) {
                    return finish('Failed', {
                      errorCode: 'missing_evaluate_source',
                      errorDetail: 'evaluate requires value'
                    });
                  }
                  var evaluated = (0, eval)(source);
                  return finish('Succeeded', {
                    message: 'evaluate: ' + clean(stringifyValue(evaluated), 500)
                  });
                }
                if (action.type === 'find' || action.type === 'waitFor') {
                  if (!element) {
                    return finish('Failed', {
                      errorCode: 'target_not_found',
                      errorDetail: targetDetail(target),
                      matchedCount: elements.length
                    });
                  }
                  return finish('Succeeded', {
                    message: 'found ' + target.kind,
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'scroll') {
                  var beforeX = Math.round(window.scrollX || document.documentElement.scrollLeft || 0);
                  var beforeY = Math.round(window.scrollY || document.documentElement.scrollTop || 0);
                  if (String(target.kind || 'none') !== 'none') {
                    if (!element) {
                      return finish('Failed', {
                        errorCode: 'target_not_found',
                        errorDetail: targetDetail(target),
                        matchedCount: elements.length
                      });
                    }
                    element.scrollIntoView({ block: 'center', inline: 'center' });
                  } else {
                    var value = String(action.value || 'down').toLowerCase();
                    var viewport = Math.max(240, window.innerHeight || document.documentElement.clientHeight || 600);
                    var pixels = Number(value);
                    if (!isFinite(pixels)) {
                      if (value === 'up') pixels = -Math.round(viewport * 0.8);
                      else if (value === 'top') window.scrollTo(0, 0);
                      else if (value === 'bottom') window.scrollTo(0, document.documentElement.scrollHeight || document.body.scrollHeight || 0);
                      else pixels = Math.round(viewport * 0.8);
                    }
                    if (isFinite(pixels)) window.scrollBy(0, pixels);
                  }
                  var afterX = Math.round(window.scrollX || document.documentElement.scrollLeft || 0);
                  var afterY = Math.round(window.scrollY || document.documentElement.scrollTop || 0);
                  return finish('Succeeded', {
                    message: 'scrolled x=' + afterX + ' y=' + afterY,
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'press') {
                  var keyInfo = normalizePressKey(action.value || target.value || target.name || '');
                  if (!keyInfo.supported) {
                    return finish('Failed', {
                      errorCode: keyInfo.errorCode,
                      errorDetail: keyInfo.errorDetail
                    });
                  }
                  var pressTarget = element || document.activeElement || document.body || document.documentElement;
                  if (String(target.kind || 'none') !== 'none') {
                    if (!element) {
                      return finish('Failed', {
                        errorCode: 'target_not_found',
                        errorDetail: targetDetail(target),
                        matchedCount: elements.length
                      });
                    }
                    if (!visible(element)) {
                      return finish('Failed', {
                        errorCode: 'target_not_visible',
                        errorDetail: targetDetail(target),
                        matchedCount: elements.length,
                        element: elementSummary(element)
                      });
                    }
                    element.scrollIntoView({ block: 'center', inline: 'center' });
                  }
                  if (!pressTarget) {
                    return finish('Failed', {
                      errorCode: 'target_not_found',
                      errorDetail: 'press target unavailable',
                      matchedCount: elements.length
                    });
                  }
                  if (disabledForAutomation(pressTarget)) {
                    return finish('Failed', {
                      errorCode: 'target_disabled',
                      errorDetail: targetDetail(target),
                      matchedCount: String(target.kind || 'none') === 'none' ? 1 : elements.length,
                      element: elementSummary(pressTarget)
                    });
                  }
                  if (pressTarget.focus) pressTarget.focus();
                  dispatchKeyboard(pressTarget, 'keydown', keyInfo);
                  if (keyInfo.sendKeyPress) dispatchKeyboard(pressTarget, 'keypress', keyInfo);
                  dispatchKeyboard(pressTarget, 'keyup', keyInfo);
                  return finish('Succeeded', {
                    message: 'pressed ' + keyInfo.label,
                    matchedCount: String(target.kind || 'none') === 'none' ? 1 : elements.length,
                    element: elementSummary(pressTarget)
                  });
                }
                if (!element) {
                  return finish('Failed', {
                    errorCode: 'target_not_found',
                    errorDetail: targetDetail(target),
                    matchedCount: elements.length
                  });
                }
                if (!visible(element)) {
                  return finish('Failed', {
                    errorCode: 'target_not_visible',
                    errorDetail: targetDetail(target),
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (requiresEnabled(action.type) && disabledForAutomation(element)) {
                  return finish('Failed', {
                    errorCode: 'target_disabled',
                    errorDetail: targetDetail(target),
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if ((action.type === 'type' || action.type === 'clear') && readonlyForAutomation(element)) {
                  return finish('Failed', {
                    errorCode: 'target_readonly',
                    errorDetail: targetDetail(target),
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'click') {
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  if (element.focus) element.focus();
                  dispatchClickPrelude(element);
                  element.click();
                  return finish('Succeeded', {
                    message: 'clicked ' + target.kind + ' with pointer prelude',
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'doubleClick') {
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  if (element.focus) element.focus();
                  dispatchDoubleClick(element);
                  return finish('Succeeded', {
                    message: 'double clicked ' + target.kind + ' with pointer prelude',
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'hover') {
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  dispatchHoverPrelude(element);
                  return finish('Succeeded', {
                    message: 'hovered ' + target.kind + ' with pointer prelude',
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'select') {
                  var tagName = String(element.tagName || '').toLowerCase();
                  if (tagName !== 'select') {
                    return finish('Failed', {
                      errorCode: 'target_not_selectable',
                      errorDetail: targetDetail(target),
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  var selected = matchSelectOption(element, action.value || '');
                  if (!selected.ok) {
                    return finish('Failed', {
                      errorCode: selected.errorCode,
                      errorDetail: selected.errorDetail,
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  if (element.focus) element.focus();
                  if (element.multiple) {
                    Array.prototype.slice.call(element.options || []).forEach(function (option) {
                      option.selected = false;
                    });
                    selected.option.selected = true;
                  } else {
                    element.selectedIndex = selected.index;
                    element.value = selected.option.value;
                  }
                  element.dispatchEvent(new Event('input', { bubbles: true }));
                  element.dispatchEvent(new Event('change', { bubbles: true }));
                  return finish('Succeeded', {
                    message: 'selected ' + optionLabelOf(selected.option),
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'check') {
                  var desired = normalizeCheckValue(action.value);
                  if (!desired.ok) {
                    return finish('Failed', {
                      errorCode: desired.errorCode,
                      errorDetail: desired.errorDetail,
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  var checkInfo = checkTargetInfo(element);
                  if (!checkInfo.ok) {
                    return finish('Failed', {
                      errorCode: 'target_not_checkable',
                      errorDetail: targetDetail(target),
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  var finalChecked = desired.mode === 'toggle' ? !checkInfo.current : !!desired.checked;
                  if (checkInfo.radio && !finalChecked) {
                    return finish('Failed', {
                      errorCode: 'target_not_checkable',
                      errorDetail: 'radio cannot be unchecked directly',
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  if (element.focus) element.focus();
                  var changed = checkInfo.current !== finalChecked;
                  if (checkInfo.htmlInput) {
                    if (changed) element.checked = finalChecked;
                  } else if (changed) {
                    element.setAttribute('aria-checked', finalChecked ? 'true' : 'false');
                  }
                  if (changed) {
                    element.dispatchEvent(new Event('input', { bubbles: true }));
                    element.dispatchEvent(new Event('change', { bubbles: true }));
                  }
                  return finish('Succeeded', {
                    message: 'checked ' + finalChecked + (changed ? ' changed' : ' unchanged'),
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'clear') {
                  var clearInfo = clearTargetInfo(element);
                  if (!clearInfo.ok) {
                    return finish('Failed', {
                      errorCode: 'target_not_editable',
                      errorDetail: targetDetail(target),
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  if (element.focus) element.focus();
                  if (clearInfo.kind === 'contenteditable') {
                    element.textContent = '';
                  } else {
                    element.value = '';
                  }
                  element.dispatchEvent(new Event('input', { bubbles: true }));
                  element.dispatchEvent(new Event('change', { bubbles: true }));
                  return finish('Succeeded', {
                    message: 'cleared ' + clearInfo.kind + (clearInfo.current ? ' changed' : ' unchanged'),
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                if (action.type === 'type') {
                  var value = String(action.value || '');
                  element.scrollIntoView({ block: 'center', inline: 'center' });
                  if (element.focus) element.focus();
                  if (element.isContentEditable) {
                    element.textContent = value;
                  } else if ('value' in element) {
                    element.value = value;
                  } else {
                    return finish('Failed', {
                      errorCode: 'target_not_editable',
                      errorDetail: targetDetail(target),
                      matchedCount: elements.length,
                      element: elementSummary(element)
                    });
                  }
                  element.dispatchEvent(new Event('input', { bubbles: true }));
                  element.dispatchEvent(new Event('change', { bubbles: true }));
                  return finish('Succeeded', {
                    message: 'typed ' + value.length + ' chars',
                    matchedCount: elements.length,
                    element: elementSummary(element)
                  });
                }
                return finish('Failed', {
                  errorCode: 'unsupported_action',
                  errorDetail: String(action.type || '')
                });
              } catch (error) {
                return JSON.stringify({
                  ok: false,
                  status: 'Failed',
                  url: String(location.href || ''),
                  title: String(document.title || ''),
                  message: 'action failed',
                  matchedCount: 0,
                  errorCode: 'script_error',
                  errorDetail: String(error && error.message ? error.message : error),
                  durationMs: Math.max(0, Date.now() - startedAt)
                });
              }
            })();
        """.trimIndent()
    }

    fun parseResult(
        sessionId: String,
        action: BrowserAutomationAction,
        rawResult: String?,
        startedAt: Long,
        completedAt: Long = System.currentTimeMillis()
    ): BrowserAutomationActionResult {
        val decoded = decodeEvaluateJavascriptResult(rawResult)
        val json = JSONObject(decoded)
        val status = enumValueOrDefault(
            json.optString("status"),
            if (json.optBoolean("ok", false)) {
                BrowserAutomationResultStatus.Succeeded
            } else {
                BrowserAutomationResultStatus.Failed
            }
        )
        return BrowserAutomationActionResult(
            actionId = action.actionId,
            sessionId = sessionId,
            type = action.type,
            status = status,
            durationMs = json.optLong("durationMs", completedAt - startedAt).coerceAtLeast(0L),
            url = BrowserAutomationRedactor.redactUrl(json.optString("url")),
            title = json.optString("title").takeIf { it.isNotBlank() },
            message = BrowserAutomationRedactor.safeText(json.optString("message"), 500),
            matchedCount = json.optInt("matchedCount", 0).coerceAtLeast(0),
            errorCode = json.optString("errorCode").takeIf { it.isNotBlank() },
            errorDetail = json.optString("errorDetail").takeIf { it.isNotBlank() },
            completedAt = completedAt
        )
    }

    fun rejectedResult(
        action: BrowserAutomationAction,
        sessionId: String,
        errorCode: String,
        detail: String
    ): BrowserAutomationActionResult =
        BrowserAutomationActionResult(
            actionId = action.actionId,
            sessionId = sessionId,
            type = action.type,
            status = BrowserAutomationResultStatus.Rejected,
            durationMs = 0L,
            url = "",
            title = null,
            message = detail,
            errorCode = errorCode,
            errorDetail = detail
        )

    fun timedOutResult(
        action: BrowserAutomationAction,
        sessionId: String,
        startedAt: Long
    ): BrowserAutomationActionResult =
        BrowserAutomationActionResult(
            actionId = action.actionId,
            sessionId = sessionId,
            type = action.type,
            status = BrowserAutomationResultStatus.TimedOut,
            durationMs = System.currentTimeMillis() - startedAt,
            url = "",
            title = null,
            message = "action timed out",
            errorCode = "action_timeout",
            errorDetail = action.displaySummary()
        )

    private fun decodeEvaluateJavascriptResult(rawResult: String?): String {
        val raw = rawResult?.trim().orEmpty()
        if (raw.isBlank() || raw == "null") {
            throw IllegalStateException("empty_action_result")
        }
        return if (raw.startsWith("\"")) {
            JSONArray("[$raw]").optString(0)
        } else {
            raw
        }.takeIf { it.isNotBlank() } ?: throw IllegalStateException("empty_action_json")
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String, default: T): T =
        runCatching { enumValueOf<T>(name) }.getOrDefault(default)
}
