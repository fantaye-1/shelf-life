// Karma runs the unit tests in a real (headless) Chrome.
// CI uses ChromeHeadlessCI because GitHub's Ubuntu runners don't allow Chrome's sandbox.
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    reporters: ['progress'],
    browsers: ['Chrome'],
    customLaunchers: {
      ChromeHeadlessCI: { base: 'ChromeHeadless', flags: ['--no-sandbox'] },
    },
    restartOnFileChange: true,
  });
};
