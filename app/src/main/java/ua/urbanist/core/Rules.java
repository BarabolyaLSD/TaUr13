package ua.urbanist.core;

public final class Rules {
    public static final int E_MAX = 8, START_E = 1, START_I = 0, LIMIT = 6, HAND = 2, NO_CARD_ROUNDS = 2, MAX_ROUNDS = 40, MAX_PLAYERS = 6;
    public static final int[] INCOME = {0, 1, 2, 3, 4, 5, 6};
    public static final String[] CARDS = {"spy", "adm", "dis", "cri", "sab"};
    public static final int[] DECK = {5, 6, 7, 6, 8};
    public static final char[] TYPES = {'I', 'M', 'D'};
    public static int cost(char t) { return t == 'I' ? 1 : t == 'M' ? 5 : 4; }
    public static int rebuildCost(char t) { return (cost(t) + 1) / 2; }
    public static int cardCost(String c) {
        switch (c) { case "spy": return 2; case "adm": return 6; case "dis": return 3; case "cri": return 4; default: return 3; }
    }
    public static String name(char t) { return t == 'I' ? "промисловість" : t == 'M' ? "військо" : "оборона"; }
    public static String cardName(String c) {
        switch (c) { case "spy": return "Шпигун"; case "adm": return "Генерал"; case "dis": return "Стихійне лихо"; case "cri": return "Економічна криза"; default: return "Диверсія"; }
    }
    public static boolean goalMet(Player q) { return q.I >= 3 && q.M >= 3 && q.D >= 3; }
    public static boolean canBuild(Player p, char t) { return p.E >= cost(t) && p.slots(t) < LIMIT; }
    public static boolean canRebuild(Player p, char t) { return p.ruin(t) > 0 && p.E >= rebuildCost(t); }
    public static boolean hasAction(Player p) { for (char t : TYPES) if (canBuild(p, t) || canRebuild(p, t)) return true; return false; }
}
