'use strict';

var exec = require('cordova/exec');

exports.faceTracker = function(success, error) {
  exec(success, error, 'Mobilevision', 'faceTracker', []);
};