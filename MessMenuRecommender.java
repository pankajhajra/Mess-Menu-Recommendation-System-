
import java.time.DayOfWeek;
import java.util.*;
import java.util.stream.Collectors;
 
/**
 * Mess Menu Recommendation System
 * --------------------------------
 * Recommends meals from the day's mess menu based on each student's preferences.
 *
 * Approach (hybrid):
 *  1. Hard filters  : diet type, allergies, maximum spice tolerance.
 *  2. Content score : cuisine match, favourite tags, calories, spice closeness.
 *  3. Community     : average rating from all students.
 *  4. Collaborative : ratings from students with similar tastes weigh more.
 *  5. Personal      : the student's own past ratings boost or lower a meal.
 *
 * Compile : javac MessMenuRecommender.java
 * Run     : java MessMenuRecommender
 */
public class MessMenuRecommender{
 
    // ======================= Domain model =======================
 
    enum Diet { VEG, NON_VEG, VEGAN }
 
    enum MealTime { BREAKFAST, LUNCH, DINNER }
 
    static class Meal {
        final int id;
        final String name;
        final MealTime time;
        final Diet diet;
        final String cuisine;
        final int spice;          // 1 (mild) .. 5 (very hot)
        final int calories;
        final Set<String> tags;
        final Set<String> allergens;
        double ratingSum = 0;
        int ratingCount = 0;
 
        Meal(int id, String name, MealTime time, Diet diet, String cuisine,
             int spice, int calories, String tags, String allergens) {
            this.id = id;
            this.name = name;
            this.time = time;
            this.diet = diet;
            this.cuisine = cuisine.toLowerCase();
            this.spice = spice;
            this.calories = calories;
            this.tags = splitSet(tags);
            this.allergens = splitSet(allergens);
        }
 
        double avgRating() {
            return ratingCount == 0 ? 0 : ratingSum / ratingCount;
        }
 
        @Override
        public String toString() {
            String r = ratingCount == 0 ? "no ratings" : String.format("%.1f/5", avgRating());
            return String.format("%-24s [%s | %s | spice %d | %d kcal | %s]",
                    name, diet, cuisine, spice, calories, r);
        }
    }
 
    static class Student {
        final String name;
        Diet diet;
        int preferredSpice;
        int maxCaloriesPerMeal;
        Set<String> cuisines;
        Set<String> likedTags;
        Set<String> allergies;
        final Map<Integer, Integer> ratings = new HashMap<>(); // mealId -> 1..5
 
        Student(String name, Diet diet, int preferredSpice, int maxCaloriesPerMeal,
                String cuisines, String likedTags, String allergies) {
            this.name = name;
            this.diet = diet;
            this.preferredSpice = preferredSpice;
            this.maxCaloriesPerMeal = maxCaloriesPerMeal;
            this.cuisines = splitSet(cuisines);
            this.likedTags = splitSet(likedTags);
            this.allergies = splitSet(allergies);
        }
    }
 
    static class Scored {
        final Meal meal;
        final double score;
        final List<String> reasons;
 
        Scored(Meal meal, double score, List<String> reasons) {
            this.meal = meal;
            this.score = score;
            this.reasons = reasons;
        }
    }
 
    // ======================= Data =======================
 
    static final List<Meal> catalogue = new ArrayList<>();
    static final Map<String, Student> students = new LinkedHashMap<>();
    static final Map<DayOfWeek, Map<MealTime, List<Meal>>> weeklyMenu = new EnumMap<>(DayOfWeek.class);
 
    static Set<String> splitSet(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            String t = s.trim().toLowerCase();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
 
    static void seedCatalogue() {
        int id = 1;
        // Breakfast
        catalogue.add(new Meal(id++, "Aloo Paratha", MealTime.BREAKFAST, Diet.VEG, "north indian", 2, 320, "stuffed,filling,comfort", "gluten,dairy"));
        catalogue.add(new Meal(id++, "Idli Sambar", MealTime.BREAKFAST, Diet.VEGAN, "south indian", 2, 220, "light,steamed,healthy", "none"));
        catalogue.add(new Meal(id++, "Masala Dosa", MealTime.BREAKFAST, Diet.VEGAN, "south indian", 3, 300, "crispy,filling", "none"));
        catalogue.add(new Meal(id++, "Poha", MealTime.BREAKFAST, Diet.VEGAN, "indian", 1, 250, "light,healthy,comfort", "peanut"));
        catalogue.add(new Meal(id++, "Egg Bhurji & Toast", MealTime.BREAKFAST, Diet.NON_VEG, "indian", 3, 340, "protein,filling", "egg,gluten"));
        catalogue.add(new Meal(id++, "Veg Sandwich", MealTime.BREAKFAST, Diet.VEG, "continental", 1, 280, "light,quick", "gluten,dairy"));
        // Lunch
        catalogue.add(new Meal(id++, "Rajma Chawal", MealTime.LUNCH, Diet.VEGAN, "north indian", 3, 520, "comfort,protein,filling", "none"));
        catalogue.add(new Meal(id++, "Paneer Butter Masala", MealTime.LUNCH, Diet.VEG, "north indian", 3, 600, "rich,creamy,protein", "dairy,nuts"));
        catalogue.add(new Meal(id++, "Chicken Curry & Rice", MealTime.LUNCH, Diet.NON_VEG, "indian", 4, 650, "protein,spicy,filling", "none"));
        catalogue.add(new Meal(id++, "Dal Tadka & Roti", MealTime.LUNCH, Diet.VEGAN, "indian", 2, 450, "healthy,protein,comfort", "gluten"));
        catalogue.add(new Meal(id++, "Veg Biryani", MealTime.LUNCH, Diet.VEG, "hyderabadi", 4, 580, "spicy,aromatic,filling", "dairy"));
        catalogue.add(new Meal(id++, "Fish Fry Meal", MealTime.LUNCH, Diet.NON_VEG, "bengali", 3, 620, "protein,crispy", "fish"));
        catalogue.add(new Meal(id++, "Sambar Rice & Poriyal", MealTime.LUNCH, Diet.VEGAN, "south indian", 2, 480, "healthy,comfort,light", "none"));
        catalogue.add(new Meal(id++, "Veg Fried Rice & Manchurian", MealTime.LUNCH, Diet.VEG, "indo-chinese", 3, 600, "crispy,tangy,filling", "soy"));
        // Dinner
        catalogue.add(new Meal(id++, "Chole Bhature", MealTime.DINNER, Diet.VEG, "north indian", 4, 700, "spicy,rich,filling", "gluten,dairy"));
        catalogue.add(new Meal(id++, "Egg Curry & Roti", MealTime.DINNER, Diet.NON_VEG, "indian", 3, 480, "protein,comfort", "egg,gluten"));
        catalogue.add(new Meal(id++, "Mixed Veg & Roti", MealTime.DINNER, Diet.VEGAN, "indian", 2, 380, "healthy,light", "gluten"));
        catalogue.add(new Meal(id++, "Butter Chicken & Naan", MealTime.DINNER, Diet.NON_VEG, "north indian", 3, 720, "rich,creamy,protein", "dairy,gluten,nuts"));
        catalogue.add(new Meal(id++, "Pasta Arrabbiata", MealTime.DINNER, Diet.VEGAN, "continental", 3, 520, "tangy,quick,filling", "gluten"));
        catalogue.add(new Meal(id++, "Khichdi & Kadhi", MealTime.DINNER, Diet.VEG, "gujarati", 1, 400, "light,comfort,healthy", "dairy"));
        catalogue.add(new Meal(id++, "Veg Pulao & Raita", MealTime.DINNER, Diet.VEG, "indian", 2, 460, "aromatic,light", "dairy"));
        catalogue.add(new Meal(id++, "Chicken Noodles", MealTime.DINNER, Diet.NON_VEG, "indo-chinese", 3, 560, "tangy,protein,filling", "gluten,soy"));
    }
 
    static void seedStudents() {
        // Pre-existing students so collaborative filtering has data to work with.
        Student a = new Student("Aarav", Diet.NON_VEG, 4, 700, "north indian,hyderabadi", "spicy,protein,rich", "none");
        Student b = new Student("Diya", Diet.VEG, 2, 500, "south indian,gujarati", "light,healthy,comfort", "peanut");
        Student c = new Student("Kabir", Diet.VEGAN, 3, 600, "indian,continental", "healthy,protein,quick", "none");
        Student d = new Student("Meera", Diet.VEG, 3, 650, "indo-chinese,north indian", "tangy,crispy,filling", "none");
        students.put("aarav", a);
        students.put("diya", b);
        students.put("kabir", c);
        students.put("meera", d);
 
        rate(a, 9, 5);  rate(a, 15, 5); rate(a, 18, 4); rate(a, 11, 4);
        rate(b, 2, 5);  rate(b, 4, 4);  rate(b, 13, 5); rate(b, 20, 5);
        rate(c, 7, 5);  rate(c, 10, 4); rate(c, 17, 4); rate(c, 19, 5);
        rate(d, 14, 5); rate(d, 15, 4); rate(d, 6, 4);  rate(d, 22, 4);
    }
 
    static void buildWeeklyMenu() {
        int dayIndex = 0;
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<MealTime, List<Meal>> dayMenu = new EnumMap<>(MealTime.class);
            for (MealTime mt : MealTime.values()) {
                List<Meal> pool = catalogue.stream()
                        .filter(m -> m.time == mt).collect(Collectors.toList());
                int itemsToServe = Math.min(5, pool.size());
                List<Meal> served = new ArrayList<>();
                for (int i = 0; i < itemsToServe; i++) {
                    served.add(pool.get((dayIndex * 2 + i) % pool.size()));
                }
                dayMenu.put(mt, served);
            }
            weeklyMenu.put(day, dayMenu);
            dayIndex++;
        }
    }
 
    // ======================= Recommendation engine =======================
 
    static boolean dietAllowed(Student s, Meal m) {
        switch (s.diet) {
            case VEGAN:  return m.diet == Diet.VEGAN;
            case VEG:    return m.diet != Diet.NON_VEG;
            default:     return true;
        }
    }
 
    static boolean isEligible(Student s, Meal m) {
        if (!dietAllowed(s, m)) return false;
        for (String allergen : m.allergens) {
            if (s.allergies.contains(allergen)) return false;
        }
        return m.spice <= s.preferredSpice + 1;
    }
 
    /** Jaccard similarity of taste profiles (cuisines + tags), small bonus for same diet. */
    static double similarity(Student a, Student b) {
        Set<String> pa = profile(a), pb = profile(b);
        Set<String> union = new HashSet<>(pa);
        union.addAll(pb);
        if (union.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(pa);
        inter.retainAll(pb);
        double sim = (double) inter.size() / union.size();
        if (a.diet == b.diet) sim += 0.1;
        return Math.min(sim, 1.0);
    }
 
    static Set<String> profile(Student s) {
        Set<String> p = new HashSet<>();
        for (String c : s.cuisines) p.add("c:" + c);
        for (String t : s.likedTags) p.add("t:" + t);
        return p;
    }
 
    static Scored score(Student s, Meal m) {
        double score = 0;
        List<String> why = new ArrayList<>();
 
        // Cuisine match (25)
        if (s.cuisines.contains(m.cuisine)) {
            score += 25;
            why.add("matches your favourite cuisine (" + m.cuisine + ")");
        }
 
        // Tag matches (up to 30)
        List<String> hits = m.tags.stream().filter(s.likedTags::contains).collect(Collectors.toList());
        if (!hits.isEmpty()) {
            score += Math.min(30, hits.size() * 10);
            why.add("fits your taste: " + String.join(", ", hits));
        }
 
        // Calories (15)
        if (m.calories <= s.maxCaloriesPerMeal) {
            score += 15;
            why.add("within your calorie limit");
        } else {
            double over = (double) (m.calories - s.maxCaloriesPerMeal) / s.maxCaloriesPerMeal;
            score += 15 * Math.max(0, 1 - over * 2);
        }
 
        // Spice closeness (10)
        int diff = Math.abs(m.spice - s.preferredSpice);
        score += 10 * (1 - diff / 4.0);
        if (diff == 0) why.add("perfect spice level");
 
        // Community rating (20)
        if (m.ratingCount > 0) {
            score += (m.avgRating() / 5.0) * 20;
            if (m.avgRating() >= 4.0) {
                why.add(String.format("highly rated by students (%.1f/5)", m.avgRating()));
            }
        }
 
        // Collaborative: ratings from similar students
        double collab = 0;
        for (Student other : students.values()) {
            if (other == s) continue;
            Integer r = other.ratings.get(m.id);
            if (r != null) collab += similarity(s, other) * (r - 3);
        }
        collab = Math.max(-10, Math.min(10, collab * 4));
        score += collab;
        if (collab > 2) why.add("liked by students with similar taste");
 
        // Personal history
        Integer mine = s.ratings.get(m.id);
        if (mine != null) {
            score += (mine - 3) * 8;
            why.add(mine >= 4 ? "you rated this " + mine + "/5 before" : "you rated this low before");
        }
 
        return new Scored(m, score, why);
    }
 
    static List<Scored> recommend(Student s, List<Meal> menu, int topN) {
        return menu.stream()
                .filter(m -> isEligible(s, m))
                .map(m -> score(s, m))
                .sorted((x, y) -> Double.compare(y.score, x.score))
                .limit(topN)
                .collect(Collectors.toList());
    }
 
    static void rate(Student s, int mealId, int rating) {
        Meal meal = findMeal(mealId);
        if (meal == null) return;
        Integer old = s.ratings.put(mealId, rating);
        if (old != null) {
            meal.ratingSum += rating - old;
        } else {
            meal.ratingSum += rating;
            meal.ratingCount++;
        }
    }
 
    static Meal findMeal(int id) {
        for (Meal m : catalogue) if (m.id == id) return m;
        return null;
    }
 
    // ======================= Console UI =======================
 
    static final Scanner in = new Scanner(System.in);
    static Student current = null;
 
    public static void main(String[] args) {
        seedCatalogue();
        seedStudents();
        buildWeeklyMenu();
 
        System.out.println("=====================================");
        System.out.println("   MESS MENU RECOMMENDATION SYSTEM   ");
        System.out.println("=====================================");
 
        boolean running = true;
        while (running) {
            System.out.println("\nLogged in as: " + (current == null ? "(nobody)" : current.name));
            System.out.println("1. Create / switch student profile");
            System.out.println("2. View menu for a day");
            System.out.println("3. Recommend meals for a meal time");
            System.out.println("4. Get a full-day meal plan");
            System.out.println("5. Rate a meal");
            System.out.println("6. Exit");
            int choice = readInt("Choose: ", 1, 6);
            switch (choice) {
                case 1: profileMenu(); break;
                case 2: viewMenu(); break;
                case 3: if (requireLogin()) recommendForMealTime(); break;
                case 4: if (requireLogin()) fullDayPlan(); break;
                case 5: if (requireLogin()) rateMeal(); break;
                default: running = false;
            }
        }
        System.out.println("Enjoy your meals!");
    }
 
    static boolean requireLogin() {
        if (current == null) {
            System.out.println("Please create or select a profile first (option 1).");
            return false;
        }
        return true;
    }
 
    static void profileMenu() {
        System.out.println("\nExisting profiles: " + String.join(", ",
                students.values().stream().map(s -> s.name).collect(Collectors.toList())));
        System.out.print("Enter your name: ");
        String name = in.nextLine().trim();
        if (name.isEmpty()) return;
 
        Student existing = students.get(name.toLowerCase());
        if (existing != null) {
            current = existing;
            System.out.println("Welcome back, " + existing.name + "!");
            return;
        }
 
        System.out.println("New profile for " + name + ":");
        System.out.println("Diet: 1) Veg  2) Non-veg  3) Vegan");
        int d = readInt("Choose: ", 1, 3);
        Diet diet = d == 1 ? Diet.VEG : d == 2 ? Diet.NON_VEG : Diet.VEGAN;
        int spice = readInt("Preferred spice level (1 mild - 5 hot): ", 1, 5);
        int cal = readInt("Max calories per meal (e.g. 600): ", 150, 1500);
        System.out.println("Cuisines available: " + allValues("cuisine"));
        System.out.print("Favourite cuisines (comma separated): ");
        String cuisines = in.nextLine();
        System.out.println("Tags available: " + allValues("tags"));
        System.out.print("Favourite tags (comma separated): ");
        String tags = in.nextLine();
        System.out.print("Allergies (e.g. peanut,dairy,gluten or blank): ");
        String allergies = in.nextLine();
 
        Student s = new Student(name, diet, spice, cal, cuisines, tags, allergies);
        students.put(name.toLowerCase(), s);
        current = s;
        System.out.println("Profile created. You're logged in!");
    }
 
    static String allValues(String what) {
        Set<String> out = new TreeSet<>();
        for (Meal m : catalogue) {
            if (what.equals("cuisine")) out.add(m.cuisine);
            else out.addAll(m.tags);
        }
        return String.join(", ", out);
    }
 
    static DayOfWeek readDay() {
        DayOfWeek[] days = DayOfWeek.values();
        for (int i = 0; i < days.length; i++) System.out.println((i + 1) + ". " + days[i]);
        return days[readInt("Pick a day: ", 1, 7) - 1];
    }
 
    static MealTime readMealTime() {
        MealTime[] times = MealTime.values();
        for (int i = 0; i < times.length; i++) System.out.println((i + 1) + ". " + times[i]);
        return times[readInt("Pick a meal time: ", 1, 3) - 1];
    }
 
    static void viewMenu() {
        DayOfWeek day = readDay();
        System.out.println("\n--- Menu for " + day + " ---");
        for (MealTime mt : MealTime.values()) {
            System.out.println(mt + ":");
            for (Meal m : weeklyMenu.get(day).get(mt)) {
                System.out.println("  #" + m.id + " " + m);
            }
        }
    }
 
    static void recommendForMealTime() {
        DayOfWeek day = readDay();
        MealTime mt = readMealTime();
        List<Meal> menu = weeklyMenu.get(day).get(mt);
        List<Scored> recs = recommend(current, menu, 3);
 
        System.out.println("\nTop picks for " + current.name + " - " + day + " " + mt + ":");
        if (recs.isEmpty()) {
            System.out.println("Sorry, nothing on this menu fits your diet/allergy/spice limits.");
            return;
        }
        int rank = 1;
        for (Scored sc : recs) {
            System.out.printf("%d. %s  (score %.0f)%n", rank++, sc.meal, sc.score);
            System.out.println("   Why: " + (sc.reasons.isEmpty() ? "a safe match for your profile"
                    : String.join("; ", sc.reasons)));
        }
    }
 
    static void fullDayPlan() {
        DayOfWeek day = readDay();
        System.out.println("\nSuggested plan for " + current.name + " on " + day + ":");
        int total = 0;
        for (MealTime mt : MealTime.values()) {
            List<Scored> recs = recommend(current, weeklyMenu.get(day).get(mt), 1);
            if (recs.isEmpty()) {
                System.out.printf("%-10s -> no suitable option today%n", mt);
            } else {
                Meal m = recs.get(0).meal;
                total += m.calories;
                System.out.printf("%-10s -> %s (%d kcal)%n", mt, m.name, m.calories);
            }
        }
        System.out.println("Total calories: " + total + " kcal");
    }
 
    static void rateMeal() {
        System.out.println("\nAll meals:");
        for (Meal m : catalogue) System.out.println("  #" + m.id + " " + m);
        int id = readInt("Meal number to rate: ", 1, catalogue.size());
        int r = readInt("Your rating (1-5): ", 1, 5);
        rate(current, id, r);
        System.out.println("Thanks! Rated " + findMeal(id).name + " " + r + "/5.");
    }
 
    static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String line = in.nextLine().trim();
            try {
                int v = Integer.parseInt(line);
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a number between " + min + " and " + max + ".");
        }
    }
}
 
