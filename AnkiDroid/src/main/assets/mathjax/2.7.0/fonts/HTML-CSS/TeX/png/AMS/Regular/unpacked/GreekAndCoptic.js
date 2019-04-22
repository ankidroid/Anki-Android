/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/GreekAndCoptic.js
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
    0x3DD: [  // GREEK SMALL LETTER DIGAMMA
      [5,6,2],[6,7,1],[8,8,2],[9,9,1],[10,11,2],[12,13,2],[15,15,2],[17,17,2],
      [20,19,2],[24,23,3],[29,27,3],[34,33,4],[40,38,5],[48,46,6]
    ],
    0x3F0: [  // GREEK KAPPA SYMBOL
      [6,3,0],[7,4,0],[8,5,0],[9,5,0],[11,7,0],[13,8,0],[15,9,0],[18,10,0],
      [21,12,0],[25,15,0],[29,17,0],[35,21,0],[41,25,0],[49,29,0]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/GreekAndCoptic.js");
