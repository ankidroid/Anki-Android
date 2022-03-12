!(function (t) {
  var e = {};
  function a(n) {
    if (e[n]) return e[n].exports;
    var r = (e[n] = { i: n, l: !1, exports: {} });
    return t[n].call(r.exports, r, r.exports, a), (r.l = !0), r.exports;
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
        for (var r in t)
          a.d(
            n,
            r,
            function (e) {
              return t[e];
            }.bind(null, r),
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
    a((a.s = 6));
})([
  function (t, e, a) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var n = a(5),
      r = {
        Href: function (t, e) {
          var a = t.GetArgument(e),
            r = o(t, e);
          n.default.setAttribute(r, 'href', a), t.Push(r);
        },
        Class: function (t, e) {
          var a = t.GetArgument(e),
            r = o(t, e),
            u = n.default.getAttribute(r, 'class');
          u && (a = u + ' ' + a), n.default.setAttribute(r, 'class', a), t.Push(r);
        },
        Style: function (t, e) {
          var a = t.GetArgument(e),
            r = o(t, e),
            u = n.default.getAttribute(r, 'style');
          u && (';' !== a.charAt(a.length - 1) && (a += ';'), (a = u + ' ' + a)),
            n.default.setAttribute(r, 'style', a),
            t.Push(r);
        },
        Id: function (t, e) {
          var a = t.GetArgument(e),
            r = o(t, e);
          n.default.setAttribute(r, 'id', a), t.Push(r);
        },
      },
      o = function (t, e) {
        var a = t.ParseArg(e);
        if (!n.default.isInferred(a)) return a;
        var r = n.default.getChildren(a);
        if (1 === r.length) return r[0];
        var o = t.create('node', 'mrow');
        return n.default.copyChildren(a, o), n.default.copyAttributes(a, o), o;
      };
    e.default = r;
  },
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
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.HtmlConfiguration = void 0);
    var n = a(3),
      r = a(4),
      o = a(0);
    new r.CommandMap(
      'html_macros',
      { href: 'Href', class: 'Class', style: 'Style', cssId: 'Id' },
      o.default,
    ),
      (e.HtmlConfiguration = n.Configuration.create('html', {
        handler: { macro: ['html_macros'] },
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
      (e.default = MathJax._.input.tex.NodeUtil.default);
  },
  function (t, e, a) {
    'use strict';
    a.r(e);
    var n = a(1),
      r = a(2),
      o = a(0);
    Object(n.combineWithMathJax)({
      _: { input: { tex: { html: { HtmlConfiguration: r, HtmlMethods: o } } } },
    });
  },
]);
