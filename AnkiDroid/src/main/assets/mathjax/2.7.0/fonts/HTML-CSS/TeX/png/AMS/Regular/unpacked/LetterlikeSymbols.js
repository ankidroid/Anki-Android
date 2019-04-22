/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/LetterlikeSymbols.js
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
    0x210F: [  // stix-/hbar - Planck's over 2pi
      [4,6,1],[5,7,1],[6,7,0],[7,10,1],[8,11,1],[10,13,1],[11,15,1],[13,18,1],
      [16,21,1],[19,24,1],[22,29,1],[27,34,1],[32,40,1],[37,47,1]
    ],
    0x2127: [  // INVERTED OHM SIGN
      [5,5,0],[6,6,0],[7,7,0],[8,10,1],[10,11,1],[12,13,1],[14,15,1],[16,17,1],
      [19,20,1],[23,24,1],[27,28,1],[32,34,2],[38,40,2],[45,47,2]
    ],
    0x2132: [  // TURNED CAPITAL F
      [4,5,0],[4,6,0],[5,7,0],[6,9,0],[7,10,0],[9,12,0],[10,14,0],[12,17,0],
      [14,21,1],[17,24,1],[20,29,1],[24,34,1],[28,40,1],[33,47,1]
    ],
    0x2136: [  // BET SYMBOL
      [6,7,1],[7,8,1],[8,9,1],[9,11,1],[11,12,1],[13,14,1],[15,16,1],[17,19,1],
      [21,23,1],[24,27,1],[28,31,1],[34,37,1],[40,45,2],[48,53,2]
    ],
    0x2137: [  // GIMEL SYMBOL
      [4,7,1],[5,8,1],[6,9,1],[6,11,1],[7,12,1],[8,14,1],[10,16,1],[11,20,2],
      [13,24,2],[15,28,2],[18,32,2],[21,38,2],[26,46,3],[30,54,3]
    ],
    0x2138: [  // DALET SYMBOL
      [5,7,1],[6,8,1],[7,9,1],[8,11,1],[9,12,1],[11,14,1],[13,16,1],[15,19,1],
      [18,24,2],[22,28,2],[26,32,2],[30,39,3],[36,46,3],[43,54,3]
    ],
    0x2141: [  // TURNED SANS-SERIF CAPITAL G
      [4,6,1],[5,7,1],[6,8,1],[7,10,1],[8,11,1],[10,13,1],[12,15,1],[14,18,1],
      [16,21,1],[19,25,1],[23,30,2],[27,35,2],[32,41,2],[38,49,2]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/LetterlikeSymbols.js");
