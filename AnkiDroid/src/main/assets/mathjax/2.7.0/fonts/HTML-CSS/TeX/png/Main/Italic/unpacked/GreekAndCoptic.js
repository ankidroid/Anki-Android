/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Italic/GreekAndCoptic.js
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
  "MathJax_Main-italic": {
    0x393: [  // GREEK CAPITAL LETTER GAMMA
      [5,5,0],[6,6,0],[7,6,0],[9,8,0],[10,9,0],[12,11,0],[14,14,0],[17,15,0],
      [20,19,0],[24,23,0],[28,26,0],[33,33,0],[39,38,0],[47,45,0]
    ],
    0x394: [  // GREEK CAPITAL LETTER DELTA
      [6,5,0],[7,6,0],[8,6,0],[9,8,0],[11,9,0],[13,11,0],[15,14,0],[18,15,0],
      [21,20,0],[25,24,0],[30,28,0],[35,34,0],[42,41,0],[50,47,0]
    ],
    0x398: [  // GREEK CAPITAL LETTER THETA
      [6,5,0],[7,6,0],[8,6,0],[10,8,0],[11,9,0],[14,11,0],[16,14,0],[19,16,1],
      [22,21,1],[26,25,1],[31,27,0],[37,35,1],[44,42,1],[52,48,1]
    ],
    0x39B: [  // GREEK CAPITAL LETTER LAMDA
      [5,5,0],[6,7,1],[7,7,1],[8,9,1],[9,10,1],[11,12,1],[13,15,1],[15,16,1],
      [18,21,1],[22,25,1],[26,28,1],[31,35,1],[36,42,1],[43,48,1]
    ],
    0x39E: [  // GREEK CAPITAL LETTER XI
      [6,6,1],[7,7,1],[8,7,1],[9,9,1],[11,10,1],[13,12,1],[15,15,1],[18,16,1],
      [21,20,1],[25,24,1],[30,27,1],[36,33,0],[42,40,1],[50,46,1]
    ],
    0x3A0: [  // GREEK CAPITAL LETTER PI
      [6,5,0],[8,6,0],[9,6,0],[11,8,0],[12,9,0],[15,11,0],[17,14,0],[20,15,0],
      [24,19,0],[29,23,0],[34,26,0],[40,33,0],[48,39,0],[57,45,0]
    ],
    0x3A3: [  // GREEK CAPITAL LETTER SIGMA
      [6,5,0],[7,6,0],[8,6,0],[10,8,0],[11,9,0],[13,11,0],[16,14,0],[19,15,0],
      [22,19,0],[26,23,0],[31,26,0],[37,33,0],[44,39,0],[52,45,0]
    ],
    0x3A5: [  // GREEK CAPITAL LETTER UPSILON
      [6,5,0],[7,6,0],[9,6,0],[10,8,0],[12,9,0],[14,11,0],[17,14,0],[20,15,0],
      [23,20,0],[28,24,0],[33,27,0],[39,33,0],[46,40,0],[55,46,0]
    ],
    0x3A6: [  // GREEK CAPITAL LETTER PHI
      [6,5,0],[7,6,0],[8,6,0],[9,8,0],[11,9,0],[13,11,0],[15,14,0],[17,15,0],
      [21,19,0],[24,23,0],[29,26,0],[34,33,0],[41,39,0],[48,45,0]
    ],
    0x3A8: [  // GREEK CAPITAL LETTER PSI
      [6,5,0],[7,6,0],[9,6,0],[10,8,0],[12,9,0],[14,11,0],[17,14,0],[20,15,0],
      [23,19,0],[28,23,0],[33,26,0],[39,33,0],[46,39,0],[55,45,0]
    ],
    0x3A9: [  // GREEK CAPITAL LETTER OMEGA
      [6,5,0],[7,6,0],[8,6,0],[9,8,0],[11,9,0],[13,11,0],[15,14,0],[18,15,0],
      [21,20,0],[25,24,0],[30,27,0],[36,34,0],[42,40,0],[50,47,0]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GreekAndCoptic.js");
