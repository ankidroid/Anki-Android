/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/MiscMathSymbolsA.js
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
    0x27E8: [  // MATHEMATICAL LEFT ANGLE BRACKET
      [3,8,2],[4,10,3],[4,11,3],[5,12,3],[6,15,4],[7,18,5],[8,20,5],[9,24,6],
      [11,28,7],[13,34,9],[15,40,10],[18,47,12],[22,56,14],[25,67,17]
    ],
    0x27E9: [  // MATHEMATICAL RIGHT ANGLE BRACKET
      [2,8,2],[3,10,3],[3,11,3],[4,12,3],[5,15,4],[6,18,5],[7,20,5],[8,24,6],
      [9,28,7],[11,34,9],[13,40,10],[15,47,12],[18,56,14],[21,67,17]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/MiscMathSymbolsA.js");
