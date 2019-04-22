/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/SansSerif/Italic/CombDiacritMarks.js
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
  "MathJax_SansSerif-italic": {
    0x300: [  // COMBINING GRAVE ACCENT
      [2,1,-4],[3,2,-4],[3,2,-6],[3,2,-7],[3,2,-7],[4,3,-9],[5,4,-10],[5,4,-13],
      [6,5,-15],[7,6,-17],[8,7,-21],[9,8,-24],[11,10,-30],[13,11,-34]
    ],
    0x301: [  // COMBINING ACUTE ACCENT
      [3,1,-4],[3,2,-4],[3,2,-6],[4,2,-7],[4,2,-7],[6,3,-9],[6,4,-10],[7,4,-13],
      [8,5,-15],[10,6,-17],[11,7,-21],[12,8,-24],[15,10,-30],[18,11,-34]
    ],
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [4,1,-4],[4,2,-4],[5,2,-6],[5,2,-7],[6,2,-7],[7,3,-9],[8,4,-10],[9,4,-13],
      [10,5,-15],[13,6,-17],[15,7,-21],[17,8,-24],[20,10,-30],[24,11,-34]
    ],
    0x303: [  // COMBINING TILDE
      [4,1,-3],[4,1,-5],[4,2,-5],[5,3,-6],[6,2,-7],[6,3,-9],[8,3,-10],[9,3,-13],
      [11,4,-15],[12,5,-17],[15,6,-21],[17,6,-25],[21,8,-31],[24,9,-35]
    ],
    0x304: [  // COMBINING MACRON
      [4,1,-3],[4,1,-5],[5,1,-6],[5,2,-6],[6,2,-6],[8,3,-9],[9,2,-11],[10,2,-13],
      [11,3,-16],[14,3,-18],[16,4,-22],[18,4,-25],[22,5,-31],[26,6,-36]
    ],
    0x306: [  // COMBINING BREVE
      [3,1,-4],[4,2,-4],[4,2,-6],[5,3,-6],[6,3,-6],[7,4,-8],[8,4,-10],[9,4,-13],
      [11,6,-14],[13,6,-17],[15,8,-20],[18,9,-23],[21,11,-29],[24,12,-33]
    ],
    0x307: [  // COMBINING DOT ABOVE
      [2,1,-3],[2,1,-5],[2,2,-5],[3,2,-7],[3,2,-7],[3,2,-10],[3,3,-10],[4,3,-13],
      [4,3,-16],[5,4,-18],[6,5,-22],[7,5,-26],[8,6,-33],[9,7,-38]
    ],
    0x308: [  // COMBINING DIAERESIS
      [3,1,-3],[4,1,-5],[4,2,-5],[5,2,-7],[5,2,-7],[6,2,-10],[7,2,-11],[8,3,-13],
      [10,3,-16],[11,4,-18],[13,4,-23],[15,5,-26],[19,6,-33],[21,7,-38]
    ],
    0x30A: [  // COMBINING RING ABOVE
      [2,1,-3],[2,2,-4],[3,2,-5],[3,2,-7],[4,2,-7],[4,3,-9],[5,3,-10],[6,5,-11],
      [7,5,-14],[8,6,-16],[9,7,-21],[11,8,-24],[13,10,-30],[15,11,-34]
    ],
    0x30B: [  // COMBINING DOUBLE ACUTE ACCENT
      [3,2,-2],[4,2,-4],[4,2,-5],[5,3,-6],[5,3,-6],[7,3,-9],[8,4,-9],[9,5,-11],
      [10,5,-14],[13,6,-16],[15,8,-20],[17,9,-23],[20,10,-30],[24,11,-34]
    ],
    0x30C: [  // COMBINING CARON
      [3,1,-3],[4,2,-4],[4,2,-5],[5,2,-6],[5,2,-6],[6,3,-9],[8,4,-10],[9,4,-12],
      [10,5,-14],[12,6,-16],[15,7,-19],[17,8,-22],[20,10,-28],[23,11,-32]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/SansSerif/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
