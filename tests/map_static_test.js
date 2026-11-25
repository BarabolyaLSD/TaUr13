// Static validation for the interactive district map definitions (no browser dependency).
const fs = require('fs');
const vm = require('vm');
const html = fs.readFileSync('app/src/main/assets/web/index.html', 'utf8');
const start = html.indexOf('const MAP_SECTORS=[');
const end = html.indexOf('const MAP_COLORS=', start);
if (start < 0 || end < 0) throw new Error('Map sector definitions not found');
const source = html.slice(start, end) + '\nthis.MAP_SECTORS=MAP_SECTORS;';
const ctx = {};
vm.runInNewContext(source, ctx);
const sectors = ctx.MAP_SECTORS;
if (sectors.length !== 12) throw new Error(`Expected 12 sectors, got ${sectors.length}`);
if (sectors.some(s => typeof s.desc !== 'string' || s.desc.length < 20)) throw new Error('District descriptions missing or too short');
const ids = sectors.map(s => s.id);
if (new Set(ids).size !== 12 || ids.some((id, i) => id !== i + 1)) throw new Error('Sector IDs are not unique/continuous');
for (const sector of sectors) {
  if (!sector.n || !sector.short || !sector.t || !Number.isFinite(sector.cx) || !Number.isFinite(sector.cy)) throw new Error(`Incomplete sector ${sector.id}`);
  const pts = sector.p.split(' ').map(p => p.split(',').map(Number));
  if (pts.length < 4 || pts.some(([x, y]) => !Number.isFinite(x) || !Number.isFinite(y) || x < 0 || x > 1000 || y < 0 || y > 600)) throw new Error(`Invalid polygon for sector ${sector.id}`);
  const area = Math.abs(pts.reduce((a, p, i) => { const q = pts[(i+1)%pts.length]; return a+p[0]*q[1]-q[0]*p[1]; }, 0)/2);
  if (area < 1000) throw new Error(`District polygon too small: ${sector.id}`);
}
for (const marker of ['data-sector', 'map-tools', 'map-sector-info', 'map-legend', 'pointerdown', 'pointermove', 'pointerup', 'wheel', 'mapFocus', 'city-map-bg', 'pointer-events:auto', 'data-map=\"zoomin\"']) {
  if (!html.includes(marker)) throw new Error(`Missing map interaction marker: ${marker}`);
}
for (const asset of ['img/city.webp', 'img/energy.webp', 'img/industry.webp', 'img/military.webp', 'img/defense.webp']) {
  if (!fs.existsSync(`app/src/main/assets/web/${asset}`)) throw new Error(`Missing static asset: ${asset}`);
}
console.log(`District map static tests: OK (${sectors.length} sectors, unique IDs, valid polygons, interactions wired, map assets present)`);

// Validate server-synchronized sector ownership mapping, balanced for 1–6 players.
const ownerStart = html.indexOf('function mapSectorData(id)');
const ownerEnd = html.indexOf('function mapTypeIcon(t)', ownerStart);
if (ownerStart < 0 || ownerEnd < 0) throw new Error('Sector state mapping functions not found');
const ownerSource = html.slice(ownerStart, ownerEnd) + '\nthis.mapOwnerIndex=mapOwnerIndex; this.mapOwner=mapOwner;';
for (let playerCount = 1; playerCount <= 6; playerCount++) {
  const serverSectors = sectors.map((s, i)=>({id:s.id, owner:i%playerCount+1, I:0,M:0,D:0}));
  const ownerCtx = { MAP_SECTORS: sectors, S: { players: Array.from({length:playerCount}, (_,i)=>({id:i+1,name:`P${i+1}`})), sectors:serverSectors } };
  vm.runInNewContext(ownerSource, ownerCtx);
  const owners = sectors.map(s => ownerCtx.mapOwnerIndex(s.id));
  if (owners.some(ix => ix < 0 || ix >= playerCount)) throw new Error(`Invalid owner index for ${playerCount} players`);
  const distribution = Array.from({length:playerCount},(_,ix)=>owners.filter(x=>x===ix).length);
  if (Math.max(...distribution)-Math.min(...distribution) > 1) throw new Error(`Sector ownership distribution unbalanced for ${playerCount} players: ${distribution}`);
}
if (!html.includes('sector:mapState.selected') && !html.includes('sector:p.sector')) throw new Error('Build action does not send sector ID');
console.log('Server sector owner mapping tests: OK (balanced for 1–6 players; build target is transmitted)');
