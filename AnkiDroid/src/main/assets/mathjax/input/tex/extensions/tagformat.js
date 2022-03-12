!(function (t) {
  var n = {};
  function o(e) {
    if (n[e]) return n[e].exports;
    var a = (n[e] = { i: e, l: !1, exports: {} });
    return t[e].call(a.exports, a, a.exports, o), (a.l = !0), a.exports;
  }
  (o.m = t),
    (o.c = n),
    (o.d = function (t, n, e) {
      o.o(t, n) || Object.defineProperty(t, n, { enumerable: !0, get: e });
    }),
    (o.r = function (t) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(t, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(t, '__esModule', { value: !0 });
    }),
    (o.t = function (t, n) {
      if ((1 & n && (t = o(t)), 8 & n)) return t;
      if (4 & n && 'object' == typeof t && t && t.__esModule) return t;
      var e = Object.create(null);
      if (
        (o.r(e),
        Object.defineProperty(e, 'default', { enumerable: !0, value: t }),
        2 & n && 'string' != typeof t)
      )
        for (var a in t)
          o.d(
            e,
            a,
            function (n) {
              return t[n];
            }.bind(null, a),
          );
      return e;
    }),
    (o.n = function (t) {
      var n =
        t && t.__esModule
          ? function () {
              return t.default;
            }
          : function () {
              return t;
            };
      return o.d(n, 'a', n), n;
    }),
    (o.o = function (t, n) {
      return Object.prototype.hasOwnProperty.call(t, n);
    }),
    (o.p = ''),
    o((o.s = 4));
})([
  function (t, n, o) {
    'use strict';
    Object.defineProperty(n, '__esModule', { value: !0 }),
      (n.isObject = MathJax._.components.global.isObject),
      (n.combineConfig = MathJax._.components.global.combineConfig),
      (n.combineDefaults = MathJax._.components.global.combineDefaults),
      (n.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (n.MathJax = MathJax._.components.global.MathJax);
  },
  function (t, n, o) {
    'use strict';
    var e,
      a =
        (this && this.__extends) ||
        ((e = function (t, n) {
          return (e =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, n) {
                t.__proto__ = n;
              }) ||
            function (t, n) {
              for (var o in n) n.hasOwnProperty(o) && (t[o] = n[o]);
            })(t, n);
        }),
        function (t, n) {
          function o() {
            this.constructor = t;
          }
          e(t, n),
            (t.prototype = null === n ? Object.create(n) : ((o.prototype = n.prototype), new o()));
        });
    Object.defineProperty(n, '__esModule', { value: !0 }),
      (n.TagFormatConfiguration = n.tagformatConfig = void 0);
    var r = o(2),
      i = o(3),
      u = 0;
    function s(t, n) {
      var o = n.parseOptions.options.tags;
      'base' !== o && t.tags.hasOwnProperty(o) && i.TagsFactory.add(o, t.tags[o]);
      var e = (function (t) {
          function o() {
            return (null !== t && t.apply(this, arguments)) || this;
          }
          return (
            a(o, t),
            (o.prototype.formatNumber = function (t) {
              return n.parseOptions.options.tagformat.number(t);
            }),
            (o.prototype.formatTag = function (t) {
              return n.parseOptions.options.tagformat.tag(t);
            }),
            (o.prototype.formatId = function (t) {
              return n.parseOptions.options.tagformat.id(t);
            }),
            (o.prototype.formatUrl = function (t, o) {
              return n.parseOptions.options.tagformat.url(t, o);
            }),
            o
          );
        })(i.TagsFactory.create(n.parseOptions.options.tags).constructor),
        r = 'configTags-' + ++u;
      i.TagsFactory.add(r, e), (n.parseOptions.options.tags = r);
    }
    (n.tagformatConfig = s),
      (n.TagFormatConfiguration = r.Configuration.create('tagformat', {
        config: [s, 10],
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
            url: function (t, n) {
              return n + '#' + encodeURIComponent(t);
            },
          },
        },
      }));
  },
  function (t, n, o) {
    'use strict';
    Object.defineProperty(n, '__esModule', { value: !0 }),
      (n.Configuration = MathJax._.input.tex.Configuration.Configuration),
      (n.ConfigurationHandler = MathJax._.input.tex.Configuration.ConfigurationHandler),
      (n.ParserConfiguration = MathJax._.input.tex.Configuration.ParserConfiguration);
  },
  function (t, n, o) {
    'use strict';
    Object.defineProperty(n, '__esModule', { value: !0 }),
      (n.Label = MathJax._.input.tex.Tags.Label),
      (n.TagInfo = MathJax._.input.tex.Tags.TagInfo),
      (n.AbstractTags = MathJax._.input.tex.Tags.AbstractTags),
      (n.NoTags = MathJax._.input.tex.Tags.NoTags),
      (n.AllTags = MathJax._.input.tex.Tags.AllTags),
      (n.TagsFactory = MathJax._.input.tex.Tags.TagsFactory);
  },
  function (t, n, o) {
    'use strict';
    o.r(n);
    var e = o(0),
      a = o(1);
    Object(e.combineWithMathJax)({
      _: { input: { tex: { tagformat: { TagFormatConfiguration: a } } } },
    }),
      (function (t, n, o) {
        var a,
          r,
          i,
          u = MathJax.config.tex;
        if (u && u.packages) {
          var s = u.packages,
            f = s.indexOf(t);
          f >= 0 && (s[f] = n),
            o &&
              u[t] &&
              (Object(e.combineConfig)(
                u,
                ((a = {}),
                (r = n),
                (i = u[t]),
                r in a
                  ? Object.defineProperty(a, r, {
                      value: i,
                      enumerable: !0,
                      configurable: !0,
                      writable: !0,
                    })
                  : (a[r] = i),
                a),
              ),
              delete u[t]);
        }
      })('tagFormat', 'tagformat', !0);
  },
]);
