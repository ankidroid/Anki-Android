/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/SupplementalArrowsA.js
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
    0x27F5: [  // LONG LEFTWARDS ARROW
      [11,5,1],[13,5,0],[15,6,1],[18,7,1],[22,9,1],[26,9,0],[30,11,1],[36,13,1],
      [43,15,1],[51,18,1],[60,21,1],[71,25,1],[85,30,1],[101,35,1]
    ],
    0x27F6: [  // LONG RIGHTWARDS ARROW
      [11,5,1],[13,5,0],[16,7,1],[19,7,1],[22,9,1],[26,9,0],[31,11,1],[37,13,1],
      [43,15,1],[52,18,1],[61,22,1],[73,25,1],[86,30,1],[103,35,1]
    ],
    0x27F7: [  // LONG LEFT RIGHT ARROW
      [13,5,1],[15,5,0],[18,6,1],[22,7,1],[25,9,1],[30,9,0],[36,11,1],[42,13,1],
      [50,15,1],[60,18,1],[71,22,1],[84,25,1],[100,30,1],[119,35,1]
    ],
    0x27F8: [  // LONG LEFTWARDS DOUBLE ARROW
      [11,5,1],[13,6,1],[16,7,1],[19,8,1],[22,9,1],[26,10,1],[31,11,1],[37,14,1],
      [43,16,1],[52,20,2],[61,23,2],[73,27,2],[86,31,2],[103,37,2]
    ],
    0x27F9: [  // LONG RIGHTWARDS DOUBLE ARROW
      [11,5,1],[14,6,1],[16,7,1],[19,8,1],[22,9,1],[27,10,1],[31,12,1],[37,14,1],
      [44,16,1],[52,19,1],[62,22,1],[74,26,1],[88,32,2],[104,37,2]
    ],
    0x27FA: [  // LONG LEFT RIGHT DOUBLE ARROW
      [13,5,1],[15,6,1],[18,7,1],[22,8,1],[25,9,1],[30,10,1],[36,12,1],[42,14,1],
      [50,16,1],[60,20,2],[71,23,2],[84,27,2],[100,31,2],[119,37,2]
    ],
    0x27FC: [  // LONG RIGHTWARDS ARROW FROM BAR
      [11,5,1],[13,5,0],[16,7,1],[19,7,1],[22,9,1],[26,9,0],[31,11,1],[37,13,1],
      [44,15,1],[52,18,1],[61,22,1],[73,25,1],[86,30,1],[103,35,1]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/SupplementalArrowsA.js");
