/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/PUA.js
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
    0xE006: [  // MJ-TeX: small does not divide
      [3,4,1],[3,5,1],[4,6,1],[4,7,1],[5,7,1],[5,9,1],[6,10,1],[7,11,1],
      [8,13,1],[9,16,1],[11,18,1],[13,22,2],[16,26,2],[18,31,2]
    ],
    0xE007: [  // MJ-TeX: small not parallel
      [4,4,1],[5,5,1],[6,6,1],[6,7,1],[7,7,1],[8,9,1],[10,10,1],[11,11,1],
      [13,13,1],[15,16,1],[17,18,1],[21,23,2],[25,26,2],[29,31,2]
    ],
    0xE008: [  // MJ-TeX: greek small letter digamma (for IE)
      [5,6,1],[6,6,1],[8,7,1],[9,9,1],[10,11,2],[12,13,2],[15,14,2],[17,17,2],
      [20,20,3],[24,23,3],[29,28,4],[34,33,4],[40,39,5],[48,46,6]
    ],
    0xE009: [  // MJ-TeX: greek kappa symbol (for IE)
      [6,4,1],[7,5,1],[8,6,1],[9,7,1],[11,7,1],[13,9,1],[15,10,1],[18,12,1],
      [21,13,1],[25,16,1],[29,19,1],[35,22,1],[41,25,1],[49,30,1]
    ],
    0xE00C: [  // MJ-TeX: less over not-equal
      [5,8,2],[6,10,3],[7,11,3],[9,13,4],[10,15,4],[12,18,5],[14,21,6],[17,25,7],
      [20,29,8],[23,35,10],[28,42,12],[33,49,14],[39,58,16],[46,69,19]
    ],
    0xE00D: [  // MJ-TeX: greater over not-equal
      [5,8,2],[6,10,3],[7,11,3],[9,13,4],[10,15,4],[12,18,5],[14,21,6],[17,25,7],
      [20,29,8],[23,35,10],[28,42,12],[33,49,14],[39,58,16],[46,69,19]
    ],
    0xE00E: [  // MJ-TeX: not greater, double equals
      [5,10,3],[6,12,4],[7,15,5],[9,16,5],[10,19,6],[12,23,7],[14,27,9],[17,32,10],
      [20,38,12],[23,45,14],[28,53,17],[33,63,20],[39,75,24],[46,89,28]
    ],
    0xE00F: [  // MJ-TeX: not greater-or-equal slanted
      [5,9,3],[6,10,3],[7,12,4],[9,14,4],[10,17,5],[12,20,6],[14,22,6],[17,27,8],
      [20,32,9],[23,37,10],[28,44,12],[33,52,14],[39,62,17],[46,73,20]
    ],
    0xE010: [  // MJ-TeX: not less-or-equal, slanted
      [5,9,3],[6,10,3],[8,12,4],[9,14,4],[10,17,5],[12,20,6],[14,22,6],[17,27,8],
      [20,32,9],[24,37,10],[28,44,12],[33,52,14],[39,62,17],[47,73,20]
    ],
    0xE011: [  // MJ-TeX: not less, double equals
      [5,10,3],[6,12,4],[7,15,5],[9,16,5],[10,19,6],[12,23,7],[14,27,9],[17,32,10],
      [20,38,12],[23,45,14],[28,53,17],[33,63,20],[39,75,24],[46,89,28]
    ],
    0xE016: [  // MJ-TeX: not subset, double equals
      [5,9,3],[6,10,3],[7,13,4],[9,14,4],[10,17,5],[12,20,6],[14,24,7],[17,27,8],
      [20,33,10],[23,38,11],[28,46,13],[33,55,16],[39,65,19],[46,77,22]
    ],
    0xE017: [  // MJ-TeX: not subset, double equals, short slash
      [5,9,3],[6,10,3],[7,12,4],[9,13,4],[10,16,5],[12,19,6],[14,22,7],[17,26,8],
      [20,31,10],[23,36,11],[28,43,13],[33,51,16],[39,61,19],[46,72,22]
    ],
    0xE018: [  // MJ-TeX: not superset, double equals
      [5,9,3],[6,10,3],[7,12,4],[9,14,4],[10,17,5],[12,20,6],[14,23,7],[17,27,8],
      [20,33,10],[23,39,11],[28,46,13],[33,55,16],[39,65,19],[46,77,22]
    ],
    0xE019: [  // MJ-TeX: not superset, double equals, short slash
      [5,9,3],[6,10,3],[7,12,4],[9,13,4],[10,16,5],[12,19,6],[14,22,7],[17,26,8],
      [20,31,10],[23,36,11],[28,43,13],[33,52,16],[39,61,19],[46,72,22]
    ],
    0xE01A: [  // MJ-TeX: not subset or equal, short slash
      [5,7,2],[6,8,2],[7,10,3],[9,11,3],[10,13,4],[12,15,4],[14,18,5],[17,21,6],
      [20,26,8],[23,30,9],[28,35,10],[33,42,12],[39,51,15],[46,59,17]
    ],
    0xE01B: [  // MJ-TeX: not superset or equal, short slash
      [5,7,2],[6,9,3],[7,10,3],[8,11,3],[10,13,4],[12,16,5],[14,18,5],[17,21,6],
      [20,25,7],[23,30,9],[28,35,10],[33,42,12],[39,50,15],[46,59,17]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/PUA.js");
