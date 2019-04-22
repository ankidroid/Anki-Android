/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/SuppMathOperators.js
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
    0x2A3F: [  // AMALGAMATION OR COPRODUCT
      [5,5,0],[6,6,0],[8,7,0],[9,9,0],[10,10,0],[12,12,0],[15,14,0],[17,16,0],
      [20,19,0],[24,23,0],[29,27,0],[34,32,0],[40,38,0],[48,45,0]
    ],
    0x2AAF: [  // PRECEDES ABOVE SINGLE-LINE EQUALS SIGN
      [5,6,1],[6,7,1],[7,9,2],[9,10,2],[10,11,2],[12,14,3],[14,16,3],[17,18,3],
      [20,22,4],[23,26,5],[28,31,6],[33,37,7],[39,44,8],[46,52,10]
    ],
    0x2AB0: [  // SUCCEEDS ABOVE SINGLE-LINE EQUALS SIGN
      [5,6,1],[6,7,1],[7,9,2],[9,10,2],[10,11,2],[12,14,3],[14,16,3],[17,19,4],
      [20,22,4],[23,26,5],[28,31,6],[33,37,7],[39,44,8],[46,52,10]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/SuppMathOperators.js");
