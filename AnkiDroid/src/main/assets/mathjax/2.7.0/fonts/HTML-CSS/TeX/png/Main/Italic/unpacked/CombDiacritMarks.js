/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Italic/CombDiacritMarks.js
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
    0x300: [  // COMBINING GRAVE ACCENT
      [2,2,-3],[2,2,-4],[3,2,-4],[3,2,-6],[3,3,-6],[3,3,-8],[4,4,-10],[5,5,-10],
      [5,6,-14],[6,7,-17],[7,8,-19],[8,10,-23],[9,11,-28],[11,13,-32]
    ],
    0x301: [  // COMBINING ACUTE ACCENT
      [3,2,-3],[3,2,-4],[3,2,-4],[4,2,-6],[4,3,-6],[4,3,-8],[5,4,-10],[6,5,-10],
      [7,6,-14],[8,7,-17],[9,8,-19],[11,10,-23],[13,11,-28],[15,13,-32]
    ],
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [3,1,-4],[4,2,-4],[4,2,-4],[4,2,-6],[5,2,-7],[6,3,-8],[6,4,-10],[7,4,-12],
      [8,5,-14],[10,6,-17],[11,7,-20],[13,8,-25],[15,10,-29],[19,11,-34]
    ],
    0x303: [  // COMBINING TILDE
      [3,1,-4],[4,1,-5],[4,1,-5],[5,1,-7],[5,2,-7],[6,2,-9],[8,3,-11],[9,3,-12],
      [10,3,-16],[11,4,-19],[14,5,-21],[16,5,-27],[19,7,-32],[22,7,-37]
    ],
    0x304: [  // COMBINING MACRON
      [3,1,-3],[4,1,-5],[4,1,-5],[5,1,-6],[5,1,-7],[6,1,-8],[8,1,-11],[9,2,-11],
      [10,3,-14],[12,3,-17],[14,3,-20],[16,4,-25],[20,4,-30],[23,4,-35]
    ],
    0x306: [  // COMBINING BREVE
      [3,2,-3],[3,2,-4],[4,2,-4],[4,2,-6],[5,3,-6],[6,3,-8],[7,5,-9],[8,5,-11],
      [9,6,-13],[11,6,-17],[13,7,-20],[15,9,-24],[18,10,-29],[21,12,-33]
    ],
    0x307: [  // COMBINING DOT ABOVE
      [2,1,-4],[2,2,-4],[2,2,-4],[2,2,-6],[3,2,-7],[3,2,-9],[4,3,-11],[4,3,-12],
      [4,4,-15],[5,5,-19],[6,5,-21],[7,6,-26],[8,7,-31],[9,8,-36]
    ],
    0x308: [  // COMBINING DIAERESIS
      [3,1,-4],[4,2,-4],[4,2,-4],[4,2,-6],[5,2,-7],[6,2,-9],[6,3,-11],[8,3,-12],
      [9,4,-15],[11,4,-19],[12,5,-21],[15,6,-26],[17,7,-31],[20,8,-36]
    ],
    0x30A: [  // COMBINING RING ABOVE
      [3,2,-3],[3,2,-4],[3,2,-4],[4,2,-6],[4,3,-6],[5,3,-8],[5,4,-10],[6,4,-11],
      [7,5,-15],[8,7,-17],[9,8,-20],[11,8,-26],[12,10,-31],[15,12,-36]
    ],
    0x30B: [  // COMBINING DOUBLE ACUTE ACCENT
      [3,2,-3],[4,2,-4],[4,2,-4],[4,2,-6],[5,3,-6],[7,3,-8],[7,4,-10],[8,5,-10],
      [9,6,-14],[12,7,-17],[13,8,-19],[16,9,-24],[18,11,-28],[22,13,-32]
    ],
    0x30C: [  // COMBINING CARON
      [3,1,-3],[3,1,-5],[4,1,-5],[4,2,-6],[5,2,-7],[5,2,-8],[6,3,-10],[7,3,-11],
      [8,4,-14],[9,5,-17],[12,5,-19],[13,7,-24],[16,8,-28],[18,9,-33]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Italic"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
