/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/CombDiacritMarks.js
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
    0x302: [  // COMBINING CIRCUMFLEX ACCENT
      [18,3,-3],[21,3,-4],[25,3,-5],[29,4,-6],[34,5,-8],[40,5,-9],[47,6,-11],[56,8,-12],
      [66,8,-16],[79,10,-19],[93,12,-21],[111,14,-26],[131,17,-30],[156,20,-36]
    ],
    0x303: [  // COMBINING TILDE
      [17,2,-4],[20,2,-5],[23,4,-5],[28,5,-7],[33,4,-9],[39,5,-10],[46,6,-12],[55,6,-15],
      [65,8,-17],[77,9,-21],[92,11,-25],[109,13,-29],[129,15,-35],[154,18,-41]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiacritMarks.js");
