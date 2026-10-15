(function () {
  if (window.__tvNav) return;
  window.__tvNav = true;

  var style = document.createElement('style');
  style.textContent =
    '.tv-focus{outline:4px solid #a970ff !important;outline-offset:3px !important;' +
    'box-shadow:0 0 0 8px rgba(169,112,255,.35) !important;border-radius:6px;}';
  document.head.appendChild(style);

  var SEL = 'a[href],button,input,select,textarea,summary,[tabindex],[role="button"],' +
            '[role="tab"],[role="link"],[role="menuitem"],[onclick]';
  var POINTER_TAGS = 'div,span,li,img,svg,label';
  var NATIVE = /^(A|BUTTON|INPUT|SELECT|TEXTAREA|SUMMARY)$/;

  function usable(el) {
    if (el.disabled) return false;
    if (el.getAttribute('tabindex') === '-1' && !(el.dataset && el.dataset.tv)) return false;
    var r = el.getBoundingClientRect();
    if (r.width < 6 || r.height < 6) return false;
    var cs = getComputedStyle(el);
    return cs.visibility !== 'hidden' && cs.display !== 'none' &&
           cs.pointerEvents !== 'none' && parseFloat(cs.opacity) > 0.05;
  }

  function candidates() {
    var out = [], seen = new Set(), i, el;
    var a = document.querySelectorAll(SEL);
    for (i = 0; i < a.length; i++) {
      el = a[i]; seen.add(el);
      if (usable(el)) out.push(el);
    }
    var b = document.querySelectorAll(POINTER_TAGS);
    for (i = 0; i < b.length; i++) {
      el = b[i];
      if (seen.has(el)) continue;
      if (getComputedStyle(el).cursor !== 'pointer') continue;
      var p = el.parentElement;
      if (p && getComputedStyle(p).cursor === 'pointer') continue;
      if (usable(el)) out.push(el);
    }
    return out;
  }

  function pick(cur, key, list) {
    var cr = cur.getBoundingClientRect();
    var cx = cr.left + cr.width / 2, cy = cr.top + cr.height / 2;
    var best = null, bestScore = Infinity;
    list.forEach(function (e) {
      if (e === cur || cur.contains(e) || e.contains(cur)) return;
      var r = e.getBoundingClientRect();
      var dx = r.left + r.width / 2 - cx, dy = r.top + r.height / 2 - cy;
      var prim, sec;
      if (key === 'ArrowRight') { prim = dx; sec = Math.abs(dy); }
      else if (key === 'ArrowLeft') { prim = -dx; sec = Math.abs(dy); }
      else if (key === 'ArrowDown') { prim = dy; sec = Math.abs(dx); }
      else { prim = -dy; sec = Math.abs(dx); }
      if (prim <= 2) return;
      var score = prim + 2.5 * sec;
      if (score < bestScore) { bestScore = score; best = e; }
    });
    return best;
  }

  function focusEl(e) {
    if (!e) return;
    var old = document.querySelectorAll('.tv-focus');
    for (var i = 0; i < old.length; i++) old[i].classList.remove('tv-focus');
    if (!NATIVE.test(e.tagName) && !e.hasAttribute('tabindex')) {
      e.setAttribute('tabindex', '-1');
      if (e.dataset) e.dataset.tv = '1';
    }
    try { e.focus({ preventScroll: true }); } catch (_) {}
    e.classList.add('tv-focus');
    try { e.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (_) {}
  }

  document.addEventListener('focusout', function (ev) {
    if (ev.target && ev.target.classList) ev.target.classList.remove('tv-focus');
  }, true);

  document.addEventListener('keydown', function (ev) {
    var k = ev.key;
    var a = document.activeElement;
    var typing = a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA' || a.isContentEditable);
    var full = document.fullscreenElement || document.webkitFullscreenElement;

    if (k === 'Enter') {
      if (ev.repeat) return;
      if (full && !(a && a.classList && a.classList.contains('tv-focus'))) {
        var v = document.querySelector('video');
        if (v) { ev.preventDefault(); if (v.paused) v.play(); else v.pause(); }
        return;
      }
      if (a && a.classList && a.classList.contains('tv-focus') && !typing) {
        ev.preventDefault();
        a.click();
      }
      return;
    }

    if (k.indexOf('Arrow') !== 0) return;
    if (full) return;                       // en pantalla completa mandan los atajos del reproductor
    if (typing && (k === 'ArrowLeft' || k === 'ArrowRight')) return;

    ev.preventDefault();
    var list = candidates();
    if (!list.length) return;
    var cur = (a && list.indexOf(a) >= 0) ? a : null;

    if (!cur) {
      var first = null;
      for (var i = 0; i < list.length; i++) {
        var r = list[i].getBoundingClientRect();
        if (r.top >= 0 && r.bottom <= innerHeight && r.left >= 0 && r.right <= innerWidth) { first = list[i]; break; }
      }
      focusEl(first || list[0]);
      return;
    }

    var next = pick(cur, k, list);
    if (next) focusEl(next);
    else if (k === 'ArrowDown') window.scrollBy(0, 300);
    else if (k === 'ArrowUp') window.scrollBy(0, -300);
  }, true);

  // =====================================================================
  //  Puntero (modo ratón) dibujado y manejado por la propia página
  // =====================================================================
  var cur = { x: innerWidth / 2, y: innerHeight / 2, on: false, el: null, hideT: null, last: null };

  function host() {
    return document.fullscreenElement || document.webkitFullscreenElement || document.body;
  }

  function ensureCur() {
    if (!cur.el) {
      var d = document.createElement('div');
      d.id = 'tv-cursor';
      d.style.cssText = 'position:fixed;left:0;top:0;width:24px;height:24px;margin:-12px 0 0 -12px;' +
        'border-radius:50%;background:#fff;border:3px solid #9146ff;box-sizing:border-box;' +
        'z-index:2147483647;pointer-events:none;display:none;transition:opacity .25s;';
      cur.el = d;
    }
    var h = host();
    if (cur.el.parentNode !== h) h.appendChild(cur.el);
    return cur.el;
  }

  function showCur() {
    var e = ensureCur();
    e.style.display = 'block';
    e.style.opacity = '1';
    e.style.left = cur.x + 'px';
    e.style.top = cur.y + 'px';
    clearTimeout(cur.hideT);
    cur.hideT = setTimeout(function () { if (cur.el) cur.el.style.opacity = '0'; }, 3000);
  }

  function fire(el, type, extra) {
    var o = { bubbles: true, cancelable: true, view: window, clientX: cur.x, clientY: cur.y,
              screenX: cur.x, screenY: cur.y, button: 0, buttons: 0,
              pointerId: 1, pointerType: 'mouse', isPrimary: true };
    if (extra) for (var k in extra) o[k] = extra[k];
    var ev;
    try {
      ev = type.indexOf('pointer') === 0 ? new PointerEvent(type, o) : new MouseEvent(type, o);
    } catch (_) {
      ev = new MouseEvent(type, o);
    }
    el_dispatch(el, ev);
  }
  function el_dispatch(el, ev) { try { el.dispatchEvent(ev); } catch (_) {} }

  function hoverUpdate() {
    var t = document.elementFromPoint(cur.x, cur.y);
    if (!t) return null;
    if (t !== cur.last) {
      if (cur.last) {
        fire(cur.last, 'pointerout', { relatedTarget: t });
        fire(cur.last, 'mouseout', { relatedTarget: t });
        fire(cur.last, 'mouseleave', { bubbles: false, relatedTarget: t });
      }
      fire(t, 'pointerover', { relatedTarget: cur.last });
      fire(t, 'mouseover', { relatedTarget: cur.last });
      fire(t, 'mouseenter', { bubbles: false, relatedTarget: cur.last });
      cur.last = t;
    }
    fire(t, 'pointermove');
    fire(t, 'mousemove');
    return t;
  }

  function scrollAt(sign) {
    var t = document.elementFromPoint(cur.x, cur.y);
    var amt = sign * Math.max(120, innerHeight * 0.25);
    while (t && t !== document.body && t !== document.documentElement) {
      var cs = getComputedStyle(t);
      if (/(auto|scroll)/.test(cs.overflowY) && t.scrollHeight > t.clientHeight + 2) {
        var before = t.scrollTop;
        t.scrollTop += amt;
        if (t.scrollTop !== before) return;
      }
      t = t.parentElement;
    }
    window.scrollBy(0, amt);
  }

  window.__tvCur = function (dir, rep) {
    if (!cur.on) return;
    var step = Math.max(10, innerWidth * 0.012) * (1 + Math.min(rep || 0, 20) * 0.25);
    var oy = cur.y;
    if (dir === 'left') cur.x -= step;
    else if (dir === 'right') cur.x += step;
    else if (dir === 'up') cur.y -= step;
    else if (dir === 'down') cur.y += step;
    cur.x = Math.max(0, Math.min(cur.x, innerWidth - 1));
    cur.y = Math.max(0, Math.min(cur.y, innerHeight - 1));
    if ((dir === 'up' || dir === 'down') && cur.y === oy) scrollAt(dir === 'up' ? -1 : 1);
    showCur();
    hoverUpdate();
    try { TVBridge.hover(cur.x / innerWidth, cur.y / innerHeight); } catch (_) {}
  };

  window.__tvCurClick = function () {
    if (!cur.on) return;
    showCur();
    var t = hoverUpdate();
    if (!t) return;
    fire(t, 'pointerdown', { buttons: 1 });
    fire(t, 'mousedown', { buttons: 1 });
    try {
      var f = t.closest ? t.closest('input,textarea,select,button,a,[tabindex]') : null;
      (f || t).focus();
    } catch (_) {}
    fire(t, 'pointerup');
    fire(t, 'mouseup');
    fire(t, 'click');
  };

  window.__tvCursorMode = function (on) {
    cur.on = !!on;
    if (cur.on) {
      showCur();
    } else {
      clearTimeout(cur.hideT);
      if (cur.el) cur.el.style.display = 'none';
    }
  };

  function onFsChange() {
    if (cur.on) showCur();
    checkMode();
  }

  // --- aviso a la app: ¿estamos en el reproductor? (activa el modo puntero) ---
  var lastMode = null;
  function checkMode() {
    var on = !!(document.fullscreenElement || document.webkitFullscreenElement);
    if (!on) {
      var v = document.querySelector('video');
      if (v) {
        var r = v.getBoundingClientRect();
        on = r.width > 300 && r.height > 150 && r.bottom > 0 && r.top < innerHeight;
      }
    }
    if (on !== lastMode) {
      lastMode = on;
      try { TVBridge.mode(on); } catch (_) {}
    }
  }
  setInterval(checkMode, 400);
  document.addEventListener('fullscreenchange', onFsChange);
  document.addEventListener('webkitfullscreenchange', onFsChange);
  checkMode();
})();
