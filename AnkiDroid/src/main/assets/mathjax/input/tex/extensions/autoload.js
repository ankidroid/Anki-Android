!(function (e) {
  var t = {};
  function o(a) {
    if (t[a]) return t[a].exports;
    var r = (t[a] = { i: a, l: !1, exports: {} });
    return e[a].call(r.exports, r, r.exports, o), (r.l = !0), r.exports;
  }
  (o.m = e),
    (o.c = t),
    (o.d = function (e, t, a) {
      o.o(e, t) || Object.defineProperty(e, t, { enumerable: !0, get: a });
    }),
    (o.r = function (e) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(e, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(e, '__esModule', { value: !0 });
    }),
    (o.t = function (e, t) {
      if ((1 & t && (e = o(e)), 8 & t)) return e;
      if (4 & t && 'object' == typeof e && e && e.__esModule) return e;
      var a = Object.create(null);
      if (
        (o.r(a),
        Object.defineProperty(a, 'default', { enumerable: !0, value: e }),
        2 & t && 'string' != typeof e)
      )
        for (var r in e)
          o.d(
            a,
            r,
            function (t) {
              return e[t];
            }.bind(null, r),
          );
      return a;
    }),
    (o.n = function (e) {
      var t =
        e && e.__esModule
          ? function () {
              return e.default;
            }
          : function () {
              return e;
            };
      return o.d(t, 'a', t), t;
    }),
    (o.o = function (e, t) {
      return Object.prototype.hasOwnProperty.call(e, t);
    }),
    (o.p = ''),
    o((o.s = 8));
})([
  function (e, t, o) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.isObject = MathJax._.components.global.isObject),
      (t.combineConfig = MathJax._.components.global.combineConfig),
      (t.combineDefaults = MathJax._.components.global.combineDefaults),
      (t.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (t.MathJax = MathJax._.components.global.MathJax);
  },
  function (e, t, o) {
    'use strict';
    var a =
        (this && this.__read) ||
        function (e, t) {
          var o = 'function' == typeof Symbol && e[Symbol.iterator];
          if (!o) return e;
          var a,
            r,
            n = o.call(e),
            i = [];
          try {
            for (; (void 0 === t || t-- > 0) && !(a = n.next()).done; ) i.push(a.value);
          } catch (e) {
            r = { error: e };
          } finally {
            try {
              a && !a.done && (o = n.return) && o.call(n);
            } finally {
              if (r) throw r.error;
            }
          }
          return i;
        },
      r =
        (this && this.__values) ||
        function (e) {
          var t = 'function' == typeof Symbol && Symbol.iterator,
            o = t && e[t],
            a = 0;
          if (o) return o.call(e);
          if (e && 'number' == typeof e.length)
            return {
              next: function () {
                return e && a >= e.length && (e = void 0), { value: e && e[a++], done: !e };
              },
            };
          throw new TypeError(t ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        };
    Object.defineProperty(t, '__esModule', { value: !0 }), (t.AutoloadConfiguration = void 0);
    var n = o(2),
      i = o(3),
      l = o(4),
      u = o(5),
      c = o(6),
      s = o(7);
    function p(e, t, o, n) {
      var i, l, s, p;
      if (c.Package.packages.has(e.options.require.prefix + o)) {
        var x = e.options.autoload[o],
          M = a(2 === x.length && Array.isArray(x[0]) ? x : [x, []], 2),
          b = M[0],
          h = M[1];
        try {
          for (var y = r(b), m = y.next(); !m.done; m = y.next()) {
            var _ = m.value;
            f.remove(_);
          }
        } catch (e) {
          i = { error: e };
        } finally {
          try {
            m && !m.done && (l = y.return) && l.call(y);
          } finally {
            if (i) throw i.error;
          }
        }
        try {
          for (var g = r(h), v = g.next(); !v.done; v = g.next()) {
            var O = v.value;
            d.remove(O);
          }
        } catch (e) {
          s = { error: e };
        } finally {
          try {
            v && !v.done && (p = g.return) && p.call(g);
          } finally {
            if (s) throw s.error;
          }
        }
        (e.string = (n ? t : '\\begin{' + t.slice(1) + '}') + e.string.slice(e.i)), (e.i = 0);
      }
      u.RequireLoad(e, o);
    }
    var f = new i.CommandMap('autoload-macros', {}, {}),
      d = new i.CommandMap('autoload-environments', {}, {});
    t.AutoloadConfiguration = n.Configuration.create('autoload', {
      handler: { macro: ['autoload-macros'], environment: ['autoload-environments'] },
      options: {
        autoload: s.expandable({
          action: ['toggle', 'mathtip', 'texttip'],
          amscd: [[], ['CD']],
          bbox: ['bbox'],
          boldsymbol: ['boldsymbol'],
          braket: [
            'bra',
            'ket',
            'braket',
            'set',
            'Bra',
            'Ket',
            'Braket',
            'Set',
            'ketbra',
            'Ketbra',
          ],
          bussproofs: [[], ['prooftree']],
          cancel: ['cancel', 'bcancel', 'xcancel', 'cancelto'],
          color: ['color', 'definecolor', 'textcolor', 'colorbox', 'fcolorbox'],
          enclose: ['enclose'],
          extpfeil: [
            'xtwoheadrightarrow',
            'xtwoheadleftarrow',
            'xmapsto',
            'xlongequal',
            'xtofrom',
            'Newextarrow',
          ],
          html: ['href', 'class', 'style', 'cssId'],
          mhchem: ['ce', 'pu'],
          newcommand: [
            'newcommand',
            'renewcommand',
            'newenvironment',
            'renewenvironment',
            'def',
            'let',
          ],
          unicode: ['unicode'],
          verb: ['verb'],
        }),
      },
      config: function (e, t) {
        var o,
          n,
          i,
          c,
          s,
          x,
          M = t.parseOptions,
          b = M.handlers.get('macro'),
          h = M.handlers.get('environment'),
          y = M.options.autoload;
        M.packageData.set('autoload', { Autoload: p });
        try {
          for (var m = r(Object.keys(y)), _ = m.next(); !_.done; _ = m.next()) {
            var g = _.value,
              v = y[g],
              O = a(2 === v.length && Array.isArray(v[0]) ? v : [v, []], 2),
              J = O[0],
              C = O[1];
            try {
              for (var P = ((i = void 0), r(J)), S = P.next(); !S.done; S = P.next()) {
                var w = S.value;
                (b.lookup(w) && 'color' !== w) || f.add(w, new l.Macro(w, p, [g, !0]));
              }
            } catch (e) {
              i = { error: e };
            } finally {
              try {
                S && !S.done && (c = P.return) && c.call(P);
              } finally {
                if (i) throw i.error;
              }
            }
            try {
              for (var k = ((s = void 0), r(C)), q = k.next(); !q.done; q = k.next()) {
                var j = q.value;
                h.lookup(j) || d.add(j, new l.Macro(j, p, [g, !1]));
              }
            } catch (e) {
              s = { error: e };
            } finally {
              try {
                q && !q.done && (x = k.return) && x.call(k);
              } finally {
                if (s) throw s.error;
              }
            }
          }
        } catch (e) {
          o = { error: e };
        } finally {
          try {
            _ && !_.done && (n = m.return) && n.call(m);
          } finally {
            if (o) throw o.error;
          }
        }
        M.packageData.get('require') || u.RequireConfiguration.config(e, t);
      },
      init: function (e) {
        e.options.require || s.defaultOptions(e.options, u.RequireConfiguration.options);
      },
      priority: 10,
    });
  },
  function (e, t, o) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (t.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (t.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (e, t, o) {
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
  function (e, t, o) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.Symbol = MathJax._.input.tex.Symbol.Symbol),
      (t.Macro = MathJax._.input.tex.Symbol.Macro);
  },
  function (e, t, o) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.RequireLoad = MathJax._.input.tex.require.RequireConfiguration.RequireLoad),
      (t.RequireMethods = MathJax._.input.tex.require.RequireConfiguration.RequireMethods),
      (t.options = MathJax._.input.tex.require.RequireConfiguration.options),
      (t.RequireConfiguration =
        MathJax._.input.tex.require.RequireConfiguration.RequireConfiguration);
  },
  function (e, t, o) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.PackageError = MathJax._.components.package.PackageError),
      (t.Package = MathJax._.components.package.Package);
  },
  function (e, t, o) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.APPEND = MathJax._.util.Options.APPEND),
      (t.REMOVE = MathJax._.util.Options.REMOVE),
      (t.Expandable = MathJax._.util.Options.Expandable),
      (t.expandable = MathJax._.util.Options.expandable),
      (t.makeArray = MathJax._.util.Options.makeArray),
      (t.keys = MathJax._.util.Options.keys),
      (t.copy = MathJax._.util.Options.copy),
      (t.insert = MathJax._.util.Options.insert),
      (t.defaultOptions = MathJax._.util.Options.defaultOptions),
      (t.userOptions = MathJax._.util.Options.userOptions),
      (t.selectOptions = MathJax._.util.Options.selectOptions),
      (t.selectOptionsFromKeys = MathJax._.util.Options.selectOptionsFromKeys),
      (t.separateOptions = MathJax._.util.Options.separateOptions);
  },
  function (e, t, o) {
    'use strict';
    o.r(t);
    var a = o(0),
      r = o(1);
    Object(a.combineWithMathJax)({
      _: { input: { tex: { autoload: { AutoloadConfiguration: r } } } },
    });
  },
]);
