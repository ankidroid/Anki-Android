/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Italic/GeneralPunctuation.js
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
    0x2013: [  // EN DASH
      [4,1,-1],[5,1,-2],[6,1,-2],[7,1,-3],[8,1,-3],[10,1,-4],[11,1,-5],[14,1,-6],
      [16,2,-6],[19,3,-8],[22,3,-9],[27,3,-11],[32,4,-13],[37,4,-15]
    ],
    0x2014: [  // EM DASH
      [8,1,-1],[9,1,-2],[11,1,-2],[13,1,-3],[15,1,-3],[18,1,-4],[21,1,-5],[25,1,-6],
      [29,1,-7],[35,3,-8],[41,3,-9],[49,2,-12],[58,3,-14],[69,3,-16]
    ],
    0x2018: [  // LEFT SINGLE QUOTATION MARK
      [3,2,-3],[3,4,-2],[4,4,-2],[5,4,-4],[5,4,-5],[6,5,-6],[8,7,-7],[9,7,-9],
      [10,9,-10],[12,11,-12],[15,12,-15],[17,15,-18],[21,18,-21],[24,21,-24]
    ],
    0x2019: [  // RIGHT SINGLE QUOTATION MARK
      [3,3,-2],[4,3,-3],[4,3,-3],[5,4,-4],[6,5,-4],[7,5,-6],[8,7,-7],[9,8,-8],
      [11,9,-10],[13,11,-12],[15,13,-14],[18,15,-18],[21,18,-21],[25,21,-24]
    ],
    0x201C: [  // LEFT DOUBLE QUOTATION MARK
      [5,2,-3],[6,4,-2],[6,4,-2],[8,4,-4],[9,4,-5],[11,5,-6],[12,7,-7],[15,7,-9],
      [17,9,-10],[20,11,-12],[24,12,-15],[29,15,-18],[34,18,-21],[40,21,-24]
    ],
    0x201D: [  // RIGHT DOUBLE QUOTATION MARK
      [4,3,-2],[5,3,-3],[6,3,-3],[7,4,-4],[8,5,-4],[9,5,-6],[11,7,-7],[13,8,-8],
      [15,9,-10],[18,11,-12],[22,13,-14],[25,15,-18],[30,18,-21],[36,21,-24]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GeneralPunctuation.js");
