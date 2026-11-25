package ua.urbanist.core;

import java.util.*;

public final class Player {
    public final int id; public String name, kind, token, policy; public int thr = 5;
    public int E, I, M, D, rI, rM, rD;
    public final List<String> hand = new ArrayList<String>();
    public String choiceKind; public char choiceType; public int choiceSector; public boolean submitted; public int plan = 1;
    public volatile long lastSeen = System.currentTimeMillis();

    public Player(int id, String name, String kind, String token, String policy) {
        this.id = id; this.name = name; this.kind = kind; this.token = token; this.policy = policy; reset();
    }
    public void reset() {
        E = Rules.START_E; I = Rules.START_I; M = D = 0; rI = rM = rD = 0;
        hand.clear(); choiceKind = null; choiceSector = 0; submitted = false; plan = 1;
    }
    public int cnt(char t) { return t == 'I' ? I : t == 'M' ? M : D; }
    public void setCnt(char t, int v) { if (t == 'I') I = v; else if (t == 'M') M = v; else D = v; }
    public int ruin(char t) { return t == 'I' ? rI : t == 'M' ? rM : rD; }
    public void setRuin(char t, int v) { if (t == 'I') rI = v; else if (t == 'M') rM = v; else rD = v; }
    public int slots(char t) { return cnt(t) + ruin(t); }
    public int total() { return I + M + D; }
    public boolean online() { return !"human".equals(kind) || System.currentTimeMillis() - lastSeen < 25000; }
    public Player copy() {
        Player p = new Player(id, name, kind, token, policy);
        p.E = E; p.I = I; p.M = M; p.D = D; p.rI = rI; p.rM = rM; p.rD = rD; p.thr = thr; p.plan = plan; return p;
    }
}
