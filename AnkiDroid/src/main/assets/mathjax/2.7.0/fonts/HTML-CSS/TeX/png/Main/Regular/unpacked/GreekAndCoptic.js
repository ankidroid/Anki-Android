/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/GreekAndCoptic.js
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
    0x393: [  // GREEK CAPITAL LETTER GAMMA
      [4,5,0],[5,6,0],[6,8,0],[7,8,0],[8,9,0],[10,12,0],[12,14,0],[14,17,0],
      [16,20,0],[20,23,0],[23,27,0],[27,32,0],[32,38,0],[39,46,0]
    ],
    0x394: [  // GREEK CAPITAL LETTER DELTA
      [6,5,0],[7,6,0],[8,8,0],[10,8,0],[11,9,0],[14,12,0],[16,14,0],[19,18,0],
      [22,21,0],[26,24,0],[31,29,0],[37,34,0],[44,40,0],[52,48,0]
    ],
    0x398: [  // GREEK CAPITAL LETTER THETA
      [5,5,0],[6,6,0],[8,8,0],[9,8,0],[10,9,0],[12,12,0],[15,14,0],[17,19,1],
      [20,22,1],[24,25,1],[29,29,0],[34,34,1],[40,41,1],[48,49,1]
    ],
    0x39B: [  // GREEK CAPITAL LETTER LAMDA
      [5,5,0],[6,6,0],[7,8,0],[8,8,0],[10,9,0],[11,12,0],[13,14,0],[16,18,0],
      [19,21,0],[22,24,0],[26,29,0],[31,34,0],[37,40,0],[44,48,0]
    ],
    0x39E: [  // GREEK CAPITAL LETTER XI
      [5,5,0],[6,6,0],[7,8,0],[8,8,0],[9,9,0],[11,12,0],[13,14,0],[15,17,0],
      [18,20,0],[21,23,0],[25,27,0],[29,32,0],[35,38,0],[42,46,0]
    ],
    0x3A0: [  // GREEK CAPITAL LETTER PI
      [5,5,0],[6,6,0],[8,8,0],[9,8,0],[11,9,0],[13,12,0],[15,14,0],[17,17,0],
      [21,20,0],[24,23,0],[29,27,0],[34,32,0],[41,38,0],[48,46,0]
    ],
    0x3A3: [  // GREEK CAPITAL LETTER SIGMA
      [5,5,0],[6,6,0],[7,8,0],[8,8,0],[10,9,0],[12,12,0],[13,14,0],[16,17,0],
      [19,20,0],[22,23,0],[27,27,0],[31,32,0],[37,38,0],[44,46,0]
    ],
    0x3A5: [  // GREEK CAPITAL LETTER UPSILON
      [5,5,0],[6,6,0],[8,8,0],[9,8,0],[10,9,0],[12,12,0],[14,14,0],[17,18,0],
      [20,21,0],[24,24,0],[29,28,0],[34,33,0],[40,40,0],[48,47,0]
    ],
    0x3A6: [  // GREEK CAPITAL LETTER PHI
      [5,5,0],[6,6,0],[7,8,0],[8,8,0],[10,9,0],[12,12,0],[13,14,0],[16,17,0],
      [19,20,0],[22,23,0],[27,27,0],[31,32,0],[37,38,0],[44,46,0]
    ],
    0x3A8: [  // GREEK CAPITAL LETTER PSI
      [6,5,0],[7,6,0],[8,8,0],[9,8,0],[11,9,0],[13,12,0],[15,14,0],[17,17,0],
      [21,20,0],[24,23,0],[29,27,0],[34,32,0],[41,38,0],[48,46,0]
    ],
    0x3A9: [  // GREEK CAPITAL LETTER OMEGA
      [5,5,0],[6,6,0],[7,8,0],[8,8,0],[10,9,0],[12,12,0],[14,14,0],[16,18,0],
      [19,21,0],[23,24,0],[27,29,0],[32,33,0],[38,40,0],[45,47,0]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GreekAndCoptic.js");
