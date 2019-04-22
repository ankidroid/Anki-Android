/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/MiscSymbols.js
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
    0x2660: [  // BLACK SPADE SUIT
      [6,6,1],[7,8,2],[9,10,2],[10,11,2],[12,12,2],[14,15,3],[17,18,3],[20,20,3],
      [23,24,4],[28,29,5],[33,35,6],[39,40,6],[46,48,8],[55,57,9]
    ],
    0x2661: [  // WHITE HEART SUIT
      [6,6,1],[7,7,1],[8,8,1],[10,10,1],[12,11,1],[14,13,1],[17,15,1],[20,18,1],
      [23,21,1],[28,26,2],[33,29,1],[39,36,2],[46,42,2],[55,49,2]
    ],
    0x2662: [  // WHITE DIAMOND SUIT
      [6,7,2],[7,8,2],[9,10,2],[10,12,3],[12,13,3],[14,15,3],[17,18,3],[20,21,4],
      [23,25,5],[28,29,5],[33,36,7],[39,42,8],[46,49,9],[55,59,11]
    ],
    0x2663: [  // BLACK CLUB SUIT
      [6,6,1],[8,8,2],[9,10,2],[11,11,2],[12,12,2],[15,15,3],[17,18,3],[20,20,3],
      [24,24,4],[29,29,5],[34,35,6],[40,41,7],[48,47,7],[57,57,9]
    ],
    0x266D: [  // MUSIC FLAT SIGN
      [3,7,1],[4,8,1],[4,9,1],[5,10,1],[6,12,1],[7,14,1],[8,16,1],[9,19,1],
      [11,22,1],[13,26,1],[15,31,1],[18,36,1],[22,43,1],[25,52,2]
    ],
    0x266E: [  // MUSIC NATURAL SIGN
      [3,8,2],[3,9,2],[4,11,3],[5,12,3],[6,15,4],[7,17,4],[8,20,5],[10,24,6],
      [11,28,7],[13,33,8],[16,38,9],[19,46,11],[22,54,13],[27,64,15]
    ],
    0x266F: [  // MUSIC SHARP SIGN
      [3,7,2],[4,8,2],[4,11,3],[5,12,3],[6,14,4],[7,16,4],[8,20,5],[9,23,6],
      [11,27,7],[13,32,8],[15,38,9],[18,45,11],[21,53,13],[25,63,15]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/MiscSymbols.js");
