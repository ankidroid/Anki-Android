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
    a((a.s = 5));
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
      (e.BboxConfiguration = e.BboxMethods = void 0);
    var o = a(2),
      n = a(3),
      i = a(4);
    (e.BboxMethods = {}),
      (e.BboxMethods.BBox = function (t, e) {
        for (
          var a,
            o,
            n,
            l = t.GetBrackets(e, ''),
            c = t.ParseArg(e),
            p = l.split(/,/),
            f = 0,
            s = p.length;
          f < s;
          f++
        ) {
          var b = p[f].trim(),
            d = b.match(/^(\.\d+|\d+(\.\d*)?)(pt|em|ex|mu|px|in|cm|mm)$/);
          if (d) {
            if (a)
              throw new i.default('MultipleBBoxProperty', '%1 specified twice in %2', 'Padding', e);
            var M = u(d[1] + d[3]);
            M &&
              (a = {
                height: '+' + M,
                depth: '+' + M,
                lspace: M,
                width: '+' + 2 * parseInt(d[1], 10) + d[3],
              });
          } else if (b.match(/^([a-z0-9]+|\#[0-9a-f]{6}|\#[0-9a-f]{3})$/i)) {
            if (o)
              throw new i.default(
                'MultipleBBoxProperty',
                '%1 specified twice in %2',
                'Background',
                e,
              );
            o = b;
          } else if (b.match(/^[-a-z]+:/i)) {
            if (n)
              throw new i.default('MultipleBBoxProperty', '%1 specified twice in %2', 'Style', e);
            n = r(b);
          } else if ('' !== b)
            throw new i.default(
              'InvalidBBoxProperty',
              '"%1" doesn\'t look like a color, a padding dimension, or a style',
              b,
            );
        }
        a && (c = t.create('node', 'mpadded', [c], a)),
          (o || n) &&
            ((a = {}),
            o && Object.assign(a, { mathbackground: o }),
            n && Object.assign(a, { style: n }),
            (c = t.create('node', 'mstyle', [c], a))),
          t.Push(c);
      });
    var r = function (t) {
        return t;
      },
      u = function (t) {
        return t;
      };
    new n.CommandMap('bbox', { bbox: 'BBox' }, e.BboxMethods),
      (e.BboxConfiguration = o.Configuration.create('bbox', { handler: { macro: ['bbox'] } }));
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
      (e.default = MathJax._.input.tex.TexError.default);
  },
  function (t, e, a) {
    'use strict';
    a.r(e);
    var o = a(0),
      n = a(1);
    Object(o.combineWithMathJax)({ _: { input: { tex: { bbox: { BboxConfiguration: n } } } } });
  },
]);
