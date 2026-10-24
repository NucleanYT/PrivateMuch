'use strict';
// The page lives at the repo root (index.html, icons/); copy it into the app on every build,
// so the Windows app always shows the same page as the Android app and the website.
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..', '..');
const web = path.join(__dirname, '..', 'web');
fs.rmSync(web, { recursive: true, force: true });
fs.mkdirSync(path.join(web, 'icons'), { recursive: true });
fs.copyFileSync(path.join(root, 'index.html'), path.join(web, 'index.html'));
for (const icon of fs.readdirSync(path.join(root, 'icons'))) {
  fs.copyFileSync(path.join(root, 'icons', icon), path.join(web, 'icons', icon));
}
fs.mkdirSync(path.join(__dirname, '..', 'build'), { recursive: true });
fs.copyFileSync(path.join(root, 'icons', 'icon-512.png'), path.join(__dirname, '..', 'build', 'icon.png'));
console.log('Copied the page and icons into the app.');
