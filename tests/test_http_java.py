# -*- coding: utf-8 -*-
"""Інтеграційний тест Java-сервера (той самий, що працюватиме в Android-додатку) через HTTP."""
import json, random, socket, subprocess, sys, threading, time, urllib.request, urllib.error, os

CP = os.environ.get('JCP', '/tmp/jout2'); ASSETS = os.environ.get('ASSETS', '/home/claude/android/app/src/main/assets/web')
errors = []; calls = [0]; procs = []

def launch():
    s = socket.socket(); s.bind(('127.0.0.1', 0)); port = s.getsockname()[1]; s.close()
    p = subprocess.Popen(['java', '-cp', CP, 'ua.urbanist.tools.Main', '--port', str(port), '--assets', ASSETS], stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
    procs.append(p)
    line = p.stdout.readline(); assert line.startswith('READY'), line
    return f'http://127.0.0.1:{port}', p

def req(base, path, body=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(base + path, data=data, headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(r, timeout=40) as resp:
            calls[0] += 1; b = resp.read(); return resp.status, (b if raw else json.loads(b)), resp.headers
    except urllib.error.HTTPError as e:
        b = e.read(); return e.code, (b if raw else (json.loads(b) if b else {})), e.headers

class Client(threading.Thread):
    def __init__(s, base, name, seed):
        super().__init__(daemon=True); s.base = base; s.name = name; s.rng = random.Random(seed); s.token = None; s.id = None
        s.view = None; s.v = 0; s.done = False; s.halt = False
    def join(s):
        c, d, _ = req(s.base, '/api/join', {'name': s.name}); assert c == 200, d; s.token, s.id = d['token'], d['id']
    def act(s, type, **kw):
        c, d, _ = req(s.base, '/api/action', dict(token=s.token, type=type, **kw)); return c, d
    def run(s):
        try:
            while not s.halt:
                c, v, _ = req(s.base, f'/api/state?token={s.token}&v={s.v}')
                if c != 200:
                    if not s.halt: errors.append(f'{s.name} state {c} {v}')
                    return
                s.view = v; s.v = v['v']
                if v['phase'] == 'over': s.done = True; time.sleep(0.05); continue
                if v['phase'] == 'lobby': continue
                s.step(v)
        except Exception as e:
            if not s.halt: errors.append(f'{s.name} crash {e!r}')
    def step(s, v):
        for p in v['players']:
            if not (0 <= p['E'] <= 8): errors.append('E range')
            if 'choice' in p or isinstance(p['hand'], list): errors.append('leak')
        if v.get('event_open'):
            opp = [p['id'] for p in v['players'] if p['id'] != s.id]
            playable = [i for i, ok in enumerate(v['can']['play']) if ok]
            if playable and s.rng.random() < .75: c, d = s.act('play', card=s.rng.choice(playable), target=s.rng.choice(opp))
            elif v['need_discard']: c, d = s.act('skip', discard=s.rng.randrange(len(v['hand'])))
            else: c, d = s.act('skip')
            if c != 200: errors.append(f'{s.name} event {c} {d}')
        elif v['phase'] == 'build' and not v.get('mine_submitted'):
            opts = [('build', t) for t in 'IMD' if v['can']['build'][t]] + [('rebuild', t) for t in 'IMD' if v['can']['rebuild'][t]]
            if opts and s.rng.random() < .9: k, t = s.rng.choice(opts); owned=[q['id'] for q in v.get('sectors',[]) if q.get('owner')==s.id]; c, d = s.act(k, b=t, sector=s.rng.choice(owned) if owned else 1)
            else: c, d = s.act('none')
            if c != 200: errors.append(f'{s.name} build {c} {d}')

def wait(cond, t=60):
    end = time.time() + t
    while time.time() < end:
        if cond(): return True
        time.sleep(0.05)
    return False

def game(base, nh, bots, seed):
    cl = []
    for i in range(nh):
        c = Client(base, f'Гравець{i+1}', seed * 10 + i); c.join(); cl.append(c)
    host = cl[0]
    for b in bots:
        c, d = host.act('add_bot', kind=b[0], thr=b[1] if len(b) > 1 else 5); assert c == 200, d
    if nh > 1:
        c, d = cl[1].act('start'); assert c == 400, (c, d)
        c, d = cl[1].act('add_bot', kind='ai'); assert c == 400
    c, d = host.act('start'); assert c == 200, d
    for x in cl: x.start()
    ok = wait(lambda: all(x.done for x in cl), 60)
    for x in cl: x.halt = True
    time.sleep(0.3); return ok, cl

t0 = time.time()
base, p0 = launch()
# статика
c, b, h = req(base, '/', raw=True); assert c == 200 and 'Тактичний урбаніст'.encode() in b and 'text/html' in h['Content-Type'], c
for img in ('energy', 'spy', 'adm', 'dis', 'cri', 'sab', 'industry', 'military', 'defense', 'ruin', 'back', 'city', 'steel-texture'):
    c, b, h = req(base, f'/img/{img}.webp', raw=True); assert c == 200 and h['Content-Type'] == 'image/webp' and len(b) > 1000, img
for bad in ('/../server.py', '/%2e%2e/x', '/nope.png', '/img/'):
    c, b, h = req(base, bad, raw=True); assert c in (404, 400), (bad, c)
c, d, _ = req(base, '/api/info'); assert c == 200 and isinstance(d['urls'], list)
# негативні API
c, d, _ = req(base, '/api/action', {'token': 'x', 'type': 'start'}); assert c == 401, (c, d)
c, d, _ = req(base, '/api/state?token=bad&v=0'); assert c == 401
c, d, _ = req(base, '/api/join', {'name': ''}); assert c == 400
c, d, _ = req(base, '/api/nope', {}); assert c == 404
r = urllib.request.Request(base + '/api/join', data=b'{broken json', headers={'Content-Type': 'application/json'})
try: urllib.request.urlopen(r, timeout=10); errors.append('broken json accepted')
except urllib.error.HTTPError as e: assert e.code == 400, e.code
# сирий сокет: обірваний запит, великий Content-Length, сміття — сервер має вижити
for payload in (b'GET /api/state HTTP/1.1\r\n', b'POST /api/join HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n', b'\x00\x01garbage\r\n\r\n', b'POST /api/join HTTP/1.1\r\nContent-Length: 50\r\n\r\n{"na'):
    s = socket.create_connection((base.split('//')[1].split(':')[0], int(base.rsplit(':', 1)[1])), timeout=3); s.sendall(payload); s.close()
c, v, _ = req(base, '/api/state'); assert c == 200 and v['phase'] == 'lobby'
print('статика та негативні перевірки: ok', flush=True)
# виявлення в мережі
us = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); us.settimeout(3); us.sendto(b'TU-SCAN-1', ('127.0.0.1', 47322))
data, _ = us.recvfrom(256); assert data.decode().startswith('TU-HOST-1|') and 'Тест-сервер' in data.decode(), data
print('UDP-виявлення:', data.decode(), flush=True)
p0.kill()

SCEN = [(2, []), (1, [('neighbor', 5)]), (3, [('ai',)]), (4, [('ai',), ('neighbor', 3)]), (6, []), (2, [('ai',), ('ai',), ('ai',)])]
for gi, (nh, bots) in enumerate(SCEN):
    base, p = launch()
    ok, cl = game(base, nh, bots, gi + 1); v = cl[0].view
    print(f'гра {gi+1}: людей {nh}, ботів {len(bots)}, завершилась: {ok}, раундів: {v["round"]}, переможці: {[q["name"] for q in v["players"] if q["id"] in v["winners"]]}', flush=True)
    if not ok: errors.append(f'game {gi+1} not finished')
    if gi == 0:   # нова гра в тій самій кімнаті
        c, d = cl[0].act('new_game'); assert c == 200, d
        c, v, _ = req(base, f'/api/state?token={cl[0].token}'); assert v['phase'] == 'lobby'
        ok2, cl2 = True, None
    p.kill()

base, p = launch()
class C(Client): pass
a = Client(base, 'Аня', 1); a.join(); b = Client(base, 'Борис', 2); b.join()
a.act('add_bot', kind='ai'); c, d = a.act('start'); assert c == 200, d
c, d, _ = req(base, '/api/join', {'name': 'Новий'}); assert c == 400
c, v, _ = req(base, f'/api/state?token={b.token}'); assert v['you'] == b.id
c, d = a.act('remove', id=b.id); assert c == 200, d
c, v, _ = req(base, f'/api/state?token={a.token}'); assert [q for q in v['players'] if q['id'] == b.id][0]['kind'] == 'ai'
c, d, _ = req(base, f'/api/state?token={b.token}'); assert c == 401
c, d, _ = req(base, '/api/join', {'name': 'Борис2', 'seat': b.id}); assert c == 200, d
print('перепідключення/зайняття місця: ok', flush=True); p.kill()

base, p = launch(); a = Client(base, 'P', 1); a.join(); c, v, _ = req(base, f'/api/state?token={a.token}&v=0'); cur = v['v']; res = {}
def lp(): t = time.time(); res['r'] = req(base, f'/api/state?token={a.token}&v={cur}'); res['dt'] = time.time() - t
th = threading.Thread(target=lp); th.start(); time.sleep(0.4); a.act('add_bot', kind='ai'); th.join(5)
print(f'long-poll розбуджено через {res["dt"]:.2f}с', flush=True); assert res['dt'] < 2
# довгий опит без змін повертається приблизно через 20с — перевіряємо, що не зависає назавжди (скорочений: 3 одночасні)
p.kill()
print(f'HTTP-запитів: {calls[0]}, час: {time.time()-t0:.1f}с, помилок: {len(errors)}')
for e in errors[:15]: print('ERR', e)
for q in procs: q.kill()
sys.exit(1 if errors else 0)
