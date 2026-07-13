const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const chromeProfile = fs.mkdtempSync(path.join(os.tmpdir(), 'evento-celebrativo-karma-chrome-'));

module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
    ],
    client: {
      clearContext: true,
    },
    jasmineHtmlReporter: {
      suppressAll: true,
    },
    coverageReporter: {
      dir: path.join(__dirname, 'coverage', 'evento-celebrativo-web'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['ChromeHeadlessLocal'],
    customLaunchers: {
      ChromeHeadlessLocal: {
        base: 'ChromeHeadless',
        flags: [
          '--disable-gpu',
          '--disable-gpu-compositing',
          '--disable-dev-shm-usage',
          '--disable-features=VizDisplayCompositor',
          '--no-sandbox',
          `--user-data-dir=${chromeProfile}`,
        ],
      },
    },
    restartOnFileChange: true,
  });
};
