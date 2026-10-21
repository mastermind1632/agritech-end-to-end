package tut.ac.za.AgriFinanceAPIs.statements;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Categorises bank transactions in three layers, exactly as the design guide describes:
 *  1. the farmer's own corrections (exact merchant memory) - they always win,
 *  2. transparent keyword rules for South African merchants and farm suppliers,
 *  3. a Naive Bayes classifier trained on the farmer's confirmed transactions.
 * Anything still uncertain is marked for review (and may be sent to the optional AI fallback).
 *
 * Pure Java: no Spring, no database, so the behaviour is fully unit tested.
 */
public final class TransactionCategorizer {

    public static final double REVIEW_THRESHOLD = 0.70;

    public record Result(ExpenseCategory category, double confidence, String source) {
        public boolean needsReview() { return confidence < REVIEW_THRESHOLD; }
    }

    private record Rule(Pattern pattern, ExpenseCategory category, double confidence) { }

    private static final List<Rule> DEBIT_RULES = new ArrayList<>();
    private static final List<Rule> CREDIT_RULES = new ArrayList<>();

    private static void debit(String regex, ExpenseCategory c, double conf) {
        DEBIT_RULES.add(new Rule(Pattern.compile("(?i)\\b(?:" + regex + ")"), c, conf));
    }

    private static void credit(String regex, ExpenseCategory c, double conf) {
        CREDIT_RULES.add(new Rule(Pattern.compile("(?i)\\b(?:" + regex + ")"), c, conf));
    }

    static {
        // Order matters: the first matching rule wins, so specific merchants come before generic words.
        debit("transfer to own|own account|inter ?account|trf to sav|to savings|interacc", ExpenseCategory.TRANSFER, 0.85);
        debit("engen|shell|sasol|bp |bp$|caltex|total ?energies|totalenergies|astron|puma energy|fuel|diesel|petrol|garage|filling st", ExpenseCategory.FUEL, 0.93);
        debit("omnia|kynoch|yara|sasol nitro|fertili[sz]er|kunsmis|ammonium|urea|lan |superphosphate|compost|manure", ExpenseCategory.FERTILISER, 0.93);
        debit("pannar|pioneer seed|starke ayres|sakata|hygrotech|klein karoo saad|seed|saad|seedling|nursery|kwekery", ExpenseCategory.SEEDS, 0.92);
        debit("syngenta|bayer crop|basf|villa crop|arysta|upl |adama|agrochem|pesticide|herbicide|fungicide|insecticide|spray|chemicals", ExpenseCategory.AGROCHEMICALS, 0.92);
        debit("wages|salary|salaries|payroll|loon|lone|casual labour|labour|uif|coida", ExpenseCategory.LABOUR, 0.9);
        debit("john deere|new holland|massey|landini|tractor|implement|agrico|bobcat|equipment|irrigation equip|centre pivot", ExpenseCategory.EQUIPMENT, 0.88);
        debit("repair|spares|spare parts|goldwagen|midas|autozone|service centre|maintenance|workshop|hardware|builders|buco|cashbuild", ExpenseCategory.REPAIRS, 0.85);
        debit("eskom|electricity|prepaid elec|municipal|municipality|water ?board|water account|rates and taxes|munic", ExpenseCategory.UTILITIES, 0.9);
        debit("transport|courier|freight|logistics|toll|sanral|e-?toll|haulage|truck hire|bakkie hire|imperial|bidvest|uber", ExpenseCategory.TRANSPORT, 0.85);
        debit("rent|lease|huur", ExpenseCategory.RENT, 0.82);
        debit("land ?bank|loan|instalment|installment|bond|vehicle finance|wesbank|mfc|absa vaf|repayment|lening", ExpenseCategory.LOAN, 0.88);
        debit("insurance|santam|hollard|old mutual|outsurance|mutual ?& ?federal|discovery insure|assurance|versekering|premium", ExpenseCategory.INSURANCE, 0.88);
        debit("service fee|monthly fee|account fee|admin fee|bank charge|cash deposit fee|sms notif|notific fee|card fee|transaction fee|fee:|fees|diensgeld|ledgerfee", ExpenseCategory.BANK_FEES, 0.9);
        debit("afgri|senwes|ovk|vkb|obaro|twk|bkb|nwk|kaap agri|agrimark|suidwes|co-?op|kooperasie|farm ?supplies|farmers ?world", ExpenseCategory.FARM_SUPPLIES, 0.75);
        debit("checkers|pick ?n ?pay|pnp|woolworths|spar|shoprite|boxer|netflix|dstv|showmax|mr price|pep stores|ackermans|kfc|nando|mcdonald|steers|debonairs|takealot|clothing|restaurant|liquor|tops at spar", ExpenseCategory.PERSONAL, 0.85);
        debit("vodacom|mtn|cell ?c|telkom|airtime|data bundle", ExpenseCategory.OTHER_EXPENSE, 0.72);

        credit("transfer from own|own account|inter ?account|from savings|interacc", ExpenseCategory.TRANSFER, 0.85);
        credit("farm market sale|joburg market|jhb market|johannesburg market|tshwane market|durban market|cape town market|freshmark|fresh produce|produce|vegetable|fruit|harvest|livestock sale|auction|veiling|sale of|crop sale|agritech sale", ExpenseCategory.PRODUCE_SALES, 0.9);
        credit("grant|subsidy|casp|ilima|letsema|dalrrd|department of agric|nda |sedfa|nef |land reform|toelaag", ExpenseCategory.GRANTS, 0.9);
        credit("interest|refund|reversal|cashback", ExpenseCategory.OTHER_INCOME, 0.8);
    }

    private static final Pattern NOISE = Pattern.compile(
            "(?i)\\b(pos|purchase|card|debit order|d/o|do|pmt|payment to|payment from|eft|internet|banking|ib|cashsend|"
                    + "magtape|ref|reference|acb|immediate|instant|trf|transfer|dr|cr|ppd|purch|pur|sett|nr|no)\\b");

    private final Map<String, ExpenseCategory> learned;
    private final NaiveBayes model;

    /**
     * @param learned the farmer's corrections: merchant key (see {@link #merchantKey}) to category
     * @param history the farmer's confirmed transactions (description, category) used to train the classifier
     */
    public TransactionCategorizer(Map<String, ExpenseCategory> learned, List<Map.Entry<String, ExpenseCategory>> history) {
        this.learned = learned == null ? Map.of() : learned;
        this.model = history == null || history.size() < 15 ? null : NaiveBayes.train(history);
    }

    public Result categorise(String description, boolean debit) {
        String desc = description == null ? "" : description;
        String key = merchantKey(desc);
        ExpenseCategory memory = learned.get(key);
        if (memory != null && directionFits(memory, debit)) return new Result(memory, 0.97, "LEARNED");

        for (Rule r : debit ? DEBIT_RULES : CREDIT_RULES) {
            if (r.pattern().matcher(desc).find()) return new Result(r.category(), r.confidence(), "RULE");
        }

        if (model != null) {
            NaiveBayes.Prediction p = model.predict(desc, debit);
            if (p != null && p.probability() >= 0.55) return new Result(p.category(), Math.min(0.9, p.probability()), "ML");
        }
        return debit ? new Result(ExpenseCategory.OTHER_EXPENSE, 0.3, "DEFAULT")
                : new Result(ExpenseCategory.OTHER_INCOME, 0.4, "DEFAULT");
    }

    public static boolean directionFits(ExpenseCategory c, boolean debit) {
        if (c == ExpenseCategory.TRANSFER) return true;
        return debit != c.isIncome();
    }

    /** A stable merchant key: upper case, digits and banking noise removed, first three meaningful words. */
    public static String merchantKey(String description) {
        if (description == null) return "";
        String s = description.replaceAll("[0-9*#/\\\\:._-]+", " ");
        s = NOISE.matcher(s).replaceAll(" ");
        s = s.replaceAll("[^A-Za-z& ]", " ").replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        String[] words = s.split(" ");
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String w : words) {
            if (w.length() < 2) continue;
            if (n > 0) b.append(' ');
            b.append(w);
            if (++n == 3) break;
        }
        return b.toString();
    }

    /** Multinomial Naive Bayes over description words, with Laplace smoothing. */
    static final class NaiveBayes {
        record Prediction(ExpenseCategory category, double probability) { }

        private final Map<ExpenseCategory, Map<String, Integer>> wordCounts = new EnumMap<>(ExpenseCategory.class);
        private final Map<ExpenseCategory, Integer> totalWords = new EnumMap<>(ExpenseCategory.class);
        private final Map<ExpenseCategory, Integer> docCounts = new EnumMap<>(ExpenseCategory.class);
        private final Set<String> vocabulary = new HashSet<>();
        private int docs;

        static NaiveBayes train(List<Map.Entry<String, ExpenseCategory>> examples) {
            NaiveBayes nb = new NaiveBayes();
            for (Map.Entry<String, ExpenseCategory> e : examples) {
                if (e.getValue() == null) continue;
                nb.docs++;
                nb.docCounts.merge(e.getValue(), 1, Integer::sum);
                for (String w : words(e.getKey())) {
                    nb.vocabulary.add(w);
                    nb.wordCounts.computeIfAbsent(e.getValue(), k -> new HashMap<>()).merge(w, 1, Integer::sum);
                    nb.totalWords.merge(e.getValue(), 1, Integer::sum);
                }
            }
            return nb;
        }

        Prediction predict(String description, boolean debit) {
            List<String> ws = words(description);
            if (ws.isEmpty() || docs == 0) return null;
            Map<ExpenseCategory, Double> logs = new EnumMap<>(ExpenseCategory.class);
            int v = Math.max(1, vocabulary.size());
            for (Map.Entry<ExpenseCategory, Integer> dc : docCounts.entrySet()) {
                if (!directionFits(dc.getKey(), debit)) continue;
                double lp = Math.log(dc.getValue() / (double) docs);
                Map<String, Integer> counts = wordCounts.getOrDefault(dc.getKey(), Map.of());
                int total = totalWords.getOrDefault(dc.getKey(), 0);
                boolean anyKnown = false;
                for (String w : ws) {
                    if (vocabulary.contains(w)) anyKnown = true;
                    lp += Math.log((counts.getOrDefault(w, 0) + 1.0) / (total + v));
                }
                if (anyKnown) logs.put(dc.getKey(), lp);
            }
            if (logs.isEmpty()) return null;
            double max = Collections.max(logs.values());
            double sum = 0;
            for (double l : logs.values()) sum += Math.exp(l - max);
            ExpenseCategory best = null;
            double bestP = -1;
            for (Map.Entry<ExpenseCategory, Double> e : logs.entrySet()) {
                double p = Math.exp(e.getValue() - max) / sum;
                if (p > bestP) { bestP = p; best = e.getKey(); }
            }
            return new Prediction(best, bestP);
        }

        static List<String> words(String s) {
            List<String> out = new ArrayList<>();
            for (String w : merchantKeyWords(s)) if (w.length() >= 3) out.add(w);
            return out;
        }

        private static String[] merchantKeyWords(String s) {
            if (s == null) return new String[0];
            String cleaned = NOISE.matcher(s.replaceAll("[0-9*#/\\\\:._-]+", " ")).replaceAll(" ");
            return cleaned.replaceAll("[^A-Za-z ]", " ").trim().toLowerCase(Locale.ROOT).split("\\s+");
        }
    }
}
