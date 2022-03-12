!(function (t) {
  var e = {};
  function n(r) {
    if (e[r]) return e[r].exports;
    var i = (e[r] = { i: r, l: !1, exports: {} });
    return t[r].call(i.exports, i, i.exports, n), (i.l = !0), i.exports;
  }
  (n.m = t),
    (n.c = e),
    (n.d = function (t, e, r) {
      n.o(t, e) || Object.defineProperty(t, e, { enumerable: !0, get: r });
    }),
    (n.r = function (t) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(t, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(t, '__esModule', { value: !0 });
    }),
    (n.t = function (t, e) {
      if ((1 & e && (t = n(t)), 8 & e)) return t;
      if (4 & e && 'object' == typeof t && t && t.__esModule) return t;
      var r = Object.create(null);
      if (
        (n.r(r),
        Object.defineProperty(r, 'default', { enumerable: !0, value: t }),
        2 & e && 'string' != typeof t)
      )
        for (var i in t)
          n.d(
            r,
            i,
            function (e) {
              return t[e];
            }.bind(null, i),
          );
      return r;
    }),
    (n.n = function (t) {
      var e =
        t && t.__esModule
          ? function () {
              return t.default;
            }
          : function () {
              return t;
            };
      return n.d(e, 'a', e), e;
    }),
    (n.o = function (t, e) {
      return Object.prototype.hasOwnProperty.call(t, e);
    }),
    (n.p = ''),
    n((n.s = 9));
})([
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.sreReady = void 0);
    var r = n(8),
      i =
        'undefined' == typeof window
          ? './a11y/sre-node.js'
          : '../../../speech-rule-engine/lib/sre_browser.js',
      a = 'undefined' == typeof sre ? r.asyncLoad(i) : Promise.resolve();
    e.sreReady = function () {
      return new Promise(function (t, e) {
        a.then(function () {
          var n = new Date().getTime(),
            r = function () {
              sre.Engine.isReady()
                ? t()
                : new Date().getTime() - n < 2e4
                ? setTimeout(r, 100)
                : e('Timed out waiting for Speech-Rule-Engine');
            };
          r();
        }).catch(function (t) {
          return e(t.message || t);
        });
      });
    };
  },
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.isObject = MathJax._.components.global.isObject),
      (e.combineConfig = MathJax._.components.global.combineConfig),
      (e.combineDefaults = MathJax._.components.global.combineDefaults),
      (e.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (e.MathJax = MathJax._.components.global.MathJax);
  },
  function (t, e, n) {
    'use strict';
    var r,
      i =
        (this && this.__extends) ||
        ((r = function (t, e) {
          return (r =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var n in e) e.hasOwnProperty(n) && (t[n] = e[n]);
            })(t, e);
        }),
        function (t, e) {
          function n() {
            this.constructor = t;
          }
          r(t, e),
            (t.prototype = null === e ? Object.create(e) : ((n.prototype = e.prototype), new n()));
        }),
      a =
        (this && this.__assign) ||
        function () {
          return (a =
            Object.assign ||
            function (t) {
              for (var e, n = 1, r = arguments.length; n < r; n++)
                for (var i in (e = arguments[n]))
                  Object.prototype.hasOwnProperty.call(e, i) && (t[i] = e[i]);
              return t;
            }).apply(this, arguments);
        },
      o =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            n = e && t[e],
            r = 0;
          if (n) return n.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && r >= t.length && (t = void 0), { value: t && t[r++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        },
      s =
        (this && this.__read) ||
        function (t, e) {
          var n = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!n) return t;
          var r,
            i,
            a = n.call(t),
            o = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(r = a.next()).done; ) o.push(r.value);
          } catch (t) {
            i = { error: t };
          } finally {
            try {
              r && !r.done && (n = a.return) && n.call(a);
            } finally {
              if (i) throw i.error;
            }
          }
          return o;
        },
      c =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(s(arguments[e]));
          return t;
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.EnrichHandler = e.EnrichedMathDocumentMixin = e.EnrichedMathItemMixin = void 0);
    var l = n(4),
      u = n(5),
      h = n(6),
      p = n(7),
      f = n(0),
      d = 'none';
    function y(t, e, n) {
      return (function (t) {
        function r() {
          return (null !== t && t.apply(this, arguments)) || this;
        }
        return (
          i(r, t),
          (r.prototype.serializeMml = function (t) {
            if ('outerHTML' in t) return t.outerHTML;
            if (
              'undefined' != typeof Element &&
              'undefined' != typeof window &&
              t instanceof Element
            ) {
              var e = window.document.createElement('div');
              return e.appendChild(t), e.innerHTML;
            }
            return t.toString();
          }),
          (r.prototype.enrich = function (t, r) {
            if ((void 0 === r && (r = !1), !(this.state() >= u.STATE.ENRICHED))) {
              if (!this.isEscaped && (t.options.enableEnrichment || r)) {
                ('undefined' != typeof sre && sre.Engine.isReady()) ||
                  l.mathjax.retryAfter(f.sreReady()),
                  t.options.enrichSpeech !== d &&
                    (SRE.setupEngine({ speech: t.options.enrichSpeech }),
                    (d = t.options.enrichSpeech));
                var i = new t.options.MathItem('', e);
                (i.math = this.serializeMml(SRE.toEnriched(n(this.root)))),
                  (i.display = this.display),
                  i.compile(t),
                  (this.root = i.root),
                  (this.inputData.originalMml = i.math);
              }
              this.state(u.STATE.ENRICHED);
            }
          }),
          (r.prototype.attachSpeech = function (t) {
            var e, n;
            if (!(this.state() >= u.STATE.ATTACHSPEECH)) {
              var r = this.root.attributes.get('aria-label') || this.getSpeech(this.root);
              if (r) {
                var i = t.adaptor,
                  a = this.typesetRoot;
                i.setAttribute(a, 'aria-label', r);
                try {
                  for (var s = o(i.childNodes(a)), c = s.next(); !c.done; c = s.next()) {
                    var l = c.value;
                    i.setAttribute(l, 'aria-hidden', 'true');
                  }
                } catch (t) {
                  e = { error: t };
                } finally {
                  try {
                    c && !c.done && (n = s.return) && n.call(s);
                  } finally {
                    if (e) throw e.error;
                  }
                }
              }
              this.state(u.STATE.ATTACHSPEECH);
            }
          }),
          (r.prototype.getSpeech = function (t) {
            var e,
              n,
              r = t.attributes;
            if (!r) return '';
            var i = r.getExplicit('data-semantic-speech');
            if (!r.getExplicit('data-semantic-parent') && i) return i;
            try {
              for (var a = o(t.childNodes), s = a.next(); !s.done; s = a.next()) {
                var c = s.value,
                  l = this.getSpeech(c);
                if (null != l) return l;
              }
            } catch (t) {
              e = { error: t };
            } finally {
              try {
                s && !s.done && (n = a.return) && n.call(a);
              } finally {
                if (e) throw e.error;
              }
            }
            return '';
          }),
          r
        );
      })(t);
    }
    function M(t, e) {
      var n;
      return (
        ((n = (function (t) {
          function n() {
            for (var n = [], r = 0; r < arguments.length; r++) n[r] = arguments[r];
            var i = t.apply(this, c(n)) || this;
            e.setMmlFactory(i.mmlFactory);
            var a = i.constructor.ProcessBits;
            a.has('enriched') || (a.allocate('enriched'), a.allocate('attach-speech'));
            var o = new h.SerializedMmlVisitor(i.mmlFactory),
              s = function (t) {
                return o.visitTree(t);
              };
            return (i.options.MathItem = y(i.options.MathItem, e, s)), i;
          }
          return (
            i(n, t),
            (n.prototype.attachSpeech = function () {
              var t, e;
              if (!this.processed.isSet('attach-speech')) {
                try {
                  for (var n = o(this.math), r = n.next(); !r.done; r = n.next()) {
                    r.value.attachSpeech(this);
                  }
                } catch (e) {
                  t = { error: e };
                } finally {
                  try {
                    r && !r.done && (e = n.return) && e.call(n);
                  } finally {
                    if (t) throw t.error;
                  }
                }
                this.processed.set('attach-speech');
              }
              return this;
            }),
            (n.prototype.enrich = function () {
              var t, e;
              if (!this.processed.isSet('enriched')) {
                if (this.options.enableEnrichment)
                  try {
                    for (var n = o(this.math), r = n.next(); !r.done; r = n.next()) {
                      r.value.enrich(this);
                    }
                  } catch (e) {
                    t = { error: e };
                  } finally {
                    try {
                      r && !r.done && (e = n.return) && e.call(n);
                    } finally {
                      if (t) throw t.error;
                    }
                  }
                this.processed.set('enriched');
              }
              return this;
            }),
            (n.prototype.state = function (e, n) {
              return (
                void 0 === n && (n = !1),
                t.prototype.state.call(this, e, n),
                e < u.STATE.ENRICHED && this.processed.clear('enriched'),
                this
              );
            }),
            n
          );
        })(t)).OPTIONS = a(a({}, t.OPTIONS), {
          enableEnrichment: !0,
          enrichSpeech: 'none',
          renderActions: p.expandable(
            a(a({}, t.OPTIONS.renderActions), {
              enrich: [u.STATE.ENRICHED],
              attachSpeech: [u.STATE.ATTACHSPEECH],
            }),
          ),
        })),
        n
      );
    }
    u.newState('ENRICHED', 30),
      u.newState('ATTACHSPEECH', 155),
      (e.EnrichedMathItemMixin = y),
      (e.EnrichedMathDocumentMixin = M),
      (e.EnrichHandler = function (t, e) {
        return e.setAdaptor(t.adaptor), (t.documentClass = M(t.documentClass, e)), t;
      });
  },
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.MathML = MathJax._.input.mathml_ts.MathML);
  },
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.mathjax = MathJax._.mathjax.mathjax);
  },
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.protoItem = MathJax._.core.MathItem.protoItem),
      (e.AbstractMathItem = MathJax._.core.MathItem.AbstractMathItem),
      (e.STATE = MathJax._.core.MathItem.STATE),
      (e.newState = MathJax._.core.MathItem.newState);
  },
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.DATAMJX = MathJax._.core.MmlTree.SerializedMmlVisitor.DATAMJX),
      (e.toEntity = MathJax._.core.MmlTree.SerializedMmlVisitor.toEntity),
      (e.SerializedMmlVisitor = MathJax._.core.MmlTree.SerializedMmlVisitor.SerializedMmlVisitor);
  },
  function (t, e, n) {
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
  function (t, e, n) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.asyncLoad = MathJax._.util.AsyncLoad.asyncLoad);
  },
  function (t, e, n) {
    'use strict';
    n.r(e);
    var r = n(1),
      i = n(2),
      a = n(0);
    Object(r.combineWithMathJax)({ _: { a11y: { 'semantic-enrich': i, sre: a } } });
    var o = n(3);
    MathJax.loader &&
      Object(r.combineDefaults)(MathJax.config.loader, 'a11y/semantic-enrich', {
        checkReady: function () {
          return Object(a.sreReady)();
        },
      }),
      MathJax.startup &&
        MathJax.startup.extendHandler(function (t) {
          return Object(i.EnrichHandler)(t, new o.MathML());
        });
  },
]);
