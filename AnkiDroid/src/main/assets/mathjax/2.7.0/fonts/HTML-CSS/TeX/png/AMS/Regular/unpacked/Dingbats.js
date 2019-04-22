/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/Dingbats.js
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
    0x2713: [  // CHECK MARK
      [6,6,1],[7,7,1],[8,8,1],[9,10,1],[11,11,1],[13,13,1],[15,15,1],[18,18,1],
      [21,21,1],[25,26,2],[30,30,2],[35,35,2],[42,42,2],[50,50,3]
    ],
    0x2720: [  // MALTESE CROSS
      [6,6,1],[7,7,1],[8,9,1],[10,10,1],[11,11,1],[13,13,1],[16,15,1],[19,18,1],
      [22,21,1],[26,25,1],[31,30,1],[37,35,1],[44,42,2],[52,50,2]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/Dingbats.js");
