package ua.urbanist.tools;

import ua.urbanist.core.*;
import java.util.*;

/** Самотести Java-ядра: ефекти карт, права, помилки, 2500 партій ботів з інваріантами. */
public final class SelfTest {
    static int checks = 0; static List<String> fails = new ArrayList<String>();
    static void chk(String n, boolean c) { checks++; if (!c) fails.add(n); }
    static void chk(String n, boolean c, Object info) { checks++; if (!c) fails.add(n + " " + info); }
    static boolean err(Runnable r) { try { r.run(); return false; } catch (GameError e) { return true; } }
    static Map<String, Object> m(Object... kv) { Map<String, Object> x = new LinkedHashMap<String, Object>(); for (int i = 0; i < kv.length; i += 2) x.put((String) kv[i], kv[i + 1]); return x; }
    static Player P() { return new Player(1, "x", "ai", null, "I4DM"); }

    public static void main(String[] args) {
        effects(); lobbyAndRules(); botGames();
        System.out.println("ПЕРЕВІРОК: " + checks + ", ПОМИЛОК: " + fails.size());
        for (int i = 0; i < Math.min(20, fails.size()); i++) System.out.println("FAIL " + fails.get(i));
        System.exit(fails.isEmpty() ? 0 : 1);
    }

    static void effects() {
        Player q = P(); q.D = 2; q.I = 3; Game.resolve("spy", q); chk("spy D", q.D == 1 && q.rD == 1 && q.I == 3);
        q = P(); q.I = 3; Game.resolve("spy", q); chk("spy I-2", q.I == 1 && q.rI == 2);
        q = P(); q.I = 3; q.E = 0; Game.resolve("spy", q); chk("floor", q.I == 1);
        q = P(); q.I = 1; q.E = 0; Game.resolve("spy", q); chk("floor stays", q.I == 1);
        q = P(); q.M = 2; q.D = 1; Game.resolve("adm", q); chk("adm", q.M == 1 && q.D == 0);
        q = P(); q.I = 3; Game.resolve("adm", q); chk("adm no M", q.I == 1);
        q = P(); q.I = 0; Game.resolve("dis", q); chk("dis noop", q.I == 0 && q.rI == 0);
        q = P(); q.E = 7; List<String> e = Game.resolve("cri", q); chk("cri", q.E == 0 && !e.isEmpty());
        q = P(); q.D = 1; q.I = 2; Game.resolve("sab", q); chk("sab D first", q.D == 0 && q.I == 2);
        q = P(); q.I = 2; Game.resolve("sab", q); chk("sab I", q.I == 1);
    }

    static void lobbyAndRules() {
        final Game g = new Game(1); final Player a = g.join("Аня", null), b = g.join("Богдан", null);
        chk("dup name", err(new Runnable() { public void run() { g.join("аня", null); } }));
        chk("empty name", err(new Runnable() { public void run() { g.join("  ", null); } }));
        chk("non-host start", err(new Runnable() { public void run() { g.act(b, m("type", "start")); } }));
        g.act(a, m("type", "start")); chk("started", g.phase().equals("build") && g.round() == 1);
        chk("no join mid-game", err(new Runnable() { public void run() { g.join("Влад", null); } }));
        chk("start twice", err(new Runnable() { public void run() { g.act(a, m("type", "start")); } }));
        chk("start values", a.I == 0 && a.M == 0 && a.D == 0 && a.E == 1 && b.I == 0 && b.M == 0 && b.D == 0 && b.E == 1, a.E + "/" + a.I);
        chk("invalid build", err(new Runnable() { public void run() { g.act(a, m("type", "build", "b", "M")); } }));
        chk("invalid type", err(new Runnable() { public void run() { g.act(a, m("type", "build", "b", "X")); } }));
        chk("no ruin", err(new Runnable() { public void run() { g.act(a, m("type", "rebuild", "b", "I", "sector", 1L)); } }));
        chk("foreign sector denied", err(new Runnable() { public void run() { g.act(b, m("type", "build", "b", "I", "sector", 1L)); } }));
        g.act(a, m("type", "build", "b", "I", "sector", 1L)); chk("sector state present", ((List<?>)g.view(a.token).get("sectors")).size()==12); chk("waits", g.phase().equals("build") && a.submitted);
        chk("double submit", err(new Runnable() { public void run() { g.act(a, m("type", "none")); } }));
        chk("play in build", err(new Runnable() { public void run() { g.act(a, m("type", "play", "card", 0L, "target", (long) b.id)); } }));
        g.act(b, m("type", "none")); chk("round 2", g.round() == 2 && a.I == 1); Map<?,?> sector1=(Map<?,?>)((List<?>)g.view(a.token).get("sectors")).get(0); chk("build placed in sector", ((Number)sector1.get("I")).intValue()==1 && ((Number)sector1.get("owner")).intValue()==a.id);
        g.act(a, m("type", "none")); g.act(b, m("type", "none")); chk("round 3 event", g.round() == 3 && g.phase().equals("event"));
        final Player act = g.playersUnsafe().get(g.round() - 1 >= 0 ? ((int) activeIdx(g)) : 0); final Player oth = act == a ? b : a;
        chk("non-active play", err(new Runnable() { public void run() { g.act(oth, m("type", "play", "card", 0L, "target", (long) act.id)); } }));
        chk("self target", err(new Runnable() { public void run() { g.act(act, m("type", "play", "card", 0L, "target", (long) act.id)); } }));
        act.E = 8; act.hand.clear(); act.hand.addAll(Arrays.asList("spy", "sab", "adm"));
        chk("need discard", err(new Runnable() { public void run() { g.act(act, m("type", "skip")); } }));
        oth.D = 1;
        g.act(act, m("type", "play", "card", 0L, "target", (long) oth.id));
        chk("sector attack energy spent and empty sector does not damage player-wide buildings", act.E == 6 && oth.D == 1 && oth.rD == 0 && act.hand.size() == 2, act.E + "/" + act.hand);
        chk("to build", g.phase().equals("build"));
        Map<String, Object> v = g.view(a.token);
        chk("own hand", v.get("hand") instanceof List);
        for (Object po : (List<?>) v.get("players")) chk("count only", ((Map<?, ?>) po).get("hand") instanceof Integer);
        chk("anon no hand", !g.view(null).containsKey("hand") && !g.view(null).containsKey("you"));
        String js = Json.stringify(v); chk("json roundtrip", Json.parseObject(js).get("phase").equals(g.phase()));
        g.act(a, m("type", "remove", "id", (long) b.id)); chk("kick->bot", b.kind.equals("ai"));
        Player c = g.join("Влад", b.id); chk("claim", c.kind.equals("human") && c.name.equals("Влад"));
        chk("claim online rejected", err(new Runnable() { public void run() { g.join("Z", a.id); } }));
        // перемога/тай-брейк
        Game g3 = new Game(3); Player x = g3.join("X", null), y = g3.join("Y", null); g3.act(x, m("type", "start"));
        for (Player p : g3.playersUnsafe()) { p.I = p.M = p.D = 3; p.E = 5; } x.I = 4;
        g3.act(x, m("type", "none")); g3.act(y, m("type", "none"));
        chk("tiebreak by buildings", g3.phase().equals("over") && g3.winners().size() == 1 && g3.winners().get(0) == x.id);
        Json.parse("{\"a\":[1,2.5,true,null,\"\\u0416\\n\"],\"b\":{}}");
        chk("json unicode", "Ж\n".equals(((List<?>) ((Map<?, ?>) Json.parse("{\"a\":[\"\\u0416\\n\"]}")).get("a")).get(0)));
    }

    static int activeIdx(Game g) {
        Object id = g.view(null).get("active"); for (int i = 0; i < g.playersUnsafe().size(); i++) if (g.playersUnsafe().get(i).id == (Integer) id) return i; return 0;
    }

    static void botGames() {
        final int TOTAL = 32; Random rnd = new Random(1); StringBuilder sb = new StringBuilder();
        for (int n = 2; n <= 6; n++) {
            long rounds = 0; int ended = 0;
            for (int i = 0; i < 500; i++) {
                final Game g = new Game(i * 7L + n);
                for (int k = 0; k < n; k++) g.addBot((n == 2 && rnd.nextBoolean()) ? "neighbor" : "ai", 3 + rnd.nextInt(3));
                g.setRoundHook(new Runnable() { public void run() { inv(g, TOTAL); } });
                g.start(false);
                chk("game ended n=" + n, g.phase().equals("over") && !g.winners().isEmpty(), g.phase() + " r" + g.round());
                inv(g, TOTAL);
                if (g.phase().equals("over")) { ended++; rounds += g.round(); }
                for (int w : g.winners()) chk("winner meets goal", Rules.goalMet(g.playerById(w)) || g.round() >= Rules.MAX_ROUNDS);
            }
            sb.append(n).append("гр: ").append(String.format("%.1f", rounds / (double) Math.max(1, ended))).append(" раундів (").append(ended).append("/500); ");
        }
        System.out.println(sb);
    }

    static void inv(Game g, int total) {
        int held = 0;
        for (Player p : g.playersUnsafe()) {
            held += p.hand.size();
            chk("E range", p.E >= 0 && p.E <= Rules.E_MAX, p.E);
            for (char t : Rules.TYPES) { chk("cnt>=0", p.cnt(t) >= 0 && p.ruin(t) >= 0); chk("slots<=6", p.slots(t) <= Rules.LIMIT, p.slots(t)); }
            chk("hand<=3", p.hand.size() <= Rules.HAND + 1);
        }
        chk("cards conserved", g.deckUnsafe().size() + g.discardUnsafe().size() + held == total);
    }
}
