!(function (t) {
  var e = {};
  function a(o) {
    if (e[o]) return e[o].exports;
    var n = (e[o] = { i: o, l: !1, exports: {} });
    return t[o].call(n.exports, n, n.exports, a), (n.l = !0), n.exports;
  }
  (a.m = t),
    (a.c = e),
    (a.d = function (t, e, o) {
      a.o(t, e) || Object.defineProperty(t, e, { enumerable: !0, get: o });
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
      var o = Object.create(null);
      if (
        (a.r(o),
        Object.defineProperty(o, 'default', { enumerable: !0, value: t }),
        2 & e && 'string' != typeof t)
      )
        for (var n in t)
          a.d(
            o,
            n,
            function (e) {
              return t[e];
            }.bind(null, n),
          );
      return o;
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
      (e.ExtpfeilConfiguration = e.ExtpfeilMethods = void 0);
    var o = a(2),
      n = a(3),
      r = a(4),
      i = a(5),
      u = a(6),
      l = a(7);
    (e.ExtpfeilMethods = {}),
      (e.ExtpfeilMethods.xArrow = r.AmsMethods.xArrow),
      (e.ExtpfeilMethods.NewExtArrow = function (t, a) {
        var o = t.GetArgument(a),
          n = t.GetArgument(a),
          r = t.GetArgument(a);
        if (!o.match(/^\\([a-z]+|.)$/i))
          throw new l.default(
            'NewextarrowArg1',
            'First argument to %1 must be a control sequence name',
            a,
          );
        if (!n.match(/^(\d+),(\d+)$/))
          throw new l.default(
            'NewextarrowArg2',
            'Second argument to %1 must be two integers separated by a comma',
            a,
          );
        if (!r.match(/^(\d+|0x[0-9A-F]+)$/i))
          throw new l.default(
            'NewextarrowArg3',
            'Third argument to %1 must be a unicode character number',
            a,
          );
        o = o.substr(1);
        var u = n.split(',');
        i.default.addMacro(t, o, e.ExtpfeilMethods.xArrow, [
          parseInt(r),
          parseInt(u[0]),
          parseInt(u[1]),
        ]);
      }),
      new n.CommandMap(
        'extpfeil',
        {
          xtwoheadrightarrow: ['xArrow', 8608, 12, 16],
          xtwoheadleftarrow: ['xArrow', 8606, 17, 13],
          xmapsto: ['xArrow', 8614, 6, 7],
          xlongequal: ['xArrow', 61, 7, 7],
          xtofrom: ['xArrow', 8644, 12, 12],
          Newextarrow: 'NewExtArrow',
        },
        e.ExtpfeilMethods,
      );
    e.ExtpfeilConfiguration = o.Configuration.create('extpfeil', {
      handler: { macro: ['extpfeil'] },
      init: function (t) {
        u.NewcommandConfiguration.init(t);
      },
    });
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
      (e.AmsMethods = MathJax._.input.tex.ams.AmsMethods.AmsMethods),
      (e.NEW_OPS = MathJax._.input.tex.ams.AmsMethods.NEW_OPS);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.newcommand.NewcommandUtil.default);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.NewcommandConfiguration =
        MathJax._.input.tex.newcommand.NewcommandConfiguration.NewcommandConfiguration);
  },
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.TexError.default);
  },
  function (t, e, a) {
    'use strict';
    a.r(e);
    var o = a(0),
      n = a(1);
    Object(o.combineWithMathJax)({
      _: { input: { tex: { extpfeil: { ExtpfeilConfiguration: n } } } },
    });
  },
]);
