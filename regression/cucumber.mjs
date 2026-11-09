export default {
    paths: ['features/**/*.feature'],
    import: ['support/**/*.mjs', 'steps/**/*.mjs'],
    format: ['progress', 'html:reports/cucumber.html', 'json:reports/cucumber.json', 'junit:reports/junit.xml'],
    parallel: 0,
    retry: 0
};
