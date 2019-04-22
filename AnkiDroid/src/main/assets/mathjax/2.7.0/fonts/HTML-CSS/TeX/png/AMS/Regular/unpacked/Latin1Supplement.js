/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/Latin1Supplement.js
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
    0xA0: [  // NO-BREAK SPACE
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0]
    ],
    0xA5: [  // YEN SIGN
      [6,5,0],[7,6,0],[8,7,0],[9,8,0],[11,9,0],[13,11,0],[15,13,0],[18,16,0],
      [21,19,0],[25,22,0],[29,27,0],[35,32,0],[41,38,0],[49,45,0]
    ],
    0xAE: [  // REGISTERED SIGN
      [7,6,1],[8,8,2],[9,9,2],[11,10,2],[13,12,2],[16,15,3],[18,17,3],[22,20,4],
      [26,24,5],[31,29,6],[36,34,7],[43,41,8],[51,49,10],[61,59,12]
    ],
    0xF0: [  // LATIN SMALL LETTER ETH
      [4,5,0],[5,6,0],[5,8,0],[6,9,0],[8,11,0],[9,13,0],[10,15,0],[12,18,0],
      [15,22,2],[17,25,1],[20,30,1],[24,36,1],[29,43,1],[34,51,1]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/Latin1Supplement.js");
