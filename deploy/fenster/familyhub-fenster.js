// FamilyHub-Startfenster für macOS (JavaScript for Automation, in macOS eingebaut). Zwei Knöpfe, Starten und
// Beenden; die eigentliche Arbeit macht familyhub.sh im Ordner deploy (Docker prüfen, .env, Image, warten usw.).
// Daraus entsteht deploy/FamilyHub.app mit fenster/app-bauen.sh. Zum Ausprobieren ohne App:
//   FAMILYHUB_DEPLOY=/pfad/zu/deploy osascript -l JavaScript deploy/fenster/familyhub-fenster.js
// Mit FAMILYHUB_SNAPSHOT=/pfad/bild.png speichert das Fenster nach dem Aufbau ein Bild von sich und schließt.
ObjC.import('Cocoa');

const env = $.NSProcessInfo.processInfo.environment;
const envValue = name => {
  const v = env.objectForKey(name);
  return v.isNil() ? '' : v.js;
};

// Ordner deploy: neben der App (deploy/FamilyHub.app) oder aus FAMILYHUB_DEPLOY
const bundlePath = $.NSBundle.mainBundle.bundlePath.js;
const deployDir = envValue('FAMILYHUB_DEPLOY') ||
  (bundlePath.endsWith('.app') ? bundlePath.replace(/\/[^/]+\.app$/, '') : '');
const tmpDir = $.NSTemporaryDirectory().js;
const logFile = tmpDir + 'familyhub-fenster.log';
const healthFile = tmpDir + 'familyhub-fenster-health.txt';
const fm = $.NSFileManager.defaultManager;

const rgb = (hex, alpha = 1) => $.NSColor.colorWithSRGBRedGreenBlueAlpha(
  parseInt(hex.slice(1, 3), 16) / 255, parseInt(hex.slice(3, 5), 16) / 255, parseInt(hex.slice(5, 7), 16) / 255, alpha);
const BLUE = rgb('#2563EB');
const TEAL = rgb('#14B8A6');
const GREEN = rgb('#16A34A');
const AMBER = rgb('#D97706');
const RED = rgb('#DC2626');

function readFile(path) {
  const s = $.NSString.stringWithContentsOfFileEncodingError(path, $.NSUTF8StringEncoding, null);
  return s.isNil() ? '' : s.js;
}

// Port und Adresse wie familyhub.sh aus deploy/.env
function envSetting(key) {
  const m = readFile(deployDir + '/.env').match(new RegExp('^' + key + '=(.*)$', 'm'));
  return m ? m[1].trim() : '';
}
const port = () => envSetting('FAMILYHUB_PORT') || '8080';
const url = () => envSetting('FAMILYHUB_URL') || 'http://localhost:' + port();

function launch(command) {
  const task = $.NSTask.alloc.init;
  task.launchPath = '/bin/bash';
  task.arguments = $(['-c', command]);
  task.currentDirectoryPath = deployDir;
  task.launch;
  return task;
}
const quote = s => "'" + s.replace(/'/g, "'\\''") + "'";

// --- Zustand ---------------------------------------------------------------------------------------------------

let job = null;          // laufendes familyhub.sh (start/stop)
let jobAction = '';
let healthTask = null;   // Abfrage von /api/health im Hintergrund, blockiert das Fenster nicht
let ticks = 0;
let state = 'unknown';   // running | stopped | starting | stopping | error
let errorText = '';
let detailsOpen = false;

// --- Oberfläche ------------------------------------------------------------------------------------------------

const W = 440, H = 300, HEADER = 118, LOG_H = 170;

ObjC.registerSubclass({
  name: 'FHController',
  methods: {
    'start:': { types: ['void', ['id']], implementation: () => runJob('start') },
    'stop:': { types: ['void', ['id']], implementation: () => runJob('stop') },
    'openBrowser:': { types: ['void', ['id']], implementation: () => openBrowser() },
    'toggleDetails:': { types: ['void', ['id']], implementation: () => toggleDetails() },
    'tick:': { types: ['void', ['id']], implementation: () => tick() },
    'windowWillClose:': { types: ['void', ['id']], implementation: () => $.NSApp.terminate(null) },
  },
});
const controller = $.FHController.alloc.init;

const app = $.NSApplication.sharedApplication;
app.setActivationPolicy($.NSApplicationActivationPolicyRegular);

const mask = $.NSWindowStyleMaskTitled | $.NSWindowStyleMaskClosable | $.NSWindowStyleMaskMiniaturizable |
  $.NSWindowStyleMaskFullSizeContentView;
const win = $.NSWindow.alloc.initWithContentRectStyleMaskBackingDefer($.NSMakeRect(0, 0, W, H), mask,
  $.NSBackingStoreBuffered, false);
win.title = 'FamilyHub';
win.titlebarAppearsTransparent = true;
win.titleVisibility = $.NSWindowTitleHidden;
win.movableByWindowBackground = true;
win.releasedWhenClosed = false;
win.delegate = controller;
win.center;
const content = win.contentView;

// Farbverlauf oben (als Bild, damit es ohne eigene Zeichen-Klasse geht)
function gradientImage(w, h) {
  const img = $.NSImage.alloc.initWithSize($.NSMakeSize(w, h));
  img.lockFocus;
  $.NSGradient.alloc.initWithStartingColorEndingColor(BLUE, TEAL)
    .drawInRectAngle($.NSMakeRect(0, 0, w, h), -20);
  img.unlockFocus;
  return img;
}
const header = $.NSImageView.alloc.initWithFrame($.NSMakeRect(0, H - HEADER, W, HEADER));
header.image = gradientImage(W, HEADER);
header.imageScaling = $.NSImageScaleAxesIndependently;
header.autoresizingMask = $.NSViewWidthSizable | $.NSViewMinYMargin;
content.addSubview(header);

function label(text, x, y, w, h, font, color) {
  const l = $.NSTextField.labelWithString(text);
  l.frame = $.NSMakeRect(x, y, w, h);
  l.font = font;
  if (color) l.textColor = color;
  l.autoresizingMask = $.NSViewMinYMargin;
  content.addSubview(l);
  return l;
}

// Haus mit Herz, weiß auf dem Verlauf
const iconPath = deployDir + '/fenster/familyhub.png';
// Weißer Rahmen ins Bild gezeichnet: layer.borderColor (CGColor) bringt JXA zum Absturz
function framedIcon(path, size) {
  const src = $.NSImage.alloc.initWithContentsOfFile(path);
  const img = $.NSImage.alloc.initWithSize($.NSMakeSize(size, size));
  img.lockFocus;
  $.NSColor.colorWithWhiteAlpha(1, 0.9).setFill;
  $.NSBezierPath.bezierPathWithRoundedRectXRadiusYRadius($.NSMakeRect(0, 0, size, size), size * 0.26, size * 0.26).fill;
  src.drawInRectFromRectOperationFraction($.NSMakeRect(2.5, 2.5, size - 5, size - 5), $.NSZeroRect,
    $.NSCompositingOperationSourceOver, 1);
  img.unlockFocus;
  return img;
}
if (fm.fileExistsAtPath(iconPath)) {
  const icon = $.NSImageView.alloc.initWithFrame($.NSMakeRect(28, H - HEADER + 30, 60, 60));
  icon.image = framedIcon(iconPath, 60);
  icon.imageScaling = $.NSImageScaleProportionallyUpOrDown;
  icon.autoresizingMask = $.NSViewMinYMargin;
  content.addSubview(icon);
}
label('FamilyHub', 104, H - HEADER + 56, 300, 30, $.NSFont.systemFontOfSizeWeight(24, 0.4), $.NSColor.whiteColor);
label('Der Familienplaner auf diesem Computer', 105, H - HEADER + 34, 320, 20, $.NSFont.systemFontOfSize(13),
  $.NSColor.colorWithWhiteAlpha(1, 0.9));

// Status: farbiger Punkt, Text und Adresse
const dot = label('●', 28, H - HEADER - 52, 20, 24, $.NSFont.systemFontOfSize(18), $.NSColor.tertiaryLabelColor);
const statusLabel = label('Status wird geprüft …', 50, H - HEADER - 50, 360, 20,
  $.NSFont.systemFontOfSizeWeight(15, 0.3), $.NSColor.labelColor);
const hintLabel = label('', 50, H - HEADER - 90, 362, 38, $.NSFont.systemFontOfSize(12),
  $.NSColor.secondaryLabelColor);
hintLabel.maximumNumberOfLines = 2;
hintLabel.lineBreakMode = $.NSLineBreakByWordWrapping;

// Knöpfe als selbst gezeichnete Bilder: macOS färbt eigene Knöpfe nur im aktiven Fenster, so sehen sie immer
// gleich aus (Verlauf für Starten, roter Umriss für Beenden; blass, solange sie nicht gehen). Nur feste Farben,
// die im hellen und dunklen Modus lesbar sind: Ein Bild passt sich einem Wechsel des Modus nicht an.
const BW = 190, BH = 42;
function buttonImage(title, primary, enabled) {
  const img = $.NSImage.alloc.initWithSize($.NSMakeSize(BW, BH));
  img.lockFocus;
  const shape = $.NSBezierPath.bezierPathWithRoundedRectXRadiusYRadius($.NSMakeRect(1, 1, BW - 2, BH - 2), 11, 11);
  const alpha = enabled ? 1 : 0.4;
  if (primary) {
    $.NSGradient.alloc.initWithStartingColorEndingColor(rgb('#2563EB', alpha), rgb('#0D9488', alpha))
      .drawInBezierPathAngle(shape, 0);
  } else {
    rgb('#E5484D', enabled ? 0.1 : 0.04).setFill;
    shape.fill;
    rgb('#E5484D', enabled ? 0.9 : 0.35).setStroke;
    shape.lineWidth = 1.5;
    shape.stroke;
  }
  const font = $.NSFont.systemFontOfSizeWeight(15, 0.4);
  const color = primary ? $.NSColor.colorWithWhiteAlpha(1, enabled ? 1 : 0.85) : rgb('#E5484D', enabled ? 1 : 0.45);
  const attrs = $({ NSFont: font, NSColor: color });
  const size = $(title).sizeWithAttributes(attrs);
  $(title).drawAtPointWithAttributes($.NSMakePoint((BW - size.width) / 2, (BH - size.height) / 2), attrs);
  img.unlockFocus;
  return img;
}
function button(title, action, x, primary) {
  const b = $.NSButton.buttonWithTitleTargetAction('', controller, action);
  b.frame = $.NSMakeRect(x, 50, BW, BH);
  b.bordered = false;
  b.imagePosition = $.NSImageOnly;
  b.image = buttonImage(title, primary, true);
  b.toolTip = title.replace(/^\S+\s+/, '');
  b.autoresizingMask = $.NSViewMinYMargin;
  content.addSubview(b);
  return { view: b, title, primary, enabled: true };
}
function setEnabled(btn, enabled) {
  if (btn.enabled === enabled) return;
  btn.enabled = enabled;
  btn.view.enabled = enabled;
  btn.view.image = buttonImage(btn.title, btn.primary, enabled);
}
const startButton = button('▶  Starten', 'start:', 24, true);
startButton.view.keyEquivalent = '\r';
const stopButton = button('■  Beenden', 'stop:', 226, false);

function linkButton(title, action, x, width) {
  const b = $.NSButton.buttonWithTitleTargetAction(title, controller, action);
  b.bordered = false;
  b.frame = $.NSMakeRect(x, 16, width, 22);
  b.font = $.NSFont.systemFontOfSize(13);
  b.contentTintColor = BLUE; // färbt bei rahmenlosen Knöpfen den Text (ab macOS 10.14)
  b.autoresizingMask = $.NSViewMinYMargin;
  content.addSubview(b);
  return b;
}
const browserLink = linkButton('Im Browser öffnen ›', 'openBrowser:', 24, 150);
const detailsLink = linkButton('Details anzeigen', 'toggleDetails:', 290, 130);
detailsLink.alignment = $.NSTextAlignmentRight;

// Protokoll (ausklappbar)
const scroll = $.NSScrollView.alloc.initWithFrame($.NSMakeRect(24, 16, W - 48, LOG_H - 20));
scroll.hasVerticalScroller = true;
scroll.borderType = $.NSBezelBorder;
scroll.autoresizingMask = $.NSViewWidthSizable;
const logView = $.NSTextView.alloc.initWithFrame($.NSMakeRect(0, 0, W - 52, LOG_H - 20));
logView.editable = false;
logView.font = $.NSFont.monospacedSystemFontOfSizeWeight(11, 0);
logView.textContainerInset = $.NSMakeSize(6, 6);
scroll.documentView = logView;
scroll.hidden = true;
content.addSubview(scroll);

function toggleDetails() {
  detailsOpen = !detailsOpen;
  const f = win.frame;
  const delta = detailsOpen ? LOG_H : -LOG_H;
  win.setFrameDisplayAnimate($.NSMakeRect(f.origin.x, f.origin.y - delta, f.size.width, f.size.height + delta),
    true, true);
  scroll.hidden = !detailsOpen;
  setLinkTitle(detailsLink, detailsOpen ? 'Details ausblenden' : 'Details anzeigen');
}
function setLinkTitle(b, title) {
  b.title = title;
}

// --- Verhalten -------------------------------------------------------------------------------------------------

function render() {
  const busy = state === 'starting' || state === 'stopping';
  const s = {
    running: ['Läuft', GREEN, url()],
    stopped: ['Beendet', $.NSColor.tertiaryLabelColor, 'Mit „Starten“ geht es los. Docker Desktop muss installiert sein.'],
    starting: ['Startet …', AMBER, 'Beim ersten Mal dauert das einige Minuten. Der Browser öffnet sich danach von selbst.'],
    stopping: ['Wird beendet …', AMBER, 'Die Daten bleiben erhalten.'],
    error: ['Fehler', RED, errorText],
    unknown: ['Status wird geprüft …', $.NSColor.tertiaryLabelColor, ''],
  }[state];
  statusLabel.stringValue = s[0];
  dot.textColor = s[1];
  hintLabel.stringValue = s[2];
  setEnabled(startButton, !busy && state !== 'running');
  setEnabled(stopButton, !busy && state !== 'stopped');
  browserLink.hidden = state !== 'running';
}

function runJob(action) {
  if (job) return;
  if (!deployDir || !fm.fileExistsAtPath(deployDir + '/familyhub.sh')) {
    state = 'error';
    errorText = 'familyhub.sh nicht gefunden. Die App muss im Ordner deploy liegen.';
    return render();
  }
  jobAction = action;
  state = action === 'start' ? 'starting' : 'stopping';
  errorText = '';
  render();
  job = launch(quote(deployDir + '/familyhub.sh') + ' ' + action + ' > ' + quote(logFile) + ' 2>&1');
}

function finishJob() {
  const ok = job.terminationStatus === 0;
  const log = readFile(logFile);
  job = null;
  if (!ok) {
    const m = log.match(/Fehler: ([^\n]+)/g);
    errorText = m ? m[m.length - 1].replace(/^Fehler: /, '') : 'Unbekannter Fehler, siehe Details.';
    state = 'error';
    if (!detailsOpen) toggleDetails();
  } else {
    state = jobAction === 'start' ? 'running' : 'stopped';
  }
  render();
}

function checkHealth() {
  if (healthTask && healthTask.isRunning) return;
  if (healthTask) {
    const up = readFile(healthFile).includes('"UP"');
    if (!job && state !== 'error') state = up ? 'running' : 'stopped';
    if (!job && state === 'error' && up) state = 'running';
    render();
  }
  healthTask = launch('curl -s -m 2 ' + quote('http://localhost:' + port() + '/api/health') + ' > ' +
    quote(healthFile) + ' 2>/dev/null');
}

function tick() {
  ticks++;
  if (job) {
    logView.string = readFile(logFile);
    logView.scrollRangeToVisible($.NSMakeRange(logView.string.length, 0));
    if (!job.isRunning) finishJob();
  } else if (ticks % 6 === 1) {
    checkHealth();
  }
}

function openBrowser() {
  $.NSWorkspace.sharedWorkspace.openURL($.NSURL.URLWithString(url()));
}

// --- Start -----------------------------------------------------------------------------------------------------

render();
$.NSTimer.scheduledTimerWithTimeIntervalTargetSelectorUserInfoRepeats(0.5, controller, 'tick:', null, true);
win.makeKeyAndOrderFront(null);
app.activateIgnoringOtherApps(true);

const snapshot = envValue('FAMILYHUB_SNAPSHOT');
if (snapshot) {
  // Testmodus: nach dem ersten Statusabgleich ein Bild des Fensters speichern und beenden
  const forceState = envValue('FAMILYHUB_SNAPSHOT_STATE');
  // Knopfdruck nachspielen (start/stop) und warten, bis familyhub.sh fertig ist
  const action = envValue('FAMILYHUB_SNAPSHOT_ACTION');
  if (action) {
    runJob(action);
    while (job) $.NSRunLoop.currentRunLoop.runUntilDate($.NSDate.dateWithTimeIntervalSinceNow(0.5));
    console.log('Zustand nach ' + action + ': ' + state + (errorText ? ' (' + errorText + ')' : ''));
  }
  if (envValue('FAMILYHUB_SNAPSHOT_LIGHT')) app.appearance = $.NSAppearance.appearanceNamed($.NSAppearanceNameAqua);
  $.NSRunLoop.currentRunLoop.runUntilDate($.NSDate.dateWithTimeIntervalSinceNow(3.5));
  if (forceState) { state = forceState; errorText = 'Port 8080 ist belegt, z. B. durch ein laufendes Entwicklungs-Backend.'; render(); }
  if (envValue('FAMILYHUB_SNAPSHOT_DETAILS')) { toggleDetails(); logView.string = 'FamilyHub wird gestartet …\nWarten, bis FamilyHub bereit ist …'; }
  $.NSRunLoop.currentRunLoop.runUntilDate($.NSDate.dateWithTimeIntervalSinceNow(0.6));
  const frameView = content.superview;
  const rep = frameView.bitmapImageRepForCachingDisplayInRect(frameView.bounds);
  frameView.cacheDisplayInRectToBitmapImageRep(frameView.bounds, rep);
  rep.representationUsingTypeProperties($.NSBitmapImageFileTypePNG, $({})).writeToFileAtomically(snapshot, true);
  app.terminate(null);
} else if (bundlePath.endsWith('.app') === false) {
  // Mit osascript gestartet: eigene Ereignisschleife (in der App läuft sie schon)
  app.run;
}
