/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Italic/LatinExtendedB.js
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
    0x237: [  // LATIN SMALL LETTER DOTLESS J
      [4,4,1],[4,6,2],[5,6,2],[5,7,2],[6,9,3],[7,10,3],[8,13,4],[9,14,4],
      [11,17,5],[12,21,6],[15,25,8],[18,31,10],[21,37,12],[24,43,14]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/LatinExtendedB.js");
