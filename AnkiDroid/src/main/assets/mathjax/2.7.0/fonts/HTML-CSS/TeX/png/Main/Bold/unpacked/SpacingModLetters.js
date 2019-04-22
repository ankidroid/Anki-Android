/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Bold/SpacingModLetters.js
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
  "MathJax_Main-bold": {
    0x2C6: [  // MODIFIER LETTER CIRCUMFLEX ACCENT
      [4,2,-3],[4,2,-5],[5,2,-6],[6,2,-6],[7,3,-7],[8,4,-9],[9,4,-10],[11,5,-13],
      [13,5,-15],[15,6,-17],[18,7,-21],[21,9,-24],[25,10,-29],[30,12,-35]
    ],
    0x2C7: [  // CARON
      [4,1,-3],[4,2,-5],[5,2,-6],[6,2,-6],[7,3,-7],[8,3,-9],[9,4,-10],[11,4,-13],
      [13,5,-15],[15,6,-17],[18,6,-21],[21,7,-24],[25,9,-28],[30,10,-34]
    ],
    0x2C9: [  // MODIFIER LETTER MACRON
      [4,1,-3],[5,1,-5],[5,1,-6],[6,1,-6],[7,1,-7],[9,2,-9],[10,3,-10],[12,2,-13],
      [14,2,-16],[17,3,-18],[20,3,-22],[23,4,-25],[28,4,-30],[33,5,-36]
    ],
    0x2CA: [  // MODIFIER LETTER ACUTE ACCENT
      [4,2,-3],[4,2,-4],[5,3,-5],[6,3,-5],[7,3,-6],[8,4,-8],[9,5,-9],[11,5,-12],
      [13,6,-14],[16,7,-16],[18,9,-19],[22,10,-23],[26,12,-27],[31,14,-33]
    ],
    0x2CB: [  // MODIFIER LETTER GRAVE ACCENT
      [3,2,-3],[3,2,-4],[4,3,-5],[4,3,-5],[5,3,-6],[6,4,-8],[7,5,-9],[8,5,-12],
      [10,6,-14],[12,7,-16],[14,9,-19],[16,10,-23],[19,12,-27],[23,14,-33]
    ],
    0x2D8: [  // BREVE
      [4,2,-3],[4,2,-4],[5,2,-6],[6,2,-6],[7,3,-6],[8,4,-8],[10,4,-10],[11,5,-12],
      [14,6,-14],[16,7,-16],[19,8,-20],[22,9,-23],[27,11,-27],[32,13,-33]
    ],
    0x2D9: [  // DOT ABOVE
      [3,2,-3],[4,2,-4],[4,2,-6],[5,2,-6],[6,3,-6],[7,3,-9],[8,4,-10],[9,5,-12],
      [11,5,-15],[13,6,-17],[15,7,-21],[18,8,-24],[21,10,-28],[25,12,-34]
    ],
    0x2DA: [  // RING ABOVE
      [3,1,-4],[4,2,-4],[5,2,-6],[5,2,-6],[6,2,-7],[7,3,-9],[9,3,-11],[10,4,-13],
      [12,6,-14],[14,6,-17],[17,7,-21],[20,8,-24],[23,9,-29],[28,11,-35]
    ],
    0x2DC: [  // SMALL TILDE
      [4,1,-4],[4,1,-5],[5,3,-6],[6,3,-6],[7,4,-6],[8,4,-9],[10,4,-11],[12,5,-13],
      [14,5,-16],[16,6,-18],[19,7,-22],[23,8,-25],[27,9,-30],[32,11,-36]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Bold"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/SpacingModLetters.js");
