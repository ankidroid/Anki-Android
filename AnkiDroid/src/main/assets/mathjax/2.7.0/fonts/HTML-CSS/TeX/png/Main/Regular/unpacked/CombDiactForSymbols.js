/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/Main/Regular/CombDiactForSymbols.js
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
    0x20D7: [  // COMBINING RIGHT ARROW ABOVE
      [4,2,-3],[4,2,-4],[5,3,-5],[6,3,-6],[7,3,-7],[8,4,-8],[10,5,-9],[11,5,-12],
      [14,6,-14],[16,7,-17],[18,9,-20],[21,10,-24],[26,12,-28],[30,14,-33]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/Main/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/CombDiactForSymbols.js");
