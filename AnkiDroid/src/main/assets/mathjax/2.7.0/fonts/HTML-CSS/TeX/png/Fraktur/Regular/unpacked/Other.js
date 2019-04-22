/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Fraktur/Regular/Other.js
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
  "MathJax_Fraktur": {
    0xA0: [  // NO-BREAK SPACE
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],
      [1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0],[1,1,0]
    ],
    0x2018: [  // LEFT SINGLE QUOTATION MARK
      [2,2,-2],[2,3,-3],[2,3,-4],[2,4,-5],[3,5,-5],[3,5,-7],[4,6,-7],[4,7,-9],
      [5,8,-11],[6,10,-14],[7,12,-16],[8,14,-18],[9,17,-21],[11,20,-26]
    ],
    0x2019: [  // RIGHT SINGLE QUOTATION MARK
      [2,2,-2],[2,3,-3],[2,3,-4],[2,4,-5],[3,5,-5],[3,5,-7],[4,6,-7],[4,7,-9],
      [5,8,-11],[6,10,-14],[7,12,-16],[8,14,-18],[10,17,-21],[11,20,-26]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Fraktur/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/Other.js");
