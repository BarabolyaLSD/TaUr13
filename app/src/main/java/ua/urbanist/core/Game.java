package ua.urbanist.core;

import java.util.*;

/** Авторитетна логіка гри «Тактичний урбаніст» (режим одночасної економіки). Потокобезпечна. */
public final class Game {
    public final Object lock = new Object();
    private final Random rng;
    private long version = 1; private int nextId = 1;
    private final List<Player> players = new ArrayList<Player>();
    private static final class SectorState {
        final int id; int ownerId; int I, M, D;
        SectorState(int id) { this.id = id; }
        int count(char t) { return t=='I'?I:t=='M'?M:D; }
        void add(char t) { if(t=='I') I++; else if(t=='M') M++; else D++; }
        void remove(char t) { remove(t,1); } void remove(char t,int n) { for(int i=0;i<n;i++){ if(t=='I'&&I>0) I--; else if(t=='M'&&M>0) M--; else if(t=='D'&&D>0) D--; else break; } }
        Map<String,Object> json() { Map<String,Object> m=new LinkedHashMap<String,Object>(); m.put("id",id);m.put("owner",ownerId);m.put("I",I);m.put("M",M);m.put("D",D);return m; }
    }
    private final List<SectorState> sectors = new ArrayList<SectorState>();
    private String phase = "lobby";
    private final List<Map<String, Object>> log = new ArrayList<Map<String, Object>>();
    private int round, active, first, games;
    private final List<String> deck = new ArrayList<String>(), discard = new ArrayList<String>();
    private List<Integer> winners = new ArrayList<Integer>(); private boolean cap;
    private Runnable roundHook;
    /** Лише для тестів: викликається на початку кожного раунду. */
    public void setRoundHook(Runnable r) { roundHook = r; }

    public Game(long seed) { rng = new Random(seed); for(int i=1;i<=12;i++) sectors.add(new SectorState(i)); }
    public Game() { this(System.nanoTime()); }

    // ---------------------------------------------------------------- допоміжне
    private void bump() { version++; lock.notifyAll(); }
    private void say(String text, String kind) {
        Map<String, Object> m = new LinkedHashMap<String, Object>(); m.put("r", round); m.put("t", text); m.put("k", kind);
        log.add(m); while (log.size() > 200) log.remove(0);
    }
    private Player byToken(String tok) {
        if (tok == null || tok.isEmpty()) return null;
        for (Player p : players) if ("human".equals(p.kind) && tok.equals(p.token)) return p;
        return null;
    }
    private Player byId(Integer id) { if (id == null) return null; for (Player p : players) if (p.id == id) return p; return null; }
    private List<Player> humans() { List<Player> h = new ArrayList<Player>(); for (Player p : players) if ("human".equals(p.kind)) h.add(p); return h; }
    private Player host() { List<Player> h = humans(); return h.isEmpty() ? null : h.get(0); }
    private static String token() { byte[] b = new byte[16]; new java.security.SecureRandom().nextBytes(b); StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }

    public boolean validToken(String tok) { synchronized (lock) { return byToken(tok) != null; } }
    public String phase() { synchronized (lock) { return phase; } }
    public int round() { synchronized (lock) { return round; } }
    public List<Integer> winners() { synchronized (lock) { return new ArrayList<Integer>(winners); } }
    public int playerCount() { synchronized (lock) { return players.size(); } }
    public Player playerById(int id) { synchronized (lock) { return byId(id); } }
    /** Для самотестів: стан гравця. */
    public List<Player> playersUnsafe() { return players; }
    public List<String> deckUnsafe() { return deck; }
    public List<String> discardUnsafe() { return discard; }

    // ---------------------------------------------------------------- лобі
    public Player join(String name, Integer seat) {
        synchronized (lock) {
            name = name == null ? "" : name.trim();
            if (name.length() > 16) name = name.substring(0, 16);
            if (name.isEmpty()) throw new GameError("Введіть ім’я");
            if (seat != null) {
                Player p = byId(seat);
                if (p == null || "lobby".equals(phase)) throw new GameError("Місце недоступне");
                if ("human".equals(p.kind) && p.online()) throw new GameError("Це місце вже зайняте");
                p.kind = "human"; p.name = name; p.token = token(); p.lastSeen = System.currentTimeMillis();
                say(name + " зайняв місце гравця.", "info"); bump(); return p;
            }
            if (!"lobby".equals(phase)) throw new GameError("Гра вже триває. Займіть вільне місце бота або відключеного гравця.");
            if (players.size() >= Rules.MAX_PLAYERS) throw new GameError("Кімната заповнена (6 гравців)");
            for (Player q : players) if (q.name.equalsIgnoreCase(name)) throw new GameError("Таке ім’я вже є");
            Player p = new Player(nextId++, name, "human", token(), "I4DM");
            players.add(p); bump(); return p;
        }
    }

    public void addBot(String kind, int thr) {
        synchronized (lock) {
            if (!"lobby".equals(phase)) throw new GameError("Ботів додають у лобі");
            if (players.size() >= Rules.MAX_PLAYERS) throw new GameError("Кімната заповнена");
            Player p;
            if ("neighbor".equals(kind)) {
                int n = 1; for (Player q : players) if ("neighbor".equals(q.kind)) n++;
                p = new Player(nextId++, n == 1 ? "Сусід" : "Сусід " + n, "neighbor", null, "I4DM"); p.thr = thr;
            } else {
                int n = 1; for (Player q : players) if ("ai".equals(q.kind)) n++;
                String[] pol = {"I4DM", "I4MD", "I5MD", "I5DM"};
                p = new Player(nextId++, "Бот " + n, "ai", null, pol[rng.nextInt(pol.length)]);
            }
            players.add(p); bump();
        }
    }

    private void remove(Integer pid) {
        Player p = byId(pid);
        if (p == null) throw new GameError("Гравця не знайдено");
        if ("lobby".equals(phase)) players.remove(p);
        else if ("human".equals(p.kind)) {
            p.kind = "ai"; p.token = null; p.policy = rng.nextBoolean() ? "I4DM" : "I4MD"; p.name += " (бот)";
            say(p.name + ": керування передано боту.", "info");
        } else throw new GameError("Це вже бот");
        bump();
    }

    private SectorState sector(int id) { return id>=1 && id<=sectors.size() ? sectors.get(id-1) : null; }
    private void resetSectors() {
        for (SectorState s : sectors) { s.ownerId=0; s.I=s.M=s.D=0; }
        if (players.isEmpty()) return;
        for (int i=0;i<sectors.size();i++) sectors.get(i).ownerId=players.get(i%players.size()).id;
    }
    private SectorState firstOwnedSector(Player p) { for(SectorState s:sectors) if(s.ownerId==p.id) return s; return null; }
    private void reconcileSectorBuildings(Player p) {
        for(char t:Rules.TYPES) {
            int target=p.cnt(t), sum=0; for(SectorState s:sectors) if(s.ownerId==p.id) sum+=s.count(t);
            while(sum>target) { boolean changed=false; for(int i=sectors.size()-1;i>=0;i--){SectorState s=sectors.get(i);if(s.ownerId==p.id&&s.count(t)>0){s.remove(t);sum--;changed=true;break;}} if(!changed)break; }
            while(sum<target) { SectorState s=firstOwnedSector(p);if(s==null)break;s.add(t);sum++; }
        }
    }

    public void start(boolean requireHuman) {
        synchronized (lock) {
            if (!"lobby".equals(phase)) throw new GameError("Гра вже триває");
            if (players.size() < 2) throw new GameError("Потрібно щонайменше 2 учасники: додайте бота або Сусіда");
            if (requireHuman && humans().isEmpty()) throw new GameError("Потрібен хоча б один живий гравець");
            for (Player p : players) p.reset();
            resetSectors();
            deck.clear(); discard.clear();
            for (int k = 0; k < Rules.CARDS.length; k++) for (int j = 0; j < Rules.DECK[k]; j++) deck.add(Rules.CARDS[k]);
            Collections.shuffle(deck, rng);
            first = rng.nextInt(players.size()); round = 0; log.clear(); winners = new ArrayList<Integer>(); cap = false; games++;
            say("Гру розпочато. Усі стартують з 1 енергією, без промисловості, війська й оборони.", "info");
            startRound(); advance(); bump();
        }
    }

    private void newGame() {
        if (!"over".equals(phase)) throw new GameError("Гра ще не завершена");
        phase = "lobby"; log.clear(); round = 0; winners = new ArrayList<Integer>();
        Iterator<Player> it = players.iterator();
        while (it.hasNext()) { Player p = it.next(); if ("human".equals(p.kind) && !p.online()) it.remove(); }
        for (Player p : players) p.reset();
        resetSectors();
        bump();
    }

    // ---------------------------------------------------------------- раунд
    private void draw(Player p) {
        if (deck.isEmpty()) { deck.addAll(discard); discard.clear(); Collections.shuffle(deck, rng); }
        if (!deck.isEmpty()) p.hand.add(deck.remove(deck.size() - 1));
    }

    private void startRound() {
        round++;
        active = (first + round - 1) % players.size();
        for (Player p : players) {
            int inc = Rules.INCOME[p.I];
            p.E = Math.min(Rules.E_MAX, p.E + inc);
            if (p.E == 0 && inc == 0) p.E = 1;
            p.plan = 1 + rng.nextInt(6); p.choiceKind = null; p.submitted = false;
        }
        Player a = players.get(active);
        say("Раунд " + round + ". Надходження енергії. Маркер події — " + a.name + ".", "round");
        draw(a); phase = "event";
        if (roundHook != null) roundHook.run();
    }

    private void discardExtra(Player a, Integer idx) {
        while (a.hand.size() > Rules.HAND) {
            int i = (idx != null && idx >= 0 && idx < a.hand.size()) ? idx : 0;
            discard.add(a.hand.remove(i)); idx = null;
        }
    }

    private void endEvent(Player a, String card, Player target, Integer targetSectorId) {
        if (card != null) {
            a.E -= Rules.cardCost(card); a.hand.remove(card); discard.add(card);
            List<String> eff = resolveSector(card, target, targetSectorId, a);
            say(a.name + " грає «" + Rules.cardName(card) + "» (−" + Rules.cardCost(card) + "⚡) проти " + target.name + ": " + (eff.isEmpty() ? "ефекту немає" : join(eff)) + ".", "event");
        } else if (round > Rules.NO_CARD_ROUNDS) say(a.name + " не грає подію.", "event");
        else say("Події грають з 3-го раунду.", "event");
        discardExtra(a, null);
        phase = "build";
        for (Player p : players) { p.choiceKind = null; p.submitted = false; }
    }

    private static String join(List<String> l) { StringBuilder b = new StringBuilder(); for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(", "); b.append(l.get(i)); } return b.toString(); }

    static int hit(Player q, char t, int n) {
        int cur = q.cnt(t); int lo = (t == 'I' && q.E == 0) ? Math.min(cur, 1) : 0;
        int nw = Math.max(lo, cur - n); int lost = cur - nw;
        q.setCnt(t, nw); q.setRuin(t, q.ruin(t) + lost); return lost;
    }
    private static void h(List<String> eff, Player q, char t, int n) { int l = hit(q, t, n); if (l > 0) eff.add("−" + l + " " + Rules.name(t)); }

    public static List<String> resolve(String c, Player q) {
        List<String> eff = new ArrayList<String>();
        switch (c) {
            case "spy": if (q.D > 0) h(eff, q, 'D', 1); else h(eff, q, 'I', 2); break;
            case "adm": if (q.M > 0) h(eff, q, 'M', 1); else h(eff, q, 'I', 2); if (q.D > 0) h(eff, q, 'D', 1); break;
            case "dis": if (q.I > 0) h(eff, q, 'I', 1); break;
            case "cri": if (q.E > 0) eff.add("енергія " + q.E + " → 0"); q.E = 0; break;
            default: if (q.D > 0) h(eff, q, 'D', 1); else h(eff, q, 'I', 1);
        }
        return eff;
    }

    /** Sector-targeted attacks: apply structure damage in the selected district, then optionally capture it. */
    private List<String> resolveSector(String card, Player target, Integer sectorId, Player attacker) {
        if ("cri".equals(card)) return resolve(card, target); // Economic crisis remains player-wide.
        List<String> eff = new ArrayList<String>();
        SectorState fallback = firstOwnedSector(target);
        SectorState sec = sector(sectorId == null ? (fallback==null?0:fallback.id) : sectorId);
        if (sec == null || sec.ownerId != target.id) throw new GameError("Оберіть сектор противника");
        char hitType = 0; int damage = 0;
        switch (card) {
            case "spy": hitType = sec.D > 0 ? 'D' : 'I'; damage = hitType=='D'?1:2; break;
            case "adm": hitType = sec.M > 0 ? 'M' : 'I'; damage = hitType=='M'?1:2; break;
            case "dis": hitType = 'I'; damage = 1; break;
            default: hitType = sec.D > 0 ? 'D' : 'I'; damage = 1; break; // sabotage
        }
        int before=sec.count(hitType), after=Math.max(0,before-damage), lost=before-after;
        if(hitType=='I' && target.E==0) after=Math.max(Math.min(before,1),after);
        lost=before-after;
        if(lost>0){ sec.remove(hitType,lost); target.setCnt(hitType,Math.max(0,target.cnt(hitType)-lost)); target.setRuin(hitType,target.ruin(hitType)+lost); eff.add("Сектор "+sec.id+": −"+lost+" "+Rules.name(hitType)); }
        if("adm".equals(card) && sec.D>0){ int dLost=sec.D>0?1:0; sec.remove('D'); target.setCnt('D',Math.max(0,target.D-dLost)); target.setRuin('D',target.rD+dLost); if(dLost>0)eff.add("Сектор "+sec.id+": −1 оборона"); }
        // Capture is allowed only after the district is cleared and the attacker has at least one army unit.
        if(sec.I==0 && sec.M==0 && sec.D==0 && attacker.M>0 && sec.ownerId==target.id){
            int owned=0; for(SectorState q:sectors) if(q.ownerId==target.id) owned++;
            if(owned>1){ sec.ownerId=attacker.id; eff.add("Сектор "+sec.id+" захоплено"); }
        }
        return eff;
    }

    private void autoBuilds() {
        for (Player p : players) {
            if (p.submitted) continue;
            Object[] ch = null;
            if ("ai".equals(p.kind)) ch = Bots.aiBuild(p);
            else if ("neighbor".equals(p.kind)) ch = Bots.nbBuild(p);
            else if (Rules.hasAction(p)) continue;                 // людина вирішує сама
            if (ch == null) p.choiceKind = null; else { p.choiceKind = (String) ch[0]; p.choiceType = (Character) ch[1]; SectorState dst=firstOwnedSector(p); p.choiceSector=dst==null?0:dst.id; }
            p.submitted = true;
        }
    }

    private void resolveBuild() {
        List<String> parts = new ArrayList<String>();
        for (Player p : players) {
            String k = p.choiceKind; char t = p.choiceType;
            if (k != null && k.equals("build") && Rules.canBuild(p, t)) {
                p.E -= Rules.cost(t); p.setCnt(t, p.cnt(t) + 1); SectorState dst=sector(p.choiceSector); if(dst!=null && dst.ownerId==p.id) dst.add(t); parts.add(p.name + ": збудував " + Rules.name(t));
            } else if (k != null && k.equals("rebuild") && Rules.canRebuild(p, t)) {
                p.E -= Rules.rebuildCost(t); p.setRuin(t, p.ruin(t) - 1); p.setCnt(t, p.cnt(t) + 1); SectorState dst=sector(p.choiceSector); if(dst!=null && dst.ownerId==p.id) dst.add(t); parts.add(p.name + ": відновив " + Rules.name(t));
            } else parts.add(p.name + ": пас");
        }
        say("Будівництво: " + join(parts) + ".", "build");
        List<Player> cand = new ArrayList<Player>();
        for (Player p : players) if (Rules.goalMet(p)) cand.add(p);
        winners = new ArrayList<Integer>();
        if (!cand.isEmpty()) pickWinners(cand);
        else if (round >= Rules.MAX_ROUNDS) { cap = true; pickWinners(players); }
        if (!winners.isEmpty()) {
            phase = "over"; StringBuilder names = new StringBuilder();
            for (Integer id : winners) { if (names.length() > 0) names.append(", "); names.append(byId(id).name); }
            say("Перемога: " + names + "!" + (cap ? " (ліміт раундів)" : ""), "win");
        } else startRound();
    }

    private void pickWinners(List<Player> cand) {
        int bt = -1, be = -1;
        for (Player p : cand) if (p.total() > bt || (p.total() == bt && p.E > be)) { bt = p.total(); be = p.E; }
        for (Player p : cand) if (p.total() == bt && p.E == be) winners.add(p.id);
    }

    private void advance() {
        int guard = 0;
        while (guard++ < 10000) {
            if ("event".equals(phase)) {
                Player a = players.get(active);
                if (round <= Rules.NO_CARD_ROUNDS) { endEvent(a, null, null, null); continue; }
                if ("ai".equals(a.kind)) { Object[] r = Bots.aiEvent(players, a); if (r == null) endEvent(a, null, null, null); else endEvent(a, (String) r[0], (Player) r[1], null); continue; }
                if ("neighbor".equals(a.kind)) { Object[] r = Bots.nbEvent(players, a, rng); if (r == null) endEvent(a, null, null, null); else endEvent(a, (String) r[0], (Player) r[1], null); continue; }
                return;
            }
            if ("build".equals(phase)) {
                autoBuilds();
                boolean all = true; for (Player p : players) if (!p.submitted) all = false;
                if (all) { resolveBuild(); continue; }
                return;
            }
            return;
        }
    }

    // ---------------------------------------------------------------- дії гравців
    public void act(Player p, Map<String, Object> d) {
        String type = Json.strVal(d.get("type"));
        synchronized (lock) {
            p.lastSeen = System.currentTimeMillis();
            if (type == null) throw new GameError("Невідома дія");
            Player host = host();
            switch (type) {
                case "ping": return;
                case "add_bot": {
                    if (p != host) throw new GameError("Лише господар кімнати");
                    Integer thr = Json.intVal(d.get("thr"));
                    addBot("neighbor".equals(d.get("kind")) ? "neighbor" : "ai", thr == null ? 5 : Math.max(1, Math.min(6, thr)));
                    return;
                }
                case "remove": {
                    Integer id = Json.intVal(d.get("id")); Player tgt = byId(id);
                    if (p != host && tgt != p) throw new GameError("Лише господар кімнати");
                    if (tgt == p && !"lobby".equals(phase)) throw new GameError("Не можна видалити себе під час гри");
                    remove(id); return;
                }
                case "start": if (p != host) throw new GameError("Лише господар кімнати"); start(true); return;
                case "new_game": if (p != host) throw new GameError("Лише господар кімнати"); newGame(); return;
                case "play": actPlay(p, d); advance(); bump(); return;
                case "skip": actSkip(p, d); advance(); bump(); return;
                case "build": case "rebuild": case "none": actBuild(p, type, d); advance(); bump(); return;
                default: throw new GameError("Невідома дія");
            }
        }
    }

    private void needEvent(Player p) {
        if (!"event".equals(phase)) throw new GameError("Зараз не фаза події");
        if (players.get(active) != p) throw new GameError("Подію грає інший гравець");
    }

    private void actPlay(Player p, Map<String, Object> d) {
        needEvent(p);
        Integer i = Json.intVal(d.get("card")); Player q = byId(Json.intVal(d.get("target")));
        if (i == null || i < 0 || i >= p.hand.size()) throw new GameError("Оберіть карту");
        String c = p.hand.get(i);
        if (q == null || q == p) throw new GameError("Оберіть суперника");
        if (p.E < Rules.cardCost(c)) throw new GameError("Не вистачає енергії");
        Integer sectorId = Json.intVal(d.get("sector")); endEvent(p, c, q, sectorId);
    }

    private void actSkip(Player p, Map<String, Object> d) {
        needEvent(p);
        if (p.hand.size() > Rules.HAND) {
            Integer idx = Json.intVal(d.get("discard"));
            if (idx == null || idx < 0 || idx >= p.hand.size()) throw new GameError("Оберіть карту, яку скинути (у руці не більше 2)");
            discardExtra(p, idx);
        }
        endEvent(p, null, null, null);
    }

    private void actBuild(Player p, String type, Map<String, Object> d) {
        if (!"build".equals(phase)) throw new GameError("Зараз не фаза будівництва");
        if (p.submitted) throw new GameError("Ви вже зробили вибір");
        if (type.equals("none")) p.choiceKind = null;
        else {
            String b = Json.strVal(d.get("b"));
            if (b == null || b.length() != 1 || "IMD".indexOf(b.charAt(0)) < 0) throw new GameError("Невідомий тип будівлі");
            char t = b.charAt(0);
            Integer sid=Json.intVal(d.get("sector")); SectorState dst=sector(sid==null?0:sid); if(dst==null || dst.ownerId!=p.id) throw new GameError("Оберіть власний сектор міста для будівництва");
            if (type.equals("build") && !Rules.canBuild(p, t)) throw new GameError("Не можна побудувати: бракує енергії або слотів");
            if (type.equals("rebuild") && !Rules.canRebuild(p, t)) throw new GameError("Не можна відновити: немає руїни або енергії");
            p.choiceKind = type; p.choiceType = t; p.choiceSector=dst.id;
        }
        p.submitted = true;
    }

    // ---------------------------------------------------------------- вигляд для клієнта
    private Map<String, Object> pub(Player p) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("id", p.id); m.put("name", p.name); m.put("kind", p.kind); m.put("E", p.E); m.put("I", p.I); m.put("M", p.M); m.put("D", p.D);
        m.put("rI", p.rI); m.put("rM", p.rM); m.put("rD", p.rD); m.put("hand", p.hand.size());
        m.put("submitted", "build".equals(phase) && p.submitted); m.put("online", p.online());
        m.put("thr", "neighbor".equals(p.kind) ? (Object) p.thr : null);
        return m;
    }

    public Map<String, Object> view(String tok) {
        synchronized (lock) {
            Player me = byToken(tok);
            if (me != null) me.lastSeen = System.currentTimeMillis();
            Player host = host();
            Map<String, Object> v = new LinkedHashMap<String, Object>();
            v.put("v", version); v.put("phase", phase); v.put("round", round); v.put("games", games);
            Map<String, Object> rules = new LinkedHashMap<String, Object>();
            Map<String, Object> cost = new LinkedHashMap<String, Object>(), cc = new LinkedHashMap<String, Object>();
            for (char t : Rules.TYPES) cost.put(String.valueOf(t), Rules.cost(t));
            for (String c : Rules.CARDS) cc.put(c, Rules.cardCost(c));
            rules.put("COST", cost); rules.put("CARD_COST", cc); rules.put("INCOME", Rules.INCOME); rules.put("LIMIT", Rules.LIMIT);
            rules.put("E_MAX", Rules.E_MAX); rules.put("NO_CARD_ROUNDS", Rules.NO_CARD_ROUNDS); rules.put("HAND", Rules.HAND);
            v.put("rules", rules);
            List<Object> pl = new ArrayList<Object>(); for (Player p : players) pl.add(pub(p)); v.put("players", pl); List<Object> secJson=new ArrayList<Object>(); for(SectorState sec:sectors) secJson.add(sec.json()); v.put("sectors",secJson);
            v.put("host", host == null ? null : (Object) host.id);
            boolean ingame = !players.isEmpty() && ("event".equals(phase) || "build".equals(phase) || "over".equals(phase));
            v.put("active", ingame ? (Object) players.get(active).id : null);
            v.put("deck", deck.size()); v.put("discard", discard.size());
            v.put("log", new ArrayList<Object>(log.subList(Math.max(0, log.size() - 80), log.size())));
            v.put("winners", new ArrayList<Integer>(winners)); v.put("cap", cap);
            v.put("can_join", "lobby".equals(phase) && players.size() < Rules.MAX_PLAYERS);
            List<Integer> seats = new ArrayList<Integer>();
            if (!"lobby".equals(phase)) for (Player p : players) if (!"human".equals(p.kind) || !p.online()) seats.add(p.id);
            v.put("seats", seats);
            if (me != null) {
                v.put("you", me.id); v.put("hand", new ArrayList<String>(me.hand));
                Map<String, Object> can = new LinkedHashMap<String, Object>(), b = new LinkedHashMap<String, Object>(), r = new LinkedHashMap<String, Object>();
                for (char t : Rules.TYPES) { b.put(String.valueOf(t), Rules.canBuild(me, t)); r.put(String.valueOf(t), Rules.canRebuild(me, t)); }
                List<Object> pp = new ArrayList<Object>(); for (String c : me.hand) pp.add(me.E >= Rules.cardCost(c));
                can.put("build", b); can.put("rebuild", r); can.put("play", pp); v.put("can", can);
                v.put("mine_submitted", me.submitted);
                boolean open = "event".equals(phase) && players.get(active) == me;
                v.put("event_open", open); v.put("need_discard", open && me.hand.size() > Rules.HAND);
            }
            return v;
        }
    }

    public Map<String, Object> waitView(String tok, long since, long timeoutMs) {
        synchronized (lock) {
            long end = System.currentTimeMillis() + timeoutMs;
            while (version == since) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) break;
                try { lock.wait(Math.min(left, 5000)); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                Player me = byToken(tok); if (me != null) me.lastSeen = System.currentTimeMillis();
            }
            return view(tok);
        }
    }
}
