!(function (e) {
  var t = {};
  function a(r) {
    if (t[r]) return t[r].exports;
    var o = (t[r] = { i: r, l: !1, exports: {} });
    return e[r].call(o.exports, o, o.exports, a), (o.l = !0), o.exports;
  }
  (a.m = e),
    (a.c = t),
    (a.d = function (e, t, r) {
      a.o(e, t) || Object.defineProperty(e, t, { enumerable: !0, get: r });
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
      var r = Object.create(null);
      if (
        (a.r(r),
        Object.defineProperty(r, 'default', { enumerable: !0, value: e }),
        2 & t && 'string' != typeof e)
      )
        for (var o in e)
          a.d(
            r,
            o,
            function (t) {
              return e[t];
            }.bind(null, o),
          );
      return r;
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
    a((a.s = 9));
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
    var r =
        (this && this.__values) ||
        function (e) {
          var t = 'function' == typeof Symbol && Symbol.iterator,
            a = t && e[t],
            r = 0;
          if (a) return a.call(e);
          if (e && 'number' == typeof e.length)
            return {
              next: function () {
                return e && r >= e.length && (e = void 0), { value: e && e[r++], done: !e };
              },
            };
          throw new TypeError(t ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        },
      o =
        (this && this.__read) ||
        function (e, t) {
          var a = 'function' == typeof Symbol && e[Symbol.iterator];
          if (!a) return e;
          var r,
            o,
            n = a.call(e),
            i = [];
          try {
            for (; (void 0 === t || t-- > 0) && !(r = n.next()).done; ) i.push(r.value);
          } catch (e) {
            o = { error: e };
          } finally {
            try {
              r && !r.done && (a = n.return) && a.call(n);
            } finally {
              if (o) throw o.error;
            }
          }
          return i;
        },
      n =
        (this && this.__spread) ||
        function () {
          for (var e = [], t = 0; t < arguments.length; t++) e = e.concat(o(arguments[t]));
          return e;
        };
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.RequireConfiguration = t.options = t.RequireMethods = t.RequireLoad = void 0);
    var i = a(2),
      u = a(3),
      s = a(4),
      p = a(0),
      l = a(5),
      c = a(6),
      f = a(7),
      d = a(8),
      x = p.MathJax.config;
    function h(e, t) {
      var a,
        o = e.parseOptions.options.require,
        n = e.parseOptions.packageData.get('require').required,
        u = t.substr(o.prefix.length);
      if (n.indexOf(u) < 0) {
        n.push(u),
          (function (e, t) {
            var a, o;
            void 0 === t && (t = []);
            var n = e.parseOptions.options.require.prefix;
            try {
              for (var i = r(t), u = i.next(); !u.done; u = i.next()) {
                var s = u.value;
                s.substr(0, n.length) === n && h(e, s);
              }
            } catch (e) {
              a = { error: e };
            } finally {
              try {
                u && !u.done && (o = i.return) && o.call(i);
              } finally {
                if (a) throw a.error;
              }
            }
          })(e, c.CONFIG.dependencies[t]);
        var s = i.ConfigurationHandler.get(u);
        if (s) {
          var p = x[t] || {};
          s.options &&
            1 === Object.keys(s.options).length &&
            s.options[u] &&
            (((a = {})[u] = p), (p = a)),
            e.configuration.add(s, e, p);
          var l = e.parseOptions.packageData.get('require').configured;
          s.preprocessors.length &&
            !l.has(u) &&
            (l.set(u, !0), f.mathjax.retryAfter(Promise.resolve()));
        }
      }
    }
    function M(e, t) {
      var a = e.options.require,
        r = a.allow,
        o = ('[' === t.substr(0, 1) ? '' : a.prefix) + t;
      if (!(r.hasOwnProperty(o) ? r[o] : r.hasOwnProperty(t) ? r[t] : a.defaultAllow))
        throw new s.default('BadRequire', 'Extension "%1" is now allowed to be loaded', o);
      l.Package.packages.has(o)
        ? h(e.configuration.packageData.get('require').jax, o)
        : f.mathjax.retryAfter(c.Loader.load(o));
    }
    (t.RequireLoad = M),
      (t.RequireMethods = {
        Require: function (e, t) {
          var a = e.GetArgument(t);
          if (a.match(/[^_a-zA-Z0-9]/) || '' === a)
            throw new s.default('BadPackageName', 'Argument for %1 is not a valid package name', t);
          M(e, a);
        },
      }),
      (t.options = {
        require: {
          allow: d.expandable({ base: !1, 'all-packages': !1 }),
          defaultAllow: !0,
          prefix: 'tex',
        },
      }),
      new u.CommandMap('require', { require: 'Require' }, t.RequireMethods),
      (t.RequireConfiguration = i.Configuration.create('require', {
        handler: { macro: ['require'] },
        config: function (e, t) {
          t.parseOptions.packageData.set('require', {
            jax: t,
            required: n(t.options.packages),
            configured: new Map(),
          });
          var a = t.parseOptions.options.require,
            r = a.prefix;
          if (r.match(/[^_a-zA-Z0-9]/)) throw Error('Illegal characters used in \\require prefix');
          c.CONFIG.paths[r] || (c.CONFIG.paths[r] = '[mathjax]/input/tex/extensions'),
            (a.prefix = '[' + r + ']/');
        },
        options: t.options,
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
      (t.default = MathJax._.input.tex.TexError.default);
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.PackageError = MathJax._.components.package.PackageError),
      (t.Package = MathJax._.components.package.Package);
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }),
      (t.Loader = MathJax._.components.loader.Loader),
      (t.MathJax = MathJax._.components.loader.MathJax),
      (t.CONFIG = MathJax._.components.loader.CONFIG);
  },
  function (e, t, a) {
    'use strict';
    Object.defineProperty(t, '__esModule', { value: !0 }), (t.mathjax = MathJax._.mathjax.mathjax);
  },
  function (e, t, a) {
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
  function (e, t, a) {
    'use strict';
    a.r(t);
    var r = a(0),
      o = a(1);
    Object(r.combineWithMathJax)({
      _: { input: { tex: { require: { RequireConfiguration: o } } } },
    });
  },
]);
