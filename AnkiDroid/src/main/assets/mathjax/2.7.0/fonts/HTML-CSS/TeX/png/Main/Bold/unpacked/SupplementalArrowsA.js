/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/SupplementalArrowsA.js
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
    0x27F5: [  // LONG LEFTWARDS ARROW
      [13,5,1],[15,5,0],[18,7,1],[21,7,1],[25,9,1],[29,10,1],[34,11,1],[41,13,1],
      [49,16,1],[58,18,1],[69,22,1],[81,25,1],[97,31,2],[115,36,2]
    ],
    0x27F6: [  // LONG RIGHTWARDS ARROW
      [13,5,1],[15,5,0],[18,7,1],[21,7,1],[25,9,1],[30,9,0],[35,11,1],[42,14,1],
      [49,16,1],[59,18,1],[70,22,1],[83,26,1],[98,30,1],[117,36,1]
    ],
    0x27F7: [  // LONG LEFT RIGHT ARROW
      [15,5,1],[18,5,0],[21,7,1],[25,7,1],[29,9,1],[35,10,1],[41,11,1],[48,14,1],
      [57,16,1],[68,18,1],[81,22,1],[96,26,1],[114,30,1],[136,36,1]
    ],
    0x27F8: [  // LONG LEFTWARDS DOUBLE ARROW
      [13,5,1],[16,6,1],[18,7,1],[22,8,1],[26,9,1],[31,10,1],[36,13,1],[42,14,1],
      [50,18,2],[60,21,2],[71,24,2],[85,29,3],[101,34,3],[120,40,3]
    ],
    0x27F9: [  // LONG RIGHTWARDS DOUBLE ARROW
      [13,5,1],[15,6,1],[18,7,1],[22,8,1],[25,9,1],[30,11,1],[36,12,1],[42,14,1],
      [50,18,2],[60,21,2],[71,24,2],[84,28,2],[100,34,3],[119,40,3]
    ],
    0x27FA: [  // LONG LEFT RIGHT DOUBLE ARROW
      [15,5,1],[18,6,1],[21,7,1],[25,8,1],[29,9,1],[35,10,1],[41,13,1],[48,15,2],
      [57,17,2],[68,21,2],[81,24,2],[96,29,3],[114,33,3],[136,41,4]
    ],
    0x27FC: [  // LONG RIGHTWARDS ARROW FROM BAR
      [13,5,1],[15,5,0],[18,7,1],[21,7,1],[25,9,1],[30,9,0],[35,11,1],[41,14,1],
      [49,16,1],[59,18,1],[70,22,1],[82,26,1],[98,30,1],[116,36,1]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/SupplementalArrowsA.js");
