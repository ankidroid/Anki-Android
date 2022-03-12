!(function (t) {
  var e = {};
  function o(r) {
    if (e[r]) return e[r].exports;
    var n = (e[r] = { i: r, l: !1, exports: {} });
    return t[r].call(n.exports, n, n.exports, o), (n.l = !0), n.exports;
  }
  (o.m = t),
    (o.c = e),
    (o.d = function (t, e, r) {
      o.o(t, e) || Object.defineProperty(t, e, { enumerable: !0, get: r });
    }),
    (o.r = function (t) {
      'undefined' != typeof Symbol &&
        Symbol.toStringTag &&
        Object.defineProperty(t, Symbol.toStringTag, { value: 'Module' }),
        Object.defineProperty(t, '__esModule', { value: !0 });
    }),
    (o.t = function (t, e) {
      if ((1 & e && (t = o(t)), 8 & e)) return t;
      if (4 & e && 'object' == typeof t && t && t.__esModule) return t;
      var r = Object.create(null);
      if (
        (o.r(r),
        Object.defineProperty(r, 'default', { enumerable: !0, value: t }),
        2 & e && 'string' != typeof t)
      )
        for (var n in t)
          o.d(
            r,
            n,
            function (e) {
              return t[e];
            }.bind(null, n),
          );
      return r;
    }),
    (o.n = function (t) {
      var e =
        t && t.__esModule
          ? function () {
              return t.default;
            }
          : function () {
              return t;
            };
      return o.d(e, 'a', e), e;
    }),
    (o.o = function (t, e) {
      return Object.prototype.hasOwnProperty.call(t, e);
    }),
    (o.p = ''),
    o((o.s = 14));
})([
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.sreReady = MathJax._.a11y.sre.sreReady);
  },
  function (t, e, o) {
    'use strict';
    var r =
        (this && this.__read) ||
        function (t, e) {
          var o = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!o) return t;
          var r,
            n,
            i = o.call(t),
            a = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(r = i.next()).done; ) a.push(r.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              r && !r.done && (o = i.return) && o.call(i);
            } finally {
              if (n) throw n.error;
            }
          }
          return a;
        },
      n =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(r(arguments[e]));
          return t;
        },
      i =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            o = e && t[e],
            r = 0;
          if (o) return o.call(t);
          if (t && 'number' == typeof t.length)
            return {
              next: function () {
                return t && r >= t.length && (t = void 0), { value: t && t[r++], done: !t };
              },
            };
          throw new TypeError(e ? 'Object is not iterable.' : 'Symbol.iterator is not defined.');
        };
    Object.defineProperty(e, '__esModule', { value: !0 }), (e.AbstractExplorer = void 0), o(0);
    var a = (function () {
      function t(t, e, o) {
        for (var r = [], n = 3; n < arguments.length; n++) r[n - 3] = arguments[n];
        (this.document = t),
          (this.region = e),
          (this.node = o),
          (this.stoppable = !0),
          (this.events = []),
          (this.highlighter = this.getHighlighter()),
          (this._active = !1);
      }
      return (
        (t.stopEvent = function (t) {
          t.preventDefault ? t.preventDefault() : (t.returnValue = !1),
            t.stopImmediatePropagation
              ? t.stopImmediatePropagation()
              : t.stopPropagation && t.stopPropagation(),
            (t.cancelBubble = !0);
        }),
        (t.create = function (t, e, o) {
          for (var r = [], i = 3; i < arguments.length; i++) r[i - 3] = arguments[i];
          var a = new (this.bind.apply(this, n([void 0, t, e, o], r)))();
          return a;
        }),
        (t.prototype.Events = function () {
          return this.events;
        }),
        Object.defineProperty(t.prototype, 'active', {
          get: function () {
            return this._active;
          },
          set: function (t) {
            this._active = t;
          },
          enumerable: !1,
          configurable: !0,
        }),
        (t.prototype.Attach = function () {
          this.AddEvents();
        }),
        (t.prototype.Detach = function () {
          this.RemoveEvents();
        }),
        (t.prototype.Start = function () {
          (this.highlighter = this.getHighlighter()), (this.active = !0);
        }),
        (t.prototype.Stop = function () {
          this.active && (this.region.Clear(), this.region.Hide(), (this.active = !1));
        }),
        (t.prototype.AddEvents = function () {
          var t, e;
          try {
            for (var o = i(this.events), n = o.next(); !n.done; n = o.next()) {
              var a = r(n.value, 2),
                s = a[0],
                l = a[1];
              this.node.addEventListener(s, l);
            }
          } catch (e) {
            t = { error: e };
          } finally {
            try {
              n && !n.done && (e = o.return) && e.call(o);
            } finally {
              if (t) throw t.error;
            }
          }
        }),
        (t.prototype.RemoveEvents = function () {
          var t, e;
          try {
            for (var o = i(this.events), n = o.next(); !n.done; n = o.next()) {
              var a = r(n.value, 2),
                s = a[0],
                l = a[1];
              this.node.removeEventListener(s, l);
            }
          } catch (e) {
            t = { error: e };
          } finally {
            try {
              n && !n.done && (e = o.return) && e.call(o);
            } finally {
              if (t) throw t.error;
            }
          }
        }),
        (t.prototype.Update = function (t) {
          void 0 === t && (t = !1);
        }),
        (t.prototype.getHighlighter = function () {
          var t = this.document.options.a11y,
            e = { color: t.foregroundColor.toLowerCase(), alpha: t.foregroundOpacity / 100 },
            o = { color: t.backgroundColor.toLowerCase(), alpha: t.backgroundOpacity / 100 };
          return sre.HighlighterFactory.highlighter(o, e, {
            renderer: this.document.outputJax.name,
            browser: 'v3',
          });
        }),
        (t.prototype.stopEvent = function (e) {
          this.stoppable && t.stopEvent(e);
        }),
        t
      );
    })();
    e.AbstractExplorer = a;
  },
  function (t, e, o) {
    'use strict';
    var r,
      n,
      i,
      a,
      s =
        (this && this.__extends) ||
        ((r = function (t, e) {
          return (r =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var o in e) e.hasOwnProperty(o) && (t[o] = e[o]);
            })(t, e);
        }),
        function (t, e) {
          function o() {
            this.constructor = t;
          }
          r(t, e),
            (t.prototype = null === e ? Object.create(e) : ((o.prototype = e.prototype), new o()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.HoverRegion =
        e.LiveRegion =
        e.ToolTip =
        e.StringRegion =
        e.DummyRegion =
        e.AbstractRegion =
          void 0);
    var l = o(13);
    o(0);
    var c = (function () {
      function t(t) {
        (this.document = t), (this.CLASS = this.constructor), this.AddStyles(), this.AddElement();
      }
      return (
        (t.prototype.AddStyles = function () {
          if (!this.CLASS.styleAdded) {
            var t = this.document.adaptor.node('style');
            (t.innerHTML = this.CLASS.style.cssText),
              this.document.adaptor.head(this.document.adaptor.document).appendChild(t),
              (this.CLASS.styleAdded = !0);
          }
        }),
        (t.prototype.AddElement = function () {
          var t = this.document.adaptor.node('div');
          t.classList.add(this.CLASS.className),
            (t.style.backgroundColor = 'white'),
            (this.div = t),
            (this.inner = this.document.adaptor.node('div')),
            this.div.appendChild(this.inner),
            this.document.adaptor.body(this.document.adaptor.document).appendChild(this.div);
        }),
        (t.prototype.Show = function (t, e) {
          this.position(t),
            this.highlight(e),
            this.div.classList.add(this.CLASS.className + '_Show');
        }),
        (t.prototype.Hide = function () {
          this.div.classList.remove(this.CLASS.className + '_Show');
        }),
        (t.prototype.stackRegions = function (t) {
          for (
            var e = t.getBoundingClientRect(),
              o = 0,
              r = Number.POSITIVE_INFINITY,
              n = this.document.adaptor.document.getElementsByClassName(
                this.CLASS.className + '_Show',
              ),
              i = 0,
              a = void 0;
            (a = n[i]);
            i++
          )
            a !== this.div &&
              ((o = Math.max(a.getBoundingClientRect().bottom, o)),
              (r = Math.min(a.getBoundingClientRect().left, r)));
          var s = (o || e.bottom + 10) + window.pageYOffset,
            l = (r < Number.POSITIVE_INFINITY ? r : e.left) + window.pageXOffset;
          (this.div.style.top = s + 'px'), (this.div.style.left = l + 'px');
        }),
        (t.styleAdded = !1),
        t
      );
    })();
    e.AbstractRegion = c;
    var h = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return (
        s(e, t),
        (e.prototype.Clear = function () {}),
        (e.prototype.Update = function () {}),
        (e.prototype.Hide = function () {}),
        (e.prototype.Show = function () {}),
        (e.prototype.AddElement = function () {}),
        (e.prototype.AddStyles = function () {}),
        (e.prototype.position = function () {}),
        (e.prototype.highlight = function (t) {}),
        e
      );
    })(c);
    e.DummyRegion = h;
    var u = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return (
        s(e, t),
        (e.prototype.Clear = function () {
          this.Update(''), (this.inner.style.top = ''), (this.inner.style.backgroundColor = '');
        }),
        (e.prototype.Update = function (t) {
          (this.inner.textContent = ''), (this.inner.textContent = t);
        }),
        (e.prototype.position = function (t) {
          this.stackRegions(t);
        }),
        (e.prototype.highlight = function (t) {
          var e = t.colorString();
          (this.inner.style.backgroundColor = e.background),
            (this.inner.style.color = e.foreground);
        }),
        e
      );
    })(c);
    e.StringRegion = u;
    var p = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return (
        s(e, t),
        (e.className = 'MJX_ToolTip'),
        (e.style = new l.CssStyles(
          (((n = {})['.' + e.className] = {
            position: 'absolute',
            display: 'inline-block',
            height: '1px',
            width: '1px',
          }),
          (n['.' + e.className + '_Show'] = {
            width: 'auto',
            height: 'auto',
            opacity: 1,
            'text-align': 'center',
            'border-radius': '6px',
            padding: '0px 0px',
            'border-bottom': '1px dotted black',
            position: 'absolute',
            'z-index': 202,
          }),
          n),
        )),
        e
      );
    })(u);
    e.ToolTip = p;
    var d = (function (t) {
      function e(e) {
        var o = t.call(this, e) || this;
        return (o.document = e), o.div.setAttribute('aria-live', 'assertive'), o;
      }
      return (
        s(e, t),
        (e.className = 'MJX_LiveRegion'),
        (e.style = new l.CssStyles(
          (((i = {})['.' + e.className] = {
            position: 'absolute',
            top: '0',
            height: '1px',
            width: '1px',
            padding: '1px',
            overflow: 'hidden',
          }),
          (i['.' + e.className + '_Show'] = {
            top: '0',
            position: 'absolute',
            width: 'auto',
            height: 'auto',
            padding: '0px 0px',
            opacity: 1,
            'z-index': '202',
            left: 0,
            right: 0,
            margin: '0 auto',
            'background-color': 'rgba(0, 0, 255, 0.2)',
            'box-shadow': '0px 10px 20px #888',
            border: '2px solid #CCCCCC',
          }),
          i),
        )),
        e
      );
    })(u);
    e.LiveRegion = d;
    var f = (function (t) {
      function e(e) {
        var o = t.call(this, e) || this;
        return (o.document = e), (o.inner.style.lineHeight = '0'), o;
      }
      return (
        s(e, t),
        (e.prototype.position = function (t) {
          var e,
            o = t.getBoundingClientRect(),
            r = this.div.getBoundingClientRect(),
            n = o.left + o.width / 2 - r.width / 2;
          switch (
            ((n = n < 0 ? 0 : n), (n += window.pageXOffset), this.document.options.a11y.align)
          ) {
            case 'top':
              e = o.top - r.height - 10;
              break;
            case 'bottom':
              e = o.bottom + 10;
              break;
            case 'center':
            default:
              e = o.top + o.height / 2 - r.height / 2;
          }
          (e = (e += window.pageYOffset) < 0 ? 0 : e),
            (this.div.style.top = e + 'px'),
            (this.div.style.left = n + 'px');
        }),
        (e.prototype.highlight = function (t) {
          if (!this.inner.firstChild || this.inner.firstChild.hasAttribute('sre-highlight')) {
            var e = t.colorString();
            (this.inner.style.backgroundColor = e.background),
              (this.inner.style.color = e.foreground);
          }
        }),
        (e.prototype.Show = function (e, o) {
          (this.div.style.fontSize = this.document.options.a11y.magnify),
            this.Update(e),
            t.prototype.Show.call(this, e, o);
        }),
        (e.prototype.Clear = function () {
          (this.inner.textContent = ''),
            (this.inner.style.top = ''),
            (this.inner.style.backgroundColor = '');
        }),
        (e.prototype.Update = function (t) {
          this.Clear();
          var e = this.cloneNode(t);
          this.inner.appendChild(e);
        }),
        (e.prototype.cloneNode = function (t) {
          var e = t.cloneNode(!0);
          if ('MJX-CONTAINER' !== e.nodeName) {
            'g' !== e.nodeName && (e.style.marginLeft = e.style.marginRight = '0');
            for (var o = t; o && 'MJX-CONTAINER' !== o.nodeName; ) o = o.parentNode;
            if ('MJX-MATH' !== e.nodeName && 'svg' !== e.nodeName)
              if ('svg' === (e = o.firstChild.cloneNode(!1).appendChild(e).parentNode).nodeName) {
                e.firstChild.setAttribute('transform', 'matrix(1 0 0 -1 0 0)');
                var r = parseFloat(e.getAttribute('viewBox').split(/ /)[2]),
                  n = parseFloat(e.getAttribute('width')),
                  i = t.getBBox(),
                  a = i.x,
                  s = i.y,
                  l = i.width,
                  c = i.height;
                e.setAttribute('viewBox', [a, -(s + c), l, c].join(' ')),
                  e.removeAttribute('style'),
                  e.setAttribute('width', (n / r) * l + 'ex'),
                  e.setAttribute('height', (n / r) * c + 'ex'),
                  o.setAttribute('sre-highlight', 'false');
              }
            (e = o.cloneNode(!1).appendChild(e).parentNode).style.margin = '0';
          }
          return e;
        }),
        (e.className = 'MJX_HoverRegion'),
        (e.style = new l.CssStyles(
          (((a = {})['.' + e.className] = {
            position: 'absolute',
            height: '1px',
            width: '1px',
            padding: '1px',
            overflow: 'hidden',
          }),
          (a['.' + e.className + '_Show'] = {
            position: 'absolute',
            width: 'max-content',
            height: 'auto',
            padding: '0px 0px',
            opacity: 1,
            'z-index': '202',
            margin: '0 auto',
            'background-color': 'rgba(0, 0, 255, 0.2)',
            'box-shadow': '0px 10px 20px #888',
            border: '2px solid #CCCCCC',
          }),
          a),
        )),
        e
      );
    })(c);
    e.HoverRegion = f;
  },
  function (t, e, o) {
    'use strict';
    var r,
      n =
        (this && this.__extends) ||
        ((r = function (t, e) {
          return (r =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var o in e) e.hasOwnProperty(o) && (t[o] = e[o]);
            })(t, e);
        }),
        function (t, e) {
          function o() {
            this.constructor = t;
          }
          r(t, e),
            (t.prototype = null === e ? Object.create(e) : ((o.prototype = e.prototype), new o()));
        }),
      i =
        (this && this.__assign) ||
        function () {
          return (i =
            Object.assign ||
            function (t) {
              for (var e, o = 1, r = arguments.length; o < r; o++)
                for (var n in (e = arguments[o]))
                  Object.prototype.hasOwnProperty.call(e, n) && (t[n] = e[n]);
              return t;
            }).apply(this, arguments);
        },
      a =
        (this && this.__values) ||
        function (t) {
          var e = 'function' == typeof Symbol && Symbol.iterator,
            o = e && t[e],
            r = 0;
          if (o) return o.call(t);
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
          var o = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!o) return t;
          var r,
            n,
            i = o.call(t),
            a = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(r = i.next()).done; ) a.push(r.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              r && !r.done && (o = i.return) && o.call(i);
            } finally {
              if (n) throw n.error;
            }
          }
          return a;
        },
      l =
        (this && this.__spread) ||
        function () {
          for (var t = [], e = 0; e < arguments.length; e++) t = t.concat(s(arguments[e]));
          return t;
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.setA11yOption =
        e.setA11yOptions =
        e.ExplorerHandler =
        e.ExplorerMathDocumentMixin =
        e.ExplorerMathItemMixin =
          void 0);
    var c = o(8),
      h = o(9),
      u = o(10),
      p = o(11),
      d = o(12),
      f = o(5),
      y = o(6),
      v = o(7),
      g = o(2);
    function m(t, e) {
      return (function (t) {
        function o() {
          var e = (null !== t && t.apply(this, arguments)) || this;
          return (
            (e.explorers = {}),
            (e.attached = []),
            (e.restart = !1),
            (e.refocus = !1),
            (e.savedId = null),
            e
          );
        }
        return (
          n(o, t),
          (o.prototype.explorable = function (t, o) {
            if ((void 0 === o && (o = !1), !(this.state() >= c.STATE.EXPLORER))) {
              if (!this.isEscaped && (t.options.enableExplorer || o)) {
                var r = this.typesetRoot,
                  n = e(this.root);
                this.savedId &&
                  (this.typesetRoot.setAttribute('sre-explorer-id', this.savedId),
                  (this.savedId = null)),
                  (this.explorers = (function (t, e, o) {
                    var r,
                      n,
                      i = {};
                    try {
                      for (var s = a(Object.keys(_)), l = s.next(); !l.done; l = s.next()) {
                        var c = l.value;
                        i[c] = _[c](t, e, o);
                      }
                    } catch (t) {
                      r = { error: t };
                    } finally {
                      try {
                        l && !l.done && (n = s.return) && n.call(s);
                      } finally {
                        if (r) throw r.error;
                      }
                    }
                    return i;
                  })(t, r, n)),
                  this.attachExplorers(t);
              }
              this.state(c.STATE.EXPLORER);
            }
          }),
          (o.prototype.attachExplorers = function (t) {
            var e, o;
            this.attached = [];
            try {
              for (var r = a(Object.keys(this.explorers)), n = r.next(); !n.done; n = r.next()) {
                var i = n.value,
                  s = this.explorers[i];
                t.options.a11y[i] ? (s.Attach(), this.attached.push(s)) : s.Detach();
              }
            } catch (t) {
              e = { error: t };
            } finally {
              try {
                n && !n.done && (o = r.return) && o.call(r);
              } finally {
                if (e) throw e.error;
              }
            }
            this.addExplorers(this.attached);
          }),
          (o.prototype.rerender = function (e, o) {
            var r, n;
            void 0 === o && (o = c.STATE.RERENDER),
              (this.savedId = this.typesetRoot.getAttribute('sre-explorer-id')),
              (this.refocus = window.document.activeElement === this.typesetRoot);
            try {
              for (var i = a(this.attached), s = i.next(); !s.done; s = i.next()) {
                var l = s.value;
                l.active && ((this.restart = !0), l.Stop());
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
            t.prototype.rerender.call(this, e, o);
          }),
          (o.prototype.updateDocument = function (e) {
            t.prototype.updateDocument.call(this, e),
              this.refocus && this.typesetRoot.focus(),
              this.restart &&
                this.attached.forEach(function (t) {
                  return t.Start();
                }),
              (this.refocus = this.restart = !1);
          }),
          (o.prototype.addExplorers = function (t) {
            var e, o;
            if (!(t.length <= 1)) {
              var r = null;
              try {
                for (var n = a(this.attached), i = n.next(); !i.done; i = n.next()) {
                  var s = i.value;
                  s instanceof f.AbstractKeyExplorer && ((s.stoppable = !1), (r = s));
                }
              } catch (t) {
                e = { error: t };
              } finally {
                try {
                  i && !i.done && (o = n.return) && o.call(n);
                } finally {
                  if (e) throw e.error;
                }
              }
              r && (r.stoppable = !0);
            }
          }),
          o
        );
      })(t);
    }
    function b(t) {
      var e;
      return (
        ((e = (function (t) {
          function e() {
            for (var e = [], o = 0; o < arguments.length; o++) e[o] = arguments[o];
            var r = t.apply(this, l(e)) || this,
              n = r.constructor.ProcessBits;
            n.has('explorer') || n.allocate('explorer');
            var i = new p.SerializedMmlVisitor(r.mmlFactory),
              a = function (t) {
                return i.visitTree(t);
              };
            return (r.options.MathItem = m(r.options.MathItem, a)), (r.explorerRegions = x(r)), r;
          }
          return (
            n(e, t),
            (e.prototype.explorable = function () {
              var t, e;
              if (!this.processed.isSet('explorer')) {
                if (this.options.enableExplorer)
                  try {
                    for (var o = a(this.math), r = o.next(); !r.done; r = o.next()) {
                      r.value.explorable(this);
                    }
                  } catch (e) {
                    t = { error: e };
                  } finally {
                    try {
                      r && !r.done && (e = o.return) && e.call(o);
                    } finally {
                      if (t) throw t.error;
                    }
                  }
                this.processed.set('explorer');
              }
              return this;
            }),
            (e.prototype.state = function (e, o) {
              return (
                void 0 === o && (o = !1),
                t.prototype.state.call(this, e, o),
                e < c.STATE.EXPLORER && this.processed.clear('explorer'),
                this
              );
            }),
            e
          );
        })(t)).OPTIONS = i(i({}, t.OPTIONS), {
          enrichSpeech: 'shallow',
          enableExplorer: !0,
          renderActions: u.expandable(
            i(i({}, t.OPTIONS.renderActions), { explorable: [c.STATE.EXPLORER] }),
          ),
          a11y: {
            align: 'top',
            backgroundColor: 'Blue',
            backgroundOpacity: 20,
            braille: !1,
            flame: !1,
            foregroundColor: 'Black',
            foregroundOpacity: 100,
            highlight: 'None',
            hover: !1,
            infoPrefix: !1,
            infoRole: !1,
            infoType: !1,
            keyMagnifier: !1,
            locale: 'en',
            magnification: 'None',
            magnify: '400%',
            mouseMagnifier: !1,
            speech: !0,
            speechRules: 'mathspeak-default',
            subtitles: !0,
            treeColoring: !1,
            viewBraille: !1,
          },
        })),
        e
      );
    }
    function x(t) {
      return {
        speechRegion: new g.LiveRegion(t),
        brailleRegion: new g.LiveRegion(t),
        magnifier: new g.HoverRegion(t),
        tooltip1: new g.ToolTip(t),
        tooltip2: new g.ToolTip(t),
        tooltip3: new g.ToolTip(t),
      };
    }
    c.newState('EXPLORER', 160),
      (e.ExplorerMathItemMixin = m),
      (e.ExplorerMathDocumentMixin = b),
      (e.ExplorerHandler = function (t, e) {
        return (
          void 0 === e && (e = null),
          !t.documentClass.prototype.enrich && e && (t = h.EnrichHandler(t, e)),
          (t.documentClass = b(t.documentClass)),
          t
        );
      });
    var _ = {
      speech: function (t, e) {
        for (var o, r = [], n = 2; n < arguments.length; n++) r[n - 2] = arguments[n];
        var i = (o = f.SpeechExplorer).create.apply(
            o,
            l([t, t.explorerRegions.speechRegion, e], r),
          ),
          a = s(t.options.a11y.speechRules.split('-'), 2),
          c = a[0],
          h = a[1];
        return (
          i.speechGenerator.setOptions({
            locale: t.options.a11y.locale,
            domain: c,
            style: h,
            modality: 'speech',
            cache: !1,
          }),
          (i.showRegion = 'subtitles'),
          i
        );
      },
      braille: function (t, e) {
        for (var o, r = [], n = 2; n < arguments.length; n++) r[n - 2] = arguments[n];
        var i = (o = f.SpeechExplorer).create.apply(
          o,
          l([t, t.explorerRegions.brailleRegion, e], r),
        );
        return (
          i.speechGenerator.setOptions({
            locale: 'nemeth',
            domain: 'default',
            style: 'default',
            modality: 'braille',
          }),
          (i.showRegion = 'viewBraille'),
          i
        );
      },
      keyMagnifier: function (t, e) {
        for (var o, r = [], n = 2; n < arguments.length; n++) r[n - 2] = arguments[n];
        return (o = f.Magnifier).create.apply(o, l([t, t.explorerRegions.magnifier, e], r));
      },
      mouseMagnifier: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return y.ContentHoverer.create(
          t,
          t.explorerRegions.magnifier,
          e,
          function (t) {
            return t.hasAttribute('data-semantic-type');
          },
          function (t) {
            return t;
          },
        );
      },
      hover: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return y.FlameHoverer.create(t, null, e);
      },
      infoType: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return y.ValueHoverer.create(
          t,
          t.explorerRegions.tooltip1,
          e,
          function (t) {
            return t.hasAttribute('data-semantic-type');
          },
          function (t) {
            return t.getAttribute('data-semantic-type');
          },
        );
      },
      infoRole: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return y.ValueHoverer.create(
          t,
          t.explorerRegions.tooltip2,
          e,
          function (t) {
            return t.hasAttribute('data-semantic-role');
          },
          function (t) {
            return t.getAttribute('data-semantic-role');
          },
        );
      },
      infoPrefix: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return y.ValueHoverer.create(
          t,
          t.explorerRegions.tooltip3,
          e,
          function (t) {
            return t.hasAttribute('data-semantic-prefix');
          },
          function (t) {
            return t.getAttribute('data-semantic-prefix');
          },
        );
      },
      flame: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return v.FlameColorer.create(t, null, e);
      },
      treeColoring: function (t, e) {
        for (var o = [], r = 2; r < arguments.length; r++) o[r - 2] = arguments[r];
        return v.TreeColorer.create.apply(v.TreeColorer, l([t, null, e], o));
      },
    };
    function M(t, e, o) {
      switch (e) {
        case 'magnification':
          switch (o) {
            case 'None':
              (t.options.a11y.magnification = o),
                (t.options.a11y.keyMagnifier = !1),
                (t.options.a11y.mouseMagnifier = !1);
              break;
            case 'Keyboard':
              (t.options.a11y.magnification = o),
                (t.options.a11y.keyMagnifier = !0),
                (t.options.a11y.mouseMagnifier = !1);
              break;
            case 'Mouse':
              (t.options.a11y.magnification = o),
                (t.options.a11y.keyMagnifier = !1),
                (t.options.a11y.mouseMagnifier = !0);
          }
          break;
        case 'highlight':
          switch (o) {
            case 'None':
              (t.options.a11y.highlight = o),
                (t.options.a11y.hover = !1),
                (t.options.a11y.flame = !1);
              break;
            case 'Hover':
              (t.options.a11y.highlight = o),
                (t.options.a11y.hover = !0),
                (t.options.a11y.flame = !1);
              break;
            case 'Flame':
              (t.options.a11y.highlight = o),
                (t.options.a11y.hover = !1),
                (t.options.a11y.flame = !0);
          }
          break;
        default:
          t.options.a11y[e] = o;
      }
    }
    (e.setA11yOptions = function (t, e) {
      var o, r;
      for (var n in e) void 0 !== t.options.a11y[n] && M(t, n, e[n]);
      try {
        for (var i = a(t.math), s = i.next(); !s.done; s = i.next()) {
          s.value.attachExplorers(t);
        }
      } catch (t) {
        o = { error: t };
      } finally {
        try {
          s && !s.done && (r = i.return) && r.call(i);
        } finally {
          if (o) throw o.error;
        }
      }
    }),
      (e.setA11yOption = M);
    var S = {},
      O = function (t, e) {
        var o,
          r,
          n = sre.ClearspeakPreferences.getLocalePreferences()[e];
        if (!n) {
          var i = t.findID('Accessibility', 'Speech', 'Clearspeak');
          return i && i.disable(), null;
        }
        !(function (t, e) {
          var o,
            r,
            n = t.pool.lookup('speechRules'),
            i = function (e) {
              if (S[e]) return 'continue';
              t.factory.get('variable')(
                t.factory,
                {
                  name: 'csprf_' + e,
                  setter: function (t) {
                    (S[e] = t),
                      n.setValue(
                        'clearspeak-' +
                          sre.ClearspeakPreferences.addPreference(
                            sre.Engine.DOMAIN_TO_STYLES.clearspeak,
                            e,
                            t,
                          ),
                      );
                  },
                  getter: function () {
                    return S[e] || 'Auto';
                  },
                },
                t.pool,
              );
            };
          try {
            for (var s = a(e), l = s.next(); !l.done; l = s.next()) i(l.value);
          } catch (t) {
            o = { error: t };
          } finally {
            try {
              l && !l.done && (r = s.return) && r.call(s);
            } finally {
              if (o) throw o.error;
            }
          }
        })(t, Object.keys(n));
        var s = [],
          l = function (t) {
            s.push({
              title: t,
              values: n[t].map(function (e) {
                return e.replace(RegExp('^' + t + '_'), '');
              }),
              variable: 'csprf_' + t,
            });
          };
        try {
          for (var c = a(Object.getOwnPropertyNames(n)), h = c.next(); !h.done; h = c.next()) {
            l(h.value);
          }
        } catch (t) {
          o = { error: t };
        } finally {
          try {
            h && !h.done && (r = c.return) && r.call(c);
          } finally {
            if (o) throw o.error;
          }
        }
        var u = t.factory.get('selectionBox')(
          t.factory,
          {
            title: 'Clearspeak Preferences',
            signature: '',
            order: 'alphabetic',
            grid: 'square',
            selections: s,
          },
          t,
        );
        return {
          type: 'command',
          id: 'ClearspeakPreferences',
          content: 'Select Preferences',
          action: function () {
            return u.post(0, 0);
          },
        };
      };
    d.MJContextMenu.DynamicSubmenus.set('Clearspeak', function (t, e) {
      var o = t.pool.lookup('locale').getValue(),
        r = O(t, o),
        n = sre.ClearspeakPreferences.smartPreferences(t.mathItem, o);
      return (
        r && n.splice(2, 0, r),
        t.factory.get('subMenu')(t.factory, { items: n, id: 'Clearspeak' }, e)
      );
    });
    var w = { de: 'German', en: 'English', es: 'Spanish', fr: 'French' };
    d.MJContextMenu.DynamicSubmenus.set('A11yLanguage', function (t, e) {
      var o,
        r,
        n = [];
      try {
        for (var i = a(sre.Variables.LOCALES), s = i.next(); !s.done; s = i.next()) {
          var l = s.value;
          'nemeth' !== l &&
            n.push({ type: 'radio', id: l, content: w[l] || l, variable: 'locale' });
        }
      } catch (t) {
        o = { error: t };
      } finally {
        try {
          s && !s.done && (r = i.return) && r.call(i);
        } finally {
          if (o) throw o.error;
        }
      }
      return (
        n.sort(function (t, e) {
          return t.content.localeCompare(e.content, 'en');
        }),
        t.factory.get('subMenu')(t.factory, { items: n, id: 'Language' }, e)
      );
    });
  },
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.isObject = MathJax._.components.global.isObject),
      (e.combineConfig = MathJax._.components.global.combineConfig),
      (e.combineDefaults = MathJax._.components.global.combineDefaults),
      (e.combineWithMathJax = MathJax._.components.global.combineWithMathJax),
      (e.MathJax = MathJax._.components.global.MathJax);
  },
  function (t, e, o) {
    'use strict';
    var r,
      n =
        (this && this.__extends) ||
        ((r = function (t, e) {
          return (r =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var o in e) e.hasOwnProperty(o) && (t[o] = e[o]);
            })(t, e);
        }),
        function (t, e) {
          function o() {
            this.constructor = t;
          }
          r(t, e),
            (t.prototype = null === e ? Object.create(e) : ((o.prototype = e.prototype), new o()));
        }),
      i =
        (this && this.__read) ||
        function (t, e) {
          var o = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!o) return t;
          var r,
            n,
            i = o.call(t),
            a = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(r = i.next()).done; ) a.push(r.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              r && !r.done && (o = i.return) && o.call(i);
            } finally {
              if (n) throw n.error;
            }
          }
          return a;
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.Magnifier = e.SpeechExplorer = e.AbstractKeyExplorer = void 0);
    var a = o(1),
      s = o(0),
      l = (function (t) {
        function e() {
          var e = (null !== t && t.apply(this, arguments)) || this;
          return (
            (e.events = t.prototype.Events.call(e).concat([
              ['keydown', e.KeyDown.bind(e)],
              ['focusin', e.FocusIn.bind(e)],
              ['focusout', e.FocusOut.bind(e)],
            ])),
            (e.oldIndex = null),
            e
          );
        }
        return (
          n(e, t),
          (e.prototype.FocusIn = function (t) {}),
          (e.prototype.FocusOut = function (t) {
            this.Stop();
          }),
          (e.prototype.Update = function (t) {
            void 0 === t && (t = !1),
              (this.active || t) &&
                (this.highlighter.unhighlight(),
                this.highlighter.highlight(this.walker.getFocus(!0).getNodes()));
          }),
          (e.prototype.Attach = function () {
            t.prototype.Attach.call(this),
              (this.oldIndex = this.node.tabIndex),
              (this.node.tabIndex = 1),
              this.node.setAttribute('role', 'application');
          }),
          (e.prototype.Detach = function () {
            (this.node.tabIndex = this.oldIndex),
              (this.oldIndex = null),
              this.node.removeAttribute('role'),
              t.prototype.Detach.call(this);
          }),
          (e.prototype.Stop = function () {
            this.active && (this.highlighter.unhighlight(), this.walker.deactivate()),
              t.prototype.Stop.call(this);
          }),
          e
        );
      })(a.AbstractExplorer);
    e.AbstractKeyExplorer = l;
    var c = (function (t) {
      function e(e, o, r, n) {
        var i = t.call(this, e, o, r) || this;
        return (
          (i.document = e),
          (i.region = o),
          (i.node = r),
          (i.mml = n),
          (i.showRegion = 'subtitles'),
          (i.init = !1),
          (i.restarted = !1),
          i.initWalker(),
          i
        );
      }
      return (
        n(e, t),
        (e.prototype.Start = function () {
          var e = this,
            o = this.getOptions();
          if (!this.init)
            return (
              (this.init = !0),
              void s
                .sreReady()
                .then(function () {
                  SRE.engineSetup().locale !== o.locale && SRE.setupEngine({ locale: o.locale }),
                    s.sreReady().then(function () {
                      e.Speech(e.walker), e.Start();
                    });
                })
                .catch(function (t) {
                  return console.log(t.message);
                })
            );
          t.prototype.Start.call(this),
            (this.speechGenerator = sre.SpeechGeneratorFactory.generator('Direct')),
            this.speechGenerator.setOptions(o),
            (this.walker = sre.WalkerFactory.walker(
              'table',
              this.node,
              this.speechGenerator,
              this.highlighter,
              this.mml,
            )),
            this.walker.activate(),
            this.Update(),
            this.document.options.a11y[this.showRegion] &&
              this.region.Show(this.node, this.highlighter),
            (this.restarted = !0);
        }),
        (e.prototype.Update = function (e) {
          void 0 === e && (e = !1),
            t.prototype.Update.call(this, e),
            this.region.Update(this.walker.speech());
          var o = this.speechGenerator.getOptions();
          'speech' === o.modality &&
            (this.document.options.a11y.speechRules = o.domain + '-' + o.style);
        }),
        (e.prototype.Speech = function (t) {
          t.speech(),
            this.node.setAttribute('hasspeech', 'true'),
            this.Update(),
            this.restarted &&
              this.document.options.a11y[this.showRegion] &&
              this.region.Show(this.node, this.highlighter);
        }),
        (e.prototype.KeyDown = function (t) {
          var e = t.keyCode;
          return 27 === e
            ? (this.Stop(), void this.stopEvent(t))
            : this.active
            ? (this.Move(e), void this.stopEvent(t))
            : void (((32 === e && t.shiftKey) || 13 === e) && (this.Start(), this.stopEvent(t)));
        }),
        (e.prototype.Move = function (t) {
          this.walker.move(t), this.Update();
        }),
        (e.prototype.initWalker = function () {
          this.speechGenerator = sre.SpeechGeneratorFactory.generator('Tree');
          var t = sre.WalkerFactory.walker(
            'dummy',
            this.node,
            this.speechGenerator,
            this.highlighter,
            this.mml,
          );
          this.walker = t;
        }),
        (e.prototype.getOptions = function () {
          var t = this.speechGenerator.getOptions(),
            e = i(this.document.options.a11y.speechRules.split('-'), 2),
            o = e[0],
            r = e[1];
          return (
            'speech' !== t.modality ||
              (t.locale === this.document.options.a11y.locale && t.domain === o && t.style === r) ||
              ((t.domain = o),
              (t.style = r),
              (t.locale = this.document.options.a11y.locale),
              this.walker.update(t)),
            t
          );
        }),
        e
      );
    })(l);
    e.SpeechExplorer = c;
    var h = (function (t) {
      function e(e, o, r, n) {
        var i = t.call(this, e, o, r) || this;
        return (
          (i.document = e),
          (i.region = o),
          (i.node = r),
          (i.mml = n),
          (i.walker = sre.WalkerFactory.walker(
            'table',
            i.node,
            sre.SpeechGeneratorFactory.generator('Dummy'),
            i.highlighter,
            i.mml,
          )),
          i
        );
      }
      return (
        n(e, t),
        (e.prototype.Update = function (e) {
          void 0 === e && (e = !1), t.prototype.Update.call(this, e), this.showFocus();
        }),
        (e.prototype.Start = function () {
          t.prototype.Start.call(this),
            this.region.Show(this.node, this.highlighter),
            this.walker.activate(),
            this.Update();
        }),
        (e.prototype.showFocus = function () {
          var t = this.walker.getFocus().getNodes()[0];
          this.region.Show(t, this.highlighter);
        }),
        (e.prototype.Move = function (t) {
          this.walker.move(t) && this.Update();
        }),
        (e.prototype.KeyDown = function (t) {
          var e = t.keyCode;
          return 27 === e
            ? (this.Stop(), void this.stopEvent(t))
            : this.active && 13 !== e
            ? (this.Move(e), void this.stopEvent(t))
            : void (((32 === e && t.shiftKey) || 13 === e) && (this.Start(), this.stopEvent(t)));
        }),
        e
      );
    })(l);
    e.Magnifier = h;
  },
  function (t, e, o) {
    'use strict';
    var r,
      n =
        (this && this.__extends) ||
        ((r = function (t, e) {
          return (r =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var o in e) e.hasOwnProperty(o) && (t[o] = e[o]);
            })(t, e);
        }),
        function (t, e) {
          function o() {
            this.constructor = t;
          }
          r(t, e),
            (t.prototype = null === e ? Object.create(e) : ((o.prototype = e.prototype), new o()));
        }),
      i =
        (this && this.__read) ||
        function (t, e) {
          var o = 'function' == typeof Symbol && t[Symbol.iterator];
          if (!o) return t;
          var r,
            n,
            i = o.call(t),
            a = [];
          try {
            for (; (void 0 === e || e-- > 0) && !(r = i.next()).done; ) a.push(r.value);
          } catch (t) {
            n = { error: t };
          } finally {
            try {
              r && !r.done && (o = i.return) && o.call(i);
            } finally {
              if (n) throw n.error;
            }
          }
          return a;
        };
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.FlameHoverer =
        e.ContentHoverer =
        e.ValueHoverer =
        e.Hoverer =
        e.AbstractMouseExplorer =
          void 0);
    var a = o(2),
      s = o(1);
    o(0);
    var l = (function (t) {
      function e() {
        var e = (null !== t && t.apply(this, arguments)) || this;
        return (
          (e.events = t.prototype.Events.call(e).concat([
            ['mouseover', e.MouseOver.bind(e)],
            ['mouseout', e.MouseOut.bind(e)],
          ])),
          e
        );
      }
      return (
        n(e, t),
        (e.prototype.MouseOver = function (t) {
          this.Start();
        }),
        (e.prototype.MouseOut = function (t) {
          this.Stop();
        }),
        e
      );
    })(s.AbstractExplorer);
    e.AbstractMouseExplorer = l;
    var c = (function (t) {
      function e(e, o, r, n, i) {
        var a = t.call(this, e, o, r) || this;
        return (
          (a.document = e), (a.region = o), (a.node = r), (a.nodeQuery = n), (a.nodeAccess = i), a
        );
      }
      return (
        n(e, t),
        (e.prototype.MouseOut = function (e) {
          (e.clientX === this.coord[0] && e.clientY === this.coord[1]) ||
            (this.highlighter.unhighlight(),
            this.region.Hide(),
            t.prototype.MouseOut.call(this, e));
        }),
        (e.prototype.MouseOver = function (e) {
          t.prototype.MouseOver.call(this, e);
          var o = e.target;
          this.coord = [e.clientX, e.clientY];
          var r = i(this.getNode(o), 2),
            n = r[0],
            a = r[1];
          n &&
            (this.highlighter.unhighlight(),
            this.highlighter.highlight([n]),
            this.region.Update(a),
            this.region.Show(n, this.highlighter));
        }),
        (e.prototype.getNode = function (t) {
          for (var e = t; t && t !== this.node; ) {
            if (this.nodeQuery(t)) return [t, this.nodeAccess(t)];
            t = t.parentNode;
          }
          for (t = e; t; ) {
            if (this.nodeQuery(t)) return [t, this.nodeAccess(t)];
            var o = t.childNodes[0];
            t = o && 'defs' === o.tagName ? t.childNodes[1] : o;
          }
          return [null, null];
        }),
        e
      );
    })(l);
    e.Hoverer = c;
    var h = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return n(e, t), e;
    })(c);
    e.ValueHoverer = h;
    var u = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return n(e, t), e;
    })(c);
    e.ContentHoverer = u;
    var p = (function (t) {
      function e(e, o, r) {
        var n =
          t.call(
            this,
            e,
            new a.DummyRegion(e),
            r,
            function (t) {
              return n.highlighter.isMactionNode(t);
            },
            function () {},
          ) || this;
        return (n.document = e), (n.node = r), n;
      }
      return n(e, t), e;
    })(c);
    e.FlameHoverer = p;
  },
  function (t, e, o) {
    'use strict';
    var r,
      n =
        (this && this.__extends) ||
        ((r = function (t, e) {
          return (r =
            Object.setPrototypeOf ||
            ({ __proto__: [] } instanceof Array &&
              function (t, e) {
                t.__proto__ = e;
              }) ||
            function (t, e) {
              for (var o in e) e.hasOwnProperty(o) && (t[o] = e[o]);
            })(t, e);
        }),
        function (t, e) {
          function o() {
            this.constructor = t;
          }
          r(t, e),
            (t.prototype = null === e ? Object.create(e) : ((o.prototype = e.prototype), new o()));
        });
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.TreeColorer = e.FlameColorer = e.AbstractTreeExplorer = void 0);
    var i = o(1);
    o(0);
    var a = (function (t) {
      function e(e, o, r, n) {
        var i = t.call(this, e, null, r) || this;
        return (i.document = e), (i.region = o), (i.node = r), (i.mml = n), (i.stoppable = !1), i;
      }
      return (
        n(e, t),
        (e.prototype.Attach = function () {
          t.prototype.Attach.call(this), this.Start();
        }),
        (e.prototype.Detach = function () {
          this.Stop(), t.prototype.Detach.call(this);
        }),
        e
      );
    })(i.AbstractExplorer);
    e.AbstractTreeExplorer = a;
    var s = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return (
        n(e, t),
        (e.prototype.Start = function () {
          this.active || ((this.active = !0), this.highlighter.highlightAll(this.node));
        }),
        (e.prototype.Stop = function () {
          this.active && this.highlighter.unhighlightAll(this.node), (this.active = !1);
        }),
        e
      );
    })(a);
    e.FlameColorer = s;
    var l = (function (t) {
      function e() {
        return (null !== t && t.apply(this, arguments)) || this;
      }
      return (
        n(e, t),
        (e.prototype.Start = function () {
          if (!this.active) {
            this.active = !0;
            var t = sre.SpeechGeneratorFactory.generator('Color');
            this.node.hasAttribute('hasforegroundcolor') ||
              (t.generateSpeech(this.node, this.mml),
              this.node.setAttribute('hasforegroundcolor', 'true')),
              this.highlighter.colorizeAll(this.node);
          }
        }),
        (e.prototype.Stop = function () {
          this.active && this.highlighter.uncolorizeAll(this.node), (this.active = !1);
        }),
        e
      );
    })(a);
    e.TreeColorer = l;
  },
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.protoItem = MathJax._.core.MathItem.protoItem),
      (e.AbstractMathItem = MathJax._.core.MathItem.AbstractMathItem),
      (e.STATE = MathJax._.core.MathItem.STATE),
      (e.newState = MathJax._.core.MathItem.newState);
  },
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.EnrichedMathItemMixin = MathJax._.a11y['semantic-enrich'].EnrichedMathItemMixin),
      (e.EnrichedMathDocumentMixin = MathJax._.a11y['semantic-enrich'].EnrichedMathDocumentMixin),
      (e.EnrichHandler = MathJax._.a11y['semantic-enrich'].EnrichHandler);
  },
  function (t, e, o) {
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
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.DATAMJX = MathJax._.core.MmlTree.SerializedMmlVisitor.DATAMJX),
      (e.toEntity = MathJax._.core.MmlTree.SerializedMmlVisitor.toEntity),
      (e.SerializedMmlVisitor = MathJax._.core.MmlTree.SerializedMmlVisitor.SerializedMmlVisitor);
  },
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.MJContextMenu = MathJax._.ui.menu.MJContextMenu.MJContextMenu);
  },
  function (t, e, o) {
    'use strict';
    Object.defineProperty(e, '__esModule', { value: !0 }),
      (e.CssStyles = MathJax._.util.StyleList.CssStyles);
  },
  function (t, e, o) {
    'use strict';
    o.r(e);
    var r = o(4),
      n = o(3),
      i = o(1),
      a = o(5),
      s = o(6),
      l = o(2),
      c = o(7),
      h = o(0);
    Object(r.combineWithMathJax)({
      _: {
        a11y: {
          explorer_ts: n,
          explorer: { Explorer: i, KeyExplorer: a, MouseExplorer: s, Region: l, TreeExplorer: c },
          sre: h,
        },
      },
    }),
      MathJax.startup &&
        MathJax.startup.extendHandler(function (t) {
          return Object(n.ExplorerHandler)(t);
        });
  },
]);
