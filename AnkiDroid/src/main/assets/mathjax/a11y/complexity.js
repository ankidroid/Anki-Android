!(function (t) {
  var e = {};
  function i(o) {
    if (e[o]) return e[o].exports;
    var r = (e[o] = { i: o, l: !1, exports: {} });
    return t[o].call(r.exports, r, r.exports, i), (r.l = !0), r.exports;
  }
  (i.m = t),
    (i.c = e),
    (i.d = function (t, e, o) {
      i.o(t, e) || Object.defineProperty(t, e, { enumerable: !0, get: o });
    }),
    (i.r = function (t) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(t, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(t, '__esModule', { value: !0 });
    }),
    (i.t = function (t, e) {
      if ((1 & e && (t = i(t)), 8 & e)) return t;
      if (4 & e && 'object' == typeof t && t && t.__esModule) return t;
      var o = Object.create(null);
      if (
        (i.r(o),
        Object.defineProperty(o, 'default', { enumerable: !0, value: t }),
        2 & e && 'string' != typeof t)
      )
        for (var r in t)
          i.d(
            o,
            r,
            function (e) {
              return t[e];
            }.bind(null, r),
          );
      return o;
    }),
    (i.n = function (t) {
      var e =
        t && t.__esModule
          ? function () {
              return t.default;
            }
          : function () {
              return t;
            };
      return i.d(e, 'a', e), e;
    }),
    (i.o = function (t, e) {
      return Object.prototype.hasOwnProperty.call(t, e);
    }),
    (i.p = ''),
    i((i.s = 9));
})([
  function (t, e, i) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.isObject = MathJax._.components.global.isObject),
      (e.combineConfig = MathJax._.components.global.combineConfig),
      (e.combineDefaults = MathJax._.components.global.combineDefaults),
      (e.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (e.MathJax = MathJax._.components.global.MathJax);
  },
  function (t, e, i) {
    'use strict';
    var o,
      r =
        (this && this.__extends) ||
        ((o = function (t, e) {
          return (o =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var i in e) e.hasOwnProperty(i) && (t[i] = e[i]);
            })(t, e);
        }),
        function (t, e) {
          function i() {
            this.constructor = t;
          }
          o(t, e),
            (t.prototype = null === e ? Object.create(e) : ((i.prototype = e.prototype), new i()));
        }),
      n =
        (this && this.__assign) ||
        function () {
          return (n =
            Object.assign ||
            function (t) {
              for (var e, i = 1, o = arguments.length; i < o; i++)
                for (var r in (e = arguments[i]))
                  Object.prototype.hasOwnProperty.call(e, r) && (t[r] = e[r]);
              return t;
            }).apply(this, arguments);
        },
      s =
        (this && this.__read) ||
        function (t, e) {
          var i = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!i) return t;
          var o,
            r,
            n = i.call(t),
            s = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(o = n.next()).done; ) s.push(o.value);
          } catch (t) {
            r = { error: t };
          } finally {
            try {
              o && !o.done && (i = n.return) && i.call(n);
            } finally {
              if (r) throw r.error;
            }
          }
          return s;
        },
      l =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(s(arguments[e]));
          return t;
        },
      a =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            i = e && t[e],
            o = 0;
          if (i) return i.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && o >= t.length && (t = void 0), { value: t && t[o++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.ComplexityHandler = e.ComplexityMathDocumentMixin = e.ComplexityMathItemMixin = void 0);
    var c = i(7),
      p = i(2),
      u = i(3),
      h = i(5);
    function y(t, e) {
      return (function (t) {
        function i() {
          return (null !== t && t.apply(this, arguments)) || this;
        }
        return (
          r(i, t),
          (i.prototype.complexity = function (t, i) {
            void 0 === i && (i = !1),
              this.state() >= c.STATE.COMPLEXITY ||
                (this.isEscaped ||
                  (!t.options.enableComplexity && !i) ||
                  (this.enrich(t, !0), e(this.root)),
                this.state(c.STATE.COMPLEXITY));
          }),
          i
        );
      })(t);
    }
    function d(t) {
      var e;
      return (
        ((e = (function (t) {
          function e() {
            for (var e = [], i = 0; i < arguments.length; i++) e[i] = arguments[i];
            var o = t.apply(this, l(e)) || this,
              r = o.constructor.ProcessBits;
            r.has('complexity') || r.allocate('complexity');
            var n = h.selectOptionsFromKeys(o.options, o.options.ComplexityVisitor.OPTIONS);
            o.complexityVisitor = new o.options.ComplexityVisitor(o.mmlFactory, n);
            var s = function (t) {
              return o.complexityVisitor.visitTree(t);
            };
            return (o.options.MathItem = y(o.options.MathItem, s)), o;
          }
          return (
            r(e, t),
            (e.prototype.complexity = function () {
              var t, e;
              if (!this.processed.isSet('complexity')) {
                if (this.options.enableComplexity)
                  try {
                    for (var i = a(this.math), o = i.next(); !o.done; o = i.next()) {
                      o.value.complexity(this);
                    }
                  } catch (e) {
                    t = { error: e };
                  } finally {
                    try {
                      o && !o.done && (e = i.return) && e.call(i);
                    } finally {
                      if (t) throw t.error;
                    }
                  }
                this.processed.set('complexity');
              }
              return this;
            }),
            (e.prototype.state = function (e, i) {
              return (
                void 0 === i && (i = !1),
                t.prototype.state.call(this, e, i),
                e < c.STATE.COMPLEXITY && this.processed.clear('complexity'),
                this
              );
            }),
            e
          );
        })(t)).OPTIONS = n(n(n({}, t.OPTIONS), u.ComplexityVisitor.OPTIONS), {
          enableComplexity: !0,
          ComplexityVisitor: u.ComplexityVisitor,
          renderActions: h.expandable(
            n(n({}, t.OPTIONS.renderActions), { complexity: [c.STATE.COMPLEXITY] }),
          ),
        })),
        e
      );
    }
    c.newState('COMPLEXITY', 40),
      (e.ComplexityMathItemMixin = y),
      (e.ComplexityMathDocumentMixin = d),
      (e.ComplexityHandler = function (t, e) {
        return (
          void 0 === e && (e = null),
          !t.documentClass.prototype.enrich && e && (t = p.EnrichHandler(t, e)),
          (t.documentClass = d(t.documentClass)),
          t
        );
      });
  },
  function (t, e, i) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.EnrichedMathItemMixin = MathJax._.a11y['semantic-enrich'].EnrichedMathItemMixin),
      (e.EnrichedMathDocumentMixin = MathJax._.a11y['semantic-enrich'].EnrichedMathDocumentMixin),
      (e.EnrichHandler = MathJax._.a11y['semantic-enrich'].EnrichHandler);
  },
  function (t, e, i) {
    'use strict';
    var o,
      r =
        (this && this.__extends) ||
        ((o = function (t, e) {
          return (o =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var i in e) e.hasOwnProperty(i) && (t[i] = e[i]);
            })(t, e);
        }),
        function (t, e) {
          function i() {
            this.constructor = t;
          }
          o(t, e),
            (t.prototype = null === e ? Object.create(e) : ((i.prototype = e.prototype), new i()));
        }),
      n =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            i = e && t[e],
            o = 0;
          if (i) return i.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && o >= t.length && (t = void 0), { value: t && t[o++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.ComplexityVisitor = void 0);
    var s = i(8),
      l = i(4),
      a = i(5),
      c = (function (t) {
        function e(e, i) {
          var o = t.call(this, e) || this;
          o.complexity = {
            text: 0.5,
            token: 0.5,
            child: 1,
            script: 0.8,
            sqrt: 2,
            subsup: 2,
            underover: 2,
            fraction: 2,
            enclose: 2,
            action: 2,
            phantom: 0,
            xml: 2,
            glyph: 2,
          };
          var r = o.constructor;
          return (
            (o.options = a.userOptions(a.defaultOptions({}, r.OPTIONS), i)),
            (o.collapse = new o.options.Collapse(o)),
            (o.factory = e),
            o
          );
        }
        return (
          r(e, t),
          (e.prototype.visitTree = function (e) {
            t.prototype.visitTree.call(this, e, !0),
              this.options.makeCollapsible && this.collapse.makeCollapse(e);
          }),
          (e.prototype.visitNode = function (e, i) {
            if (!e.attributes.get('data-semantic-complexity'))
              return t.prototype.visitNode.call(this, e, i);
          }),
          (e.prototype.visitDefault = function (t, e) {
            var i;
            if (t.isToken) {
              var o = t.getText();
              i = this.complexity.text * o.length + this.complexity.token;
            } else i = this.childrenComplexity(t);
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMfracNode = function (t, e) {
            var i = this.childrenComplexity(t) * this.complexity.script + this.complexity.fraction;
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMsqrtNode = function (t, e) {
            var i = this.childrenComplexity(t) + this.complexity.sqrt;
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMrootNode = function (t, e) {
            var i =
              this.childrenComplexity(t) +
              this.complexity.sqrt -
              (1 - this.complexity.script) * this.getComplexity(t.childNodes[1]);
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMphantomNode = function (t, e) {
            return this.setComplexity(t, this.complexity.phantom, e);
          }),
          (e.prototype.visitMsNode = function (t, e) {
            var i =
              (t.attributes.get('lquote') + t.getText() + t.attributes.get('rquote')).length *
              this.complexity.text;
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMsubsupNode = function (e, i) {
            t.prototype.visitDefault.call(this, e, !0);
            var o = e.childNodes[e.sub],
              r = e.childNodes[e.sup],
              n = e.childNodes[e.base],
              s =
                Math.max(o ? this.getComplexity(o) : 0, r ? this.getComplexity(r) : 0) *
                this.complexity.script;
            return (
              (s += this.complexity.child * ((o ? 1 : 0) + (r ? 1 : 0))),
              (s += n ? this.getComplexity(n) + this.complexity.child : 0),
              (s += this.complexity.subsup),
              this.setComplexity(e, s, i)
            );
          }),
          (e.prototype.visitMsubNode = function (t, e) {
            return this.visitMsubsupNode(t, e);
          }),
          (e.prototype.visitMsupNode = function (t, e) {
            return this.visitMsubsupNode(t, e);
          }),
          (e.prototype.visitMunderoverNode = function (e, i) {
            t.prototype.visitDefault.call(this, e, !0);
            var o = e.childNodes[e.under],
              r = e.childNodes[e.over],
              n = e.childNodes[e.base],
              s =
                Math.max(o ? this.getComplexity(o) : 0, r ? this.getComplexity(r) : 0) *
                this.complexity.script;
            return (
              n && (s = Math.max(this.getComplexity(n), s)),
              (s += this.complexity.child * ((o ? 1 : 0) + (r ? 1 : 0) + (n ? 1 : 0))),
              (s += this.complexity.underover),
              this.setComplexity(e, s, i)
            );
          }),
          (e.prototype.visitMunderNode = function (t, e) {
            return this.visitMunderoverNode(t, e);
          }),
          (e.prototype.visitMoverNode = function (t, e) {
            return this.visitMunderoverNode(t, e);
          }),
          (e.prototype.visitMencloseNode = function (t, e) {
            var i = this.childrenComplexity(t) + this.complexity.enclose;
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMactionNode = function (t, e) {
            this.childrenComplexity(t);
            var i = this.getComplexity(t.selected);
            return this.setComplexity(t, i, e);
          }),
          (e.prototype.visitMsemanticsNode = function (t, e) {
            var i = t.childNodes[0],
              o = 0;
            return (
              i && (this.visitNode(i, !0), (o = this.getComplexity(i))), this.setComplexity(t, o, e)
            );
          }),
          (e.prototype.visitAnnotationNode = function (t, e) {
            return this.setComplexity(t, this.complexity.xml, e);
          }),
          (e.prototype.visitAnnotation_xmlNode = function (t, e) {
            return this.setComplexity(t, this.complexity.xml, e);
          }),
          (e.prototype.visitMglyphNode = function (t, e) {
            return this.setComplexity(t, this.complexity.glyph, e);
          }),
          (e.prototype.getComplexity = function (t) {
            var e = t.getProperty('collapsedComplexity');
            return null != e ? e : t.attributes.get('data-semantic-complexity');
          }),
          (e.prototype.setComplexity = function (t, e, i) {
            return (
              i &&
                (this.options.identifyCollapsible && (e = this.collapse.check(t, e)),
                t.attributes.set('data-semantic-complexity', e)),
              e
            );
          }),
          (e.prototype.childrenComplexity = function (e) {
            var i, o;
            t.prototype.visitDefault.call(this, e, !0);
            var r = 0;
            try {
              for (var s = n(e.childNodes), l = s.next(); !l.done; l = s.next()) {
                var a = l.value;
                r += this.getComplexity(a);
              }
            } catch (t) {
              i = { error: t };
            } finally {
              try {
                l && !l.done && (o = s.return) && o.call(s);
              } finally {
                if (i) throw i.error;
              }
            }
            return e.childNodes.length > 1 && (r += e.childNodes.length * this.complexity.child), r;
          }),
          (e.OPTIONS = { identifyCollapsible: !0, makeCollapsible: !0, Collapse: l.Collapse }),
          e
        );
      })(s.MmlVisitor);
    e.ComplexityVisitor = c;
  },
  function (t, e, i) {
    'use strict';
    var o =
      (this && this.__values) ||
      function (t) {
        var e = 'function' == typeof Symbol && Symbol.iterator,
          i = e && t[e],
          o = 0;
        if (i) return i.call(t);
        if (t && 'number' == typeof t.length)
          return {
            next: function () {
              return t && o >= t.length && (t = void 0), { value: t && t[o++], done: !t };
            },
          };
        throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
      };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.Collapse = void 0);
    var r = (function () {
      function t(e) {
        var i = this;
        (this.cutoff = {
          identifier: 3,
          number: 3,
          text: 10,
          infixop: 15,
          relseq: 15,
          multirel: 15,
          fenced: 18,
          bigop: 20,
          integral: 20,
          fraction: 12,
          sqrt: 9,
          root: 12,
          vector: 15,
          matrix: 15,
          cases: 15,
          superscript: 9,
          subscript: 9,
          subsup: 9,
          punctuated: { endpunct: t.NOCOLLAPSE, startpunct: t.NOCOLLAPSE, value: 12 },
        }),
          (this.marker = {
            identifier: 'x',
            number: '#',
            text: '...',
            appl: { 'limit function': 'lim', value: 'f()' },
            fraction: '/',
            sqrt: '\u221a',
            root: '\u221a',
            superscript: '\u25fd\u02d9',
            subscript: '\u25fd.',
            subsup: '\u25fd:',
            vector: { binomial: '(:)', determinant: '|:|', value: '\u27e8:\u27e9' },
            matrix: {
              squarematrix: '[::]',
              rowvector: '\u27e8\u22ef\u27e9',
              columnvector: '\u27e8\u22ee\u27e9',
              determinant: '|::|',
              value: '(::)',
            },
            cases: '{:',
            infixop: {
              addition: '+',
              subtraction: '\u2212',
              multiplication: '\u22c5',
              implicit: '\u22c5',
              value: '+',
            },
            punctuated: { text: '...', value: ',' },
          }),
          (this.collapse = new Map([
            [
              'fenced',
              function (t, e) {
                return (
                  (e = i.uncollapseChild(e, t, 1)) > i.cutoff.fenced &&
                    'leftright' === t.attributes.get('data-semantic-role') &&
                    (e = i.recordCollapse(
                      t,
                      e,
                      i.getText(t.childNodes[0]) + i.getText(t.childNodes[t.childNodes.length - 1]),
                    )),
                  e
                );
              },
            ],
            [
              'appl',
              function (t, e) {
                if (i.canUncollapse(t, 2, 2)) {
                  e = i.complexity.visitNode(t, !1);
                  var o = i.marker.appl,
                    r = o[t.attributes.get('data-semantic-role')] || o.value;
                  e = i.recordCollapse(t, e, r);
                }
                return e;
              },
            ],
            [
              'sqrt',
              function (t, e) {
                return (
                  (e = i.uncollapseChild(e, t, 0)) > i.cutoff.sqrt &&
                    (e = i.recordCollapse(t, e, i.marker.sqrt)),
                  e
                );
              },
            ],
            [
              'root',
              function (t, e) {
                return (
                  (e = i.uncollapseChild(e, t, 0, 2)) > i.cutoff.sqrt &&
                    (e = i.recordCollapse(t, e, i.marker.sqrt)),
                  e
                );
              },
            ],
            [
              'enclose',
              function (t, e) {
                if (1 === i.splitAttribute(t, 'children').length) {
                  var o = i.canUncollapse(t, 1);
                  if (o) {
                    var r = o.getProperty('collapse-marker');
                    i.unrecordCollapse(o),
                      (e = i.recordCollapse(t, i.complexity.visitNode(t, !1), r));
                  }
                }
                return e;
              },
            ],
            [
              'bigop',
              function (t, e) {
                if (e > i.cutoff.bigop || !t.isKind('mo')) {
                  var o = i.splitAttribute(t, 'content').pop(),
                    r = i.findChildText(t, o);
                  e = i.recordCollapse(t, e, r);
                }
                return e;
              },
            ],
            [
              'integral',
              function (t, e) {
                if (e > i.cutoff.integral || !t.isKind('mo')) {
                  var o = i.splitAttribute(t, 'content').pop(),
                    r = i.findChildText(t, o);
                  e = i.recordCollapse(t, e, r);
                }
                return e;
              },
            ],
            [
              'relseq',
              function (t, e) {
                if (e > i.cutoff.relseq) {
                  var o = i.splitAttribute(t, 'content')[0],
                    r = i.findChildText(t, o);
                  e = i.recordCollapse(t, e, r);
                }
                return e;
              },
            ],
            [
              'multirel',
              function (t, e) {
                if (e > i.cutoff.relseq) {
                  var o = i.splitAttribute(t, 'content')[0],
                    r = i.findChildText(t, o) + '\u22ef';
                  e = i.recordCollapse(t, e, r);
                }
                return e;
              },
            ],
            [
              'superscript',
              function (t, e) {
                return (
                  (e = i.uncollapseChild(e, t, 0, 2)) > i.cutoff.superscript &&
                    (e = i.recordCollapse(t, e, i.marker.superscript)),
                  e
                );
              },
            ],
            [
              'subscript',
              function (t, e) {
                return (
                  (e = i.uncollapseChild(e, t, 0, 2)) > i.cutoff.subscript &&
                    (e = i.recordCollapse(t, e, i.marker.subscript)),
                  e
                );
              },
            ],
            [
              'subsup',
              function (t, e) {
                return (
                  (e = i.uncollapseChild(e, t, 0, 3)) > i.cutoff.subsup &&
                    (e = i.recordCollapse(t, e, i.marker.subsup)),
                  e
                );
              },
            ],
          ])),
          (this.idCount = 0),
          (this.complexity = e);
      }
      return (
        (t.prototype.check = function (t, e) {
          var i = t.attributes.get('data-semantic-type');
          return this.collapse.has(i)
            ? this.collapse.get(i).call(this, t, e)
            : this.cutoff.hasOwnProperty(i)
            ? this.defaultCheck(t, e, i)
            : e;
        }),
        (t.prototype.defaultCheck = function (t, e, i) {
          var o = t.attributes.get('data-semantic-role'),
            r = this.cutoff[i];
          if (e > ('number' == typeof r ? r : r[o] || r.value)) {
            var n = this.marker[i] || '??',
              s = 'string' == typeof n ? n : n[o] || n.value;
            e = this.recordCollapse(t, e, s);
          }
          return e;
        }),
        (t.prototype.recordCollapse = function (t, e, i) {
          return (
            (i = '\u25c2' + i + '\u25b8'),
            t.setProperty('collapse-marker', i),
            t.setProperty('collapse-complexity', e),
            i.length * this.complexity.complexity.text
          );
        }),
        (t.prototype.unrecordCollapse = function (t) {
          var e = t.getProperty('collapse-complexity');
          null != e &&
            (t.attributes.set('data-semantic-complexity', e),
            t.removeProperty('collapse-complexity'),
            t.removeProperty('collapse-marker'));
        }),
        (t.prototype.canUncollapse = function (t, e, i) {
          if ((void 0 === i && (i = 1), this.splitAttribute(t, 'children').length === i)) {
            var o = 1 === t.childNodes.length && t.childNodes[0].isInferred ? t.childNodes[0] : t;
            if (o && o.childNodes[e]) {
              var r = o.childNodes[e];
              if (r.getProperty('collapse-marker')) return r;
            }
          }
          return null;
        }),
        (t.prototype.uncollapseChild = function (t, e, i, o) {
          void 0 === o && (o = 1);
          var r = this.canUncollapse(e, i, o);
          return (
            r &&
              (this.unrecordCollapse(r),
              r.parent !== e && r.parent.attributes.set('data-semantic-complexity', void 0),
              (t = this.complexity.visitNode(e, !1))),
            t
          );
        }),
        (t.prototype.splitAttribute = function (t, e) {
          return (t.attributes.get('data-semantic-' + e) || '').split(/,/);
        }),
        (t.prototype.getText = function (t) {
          var e = this;
          return t.isToken
            ? t.getText()
            : t.childNodes
                .map(function (t) {
                  return e.getText(t);
                })
                .join('');
        }),
        (t.prototype.findChildText = function (t, e) {
          var i = this.findChild(t, e);
          return this.getText(i.coreMO() || i);
        }),
        (t.prototype.findChild = function (t, e) {
          var i, r;
          if (!t || t.attributes.get('data-semantic-id') === e) return t;
          if (!t.isToken)
            try {
              for (var n = o(t.childNodes), s = n.next(); !s.done; s = n.next()) {
                var l = s.value,
                  a = this.findChild(l, e);
                if (a) return a;
              }
            } catch (t) {
              i = { error: t };
            } finally {
              try {
                s && !s.done && (r = n.return) && r.call(n);
              } finally {
                if (i) throw i.error;
              }
            }
          return null;
        }),
        (t.prototype.makeCollapse = function (t) {
          var e = [];
          t.walkTree(function (t) {
            t.getProperty('collapse-marker') && e.push(t);
          }),
            this.makeActions(e);
        }),
        (t.prototype.makeActions = function (t) {
          var e, i;
          try {
            for (var r = o(t), n = r.next(); !n.done; n = r.next()) {
              var s = n.value;
              this.makeAction(s);
            }
          } catch (t) {
            e = { error: t };
          } finally {
            try {
              n && !n.done && (i = r.return) && i.call(r);
            } finally {
              if (e) throw e.error;
            }
          }
        }),
        (t.prototype.makeId = function () {
          return 'mjx-collapse-' + this.idCount++;
        }),
        (t.prototype.makeAction = function (t) {
          t.isKind('math') && (t = this.addMrow(t));
          var e = this.complexity.factory,
            i = t.getProperty('collapse-marker'),
            o = t.parent,
            r = e.create(
              'maction',
              {
                actiontype: 'toggle',
                selection: 2,
                'data-collapsible': !0,
                id: this.makeId(),
                'data-semantic-complexity': t.attributes.get('data-semantic-complexity'),
              },
              [e.create('mtext', { mathcolor: 'blue' }, [e.create('text').setText(i)])],
            );
          r.inheritAttributesFrom(t),
            t.attributes.set('data-semantic-complexity', t.getProperty('collapse-complexity')),
            t.removeProperty('collapse-marker'),
            t.removeProperty('collapse-complexity'),
            o.replaceChild(r, t),
            r.appendChild(t);
        }),
        (t.prototype.addMrow = function (t) {
          var e,
            i,
            r = this.complexity.factory.create('mrow', null, t.childNodes[0].childNodes);
          t.childNodes[0].setChildren([r]);
          var n = t.attributes.getAllAttributes();
          try {
            for (var s = o(Object.keys(n)), l = s.next(); !l.done; l = s.next()) {
              var a = l.value;
              'data-semantic-' === a.substr(0, 14) && (r.attributes.set(a, n[a]), delete n[a]);
            }
          } catch (t) {
            e = { error: t };
          } finally {
            try {
              l && !l.done && (i = s.return) && i.call(s);
            } finally {
              if (e) throw e.error;
            }
          }
          return (
            r.setProperty('collapse-marker', t.getProperty('collapse-marker')),
            r.setProperty('collapse-complexity', t.getProperty('collapse-complexity')),
            t.removeProperty('collapse-marker'),
            t.removeProperty('collapse-complexity'),
            r
          );
        }),
        (t.NOCOLLAPSE = 1e7),
        t
      );
    })();
    e.Collapse = r;
  },
  function (t, e, i) {
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
  function (t, e, i) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.sreReady = MathJax._.a11y.sre.sreReady);
  },
  function (t, e, i) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.protoItem = MathJax._.core.MathItem.protoItem),
      (e.AbstractMathItem = MathJax._.core.MathItem.AbstractMathItem),
      (e.STATE = MathJax._.core.MathItem.STATE),
      (e.newState = MathJax._.core.MathItem.newState);
  },
  function (t, e, i) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.MmlVisitor = MathJax._.core.MmlTree.MmlVisitor.MmlVisitor);
  },
  function (t, e, i) {
    'use strict';
    i.r(e);
    var o = i(0),
      r = i(1),
      n = i(4),
      s = i(3),
      l = i(2),
      a = i(6);
    Object(o.combineWithMathJax)({
      _: {
        a11y: {
          complexity_ts: r,
          complexity: { collapse: n, visitor: s },
          'semantic-enrich': l,
          sre: a,
        },
      },
    }),
      MathJax.startup &&
        (MathJax.startup.extendHandler(function (t) {
          return Object(r.ComplexityHandler)(t);
        }),
        Object(o.combineDefaults)(
          MathJax.config,
          'options',
          MathJax.config['a11y/complexity'] || {},
        ));
  },
]);
