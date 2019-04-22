/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/MiscMathSymbolsA.js
 *  
 *  Defines the image size data needed for the HTML-CSS OutputJax
 *  to display mathematics using fallback images when the fonts
 *  are not availble to the client browser.
 *
 *  ---------------------------------------------------------------------
 *
 *  Copyright (c) 2009-2013 The MathJax Consortium
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

MathJax.OutputJax["HTML-CSS"].defineImageData({
  "MathJax_Main": {
    0x27E8: [  // MATHEMATICAL LEFT ANGLE BRACKET
      [3,8,2],[3,10,3],[4,11,3],[4,12,3],[5,15,4],[6,18,5],[7,20,5],[8,24,6],
      [10,28,7],[11,34,9],[14,40,10],[16,47,12],[19,56,14],[22,67,17]
    ],
    0x27E9: [  // MATHEMATICAL RIGHT ANGLE BRACKET
      [2,8,2],[3,10,3],[3,11,3],[4,12,3],[4,15,4],[5,18,5],[6,20,5],[7,24,6],
      [8,28,7],[10,34,9],[11,40,10],[13,47,12],[16,56,14],[19,67,17]
    ],
    0x27EE: [  // MATHEMATICAL LEFT FLATTENED PARENTHESIS
      [3,8,2],[3,9,2],[4,11,3],[5,12,3],[5,15,4],[6,17,4],[8,20,5],[9,24,6],
      [10,28,7],[12,33,8],[14,40,10],[17,47,12],[20,56,14],[24,65,16]
    ],
    0x27EF: [  // MATHEMATICAL RIGHT FLATTENED PARENTHESIS
      [2,8,2],[2,9,2],[3,11,3],[3,12,3],[4,15,4],[4,17,4],[5,20,5],[6,24,6],
      [7,28,7],[8,33,8],[10,40,10],[12,46,11],[14,55,13],[16,65,16]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/MiscMathSymbolsA.js");
