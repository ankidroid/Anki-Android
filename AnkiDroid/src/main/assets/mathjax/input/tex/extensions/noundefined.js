!(function (e) {
  var n = {};
  function t(o) {
    if (n[o]) return n[o].exports;
    var r = (n[o] = { i: o, l: !1, exports: {} });
    return e[o].call(r.exports, r, r.exports, t), (r.l = !0), r.exports;
  }
  (t.m = e),
    (t.c = n),
    (t.d = function (e, n, o) {
      t.o(e, n) || Object.defineProperty(e, n, { enumerable: !0, get: o });
    }),
    (t.r = function (e) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(e, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(e, '__esModule', { value: !0 });
    }),
    (t.t = function (e, n) {
      if ((1 & n && (e = t(e)), 8 & n)) return e;
      if (4 & n && 'object' == typeof e && e && e.__esModule) return e;
      var o = Object.create(null);
      if (
        (t.r(o),
        Object.defineProperty(o, 'default', { enumerable: !0, value: e }),
        2 & n && 'string' != typeof e)
      )
        for (var r in e)
          t.d(
            o,
            r,
            function (n) {
              return e[n];
            }.bind(null, r),
          );
      return o;
    }),
    (t.n = function (e) {
      var n =
        e && e.__esModule
          ? function () {
              return e.default;
            }
          : function () {
              return e;
            };
      return t.d(n, 'a', n), n;
    }),
    (t.o = function (e, n) {
      return Object.prototype.hasOwnProperty.call(e, n);
    }),
    (t.p = ''),
    t((t.s = 3));
})([
  function (e, n, t) {
    'use strict';
    Object.defineProperty(n, '__esModule', { value: !0 }),
      (n.isObject = MathJax._.components.global.isObject),
      (n.combineConfig = MathJax._.components.global.combineConfig),
      (n.combineDefaults = MathJax._.components.global.combineDefaults),
      (n.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (n.MathJax = MathJax._.components.global.MathJax);
  },
  function (e, n, t) {
    'use strict';
    var o =
      (this && this.__values) ||
      function (e) {
        var n = 'function' == typeof Symbol && Symbol.iterator,
          t = n && e[n],
          o = 0;
        if (t) return t.call(e);
        if (e && 'number' == typeof e.length)
          return {
            next: function () {
              return e && o >= e.length && (e = void 0), { value: e && e[o++], done: !e };
            },
          };
        throw new TypeError(n ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
      };
    Object.defineProperty(n, '__esModule', { value: !0 }), (n.NoUndefinedConfiguration = void 0);
    var r = t(2);
    n.NoUndefinedConfiguration = r.Configuration.create('noundefined', {
      fallback: {
        macro: function (e, n) {
          var t,
            r,
            i = e.create('text', '\\' + n),
            a = e.options.noundefined || {},
            u = {};
          try {
            for (var f = o(['color', 'background', 'size']), c = f.next(); !c.done; c = f.next()) {
              var l = c.value;
              a[l] && (u['math' + l] = a[l]);
            }
          } catch (e) {
            t = { error: e };
          } finally {
            try {
              c && !c.done && (r = f.return) && r.call(f);
            } finally {
              if (t) throw t.error;
            }
          }
          e.Push(e.create('node', 'mtext', [], u, i));
        },
      },
      options: { noundefined: { color: 'red', background: '', size: '' } },
      priority: 3,
    });
  },
  function (e, n, t) {
    'use strict';
    Object.defineProperty(n, '__esModule', { value: !0 }),
      (n.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (n.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (n.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (e, n, t) {
    'use strict';
    t.r(n);
    var o = t(0),
      r = t(1);
    Object(o.combineWithMathJax)({
      _: { input: { tex: { noundefined: { NoUndefinedConfiguration: r } } } },
    });
  },
]);
