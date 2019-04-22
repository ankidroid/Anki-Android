/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/SansSerif/Regular/CombDiacritMarks.js
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
  "MathJax_SansSerif": {
    0x300: [  // COMBINING GRAVE ACCENT
      [2,1,-4],[3,2,-4],[4,2,-6],[3,2,-7],[4,2,-7],[4,3,-9],[6,4,-10],[6,4,-13],
      [7,5,-15],[8,6,-17],[10,7,-20],[11,8,-24],[13,10,-29],[15,11,-34]
    ],
    0x301: [  // COMBINING ACUTE ACCENT
      [3,1,-4],[3,2,-4],[3,2,-6],[4,2,-7],[4,2,-7],[5,3,-9],[5,4,-10],[7,4,-13],
      [7,5,-15],[8,6,-17],[9,7,-20],[12,8,-24],[13,10,-29],[15,11,-34]
    ],
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [3,1,-4],[4,2,-4],[5,2,-6],[5,2,-7],[5,2,-7],[6,3,-9],[8,4,-10],[9,4,-13],
      [10,5,-15],[12,6,-17],[14,7,-20],[17,8,-24],[20,10,-29],[23,11,-34]
    ],
    0x303: [  // COMBINING TILDE
      [3,1,-4],[4,1,-5],[5,2,-6],[5,3,-6],[5,2,-7],[6,3,-9],[8,3,-11],[9,3,-14],
      [10,4,-16],[12,5,-18],[14,6,-21],[17,6,-25],[20,8,-31],[23,9,-35]
    ],
    0x304: [  // COMBINING MACRON
      [3,1,-3],[4,1,-5],[5,1,-6],[6,2,-6],[6,2,-6],[7,3,-9],[8,2,-11],[9,2,-13],
      [11,3,-16],[13,3,-18],[15,4,-22],[18,4,-25],[21,5,-31],[25,6,-36]
    ],
    0x306: [  // COMBINING BREVE
      [3,1,-4],[4,2,-4],[5,2,-6],[6,3,-6],[5,3,-6],[7,4,-8],[8,4,-10],[9,4,-13],
      [10,6,-14],[13,6,-17],[15,8,-19],[17,9,-23],[20,11,-28],[25,12,-33]
    ],
    0x307: [  // COMBINING DOT ABOVE
      [2,1,-4],[2,1,-5],[2,2,-6],[2,2,-7],[3,2,-7],[3,2,-10],[3,3,-11],[4,3,-14],
      [4,3,-17],[4,4,-19],[5,5,-23],[6,5,-26],[7,6,-33],[7,7,-38]
    ],
    0x308: [  // COMBINING DIAERESIS
      [3,1,-4],[4,1,-5],[3,2,-6],[4,2,-7],[5,2,-7],[6,2,-10],[6,2,-12],[8,3,-14],
      [9,3,-17],[11,4,-19],[12,4,-23],[15,5,-27],[17,6,-33],[21,7,-38]
    ],
    0x30A: [  // COMBINING RING ABOVE
      [3,1,-4],[3,2,-4],[4,2,-6],[3,2,-7],[4,2,-7],[5,3,-9],[6,3,-11],[6,5,-12],
      [7,6,-14],[8,6,-17],[10,7,-20],[11,8,-24],[13,10,-29],[15,11,-34]
    ],
    0x30B: [  // COMBINING DOUBLE ACUTE ACCENT
      [3,2,-3],[4,2,-4],[4,2,-6],[5,3,-6],[5,3,-6],[6,3,-9],[7,4,-10],[9,5,-12],
      [10,5,-15],[12,6,-17],[13,7,-20],[16,8,-24],[19,10,-29],[22,11,-34]
    ],
    0x30C: [  // COMBINING CARON
      [3,1,-3],[4,2,-4],[5,2,-5],[5,2,-6],[5,2,-6],[6,3,-9],[8,4,-10],[9,4,-12],
      [10,5,-14],[12,6,-16],[14,7,-19],[17,8,-22],[20,10,-28],[23,11,-32]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/SansSerif/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
