/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/LetterlikeSymbols.js
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
    0x210F: [  // stix-/hbar - Planck's over 2pi
      [4,6,1],[5,7,1],[6,7,0],[7,10,1],[8,11,1],[10,13,1],[11,15,1],[13,18,1],
      [16,21,1],[19,25,1],[22,29,1],[26,34,1],[31,40,1],[37,47,1]
    ],
    0x2111: [  // BLACK-LETTER CAPITAL I
      [5,6,1],[6,7,1],[7,8,1],[8,9,0],[10,11,1],[12,13,1],[14,15,1],[17,18,1],
      [20,21,1],[23,25,1],[28,29,1],[33,34,1],[39,40,1],[46,48,1]
    ],
    0x2113: [  // SCRIPT SMALL L
      [4,5,0],[4,7,1],[4,8,1],[5,10,1],[6,11,1],[7,13,1],[8,15,1],[10,18,1],
      [12,21,1],[14,25,1],[16,29,1],[19,34,1],[23,41,2],[27,49,2]
    ],
    0x2118: [  // SCRIPT CAPITAL P
      [5,5,2],[5,6,2],[6,7,2],[8,9,3],[9,10,3],[11,12,4],[13,13,4],[15,16,5],
      [18,19,6],[21,23,8],[25,27,9],[29,32,11],[35,37,12],[41,45,15]
    ],
    0x211C: [  // BLACK-LETTER CAPITAL R
      [6,5,0],[6,6,0],[7,8,0],[9,9,0],[11,11,1],[13,13,1],[15,16,1],[17,18,1],
      [21,22,1],[24,25,1],[29,30,1],[34,35,1],[40,41,1],[48,50,2]
    ],
    0x2135: [  // ALEF SYMBOL
      [4,5,0],[5,6,0],[6,7,0],[7,9,0],[8,10,0],[10,12,0],[11,14,0],[13,17,0],
      [16,20,0],[19,23,0],[22,28,0],[26,33,0],[31,39,0],[37,46,0]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/LetterlikeSymbols.js");
