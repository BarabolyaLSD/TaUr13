package ua.urbanist.core;

import java.util.*;

/** Стратегії ботів (порт перевіреного Python-симулятора) та Автоматичний Сусід. */
final class Bots {
    private Bots() {}

    static double worth(Player q) { return 1.5 * q.I + 3.0 * q.M + 2.0 * q.D + 0.8 * q.E; }
    static int dist(Player q) { return Math.max(0, 3 - q.I) + Math.max(0, 3 - q.M) + Math.max(0, 3 - q.D); }

    static double gain(String c, Player q) {
        Player q2 = q.copy(); Game.resolve(c, q2);
        double d = worth(q) - worth(q2);
        if (Rules.goalMet(q) && !Rules.goalMet(q2)) d += 8; else if (dist(q) <= 1) d += 1.5;
        return d;
    }

    static char planFor(Player p) {
        int tgt = p.policy.charAt(1) - '0';
        if (p.I < tgt) return 'I';
        for (int k = 2; k < p.policy.length(); k++) { char t = p.policy.charAt(k); if (p.cnt(t) < 3) return t; }
        return 'I';
    }

    /** Повертає {card, targetPlayer} або null. */
    static Object[] aiEvent(List<Player> players, Player a) {
        char want = planFor(a);
        boolean close = false;
        for (Player q : players) if (q != a && (dist(q) <= 2 || Rules.goalMet(q))) close = true;
        double bestScore = -1e9; Object[] best = null;
        for (String c : new LinkedHashSet<String>(a.hand)) {
            int price = Rules.cardCost(c);
            if (a.E < price) continue;
            for (Player q : players) {
                if (q == a) continue;
                double gn = gain(c, q);
                boolean ok = (a.E - price >= Rules.cost(want)) || close || a.E + Rules.INCOME[a.I] > Rules.E_MAX;
                if (gn >= price && ok && gn - price > bestScore) { bestScore = gn - price; best = new Object[]{c, q}; }
            }
        }
        return best;
    }

    /** Повертає {"build"|"rebuild", Character} або null. */
    static Object[] aiBuild(Player a) {
        char want = planFor(a);
        char[] order = {want, 'I', 'D', 'M'};
        for (char t : order) {
            if (a.ruin(t) > 0 && a.E >= Rules.rebuildCost(t) && (t == want || a.cnt(t) < 3)) return new Object[]{"rebuild", t};
        }
        if (Rules.canBuild(a, want)) return new Object[]{"build", want};
        if (a.E + Rules.INCOME[a.I] > Rules.E_MAX) for (char t : new char[]{'I', 'D', 'M'}) if (Rules.canBuild(a, t)) return new Object[]{"build", t};
        return null;
    }

    static Object[] nbEvent(List<Player> players, Player a, Random rng) {
        if (a.plan >= a.thr) {
            int bestCost = -1; String bc = null;
            for (String c : a.hand) { int pr = Rules.cardCost(c); if (a.E >= pr && pr > bestCost) { bestCost = pr; bc = c; } }
            if (bc != null) {
                List<Player> others = new ArrayList<Player>();
                for (Player q : players) if (q != a) others.add(q);
                return new Object[]{bc, others.get(rng.nextInt(others.size()))};
            }
        }
        return null;
    }

    static Object[] nbBuild(Player a) {
        int[] g = {2, 3, 3}; char[] ts = Rules.TYPES;
        for (int k = 0; k < 3; k++) if (a.ruin(ts[k]) > 0 && a.cnt(ts[k]) < g[k] && a.E >= Rules.rebuildCost(ts[k])) return new Object[]{"rebuild", ts[k]};
        if (a.I < 2 && Rules.canBuild(a, 'I')) return new Object[]{"build", 'I'};
        String[] orders = {"", "IMD", "IDM", "MID", "DIM", "IMD", "IDM"};
        String o = orders[a.plan];
        for (int k = 0; k < 3; k++) if (Rules.canBuild(a, o.charAt(k))) return new Object[]{"build", o.charAt(k)};
        return null;
    }
}
