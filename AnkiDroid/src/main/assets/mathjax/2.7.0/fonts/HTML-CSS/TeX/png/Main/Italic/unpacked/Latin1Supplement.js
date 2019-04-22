/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Italic/Latin1Supplement.js
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
    0xA0: [  // NO-BREAK SPACE
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0]
    ],
    0xA3: [  // POUND SIGN
      [5,5,0],[6,6,0],[7,6,0],[9,8,0],[10,9,0],[12,11,0],[14,14,0],[17,15,0],
      [20,20,0],[24,24,0],[28,27,0],[33,34,1],[39,41,1],[47,48,1]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/Latin1Supplement.js");
