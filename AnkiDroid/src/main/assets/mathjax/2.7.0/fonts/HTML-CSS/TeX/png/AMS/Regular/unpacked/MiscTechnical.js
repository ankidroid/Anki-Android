/*************************************************************
 *
 *  MathJax/fonts/HTML-CSS/TeX/png/AMS/Regular/MiscTechnical.js
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
    0x2322: [  // stix-small down curve
      [5,3,0],[6,3,0],[8,3,-1],[9,4,-1],[10,5,-1],[12,6,-1],[15,6,-2],[17,7,-2],
      [20,8,-3],[24,9,-4],[29,11,-4],[34,13,-5],[40,15,-6],[48,17,-8]
    ],
    0x2323: [  // stix-small up curve
      [5,2,-1],[6,3,-1],[8,3,-1],[9,4,-1],[11,4,-2],[13,5,-2],[15,5,-3],[17,6,-3],
      [21,8,-3],[24,9,-4],[29,10,-5],[34,12,-6],[40,14,-7],[48,16,-9]
    ]
  }
});

MathJax.Ajax.loadComplete(MathJax.OutputJax["HTML-CSS"].imgDir+"/AMS/Regular"+
                          MathJax.OutputJax["HTML-CSS"].imgPacked+"/MiscTechnical.js");
