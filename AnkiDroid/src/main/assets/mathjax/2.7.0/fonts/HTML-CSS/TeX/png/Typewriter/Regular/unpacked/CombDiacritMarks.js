/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Typewriter/Regular/CombDiacritMarks.js
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
  "MathJax_Typewriter": {
    0x300: [  // COMBINING GRAVE ACCENT
      [2,1,-3],[3,1,-5],[4,1,-5],[3,2,-5],[4,2,-6],[4,2,-8],[5,3,-10],[6,3,-11],
      [7,4,-13],[8,5,-16],[9,5,-19],[10,6,-22],[13,7,-27],[15,9,-32]
    ],
    0x301: [  // COMBINING ACUTE ACCENT
      [3,1,-3],[3,1,-5],[3,1,-5],[3,2,-5],[4,2,-6],[5,2,-8],[5,3,-10],[6,3,-11],
      [7,4,-13],[8,5,-16],[9,5,-19],[11,6,-22],[13,7,-27],[15,9,-32]
    ],
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [3,1,-3],[4,2,-4],[5,2,-4],[5,2,-5],[5,2,-6],[7,3,-7],[8,3,-10],[9,4,-10],
      [10,4,-13],[12,5,-16],[14,6,-18],[17,7,-21],[19,9,-25],[23,10,-31]
    ],
    0x303: [  // COMBINING TILDE
      [4,1,-3],[4,1,-5],[5,1,-5],[5,2,-5],[6,3,-5],[7,3,-7],[8,3,-10],[9,3,-11],
      [11,4,-13],[13,5,-16],[15,6,-18],[17,7,-21],[21,8,-26],[24,10,-31]
    ],
    0x304: [  // COMBINING MACRON
      [4,1,-3],[4,1,-4],[5,1,-4],[6,1,-6],[6,2,-6],[7,3,-7],[8,2,-10],[10,2,-11],
      [11,3,-14],[13,3,-17],[16,3,-19],[18,4,-23],[21,5,-27],[26,6,-33]
    ],
    0x306: [  // COMBINING BREVE
      [4,1,-3],[4,1,-5],[5,1,-5],[6,1,-6],[6,2,-6],[7,3,-8],[8,2,-11],[10,3,-11],
      [11,3,-14],[13,4,-17],[15,5,-19],[18,5,-23],[21,6,-28],[25,7,-34]
    ],
    0x308: [  // COMBINING DIAERESIS
      [3,1,-3],[4,1,-5],[4,1,-5],[4,2,-5],[5,2,-6],[7,2,-8],[7,2,-11],[8,3,-11],
      [10,3,-14],[11,4,-17],[13,4,-20],[16,5,-23],[19,6,-28],[22,7,-34]
    ],
    0x30A: [  // COMBINING RING ABOVE
      [2,1,-3],[2,1,-5],[3,1,-5],[3,2,-5],[3,2,-6],[3,2,-8],[4,3,-10],[4,3,-11],
      [5,3,-14],[7,5,-16],[7,5,-19],[8,6,-22],[10,7,-27],[12,8,-33]
    ],
    0x30C: [  // COMBINING CARON
      [3,1,-3],[4,1,-4],[5,1,-4],[5,2,-5],[5,2,-6],[7,2,-7],[8,3,-9],[8,3,-10],
      [10,4,-12],[12,5,-15],[14,5,-17],[16,6,-21],[19,7,-25],[23,9,-30]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Typewriter/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
