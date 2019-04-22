/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Italic/LetterlikeSymbols.js
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
    0x210F: [  // stix-/hbar - Planck's over 2pi
      [4,6,1],[5,7,1],[6,7,0],[7,10,1],[8,11,1],[10,13,1],[11,15,1],[13,18,1],
      [16,21,1],[19,24,1],[22,29,1],[27,34,1],[32,40,1],[37,47,1]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/LetterlikeSymbols.js");
