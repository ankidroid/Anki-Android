/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/CombDiacritMarks.js
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
    0x300: [  // COMBINING GRAVE ACCENT
      [2,2,-3],[3,2,-4],[2,3,-5],[3,3,-5],[4,3,-6],[4,4,-8],[4,4,-10],[6,6,-12],
      [6,7,-14],[7,7,-17],[8,8,-20],[10,10,-23],[11,11,-28],[13,13,-34]
    ],
    0x301: [  // COMBINING ACUTE ACCENT
      [3,2,-3],[3,2,-4],[2,3,-5],[3,3,-5],[4,3,-6],[4,4,-8],[4,5,-9],[5,6,-12],
      [7,7,-14],[7,7,-17],[8,9,-19],[10,10,-23],[12,12,-28],[13,13,-34]
    ],
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [3,2,-3],[4,3,-4],[3,3,-5],[4,3,-5],[5,3,-7],[6,4,-9],[6,4,-10],[8,5,-13],
      [8,6,-15],[10,7,-17],[12,7,-21],[14,9,-24],[16,10,-29],[19,12,-35]
    ],
    0x303: [  // COMBINING TILDE
      [3,1,-4],[4,1,-5],[5,1,-7],[5,1,-7],[5,1,-8],[6,3,-9],[8,3,-11],[9,3,-14],
      [10,3,-17],[12,4,-19],[14,4,-23],[17,5,-26],[20,6,-32],[23,7,-38]
    ],
    0x304: [  // COMBINING MACRON
      [3,1,-3],[4,1,-5],[5,1,-6],[6,1,-6],[6,1,-7],[7,1,-10],[8,1,-11],[9,2,-13],
      [11,3,-15],[13,2,-18],[15,2,-22],[18,3,-26],[21,3,-30],[25,4,-36]
    ],
    0x306: [  // COMBINING BREVE
      [3,2,-3],[4,2,-4],[4,2,-6],[4,2,-6],[5,3,-6],[6,3,-9],[7,4,-10],[8,5,-12],
      [10,5,-15],[11,6,-17],[13,7,-21],[15,9,-24],[18,10,-29],[21,12,-34]
    ],
    0x307: [  // COMBINING DOT ABOVE
      [2,1,-4],[2,2,-4],[3,2,-6],[2,2,-6],[3,2,-7],[3,3,-9],[4,3,-11],[4,3,-14],
      [4,4,-16],[5,5,-18],[6,5,-22],[7,6,-26],[8,7,-31],[9,9,-37]
    ],
    0x308: [  // COMBINING DIAERESIS
      [3,1,-4],[4,2,-4],[4,2,-6],[4,2,-6],[5,2,-7],[6,3,-9],[7,3,-11],[8,3,-14],
      [10,4,-16],[11,4,-19],[13,5,-22],[15,6,-26],[18,7,-31],[21,8,-37]
    ],
    0x30A: [  // COMBINING RING ABOVE
      [2,2,-3],[2,2,-4],[3,2,-6],[4,2,-6],[3,3,-6],[4,3,-9],[5,4,-10],[6,5,-13],
      [6,6,-15],[8,7,-17],[9,8,-22],[11,8,-25],[12,10,-30],[15,12,-36]
    ],
    0x30B: [  // COMBINING DOUBLE ACUTE ACCENT
      [3,2,-3],[4,2,-4],[4,2,-6],[5,2,-6],[5,3,-6],[6,4,-8],[7,4,-10],[8,5,-13],
      [9,6,-15],[11,7,-17],[12,8,-20],[15,9,-24],[17,11,-28],[20,13,-34]
    ],
    0x30C: [  // COMBINING CARON
      [3,1,-3],[4,1,-5],[3,2,-6],[4,2,-6],[5,2,-7],[6,3,-9],[6,3,-10],[7,3,-13],
      [8,4,-15],[10,5,-17],[12,6,-21],[13,6,-24],[16,8,-29],[19,9,-34]
    ],
    0x338: [  // COMBINING LONG SOLIDUS OVERLAY
      [5,6,1],[5,8,2],[6,10,2],[7,10,2],[8,12,3],[9,16,4],[11,18,4],[12,23,5],
      [15,27,6],[18,31,7],[20,38,8],[24,44,10],[29,52,12],[34,63,14]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
