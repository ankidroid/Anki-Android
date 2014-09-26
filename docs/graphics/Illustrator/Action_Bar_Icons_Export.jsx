// Docs: http://www.adobe.com/devnet/illustrator/scripting.html

// Script that exports action bar icons from Illustrator

var DPIS = {
  'xxhdpi': 3,
   'xhdpi': 2,
    'hdpi': 1.5,
    'mdpi': 1
};

var STYLES = {
  'dark': {
    'color': '#ffffff',
    'opacity': 80
  },
  'light': {
    'color': '#333333',
    'opacity': 60
  },
  
  'dark_disabled': {
    'color': '#ffffff',
    'opacity': 30
  },
  'light_disabled': {
    'color': '#333333',
    'opacity': 30
  }  
};

function go() {
  // Find errors
  var errorLayers = [];
  walkLayers(activeDocument.layers, function(layer) {
    if (layer.layers && layer.layers.length) {
      return; // not a leaf node
    }
    if (layer.locked) {
      return; // not a leaf node
    }
    var foundItem = false;
    walkPathItems(layer, function(pathItem) {
      foundItem = true;
    });
    if (!foundItem) {
      errorLayers.push(layer);
    }
  });

  if (errorLayers.length > 0) {
    var errorLayerNames = errorLayers.map(function(l) {
      return (l.parent ? (l.parent.name + ' > ') : '') + l.name;
    });

    alert('No compound paths found in layer(s)\n'
        + 'The following layers didn\'t have compound or regular paths:\n\n'
        + errorLayerNames.join('\n') + '\n\n'
        + 'If there\'s indeed a compound path there, release it, flatten any '
        + 'groups, and re-make the compound path.');
    return;
  }

  // Hide all layers
  walkLayers(activeDocument.layers, function(layer) {
    layer.visible = false;
  });

  // Look through leaf nodes
  walkLayers(activeDocument.layers, function(layer) {
    if (layer.layers && layer.layers.length) {
      return; // not a leaf node
    }
    if (layer.locked) {
      return; // not a leaf node
    }

    var category = '';

    var anc = ancestors(layer);
    for (var i = 0; i < anc.length; i++) {
      if (anc[i].typename == 'Layer') {
        anc[i].visible = true;
        if (category.length) {
          category += '_';
        }
        category += anc[i].name.replace(/\s/g, '_');
      }
    }

    var name = layer.name.replace(/\s/g, '_');

    layer.visible = true;

    for (var stylename in STYLES) {
      var style = STYLES[stylename];
      var color = hexColor(style.color);
      layer.opacity = style.opacity;

      // Color the layer contents
      walkPathItems(layer, function(pathItem) {
        pathItem.fillColor = color;
      });

      // Output the icon in all densities
      for (var dpiname in DPIS) {
        var destFile = new File(activeDocument.path
            + '/out/' + category + '_' + name + '/drawable' + '-' + dpiname
            + '/ic_action_' + name + '_' + stylename + '.png');
        mkdirs(destFile.parent);

        // Create PNG export
        var opts = new ExportOptionsPNG24();
        opts.artBoardClipping = true;
        opts.horizontalScale = opts.verticalScale = 100 * DPIS[dpiname];
        activeDocument.exportFile(destFile, ExportType.PNG24, opts);
      }
    }

    // Reset state
    if (!layer.locked) {
      layer.opacity = 100;
    }
    layer.visible = false;

    for (var i = 0; i < anc.length; i++) {
      if ('visible' in anc[i]) anc[i].visible = false;
    }
  });

  // Show all layers
  walkLayers(activeDocument.layers, function(layer) {
    layer.visible = true;
  });
}

// Helper methods

function hexColor(s) {
  s = s.replace(/#/, '');
  if (s.length == 3) {
    s = s.substring(0,1) + s.substring(0,1)
      + s.substring(1,2) + s.substring(1,2)
      + s.substring(2,3) + s.substring(2,3);
  }

  if (s.length != 6) {
    return new RGBColor();
  }

  var color = new RGBColor();
  color.red = parseInt(s.substring(0, 2), 16);
  color.green = parseInt(s.substring(2, 4), 16);
  color.blue = parseInt(s.substring(4, 6), 16);
  return color;
}

function ancestors(obj) {
  var anc = [];
  var parent = obj.parent;
  while (parent) {
    anc.push(parent);
    parent = parent.parent;
  }
  return anc;
}

function walkPathItems(container, fn) {
  fn = fn || function(){};
  container = container || {};
  if ('pathItems' in container) {
    for (var i = 0; i < container.pathItems.length; i++) {
      fn(container.pathItems[i]);
    }
  }
  if ('compoundPathItems' in container) {
    for (var i = 0; i < container.compoundPathItems.length; i++) {
      walkPathItems(container.compoundPathItems[i], fn);
    }
  }
  if ('groupItems' in container) {
    for (var i = 0; i < container.groupItems.length; i++) {
      walkPathItems(container.groupItems[i], fn);
    }
  }
}

function walkLayers(layers, fn) {
  fn = fn || function(){};
  layers = layers || [];
  for (var i = 0; i < layers.length; i++) {
    fn(layers[i]);
    walkLayers(layers[i].layers, fn);
  }
}

function repr(obj) {
  if (obj == null) {
    return 'null';
  }
  var s = '';
  var n = 0;
  for (var k in obj) {
    ++n;
    try {
      s += n + ". " + k + " = " + obj[k] + "\n";
    } catch(e) {
      s += n + '. ' + k + ' = <ERROR>\n';
    }
  }
  return (n + " properties\n" + s);
}

function mkdirs(folder) {
  if (folder && !folder.exists && folder.create()) {
    mkdirs(folder.parent);
  }
}

if (!Array.prototype.map) {
  Array.prototype.map = function(f) {
    var a = [];
    for (var k in this) {
      a[k] = f(this[k]);
    }
    return a;
  };
}

// go!

go();