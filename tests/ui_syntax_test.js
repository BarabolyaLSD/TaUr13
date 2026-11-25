// Validate the embedded game UI JavaScript syntax without a browser runtime.
const fs = require('fs');
const vm = require('vm');
const html = fs.readFileSync('app/src/main/assets/web/index.html', 'utf8');
const match = html.match(/<script>([\s\S]*?)<\/script>/);
if (!match) throw new Error('Inline game script is missing');
new vm.Script(match[1], { filename: 'index.html:inline-script' });
console.log('Embedded UI JavaScript syntax: OK');
