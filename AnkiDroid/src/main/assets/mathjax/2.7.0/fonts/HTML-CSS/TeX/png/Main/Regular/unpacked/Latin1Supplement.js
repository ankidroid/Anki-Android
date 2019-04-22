/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/Latin1Supplement.js
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
    0xA0: [  // NO-BREAK SPACE
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0]
    ],
    0xA8: [  // DIAERESIS
      [3,1,-4],[4,2,-4],[4,2,-6],[5,2,-6],[6,2,-7],[7,3,-9],[8,3,-11],[10,3,-14],
      [12,4,-16],[14,4,-19],[16,5,-22],[19,6,-26],[23,7,-31],[27,8,-37]
    ],
    0xAC: [  // NOT SIGN
      [5,3,0],[6,3,0],[6,4,0],[8,4,0],[9,4,-1],[11,5,-2],[12,6,-1],[15,7,-2],
      [17,8,-2],[21,10,-3],[24,11,-4],[29,13,-4],[34,16,-5],[41,18,-6]
    ],
    0xAF: [  // MACRON
      [3,1,-3],[4,1,-5],[5,1,-6],[6,1,-6],[6,1,-7],[8,1,-10],[9,1,-11],[10,2,-13],
      [12,3,-15],[15,2,-18],[17,2,-22],[20,3,-26],[24,3,-30],[29,4,-36]
    ],
    0xB0: [  // DEGREE SIGN
      [3,2,-3],[3,2,-4],[4,2,-6],[5,2,-6],[5,3,-6],[6,3,-9],[7,4,-10],[9,5,-13],
      [10,6,-15],[12,7,-17],[14,8,-22],[17,8,-25],[20,10,-30],[24,12,-36]
    ],
    0xB1: [  // PLUS-MINUS SIGN
      [5,5,0],[6,6,0],[8,8,0],[9,8,0],[10,9,0],[12,12,0],[15,14,0],[17,17,0],
      [20,20,0],[24,23,0],[29,27,0],[34,31,0],[40,37,0],[48,45,0]
    ],
    0xB4: [  // ACUTE ACCENT
      [3,2,-3],[4,2,-4],[4,3,-5],[5,3,-5],[6,3,-6],[7,4,-8],[8,5,-9],[10,6,-12],
      [11,7,-14],[13,7,-17],[16,9,-19],[19,10,-23],[22,12,-28],[26,13,-34]
    ],
    0xD7: [  // MULTIPLICATION SIGN
      [5,4,0],[6,5,0],[7,6,0],[8,6,0],[9,7,0],[11,9,0],[13,10,0],[15,12,0],
      [18,14,0],[21,17,0],[25,20,0],[30,23,0],[36,27,-1],[42,33,-1]
    ],
    0xF7: [  // DIVISION SIGN
      [5,4,0],[6,6,0],[8,7,0],[9,7,0],[10,8,0],[12,10,0],[15,12,0],[17,15,1],
      [20,18,1],[24,19,1],[29,23,1],[34,27,2],[40,32,2],[48,39,2]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/Latin1Supplement.js");
