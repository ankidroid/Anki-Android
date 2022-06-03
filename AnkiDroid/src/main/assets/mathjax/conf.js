"use strict";
// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
/// <reference types="./mathjax-types" />
const packages = ["noerrors", "mathtools", "mhchem"];
function packagesForLoading(packages) {
    return packages.map((value) => `[tex]/${value}`);
}
window.MathJax = {
    tex: {
        displayMath: [["\\[", "\\]"]],
        processEscapes: false,
        processEnvironments: false,
        processRefs: false,
        packages: {
            "[+]": packages,
        },
    },
    loader: {
        load: packagesForLoading(packages),
        paths: {
            mathjax: "/android_asset/mathjax",
        },
    },
    startup: {
        typeset: false,
    },
};
