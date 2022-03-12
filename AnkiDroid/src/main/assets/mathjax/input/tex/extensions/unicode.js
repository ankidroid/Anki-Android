!(function (t) {
  var e = {};
  function a(n) {
    if (e[n]) return e[n].exports;
    var o = (e[n] = { i: n, l: !1, exports: {} });
    return t[n].call(o.exports, o, o.exports, a), (o.l = !0), o.exports;
  }
  (a.m = t),
    (a.c = e),
    (a.d = function (t, e, n) {
      a.o(t, e) || Object.defineProperty(t, e, { enumerable: !0, get: n });
    }),
    (a.r = function (t) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(t, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(t, '__esModule', { value: !0 });
    }),
    (a.t = function (t, e) {
      if ((1 & e && (t = a(t)), 8 & e)) return t;
      if (4 & e && 'object' == typeof t && t && t.__esModule) return t;
      var n = Object.create(null);
      if (
        (a.r(n),
        Object.defineProperty(n, 'default', { enumerable: !0, value: t }),
        2 & e && 'string' != typeof t)
      )
        for (var o in t)
          a.d(
            n,
            o,
            function (e) {
              return t[e];
            }.bind(null, o),
          );
      return n;
    }),
    (a.n = function (t) {
      var e =
        t && t.__esModule
          ? function () {
              return t.default;
            }
          : function () {
              return t;
            };
      return a.d(e, 'a', e), e;
    }),
    (a.o = function (t, e) {
      return Object.prototype.hasOwnProperty.call(t, e);
    }),
    (a.p = ''),
    a((a.s = 8));
})([
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.isObject = MathJax._.components.global.isObject),
      (e.combineConfig = MathJax._.components.global.combineConfig),
      (e.combineDefaults = MathJax._.components.global.combineDefaults),
      (e.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (e.MathJax = MathJax._.components.global.MathJax);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.UnicodeConfiguration = e.UnicodeMethods = void 0);
    var n = a(2),
      o = a(3),
      i = a(4),
      r = a(5),
      u = a(6),
      c = a(7);
    e.UnicodeMethods = {};
    var l = {};
    (e.UnicodeMethods.Unicode = function (t, e) {
      var a = t.GetBrackets(e),
        n = null,
        i = null;
      a &&
        (a.replace(/ /g, '').match(/^(\d+(\.\d*)?|\.\d+),(\d+(\.\d*)?|\.\d+)$/)
          ? ((n = a.replace(/ /g, '').split(/,/)), (i = t.GetBrackets(e)))
          : (i = a));
      var d = r.default.trimSpaces(t.GetArgument(e)).replace(/^0x/, 'x');
      if (!d.match(/^(x[0-9A-Fa-f]+|[0-9]+)$/))
        throw new o.default('BadUnicode', 'Argument to \\unicode must be a number');
      var p = parseInt(d.match(/^x/) ? '0' + d : d);
      l[p] ? i || (i = l[p][2]) : (l[p] = [800, 200, i, p]),
        n &&
          ((l[p][0] = Math.floor(1e3 * parseFloat(n[0]))),
          (l[p][1] = Math.floor(1e3 * parseFloat(n[1]))));
      var s = t.stack.env.font,
        f = {};
      i
        ? ((l[p][2] = f.fontfamily = i.replace(/'/g, "'")),
          s &&
            (s.match(/bold/) && (f.fontweight = 'bold'),
            s.match(/italic|-mathit/) && (f.fontstyle = 'italic')))
        : s && (f.mathvariant = s);
      var M = t.create('token', 'mtext', f, c.numeric(d));
      u.default.setProperty(M, 'unicode', !0), t.Push(M);
    }),
      new i.CommandMap('unicode', { unicode: 'Unicode' }, e.UnicodeMethods),
      (e.UnicodeConfiguration = n.Configuration.create('unicode', {
        handler: { macro: ['unicode'] },
      }));
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (e.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (e.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.TexError.default);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.AbstractSymbolMap = MathJax._.input.tex.SymbolMap.AbstractSymbolMap),
      (e.RegExpMap = MathJax._.input.tex.SymbolMap.RegExpMap),
      (e.AbstractParseMap = MathJax._.input.tex.SymbolMap.AbstractParseMap),
      (e.CharacterMap = MathJax._.input.tex.SymbolMap.CharacterMap),
      (e.DelimiterMap = MathJax._.input.tex.SymbolMap.DelimiterMap),
      (e.MacroMap = MathJax._.input.tex.SymbolMap.MacroMap),
      (e.CommandMap = MathJax._.input.tex.SymbolMap.CommandMap),
      (e.EnvironmentMap = MathJax._.input.tex.SymbolMap.EnvironmentMap);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.ParseUtil.default);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.NodeUtil.default);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.options = MathJax._.util.Entities.options),
      (e.entities = MathJax._.util.Entities.entities),
      (e.add = MathJax._.util.Entities.add),
      (e.remove = MathJax._.util.Entities.remove),
      (e.translate = MathJax._.util.Entities.translate),
      (e.numeric = MathJax._.util.Entities.numeric);
  },
  function (t, e, a) {
    'use strict';
    a.r(e);
    var n = a(0),
      o = a(1);
    Object(n.combineWithMathJax)({
      _: { input: { tex: { unicode: { UnicodeConfiguration: o } } } },
    });
  },
]);
