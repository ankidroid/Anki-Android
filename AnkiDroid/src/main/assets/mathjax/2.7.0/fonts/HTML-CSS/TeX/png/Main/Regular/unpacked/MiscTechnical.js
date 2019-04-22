/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/MiscTechnical.js
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
    0x2308: [  // LEFT CEILING
      [3,8,2],[4,9,2],[5,11,3],[5,12,3],[6,15,4],[8,17,4],[9,20,5],[10,25,7],
      [12,28,7],[14,34,9],[17,40,10],[20,47,12],[24,56,14],[28,67,17]
    ],
    0x2309: [  // RIGHT CEILING
      [2,8,2],[3,9,2],[3,11,3],[3,12,3],[4,15,4],[5,17,4],[5,20,5],[7,24,6],
      [8,28,7],[9,34,9],[11,40,10],[13,47,12],[15,56,14],[18,67,17]
    ],
    0x230A: [  // LEFT FLOOR
      [3,8,2],[4,9,2],[5,11,3],[5,12,3],[6,15,4],[8,17,4],[8,20,5],[10,24,6],
      [12,28,7],[14,34,9],[17,40,10],[20,47,12],[24,56,14],[28,67,17]
    ],
    0x230B: [  // RIGHT FLOOR
      [2,8,2],[3,9,2],[3,11,3],[3,12,3],[4,15,4],[5,17,4],[5,20,5],[6,24,6],
      [8,28,7],[9,34,9],[11,40,10],[13,47,12],[15,56,14],[18,67,17]
    ],
    0x2322: [  // stix-small down curve
      [7,3,0],[8,4,0],[10,3,-1],[12,4,-1],[14,5,-1],[16,6,-1],[19,6,-2],[22,8,-2],
      [27,8,-3],[32,9,-4],[37,12,-4],[44,13,-5],[53,16,-6],[63,19,-7]
    ],
    0x2323: [  // stix-small up curve
      [7,2,-1],[8,3,-1],[10,3,-1],[12,4,-1],[14,4,-2],[16,5,-2],[19,6,-2],[22,6,-3],
      [27,8,-3],[32,9,-4],[38,10,-5],[44,12,-6],[53,15,-7],[63,17,-8]
    ],
    0x23B0: [  // UPPER LEFT OR LOWER RIGHT CURLY BRACKET SECTION
      [3,8,2],[3,9,2],[4,11,3],[5,12,3],[5,15,4],[6,17,4],[7,20,5],[9,24,6],
      [10,28,7],[12,33,8],[14,40,10],[17,46,11],[20,55,13],[24,65,16]
    ],
    0x23B1: [  // UPPER RIGHT OR LOWER LEFT CURLY BRACKET SECTION
      [3,8,2],[3,9,2],[4,11,3],[5,12,3],[5,15,4],[6,17,4],[7,20,5],[9,24,6],
      [10,28,7],[12,33,8],[14,39,10],[17,47,12],[20,55,14],[24,65,16]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/MiscTechnical.js");
