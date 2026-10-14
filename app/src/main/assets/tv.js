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
    try { e.scrollIntoView({ block: 'center', inline: 'center' }); } catch (_) {}
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
})();
