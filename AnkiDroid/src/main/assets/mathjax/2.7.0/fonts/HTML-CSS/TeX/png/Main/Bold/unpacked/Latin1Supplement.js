/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/Latin1Supplement.js
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
    0xA0: [  // NO-BREAK SPACE
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0]
    ],
    0xA8: [  // DIAERESIS
      [4,2,-3],[4,2,-4],[5,2,-6],[6,2,-6],[7,3,-6],[8,3,-9],[10,4,-10],[12,4,-13],
      [14,5,-15],[16,6,-17],[19,7,-21],[23,8,-24],[27,9,-29],[32,11,-35]
    ],
    0xAC: [  // NOT SIGN
      [5,3,0],[6,3,-1],[7,4,0],[9,4,0],[10,5,0],[12,6,-1],[14,8,0],[17,9,-1],
      [20,9,-2],[24,11,-2],[28,13,-2],[33,15,-2],[39,18,-3],[47,22,-4]
    ],
    0xAF: [  // MACRON
      [4,1,-3],[5,1,-5],[5,1,-6],[6,1,-6],[7,1,-7],[9,2,-9],[10,3,-10],[12,2,-13],
      [14,2,-16],[17,3,-18],[20,3,-22],[23,4,-25],[28,4,-30],[33,5,-36]
    ],
    0xB0: [  // DEGREE SIGN
      [3,1,-4],[4,2,-4],[5,2,-6],[5,2,-6],[6,2,-7],[7,3,-9],[9,3,-11],[10,4,-13],
      [12,6,-14],[14,6,-17],[17,7,-21],[20,8,-24],[23,9,-29],[28,11,-35]
    ],
    0xB1: [  // PLUS-MINUS SIGN
      [6,6,1],[7,8,1],[9,9,1],[10,9,1],[12,11,1],[14,15,1],[17,16,2],[20,20,2],
      [23,22,1],[28,26,1],[33,31,1],[39,36,2],[46,42,2],[55,51,2]
    ],
    0xB4: [  // ACUTE ACCENT
      [4,2,-3],[4,2,-4],[5,3,-5],[6,3,-5],[7,3,-6],[8,4,-8],[9,5,-9],[11,5,-12],
      [13,6,-14],[16,7,-16],[18,9,-19],[22,10,-23],[26,12,-27],[31,14,-33]
    ],
    0xD7: [  // MULTIPLICATION SIGN
      [6,4,0],[7,5,0],[8,7,0],[9,7,0],[11,8,0],[13,10,0],[15,12,0],[17,14,1],
      [21,17,1],[24,19,1],[29,23,1],[34,26,1],[41,31,2],[48,37,2]
    ],
    0xF7: [  // DIVISION SIGN
      [6,6,1],[7,6,1],[9,8,1],[10,8,1],[12,9,1],[14,12,2],[17,14,2],[20,17,2],
      [23,20,3],[28,23,3],[33,28,4],[39,32,4],[46,39,5],[55,46,6]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/Latin1Supplement.js");
