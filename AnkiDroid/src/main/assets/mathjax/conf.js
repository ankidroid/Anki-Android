window.MathJax = {
  tex: {
    displayMath: [["\\[", "\\]"]],
    processRefs: false,
    processEnvironments: false,
    packages: {
      "[+]": ["noerrors", "mhchem"],
    },
    // Use \color from version 2 of MathJax
    autoload: {
      color: [],            // Don't autoload the color extension
      colorv2: ['color']    // Autoload colorv2 on the first use of \color
    }
  },
  startup: {
    typeset: false,
    pageReady: () => {
      return MathJax.startup.defaultPageReady();
    },
  },
  options: {
    renderActions: {
      addMenu: [],
      checkLoading: [],
    },
    ignoreHtmlClass: "tex2jax_ignore",
    processHtmlClass: "tex2jax_process",
  },
  loader: {
    load: ["[tex]/noerrors", "[tex]/mhchem"],
  },
};
