'use strict';

var argscheck = require('cordova/argscheck'),
    exec = require('cordova/exec');

exports.faceTracker = function(success, error) {

    var quality = 50;    
    var colorOK = "#FFFFFF";
    var colorKO ="#FF0033";
    var messageTakePhotoOK = "Visage détecté. Vous pouvez prendre la photo.";
    var messageTakePhotoKO = "Visage non détecté. Rapprochez vous.";
    var minFaceSize = 0.82;
    
    console.log("quality="+quality);
    console.log("colorOK="+colorOK);
    console.log("colorKO="+colorKO);
    console.log("messageTakePhotoOK="+messageTakePhotoOK);
    console.log("messageTakePhotoKO="+messageTakePhotoKO);
    console.log("minFaceSize="+minFaceSize);

    var args = [quality, colorOK, colorKO, messageTakePhotoOK, messageTakePhotoKO, minFaceSize];
    
    exec(success, error, 'Mobilevision', 'faceTracker', args);
};