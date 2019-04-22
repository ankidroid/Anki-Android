/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/GeometricShapes.js
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
  "MathJax_AMS": {
    0x25A0: [  // BLACK SQUARE
      [5,5,0],[6,6,0],[8,7,0],[9,9,0],[10,10,0],[12,12,0],[15,14,0],[17,16,0],
      [20,20,0],[24,23,0],[29,27,0],[34,32,0],[40,39,0],[48,46,0]
    ],
    0x25A1: [  // WHITE SQUARE
      [5,5,0],[6,6,0],[7,7,0],[9,8,0],[10,10,0],[12,12,0],[14,14,0],[17,16,0],
      [21,20,0],[24,23,0],[29,27,0],[34,32,0],[40,39,0],[48,46,0]
    ],
    0x25B2: [  // BLACK UP-POINTING TRIANGLE
      [5,5,1],[6,6,1],[7,7,1],[8,8,1],[9,9,1],[11,11,1],[13,13,1],[15,15,1],
      [18,17,1],[21,20,1],[25,24,1],[30,28,1],[36,34,2],[42,40,2]
    ],
    0x25B3: [  // WHITE UP-POINTING TRIANGLE
      [5,5,0],[6,5,0],[7,6,0],[8,8,1],[9,10,1],[11,11,1],[13,13,1],[15,15,1],
      [18,17,1],[21,20,1],[25,24,1],[30,28,1],[36,34,2],[42,41,2]
    ],
    0x25B6: [  // BLACK RIGHT-POINTING TRIANGLE
      [5,5,1],[6,6,1],[7,7,1],[9,8,1],[10,9,1],[12,10,1],[14,12,1],[17,14,1],
      [20,17,2],[23,20,2],[28,24,2],[33,28,2],[39,33,3],[46,39,3]
    ],
    0x25BC: [  // BLACK DOWN-POINTING TRIANGLE
      [5,5,1],[6,6,1],[7,7,1],[8,8,1],[9,9,1],[11,11,1],[13,13,1],[15,15,1],
      [18,17,1],[21,20,1],[25,24,1],[30,28,1],[36,33,1],[42,40,2]
    ],
    0x25BD: [  // WHITE DOWN-POINTING TRIANGLE
      [5,5,1],[6,6,1],[7,7,1],[8,8,1],[9,9,1],[11,11,1],[13,13,1],[15,15,1],
      [18,17,1],[21,20,1],[25,24,1],[30,28,1],[36,33,1],[42,40,2]
    ],
    0x25C0: [  // BLACK LEFT-POINTING TRIANGLE
      [5,5,1],[6,6,1],[7,7,1],[9,8,1],[10,9,1],[12,10,1],[14,12,1],[17,14,1],
      [20,17,2],[23,20,2],[28,24,2],[33,28,2],[39,33,3],[46,39,3]
    ],
    0x25CA: [  // LOZENGE
      [5,6,1],[6,8,2],[7,10,2],[8,11,2],[9,12,2],[11,15,3],[12,17,3],[15,21,4],
      [17,24,4],[21,29,5],[25,35,6],[29,41,7],[34,48,8],[41,57,9]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GeometricShapes.js");
