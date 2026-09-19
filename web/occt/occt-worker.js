/*
 * The CAD kernel, on a thread of its own.
 *
 * Reading a STEP file is not fast and cannot be made fast by asking for a coarser mesh: measured
 * on goBILDA's own GripForce wheel, the default tessellation takes 17.3 seconds and the coarsest
 * one still takes 14.6. The cost is OpenCascade reading the B-rep topology -- 223 solids and
 * about a million NURBS control points -- and the triangles come almost free at the end of it.
 *
 * Fifteen seconds of a frozen page is not a slow import, it is a broken program: the tab stops
 * painting, the spinner stops spinning, and the student force-quits it. So the kernel runs here,
 * and the page stays alive to say what is happening.
 *
 * WHAT IT SENDS BACK
 * ------------------
 * One flat Float32Array of triangle vertices, transferred rather than copied. occt hands back a
 * mesh per solid -- 223 of them for that wheel -- and structured-cloning that tree across the
 * thread boundary costs more than the meshing did. Flattened here, where the data already is.
 *
 * The deflection is a RATIO of the model's own bounding box, so it means the same thing for a
 * bracket and for a whole drivetrain. At 1 the wheel comes out 108k triangles instead of 985k,
 * for no extra time at all -- the default is simply finer than anything drawn at robot scale can
 * show.
 */
'use strict';

importScripts('occt-import-js.js');

var ready = null;

self.onmessage = function (ev) {
  var bytes = ev.data.bytes;
  var deflection = ev.data.deflection || 1;

  // Started on the first file rather than when the worker is created, so the 7 MB is not paid
  // for by anybody who opens the dialog and changes their mind.
  if (!ready) {
    self.postMessage({ stage: 'starting the CAD kernel' });
    ready = occtimportjs({ locateFile: function (f) { return f; } });
  }

  ready.then(function (occt) {
    self.postMessage({ stage: 'reading the STEP file' });
    var result;
    try {
      result = occt.ReadStepFile(new Uint8Array(bytes), {
        linearUnit: 'millimeter',
        linearDeflectionType: 'bounding_box_ratio',
        linearDeflection: deflection,
        angularDeflection: 0.5
      });
    } catch (e) {
      self.postMessage({ error: 'the CAD kernel could not read that file: ' + e });
      return;
    }
    if (!result || !result.success || !result.meshes || !result.meshes.length) {
      self.postMessage({ error: 'that STEP file has no solids in it that could be turned into '
        + 'a surface. An assembly saved as references rather than geometry does this.' });
      return;
    }

    var total = 0, i, m;
    for (i = 0; i < result.meshes.length; i++) {
      total += result.meshes[i].index.array.length;
    }

    var out = new Float32Array(total * 3);
    var at = 0;
    var names = [];
    for (i = 0; i < result.meshes.length; i++) {
      m = result.meshes[i];
      if (names.length < 8 && m.name) names.push(m.name);
      var pos = m.attributes.position.array;
      var idx = m.index.array;
      for (var k = 0; k < idx.length; k++) {
        var v = idx[k] * 3;
        out[at++] = pos[v];
        out[at++] = pos[v + 1];
        out[at++] = pos[v + 2];
      }
    }

    self.postMessage({
      positions: out,
      triangles: total / 3,
      solids: result.meshes.length,
      names: names
    }, [out.buffer]);
  }).catch(function (e) {
    self.postMessage({ error: 'the CAD kernel would not start: ' + e });
  });
};
