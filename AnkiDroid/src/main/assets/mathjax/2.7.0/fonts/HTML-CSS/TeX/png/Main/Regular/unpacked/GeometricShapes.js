/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/GeometricShapes.js
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
    0x25B3: [  // WHITE UP-POINTING TRIANGLE
      [6,5,0],[7,6,0],[9,8,0],[10,9,0],[12,10,0],[14,12,0],[17,14,0],[20,17,0],
      [23,20,0],[28,24,0],[33,29,0],[39,34,0],[46,40,0],[55,48,0]
    ],
    0x25B9: [  // WHITE RIGHT-POINTING SMALL TRIANGLE
      [4,5,1],[5,6,1],[5,6,1],[6,7,1],[7,8,1],[9,10,1],[10,11,1],[12,13,1],
      [14,15,1],[17,18,1],[20,21,1],[23,25,1],[28,29,1],[33,35,1]
    ],
    0x25BD: [  // WHITE DOWN-POINTING TRIANGLE
      [6,6,2],[7,6,2],[9,8,3],[10,9,3],[12,10,3],[14,13,4],[17,15,5],[20,17,5],
      [23,20,6],[28,25,8],[33,29,9],[39,35,11],[46,40,12],[55,48,15]
    ],
    0x25C3: [  // WHITE LEFT-POINTING SMALL TRIANGLE
      [4,5,1],[4,6,1],[5,6,1],[6,7,1],[7,8,1],[8,10,1],[9,11,1],[11,13,1],
      [14,15,1],[16,18,1],[19,21,1],[22,25,1],[27,29,1],[32,35,1]
    ],
    0x25EF: [  // LARGE CIRCLE
      [7,7,2],[8,8,2],[10,10,2],[11,12,3],[14,13,3],[16,16,4],[19,18,4],[22,22,5],
      [27,26,6],[32,32,8],[37,37,9],[45,44,10],[53,52,12],[63,63,15]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GeometricShapes.js");
