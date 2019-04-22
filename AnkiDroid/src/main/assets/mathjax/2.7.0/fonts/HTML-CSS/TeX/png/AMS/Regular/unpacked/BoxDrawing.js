/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/BoxDrawing.js
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
    0x250C: [  // BOX DRAWINGS LIGHT DOWN AND RIGHT
      [4,3,-2],[4,4,-2],[5,4,-3],[6,5,-3],[7,6,-4],[8,7,-5],[9,8,-6],[11,10,-7],
      [13,12,-8],[15,13,-10],[18,17,-11],[21,19,-14],[25,23,-16],[30,26,-20]
    ],
    0x2510: [  // BOX DRAWINGS LIGHT DOWN AND LEFT
      [4,3,-2],[4,4,-2],[5,4,-3],[6,5,-3],[7,6,-4],[8,7,-5],[9,9,-5],[11,10,-7],
      [13,12,-8],[15,13,-10],[18,17,-11],[21,19,-14],[25,23,-16],[30,26,-20]
    ],
    0x2514: [  // BOX DRAWINGS LIGHT UP AND RIGHT
      [4,3,0],[4,4,0],[5,4,0],[6,6,1],[7,6,1],[8,7,1],[9,8,1],[11,10,1],
      [13,11,1],[15,13,1],[18,16,1],[21,19,1],[25,23,2],[30,27,2]
    ],
    0x2518: [  // BOX DRAWINGS LIGHT UP AND LEFT
      [4,4,1],[4,4,1],[5,5,1],[6,6,1],[7,6,1],[8,7,1],[9,8,1],[11,10,1],
      [13,11,1],[15,13,1],[18,16,1],[21,18,1],[25,23,2],[30,26,2]
    ],
    0x2571: [  // BOX DRAWINGS LIGHT DIAGONAL UPPER RIGHT TO LOWER LEFT
      [6,7,2],[8,8,2],[9,9,2],[11,12,3],[12,13,3],[15,16,4],[17,18,4],[20,22,5],
      [24,26,6],[29,30,7],[34,36,8],[40,43,10],[48,50,11],[57,59,13]
    ],
    0x2572: [  // BOX DRAWINGS LIGHT DIAGONAL UPPER LEFT TO LOWER RIGHT
      [6,7,2],[8,8,2],[9,9,2],[11,12,3],[12,13,3],[15,16,4],[17,18,4],[20,22,5],
      [24,26,6],[29,30,7],[34,36,8],[40,43,10],[48,50,11],[57,59,13]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/BoxDrawing.js");
