/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/SpacingModLetters.js
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
    0x2C6: [  // MODIFIER LETTER CIRCUMFLEX ACCENT
      [3,2,-3],[4,3,-4],[4,3,-5],[5,3,-5],[6,3,-7],[7,4,-9],[8,4,-10],[9,5,-13],
      [11,6,-15],[13,7,-17],[16,7,-21],[18,9,-24],[22,10,-29],[26,12,-35]
    ],
    0x2C7: [  // CARON
      [3,1,-3],[4,1,-5],[4,2,-6],[5,2,-6],[6,2,-7],[7,3,-9],[8,3,-10],[9,3,-13],
      [11,4,-15],[13,5,-17],[16,6,-21],[18,6,-24],[22,8,-29],[26,9,-34]
    ],
    0x2C9: [  // MODIFIER LETTER MACRON
      [3,1,-3],[4,1,-5],[5,1,-6],[6,1,-6],[6,1,-7],[8,1,-10],[9,1,-11],[10,2,-13],
      [12,3,-15],[15,2,-18],[17,2,-22],[20,3,-26],[24,3,-30],[29,4,-36]
    ],
    0x2CA: [  // MODIFIER LETTER ACUTE ACCENT
      [3,2,-3],[4,2,-4],[4,3,-5],[5,3,-5],[6,3,-6],[7,4,-8],[8,5,-9],[10,6,-12],
      [11,7,-14],[13,7,-17],[16,9,-19],[19,10,-23],[22,12,-28],[26,13,-34]
    ],
    0x2CB: [  // MODIFIER LETTER GRAVE ACCENT
      [3,2,-3],[3,2,-4],[3,3,-5],[4,3,-5],[5,3,-6],[5,4,-8],[6,4,-10],[7,6,-12],
      [9,7,-14],[10,7,-17],[12,8,-20],[14,10,-23],[17,11,-28],[20,13,-34]
    ],
    0x2D8: [  // BREVE
      [3,2,-3],[4,2,-4],[4,2,-6],[5,2,-6],[6,3,-6],[7,3,-9],[8,4,-10],[10,5,-12],
      [12,5,-15],[14,6,-17],[16,7,-21],[19,9,-24],[23,10,-29],[27,12,-34]
    ],
    0x2D9: [  // DOT ABOVE
      [3,1,-4],[3,2,-4],[4,2,-6],[4,2,-6],[5,2,-7],[6,3,-9],[7,3,-11],[8,3,-14],
      [9,4,-16],[11,5,-18],[13,5,-22],[15,6,-26],[18,7,-31],[21,9,-37]
    ],
    0x2DA: [  // RING ABOVE
      [3,2,-3],[3,2,-4],[4,2,-6],[5,2,-6],[5,3,-6],[6,3,-9],[7,4,-10],[9,5,-13],
      [10,6,-15],[12,7,-17],[14,8,-22],[17,8,-25],[20,10,-30],[24,12,-36]
    ],
    0x2DC: [  // SMALL TILDE
      [3,1,-4],[4,1,-5],[5,1,-7],[5,1,-7],[6,1,-8],[7,3,-9],[9,3,-11],[10,3,-14],
      [12,3,-17],[14,4,-19],[17,4,-23],[20,5,-26],[23,6,-32],[28,7,-38]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/SpacingModLetters.js");
