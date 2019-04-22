/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/SansSerif/Bold/CombDiacritMarks.js
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
  "MathJax_SansSerif-bold": {
    0x300: [  // COMBINING GRAVE ACCENT
      [3,1,-3],[3,2,-4],[3,2,-5],[4,2,-7],[4,3,-7],[5,3,-9],[5,3,-10],[6,4,-12],
      [7,5,-14],[9,6,-17],[10,7,-21],[12,8,-24],[14,9,-29],[17,11,-34]
    ],
    0x301: [  // COMBINING ACUTE ACCENT
      [3,1,-3],[3,2,-4],[4,2,-5],[3,2,-7],[4,3,-7],[5,3,-9],[6,3,-10],[6,4,-12],
      [8,5,-14],[8,6,-17],[11,7,-21],[12,8,-24],[14,9,-29],[16,11,-34]
    ],
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [4,1,-3],[4,2,-4],[4,2,-5],[5,2,-7],[6,3,-7],[7,3,-9],[7,3,-10],[9,4,-12],
      [10,5,-14],[12,6,-17],[14,7,-21],[16,8,-24],[19,9,-29],[23,11,-34]
    ],
    0x303: [  // COMBINING TILDE
      [4,1,-3],[4,1,-5],[5,2,-5],[5,3,-6],[6,3,-7],[7,3,-9],[8,3,-10],[9,4,-12],
      [11,4,-15],[13,5,-18],[16,6,-22],[18,7,-25],[21,8,-30],[25,10,-35]
    ],
    0x304: [  // COMBINING MACRON
      [4,1,-3],[4,1,-5],[5,2,-5],[6,3,-6],[6,3,-8],[7,2,-9],[9,2,-11],[11,3,-13],
      [12,3,-15],[14,4,-19],[16,5,-23],[20,5,-26],[23,6,-31],[27,7,-37]
    ],
    0x306: [  // COMBINING BREVE
      [4,1,-3],[4,1,-5],[5,2,-5],[6,3,-6],[6,2,-8],[7,2,-10],[9,3,-10],[10,4,-12],
      [11,4,-15],[14,5,-18],[16,6,-22],[19,7,-25],[22,8,-30],[26,10,-35]
    ],
    0x307: [  // COMBINING DOT ABOVE
      [2,1,-3],[2,1,-5],[3,2,-5],[3,2,-7],[3,2,-8],[3,2,-10],[4,2,-11],[5,3,-13],
      [5,3,-16],[6,4,-19],[7,4,-24],[8,5,-27],[10,6,-32],[12,7,-38]
    ],
    0x308: [  // COMBINING DIAERESIS
      [4,2,-3],[4,2,-5],[5,3,-5],[5,3,-7],[6,3,-8],[7,3,-10],[8,3,-11],[9,4,-13],
      [11,4,-16],[12,5,-19],[15,5,-24],[18,6,-27],[21,7,-32],[25,8,-38]
    ],
    0x30A: [  // COMBINING RING ABOVE
      [3,1,-3],[3,2,-4],[3,2,-5],[4,2,-7],[5,2,-8],[5,3,-9],[6,3,-10],[7,4,-12],
      [8,5,-14],[10,6,-17],[11,6,-22],[13,7,-25],[15,9,-29],[18,10,-35]
    ],
    0x30B: [  // COMBINING DOUBLE ACUTE ACCENT
      [4,1,-3],[4,2,-4],[5,2,-5],[5,2,-7],[6,3,-7],[7,3,-9],[8,3,-10],[9,4,-12],
      [11,5,-14],[12,6,-17],[15,7,-21],[17,8,-24],[20,9,-29],[23,11,-34]
    ],
    0x30C: [  // COMBINING CARON
      [4,1,-3],[4,2,-4],[4,2,-5],[5,2,-6],[6,3,-7],[7,3,-8],[7,3,-10],[9,4,-12],
      [10,5,-14],[12,6,-17],[14,7,-20],[16,8,-23],[19,9,-27],[23,11,-33]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/SansSerif/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
