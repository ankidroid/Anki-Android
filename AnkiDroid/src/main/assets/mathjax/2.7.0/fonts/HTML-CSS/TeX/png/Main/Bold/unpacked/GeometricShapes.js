/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/GeometricShapes.js
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
    0x25B3: [  // WHITE UP-POINTING TRIANGLE
      [7,5,0],[8,6,0],[10,7,0],[12,9,0],[14,10,0],[16,12,0],[19,14,0],[23,17,0],
      [27,20,0],[32,24,0],[38,28,0],[45,34,0],[53,40,0],[63,47,0]
    ],
    0x25B9: [  // WHITE RIGHT-POINTING SMALL TRIANGLE
      [4,5,1],[5,6,1],[6,7,1],[7,8,1],[8,9,1],[10,10,1],[11,12,1],[13,14,1],
      [15,17,2],[18,20,2],[22,24,2],[25,28,2],[30,33,3],[36,39,3]
    ],
    0x25BD: [  // WHITE DOWN-POINTING TRIANGLE
      [7,6,2],[8,7,2],[10,8,3],[12,9,3],[14,10,3],[16,13,4],[19,14,4],[23,18,6],
      [27,20,6],[32,25,8],[38,29,9],[45,34,10],[53,40,12],[63,47,14]
    ],
    0x25C3: [  // WHITE LEFT-POINTING SMALL TRIANGLE
      [4,5,1],[5,6,1],[6,7,1],[7,8,1],[8,9,1],[9,10,1],[11,12,1],[13,14,1],
      [15,17,2],[18,20,2],[22,24,2],[26,28,2],[30,33,3],[36,39,3]
    ],
    0x25EF: [  // LARGE CIRCLE
      [8,7,2],[9,8,2],[11,9,2],[13,12,3],[15,13,3],[18,16,4],[22,19,5],[26,22,5],
      [30,26,6],[36,31,7],[43,37,9],[51,44,10],[60,52,12],[72,61,14]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GeometricShapes.js");
