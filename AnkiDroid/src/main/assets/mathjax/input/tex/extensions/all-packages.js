!(function (t) {
  var e = {};
  function r(a) {
    if (e[a]) return e[a].exports;
    var n = (e[a] = { i: a, l: !1, exports: {} });
    return t[a].call(n.exports, n, n.exports, r), (n.l = !0), n.exports;
  }
  (r.m = t),
    (r.c = e),
    (r.d = function (t, e, a) {
      r.o(t, e) || Object.defineProperty(t, e, { enumerable: !0, get: a });
    }),
    (r.r = function (t) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(t, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(t, '__esModule', { value: !0 });
    }),
    (r.t = function (t, e) {
      if ((1 & e && (t = r(t)), 8 & e)) return t;
      if (4 & e && 'object' == typeof t && t && t.__esModule) return t;
      var a = Object.create(null);
      if (
        (r.r(a),
        Object.defineProperty(a, 'default', { enumerable: !0, value: t }),
        2 & e && 'string' != typeof t)
      )
        for (var n in t)
          r.d(
            a,
            n,
            function (e) {
              return t[e];
            }.bind(null, n),
          );
      return a;
    }),
    (r.n = function (t) {
      var e =
        t && t.__esModule
          ? function () {
              return t.default;
            }
          : function () {
              return t;
            };
      return r.d(e, 'a', e), e;
    }),
    (r.o = function (t, e) {
      return Object.prototype.hasOwnProperty.call(t, e);
    }),
    (r.p = ''),
    r((r.s = 78));
})([
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (e.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (e.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (t, e, r) {
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
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.TexError.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.ParseUtil.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.NodeUtil.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.TexParser.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.TexConstant = MathJax._.input.tex.TexConstants.TexConstant);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.TEXCLASS = MathJax._.core.MmlTree.MmlNode.TEXCLASS),
      (e.TEXCLASSNAMES = MathJax._.core.MmlTree.MmlNode.TEXCLASSNAMES),
      (e.indentAttributes = MathJax._.core.MmlTree.MmlNode.indentAttributes),
      (e.AbstractMmlNode = MathJax._.core.MmlTree.MmlNode.AbstractMmlNode),
      (e.AbstractMmlTokenNode = MathJax._.core.MmlTree.MmlNode.AbstractMmlTokenNode),
      (e.AbstractMmlLayoutNode = MathJax._.core.MmlTree.MmlNode.AbstractMmlLayoutNode),
      (e.AbstractMmlBaseNode = MathJax._.core.MmlTree.MmlNode.AbstractMmlBaseNode),
      (e.AbstractMmlEmptyNode = MathJax._.core.MmlTree.MmlNode.AbstractMmlEmptyNode),
      (e.TextNode = MathJax._.core.MmlTree.MmlNode.TextNode),
      (e.XMLNode = MathJax._.core.MmlTree.MmlNode.XMLNode);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.base.BaseMethods.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.ParseMethods.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.APPEND = MathJax._.util.Options.APPEND),
      (e.REMOVE = MathJax._.util.Options.REMOVE),
      (e.Expandable = MathJax._.util.Options.Expandable),
      (e.expandable = MathJax._.util.Options.expandable),
      (e.makeArray = MathJax._.util.Options.makeArray),
      (e.keys = MathJax._.util.Options.keys),
      (e.copy = MathJax._.util.Options.copy),
      (e.insert = MathJax._.util.Options.insert),
      (e.defaultOptions = MathJax._.util.Options.defaultOptions),
      (e.userOptions = MathJax._.util.Options.userOptions),
      (e.selectOptions = MathJax._.util.Options.selectOptions),
      (e.selectOptionsFromKeys = MathJax._.util.Options.selectOptionsFromKeys),
      (e.separateOptions = MathJax._.util.Options.separateOptions);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.NEW_OPS = e.AmsMethods = void 0);
    var a = r(3),
      n = r(4),
      o = r(6),
      i = r(5),
      s = r(2),
      c = r(12),
      l = r(8),
      u = r(7);
    (e.AmsMethods = {}),
      (e.AmsMethods.AmsEqnArray = function (t, e, r, n, o, i, s) {
        var c = t.GetBrackets('\\begin{' + e.getName() + '}'),
          u = l.default.EqnArray(t, e, r, n, o, i, s);
        return a.default.setArrayAlign(u, c);
      }),
      (e.AmsMethods.AlignAt = function (t, r, n, o) {
        var i,
          c,
          l = r.getName(),
          u = '',
          d = [];
        if (
          (o || (c = t.GetBrackets('\\begin{' + l + '}')),
          (i = t.GetArgument('\\begin{' + l + '}')).match(/[^0-9]/))
        )
          throw new s.default(
            'PositiveIntegerArg',
            'Argument to %1 must me a positive integer',
            '\\begin{' + l + '}',
          );
        for (var p = parseInt(i, 10); p > 0; ) (u += 'rl'), d.push('0em 0em'), p--;
        var f = d.join(' ');
        if (o) return e.AmsMethods.EqnArray(t, r, n, o, u, f);
        var m = e.AmsMethods.EqnArray(t, r, n, o, u, f);
        return a.default.setArrayAlign(m, c);
      }),
      (e.AmsMethods.Multline = function (t, e, r) {
        t.Push(e), a.default.checkEqnEnv(t);
        var n = t.itemFactory.create('multline', r, t.stack);
        return (
          (n.arraydef = {
            displaystyle: !0,
            rowspacing: '.5em',
            columnwidth: '100%',
            width: t.options.multlineWidth,
            side: t.options.tagSide,
            minlabelspacing: t.options.tagIndent,
          }),
          n
        );
      }),
      (e.NEW_OPS = 'ams-declare-ops'),
      (e.AmsMethods.HandleDeclareOp = function (t, r) {
        var n = t.GetStar() ? '' : '\\nolimits\\SkipLimits',
          o = a.default.trimSpaces(t.GetArgument(r));
        '\\' === o.charAt(0) && (o = o.substr(1));
        var i = t.GetArgument(r);
        i.match(/\\text/) || (i = i.replace(/\*/g, '\\text{*}').replace(/-/g, '\\text{-}')),
          t.configuration.handlers
            .retrieve(e.NEW_OPS)
            .add(o, new c.Macro(o, e.AmsMethods.Macro, ['\\mathop{\\rm ' + i + '}' + n]));
      }),
      (e.AmsMethods.HandleOperatorName = function (t, e) {
        var r = t.GetStar() ? '' : '\\nolimits\\SkipLimits',
          n = a.default.trimSpaces(t.GetArgument(e));
        n.match(/\\text/) || (n = n.replace(/\*/g, '\\text{*}').replace(/-/g, '\\text{-}')),
          (t.string = '\\mathop{\\rm ' + n + '}' + r + ' ' + t.string.slice(t.i)),
          (t.i = 0);
      }),
      (e.AmsMethods.SkipLimits = function (t, e) {
        var r = t.GetNext(),
          a = t.i;
        '\\' === r && ++t.i && 'limits' !== t.GetCS() && (t.i = a);
      }),
      (e.AmsMethods.MultiIntegral = function (t, e, r) {
        var a = t.GetNext();
        if ('\\' === a) {
          var n = t.i;
          (a = t.GetArgument(e)),
            (t.i = n),
            '\\limits' === a &&
              (r =
                '\\idotsint' === e
                  ? '\\!\\!\\mathop{\\,\\,' + r + '}'
                  : '\\!\\!\\!\\mathop{\\,\\,\\,' + r + '}');
        }
        (t.string = r + ' ' + t.string.slice(t.i)), (t.i = 0);
      }),
      (e.AmsMethods.xArrow = function (t, e, r, o, s) {
        var c = { width: '+' + a.default.Em((o + s) / 18), lspace: a.default.Em(o / 18) },
          l = t.GetBrackets(e),
          d = t.ParseArg(e),
          p = t.create(
            'token',
            'mo',
            { stretchy: !0, texClass: u.TEXCLASS.REL },
            String.fromCodePoint(r),
          ),
          f = t.create('node', 'munderover', [p]),
          m = t.create('node', 'mpadded', [d], c);
        if ((n.default.setAttribute(m, 'voffset', '.15em'), n.default.setChild(f, f.over, m), l)) {
          var h = new i.default(l, t.stack.env, t.configuration).mml();
          (m = t.create('node', 'mpadded', [h], c)),
            n.default.setAttribute(m, 'voffset', '-.24em'),
            n.default.setChild(f, f.under, m);
        }
        n.default.setProperty(f, 'subsupOK', !0), t.Push(f);
      }),
      (e.AmsMethods.HandleShove = function (t, e, r) {
        var a = t.stack.Top();
        if ('multline' !== a.kind)
          throw new s.default(
            'CommandOnlyAllowedInEnv',
            '%1 only allowed in %2 environment',
            t.currentCS,
            'multline',
          );
        if (a.Size())
          throw new s.default(
            'CommandAtTheBeginingOfLine',
            '%1 must come at the beginning of the line',
            t.currentCS,
          );
        a.setProperty('shove', r);
      }),
      (e.AmsMethods.CFrac = function (t, e) {
        var r = a.default.trimSpaces(t.GetBrackets(e, '')),
          c = t.GetArgument(e),
          l = t.GetArgument(e),
          u = { l: o.TexConstant.Align.LEFT, r: o.TexConstant.Align.RIGHT, '': '' },
          d = new i.default('\\strut\\textstyle{' + c + '}', t.stack.env, t.configuration).mml(),
          p = new i.default('\\strut\\textstyle{' + l + '}', t.stack.env, t.configuration).mml(),
          f = t.create('node', 'mfrac', [d, p]);
        if (null == (r = u[r]))
          throw new s.default('IllegalAlign', 'Illegal alignment specified in %1', t.currentCS);
        r && n.default.setProperties(f, { numalign: r, denomalign: r }), t.Push(f);
      }),
      (e.AmsMethods.Genfrac = function (t, e, r, o, i, c) {
        null == r && (r = t.GetDelimiterArg(e)),
          null == o && (o = t.GetDelimiterArg(e)),
          null == i && (i = t.GetArgument(e)),
          null == c && (c = a.default.trimSpaces(t.GetArgument(e)));
        var l = t.ParseArg(e),
          u = t.ParseArg(e),
          d = t.create('node', 'mfrac', [l, u]);
        if (
          ('' !== i && n.default.setAttribute(d, 'linethickness', i),
          (r || o) &&
            (n.default.setProperty(d, 'withDelims', !0),
            (d = a.default.fixedFence(t.configuration, r, d, o))),
          '' !== c)
        ) {
          var p = parseInt(c, 10),
            f = ['D', 'T', 'S', 'SS'][p];
          if (null == f)
            throw new s.default('BadMathStyleFor', 'Bad math style for %1', t.currentCS);
          (d = t.create('node', 'mstyle', [d])),
            'D' === f
              ? n.default.setProperties(d, { displaystyle: !0, scriptlevel: 0 })
              : n.default.setProperties(d, { displaystyle: !1, scriptlevel: p - 1 });
        }
        t.Push(d);
      }),
      (e.AmsMethods.HandleTag = function (t, e) {
        if (!t.tags.currentTag.taggable && t.tags.env)
          throw new s.default(
            'CommandNotAllowedInEnv',
            '%1 not allowed in %2 environment',
            t.currentCS,
            t.tags.env,
          );
        if (t.tags.currentTag.tag)
          throw new s.default('MultipleCommand', 'Multiple %1', t.currentCS);
        var r = t.GetStar(),
          n = a.default.trimSpaces(t.GetArgument(e));
        t.tags.tag(n, r);
      }),
      (e.AmsMethods.HandleNoTag = l.default.HandleNoTag),
      (e.AmsMethods.HandleRef = l.default.HandleRef),
      (e.AmsMethods.Macro = l.default.Macro),
      (e.AmsMethods.Accent = l.default.Accent),
      (e.AmsMethods.Tilde = l.default.Tilde),
      (e.AmsMethods.Array = l.default.Array),
      (e.AmsMethods.Spacer = l.default.Spacer),
      (e.AmsMethods.NamedOp = l.default.NamedOp),
      (e.AmsMethods.EqnArray = l.default.EqnArray);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Symbol = MathJax._.input.tex.Symbol.Symbol),
      (e.Macro = MathJax._.input.tex.Symbol.Macro);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.MmlStack = MathJax._.input.tex.StackItem.MmlStack),
      (e.BaseItem = MathJax._.input.tex.StackItem.BaseItem);
  },
  function (t, e, r) {
    'use strict';
    var a =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            r = e && t[e],
            a = 0;
          if (r) return r.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        },
      n =
        (this && this.__read) ||
        function (t, e) {
          var r = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!r) return t;
          var a,
            n,
            o = r.call(t),
            i = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(a = o.next()).done; ) i.push(a.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              a && !a.done && (r = o.return) && r.call(o);
            } finally {
              if (n) throw n.error;
            }
          }
          return i;
        },
      o =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(n(arguments[e]));
          return t;
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.RequireConfiguration = e.options = e.RequireMethods = e.RequireLoad = void 0);
    var i = r(0),
      s = r(1),
      c = r(2),
      l = r(20),
      u = r(28),
      d = r(76),
      p = r(77),
      f = r(10),
      m = l.MathJax.config;
    function h(t, e) {
      var r,
        n = t.parseOptions.options.require,
        o = t.parseOptions.packageData.get('require').required,
        s = e.substr(n.prefix.length);
      if (o.indexOf(s) < 0) {
        o.push(s),
          (function (t, e) {
            var r, n;
            void 0 === e && (e = []);
            var o = t.parseOptions.options.require.prefix;
            try {
              for (var i = a(e), s = i.next(); !s.done; s = i.next()) {
                var c = s.value;
                c.substr(0, o.length) === o && h(t, c);
              }
            } catch (t) {
              r = { error: t };
            } finally {
              try {
                s && !s.done && (n = i.return) && n.call(i);
              } finally {
                if (r) throw r.error;
              }
            }
          })(t, d.CONFIG.dependencies[e]);
        var c = i.ConfigurationHandler.get(s);
        if (c) {
          var l = m[e] || {};
          c.options &&
            1 === Object.keys(c.options).length &&
            c.options[s] &&
            (((r = {})[s] = l), (l = r)),
            t.configuration.add(c, t, l);
          var u = t.parseOptions.packageData.get('require').configured;
          c.preprocessors.length &&
            !u.has(s) &&
            (u.set(s, !0), p.mathjax.retryAfter(Promise.resolve()));
        }
      }
    }
    function g(t, e) {
      var r = t.options.require,
        a = r.allow,
        n = ('[' === e.substr(0, 1) ? '' : r.prefix) + e;
      if (!(a.hasOwnProperty(n) ? a[n] : a.hasOwnProperty(e) ? a[e] : r.defaultAllow))
        throw new c.default('BadRequire', 'Extension "%1" is now allowed to be loaded', n);
      u.Package.packages.has(n)
        ? h(t.configuration.packageData.get('require').jax, n)
        : p.mathjax.retryAfter(d.Loader.load(n));
    }
    (e.RequireLoad = g),
      (e.RequireMethods = {
        Require: function (t, e) {
          var r = t.GetArgument(e);
          if (r.match(/[^_a-zA-Z0-9]/) || '' === r)
            throw new c.default('BadPackageName', 'Argument for %1 is not a valid package name', e);
          g(t, r);
        },
      }),
      (e.options = {
        require: {
          allow: f.expandable({ base: !1, 'all-packages': !1 }),
          defaultAllow: !0,
          prefix: 'tex',
        },
      }),
      new s.CommandMap('require', { require: 'Require' }, e.RequireMethods),
      (e.RequireConfiguration = i.Configuration.create('require', {
        handler: { macro: ['require'] },
        config: function (t, e) {
          e.parseOptions.packageData.set('require', {
            jax: e,
            required: o(e.options.packages),
            configured: new Map(),
          });
          var r = e.parseOptions.options.require,
            a = r.prefix;
          if (a.match(/[^_a-zA-Z0-9]/)) throw Error('Illegal characters used in \\require prefix');
          d.CONFIG.paths[a] || (d.CONFIG.paths[a] = '[mathjax]/input/tex/extensions'),
            (r.prefix = '[' + a + ']/');
        },
        options: e.options,
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.AllPackages = void 0),
      r(22),
      r(29),
      r(30),
      r(33),
      r(36),
      r(37),
      r(38),
      r(42),
      r(47),
      r(48),
      r(52),
      r(53),
      r(24),
      r(54),
      r(57),
      r(59),
      r(26),
      r(61),
      r(62),
      r(63),
      r(67),
      r(68),
      r(73),
      r(75),
      'undefined' != typeof MathJax &&
        MathJax.loader &&
        MathJax.loader.preLoad(
          '[tex]/action',
          '[tex]/ams',
          '[tex]/amscd',
          '[tex]/bbox',
          '[tex]/boldsymbol',
          '[tex]/braket',
          '[tex]/bussproofs',
          '[tex]/cancel',
          '[tex]/color',
          '[tex]/colorv2',
          '[tex]/enclose',
          '[tex]/extpfeil',
          '[tex]/html',
          '[tex]/mhchem',
          '[tex]/newcommand',
          '[tex]/noerrors',
          '[tex]/noundefined',
          '[tex]/physics',
          '[tex]/unicode',
          '[tex]/verb',
          '[tex]/configmacros',
          '[tex]/tagformat',
          '[tex]/textmacros',
        ),
      (e.AllPackages = [
        'base',
        'action',
        'ams',
        'amscd',
        'bbox',
        'boldsymbol',
        'braket',
        'bussproofs',
        'cancel',
        'color',
        'enclose',
        'extpfeil',
        'html',
        'mhchem',
        'newcommand',
        'noerrors',
        'noundefined',
        'unicode',
        'verb',
        'configmacros',
        'tagformat',
        'textmacros',
      ]);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.StartItem = MathJax._.input.tex.base.BaseItems.StartItem),
      (e.StopItem = MathJax._.input.tex.base.BaseItems.StopItem),
      (e.OpenItem = MathJax._.input.tex.base.BaseItems.OpenItem),
      (e.CloseItem = MathJax._.input.tex.base.BaseItems.CloseItem),
      (e.PrimeItem = MathJax._.input.tex.base.BaseItems.PrimeItem),
      (e.SubsupItem = MathJax._.input.tex.base.BaseItems.SubsupItem),
      (e.OverItem = MathJax._.input.tex.base.BaseItems.OverItem),
      (e.LeftItem = MathJax._.input.tex.base.BaseItems.LeftItem),
      (e.RightItem = MathJax._.input.tex.base.BaseItems.RightItem),
      (e.BeginItem = MathJax._.input.tex.base.BaseItems.BeginItem),
      (e.EndItem = MathJax._.input.tex.base.BaseItems.EndItem),
      (e.StyleItem = MathJax._.input.tex.base.BaseItems.StyleItem),
      (e.PositionItem = MathJax._.input.tex.base.BaseItems.PositionItem),
      (e.CellItem = MathJax._.input.tex.base.BaseItems.CellItem),
      (e.MmlItem = MathJax._.input.tex.base.BaseItems.MmlItem),
      (e.FnItem = MathJax._.input.tex.base.BaseItems.FnItem),
      (e.NotItem = MathJax._.input.tex.base.BaseItems.NotItem),
      (e.DotsItem = MathJax._.input.tex.base.BaseItems.DotsItem),
      (e.ArrayItem = MathJax._.input.tex.base.BaseItems.ArrayItem),
      (e.EqnArrayItem = MathJax._.input.tex.base.BaseItems.EqnArrayItem),
      (e.EquationItem = MathJax._.input.tex.base.BaseItems.EquationItem);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Label = MathJax._.input.tex.Tags.Label),
      (e.TagInfo = MathJax._.input.tex.Tags.TagInfo),
      (e.AbstractTags = MathJax._.input.tex.Tags.AbstractTags),
      (e.NoTags = MathJax._.input.tex.Tags.NoTags),
      (e.AllTags = MathJax._.input.tex.Tags.AllTags),
      (e.TagsFactory = MathJax._.input.tex.Tags.TagsFactory);
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__read) ||
        function (t, e) {
          var r = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!r) return t;
          var a,
            n,
            o = r.call(t),
            i = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(a = o.next()).done; ) i.push(a.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              a && !a.done && (r = o.return) && r.call(o);
            } finally {
              if (n) throw n.error;
            }
          }
          return i;
        },
      o =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            r = e && t[e],
            a = 0;
          if (r) return r.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.clearDocument =
        e.saveDocument =
        e.makeBsprAttributes =
        e.removeProperty =
        e.getProperty =
        e.setProperty =
        e.balanceRules =
          void 0);
    var i = r(4),
      s = r(3),
      c = null,
      l = null,
      u = function (t) {
        return (l.root = t), c.outputJax.getBBox(l, c).w;
      },
      d = function (t) {
        for (var e = 0; t && !i.default.isType(t, 'mtable'); ) {
          if (i.default.isType(t, 'text')) return null;
          i.default.isType(t, 'mrow')
            ? ((t = t.childNodes[0]), (e = 0))
            : ((t = t.parent.childNodes[e]), e++);
        }
        return t;
      },
      p = function (t, e) {
        return t.childNodes['up' === e ? 1 : 0].childNodes[0].childNodes[0].childNodes[0]
          .childNodes[0];
      },
      f = function (t, e) {
        return t.childNodes[e].childNodes[0].childNodes[0];
      },
      m = function (t) {
        return f(t, 0);
      },
      h = function (t) {
        return f(t, t.childNodes.length - 1);
      },
      g = function (t, e) {
        return t.childNodes['up' === e ? 0 : 1].childNodes[0].childNodes[0].childNodes[0];
      },
      v = function (t) {
        for (; t && !i.default.isType(t, 'mtd'); ) t = t.parent;
        return t;
      },
      y = function (t) {
        return t.parent.childNodes[t.parent.childNodes.indexOf(t) + 1];
      },
      x = function (t) {
        for (; t && null == e.getProperty(t, 'inference'); ) t = t.parent;
        return t;
      },
      b = function (t, e, r) {
        void 0 === r && (r = !1);
        var a = 0;
        if (t === e) return a;
        if (t !== e.parent) {
          var n = t.childNodes,
            o = r ? n.length - 1 : 0;
          i.default.isType(n[o], 'mspace') && (a += u(n[o])), (t = e.parent);
        }
        if (t === e) return a;
        var s = t.childNodes,
          c = r ? s.length - 1 : 0;
        return s[c] !== e && (a += u(s[c])), a;
      },
      _ = function (t, r) {
        void 0 === r && (r = !1);
        var a = d(t),
          n = g(a, e.getProperty(a, 'inferenceRule'));
        return b(t, a, r) + (u(a) - u(n)) / 2;
      },
      M = function (t, r, a, n) {
        if (
          (void 0 === n && (n = !1),
          e.getProperty(r, 'inferenceRule') || e.getProperty(r, 'labelledRule'))
        ) {
          var o = t.nodeFactory.create('node', 'mrow');
          r.parent.replaceChild(o, r), o.setChildren([r]), A(r, o), (r = o);
        }
        var c = n ? r.childNodes.length - 1 : 0,
          l = r.childNodes[c];
        i.default.isType(l, 'mspace')
          ? i.default.setAttribute(
              l,
              'width',
              s.default.Em(s.default.dimen2em(i.default.getAttribute(l, 'width')) + a),
            )
          : ((l = t.nodeFactory.create('node', 'mspace', [], { width: s.default.Em(a) })),
            n ? r.appendChild(l) : ((l.parent = r), r.childNodes.unshift(l)));
      },
      A = function (t, r) {
        ['inference', 'proof', 'maxAdjust', 'labelledRule'].forEach(function (a) {
          var n = e.getProperty(t, a);
          null != n && (e.setProperty(r, a, n), e.removeProperty(t, a));
        });
      },
      w = function (t, r, a, n, o) {
        var i = t.nodeFactory.create('node', 'mspace', [], { width: s.default.Em(o) });
        if ('left' === n) {
          var c = r.childNodes[a].childNodes[0];
          (i.parent = c), c.childNodes.unshift(i);
        } else r.childNodes[a].appendChild(i);
        e.setProperty(r.parent, 'sequentAdjust_' + n, o);
      },
      C = function (t, r) {
        for (var a = r.pop(); r.length; ) {
          var o = r.pop(),
            i = n(S(a, o), 2),
            s = i[0],
            c = i[1];
          e.getProperty(a.parent, 'axiom') &&
            (w(t, s < 0 ? a : o, 0, 'left', Math.abs(s)),
            w(t, c < 0 ? a : o, 2, 'right', Math.abs(c))),
            (a = o);
        }
      },
      S = function (t, e) {
        var r = u(t.childNodes[2]),
          a = u(e.childNodes[2]);
        return [u(t.childNodes[0]) - u(e.childNodes[0]), r - a];
      };
    e.balanceRules = function (t) {
      var r, a;
      l = new t.document.options.MathItem('', null, t.math.display);
      var n = t.data;
      !(function (t) {
        var r = t.nodeLists.sequent;
        if (r)
          for (var a = r.length - 1, n = void 0; (n = r[a]); a--)
            if (e.getProperty(n, 'sequentProcessed')) e.removeProperty(n, 'sequentProcessed');
            else {
              var o = [],
                i = x(n);
              if (1 === e.getProperty(i, 'inference')) {
                for (o.push(n); 1 === e.getProperty(i, 'inference'); ) {
                  i = d(i);
                  var s = m(p(i, e.getProperty(i, 'inferenceRule'))),
                    c = e.getProperty(s, 'inferenceRule')
                      ? g(s, e.getProperty(s, 'inferenceRule'))
                      : s;
                  e.getProperty(c, 'sequent') &&
                    ((n = c.childNodes[0]), o.push(n), e.setProperty(n, 'sequentProcessed', !0)),
                    (i = s);
                }
                C(t, o);
              }
            }
      })(n);
      var i = n.nodeLists.inference || [];
      try {
        for (var s = o(i), c = s.next(); !c.done; c = s.next()) {
          var u = c.value,
            f = e.getProperty(u, 'proof'),
            A = d(u),
            w = p(A, e.getProperty(A, 'inferenceRule')),
            S = m(w);
          if (e.getProperty(S, 'inference')) {
            var P = _(S);
            if (P) {
              M(n, S, -P);
              var k = b(u, A, !1);
              M(n, u, P - k);
            }
          }
          var O = h(w);
          if (null != e.getProperty(O, 'inference')) {
            var T = _(O, !0);
            M(n, O, -T, !0);
            var q = b(u, A, !0),
              E = e.getProperty(u, 'maxAdjust');
            null != E && (T = Math.max(T, E));
            var I = void 0;
            if (!f && (I = v(u))) {
              var N = y(I);
              if (N) {
                var B = n.nodeFactory.create('node', 'mspace', [], { width: T - q + 'em' });
                N.appendChild(B), u.removeProperty('maxAdjust');
              } else {
                var G = x(I);
                G &&
                  ((T = e.getProperty(G, 'maxAdjust')
                    ? Math.max(e.getProperty(G, 'maxAdjust'), T)
                    : T),
                  e.setProperty(G, 'maxAdjust', T));
              }
            } else M(n, e.getProperty(u, 'proof') ? u : u.parent, T - q, !0);
          }
        }
      } catch (t) {
        r = { error: t };
      } finally {
        try {
          c && !c.done && (a = s.return) && a.call(s);
        } finally {
          if (r) throw r.error;
        }
      }
    };
    var P = (((a = {}).bspr_maxAdjust = !0), a);
    (e.setProperty = function (t, e, r) {
      i.default.setProperty(t, 'bspr_' + e, r);
    }),
      (e.getProperty = function (t, e) {
        return i.default.getProperty(t, 'bspr_' + e);
      }),
      (e.removeProperty = function (t, e) {
        t.removeProperty('bspr_' + e);
      }),
      (e.makeBsprAttributes = function (t) {
        t.data.root.walkTree(function (t, e) {
          var r = [];
          t.getPropertyNames().forEach(function (e) {
            !P[e] && e.match(RegExp('^bspr_')) && r.push(e + ':' + t.getProperty(e));
          }),
            r.length && i.default.setAttribute(t, 'semantics', r.join(';'));
        });
      }),
      (e.saveDocument = function (t) {
        if (!('getBBox' in (c = t.document).outputJax))
          throw Error('The bussproofs extension requires an output jax with a getBBox() method');
      }),
      (e.clearDocument = function (t) {
        c = null;
      });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a,
      n = r(3),
      o = r(2),
      i = r(12);
    !(function (t) {
      function e(t, e) {
        return t.string.substr(t.i, e.length) !== e ||
          (e.match(/\\[a-z]+$/i) && t.string.charAt(t.i + e.length).match(/[a-z]/i))
          ? 0
          : ((t.i += e.length), 1);
      }
      (t.disassembleSymbol = function (t, e) {
        var r = [t, e.char];
        if (e.attributes) for (var a in e.attributes) r.push(a), r.push(e.attributes[a]);
        return r;
      }),
        (t.assembleSymbol = function (t) {
          for (var e = t[0], r = t[1], a = {}, n = 2; n < t.length; n += 2) a[t[n]] = t[n + 1];
          return new i.Symbol(e, r, a);
        }),
        (t.GetCSname = function (t, e) {
          if ('\\' !== t.GetNext())
            throw new o.default('MissingCS', '%1 must be followed by a control sequence', e);
          return n.default.trimSpaces(t.GetArgument(e)).substr(1);
        }),
        (t.GetTemplate = function (t, e, r) {
          for (var a = t.GetNext(), n = [], i = 0, s = t.i; t.i < t.string.length; ) {
            if ('#' === (a = t.GetNext())) {
              if (
                (s !== t.i && (n[i] = t.string.substr(s, t.i - s)),
                !(a = t.string.charAt(++t.i)).match(/^[1-9]$/))
              )
                throw new o.default('CantUseHash2', 'Illegal use of # in template for %1', r);
              if (parseInt(a) !== ++i)
                throw new o.default(
                  'SequentialParam',
                  'Parameters for %1 must be numbered sequentially',
                  r,
                );
              s = t.i + 1;
            } else if ('{' === a)
              return (
                s !== t.i && (n[i] = t.string.substr(s, t.i - s)),
                n.length > 0 ? [i.toString()].concat(n) : i
              );
            t.i++;
          }
          throw new o.default(
            'MissingReplacementString',
            'Missing replacement string for definition of %1',
            e,
          );
        }),
        (t.GetParameter = function (t, r, a) {
          if (null == a) return t.GetArgument(r);
          for (var n = t.i, i = 0, s = 0; t.i < t.string.length; ) {
            var c = t.string.charAt(t.i);
            if ('{' === c) t.i === n && (s = 1), t.GetArgument(r), (i = t.i - n);
            else {
              if (e(t, a)) return s && (n++, (i -= 2)), t.string.substr(n, i);
              if ('\\' === c) {
                t.i++, i++, (s = 0);
                var l = t.string.substr(t.i).match(/[a-z]+|./i);
                l && ((t.i += l[0].length), (i = t.i - n));
              } else t.i++, i++, (s = 0);
            }
          }
          throw new o.default('RunawayArgument', 'Runaway argument for %1?', r);
        }),
        (t.MatchParam = e),
        (t.addDelimiter = function (e, r, a, n) {
          e.configuration.handlers.retrieve(t.NEW_DELIMITER).add(r, new i.Symbol(r, a, n));
        }),
        (t.addMacro = function (e, r, a, n, o) {
          void 0 === o && (o = ''),
            e.configuration.handlers.retrieve(t.NEW_COMMAND).add(r, new i.Macro(o || r, a, n));
        }),
        (t.addEnvironment = function (e, r, a, n) {
          e.configuration.handlers.retrieve(t.NEW_ENVIRONMENT).add(r, new i.Macro(r, a, n));
        }),
        (t.NEW_DELIMITER = 'new-Delimiter'),
        (t.NEW_COMMAND = 'new-Command'),
        (t.NEW_ENVIRONMENT = 'new-Environment');
    })(a || (a = {})),
      (e.default = a);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.isObject = MathJax._.components.global.isObject),
      (e.combineConfig = MathJax._.components.global.combineConfig),
      (e.combineDefaults = MathJax._.components.global.combineDefaults),
      (e.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (e.MathJax = MathJax._.components.global.MathJax);
  },
  function (t, e, r) {
    'use strict';
    var a =
        (this && this.__read) ||
        function (t, e) {
          var r = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!r) return t;
          var a,
            n,
            o = r.call(t),
            i = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(a = o.next()).done; ) i.push(a.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              a && !a.done && (r = o.return) && r.call(o);
            } finally {
              if (n) throw n.error;
            }
          }
          return i;
        },
      n =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            r = e && t[e],
            a = 0;
          if (r) return r.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.AutoloadConfiguration = void 0);
    var o = r(0),
      i = r(1),
      s = r(12),
      c = r(14),
      l = r(28),
      u = r(10);
    function d(t, e, r, o) {
      var i, s, u, d;
      if (l.Package.packages.has(t.options.require.prefix + r)) {
        var m = t.options.autoload[r],
          h = a(2 === m.length && Array.isArray(m[0]) ? m : [m, []], 2),
          g = h[0],
          v = h[1];
        try {
          for (var y = n(g), x = y.next(); !x.done; x = y.next()) {
            var b = x.value;
            p.remove(b);
          }
        } catch (t) {
          i = { error: t };
        } finally {
          try {
            x && !x.done && (s = y.return) && s.call(y);
          } finally {
            if (i) throw i.error;
          }
        }
        try {
          for (var _ = n(v), M = _.next(); !M.done; M = _.next()) {
            var A = M.value;
            f.remove(A);
          }
        } catch (t) {
          u = { error: t };
        } finally {
          try {
            M && !M.done && (d = _.return) && d.call(_);
          } finally {
            if (u) throw u.error;
          }
        }
        (t.string = (o ? e : '\\begin{' + e.slice(1) + '}') + t.string.slice(t.i)), (t.i = 0);
      }
      c.RequireLoad(t, r);
    }
    var p = new i.CommandMap('autoload-macros', {}, {}),
      f = new i.CommandMap('autoload-environments', {}, {});
    e.AutoloadConfiguration = o.Configuration.create('autoload', {
      handler: { macro: ['autoload-macros'], environment: ['autoload-environments'] },
      options: {
        autoload: u.expandable({
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
      config: function (t, e) {
        var r,
          o,
          i,
          l,
          u,
          m,
          h = e.parseOptions,
          g = h.handlers.get('macro'),
          v = h.handlers.get('environment'),
          y = h.options.autoload;
        h.packageData.set('autoload', { Autoload: d });
        try {
          for (var x = n(Object.keys(y)), b = x.next(); !b.done; b = x.next()) {
            var _ = b.value,
              M = y[_],
              A = a(2 === M.length && Array.isArray(M[0]) ? M : [M, []], 2),
              w = A[0],
              C = A[1];
            try {
              for (var S = ((i = void 0), n(w)), P = S.next(); !P.done; P = S.next()) {
                var k = P.value;
                (g.lookup(k) && 'color' !== k) || p.add(k, new s.Macro(k, d, [_, !0]));
              }
            } catch (t) {
              i = { error: t };
            } finally {
              try {
                P && !P.done && (l = S.return) && l.call(S);
              } finally {
                if (i) throw i.error;
              }
            }
            try {
              for (var O = ((u = void 0), n(C)), T = O.next(); !T.done; T = O.next()) {
                var q = T.value;
                v.lookup(q) || f.add(q, new s.Macro(q, d, [_, !1]));
              }
            } catch (t) {
              u = { error: t };
            } finally {
              try {
                T && !T.done && (m = O.return) && m.call(O);
              } finally {
                if (u) throw u.error;
              }
            }
          }
        } catch (t) {
          r = { error: t };
        } finally {
          try {
            b && !b.done && (o = x.return) && o.call(x);
          } finally {
            if (r) throw r.error;
          }
        }
        h.packageData.get('require') || c.RequireConfiguration.config(t, e);
      },
      init: function (t) {
        t.options.require || u.defaultOptions(t.options, c.RequireConfiguration.options);
      },
      priority: 10,
    });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Other = MathJax._.input.tex.base.BaseConfiguration.Other),
      (e.BaseTags = MathJax._.input.tex.base.BaseConfiguration.BaseTags),
      (e.BaseConfiguration = MathJax._.input.tex.base.BaseConfiguration.BaseConfiguration);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.NodeFactory = MathJax._.input.tex.NodeFactory.NodeFactory);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.EncloseConfiguration = e.EncloseMethods = e.ENCLOSE_OPTIONS = void 0);
    var a = r(0),
      n = r(1),
      o = r(3);
    (e.ENCLOSE_OPTIONS = {
      'data-arrowhead': 1,
      color: 1,
      mathcolor: 1,
      background: 1,
      mathbackground: 1,
      'data-padding': 1,
      'data-thickness': 1,
    }),
      (e.EncloseMethods = {}),
      (e.EncloseMethods.Enclose = function (t, r) {
        var a = t.GetArgument(r).replace(/,/g, ' '),
          n = t.GetBrackets(r, ''),
          i = t.ParseArg(r),
          s = o.default.keyvalOptions(n, e.ENCLOSE_OPTIONS);
        (s.notation = a), t.Push(t.create('node', 'menclose', [i], s));
      }),
      new n.CommandMap('enclose', { enclose: 'Enclose' }, e.EncloseMethods),
      (e.EncloseConfiguration = a.Configuration.create('enclose', {
        handler: { macro: ['enclose'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(2),
      n = r(1),
      o = r(8),
      i = r(3),
      s = r(19),
      c = {
        NewCommand: function (t, e) {
          var r = i.default.trimSpaces(t.GetArgument(e)),
            n = t.GetBrackets(e),
            o = t.GetBrackets(e),
            l = t.GetArgument(e);
          if (('\\' === r.charAt(0) && (r = r.substr(1)), !r.match(/^(.|[a-z]+)$/i)))
            throw new a.default(
              'IllegalControlSequenceName',
              'Illegal control sequence name for %1',
              e,
            );
          if (n && !(n = i.default.trimSpaces(n)).match(/^[0-9]+$/))
            throw new a.default(
              'IllegalParamNumber',
              'Illegal number of parameters specified in %1',
              e,
            );
          s.default.addMacro(t, r, c.Macro, [l, n, o]);
        },
        NewEnvironment: function (t, e) {
          var r = i.default.trimSpaces(t.GetArgument(e)),
            n = t.GetBrackets(e),
            o = t.GetBrackets(e),
            l = t.GetArgument(e),
            u = t.GetArgument(e);
          if (n && !(n = i.default.trimSpaces(n)).match(/^[0-9]+$/))
            throw new a.default(
              'IllegalParamNumber',
              'Illegal number of parameters specified in %1',
              e,
            );
          s.default.addEnvironment(t, r, c.BeginEnv, [!0, l, u, n, o]);
        },
        MacroDef: function (t, e) {
          var r = s.default.GetCSname(t, e),
            a = s.default.GetTemplate(t, e, '\\' + r),
            n = t.GetArgument(e);
          a instanceof Array
            ? s.default.addMacro(t, r, c.MacroWithTemplate, [n].concat(a))
            : s.default.addMacro(t, r, c.Macro, [n, a]);
        },
        Let: function (t, e) {
          var r = s.default.GetCSname(t, e),
            a = t.GetNext();
          '=' === a && (t.i++, (a = t.GetNext()));
          var o = t.configuration.handlers;
          if ('\\' !== a) {
            t.i++;
            var i = o.get('delimiter').lookup(a);
            i
              ? s.default.addDelimiter(t, '\\' + r, i.char, i.attributes)
              : s.default.addMacro(t, r, c.Macro, [a]);
          } else {
            e = s.default.GetCSname(t, e);
            var l = o.get('delimiter').lookup('\\' + e);
            if (l) return void s.default.addDelimiter(t, '\\' + r, l.char, l.attributes);
            var u = o.get('macro').applicable(e);
            if (!u) return;
            if (u instanceof n.MacroMap) {
              var d = u.lookup(e);
              return void s.default.addMacro(t, r, d.func, d.args, d.symbol);
            }
            l = u.lookup(e);
            var p = s.default.disassembleSymbol(r, l);
            s.default.addMacro(
              t,
              r,
              function (t, e) {
                for (var r = [], a = 2; a < arguments.length; a++) r[a - 2] = arguments[a];
                var n = s.default.assembleSymbol(r);
                return u.parser(t, n);
              },
              p,
            );
          }
        },
        MacroWithTemplate: function (t, e, r, n) {
          for (var o = [], c = 4; c < arguments.length; c++) o[c - 4] = arguments[c];
          var l = parseInt(n, 10);
          if (l) {
            var u = [];
            if ((t.GetNext(), o[0] && !s.default.MatchParam(t, o[0])))
              throw new a.default('MismatchUseDef', "Use of %1 doesn't match its definition", e);
            for (var d = 0; d < l; d++) u.push(s.default.GetParameter(t, e, o[d + 1]));
            r = i.default.substituteArgs(t, u, r);
          }
          if (
            ((t.string = i.default.addArgs(t, r, t.string.slice(t.i))),
            (t.i = 0),
            ++t.macroCount > t.configuration.options.maxMacros)
          )
            throw new a.default(
              'MaxMacroSub1',
              'MathJax maximum macro substitution count exceeded; is here a recursive macro call?',
            );
        },
        BeginEnv: function (t, e, r, a, n, o) {
          if (e.getProperty('end') && t.stack.env.closing === e.getName()) {
            delete t.stack.env.closing;
            var s = t.string.slice(t.i);
            return (
              (t.string = a),
              (t.i = 0),
              t.Parse(),
              (t.string = s),
              (t.i = 0),
              t.itemFactory.create('end').setProperty('name', e.getName())
            );
          }
          if (n) {
            var c = [];
            if (null != o) {
              var l = t.GetBrackets('\\begin{' + e.getName() + '}');
              c.push(null == l ? o : l);
            }
            for (var u = c.length; u < n; u++)
              c.push(t.GetArgument('\\begin{' + e.getName() + '}'));
            (r = i.default.substituteArgs(t, c, r)), (a = i.default.substituteArgs(t, [], a));
          }
          return (
            (t.string = i.default.addArgs(t, r, t.string.slice(t.i))),
            (t.i = 0),
            t.itemFactory.create('beginEnv').setProperty('name', e.getName())
          );
        },
      };
    (c.Macro = o.default.Macro), (e.default = c);
  },
  function (t, e, r) {
    'use strict';
    var a;
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.NewcommandConfiguration = void 0);
    var n = r(0),
      o = r(55),
      i = r(19);
    r(56);
    var s = r(9),
      c = r(1);
    e.NewcommandConfiguration = n.Configuration.create('newcommand', {
      handler: { macro: ['Newcommand-macros'] },
      items: ((a = {}), (a[o.BeginEnvItem.prototype.kind] = o.BeginEnvItem), a),
      options: { maxMacros: 1e3 },
      init: function (t) {
        new c.DelimiterMap(i.default.NEW_DELIMITER, s.default.delimiter, {}),
          new c.CommandMap(i.default.NEW_COMMAND, {}, {}),
          new c.EnvironmentMap(i.default.NEW_ENVIRONMENT, s.default.environment, {}, {}),
          t.append(
            n.Configuration.local({
              handler: {
                character: [],
                delimiter: [i.default.NEW_DELIMITER],
                macro: [i.default.NEW_DELIMITER, i.default.NEW_COMMAND],
                environment: [i.default.NEW_ENVIRONMENT],
              },
              priority: -1,
            }),
          );
      },
    });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.TextMacrosMethods = void 0);
    var a = r(5),
      n = r(71),
      o = r(8);
    e.TextMacrosMethods = {
      Comment: function (t, e) {
        for (; t.i < t.string.length && '\n' !== t.string.charAt(t.i); ) t.i++;
        t.i++;
      },
      Math: function (t, e) {
        t.saveText();
        for (var r, n, o = t.i, i = 0; (n = t.GetNext()); )
          switch (((r = t.i++), n)) {
            case '\\':
              ')' === t.GetCS() && (n = '\\(');
            case '$':
              if (0 === i && e === n) {
                var s = t.texParser.configuration,
                  c = new a.default(t.string.substr(o, r - o), t.stack.env, s).mml();
                return void t.PushMath(c);
              }
              break;
            case '{':
              i++;
              break;
            case '}':
              0 === i &&
                t.Error('ExtraCloseMissingOpen', 'Extra close brace or missing open brace'),
                i--;
          }
        t.Error('MathNotTerminated', 'Math-mode is not properly terminated');
      },
      MathModeOnly: function (t, e) {
        t.Error('MathModeOnly', "'%1' allowed only in math mode", e);
      },
      Misplaced: function (t, e) {
        t.Error('Misplaced', "'%1' can not be used here", e);
      },
      OpenBrace: function (t, e) {
        var r = t.stack.env;
        t.envStack.push(r), (t.stack.env = Object.assign({}, r));
      },
      CloseBrace: function (t, e) {
        t.envStack.length
          ? (t.saveText(), (t.stack.env = t.envStack.pop()))
          : t.Error('ExtraCloseMissingOpen', 'Extra close brace or missing open brace');
      },
      OpenQuote: function (t, e) {
        t.string.charAt(t.i) === e ? ((t.text += '\u201c'), t.i++) : (t.text += '\u2018');
      },
      CloseQuote: function (t, e) {
        t.string.charAt(t.i) === e ? ((t.text += '\u201d'), t.i++) : (t.text += '\u2019');
      },
      Tilde: function (t, e) {
        t.text += '\xa0';
      },
      Space: function (t, e) {
        for (t.text += ' '; t.GetNext().match(/\s/); ) t.i++;
      },
      SelfQuote: function (t, e) {
        t.text += e.substr(1);
      },
      Insert: function (t, e, r) {
        t.text += r;
      },
      Accent: function (t, e, r) {
        var a = t.ParseArg(name),
          n = t.create('token', 'mo', {}, r);
        t.addAttributes(n), t.Push(t.create('node', 'mover', [a, n]));
      },
      Emph: function (t, e) {
        var r = '-tex-mathit' === t.stack.env.mathvariant ? 'normal' : '-tex-mathit';
        t.Push(t.ParseTextArg(e, { mathvariant: r }));
      },
      SetFont: function (t, e, r) {
        t.saveText(), (t.stack.env.mathvariant = r);
      },
      SetSize: function (t, e, r) {
        t.saveText(), (t.stack.env.mathsize = r);
      },
      CheckAutoload: function (t, e) {
        var r = t.configuration.packageData.get('autoload'),
          a = t.texParser;
        e = e.slice(1);
        var o = a.lookup('macro', e);
        if (!o || (r && o._func === r.Autoload)) {
          if ((a.parse('macro', [a, e]), !o)) return;
          n.retryAfter(Promise.resolve());
        }
        a.parse('macro', [t, e]);
      },
      Macro: o.default.Macro,
      Spacer: o.default.Spacer,
      Hskip: o.default.Hskip,
      rule: o.default.rule,
      Rule: o.default.Rule,
      HandleRef: o.default.HandleRef,
    };
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.PackageError = MathJax._.components.package.PackageError),
      (e.Package = MathJax._.components.package.Package);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.ActionConfiguration = e.ActionMethods = void 0);
    var a = r(0),
      n = r(5),
      o = r(1),
      i = r(8);
    (e.ActionMethods = {}),
      (e.ActionMethods.Macro = i.default.Macro),
      (e.ActionMethods.Toggle = function (t, e) {
        for (var r, a = []; '\\endtoggle' !== (r = t.GetArgument(e)); )
          a.push(new n.default(r, t.stack.env, t.configuration).mml());
        t.Push(t.create('node', 'maction', a, { actiontype: 'toggle' }));
      }),
      (e.ActionMethods.Mathtip = function (t, e) {
        var r = t.ParseArg(e),
          a = t.ParseArg(e);
        t.Push(t.create('node', 'maction', [r, a], { actiontype: 'tooltip' }));
      }),
      new o.CommandMap(
        'action-macros',
        {
          toggle: 'Toggle',
          mathtip: 'Mathtip',
          texttip: ['Macro', '\\mathtip{#1}{\\text{#2}}', 2],
        },
        e.ActionMethods,
      ),
      (e.ActionConfiguration = a.Configuration.create('action', {
        handler: { macro: ['action-macros'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    var a,
      n,
      o =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.AmsConfiguration = e.AmsTags = void 0);
    var i = r(0),
      s = r(31),
      c = r(17),
      l = r(11);
    r(32);
    var u = r(1),
      d = (function (t) {
        function e() {
          return (null !== t && t.apply(this, arguments)) || this;
        }
        return o(e, t), e;
      })(c.AbstractTags);
    e.AmsTags = d;
    e.AmsConfiguration = i.Configuration.create('ams', {
      handler: {
        delimiter: ['AMSsymbols-delimiter', 'AMSmath-delimiter'],
        macro: [
          'AMSsymbols-mathchar0mi',
          'AMSsymbols-mathchar0m0',
          'AMSsymbols-delimiter',
          'AMSsymbols-macros',
          'AMSmath-mathchar0mo',
          'AMSmath-macros',
          'AMSmath-delimiter',
        ],
        environment: ['AMSmath-environment'],
      },
      items: ((n = {}), (n[s.MultlineItem.prototype.kind] = s.MultlineItem), n),
      tags: { ams: d },
      init: function (t) {
        new u.CommandMap(l.NEW_OPS, {}, {}),
          t.append(i.Configuration.local({ handler: { macro: [l.NEW_OPS] }, priority: -1 }));
      },
    });
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.MultlineItem = void 0);
    var o = r(16),
      i = r(3),
      s = r(4),
      c = r(2),
      l = r(6),
      u = (function (t) {
        function e(e) {
          for (var r = [], a = 1; a < arguments.length; a++) r[a - 1] = arguments[a];
          var n = t.call(this, e) || this;
          return n.factory.configuration.tags.start('multline', !0, r[0]), n;
        }
        return (
          n(e, t),
          Object.defineProperty(e.prototype, 'kind', {
            get: function () {
              return 'multline';
            },
            enumerable: !1,
            configurable: !0,
          }),
          (e.prototype.EndEntry = function () {
            this.table.length && i.default.fixInitialMO(this.factory.configuration, this.nodes);
            var t = this.getProperty('shove'),
              e = this.create('node', 'mtd', this.nodes, t ? { columnalign: t } : {});
            this.setProperty('shove', null), this.row.push(e), this.Clear();
          }),
          (e.prototype.EndRow = function () {
            if (1 !== this.row.length)
              throw new c.default(
                'MultlineRowsOneCol',
                'The rows within the %1 environment must have exactly one column',
                'multline',
              );
            var t = this.create('node', 'mtr', this.row);
            this.table.push(t), (this.row = []);
          }),
          (e.prototype.EndTable = function () {
            if ((t.prototype.EndTable.call(this), this.table.length)) {
              var e = this.table.length - 1,
                r = -1;
              s.default.getAttribute(s.default.getChildren(this.table[0])[0], 'columnalign') ||
                s.default.setAttribute(
                  s.default.getChildren(this.table[0])[0],
                  'columnalign',
                  l.TexConstant.Align.LEFT,
                ),
                s.default.getAttribute(s.default.getChildren(this.table[e])[0], 'columnalign') ||
                  s.default.setAttribute(
                    s.default.getChildren(this.table[e])[0],
                    'columnalign',
                    l.TexConstant.Align.RIGHT,
                  );
              var a = this.factory.configuration.tags.getTag();
              if (a) {
                r = this.arraydef.side === l.TexConstant.Align.LEFT ? 0 : this.table.length - 1;
                var n = this.table[r],
                  o = this.create('node', 'mlabeledtr', [a].concat(s.default.getChildren(n)));
                s.default.copyAttributes(n, o), (this.table[r] = o);
              }
            }
            this.factory.configuration.tags.end();
          }),
          e
        );
      })(o.ArrayItem);
    e.MultlineItem = u;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(11),
      n = r(1),
      o = r(6),
      i = r(9),
      s = r(3),
      c = r(7),
      l = function (t) {
        for (var e = [], r = 0, a = t.length; r < a; r++) e[r] = s.default.Em(t[r]);
        return e.join(' ');
      };
    new n.CharacterMap('AMSmath-mathchar0mo', i.default.mathchar0mo, {
      iiiint: ['\u2a0c', { texClass: c.TEXCLASS.OP }],
    }),
      new n.CommandMap(
        'AMSmath-macros',
        {
          mathring: ['Accent', '02DA'],
          nobreakspace: 'Tilde',
          negmedspace: ['Spacer', o.TexConstant.Length.NEGATIVEMEDIUMMATHSPACE],
          negthickspace: ['Spacer', o.TexConstant.Length.NEGATIVETHICKMATHSPACE],
          idotsint: ['MultiIntegral', '\\int\\cdots\\int'],
          dddot: ['Accent', '20DB'],
          ddddot: ['Accent', '20DC'],
          sideset: [
            'Macro',
            '\\mathop{\\mathop{\\rlap{\\phantom{#3}}}\\nolimits#1\\!\\mathop{#3}\\nolimits#2}',
            3,
          ],
          boxed: ['Macro', '\\fbox{$\\displaystyle{#1}$}', 1],
          tag: 'HandleTag',
          notag: 'HandleNoTag',
          eqref: ['HandleRef', !0],
          substack: ['Macro', '\\begin{subarray}{c}#1\\end{subarray}', 1],
          injlim: ['NamedOp', 'inj&thinsp;lim'],
          projlim: ['NamedOp', 'proj&thinsp;lim'],
          varliminf: ['Macro', '\\mathop{\\underline{\\mmlToken{mi}{lim}}}'],
          varlimsup: ['Macro', '\\mathop{\\overline{\\mmlToken{mi}{lim}}}'],
          varinjlim: ['Macro', '\\mathop{\\underrightarrow{\\mmlToken{mi}{lim}}}'],
          varprojlim: ['Macro', '\\mathop{\\underleftarrow{\\mmlToken{mi}{lim}}}'],
          DeclareMathOperator: 'HandleDeclareOp',
          operatorname: 'HandleOperatorName',
          SkipLimits: 'SkipLimits',
          genfrac: 'Genfrac',
          frac: ['Genfrac', '', '', '', ''],
          tfrac: ['Genfrac', '', '', '', '1'],
          dfrac: ['Genfrac', '', '', '', '0'],
          binom: ['Genfrac', '(', ')', '0', ''],
          tbinom: ['Genfrac', '(', ')', '0', '1'],
          dbinom: ['Genfrac', '(', ')', '0', '0'],
          cfrac: 'CFrac',
          shoveleft: ['HandleShove', o.TexConstant.Align.LEFT],
          shoveright: ['HandleShove', o.TexConstant.Align.RIGHT],
          xrightarrow: ['xArrow', 8594, 5, 6],
          xleftarrow: ['xArrow', 8592, 7, 3],
        },
        a.AmsMethods,
      ),
      new n.EnvironmentMap(
        'AMSmath-environment',
        i.default.environment,
        {
          'eqnarray*': [
            'EqnArray',
            null,
            !1,
            !0,
            'rcl',
            '0 ' + o.TexConstant.Length.THICKMATHSPACE,
            '.5em',
          ],
          align: ['EqnArray', null, !0, !0, 'rlrlrlrlrlrl', l([0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0])],
          'align*': [
            'EqnArray',
            null,
            !1,
            !0,
            'rlrlrlrlrlrl',
            l([0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0]),
          ],
          multline: ['Multline', null, !0],
          'multline*': ['Multline', null, !1],
          split: ['EqnArray', null, !1, !1, 'rl', l([0])],
          gather: ['EqnArray', null, !0, !0, 'c'],
          'gather*': ['EqnArray', null, !1, !0, 'c'],
          alignat: ['AlignAt', null, !0, !0],
          'alignat*': ['AlignAt', null, !1, !0],
          alignedat: ['AlignAt', null, !1, !1],
          aligned: [
            'AmsEqnArray',
            null,
            null,
            null,
            'rlrlrlrlrlrl',
            l([0, 2, 0, 2, 0, 2, 0, 2, 0, 2, 0]),
            '.5em',
            'D',
          ],
          gathered: ['AmsEqnArray', null, null, null, 'c', null, '.5em', 'D'],
          subarray: ['Array', null, null, null, null, l([0]), '0.1em', 'S', 1],
          smallmatrix: ['Array', null, null, null, 'c', l([1 / 3]), '.2em', 'S', 1],
          matrix: ['Array', null, null, null, 'c'],
          pmatrix: ['Array', null, '(', ')', 'c'],
          bmatrix: ['Array', null, '[', ']', 'c'],
          Bmatrix: ['Array', null, '\\{', '\\}', 'c'],
          vmatrix: ['Array', null, '\\vert', '\\vert', 'c'],
          Vmatrix: ['Array', null, '\\Vert', '\\Vert', 'c'],
          cases: ['Array', null, '\\{', '.', 'll', null, '.2em', 'T'],
        },
        a.AmsMethods,
      ),
      new n.DelimiterMap('AMSmath-delimiter', i.default.delimiter, {
        '\\lvert': ['|', { texClass: c.TEXCLASS.OPEN }],
        '\\rvert': ['|', { texClass: c.TEXCLASS.CLOSE }],
        '\\lVert': ['\u2016', { texClass: c.TEXCLASS.OPEN }],
        '\\rVert': ['\u2016', { texClass: c.TEXCLASS.CLOSE }],
      }),
      new n.CharacterMap('AMSsymbols-mathchar0mi', i.default.mathchar0mi, {
        digamma: '\u03dd',
        varkappa: '\u03f0',
        varGamma: ['\u0393', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varDelta: ['\u0394', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varTheta: ['\u0398', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varLambda: ['\u039b', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varXi: ['\u039e', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varPi: ['\u03a0', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varSigma: ['\u03a3', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varUpsilon: ['\u03a5', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varPhi: ['\u03a6', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varPsi: ['\u03a8', { mathvariant: o.TexConstant.Variant.ITALIC }],
        varOmega: ['\u03a9', { mathvariant: o.TexConstant.Variant.ITALIC }],
        beth: '\u2136',
        gimel: '\u2137',
        daleth: '\u2138',
        backprime: ['\u2035', { variantForm: !0 }],
        hslash: '\u210f',
        varnothing: ['\u2205', { variantForm: !0 }],
        blacktriangle: '\u25b4',
        triangledown: ['\u25bd', { variantForm: !0 }],
        blacktriangledown: '\u25be',
        square: '\u25fb',
        Box: '\u25fb',
        blacksquare: '\u25fc',
        lozenge: '\u25ca',
        Diamond: '\u25ca',
        blacklozenge: '\u29eb',
        circledS: ['\u24c8', { mathvariant: o.TexConstant.Variant.NORMAL }],
        bigstar: '\u2605',
        sphericalangle: '\u2222',
        measuredangle: '\u2221',
        nexists: '\u2204',
        complement: '\u2201',
        mho: '\u2127',
        eth: ['\xf0', { mathvariant: o.TexConstant.Variant.NORMAL }],
        Finv: '\u2132',
        diagup: '\u2571',
        Game: '\u2141',
        diagdown: '\u2572',
        Bbbk: ['k', { mathvariant: o.TexConstant.Variant.DOUBLESTRUCK }],
        yen: '\xa5',
        circledR: '\xae',
        checkmark: '\u2713',
        maltese: '\u2720',
      }),
      new n.CharacterMap('AMSsymbols-mathchar0m0', i.default.mathchar0mo, {
        dotplus: '\u2214',
        ltimes: '\u22c9',
        smallsetminus: ['\u2216', { variantForm: !0 }],
        rtimes: '\u22ca',
        Cap: '\u22d2',
        doublecap: '\u22d2',
        leftthreetimes: '\u22cb',
        Cup: '\u22d3',
        doublecup: '\u22d3',
        rightthreetimes: '\u22cc',
        barwedge: '\u22bc',
        curlywedge: '\u22cf',
        veebar: '\u22bb',
        curlyvee: '\u22ce',
        doublebarwedge: '\u2a5e',
        boxminus: '\u229f',
        circleddash: '\u229d',
        boxtimes: '\u22a0',
        circledast: '\u229b',
        boxdot: '\u22a1',
        circledcirc: '\u229a',
        boxplus: '\u229e',
        centerdot: ['\u22c5', { variantForm: !0 }],
        divideontimes: '\u22c7',
        intercal: '\u22ba',
        leqq: '\u2266',
        geqq: '\u2267',
        leqslant: '\u2a7d',
        geqslant: '\u2a7e',
        eqslantless: '\u2a95',
        eqslantgtr: '\u2a96',
        lesssim: '\u2272',
        gtrsim: '\u2273',
        lessapprox: '\u2a85',
        gtrapprox: '\u2a86',
        approxeq: '\u224a',
        lessdot: '\u22d6',
        gtrdot: '\u22d7',
        lll: '\u22d8',
        llless: '\u22d8',
        ggg: '\u22d9',
        gggtr: '\u22d9',
        lessgtr: '\u2276',
        gtrless: '\u2277',
        lesseqgtr: '\u22da',
        gtreqless: '\u22db',
        lesseqqgtr: '\u2a8b',
        gtreqqless: '\u2a8c',
        doteqdot: '\u2251',
        Doteq: '\u2251',
        eqcirc: '\u2256',
        risingdotseq: '\u2253',
        circeq: '\u2257',
        fallingdotseq: '\u2252',
        triangleq: '\u225c',
        backsim: '\u223d',
        thicksim: ['\u223c', { variantForm: !0 }],
        backsimeq: '\u22cd',
        thickapprox: ['\u2248', { variantForm: !0 }],
        subseteqq: '\u2ac5',
        supseteqq: '\u2ac6',
        Subset: '\u22d0',
        Supset: '\u22d1',
        sqsubset: '\u228f',
        sqsupset: '\u2290',
        preccurlyeq: '\u227c',
        succcurlyeq: '\u227d',
        curlyeqprec: '\u22de',
        curlyeqsucc: '\u22df',
        precsim: '\u227e',
        succsim: '\u227f',
        precapprox: '\u2ab7',
        succapprox: '\u2ab8',
        vartriangleleft: '\u22b2',
        lhd: '\u22b2',
        vartriangleright: '\u22b3',
        rhd: '\u22b3',
        trianglelefteq: '\u22b4',
        unlhd: '\u22b4',
        trianglerighteq: '\u22b5',
        unrhd: '\u22b5',
        vDash: ['\u22a8', { variantForm: !0 }],
        Vdash: '\u22a9',
        Vvdash: '\u22aa',
        smallsmile: ['\u2323', { variantForm: !0 }],
        shortmid: ['\u2223', { variantForm: !0 }],
        smallfrown: ['\u2322', { variantForm: !0 }],
        shortparallel: ['\u2225', { variantForm: !0 }],
        bumpeq: '\u224f',
        between: '\u226c',
        Bumpeq: '\u224e',
        pitchfork: '\u22d4',
        varpropto: ['\u221d', { variantForm: !0 }],
        backepsilon: '\u220d',
        blacktriangleleft: '\u25c2',
        blacktriangleright: '\u25b8',
        therefore: '\u2234',
        because: '\u2235',
        eqsim: '\u2242',
        vartriangle: ['\u25b3', { variantForm: !0 }],
        Join: '\u22c8',
        nless: '\u226e',
        ngtr: '\u226f',
        nleq: '\u2270',
        ngeq: '\u2271',
        nleqslant: ['\u2a87', { variantForm: !0 }],
        ngeqslant: ['\u2a88', { variantForm: !0 }],
        nleqq: ['\u2270', { variantForm: !0 }],
        ngeqq: ['\u2271', { variantForm: !0 }],
        lneq: '\u2a87',
        gneq: '\u2a88',
        lneqq: '\u2268',
        gneqq: '\u2269',
        lvertneqq: ['\u2268', { variantForm: !0 }],
        gvertneqq: ['\u2269', { variantForm: !0 }],
        lnsim: '\u22e6',
        gnsim: '\u22e7',
        lnapprox: '\u2a89',
        gnapprox: '\u2a8a',
        nprec: '\u2280',
        nsucc: '\u2281',
        npreceq: ['\u22e0', { variantForm: !0 }],
        nsucceq: ['\u22e1', { variantForm: !0 }],
        precneqq: '\u2ab5',
        succneqq: '\u2ab6',
        precnsim: '\u22e8',
        succnsim: '\u22e9',
        precnapprox: '\u2ab9',
        succnapprox: '\u2aba',
        nsim: '\u2241',
        ncong: '\u2247',
        nshortmid: ['\u2224', { variantForm: !0 }],
        nshortparallel: ['\u2226', { variantForm: !0 }],
        nmid: '\u2224',
        nparallel: '\u2226',
        nvdash: '\u22ac',
        nvDash: '\u22ad',
        nVdash: '\u22ae',
        nVDash: '\u22af',
        ntriangleleft: '\u22ea',
        ntriangleright: '\u22eb',
        ntrianglelefteq: '\u22ec',
        ntrianglerighteq: '\u22ed',
        nsubseteq: '\u2288',
        nsupseteq: '\u2289',
        nsubseteqq: ['\u2288', { variantForm: !0 }],
        nsupseteqq: ['\u2289', { variantForm: !0 }],
        subsetneq: '\u228a',
        supsetneq: '\u228b',
        varsubsetneq: ['\u228a', { variantForm: !0 }],
        varsupsetneq: ['\u228b', { variantForm: !0 }],
        subsetneqq: '\u2acb',
        supsetneqq: '\u2acc',
        varsubsetneqq: ['\u2acb', { variantForm: !0 }],
        varsupsetneqq: ['\u2acc', { variantForm: !0 }],
        leftleftarrows: '\u21c7',
        rightrightarrows: '\u21c9',
        leftrightarrows: '\u21c6',
        rightleftarrows: '\u21c4',
        Lleftarrow: '\u21da',
        Rrightarrow: '\u21db',
        twoheadleftarrow: '\u219e',
        twoheadrightarrow: '\u21a0',
        leftarrowtail: '\u21a2',
        rightarrowtail: '\u21a3',
        looparrowleft: '\u21ab',
        looparrowright: '\u21ac',
        leftrightharpoons: '\u21cb',
        rightleftharpoons: ['\u21cc', { variantForm: !0 }],
        curvearrowleft: '\u21b6',
        curvearrowright: '\u21b7',
        circlearrowleft: '\u21ba',
        circlearrowright: '\u21bb',
        Lsh: '\u21b0',
        Rsh: '\u21b1',
        upuparrows: '\u21c8',
        downdownarrows: '\u21ca',
        upharpoonleft: '\u21bf',
        upharpoonright: '\u21be',
        downharpoonleft: '\u21c3',
        restriction: '\u21be',
        multimap: '\u22b8',
        downharpoonright: '\u21c2',
        leftrightsquigarrow: '\u21ad',
        rightsquigarrow: '\u21dd',
        leadsto: '\u21dd',
        dashrightarrow: '\u21e2',
        dashleftarrow: '\u21e0',
        nleftarrow: '\u219a',
        nrightarrow: '\u219b',
        nLeftarrow: '\u21cd',
        nRightarrow: '\u21cf',
        nleftrightarrow: '\u21ae',
        nLeftrightarrow: '\u21ce',
      }),
      new n.DelimiterMap('AMSsymbols-delimiter', i.default.delimiter, {
        '\\ulcorner': '\u231c',
        '\\urcorner': '\u231d',
        '\\llcorner': '\u231e',
        '\\lrcorner': '\u231f',
      }),
      new n.CommandMap(
        'AMSsymbols-macros',
        {
          implies: ['Macro', '\\;\\Longrightarrow\\;'],
          impliedby: ['Macro', '\\;\\Longleftarrow\\;'],
        },
        a.AmsMethods,
      );
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.AmsCdConfiguration = void 0);
    var a = r(0);
    r(34),
      (e.AmsCdConfiguration = a.Configuration.create('amscd', {
        handler: {
          character: ['amscd_special'],
          macro: ['amscd_macros'],
          environment: ['amscd_environment'],
        },
        options: {
          amscd: {
            colspace: '5pt',
            rowspace: '5pt',
            harrowsize: '2.75em',
            varrowsize: '1.75em',
            hideHorizontalLabels: !1,
          },
        },
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(1),
      n = r(9),
      o = r(35);
    new a.EnvironmentMap('amscd_environment', n.default.environment, { CD: 'CD' }, o.default),
      new a.CommandMap(
        'amscd_macros',
        { minCDarrowwidth: 'minCDarrowwidth', minCDarrowheight: 'minCDarrowheight' },
        o.default,
      ),
      new a.MacroMap('amscd_special', { '@': 'arrow' }, o.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(5),
      n = r(22),
      o = r(7),
      i = r(4),
      s = {
        CD: function (t, e) {
          t.Push(e);
          var r = t.itemFactory.create('array'),
            a = t.configuration.options.amscd;
          return (
            r.setProperties({
              minw: t.stack.env.CD_minw || a.harrowsize,
              minh: t.stack.env.CD_minh || a.varrowsize,
            }),
            (r.arraydef = {
              columnalign: 'center',
              columnspacing: a.colspace,
              rowspacing: a.rowspace,
              displaystyle: !0,
            }),
            r
          );
        },
        arrow: function (t, e) {
          var r = t.string.charAt(t.i);
          if (!r.match(/[><VA.|=]/)) return n.Other(t, e);
          t.i++;
          var c = t.stack.Top();
          (c.isKind('array') && !c.Size()) || (s.cell(t, e), (c = t.stack.Top()));
          for (var l, u = c, d = u.table.length % 2 == 1, p = (u.row.length + (d ? 0 : 1)) % 2; p; )
            s.cell(t, e), p--;
          var f = { minsize: u.getProperty('minw'), stretchy: !0 },
            m = {
              minsize: u.getProperty('minh'),
              stretchy: !0,
              symmetric: !0,
              lspace: 0,
              rspace: 0,
            };
          if ('.' === r);
          else if ('|' === r) l = t.create('token', 'mo', m, '\u2225');
          else if ('=' === r) l = t.create('token', 'mo', f, '=');
          else {
            var h = { '>': '\u2192', '<': '\u2190', V: '\u2193', A: '\u2191' }[r],
              g = t.GetUpTo(e + r, r),
              v = t.GetUpTo(e + r, r);
            if ('>' === r || '<' === r) {
              if (
                ((l = t.create('token', 'mo', f, h)),
                g || (g = '\\kern ' + u.getProperty('minw')),
                g || v)
              ) {
                var y = { width: '.67em', lspace: '.33em' };
                if (((l = t.create('node', 'munderover', [l])), g)) {
                  var x = new a.default(g, t.stack.env, t.configuration).mml(),
                    b = t.create('node', 'mpadded', [x], y);
                  i.default.setAttribute(b, 'voffset', '.1em'), i.default.setChild(l, l.over, b);
                }
                if (v) {
                  var _ = new a.default(v, t.stack.env, t.configuration).mml();
                  i.default.setChild(l, l.under, t.create('node', 'mpadded', [_], y));
                }
                t.configuration.options.amscd.hideHorizontalLabels &&
                  (l = t.create('node', 'mpadded', l, { depth: 0, height: '.67em' }));
              }
            } else {
              var M = t.create('token', 'mo', m, h);
              (l = M),
                (g || v) &&
                  ((l = t.create('node', 'mrow')),
                  g &&
                    i.default.appendChildren(l, [
                      new a.default(
                        '\\scriptstyle\\llap{' + g + '}',
                        t.stack.env,
                        t.configuration,
                      ).mml(),
                    ]),
                  (M.texClass = o.TEXCLASS.ORD),
                  i.default.appendChildren(l, [M]),
                  v &&
                    i.default.appendChildren(l, [
                      new a.default(
                        '\\scriptstyle\\rlap{' + v + '}',
                        t.stack.env,
                        t.configuration,
                      ).mml(),
                    ]));
            }
          }
          l && t.Push(l), s.cell(t, e);
        },
        cell: function (t, e) {
          var r = t.stack.Top();
          (r.table || []).length % 2 == 0 &&
            0 === (r.row || []).length &&
            t.Push(t.create('node', 'mpadded', [], { height: '8.5pt', depth: '2pt' })),
            t.Push(t.itemFactory.create('cell').setProperties({ isEntry: !0, name: e }));
        },
        minCDarrowwidth: function (t, e) {
          t.stack.env.CD_minw = t.GetDimen(e);
        },
        minCDarrowheight: function (t, e) {
          t.stack.env.CD_minh = t.GetDimen(e);
        },
      };
    e.default = s;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.BboxConfiguration = e.BboxMethods = void 0);
    var a = r(0),
      n = r(1),
      o = r(2);
    (e.BboxMethods = {}),
      (e.BboxMethods.BBox = function (t, e) {
        for (
          var r,
            a,
            n,
            c = t.GetBrackets(e, ''),
            l = t.ParseArg(e),
            u = c.split(/,/),
            d = 0,
            p = u.length;
          d < p;
          d++
        ) {
          var f = u[d].trim(),
            m = f.match(/^(\.\d+|\d+(\.\d*)?)(pt|em|ex|mu|px|in|cm|mm)$/);
          if (m) {
            if (r)
              throw new o.default('MultipleBBoxProperty', '%1 specified twice in %2', 'Padding', e);
            var h = s(m[1] + m[3]);
            h &&
              (r = {
                height: '+' + h,
                depth: '+' + h,
                lspace: h,
                width: '+' + 2 * parseInt(m[1], 10) + m[3],
              });
          } else if (f.match(/^([a-z0-9]+|\#[0-9a-f]{6}|\#[0-9a-f]{3})$/i)) {
            if (a)
              throw new o.default(
                'MultipleBBoxProperty',
                '%1 specified twice in %2',
                'Background',
                e,
              );
            a = f;
          } else if (f.match(/^[-a-z]+:/i)) {
            if (n)
              throw new o.default('MultipleBBoxProperty', '%1 specified twice in %2', 'Style', e);
            n = i(f);
          } else if ('' !== f)
            throw new o.default(
              'InvalidBBoxProperty',
              '"%1" doesn\'t look like a color, a padding dimension, or a style',
              f,
            );
        }
        r && (l = t.create('node', 'mpadded', [l], r)),
          (a || n) &&
            ((r = {}),
            a && Object.assign(r, { mathbackground: a }),
            n && Object.assign(r, { style: n }),
            (l = t.create('node', 'mstyle', [l], r))),
          t.Push(l);
      });
    var i = function (t) {
        return t;
      },
      s = function (t) {
        return t;
      };
    new n.CommandMap('bbox', { bbox: 'BBox' }, e.BboxMethods),
      (e.BboxConfiguration = a.Configuration.create('bbox', { handler: { macro: ['bbox'] } }));
  },
  function (t, e, r) {
    'use strict';
    var a =
      (this && this.__values) ||
      function (t) {
        var e = 'function' == typeof Symbol && Symbol.iterator,
          r = e && t[e],
          a = 0;
        if (r) return r.call(t);
        if (t && 'number' == typeof t.length)
          return {
            next: function () {
              return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
            },
          };
        throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
      };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.BoldsymbolConfiguration =
        e.rewriteBoldTokens =
        e.createBoldToken =
        e.BoldsymbolMethods =
          void 0);
    var n = r(0),
      o = r(4),
      i = r(6),
      s = r(1),
      c = r(23),
      l = {};
    function u(t, e, r, a) {
      var n = c.NodeFactory.createToken(t, e, r, a);
      return (
        'mtext' !== e &&
          t.configuration.parser.stack.env.boldsymbol &&
          (o.default.setProperty(n, 'fixBold', !0), t.configuration.addNode('fixBold', n)),
        n
      );
    }
    function d(t) {
      var e, r;
      try {
        for (var n = a(t.data.getList('fixBold')), s = n.next(); !s.done; s = n.next()) {
          var c = s.value;
          if (o.default.getProperty(c, 'fixBold')) {
            var u = o.default.getAttribute(c, 'mathvariant');
            null == u
              ? o.default.setAttribute(c, 'mathvariant', i.TexConstant.Variant.BOLD)
              : o.default.setAttribute(c, 'mathvariant', l[u] || u),
              o.default.removeProperties(c, 'fixBold');
          }
        }
      } catch (t) {
        e = { error: t };
      } finally {
        try {
          s && !s.done && (r = n.return) && r.call(n);
        } finally {
          if (e) throw e.error;
        }
      }
    }
    (l[i.TexConstant.Variant.NORMAL] = i.TexConstant.Variant.BOLD),
      (l[i.TexConstant.Variant.ITALIC] = i.TexConstant.Variant.BOLDITALIC),
      (l[i.TexConstant.Variant.FRAKTUR] = i.TexConstant.Variant.BOLDFRAKTUR),
      (l[i.TexConstant.Variant.SCRIPT] = i.TexConstant.Variant.BOLDSCRIPT),
      (l[i.TexConstant.Variant.SANSSERIF] = i.TexConstant.Variant.BOLDSANSSERIF),
      (l['-tex-calligraphic'] = '-tex-bold-calligraphic'),
      (l['-tex-oldstyle'] = '-tex-bold-oldstyle'),
      (e.BoldsymbolMethods = {}),
      (e.BoldsymbolMethods.Boldsymbol = function (t, e) {
        var r = t.stack.env.boldsymbol;
        t.stack.env.boldsymbol = !0;
        var a = t.ParseArg(e);
        (t.stack.env.boldsymbol = r), t.Push(a);
      }),
      new s.CommandMap('boldsymbol', { boldsymbol: 'Boldsymbol' }, e.BoldsymbolMethods),
      (e.createBoldToken = u),
      (e.rewriteBoldTokens = d),
      (e.BoldsymbolConfiguration = n.Configuration.create('boldsymbol', {
        handler: { macro: ['boldsymbol'] },
        nodes: { token: u },
        postprocessors: [d],
      }));
  },
  function (t, e, r) {
    'use strict';
    var a;
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.BraketConfiguration = void 0);
    var n = r(0),
      o = r(39);
    r(40),
      (e.BraketConfiguration = n.Configuration.create('braket', {
        handler: { character: ['Braket-characters'], macro: ['Braket-macros'] },
        items: ((a = {}), (a[o.BraketItem.prototype.kind] = o.BraketItem), a),
      }));
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.BraketItem = void 0);
    var o = r(13),
      i = r(7),
      s = r(3),
      c = (function (t) {
        function e() {
          return (null !== t && t.apply(this, arguments)) || this;
        }
        return (
          n(e, t),
          Object.defineProperty(e.prototype, 'kind', {
            get: function () {
              return 'braket';
            },
            enumerable: !1,
            configurable: !0,
          }),
          Object.defineProperty(e.prototype, 'isOpen', {
            get: function () {
              return !0;
            },
            enumerable: !1,
            configurable: !0,
          }),
          (e.prototype.checkItem = function (e) {
            return e.isKind('close')
              ? [[this.factory.create('mml', this.toMml())], !0]
              : e.isKind('mml')
              ? (this.Push(e.toMml()),
                this.getProperty('single') ? [[this.toMml()], !0] : o.BaseItem.fail)
              : t.prototype.checkItem.call(this, e);
          }),
          (e.prototype.toMml = function () {
            var e = t.prototype.toMml.call(this),
              r = this.getProperty('open'),
              a = this.getProperty('close');
            if (this.getProperty('stretchy'))
              return s.default.fenced(this.factory.configuration, r, e, a);
            var n = { fence: !0, stretchy: !1, symmetric: !0, texClass: i.TEXCLASS.OPEN },
              o = this.create('token', 'mo', n, r);
            n.texClass = i.TEXCLASS.CLOSE;
            var c = this.create('token', 'mo', n, a);
            return this.create('node', 'mrow', [o, e, c], {
              open: r,
              close: a,
              texClass: i.TEXCLASS.INNER,
            });
          }),
          e
        );
      })(o.BaseItem);
    e.BraketItem = c;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(1),
      n = r(41);
    new a.CommandMap(
      'Braket-macros',
      {
        bra: ['Macro', '{\\langle {#1} \\vert}', 1],
        ket: ['Macro', '{\\vert {#1} \\rangle}', 1],
        braket: ['Braket', '\u27e8', '\u27e9', !1, 1 / 0],
        set: ['Braket', '{', '}', !1, 1],
        Bra: ['Macro', '{\\left\\langle {#1} \\right\\vert}', 1],
        Ket: ['Macro', '{\\left\\vert {#1} \\right\\rangle}', 1],
        Braket: ['Braket', '\u27e8', '\u27e9', !0, 1 / 0],
        Set: ['Braket', '{', '}', !0, 1],
        ketbra: ['Macro', '{\\vert {#1} \\rangle\\langle {#2} \\vert}', 2],
        Ketbra: [
          'Macro',
          '{\\left\\vert {#1} \\right\\rangle\\left\\langle {#2} \\right\\vert}',
          2,
        ],
        '|': 'Bar',
      },
      n.default,
    ),
      new a.MacroMap('Braket-characters', { '|': 'Bar' }, n.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(8),
      n = r(7),
      o = r(2),
      i = {};
    (i.Macro = a.default.Macro),
      (i.Braket = function (t, e, r, a, n, i) {
        var s = t.GetNext();
        if ('' === s) throw new o.default('MissingArgFor', 'Missing argument for %1', t.currentCS);
        var c = !0;
        '{' === s && (t.i++, (c = !1)),
          t.Push(
            t.itemFactory
              .create('braket')
              .setProperties({ barmax: i, barcount: 0, open: r, close: a, stretchy: n, single: c }),
          );
      }),
      (i.Bar = function (t, e) {
        var r = '|' === e ? '|' : '\u2225',
          a = t.stack.Top();
        if ('braket' !== a.kind || a.getProperty('barcount') >= a.getProperty('barmax')) {
          var o = t.create('token', 'mo', { texClass: n.TEXCLASS.ORD, stretchy: !1 }, r);
          t.Push(o);
        } else {
          if (
            ('|' === r && '|' === t.GetNext() && (t.i++, (r = '\u2225')), a.getProperty('stretchy'))
          ) {
            var i = t.create('node', 'TeXAtom', [], { texClass: n.TEXCLASS.CLOSE });
            t.Push(i),
              a.setProperty('barcount', a.getProperty('barcount') + 1),
              (i = t.create('token', 'mo', { stretchy: !0, braketbar: !0 }, r)),
              t.Push(i),
              (i = t.create('node', 'TeXAtom', [], { texClass: n.TEXCLASS.OPEN })),
              t.Push(i);
          } else {
            var s = t.create('token', 'mo', { stretchy: !1, braketbar: !0 }, r);
            t.Push(s);
          }
        }
      }),
      (e.default = i);
  },
  function (t, e, r) {
    'use strict';
    var a;
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.BussproofsConfiguration = void 0);
    var n = r(0),
      o = r(43),
      i = r(18);
    r(45),
      (e.BussproofsConfiguration = n.Configuration.create('bussproofs', {
        handler: { macro: ['Bussproofs-macros'], environment: ['Bussproofs-environments'] },
        items: ((a = {}), (a[o.ProofTreeItem.prototype.kind] = o.ProofTreeItem), a),
        preprocessors: [[i.saveDocument, 1]],
        postprocessors: [
          [i.clearDocument, 3],
          [i.makeBsprAttributes, 2],
          [i.balanceRules, 1],
        ],
      }));
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.ProofTreeItem = void 0);
    var o = r(2),
      i = r(13),
      s = r(44),
      c = r(18),
      l = (function (t) {
        function e() {
          var e = (null !== t && t.apply(this, arguments)) || this;
          return (
            (e.leftLabel = null),
            (e.rigthLabel = null),
            (e.innerStack = new s.default(e.factory, {}, !0)),
            e
          );
        }
        return (
          n(e, t),
          Object.defineProperty(e.prototype, 'kind', {
            get: function () {
              return 'proofTree';
            },
            enumerable: !1,
            configurable: !0,
          }),
          (e.prototype.checkItem = function (t) {
            if (t.isKind('end') && 'prooftree' === t.getName()) {
              var e = this.toMml();
              return c.setProperty(e, 'proof', !0), [[this.factory.create('mml', e), t], !0];
            }
            if (t.isKind('stop'))
              throw new o.default('EnvMissingEnd', 'Missing \\end{%1}', this.getName());
            return this.innerStack.Push(t), i.BaseItem.fail;
          }),
          (e.prototype.toMml = function () {
            var e = t.prototype.toMml.call(this),
              r = this.innerStack.Top();
            if (r.isKind('start') && !r.Size()) return e;
            this.innerStack.Push(this.factory.create('stop'));
            var a = this.innerStack.Top().toMml();
            return this.create('node', 'mrow', [a, e], {});
          }),
          e
        );
      })(i.BaseItem);
    e.ProofTreeItem = l;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.Stack.default);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(46),
      n = r(9),
      o = r(1);
    new o.CommandMap(
      'Bussproofs-macros',
      {
        AxiomC: 'Axiom',
        UnaryInfC: ['Inference', 1],
        BinaryInfC: ['Inference', 2],
        TrinaryInfC: ['Inference', 3],
        QuaternaryInfC: ['Inference', 4],
        QuinaryInfC: ['Inference', 5],
        RightLabel: ['Label', 'right'],
        LeftLabel: ['Label', 'left'],
        AXC: 'Axiom',
        UIC: ['Inference', 1],
        BIC: ['Inference', 2],
        TIC: ['Inference', 3],
        RL: ['Label', 'right'],
        LL: ['Label', 'left'],
        noLine: ['SetLine', 'none', !1],
        singleLine: ['SetLine', 'solid', !1],
        solidLine: ['SetLine', 'solid', !1],
        dashedLine: ['SetLine', 'dashed', !1],
        alwaysNoLine: ['SetLine', 'none', !0],
        alwaysSingleLine: ['SetLine', 'solid', !0],
        alwaysSolidLine: ['SetLine', 'solid', !0],
        alwaysDashedLine: ['SetLine', 'dashed', !0],
        rootAtTop: ['RootAtTop', !0],
        alwaysRootAtTop: ['RootAtTop', !0],
        rootAtBottom: ['RootAtTop', !1],
        alwaysRootAtBottom: ['RootAtTop', !1],
        fCenter: 'FCenter',
        Axiom: 'AxiomF',
        UnaryInf: ['InferenceF', 1],
        BinaryInf: ['InferenceF', 2],
        TrinaryInf: ['InferenceF', 3],
        QuaternaryInf: ['InferenceF', 4],
        QuinaryInf: ['InferenceF', 5],
      },
      a.default,
    ),
      new o.EnvironmentMap(
        'Bussproofs-environments',
        n.default.environment,
        { prooftree: ['Prooftree', null, !1] },
        a.default,
      );
  },
  function (t, e, r) {
    'use strict';
    var a =
        (this && this.__read) ||
        function (t, e) {
          var r = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!r) return t;
          var a,
            n,
            o = r.call(t),
            i = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(a = o.next()).done; ) i.push(a.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              a && !a.done && (r = o.return) && r.call(o);
            } finally {
              if (n) throw n.error;
            }
          }
          return i;
        },
      n =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(a(arguments[e]));
          return t;
        };
    Object.defineProperty(e, '__esModule', { value: !0 });
    var o = r(2),
      i = r(5),
      s = r(3),
      c = r(18),
      l = {
        Prooftree: function (t, e) {
          return (
            t.Push(e),
            t.itemFactory.create('proofTree').setProperties({
              name: e.getName(),
              line: 'solid',
              currentLine: 'solid',
              rootAtTop: !1,
            })
          );
        },
        Axiom: function (t, e) {
          var r = t.stack.Top();
          if ('proofTree' !== r.kind)
            throw new o.default(
              'IllegalProofCommand',
              'Proof commands only allowed in prooftree environment.',
            );
          var a = u(t, t.GetArgument(e));
          c.setProperty(a, 'axiom', !0), r.Push(a);
        },
      },
      u = function (t, e) {
        var r = s.default.internalMath(t, s.default.trimSpaces(e), 0);
        if (!r[0].childNodes[0].childNodes.length) return t.create('node', 'mrow', []);
        var a = t.create('node', 'mspace', [], { width: '.5ex' }),
          o = t.create('node', 'mspace', [], { width: '.5ex' });
        return t.create('node', 'mrow', n([a], r, [o]));
      };
    function d(t, e, r, a, n, o, i) {
      var s,
        l,
        u,
        d,
        p = t.create('node', 'mtr', [t.create('node', 'mtd', [e], {})], {}),
        f = t.create('node', 'mtr', [t.create('node', 'mtd', r, {})], {}),
        m = t.create('node', 'mtable', i ? [f, p] : [p, f], {
          align: 'top 2',
          rowlines: o,
          framespacing: '0 0',
        });
      if (
        (c.setProperty(m, 'inferenceRule', i ? 'up' : 'down'),
        a &&
          ((s = t.create('node', 'mpadded', [a], {
            height: '+.5em',
            width: '+.5em',
            voffset: '-.15em',
          })),
          c.setProperty(s, 'prooflabel', 'left')),
        n &&
          ((l = t.create('node', 'mpadded', [n], {
            height: '+.5em',
            width: '+.5em',
            voffset: '-.15em',
          })),
          c.setProperty(l, 'prooflabel', 'right')),
        a && n)
      )
        (u = [s, m, l]), (d = 'both');
      else if (a) (u = [s, m]), (d = 'left');
      else {
        if (!n) return m;
        (u = [m, l]), (d = 'right');
      }
      return (m = t.create('node', 'mrow', u)), c.setProperty(m, 'labelledRule', d), m;
    }
    function p(t, e) {
      if ('$' !== t.GetNext())
        throw new o.default('IllegalUseOfCommand', "Use of %1 does not match it's definition.", e);
      t.i++;
      var r = t.GetUpTo(e, '$');
      if (-1 === r.indexOf('\\fCenter'))
        throw new o.default('IllegalUseOfCommand', 'Missing \\fCenter in %1.', e);
      var n = a(r.split('\\fCenter'), 2),
        s = n[0],
        l = n[1],
        u = new i.default(s, t.stack.env, t.configuration).mml(),
        d = new i.default(l, t.stack.env, t.configuration).mml(),
        p = new i.default('\\fCenter', t.stack.env, t.configuration).mml(),
        f = t.create('node', 'mtd', [u], {}),
        m = t.create('node', 'mtd', [p], {}),
        h = t.create('node', 'mtd', [d], {}),
        g = t.create('node', 'mtr', [f, m, h], {}),
        v = t.create('node', 'mtable', [g], { columnspacing: '.5ex', columnalign: 'center 2' });
      return c.setProperty(v, 'sequent', !0), t.configuration.addNode('sequent', g), v;
    }
    (l.Inference = function (t, e, r) {
      var a = t.stack.Top();
      if ('proofTree' !== a.kind)
        throw new o.default(
          'IllegalProofCommand',
          'Proof commands only allowed in prooftree environment.',
        );
      if (a.Size() < r) throw new o.default('BadProofTree', 'Proof tree badly specified.');
      var n = a.getProperty('rootAtTop'),
        i = 1 !== r || a.Peek()[0].childNodes.length ? r : 0,
        s = [];
      do {
        s.length && s.unshift(t.create('node', 'mtd', [], {})),
          s.unshift(t.create('node', 'mtd', [a.Pop()], { rowalign: n ? 'top' : 'bottom' })),
          r--;
      } while (r > 0);
      var l = t.create('node', 'mtr', s, {}),
        p = t.create('node', 'mtable', [l], { framespacing: '0 0' }),
        f = u(t, t.GetArgument(e)),
        m = a.getProperty('currentLine');
      m !== a.getProperty('line') && a.setProperty('currentLine', a.getProperty('line'));
      var h = d(t, p, [f], a.getProperty('left'), a.getProperty('right'), m, n);
      a.setProperty('left', null),
        a.setProperty('right', null),
        c.setProperty(h, 'inference', i),
        t.configuration.addNode('inference', h),
        a.Push(h);
    }),
      (l.Label = function (t, e, r) {
        var a = t.stack.Top();
        if ('proofTree' !== a.kind)
          throw new o.default(
            'IllegalProofCommand',
            'Proof commands only allowed in prooftree environment.',
          );
        var n = s.default.internalMath(t, t.GetArgument(e), 0),
          i = n.length > 1 ? t.create('node', 'mrow', n, {}) : n[0];
        a.setProperty(r, i);
      }),
      (l.SetLine = function (t, e, r, a) {
        var n = t.stack.Top();
        if ('proofTree' !== n.kind)
          throw new o.default(
            'IllegalProofCommand',
            'Proof commands only allowed in prooftree environment.',
          );
        n.setProperty('currentLine', r), a && n.setProperty('line', r);
      }),
      (l.RootAtTop = function (t, e, r) {
        var a = t.stack.Top();
        if ('proofTree' !== a.kind)
          throw new o.default(
            'IllegalProofCommand',
            'Proof commands only allowed in prooftree environment.',
          );
        a.setProperty('rootAtTop', r);
      }),
      (l.AxiomF = function (t, e) {
        var r = t.stack.Top();
        if ('proofTree' !== r.kind)
          throw new o.default(
            'IllegalProofCommand',
            'Proof commands only allowed in prooftree environment.',
          );
        var a = p(t, e);
        c.setProperty(a, 'axiom', !0), r.Push(a);
      }),
      (l.FCenter = function (t, e) {}),
      (l.InferenceF = function (t, e, r) {
        var a = t.stack.Top();
        if ('proofTree' !== a.kind)
          throw new o.default(
            'IllegalProofCommand',
            'Proof commands only allowed in prooftree environment.',
          );
        if (a.Size() < r) throw new o.default('BadProofTree', 'Proof tree badly specified.');
        var n = a.getProperty('rootAtTop'),
          i = 1 !== r || a.Peek()[0].childNodes.length ? r : 0,
          s = [];
        do {
          s.length && s.unshift(t.create('node', 'mtd', [], {})),
            s.unshift(t.create('node', 'mtd', [a.Pop()], { rowalign: n ? 'top' : 'bottom' })),
            r--;
        } while (r > 0);
        var l = t.create('node', 'mtr', s, {}),
          u = t.create('node', 'mtable', [l], { framespacing: '0 0' }),
          f = p(t, e),
          m = a.getProperty('currentLine');
        m !== a.getProperty('line') && a.setProperty('currentLine', a.getProperty('line'));
        var h = d(t, u, [f], a.getProperty('left'), a.getProperty('right'), m, n);
        a.setProperty('left', null),
          a.setProperty('right', null),
          c.setProperty(h, 'inference', i),
          t.configuration.addNode('inference', h),
          a.Push(h);
      }),
      (e.default = l);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.CancelConfiguration = e.CancelMethods = void 0);
    var a = r(0),
      n = r(6),
      o = r(1),
      i = r(3),
      s = r(24);
    (e.CancelMethods = {}),
      (e.CancelMethods.Cancel = function (t, e, r) {
        var a = t.GetBrackets(e, ''),
          n = t.ParseArg(e),
          o = i.default.keyvalOptions(a, s.ENCLOSE_OPTIONS);
        (o.notation = r), t.Push(t.create('node', 'menclose', [n], o));
      }),
      (e.CancelMethods.CancelTo = function (t, e) {
        var r = t.GetBrackets(e, ''),
          a = t.ParseArg(e),
          o = t.ParseArg(e),
          c = i.default.keyvalOptions(r, s.ENCLOSE_OPTIONS);
        (c.notation = [
          n.TexConstant.Notation.UPDIAGONALSTRIKE,
          n.TexConstant.Notation.UPDIAGONALARROW,
          n.TexConstant.Notation.NORTHEASTARROW,
        ].join(' ')),
          (a = t.create('node', 'mpadded', [a], {
            depth: '-.1em',
            height: '+.1em',
            voffset: '.1em',
          })),
          t.Push(t.create('node', 'msup', [t.create('node', 'menclose', [o], c), a]));
      }),
      new o.CommandMap(
        'cancel',
        {
          cancel: ['Cancel', n.TexConstant.Notation.UPDIAGONALSTRIKE],
          bcancel: ['Cancel', n.TexConstant.Notation.DOWNDIAGONALSTRIKE],
          xcancel: [
            'Cancel',
            n.TexConstant.Notation.UPDIAGONALSTRIKE +
              ' ' +
              n.TexConstant.Notation.DOWNDIAGONALSTRIKE,
          ],
          cancelto: 'CancelTo',
        },
        e.CancelMethods,
      ),
      (e.CancelConfiguration = a.Configuration.create('cancel', {
        handler: { macro: ['cancel'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.ColorConfiguration = void 0);
    var a = r(1),
      n = r(0),
      o = r(49),
      i = r(50);
    new a.CommandMap(
      'color',
      {
        color: 'Color',
        textcolor: 'TextColor',
        definecolor: 'DefineColor',
        colorbox: 'ColorBox',
        fcolorbox: 'FColorBox',
      },
      o.ColorMethods,
    );
    e.ColorConfiguration = n.Configuration.create('color', {
      handler: { macro: ['color'] },
      options: { color: { padding: '5px', borderWidth: '2px' } },
      config: function (t, e) {
        e.parseOptions.packageData.set('color', { model: new i.ColorModel() });
      },
    });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.ColorMethods = void 0);
    var a = r(4),
      n = r(3);
    function o(t) {
      var e = '+' + t,
        r = t.replace(/^.*?([a-z]*)$/, '$1');
      return { width: '+' + 2 * parseFloat(e) + r, height: e, depth: e, lspace: t };
    }
    (e.ColorMethods = {}),
      (e.ColorMethods.Color = function (t, e) {
        var r = t.GetBrackets(e, ''),
          a = t.GetArgument(e),
          n = t.configuration.packageData.get('color').model.getColor(r, a),
          o = t.itemFactory.create('style').setProperties({ styles: { mathcolor: n } });
        (t.stack.env.color = n), t.Push(o);
      }),
      (e.ColorMethods.TextColor = function (t, e) {
        var r = t.GetBrackets(e, ''),
          a = t.GetArgument(e),
          n = t.configuration.packageData.get('color').model.getColor(r, a),
          o = t.stack.env.color;
        t.stack.env.color = n;
        var i = t.ParseArg(e);
        o ? (t.stack.env.color = o) : delete t.stack.env.color;
        var s = t.create('node', 'mstyle', [i], { mathcolor: n });
        t.Push(s);
      }),
      (e.ColorMethods.DefineColor = function (t, e) {
        var r = t.GetArgument(e),
          a = t.GetArgument(e),
          n = t.GetArgument(e);
        t.configuration.packageData.get('color').model.defineColor(a, r, n);
      }),
      (e.ColorMethods.ColorBox = function (t, e) {
        var r = t.GetArgument(e),
          i = n.default.internalMath(t, t.GetArgument(e)),
          s = t.configuration.packageData.get('color').model,
          c = t.create('node', 'mpadded', i, { mathbackground: s.getColor('named', r) });
        a.default.setProperties(c, o(t.options.color.padding)), t.Push(c);
      }),
      (e.ColorMethods.FColorBox = function (t, e) {
        var r = t.GetArgument(e),
          i = t.GetArgument(e),
          s = n.default.internalMath(t, t.GetArgument(e)),
          c = t.options.color,
          l = t.configuration.packageData.get('color').model,
          u = t.create('node', 'mpadded', s, {
            mathbackground: l.getColor('named', i),
            style: 'border: ' + c.borderWidth + ' solid ' + l.getColor('named', r),
          });
        a.default.setProperties(u, o(c.padding)), t.Push(u);
      });
  },
  function (t, e, r) {
    'use strict';
    var a =
      (this && this.__values) ||
      function (t) {
        var e = 'function' == typeof Symbol && Symbol.iterator,
          r = e && t[e],
          a = 0;
        if (r) return r.call(t);
        if (t && 'number' == typeof t.length)
          return {
            next: function () {
              return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
            },
          };
        throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
      };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.ColorModel = void 0);
    var n = r(2),
      o = r(51),
      i = new Map(),
      s = (function () {
        function t() {
          this.userColors = new Map();
        }
        return (
          (t.prototype.normalizeColor = function (t, e) {
            if (!t || 'named' === t) return e;
            if (i.has(t)) return i.get(t)(e);
            throw new n.default('UndefinedColorModel', "Color model '%1' not defined", t);
          }),
          (t.prototype.getColor = function (t, e) {
            return t && 'named' !== t ? this.normalizeColor(t, e) : this.getColorByName(e);
          }),
          (t.prototype.getColorByName = function (t) {
            return this.userColors.has(t)
              ? this.userColors.get(t)
              : o.COLORS.has(t)
              ? o.COLORS.get(t)
              : t;
          }),
          (t.prototype.defineColor = function (t, e, r) {
            var a = this.normalizeColor(t, r);
            this.userColors.set(e, a);
          }),
          t
        );
      })();
    (e.ColorModel = s),
      i.set('rgb', function (t) {
        var e,
          r,
          o = t.trim().split(/\s*,\s*/),
          i = '#';
        if (3 !== o.length)
          throw new n.default(
            'ModelArg1',
            'Color values for the %1 model require 3 numbers',
            'rgb',
          );
        try {
          for (var s = a(o), c = s.next(); !c.done; c = s.next()) {
            var l = c.value;
            if (!l.match(/^(\d+(\.\d*)?|\.\d+)$/))
              throw new n.default('InvalidDecimalNumber', 'Invalid decimal number');
            var u = parseFloat(l);
            if (u < 0 || u > 1)
              throw new n.default(
                'ModelArg2',
                'Color values for the %1 model must be between %2 and %3',
                'rgb',
                '0',
                '1',
              );
            var d = Math.floor(255 * u).toString(16);
            d.length < 2 && (d = '0' + d), (i += d);
          }
        } catch (t) {
          e = { error: t };
        } finally {
          try {
            c && !c.done && (r = s.return) && r.call(s);
          } finally {
            if (e) throw e.error;
          }
        }
        return i;
      }),
      i.set('RGB', function (t) {
        var e,
          r,
          o = t.trim().split(/\s*,\s*/),
          i = '#';
        if (3 !== o.length)
          throw new n.default(
            'ModelArg1',
            'Color values for the %1 model require 3 numbers',
            'RGB',
          );
        try {
          for (var s = a(o), c = s.next(); !c.done; c = s.next()) {
            var l = c.value;
            if (!l.match(/^\d+$/)) throw new n.default('InvalidNumber', 'Invalid number');
            var u = parseInt(l);
            if (u > 255)
              throw new n.default(
                'ModelArg2',
                'Color values for the %1 model must be between %2 and %3',
                'RGB',
                '0',
                '255',
              );
            var d = u.toString(16);
            d.length < 2 && (d = '0' + d), (i += d);
          }
        } catch (t) {
          e = { error: t };
        } finally {
          try {
            c && !c.done && (r = s.return) && r.call(s);
          } finally {
            if (e) throw e.error;
          }
        }
        return i;
      }),
      i.set('gray', function (t) {
        if (!t.match(/^\s*(\d+(\.\d*)?|\.\d+)\s*$/))
          throw new n.default('InvalidDecimalNumber', 'Invalid decimal number');
        var e = parseFloat(t);
        if (e < 0 || e > 1)
          throw new n.default(
            'ModelArg2',
            'Color values for the %1 model must be between %2 and %3',
            'gray',
            '0',
            '1',
          );
        var r = Math.floor(255 * e).toString(16);
        return r.length < 2 && (r = '0' + r), '#' + r + r + r;
      });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.COLORS = void 0),
      (e.COLORS = new Map([
        ['Apricot', '#FBB982'],
        ['Aquamarine', '#00B5BE'],
        ['Bittersweet', '#C04F17'],
        ['Black', '#221E1F'],
        ['Blue', '#2D2F92'],
        ['BlueGreen', '#00B3B8'],
        ['BlueViolet', '#473992'],
        ['BrickRed', '#B6321C'],
        ['Brown', '#792500'],
        ['BurntOrange', '#F7921D'],
        ['CadetBlue', '#74729A'],
        ['CarnationPink', '#F282B4'],
        ['Cerulean', '#00A2E3'],
        ['CornflowerBlue', '#41B0E4'],
        ['Cyan', '#00AEEF'],
        ['Dandelion', '#FDBC42'],
        ['DarkOrchid', '#A4538A'],
        ['Emerald', '#00A99D'],
        ['ForestGreen', '#009B55'],
        ['Fuchsia', '#8C368C'],
        ['Goldenrod', '#FFDF42'],
        ['Gray', '#949698'],
        ['Green', '#00A64F'],
        ['GreenYellow', '#DFE674'],
        ['JungleGreen', '#00A99A'],
        ['Lavender', '#F49EC4'],
        ['LimeGreen', '#8DC73E'],
        ['Magenta', '#EC008C'],
        ['Mahogany', '#A9341F'],
        ['Maroon', '#AF3235'],
        ['Melon', '#F89E7B'],
        ['MidnightBlue', '#006795'],
        ['Mulberry', '#A93C93'],
        ['NavyBlue', '#006EB8'],
        ['OliveGreen', '#3C8031'],
        ['Orange', '#F58137'],
        ['OrangeRed', '#ED135A'],
        ['Orchid', '#AF72B0'],
        ['Peach', '#F7965A'],
        ['Periwinkle', '#7977B8'],
        ['PineGreen', '#008B72'],
        ['Plum', '#92268F'],
        ['ProcessBlue', '#00B0F0'],
        ['Purple', '#99479B'],
        ['RawSienna', '#974006'],
        ['Red', '#ED1B23'],
        ['RedOrange', '#F26035'],
        ['RedViolet', '#A1246B'],
        ['Rhodamine', '#EF559F'],
        ['RoyalBlue', '#0071BC'],
        ['RoyalPurple', '#613F99'],
        ['RubineRed', '#ED017D'],
        ['Salmon', '#F69289'],
        ['SeaGreen', '#3FBC9D'],
        ['Sepia', '#671800'],
        ['SkyBlue', '#46C5DD'],
        ['SpringGreen', '#C6DC67'],
        ['Tan', '#DA9D76'],
        ['TealBlue', '#00AEB3'],
        ['Thistle', '#D883B7'],
        ['Turquoise', '#00B4CE'],
        ['Violet', '#58429B'],
        ['VioletRed', '#EF58A0'],
        ['White', '#FFFFFF'],
        ['WildStrawberry', '#EE2967'],
        ['Yellow', '#FFF200'],
        ['YellowGreen', '#98CC70'],
        ['YellowOrange', '#FAA21A'],
      ]));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.ColorConfiguration = e.ColorV2Methods = void 0);
    var a = r(1),
      n = r(0);
    (e.ColorV2Methods = {
      Color: function (t, e) {
        var r = t.GetArgument(e),
          a = t.stack.env.color;
        t.stack.env.color = r;
        var n = t.ParseArg(e);
        a ? (t.stack.env.color = a) : delete t.stack.env.color;
        var o = t.create('node', 'mstyle', [n], { mathcolor: r });
        t.Push(o);
      },
    }),
      new a.CommandMap('colorv2', { color: 'Color' }, e.ColorV2Methods),
      (e.ColorConfiguration = n.Configuration.create('colorv2', {
        handler: { macro: ['colorv2'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    var a =
      (this && this.__values) ||
      function (t) {
        var e = 'function' == typeof Symbol && Symbol.iterator,
          r = e && t[e],
          a = 0;
        if (r) return r.call(t);
        if (t && 'number' == typeof t.length)
          return {
            next: function () {
              return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
            },
          };
        throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
      };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.ConfigMacrosConfiguration = void 0);
    var n = r(0),
      o = r(10),
      i = r(1),
      s = r(12),
      c = r(25);
    e.ConfigMacrosConfiguration = n.Configuration.create('configmacros', {
      init: function (t) {
        new i.CommandMap('configmacros-map', {}, {}),
          t.append(
            n.Configuration.local({ handler: { macro: ['configmacros-map'] }, priority: 3 }),
          );
      },
      config: function (t, e) {
        var r,
          n,
          o = e.parseOptions.handlers.retrieve('configmacros-map'),
          i = e.parseOptions.options.macros;
        try {
          for (var l = a(Object.keys(i)), u = l.next(); !u.done; u = l.next()) {
            var d = u.value,
              p = 'string' == typeof i[d] ? [i[d]] : i[d],
              f = Array.isArray(p[2])
                ? new s.Macro(d, c.default.MacroWithTemplate, p.slice(0, 2).concat(p[2]))
                : new s.Macro(d, c.default.Macro, p);
            o.add(d, f);
          }
        } catch (t) {
          r = { error: t };
        } finally {
          try {
            u && !u.done && (n = l.return) && n.call(l);
          } finally {
            if (r) throw r.error;
          }
        }
      },
      options: { macros: o.expandable({}) },
    });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.ExtpfeilConfiguration = e.ExtpfeilMethods = void 0);
    var a = r(0),
      n = r(1),
      o = r(11),
      i = r(19),
      s = r(26),
      c = r(2);
    (e.ExtpfeilMethods = {}),
      (e.ExtpfeilMethods.xArrow = o.AmsMethods.xArrow),
      (e.ExtpfeilMethods.NewExtArrow = function (t, r) {
        var a = t.GetArgument(r),
          n = t.GetArgument(r),
          o = t.GetArgument(r);
        if (!a.match(/^\\([a-z]+|.)$/i))
          throw new c.default(
            'NewextarrowArg1',
            'First argument to %1 must be a control sequence name',
            r,
          );
        if (!n.match(/^(\d+),(\d+)$/))
          throw new c.default(
            'NewextarrowArg2',
            'Second argument to %1 must be two integers separated by a comma',
            r,
          );
        if (!o.match(/^(\d+|0x[0-9A-F]+)$/i))
          throw new c.default(
            'NewextarrowArg3',
            'Third argument to %1 must be a unicode character number',
            r,
          );
        a = a.substr(1);
        var s = n.split(',');
        i.default.addMacro(t, a, e.ExtpfeilMethods.xArrow, [
          parseInt(o),
          parseInt(s[0]),
          parseInt(s[1]),
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
    e.ExtpfeilConfiguration = a.Configuration.create('extpfeil', {
      handler: { macro: ['extpfeil'] },
      init: function (t) {
        s.NewcommandConfiguration.init(t);
      },
    });
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.BeginEnvItem = void 0);
    var o = r(2),
      i = (function (t) {
        function e() {
          return (null !== t && t.apply(this, arguments)) || this;
        }
        return (
          n(e, t),
          Object.defineProperty(e.prototype, 'kind', {
            get: function () {
              return 'beginEnv';
            },
            enumerable: !1,
            configurable: !0,
          }),
          Object.defineProperty(e.prototype, 'isOpen', {
            get: function () {
              return !0;
            },
            enumerable: !1,
            configurable: !0,
          }),
          (e.prototype.checkItem = function (e) {
            if (e.isKind('end')) {
              if (e.getName() !== this.getName())
                throw new o.default(
                  'EnvBadEnd',
                  '\\begin{%1} ended with \\end{%2}',
                  this.getName(),
                  e.getName(),
                );
              return [[this.factory.create('mml', this.toMml())], !0];
            }
            if (e.isKind('stop'))
              throw new o.default('EnvMissingEnd', 'Missing \\end{%1}', this.getName());
            return t.prototype.checkItem.call(this, e);
          }),
          e
        );
      })(r(13).BaseItem);
    e.BeginEnvItem = i;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(25);
    new (r(1).CommandMap)(
      'Newcommand-macros',
      {
        newcommand: 'NewCommand',
        renewcommand: 'NewCommand',
        newenvironment: 'NewEnvironment',
        renewenvironment: 'NewEnvironment',
        def: 'MacroDef',
        let: 'Let',
      },
      a.default,
    );
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.HtmlConfiguration = void 0);
    var a = r(0),
      n = r(1),
      o = r(58);
    new n.CommandMap(
      'html_macros',
      { href: 'Href', class: 'Class', style: 'Style', cssId: 'Id' },
      o.default,
    ),
      (e.HtmlConfiguration = a.Configuration.create('html', {
        handler: { macro: ['html_macros'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(4),
      n = {
        Href: function (t, e) {
          var r = t.GetArgument(e),
            n = o(t, e);
          a.default.setAttribute(n, 'href', r), t.Push(n);
        },
        Class: function (t, e) {
          var r = t.GetArgument(e),
            n = o(t, e),
            i = a.default.getAttribute(n, 'class');
          i && (r = i + ' ' + r), a.default.setAttribute(n, 'class', r), t.Push(n);
        },
        Style: function (t, e) {
          var r = t.GetArgument(e),
            n = o(t, e),
            i = a.default.getAttribute(n, 'style');
          i && (';' !== r.charAt(r.length - 1) && (r += ';'), (r = i + ' ' + r)),
            a.default.setAttribute(n, 'style', r),
            t.Push(n);
        },
        Id: function (t, e) {
          var r = t.GetArgument(e),
            n = o(t, e);
          a.default.setAttribute(n, 'id', r), t.Push(n);
        },
      },
      o = function (t, e) {
        var r = t.ParseArg(e);
        if (!a.default.isInferred(r)) return r;
        var n = a.default.getChildren(r);
        if (1 === n.length) return n[0];
        var o = t.create('node', 'mrow');
        return a.default.copyChildren(r, o), a.default.copyAttributes(r, o), o;
      };
    e.default = n;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.MhchemConfiguration = void 0);
    var a = r(0),
      n = r(1),
      o = r(2),
      i = r(8),
      s = r(11),
      c = r(60),
      l = {};
    (l.Macro = i.default.Macro),
      (l.xArrow = s.AmsMethods.xArrow),
      (l.Machine = function (t, e, r) {
        try {
          var a = t.GetArgument(e),
            n = c.mhchemParser.go(a, r),
            i = c.texify.go(n);
          (t.string = i + t.string.substr(t.i)), (t.i = 0);
        } catch (t) {
          throw new o.default(t[0], t[1], t.slice(2));
        }
      }),
      new n.CommandMap(
        'mhchem',
        {
          ce: ['Machine', 'ce'],
          pu: ['Machine', 'pu'],
          longrightleftharpoons: [
            'Macro',
            '\\stackrel{\\textstyle{-}\\!\\!{\\rightharpoonup}}{\\smash{{\\leftharpoondown}\\!\\!{-}}}',
          ],
          longRightleftharpoons: [
            'Macro',
            '\\stackrel{\\textstyle{-}\\!\\!{\\rightharpoonup}}{\\smash{\\leftharpoondown}}',
          ],
          longLeftrightharpoons: [
            'Macro',
            '\\stackrel{\\textstyle\\vphantom{{-}}{\\rightharpoonup}}{\\smash{{\\leftharpoondown}\\!\\!{-}}}',
          ],
          longleftrightarrows: [
            'Macro',
            '\\stackrel{\\longrightarrow}{\\smash{\\longleftarrow}\\Rule{0px}{.25em}{0px}}',
          ],
          tripledash: [
            'Macro',
            '\\vphantom{-}\\raise2mu{\\kern2mu\\tiny\\text{-}\\kern1mu\\text{-}\\kern1mu\\text{-}\\kern2mu}',
          ],
          xrightarrow: ['xArrow', 8594, 5, 6],
          xleftarrow: ['xArrow', 8592, 7, 3],
          xleftrightarrow: ['xArrow', 8596, 6, 6],
          xrightleftharpoons: ['xArrow', 8652, 5, 7],
          xRightleftharpoons: ['xArrow', 8652, 5, 7],
          xLeftrightharpoons: ['xArrow', 8652, 5, 7],
        },
        l,
      ),
      (e.MhchemConfiguration = a.Configuration.create('mhchem', {
        handler: { macro: ['mhchem'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = {
      go: function (t, e) {
        if (!t) return [];
        void 0 === e && (e = 'ce');
        var r,
          n = '0',
          o = {};
        (o.parenthesisLevel = 0),
          (t = (t = (t = t.replace(/\n/g, ' ')).replace(
            /[\u2212\u2013\u2014\u2010]/g,
            '-',
          )).replace(/[\u2026]/g, '...'));
        for (var i = 10, s = []; ; ) {
          r !== t ? ((i = 10), (r = t)) : i--;
          var c = a.stateMachines[e],
            l = c.transitions[n] || c.transitions['*'];
          t: for (var u = 0; u < l.length; u++) {
            var d = a.patterns.match_(l[u].pattern, t);
            if (d) {
              for (var p = l[u].task, f = 0; f < p.action_.length; f++) {
                var m;
                if (c.actions[p.action_[f].type_])
                  m = c.actions[p.action_[f].type_](o, d.match_, p.action_[f].option);
                else {
                  if (!a.actions[p.action_[f].type_])
                    throw [
                      'MhchemBugA',
                      'mhchem bug A. Please report. (' + p.action_[f].type_ + ')',
                    ];
                  m = a.actions[p.action_[f].type_](o, d.match_, p.action_[f].option);
                }
                a.concatArray(s, m);
              }
              if (((n = p.nextState || n), !(t.length > 0))) return s;
              if ((p.revisit || (t = d.remainder), !p.toContinue)) break t;
            }
          }
          if (i <= 0) throw ['MhchemBugU', 'mhchem bug U. Please report.'];
        }
      },
      concatArray: function (t, e) {
        if (e)
          if (Array.isArray(e)) for (var r = 0; r < e.length; r++) t.push(e[r]);
          else t.push(e);
      },
      patterns: {
        patterns: {
          empty: /^$/,
          else: /^./,
          else2: /^./,
          space: /^\s/,
          'space A': /^\s(?=[A-Z\\$])/,
          space$: /^\s$/,
          'a-z': /^[a-z]/,
          x: /^x/,
          x$: /^x$/,
          i$: /^i$/,
          letters:
            /^(?:[a-zA-Z\u03B1-\u03C9\u0391-\u03A9?@]|(?:\\(?:alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega)(?:\s+|\{\}|(?![a-zA-Z]))))+/,
          '\\greek':
            /^\\(?:alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega|Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega)(?:\s+|\{\}|(?![a-zA-Z]))/,
          'one lowercase latin letter $': /^(?:([a-z])(?:$|[^a-zA-Z]))$/,
          '$one lowercase latin letter$ $': /^\$(?:([a-z])(?:$|[^a-zA-Z]))\$$/,
          'one lowercase greek letter $':
            /^(?:\$?[\u03B1-\u03C9]\$?|\$?\\(?:alpha|beta|gamma|delta|epsilon|zeta|eta|theta|iota|kappa|lambda|mu|nu|xi|omicron|pi|rho|sigma|tau|upsilon|phi|chi|psi|omega)\s*\$?)(?:\s+|\{\}|(?![a-zA-Z]))$/,
          digits: /^[0-9]+/,
          '-9.,9': /^[+\-]?(?:[0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+))/,
          '-9.,9 no missing 0': /^[+\-]?[0-9]+(?:[.,][0-9]+)?/,
          '(-)(9.,9)(e)(99)': function (t) {
            var e = t.match(
              /^(\+\-|\+\/\-|\+|\-|\\pm\s?)?([0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+))?(\((?:[0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+))\))?(?:([eE]|\s*(\*|x|\\times|\u00D7)\s*10\^)([+\-]?[0-9]+|\{[+\-]?[0-9]+\}))?/,
            );
            return e && e[0] ? { match_: e.splice(1), remainder: t.substr(e[0].length) } : null;
          },
          '(-)(9)^(-9)': function (t) {
            var e = t.match(
              /^(\+\-|\+\/\-|\+|\-|\\pm\s?)?([0-9]+(?:[,.][0-9]+)?|[0-9]*(?:\.[0-9]+)?)\^([+\-]?[0-9]+|\{[+\-]?[0-9]+\})/,
            );
            return e && e[0] ? { match_: e.splice(1), remainder: t.substr(e[0].length) } : null;
          },
          'state of aggregation $': function (t) {
            var e = a.patterns.findObserveGroups(t, '', /^\([a-z]{1,3}(?=[\),])/, ')', '');
            if (e && e.remainder.match(/^($|[\s,;\)\]\}])/)) return e;
            var r = t.match(/^(?:\((?:\\ca\s?)?\$[amothc]\$\))/);
            return r ? { match_: r[0], remainder: t.substr(r[0].length) } : null;
          },
          '_{(state of aggregation)}$': /^_\{(\([a-z]{1,3}\))\}/,
          '{[(': /^(?:\\\{|\[|\()/,
          ')]}': /^(?:\)|\]|\\\})/,
          ', ': /^[,;]\s*/,
          ',': /^[,;]/,
          '.': /^[.]/,
          '. ': /^([.\u22C5\u00B7\u2022])\s*/,
          '...': /^\.\.\.(?=$|[^.])/,
          '* ': /^([*])\s*/,
          '^{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '^{', '', '', '}');
          },
          '^($...$)': function (t) {
            return a.patterns.findObserveGroups(t, '^', '$', '$', '');
          },
          '^a': /^\^([0-9]+|[^\\_])/,
          '^\\x{}{}': function (t) {
            return a.patterns.findObserveGroups(
              t,
              '^',
              /^\\[a-zA-Z]+\{/,
              '}',
              '',
              '',
              '{',
              '}',
              '',
              !0,
            );
          },
          '^\\x{}': function (t) {
            return a.patterns.findObserveGroups(t, '^', /^\\[a-zA-Z]+\{/, '}', '');
          },
          '^\\x': /^\^(\\[a-zA-Z]+)\s*/,
          '^(-1)': /^\^(-?\d+)/,
          "'": /^'/,
          '_{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '_{', '', '', '}');
          },
          '_($...$)': function (t) {
            return a.patterns.findObserveGroups(t, '_', '$', '$', '');
          },
          _9: /^_([+\-]?[0-9]+|[^\\])/,
          '_\\x{}{}': function (t) {
            return a.patterns.findObserveGroups(
              t,
              '_',
              /^\\[a-zA-Z]+\{/,
              '}',
              '',
              '',
              '{',
              '}',
              '',
              !0,
            );
          },
          '_\\x{}': function (t) {
            return a.patterns.findObserveGroups(t, '_', /^\\[a-zA-Z]+\{/, '}', '');
          },
          '_\\x': /^_(\\[a-zA-Z]+)\s*/,
          '^_': /^(?:\^(?=_)|\_(?=\^)|[\^_]$)/,
          '{}': /^\{\}/,
          '{...}': function (t) {
            return a.patterns.findObserveGroups(t, '', '{', '}', '');
          },
          '{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '{', '', '', '}');
          },
          '$...$': function (t) {
            return a.patterns.findObserveGroups(t, '', '$', '$', '');
          },
          '${(...)}$': function (t) {
            return a.patterns.findObserveGroups(t, '${', '', '', '}$');
          },
          '$(...)$': function (t) {
            return a.patterns.findObserveGroups(t, '$', '', '', '$');
          },
          '=<>': /^[=<>]/,
          '#': /^[#\u2261]/,
          '+': /^\+/,
          '-$': /^-(?=[\s_},;\]/]|$|\([a-z]+\))/,
          '-9': /^-(?=[0-9])/,
          '- orbital overlap': /^-(?=(?:[spd]|sp)(?:$|[\s,;\)\]\}]))/,
          '-': /^-/,
          'pm-operator': /^(?:\\pm|\$\\pm\$|\+-|\+\/-)/,
          operator: /^(?:\+|(?:[\-=<>]|<<|>>|\\approx|\$\\approx\$)(?=\s|$|-?[0-9]))/,
          arrowUpDown: /^(?:v|\(v\)|\^|\(\^\))(?=$|[\s,;\)\]\}])/,
          '\\bond{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '\\bond{', '', '', '}');
          },
          '->': /^(?:<->|<-->|->|<-|<=>>|<<=>|<=>|[\u2192\u27F6\u21CC])/,
          CMT: /^[CMT](?=\[)/,
          '[(...)]': function (t) {
            return a.patterns.findObserveGroups(t, '[', '', '', ']');
          },
          '1st-level escape': /^(&|\\\\|\\hline)\s*/,
          '\\,': /^(?:\\[,\ ;:])/,
          '\\x{}{}': function (t) {
            return a.patterns.findObserveGroups(
              t,
              '',
              /^\\[a-zA-Z]+\{/,
              '}',
              '',
              '',
              '{',
              '}',
              '',
              !0,
            );
          },
          '\\x{}': function (t) {
            return a.patterns.findObserveGroups(t, '', /^\\[a-zA-Z]+\{/, '}', '');
          },
          '\\ca': /^\\ca(?:\s+|(?![a-zA-Z]))/,
          '\\x': /^(?:\\[a-zA-Z]+\s*|\\[_&{}%])/,
          orbital: /^(?:[0-9]{1,2}[spdfgh]|[0-9]{0,2}sp)(?=$|[^a-zA-Z])/,
          others: /^[\/~|]/,
          '\\frac{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '\\frac{', '', '', '}', '{', '', '', '}');
          },
          '\\overset{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '\\overset{', '', '', '}', '{', '', '', '}');
          },
          '\\underset{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '\\underset{', '', '', '}', '{', '', '', '}');
          },
          '\\underbrace{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '\\underbrace{', '', '', '}_', '{', '', '', '}');
          },
          '\\color{(...)}0': function (t) {
            return a.patterns.findObserveGroups(t, '\\color{', '', '', '}');
          },
          '\\color{(...)}{(...)}1': function (t) {
            return a.patterns.findObserveGroups(t, '\\color{', '', '', '}', '{', '', '', '}');
          },
          '\\color(...){(...)}2': function (t) {
            return a.patterns.findObserveGroups(
              t,
              '\\color',
              '\\',
              '',
              /^(?=\{)/,
              '{',
              '',
              '',
              '}',
            );
          },
          '\\ce{(...)}': function (t) {
            return a.patterns.findObserveGroups(t, '\\ce{', '', '', '}');
          },
          oxidation$: /^(?:[+-][IVX]+|\\pm\s*0|\$\\pm\$\s*0)$/,
          'd-oxidation$': /^(?:[+-]?\s?[IVX]+|\\pm\s*0|\$\\pm\$\s*0)$/,
          'roman numeral': /^[IVX]+/,
          '1/2$': /^[+\-]?(?:[0-9]+|\$[a-z]\$|[a-z])\/[0-9]+(?:\$[a-z]\$|[a-z])?$/,
          amount: function (t) {
            var e;
            if (
              (e = t.match(
                /^(?:(?:(?:\([+\-]?[0-9]+\/[0-9]+\)|[+\-]?(?:[0-9]+|\$[a-z]\$|[a-z])\/[0-9]+|[+\-]?[0-9]+[.,][0-9]+|[+\-]?\.[0-9]+|[+\-]?[0-9]+)(?:[a-z](?=\s*[A-Z]))?)|[+\-]?[a-z](?=\s*[A-Z])|\+(?!\s))/,
              ))
            )
              return { match_: e[0], remainder: t.substr(e[0].length) };
            var r = a.patterns.findObserveGroups(t, '', '$', '$', '');
            return r &&
              (e = r.match_.match(
                /^\$(?:\(?[+\-]?(?:[0-9]*[a-z]?[+\-])?[0-9]*[a-z](?:[+\-][0-9]*[a-z]?)?\)?|\+|-)\$$/,
              ))
              ? { match_: e[0], remainder: t.substr(e[0].length) }
              : null;
          },
          amount2: function (t) {
            return this.amount(t);
          },
          '(KV letters),': /^(?:[A-Z][a-z]{0,2}|i)(?=,)/,
          formula$: function (t) {
            if (t.match(/^\([a-z]+\)$/)) return null;
            var e = t.match(
              /^(?:[a-z]|(?:[0-9\ \+\-\,\.\(\)]+[a-z])+[0-9\ \+\-\,\.\(\)]*|(?:[a-z][0-9\ \+\-\,\.\(\)]+)+[a-z]?)$/,
            );
            return e ? { match_: e[0], remainder: t.substr(e[0].length) } : null;
          },
          uprightEntities: /^(?:pH|pOH|pC|pK|iPr|iBu)(?=$|[^a-zA-Z])/,
          '/': /^\s*(\/)\s*/,
          '//': /^\s*(\/\/)\s*/,
          '*': /^\s*[*.]\s*/,
        },
        findObserveGroups: function (t, e, r, a, n, o, i, s, c, l) {
          var u = function (t, e) {
              if ('string' == typeof e) return 0 !== t.indexOf(e) ? null : e;
              var r = t.match(e);
              return r ? r[0] : null;
            },
            d = u(t, e);
          if (null === d) return null;
          if (((t = t.substr(d.length)), null === (d = u(t, r)))) return null;
          var p = (function (t, e, r) {
            for (var a = 0; e < t.length; ) {
              var n = t.charAt(e),
                o = u(t.substr(e), r);
              if (null !== o && 0 === a) return { endMatchBegin: e, endMatchEnd: e + o.length };
              if ('{' === n) a++;
              else if ('}' === n) {
                if (0 === a)
                  throw ['ExtraCloseMissingOpen', 'Extra close brace or missing open brace'];
                a--;
              }
              e++;
            }
            return null;
          })(t, d.length, a || n);
          if (null === p) return null;
          var f = t.substring(0, a ? p.endMatchEnd : p.endMatchBegin);
          if (o || i) {
            var m = this.findObserveGroups(t.substr(p.endMatchEnd), o, i, s, c);
            if (null === m) return null;
            var h = [f, m.match_];
            return { match_: l ? h.join('') : h, remainder: m.remainder };
          }
          return { match_: f, remainder: t.substr(p.endMatchEnd) };
        },
        match_: function (t, e) {
          var r = a.patterns.patterns[t];
          if (void 0 === r) throw ['MhchemBugP', 'mhchem bug P. Please report. (' + t + ')'];
          if ('function' == typeof r) return a.patterns.patterns[t](e);
          var n = e.match(r);
          return n
            ? { match_: n[2] ? [n[1], n[2]] : n[1] ? n[1] : n[0], remainder: e.substr(n[0].length) }
            : null;
        },
      },
      actions: {
        'a=': function (t, e) {
          t.a = (t.a || '') + e;
        },
        'b=': function (t, e) {
          t.b = (t.b || '') + e;
        },
        'p=': function (t, e) {
          t.p = (t.p || '') + e;
        },
        'o=': function (t, e) {
          t.o = (t.o || '') + e;
        },
        'q=': function (t, e) {
          t.q = (t.q || '') + e;
        },
        'd=': function (t, e) {
          t.d = (t.d || '') + e;
        },
        'rm=': function (t, e) {
          t.rm = (t.rm || '') + e;
        },
        'text=': function (t, e) {
          t.text_ = (t.text_ || '') + e;
        },
        insert: function (t, e, r) {
          return { type_: r };
        },
        'insert+p1': function (t, e, r) {
          return { type_: r, p1: e };
        },
        'insert+p1+p2': function (t, e, r) {
          return { type_: r, p1: e[0], p2: e[1] };
        },
        copy: function (t, e) {
          return e;
        },
        rm: function (t, e) {
          return { type_: 'rm', p1: e || '' };
        },
        text: function (t, e) {
          return a.go(e, 'text');
        },
        '{text}': function (t, e) {
          var r = ['{'];
          return a.concatArray(r, a.go(e, 'text')), r.push('}'), r;
        },
        'tex-math': function (t, e) {
          return a.go(e, 'tex-math');
        },
        'tex-math tight': function (t, e) {
          return a.go(e, 'tex-math tight');
        },
        bond: function (t, e, r) {
          return { type_: 'bond', kind_: r || e };
        },
        'color0-output': function (t, e) {
          return { type_: 'color0', color: e[0] };
        },
        ce: function (t, e) {
          return a.go(e);
        },
        '1/2': function (t, e) {
          var r = [];
          e.match(/^[+\-]/) && (r.push(e.substr(0, 1)), (e = e.substr(1)));
          var a = e.match(/^([0-9]+|\$[a-z]\$|[a-z])\/([0-9]+)(\$[a-z]\$|[a-z])?$/);
          return (
            (a[1] = a[1].replace(/\$/g, '')),
            r.push({ type_: 'frac', p1: a[1], p2: a[2] }),
            a[3] && ((a[3] = a[3].replace(/\$/g, '')), r.push({ type_: 'tex-math', p1: a[3] })),
            r
          );
        },
        '9,9': function (t, e) {
          return a.go(e, '9,9');
        },
      },
      createTransitions: function (t) {
        var e,
          r,
          a,
          n,
          o = {};
        for (e in t)
          for (r in t[e])
            for (a = r.split('|'), t[e][r].stateArray = a, n = 0; n < a.length; n++) o[a[n]] = [];
        for (e in t)
          for (r in t[e])
            for (a = t[e][r].stateArray || [], n = 0; n < a.length; n++) {
              var i = t[e][r];
              if (i.action_) {
                i.action_ = [].concat(i.action_);
                for (var s = 0; s < i.action_.length; s++)
                  'string' == typeof i.action_[s] && (i.action_[s] = { type_: i.action_[s] });
              } else i.action_ = [];
              for (var c = e.split('|'), l = 0; l < c.length; l++)
                if ('*' === a[n]) for (var u in o) o[u].push({ pattern: c[l], task: i });
                else o[a[n]].push({ pattern: c[l], task: i });
            }
        return o;
      },
      stateMachines: {},
    };
    a.stateMachines = {
      ce: {
        transitions: a.createTransitions({
          empty: { '*': { action_: 'output' } },
          else: { '0|1|2': { action_: 'beginsWithBond=false', revisit: !0, toContinue: !0 } },
          oxidation$: { 0: { action_: 'oxidation-output' } },
          CMT: {
            r: { action_: 'rdt=', nextState: 'rt' },
            rd: { action_: 'rqt=', nextState: 'rdt' },
          },
          arrowUpDown: {
            '0|1|2|as': { action_: ['sb=false', 'output', 'operator'], nextState: '1' },
          },
          uprightEntities: { '0|1|2': { action_: ['o=', 'output'], nextState: '1' } },
          orbital: { '0|1|2|3': { action_: 'o=', nextState: 'o' } },
          '->': {
            '0|1|2|3': { action_: 'r=', nextState: 'r' },
            'a|as': { action_: ['output', 'r='], nextState: 'r' },
            '*': { action_: ['output', 'r='], nextState: 'r' },
          },
          '+': {
            o: { action_: 'd= kv', nextState: 'd' },
            'd|D': { action_: 'd=', nextState: 'd' },
            q: { action_: 'd=', nextState: 'qd' },
            'qd|qD': { action_: 'd=', nextState: 'qd' },
            dq: { action_: ['output', 'd='], nextState: 'd' },
            3: { action_: ['sb=false', 'output', 'operator'], nextState: '0' },
          },
          amount: { '0|2': { action_: 'a=', nextState: 'a' } },
          'pm-operator': {
            '0|1|2|a|as': {
              action_: ['sb=false', 'output', { type_: 'operator', option: '\\pm' }],
              nextState: '0',
            },
          },
          operator: {
            '0|1|2|a|as': { action_: ['sb=false', 'output', 'operator'], nextState: '0' },
          },
          '-$': {
            'o|q': { action_: ['charge or bond', 'output'], nextState: 'qd' },
            d: { action_: 'd=', nextState: 'd' },
            D: { action_: ['output', { type_: 'bond', option: '-' }], nextState: '3' },
            q: { action_: 'd=', nextState: 'qd' },
            qd: { action_: 'd=', nextState: 'qd' },
            'qD|dq': { action_: ['output', { type_: 'bond', option: '-' }], nextState: '3' },
          },
          '-9': {
            '3|o': { action_: ['output', { type_: 'insert', option: 'hyphen' }], nextState: '3' },
          },
          '- orbital overlap': {
            o: { action_: ['output', { type_: 'insert', option: 'hyphen' }], nextState: '2' },
            d: { action_: ['output', { type_: 'insert', option: 'hyphen' }], nextState: '2' },
          },
          '-': {
            '0|1|2': {
              action_: [
                { type_: 'output', option: 1 },
                'beginsWithBond=true',
                { type_: 'bond', option: '-' },
              ],
              nextState: '3',
            },
            3: { action_: { type_: 'bond', option: '-' } },
            a: { action_: ['output', { type_: 'insert', option: 'hyphen' }], nextState: '2' },
            as: {
              action_: [
                { type_: 'output', option: 2 },
                { type_: 'bond', option: '-' },
              ],
              nextState: '3',
            },
            b: { action_: 'b=' },
            o: { action_: { type_: '- after o/d', option: !1 }, nextState: '2' },
            q: { action_: { type_: '- after o/d', option: !1 }, nextState: '2' },
            'd|qd|dq': { action_: { type_: '- after o/d', option: !0 }, nextState: '2' },
            'D|qD|p': { action_: ['output', { type_: 'bond', option: '-' }], nextState: '3' },
          },
          amount2: { '1|3': { action_: 'a=', nextState: 'a' } },
          letters: {
            '0|1|2|3|a|as|b|p|bp|o': { action_: 'o=', nextState: 'o' },
            'q|dq': { action_: ['output', 'o='], nextState: 'o' },
            'd|D|qd|qD': { action_: 'o after d', nextState: 'o' },
          },
          digits: {
            o: { action_: 'q=', nextState: 'q' },
            'd|D': { action_: 'q=', nextState: 'dq' },
            q: { action_: ['output', 'o='], nextState: 'o' },
            a: { action_: 'o=', nextState: 'o' },
          },
          'space A': { 'b|p|bp': {} },
          space: {
            a: { nextState: 'as' },
            0: { action_: 'sb=false' },
            '1|2': { action_: 'sb=true' },
            'r|rt|rd|rdt|rdq': { action_: 'output', nextState: '0' },
            '*': { action_: ['output', 'sb=true'], nextState: '1' },
          },
          '1st-level escape': {
            '1|2': { action_: ['output', { type_: 'insert+p1', option: '1st-level escape' }] },
            '*': {
              action_: ['output', { type_: 'insert+p1', option: '1st-level escape' }],
              nextState: '0',
            },
          },
          '[(...)]': {
            'r|rt': { action_: 'rd=', nextState: 'rd' },
            'rd|rdt': { action_: 'rq=', nextState: 'rdq' },
          },
          '...': {
            'o|d|D|dq|qd|qD': {
              action_: ['output', { type_: 'bond', option: '...' }],
              nextState: '3',
            },
            '*': {
              action_: [
                { type_: 'output', option: 1 },
                { type_: 'insert', option: 'ellipsis' },
              ],
              nextState: '1',
            },
          },
          '. |* ': {
            '*': {
              action_: ['output', { type_: 'insert', option: 'addition compound' }],
              nextState: '1',
            },
          },
          'state of aggregation $': {
            '*': { action_: ['output', 'state of aggregation'], nextState: '1' },
          },
          '{[(': {
            'a|as|o': { action_: ['o=', 'output', 'parenthesisLevel++'], nextState: '2' },
            '0|1|2|3': { action_: ['o=', 'output', 'parenthesisLevel++'], nextState: '2' },
            '*': { action_: ['output', 'o=', 'output', 'parenthesisLevel++'], nextState: '2' },
          },
          ')]}': {
            '0|1|2|3|b|p|bp|o': { action_: ['o=', 'parenthesisLevel--'], nextState: 'o' },
            'a|as|d|D|q|qd|qD|dq': {
              action_: ['output', 'o=', 'parenthesisLevel--'],
              nextState: 'o',
            },
          },
          ', ': { '*': { action_: ['output', 'comma'], nextState: '0' } },
          '^_': { '*': {} },
          '^{(...)}|^($...$)': {
            '0|1|2|as': { action_: 'b=', nextState: 'b' },
            p: { action_: 'b=', nextState: 'bp' },
            '3|o': { action_: 'd= kv', nextState: 'D' },
            q: { action_: 'd=', nextState: 'qD' },
            'd|D|qd|qD|dq': { action_: ['output', 'd='], nextState: 'D' },
          },
          "^a|^\\x{}{}|^\\x{}|^\\x|'": {
            '0|1|2|as': { action_: 'b=', nextState: 'b' },
            p: { action_: 'b=', nextState: 'bp' },
            '3|o': { action_: 'd= kv', nextState: 'd' },
            q: { action_: 'd=', nextState: 'qd' },
            'd|qd|D|qD': { action_: 'd=' },
            dq: { action_: ['output', 'd='], nextState: 'd' },
          },
          '_{(state of aggregation)}$': {
            'd|D|q|qd|qD|dq': { action_: ['output', 'q='], nextState: 'q' },
          },
          '_{(...)}|_($...$)|_9|_\\x{}{}|_\\x{}|_\\x': {
            '0|1|2|as': { action_: 'p=', nextState: 'p' },
            b: { action_: 'p=', nextState: 'bp' },
            '3|o': { action_: 'q=', nextState: 'q' },
            'd|D': { action_: 'q=', nextState: 'dq' },
            'q|qd|qD|dq': { action_: ['output', 'q='], nextState: 'q' },
          },
          '=<>': {
            '0|1|2|3|a|as|o|q|d|D|qd|qD|dq': {
              action_: [{ type_: 'output', option: 2 }, 'bond'],
              nextState: '3',
            },
          },
          '#': {
            '0|1|2|3|a|as|o': {
              action_: [
                { type_: 'output', option: 2 },
                { type_: 'bond', option: '#' },
              ],
              nextState: '3',
            },
          },
          '{}': { '*': { action_: { type_: 'output', option: 1 }, nextState: '1' } },
          '{...}': {
            '0|1|2|3|a|as|b|p|bp': { action_: 'o=', nextState: 'o' },
            'o|d|D|q|qd|qD|dq': { action_: ['output', 'o='], nextState: 'o' },
          },
          '$...$': {
            a: { action_: 'a=' },
            '0|1|2|3|as|b|p|bp|o': { action_: 'o=', nextState: 'o' },
            'as|o': { action_: 'o=' },
            'q|d|D|qd|qD|dq': { action_: ['output', 'o='], nextState: 'o' },
          },
          '\\bond{(...)}': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'bond'], nextState: '3' },
          },
          '\\frac{(...)}': {
            '*': { action_: [{ type_: 'output', option: 1 }, 'frac-output'], nextState: '3' },
          },
          '\\overset{(...)}': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'overset-output'], nextState: '3' },
          },
          '\\underset{(...)}': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'underset-output'], nextState: '3' },
          },
          '\\underbrace{(...)}': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'underbrace-output'], nextState: '3' },
          },
          '\\color{(...)}{(...)}1|\\color(...){(...)}2': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'color-output'], nextState: '3' },
          },
          '\\color{(...)}0': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'color0-output'] },
          },
          '\\ce{(...)}': {
            '*': { action_: [{ type_: 'output', option: 2 }, 'ce'], nextState: '3' },
          },
          '\\,': { '*': { action_: [{ type_: 'output', option: 1 }, 'copy'], nextState: '1' } },
          '\\x{}{}|\\x{}|\\x': {
            '0|1|2|3|a|as|b|p|bp|o|c0': { action_: ['o=', 'output'], nextState: '3' },
            '*': { action_: ['output', 'o=', 'output'], nextState: '3' },
          },
          others: { '*': { action_: [{ type_: 'output', option: 1 }, 'copy'], nextState: '3' } },
          else2: {
            a: { action_: 'a to o', nextState: 'o', revisit: !0 },
            as: { action_: ['output', 'sb=true'], nextState: '1', revisit: !0 },
            'r|rt|rd|rdt|rdq': { action_: ['output'], nextState: '0', revisit: !0 },
            '*': { action_: ['output', 'copy'], nextState: '3' },
          },
        }),
        actions: {
          'o after d': function (t, e) {
            var r;
            if ((t.d || '').match(/^[0-9]+$/)) {
              var n = t.d;
              (t.d = void 0), (r = this.output(t)), (t.b = n);
            } else r = this.output(t);
            return a.actions['o='](t, e), r;
          },
          'd= kv': function (t, e) {
            (t.d = e), (t.dType = 'kv');
          },
          'charge or bond': function (t, e) {
            if (t.beginsWithBond) {
              var r = [];
              return (
                a.concatArray(r, this.output(t)), a.concatArray(r, a.actions.bond(t, e, '-')), r
              );
            }
            t.d = e;
          },
          '- after o/d': function (t, e, r) {
            var n = a.patterns.match_('orbital', t.o || ''),
              o = a.patterns.match_('one lowercase greek letter $', t.o || ''),
              i = a.patterns.match_('one lowercase latin letter $', t.o || ''),
              s = a.patterns.match_('$one lowercase latin letter$ $', t.o || ''),
              c = '-' === e && ((n && '' === n.remainder) || o || i || s);
            !c || t.a || t.b || t.p || t.d || t.q || n || !i || (t.o = '$' + t.o + '$');
            var l = [];
            return (
              c
                ? (a.concatArray(l, this.output(t)), l.push({ type_: 'hyphen' }))
                : ((n = a.patterns.match_('digits', t.d || '')),
                  r && n && '' === n.remainder
                    ? (a.concatArray(l, a.actions['d='](t, e)), a.concatArray(l, this.output(t)))
                    : (a.concatArray(l, this.output(t)),
                      a.concatArray(l, a.actions.bond(t, e, '-')))),
              l
            );
          },
          'a to o': function (t) {
            (t.o = t.a), (t.a = void 0);
          },
          'sb=true': function (t) {
            t.sb = !0;
          },
          'sb=false': function (t) {
            t.sb = !1;
          },
          'beginsWithBond=true': function (t) {
            t.beginsWithBond = !0;
          },
          'beginsWithBond=false': function (t) {
            t.beginsWithBond = !1;
          },
          'parenthesisLevel++': function (t) {
            t.parenthesisLevel++;
          },
          'parenthesisLevel--': function (t) {
            t.parenthesisLevel--;
          },
          'state of aggregation': function (t, e) {
            return { type_: 'state of aggregation', p1: a.go(e, 'o') };
          },
          comma: function (t, e) {
            var r = e.replace(/\s*$/, '');
            return r !== e && 0 === t.parenthesisLevel
              ? { type_: 'comma enumeration L', p1: r }
              : { type_: 'comma enumeration M', p1: r };
          },
          output: function (t, e, r) {
            var n, o, i;
            t.r
              ? ((o =
                  'M' === t.rdt
                    ? a.go(t.rd, 'tex-math')
                    : 'T' === t.rdt
                    ? [{ type_: 'text', p1: t.rd || '' }]
                    : a.go(t.rd)),
                (i =
                  'M' === t.rqt
                    ? a.go(t.rq, 'tex-math')
                    : 'T' === t.rqt
                    ? [{ type_: 'text', p1: t.rq || '' }]
                    : a.go(t.rq)),
                (n = { type_: 'arrow', r: t.r, rd: o, rq: i }))
              : ((n = []),
                (t.a || t.b || t.p || t.o || t.q || t.d || r) &&
                  (t.sb && n.push({ type_: 'entitySkip' }),
                  t.o || t.q || t.d || t.b || t.p || 2 === r
                    ? t.o || t.q || t.d || (!t.b && !t.p)
                      ? t.o && 'kv' === t.dType && a.patterns.match_('d-oxidation$', t.d || '')
                        ? (t.dType = 'oxidation')
                        : t.o && 'kv' === t.dType && !t.q && (t.dType = void 0)
                      : ((t.o = t.a), (t.d = t.b), (t.q = t.p), (t.a = t.b = t.p = void 0))
                    : ((t.o = t.a), (t.a = void 0)),
                  n.push({
                    type_: 'chemfive',
                    a: a.go(t.a, 'a'),
                    b: a.go(t.b, 'bd'),
                    p: a.go(t.p, 'pq'),
                    o: a.go(t.o, 'o'),
                    q: a.go(t.q, 'pq'),
                    d: a.go(t.d, 'oxidation' === t.dType ? 'oxidation' : 'bd'),
                    dType: t.dType,
                  })));
            for (var s in t) 'parenthesisLevel' !== s && 'beginsWithBond' !== s && delete t[s];
            return n;
          },
          'oxidation-output': function (t, e) {
            var r = ['{'];
            return a.concatArray(r, a.go(e, 'oxidation')), r.push('}'), r;
          },
          'frac-output': function (t, e) {
            return { type_: 'frac-ce', p1: a.go(e[0]), p2: a.go(e[1]) };
          },
          'overset-output': function (t, e) {
            return { type_: 'overset', p1: a.go(e[0]), p2: a.go(e[1]) };
          },
          'underset-output': function (t, e) {
            return { type_: 'underset', p1: a.go(e[0]), p2: a.go(e[1]) };
          },
          'underbrace-output': function (t, e) {
            return { type_: 'underbrace', p1: a.go(e[0]), p2: a.go(e[1]) };
          },
          'color-output': function (t, e) {
            return { type_: 'color', color1: e[0], color2: a.go(e[1]) };
          },
          'r=': function (t, e) {
            t.r = e;
          },
          'rdt=': function (t, e) {
            t.rdt = e;
          },
          'rd=': function (t, e) {
            t.rd = e;
          },
          'rqt=': function (t, e) {
            t.rqt = e;
          },
          'rq=': function (t, e) {
            t.rq = e;
          },
          operator: function (t, e, r) {
            return { type_: 'operator', kind_: r || e };
          },
        },
      },
      a: {
        transitions: a.createTransitions({
          empty: { '*': {} },
          '1/2$': { 0: { action_: '1/2' } },
          else: { 0: { nextState: '1', revisit: !0 } },
          '$(...)$': { '*': { action_: 'tex-math tight', nextState: '1' } },
          ',': { '*': { action_: { type_: 'insert', option: 'commaDecimal' } } },
          else2: { '*': { action_: 'copy' } },
        }),
        actions: {},
      },
      o: {
        transitions: a.createTransitions({
          empty: { '*': {} },
          '1/2$': { 0: { action_: '1/2' } },
          else: { 0: { nextState: '1', revisit: !0 } },
          letters: { '*': { action_: 'rm' } },
          '\\ca': { '*': { action_: { type_: 'insert', option: 'circa' } } },
          '\\x{}{}|\\x{}|\\x': { '*': { action_: 'copy' } },
          '${(...)}$|$(...)$': { '*': { action_: 'tex-math' } },
          '{(...)}': { '*': { action_: '{text}' } },
          else2: { '*': { action_: 'copy' } },
        }),
        actions: {},
      },
      text: {
        transitions: a.createTransitions({
          empty: { '*': { action_: 'output' } },
          '{...}': { '*': { action_: 'text=' } },
          '${(...)}$|$(...)$': { '*': { action_: 'tex-math' } },
          '\\greek': { '*': { action_: ['output', 'rm'] } },
          '\\,|\\x{}{}|\\x{}|\\x': { '*': { action_: ['output', 'copy'] } },
          else: { '*': { action_: 'text=' } },
        }),
        actions: {
          output: function (t) {
            if (t.text_) {
              var e = { type_: 'text', p1: t.text_ };
              for (var r in t) delete t[r];
              return e;
            }
          },
        },
      },
      pq: {
        transitions: a.createTransitions({
          empty: { '*': {} },
          'state of aggregation $': { '*': { action_: 'state of aggregation' } },
          i$: { 0: { nextState: '!f', revisit: !0 } },
          '(KV letters),': { 0: { action_: 'rm', nextState: '0' } },
          formula$: { 0: { nextState: 'f', revisit: !0 } },
          '1/2$': { 0: { action_: '1/2' } },
          else: { 0: { nextState: '!f', revisit: !0 } },
          '${(...)}$|$(...)$': { '*': { action_: 'tex-math' } },
          '{(...)}': { '*': { action_: 'text' } },
          'a-z': { f: { action_: 'tex-math' } },
          letters: { '*': { action_: 'rm' } },
          '-9.,9': { '*': { action_: '9,9' } },
          ',': { '*': { action_: { type_: 'insert+p1', option: 'comma enumeration S' } } },
          '\\color{(...)}{(...)}1|\\color(...){(...)}2': { '*': { action_: 'color-output' } },
          '\\color{(...)}0': { '*': { action_: 'color0-output' } },
          '\\ce{(...)}': { '*': { action_: 'ce' } },
          '\\,|\\x{}{}|\\x{}|\\x': { '*': { action_: 'copy' } },
          else2: { '*': { action_: 'copy' } },
        }),
        actions: {
          'state of aggregation': function (t, e) {
            return { type_: 'state of aggregation subscript', p1: a.go(e, 'o') };
          },
          'color-output': function (t, e) {
            return { type_: 'color', color1: e[0], color2: a.go(e[1], 'pq') };
          },
        },
      },
      bd: {
        transitions: a.createTransitions({
          empty: { '*': {} },
          x$: { 0: { nextState: '!f', revisit: !0 } },
          formula$: { 0: { nextState: 'f', revisit: !0 } },
          else: { 0: { nextState: '!f', revisit: !0 } },
          '-9.,9 no missing 0': { '*': { action_: '9,9' } },
          '.': { '*': { action_: { type_: 'insert', option: 'electron dot' } } },
          'a-z': { f: { action_: 'tex-math' } },
          x: { '*': { action_: { type_: 'insert', option: 'KV x' } } },
          letters: { '*': { action_: 'rm' } },
          "'": { '*': { action_: { type_: 'insert', option: 'prime' } } },
          '${(...)}$|$(...)$': { '*': { action_: 'tex-math' } },
          '{(...)}': { '*': { action_: 'text' } },
          '\\color{(...)}{(...)}1|\\color(...){(...)}2': { '*': { action_: 'color-output' } },
          '\\color{(...)}0': { '*': { action_: 'color0-output' } },
          '\\ce{(...)}': { '*': { action_: 'ce' } },
          '\\,|\\x{}{}|\\x{}|\\x': { '*': { action_: 'copy' } },
          else2: { '*': { action_: 'copy' } },
        }),
        actions: {
          'color-output': function (t, e) {
            return { type_: 'color', color1: e[0], color2: a.go(e[1], 'bd') };
          },
        },
      },
      oxidation: {
        transitions: a.createTransitions({
          empty: { '*': {} },
          'roman numeral': { '*': { action_: 'roman-numeral' } },
          '${(...)}$|$(...)$': { '*': { action_: 'tex-math' } },
          else: { '*': { action_: 'copy' } },
        }),
        actions: {
          'roman-numeral': function (t, e) {
            return { type_: 'roman numeral', p1: e || '' };
          },
        },
      },
      'tex-math': {
        transitions: a.createTransitions({
          empty: { '*': { action_: 'output' } },
          '\\ce{(...)}': { '*': { action_: ['output', 'ce'] } },
          '{...}|\\,|\\x{}{}|\\x{}|\\x': { '*': { action_: 'o=' } },
          else: { '*': { action_: 'o=' } },
        }),
        actions: {
          output: function (t) {
            if (t.o) {
              var e = { type_: 'tex-math', p1: t.o };
              for (var r in t) delete t[r];
              return e;
            }
          },
        },
      },
      'tex-math tight': {
        transitions: a.createTransitions({
          empty: { '*': { action_: 'output' } },
          '\\ce{(...)}': { '*': { action_: ['output', 'ce'] } },
          '{...}|\\,|\\x{}{}|\\x{}|\\x': { '*': { action_: 'o=' } },
          '-|+': { '*': { action_: 'tight operator' } },
          else: { '*': { action_: 'o=' } },
        }),
        actions: {
          'tight operator': function (t, e) {
            t.o = (t.o || '') + '{' + e + '}';
          },
          output: function (t) {
            if (t.o) {
              var e = { type_: 'tex-math', p1: t.o };
              for (var r in t) delete t[r];
              return e;
            }
          },
        },
      },
      '9,9': {
        transitions: a.createTransitions({
          empty: { '*': {} },
          ',': { '*': { action_: 'comma' } },
          else: { '*': { action_: 'copy' } },
        }),
        actions: {
          comma: function () {
            return { type_: 'commaDecimal' };
          },
        },
      },
      pu: {
        transitions: a.createTransitions({
          empty: { '*': { action_: 'output' } },
          space$: { '*': { action_: ['output', 'space'] } },
          '{[(|)]}': { '0|a': { action_: 'copy' } },
          '(-)(9)^(-9)': { 0: { action_: 'number^', nextState: 'a' } },
          '(-)(9.,9)(e)(99)': { 0: { action_: 'enumber', nextState: 'a' } },
          space: { '0|a': {} },
          'pm-operator': {
            '0|a': { action_: { type_: 'operator', option: '\\pm' }, nextState: '0' },
          },
          operator: { '0|a': { action_: 'copy', nextState: '0' } },
          '//': { d: { action_: 'o=', nextState: '/' } },
          '/': { d: { action_: 'o=', nextState: '/' } },
          '{...}|else': {
            '0|d': { action_: 'd=', nextState: 'd' },
            a: { action_: ['space', 'd='], nextState: 'd' },
            '/|q': { action_: 'q=', nextState: 'q' },
          },
        }),
        actions: {
          enumber: function (t, e) {
            var r = [];
            return (
              '+-' === e[0] || '+/-' === e[0] ? r.push('\\pm ') : e[0] && r.push(e[0]),
              e[1] &&
                (a.concatArray(r, a.go(e[1], 'pu-9,9')),
                e[2] &&
                  (e[2].match(/[,.]/) ? a.concatArray(r, a.go(e[2], 'pu-9,9')) : r.push(e[2])),
                (e[3] = e[4] || e[3]),
                e[3] &&
                  ((e[3] = e[3].trim()),
                  'e' === e[3] || '*' === e[3].substr(0, 1)
                    ? r.push({ type_: 'cdot' })
                    : r.push({ type_: 'times' }))),
              e[3] && r.push('10^{' + e[5] + '}'),
              r
            );
          },
          'number^': function (t, e) {
            var r = [];
            return (
              '+-' === e[0] || '+/-' === e[0] ? r.push('\\pm ') : e[0] && r.push(e[0]),
              a.concatArray(r, a.go(e[1], 'pu-9,9')),
              r.push('^{' + e[2] + '}'),
              r
            );
          },
          operator: function (t, e, r) {
            return { type_: 'operator', kind_: r || e };
          },
          space: function () {
            return { type_: 'pu-space-1' };
          },
          output: function (t) {
            var e,
              r = a.patterns.match_('{(...)}', t.d || '');
            r && '' === r.remainder && (t.d = r.match_);
            var n = a.patterns.match_('{(...)}', t.q || '');
            if (
              (n && '' === n.remainder && (t.q = n.match_),
              t.d &&
                ((t.d = t.d.replace(/\u00B0C|\^oC|\^{o}C/g, '{}^{\\circ}C')),
                (t.d = t.d.replace(/\u00B0F|\^oF|\^{o}F/g, '{}^{\\circ}F'))),
              t.q)
            ) {
              (t.q = t.q.replace(/\u00B0C|\^oC|\^{o}C/g, '{}^{\\circ}C')),
                (t.q = t.q.replace(/\u00B0F|\^oF|\^{o}F/g, '{}^{\\circ}F'));
              var o = { d: a.go(t.d, 'pu'), q: a.go(t.q, 'pu') };
              '//' === t.o
                ? (e = { type_: 'pu-frac', p1: o.d, p2: o.q })
                : ((e = o.d),
                  o.d.length > 1 || o.q.length > 1
                    ? e.push({ type_: ' / ' })
                    : e.push({ type_: '/' }),
                  a.concatArray(e, o.q));
            } else e = a.go(t.d, 'pu-2');
            for (var i in t) delete t[i];
            return e;
          },
        },
      },
      'pu-2': {
        transitions: a.createTransitions({
          empty: { '*': { action_: 'output' } },
          '*': { '*': { action_: ['output', 'cdot'], nextState: '0' } },
          '\\x': { '*': { action_: 'rm=' } },
          space: { '*': { action_: ['output', 'space'], nextState: '0' } },
          '^{(...)}|^(-1)': { 1: { action_: '^(-1)' } },
          '-9.,9': {
            0: { action_: 'rm=', nextState: '0' },
            1: { action_: '^(-1)', nextState: '0' },
          },
          '{...}|else': { '*': { action_: 'rm=', nextState: '1' } },
        }),
        actions: {
          cdot: function () {
            return { type_: 'tight cdot' };
          },
          '^(-1)': function (t, e) {
            t.rm += '^{' + e + '}';
          },
          space: function () {
            return { type_: 'pu-space-2' };
          },
          output: function (t) {
            var e = [];
            if (t.rm) {
              var r = a.patterns.match_('{(...)}', t.rm || '');
              e = r && '' === r.remainder ? a.go(r.match_, 'pu') : { type_: 'rm', p1: t.rm };
            }
            for (var n in t) delete t[n];
            return e;
          },
        },
      },
      'pu-9,9': {
        transitions: a.createTransitions({
          empty: { 0: { action_: 'output-0' }, o: { action_: 'output-o' } },
          ',': { 0: { action_: ['output-0', 'comma'], nextState: 'o' } },
          '.': { 0: { action_: ['output-0', 'copy'], nextState: 'o' } },
          else: { '*': { action_: 'text=' } },
        }),
        actions: {
          comma: function () {
            return { type_: 'commaDecimal' };
          },
          'output-0': function (t) {
            var e = [];
            if (((t.text_ = t.text_ || ''), t.text_.length > 4)) {
              var r = t.text_.length % 3;
              0 === r && (r = 3);
              for (var a = t.text_.length - 3; a > 0; a -= 3)
                e.push(t.text_.substr(a, 3)), e.push({ type_: '1000 separator' });
              e.push(t.text_.substr(0, r)), e.reverse();
            } else e.push(t.text_);
            for (var n in t) delete t[n];
            return e;
          },
          'output-o': function (t) {
            var e = [];
            if (((t.text_ = t.text_ || ''), t.text_.length > 4)) {
              for (var r = t.text_.length - 3, a = 0; a < r; a += 3)
                e.push(t.text_.substr(a, 3)), e.push({ type_: '1000 separator' });
              e.push(t.text_.substr(a));
            } else e.push(t.text_);
            for (var n in t) delete t[n];
            return e;
          },
        },
      },
    };
    var n = {
      go: function (t, e) {
        if (!t) return '';
        for (var r = '', a = !1, o = 0; o < t.length; o++) {
          var i = t[o];
          'string' == typeof i
            ? (r += i)
            : ((r += n._go2(i)), '1st-level escape' === i.type_ && (a = !0));
        }
        return e || a || !r || (r = '{' + r + '}'), r;
      },
      _goInner: function (t) {
        return t ? n.go(t, !0) : t;
      },
      _go2: function (t) {
        var e;
        switch (t.type_) {
          case 'chemfive':
            e = '';
            var r = {
              a: n._goInner(t.a),
              b: n._goInner(t.b),
              p: n._goInner(t.p),
              o: n._goInner(t.o),
              q: n._goInner(t.q),
              d: n._goInner(t.d),
            };
            r.a && (r.a.match(/^[+\-]/) && (r.a = '{' + r.a + '}'), (e += r.a + '\\,')),
              (r.b || r.p) &&
                ((e += '{\\vphantom{X}}'),
                (e += '^{\\hphantom{' + (r.b || '') + '}}_{\\hphantom{' + (r.p || '') + '}}'),
                (e += '{\\vphantom{X}}'),
                (e += '^{\\smash[t]{\\vphantom{2}}\\llap{' + (r.b || '') + '}}'),
                (e += '_{\\vphantom{2}\\llap{\\smash[t]{' + (r.p || '') + '}}}')),
              r.o && (r.o.match(/^[+\-]/) && (r.o = '{' + r.o + '}'), (e += r.o)),
              'kv' === t.dType
                ? ((r.d || r.q) && (e += '{\\vphantom{X}}'),
                  r.d && (e += '^{' + r.d + '}'),
                  r.q && (e += '_{\\smash[t]{' + r.q + '}}'))
                : 'oxidation' === t.dType
                ? (r.d && ((e += '{\\vphantom{X}}'), (e += '^{' + r.d + '}')),
                  r.q && ((e += '{\\vphantom{X}}'), (e += '_{\\smash[t]{' + r.q + '}}')))
                : (r.q && ((e += '{\\vphantom{X}}'), (e += '_{\\smash[t]{' + r.q + '}}')),
                  r.d && ((e += '{\\vphantom{X}}'), (e += '^{' + r.d + '}')));
            break;
          case 'rm':
            e = '\\mathrm{' + t.p1 + '}';
            break;
          case 'text':
            t.p1.match(/[\^_]/)
              ? ((t.p1 = t.p1.replace(' ', '~').replace('-', '\\text{-}')),
                (e = '\\mathrm{' + t.p1 + '}'))
              : (e = '\\text{' + t.p1 + '}');
            break;
          case 'roman numeral':
            e = '\\mathrm{' + t.p1 + '}';
            break;
          case 'state of aggregation':
            e = '\\mskip2mu ' + n._goInner(t.p1);
            break;
          case 'state of aggregation subscript':
            e = '\\mskip1mu ' + n._goInner(t.p1);
            break;
          case 'bond':
            if (!(e = n._getBond(t.kind_)))
              throw ['MhchemErrorBond', 'mhchem Error. Unknown bond type (' + t.kind_ + ')'];
            break;
          case 'frac':
            var a = '\\frac{' + t.p1 + '}{' + t.p2 + '}';
            e = '\\mathchoice{\\textstyle' + a + '}{' + a + '}{' + a + '}{' + a + '}';
            break;
          case 'pu-frac':
            var o = '\\frac{' + n._goInner(t.p1) + '}{' + n._goInner(t.p2) + '}';
            e = '\\mathchoice{\\textstyle' + o + '}{' + o + '}{' + o + '}{' + o + '}';
            break;
          case 'tex-math':
            e = t.p1 + ' ';
            break;
          case 'frac-ce':
            e = '\\frac{' + n._goInner(t.p1) + '}{' + n._goInner(t.p2) + '}';
            break;
          case 'overset':
            e = '\\overset{' + n._goInner(t.p1) + '}{' + n._goInner(t.p2) + '}';
            break;
          case 'underset':
            e = '\\underset{' + n._goInner(t.p1) + '}{' + n._goInner(t.p2) + '}';
            break;
          case 'underbrace':
            e = '\\underbrace{' + n._goInner(t.p1) + '}_{' + n._goInner(t.p2) + '}';
            break;
          case 'color':
            e = '{\\color{' + t.color1 + '}{' + n._goInner(t.color2) + '}}';
            break;
          case 'color0':
            e = '\\color{' + t.color + '}';
            break;
          case 'arrow':
            var i = { rd: n._goInner(t.rd), rq: n._goInner(t.rq) },
              s = n._getArrow(t.r);
            i.rd || i.rq
              ? '<=>' === t.r || '<=>>' === t.r || '<<=>' === t.r || '<--\x3e' === t.r
                ? ((s = '\\long' + s),
                  i.rd && (s = '\\overset{' + i.rd + '}{' + s + '}'),
                  i.rq && (s = '\\underset{\\lower7mu{' + i.rq + '}}{' + s + '}'),
                  (s = ' {}\\mathrel{' + s + '}{} '))
                : (i.rq && (s += '[{' + i.rq + '}]'),
                  (s = ' {}\\mathrel{\\x' + (s += '{' + i.rd + '}') + '}{} '))
              : (s = ' {}\\mathrel{\\long' + s + '}{} '),
              (e = s);
            break;
          case 'operator':
            e = n._getOperator(t.kind_);
            break;
          case '1st-level escape':
            e = t.p1 + ' ';
            break;
          case 'space':
            e = ' ';
            break;
          case 'entitySkip':
          case 'pu-space-1':
            e = '~';
            break;
          case 'pu-space-2':
            e = '\\mkern3mu ';
            break;
          case '1000 separator':
            e = '\\mkern2mu ';
            break;
          case 'commaDecimal':
            e = '{,}';
            break;
          case 'comma enumeration L':
            e = '{' + t.p1 + '}\\mkern6mu ';
            break;
          case 'comma enumeration M':
            e = '{' + t.p1 + '}\\mkern3mu ';
            break;
          case 'comma enumeration S':
            e = '{' + t.p1 + '}\\mkern1mu ';
            break;
          case 'hyphen':
            e = '\\text{-}';
            break;
          case 'addition compound':
            e = '\\,{\\cdot}\\,';
            break;
          case 'electron dot':
            e = '\\mkern1mu \\bullet\\mkern1mu ';
            break;
          case 'KV x':
            e = '{\\times}';
            break;
          case 'prime':
            e = '\\prime ';
            break;
          case 'cdot':
            e = '\\cdot ';
            break;
          case 'tight cdot':
            e = '\\mkern1mu{\\cdot}\\mkern1mu ';
            break;
          case 'times':
            e = '\\times ';
            break;
          case 'circa':
            e = '{\\sim}';
            break;
          case '^':
            e = 'uparrow';
            break;
          case 'v':
            e = 'downarrow';
            break;
          case 'ellipsis':
            e = '\\ldots ';
            break;
          case '/':
            e = '/';
            break;
          case ' / ':
            e = '\\,/\\,';
            break;
          default:
            throw ['MhchemBugT', 'mhchem bug T. Please report.'];
        }
        return e;
      },
      _getArrow: function (t) {
        switch (t) {
          case '->':
          case '\u2192':
          case '\u27f6':
            return 'rightarrow';
          case '<-':
            return 'leftarrow';
          case '<->':
            return 'leftrightarrow';
          case '<--\x3e':
            return 'leftrightarrows';
          case '<=>':
          case '\u21cc':
            return 'rightleftharpoons';
          case '<=>>':
            return 'Rightleftharpoons';
          case '<<=>':
            return 'Leftrightharpoons';
          default:
            throw ['MhchemBugT', 'mhchem bug T. Please report.'];
        }
      },
      _getBond: function (t) {
        switch (t) {
          case '-':
          case '1':
            return '{-}';
          case '=':
          case '2':
            return '{=}';
          case '#':
          case '3':
            return '{\\equiv}';
          case '~':
            return '{\\tripledash}';
          case '~-':
            return '{\\rlap{\\lower.1em{-}}\\raise.1em{\\tripledash}}';
          case '~=':
          case '~--':
            return '{\\rlap{\\lower.2em{-}}\\rlap{\\raise.2em{\\tripledash}}-}';
          case '-~-':
            return '{\\rlap{\\lower.2em{-}}\\rlap{\\raise.2em{-}}\\tripledash}';
          case '...':
            return '{{\\cdot}{\\cdot}{\\cdot}}';
          case '....':
            return '{{\\cdot}{\\cdot}{\\cdot}{\\cdot}}';
          case '->':
            return '{\\rightarrow}';
          case '<-':
            return '{\\leftarrow}';
          case '<':
            return '{<}';
          case '>':
            return '{>}';
          default:
            throw ['MhchemBugT', 'mhchem bug T. Please report.'];
        }
      },
      _getOperator: function (t) {
        switch (t) {
          case '+':
            return ' {}+{} ';
          case '-':
            return ' {}-{} ';
          case '=':
            return ' {}={} ';
          case '<':
            return ' {}<{} ';
          case '>':
            return ' {}>{} ';
          case '<<':
            return ' {}\\ll{} ';
          case '>>':
            return ' {}\\gg{} ';
          case '\\pm':
            return ' {}\\pm{} ';
          case '\\approx':
          case '$\\approx$':
            return ' {}\\approx{} ';
          case 'v':
          case '(v)':
            return ' \\downarrow{} ';
          case '^':
          case '(^)':
            return ' \\uparrow{} ';
          default:
            throw ['MhchemBugT', 'mhchem bug T. Please report.'];
        }
      },
    };
    function o(t) {}
    function i(t) {}
    (e.mhchemParser = a), (e.texify = n), (e.assertNever = o), (e.assertString = i);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.NoErrorsConfiguration = void 0);
    var a = r(0);
    e.NoErrorsConfiguration = a.Configuration.create('noerrors', {
      nodes: {
        error: function (t, e, r, a) {
          var n = t.create('token', 'mtext', {}, a.replace(/\n/g, ' '));
          return t.create('node', 'merror', [n], { 'data-mjx-error': e, title: e });
        },
      },
    });
  },
  function (t, e, r) {
    'use strict';
    var a =
      (this && this.__values) ||
      function (t) {
        var e = 'function' == typeof Symbol && Symbol.iterator,
          r = e && t[e],
          a = 0;
        if (r) return r.call(t);
        if (t && 'number' == typeof t.length)
          return {
            next: function () {
              return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
            },
          };
        throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
      };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.NoUndefinedConfiguration = void 0);
    var n = r(0);
    e.NoUndefinedConfiguration = n.Configuration.create('noundefined', {
      fallback: {
        macro: function (t, e) {
          var r,
            n,
            o = t.create('text', '\\' + e),
            i = t.options.noundefined || {},
            s = {};
          try {
            for (var c = a(['color', 'background', 'size']), l = c.next(); !l.done; l = c.next()) {
              var u = l.value;
              i[u] && (s['math' + u] = i[u]);
            }
          } catch (t) {
            r = { error: t };
          } finally {
            try {
              l && !l.done && (n = c.return) && n.call(c);
            } finally {
              if (r) throw r.error;
            }
          }
          t.Push(t.create('node', 'mtext', [], s, o));
        },
      },
      options: { noundefined: { color: 'red', background: '', size: '' } },
      priority: 3,
    });
  },
  function (t, e, r) {
    'use strict';
    var a;
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.PhysicsConfiguration = void 0);
    var n = r(0),
      o = r(64);
    r(65),
      (e.PhysicsConfiguration = n.Configuration.create('physics', {
        handler: {
          macro: [
            'Physics-automatic-bracing-macros',
            'Physics-vector-macros',
            'Physics-vector-chars',
            'Physics-derivative-macros',
            'Physics-expressions-macros',
            'Physics-quick-quad-macros',
            'Physics-bra-ket-macros',
            'Physics-matrix-macros',
          ],
          character: ['Physics-characters'],
          environment: ['Physics-aux-envs'],
        },
        items: ((a = {}), (a[o.AutoOpen.prototype.kind] = o.AutoOpen), a),
      }));
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.AutoOpen = void 0);
    var o = r(13),
      i = r(3),
      s = r(5),
      c = (function (t) {
        function e() {
          return (null !== t && t.apply(this, arguments)) || this;
        }
        return (
          n(e, t),
          Object.defineProperty(e.prototype, 'kind', {
            get: function () {
              return 'auto open';
            },
            enumerable: !1,
            configurable: !0,
          }),
          Object.defineProperty(e.prototype, 'isOpen', {
            get: function () {
              return !0;
            },
            enumerable: !1,
            configurable: !0,
          }),
          (e.prototype.toMml = function () {
            var e = this.factory.configuration.parser,
              r = this.getProperty('right');
            if (this.getProperty('smash')) {
              var a = t.prototype.toMml.call(this),
                n = e.create('node', 'mpadded', [a], { height: 0, depth: 0 });
              this.Clear(), this.Push(e.create('node', 'TeXAtom', [n]));
            }
            r && this.Push(new s.default(r, e.stack.env, e.configuration).mml());
            var o = t.prototype.toMml.call(this);
            return i.default.fenced(
              this.factory.configuration,
              this.getProperty('open'),
              o,
              this.getProperty('close'),
              this.getProperty('big'),
            );
          }),
          (e.prototype.checkItem = function (e) {
            var r = e.getProperty('autoclose');
            return r && r === this.getProperty('close')
              ? this.getProperty('ignore')
                ? (this.Clear(), [[], !0])
                : [[this.toMml()], !0]
              : t.prototype.checkItem.call(this, e);
          }),
          e
        );
      })(o.BaseItem);
    e.AutoOpen = c;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(1),
      n = r(66),
      o = r(6),
      i = r(9),
      s = r(7);
    new a.CommandMap(
      'Physics-automatic-bracing-macros',
      {
        quantity: 'Quantity',
        qty: 'Quantity',
        pqty: ['Quantity', '(', ')', !0],
        bqty: ['Quantity', '[', ']', !0],
        vqty: ['Quantity', '|', '|', !0],
        Bqty: ['Quantity', '{', '}', !0],
        absolutevalue: ['Quantity', '|', '|', !0],
        abs: ['Quantity', '|', '|', !0],
        norm: ['Quantity', '\\|', '\\|', !0],
        evaluated: 'Eval',
        eval: 'Eval',
        order: ['Quantity', '(', ')', !0, 'O', o.TexConstant.Variant.CALLIGRAPHIC],
        commutator: 'Commutator',
        comm: 'Commutator',
        anticommutator: ['Commutator', '\\{', '\\}'],
        acomm: ['Commutator', '\\{', '\\}'],
        poissonbracket: ['Commutator', '\\{', '\\}'],
        pb: ['Commutator', '\\{', '\\}'],
      },
      n.default,
    ),
      new a.CharacterMap('Physics-vector-chars', i.default.mathchar0mi, {
        dotproduct: ['\u22c5', { mathvariant: o.TexConstant.Variant.BOLD }],
        vdot: ['\u22c5', { mathvariant: o.TexConstant.Variant.BOLD }],
        crossproduct: '\xd7',
        cross: '\xd7',
        cp: '\xd7',
        gradientnabla: ['\u2207', { mathvariant: o.TexConstant.Variant.BOLD }],
        real: ['\u211c', { mathvariant: o.TexConstant.Variant.NORMAL }],
        imaginary: ['\u2111', { mathvariant: o.TexConstant.Variant.NORMAL }],
      }),
      new a.CommandMap(
        'Physics-vector-macros',
        {
          vectorbold: 'VectorBold',
          vb: 'VectorBold',
          vectorarrow: ['StarMacro', 1, '\\vec{\\vb', '{#1}}'],
          va: ['StarMacro', 1, '\\vec{\\vb', '{#1}}'],
          vectorunit: ['StarMacro', 1, '\\hat{\\vb', '{#1}}'],
          vu: ['StarMacro', 1, '\\hat{\\vb', '{#1}}'],
          gradient: ['OperatorApplication', '\\gradientnabla', '(', '['],
          grad: ['OperatorApplication', '\\gradientnabla', '(', '['],
          divergence: ['VectorOperator', '\\gradientnabla\\vdot', '(', '['],
          div: ['VectorOperator', '\\gradientnabla\\vdot', '(', '['],
          curl: ['VectorOperator', '\\gradientnabla\\crossproduct', '(', '['],
          laplacian: ['OperatorApplication', '\\nabla^2', '(', '['],
        },
        n.default,
      ),
      new a.CommandMap(
        'Physics-expressions-macros',
        {
          sin: 'Expression',
          sinh: 'Expression',
          arcsin: 'Expression',
          asin: 'Expression',
          cos: 'Expression',
          cosh: 'Expression',
          arccos: 'Expression',
          acos: 'Expression',
          tan: 'Expression',
          tanh: 'Expression',
          arctan: 'Expression',
          atan: 'Expression',
          csc: 'Expression',
          csch: 'Expression',
          arccsc: 'Expression',
          acsc: 'Expression',
          sec: 'Expression',
          sech: 'Expression',
          arcsec: 'Expression',
          asec: 'Expression',
          cot: 'Expression',
          coth: 'Expression',
          arccot: 'Expression',
          acot: 'Expression',
          exp: ['Expression', !1],
          log: 'Expression',
          ln: 'Expression',
          det: ['Expression', !1],
          Pr: ['Expression', !1],
          tr: ['Expression', !1],
          trace: ['Expression', !1, 'tr'],
          Tr: ['Expression', !1],
          Trace: ['Expression', !1, 'Tr'],
          rank: 'NamedFn',
          erf: ['Expression', !1],
          Res: ['OperatorApplication', '{\\rm Res}', '(', '[', '{'],
          principalvalue: ['OperatorApplication', '{\\cal P}'],
          pv: ['OperatorApplication', '{\\cal P}'],
          PV: ['OperatorApplication', '{\\rm P.V.}'],
          Re: ['OperatorApplication', '{\\rm Re}', '{'],
          Im: ['OperatorApplication', '{\\rm Im}', '{'],
          sine: ['NamedFn', 'sin'],
          hypsine: ['NamedFn', 'sinh'],
          arcsine: ['NamedFn', 'arcsin'],
          asine: ['NamedFn', 'asin'],
          cosine: ['NamedFn', 'cos'],
          hypcosine: ['NamedFn', 'cosh'],
          arccosine: ['NamedFn', 'arccos'],
          acosine: ['NamedFn', 'acos'],
          tangent: ['NamedFn', 'tan'],
          hyptangent: ['NamedFn', 'tanh'],
          arctangent: ['NamedFn', 'arctan'],
          atangent: ['NamedFn', 'atan'],
          cosecant: ['NamedFn', 'csc'],
          hypcosecant: ['NamedFn', 'csch'],
          arccosecant: ['NamedFn', 'arccsc'],
          acosecant: ['NamedFn', 'acsc'],
          secant: ['NamedFn', 'sec'],
          hypsecant: ['NamedFn', 'sech'],
          arcsecant: ['NamedFn', 'arcsec'],
          asecant: ['NamedFn', 'asec'],
          cotangent: ['NamedFn', 'cot'],
          hypcotangent: ['NamedFn', 'coth'],
          arccotangent: ['NamedFn', 'arccot'],
          acotangent: ['NamedFn', 'acot'],
          exponential: ['NamedFn', 'exp'],
          logarithm: ['NamedFn', 'log'],
          naturallogarithm: ['NamedFn', 'ln'],
          determinant: ['NamedFn', 'det'],
          Probability: ['NamedFn', 'Pr'],
        },
        n.default,
      ),
      new a.CommandMap(
        'Physics-quick-quad-macros',
        {
          qqtext: 'Qqtext',
          qq: 'Qqtext',
          qcomma: ['Macro', '\\qqtext*{,}'],
          qc: ['Macro', '\\qqtext*{,}'],
          qcc: ['Qqtext', 'c.c.'],
          qif: ['Qqtext', 'if'],
          qthen: ['Qqtext', 'then'],
          qelse: ['Qqtext', 'else'],
          qotherwise: ['Qqtext', 'otherwise'],
          qunless: ['Qqtext', 'unless'],
          qgiven: ['Qqtext', 'given'],
          qusing: ['Qqtext', 'using'],
          qassume: ['Qqtext', 'assume'],
          'qsince,': ['Qqtext', 'since,'],
          qlet: ['Qqtext', 'let'],
          qfor: ['Qqtext', 'for'],
          qall: ['Qqtext', 'all'],
          qeven: ['Qqtext', 'even'],
          qodd: ['Qqtext', 'odd'],
          qinteger: ['Qqtext', 'integer'],
          qand: ['Qqtext', 'and'],
          qor: ['Qqtext', 'or'],
          qas: ['Qqtext', 'as'],
          qin: ['Qqtext', 'in'],
        },
        n.default,
      ),
      new a.CommandMap(
        'Physics-derivative-macros',
        {
          flatfrac: ['Macro', '\\left.#1\\middle/#2\\right.', 2],
          differential: ['Differential', '{\\rm d}'],
          dd: ['Differential', '{\\rm d}'],
          variation: ['Differential', '\\delta'],
          var: ['Differential', '\\delta'],
          derivative: ['Derivative', 2, '{\\rm d}'],
          dv: ['Derivative', 2, '{\\rm d}'],
          partialderivative: ['Derivative', 3, '\\partial'],
          pderivative: ['Derivative', 3, '\\partial'],
          pdv: ['Derivative', 3, '\\partial'],
          functionalderivative: ['Derivative', 2, '\\delta'],
          fderivative: ['Derivative', 2, '\\delta'],
          fdv: ['Derivative', 2, '\\delta'],
        },
        n.default,
      ),
      new a.CommandMap(
        'Physics-bra-ket-macros',
        {
          bra: 'Bra',
          ket: 'Ket',
          innerproduct: 'BraKet',
          braket: 'BraKet',
          outerproduct: 'KetBra',
          dyad: 'KetBra',
          ketbra: 'KetBra',
          op: 'KetBra',
          expectationvalue: 'Expectation',
          expval: 'Expectation',
          ev: 'Expectation',
          matrixelement: 'MatrixElement',
          matrixel: 'MatrixElement',
          mel: 'MatrixElement',
        },
        n.default,
      ),
      new a.CommandMap(
        'Physics-matrix-macros',
        {
          matrixquantity: 'MatrixQuantity',
          mqty: 'MatrixQuantity',
          pmqty: ['Macro', '\\mqty(#1)', 1],
          Pmqty: ['Macro', '\\mqty*(#1)', 1],
          bmqty: ['Macro', '\\mqty[#1]', 1],
          vmqty: ['Macro', '\\mqty|#1|', 1],
          smallmatrixquantity: ['MatrixQuantity', !0],
          smqty: ['MatrixQuantity', !0],
          spmqty: ['Macro', '\\smqty(#1)', 1],
          sPmqty: ['Macro', '\\smqty*(#1)', 1],
          sbmqty: ['Macro', '\\smqty[#1]', 1],
          svmqty: ['Macro', '\\smqty|#1|', 1],
          matrixdeterminant: ['Macro', '\\vmqty{#1}', 1],
          mdet: ['Macro', '\\vmqty{#1}', 1],
          smdet: ['Macro', '\\svmqty{#1}', 1],
          identitymatrix: 'IdentityMatrix',
          imat: 'IdentityMatrix',
          xmatrix: 'XMatrix',
          xmat: 'XMatrix',
          zeromatrix: ['Macro', '\\xmat{0}{#1}{#2}', 2],
          zmat: ['Macro', '\\xmat{0}{#1}{#2}', 2],
          paulimatrix: 'PauliMatrix',
          pmat: 'PauliMatrix',
          diagonalmatrix: 'DiagonalMatrix',
          dmat: 'DiagonalMatrix',
          antidiagonalmatrix: ['DiagonalMatrix', !0],
          admat: ['DiagonalMatrix', !0],
        },
        n.default,
      ),
      new a.EnvironmentMap(
        'Physics-aux-envs',
        i.default.environment,
        { smallmatrix: ['Array', null, null, null, 'c', '0.333em', '.2em', 'S', 1] },
        n.default,
      ),
      new a.MacroMap(
        'Physics-characters',
        { '|': ['AutoClose', s.TEXCLASS.ORD], ')': 'AutoClose', ']': 'AutoClose' },
        n.default,
      );
  },
  function (t, e, r) {
    'use strict';
    var a =
      (this && this.__read) ||
      function (t, e) {
        var r = 'function' == typeof Symbol && t[Symbol.iterator];
        if (!r) return t;
        var a,
          n,
          o = r.call(t),
          i = [];
        try {
          for (; (void 0 === e || e-- > 0) && !(a = o.next()).done; ) i.push(a.value);
        } catch (t) {
          n = { error: t };
        } finally {
          try {
            a && !a.done && (r = o.return) && r.call(o);
          } finally {
            if (n) throw n.error;
          }
        }
        return i;
      };
    Object.defineProperty(e, '__esModule', { value: !0 });
    var n = r(8),
      o = r(5),
      i = r(2),
      s = r(7),
      c = r(3),
      l = r(4),
      u = r(23),
      d = {},
      p = { '(': ')', '[': ']', '{': '}', '|': '|' },
      f = /^(b|B)i(g{1,2})$/;
    (d.Quantity = function (t, e, r, a, n, u, d) {
      void 0 === r && (r = '('),
        void 0 === a && (a = ')'),
        void 0 === n && (n = !1),
        void 0 === u && (u = ''),
        void 0 === d && (d = '');
      var m = !!n && t.GetStar(),
        h = t.GetNext(),
        g = t.i,
        v = null;
      if ('\\' === h) {
        if ((t.i++, !(v = t.GetCS()).match(f))) {
          var y = t.create('node', 'mrow');
          return t.Push(c.default.fenced(t.configuration, r, y, a)), void (t.i = g);
        }
        h = t.GetNext();
      }
      var x = p[h];
      if (n && '{' !== h)
        throw new i.default('MissingArgFor', 'Missing argument for %1', t.currentCS);
      if (!x) {
        y = t.create('node', 'mrow');
        return t.Push(c.default.fenced(t.configuration, r, y, a)), void (t.i = g);
      }
      if (u) {
        var b = t.create('token', 'mi', { texClass: s.TEXCLASS.OP }, u);
        d && l.default.setAttribute(b, 'mathvariant', d), t.Push(t.itemFactory.create('fn', b));
      }
      if ('{' === h) {
        var _ = t.GetArgument(e);
        return (
          (h = n ? r : '\\{'),
          (x = n ? a : '\\}'),
          (_ = m
            ? h + ' ' + _ + ' ' + x
            : v
            ? '\\' + v + 'l' + h + ' ' + _ + ' \\' + v + 'r' + x
            : '\\left' + h + ' ' + _ + ' \\right' + x),
          void t.Push(new o.default(_, t.stack.env, t.configuration).mml())
        );
      }
      n && ((h = r), (x = a)),
        t.i++,
        t.Push(t.itemFactory.create('auto open').setProperties({ open: h, close: x, big: v }));
    }),
      (d.Eval = function (t, e) {
        var r = t.GetStar(),
          a = t.GetNext();
        if ('{' !== a) {
          if ('(' === a || '[' === a)
            return (
              t.i++,
              void t.Push(
                t.itemFactory
                  .create('auto open')
                  .setProperties({ open: a, close: '|', smash: r, right: '\\vphantom{\\int}' }),
              )
            );
          throw new i.default('MissingArgFor', 'Missing argument for %1', t.currentCS);
        }
        var n = t.GetArgument(e),
          o = '\\left. ' + (r ? '\\smash{' + n + '}' : n) + ' \\vphantom{\\int}\\right|';
        t.string = t.string.slice(0, t.i) + o + t.string.slice(t.i);
      }),
      (d.Commutator = function (t, e, r, a) {
        void 0 === r && (r = '['), void 0 === a && (a = ']');
        var n = t.GetStar(),
          s = t.GetNext(),
          c = null;
        if ('\\' === s) {
          if ((t.i++, !(c = t.GetCS()).match(f)))
            throw new i.default('MissingArgFor', 'Missing argument for %1', t.currentCS);
          s = t.GetNext();
        }
        if ('{' !== s) throw new i.default('MissingArgFor', 'Missing argument for %1', t.currentCS);
        var l = t.GetArgument(e) + ',' + t.GetArgument(e);
        (l = n
          ? r + ' ' + l + ' ' + a
          : c
          ? '\\' + c + 'l' + r + ' ' + l + ' \\' + c + 'r' + a
          : '\\left' + r + ' ' + l + ' \\right' + a),
          t.Push(new o.default(l, t.stack.env, t.configuration).mml());
      });
    var m = [65, 90],
      h = [97, 122],
      g = [913, 937],
      v = [945, 969],
      y = [48, 57];
    function x(t, e) {
      return t >= e[0] && t <= e[1];
    }
    function b(t, e, r, a) {
      var n = t.configuration.parser,
        o = u.NodeFactory.createToken(t, e, r, a),
        i = a.codePointAt(0);
      return (
        1 === a.length &&
          !n.stack.env.font &&
          n.stack.env.vectorFont &&
          (x(i, m) ||
            x(i, h) ||
            x(i, g) ||
            x(i, y) ||
            (x(i, v) && n.stack.env.vectorStar) ||
            l.default.getAttribute(o, 'accent')) &&
          l.default.setAttribute(o, 'mathvariant', n.stack.env.vectorFont),
        o
      );
    }
    (d.VectorBold = function (t, e) {
      var r = t.GetStar(),
        a = t.GetArgument(e),
        n = t.configuration.nodeFactory.get('token'),
        i = t.stack.env.font;
      delete t.stack.env.font,
        t.configuration.nodeFactory.set('token', b),
        (t.stack.env.vectorFont = r ? 'bold-italic' : 'bold'),
        (t.stack.env.vectorStar = r);
      var s = new o.default(a, t.stack.env, t.configuration).mml();
      i && (t.stack.env.font = i),
        delete t.stack.env.vectorFont,
        delete t.stack.env.vectorStar,
        t.configuration.nodeFactory.set('token', n),
        t.Push(s);
    }),
      (d.StarMacro = function (t, e, r) {
        for (var a = [], n = 3; n < arguments.length; n++) a[n - 3] = arguments[n];
        var o = t.GetStar(),
          s = [];
        if (r) for (var l = s.length; l < r; l++) s.push(t.GetArgument(e));
        var u = a.join(o ? '*' : '');
        if (
          ((u = c.default.substituteArgs(t, s, u)),
          (t.string = c.default.addArgs(t, u, t.string.slice(t.i))),
          (t.i = 0),
          ++t.macroCount > t.configuration.options.maxMacros)
        )
          throw new i.default(
            'MaxMacroSub1',
            'MathJax maximum macro substitution count exceeded; is there a recursive macro call?',
          );
      });
    var _ = function (t, e, r, a, n) {
      var i = new o.default(a, t.stack.env, t.configuration).mml();
      t.Push(t.itemFactory.create(e, i));
      var s = t.GetNext(),
        c = p[s];
      if (c) {
        var l = -1 !== n.indexOf(s);
        if ('{' === s) {
          var u = (l ? '\\left\\{' : '') + ' ' + t.GetArgument(r) + ' ' + (l ? '\\right\\}' : '');
          return (t.string = u + t.string.slice(t.i)), void (t.i = 0);
        }
        l &&
          (t.i++, t.Push(t.itemFactory.create('auto open').setProperties({ open: s, close: c })));
      }
    };
    function M(t, e, r) {
      var n = a(t, 3),
        o = n[0],
        i = n[1],
        s = n[2];
      return e && r
        ? '\\left\\langle{' +
            o +
            '}\\middle\\vert{' +
            i +
            '}\\middle\\vert{' +
            s +
            '}\\right\\rangle'
        : e
        ? '\\langle{' + o + '}\\vert{' + i + '}\\vert{' + s + '}\\rangle'
        : '\\left\\langle{' + o + '}\\right\\vert{' + i + '}\\left\\vert{' + s + '}\\right\\rangle';
    }
    (d.OperatorApplication = function (t, e, r) {
      for (var a = [], n = 3; n < arguments.length; n++) a[n - 3] = arguments[n];
      _(t, 'fn', e, r, a);
    }),
      (d.VectorOperator = function (t, e, r) {
        for (var a = [], n = 3; n < arguments.length; n++) a[n - 3] = arguments[n];
        _(t, 'mml', e, r, a);
      }),
      (d.Expression = function (t, e, r, a) {
        void 0 === r && (r = !0), void 0 === a && (a = ''), (a = a || e.slice(1));
        var n = r ? t.GetBrackets(e) : null,
          i = t.create('token', 'mi', { texClass: s.TEXCLASS.OP }, a);
        if (n) {
          var c = new o.default(n, t.stack.env, t.configuration).mml();
          i = t.create('node', 'msup', [i, c]);
        }
        t.Push(t.itemFactory.create('fn', i)),
          '(' === t.GetNext() &&
            (t.i++,
            t.Push(t.itemFactory.create('auto open').setProperties({ open: '(', close: ')' })));
      }),
      (d.Qqtext = function (t, e, r) {
        var a = (t.GetStar() ? '' : '\\quad') + '\\text{' + (r || t.GetArgument(e)) + '}\\quad ';
        t.string = t.string.slice(0, t.i) + a + t.string.slice(t.i);
      }),
      (d.Differential = function (t, e, r) {
        var a = t.GetBrackets(e),
          n = null != a ? '^{' + a + '}' : ' ',
          i = '(' === t.GetNext(),
          c = '{' === t.GetNext(),
          l = r + n;
        if (i || c)
          if (c) {
            l += t.GetArgument(e);
            u = new o.default(l, t.stack.env, t.configuration).mml();
            t.Push(t.create('node', 'TeXAtom', [u], { texClass: s.TEXCLASS.OP }));
          } else
            t.Push(new o.default(l, t.stack.env, t.configuration).mml()),
              t.i++,
              t.Push(t.itemFactory.create('auto open').setProperties({ open: '(', close: ')' }));
        else {
          l += t.GetArgument(e, !0) || '';
          var u = new o.default(l, t.stack.env, t.configuration).mml();
          t.Push(u);
        }
      }),
      (d.Derivative = function (t, e, r, a) {
        var n = t.GetStar(),
          i = t.GetBrackets(e),
          s = 1,
          c = [];
        for (c.push(t.GetArgument(e)); '{' === t.GetNext() && s < r; )
          c.push(t.GetArgument(e)), s++;
        var l = !1,
          u = ' ',
          d = ' ';
        r > 2 && c.length > 2
          ? ((u = '^{' + (c.length - 1) + '}'), (l = !0))
          : null != i && (r > 2 && c.length > 1 && (l = !0), (d = u = '^{' + i + '}'));
        for (
          var p = n ? '\\flatfrac' : '\\frac',
            f = c.length > 1 ? c[0] : '',
            m = c.length > 1 ? c[1] : c[0],
            h = '',
            g = 2,
            v = void 0;
          (v = c[g]);
          g++
        )
          h += a + ' ' + v;
        var y = p + '{' + a + u + f + '}{' + a + ' ' + m + d + ' ' + h + '}';
        t.Push(new o.default(y, t.stack.env, t.configuration).mml()),
          '(' === t.GetNext() &&
            (t.i++,
            t.Push(
              t.itemFactory.create('auto open').setProperties({ open: '(', close: ')', ignore: l }),
            ));
      }),
      (d.Bra = function (t, e) {
        var r = t.GetStar(),
          a = t.GetArgument(e),
          n = '',
          i = !1,
          s = !1;
        if ('\\' === t.GetNext()) {
          var c = t.i;
          t.i++;
          var l = t.GetCS(),
            u = t.lookup('macro', l);
          u && 'ket' === u.symbol
            ? ((i = !0),
              (c = t.i),
              (s = t.GetStar()),
              '{' === t.GetNext() ? (n = t.GetArgument(l, !0)) : ((t.i = c), (s = !1)))
            : (t.i = c);
        }
        var d = '';
        (d = i
          ? r || s
            ? '\\langle{' + a + '}\\vert{' + n + '}\\rangle'
            : '\\left\\langle{' + a + '}\\middle\\vert{' + n + '}\\right\\rangle'
          : r || s
          ? '\\langle{' + a + '}\\vert'
          : '\\left\\langle{' + a + '}\\right\\vert{' + n + '}'),
          t.Push(new o.default(d, t.stack.env, t.configuration).mml());
      }),
      (d.Ket = function (t, e) {
        var r = t.GetStar(),
          a = t.GetArgument(e),
          n = r ? '\\vert{' + a + '}\\rangle' : '\\left\\vert{' + a + '}\\right\\rangle';
        t.Push(new o.default(n, t.stack.env, t.configuration).mml());
      }),
      (d.BraKet = function (t, e) {
        var r = t.GetStar(),
          a = t.GetArgument(e),
          n = null;
        '{' === t.GetNext() && (n = t.GetArgument(e, !0));
        var i = '';
        (i =
          null == n
            ? r
              ? '\\langle{' + a + '}\\vert{' + a + '}\\rangle'
              : '\\left\\langle{' + a + '}\\middle\\vert{' + a + '}\\right\\rangle'
            : r
            ? '\\langle{' + a + '}\\vert{' + n + '}\\rangle'
            : '\\left\\langle{' + a + '}\\middle\\vert{' + n + '}\\right\\rangle'),
          t.Push(new o.default(i, t.stack.env, t.configuration).mml());
      }),
      (d.KetBra = function (t, e) {
        var r = t.GetStar(),
          a = t.GetArgument(e),
          n = null;
        '{' === t.GetNext() && (n = t.GetArgument(e, !0));
        var i = '';
        (i =
          null == n
            ? r
              ? '\\vert{' + a + '}\\rangle\\!\\langle{' + a + '}\\vert'
              : '\\left\\vert{' + a + '}\\middle\\rangle\\!\\middle\\langle{' + a + '}\\right\\vert'
            : r
            ? '\\vert{' + a + '}\\rangle\\!\\langle{' + n + '}\\vert'
            : '\\left\\vert{' + a + '}\\middle\\rangle\\!\\middle\\langle{' + n + '}\\right\\vert'),
          t.Push(new o.default(i, t.stack.env, t.configuration).mml());
      }),
      (d.Expectation = function (t, e) {
        var r = t.GetStar(),
          a = r && t.GetStar(),
          n = t.GetArgument(e),
          i = null;
        '{' === t.GetNext() && (i = t.GetArgument(e, !0));
        var s =
          n && i
            ? M([i, n, i], r, a)
            : r
            ? '\\langle {' + n + '} \\rangle'
            : '\\left\\langle {' + n + '} \\right\\rangle';
        t.Push(new o.default(s, t.stack.env, t.configuration).mml());
      }),
      (d.MatrixElement = function (t, e) {
        var r = t.GetStar(),
          a = r && t.GetStar(),
          n = M([t.GetArgument(e), t.GetArgument(e), t.GetArgument(e)], r, a);
        t.Push(new o.default(n, t.stack.env, t.configuration).mml());
      }),
      (d.MatrixQuantity = function (t, e, r) {
        var a = t.GetStar(),
          n = r ? 'smallmatrix' : 'array',
          i = '',
          s = '',
          c = '';
        switch (t.GetNext()) {
          case '{':
            i = t.GetArgument(e);
            break;
          case '(':
            t.i++, (s = a ? '\\lgroup' : '('), (c = a ? '\\rgroup' : ')'), (i = t.GetUpTo(e, ')'));
            break;
          case '[':
            t.i++, (s = '['), (c = ']'), (i = t.GetUpTo(e, ']'));
            break;
          case '|':
            t.i++, (s = '|'), (c = '|'), (i = t.GetUpTo(e, '|'));
            break;
          default:
            (s = '('), (c = ')');
        }
        var l =
          (s ? '\\left' : '') +
          s +
          '\\begin{' +
          n +
          '}{} ' +
          i +
          '\\end{' +
          n +
          '}' +
          (s ? '\\right' : '') +
          c;
        t.Push(new o.default(l, t.stack.env, t.configuration).mml());
      }),
      (d.IdentityMatrix = function (t, e) {
        var r = t.GetArgument(e),
          a = parseInt(r, 10);
        if (isNaN(a)) throw new i.default('InvalidNumber', 'Invalid number');
        if (a <= 1) return (t.string = '1' + t.string.slice(t.i)), void (t.i = 0);
        for (var n = Array(a).fill('0'), o = [], s = 0; s < a; s++) {
          var c = n.slice();
          (c[s] = '1'), o.push(c.join(' & '));
        }
        (t.string = o.join('\\\\ ') + t.string.slice(t.i)), (t.i = 0);
      }),
      (d.XMatrix = function (t, e) {
        var r = t.GetStar(),
          a = t.GetArgument(e),
          n = t.GetArgument(e),
          o = t.GetArgument(e),
          s = parseInt(n, 10),
          c = parseInt(o, 10);
        if (isNaN(s) || isNaN(c) || c.toString() !== o || s.toString() !== n)
          throw new i.default('InvalidNumber', 'Invalid number');
        if (((s = s < 1 ? 1 : s), (c = c < 1 ? 1 : c), !r)) {
          var l = Array(c).fill(a).join(' & '),
            u = Array(s).fill(l).join('\\\\ ');
          return (t.string = u + t.string.slice(t.i)), void (t.i = 0);
        }
        var d = '';
        if (1 === s && 1 === c) d = a;
        else if (1 === s) {
          l = [];
          for (var p = 1; p <= c; p++) l.push(a + '_{' + p + '}');
          d = l.join(' & ');
        } else if (1 === c) {
          for (l = [], p = 1; p <= s; p++) l.push(a + '_{' + p + '}');
          d = l.join('\\\\ ');
        } else {
          var f = [];
          for (p = 1; p <= s; p++) {
            l = [];
            for (var m = 1; m <= c; m++) l.push(a + '_{{' + p + '}{' + m + '}}');
            f.push(l.join(' & '));
          }
          d = f.join('\\\\ ');
        }
        (t.string = d + t.string.slice(t.i)), (t.i = 0);
      }),
      (d.PauliMatrix = function (t, e) {
        var r = t.GetArgument(e),
          a = r.slice(1);
        switch (r[0]) {
          case '0':
            a += ' 1 & 0\\\\ 0 & 1';
            break;
          case '1':
          case 'x':
            a += ' 0 & 1\\\\ 1 & 0';
            break;
          case '2':
          case 'y':
            a += ' 0 & -i\\\\ i & 0';
            break;
          case '3':
          case 'z':
            a += ' 1 & 0\\\\ 0 & -1';
        }
        (t.string = a + t.string.slice(t.i)), (t.i = 0);
      }),
      (d.DiagonalMatrix = function (t, e, r) {
        if ('{' === t.GetNext()) {
          var a = t.i;
          t.GetArgument(e);
          var n = t.i;
          t.i = a + 1;
          for (var o = [], i = '', s = t.i; s < n; ) {
            try {
              i = t.GetUpTo(e, ',');
            } catch (e) {
              (t.i = n), o.push(t.string.slice(s, n - 1));
              break;
            }
            if (t.i >= n) {
              o.push(t.string.slice(s, n));
              break;
            }
            (s = t.i), o.push(i);
          }
          (t.string =
            (function (t, e) {
              for (var r = t.length, a = [], n = 0; n < r; n++)
                a.push(Array(e ? r - n : n + 1).join('&') + '\\mqty{' + t[n] + '}');
              return a.join('\\\\ ');
            })(o, r) + t.string.slice(n)),
            (t.i = 0);
        }
      }),
      (d.AutoClose = function (t, e, r) {
        var a = t.create('token', 'mo', { stretchy: !1 }, e),
          n = t.itemFactory.create('mml', a).setProperties({ autoclose: e });
        t.Push(n);
      }),
      (d.Macro = n.default.Macro),
      (d.NamedFn = n.default.NamedFn),
      (d.Array = n.default.Array),
      (e.default = d);
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.TagFormatConfiguration = e.tagformatConfig = void 0);
    var o = r(0),
      i = r(17),
      s = 0;
    function c(t, e) {
      var r = e.parseOptions.options.tags;
      'base' !== r && t.tags.hasOwnProperty(r) && i.TagsFactory.add(r, t.tags[r]);
      var a = (function (t) {
          function r() {
            return (null !== t && t.apply(this, arguments)) || this;
          }
          return (
            n(r, t),
            (r.prototype.formatNumber = function (t) {
              return e.parseOptions.options.tagformat.number(t);
            }),
            (r.prototype.formatTag = function (t) {
              return e.parseOptions.options.tagformat.tag(t);
            }),
            (r.prototype.formatId = function (t) {
              return e.parseOptions.options.tagformat.id(t);
            }),
            (r.prototype.formatUrl = function (t, r) {
              return e.parseOptions.options.tagformat.url(t, r);
            }),
            r
          );
        })(i.TagsFactory.create(e.parseOptions.options.tags).constructor),
        o = 'configTags-' + ++s;
      i.TagsFactory.add(o, a), (e.parseOptions.options.tags = o);
    }
    (e.tagformatConfig = c),
      (e.TagFormatConfiguration = o.Configuration.create('tagformat', {
        config: [c, 10],
        options: {
          tagformat: {
            number: function (t) {
              return t.toString();
            },
            tag: function (t) {
              return '(' + t + ')';
            },
            id: function (t) {
              return 'mjx-eqn-' + t.replace(/\s/g, '_');
            },
            url: function (t, e) {
              return e + '#' + encodeURIComponent(t);
            },
          },
        },
      }));
  },
  function (t, e, r) {
    'use strict';
    var a;
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.textBase = void 0);
    var n = r(0),
      o = r(69),
      i = r(17),
      s = r(16),
      c = r(70),
      l = r(27);
    function u(t, e, r, a) {
      var n = t.configuration.packageData.get('textmacros');
      return (
        t instanceof c.TextParser || (n.texParser = t),
        [new c.TextParser(e, a ? { mathvariant: a } : {}, n.parseOptions, r).mml()]
      );
    }
    r(72),
      (e.textBase = n.Configuration.local({
        handler: { character: ['command', 'text-special'], macro: ['text-macros'] },
        fallback: {
          character: function (t, e) {
            t.text += e;
          },
          macro: function (t, e) {
            var r = t.texParser,
              a = r.lookup('macro', e);
            a &&
              a._func !== l.TextMacrosMethods.Macro &&
              t.Error('MathMacro', '%1 is only supported in math mode', '\\' + e),
              r.parse('macro', [a ? t : r, e]);
          },
        },
        items:
          ((a = {}),
          (a[s.StartItem.prototype.kind] = s.StartItem),
          (a[s.StopItem.prototype.kind] = s.StopItem),
          (a[s.MmlItem.prototype.kind] = s.MmlItem),
          (a[s.StyleItem.prototype.kind] = s.StyleItem),
          a),
      })),
      n.Configuration.create('textmacros', {
        config: function (t, r) {
          var a = new n.ParserConfiguration([]);
          a.append(e.textBase), a.init();
          var s = new o.default(a, []);
          (s.options = r.parseOptions.options),
            a.config(r),
            i.TagsFactory.addTags(a.tags),
            (s.tags = i.TagsFactory.getDefault()),
            (s.tags.configuration = s),
            (s.packageData = r.parseOptions.packageData),
            s.packageData.set('textmacros', { parseOptions: s, jax: r, texParser: null }),
            (s.options.internalMath = u);
        },
        preprocessors: [
          function (t) {
            var e = t.data.packageData.get('textmacros');
            e.parseOptions.nodeFactory.setMmlFactory(e.jax.mmlFactory);
          },
        ],
      });
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.default = MathJax._.input.tex.ParseOptions.default);
  },
  function (t, e, r) {
    'use strict';
    var a,
      n =
        (this && this.__extends) ||
        ((a = function (t, e) {
          return (a =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var r in e) e.hasOwnProperty(r) && (t[r] = e[r]);
            })(t, e);
        }),
        function (t, e) {
          function r() {
            this.constructor = t;
          }
          a(t, e),
            (t.prototype = null === e ? Object.create(e) : ((r.prototype = e.prototype), new r()));
        }),
      o =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            r = e && t[e],
            a = 0;
          if (r) return r.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && a >= t.length && (t = void 0), { value: t && t[a++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        },
      i =
        (this && this.__read) ||
        function (t, e) {
          var r = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!r) return t;
          var a,
            n,
            o = r.call(t),
            i = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(a = o.next()).done; ) i.push(a.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              a && !a.done && (r = o.return) && r.call(o);
            } finally {
              if (n) throw n.error;
            }
          }
          return i;
        },
      s =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(i(arguments[e]));
          return t;
        };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.TextParser = void 0);
    var c = r(5),
      l = r(2),
      u = r(3),
      d = r(7),
      p = r(4),
      f = r(16),
      m = (function (t) {
        function e(e, r, a, n) {
          var o = t.call(this, e, r, a) || this;
          return (o.level = n), o;
        }
        return (
          n(e, t),
          Object.defineProperty(e.prototype, 'texParser', {
            get: function () {
              return this.configuration.packageData.get('textmacros').texParser;
            },
            enumerable: !1,
            configurable: !0,
          }),
          Object.defineProperty(e.prototype, 'tags', {
            get: function () {
              return this.texParser.tags;
            },
            enumerable: !1,
            configurable: !0,
          }),
          (e.prototype.mml = function () {
            return null != this.level
              ? this.create('node', 'mstyle', this.nodes, {
                  displaystyle: !1,
                  scriptlevel: this.level,
                })
              : 1 === this.nodes.length
              ? this.nodes[0]
              : this.create('node', 'inferredMrow', this.nodes);
          }),
          (e.prototype.Parse = function () {
            (this.text = ''), (this.nodes = []), (this.envStack = []), t.prototype.Parse.call(this);
          }),
          (e.prototype.saveText = function () {
            if (this.text) {
              var t = this.stack.env.mathvariant,
                e = u.default.internalText(this, this.text, t ? { mathvariant: t } : {});
              (this.text = ''), this.Push(e);
            }
          }),
          (e.prototype.Push = function (e) {
            if ((this.text && this.saveText(), e instanceof f.StopItem))
              return t.prototype.Push.call(this, e);
            e instanceof f.StyleItem
              ? (this.stack.env.mathcolor = this.stack.env.color)
              : e instanceof d.AbstractMmlNode && (this.addAttributes(e), this.nodes.push(e));
          }),
          (e.prototype.PushMath = function (t) {
            var e,
              r,
              a = this.stack.env;
            try {
              for (var n = o(['mathsize', 'mathcolor']), i = n.next(); !i.done; i = n.next()) {
                var s = i.value;
                a[s] &&
                  !t.attributes.getExplicit(s) &&
                  (t.isToken || t.isKind('mstyle') || (t = this.create('node', 'mstyle', [t])),
                  p.default.setAttribute(t, s, a[s]));
              }
            } catch (t) {
              e = { error: t };
            } finally {
              try {
                i && !i.done && (r = n.return) && r.call(n);
              } finally {
                if (e) throw e.error;
              }
            }
            t.isKind('inferredMrow') && (t = this.create('node', 'mrow', t.childNodes)),
              this.nodes.push(t);
          }),
          (e.prototype.addAttributes = function (t) {
            var e,
              r,
              a = this.stack.env;
            if (t.isToken)
              try {
                for (
                  var n = o(['mathsize', 'mathcolor', 'mathvariant']), i = n.next();
                  !i.done;
                  i = n.next()
                ) {
                  var s = i.value;
                  a[s] && !t.attributes.getExplicit(s) && p.default.setAttribute(t, s, a[s]);
                }
              } catch (t) {
                e = { error: t };
              } finally {
                try {
                  i && !i.done && (r = n.return) && r.call(n);
                } finally {
                  if (e) throw e.error;
                }
              }
          }),
          (e.prototype.ParseTextArg = function (t, r) {
            return new e(
              this.GetArgument(t),
              (r = Object.assign(Object.assign({}, this.stack.env), r)),
              this.configuration,
            ).mml();
          }),
          (e.prototype.ParseArg = function (t) {
            return new e(this.GetArgument(t), this.stack.env, this.configuration).mml();
          }),
          (e.prototype.Error = function (t, e) {
            for (var r = [], a = 2; a < arguments.length; a++) r[a - 2] = arguments[a];
            throw new (l.default.bind.apply(l.default, s([void 0, t, e], r)))();
          }),
          e
        );
      })(c.default);
    e.TextParser = m;
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.handleRetriesFor = MathJax._.util.Retries.handleRetriesFor),
      (e.retryAfter = MathJax._.util.Retries.retryAfter);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 });
    var a = r(1),
      n = r(6),
      o = r(27);
    new a.MacroMap(
      'text-special',
      {
        $: 'Math',
        '%': 'Comment',
        '^': 'MathModeOnly',
        _: 'MathModeOnly',
        '&': 'Misplaced',
        '#': 'Misplaced',
        '~': 'Tilde',
        ' ': 'Space',
        '\t': 'Space',
        '\r': 'Space',
        '\n': 'Space',
        '\xa0': 'Tilde',
        '{': 'OpenBrace',
        '}': 'CloseBrace',
        '`': 'OpenQuote',
        "'": 'CloseQuote',
      },
      o.TextMacrosMethods,
    ),
      new a.CommandMap(
        'text-macros',
        {
          '(': 'Math',
          $: 'SelfQuote',
          _: 'SelfQuote',
          '%': 'SelfQuote',
          '{': 'SelfQuote',
          '}': 'SelfQuote',
          ' ': 'SelfQuote',
          '&': 'SelfQuote',
          '#': 'SelfQuote',
          '\\': 'SelfQuote',
          "'": ['Accent', '\xb4'],
          '\u2019': ['Accent', '\xb4'],
          '`': ['Accent', '`'],
          '\u2018': ['Accent', '`'],
          '^': ['Accent', '^'],
          '"': ['Accent', '\xa8'],
          '~': ['Accent', '~'],
          '=': ['Accent', '\xaf'],
          '.': ['Accent', '\u02d9'],
          u: ['Accent', '\u02d8'],
          v: ['Accent', '\u02c7'],
          emph: 'Emph',
          rm: ['SetFont', n.TexConstant.Variant.NORMAL],
          mit: ['SetFont', n.TexConstant.Variant.ITALIC],
          oldstyle: ['SetFont', n.TexConstant.Variant.OLDSTYLE],
          cal: ['SetFont', n.TexConstant.Variant.CALLIGRAPHIC],
          it: ['SetFont', '-tex-mathit'],
          bf: ['SetFont', n.TexConstant.Variant.BOLD],
          bbFont: ['SetFont', n.TexConstant.Variant.DOUBLESTRUCK],
          scr: ['SetFont', n.TexConstant.Variant.SCRIPT],
          frak: ['SetFont', n.TexConstant.Variant.FRAKTUR],
          sf: ['SetFont', n.TexConstant.Variant.SANSSERIF],
          tt: ['SetFont', n.TexConstant.Variant.MONOSPACE],
          tiny: ['SetSize', 0.5],
          Tiny: ['SetSize', 0.6],
          scriptsize: ['SetSize', 0.7],
          small: ['SetSize', 0.85],
          normalsize: ['SetSize', 1],
          large: ['SetSize', 1.2],
          Large: ['SetSize', 1.44],
          LARGE: ['SetSize', 1.73],
          huge: ['SetSize', 2.07],
          Huge: ['SetSize', 2.49],
          mathcal: 'MathModeOnly',
          mathscr: 'MathModeOnly',
          mathrm: 'MathModeOnly',
          mathbf: 'MathModeOnly',
          mathbb: 'MathModeOnly',
          mathit: 'MathModeOnly',
          mathfrak: 'MathModeOnly',
          mathsf: 'MathModeOnly',
          mathtt: 'MathModeOnly',
          Bbb: ['Macro', '{\\bbFont #1}', 1],
          textrm: ['Macro', '{\\rm #1}', 1],
          textit: ['Macro', '{\\it #1}', 1],
          textbf: ['Macro', '{\\bf #1}', 1],
          textsf: ['Macro', '{\\sf #1}', 1],
          texttt: ['Macro', '{\\tt #1}', 1],
          dagger: ['Insert', '\u2020'],
          ddagger: ['Insert', '\u2021'],
          S: ['Insert', '\xa7'],
          ',': ['Spacer', n.TexConstant.Length.THINMATHSPACE],
          ':': ['Spacer', n.TexConstant.Length.MEDIUMMATHSPACE],
          '>': ['Spacer', n.TexConstant.Length.MEDIUMMATHSPACE],
          ';': ['Spacer', n.TexConstant.Length.THICKMATHSPACE],
          '!': ['Spacer', n.TexConstant.Length.NEGATIVETHINMATHSPACE],
          enspace: ['Spacer', '.5em'],
          quad: ['Spacer', '1em'],
          qquad: ['Spacer', '2em'],
          thinspace: ['Spacer', n.TexConstant.Length.THINMATHSPACE],
          negthinspace: ['Spacer', n.TexConstant.Length.NEGATIVETHINMATHSPACE],
          hskip: 'Hskip',
          hspace: 'Hskip',
          kern: 'Hskip',
          mskip: 'Hskip',
          mspace: 'Hskip',
          mkern: 'Hskip',
          rule: 'rule',
          Rule: ['Rule'],
          Space: ['Rule', 'blank'],
          color: 'CheckAutoload',
          textcolor: 'CheckAutoload',
          colorbox: 'CheckAutoload',
          fcolorbox: 'CheckAutoload',
          href: 'CheckAutoload',
          style: 'CheckAutoload',
          class: 'CheckAutoload',
          cssId: 'CheckAutoload',
          unicode: 'CheckAutoload',
          ref: ['HandleRef', !1],
          eqref: ['HandleRef', !0],
        },
        o.TextMacrosMethods,
      );
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.UnicodeConfiguration = e.UnicodeMethods = void 0);
    var a = r(0),
      n = r(2),
      o = r(1),
      i = r(3),
      s = r(4),
      c = r(74);
    e.UnicodeMethods = {};
    var l = {};
    (e.UnicodeMethods.Unicode = function (t, e) {
      var r = t.GetBrackets(e),
        a = null,
        o = null;
      r &&
        (r.replace(/ /g, '').match(/^(\d+(\.\d*)?|\.\d+),(\d+(\.\d*)?|\.\d+)$/)
          ? ((a = r.replace(/ /g, '').split(/,/)), (o = t.GetBrackets(e)))
          : (o = r));
      var u = i.default.trimSpaces(t.GetArgument(e)).replace(/^0x/, 'x');
      if (!u.match(/^(x[0-9A-Fa-f]+|[0-9]+)$/))
        throw new n.default('BadUnicode', 'Argument to \\unicode must be a number');
      var d = parseInt(u.match(/^x/) ? '0' + u : u);
      l[d] ? o || (o = l[d][2]) : (l[d] = [800, 200, o, d]),
        a &&
          ((l[d][0] = Math.floor(1e3 * parseFloat(a[0]))),
          (l[d][1] = Math.floor(1e3 * parseFloat(a[1]))));
      var p = t.stack.env.font,
        f = {};
      o
        ? ((l[d][2] = f.fontfamily = o.replace(/'/g, "'")),
          p &&
            (p.match(/bold/) && (f.fontweight = 'bold'),
            p.match(/italic|-mathit/) && (f.fontstyle = 'italic')))
        : p && (f.mathvariant = p);
      var m = t.create('token', 'mtext', f, c.numeric(u));
      s.default.setProperty(m, 'unicode', !0), t.Push(m);
    }),
      new o.CommandMap('unicode', { unicode: 'Unicode' }, e.UnicodeMethods),
      (e.UnicodeConfiguration = a.Configuration.create('unicode', {
        handler: { macro: ['unicode'] },
      }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.options = MathJax._.util.Entities.options),
      (e.entities = MathJax._.util.Entities.entities),
      (e.add = MathJax._.util.Entities.add),
      (e.remove = MathJax._.util.Entities.remove),
      (e.translate = MathJax._.util.Entities.translate),
      (e.numeric = MathJax._.util.Entities.numeric);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.VerbConfiguration = e.VerbMethods = void 0);
    var a = r(0),
      n = r(6),
      o = r(1),
      i = r(2);
    (e.VerbMethods = {}),
      (e.VerbMethods.Verb = function (t, e) {
        var r = t.GetNext(),
          a = ++t.i;
        if ('' === r) throw new i.default('MissingArgFor', 'Missing argument for %1', e);
        for (; t.i < t.string.length && t.string.charAt(t.i) !== r; ) t.i++;
        if (t.i === t.string.length)
          throw new i.default('NoClosingDelim', "Can't find closing delimiter for %1", t.currentCS);
        var o = t.string.slice(a, t.i).replace(/ /g, '\xa0');
        t.i++,
          t.Push(t.create('token', 'mtext', { mathvariant: n.TexConstant.Variant.MONOSPACE }, o));
      }),
      new o.CommandMap('verb', { verb: 'Verb' }, e.VerbMethods),
      (e.VerbConfiguration = a.Configuration.create('verb', { handler: { macro: ['verb'] } }));
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Loader = MathJax._.components.loader.Loader),
      (e.MathJax = MathJax._.components.loader.MathJax),
      (e.CONFIG = MathJax._.components.loader.CONFIG);
  },
  function (t, e, r) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.mathjax = MathJax._.mathjax.mathjax);
  },
  function (t, e, r) {
    'use strict';
    r.r(e);
    var a = r(20),
      n = r(15),
      o = r(21),
      i = r(14);
    Object(a.combineWithMathJax)({
      _: {
        input: {
          tex: {
            AllPackages: n,
            autoload: { AutoloadConfiguration: o },
            require: { RequireConfiguration: i },
          },
        },
      },
    });
    var s,
      c = r(10);
    function l(t, e) {
      (null == e || e > t.length) && (e = t.length);
      for (var r = 0, a = new Array(e); r < e; r++) a[r] = t[r];
      return a;
    }
    if (
      (MathJax.loader && MathJax.loader.preLoad('[tex]/autoload', '[tex]/require'), MathJax.startup)
    ) {
      MathJax.config.tex || (MathJax.config.tex = {});
      var u = MathJax.config.tex.packages;
      (MathJax.config.tex.packages = ['autoload', 'require'].concat(
        (function (t) {
          if (Array.isArray(t)) return l(t);
        })((s = n.AllPackages)) ||
          (function (t) {
            if ('undefined' != typeof Symbol && Symbol.iterator in Object(t)) return Array.from(t);
          })(s) ||
          (function (t, e) {
            if (t) {
              if ('string' == typeof t) return l(t, e);
              var r = Object.prototype.toString.call(t).slice(8, -1);
              return (
                'Object' === r && t.constructor && (r = t.constructor.name),
                'Map' === r || 'Set' === r
                  ? Array.from(t)
                  : 'Arguments' === r || /^(?:Ui|I)nt(?:8|16|32)(?:Clamped)?Array$/.test(r)
                  ? l(t, e)
                  : void 0
              );
            }
          })(s) ||
          (function () {
            throw new TypeError(
              'Invalid attempt to spread non-iterable instance.\nIn order to be iterable, non-array objects must have a [Symbol.iterator]() method.',
            );
          })(),
      )),
        u && Object(c.insert)(MathJax.config.tex, { packages: u });
    }
  },
]);
