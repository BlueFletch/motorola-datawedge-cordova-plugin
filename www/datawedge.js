var cordova = require('cordova');
var exec = require('cordova/exec');

 /**
         * Constructor.
 *
 * @returns {DataWedge}
 */
function DataWedge() {

};

/**
 * Turn on DataWedge (default profile) and listen for event.  Listens for hardward button events.
 * 
 * @param successCallback - Success function should expect a barcode to be passed in
 * @param intentAction - action to listen for.  This is what you configured in the DataWedge app.  
 *       Defaults to: "com.bluefletch.motorola.datawedge.ACTION";
 */
DataWedge.prototype.start = function (intentAction) {
    exec(successCallback, null, 'MotorolaDataWedge', 'start', [intentAction]);
};
/**
 * Turn off DataWedge plugin
 */
DataWedge.prototype.stop = function () {
  
    exec(null, null, 'MotorolaDataWedge', 'stop', []);
};

/**
 * Activate a different profile for the data wedge.  For instance, to enable data processing rules
 */
DataWedge.prototype.switchProfile = function (profileName) {

    exec(null, null, 'MotorolaDataWedge', 'switchProfile', [profileName]);
};


/**
 * Register a callback for scan events.  This function will be called when barcdoes are read
 */
DataWedge.prototype.registerForBarcode = function (callback) {
    
    exec(callback, null, 'MotorolaDataWedge', 'scanner.register', []);
};

/**
 * De-register a callback for scan events.  
 */
DataWedge.prototype.unregisterBarcode = function () {
    
    exec(null, null, 'MotorolaDataWedge', 'scanner.unregister', []);
};

/**
 * Register a callback for magstripe reads
 */
DataWedge.prototype.registerForMagstripe = function (callback) {
    
    exec(callback, null, 'MotorolaDataWedge', 'magstripe.register', []);
};

/**
 * De-register a callback for magstripe events.  
 */
DataWedge.prototype.unregisterMagstripe = function () {
    
    exec(null, null, 'MotorolaDataWedge', 'magstripe.unregister', []);
};

/**
 * Manually turn on barcode scanner
 */
DataWedge.prototype.startScanner = function () {
    
    exec(null, null, 'MotorolaDataWedge', 'scanner.softScanOn', []);
};

/**
 * Manually turn off barcode scanner
 */
DataWedge.prototype.stopScanner = function () {
    exec(null, null, 'MotorolaDataWedge', 'scanner.softScanOff', []);
};




//create instance
var DataWedge = new DataWedge();

module.exports = DataWedge;