!(function (e) {
  var t = {};
  function n(a) {
    if (t[a]) return t[a].exports;
    var o = (t[a] = { i: a, l: !1, exports: {} });
    return e[a].call(o.exports, o, o.exports, n), (o.l = !0), o.exports;
  }
  (n.m = e),
    (n.c = t),
    (n.d = function (e, t, a) {
      n.o(e, t) || Object.defineProperty(e, t, { enumerable: !0, get: a });
    }),
    (n.r = function (e) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(e, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(e, '__esModule', { value: !0 });
    }),
    (n.t = function (e, t) {
      if ((1 & t && (e = n(e)), 8 & t)) return e;
      if (4 & t && 'object' == typeof e && e && e.__esModule) return e;
      var a = Object.create(null);
      if (
        (n.r(a),
        Object.defineProperty(a, 'default', { enumerable: !0, value: e }),
        2 & t && 'string' != typeof e)
      )
        for (var o in e)
          n.d(
            a,
            o,
            function (t) {
              return e[t];
            }.bind(null, o),
          );
      return a;
    }),
    (n.n = function (e) {
      var t =
        e && e.__esModule
          ? function () {
              return e.default;
            }
          : function () {
              return e;
            };
      return n.d(t, 'a', t), t;
    }),
    (n.o = function (e, t) {
      return Object.prototype.hasOwnProperty.call(e, t);
    }),
    (n.p = ''),
    n((n.s = 7));
})([
  function (e, t, n) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.isObject = MathJax._.components.global.isObject),
      (t.combineConfig = MathJax._.components.global.combineConfig),
      (t.combineDefaults = MathJax._.components.global.combineDefaults),
      (t.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (t.MathJax = MathJax._.components.global.MathJax);
  },
  function (e, t, n) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.CancelConfiguration = t.CancelMethods = void 0);
    var a = n(2),
      o = n(3),
      i = n(4),
      r = n(5),
      c = n(6);
    (t.CancelMethods = {}),
      (t.CancelMethods.Cancel = function (e, t, n) {
        var a = e.GetBrackets(t, ''),
          o = e.ParseArg(t),
          i = r.default.keyvalOptions(a, c.ENCLOSE_OPTIONS);
        (i.notation = n), e.Push(e.create('node', 'menclose', [o], i));
      }),
      (t.CancelMethods.CancelTo = function (e, t) {
        var n = e.GetBrackets(t, ''),
          a = e.ParseArg(t),
          i = e.ParseArg(t),
          u = r.default.keyvalOptions(n, c.ENCLOSE_OPTIONS);
        (u.notation = [
          o.TexConstant.Notation.UPDIAGONALSTRIKE,
          o.TexConstant.Notation.UPDIAGONALARROW,
          o.TexConstant.Notation.NORTHEASTARROW,
        ].join(' ')),
          (a = e.create('node', 'mpadded', [a], {
            depth: '-.1em',
            height: '+.1em',
            voffset: '.1em',
          })),
          e.Push(e.create('node', 'msup', [e.create('node', 'menclose', [i], u), a]));
      }),
      new i.CommandMap(
        'cancel',
        {
          cancel: ['Cancel', o.TexConstant.Notation.UPDIAGONALSTRIKE],
          bcancel: ['Cancel', o.TexConstant.Notation.DOWNDIAGONALSTRIKE],
          xcancel: [
            'Cancel',
            o.TexConstant.Notation.UPDIAGONALSTRIKE +
              ' ' +
              o.TexConstant.Notation.DOWNDIAGONALSTRIKE,
          ],
          cancelto: 'CancelTo',
        },
        t.CancelMethods,
      ),
      (t.CancelConfiguration = a.Configuration.create('cancel', {
        handler: { macro: ['cancel'] },
      }));
  },
  function (e, t, n) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (t.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (t.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (e, t, n) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.TexConstant = MathJax._.input.tex.TexConstants.TexConstant);
  },
  function (e, t, n) {
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
  function (e, t, n) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.default = MathJax._.input.tex.ParseUtil.default);
  },
  function (e, t, n) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.ENCLOSE_OPTIONS = MathJax._.input.tex.enclose.EncloseConfiguration.ENCLOSE_OPTIONS),
      (t.EncloseMethods = MathJax._.input.tex.enclose.EncloseConfiguration.EncloseMethods),
      (t.EncloseConfiguration =
        MathJax._.input.tex.enclose.EncloseConfiguration.EncloseConfiguration);
  },
  function (e, t, n) {
    'use strict';
    n.r(t);
    var a = n(0),
      o = n(1);
    Object(a.combineWithMathJax)({ _: { input: { tex: { cancel: { CancelConfiguration: o } } } } });
  },
]);
