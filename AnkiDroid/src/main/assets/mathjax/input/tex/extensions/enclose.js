!(function (e) {
  var t = {};
  function a(n) {
    if (t[n]) return t[n].exports;
    var o = (t[n] = { i: n, l: !1, exports: {} });
    return e[n].call(o.exports, o, o.exports, a), (o.l = !0), o.exports;
  }
  (a.m = e),
    (a.c = t),
    (a.d = function (e, t, n) {
      a.o(e, t) || Object.defineProperty(e, t, { enumerable: !0, get: n });
    }),
    (a.r = function (e) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(e, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(e, '__esModule', { value: !0 });
    }),
    (a.t = function (e, t) {
      if ((1 & t && (e = a(e)), 8 & t)) return e;
      if (4 & t && 'object' == typeof e && e && e.__esModule) return e;
      var n = Object.create(null);
      if (
        (a.r(n),
        Object.defineProperty(n, 'default', { enumerable: !0, value: e }),
        2 & t && 'string' != typeof e)
      )
        for (var o in e)
          a.d(
            n,
            o,
            function (t) {
              return e[t];
            }.bind(null, o),
          );
      return n;
    }),
    (a.n = function (e) {
      var t =
        e && e.__esModule
          ? function () {
              return e.default;
            }
          : function () {
              return e;
            };
      return a.d(t, 'a', t), t;
    }),
    (a.o = function (e, t) {
      return Object.prototype.hasOwnProperty.call(e, t);
    }),
    (a.p = ''),
    a((a.s = 5));
})([
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.isObject = MathJax._.components.global.isObject),
      (t.combineConfig = MathJax._.components.global.combineConfig),
      (t.combineDefaults = MathJax._.components.global.combineDefaults),
      (t.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (t.MathJax = MathJax._.components.global.MathJax);
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.EncloseConfiguration = t.EncloseMethods = t.ENCLOSE_OPTIONS = void 0);
    var n = a(2),
      o = a(3),
      r = a(4);
    (t.ENCLOSE_OPTIONS = {
      'data-arrowhead': 1,
      color: 1,
      mathcolor: 1,
      background: 1,
      mathbackground: 1,
      'data-padding': 1,
      'data-thickness': 1,
    }),
      (t.EncloseMethods = {}),
      (t.EncloseMethods.Enclose = function (e, a) {
        var n = e.GetArgument(a).replace(/,/g, ' '),
          o = e.GetBrackets(a, ''),
          i = e.ParseArg(a),
          u = r.default.keyvalOptions(o, t.ENCLOSE_OPTIONS);
        (u.notation = n), e.Push(e.create('node', 'menclose', [i], u));
      }),
      new o.CommandMap('enclose', { enclose: 'Enclose' }, t.EncloseMethods),
      (t.EncloseConfiguration = n.Configuration.create('enclose', {
        handler: { macro: ['enclose'] },
      }));
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (t.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (t.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.AbstractSymbolMap = MathJax._.input.tex.SymbolMap.AbstractSymbolMap),
      (t.RegExpMap = MathJax._.input.tex.SymbolMap.RegExpMap),
      (t.AbstractParseMap = MathJax._.input.tex.SymbolMap.AbstractParseMap),
      (t.CharacterMap = MathJax._.input.tex.SymbolMap.CharacterMap),
      (t.DelimiterMap = MathJax._.input.tex.SymbolMap.DelimiterMap),
      (t.MacroMap = MathJax._.input.tex.SymbolMap.MacroMap),
      (t.CommandMap = MathJax._.input.tex.SymbolMap.CommandMap),
      (t.EnvironmentMap = MathJax._.input.tex.SymbolMap.EnvironmentMap);
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.default = MathJax._.input.tex.ParseUtil.default);
  },
  function (e, t, a) {
    'use strict';
    a.r(t);
    var n = a(0),
      o = a(1);
    Object(n.combineWithMathJax)({
      _: { input: { tex: { enclose: { EncloseConfiguration: o } } } },
    });
  },
]);
