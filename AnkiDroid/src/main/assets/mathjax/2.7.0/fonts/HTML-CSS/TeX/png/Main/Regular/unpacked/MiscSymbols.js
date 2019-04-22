/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/MiscSymbols.js
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
    0x2660: [  // BLACK SPADE SUIT
      [5,7,1],[7,9,2],[8,10,2],[9,11,2],[10,13,2],[12,16,3],[14,18,3],[17,20,3],
      [20,25,4],[24,29,5],[29,35,6],[34,40,6],[40,49,8],[48,57,9]
    ],
    0x2661: [  // WHITE HEART SUIT
      [5,6,1],[6,7,1],[7,8,1],[9,10,1],[10,11,1],[12,13,1],[15,15,1],[17,18,1],
      [21,21,1],[24,26,2],[29,31,2],[34,36,2],[40,42,2],[48,51,3]
    ],
    0x2662: [  // WHITE DIAMOND SUIT
      [5,8,2],[6,9,2],[8,10,2],[9,11,2],[10,14,3],[12,16,3],[15,19,4],[17,21,4],
      [20,26,5],[24,30,6],[29,36,7],[34,42,8],[40,50,9],[48,59,11]
    ],
    0x2663: [  // BLACK CLUB SUIT
      [6,7,1],[7,9,2],[8,10,2],[9,11,2],[11,13,2],[13,16,3],[15,18,3],[18,20,3],
      [21,25,4],[25,29,5],[30,34,5],[35,40,6],[42,49,8],[50,57,9]
    ],
    0x266D: [  // MUSIC FLAT SIGN
      [3,7,1],[3,8,1],[4,9,1],[4,10,1],[5,12,1],[6,14,1],[7,16,1],[8,19,1],
      [10,22,1],[11,26,1],[14,31,1],[16,37,2],[19,44,2],[22,52,2]
    ],
    0x266E: [  // MUSIC NATURAL SIGN
      [3,8,2],[3,9,2],[3,11,3],[4,12,3],[5,15,4],[6,17,4],[7,20,5],[8,24,6],
      [9,28,7],[11,33,8],[13,38,9],[16,46,11],[19,54,13],[22,64,15]
    ],
    0x266F: [  // MUSIC SHARP SIGN
      [3,7,2],[3,8,2],[4,11,3],[4,12,3],[5,14,4],[6,16,4],[7,20,5],[8,23,6],
      [10,27,7],[11,32,8],[13,38,9],[16,45,11],[19,53,13],[22,63,15]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/MiscSymbols.js");
