/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/SuppMathOperators.js
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
  "MathJax_Main-bold": {
    0x2A3F: [  // AMALGAMATION OR COPRODUCT
      [6,5,0],[7,6,0],[9,7,0],[11,8,0],[12,10,0],[15,12,0],[17,14,0],[20,16,0],
      [24,19,0],[29,23,0],[34,27,0],[40,32,0],[48,38,0],[57,45,0]
    ],
    0x2AAF: [  // PRECEDES ABOVE SINGLE-LINE EQUALS SIGN
      [6,7,2],[7,8,2],[8,9,2],[10,12,3],[12,13,3],[14,16,4],[16,18,4],[19,22,5],
      [23,26,6],[27,30,7],[32,36,8],[38,43,10],[45,51,12],[53,60,14]
    ],
    0x2AB0: [  // SUCCEEDS ABOVE SINGLE-LINE EQUALS SIGN
      [6,7,2],[7,8,2],[8,9,2],[10,12,3],[11,13,3],[14,16,4],[16,18,4],[19,22,5],
      [22,26,6],[27,30,7],[32,36,8],[37,43,10],[44,50,11],[53,59,13]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/SuppMathOperators.js");
